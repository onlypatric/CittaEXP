package it.patric.cittaexp.challenges;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import it.patric.cittaexp.integration.husktowns.HuskTownsApiHook;
import it.patric.cittaexp.permissions.TownMemberPermission;
import it.patric.cittaexp.permissions.TownMemberPermissionService;
import it.patric.cittaexp.playersgui.CityPlayersSession;
import it.patric.cittaexp.text.MiniMessageHelper;
import it.patric.cittaexp.text.UiLoreStyle;
import it.patric.cittaexp.utils.DialogViewUtils;
import it.patric.cittaexp.utils.DialogInputUtils;
import it.patric.cittaexp.utils.GuiHeadTextures;
import it.patric.cittaexp.utils.GuiItemFactory;
import it.patric.cittaexp.utils.GuiLayoutUtils;
import it.patric.cittaexp.utils.GuiNavigationUtils;
import it.patric.cittaexp.utils.PluginConfigUtils;
import it.patric.cittaexp.utils.TexturedGuiSupport;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.time.temporal.TemporalAdjusters;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;

public final class CityChallengeGuiService implements Listener {

    private static final int GUI_SIZE = 54;
    private static final String QUEST_MAIN_FONT_IMAGE_ID = "cittaexp_gui:quest_main_bg";
    private static final String WEEKLY_MAIN_FONT_IMAGE_ID = "cittaexp_gui:settimanali_main_bg";
    private static final String MONTHLY_MAIN_FONT_IMAGE_ID = "cittaexp_gui:mensili_main_bg";
    private static final String STORY_MODE_FONT_IMAGE_ID = "cittaexp_gui:story_mode_bg";
    private static final String SEASONAL_MAIN_FONT_IMAGE_ID = "cittaexp_gui:stagionali_main_bg";
    private static final String EVENTS_MAIN_FONT_IMAGE_ID = "cittaexp_gui:eventi_main_bg";
    private static final String TRANSPARENT_BUTTON_ID = "cittaexp_gui:transparent_button";
    private static final String SECRET_QUEST_ID = "proc_secret_quest";
    private static final ClickCallback.Options CLICK_OPTIONS = ClickCallback.Options.builder()
            .uses(1)
            .build();
    private static final int[] MAIN_DAILY_BUTTON_SLOTS = {0, 1, 2, 3, 9, 10, 11, 12};
    private static final int[] MAIN_DAILY_MISSION_SLOTS = {4, 5, 6, 7, 8, 14, 15, 16};
    private static final int MAIN_DAILY_PREV_SLOT = 13;
    private static final int MAIN_DAILY_NEXT_SLOT = 17;
    private static final int[] MAIN_WEEKLY_BUTTON_SLOTS = {18, 19, 20, 21, 27, 28, 29, 30};
    private static final int[] MAIN_MONTHLY_BUTTON_SLOTS = {22, 23, 24, 25, 26, 31, 32, 33, 34, 35};
    private static final int[] MAIN_SEASONAL_BUTTON_SLOTS = {36, 37, 38, 39};
    private static final int[] MAIN_EVENTS_BUTTON_SLOTS = {40, 41, 42, 43, 44};
    private static final int[] MAIN_BACK_SLOTS = {46, 47, 48};
    private static final int[] MAIN_ATLAS_SLOTS = {50, 51, 52};
    private static final int[] STORY_LEFT_CHAPTER_SLOTS = {
            0, 1, 2, 3,
            9, 10, 11, 12,
            18, 19, 20, 21,
            27, 28, 29, 30,
            36, 37, 38, 39
    };
    private static final int STORY_CHAPTER_PREV_SLOT = 45;
    private static final int STORY_CHAPTER_NEXT_SLOT = 53;
    private static final int[] STORY_RIGHT_QUEST_SLOTS = {
            5, 6, 7, 8,
            14, 15, 16, 17,
            23, 24, 25, 26,
            32, 33, 34, 35,
            41, 42, 43, 44
    };
    private static final int STORY_BACK_SLOT = 49;
    private static final int STORY_MODE_TEXTURE_OFFSET_X = -44;
    private static final int WEEKLY_MONTHLY_SIZE = 27;
    private static final int[] WEEKLY_MONTHLY_MISSION_SLOTS = {
            0, 1, 2, 3, 4, 5, 6, 7, 8,
            9, 10, 11, 12, 13, 14, 15, 16, 17
    };
    private static final int[] WEEKLY_MONTHLY_BACK_SLOTS = {19, 20, 21};
    private static final int[] WEEKLY_MONTHLY_INFO_SLOTS = {23, 24, 25};
    private static final int PAGED_OVERLAY_SIZE = 54;
    private static final int[] PAGED_OVERLAY_MISSION_SLOTS = {
            11, 12, 13, 14, 15,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    private static final int PAGED_OVERLAY_PREV_SLOT = 36;
    private static final int PAGED_OVERLAY_NEXT_SLOT = 44;
    private static final int[] PAGED_OVERLAY_BACK_SLOTS = {46, 47, 48};
    private static final int[] PAGED_OVERLAY_INFO_SLOTS = {50, 51, 52};
    private static final int[] DAILY_PYRAMID_SLOTS = {20, 22, 24, 30, 32};
    private static final int[] WEEKLY_PYRAMID_SLOTS = {22, 30, 32};
    private static final int[] MONTHLY_PYRAMID_SLOTS = {10, 12, 14, 16, 22, 30, 32};
    private static final int[] EVENT_PYRAMID_SLOTS = {20, 22, 24, 30, 32};
    private static final int[] SEASONAL_PYRAMID_SLOTS = {10, 12, 14, 16, 20, 22, 24, 28, 30, 32};
    private static final int SLOT_TAB_DAILY = 2;
    private static final int SLOT_TAB_WEEKLY = 3;
    private static final int SLOT_TAB_MONTHLY = 4;
    private static final int SLOT_TAB_EVENTS = 5;
    private static final int SLOT_TAB_SEASONAL = 6;
    private static final int SLOT_TAB_ATLAS = 7;
    private static final int SLOT_DEFENSE = 48;
    private static final int SLOT_BACK = 47;
    private static final int SLOT_CLOSE = 51;
    private static final int SLOT_MAYOR = 53;
    private static final int SLOT_TAB_HINT = 49;
    private static final int MANAGE_GUI_SIZE = 27;
    private static final int MANAGE_SLOT_BACK = 18;
    private static final int MANAGE_SLOT_CLOSE = 26;
    private static final int MANAGE_SLOT_REROLL = 11;
    private static final int MANAGE_SLOT_SEASON = 13;
    private static final int MANAGE_SLOT_MILESTONE = 15;
    private static final int ATLAS_SLOT_SEALS = 50;

    private final Plugin plugin;
    private final HuskTownsApiHook huskTownsApiHook;
    private final TownMemberPermissionService permissionService;
    private final PluginConfigUtils configUtils;
    private final CityChallengeService challengeService;
    private final StaffEventService staffEventService;
    private final ConcurrentMap<UUID, Session> sessionsByViewer = new ConcurrentHashMap<>();
    private DefenseOpener defenseOpener;

    public CityChallengeGuiService(
            Plugin plugin,
            HuskTownsApiHook huskTownsApiHook,
            TownMemberPermissionService permissionService,
            CityChallengeService challengeService,
            StaffEventService staffEventService
    ) {
        this.plugin = plugin;
        this.huskTownsApiHook = huskTownsApiHook;
        this.permissionService = permissionService;
        this.configUtils = new PluginConfigUtils(plugin);
        this.challengeService = challengeService;
        this.staffEventService = staffEventService;
    }

    @FunctionalInterface
    public interface DefenseOpener {
        void open(Player player);
    }

    public void setDefenseOpener(DefenseOpener defenseOpener) {
        this.defenseOpener = defenseOpener;
    }

    public void clearAllSessions() {
        sessionsByViewer.clear();
    }

    public void open(Player player, CityPlayersSession citySession) {
        if (citySession.staffView()) {
            player.sendMessage(configUtils.msg("city.players.gui.challenges.errors.staff_readonly",
                    "<red>La sezione Sfide non e disponibile in visuale staff.</red>"));
            return;
        }
        Session session = new Session(
                UUID.randomUUID(),
                player.getUniqueId(),
                citySession.townId(),
                citySession.townName(),
                citySession.staffView(),
                false
        );
        sessionsByViewer.put(player.getUniqueId(), session);
        openMainPage(player, session);
    }

    private void openMainPage(Player player, Session session) {
        Holder holder = new Holder(session.sessionId);
        holder.kind = HolderKind.MAIN;
        MainOpenResult main = createMainInventory(holder, session);
        Inventory inventory = main.inventory();
        ItemStack transparentBase = transparentButtonBase();

        fillButtonGroup(inventory, transparentBase, MAIN_DAILY_BUTTON_SLOTS,
                "city.players.gui.missions.main.daily",
                "<gradient:#fff07a:#ffb347><bold>✦ GIORNALIERE ✦</bold></gradient>",
                framedLore(
                        "<gray>Il diario quotidiano della citta non resta mai fermo.</gray>",
                        "<yellow>✦</yellow> <white>Qui trovi il ritmo giornaliero della tua squadra</white>",
                        "<yellow>✦</yellow> <white>Missioni rapide, race nascoste e progressi immediati</white>",
                        "<green>➜ Qui puoi aprire direttamente le missioni giornaliere del giorno.</green>"
                ));
        fillButtonGroup(inventory, transparentBase, MAIN_WEEKLY_BUTTON_SLOTS,
                "city.players.gui.missions.main.weekly",
                "<gradient:#8be9fd:#4facfe><bold>✦ SETTIMANALI ✦</bold></gradient>",
                framedLore(
                        "<gray>Qui iniziano gli obiettivi che chiedono vera cooperazione.</gray>",
                        "<yellow>✦</yellow> <white>Le settimanali guidano la citta per piu giorni</white>",
                        "<yellow>✦</yellow> <white>Premi migliori, progressi piu pesanti, bonus speciali</white>",
                        "<green>➜ Clicca per vedere le settimanali attive.</green>"
                ));
        fillButtonGroup(inventory, transparentBase, MAIN_MONTHLY_BUTTON_SLOTS,
                "city.players.gui.missions.main.monthly",
                "<gradient:#f6d365:#fda085><bold>✦ MENSILI ✦</bold></gradient>",
                framedLore(
                        "<gray>Le missioni mensili segnano i grandi archi della stagione cittadina.</gray>",
                        "<yellow>✦</yellow> <white>Qui trovi i traguardi lunghi e piu prestigiosi</white>",
                        "<yellow>✦</yellow> <white>Sono le prove che fanno davvero pesare il nome della citta</white>",
                        "<green>➜ Clicca per aprire il fronte mensile.</green>"
                ));
        fillButtonGroup(inventory, transparentBase, MAIN_SEASONAL_BUTTON_SLOTS,
                "city.players.gui.missions.main.seasonal",
                "<gradient:#c471ed:#12c2e9><bold>✦ STAGIONALI ✦</bold></gradient>",
                framedLore(
                        "<gray>Le stagioni raccolgono le prove rare che distinguono le citta costanti.</gray>",
                        "<yellow>✦</yellow> <white>Qui scorrono gli incarichi piu lenti e memorabili</white>",
                        "<yellow>✦</yellow> <white>Ogni progresso lascia un segno piu grande del singolo giorno</white>",
                        "<green>➜ Clicca per aprire le sfide stagionali.</green>"
                ));
        fillButtonGroup(inventory, transparentBase, MAIN_EVENTS_BUTTON_SLOTS,
                "city.players.gui.missions.main.events",
                "<gradient:#ffd76a:#ff6b6b><bold>✦ EVENTI ✦</bold></gradient>",
                framedLore(
                        "<gray>Qui si aprono le finestre competitive dove le citta si misurano davvero.</gray>",
                        "<yellow>✦</yellow> <white>Race, eventi e momenti a tempo con pressione reale</white>",
                        "<yellow>✦</yellow> <white>Chi arriva prima lascia il segno davanti a tutti</white>",
                        "<green>➜ Clicca per entrare negli eventi.</green>"
                ));
        fillButtonGroup(inventory, transparentBase, MAIN_BACK_SLOTS,
                "city.players.gui.missions.main.back",
                "<gradient:#ff9a9e:#f6416c><bold>✦ INDIETRO ✦</bold></gradient>",
                framedLore(
                        "<gray>Se il diario missioni e a posto, puoi tornare al cuore della citta.</gray>",
                        "<yellow>✦</yellow> <white>Chiudi questa sezione e rientra nella HUB principale</white>",
                        "<green>➜ Clicca per tornare a /city.</green>"
                ));
        fillButtonGroup(inventory, transparentBase, MAIN_ATLAS_SLOTS,
                "city.players.gui.missions.main.atlas",
                "<gradient:#7afcff:#5ee7df><bold>✦ STORY MODE ✦</bold></gradient>",
                framedLore(
                        "<gray>Questo e il viaggio permanente della tua citta, capitolo dopo capitolo.</gray>",
                        "<yellow>✦</yellow> <white>Segui la saga che trasforma un gruppo in una vera potenza</white>",
                        "<yellow>✦</yellow> <white>Ogni capitolo sblocca il passo successivo della tua storia</white>",
                        "<green>➜ Clicca per aprire Story Mode.</green>"
                ));

        renderDailyPreview(inventory, holder, session, transparentBase);

        if (main.wrapper() != null) {
            main.wrapper().showInventory(player);
            return;
        }
        player.openInventory(inventory);
    }

    private void openWeeklyMainPage(Player player, Session session) {
        openCycleOverlayPage(player, session, HolderKind.WEEKLY_MAIN, MissionTab.WEEKLY, WEEKLY_MAIN_FONT_IMAGE_ID,
                "city.players.gui.missions.weekly.title",
                "<gold>Settimanali</gold> <gray>{town}</gray>",
                "city.players.gui.missions.weekly.info",
                "<gradient:#8be9fd:#4facfe><bold>✦ INFO ✦</bold></gradient>",
                nextWeeklyRefreshLore());
    }

    private void openDailyMainPage(Player player, Session session) {
        openCycleOverlayPage(player, session, HolderKind.DAILY_MAIN, MissionTab.DAILY, QUEST_MAIN_FONT_IMAGE_ID,
                "city.players.gui.missions.daily.title",
                "<gold>Giornaliere</gold> <gray>{town}</gray>",
                "city.players.gui.missions.daily.info",
                "<gradient:#fff07a:#ffb347><bold>✦ INFO ✦</bold></gradient>",
                nextDailyRefreshLore());
    }

    private void openMonthlyMainPage(Player player, Session session) {
        openCycleOverlayPage(player, session, HolderKind.MONTHLY_MAIN, MissionTab.MONTHLY, MONTHLY_MAIN_FONT_IMAGE_ID,
                "city.players.gui.missions.monthly.title",
                "<gold>Mensili</gold> <gray>{town}</gray>",
                "city.players.gui.missions.monthly.info",
                "<gradient:#f6d365:#fda085><bold>✦ INFO ✦</bold></gradient>",
                nextMonthlyRefreshLore());
    }

    private void openStoryModePage(Player player, Session session) {
        Holder holder = new Holder(session.sessionId);
        holder.kind = HolderKind.STORY_MODE;
        TexturedGuiSupport.OpenResult openResult = createTexturedInventory(
                holder,
                GUI_SIZE,
                configUtils.msg(
                        "city.players.gui.missions.story_mode.title",
                        "<gold>Story Mode</gold> <gray>{town}</gray>",
                        Placeholder.unparsed("town", session.townName)
                ),
                plugin.getConfig().getString("city.missions.itemsadder.story_mode_title_plain", "Story Mode"),
                STORY_MODE_TEXTURE_OFFSET_X,
                STORY_MODE_FONT_IMAGE_ID,
                "story-mode"
        );
        Inventory inventory = openResult.inventory();

        List<CityStoryModeService.StoryModeChapterSnapshot> allChapters = challengeService.storyModeChapterSnapshots(
                session.townId,
                player.getUniqueId()
        );
        CityStoryModeService.StoryModeDiagnostics diagnostics = challengeService.storyModeDiagnostics(session.townId);
        int maxPage = challengeService.storyModeMaxPage(STORY_LEFT_CHAPTER_SLOTS.length);
        int safePage = Math.max(0, Math.min(session.storyModeChapterPage, maxPage));
        session.storyModeChapterPage = safePage;
        int start = safePage * STORY_LEFT_CHAPTER_SLOTS.length;
        int end = Math.min(allChapters.size(), start + STORY_LEFT_CHAPTER_SLOTS.length);
        List<CityStoryModeService.StoryModeChapterSnapshot> chapters = start >= end ? List.of() : allChapters.subList(start, end);
        boolean selectedVisible = session.storyModeSelectedChapterId != null
                && allChapters.stream().anyMatch(chapter -> chapter.chapterId().equalsIgnoreCase(session.storyModeSelectedChapterId));
        if (!selectedVisible && !chapters.isEmpty()) {
            session.storyModeSelectedChapterId = chapters.getFirst().chapterId();
        }

        holder.storyModeChapterBySlot.clear();
        for (int i = 0; i < STORY_LEFT_CHAPTER_SLOTS.length; i++) {
            int slot = STORY_LEFT_CHAPTER_SLOTS[i];
            inventory.setItem(slot, null);
            if (i >= chapters.size()) {
                continue;
            }
            CityStoryModeService.StoryModeChapterSnapshot chapter = chapters.get(i);
            inventory.setItem(slot, storyModeChapterItem(chapter, chapter.chapterId().equalsIgnoreCase(session.storyModeSelectedChapterId)));
            holder.storyModeChapterBySlot.put(slot, chapter.chapterId());
        }

        CityStoryModeService.StoryModeChapterSnapshot selected = challengeService.storyModeChapterSnapshot(
                session.townId,
                session.storyModeSelectedChapterId,
                player.getUniqueId()
        );
        for (int slot : STORY_RIGHT_QUEST_SLOTS) {
            inventory.setItem(slot, null);
        }
        List<CityStoryModeService.StoryModeTaskSnapshot> quests = selected == null ? List.of() : selected.tasks();
        int renderCount = Math.min(quests.size(), STORY_RIGHT_QUEST_SLOTS.length);
        for (int i = 0; i < renderCount; i++) {
            inventory.setItem(STORY_RIGHT_QUEST_SLOTS[i], storyModeQuestItem(quests.get(i), selected));
        }
        int extraIndex = renderCount;
        if (selected != null && extraIndex < STORY_RIGHT_QUEST_SLOTS.length) {
            inventory.setItem(STORY_RIGHT_QUEST_SLOTS[extraIndex++], storyModeSummaryItem(selected));
        }
        if (selected != null && extraIndex < STORY_RIGHT_QUEST_SLOTS.length) {
            inventory.setItem(STORY_RIGHT_QUEST_SLOTS[extraIndex], storyModeRewardItem(selected));
        }
        if (allChapters.isEmpty()) {
            inventory.setItem(STORY_RIGHT_QUEST_SLOTS[0], storyModeFallbackItem(diagnostics));
        }

        inventory.setItem(STORY_CHAPTER_PREV_SLOT, storyModeNavItem(
                Material.ARROW,
                "<gradient:#7afcff:#5ee7df><bold>✦ PAGINA PREC ✦</bold></gradient>",
                "<gray>Scorri i capitoli precedenti di Story Mode.</gray>",
                "<green>➜ Clicca per tornare alla pagina prima.</green>"
        ));
        inventory.setItem(STORY_CHAPTER_NEXT_SLOT, storyModeNavItem(
                Material.ARROW,
                "<gradient:#7afcff:#5ee7df><bold>✦ PAGINA SUCC ✦</bold></gradient>",
                "<gray>Scorri i capitoli successivi di Story Mode.</gray>",
                "<green>➜ Clicca per avanzare nella saga.</green>"
        ));
        inventory.setItem(STORY_BACK_SLOT, GuiItemFactory.item(
                Material.COMPASS,
                configUtils.msg(
                        "city.players.gui.story_mode.back",
                        "<gradient:#ff9a9e:#f6416c><bold>✦ INDIETRO ✦</bold></gradient>"
                ),
                storyModeInfoLore(session, selected, maxPage, diagnostics)
        ));
        if (openResult.texturedWrapper() != null) {
            openResult.texturedWrapper().showInventory(player);
            return;
        }
        player.openInventory(inventory);
    }

    private void openSeasonalMainPage(Player player, Session session) {
        openPagedOverlayPage(player, session, HolderKind.SEASONAL_MAIN, MissionTab.CODEX, SEASONAL_MAIN_FONT_IMAGE_ID,
                "city.players.gui.missions.seasonal.title",
                "<gold>Stagionali</gold> <gray>{town}</gray>",
                "city.players.gui.missions.seasonal.info",
                "<gradient:#c471ed:#12c2e9><bold>✦ INFO ✦</bold></gradient>",
                nextSeasonalRefreshLore(),
                session.seasonalPage,
                true);
    }

    private void openEventsMainPage(Player player, Session session) {
        openPagedOverlayPage(player, session, HolderKind.EVENTS_MAIN, MissionTab.RACES, EVENTS_MAIN_FONT_IMAGE_ID,
                "city.players.gui.missions.events.title",
                "<gold>Eventi</gold> <gray>{town}</gray>",
                "city.players.gui.missions.events.info",
                "<gradient:#ffd76a:#ff6b6b><bold>✦ INFO ✦</bold></gradient>",
                nextEventsRefreshLore(),
                session.eventsPage,
                false);
    }

    private void openCycleOverlayPage(
            Player player,
            Session session,
            HolderKind kind,
            MissionTab tab,
            String fontImageId,
            String titlePath,
            String titleFallback,
            String infoPath,
            String infoFallback,
            List<Component> infoLore
    ) {
        Holder holder = new Holder(session.sessionId);
        holder.kind = kind;
        TexturedGuiSupport.OpenResult openResult = createTexturedInventory(
                holder,
                WEEKLY_MONTHLY_SIZE,
                configUtils.msg(
                        titlePath,
                        titleFallback,
                        Placeholder.unparsed("town", session.townName)
                ),
                switch (tab) {
                    case DAILY -> "Giornaliere";
                    case WEEKLY -> "Settimanali";
                    case MONTHLY -> "Mensili";
                    default -> "Missioni";
                },
                plugin.getConfig().getInt("city.missions.itemsadder.texture_offset", -8),
                fontImageId,
                "missions-overlay"
        );
        Inventory inventory = openResult.inventory();

        ItemStack transparentBase = transparentButtonBase();
        List<ChallengeSnapshot> snapshots = rankForMissionHome(snapshotsForTab(session, tab));
        int capacity = challengeService.questBatchCapacityForTownPreview(session.townId);
        int visibleCount = Math.min(snapshots.size(), capacity);
        for (int i = 0; i < WEEKLY_MONTHLY_MISSION_SLOTS.length; i++) {
            int slot = WEEKLY_MONTHLY_MISSION_SLOTS[i];
            if (i < visibleCount) {
                ChallengeSnapshot snapshot = snapshots.get(i);
                inventory.setItem(slot, challengeItem(
                        snapshot,
                        challengeService.rewardPreviewForInstance(snapshot.instanceId()),
                        session.townId,
                        tab == MissionTab.WEEKLY ? "Weekly" : "Monthly"
                ));
                continue;
            }
            if (i < capacity) {
                inventory.setItem(slot, availableBatchPlaceholderItem(tab, i + 1));
                continue;
            }
            inventory.setItem(slot, lockedBatchPlaceholderItem(tab, i + 1));
        }

        fillButtonGroup(inventory, transparentBase, WEEKLY_MONTHLY_BACK_SLOTS,
                "city.players.gui.missions.overlay.back",
                "<white> </white>",
                framedLore(
                        "<gray>Hai visto abbastanza di questo fronte missioni.</gray>",
                        "<yellow>✦</yellow> <white>Torna alla sala principale delle Quest</white>",
                        "<green>➜ Clicca per rientrare nel pannello centrale.</green>"
                ));
        fillButtonGroup(inventory, transparentBase, WEEKLY_MONTHLY_INFO_SLOTS,
                infoPath,
                infoFallback,
                infoLore);

        if (openResult.texturedWrapper() != null) {
            openResult.texturedWrapper().showInventory(player);
            return;
        }
        player.openInventory(inventory);
    }

    private void openPagedOverlayPage(
            Player player,
            Session session,
            HolderKind kind,
            MissionTab tab,
            String fontImageId,
            String titlePath,
            String titleFallback,
            String infoPath,
            String infoFallback,
            List<Component> infoLore,
            int requestedPage,
            boolean seasonalPage
    ) {
        Holder holder = new Holder(session.sessionId);
        holder.kind = kind;
        TexturedGuiSupport.OpenResult openResult = createTexturedInventory(
                holder,
                PAGED_OVERLAY_SIZE,
                configUtils.msg(
                        titlePath,
                        titleFallback,
                        Placeholder.unparsed("town", session.townName)
                ),
                tab == MissionTab.CODEX ? "Stagionali" : "Eventi",
                plugin.getConfig().getInt("city.missions.itemsadder.texture_offset", -8),
                fontImageId,
                "missions-overlay"
        );
        Inventory inventory = openResult.inventory();

        ItemStack transparentBase = transparentButtonBase();
        List<ChallengeSnapshot> snapshots = rankForMissionHome(snapshotsForTab(session, tab));
        List<EventOverlayEntry> eventEntries = seasonalPage ? List.of() : buildEventOverlayEntries(session, snapshots);
        int totalEntries = seasonalPage ? snapshots.size() : eventEntries.size();
        int maxPage = maxOverlayPage(totalEntries, PAGED_OVERLAY_MISSION_SLOTS.length);
        int page = Math.max(0, Math.min(requestedPage, maxPage));
        if (seasonalPage) {
            session.seasonalPage = page;
        } else {
            session.eventsPage = page;
        }

        int start = page * PAGED_OVERLAY_MISSION_SLOTS.length;
        holder.staffEventBySlot.clear();
        for (int i = 0; i < PAGED_OVERLAY_MISSION_SLOTS.length; i++) {
            int index = start + i;
            int slot = PAGED_OVERLAY_MISSION_SLOTS[i];
            if (index >= totalEntries) {
                inventory.setItem(slot, null);
                continue;
            }
            if (!seasonalPage) {
                EventOverlayEntry entry = eventEntries.get(index);
                if (entry.staffEvent() != null) {
                    inventory.setItem(slot, staffEventItem(entry.staffEvent(), session));
                    holder.staffEventBySlot.put(slot, entry.staffEvent());
                    continue;
                }
            }
            ChallengeSnapshot snapshot = seasonalPage ? snapshots.get(index) : eventEntries.get(index).snapshot();
            if (isHiddenSecretQuest(snapshot)) {
                inventory.setItem(slot, hiddenSecretQuestItem());
                continue;
            }
            inventory.setItem(slot, challengeItem(
                    snapshot,
                    challengeService.rewardPreviewForInstance(snapshot.instanceId()),
                    session.townId,
                    seasonalPage ? "Seasonal" : "Event"
            ));
        }

        inventory.setItem(PAGED_OVERLAY_PREV_SLOT, overlayButton(
                transparentBase,
                "city.players.gui.missions.overlay.prev",
                "<white> </white>",
                framedLore(
                        "<gray>Scorri verso le sfide gia viste in questa sezione.</gray>",
                        "<yellow>✦</yellow> <white>Apri la pagina precedente del registro</white>",
                        "<green>➜ Clicca per tornare indietro.</green>"
                )
        ));
        inventory.setItem(PAGED_OVERLAY_NEXT_SLOT, overlayButton(
                transparentBase,
                "city.players.gui.missions.overlay.next",
                "<white> </white>",
                framedLore(
                        "<gray>Ci sono altre sfide oltre questa pagina.</gray>",
                        "<yellow>✦</yellow> <white>Apri la pagina successiva del registro</white>",
                        "<green>➜ Clicca per avanzare.</green>"
                )
        ));
        fillButtonGroup(inventory, transparentBase, PAGED_OVERLAY_BACK_SLOTS,
                "city.players.gui.missions.overlay.back",
                "<white> </white>",
                framedLore(
                        "<gray>Questa sezione e pronta: puoi tornare al centro missioni.</gray>",
                        "<yellow>✦</yellow> <white>Rientra nella schermata principale delle Quest</white>",
                        "<green>➜ Clicca per tornare indietro.</green>"
                ));
        fillButtonGroup(inventory, transparentBase, PAGED_OVERLAY_INFO_SLOTS, infoPath, infoFallback, infoLore);

        if (openResult.texturedWrapper() != null) {
            openResult.texturedWrapper().showInventory(player);
            return;
        }
        player.openInventory(inventory);
    }

    private void openTabPage(Player player, Session session) {
        MissionTab tab = session.tab == null ? MissionTab.DAILY : session.tab;
        List<ChallengeSnapshot> filtered = filterForTab(
                challengeService.listActiveForTownForPlayer(session.townId, player.getUniqueId()),
                tab
        );
        List<MissionCardRow> rows = buildMissionCards(rankForMissionHome(filtered));
        Holder holder = new Holder(session.sessionId);
        holder.kind = HolderKind.LIST;
        Inventory inventory = Bukkit.createInventory(holder, GUI_SIZE, configUtils.msg(
                "city.players.gui.missions.title",
                "<gold>Missioni Citta</gold> <gray>{town}</gray>",
                Placeholder.unparsed("town", session.townName)
        ));
        holder.bind(inventory);
        GuiLayoutUtils.fillAllExcept(
                inventory,
                GuiItemFactory.fillerPane(),
                SLOT_TAB_DAILY, SLOT_TAB_WEEKLY, SLOT_TAB_MONTHLY, SLOT_TAB_EVENTS,
                SLOT_TAB_SEASONAL, SLOT_TAB_ATLAS,
                SLOT_BACK, SLOT_DEFENSE, SLOT_TAB_HINT, SLOT_CLOSE, SLOT_MAYOR, ATLAS_SLOT_SEALS
        );
        inventory.setItem(SLOT_TAB_DAILY, tabButton(MissionTab.DAILY, tab == MissionTab.DAILY));
        inventory.setItem(SLOT_TAB_WEEKLY, tabButton(MissionTab.WEEKLY, tab == MissionTab.WEEKLY));
        inventory.setItem(SLOT_TAB_MONTHLY, tabButton(MissionTab.MONTHLY, tab == MissionTab.MONTHLY));
        inventory.setItem(SLOT_TAB_EVENTS, tabButton(MissionTab.RACES, tab == MissionTab.RACES));
        inventory.setItem(SLOT_TAB_SEASONAL, tabButton(MissionTab.CODEX, tab == MissionTab.CODEX));
        inventory.setItem(SLOT_TAB_ATLAS, tabButton(MissionTab.ATLAS, tab == MissionTab.ATLAS));
        inventory.setItem(SLOT_BACK, GuiItemFactory.customHead(
                GuiHeadTextures.BACK,
                configUtils.msg("city.players.gui.challenges.nav.back", "<yellow>Indietro</yellow>")
        ));
        inventory.setItem(SLOT_DEFENSE, GuiItemFactory.customHead(
                GuiHeadTextures.DANGER,
                configUtils.msg("city.players.gui.challenges.nav.defense", "<gold>Difesa citta</gold>")
        ));
        inventory.setItem(SLOT_CLOSE, GuiItemFactory.customHead(
                GuiHeadTextures.CLOSE,
                configUtils.msg("city.players.gui.challenges.nav.close", "<red>Chiudi</red>")
        ));
        if (!session.staffView && canManageChallenges(player, session)) {
            inventory.setItem(SLOT_MAYOR, GuiItemFactory.customHead(
                    GuiHeadTextures.ACTIONS,
                    configUtils.msg("city.players.gui.challenges.nav.mayor", "<gold>Gestione Sfide</gold>")
            ));
        }
        int displayedCount = Math.min(
                tabSlots(tab).length,
                tab == MissionTab.ATLAS
                        ? challengeService.atlasChapterSnapshots(session.townId).size()
                        : rows.size() + (tab == MissionTab.RACES ? hiddenDailyRaceCount() : 0)
        );
        inventory.setItem(SLOT_TAB_HINT, tabHintItem(tab, displayedCount, session.townId));
        if (tab == MissionTab.ATLAS && challengeService.atlasEnabled()) {
            CityAtlasService.AtlasSealSnapshot seals = challengeService.atlasSeals(session.townId);
            inventory.setItem(ATLAS_SLOT_SEALS, atlasSealItem(seals));
        } else {
            inventory.setItem(ATLAS_SLOT_SEALS, GuiItemFactory.fillerPane());
        }

        int[] slots = tabSlots(tab);
        if (tab == MissionTab.ATLAS) {
            holder.atlasChapterBySlot.clear();
            List<CityAtlasService.AtlasChapterSnapshot> chapters = challengeService.atlasChapterSnapshots(session.townId);
            for (int i = 0; i < Math.min(slots.length, chapters.size()); i++) {
                CityAtlasService.AtlasChapterSnapshot chapter = chapters.get(i);
                int slot = slots[i];
                inventory.setItem(slot, atlasChapterItem(chapter));
                holder.atlasChapterBySlot.put(slot, chapter.chapter());
            }
            player.openInventory(inventory);
            return;
        }
        int rendered = 0;
        for (int i = 0; i < Math.min(rows.size(), slots.length); i++) {
            MissionCardRow row = rows.get(i);
            int slot = slots[i];
            if (isHiddenSecretQuest(row.snapshot())) {
                inventory.setItem(slot, hiddenSecretQuestItem());
            } else {
                inventory.setItem(slot, challengeItem(row.snapshot(), challengeService.rewardPreviewForInstance(row.snapshot().instanceId()), session.townId, row.bucketLabel()));
            }
            rendered++;
        }
        if (tab == MissionTab.RACES) {
            int hidden = hiddenDailyRaceCount();
            for (int i = 0; i < hidden && rendered < Math.min(slots.length, 5); i++) {
                inventory.setItem(slots[rendered], hiddenRaceItem());
                rendered++;
            }
        }
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null || event.getRawSlot() < 0) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof Holder holder)) {
            return;
        }
        if (event.getClickedInventory() != top) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);

        Session session = sessionsByViewer.get(player.getUniqueId());
        if (session == null || !session.sessionId.equals(holder.sessionId())) {
            player.closeInventory();
            return;
        }
        int slot = event.getRawSlot();
        if (holder.kind == HolderKind.MANAGE) {
            handleManageClick(player, session, slot);
            return;
        }
        if (holder.kind == HolderKind.MAIN) {
            handleMainClick(player, session, holder, slot);
            return;
        }
        if (holder.kind == HolderKind.WEEKLY_MAIN) {
            handleWeeklyMonthlyMainClick(player, session, slot);
            return;
        }
        if (holder.kind == HolderKind.DAILY_MAIN) {
            handleWeeklyMonthlyMainClick(player, session, slot);
            return;
        }
        if (holder.kind == HolderKind.MONTHLY_MAIN) {
            handleWeeklyMonthlyMainClick(player, session, slot);
            return;
        }
        if (holder.kind == HolderKind.STORY_MODE) {
            handleStoryModeClick(player, session, holder, slot);
            return;
        }
        if (holder.kind == HolderKind.SEASONAL_MAIN) {
            handlePagedOverlayClick(player, session, slot, true);
            return;
        }
        if (holder.kind == HolderKind.EVENTS_MAIN) {
            handlePagedOverlayClick(player, session, slot, false);
            return;
        }
        if (holder.kind == HolderKind.ATLAS_CHAPTER) {
            handleAtlasChapterClick(player, session, holder, slot);
            return;
        }
        if (holder.kind == HolderKind.ATLAS_FAMILY) {
            handleAtlasFamilyClick(player, session, holder, slot);
            return;
        }
        if (slot == SLOT_TAB_DAILY) {
            openMainPage(player, session);
            return;
        }
        if (slot == SLOT_TAB_WEEKLY) {
            openWeeklyMainPage(player, session);
            return;
        }
        if (slot == SLOT_TAB_MONTHLY) {
            openMonthlyMainPage(player, session);
            return;
        }
        if (slot == SLOT_TAB_EVENTS) {
            openEventsMainPage(player, session);
            return;
        }
        if (slot == SLOT_TAB_SEASONAL) {
            openSeasonalMainPage(player, session);
            return;
        }
        if (slot == SLOT_TAB_ATLAS) {
            openStoryModePage(player, session);
            return;
        }
        if (slot == SLOT_BACK) {
            openMainPage(player, session);
            return;
        }
        if (slot == SLOT_DEFENSE) {
            if (defenseOpener != null) {
                defenseOpener.open(player);
            } else {
                player.sendMessage(configUtils.msg(
                        "city.players.gui.challenges.errors.defense_unavailable",
                        "<red>Difesa citta non disponibile.</red>"
                ));
            }
            return;
        }
        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == SLOT_MAYOR && !session.staffView && canManageChallenges(player, session)) {
            openMayorManage(player, session);
            return;
        }
        if (session.tab == MissionTab.ATLAS) {
            AtlasChapter chapter = holder.atlasChapterBySlot.get(slot);
            if (chapter != null) {
                openAtlasChapter(player, session, chapter);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        sessionsByViewer.remove(event.getPlayer().getUniqueId());
    }

    private void handleMainClick(Player player, Session session, Holder holder, int slot) {
        if (slot == MAIN_DAILY_PREV_SLOT) {
            if (session.mainDailyPage > 0) {
                session.mainDailyPage--;
                openMainPage(player, session);
            }
            return;
        }
        if (slot == MAIN_DAILY_NEXT_SLOT) {
            if (session.mainDailyPage < maxDailyMainPage(session)) {
                session.mainDailyPage++;
                openMainPage(player, session);
            }
            return;
        }
        if (contains(MAIN_DAILY_MISSION_SLOTS, slot)) {
            DailyPreviewEntry entry = holder.mainDailyEntryBySlot.get(slot);
            if (entry == null) {
                return;
            }
            if (entry.kind() == DailyPreviewEntryKind.STANDARD) {
                if (entry.snapshot() != null) {
                    openChallengeDialog(player, entry.snapshot(), session.townId, "Daily");
                }
                return;
            }
            handleDailyRacePreviewClick(player, entry.racePreview());
            return;
        }
        if (contains(MAIN_WEEKLY_BUTTON_SLOTS, slot)) {
            openWeeklyMainPage(player, session);
            return;
        }
        if (contains(MAIN_MONTHLY_BUTTON_SLOTS, slot)) {
            openMonthlyMainPage(player, session);
            return;
        }
        if (contains(MAIN_SEASONAL_BUTTON_SLOTS, slot)) {
            openSeasonalMainPage(player, session);
            return;
        }
        if (contains(MAIN_EVENTS_BUTTON_SLOTS, slot)) {
            openEventsMainPage(player, session);
            return;
        }
        if (contains(MAIN_ATLAS_SLOTS, slot)) {
            openStoryModePage(player, session);
            return;
        }
        if (contains(MAIN_BACK_SLOTS, slot)) {
            GuiNavigationUtils.openCityHub(plugin, player);
        }
    }

    private void handleWeeklyMonthlyMainClick(Player player, Session session, int slot) {
        if (contains(WEEKLY_MONTHLY_MISSION_SLOTS, slot)) {
            Holder holder = (Holder) player.getOpenInventory().getTopInventory().getHolder();
            MissionTab tab = holder != null && holder.kind == HolderKind.WEEKLY_MAIN ? MissionTab.WEEKLY : MissionTab.MONTHLY;
            List<ChallengeSnapshot> snapshots = rankForMissionHome(snapshotsForTab(session, tab));
            int visibleCount = Math.min(snapshots.size(), challengeService.questBatchCapacityForTownPreview(session.townId));
            int index = indexOfSlot(WEEKLY_MONTHLY_MISSION_SLOTS, slot);
            if (index >= 0 && index < visibleCount) {
                openChallengeDialog(
                        player,
                        snapshots.get(index),
                        session.townId,
                        tab == MissionTab.WEEKLY ? "Weekly" : "Monthly"
                );
            }
            return;
        }
        if (contains(WEEKLY_MONTHLY_BACK_SLOTS, slot)) {
            openMainPage(player, session);
            return;
        }
        if (contains(WEEKLY_MONTHLY_INFO_SLOTS, slot)) {
            Holder holder = (Holder) player.getOpenInventory().getTopInventory().getHolder();
            if (holder != null && holder.kind == HolderKind.WEEKLY_MAIN) {
                openWeeklyMainPage(player, session);
            } else if (holder != null && holder.kind == HolderKind.MONTHLY_MAIN) {
                openMonthlyMainPage(player, session);
            }
        }
    }

    private void handleStoryModeClick(Player player, Session session, Holder holder, int slot) {
        if (slot == STORY_CHAPTER_PREV_SLOT) {
            if (session.storyModeChapterPage > 0) {
                session.storyModeChapterPage--;
            }
            openStoryModePage(player, session);
            return;
        }
        if (slot == STORY_CHAPTER_NEXT_SLOT) {
            if (session.storyModeChapterPage < challengeService.storyModeMaxPage(STORY_LEFT_CHAPTER_SLOTS.length)) {
                session.storyModeChapterPage++;
            }
            openStoryModePage(player, session);
            return;
        }
        if (slot == STORY_BACK_SLOT) {
            openMainPage(player, session);
            return;
        }
        String chapterId = holder.storyModeChapterBySlot.get(slot);
        if (chapterId != null) {
            session.storyModeSelectedChapterId = chapterId;
            openStoryModePage(player, session);
        }
    }

    private void handlePagedOverlayClick(Player player, Session session, int slot, boolean seasonalPage) {
        if (contains(PAGED_OVERLAY_MISSION_SLOTS, slot)) {
            MissionTab tab = seasonalPage ? MissionTab.CODEX : MissionTab.RACES;
            if (!seasonalPage) {
                Holder holder = (Holder) player.getOpenInventory().getTopInventory().getHolder();
                if (holder != null) {
                    StaffEventService.EventCardView staffEvent = holder.staffEventBySlot.get(slot);
                    if (staffEvent != null) {
                        openStaffEventDialog(player, session, staffEvent);
                        return;
                    }
                }
            }
            List<ChallengeSnapshot> snapshots = rankForMissionHome(snapshotsForTab(session, tab));
            List<EventOverlayEntry> eventEntries = seasonalPage ? List.of() : buildEventOverlayEntries(session, snapshots);
            int page = seasonalPage ? session.seasonalPage : session.eventsPage;
            int pageIndex = indexOfSlot(PAGED_OVERLAY_MISSION_SLOTS, slot);
            int index = pageIndex < 0 ? -1 : (page * PAGED_OVERLAY_MISSION_SLOTS.length) + pageIndex;
            int total = seasonalPage ? snapshots.size() : eventEntries.size();
            if (index >= 0 && index < total) {
                ChallengeSnapshot snapshot = seasonalPage ? snapshots.get(index) : eventEntries.get(index).snapshot();
                if (!isHiddenSecretQuest(snapshot)) {
                    openChallengeDialog(player, snapshot, session.townId, seasonalPage ? "Seasonal" : "Event");
                }
            }
            return;
        }
        if (slot == PAGED_OVERLAY_PREV_SLOT) {
            if (seasonalPage) {
                if (session.seasonalPage > 0) {
                    session.seasonalPage--;
                }
                openSeasonalMainPage(player, session);
            } else {
                if (session.eventsPage > 0) {
                    session.eventsPage--;
                }
                openEventsMainPage(player, session);
            }
            return;
        }
        if (slot == PAGED_OVERLAY_NEXT_SLOT) {
            List<ChallengeSnapshot> snapshots = rankForMissionHome(filterForTab(
                    challengeService.listActiveForTownForPlayer(session.townId, session.viewerId),
                    seasonalPage ? MissionTab.CODEX : MissionTab.RACES
            ));
            int totalEntries = seasonalPage
                    ? snapshots.size()
                    : buildEventOverlayEntries(session, snapshots).size();
            int maxPage = maxOverlayPage(totalEntries, PAGED_OVERLAY_MISSION_SLOTS.length);
            if (seasonalPage) {
                if (session.seasonalPage < maxPage) {
                    session.seasonalPage++;
                }
                openSeasonalMainPage(player, session);
            } else {
                if (session.eventsPage < maxPage) {
                    session.eventsPage++;
                }
                openEventsMainPage(player, session);
            }
            return;
        }
        if (contains(PAGED_OVERLAY_BACK_SLOTS, slot)) {
            openMainPage(player, session);
            return;
        }
        if (contains(PAGED_OVERLAY_INFO_SLOTS, slot)) {
            if (seasonalPage) {
                openSeasonalMainPage(player, session);
            } else {
                openEventsMainPage(player, session);
            }
        }
    }

    private MainOpenResult createMainInventory(Holder holder, Session session) {
        TexturedGuiSupport.OpenResult result = createTexturedInventory(
                holder,
                GUI_SIZE,
                configUtils.msg(
                        "city.players.gui.missions.main.title",
                        "<gold>Quest</gold> <gray>{town}</gray>",
                        Placeholder.unparsed("town", session.townName)
                ),
                plugin.getConfig().getString("city.missions.itemsadder.title_plain", "Quest"),
                plugin.getConfig().getInt("city.missions.itemsadder.texture_offset", -8),
                QUEST_MAIN_FONT_IMAGE_ID,
                "quest-main"
        );
        return new MainOpenResult(result.inventory(), result.texturedWrapper());
    }

    private void fillButtonGroup(
            Inventory inventory,
            ItemStack transparentBase,
            int[] slots,
            String path,
            String fallback,
            List<Component> lore
    ) {
        TexturedGuiSupport.fillGroup(inventory, slots, overlayButton(transparentBase, path, fallback, lore));
    }

    private ItemStack overlayButton(ItemStack transparentBase, String path, String fallback, List<Component> lore) {
        return TexturedGuiSupport.overlayButton(
                transparentBase,
                noItalic(configUtils.msg(path, fallback)),
                lore == null ? List.of() : lore.stream().map(this::noItalic).toList(),
                new ItemStack(Material.PAPER)
        );
    }

    private Component noItalic(Component component) {
        return component == null ? Component.empty() : component.decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    private ItemStack transparentButtonBase() {
        return TexturedGuiSupport.transparentButtonBase(plugin, TRANSPARENT_BUTTON_ID);
    }

    private TexturedGuiSupport.OpenResult createTexturedInventory(
            Holder holder,
            int size,
            Component fallbackTitle,
            String plainTitle,
            int textureOffset,
            String fontImageId,
            String logPrefix
    ) {
        return TexturedGuiSupport.createInventory(
                plugin,
                holder,
                holder::bind,
                size,
                fallbackTitle,
                plainTitle,
                plugin.getConfig().getInt("city.missions.itemsadder.title_offset", 0),
                textureOffset,
                fontImageId,
                logPrefix
        );
    }

    private void renderDailyPreview(Inventory inventory, Holder holder, Session session, ItemStack transparentBase) {
        List<DailyPreviewEntry> entries = buildDailyMainEntries(session);
        int page = Math.max(0, Math.min(session.mainDailyPage, maxDailyMainPage(session)));
        session.mainDailyPage = page;
        holder.mainDailyEntryBySlot.clear();

        int start = page * MAIN_DAILY_MISSION_SLOTS.length;
        for (int i = 0; i < MAIN_DAILY_MISSION_SLOTS.length; i++) {
            int index = start + i;
            int slot = MAIN_DAILY_MISSION_SLOTS[i];
            if (index < entries.size()) {
                DailyPreviewEntry entry = entries.get(index);
                inventory.setItem(slot, entry.item());
                holder.mainDailyEntryBySlot.put(slot, entry);
                continue;
            }
            inventory.setItem(slot, null);
        }

        inventory.setItem(MAIN_DAILY_PREV_SLOT, overlayButton(
                transparentBase,
                "city.players.gui.missions.main.daily_prev",
                "<white> </white>",
                framedLore(
                        "<gray>Scorri le missioni giornaliere gia passate in rassegna.</gray>",
                        "<yellow>✦</yellow> <white>Torna alla pagina precedente del diario</white>",
                        "<green>➜ Clicca per cambiare pagina.</green>"
                )
        ));
        inventory.setItem(MAIN_DAILY_NEXT_SLOT, overlayButton(
                transparentBase,
                "city.players.gui.missions.main.daily_next",
                "<white> </white>",
                framedLore(
                        "<gray>Altre missioni giornaliere aspettano piu avanti.</gray>",
                        "<yellow>✦</yellow> <white>Apri la pagina successiva del diario</white>",
                        "<green>➜ Clicca per continuare.</green>"
                )
        ));
    }

    private List<DailyPreviewEntry> buildDailyMainEntries(Session session) {
        List<DailyPreviewEntry> entries = new ArrayList<>();
        List<ChallengeSnapshot> daily = rankForMissionHome(filterForTab(
                challengeService.listActiveForTownForPlayer(session.townId, session.viewerId),
                MissionTab.DAILY
        ));
        int standardCount = Math.min(MAIN_DAILY_MISSION_SLOTS.length, daily.size());
        for (int i = 0; i < standardCount; i++) {
            ChallengeSnapshot snapshot = daily.get(i);
            entries.add(new DailyPreviewEntry(
                    DailyPreviewEntryKind.STANDARD,
                    challengeItem(
                    snapshot,
                    challengeService.rewardPreviewForInstance(snapshot.instanceId()),
                    session.townId,
                    "Daily"
                    ),
                    snapshot,
                    null
            ));
        }
        for (DailyRacePreview racePreview : buildDailyRacePreviews(session)) {
            entries.add(new DailyPreviewEntry(
                    racePreview.kind(),
                    racePreviewItem(racePreview, session.townId),
                    racePreview.snapshot(),
                    racePreview
            ));
        }
        return entries;
    }

    private int maxDailyMainPage(Session session) {
        List<DailyPreviewEntry> entries = buildDailyMainEntries(session);
        if (entries.isEmpty()) {
            return 0;
        }
        return Math.max(0, (entries.size() - 1) / MAIN_DAILY_MISSION_SLOTS.length);
    }

    private List<DailyRacePreview> buildDailyRacePreviews(Session session) {
        ZonedDateTime now = ZonedDateTime.now(challengeService.settings().timezone());
        List<java.time.LocalTime> windows = new ArrayList<>(challengeService.settings().raceWindows());
        if (windows.isEmpty()) {
            windows.add(java.time.LocalTime.of(18, 30));
            windows.add(java.time.LocalTime.of(21, 30));
        } else if (windows.size() == 1) {
            windows.add(java.time.LocalTime.of(21, 30));
        }
        windows.sort(java.time.LocalTime::compareTo);
        List<DailyRacePreview> previews = new ArrayList<>();
        for (int i = 0; i < Math.min(2, windows.size()); i++) {
            java.time.LocalTime window = windows.get(i);
            ChallengeMode mode = i == 0 ? ChallengeMode.DAILY_SPRINT_1830 : ChallengeMode.DAILY_SPRINT_2130;
            ChallengeSnapshot snapshot = challengeService.currentRaceSnapshotForTownForPlayer(session.townId, session.viewerId, mode)
                    .orElse(null);
            ZonedDateTime start = now.withHour(window.getHour()).withMinute(window.getMinute()).withSecond(0).withNano(0);
            ZonedDateTime revealAt = start.minusMinutes(10);
            ZonedDateTime end = start.plusMinutes(45);
            if (!now.isBefore(revealAt) && !now.isAfter(end) && snapshot != null) {
                previews.add(new DailyRacePreview(
                        DailyPreviewEntryKind.RACE_ACTIVE,
                        mode,
                        window,
                        Math.max(0L, java.time.Duration.between(now, end).getSeconds()),
                        snapshot
                ));
                continue;
            }
            if (!now.isBefore(revealAt) && !now.isAfter(end)) {
                previews.add(new DailyRacePreview(
                        DailyPreviewEntryKind.RACE_ACTIVE,
                        mode,
                        window,
                        Math.max(0L, java.time.Duration.between(now, end).getSeconds()),
                        null
                ));
                continue;
            }
            if (now.isBefore(revealAt)) {
                previews.add(new DailyRacePreview(
                        DailyPreviewEntryKind.RACE_HIDDEN,
                        mode,
                        window,
                        Math.max(0L, java.time.Duration.between(now, revealAt).getSeconds()),
                        null
                ));
                continue;
            }
            ZonedDateTime nextReveal = revealAt;
            if (!now.isBefore(revealAt)) {
                nextReveal = nextReveal.plusDays(1);
            }
            previews.add(new DailyRacePreview(
                    DailyPreviewEntryKind.RACE_CLOSED,
                    mode,
                    window,
                    Math.max(0L, java.time.Duration.between(now, nextReveal).getSeconds()),
                    snapshot
            ));
        }
        return previews;
    }

    private ItemStack racePreviewItem(DailyRacePreview preview, int townId) {
        if (preview.snapshot() != null && preview.kind() != DailyPreviewEntryKind.RACE_HIDDEN) {
            return challengeItem(
                    preview.snapshot(),
                    challengeService.rewardPreviewForInstance(preview.snapshot().instanceId()),
                    townId,
                    "Race " + preview.window().toString()
            );
        }
        Material material = preview.kind() == DailyPreviewEntryKind.RACE_HIDDEN
                ? Material.RED_STAINED_GLASS_PANE
                : (preview.kind() == DailyPreviewEntryKind.RACE_ACTIVE
                ? Material.BLUE_STAINED_GLASS_PANE
                : Material.ORANGE_STAINED_GLASS_PANE);
        String title = preview.kind() == DailyPreviewEntryKind.RACE_HIDDEN
                ? "<gradient:#ff9a9e:#f6416c><bold>✦ RACE NASCOSTA ✦</bold></gradient>"
                : (preview.kind() == DailyPreviewEntryKind.RACE_ACTIVE
                ? "<gradient:#7dd3fc:#38bdf8><bold>✦ RACE IN APERTURA ✦</bold></gradient>"
                : "<gradient:#ffd76a:#ff8c42><bold>✦ RACE IN ATTESA ✦</bold></gradient>");
        String countdownLabel = preview.kind() == DailyPreviewEntryKind.RACE_HIDDEN
                ? "Rivelazione tra"
                : (preview.kind() == DailyPreviewEntryKind.RACE_ACTIVE
                ? "Chiusura tra"
                : "Prossima finestra tra");
        String description = preview.kind() == DailyPreviewEntryKind.RACE_HIDDEN
                ? "Una sfida competitiva sta per emergere in questa finestra."
                : (preview.kind() == DailyPreviewEntryKind.RACE_ACTIVE
                ? "La race e appena partita. I dettagli completi stanno arrivando."
                : "Questa race non e attiva adesso, ma tornera nel prossimo ciclo.");
        String cta = preview.kind() == DailyPreviewEntryKind.RACE_HIDDEN
                ? "<green>➜ Clicca per vedere il countdown preciso della rivelazione.</green>"
                : (preview.kind() == DailyPreviewEntryKind.RACE_ACTIVE
                ? "<aqua>➜ Aggiorna tra pochi secondi per vedere la race completa.</aqua>"
                : "<gold>➜ Clicca per sapere quando riaprira questa race.</gold>");
        return GuiItemFactory.item(
                material,
                MiniMessageHelper.parse(title),
                framedLore(
                        "<gray>" + description + "</gray>",
                        "<yellow>✦</yellow> <white>Finestra:</white> <gold>" + preview.window() + "</gold>",
                        "<yellow>✦</yellow> <white>" + countdownLabel + ":</white> <aqua>" + formatRemaining(preview.secondsUntilStateChange()) + "</aqua>",
                        cta
                )
        );
    }

    private void handleDailyRacePreviewClick(Player player, DailyRacePreview preview) {
        if (preview == null) {
            return;
        }
        if (preview.snapshot() != null) {
            Session session = sessionsByViewer.get(player.getUniqueId());
            if (session != null) {
                openChallengeDialog(player, preview.snapshot(), session.townId, "Race " + preview.window());
            }
            return;
        }
        String label = preview.kind() == DailyPreviewEntryKind.RACE_HIDDEN ? "rivelazione" : "prossima apertura";
        player.sendMessage(MiniMessageHelper.parse(
                "<gradient:#ff9a9e:#f6416c><bold>✦ RACE GIORNALIERA ✦</bold></gradient> "
                        + "<gray>Finestra</gray> <gold>" + preview.window() + "</gold> <gray>•</gray> <white>"
                        + label + "</white> <aqua>" + formatRemaining(preview.secondsUntilStateChange()) + "</aqua>"
        ));
    }

    private ItemStack availableBatchPlaceholderItem(MissionTab tab, int slotNumber) {
        String label = tab == MissionTab.WEEKLY ? "Settimanale" : "Mensile";
        return GuiItemFactory.item(
                Material.MAP,
                MiniMessageHelper.parse("<gradient:#8be9fd:#4facfe><bold>✦ SLOT " + slotNumber + " DISPONIBILE ✦</bold></gradient>"),
                framedLore(
                        "<gray>Questo spazio e gia sbloccato per la tua citta.</gray>",
                        "<yellow>✦</yellow> <white>Fronte:</white> <gray>" + label + "</gray>",
                        "<yellow>✦</yellow> <white>Stato:</white> <white>in attesa di missione</white>",
                        "<gold>➜ Torna qui quando il ciclo avra completato il prossimo riordino.</gold>"
                )
        );
    }

    private ItemStack lockedBatchPlaceholderItem(MissionTab tab, int slotNumber) {
        int requiredLevel = requiredLevelForBatchSlot(slotNumber);
        String label = tab == MissionTab.WEEKLY ? "Settimanale" : "Mensile";
        return GuiItemFactory.item(
                Material.GRAY_STAINED_GLASS_PANE,
                MiniMessageHelper.parse("<gray>✦ SLOT " + slotNumber + " BLOCCATO ✦</gray>"),
                framedLore(
                        "<gray>Questo spazio si apre solo quando la citta sale di livello.</gray>",
                        "<yellow>✦</yellow> <white>Fronte:</white> <gray>" + label + "</gray>",
                        "<yellow>✦</yellow> <white>Livello richiesto:</white> <gold>" + requiredLevel + "</gold>",
                        "<red>➜ Fai crescere la citta per sbloccare questo incarico.</red>"
                )
        );
    }

    private int requiredLevelForBatchSlot(int slotNumber) {
        int pairIndex = Math.max(0, slotNumber - 1) / 2;
        return pairIndex <= 0 ? 1 : pairIndex * 10;
    }

    private List<Component> nextWeeklyRefreshLore() {
        ZonedDateTime now = ZonedDateTime.now(challengeService.settings().timezone());
        LocalDate nextMonday = now.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        LocalDateTime nextWeekly = nextMonday.atTime(challengeService.settings().weeklyRollover());
        if (!now.toLocalDateTime().isBefore(nextWeekly)) {
            nextWeekly = nextWeekly.plusWeeks(1);
        }
        return framedLore(
                "<gray>Le sfide settimanali cambiano quando il ciclo della citta riparte.</gray>",
                "<yellow>✦</yellow> <white>Nuova rotazione tra:</white> <aqua>{time}</aqua>".replace("{time}",
                        formatRemaining(java.time.Duration.between(now, nextWeekly.atZone(challengeService.settings().timezone())).getSeconds())),
                "<green>➜ Controlla bene il tempo rimasto prima del reset.</green>"
        );
    }

    private List<Component> nextMonthlyRefreshLore() {
        ZonedDateTime now = ZonedDateTime.now(challengeService.settings().timezone());
        LocalDate firstDay = now.toLocalDate().withDayOfMonth(1);
        LocalDateTime nextMonthly = firstDay.atTime(challengeService.settings().monthlyRollover());
        if (!now.toLocalDateTime().isBefore(nextMonthly)) {
            nextMonthly = nextMonthly.plusMonths(1).withDayOfMonth(1);
        }
        return framedLore(
                "<gray>Le missioni mensili segnano i grandi archi della stagione cittadina.</gray>",
                "<yellow>✦</yellow> <white>Nuova rotazione tra:</white> <gold>{time}</gold>".replace("{time}",
                        formatRemaining(java.time.Duration.between(now, nextMonthly.atZone(challengeService.settings().timezone())).getSeconds())),
                "<green>➜ Tieni d'occhio questo timer per non perdere il cambio ciclo.</green>"
        );
    }

    private List<Component> nextSeasonalRefreshLore() {
        ZonedDateTime now = ZonedDateTime.now(challengeService.settings().timezone());
        LocalDate current = now.toLocalDate();
        LocalDate nextSeasonStart = nextSeasonStart(current);
        LocalDateTime nextSeason = nextSeasonStart.atTime(challengeService.settings().seasonalRollover());
        return framedLore(
                "<gray>Le sfide stagionali cambiano solo quando una nuova fase prende il posto della precedente.</gray>",
                "<yellow>✦</yellow> <white>Nuova stagione tra:</white> <light_purple>{time}</light_purple>".replace("{time}",
                        formatRemaining(java.time.Duration.between(now, nextSeason.atZone(challengeService.settings().timezone())).getSeconds())),
                "<green>➜ Preparati: il cambio stagione riscrive il fronte attivo.</green>"
        );
    }

    private List<Component> nextEventsRefreshLore() {
        ZonedDateTime now = ZonedDateTime.now(challengeService.settings().timezone());
        ZonedDateTime next = nextEventsRefresh(now);
        return framedLore(
                "<gray>Gli eventi si aprono in finestre precise: chi arriva pronto prende il vantaggio.</gray>",
                "<yellow>✦</yellow> <white>Prossima apertura tra:</white> <red>{time}</red>".replace("{time}",
                        formatRemaining(java.time.Duration.between(now, next).getSeconds())),
                "<green>➜ Tieni il timer d'occhio e preparati al momento giusto.</green>"
        );
    }

    private static LocalDate nextSeasonStart(LocalDate date) {
        LocalDate spring = LocalDate.of(date.getYear(), 3, 1);
        LocalDate summer = LocalDate.of(date.getYear(), 6, 1);
        LocalDate autumn = LocalDate.of(date.getYear(), 9, 1);
        LocalDate winter = LocalDate.of(date.getYear(), 12, 1);
        if (date.isBefore(spring)) {
            return spring;
        }
        if (date.isBefore(summer)) {
            return summer;
        }
        if (date.isBefore(autumn)) {
            return autumn;
        }
        if (date.isBefore(winter)) {
            return winter;
        }
        return LocalDate.of(date.getYear() + 1, 3, 1);
    }

    private ZonedDateTime nextEventsRefresh(ZonedDateTime now) {
        List<ZonedDateTime> candidates = new ArrayList<>();
        for (java.time.LocalTime window : challengeService.settings().raceWindows()) {
            ZonedDateTime candidate = now.with(window);
            if (!candidate.isAfter(now)) {
                candidate = candidate.plusDays(1);
            }
            candidates.add(candidate);
        }
        ZonedDateTime weeklyClash = now.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
                .withHour(21)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);
        if (!weeklyClash.isAfter(now)) {
            weeklyClash = weeklyClash.plusWeeks(1);
        }
        candidates.add(weeklyClash);

        ZonedDateTime monthlyCrown = firstSundayOfMonth(now);
        if (!monthlyCrown.isAfter(now)) {
            monthlyCrown = firstSundayOfMonth(now.plusMonths(1).withDayOfMonth(1));
        }
        candidates.add(monthlyCrown);

        return candidates.stream().min(ZonedDateTime::compareTo).orElse(now.plusDays(1));
    }

    private static ZonedDateTime firstSundayOfMonth(ZonedDateTime base) {
        return base.withDayOfMonth(1)
                .with(TemporalAdjusters.firstInMonth(DayOfWeek.SUNDAY))
                .withHour(18)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);
    }

    private static int maxOverlayPage(int totalEntries, int perPage) {
        if (totalEntries <= 0 || perPage <= 0) {
            return 0;
        }
        return Math.max(0, (totalEntries - 1) / perPage);
    }

    private ItemStack challengeItem(
            ChallengeSnapshot snapshot,
            ChallengeRewardPreview rewardPreview,
            int townId,
            String bucketLabel
    ) {
        ChallengeRewardPreview safePreview = rewardPreview == null ? ChallengeRewardPreview.EMPTY : rewardPreview;
        boolean secretStyle = isSecretStyleMission(snapshot);
        String objectiveName = ChallengeObjectivePresentation.objectiveLabel(challengeService.textCatalog(), snapshot.objectiveType());
        List<String> objectiveLines = ChallengeNarrativeFormatter.objectiveDescriptionLines(challengeService.textCatalog(), snapshot);
        ChallengeLoreFormatter.ProgressPhase phase = ChallengeLoreFormatter.phase(
                snapshot.townProgress(),
                snapshot.target(),
                snapshot.excellenceTarget(),
                24
        );
        List<Component> lore = UiLoreStyle.renderFramedComponents(
                buildChallengeLore(snapshot, safePreview, townId, secretStyle, objectiveName, objectiveLines, phase)
        );
        Material icon = secretStyle ? Material.ENDER_EYE : ChallengeObjectivePresentation.icon(snapshot.objectiveType());
        String cardTitle = secretStyle ? snapshot.challengeName() : objectiveName;
        return GuiItemFactory.item(icon, configUtils.msg(
                "city.players.gui.challenges.entry.v2.title",
                "<gradient:#fff07a:#ffb347><bold>✦ {type} ✦</bold></gradient>",
                Placeholder.unparsed("type", cardTitle)
        ), lore);
    }

    private void openChallengeDialog(Player player, ChallengeSnapshot snapshot, int townId, String bucketLabel) {
        if (player == null || snapshot == null) {
            return;
        }
        ChallengeRewardPreview preview = challengeService.rewardPreviewForInstance(snapshot.instanceId());
        boolean secretStyle = isSecretStyleMission(snapshot);
        String objectiveName = ChallengeObjectivePresentation.objectiveLabel(challengeService.textCatalog(), snapshot.objectiveType());
        List<String> objectiveLines = ChallengeNarrativeFormatter.objectiveDescriptionLines(challengeService.textCatalog(), snapshot);
        ChallengeLoreFormatter.ProgressPhase phase = ChallengeLoreFormatter.phase(
                snapshot.townProgress(),
                snapshot.target(),
                snapshot.excellenceTarget(),
                24
        );
        List<UiLoreStyle.StyledLine> lines = buildChallengeLore(
                snapshot,
                preview == null ? ChallengeRewardPreview.EMPTY : preview,
                townId,
                secretStyle,
                objectiveName,
                objectiveLines,
                phase
        );
        String dialogTitle = secretStyle
                ? snapshot.challengeName()
                : (snapshot.challengeName() != null && !snapshot.challengeName().isBlank()
                ? snapshot.challengeName()
                : objectiveName);
        DialogBase base = DialogBase.builder(configUtils.msg(
                        "city.players.gui.challenges.dialog.title",
                        "<gold>{title}</gold>",
                        Placeholder.unparsed("title", dialogTitle)
                ))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(UiLoreStyle.renderLooseStyledComponents(lines).stream().map(DialogBody::plainMessage).toList())
                .build();
        Dialog dialog = Dialog.create(factory -> {
            ActionButton closeButton = ActionButton.create(
                    configUtils.msg("city.players.gui.challenges.dialog.close", "<red>Chiudi</red>"),
                    null,
                    180,
                    null
            );
            factory.empty().base(base).type(DialogType.notice(closeButton));
        });
        DialogViewUtils.showDialog(
                plugin,
                player,
                dialog,
                configUtils.msg("city.players.gui.challenges.errors.dialog_unavailable", "<red>Dialog non disponibile su questo server.</red>"),
                "challenge-dialog"
        );
    }

    private List<EventOverlayEntry> buildEventOverlayEntries(Session session, List<ChallengeSnapshot> raceSnapshots) {
        List<EventOverlayEntry> entries = new ArrayList<>();
        java.util.Set<String> linkedInstanceIds = new java.util.HashSet<>();
        for (StaffEventService.EventCardView view : staffEventService.listVisibleEventCards(session.townId, session.viewerId)) {
            entries.add(new EventOverlayEntry(view, view.linkedSnapshot().orElse(null)));
            if (view.linkedInstanceId() != null && !view.linkedInstanceId().isBlank()) {
                linkedInstanceIds.add(view.linkedInstanceId());
            }
        }
        for (ChallengeSnapshot snapshot : raceSnapshots) {
            if (snapshot != null && !linkedInstanceIds.contains(snapshot.instanceId())) {
                entries.add(new EventOverlayEntry(null, snapshot));
            }
        }
        return List.copyOf(entries);
    }

    private ItemStack staffEventItem(StaffEventService.EventCardView view, Session session) {
        if (view == null) {
            return GuiItemFactory.fillerPane();
        }
        StaffEvent event = view.event();
        Material material = event.kind() == StaffEventKind.AUTO_RACE ? Material.FIREWORK_STAR : Material.BRICKS;
        List<UiLoreStyle.StyledLine> lines = new ArrayList<>();
        addChallengeSection(lines, "Stato evento:");
        addChallengeBullet(lines, UiLoreStyle.LineType.STATUS, "Tipo: " + (event.kind() == StaffEventKind.AUTO_RACE ? "Evento auto race" : "Contest build"));
        addChallengeBullet(lines, UiLoreStyle.LineType.STATUS, "Stato: " + staffEventService.playerStatusLabel(event));
        if (event.subtitle() != null && !event.subtitle().isBlank()) {
            addChallengeBullet(lines, UiLoreStyle.LineType.INFO, event.subtitle());
        }
        addChallengeSection(lines, "Dettagli:");
        addChallengeBullet(lines, UiLoreStyle.LineType.TIME, "Inizio: " + formatInstantForPlayer(event.startAt()));
        addChallengeBullet(lines, UiLoreStyle.LineType.TIME, "Fine: " + formatInstantForPlayer(event.endAt()));
        if (event.description() != null && !event.description().isBlank()) {
            addChallengeBullet(lines, UiLoreStyle.LineType.INFO, event.description());
        }
        if (event.kind() == StaffEventKind.JUDGED_BUILD) {
            addChallengeSection(lines, "Submission:");
            if (view.submission() == null) {
                addChallengeBullet(lines, UiLoreStyle.LineType.WARNING, "La tua citta non ha ancora inviato una partecipazione");
            } else {
                addChallengeBullet(lines, UiLoreStyle.LineType.REWARD, "Inviata da: " + shortPlayerName(view.submission().submittedBy()));
                addChallengeBullet(lines, UiLoreStyle.LineType.STATUS, "Review: " + view.submission().reviewStatus().name());
                if (view.submission().placement() != null) {
                    addChallengeBullet(lines, UiLoreStyle.LineType.REWARD, "Placement: #" + view.submission().placement());
                }
            }
        } else if (view.linkedSnapshot().isPresent()) {
            addChallengeSection(lines, "Race:");
            addChallengeBullet(lines, UiLoreStyle.LineType.ACTION, "La challenge collegata e disponibile");
        }
        addChallengeSection(lines, "Azione:");
        if (event.kind() == StaffEventKind.JUDGED_BUILD && event.status() == StaffEventStatus.ACTIVE) {
            addChallengeBullet(lines, UiLoreStyle.LineType.ACTION, "Clicca per aprire il dialog e inviare la build");
        } else if (view.linkedSnapshot().isPresent()) {
            addChallengeBullet(lines, UiLoreStyle.LineType.ACTION, "Clicca per aprire la challenge evento");
        } else {
            addChallengeBullet(lines, UiLoreStyle.LineType.ACTION, "Clicca per vedere i dettagli dell'evento");
        }
        return GuiItemFactory.item(
                material,
                configUtils.msg("city.players.gui.events.staff.title", "<gold>{title}</gold>",
                        Placeholder.unparsed("title", event.title())),
                UiLoreStyle.renderFramedComponents(lines)
        );
    }

    private void openStaffEventDialog(Player player, Session session, StaffEventService.EventCardView view) {
        if (player == null || view == null) {
            return;
        }
        if (view.event().kind() == StaffEventKind.AUTO_RACE && view.linkedSnapshot().isPresent()) {
            openChallengeDialog(player, view.linkedSnapshot().get(), session.townId, "Event");
            return;
        }
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(configUtils.msg("city.players.gui.events.staff.dialog.status",
                "<gray>Stato:</gray> <white>{value}</white>",
                Placeholder.unparsed("value", staffEventService.playerStatusLabel(view.event())))));
        body.add(DialogBody.plainMessage(configUtils.msg("city.players.gui.events.staff.dialog.window",
                "<gray>Finestra:</gray> <white>{start}</white> <gray>→</gray> <white>{end}</white>",
                Placeholder.unparsed("start", formatInstantForPlayer(view.event().startAt())),
                Placeholder.unparsed("end", formatInstantForPlayer(view.event().endAt())))));
        if (view.event().description() != null && !view.event().description().isBlank()) {
            body.add(DialogBody.plainMessage(Component.text(UiLoreStyle.sanitizeContent(view.event().description()))));
        }
        if (view.submission() != null) {
            body.add(DialogBody.plainMessage(configUtils.msg("city.players.gui.events.staff.dialog.submission",
                    "<gray>Hai gia inviato una submission.</gray> <white>Review:</white> <white>{value}</white>",
                    Placeholder.unparsed("value", view.submission().reviewStatus().name()))));
        }
        DialogBase base = DialogBase.builder(configUtils.msg(
                        "city.players.gui.events.staff.dialog.title",
                        "<gold>{title}</gold>",
                        Placeholder.unparsed("title", view.event().title())
                ))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(body)
                .build();
        Dialog dialog = Dialog.create(factory -> {
            List<ActionButton> actions = new ArrayList<>();
            if (view.event().kind() == StaffEventKind.JUDGED_BUILD && view.event().status() == StaffEventStatus.ACTIVE) {
                actions.add(ActionButton.create(
                        configUtils.msg("city.players.gui.events.staff.dialog.submit", "<green>INVIA PARTECIPAZIONE</green>"),
                        null,
                        180,
                        DialogAction.customClick((response, audience) -> {
                            if (audience instanceof Player actor) {
                                openStaffEventSubmitDialog(actor, session, view);
                            }
                        }, CLICK_OPTIONS)
                ));
            }
            ActionButton close = ActionButton.create(configUtils.msg(
                    "city.players.gui.challenges.dialog.close", "<red>Chiudi</red>"
            ), null, 180, null);
            if (actions.isEmpty()) {
                factory.empty().base(base).type(DialogType.notice(close));
            } else {
                factory.empty().base(base).type(DialogType.multiAction(actions, close, 2));
            }
        });
        DialogViewUtils.showDialog(
                plugin,
                player,
                dialog,
                configUtils.msg("city.players.gui.challenges.errors.dialog_unavailable", "<red>Dialog non disponibile su questo server.</red>"),
                "staff-event-dialog"
        );
    }

    private void openStaffEventSubmitDialog(Player player, Session session, StaffEventService.EventCardView view) {
        DialogBase base = DialogBase.builder(configUtils.msg(
                        "city.players.gui.events.staff.submit.title",
                        "<gold>Invia build</gold>"
                ))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(List.of(DialogBody.plainMessage(configUtils.msg(
                        "city.players.gui.events.staff.submit.body",
                        "<gray>La submission salva la tua posizione attuale e una nota breve per lo staff.</gray>"
                ))))
                .inputs(List.of(DialogInputUtils.text(
                        "note",
                        320,
                        configUtils.msg("city.players.gui.events.staff.submit.note", "<yellow>Nota</yellow>"),
                        false,
                        view.submission() == null || view.submission().note() == null ? "" : view.submission().note(),
                        180
                )))
                .build();
        Dialog dialog = Dialog.create(factory -> {
            ActionButton confirm = ActionButton.create(
                    configUtils.msg("city.players.gui.events.staff.submit.confirm", "<green>INVIA</green>"),
                    null,
                    170,
                    DialogAction.customClick((response, audience) -> {
                        if (!(audience instanceof Player actor)) {
                            return;
                        }
                        String note = DialogInputUtils.normalized(response, "note", configUtils);
                        StaffEventService.SubmitResult result = staffEventService.submitBuild(actor, session.townId, view.event().eventId(), note);
                        if (!result.success()) {
                            actor.sendMessage(configUtils.msg("city.players.gui.events.staff.submit.failed",
                                    "<red>Invio non riuscito: {reason}</red>",
                                    Placeholder.unparsed("reason", result.reason())));
                            return;
                        }
                        actor.sendMessage(configUtils.msg("city.players.gui.events.staff.submit.success",
                                "<green>Partecipazione inviata. Puoi aggiornarla finche la finestra resta aperta.</green>"));
                        openEventsMainPage(actor, session);
                    }, CLICK_OPTIONS)
            );
            ActionButton cancel = ActionButton.create(configUtils.msg(
                    "city.players.gui.events.staff.submit.cancel", "<red>Annulla</red>"
            ), null, 170, null);
            factory.empty().base(base).type(DialogType.multiAction(List.of(confirm), cancel, 2));
        });
        DialogViewUtils.showDialog(
                plugin,
                player,
                dialog,
                configUtils.msg("city.players.gui.challenges.errors.dialog_unavailable", "<red>Dialog non disponibile su questo server.</red>"),
                "staff-event-submit"
        );
    }

    private List<UiLoreStyle.StyledLine> buildChallengeLore(
            ChallengeSnapshot snapshot,
            ChallengeRewardPreview preview,
            int townId,
            boolean secretStyle,
            String objectiveName,
            List<String> objectiveLines,
            ChallengeLoreFormatter.ProgressPhase phase
    ) {
        List<UiLoreStyle.StyledLine> lore = new ArrayList<>();
        addChallengeSection(lore, "Stato missione:");
        addChallengeBullet(lore, UiLoreStyle.LineType.STATUS, "Tipo: " + questModeLabel(snapshot.mode()));
        addChallengeBullet(lore, UiLoreStyle.LineType.STATUS, "Stato: " + challengeStatusLabel(snapshot, phase));
        if (snapshot.cycleKey() != null && !snapshot.cycleKey().isBlank()) {
            addChallengeBullet(lore, UiLoreStyle.LineType.STATUS, "Stagione: " + snapshot.cycleKey());
        }
        weeklyFirstCompletionStatusLine(snapshot, townId)
                .ifPresent(line -> addChallengeBullet(lore, UiLoreStyle.LineType.SPECIAL, line));

        if (secretStyle) {
            addChallengeSection(lore, "Indizi:");
            for (String clue : secretHintLines(snapshot).stream().limit(3).toList()) {
                addChallengeBullet(lore, UiLoreStyle.LineType.SPECIAL, clue);
            }
        } else {
            addChallengeSection(lore, "Obiettivo:");
            addChallengeBullet(lore, UiLoreStyle.LineType.FOCUS, objectiveHeadline(snapshot, objectiveName, objectiveLines));
            String focusLabel = displayFocusLabel(snapshot);
            if (focusLabel != null && !focusLabel.isBlank()) {
                addChallengeBullet(lore, UiLoreStyle.LineType.FOCUS, "Focus: " + focusLabel);
            }
            for (String detail : objectiveDetailLines(objectiveLines)) {
                addChallengeBullet(lore, UiLoreStyle.LineType.INFO, detail);
            }
            if (excellenceInProgress(snapshot)) {
                addChallengeBullet(lore, UiLoreStyle.LineType.SPECIAL,
                        "Fase eccellenza: raggiungi " + snapshot.excellenceTarget());
            }
        }

        appendChallengeProgressSection(lore, snapshot, townId, phase);
        appendChallengeRewardsSection(lore, snapshot, preview);
        appendChallengeTimeSection(lore, snapshot);
        appendChallengeActionSection(lore, snapshot, secretStyle);
        return List.copyOf(lore);
    }

    private void appendChallengeProgressSection(
            List<UiLoreStyle.StyledLine> lore,
            ChallengeSnapshot snapshot,
            int townId,
            ChallengeLoreFormatter.ProgressPhase phase
    ) {
        boolean race = snapshot.mode() != null && snapshot.mode().race();
        if (race && snapshot.status() == ChallengeInstanceStatus.COMPLETED) {
            addChallengeSection(lore, "Esito:");
            String winner = winnerLabel(snapshot);
            if (snapshot.winnerTownId() != null && snapshot.winnerTownId() == townId) {
                addChallengeBullet(lore, UiLoreStyle.LineType.REWARD, "La tua citta ha vinto la race");
            } else {
                addChallengeBullet(lore, UiLoreStyle.LineType.WARNING, "Gara non piu completabile");
            }
            addChallengeBullet(lore, UiLoreStyle.LineType.STATUS, "Vincitore: " + winner);
            addChallengeBullet(lore, UiLoreStyle.LineType.STATUS,
                    "Tua citta: " + snapshot.townProgress() + " / " + snapshot.target());
            return;
        }
        if (race && snapshot.status() == ChallengeInstanceStatus.EXPIRED) {
            addChallengeSection(lore, "Esito:");
            addChallengeBullet(lore, UiLoreStyle.LineType.WARNING, "Nessuna citta ha chiuso la race in tempo");
            addChallengeBullet(lore, UiLoreStyle.LineType.STATUS,
                    "Tua citta: " + snapshot.townProgress() + " / " + snapshot.target());
            return;
        }
        if (race) {
            addChallengeSection(lore, "Classifica:");
            addChallengeBullet(lore, UiLoreStyle.LineType.STATUS, "Vincitore: nessuno");
            addChallengeBullet(lore, UiLoreStyle.LineType.STATUS,
                    "Tua citta: " + snapshot.townProgress() + " / " + snapshot.target());
            addChallengeBullet(lore, UiLoreStyle.LineType.STATUS,
                    "Avanzamento: " + progressBarLine(phase.percent(), 12) + " " + phase.percent() + "%");
            return;
        }
        if (snapshot.townExcellenceCompleted()) {
            addChallengeSection(lore, "Esito:");
            addChallengeBullet(lore, UiLoreStyle.LineType.REWARD,
                    "Base completata: " + snapshot.target() + " / " + snapshot.target());
            addChallengeBullet(lore, UiLoreStyle.LineType.SPECIAL,
                    "Eccellenza completata: " + excellenceCurrent(snapshot) + " / " + excellenceTotal(snapshot));
            addChallengeBullet(lore, UiLoreStyle.LineType.REWARD, "Missione chiusa con successo");
            return;
        }
        if (excellenceInProgress(snapshot)) {
            addChallengeSection(lore, "Progresso:");
            addChallengeBullet(lore, UiLoreStyle.LineType.STATUS,
                    "Base completata: " + snapshot.target() + " / " + snapshot.target());
            addChallengeBullet(lore, UiLoreStyle.LineType.SPECIAL,
                    "Eccellenza: " + excellenceCurrent(snapshot) + " / " + excellenceTotal(snapshot));
            addChallengeBullet(lore, UiLoreStyle.LineType.WARNING,
                    "Mancano: " + Math.max(0, excellenceTotal(snapshot) - excellenceCurrent(snapshot)));
            addChallengeBullet(lore, UiLoreStyle.LineType.STATUS,
                    "Avanzamento: " + progressBarLine(phase.percent(), 12) + " " + phase.percent() + "%");
            return;
        }
        if (snapshot.townCompleted()) {
            addChallengeSection(lore, "Esito:");
            addChallengeBullet(lore, UiLoreStyle.LineType.REWARD,
                    "Completato: " + snapshot.target() + " / " + snapshot.target());
            addChallengeBullet(lore, UiLoreStyle.LineType.REWARD, "Missione chiusa con successo");
            return;
        }
        addChallengeSection(lore, "Progresso:");
        addChallengeBullet(lore, UiLoreStyle.LineType.STATUS,
                "Completato: " + snapshot.townProgress() + " / " + snapshot.target());
        addChallengeBullet(lore, UiLoreStyle.LineType.WARNING,
                "Mancano: " + Math.max(0, snapshot.target() - snapshot.townProgress()));
        addChallengeBullet(lore, UiLoreStyle.LineType.STATUS,
                "Avanzamento: " + progressBarLine(phase.percent(), 12) + " " + phase.percent() + "%");
    }

    private void appendChallengeRewardsSection(
            List<UiLoreStyle.StyledLine> lore,
            ChallengeSnapshot snapshot,
            ChallengeRewardPreview preview
    ) {
        String title;
        if (snapshot.mode() != null && snapshot.mode().race() && snapshot.status() == ChallengeInstanceStatus.COMPLETED) {
            title = "Ricompense del vincitore:";
        } else if (excellenceInProgress(snapshot)) {
            title = "Ricompense base:";
        } else if (snapshot.townCompleted()) {
            title = "Ricompense ottenute:";
        } else {
            title = "Ricompense:";
        }
        addChallengeSection(lore, title);
        for (UiLoreStyle.StyledLine line : rewardLines(preview)) {
            lore.add(line);
        }
        if (snapshot.excellenceTarget() > snapshot.target() && !snapshot.townExcellenceCompleted()) {
            addChallengeBullet(lore, UiLoreStyle.LineType.SPECIAL,
                    "Bonus eccellenza disponibile completando la seconda fase");
        }
    }

    private void appendChallengeTimeSection(List<UiLoreStyle.StyledLine> lore, ChallengeSnapshot snapshot) {
        addChallengeSection(lore, "Tempo:");
        if (snapshot.mode() != null && snapshot.mode().race()) {
            if (snapshot.status() == ChallengeInstanceStatus.ACTIVE) {
                addChallengeBullet(lore, UiLoreStyle.LineType.TIME,
                        "Finestra aperta per: " + formatRemaining(snapshot.secondsRemaining()));
            } else {
                addChallengeBullet(lore, UiLoreStyle.LineType.TIME,
                        "La prossima race arrivera nel prossimo ciclo");
            }
            return;
        }
        if (snapshot.townCompleted()) {
            if (snapshot.secondsRemaining() > 0L) {
                addChallengeBullet(lore, UiLoreStyle.LineType.TIME,
                        "Resta visibile per: " + formatRemaining(snapshot.secondsRemaining()));
            } else {
                addChallengeBullet(lore, UiLoreStyle.LineType.TIME,
                        "Ciclo terminato o missione gia riscossa");
            }
            return;
        }
        addChallengeBullet(lore, UiLoreStyle.LineType.TIME,
                "Scade tra: " + formatRemaining(snapshot.secondsRemaining()));
    }

    private void appendChallengeActionSection(
            List<UiLoreStyle.StyledLine> lore,
            ChallengeSnapshot snapshot,
            boolean secretStyle
    ) {
        if (snapshot.mode() != null && snapshot.mode().race()
                && snapshot.status() != ChallengeInstanceStatus.ACTIVE) {
            return;
        }
        if (snapshot.townExcellenceCompleted() || (snapshot.townCompleted() && snapshot.excellenceTarget() <= snapshot.target())) {
            return;
        }
        addChallengeSection(lore, "Azione:");
        if (snapshot.mode() != null && snapshot.mode().race()) {
            addChallengeBullet(lore, UiLoreStyle.LineType.ACTION,
                    "Completa la race prima delle altre citta");
            return;
        }
        if (secretStyle) {
            addChallengeBullet(lore, UiLoreStyle.LineType.ACTION,
                    "Segui gli indizi per completare la missione");
            return;
        }
        if (excellenceInProgress(snapshot)) {
            addChallengeBullet(lore, UiLoreStyle.LineType.ACTION,
                    "Completa l'eccellenza per ottenere il bonus extra");
            return;
        }
        addChallengeBullet(lore, UiLoreStyle.LineType.ACTION,
                "Completa la missione per ottenere le ricompense");
    }

    private List<UiLoreStyle.StyledLine> rewardLines(ChallengeRewardPreview preview) {
        List<UiLoreStyle.StyledLine> lines = new ArrayList<>();
        if (preview.xpCity() > 0.0D) {
            lines.add(UiLoreStyle.line(UiLoreStyle.LineType.REWARD,
                    "• +" + (int) Math.round(preview.xpCity()) + " XP citta"));
        }
        if (preview.moneyCity() > 0.0D) {
            lines.add(UiLoreStyle.line(UiLoreStyle.LineType.REWARD,
                    "• +" + format(preview.moneyCity()) + " Monete"));
        }
        for (ChallengeRewardPreview.RewardItem item : preview.rewardItems()) {
            lines.add(UiLoreStyle.line(UiLoreStyle.LineType.REWARD,
                    "• " + item.amount() + "x " + ChallengeLoreFormatter.materialLabel(item.material())));
        }
        for (String description : rewardCommandDescriptionPlainLines(preview)) {
            lines.add(UiLoreStyle.line(UiLoreStyle.LineType.REWARD, "• " + description));
        }
        if (preview.personalXpCityBonus() > 0.0D) {
            lines.add(UiLoreStyle.line(UiLoreStyle.LineType.SPECIAL,
                    "• Bonus personale XP citta: +" + format(preview.personalXpCityBonus()) + "%"));
        }
        if (lines.isEmpty()) {
            lines.add(UiLoreStyle.line(UiLoreStyle.LineType.REWARD, "• Ricompensa speciale"));
        }
        return List.copyOf(lines);
    }

    private List<String> rewardCommandDescriptionPlainLines(ChallengeRewardPreview preview) {
        if (preview == null || preview.commandKeys() == null || preview.commandKeys().isEmpty()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        Map<String, Boolean> seen = new HashMap<>();
        for (String key : preview.commandKeys()) {
            if (key == null || key.isBlank()) {
                continue;
            }
            String normalized = key.trim().toLowerCase(java.util.Locale.ROOT);
            if (normalized.endsWith("_broadcast")) {
                continue;
            }
            if (seen.putIfAbsent(normalized, Boolean.TRUE) != null) {
                continue;
            }
            String path = "city.challenges.reward_command_descriptions." + normalized;
            String description = plugin.getConfig().getString(path);
            if (description == null || description.isBlank()) {
                continue;
            }
            lines.add(description);
        }
        return List.copyOf(lines);
    }

    private List<String> objectiveDetailLines(List<String> objectiveLines) {
        if (objectiveLines == null || objectiveLines.size() <= 1) {
            return List.of();
        }
        List<String> details = new ArrayList<>();
        for (String detail : objectiveLines.subList(1, Math.min(objectiveLines.size(), 4))) {
            if (detail != null && !detail.isBlank()) {
                details.add(detail);
            }
        }
        return List.copyOf(details);
    }

    private String objectiveHeadline(ChallengeSnapshot snapshot, String objectiveName, List<String> objectiveLines) {
        String base = objectiveLines == null || objectiveLines.isEmpty()
                ? objectiveName
                : objectiveLines.getFirst();
        if (snapshot.mode() != null && snapshot.mode().race()) {
            String lower = base.toLowerCase(java.util.Locale.ROOT);
            if (!lower.startsWith("prima citta")) {
                base = "Prima citta a " + lowerFirst(base);
            }
        }
        return base;
    }

    private String displayFocusLabel(ChallengeSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        if (snapshot.focusType() == ChallengeFocusType.MATERIAL && snapshot.focusKey() != null && !snapshot.focusKey().isBlank()) {
            Material material = Material.matchMaterial(snapshot.focusKey().trim().toUpperCase(java.util.Locale.ROOT));
            if (material != null) {
                return ChallengeLoreFormatter.materialLabel(material);
            }
        }
        if (snapshot.focusLabel() != null && !snapshot.focusLabel().isBlank()) {
            return snapshot.focusLabel().trim();
        }
        if (snapshot.focusKey() != null && !snapshot.focusKey().isBlank()) {
            return ChallengeLoreFormatter.keyLabel(snapshot.focusKey());
        }
        return null;
    }

    private java.util.Optional<String> weeklyFirstCompletionStatusLine(ChallengeSnapshot snapshot, int townId) {
        if (snapshot == null || snapshot.mode() != ChallengeMode.WEEKLY_STANDARD) {
            return java.util.Optional.empty();
        }
        CityChallengeService.WeeklyFirstCompletionStatus status = challengeService.weeklyFirstCompletionStatus(townId);
        if (!status.enabled()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of("Primo completamento settimanale: " + (status.claimed() ? "riscosso" : "disponibile"));
    }

    private boolean excellenceInProgress(ChallengeSnapshot snapshot) {
        return snapshot != null
                && snapshot.townCompleted()
                && !snapshot.townExcellenceCompleted()
                && snapshot.excellenceTarget() > snapshot.target();
    }

    private int excellenceCurrent(ChallengeSnapshot snapshot) {
        return Math.max(0, snapshot.townProgress() - snapshot.target());
    }

    private int excellenceTotal(ChallengeSnapshot snapshot) {
        return Math.max(1, snapshot.excellenceTarget() - snapshot.target());
    }

    private String winnerLabel(ChallengeSnapshot snapshot) {
        if (snapshot == null || snapshot.winnerTownName() == null || snapshot.winnerTownName().isBlank() || "-".equals(snapshot.winnerTownName())) {
            return "Sconosciuta";
        }
        return snapshot.winnerTownName();
    }

    private void addChallengeSection(List<UiLoreStyle.StyledLine> lore, String title) {
        if (!lore.isEmpty()) {
            lore.add(UiLoreStyle.spacer());
        }
        lore.add(UiLoreStyle.line(UiLoreStyle.LineType.FOCUS, title));
    }

    private void addChallengeBullet(List<UiLoreStyle.StyledLine> lore, UiLoreStyle.LineType type, String text) {
        lore.add(UiLoreStyle.line(type, "• " + text));
    }

    private String progressBarLine(int percent, int width) {
        int safeWidth = Math.max(8, width);
        int safePercent = Math.max(0, Math.min(100, percent));
        int filled = (int) Math.round((safePercent / 100.0D) * safeWidth);
        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < safeWidth; i++) {
            bar.append(i < filled ? '█' : '░');
        }
        bar.append(']');
        return bar.toString();
    }

    private String lowerFirst(String value) {
        if (value == null || value.isBlank()) {
            return value == null ? "" : value;
        }
        if (value.length() == 1) {
            return value.toLowerCase(java.util.Locale.ROOT);
        }
        return Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }

    private boolean isSecretStyleMission(ChallengeSnapshot snapshot) {
        if (snapshot == null) {
            return false;
        }
        if (snapshot.mode() == ChallengeMode.MONTHLY_LEDGER_MYSTERY || snapshot.mode() == ChallengeMode.SEASON_CODEX_HIDDEN_RELIC) {
            return true;
        }
        String id = snapshot.challengeId() == null ? "" : snapshot.challengeId().toLowerCase(java.util.Locale.ROOT);
        return SECRET_QUEST_ID.equalsIgnoreCase(snapshot.challengeId()) || id.startsWith("secret_");
    }

    private List<String> secretHintLines(ChallengeSnapshot snapshot) {
        if (snapshot == null) {
            return List.of(
                    "Questa missione non ti dice tutto subito.",
                    "Prova a esplorare, completare attivita diverse e guardare gli indizi.",
                    "Se una pista non funziona, cambia zona o cambia approccio."
            );
        }
        if (SecretQuestCatalog.hasScript(snapshot.challengeId())) {
            return SecretQuestCatalog.currentHints(snapshot.challengeId(), snapshot.townProgress());
        }
        return switch (snapshot.objectiveType()) {
            case ARCHAEOLOGY_BRUSH -> List.of(
                    "Cerca sabbia o ghiaia sospetta nelle strutture giuste.",
                    "Vai con calma e controlla piu punti diversi.",
                    "Se vi dividete bene, trovate indizi piu in fretta."
            );
            case TRIAL_VAULT_OPEN -> List.of(
                    "Pulisci bene la Trial Chamber prima di fermarti.",
                    "Non tutti i vault utili sono vicini all'ingresso.",
                    "Più esplori, più aumentano le chance di trovare quello giusto."
            );
            case TRANSPORT_DISTANCE -> List.of(
                    "Conta la distanza totale, non andare veloce per forza.",
                    "Continua a muoverti su tratte lunghe e regolari.",
                    "Se ripeti bene il percorso, la missione avanza."
            );
            case RAID_WIN, RARE_MOB_KILL, BOSS_KILL -> List.of(
                    "Preparati bene prima di partire.",
                    "I bersagli rari non si trovano sempre subito.",
                    "Una squadra organizzata vale piu della fretta."
            );
            case OCEAN_ACTIVITY, NETHER_ACTIVITY, DIMENSION_TRAVEL -> List.of(
                    "Questa missione si completa nelle zone piu pericolose.",
                    "Una spedizione lunga rende piu di una corsa veloce.",
                    "Continua a esplorare e a cambiare area."
            );
            case STRUCTURE_DISCOVERY -> List.of(
                    "Le mappe aiutano piu della fortuna.",
                    "Cerca fuori dalle zone gia esplorate.",
                    "Se vi dividete le direzioni, trovate prima le strutture."
            );
            case PLACE_BLOCK, CONSTRUCTION, REDSTONE_AUTOMATION, ITEM_CRAFT -> List.of(
                    "Non basta avere i materiali: devi usarli davvero.",
                    "Prepara una piccola linea di lavoro stabile.",
                    "Quando la build o la macchina prende forma, la missione avanza."
            );
            case FOOD_CRAFT, BREW_POTION, VILLAGER_TRADE, ECONOMY_ADVANCED, RESOURCE_CONTRIBUTION, VAULT_DELIVERY -> List.of(
                    "Conta quello che produci e consegni davvero.",
                    "Organizzare bene risorse e scambi fa la differenza.",
                    "Se tutta la citta collabora, i numeri salgono molto piu in fretta."
            );
            case BLOCK_MINE -> List.of(
                    "Le vene migliori spesso sono lontane dalla base.",
                    "Scava in piu punti invece di insistere sempre nello stesso.",
                    "Con un piano trovi molto piu di quanto trovi a caso."
            );
            case SECRET_QUEST -> List.of(
                    "Gli indizi veri si trovano giocando, non leggendo la descrizione.",
                    "Prova attivita diverse e confronta quello che avete fatto.",
                    "Se piu piste sembrano collegate, probabilmente sei vicino alla soluzione."
            );
            default -> List.of(
                    "Questa missione non ti dice tutto subito.",
                    "Prova a esplorare, completare attivita diverse e guardare gli indizi.",
                    "Se una pista non funziona, cambia zona o cambia approccio."
            );
        };
    }

    private String secretQuestPhaseLabel(ChallengeSnapshot snapshot) {
        if (snapshot == null) {
            return "Fasi della missione";
        }
        if (!SecretQuestCatalog.hasScript(snapshot.challengeId())) {
            return "Missione nascosta";
        }
        int total = Math.max(1, SecretQuestCatalog.stageCount(snapshot.challengeId()));
        int current = Math.min(total, Math.max(1, snapshot.townProgress() + 1));
        if (snapshot.townCompleted()) {
            return "Missione completata";
        }
        return "Fase " + current + "/" + total;
    }

    private void appendRewardCommandDescriptions(List<Component> lore, ChallengeRewardPreview preview) {
        if (lore == null || preview == null) {
            return;
        }
        Map<String, Boolean> seen = new HashMap<>();
        for (String key : preview.commandKeys()) {
            appendRewardCommandDescription(lore, key, seen);
        }
    }

    private void appendRewardCommandDescription(List<Component> lore, String key, Map<String, Boolean> seen) {
        if (key == null || key.isBlank()) {
            return;
        }
        String normalized = key.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.endsWith("_broadcast")) {
            return;
        }
        if (seen.putIfAbsent(normalized, Boolean.TRUE) != null) {
            return;
        }
        String path = "city.challenges.reward_command_descriptions." + normalized;
        String description = plugin.getConfig().getString(path);
        if (description == null || description.isBlank()) {
            return;
        }
        lore.add(configUtils.msg(
                "city.players.gui.challenges.entry.v2.reward_command",
                "<gray>|</gray> <gold>-</gold> <white>{description}</white>",
                Placeholder.unparsed("description", description)
        ));
    }

    private List<ChallengeSnapshot> rankForMissionHome(List<ChallengeSnapshot> source) {
        return source.stream()
                .sorted(Comparator
                        .comparingInt((ChallengeSnapshot s) -> s.townCompleted() ? 1 : 0)
                        .thenComparingDouble(this::completionRatio)
                        .thenComparingLong(ChallengeSnapshot::secondsRemaining)
                        .thenComparing(ChallengeSnapshot::instanceId))
                .toList();
    }

    private double completionRatio(ChallengeSnapshot snapshot) {
        if (snapshot.target() <= 0) {
            return 1.0D;
        }
        return -((double) snapshot.townProgress() / (double) snapshot.target());
    }

    private List<MissionCardRow> buildMissionCards(List<ChallengeSnapshot> ranked) {
        List<MissionCardRow> rows = new ArrayList<>(ranked.size());
        for (int index = 0; index < ranked.size(); index++) {
            String bucket = missionBucket(index % 5);
            rows.add(new MissionCardRow(ranked.get(index), bucket));
        }
        return rows;
    }

    private String missionBucket(int indexInPage) {
        if (indexInPage <= 2) {
            return "Consigliata";
        }
        if (indexInPage <= 4) {
            return "Secondaria";
        }
        return "Altre";
    }

    private List<ChallengeSnapshot> filterForTab(List<ChallengeSnapshot> source, MissionTab tab) {
        return source.stream()
                .filter(snapshot -> switch (tab) {
                    case DAILY -> snapshot.mode() == ChallengeMode.DAILY_STANDARD;
                    case WEEKLY -> snapshot.mode().weeklyStandard();
                    case MONTHLY -> snapshot.mode().monthlyLedger();
                    case RACES -> snapshot.mode().m8Race() || snapshot.mode().dailyRace();
                    case CODEX -> snapshot.mode().seasonCodex();
                    case ATLAS -> false;
                })
                .toList();
    }

    private List<ChallengeSnapshot> snapshotsForTab(Session session, MissionTab tab) {
        if (session == null || tab == null) {
            return List.of();
        }
        if (tab == MissionTab.RACES) {
            return challengeService.listVisibleRacesForTownForPlayer(session.townId, session.viewerId);
        }
        return filterForTab(
                challengeService.listActiveForTownForPlayer(session.townId, session.viewerId),
                tab
        );
    }

    private int[] tabSlots(MissionTab tab) {
        return switch (tab) {
            case DAILY -> DAILY_PYRAMID_SLOTS;
            case WEEKLY -> WEEKLY_PYRAMID_SLOTS;
            case MONTHLY -> MONTHLY_PYRAMID_SLOTS;
            case RACES -> EVENT_PYRAMID_SLOTS;
            case CODEX -> SEASONAL_PYRAMID_SLOTS;
            case ATLAS -> new int[]{10, 12, 14, 16, 20, 22};
        };
    }

    private int hiddenDailyRaceCount() {
        int expected = Math.max(0, challengeService.questCapacityProfile().dailyRaceCount());
        List<java.time.LocalTime> windows = new ArrayList<>(challengeService.settings().raceWindows());
        if (windows.isEmpty() || expected <= 0) {
            return 0;
        }
        windows.sort(java.time.LocalTime::compareTo);
        java.time.LocalTime now = java.time.LocalTime.now(challengeService.settings().timezone());
        int revealed = 0;
        for (java.time.LocalTime window : windows) {
            java.time.LocalTime revealAt = window.minusMinutes(10);
            if (!now.isBefore(revealAt)) {
                revealed++;
            }
        }
        return Math.max(0, Math.min(expected, windows.size()) - revealed);
    }

    private ItemStack hiddenRaceItem() {
        return GuiItemFactory.item(
                Material.RED_STAINED_GLASS_PANE,
                configUtils.msg("city.players.gui.challenges.race_hidden.title", "<gradient:#ff9a9e:#f6416c><bold>GARA NASCOSTA</bold></gradient>"),
                framedLore(
                        "<gray>Questa gara non e ancora visibile.</gray>",
                        "<yellow>✦</yellow> <white>Si sblocca solo quando arriva l'orario giusto.</white>",
                        "<yellow>✦</yellow> <white>Prepararti prima ti dara un vantaggio.</white>",
                        "<green>➜ Torna qui quando la gara viene mostrata.</green>"
                )
        );
    }

    private ItemStack hiddenSecretQuestItem() {
        return GuiItemFactory.item(
                Material.BLACK_STAINED_GLASS_PANE,
                configUtils.msg(
                        "city.players.gui.challenges.secret_hidden.title",
                        "<gradient:#6a11cb:#2575fc><bold>MISSIONE SEGRETA</bold></gradient>"
                ),
                framedLore(
                        "<gray>Questa missione resta nascosta finche non trovi gli indizi giusti.</gray>",
                        "<yellow>✦</yellow> <white>Non ricevi la soluzione direttamente.</white>",
                        "<yellow>✦</yellow> <white>Potrebbe servirti esplorare o completare prove diverse.</white>",
                        "<yellow>✦</yellow> <white>Confronta quello che fai con il resto della citta.</white>",
                        "<green>➜ Segui gli indizi e scopri la missione.</green>"
                )
        );
    }

    private static boolean isHiddenSecretQuest(ChallengeSnapshot snapshot) {
        return snapshot != null
                && SECRET_QUEST_ID.equalsIgnoreCase(snapshot.challengeId())
                && !snapshot.townCompleted();
    }

    private ItemStack tabButton(MissionTab tab, boolean active) {
        Material material = switch (tab) {
            case DAILY -> Material.CLOCK;
            case WEEKLY -> Material.BOOK;
            case MONTHLY -> Material.AMETHYST_SHARD;
            case RACES -> Material.FIREWORK_ROCKET;
            case CODEX -> Material.NETHER_STAR;
            case ATLAS -> Material.LECTERN;
        };
        String label = switch (tab) {
            case DAILY -> "Giornaliere";
            case WEEKLY -> "Settimanali";
            case MONTHLY -> "Mensili";
            case RACES -> "Eventi";
            case CODEX -> "Stagionali";
            case ATLAS -> "Story Mode";
        };
        return GuiItemFactory.item(
                material,
                configUtils.msg(
                        active
                                ? "city.players.gui.challenges.tabs.active"
                                : "city.players.gui.challenges.tabs.inactive",
                        active ? "<gradient:#fff07a:#ffb347><bold>✦ {label} ✦</bold></gradient>"
                                : "<gray>✦ {label} ✦</gray>",
                        Placeholder.unparsed("label", label)
                )
        );
    }

    private ItemStack tabHintItem(MissionTab tab, int count, int townId) {
        List<Component> lore = new ArrayList<>();
        lore.add(configUtils.msg(
                "city.players.gui.challenges.tab_hint.count",
                "<yellow>✦</yellow> <white>Missioni visibili:</white> <aqua>{count}</aqua>",
                Placeholder.unparsed("count", Integer.toString(count))
        ));
        if (tab == MissionTab.WEEKLY) {
            CityChallengeService.WeeklyFirstCompletionStatus status = challengeService.weeklyFirstCompletionStatus(townId);
            if (status.enabled()) {
                lore.add(configUtils.msg(
                        "city.players.gui.challenges.tab_hint.weekly_first",
                        "<yellow>✦</yellow> <white>Bonus primo completamento:</white> <gold>{status}</gold>",
                        Placeholder.unparsed("status", status.claimed() ? "Riscosso" : "Disponibile")
                ));
            }
        }
        if (tab == MissionTab.MONTHLY || tab == MissionTab.RACES) {
            challengeService.latestRecapForTown(townId).ifPresent(recap -> appendQuestRecapLore(lore, recap));
        }
        if (tab == MissionTab.CODEX) {
            lore.add(configUtils.msg(
                    "city.players.gui.challenges.tab_hint.codex_points",
                    "<yellow>✦</yellow> <white>Punti stagionali:</white> <light_purple>{points}</light_purple>",
                    Placeholder.unparsed("points", Integer.toString(challengeService.seasonCodexPoints(townId)))
            ));
            lore.add(configUtils.msg(
                    "city.players.gui.challenges.tab_hint.codex_act",
                    "<yellow>✦</yellow> <white>Struttura stagione:</white> <aqua>{act}</aqua>",
                    Placeholder.unparsed("act", challengeService.seasonCodexActLabel(townId))
            ));
        }
        lore.addAll(mmoLore(
                "<green>➜ Usa questa scheda per capire dove conviene spingere adesso.</green>",
                "<dark_gray>╰────────────╯</dark_gray>"
        ));
        return GuiItemFactory.item(
                Material.BOOK,
                configUtils.msg("city.players.gui.challenges.tab_hint.title", "<gradient:#7afcff:#5ee7df><bold>✦ {tab} ✦</bold></gradient>",
                        Placeholder.unparsed("tab", switch (tab) {
                            case DAILY -> "Missioni giornaliere";
                            case WEEKLY -> "Missioni settimanali";
                            case MONTHLY -> "Missioni mensili";
                            case RACES -> "Eventi competitivi";
                            case CODEX -> "Stagionali";
                            case ATLAS -> "Story Mode";
                        })),
                lore
        );
    }

    private List<Component> nextDailyRefreshLore() {
        ZonedDateTime now = ZonedDateTime.now(challengeService.settings().timezone());
        LocalDateTime nextDaily = now.toLocalDate().plusDays(1).atTime(0, 5);
        List<java.time.LocalTime> windows = new ArrayList<>(challengeService.settings().raceWindows());
        if (windows.isEmpty()) {
            windows = List.of(java.time.LocalTime.of(18, 30), java.time.LocalTime.of(21, 30));
        }
        List<Component> lore = new ArrayList<>(framedLore(
                "<gray>Le giornaliere si rinnovano ogni giorno e includono anche le finestre race.</gray>",
                "<yellow>✦</yellow> <white>Nuovo ciclo tra:</white> <aqua>"
                        + formatRemaining(java.time.Duration.between(now, nextDaily.atZone(challengeService.settings().timezone())).getSeconds())
                        + "</aqua>",
                "<yellow>✦</yellow> <white>Race previste oggi:</white> <gold>"
                        + windows.stream().map(Object::toString).reduce((left, right) -> left + ", " + right).orElse("-")
                        + "</gold>",
                "<green>➜ Controlla questa sezione durante il giorno: le race restano qui anche dopo la chiusura.</green>"
        ));
        return lore;
    }

    private void appendQuestRecapLore(List<Component> lore, CycleRecapSnapshot recap) {
        if (lore == null || recap == null) {
            return;
        }
        lore.add(configUtils.msg(
                "city.players.gui.challenges.tab_hint.recap_cycle",
                "<yellow>✦</yellow> <white>Ultimo recap:</white> <aqua>{cycle}</aqua>",
                Placeholder.unparsed("cycle", recap.cycleType().id() + " " + recap.cycleKey())
        ));
        lore.add(configUtils.msg(
                "city.players.gui.challenges.tab_hint.recap_progress",
                "<yellow>✦</yellow> <white>Chiusure:</white> <green>{done}</green><gray>/</gray><white>{total}</white>",
                Placeholder.unparsed("done", Integer.toString(recap.completedChallenges())),
                Placeholder.unparsed("total", Integer.toString(recap.totalChallenges()))
        ));
        if (recap.leaderboardPosition() > 0) {
            lore.add(configUtils.msg(
                    "city.players.gui.challenges.tab_hint.recap_position",
                    "<yellow>✦</yellow> <white>Posizione finale:</white> <gold>#{position}</gold>",
                    Placeholder.unparsed("position", Integer.toString(recap.leaderboardPosition()))
            ));
        }
    }

    private ItemStack storyModeChapterItem(CityStoryModeService.StoryModeChapterSnapshot chapter, boolean selected) {
        Material material = switch (chapter.state()) {
            case COMPLETED -> Material.LIME_DYE;
            case IN_PROGRESS -> Material.CLOCK;
            case UNLOCKED -> Material.WRITABLE_BOOK;
            case LOCKED -> Material.GRAY_DYE;
        };
        return GuiItemFactory.item(
                material,
                configUtils.msg(
                        "city.players.gui.story_mode.chapter.title",
                        selected
                                ? "<gradient:#7afcff:#5ee7df><bold>✦ {title} ✦</bold></gradient>"
                                : "<white>✦ {title} ✦</white>",
                        Placeholder.unparsed("title", chapter.title())
                ),
                storyModeChapterLore(chapter)
        );
    }

    private ItemStack storyModeQuestItem(
            CityStoryModeService.StoryModeTaskSnapshot quest,
            CityStoryModeService.StoryModeChapterSnapshot chapter
    ) {
        Material material = switch (quest.state()) {
            case COMPLETED -> Material.EMERALD;
            case IN_PROGRESS -> Material.MAP;
            case AVAILABLE -> Material.PAPER;
            case LOCKED -> Material.GRAY_STAINED_GLASS_PANE;
        };
        return GuiItemFactory.item(
                material,
                configUtils.msg(
                        "city.players.gui.story_mode.quest.title",
                        "<gradient:#fff07a:#ffb347><bold>✦ {title} ✦</bold></gradient>",
                        Placeholder.unparsed("title", quest.title())
                ),
                storyModeQuestLore(quest, chapter)
        );
    }

    private ItemStack storyModeSummaryItem(CityStoryModeService.StoryModeChapterSnapshot selected) {
        if (selected == null) {
            return null;
        }
        return GuiItemFactory.item(
                Material.BOOK,
                configUtils.msg("city.players.gui.story_mode.summary.title", "<gradient:#7afcff:#5ee7df><bold>✦ RIEPILOGO CAPITOLO ✦</bold></gradient>"),
                storyModeSummaryLore(selected)
        );
    }

    private ItemStack storyModeRewardItem(CityStoryModeService.StoryModeChapterSnapshot selected) {
        if (selected == null) {
            return null;
        }
        return GuiItemFactory.item(
                Material.CHEST,
                configUtils.msg("city.players.gui.story_mode.reward.title", "<gradient:#fff07a:#ffb347><bold>✦ RICOMPENSE CAPITOLO ✦</bold></gradient>"),
                storyModeRewardLore(selected)
        );
    }

    private ItemStack storyModeFallbackItem(CityStoryModeService.StoryModeDiagnostics diagnostics) {
        return GuiItemFactory.item(
                Material.BARRIER,
                configUtils.msg(
                        "city.players.gui.story_mode.fallback.title",
                        "<gradient:#ff9a9e:#f6416c><bold>✦ STORY MODE NON PRONTA ✦</bold></gradient>"
                ),
                storyModeFallbackLore(diagnostics)
        );
    }

    private ItemStack storyModeNavItem(Material material, String title, String... lines) {
        return GuiItemFactory.item(material, MiniMessageHelper.parse(title), framedLore(lines));
    }

    private List<Component> storyModeInfoLore(
            Session session,
            CityStoryModeService.StoryModeChapterSnapshot selected,
            int maxPage,
            CityStoryModeService.StoryModeDiagnostics diagnostics
    ) {
        List<UiLoreStyle.StyledLine> lore = new ArrayList<>();
        addChallengeSection(lore, "Navigazione:");
        addChallengeBullet(lore, UiLoreStyle.LineType.STATUS,
                "Pagina capitoli: " + (session.storyModeChapterPage + 1) + "/" + (maxPage + 1));
        addChallengeBullet(lore, UiLoreStyle.LineType.STATUS,
                "Catalogo: " + diagnostics.baseChapterCount() + " base / " + diagnostics.eliteChapterCount() + " elite");

        if (selected != null) {
            addChallengeSection(lore, "Capitolo selezionato:");
            addChallengeBullet(lore, UiLoreStyle.LineType.FOCUS, selected.title());
            addChallengeBullet(lore, UiLoreStyle.LineType.STATUS,
                    "Stato: " + storyModeChapterStateLabel(selected.state()));
            addChallengeBullet(lore, UiLoreStyle.LineType.INFO,
                    "Atto: " + selected.act());
        } else {
            addChallengeSection(lore, "Stato render:");
            addChallengeBullet(lore, UiLoreStyle.LineType.WARNING, diagnostics.reason());
        }

        addChallengeSection(lore, "Azione:");
        addChallengeBullet(lore, UiLoreStyle.LineType.ACTION,
                "Clicca per tornare alla schermata principale delle Quest");
        return UiLoreStyle.renderFramedComponents(lore);
    }

    private List<Component> storyModeChapterLore(CityStoryModeService.StoryModeChapterSnapshot chapter) {
        List<UiLoreStyle.StyledLine> lore = new ArrayList<>();
        addChallengeSection(lore, "Stato capitolo:");
        addChallengeBullet(lore, UiLoreStyle.LineType.STATUS, "Atto: " + chapter.act());
        addChallengeBullet(lore, UiLoreStyle.LineType.STATUS,
                "Stato: " + storyModeChapterStateLabel(chapter.state()));
        addChallengeBullet(lore, UiLoreStyle.LineType.STATUS,
                "Avanzamento: " + progressBarLine(chapter.progressPercent(), 12) + " " + chapter.progressPercent() + "%");

        addChallengeSection(lore, "Requisiti:");
        addChallengeBullet(lore, UiLoreStyle.LineType.WARNING,
                "Livello citta richiesto: " + chapter.minCityLevel());
        if (chapter.primaryChapter() != null) {
            addChallengeBullet(lore, UiLoreStyle.LineType.STATUS,
                    "Pilastro: " + chapter.primaryChapter().defaultDisplayName());
        }

        addChallengeSection(lore, "Contenuto:");
        addChallengeBullet(lore, UiLoreStyle.LineType.FOCUS,
                "Obiettivo del capitolo: " + chapter.signatureGoal());
        addChallengeBullet(lore, UiLoreStyle.LineType.INFO, chapter.narrativeBeat());

        addChallengeSection(lore, "Azione:");
        addChallengeBullet(lore, chapter.state() == CityStoryModeService.ChapterState.LOCKED
                        ? UiLoreStyle.LineType.WARNING
                        : UiLoreStyle.LineType.ACTION,
                chapter.state() == CityStoryModeService.ChapterState.LOCKED
                        ? "Completa il capitolo precedente e raggiungi il livello richiesto"
                        : "Clicca per seguire questo capitolo");
        return UiLoreStyle.renderFramedComponents(lore);
    }

    private List<Component> storyModeQuestLore(
            CityStoryModeService.StoryModeTaskSnapshot quest,
            CityStoryModeService.StoryModeChapterSnapshot chapter
    ) {
        List<UiLoreStyle.StyledLine> lore = new ArrayList<>();
        addChallengeSection(lore, "Stato prova:");
        addChallengeBullet(lore, UiLoreStyle.LineType.STATUS,
                "Stato: " + storyModeQuestStateLabel(quest.state()));
        if (chapter != null) {
            addChallengeBullet(lore, UiLoreStyle.LineType.STATUS,
                    "Capitolo attivo: " + chapter.title());
        }

        addChallengeSection(lore, "Obiettivo:");
        addChallengeBullet(lore, UiLoreStyle.LineType.FOCUS, quest.description());

        addChallengeSection(lore, "Progresso:");
        addChallengeBullet(lore, UiLoreStyle.LineType.STATUS,
                "Tracciamento: " + quest.progressLabel());
        addChallengeBullet(lore, UiLoreStyle.LineType.STATUS,
                "Avanzamento: " + progressBarLine(quest.progressPercent(), 12) + " " + quest.progressPercent() + "%");

        addChallengeSection(lore, "Azione:");
        if (quest.state() == CityStoryModeService.TaskState.LOCKED) {
            addChallengeBullet(lore, UiLoreStyle.LineType.WARNING,
                    "Questa prova si aprira quando il capitolo sara pronto");
        } else if (quest.state() == CityStoryModeService.TaskState.COMPLETED) {
            addChallengeBullet(lore, UiLoreStyle.LineType.REWARD,
                    "Prova completata in questo capitolo");
        } else {
            addChallengeBullet(lore, UiLoreStyle.LineType.ACTION,
                    "Avanza questa prova per spingere il capitolo verso il finale");
        }
        return UiLoreStyle.renderFramedComponents(lore);
    }

    private List<Component> storyModeSummaryLore(CityStoryModeService.StoryModeChapterSnapshot selected) {
        List<UiLoreStyle.StyledLine> lore = new ArrayList<>();
        addChallengeSection(lore, "Stato capitolo:");
        addChallengeBullet(lore, UiLoreStyle.LineType.STATUS, "Atto: " + selected.act());
        addChallengeBullet(lore, UiLoreStyle.LineType.STATUS,
                "Stato: " + storyModeChapterStateLabel(selected.state()));

        addChallengeSection(lore, "Obiettivo del capitolo:");
        addChallengeBullet(lore, UiLoreStyle.LineType.FOCUS, selected.signatureGoal());

        addChallengeSection(lore, "Finale:");
        addChallengeBullet(lore, UiLoreStyle.LineType.SPECIAL, selected.chapterProof());

        addChallengeSection(lore, "Azione:");
        addChallengeBullet(lore, UiLoreStyle.LineType.ACTION,
                "Chiudi tutte le prove per completare questo capitolo");
        return UiLoreStyle.renderFramedComponents(lore);
    }

    private List<Component> storyModeRewardLore(CityStoryModeService.StoryModeChapterSnapshot selected) {
        List<UiLoreStyle.StyledLine> lore = new ArrayList<>();
        addChallengeSection(lore, "Stato reward:");
        addChallengeBullet(lore, UiLoreStyle.LineType.STATUS,
                "Stato: " + (selected.rewardGranted() ? "assegnata" : "in attesa"));

        addChallengeSection(lore, "Ricompense:");
        for (UiLoreStyle.StyledLine line : rewardLines(selected.rewardPreview())) {
            lore.add(line);
        }

        addChallengeSection(lore, "Azione:");
        addChallengeBullet(lore, selected.rewardGranted()
                        ? UiLoreStyle.LineType.REWARD
                        : UiLoreStyle.LineType.ACTION,
                selected.rewardGranted()
                        ? "Ricompensa gia assegnata al completamento del capitolo"
                        : "Completa il capitolo per ottenere queste ricompense");
        return UiLoreStyle.renderFramedComponents(lore);
    }

    private List<Component> storyModeFallbackLore(CityStoryModeService.StoryModeDiagnostics diagnostics) {
        List<UiLoreStyle.StyledLine> lore = new ArrayList<>();
        addChallengeSection(lore, "Stato sistema:");
        addChallengeBullet(lore, UiLoreStyle.LineType.STATUS,
                "Atlas attivo: " + yesNo(diagnostics.atlasEnabled()));
        addChallengeBullet(lore, UiLoreStyle.LineType.STATUS,
                "Story Mode attiva: " + yesNo(diagnostics.storyModeEnabled()));
        addChallengeBullet(lore, UiLoreStyle.LineType.STATUS,
                "Capitoli caricati: " + diagnostics.baseChapterCount() + " base / "
                        + diagnostics.eliteChapterCount() + " elite");

        addChallengeSection(lore, "Problema:");
        addChallengeBullet(lore, UiLoreStyle.LineType.WARNING, diagnostics.reason());
        return UiLoreStyle.renderFramedComponents(lore);
    }

    private static String yesNo(boolean value) {
        return value ? "si" : "no";
    }

    private static String storyModeChapterStateLabel(CityStoryModeService.ChapterState state) {
        return switch (state) {
            case COMPLETED -> "completato";
            case IN_PROGRESS -> "in corso";
            case UNLOCKED -> "sbloccato";
            case LOCKED -> "bloccato";
        };
    }

    private static String storyModeQuestStateLabel(CityStoryModeService.TaskState state) {
        return switch (state) {
            case COMPLETED -> "completata";
            case IN_PROGRESS -> "in corso";
            case AVAILABLE -> "disponibile";
            case LOCKED -> "bloccata";
        };
    }

    private List<Component> framedLore(String... innerLines) {
        return UiLoreStyle.renderLegacyComponents(innerLines == null ? List.of() : List.of(innerLines));
    }

    private List<String> summarizePreviewLines(ChallengeRewardPreview preview) {
        List<String> parts = new ArrayList<>();
        if (preview.xpCity() > 0.0D) {
            parts.add("Ricompensa: " + (int) Math.round(preview.xpCity()) + " XP citta");
        }
        if (preview.moneyCity() > 0.0D) {
            parts.add(parts.isEmpty()
                    ? "Ricompensa: $" + format(preview.moneyCity())
                    : "$" + format(preview.moneyCity()));
        }
        if (!preview.rewardItems().isEmpty()) {
            boolean first = parts.isEmpty();
            for (ChallengeRewardPreview.RewardItem item : preview.rewardItems()) {
                parts.add((first ? "Ricompensa: " : "")
                        + item.amount() + "x " + ChallengeLoreFormatter.materialLabel(item.material()));
                first = false;
            }
        }
        parts.addAll(rewardCommandDescriptionLines(preview, parts.isEmpty()));
        if (preview.personalXpCityBonus() > 0.0D) {
            parts.add((parts.isEmpty() ? "Ricompensa: " : "")
                    + "Bonus personale +" + format(preview.personalXpCityBonus()) + "% XP citta");
        }
        if (parts.isEmpty()) {
            return List.of("Ricompensa: speciale");
        }
        return List.copyOf(parts);
    }

    private List<String> summarizeSecretHintLines(ChallengeSnapshot snapshot) {
        List<String> clues = secretHintLines(snapshot);
        if (clues.isEmpty()) {
            return List.of("Indizio: segui le tracce della missione");
        }
        List<String> lines = new ArrayList<>();
        for (String clue : clues.stream().limit(3).toList()) {
            lines.add(lines.isEmpty() ? "Indizio: " + clue : clue);
        }
        return List.copyOf(lines);
    }

    private List<String> summarizeExtraObjectiveLines(List<String> objectiveLines) {
        if (objectiveLines == null || objectiveLines.size() <= 1) {
            return List.of("Dettaglio: completa l'obiettivo con la tua citta");
        }
        List<String> lines = new ArrayList<>();
        for (String detail : objectiveLines.subList(1, Math.min(objectiveLines.size(), 4))) {
            lines.add(lines.isEmpty() ? "Dettaglio: " + detail : detail);
        }
        return List.copyOf(lines);
    }

    private String challengeStatusLabel(ChallengeSnapshot snapshot, ChallengeLoreFormatter.ProgressPhase phase) {
        if (snapshot != null && snapshot.mode().race()) {
            return switch (snapshot.status()) {
                case COMPLETED -> "Race conclusa";
                case EXPIRED -> "Race chiusa";
                case ACTIVE -> "Race in corso";
            };
        }
        if (snapshot.townExcellenceCompleted()) {
            return "Eccellenza completata";
        }
        if (snapshot.townCompleted()) {
            return "Completata";
        }
        if (phase.excellencePhase()) {
            return "Eccellenza in corso";
        }
        return "In corso";
    }

    private List<String> raceOutcomeLines(ChallengeSnapshot snapshot, int townId) {
        if (snapshot == null || !snapshot.mode().race()) {
            return List.of();
        }
        if (snapshot.status() == ChallengeInstanceStatus.COMPLETED) {
            String winner = snapshot.winnerTownName() == null || snapshot.winnerTownName().isBlank() || "-".equals(snapshot.winnerTownName())
                    ? "Sconosciuta"
                    : snapshot.winnerTownName();
            if (snapshot.winnerTownId() != null && snapshot.winnerTownId() == townId) {
                return List.of(
                        "Esito: la tua citta ha vinto questa race",
                        "Vincitore: " + winner
                );
            }
            return List.of(
                    "Esito: questa race e gia stata vinta",
                    "Vincitore: " + winner
            );
        }
        if (snapshot.status() == ChallengeInstanceStatus.EXPIRED) {
            return List.of("Esito: tempo scaduto, nessuna nuova vittoria registrabile");
        }
        return List.of();
    }

    private String progressSummaryLine(ChallengeSnapshot snapshot, int percent) {
        return "Progresso: " + snapshot.townProgress() + "/" + snapshot.target() + " (" + percent + "%)";
    }

    private List<String> excellenceStatusLines(ChallengeSnapshot snapshot, ChallengeLoreFormatter.ProgressPhase phase) {
        if (snapshot == null || snapshot.excellenceTarget() <= snapshot.target()) {
            return List.of();
        }
        if (snapshot.townExcellenceCompleted()) {
            return List.of("Eccellenza: completata");
        }
        if (snapshot.townCompleted()) {
            int progress = Math.max(0, snapshot.townProgress() - snapshot.target());
            int target = Math.max(1, snapshot.excellenceTarget() - snapshot.target());
            int remaining = Math.max(0, target - progress);
            return List.of(
                    "Eccellenza: " + progress + "/" + target,
                    remaining > 0 ? "Mancano " + remaining + " punti per chiuderla" : "Eccellenza pronta da chiudere"
            );
        }
        return List.of("Eccellenza: si sblocca dopo il completamento base");
    }

    private List<String> firstClearStatusLines(ChallengeSnapshot snapshot, int townId) {
        if (snapshot == null || snapshot.mode() != ChallengeMode.WEEKLY_STANDARD) {
            return List.of();
        }
        CityChallengeService.WeeklyFirstCompletionStatus status = challengeService.weeklyFirstCompletionStatus(townId);
        if (!status.enabled()) {
            return List.of();
        }
        return List.of("Primo completamento settimanale: " + (status.claimed() ? "riscosso" : "disponibile"));
    }

    private List<String> rewardCommandDescriptionLines(ChallengeRewardPreview preview, boolean first) {
        if (preview == null || preview.commandKeys() == null || preview.commandKeys().isEmpty()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        boolean firstLine = first;
        Map<String, Boolean> seen = new HashMap<>();
        for (String key : preview.commandKeys()) {
            if (key == null || key.isBlank()) {
                continue;
            }
            String normalized = key.trim().toLowerCase(java.util.Locale.ROOT);
            if (normalized.endsWith("_broadcast")) {
                continue;
            }
            if (seen.putIfAbsent(normalized, Boolean.TRUE) != null) {
                continue;
            }
            String path = "city.challenges.reward_command_descriptions." + normalized;
            String description = plugin.getConfig().getString(path);
            if (description == null || description.isBlank()) {
                continue;
            }
            lines.add((firstLine ? "Ricompensa: " : "") + description);
            firstLine = false;
        }
        return List.copyOf(lines);
    }

    private List<Component> mmoLore(String... lines) {
        return UiLoreStyle.renderLooseComponents(lines);
    }

    private static String questModeLabel(ChallengeMode mode) {
        if (mode == null) {
            return "Missione";
        }
        return switch (mode) {
            case DAILY_STANDARD -> "Giornaliera";
            case DAILY_SPRINT_1830, DAILY_SPRINT_2130, DAILY_RACE_1600, DAILY_RACE_2100 -> "Competizione Giornaliera";
            case WEEKLY_STANDARD -> "Settimanale";
            case WEEKLY_CLASH -> "Clash Settimanale";
            case MONTHLY_LEDGER_GRAND -> "Mensile • Grande Progetto";
            case MONTHLY_LEDGER_CONTRACT -> "Mensile • Contratto";
            case MONTHLY_LEDGER_MYSTERY -> "Mensile • Mistero";
            case MONTHLY_CROWN -> "Corona Mensile";
            case SEASON_CODEX_ACT_I, SEASON_CODEX_ACT_II, SEASON_CODEX_ACT_III -> "Stagionale";
            case SEASON_CODEX_ELITE -> "Stagionale Elite";
            case SEASON_CODEX_HIDDEN_RELIC -> "Stagionale • Reliquia";
            case MONTHLY_STANDARD -> "Mensile";
            case MONTHLY_EVENT_A, MONTHLY_EVENT_B -> "Evento Mensile";
            case SEASONAL_RACE -> "Race Stagionale";
        };
    }

    private ItemStack atlasSealItem(CityAtlasService.AtlasSealSnapshot snapshot) {
        if (snapshot == null) {
            return GuiItemFactory.item(
                    Material.NETHER_STAR,
                    configUtils.msg("city.players.gui.challenges.atlas.seals.fallback", "<gold>Seals Atlas</gold>")
            );
        }
        return GuiItemFactory.item(
                Material.NETHER_STAR,
                configUtils.msg(
                        "city.players.gui.challenges.atlas.seals.title",
                        "<gold>Seals Atlas</gold> <gray>{earned}/{required}</gray>",
                        Placeholder.unparsed("earned", Integer.toString(snapshot.earned())),
                        Placeholder.unparsed("required", Integer.toString(snapshot.required()))
                ),
                List.of(
                        configUtils.msg(
                                "city.players.gui.challenges.atlas.seals.stage",
                                "<gray>Stage:</gray> <white>{stage}</white>",
                                Placeholder.unparsed("stage", snapshot.stage().name())
                        ),
                        configUtils.msg(
                                "city.players.gui.challenges.atlas.seals.tier",
                                "<gray>Tier richiesto:</gray> <white>{tier}</white>",
                                Placeholder.unparsed("tier", snapshot.requiredTier().name())
                        )
                )
        );
    }

    private ItemStack atlasChapterItem(CityAtlasService.AtlasChapterSnapshot chapter) {
        int pct = chapter.totalFamilies() <= 0
                ? 0
                : (int) Math.min(100L, Math.round((chapter.completedFamilies() * 100.0D) / chapter.totalFamilies()));
        Material icon = chapter.icon() == null ? Material.BOOK : chapter.icon();
        return GuiItemFactory.item(
                icon,
                configUtils.msg(
                        "city.players.gui.challenges.atlas.chapter.title",
                        "<gold>{chapter}</gold>",
                        Placeholder.unparsed("chapter", chapter.displayName())
                ),
                List.of(
                        configUtils.msg(
                                "city.players.gui.challenges.atlas.chapter.progress",
                                "<gray>Completamento:</gray> <white>{done}/{total}</white> <gray>({pct}%)</gray>",
                                Placeholder.unparsed("done", Integer.toString(chapter.completedFamilies())),
                                Placeholder.unparsed("total", Integer.toString(chapter.totalFamilies())),
                                Placeholder.unparsed("pct", Integer.toString(pct))
                        ),
                        configUtils.msg(
                                "city.players.gui.challenges.atlas.chapter.seal",
                                "<gray>Seal:</gray> <white>{state}</white>",
                                Placeholder.unparsed("state", chapter.sealed() ? "ottenuto" : "in corso")
                        ),
                        configUtils.msg(
                                "city.players.gui.challenges.atlas.chapter.click",
                                "<yellow>Click per aprire le famiglie</yellow>"
                        )
                )
        );
    }

    private void openAtlasChapter(Player player, Session session, AtlasChapter chapter) {
        Holder holder = new Holder(session.sessionId);
        holder.kind = HolderKind.ATLAS_CHAPTER;
        holder.atlasChapter = chapter;
        Inventory inventory = Bukkit.createInventory(
                holder,
                GUI_SIZE,
                configUtils.msg(
                        "city.players.gui.challenges.atlas.chapter_view.title",
                        "<gold>Atlas</gold> <gray>{chapter}</gray>",
                        Placeholder.unparsed("chapter", chapter.defaultDisplayName())
                )
        );
        holder.bind(inventory);
        GuiLayoutUtils.fillAllExcept(inventory, GuiItemFactory.fillerPane(), 45, 53);
        inventory.setItem(45, GuiItemFactory.customHead(
                GuiHeadTextures.BACK,
                configUtils.msg("city.players.gui.challenges.nav.back", "<yellow>Indietro</yellow>")
        ));
        inventory.setItem(53, GuiItemFactory.customHead(
                GuiHeadTextures.CLOSE,
                configUtils.msg("city.players.gui.challenges.nav.close", "<red>Chiudi</red>")
        ));
        List<CityAtlasService.AtlasChapterSnapshot> chapters = challengeService.atlasChapterSnapshots(session.townId);
        CityAtlasService.AtlasChapterSnapshot selected = chapters.stream()
                .filter(snapshot -> snapshot.chapter() == chapter)
                .findFirst()
                .orElse(null);
        if (selected != null) {
            int slot = 0;
            for (CityAtlasService.AtlasFamilySnapshot family : selected.families()) {
                if (slot >= 45) {
                    break;
                }
                inventory.setItem(slot, atlasFamilyItem(family));
                holder.atlasFamilyBySlot.put(slot, family.familyId());
                slot++;
            }
        }
        player.openInventory(inventory);
    }

    private ItemStack atlasFamilyItem(CityAtlasService.AtlasFamilySnapshot family) {
        int highestPct = 0;
        for (CityAtlasService.AtlasTierSnapshot tier : family.tiers()) {
            int pct = tier.target() <= 0 ? 0 : (int) Math.min(100L, Math.round((tier.progress() * 100.0D) / tier.target()));
            highestPct = Math.max(highestPct, pct);
        }
        Material icon = ChallengeObjectivePresentation.icon(family.objectiveType());
        List<Component> lore = new ArrayList<>();
        lore.add(configUtils.msg(
                "city.players.gui.challenges.atlas.family.progress",
                "<gray>Progresso:</gray> <white>{pct}%</white>",
                Placeholder.unparsed("pct", Integer.toString(highestPct))
        ));
        lore.add(configUtils.msg(
                "city.players.gui.challenges.atlas.family.unlock",
                "<gray>Stato:</gray> <white>{state}</white>",
                Placeholder.unparsed("state", family.unlockedForStage() ? "sbloccata" : "bloccata")
        ));
        lore.add(configUtils.msg(
                "city.players.gui.challenges.atlas.family.click",
                "<yellow>Click per dettaglio tier</yellow>"
        ));
        return GuiItemFactory.item(
                icon,
                configUtils.msg("city.players.gui.challenges.atlas.family.title", "<gold>{family}</gold>",
                        Placeholder.unparsed("family", family.displayName())),
                lore
        );
    }

    private void openAtlasFamily(Player player, Session session, AtlasChapter chapter, String familyId) {
        List<CityAtlasService.AtlasChapterSnapshot> chapters = challengeService.atlasChapterSnapshots(session.townId);
        CityAtlasService.AtlasFamilySnapshot family = chapters.stream()
                .filter(snapshot -> snapshot.chapter() == chapter)
                .flatMap(snapshot -> snapshot.families().stream())
                .filter(row -> row.familyId().equalsIgnoreCase(familyId))
                .findFirst()
                .orElse(null);
        if (family == null) {
            player.sendMessage(configUtils.msg("city.players.gui.challenges.atlas.errors.family_missing",
                    "<red>Famiglia Atlas non trovata.</red>"));
            openAtlasChapter(player, session, chapter);
            return;
        }
        Holder holder = new Holder(session.sessionId);
        holder.kind = HolderKind.ATLAS_FAMILY;
        holder.atlasChapter = chapter;
        holder.atlasFamilyId = family.familyId();
        Inventory inventory = Bukkit.createInventory(
                holder,
                27,
                configUtils.msg(
                        "city.players.gui.challenges.atlas.family_view.title",
                        "<gold>{family}</gold>",
                        Placeholder.unparsed("family", family.displayName())
                )
        );
        holder.bind(inventory);
        GuiLayoutUtils.fillAllExcept(inventory, GuiItemFactory.fillerPane(), 11, 13, 15, 18, 26);
        inventory.setItem(18, GuiItemFactory.customHead(
                GuiHeadTextures.BACK,
                configUtils.msg("city.players.gui.challenges.nav.back", "<yellow>Indietro</yellow>")
        ));
        inventory.setItem(26, GuiItemFactory.customHead(
                GuiHeadTextures.CLOSE,
                configUtils.msg("city.players.gui.challenges.nav.close", "<red>Chiudi</red>")
        ));

        int[] slots = {11, 13, 15, 22};
        int index = 0;
        for (CityAtlasService.AtlasTierSnapshot tier : family.tiers()) {
            if (index >= slots.length) {
                break;
            }
            int slot = slots[index++];
            inventory.setItem(slot, atlasTierItem(tier));
            holder.atlasTierBySlot.put(slot, tier.tier());
        }
        player.openInventory(inventory);
    }

    private ItemStack atlasTierItem(CityAtlasService.AtlasTierSnapshot tier) {
        Material material = tier.claimed() ? Material.LIME_DYE : (tier.claimable() ? Material.EMERALD : Material.GRAY_DYE);
        int pct = tier.target() <= 0 ? 0 : (int) Math.min(100L, Math.round((tier.progress() * 100.0D) / tier.target()));
        List<Component> lore = new ArrayList<>();
        lore.add(configUtils.msg(
                "city.players.gui.challenges.atlas.tier.progress",
                "<gray>Progresso:</gray> <white>{progress}/{target}</white> <gray>({pct}%)</gray>",
                Placeholder.unparsed("progress", Long.toString(tier.progress())),
                Placeholder.unparsed("target", Integer.toString(tier.target())),
                Placeholder.unparsed("pct", Integer.toString(pct))
        ));
        lore.add(configUtils.msg(
                "city.players.gui.challenges.atlas.tier.reward",
                "<gray>Reward XP:</gray> <white>{xp}</white>",
                Placeholder.unparsed("xp", Integer.toString((int) Math.round(tier.reward().xpCity())))
        ));
        lore.add(configUtils.msg(
                "city.players.gui.challenges.atlas.tier.state",
                "<gray>Stato:</gray> <white>{state}</white>",
                Placeholder.unparsed("state", tier.claimed() ? "riscattato" : (tier.claimable() ? "riscattabile" : "non pronto"))
        ));
        return GuiItemFactory.item(
                material,
                configUtils.msg("city.players.gui.challenges.atlas.tier.title", "<gold>Tier {tier}</gold>",
                        Placeholder.unparsed("tier", tier.tier().name())),
                lore
        );
    }

    private void handleAtlasChapterClick(Player player, Session session, Holder holder, int slot) {
        if (slot == 45) {
            openStoryModePage(player, session);
            return;
        }
        if (slot == 53) {
            player.closeInventory();
            return;
        }
        String familyId = holder.atlasFamilyBySlot.get(slot);
        if (familyId != null) {
            openAtlasFamily(player, session, holder.atlasChapter, familyId);
        }
    }

    private void handleAtlasFamilyClick(Player player, Session session, Holder holder, int slot) {
        if (slot == 18) {
            openAtlasChapter(player, session, holder.atlasChapter);
            return;
        }
        if (slot == 26) {
            player.closeInventory();
            return;
        }
        AtlasTier tier = holder.atlasTierBySlot.get(slot);
        if (tier == null || holder.atlasFamilyId == null) {
            return;
        }
        challengeService.claimAtlasTierReward(player, session.townId, holder.atlasFamilyId, tier)
                .whenComplete((result, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (error != null || result == null || !result.success()) {
                        String reason = error != null ? error.getMessage() : (result == null ? "-" : result.reason());
                        player.sendMessage(configUtils.msg(
                                "city.players.gui.challenges.atlas.claim.fail",
                                "<red>Claim Atlas fallito:</red> <gray>{reason}</gray>",
                                Placeholder.unparsed("reason", reason == null || reason.isBlank() ? "-" : reason)
                        ));
                    } else {
                        player.sendMessage(configUtils.msg(
                                "city.players.gui.challenges.atlas.claim.ok",
                                "<green>Reward Atlas riscattata.</green>"
                        ));
                    }
                    openAtlasFamily(player, session, holder.atlasChapter, holder.atlasFamilyId);
                }));
    }

    private static String percent(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f%%", Math.max(0.0D, value) * 100.0D);
    }

    private static String playerName(UUID playerId) {
        if (playerId == null) {
            return "-";
        }
        Player online = Bukkit.getPlayer(playerId);
        if (online != null) {
            return online.getName();
        }
        org.bukkit.OfflinePlayer offline = Bukkit.getOfflinePlayer(playerId);
        if (offline.getName() != null && !offline.getName().isBlank()) {
            return offline.getName();
        }
        return playerId.toString().substring(0, 8);
    }

    private static String formatRemaining(long seconds) {
        long safe = Math.max(0L, seconds);
        long hours = safe / 3600L;
        long minutes = (safe % 3600L) / 60L;
        long secs = safe % 60L;
        return String.format(java.util.Locale.ROOT, "%02dh %02dm %02ds", hours, minutes, secs);
    }

    private String formatInstantForPlayer(java.time.Instant instant) {
        if (instant == null) {
            return "-";
        }
        return java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm", java.util.Locale.ROOT)
                .withZone(challengeService.settings().timezone())
                .format(instant);
    }

    private String shortPlayerName(UUID playerId) {
        if (playerId == null) {
            return "-";
        }
        Player online = Bukkit.getPlayer(playerId);
        if (online != null) {
            return online.getName();
        }
        return playerId.toString().substring(0, 8);
    }

    private static String format(double value) {
        java.math.BigDecimal decimal = java.math.BigDecimal.valueOf(value)
                .setScale(2, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros();
        return decimal.toPlainString();
    }

    private static boolean contains(int[] slots, int slot) {
        for (int value : slots) {
            if (value == slot) {
                return true;
            }
        }
        return false;
    }

    private static int indexOfSlot(int[] slots, int slot) {
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == slot) {
                return i;
            }
        }
        return -1;
    }

    private static final class Session {
        private final UUID sessionId;
        private final UUID viewerId;
        private final int townId;
        private final String townName;
        private final boolean staffView;
        private final boolean returnToProgressionHub;
        private MissionTab tab;
        private int mainDailyPage;
        private int storyModeChapterPage;
        private String storyModeSelectedChapterId;
        private int seasonalPage;
        private int eventsPage;

        private Session(
                UUID sessionId,
                UUID viewerId,
                int townId,
                String townName,
                boolean staffView,
                boolean returnToProgressionHub
        ) {
            this.sessionId = sessionId;
            this.viewerId = viewerId;
            this.townId = townId;
            this.townName = townName;
            this.staffView = staffView;
            this.returnToProgressionHub = returnToProgressionHub;
            this.tab = MissionTab.DAILY;
            this.mainDailyPage = 0;
            this.storyModeChapterPage = 0;
            this.storyModeSelectedChapterId = null;
            this.seasonalPage = 0;
            this.eventsPage = 0;
        }
    }

    private static final class Holder implements InventoryHolder {
        private final UUID sessionId;
        private Inventory inventory;
        private HolderKind kind = HolderKind.LIST;
        private final Map<Integer, AtlasChapter> atlasChapterBySlot = new HashMap<>();
        private final Map<Integer, String> atlasFamilyBySlot = new HashMap<>();
        private final Map<Integer, AtlasTier> atlasTierBySlot = new HashMap<>();
        private final Map<Integer, String> storyModeChapterBySlot = new HashMap<>();
        private final Map<Integer, DailyPreviewEntry> mainDailyEntryBySlot = new HashMap<>();
        private final Map<Integer, StaffEventService.EventCardView> staffEventBySlot = new HashMap<>();
        private AtlasChapter atlasChapter;
        private String atlasFamilyId;

        private Holder(UUID sessionId) {
            this.sessionId = sessionId;
        }

        private UUID sessionId() {
            return sessionId;
        }

        private void bind(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private record MissionCardRow(ChallengeSnapshot snapshot, String bucketLabel) {
    }

    private record EventOverlayEntry(
            StaffEventService.EventCardView staffEvent,
            ChallengeSnapshot snapshot
    ) {
    }

    private record DailyPreviewEntry(
            DailyPreviewEntryKind kind,
            ItemStack item,
            ChallengeSnapshot snapshot,
            DailyRacePreview racePreview
    ) {
    }

    private record DailyRacePreview(
            DailyPreviewEntryKind kind,
            ChallengeMode mode,
            java.time.LocalTime window,
            long secondsUntilStateChange,
            ChallengeSnapshot snapshot
    ) {
    }

    private record MainOpenResult(
            Inventory inventory,
            dev.lone.itemsadder.api.FontImages.TexturedInventoryWrapper wrapper
    ) {
    }

    private enum HolderKind {
        MAIN,
        DAILY_MAIN,
        WEEKLY_MAIN,
        MONTHLY_MAIN,
        STORY_MODE,
        SEASONAL_MAIN,
        EVENTS_MAIN,
        LIST,
        MANAGE,
        ATLAS_CHAPTER,
        ATLAS_FAMILY
    }

    private enum DailyPreviewEntryKind {
        STANDARD,
        RACE_HIDDEN,
        RACE_ACTIVE,
        RACE_CLOSED
    }

    private enum MissionTab {
        DAILY,
        WEEKLY,
        MONTHLY,
        RACES,
        CODEX,
        ATLAS
    }

    private boolean isMayor(Player player, Session session) {
        return huskTownsApiHook.getTownById(session.townId)
                .map(town -> town.getMayor().equals(player.getUniqueId()))
                .orElse(false);
    }

    private boolean canManageChallenges(Player player, Session session) {
        return huskTownsApiHook.getTownById(session.townId)
                .map(town -> permissionService.isAllowed(
                        session.townId,
                        town.getMayor(),
                        player.getUniqueId(),
                        TownMemberPermission.CITY_CHALLENGES_MANAGE
                ))
                .orElse(false);
    }

    private void openMayorManage(Player player, Session session) {
        CityChallengeService.MayorGovernanceSnapshot snapshot = challengeService.mayorSnapshot(session.townId);
        Holder holder = new Holder(session.sessionId);
        holder.kind = HolderKind.MANAGE;
        Inventory inventory = Bukkit.createInventory(holder, MANAGE_GUI_SIZE, configUtils.msg(
                "city.players.gui.challenges.manage.title",
                "<gold>Gestione Sfide</gold> <gray>{town}</gray>",
                Placeholder.unparsed("town", session.townName)
        ));
        holder.bind(inventory);
        GuiLayoutUtils.fillAllExcept(
                inventory,
                GuiItemFactory.fillerPane(),
                MANAGE_SLOT_BACK, MANAGE_SLOT_CLOSE, MANAGE_SLOT_REROLL, MANAGE_SLOT_SEASON, MANAGE_SLOT_MILESTONE
        );
        inventory.setItem(MANAGE_SLOT_BACK, GuiItemFactory.customHead(
                GuiHeadTextures.BACK,
                configUtils.msg("city.players.gui.challenges.manage.back", "<yellow>Indietro</yellow>")
        ));
        inventory.setItem(MANAGE_SLOT_CLOSE, GuiItemFactory.customHead(
                GuiHeadTextures.CLOSE,
                configUtils.msg("city.players.gui.challenges.manage.close", "<red>Chiudi</red>")
        ));
        inventory.setItem(MANAGE_SLOT_SEASON, GuiItemFactory.customHead(
                GuiHeadTextures.INFO,
                configUtils.msg(
                        "city.players.gui.challenges.manage.season",
                        "<aqua>Stagione:</aqua> <white>{season}</white> <gray>({key})</gray>",
                        Placeholder.unparsed("season", snapshot.seasonLabel()),
                        Placeholder.unparsed("key", snapshot.seasonKey())
                )
        ));
        inventory.setItem(MANAGE_SLOT_MILESTONE, GuiItemFactory.customHead(
                GuiHeadTextures.LEVELS,
                configUtils.msg(
                        "city.players.gui.challenges.manage.milestone",
                        "<gold>Milestone</gold> <gray>{count}/3 weekly</gray>",
                        Placeholder.unparsed("count", Integer.toString(snapshot.weeklyCompletedCount()))
                ),
                List.of(
                        configUtils.msg("city.players.gui.challenges.manage.m1", "<gray>M1:</gray> <white>{status}</white>",
                                Placeholder.unparsed("status", snapshot.milestone1Done() ? "fatta" : "in corso")),
                        configUtils.msg("city.players.gui.challenges.manage.m2", "<gray>M2:</gray> <white>{status}</white>",
                                Placeholder.unparsed("status", snapshot.milestone2Done() ? "fatta" : "in corso")),
                        configUtils.msg("city.players.gui.challenges.manage.m3", "<gray>M3:</gray> <white>{status}</white>",
                                Placeholder.unparsed("status", snapshot.milestone3Done() ? "fatta" : "in corso")),
                        configUtils.msg("city.players.gui.challenges.manage.final", "<gray>Finale:</gray> <white>{status}</white>",
                                Placeholder.unparsed("status", snapshot.seasonalFinalDone() ? "fatta" : "in corso"))
                )
        ));
        inventory.setItem(MANAGE_SLOT_REROLL, GuiItemFactory.customHead(
                GuiHeadTextures.ACTIONS,
                configUtils.msg(
                        "city.players.gui.challenges.manage.reroll",
                        "<gold>Reroll Weekly</gold> <gray>{used}/{max}</gray>",
                        Placeholder.unparsed("used", Integer.toString(snapshot.rerollUsed())),
                        Placeholder.unparsed("max", Integer.toString(snapshot.rerollMax()))
                )
        ));
        player.openInventory(inventory);
    }

    private void handleManageClick(Player player, Session session, int slot) {
        if (slot == MANAGE_SLOT_BACK) {
            openMainPage(player, session);
            return;
        }
        if (slot == MANAGE_SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == MANAGE_SLOT_REROLL) {
            challengeService.rerollWeeklyPackage(session.townId).whenComplete((reason, error) ->
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        String code = error != null ? error.getMessage() : reason;
                        if (code == null || code.isBlank() || "-".equals(code)) {
                            player.sendMessage(configUtils.msg("city.players.gui.challenges.manage.reroll_ok",
                                    "<green>Reroll weekly completato.</green>"));
                        } else {
                            player.sendMessage(configUtils.msg(
                                    "city.players.gui.challenges.manage.reroll_fail",
                                    "<red>Reroll non disponibile:</red> <gray>{reason}</gray>",
                                    Placeholder.unparsed("reason", code)
                            ));
                        }
                        openMayorManage(player, session);
                    }));
        }
    }
}
