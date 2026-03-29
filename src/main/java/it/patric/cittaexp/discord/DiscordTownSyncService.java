package it.patric.cittaexp.discord;

import it.patric.cittaexp.integration.husktowns.HuskTownsApiHook;
import it.patric.cittaexp.levels.CityLevelService;
import it.patric.cittaexp.levels.TownStage;
import it.patric.cittaexp.utils.PluginConfigUtils;
import java.awt.Color;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.format.TextColor;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.RestAction;
import net.william278.husktowns.events.MemberJoinEvent;
import net.william278.husktowns.events.MemberLeaveEvent;
import net.william278.husktowns.events.MemberRoleChangeEvent;
import net.william278.husktowns.events.PostTownCreateEvent;
import net.william278.husktowns.events.TownDisbandEvent;
import net.william278.husktowns.events.TownUpdateEvent;
import net.william278.husktowns.town.Town;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class DiscordTownSyncService implements Listener {

    private static final Key CITY_TAG_KEY = Key.key("cittaexp", "tag");
    private static final long FULL_RECONCILE_DEBOUNCE_TICKS = 20L;
    private static final long TOWN_SYNC_DEBOUNCE_TICKS = 8L;
    private static final long CHANNEL_METADATA_WINDOW_MS = TimeUnit.MINUTES.toMillis(15);
    private static final long CHANNEL_METADATA_COOLDOWN_MS = TimeUnit.MINUTES.toMillis(15);
    private static final int CHANNEL_METADATA_BURST_LIMIT = 3;
    private static final long STABLE_EVENT_TOWN_SYNC_COOLDOWN_MS = TimeUnit.MINUTES.toMillis(30);
    private static final long STABLE_RECONCILE_TOWN_SYNC_COOLDOWN_MS = TimeUnit.MINUTES.toMillis(30);
    private static final long STABLE_GLOBAL_IDENTITY_SYNC_COOLDOWN_MS = TimeUnit.MINUTES.toMillis(30);
    private static final long FULL_RECONCILE_TOWN_PAUSE_MS = 250L;
    private static final int TOPIC_MAX_LENGTH = 768;
    private static final int TOPIC_BIO_RESERVED = 256;
    private static final DateTimeFormatter FOUNDED_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yy");
    private static final ErrorHandler IGNORE_NOT_FOUND = new ErrorHandler().ignore(ErrorResponse.UNKNOWN_MEMBER)
            .ignore(ErrorResponse.UNKNOWN_ROLE)
            .ignore(ErrorResponse.UNKNOWN_CHANNEL)
            .ignore(ErrorResponse.UNKNOWN_USER);

    private final Plugin plugin;
    private final HuskTownsApiHook huskTownsApiHook;
    private final CityLevelService cityLevelService;
    private final DiscordBridgeSettings settings;
    private final DiscordLinkRepository repository;
    private final DiscordIdentityLinkService linkService;
    private final DiscordBotService botService;
    private final DiscordLogHelper log;
    private final PluginConfigUtils configUtils;
    private final ExecutorService discordExecutor;
    private final Map<Integer, BukkitTask> pendingTownTasks = new ConcurrentHashMap<>();
    private final Map<Integer, BukkitTask> pendingMetadataRetryTasks = new ConcurrentHashMap<>();
    private final Map<Integer, TownSyncSnapshot> lastQueuedTownSnapshots = new ConcurrentHashMap<>();
    private final Map<Integer, Long> lastQueuedTownSyncAt = new ConcurrentHashMap<>();
    private final Map<Long, ArrayDeque<Long>> channelMetadataAttemptTimes = new ConcurrentHashMap<>();
    private final Map<Long, Long> channelMetadataCooldownUntil = new ConcurrentHashMap<>();
    private final Map<Integer, UUID> pendingTownEditors = new ConcurrentHashMap<>();
    private volatile BukkitTask pendingFullReconcileTask;
    private volatile boolean stopping;
    private volatile int lastLinkedIdentityHash;
    private volatile long lastLinkedIdentitySyncAt;
    private volatile int lastFullReconcileHash;
    private int reconcileTaskId = -1;

    public DiscordTownSyncService(
            Plugin plugin,
            HuskTownsApiHook huskTownsApiHook,
            CityLevelService cityLevelService,
            DiscordBridgeSettings settings,
            DiscordLinkRepository repository,
            DiscordIdentityLinkService linkService,
            DiscordBotService botService
    ) {
        this.plugin = plugin;
        this.huskTownsApiHook = huskTownsApiHook;
        this.cityLevelService = cityLevelService;
        this.settings = settings;
        this.repository = repository;
        this.linkService = linkService;
        this.botService = botService;
        this.log = new DiscordLogHelper(plugin, settings.verboseLogging());
        this.configUtils = new PluginConfigUtils(plugin);
        this.discordExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "CittaEXP-DiscordSync");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        this.stopping = false;
        stopTasksOnly();
        long periodTicks = Math.max(20L, settings.reconcileIntervalSeconds() * 20L);
        log.info("starting town sync service reconcileIntervalSeconds=" + settings.reconcileIntervalSeconds()
                + " minStage=" + settings.minStage().name()
                + " provisionAllTowns=" + settings.provisionAllTowns()
                + " categoryConfigured=" + settings.categoryConfigured());
        this.reconcileTaskId = Bukkit.getScheduler().scheduleAsyncRepeatingTask(plugin, this::reconcileAll, 40L, periodTicks);
    }

    public void stop() {
        this.stopping = true;
        log.info("stopping town sync service");
        stopTasksOnly();
        discordExecutor.shutdown();
        try {
            if (!discordExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                discordExecutor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            discordExecutor.shutdownNow();
        }
    }

    public void reconcileAll() {
        log.debug("global reconcile requested");
        Bukkit.getScheduler().runTask(plugin, this::scheduleFullReconcileOnMainThread);
    }

    public void resyncTownByName(String rawTownName) {
        Bukkit.getScheduler().runTask(plugin, () -> huskTownsApiHook.getTownByName(rawTownName).ifPresent(this::syncTown));
    }

    public void syncPlayer(UUID minecraftUuid) {
        log.debug("player sync requested player=" + minecraftUuid);
        Bukkit.getScheduler().runTask(plugin, () -> {
            Optional<Town> townOptional = findTownOfMember(minecraftUuid);
            if (townOptional.isPresent()) {
                syncTown(townOptional.get());
                return;
            }
            scheduleFullReconcileOnMainThread();
        });
    }

    public void cleanupLinkedDiscordUser(long discordUserId) {
        log.debug("cleanup requested for discord user=" + Long.toUnsignedString(discordUserId));
        queueDiscordWork(() -> applyLinkedUserCleanup(discordUserId));
    }

    public void registerTownMetadataEdit(int townId, UUID editorId) {
        if (editorId == null) {
            return;
        }
        pendingTownEditors.put(townId, editorId);
        log.debug("registered town metadata editor town=" + townId + " player=" + editorId);
    }

    @EventHandler
    public void onPostTownCreate(PostTownCreateEvent event) {
        log.info("town create event town=" + event.getTown().getId() + " name=" + event.getTown().getName());
        syncTown(event.getTown());
    }

    @EventHandler
    public void onTownUpdate(TownUpdateEvent event) {
        log.debug("town update event town=" + event.getTown().getId() + " name=" + event.getTown().getName());
        syncTown(event.getTown());
    }

    @EventHandler
    public void onTownDisband(TownDisbandEvent event) {
        int townId = event.getTown().getId();
        log.info("town disband event town=" + townId + " name=" + event.getTown().getName());
        queueDiscordWork(() -> applyDeprovision(townId));
    }

    @EventHandler
    public void onMemberJoin(MemberJoinEvent event) {
        log.debug("member join event town=" + event.getTown().getId() + " player=" + event.getUser().getUuid());
        syncTown(event.getTown());
        String playerName = resolveMinecraftName(event.getUser().getUuid());
        TownSyncSnapshot snapshot = captureSnapshot(event.getTown());
        queueDiscordWork(() -> sendGreetingMessage(snapshot, playerName));
    }

    @EventHandler
    public void onMemberLeave(MemberLeaveEvent event) {
        log.debug("member leave event town=" + event.getTown().getId() + " player=" + event.getUser().getUuid());
        syncTown(event.getTown());
        syncPlayer(event.getUser().getUuid());
    }

    @EventHandler
    public void onMemberRoleChange(MemberRoleChangeEvent event) {
        log.debug("member role change event town=" + event.getTown().getId() + " player=" + event.getUser().getUuid());
        syncTown(event.getTown());
    }

    public void syncTown(Town town) {
        if (town == null) {
            return;
        }
        int townId = town.getId();
        log.debug("town sync requested town=" + townId + " name=" + town.getName());
        BukkitTask previous = pendingTownTasks.remove(townId);
        if (previous != null) {
            previous.cancel();
            log.debug("replaced pending town sync town=" + townId);
        }
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingTownTasks.remove(townId);
            huskTownsApiHook.getTownById(townId)
                    .map(this::captureSnapshot)
                    .ifPresent(snapshot -> {
                        log.debug("captured town snapshot town=" + snapshot.townId()
                                + " eligible=" + snapshot.eligible()
                                + " members=" + snapshot.memberIds().size());
                        if (shouldSkipTownSnapshot(snapshot, false)) {
                            log.debug("skipping stable event town sync town=" + snapshot.townId()
                                    + " name=" + snapshot.townName());
                            return;
                        }
                        markTownSnapshotQueued(snapshot);
                        queueDiscordWork(() -> applyTownSnapshot(snapshot));
                    });
        }, TOWN_SYNC_DEBOUNCE_TICKS);
        pendingTownTasks.put(townId, task);
    }

    private void scheduleFullReconcileOnMainThread() {
        if (pendingFullReconcileTask != null) {
            log.debug("skipping full reconcile schedule because one is already pending");
            return;
        }
        pendingFullReconcileTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingFullReconcileTask = null;
            Set<Integer> liveTownIds = new HashSet<>();
            java.util.List<TownSyncSnapshot> snapshots = huskTownsApiHook.getPlayableTowns().stream()
                    .map(town -> {
                        liveTownIds.add(town.getId());
                        return captureSnapshot(town);
                    })
                    .toList();
            int reconcileHash = computeFullReconcileHash(snapshots, liveTownIds);
            if (reconcileHash == lastFullReconcileHash) {
                log.debug("skipping full reconcile because minecraft state hash is unchanged snapshotCount="
                        + snapshots.size() + " liveTownIds=" + liveTownIds.size());
                return;
            }
            log.info("captured full reconcile snapshotCount=" + snapshots.size() + " liveTownIds=" + liveTownIds.size());
            lastFullReconcileHash = reconcileHash;
            queueDiscordWork(() -> applyFullReconcile(snapshots, liveTownIds));
        }, FULL_RECONCILE_DEBOUNCE_TICKS);
    }

    private TownSyncSnapshot captureSnapshot(Town town) {
        CityLevelService.LevelStatus levelStatus = cityLevelService.statusForTown(town.getId(), false);
        TownStage stage = levelStatus.stage();
        boolean eligible = settings.provisionAllTowns() || stage.ordinal() >= settings.minStage().ordinal();
        String townTag = town.getMetadataTag(CITY_TAG_KEY).map(String::trim).filter(s -> !s.isBlank()).orElse("---");
        return new TownSyncSnapshot(
                town.getId(),
                town.getName(),
                townTag,
                normalizeTownHex(town.getColorRgb()),
                normalizeTopic(town.getBio().orElse("")),
                town.getGreeting().orElse("").trim(),
                levelStatus.level(),
                stage.displayName(),
                town.getMembers().size(),
                levelStatus.memberCap(),
                town.getClaimCount(),
                levelStatus.claimCap(),
                normalizeMoney(town.getMoney()),
                town.getFoundedTime().format(FOUNDED_FORMAT),
                resolveMinecraftName(town.getMayor()),
                warpLabel(town),
                eligible,
                Set.copyOf(town.getMembers().keySet())
        );
    }

    private void applyFullReconcile(java.util.List<TownSyncSnapshot> snapshots, Set<Integer> liveTownIds) {
        if (!botService.isReady()) {
            log.debug("full reconcile skipped because bot is not ready");
            return;
        }
        log.info("applying full reconcile snapshotCount=" + snapshots.size());
        Optional<Guild> guildOptional = botService.guild();
        if (guildOptional.isEmpty()) {
            return;
        }
        Guild guild = guildOptional.get();
        if (shouldRunGlobalIdentitySync()) {
            syncVerifiedRoleMembership(guild);
            syncLinkedNicknames(guild);
            markGlobalIdentitySyncQueued();
        } else {
            log.debug("skipping stable global identity reconcile because no linked-user drift was detected recently");
        }
        for (TownSyncSnapshot snapshot : snapshots) {
            if (shouldSkipTownSnapshot(snapshot, true)) {
                log.debug("skipping stable reconcile town sync town=" + snapshot.townId()
                        + " name=" + snapshot.townName());
                continue;
            }
            markTownSnapshotQueued(snapshot);
            applyTownSnapshot(snapshot);
            if (FULL_RECONCILE_TOWN_PAUSE_MS > 0L) {
                try {
                    Thread.sleep(FULL_RECONCILE_TOWN_PAUSE_MS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        long guildId = guildOptional.get().getIdLong();
        for (DiscordLinkRepository.TownDiscordBinding binding : repository.allTownBindings(guildId)) {
            if (!liveTownIds.contains(binding.townId())) {
                log.info("binding references missing town; deprovision town=" + binding.townId());
                applyDeprovision(binding.townId());
            }
        }
    }

    private void applyTownSnapshot(TownSyncSnapshot snapshot) {
        Optional<Guild> guildOptional = botService.guild();
        if (guildOptional.isEmpty()) {
            log.debug("town snapshot skipped because guild is unavailable town=" + snapshot.townId());
            return;
        }
        Guild guild = guildOptional.get();
        if (!settings.categoryConfigured()) {
            log.warn("categoryId not configured; deprovisioning/ignoring town=" + snapshot.townId() + " name=" + snapshot.townName());
            applyDeprovision(snapshot.townId());
            return;
        }
        Category category = guild.getCategoryById(settings.categoryId());
        if (category == null) {
            log.warn("configured category not found in guild; deprovisioning/ignoring town=" + snapshot.townId()
                    + " categoryId=" + settings.categoryId());
            applyDeprovision(snapshot.townId());
            return;
        }
        if (!snapshot.eligible()) {
            log.info("town below discord stage threshold; deprovision town=" + snapshot.townId() + " name=" + snapshot.townName());
            applyDeprovision(snapshot.townId());
            return;
        }

        DiscordLinkRepository.TownDiscordBinding binding = repository.findTownBinding(snapshot.townId(), guild.getIdLong()).orElse(null);
        String desiredRoleName = renderRoleName(snapshot);
        String desiredChannelName = renderChannelName(snapshot);
        Role existingRole = resolveRole(guild, binding, desiredRoleName);
        if (existingRole == null) {
            log.info("creating discord role town=" + snapshot.townId() + " role=" + desiredRoleName);
            guild.createRole().setName(desiredRoleName).queue(
                    createdRole -> {
                        syncRoleColor(snapshot, createdRole);
                        ensureChannelAndMembers(guild, category, snapshot, binding, createdRole, desiredChannelName);
                    },
                    failure -> logDiscordFailure("create role town=" + snapshot.townId(), failure)
            );
            return;
        }
        if (!existingRole.getName().equals(desiredRoleName)) {
            log.info("renaming discord role town=" + snapshot.townId()
                    + " old=" + existingRole.getName() + " new=" + desiredRoleName);
            existingRole.getManager().setName(desiredRoleName).queue(
                    ignored -> { },
                    failure -> logDiscordFailure("rename role town=" + snapshot.townId(), failure)
            );
        } else {
            log.debug("reusing discord role town=" + snapshot.townId() + " roleId=" + existingRole.getId());
        }
        syncRoleColor(snapshot, existingRole);
        ensureChannelAndMembers(guild, category, snapshot, binding, existingRole, desiredChannelName);
    }

    private void ensureChannelAndMembers(
            Guild guild,
            Category category,
            TownSyncSnapshot snapshot,
            DiscordLinkRepository.TownDiscordBinding binding,
            Role role,
            String desiredChannelName
    ) {
        TextChannel existingChannel = resolveChannel(guild, category, binding, desiredChannelName);
        String desiredTopic = buildTownTopic(snapshot);
        if (existingChannel == null) {
            log.info("creating discord text channel town=" + snapshot.townId() + " channel=" + desiredChannelName);
            createTownChannel(category, desiredChannelName, desiredTopic).queue(
                    createdChannel -> {
                        configureChannelPermissions(guild, createdChannel, role);
                        repository.upsertTownBinding(snapshot.townId(), guild.getIdLong(), role.getIdLong(), createdChannel.getIdLong(), Instant.now());
                        log.info("binding upserted town=" + snapshot.townId()
                                + " roleId=" + role.getIdLong() + " channelId=" + createdChannel.getIdLong());
                        sendTownChannelBootstrap(snapshot, createdChannel);
                        syncRoleMembership(guild, snapshot, role);
                    },
                    failure -> logDiscordFailure("create channel town=" + snapshot.townId(), failure)
            );
            return;
        }
        syncChannelState(snapshot, existingChannel, category, desiredChannelName, desiredTopic);
        configureChannelPermissions(guild, existingChannel, role);
        repository.upsertTownBinding(snapshot.townId(), guild.getIdLong(), role.getIdLong(), existingChannel.getIdLong(), Instant.now());
        log.debug("binding refreshed town=" + snapshot.townId()
                + " roleId=" + role.getIdLong() + " channelId=" + existingChannel.getIdLong());
        syncRoleMembership(guild, snapshot, role);
    }

    private void applyDeprovision(int townId) {
        Optional<Guild> guildOptional = botService.guild();
        if (guildOptional.isEmpty()) {
            return;
        }
        Guild guild = guildOptional.get();
        Optional<DiscordLinkRepository.TownDiscordBinding> bindingOptional = repository.findTownBinding(townId, guild.getIdLong());
        if (bindingOptional.isEmpty()) {
            log.debug("deprovision skipped no binding town=" + townId);
            return;
        }
        DiscordLinkRepository.TownDiscordBinding binding = bindingOptional.get();
        log.info("deprovisioning town=" + townId + " roleId=" + binding.roleId() + " channelId=" + binding.textChannelId());
        boolean queuedDelete = false;
        if (binding.textChannelId() != null) {
            TextChannel channel = guild.getTextChannelById(binding.textChannelId());
            if (channel != null) {
                queuedDelete = true;
                channel.delete().queue(
                        ignored -> { },
                        failure -> logDiscordFailure("delete channel town=" + townId, failure)
                );
            }
        }
        if (binding.roleId() != null) {
            Role role = guild.getRoleById(binding.roleId());
            if (role != null) {
                queuedDelete = true;
                role.delete().queue(
                        ignored -> { },
                        failure -> logDiscordFailure("delete role town=" + townId, failure)
                );
            }
        }
        if (!queuedDelete) {
            repository.deleteTownBinding(townId, guild.getIdLong());
            log.info("binding deleted immediately town=" + townId + " guildId=" + guild.getIdLong());
        } else {
            log.debug("delete queued on discord API; binding retained until next reconcile town=" + townId);
        }
        lastQueuedTownSnapshots.remove(townId);
        lastQueuedTownSyncAt.remove(townId);
    }

    private void syncRoleMembership(Guild guild, TownSyncSnapshot snapshot, Role role) {
        Set<Long> desiredDiscordIds = new HashSet<>();
        for (DiscordLinkRepository.PlayerDiscordLink link : linkService.allLinks()) {
            if (snapshot.memberIds().contains(link.minecraftUuid())) {
                desiredDiscordIds.add(link.discordUserId());
            }
        }
        log.info("syncing role membership town=" + snapshot.townId()
                + " desiredMembers=" + desiredDiscordIds.size()
                + " roleId=" + role.getIdLong());

        for (DiscordLinkRepository.PlayerDiscordLink link : linkService.allLinks()) {
            long discordUserId = link.discordUserId();
            boolean shouldHaveRole = desiredDiscordIds.contains(discordUserId);
            guild.retrieveMemberById(discordUserId).queue(
                    member -> syncSingleRoleMembership(
                            guild,
                            member,
                            role,
                            shouldHaveRole,
                            "town=" + snapshot.townId() + " memberId=" + discordUserId
                    ),
                    failure -> logDiscordFailure("retrieve member town=" + snapshot.townId() + " memberId=" + discordUserId, failure)
            );
        }
    }

    private void syncVerifiedRoleMembership(Guild guild) {
        if (!settings.verifiedRoleConfigured()) {
            log.debug("verified role sync skipped because verifiedRoleId is not configured");
            return;
        }
        Role verifiedRole = guild.getRoleById(settings.verifiedRoleId());
        if (verifiedRole == null) {
            log.warn("verified role configured but not found roleId=" + settings.verifiedRoleId());
            return;
        }
        Member selfMember = guild.getSelfMember();
        if (!selfMember.hasPermission(Permission.MANAGE_ROLES)) {
            log.warn("verified role sync skipped reason=missing Manage Roles permission roleId=" + verifiedRole.getId());
            return;
        }
        if (!selfMember.canInteract(verifiedRole)) {
            log.warn("verified role sync skipped reason=verified role has higher or equal hierarchy roleId=" + verifiedRole.getId());
            return;
        }
        Set<Long> desiredDiscordIds = linkService.allLinks().stream()
                .map(DiscordLinkRepository.PlayerDiscordLink::discordUserId)
                .collect(java.util.stream.Collectors.toSet());
        log.info("syncing verified discord role desiredMembers=" + desiredDiscordIds.size()
                + " roleId=" + verifiedRole.getIdLong());
        for (DiscordLinkRepository.PlayerDiscordLink link : linkService.allLinks()) {
            long discordUserId = link.discordUserId();
            guild.retrieveMemberById(discordUserId).queue(
                    member -> syncSingleRoleMembership(
                            guild,
                            member,
                            verifiedRole,
                            desiredDiscordIds.contains(discordUserId),
                            "verified-role memberId=" + discordUserId
                    ),
                    failure -> logDiscordFailure("retrieve verified-role memberId=" + discordUserId, failure)
            );
        }
    }

    private void syncLinkedNicknames(Guild guild) {
        for (DiscordLinkRepository.PlayerDiscordLink link : linkService.allLinks()) {
            String desiredNickname = resolveMinecraftName(link.minecraftUuid());
            if (desiredNickname == null || desiredNickname.isBlank()) {
                continue;
            }
            Member cachedMember = guild.getMemberById(link.discordUserId());
            if (cachedMember != null) {
                syncLinkedNickname(guild, cachedMember, link.discordUserId(), desiredNickname);
                continue;
            }
            guild.retrieveMemberById(link.discordUserId()).queue(
                    member -> syncLinkedNickname(guild, member, link.discordUserId(), desiredNickname),
                    failure -> logDiscordFailure("retrieve nickname-sync memberId=" + link.discordUserId(), failure)
            );
        }
    }

    private void syncLinkedNickname(Guild guild, Member member, long discordUserId, String desiredNickname) {
        String currentNickname = member.getNickname();
        String effectiveCurrent = currentNickname == null || currentNickname.isBlank()
                ? member.getUser().getName()
                : currentNickname;
        if (desiredNickname.equals(effectiveCurrent)) {
            log.debug("discord nickname already aligned memberId=" + member.getId()
                    + " nickname=" + desiredNickname);
            return;
        }
        Member selfMember = guild.getSelfMember();
        if (!selfMember.hasPermission(net.dv8tion.jda.api.Permission.NICKNAME_MANAGE)) {
            log.warn("skipping discord nickname sync memberId=" + Long.toUnsignedString(discordUserId)
                    + " reason=missing Manage Nicknames permission");
            return;
        }
        boolean silentHierarchyAttempt = !selfMember.canInteract(member);
        log.info("updating discord nickname memberId=" + Long.toUnsignedString(discordUserId)
                + " old=" + effectiveCurrent + " new=" + desiredNickname);
        try {
            guild.modifyNickname(member, desiredNickname).queue(
                    ignored -> { },
                    failure -> {
                        if (!silentHierarchyAttempt) {
                            logDiscordFailure("set nickname memberId=" + discordUserId, failure);
                        }
                    }
            );
        } catch (RuntimeException ignored) {
            if (!silentHierarchyAttempt) {
                throw ignored;
            }
        }
    }

    private void syncSingleRoleMembership(Guild guild, Member member, Role role, boolean shouldHaveRole, String context) {
        Member selfMember = guild.getSelfMember();
        if (!selfMember.hasPermission(Permission.MANAGE_ROLES)) {
            log.warn("skipping role sync " + context + " reason=missing Manage Roles permission");
            return;
        }
        if (!selfMember.canInteract(role)) {
            log.warn("skipping role sync " + context + " reason=role has higher or equal hierarchy roleId=" + role.getId());
            return;
        }
        boolean silentHierarchyAttempt = !selfMember.canInteract(member);
        boolean hasRole = member.getRoles().contains(role);
        if (shouldHaveRole) {
            if (hasRole) {
                log.debug("role already aligned " + context + " roleId=" + role.getId());
                return;
            }
            log.info("adding discord role " + context + " roleId=" + role.getIdLong());
            try {
                guild.addRoleToMember(member, role).queue(
                        ignored -> { },
                        failure -> {
                            if (!silentHierarchyAttempt) {
                                logDiscordFailure("add role " + context, failure);
                            }
                        }
                );
            } catch (RuntimeException ignored) {
                if (!silentHierarchyAttempt) {
                    throw ignored;
                }
            }
            return;
        }
        if (!hasRole) {
            log.debug("role removal not needed " + context + " roleId=" + role.getId());
            return;
        }
        log.info("removing discord role " + context + " roleId=" + role.getIdLong());
        try {
            guild.removeRoleFromMember(member, role).queue(
                    ignored -> { },
                    failure -> {
                        if (!silentHierarchyAttempt) {
                            logDiscordFailure("remove role " + context, failure);
                        }
                    }
            );
        } catch (RuntimeException ignored) {
            if (!silentHierarchyAttempt) {
                throw ignored;
            }
        }
    }

    private void applyLinkedUserCleanup(long discordUserId) {
        Optional<Guild> guildOptional = botService.guild();
        if (guildOptional.isEmpty()) {
            log.debug("cleanup skipped because guild is not ready discordUserId=" + Long.toUnsignedString(discordUserId));
            return;
        }
        Guild guild = guildOptional.get();
        guild.retrieveMemberById(discordUserId).queue(
                member -> {
                    if (settings.verifiedRoleConfigured()) {
                        Role verifiedRole = guild.getRoleById(settings.verifiedRoleId());
                        if (verifiedRole != null) {
                            syncSingleRoleMembership(guild, member, verifiedRole, false,
                                    "cleanup verified-role memberId=" + discordUserId);
                        }
                    }
                    for (DiscordLinkRepository.TownDiscordBinding binding : repository.allTownBindings(guild.getIdLong())) {
                        if (binding.roleId() == null) {
                            continue;
                        }
                        Role role = guild.getRoleById(binding.roleId());
                        if (role == null) {
                            continue;
                        }
                        syncSingleRoleMembership(guild, member, role, false,
                                "cleanup town=" + binding.townId() + " memberId=" + discordUserId);
                    }
                },
                failure -> logDiscordFailure("cleanup retrieve memberId=" + discordUserId, failure)
        );
    }

    private void syncRoleColor(TownSyncSnapshot snapshot, Role role) {
        Color desiredColor = parseDiscordColor(snapshot.townColorHex());
        int desiredRaw = desiredColor.getRGB() & 0xFFFFFF;
        int currentRaw = role.getColorRaw() & 0xFFFFFF;
        if (currentRaw == desiredRaw) {
            log.debug("discord role color already aligned town=" + snapshot.townId()
                    + " roleId=" + role.getId() + " color=" + snapshot.townColorHex());
            return;
        }
        log.info("updating discord role color town=" + snapshot.townId()
                + " roleId=" + role.getId()
                + " old=#" + String.format(Locale.ROOT, "%06x", currentRaw)
                + " new=" + snapshot.townColorHex());
        role.getManager().setColor(desiredColor).queue(
                ignored -> { },
                failure -> logDiscordFailure("set role color town=" + snapshot.townId(), failure)
        );
    }

    private void syncChannelState(
            TownSyncSnapshot snapshot,
            TextChannel channel,
            Category desiredCategory,
            String desiredChannelName,
            String desiredTopic
    ) {
        boolean renameNeeded = !channel.getName().equals(desiredChannelName);
        String currentTopic = normalizeTopic(channel.getTopic());
        boolean topicUpdateNeeded = !currentTopic.equals(desiredTopic);
        boolean categoryMoveNeeded = !desiredCategory.equals(channel.getParentCategory());

        if (!renameNeeded && !topicUpdateNeeded && !categoryMoveNeeded) {
            log.debug("discord channel already aligned town=" + snapshot.townId()
                    + " channelId=" + channel.getId()
                    + " name=" + channel.getName());
            return;
        }

        if (shouldDeferChannelMetadataUpdate(snapshot.townId(), channel.getIdLong())) {
            return;
        }

        if (renameNeeded) {
            log.info("renaming discord channel town=" + snapshot.townId()
                    + " old=" + channel.getName() + " new=" + desiredChannelName);
        } else {
            log.debug("reusing discord channel town=" + snapshot.townId() + " channelId=" + channel.getId());
        }
        if (categoryMoveNeeded) {
            log.info("moving discord channel into configured category town=" + snapshot.townId()
                    + " channelId=" + channel.getId() + " categoryId=" + desiredCategory.getId());
        }
        if (topicUpdateNeeded) {
            log.info("updating discord channel topic town=" + snapshot.townId()
                    + " channelId=" + channel.getId()
                    + " topicLength=" + desiredTopic.length());
        } else {
            log.debug("discord channel topic already aligned town=" + snapshot.townId()
                    + " channelId=" + channel.getId());
        }

        channel.getManager()
                .setName(desiredChannelName)
                .setParent(desiredCategory)
                .setTopic(desiredTopic.isBlank() ? null : desiredTopic)
                .queue(
                ignored -> { },
                failure -> logDiscordFailure("update channel town=" + snapshot.townId(), failure)
        );
    }

    private void sendTownChannelBootstrap(TownSyncSnapshot snapshot, TextChannel channel) {
        String greeting = snapshot.townGreeting();
        String message = (greeting == null || greeting.isBlank())
                ? "✦ **" + snapshot.townName() + "** ha aperto il suo salone cittadino. Il vessillo e ora alzato."
                : "✦ **" + snapshot.townName() + "** ha aperto il suo salone cittadino.\n" + formatGreetingMessage(snapshot, "Nuovi cittadini");
        log.info("sending town bootstrap message town=" + snapshot.townId()
                + " channelId=" + channel.getId());
        channel.sendMessage(message).queue(
                ignored -> { },
                failure -> logDiscordFailure("send bootstrap message town=" + snapshot.townId(), failure)
        );
    }

    private RestAction<TextChannel> createTownChannel(Category category, String desiredChannelName, String desiredTopic) {
        return category.createTextChannel(desiredChannelName)
                .setTopic(desiredTopic.isBlank() ? null : desiredTopic);
    }

    private void configureChannelPermissions(Guild guild, TextChannel channel, Role role) {
        channel.upsertPermissionOverride(guild.getPublicRole())
                .deny(Permission.VIEW_CHANNEL)
                .queue(null, IGNORE_NOT_FOUND);
        channel.upsertPermissionOverride(role)
                .grant(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY)
                .queue(null, IGNORE_NOT_FOUND);
        for (Long staffRoleId : settings.staffRoleIds()) {
            Role staffRole = guild.getRoleById(staffRoleId);
            if (staffRole == null) {
                continue;
            }
            channel.upsertPermissionOverride(staffRole)
                    .grant(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY)
                    .queue(null, IGNORE_NOT_FOUND);
        }
    }

    private Optional<Town> findTownOfMember(UUID playerId) {
        return huskTownsApiHook.getPlayableTowns().stream()
                .filter(town -> town.getMembers().containsKey(playerId))
                .findFirst();
    }

    private String resolveMinecraftName(UUID playerId) {
        var online = Bukkit.getPlayer(playerId);
        if (online != null) {
            return online.getName();
        }
        var offline = Bukkit.getOfflinePlayer(playerId);
        if (offline.getName() != null && !offline.getName().isBlank()) {
            return offline.getName();
        }
        return playerId.toString().substring(0, 8);
    }

    private Role resolveRole(Guild guild, DiscordLinkRepository.TownDiscordBinding binding, String desiredRoleName) {
        Role role = binding == null || binding.roleId() == null ? null : guild.getRoleById(binding.roleId());
        if (role != null) {
            return role;
        }
        return guild.getRolesByName(desiredRoleName, true).stream().findFirst().orElse(null);
    }

    private TextChannel resolveChannel(Guild guild, Category category, DiscordLinkRepository.TownDiscordBinding binding, String desiredChannelName) {
        TextChannel channel = binding == null || binding.textChannelId() == null ? null : guild.getTextChannelById(binding.textChannelId());
        if (channel != null) {
            return channel;
        }
        return category.getTextChannels().stream()
                .filter(textChannel -> textChannel.getName().equalsIgnoreCase(desiredChannelName))
                .findFirst()
                .orElse(null);
    }

    private void sendGreetingMessage(TownSyncSnapshot snapshot, String playerName) {
        String greeting = snapshot.townGreeting();
        if (greeting == null || greeting.isBlank()) {
            log.debug("skipping greeting message because greeting is blank town=" + snapshot.townId());
            return;
        }
        Optional<Guild> guildOptional = botService.guild();
        if (guildOptional.isEmpty()) {
            log.debug("skipping greeting message because guild is unavailable town=" + snapshot.townId());
            return;
        }
        Guild guild = guildOptional.get();
        Category category = settings.categoryConfigured() ? guild.getCategoryById(settings.categoryId()) : null;
        DiscordLinkRepository.TownDiscordBinding binding = repository.findTownBinding(snapshot.townId(), guild.getIdLong()).orElse(null);
        TextChannel channel = category == null ? null : resolveChannel(guild, category, binding, renderChannelName(snapshot));
        if (channel == null) {
            log.debug("skipping greeting message because channel is unavailable town=" + snapshot.townId());
            return;
        }
        String message = formatGreetingMessage(snapshot, playerName);
        log.info("sending town greeting message town=" + snapshot.townId()
                + " channelId=" + channel.getId()
                + " player=" + playerName);
        channel.sendMessage(message).queue(
                ignored -> { },
                failure -> logDiscordFailure("send greeting message town=" + snapshot.townId(), failure)
        );
    }

    private String renderRoleName(TownSyncSnapshot snapshot) {
        return settings.roleNameTemplate()
                .replace("{townName}", snapshot.townName())
                .replace("{townTag}", snapshot.townTag())
                .replace("{townSlug}", sanitizeChannelName(snapshot.townName()));
    }

    private String renderChannelName(TownSyncSnapshot snapshot) {
        return sanitizeChannelName(settings.channelNameTemplate()
                .replace("{townName}", snapshot.townName())
                .replace("{townTag}", snapshot.townTag())
                .replace("{townSlug}", sanitizeChannelName(snapshot.townName())));
    }

    private void queueDiscordWork(Runnable runnable) {
        if (stopping || botService.isShuttingDown() || discordExecutor.isShutdown()) {
            log.debug("ignoring discord work enqueue because shutdown is in progress");
            return;
        }
        discordExecutor.execute(runnable);
    }

    private void stopTasksOnly() {
        if (reconcileTaskId != -1) {
            Bukkit.getScheduler().cancelTask(reconcileTaskId);
            reconcileTaskId = -1;
        }
        BukkitTask fullTask = pendingFullReconcileTask;
        if (fullTask != null) {
            fullTask.cancel();
            pendingFullReconcileTask = null;
        }
        for (BukkitTask task : pendingTownTasks.values()) {
            task.cancel();
        }
        pendingTownTasks.clear();
        for (BukkitTask task : pendingMetadataRetryTasks.values()) {
            task.cancel();
        }
        pendingMetadataRetryTasks.clear();
    }

    private boolean shouldSkipTownSnapshot(TownSyncSnapshot snapshot, boolean fromFullReconcile) {
        TownSyncSnapshot previousSnapshot = lastQueuedTownSnapshots.get(snapshot.townId());
        if (previousSnapshot == null || !previousSnapshot.equals(snapshot)) {
            return false;
        }
        long lastQueuedAt = lastQueuedTownSyncAt.getOrDefault(snapshot.townId(), 0L);
        long cooldown = fromFullReconcile ? STABLE_RECONCILE_TOWN_SYNC_COOLDOWN_MS : STABLE_EVENT_TOWN_SYNC_COOLDOWN_MS;
        return System.currentTimeMillis() - lastQueuedAt < cooldown;
    }

    private void markTownSnapshotQueued(TownSyncSnapshot snapshot) {
        lastQueuedTownSnapshots.put(snapshot.townId(), snapshot);
        lastQueuedTownSyncAt.put(snapshot.townId(), System.currentTimeMillis());
    }

    private boolean shouldRunGlobalIdentitySync() {
        int currentIdentityHash = computeLinkedIdentityHash();
        long now = System.currentTimeMillis();
        if (currentIdentityHash != lastLinkedIdentityHash) {
            return true;
        }
        return now - lastLinkedIdentitySyncAt >= STABLE_GLOBAL_IDENTITY_SYNC_COOLDOWN_MS;
    }

    private void markGlobalIdentitySyncQueued() {
        this.lastLinkedIdentityHash = computeLinkedIdentityHash();
        this.lastLinkedIdentitySyncAt = System.currentTimeMillis();
    }

    private boolean shouldDeferChannelMetadataUpdate(int townId, long channelId) {
        long now = System.currentTimeMillis();
        long cooldownUntil = channelMetadataCooldownUntil.getOrDefault(channelId, 0L);
        if (cooldownUntil > now) {
            long remainingMs = cooldownUntil - now;
            log.warn("deferring discord channel metadata sync town=" + townId
                    + " channelId=" + channelId
                    + " remainingMs=" + remainingMs);
            notifyDeferredTownEditor(townId, remainingMs);
            scheduleMetadataRetry(townId, remainingMs);
            return true;
        }

        ArrayDeque<Long> attempts = channelMetadataAttemptTimes.computeIfAbsent(channelId, ignored -> new ArrayDeque<>());
        while (!attempts.isEmpty() && now - attempts.peekFirst() > CHANNEL_METADATA_WINDOW_MS) {
            attempts.removeFirst();
        }
        attempts.addLast(now);
        if (attempts.size() < CHANNEL_METADATA_BURST_LIMIT) {
            return false;
        }

        channelMetadataCooldownUntil.put(channelId, now + CHANNEL_METADATA_COOLDOWN_MS);
        attempts.clear();
        log.warn("channel metadata burst detected; deferring discord sync town=" + townId
                + " channelId=" + channelId
                + " cooldownMs=" + CHANNEL_METADATA_COOLDOWN_MS);
        notifyDeferredTownEditor(townId, CHANNEL_METADATA_COOLDOWN_MS);
        scheduleMetadataRetry(townId, CHANNEL_METADATA_COOLDOWN_MS);
        return true;
    }

    private void scheduleMetadataRetry(int townId, long delayMs) {
        long delayTicks = Math.max(20L, (delayMs / 50L) + 20L);
        BukkitTask previous = pendingMetadataRetryTasks.remove(townId);
        if (previous != null) {
            previous.cancel();
        }
        BukkitTask retryTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingMetadataRetryTasks.remove(townId);
            huskTownsApiHook.getTownById(townId).ifPresent(this::syncTown);
        }, delayTicks);
        pendingMetadataRetryTasks.put(townId, retryTask);
        log.debug("scheduled deferred discord metadata retry town=" + townId + " delayTicks=" + delayTicks);
    }

    private void notifyDeferredTownEditor(int townId, long remainingMs) {
        UUID editorId = pendingTownEditors.remove(townId);
        if (editorId == null) {
            return;
        }
        var player = Bukkit.getPlayer(editorId);
        if (player == null || !player.isOnline()) {
            return;
        }
        long minutes = Math.max(1L, TimeUnit.MILLISECONDS.toMinutes(Math.max(remainingMs, 1L)));
        player.sendMessage(configUtils.msg(
                "city.discord.sync_deferred",
                "<yellow>Discord e sotto pressione.</yellow> <gray>Le modifiche della citta verranno sincronizzate piu tardi.</gray> <white>Attesa stimata:</white> <gold>{minutes} min</gold>",
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("minutes", Long.toString(minutes))
        ));
    }

    private int computeLinkedIdentityHash() {
        int hash = 1;
        java.util.List<DiscordLinkRepository.PlayerDiscordLink> links = linkService.allLinks().stream()
                .sorted(java.util.Comparator
                        .comparing((DiscordLinkRepository.PlayerDiscordLink link) -> link.minecraftUuid().toString())
                        .thenComparingLong(DiscordLinkRepository.PlayerDiscordLink::discordUserId))
                .toList();
        for (DiscordLinkRepository.PlayerDiscordLink link : links) {
            hash = 31 * hash + link.minecraftUuid().hashCode();
            hash = 31 * hash + Long.hashCode(link.discordUserId());
            hash = 31 * hash + resolveMinecraftName(link.minecraftUuid()).hashCode();
        }
        return hash;
    }

    private int computeFullReconcileHash(java.util.List<TownSyncSnapshot> snapshots, Set<Integer> liveTownIds) {
        int hash = 1;
        java.util.List<TownSyncSnapshot> orderedSnapshots = snapshots.stream()
                .sorted(java.util.Comparator.comparingInt(TownSyncSnapshot::townId))
                .toList();
        for (TownSyncSnapshot snapshot : orderedSnapshots) {
            hash = 31 * hash + snapshot.hashCode();
        }
        java.util.List<Integer> orderedTownIds = liveTownIds.stream().sorted().toList();
        for (Integer townId : orderedTownIds) {
            hash = 31 * hash + townId.hashCode();
        }
        return hash;
    }

    private void logDiscordFailure(String context, Throwable failure) {
        if (failure instanceof CancellationException
                || (failure != null && failure.getMessage() != null
                && failure.getMessage().toLowerCase(Locale.ROOT).contains("cancelled"))) {
            if (stopping || botService.isShuttingDown()) {
                log.debug(context + " cancelled during shutdown");
                return;
            }
        }
        log.warn(context + " failed", failure);
    }

    private static String normalizeTopic(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String normalized = raw.replace("\r", "")
                .replaceAll("\\s*\\n\\s*", " • ")
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.length() > 1024) {
            normalized = normalized.substring(0, 1024).trim();
        }
        return normalized;
    }

    private static String buildTownTopic(TownSyncSnapshot snapshot) {
        String bio = clamp(snapshot.townBio(), TOPIC_BIO_RESERVED);
        String header = String.join(" • ",
                "✦ " + snapshot.townTag(),
                "🏰 " + snapshot.townName(),
                "👑 " + snapshot.mayorName(),
                "📜 " + snapshot.foundedLabel(),
                "⬆ Lv " + snapshot.level(),
                "🛡 " + snapshot.stageLabel(),
                "👥 " + snapshot.memberCount() + "/" + snapshot.memberCap(),
                "⛳ " + snapshot.claimCount() + "/" + snapshot.claimCap(),
                "💰 " + snapshot.moneyLabel(),
                "📍 " + snapshot.warpLabel(),
                "🎨 " + snapshot.townColorHex()
        );
        if (bio.isBlank()) {
            return clamp(header, TOPIC_MAX_LENGTH);
        }
        String full = header + " | 📝 " + bio;
        return clamp(full, TOPIC_MAX_LENGTH);
    }

    private static String normalizeTownHex(String rawHex) {
        TextColor parsed = TextColor.fromHexString(rawHex);
        if (parsed == null) {
            return "#ffffff";
        }
        return String.format(Locale.ROOT, "#%02x%02x%02x", parsed.red(), parsed.green(), parsed.blue());
    }

    private static Color parseDiscordColor(String rawHex) {
        TextColor parsed = TextColor.fromHexString(rawHex);
        if (parsed == null) {
            return new Color(255, 255, 255);
        }
        return new Color(parsed.red(), parsed.green(), parsed.blue());
    }

    private static String formatGreetingMessage(TownSyncSnapshot snapshot, String playerName) {
        String renderedGreeting = snapshot.townGreeting()
                .replace("{player}", playerName)
                .replace("{town}", snapshot.townName())
                .trim();
        return "**" + playerName + "** entra sotto il vessillo di **" + snapshot.townName() + "**.\n" + renderedGreeting;
    }

    private static String normalizeMoney(java.math.BigDecimal money) {
        if (money == null) {
            return "0";
        }
        return money.stripTrailingZeros().toPlainString();
    }

    private static String warpLabel(Town town) {
        if (town.getSpawn().isEmpty()) {
            return "Assente";
        }
        return town.getSpawn().get().isPublic() ? "Pubblico" : "Privato";
    }

    private static String clamp(String raw, int max) {
        if (raw == null) {
            return "";
        }
        String normalized = raw.trim();
        if (normalized.length() <= max) {
            return normalized;
        }
        if (max <= 1) {
            return normalized.substring(0, Math.max(0, max));
        }
        return normalized.substring(0, max - 1).trim() + "…";
    }

    private static String sanitizeChannelName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "citta-senza-nome";
        }
        String normalized = raw.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "")
                .replaceAll("-{2,}", "-");
        if (normalized.isBlank()) {
            normalized = "citta";
        }
        return normalized.length() > 90 ? normalized.substring(0, 90) : normalized;
    }

    private record TownSyncSnapshot(
            int townId,
            String townName,
            String townTag,
            String townColorHex,
            String townBio,
            String townGreeting,
            int level,
            String stageLabel,
            int memberCount,
            int memberCap,
            int claimCount,
            int claimCap,
            String moneyLabel,
            String foundedLabel,
            String mayorName,
            String warpLabel,
            boolean eligible,
            Set<UUID> memberIds
    ) {
    }
}
