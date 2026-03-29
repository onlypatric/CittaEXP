package it.patric.cittaexp.challenges;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import it.patric.cittaexp.economy.EconomyValueService;
import it.patric.cittaexp.integration.husktowns.HuskTownsApiHook;
import it.patric.cittaexp.levels.CityLevelService;
import it.patric.cittaexp.permissions.TownMemberPermission;
import it.patric.cittaexp.permissions.TownMemberPermissionService;
import it.patric.cittaexp.utils.DialogInputUtils;
import it.patric.cittaexp.utils.DialogViewUtils;
import it.patric.cittaexp.utils.PluginConfigUtils;
import it.patric.cittaexp.vault.CityItemVaultService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.william278.husktowns.town.Town;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class StaffEventService {

    public record CreateResult(boolean success, String reason, StaffEvent event) {}

    public record TransitionResult(boolean success, String reason, StaffEvent event) {}

    public record SubmitResult(boolean success, String reason, StaffEventSubmission submission) {}

    public record ManualRewardSpec(
            long xpScaled,
            double money,
            Map<Material, Integer> vaultItems,
            List<String> commands,
            String broadcast
    ) {}

    public record ManualRewardResult(boolean success, String reason, StaffEventSubmission submission) {}

    public record EventCardView(
            StaffEvent event,
            StaffEventSubmission submission,
            String linkedInstanceId,
            Optional<ChallengeSnapshot> linkedSnapshot
    ) {}

    private static final DateTimeFormatter PLAYER_TIME = DateTimeFormatter.ofPattern("dd/MM HH:mm", Locale.ROOT);
    private static final DateTimeFormatter DIALOG_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT);
    private static final String PAYLOAD_PRESET_KEY = "preset_key";
    private static final String PAYLOAD_TEMPLATE_MODE = "template_mode";
    private static final String PAYLOAD_TEMPLATE_CHALLENGE = "template_challenge_id";
    private static final ClickCallback.Options CLICK_OPTIONS = ClickCallback.Options.builder()
            .uses(1)
            .lifetime(java.time.Duration.ofMinutes(2))
            .build();

    private final Plugin plugin;
    private final HuskTownsApiHook huskTownsApiHook;
    private final CityChallengeService challengeService;
    private final CityLevelService cityLevelService;
    private final CityItemVaultService cityItemVaultService;
    private final EconomyValueService economyValueService;
    private final TownMemberPermissionService permissionService;
    private final PluginConfigUtils configUtils;
    private final StaffEventRepository repository;
    private final ScheduledExecutorService scheduler;
    private final Map<String, StaffEvent> eventsById;
    private final Map<String, Map<Integer, StaffEventSubmission>> submissionsByEventTown;

    public StaffEventService(
            Plugin plugin,
            HuskTownsApiHook huskTownsApiHook,
            CityChallengeService challengeService,
            CityLevelService cityLevelService,
            CityItemVaultService cityItemVaultService,
            EconomyValueService economyValueService,
            TownMemberPermissionService permissionService,
            StaffEventRepository repository
    ) {
        this.plugin = plugin;
        this.huskTownsApiHook = huskTownsApiHook;
        this.challengeService = challengeService;
        this.cityLevelService = cityLevelService;
        this.cityItemVaultService = cityItemVaultService;
        this.economyValueService = economyValueService;
        this.permissionService = permissionService;
        this.configUtils = new PluginConfigUtils(plugin);
        this.repository = repository;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "cittaexp-staff-events");
            thread.setDaemon(true);
            return thread;
        });
        this.eventsById = new ConcurrentHashMap<>();
        this.submissionsByEventTown = new ConcurrentHashMap<>();
    }

    public void start() {
        repository.initialize();
        eventsById.clear();
        eventsById.putAll(repository.loadEvents());
        submissionsByEventTown.clear();
        for (StaffEventSubmission submission : repository.loadSubmissions()) {
            submissionsByEventTown
                    .computeIfAbsent(submission.eventId(), ignored -> new ConcurrentHashMap<>())
                    .put(submission.townId(), submission);
        }
        scheduler.scheduleAtFixedRate(this::tickSafe, 15L, 15L, TimeUnit.SECONDS);
    }

    public void stop() {
        scheduler.shutdownNow();
    }

    public List<StaffEvent> listAll() {
        return eventsById.values().stream()
                .sorted(Comparator.comparing(StaffEvent::createdAt).reversed())
                .toList();
    }

    public List<StaffEvent> listByStatus(StaffEventStatus status) {
        return eventsById.values().stream()
                .filter(event -> event.status() == status)
                .sorted(Comparator.comparing(StaffEvent::startAt).thenComparing(StaffEvent::eventId))
                .toList();
    }

    public Optional<StaffEvent> findEvent(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(eventsById.get(eventId));
    }

    public List<StaffEventSubmission> listSubmissions(String eventId) {
        return submissionsByEventTown.getOrDefault(eventId, Map.of()).values().stream()
                .sorted(Comparator.comparing(StaffEventSubmission::submittedAt))
                .toList();
    }

    public Set<String> eventIds() {
        return Set.copyOf(eventsById.keySet());
    }

    public Optional<StaffEventSubmission> findSubmission(String eventId, int townId) {
        return Optional.ofNullable(submissionsByEventTown.getOrDefault(eventId, Map.of()).get(townId));
    }

    public CreateResult createAutoRaceDraft(
            UUID actorId,
            String title,
            String subtitle,
            String description,
            Instant startAt,
            Instant endAt,
            ChallengeMode templateMode,
            String templateChallengeId,
            Map<String, String> payloadExtras
    ) {
        if (actorId == null) {
            return new CreateResult(false, "actor-required", null);
        }
        if (templateMode == null || !templateMode.race()) {
            return new CreateResult(false, "invalid-template-mode", null);
        }
        if (title == null || title.isBlank() || startAt == null || endAt == null || !endAt.isAfter(startAt)) {
            return new CreateResult(false, "invalid-window", null);
        }
        Instant now = Instant.now();
        String eventId = buildEventId("auto", title, now);
        StaffEvent event = new StaffEvent(
                eventId,
                StaffEventKind.AUTO_RACE,
                StaffEventStatus.DRAFT,
                title.trim(),
                blankToNull(subtitle),
                blankToNull(description),
                startAt,
                endAt,
                actorId,
                null,
                null,
                false,
                null,
                encodePayload(autoRacePayload(templateMode, templateChallengeId, payloadExtras)),
                now,
                now,
                null,
                null
        );
        persistEvent(event);
        return new CreateResult(true, "created", event);
    }

    public CreateResult createJudgedBuildDraft(
            UUID actorId,
            String title,
            String subtitle,
            String description,
            Instant startAt,
            Instant endAt,
            Map<String, String> payloadExtras
    ) {
        if (actorId == null) {
            return new CreateResult(false, "actor-required", null);
        }
        if (title == null || title.isBlank() || startAt == null || endAt == null || !endAt.isAfter(startAt)) {
            return new CreateResult(false, "invalid-window", null);
        }
        Instant now = Instant.now();
        String eventId = buildEventId("build", title, now);
        StaffEvent event = new StaffEvent(
                eventId,
                StaffEventKind.JUDGED_BUILD,
                StaffEventStatus.DRAFT,
                title.trim(),
                blankToNull(subtitle),
                blankToNull(description),
                startAt,
                endAt,
                actorId,
                null,
                null,
                false,
                null,
                encodePayload(payloadExtras),
                now,
                now,
                null,
                null
        );
        persistEvent(event);
        return new CreateResult(true, "created", event);
    }

    public TransitionResult publish(String eventId, UUID actorId) {
        StaffEvent event = eventsById.get(eventId);
        if (event == null) {
            return new TransitionResult(false, "not-found", null);
        }
        if (event.status() != StaffEventStatus.DRAFT) {
            return new TransitionResult(false, "invalid-status", event);
        }
        Instant now = Instant.now();
        StaffEvent updated = copy(event,
                StaffEventStatus.PUBLISHED,
                actorId,
                event.closedBy(),
                true,
                event.linkedChallengeInstanceId(),
                now,
                now,
                event.completedAt());
        persistEvent(updated);
        if (!updated.startAt().isAfter(now)) {
            activateEvent(updated);
            updated = eventsById.get(updated.eventId());
        }
        return new TransitionResult(true, "published", updated);
    }

    public TransitionResult cancel(String eventId, UUID actorId) {
        StaffEvent event = eventsById.get(eventId);
        if (event == null) {
            return new TransitionResult(false, "not-found", null);
        }
        if (event.status() == StaffEventStatus.COMPLETED || event.status() == StaffEventStatus.ARCHIVED) {
            return new TransitionResult(false, "invalid-status", event);
        }
        if (event.linkedChallengeInstanceId() != null && event.status() == StaffEventStatus.ACTIVE) {
            challengeService.forceExpireInstance(event.linkedChallengeInstanceId());
        }
        Instant now = Instant.now();
        StaffEvent updated = copy(event,
                StaffEventStatus.CANCELED,
                event.publishedBy(),
                actorId,
                false,
                event.linkedChallengeInstanceId(),
                event.publishedAt(),
                now,
                now);
        persistEvent(updated);
        return new TransitionResult(true, "canceled", updated);
    }

    public TransitionResult archive(String eventId, UUID actorId) {
        StaffEvent event = eventsById.get(eventId);
        if (event == null) {
            return new TransitionResult(false, "not-found", null);
        }
        if (event.status() != StaffEventStatus.COMPLETED && event.status() != StaffEventStatus.CANCELED) {
            return new TransitionResult(false, "invalid-status", event);
        }
        Instant now = Instant.now();
        StaffEvent updated = copy(event,
                StaffEventStatus.ARCHIVED,
                event.publishedBy(),
                actorId,
                false,
                event.linkedChallengeInstanceId(),
                event.publishedAt(),
                now,
                event.completedAt());
        persistEvent(updated);
        return new TransitionResult(true, "archived", updated);
    }

    public TransitionResult forceStart(String eventId, UUID actorId) {
        StaffEvent event = eventsById.get(eventId);
        if (event == null) {
            return new TransitionResult(false, "not-found", null);
        }
        if (event.status() != StaffEventStatus.PUBLISHED) {
            return new TransitionResult(false, "invalid-status", event);
        }
        activateEvent(event);
        return new TransitionResult(true, "started", eventsById.get(eventId));
    }

    public TransitionResult forceClose(String eventId, UUID actorId) {
        StaffEvent event = eventsById.get(eventId);
        if (event == null) {
            return new TransitionResult(false, "not-found", null);
        }
        if (event.status() != StaffEventStatus.ACTIVE && event.status() != StaffEventStatus.REVIEW) {
            return new TransitionResult(false, "invalid-status", event);
        }
        if (event.kind() == StaffEventKind.AUTO_RACE && event.linkedChallengeInstanceId() != null) {
            challengeService.forceExpireInstance(event.linkedChallengeInstanceId());
            StaffEvent updated = complete(event, actorId);
            persistEvent(updated);
            return new TransitionResult(true, "closed", updated);
        }
        StaffEvent updated = event.kind() == StaffEventKind.JUDGED_BUILD
                ? copy(event,
                StaffEventStatus.REVIEW,
                event.publishedBy(),
                actorId,
                true,
                event.linkedChallengeInstanceId(),
                event.publishedAt(),
                Instant.now(),
                event.completedAt())
                : complete(event, actorId);
        persistEvent(updated);
        return new TransitionResult(true, "closed", updated);
    }

    public TransitionResult publishResults(String eventId, UUID actorId) {
        StaffEvent event = eventsById.get(eventId);
        if (event == null) {
            return new TransitionResult(false, "not-found", null);
        }
        if (event.kind() != StaffEventKind.JUDGED_BUILD || event.status() != StaffEventStatus.REVIEW) {
            return new TransitionResult(false, "invalid-status", event);
        }
        StaffEvent updated = complete(event, actorId);
        persistEvent(updated);
        return new TransitionResult(true, "published-results", updated);
    }

    public SubmitResult submitBuild(Player player, int townId, String eventId, String note) {
        StaffEvent event = eventsById.get(eventId);
        if (player == null || townId <= 0 || event == null) {
            return new SubmitResult(false, "not-found", null);
        }
        if (event.kind() != StaffEventKind.JUDGED_BUILD || event.status() != StaffEventStatus.ACTIVE) {
            return new SubmitResult(false, "submission-closed", null);
        }
        Optional<Town> townOptional = huskTownsApiHook.getTownById(townId);
        if (townOptional.isEmpty()) {
            return new SubmitResult(false, "town-not-found", null);
        }
        UUID mayorId = townOptional.get().getMayor();
        if (!permissionService.isAllowed(townId, mayorId, player.getUniqueId(), TownMemberPermission.CITY_EVENTS_SUBMIT)) {
            return new SubmitResult(false, "no-permission", null);
        }
        Location location = player.getLocation();
        StaffEventSubmission submission = new StaffEventSubmission(
                eventId,
                townId,
                player.getUniqueId(),
                Instant.now(),
                location.getWorld() == null ? "world" : location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                blankToNull(note),
                StaffEventReviewStatus.PENDING,
                null,
                null,
                null,
                null,
                false,
                null
        );
        persistSubmission(submission);
        return new SubmitResult(true, "submitted", submission);
    }

    public SubmitResult reviewSubmission(
            String eventId,
            int townId,
            StaffEventReviewStatus status,
            UUID reviewerId,
            String staffNote
    ) {
        StaffEventSubmission current = submissionsByEventTown.getOrDefault(eventId, Map.of()).get(townId);
        if (current == null) {
            return new SubmitResult(false, "submission-not-found", null);
        }
        StaffEventSubmission updated = new StaffEventSubmission(
                current.eventId(),
                current.townId(),
                current.submittedBy(),
                current.submittedAt(),
                current.worldName(),
                current.x(),
                current.y(),
                current.z(),
                current.note(),
                status == null ? StaffEventReviewStatus.PENDING : status,
                reviewerId,
                Instant.now(),
                blankToNull(staffNote),
                current.placement(),
                current.rewardGranted(),
                current.rewardGrantedAt()
        );
        persistSubmission(updated);
        return new SubmitResult(true, "reviewed", updated);
    }

    public SubmitResult assignPlacement(String eventId, int townId, int placement, UUID reviewerId) {
        StaffEventSubmission current = submissionsByEventTown.getOrDefault(eventId, Map.of()).get(townId);
        if (current == null) {
            return new SubmitResult(false, "submission-not-found", null);
        }
        StaffEventSubmission updated = new StaffEventSubmission(
                current.eventId(),
                current.townId(),
                current.submittedBy(),
                current.submittedAt(),
                current.worldName(),
                current.x(),
                current.y(),
                current.z(),
                current.note(),
                current.reviewStatus(),
                reviewerId,
                Instant.now(),
                current.staffNote(),
                placement,
                current.rewardGranted(),
                current.rewardGrantedAt()
        );
        persistSubmission(updated);
        return new SubmitResult(true, "placed", updated);
    }

    public ManualRewardResult grantManualReward(String eventId, int townId, UUID actorId, ManualRewardSpec spec) {
        StaffEvent event = eventsById.get(eventId);
        if (event == null) {
            return new ManualRewardResult(false, "not-found", null);
        }
        StaffEventSubmission submission = submissionsByEventTown.getOrDefault(eventId, Map.of()).get(townId);
        if (submission == null) {
            return new ManualRewardResult(false, "submission-not-found", null);
        }
        if (spec == null) {
            return new ManualRewardResult(false, "invalid-spec", submission);
        }
        if (spec.xpScaled() > 0L) {
            cityLevelService.grantXpBonus(townId, spec.xpScaled(), actorId, "staff-event:" + eventId);
        }
        if (spec.money() > 0.0D) {
            try {
                huskTownsApiHook.editTown(actorId, townId, mutableTown ->
                        mutableTown.setMoney(mutableTown.getMoney().add(BigDecimal.valueOf(spec.money()))));
                economyValueService.recordRewardEmission(spec.money());
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("[staff-events] money reward failed event=" + eventId + " town=" + townId + " error=" + exception.getMessage());
                return new ManualRewardResult(false, "money-failed", submission);
            }
        }
        if (spec.vaultItems() != null && !spec.vaultItems().isEmpty()) {
            cityItemVaultService.depositRewardItems(townId, spec.vaultItems(), "staff-event:" + eventId);
        }
        if (spec.commands() != null) {
            for (String command : spec.commands()) {
                String rendered = renderCommand(command, townId, actorId);
                if (rendered != null && !rendered.isBlank()) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), rendered);
                }
            }
        }
        if (spec.broadcast() != null && !spec.broadcast().isBlank()) {
            Bukkit.broadcast(it.patric.cittaexp.text.MiniMessageHelper.parse(spec.broadcast().replace("{town}", townName(townId))));
        }
        StaffEventSubmission updated = new StaffEventSubmission(
                submission.eventId(),
                submission.townId(),
                submission.submittedBy(),
                submission.submittedAt(),
                submission.worldName(),
                submission.x(),
                submission.y(),
                submission.z(),
                submission.note(),
                submission.reviewStatus(),
                actorId,
                Instant.now(),
                submission.staffNote(),
                submission.placement(),
                true,
                Instant.now()
        );
        persistSubmission(updated);
        return new ManualRewardResult(true, "rewarded", updated);
    }

    public List<EventCardView> listVisibleEventCards(int townId, UUID playerId) {
        if (townId <= 0) {
            return List.of();
        }
        List<EventCardView> result = new ArrayList<>();
        for (StaffEvent event : eventsById.values()) {
            if (!event.visible()) {
                continue;
            }
            if (event.status() == StaffEventStatus.DRAFT || event.status() == StaffEventStatus.CANCELED || event.status() == StaffEventStatus.ARCHIVED) {
                continue;
            }
            StaffEventSubmission submission = submissionsByEventTown.getOrDefault(event.eventId(), Map.of()).get(townId);
            Optional<ChallengeSnapshot> linked = Optional.empty();
            if (event.linkedChallengeInstanceId() != null && playerId != null) {
                linked = challengeService.snapshotForTownPlayerByInstanceId(townId, playerId, event.linkedChallengeInstanceId());
            }
            result.add(new EventCardView(event, submission, event.linkedChallengeInstanceId(), linked));
        }
        result.sort(Comparator.comparing((EventCardView view) -> view.event().startAt()).reversed());
        return List.copyOf(result);
    }

    public String playerStatusLabel(StaffEvent event) {
        if (event == null) {
            return "-";
        }
        return switch (event.status()) {
            case PUBLISHED -> "Apre il " + PLAYER_TIME.withZone(timezone()).format(event.startAt());
            case ACTIVE -> event.kind() == StaffEventKind.JUDGED_BUILD ? "Submission aperte" : "Evento attivo";
            case REVIEW -> "In review";
            case COMPLETED -> "Concluso";
            case CANCELED -> "Annullato";
            case ARCHIVED -> "Archiviato";
            case DRAFT -> "Bozza";
        };
    }

    public ZoneId timezone() {
        return challengeService.settings().timezone();
    }

    private void handleCreateAutoDialog(Player player, DialogResponseView response) {
        String presetKey = normalizePresetKey(DialogInputUtils.normalized(response, "preset_key", configUtils));
        Optional<StaffEventPresetCatalog.StaffEventPreset> presetOptional = StaffEventPresetCatalog.findAutoRace(presetKey);
        if (presetKey != null && presetOptional.isEmpty()) {
            player.sendMessage(configUtils.msg("city.staff.events.errors.create_auto_failed",
                    "<red>Preset evento auto non trovato:</red> <white>{reason}</white>",
                    net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed(
                            "reason",
                            presetKey + " | disponibili: " + StaffEventPresetCatalog.autoRaceKeysLabel()
                    )));
            openCreateAutoDialog(player);
            return;
        }
        StaffEventPresetCatalog.StaffEventPreset preset = presetOptional.orElse(null);
        String title = firstNonBlank(DialogInputUtils.normalized(response, "title", configUtils), preset == null ? null : preset.title());
        String subtitle = firstNonBlank(DialogInputUtils.normalized(response, "subtitle", configUtils), preset == null ? null : preset.subtitle());
        String description = firstNonBlank(DialogInputUtils.normalized(response, "description", configUtils), preset == null ? null : preset.description());
        Instant startAt = parseDialogInstant(DialogInputUtils.normalized(response, "start_at", configUtils));
        Instant endAt = parseDialogInstant(DialogInputUtils.normalized(response, "end_at", configUtils));
        ChallengeMode mode = parseMode(DialogInputUtils.normalizedUpper(response, "template_mode", configUtils));
        if (mode == null && preset != null) {
            mode = preset.templateMode();
        }
        String challengeId = firstNonBlank(DialogInputUtils.normalized(response, "template_challenge_id", configUtils), preset == null ? null : preset.templateChallengeId());
        Map<String, String> payloadExtras = new LinkedHashMap<>();
        if (preset != null) {
            payloadExtras.put(PAYLOAD_PRESET_KEY, preset.key());
            payloadExtras.putAll(preset.payloadDefaults());
        }
        CreateResult result = createAutoRaceDraft(
                player.getUniqueId(),
                title,
                subtitle,
                description,
                startAt,
                endAt,
                mode,
                challengeId,
                payloadExtras
        );
        if (!result.success()) {
            player.sendMessage(configUtils.msg("city.staff.events.errors.create_auto_failed",
                    "<red>Creazione evento auto fallita: {reason}</red>",
                    net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("reason", result.reason())));
            openCreateAutoDialog(player);
            return;
        }
        player.sendMessage(configUtils.msg("city.staff.events.create_auto.success",
                "<green>Bozza evento auto creata:</green> <white>{id}</white>",
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("id", result.event().eventId())));
    }

    private void handleCreateJudgedDialog(Player player, DialogResponseView response) {
        String presetKey = normalizePresetKey(DialogInputUtils.normalized(response, "preset_key", configUtils));
        Optional<StaffEventPresetCatalog.StaffEventPreset> presetOptional = StaffEventPresetCatalog.findJudgedBuild(presetKey);
        if (presetKey != null && presetOptional.isEmpty()) {
            player.sendMessage(configUtils.msg("city.staff.events.errors.create_judged_failed",
                    "<red>Preset contest build non trovato:</red> <white>{reason}</white>",
                    net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed(
                            "reason",
                            presetKey + " | disponibili: " + StaffEventPresetCatalog.judgedBuildKeysLabel()
                    )));
            openCreateJudgedDialog(player);
            return;
        }
        StaffEventPresetCatalog.StaffEventPreset preset = presetOptional.orElse(null);
        String title = firstNonBlank(DialogInputUtils.normalized(response, "title", configUtils), preset == null ? null : preset.title());
        String subtitle = firstNonBlank(DialogInputUtils.normalized(response, "subtitle", configUtils), preset == null ? null : preset.subtitle());
        String description = firstNonBlank(DialogInputUtils.normalized(response, "description", configUtils), preset == null ? null : preset.description());
        Instant startAt = parseDialogInstant(DialogInputUtils.normalized(response, "start_at", configUtils));
        Instant endAt = parseDialogInstant(DialogInputUtils.normalized(response, "end_at", configUtils));
        Map<String, String> payloadExtras = new LinkedHashMap<>();
        if (preset != null) {
            payloadExtras.put(PAYLOAD_PRESET_KEY, preset.key());
            payloadExtras.putAll(preset.payloadDefaults());
        }
        CreateResult result = createJudgedBuildDraft(
                player.getUniqueId(),
                title,
                subtitle,
                description,
                startAt,
                endAt,
                payloadExtras
        );
        if (!result.success()) {
            player.sendMessage(configUtils.msg("city.staff.events.errors.create_judged_failed",
                    "<red>Creazione contest build fallita: {reason}</red>",
                    net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("reason", result.reason())));
            openCreateJudgedDialog(player);
            return;
        }
        player.sendMessage(configUtils.msg("city.staff.events.create_judged.success",
                "<green>Bozza contest build creata:</green> <white>{id}</white>",
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("id", result.event().eventId())));
    }

    private void openStaffNoteDialog(Player player, String eventId, int townId) {
        DialogBase base = DialogBase.builder(configUtils.msg("city.staff.events.note.title", "<gold>Nota staff</gold>"))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(List.of(DialogBody.plainMessage(configUtils.msg("city.staff.events.note.body", "<gray>Scrivi una nota staff per questa submission.</gray>"))))
                .inputs(List.of(DialogInputUtils.text("staff_note", 320, configUtils.msg("city.staff.events.note.label", "<yellow>Nota</yellow>"), false, "", 180)))
                .build();
        Dialog dialog = Dialog.create(factory -> {
            ActionButton confirm = ActionButton.create(configUtils.msg("city.staff.events.note.save", "<green>SALVA</green>"), null, 160,
                    DialogAction.customClick((response, audience) -> {
                        if (audience instanceof Player actor) {
                            String note = DialogInputUtils.normalized(response, "staff_note", configUtils);
                            reviewSubmission(eventId, townId, findSubmission(eventId, townId).map(StaffEventSubmission::reviewStatus).orElse(StaffEventReviewStatus.PENDING), actor.getUniqueId(), note);
                            actor.sendMessage(configUtils.msg("city.staff.events.note.saved", "<green>Nota staff salvata.</green>"));
                        }
                    }, CLICK_OPTIONS));
            ActionButton cancel = ActionButton.create(configUtils.msg("city.staff.events.dialog.cancel", "<red>ANNULLA</red>"), null, 160, null);
            factory.empty().base(base).type(DialogType.multiAction(List.of(confirm), cancel, 2));
        });
        DialogViewUtils.showDialog(plugin, player, dialog, configUtils.msg("city.staff.events.dialog.unavailable", "<red>Dialog non disponibile su questo server.</red>"), "staff-events-note");
    }

    private void openPlacementDialog(Player player, String eventId, int townId) {
        DialogBase base = DialogBase.builder(configUtils.msg("city.staff.events.placement.title", "<gold>Placement</gold>"))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(List.of(DialogBody.plainMessage(configUtils.msg("city.staff.events.placement.body", "<gray>Inserisci il placement finale di questa submission.</gray>"))))
                .inputs(List.of(DialogInputUtils.text("placement", 180, configUtils.msg("city.staff.events.placement.label", "<yellow>Placement</yellow>"), true, "", 8)))
                .build();
        Dialog dialog = Dialog.create(factory -> {
            ActionButton confirm = ActionButton.create(configUtils.msg("city.staff.events.placement.save", "<green>SALVA</green>"), null, 160,
                    DialogAction.customClick((response, audience) -> {
                        if (audience instanceof Player actor) {
                            Integer placement = parseInteger(DialogInputUtils.normalized(response, "placement", configUtils));
                            if (placement == null || placement <= 0) {
                                actor.sendMessage(configUtils.msg("city.staff.events.placement.invalid", "<red>Placement non valido.</red>"));
                                openPlacementDialog(actor, eventId, townId);
                                return;
                            }
                            assignPlacement(eventId, townId, placement, actor.getUniqueId());
                            actor.sendMessage(configUtils.msg("city.staff.events.placement.saved", "<green>Placement salvato.</green>"));
                        }
                    }, CLICK_OPTIONS));
            ActionButton cancel = ActionButton.create(configUtils.msg("city.staff.events.dialog.cancel", "<red>ANNULLA</red>"), null, 160, null);
            factory.empty().base(base).type(DialogType.multiAction(List.of(confirm), cancel, 2));
        });
        DialogViewUtils.showDialog(plugin, player, dialog, configUtils.msg("city.staff.events.dialog.unavailable", "<red>Dialog non disponibile su questo server.</red>"), "staff-events-placement");
    }

    private void handleRewardDialog(Player player, String eventId, int townId, DialogResponseView response) {
        Double xp = parseDouble(DialogInputUtils.normalized(response, "xp_city", configUtils));
        Double money = parseDouble(DialogInputUtils.normalized(response, "money", configUtils));
        Map<Material, Integer> items = parseVaultItems(DialogInputUtils.normalized(response, "items", configUtils));
        List<String> commands = parseCommands(DialogInputUtils.normalized(response, "commands", configUtils));
        String broadcast = DialogInputUtils.normalized(response, "broadcast", configUtils);
        ManualRewardResult result = grantManualReward(
                eventId,
                townId,
                player.getUniqueId(),
                new ManualRewardSpec(
                        xp == null ? 0L : cityLevelService.toScaledXp(xp),
                        money == null ? 0.0D : money,
                        items,
                        commands,
                        blankToNull(broadcast)
                )
        );
        if (!result.success()) {
            player.sendMessage(configUtils.msg("city.staff.events.reward.failed",
                    "<red>Assegnazione reward fallita: {reason}</red>",
                    net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("reason", result.reason())));
            openRewardDialog(player, eventId, townId);
            return;
        }
        player.sendMessage(configUtils.msg("city.staff.events.reward.success", "<green>Reward assegnata.</green>"));
    }

    public void openCreateAutoDialog(Player player) {
        if (player == null) {
            return;
        }
        DialogBase base = DialogBase.builder(configUtils.msg(
                        "city.staff.events.create.auto.title",
                        "<gold>Crea evento auto race</gold>"
                ))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(List.of(
                        DialogBody.plainMessage(configUtils.msg(
                                "city.staff.events.create.auto.body",
                                "<gray>Compila titolo, finestra e template della race.</gray><newline><gray>Preset disponibili:</gray> <white>"
                                        + StaffEventPresetCatalog.autoRaceKeysLabel()
                                        + "</white><newline><gray>Formato date:</gray> <white>yyyy-MM-dd HH:mm</white>"
                        ))
                ))
                .inputs(List.of(
                        DialogInputUtils.text("preset_key", 240, configUtils.msg("city.staff.events.create.preset", "<yellow>Preset (opzionale)</yellow>"), false, "", 48),
                        DialogInputUtils.text("title", 320, configUtils.msg("city.staff.events.create.title", "<yellow>Titolo</yellow>"), true, "", 64),
                        DialogInputUtils.text("subtitle", 320, configUtils.msg("city.staff.events.create.subtitle", "<yellow>Sottotitolo</yellow>"), false, "", 96),
                        DialogInputUtils.text("description", 320, configUtils.msg("city.staff.events.create.description", "<yellow>Descrizione</yellow>"), false, "", 180),
                        DialogInputUtils.text("start_at", 220, configUtils.msg("city.staff.events.create.start", "<yellow>Inizio</yellow>"), true, "", 32),
                        DialogInputUtils.text("end_at", 220, configUtils.msg("city.staff.events.create.end", "<yellow>Fine</yellow>"), true, "", 32),
                        DialogInputUtils.text("template_mode", 200, configUtils.msg("city.staff.events.create.auto.mode", "<yellow>Template mode</yellow>"), true, ChallengeMode.MONTHLY_CROWN.name(), 32),
                        DialogInputUtils.text("template_challenge_id", 280, configUtils.msg("city.staff.events.create.auto.challenge", "<yellow>Challenge id (opzionale)</yellow>"), false, "", 64)
                ))
                .build();
        Dialog dialog = Dialog.create(factory -> {
            ActionButton confirm = ActionButton.create(
                    configUtils.msg("city.staff.events.dialog.confirm", "<green>CREA</green>"),
                    null,
                    170,
                    DialogAction.customClick((response, audience) -> {
                        if (audience instanceof Player actor) {
                            handleCreateAutoDialog(actor, response);
                        }
                    }, CLICK_OPTIONS)
            );
            ActionButton cancel = ActionButton.create(
                    configUtils.msg("city.staff.events.dialog.cancel", "<red>ANNULLA</red>"),
                    null,
                    170,
                    null
            );
            factory.empty().base(base).type(DialogType.multiAction(List.of(confirm), cancel, 2));
        });
        DialogViewUtils.showDialog(plugin, player, dialog, configUtils.msg(
                "city.staff.events.dialog.unavailable",
                "<red>Dialog non disponibile su questo server.</red>"
        ), "staff-events-create-auto");
    }

    public void openCreateJudgedDialog(Player player) {
        if (player == null) {
            return;
        }
        DialogBase base = DialogBase.builder(configUtils.msg(
                        "city.staff.events.create.judged.title",
                        "<gold>Crea contest build</gold>"
                ))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(List.of(
                        DialogBody.plainMessage(configUtils.msg(
                                "city.staff.events.create.judged.body",
                                "<gray>Compila i dati del contest build.</gray><newline><gray>Preset disponibili:</gray> <white>"
                                        + StaffEventPresetCatalog.judgedBuildKeysLabel()
                                        + "</white><newline><gray>Formato date:</gray> <white>yyyy-MM-dd HH:mm</white>"
                        ))
                ))
                .inputs(List.of(
                        DialogInputUtils.text("preset_key", 240, configUtils.msg("city.staff.events.create.preset", "<yellow>Preset (opzionale)</yellow>"), false, "", 48),
                        DialogInputUtils.text("title", 320, configUtils.msg("city.staff.events.create.title", "<yellow>Titolo</yellow>"), true, "", 64),
                        DialogInputUtils.text("subtitle", 320, configUtils.msg("city.staff.events.create.subtitle", "<yellow>Sottotitolo</yellow>"), false, "", 96),
                        DialogInputUtils.text("description", 320, configUtils.msg("city.staff.events.create.description", "<yellow>Descrizione</yellow>"), false, "", 180),
                        DialogInputUtils.text("start_at", 220, configUtils.msg("city.staff.events.create.start", "<yellow>Apertura submission</yellow>"), true, "", 32),
                        DialogInputUtils.text("end_at", 220, configUtils.msg("city.staff.events.create.end", "<yellow>Chiusura submission</yellow>"), true, "", 32)
                ))
                .build();
        Dialog dialog = Dialog.create(factory -> {
            ActionButton confirm = ActionButton.create(
                    configUtils.msg("city.staff.events.dialog.confirm", "<green>CREA</green>"),
                    null,
                    170,
                    DialogAction.customClick((response, audience) -> {
                        if (audience instanceof Player actor) {
                            handleCreateJudgedDialog(actor, response);
                        }
                    }, CLICK_OPTIONS)
            );
            ActionButton cancel = ActionButton.create(
                    configUtils.msg("city.staff.events.dialog.cancel", "<red>ANNULLA</red>"),
                    null,
                    170,
                    null
            );
            factory.empty().base(base).type(DialogType.multiAction(List.of(confirm), cancel, 2));
        });
        DialogViewUtils.showDialog(plugin, player, dialog, configUtils.msg(
                "city.staff.events.dialog.unavailable",
                "<red>Dialog non disponibile su questo server.</red>"
        ), "staff-events-create-judged");
    }

    public void openReviewDialog(Player player, String eventId, int townId) {
        if (player == null) {
            return;
        }
        Optional<StaffEvent> eventOptional = findEvent(eventId);
        Optional<StaffEventSubmission> submissionOptional = findSubmission(eventId, townId);
        if (eventOptional.isEmpty() || submissionOptional.isEmpty()) {
            player.sendMessage(configUtils.msg("city.staff.events.errors.review_missing", "<red>Submission non trovata.</red>"));
            return;
        }
        StaffEvent event = eventOptional.get();
        StaffEventSubmission submission = submissionOptional.get();
        DialogBase base = DialogBase.builder(configUtils.msg(
                        "city.staff.events.review.title",
                        "<gold>Review evento</gold>"
                ))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(List.of(
                        DialogBody.plainMessage(configUtils.msg("city.staff.events.review.event", "<gray>Evento:</gray> <white>{value}</white>",
                                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("value", event.title()))),
                        DialogBody.plainMessage(configUtils.msg("city.staff.events.review.town", "<gray>Citta:</gray> <white>{value}</white>",
                                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("value", townName(townId)))),
                        DialogBody.plainMessage(configUtils.msg("city.staff.events.review.location", "<gray>Posizione:</gray> <white>{value}</white>",
                                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("value", submission.worldName() + " @ "
                                        + Math.round(submission.x()) + ", " + Math.round(submission.y()) + ", " + Math.round(submission.z())))),
                        DialogBody.plainMessage(configUtils.msg("city.staff.events.review.note", "<gray>Nota:</gray> <white>{value}</white>",
                                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("value", blankToEmpty(submission.note())))),
                        DialogBody.plainMessage(configUtils.msg("city.staff.events.review.status", "<gray>Review:</gray> <white>{value}</white>",
                                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("value", submission.reviewStatus().name())))
                ))
                .build();
        Dialog dialog = Dialog.create(factory -> {
            ActionButton accept = ActionButton.create(configUtils.msg("city.staff.events.review.accept", "<green>ACCETTA</green>"), null, 150,
                    DialogAction.customClick((response, audience) -> {
                        if (audience instanceof Player actor) {
                            reviewSubmission(eventId, townId, StaffEventReviewStatus.ACCEPTED, actor.getUniqueId(), submission.staffNote());
                            actor.sendMessage(configUtils.msg("city.staff.events.review.accepted", "<green>Submission accettata.</green>"));
                        }
                    }, CLICK_OPTIONS));
            ActionButton reject = ActionButton.create(configUtils.msg("city.staff.events.review.reject", "<red>RIFIUTA</red>"), null, 150,
                    DialogAction.customClick((response, audience) -> {
                        if (audience instanceof Player actor) {
                            reviewSubmission(eventId, townId, StaffEventReviewStatus.REJECTED, actor.getUniqueId(), submission.staffNote());
                            actor.sendMessage(configUtils.msg("city.staff.events.review.rejected", "<yellow>Submission rifiutata.</yellow>"));
                        }
                    }, CLICK_OPTIONS));
            ActionButton note = ActionButton.create(configUtils.msg("city.staff.events.review.note_button", "<yellow>NOTA</yellow>"), null, 150,
                    DialogAction.customClick((response, audience) -> {
                        if (audience instanceof Player actor) {
                            openStaffNoteDialog(actor, eventId, townId);
                        }
                    }, CLICK_OPTIONS));
            ActionButton placement = ActionButton.create(configUtils.msg("city.staff.events.review.placement_button", "<aqua>PLACEMENT</aqua>"), null, 150,
                    DialogAction.customClick((response, audience) -> {
                        if (audience instanceof Player actor) {
                            openPlacementDialog(actor, eventId, townId);
                        }
                    }, CLICK_OPTIONS));
            ActionButton close = ActionButton.create(configUtils.msg("city.staff.events.dialog.close", "<gray>CHIUDI</gray>"), null, 150, null);
            factory.empty().base(base).type(DialogType.multiAction(List.of(accept, reject, note, placement), close, 2));
        });
        DialogViewUtils.showDialog(plugin, player, dialog, configUtils.msg(
                "city.staff.events.dialog.unavailable",
                "<red>Dialog non disponibile su questo server.</red>"
        ), "staff-events-review");
    }

    public void openRewardDialog(Player player, String eventId, int townId) {
        if (player == null) {
            return;
        }
        Optional<StaffEventSubmission> submissionOptional = findSubmission(eventId, townId);
        if (submissionOptional.isEmpty()) {
            player.sendMessage(configUtils.msg("city.staff.events.errors.reward_missing", "<red>Submission non trovata.</red>"));
            return;
        }
        DialogBase base = DialogBase.builder(configUtils.msg(
                        "city.staff.events.reward.title",
                        "<gold>Reward manuale</gold>"
                ))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(List.of(
                        DialogBody.plainMessage(configUtils.msg("city.staff.events.reward.body",
                                "<gray>Compila le reward manuali.</gray><newline><gray>Items:</gray> <white>MATERIAL:amount,MATERIAL:amount</white><newline><gray>Commands:</gray> <white>cmd1||cmd2</white>"))
                ))
                .inputs(List.of(
                        DialogInputUtils.text("xp_city", 180, configUtils.msg("city.staff.events.reward.xp", "<yellow>XP citta</yellow>"), false, "", 16),
                        DialogInputUtils.text("money", 180, configUtils.msg("city.staff.events.reward.money", "<yellow>Monete citta</yellow>"), false, "", 16),
                        DialogInputUtils.text("items", 320, configUtils.msg("city.staff.events.reward.items", "<yellow>Vault items</yellow>"), false, "", 180),
                        DialogInputUtils.text("commands", 320, configUtils.msg("city.staff.events.reward.commands", "<yellow>Commands</yellow>"), false, "", 180),
                        DialogInputUtils.text("broadcast", 320, configUtils.msg("city.staff.events.reward.broadcast", "<yellow>Broadcast</yellow>"), false, "", 180)
                ))
                .build();
        Dialog dialog = Dialog.create(factory -> {
            ActionButton confirm = ActionButton.create(
                    configUtils.msg("city.staff.events.dialog.confirm", "<green>ASSEGNA</green>"),
                    null,
                    170,
                    DialogAction.customClick((response, audience) -> {
                        if (audience instanceof Player actor) {
                            handleRewardDialog(actor, eventId, townId, response);
                        }
                    }, CLICK_OPTIONS)
            );
            ActionButton cancel = ActionButton.create(configUtils.msg("city.staff.events.dialog.cancel", "<red>ANNULLA</red>"), null, 170, null);
            factory.empty().base(base).type(DialogType.multiAction(List.of(confirm), cancel, 2));
        });
        DialogViewUtils.showDialog(plugin, player, dialog, configUtils.msg(
                "city.staff.events.dialog.unavailable",
                "<red>Dialog non disponibile su questo server.</red>"
        ), "staff-events-reward");
    }

    private void tickSafe() {
        try {
            tick();
        } catch (Exception exception) {
            plugin.getLogger().warning("[staff-events] lifecycle tick failed: " + exception.getMessage());
        }
    }

    private void tick() {
        Instant now = Instant.now();
        for (StaffEvent event : new ArrayList<>(eventsById.values())) {
            if (event.status() == StaffEventStatus.PUBLISHED && !event.startAt().isAfter(now)) {
                activateEvent(event);
                continue;
            }
            if (event.status() != StaffEventStatus.ACTIVE) {
                continue;
            }
            if (event.kind() == StaffEventKind.AUTO_RACE && event.linkedChallengeInstanceId() != null) {
                Optional<ChallengeInstanceStatus> linkedStatus = challengeService.instanceStatus(event.linkedChallengeInstanceId());
                if (linkedStatus.isPresent() && linkedStatus.get() != ChallengeInstanceStatus.ACTIVE) {
                    persistEvent(complete(event, event.closedBy()));
                    continue;
                }
            }
            if (event.endAt().isAfter(now)) {
                continue;
            }
            if (event.kind() == StaffEventKind.AUTO_RACE) {
                if (event.linkedChallengeInstanceId() != null) {
                    challengeService.forceExpireInstance(event.linkedChallengeInstanceId());
                }
                persistEvent(complete(event, event.closedBy()));
            } else {
                persistEvent(copy(event,
                        StaffEventStatus.REVIEW,
                        event.publishedBy(),
                        event.closedBy(),
                        true,
                        event.linkedChallengeInstanceId(),
                        event.publishedAt(),
                        Instant.now(),
                        event.completedAt()));
            }
        }
    }

    private void activateEvent(StaffEvent event) {
        if (event == null) {
            return;
        }
        if (event.kind() == StaffEventKind.AUTO_RACE) {
            Map<String, String> payload = decodePayload(event.payload());
            ChallengeMode mode = parseMode(payload.get(PAYLOAD_TEMPLATE_MODE));
            String challengeId = blankToNull(payload.get(PAYLOAD_TEMPLATE_CHALLENGE));
            if (mode == null || !mode.race()) {
                plugin.getLogger().warning("[staff-events] invalid auto race template mode for event=" + event.eventId());
                return;
            }
            String linkedId = challengeService.createStaffManagedRaceInstance(
                    event.eventId(),
                    mode,
                    challengeId,
                    event.title(),
                    event.startAt(),
                    event.endAt()
            ).orElse(null);
            if (linkedId == null) {
                plugin.getLogger().warning("[staff-events] unable to create linked challenge for event=" + event.eventId());
                return;
            }
            StaffEvent updated = copy(event,
                    StaffEventStatus.ACTIVE,
                    event.publishedBy(),
                    event.closedBy(),
                    true,
                    linkedId,
                    event.publishedAt() == null ? Instant.now() : event.publishedAt(),
                    Instant.now(),
                    event.completedAt());
            persistEvent(updated);
            return;
        }
        StaffEvent updated = copy(event,
                StaffEventStatus.ACTIVE,
                event.publishedBy(),
                event.closedBy(),
                true,
                event.linkedChallengeInstanceId(),
                event.publishedAt() == null ? Instant.now() : event.publishedAt(),
                Instant.now(),
                event.completedAt());
        persistEvent(updated);
    }

    private StaffEvent complete(StaffEvent event, UUID closedBy) {
        Instant now = Instant.now();
        return copy(event,
                StaffEventStatus.COMPLETED,
                event.publishedBy(),
                closedBy,
                true,
                event.linkedChallengeInstanceId(),
                event.publishedAt(),
                now,
                now);
    }

    private void persistEvent(StaffEvent event) {
        repository.upsertEvent(event);
        eventsById.put(event.eventId(), event);
    }

    private void persistSubmission(StaffEventSubmission submission) {
        repository.upsertSubmission(submission);
        submissionsByEventTown
                .computeIfAbsent(submission.eventId(), ignored -> new ConcurrentHashMap<>())
                .put(submission.townId(), submission);
    }

    private StaffEvent copy(
            StaffEvent event,
            StaffEventStatus status,
            UUID publishedBy,
            UUID closedBy,
            boolean visible,
            String linkedChallengeInstanceId,
            Instant publishedAt,
            Instant updatedAt,
            Instant completedAt
    ) {
        return new StaffEvent(
                event.eventId(),
                event.kind(),
                status,
                event.title(),
                event.subtitle(),
                event.description(),
                event.startAt(),
                event.endAt(),
                event.createdBy(),
                publishedBy,
                closedBy,
                visible,
                linkedChallengeInstanceId,
                event.payload(),
                event.createdAt(),
                updatedAt,
                publishedAt,
                completedAt
        );
    }

    private String renderCommand(String command, int townId, UUID actorId) {
        if (command == null || command.isBlank()) {
            return "";
        }
        return command
                .replace("{town}", townName(townId))
                .replace("{town_id}", Integer.toString(townId))
                .replace("{actor}", actorId == null ? "" : actorId.toString());
    }

    private String townName(int townId) {
        return huskTownsApiHook.getTownById(townId).map(Town::getName).orElse("#" + townId);
    }

    private static String buildEventId(String prefix, String title, Instant now) {
        String normalized = title == null ? "event" : title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        if (normalized.isBlank()) {
            normalized = "event";
        }
        return prefix + ':' + normalized + ':' + now.getEpochSecond();
    }

    private static String encodePayload(Map<String, String> values) {
        Map<String, String> safe = values == null ? Map.of() : new LinkedHashMap<>(values);
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, String> entry : safe.entrySet()) {
            parts.add(base64(entry.getKey()) + '=' + base64(blankToEmpty(entry.getValue())));
        }
        return String.join(";", parts);
    }

    private static Map<String, String> decodePayload(String payload) {
        Map<String, String> result = new HashMap<>();
        if (payload == null || payload.isBlank()) {
            return result;
        }
        for (String part : payload.split(";")) {
            int separator = part.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            result.put(unbase64(part.substring(0, separator)), unbase64(part.substring(separator + 1)));
        }
        return result;
    }

    private static String base64(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(blankToEmpty(value).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String unbase64(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            return new String(Base64.getUrlDecoder().decode(value), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }

    private static ChallengeMode parseMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return ChallengeMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private Instant parseDialogInstant(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return java.time.LocalDateTime.parse(raw.trim(), DIALOG_DATE_TIME).atZone(timezone()).toInstant();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Integer parseInteger(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Double parseDouble(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(raw.trim().replace(',', '.'));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static List<String> parseCommands(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(raw.split("\\|\\|"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private static Map<Material, Integer> parseVaultItems(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        Map<Material, Integer> result = new LinkedHashMap<>();
        for (String token : raw.split(",")) {
            if (token == null || token.isBlank()) {
                continue;
            }
            String[] parts = token.trim().split(":");
            if (parts.length != 2) {
                continue;
            }
            Material material = Material.matchMaterial(parts[0].trim().toUpperCase(Locale.ROOT));
            Integer amount = parseInteger(parts[1].trim());
            if (material == null || amount == null || amount <= 0) {
                continue;
            }
            result.merge(material, amount, Integer::sum);
        }
        return Map.copyOf(result);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String firstNonBlank(String first, String fallback) {
        String normalized = blankToNull(first);
        return normalized != null ? normalized : blankToNull(fallback);
    }

    private static String normalizePresetKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private static Map<String, String> autoRacePayload(
            ChallengeMode templateMode,
            String templateChallengeId,
            Map<String, String> payloadExtras
    ) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put(PAYLOAD_TEMPLATE_MODE, templateMode == null ? "" : templateMode.name());
        payload.put(PAYLOAD_TEMPLATE_CHALLENGE, blankToEmpty(templateChallengeId));
        if (payloadExtras != null && !payloadExtras.isEmpty()) {
            payload.putAll(payloadExtras);
        }
        return Map.copyOf(payload);
    }
}
