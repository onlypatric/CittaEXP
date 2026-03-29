package it.patric.cittaexp.discord;

import it.patric.cittaexp.text.MiniMessageHelper;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

public final class DiscordIdentityLinkService {

    public enum LinkCompletionReason {
        LINKED,
        INVALID_CODE,
        EXPIRED_CODE,
        DISCORD_ALREADY_LINKED,
        PLAYER_ALREADY_LINKED_ELSEWHERE,
        INTERNAL_ERROR
    }

    public record LinkCodeIssueResult(String code, Instant expiresAt) {
    }

    public record LinkChange(UUID minecraftUuid, Long discordUserId) {
    }

    public record LinkCompletionResult(LinkCompletionReason reason, Optional<DiscordLinkRepository.PlayerDiscordLink> link) {
        public boolean success() {
            return reason == LinkCompletionReason.LINKED;
        }
    }

    private static final char[] CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private final Plugin plugin;
    private final DiscordLinkRepository repository;
    private final DiscordBridgeSettings settings;
    private final DiscordLogHelper log;
    private final SecureRandom random = new SecureRandom();
    private Consumer<LinkChange> playerLinkChangedListener = ignored -> { };

    public DiscordIdentityLinkService(
            Plugin plugin,
            DiscordLinkRepository repository,
            DiscordBridgeSettings settings
    ) {
        this.plugin = plugin;
        this.repository = repository;
        this.settings = settings;
        this.log = new DiscordLogHelper(plugin, settings.verboseLogging());
    }

    public void setPlayerLinkChangedListener(Consumer<LinkChange> playerLinkChangedListener) {
        this.playerLinkChangedListener = playerLinkChangedListener == null ? ignored -> { } : playerLinkChangedListener;
    }

    public LinkCodeIssueResult issueLinkCode(UUID playerId) {
        Instant now = Instant.now();
        int purged = repository.purgeExpiredLinkCodes(now);
        String code = nextCode();
        Instant expiresAt = now.plusSeconds(settings.linkCodeTtlMinutes() * 60L);
        repository.createOrReplaceLinkCode(playerId, code, now, expiresAt);
        log.info("issued link code player=" + playerId + " expiresAt=" + expiresAt + " purgedExpired=" + purged);
        return new LinkCodeIssueResult(code, expiresAt);
    }

    public LinkCompletionResult completeLink(String rawCode, long discordUserId, String username, String globalName) {
        try {
            String code = rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
            if (code.isBlank()) {
                log.debug("link completion rejected blank code discordUserId=" + Long.toUnsignedString(discordUserId));
                return new LinkCompletionResult(LinkCompletionReason.INVALID_CODE, Optional.empty());
            }
            Instant now = Instant.now();
            int purged = repository.purgeExpiredLinkCodes(now);
            log.debug("consuming link code discordUserId=" + Long.toUnsignedString(discordUserId)
                    + " username=" + username + " purgedExpired=" + purged);
            Optional<DiscordLinkRepository.LinkCodeRecord> recordOptional = repository.consumeLinkCode(code, now);
            if (recordOptional.isEmpty()) {
                log.info("link completion invalid code discordUserId=" + Long.toUnsignedString(discordUserId));
                return new LinkCompletionResult(LinkCompletionReason.INVALID_CODE, Optional.empty());
            }
            DiscordLinkRepository.LinkCodeRecord record = recordOptional.get();
            if (record.expiresAt().isBefore(now)) {
                log.info("link completion expired code discordUserId=" + Long.toUnsignedString(discordUserId)
                        + " player=" + record.minecraftUuid());
                return new LinkCompletionResult(LinkCompletionReason.EXPIRED_CODE, Optional.empty());
            }
            Optional<DiscordLinkRepository.PlayerDiscordLink> existingDiscord = repository.findLinkByDiscord(discordUserId);
            if (existingDiscord.isPresent() && !existingDiscord.get().minecraftUuid().equals(record.minecraftUuid())) {
                log.warn("discord account already linked discordUserId=" + Long.toUnsignedString(discordUserId)
                        + " existingPlayer=" + existingDiscord.get().minecraftUuid()
                        + " attemptedPlayer=" + record.minecraftUuid());
                return new LinkCompletionResult(LinkCompletionReason.DISCORD_ALREADY_LINKED, existingDiscord);
            }
            Optional<DiscordLinkRepository.PlayerDiscordLink> existingMinecraft = repository.findLinkByMinecraft(record.minecraftUuid());
            if (existingMinecraft.isPresent()
                    && existingMinecraft.get().discordUserId() != discordUserId
                    && repository.findLinkByDiscord(existingMinecraft.get().discordUserId()).isPresent()) {
                log.info("replacing previous discord link player=" + record.minecraftUuid()
                        + " oldDiscordUserId=" + Long.toUnsignedString(existingMinecraft.get().discordUserId())
                        + " newDiscordUserId=" + Long.toUnsignedString(discordUserId));
                repository.deleteLinkByDiscord(existingMinecraft.get().discordUserId());
            }
            repository.upsertPlayerLink(record.minecraftUuid(), discordUserId, username, globalName, now, now);
            playerLinkChangedListener.accept(new LinkChange(record.minecraftUuid(), discordUserId));
            var online = Bukkit.getPlayer(record.minecraftUuid());
            if (online != null) {
                online.sendMessage(MiniMessageHelper.parse(
                        "<dark_gray>[</dark_gray><gold>Discord</gold><dark_gray>]</dark_gray> "
                                + "<green>Collegamento completato.</green> "
                                + "<gray>Il tuo account e ora associato a</gray> <white>@{username}</white><gray>.</gray>",
                        net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("username", username == null ? "discord" : username)
                ));
            }
            log.info("link completion success player=" + record.minecraftUuid()
                    + " discordUserId=" + Long.toUnsignedString(discordUserId)
                    + " username=" + username);
            return new LinkCompletionResult(
                    LinkCompletionReason.LINKED,
                    repository.findLinkByMinecraft(record.minecraftUuid())
            );
        } catch (RuntimeException exception) {
            log.error("link completion failed", exception);
            return new LinkCompletionResult(LinkCompletionReason.INTERNAL_ERROR, Optional.empty());
        }
    }

    public boolean unlink(UUID minecraftUuid) {
        Optional<DiscordLinkRepository.PlayerDiscordLink> existing = repository.findLinkByMinecraft(minecraftUuid);
        boolean removed = repository.deleteLinkByMinecraft(minecraftUuid);
        if (removed) {
            playerLinkChangedListener.accept(new LinkChange(
                    minecraftUuid,
                    existing.map(DiscordLinkRepository.PlayerDiscordLink::discordUserId).orElse(null)
            ));
            log.info("unlinked player=" + minecraftUuid);
            return true;
        }
        log.debug("unlink requested but no link present player=" + minecraftUuid);
        return false;
    }

    public boolean staffUnlinkByPlayerName(String rawPlayerName) {
        OfflinePlayer offlinePlayer = resolveOfflinePlayer(rawPlayerName);
        if (offlinePlayer == null) {
            return false;
        }
        return unlink(offlinePlayer.getUniqueId());
    }

    public Optional<DiscordLinkRepository.PlayerDiscordLink> findLinkByMinecraft(UUID minecraftUuid) {
        return repository.findLinkByMinecraft(minecraftUuid);
    }

    public Optional<DiscordLinkRepository.PlayerDiscordLink> findLinkByDiscord(long discordUserId) {
        return repository.findLinkByDiscord(discordUserId);
    }

    public List<DiscordLinkRepository.PlayerDiscordLink> allLinks() {
        return repository.allLinks();
    }

    public Optional<DiscordLinkRepository.PlayerDiscordLink> findLinkByPlayerName(String rawPlayerName) {
        OfflinePlayer offlinePlayer = resolveOfflinePlayer(rawPlayerName);
        if (offlinePlayer == null) {
            return Optional.empty();
        }
        return findLinkByMinecraft(offlinePlayer.getUniqueId());
    }

    private OfflinePlayer resolveOfflinePlayer(String rawPlayerName) {
        if (rawPlayerName == null || rawPlayerName.isBlank()) {
            return null;
        }
        var online = Bukkit.getPlayerExact(rawPlayerName.trim());
        if (online != null) {
            return online;
        }
        return Bukkit.getOfflinePlayer(rawPlayerName.trim());
    }

    private String nextCode() {
        StringBuilder builder = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            if (i == 5) {
                builder.append('-');
            }
            builder.append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)]);
        }
        return builder.toString();
    }
}
