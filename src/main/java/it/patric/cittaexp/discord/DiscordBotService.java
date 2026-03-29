package it.patric.cittaexp.discord;

import it.patric.cittaexp.discord.DiscordIdentityLinkService.LinkCompletionReason;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import org.bukkit.OfflinePlayer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public final class DiscordBotService {

    private final Plugin plugin;
    private final DiscordBridgeSettings settings;
    private final DiscordIdentityLinkService linkService;
    private final DiscordLogHelper log;
    private JDA jda;
    private volatile boolean shuttingDown;
    private Runnable readyListener = () -> { };

    public DiscordBotService(
            Plugin plugin,
            DiscordBridgeSettings settings,
            DiscordIdentityLinkService linkService
    ) {
        this.plugin = plugin;
        this.settings = settings;
        this.linkService = linkService;
        this.log = new DiscordLogHelper(plugin, settings.verboseLogging());
    }

    public void setReadyListener(Runnable readyListener) {
        this.readyListener = readyListener == null ? () -> { } : readyListener;
    }

    public void start() {
        this.shuttingDown = false;
        if (!settings.enabled()) {
            log.info("bridge disabled in discord.yml");
            return;
        }
        if (!settings.tokenConfigured()) {
            log.warn("token missing; bridge disabled. Configure discord.yml or env " + settings.tokenEnvVar());
            return;
        }
        if (!settings.guildConfigured()) {
            log.warn("guildId missing/invalid; bridge disabled.");
            return;
        }
        if (!settings.categoryConfigured()) {
            log.warn("categoryId missing/invalid; town provisioning will stay disabled until category is configured.");
        }
        if (!settings.verificationChannelConfigured()) {
            log.warn("verificationChannelId missing/invalid; slash verification will stay disabled.");
        }
        log.info("starting bot bridge guildId=" + settings.guildId() + " intents=GUILD_MEMBERS,DIRECT_MESSAGES,MESSAGE_CONTENT");
        try {
            this.jda = JDABuilder.createDefault(
                            settings.token(),
                            EnumSet.of(GatewayIntent.GUILD_MEMBERS, GatewayIntent.DIRECT_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                    )
                    .setMemberCachePolicy(MemberCachePolicy.ALL)
                    .addEventListeners(new DiscordListener())
                    .build();
            log.debug("JDA build submitted successfully");
        } catch (RuntimeException exception) {
            log.error("failed to start bot bridge", exception);
            this.jda = null;
        }
    }

    public void stop() {
        this.shuttingDown = true;
        JDA current = this.jda;
        this.jda = null;
        if (current == null) {
            log.debug("stop requested with no active JDA instance");
            return;
        }

        current.removeEventListener(current.getRegisteredListeners().toArray());
        log.info("shutting down bot bridge status=" + current.getStatus());
        current.shutdown();
        try {
            if (current.awaitShutdown(10L, TimeUnit.SECONDS)) {
                log.info("bot bridge shut down cleanly");
                return;
            }
            log.warn("graceful shutdown timed out; forcing immediate shutdown");
            current.shutdownNow();
            if (current.awaitShutdown(5L, TimeUnit.SECONDS)) {
                log.info("bot bridge forced shutdown completed");
                return;
            }
            log.warn("bot bridge still not fully shut down after forced shutdown");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("shutdown interrupted; forcing immediate shutdown");
            current.shutdownNow();
        } catch (RuntimeException exception) {
            log.error("unexpected error while shutting down bot bridge", exception);
        }
    }

    public boolean isReady() {
        return guild().isPresent();
    }

    public boolean isShuttingDown() {
        return shuttingDown;
    }

    public Optional<Guild> guild() {
        if (jda == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(jda.getGuildById(settings.guildId()));
    }

    public Optional<JDA> jda() {
        return Optional.ofNullable(jda);
    }

    public Optional<Long> botUserId() {
        if (jda == null || jda.getSelfUser() == null) {
            return Optional.empty();
        }
        return Optional.of(jda.getSelfUser().getIdLong());
    }

    private final class DiscordListener extends ListenerAdapter {
        @Override
        public void onReady(@NotNull ReadyEvent event) {
            Guild guild = event.getJDA().getGuildById(settings.guildId());
            if (guild == null) {
                log.warn("connected but guildId " + settings.guildId() + " not found; bridge remains idle.");
                return;
            }
            log.info("bot connected to guild='" + guild.getName() + "' id=" + guild.getId() + " members=" + guild.getMemberCount());
            validateRequiredPermissions(guild);
            registerGuildCommands(guild);
            validateVerificationChannel(guild);
            Bukkit.getScheduler().runTask(plugin, readyListener);
        }

        @Override
        public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
            if (event.getGuild() == null || event.getGuild().getIdLong() != settings.guildId()) {
                return;
            }
            switch (event.getName()) {
                case "link" -> handleLinkSlash(event);
                case "who" -> handleWhoSlash(event);
                default -> {
                }
            }
        }

        @Override
        public void onMessageReceived(@NotNull MessageReceivedEvent event) {
            if (event.getAuthor().isBot()) {
                return;
            }
            if (settings.verificationChannelConfigured()
                    && event.isFromGuild()
                    && event.getChannel().getIdLong() == settings.verificationChannelId()) {
                log.debug("deleting verification-channel message messageId=" + event.getMessageId()
                        + " author=" + event.getAuthor().getId());
                event.getMessage().delete().queue(
                        success -> log.debug("deleted verification-channel message messageId=" + event.getMessageId()),
                        failure -> log.warn("failed to delete verification-channel message messageId=" + event.getMessageId()
                                + " reason=" + failure.getMessage())
                );
                return;
            }
            if (!event.isFromType(net.dv8tion.jda.api.entities.channel.ChannelType.PRIVATE)) {
                return;
            }
            String content = event.getMessage().getContentRaw();
            if (content == null || content.isBlank()) {
                log.debug("ignoring blank DM from discordUserId=" + event.getAuthor().getId());
                return;
            }
            log.debug("received DM verification attempt discordUserId=" + event.getAuthor().getId()
                    + " username=" + event.getAuthor().getName()
                    + " length=" + content.trim().length());
            Bukkit.getScheduler().runTask(plugin, () -> {
                var result = linkService.completeLink(
                        content,
                        event.getAuthor().getIdLong(),
                        event.getAuthor().getName(),
                        event.getAuthor().getGlobalName()
                );
                log.debug("DM link completion discordUserId=" + event.getAuthor().getId()
                        + " result=" + result.reason().name());
                switch (result.reason()) {
                    case LINKED -> event.getChannel().sendMessage("Collegamento completato. Il tuo account Discord e ora associato a Minecraft.").queue();
                    case DISCORD_ALREADY_LINKED -> event.getChannel().sendMessage("Questo account Discord e gia collegato a un altro account Minecraft.").queue();
                    case EXPIRED_CODE -> event.getChannel().sendMessage("Codice scaduto. Generane uno nuovo in game con /verify.").queue();
                    case INVALID_CODE -> event.getChannel().sendMessage("Codice non valido. Controlla il codice e riprova.").queue();
                    case PLAYER_ALREADY_LINKED_ELSEWHERE, INTERNAL_ERROR -> event.getChannel().sendMessage("Impossibile completare il collegamento in questo momento.").queue();
                }
            });
        }

        private void registerGuildCommands(Guild guild) {
            log.info("registering guild slash commands for verification guildId=" + guild.getId());
            guild.updateCommands()
                    .addCommands(Commands.slash("link", "Collega il tuo account Minecraft usando il codice di /verify")
                                    .addOption(OptionType.STRING, "codice", "Il codice generato in Minecraft con /verify", true),
                            Commands.slash("who", "Mostra l'account Minecraft collegato a un utente Discord")
                                    .addOption(OptionType.USER, "utente", "L'utente Discord da controllare", false))
                    .queue(
                            success -> log.info("guild slash commands registered successfully guildId=" + guild.getId()),
                            failure -> log.error("failed to register guild slash commands", failure)
                    );
        }

        private void handleLinkSlash(SlashCommandInteractionEvent event) {
            if (!settings.verificationChannelConfigured()) {
                log.warn("received /link but verificationChannelId is not configured");
                event.reply("Verifica Discord non configurata sul server.").setEphemeral(true).queue();
                return;
            }
            long channelId = event.getChannel().getIdLong();
            if (channelId != settings.verificationChannelId()) {
                log.info("rejected /link outside verification channel user=" + event.getUser().getId() + " channelId=" + channelId);
                event.reply("Usa `/link` nel canale Discord di verifica.").setEphemeral(true).queue();
                return;
            }
            String code = event.getOption("codice") == null ? "" : event.getOption("codice").getAsString();
            log.info("received /link interaction discordUserId=" + event.getUser().getId()
                    + " channelId=" + channelId + " codeLength=" + code.trim().length());
            Bukkit.getScheduler().runTask(plugin, () -> {
                var result = linkService.completeLink(
                        code,
                        event.getUser().getIdLong(),
                        event.getUser().getName(),
                        event.getUser().getGlobalName()
                );
                log.info("slash /link completion discordUserId=" + event.getUser().getId()
                        + " result=" + result.reason().name());
                switch (result.reason()) {
                    case LINKED -> event.reply("Collegamento completato. Il tuo account Discord ora e associato a Minecraft.").setEphemeral(true).queue();
                    case DISCORD_ALREADY_LINKED -> event.reply("Questo account Discord e gia collegato a un altro account Minecraft.").setEphemeral(true).queue();
                    case EXPIRED_CODE -> event.reply("Codice scaduto. Generane uno nuovo in gioco con `/verify`.").setEphemeral(true).queue();
                    case INVALID_CODE -> event.reply("Codice non valido. Controlla il codice e riprova.").setEphemeral(true).queue();
                    case PLAYER_ALREADY_LINKED_ELSEWHERE, INTERNAL_ERROR -> event.reply("Impossibile completare il collegamento in questo momento.").setEphemeral(true).queue();
                }
            });
        }

        private void handleWhoSlash(SlashCommandInteractionEvent event) {
            long targetUserId = event.getOption("utente") == null
                    ? event.getUser().getIdLong()
                    : event.getOption("utente").getAsUser().getIdLong();
            String targetDiscordName = event.getOption("utente") == null
                    ? event.getUser().getName()
                    : event.getOption("utente").getAsUser().getName();
            log.debug("received /who interaction requester=" + event.getUser().getId()
                    + " targetDiscordUserId=" + Long.toUnsignedString(targetUserId));
            Bukkit.getScheduler().runTask(plugin, () -> {
                Optional<DiscordLinkRepository.PlayerDiscordLink> linkOptional = linkService.findLinkByDiscord(targetUserId);
                if (linkOptional.isEmpty()) {
                    event.reply("Nessun account Minecraft collegato per @" + targetDiscordName + ".")
                            .setEphemeral(true)
                            .queue();
                    return;
                }
                DiscordLinkRepository.PlayerDiscordLink link = linkOptional.get();
                String minecraftName = resolveMinecraftName(link.minecraftUuid());
                event.reply("@" + targetDiscordName + " -> `" + minecraftName + "`")
                        .setEphemeral(true)
                        .queue();
            });
        }

        private void validateVerificationChannel(Guild guild) {
            if (!settings.verificationChannelConfigured()) {
                return;
            }
            TextChannel channel = guild.getTextChannelById(settings.verificationChannelId());
            if (channel == null) {
                log.warn("verification channel not found guildId=" + guild.getId() + " channelId=" + settings.verificationChannelId());
                return;
            }
            if (settings.verificationCategoryConfigured()) {
                String actualCategoryId = channel.getParentCategory() == null ? null : channel.getParentCategory().getId();
                if (actualCategoryId == null || !actualCategoryId.equals(Long.toUnsignedString(settings.verificationCategoryId()))) {
                    log.warn("verification channel category mismatch channelId=" + channel.getId()
                            + " expectedCategory=" + settings.verificationCategoryId()
                            + " actualCategory=" + (actualCategoryId == null ? "none" : actualCategoryId));
                    return;
                }
            }
            validateVerificationChannelPermissions(guild, channel);
            log.info("verification channel validated channelId=" + channel.getId()
                    + " categoryId=" + (channel.getParentCategory() == null ? "none" : channel.getParentCategory().getId()));
        }

        private void validateRequiredPermissions(Guild guild) {
            Member self = guild.getSelfMember();
            Set<Permission> missing = new LinkedHashSet<>();
            requireGuildPermission(self, Permission.VIEW_CHANNEL, missing);
            requireGuildPermission(self, Permission.MANAGE_CHANNEL, missing);
            requireGuildPermission(self, Permission.MANAGE_ROLES, missing);
            requireGuildPermission(self, Permission.MESSAGE_SEND, missing);
            requireGuildPermission(self, Permission.MESSAGE_MANAGE, missing);
            requireGuildPermission(self, Permission.MESSAGE_HISTORY, missing);
            requireGuildPermission(self, Permission.NICKNAME_MANAGE, missing);
            if (missing.isEmpty()) {
                log.info("required guild permissions validated successfully");
                return;
            }
            log.warn("missing required guild permissions: " + formatPermissions(missing));
        }

        private void validateVerificationChannelPermissions(Guild guild, TextChannel channel) {
            Member self = guild.getSelfMember();
            Set<Permission> missing = new LinkedHashSet<>();
            requireChannelPermission(self, channel, Permission.VIEW_CHANNEL, missing);
            requireChannelPermission(self, channel, Permission.MESSAGE_SEND, missing);
            requireChannelPermission(self, channel, Permission.MESSAGE_MANAGE, missing);
            requireChannelPermission(self, channel, Permission.MESSAGE_HISTORY, missing);
            if (missing.isEmpty()) {
                log.info("verification channel permissions validated successfully channelId=" + channel.getId());
                return;
            }
            log.warn("missing verification-channel permissions channelId=" + channel.getId()
                    + " permissions=" + formatPermissions(missing));
        }

        private void requireGuildPermission(Member self, Permission permission, Set<Permission> missing) {
            if (!self.hasPermission(permission)) {
                missing.add(permission);
            }
        }

        private void requireChannelPermission(Member self, TextChannel channel, Permission permission, Set<Permission> missing) {
            if (!self.hasPermission(channel, permission)) {
                missing.add(permission);
            }
        }

        private String resolveMinecraftName(java.util.UUID minecraftUuid) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(minecraftUuid);
            if (offlinePlayer.getName() != null && !offlinePlayer.getName().isBlank()) {
                return offlinePlayer.getName();
            }
            return minecraftUuid.toString();
        }

        private String formatPermissions(Set<Permission> permissions) {
            return permissions.stream()
                    .map(Permission::getName)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("none");
        }
    }
}
