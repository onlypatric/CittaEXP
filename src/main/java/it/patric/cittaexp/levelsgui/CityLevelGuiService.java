package it.patric.cittaexp.levelsgui;

import it.patric.cittaexp.defense.CityDefenseService;
import it.patric.cittaexp.levels.CityLevelRequestService;
import it.patric.cittaexp.levels.CityLevelRepository;
import it.patric.cittaexp.levels.CityLevelService;
import it.patric.cittaexp.levels.TownLevelRequest;
import it.patric.cittaexp.levels.TownStage;
import it.patric.cittaexp.playersgui.CityPlayersSession;
import it.patric.cittaexp.text.MiniMessageHelper;
import it.patric.cittaexp.text.UiLoreStyle;
import it.patric.cittaexp.utils.DialogConfirmHelper;
import it.patric.cittaexp.utils.GuiHeadTextures;
import it.patric.cittaexp.utils.GuiItemFactory;
import it.patric.cittaexp.utils.GuiNavigationUtils;
import it.patric.cittaexp.utils.PluginConfigUtils;
import it.patric.cittaexp.utils.ReasonMessageMapper;
import it.patric.cittaexp.utils.TexturedGuiSupport;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class CityLevelGuiService implements Listener {

    private static final int GUI_SIZE = 54;
    private static final int[] SLOT_MILESTONES = {0, 1, 2, 3, 4, 5, 6, 7, 8};
    private static final int[] SLOT_PREV = {18};
    private static final int SLOT_CURRENT = 22;
    private static final int[] SLOT_NEXT = {26};
    private static final int[] SLOT_CLOSE = {46, 47, 48};
    private static final String LEVELS_FONT_IMAGE_ID = "cittaexp_gui:livelli_9x6";
    private static final String TRANSPARENT_BUTTON_ID = "cittaexp_gui:transparent_button";
    private static final int PROGRESS_BAR_WIDTH = 12;

    private final Plugin plugin;
    private final PluginConfigUtils configUtils;
    private final CityLevelService cityLevelService;
    private final CityLevelRequestService requestService;
    private final CityLevelTimelineBuilder timelineBuilder;
    private final ConcurrentMap<UUID, CityLevelGuiSession> sessionsByViewer = new ConcurrentHashMap<>();
    public CityLevelGuiService(
            Plugin plugin,
            CityLevelService cityLevelService,
            CityLevelRequestService requestService,
            CityDefenseService cityDefenseService
    ) {
        this.plugin = plugin;
        this.configUtils = new PluginConfigUtils(plugin);
        this.cityLevelService = cityLevelService;
        this.requestService = requestService;
        this.timelineBuilder = new CityLevelTimelineBuilder(cityLevelService, requestService, cityDefenseService);
    }

    public void clearAllSessions() {
        sessionsByViewer.clear();
    }

    public void open(Player player, CityPlayersSession citySession) {
        if (citySession.staffView()) {
            player.sendMessage(configUtils.msg(
                    "city.players.gui.levels.errors.staff_readonly",
                    "<red>La GUI livelli e disponibile solo per la tua citta.</red>"
            ));
            return;
        }
        CityLevelGuiSession session = new CityLevelGuiSession(
                UUID.randomUUID(),
                player.getUniqueId(),
                citySession.townId(),
                citySession.townName(),
                false
        );
        session.page(defaultLevelIndex(session.townId()));
        sessionsByViewer.put(player.getUniqueId(), session);
        openList(player, session);
    }

    private void openList(Player player, CityLevelGuiSession session) {
        CityLevelTimelineBuilder.Timeline timeline = timelineBuilder.build(session.townId());
        List<CityLevelRowView> rows = timeline.rows();
        if (rows.isEmpty()) {
            player.closeInventory();
            return;
        }
        if (session.page() >= rows.size()) {
            session.page(rows.size() - 1);
        }
        CityLevelRowView selectedRow = rows.get(session.page());

        Holder holder = new Holder(session.sessionId());
        LevelOpenResult openResult = createLevelsInventory(
                holder,
                configUtils.msg(
                        "city.players.gui.levels.title",
                        "<gold>Livelli Citta</gold> <gray>{town}</gray>",
                        Placeholder.unparsed("town", timeline.summary().townName())
                )
        );
        Inventory inventory = openResult.inventory();
        ItemStack transparentBase = TexturedGuiSupport.transparentButtonBase(plugin, TRANSPARENT_BUTTON_ID);
        renderMilestones(inventory, rows, timeline.summary());
        TexturedGuiSupport.fillGroup(inventory, SLOT_PREV, navPrev(session.page() > 0, transparentBase, session.page(), rows.size()));
        TexturedGuiSupport.fillGroup(inventory, SLOT_NEXT, navNext(session.page() + 1 < rows.size(), transparentBase, session.page(), rows.size()));
        TexturedGuiSupport.fillGroup(inventory, SLOT_CLOSE, closeButton(transparentBase, session));
        inventory.setItem(SLOT_CURRENT, selectedLevelItem(selectedRow, timeline.summary(), session.page() + 1, rows.size()));
        if (openResult.texturedWrapper() != null) {
            openResult.texturedWrapper().showInventory(player);
            return;
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

        CityLevelGuiSession session = sessionsByViewer.get(player.getUniqueId());
        if (session == null || !session.sessionId().equals(holder.sessionId())) {
            player.closeInventory();
            return;
        }

        CityLevelTimelineBuilder.Timeline timeline = timelineBuilder.build(session.townId());
        List<CityLevelRowView> rows = timeline.rows();
        if (rows.isEmpty()) {
            player.closeInventory();
            return;
        }
        if (session.page() >= rows.size()) {
            session.page(rows.size() - 1);
        }
        int slot = event.getRawSlot();

        if (containsSlot(SLOT_PREV, slot)) {
            if (session.page() > 0) {
                session.page(session.page() - 1);
            }
            openList(player, session);
            return;
        }
        if (containsSlot(SLOT_NEXT, slot)) {
            if (session.page() + 1 < rows.size()) {
                session.page(session.page() + 1);
            }
            openList(player, session);
            return;
        }
        int milestoneIndex = milestoneSlotIndex(slot);
        if (milestoneIndex >= 0) {
            List<MilestoneEntry> milestones = milestoneEntries(rows, timeline.summary());
            if (milestoneIndex < milestones.size()) {
                session.page(milestones.get(milestoneIndex).rowIndex());
                openList(player, session);
            }
            return;
        }
        if (containsSlot(SLOT_CLOSE, slot)) {
            returnFromLevels(player, session);
            return;
        }
        if (slot != SLOT_CURRENT) {
            return;
        }
        handleRowClick(player, rows.get(session.page()), timeline.summary());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        sessionsByViewer.remove(event.getPlayer().getUniqueId());
    }

    private void handleRowClick(Player player, CityLevelRowView row, CityLevelSummaryView summary) {
        if (row.level() != summary.currentLevel() + 1) {
            player.sendMessage(configUtils.msg(
                    "city.players.gui.levels.actions.not_actionable",
                    "<gray>Devi prima sbloccare il livello precedente.</gray>"
            ));
            return;
        }
        if (!row.actionable()) {
            player.sendMessage(configUtils.msg(
                    "city.players.gui.levels.actions.not_actionable",
                    "<gray>Non hai ancora abbastanza XP per questo livello.</gray>"
            ));
            return;
        }
        openUpgradeConfirmation(player, row, summary);
    }

    private ItemStack selectedLevelItem(CityLevelRowView row, CityLevelSummaryView summary, int currentIndex, int totalLevels) {
        String titlePath = switch (row.rowState()) {
            case CURRENT -> "city.players.gui.levels.entry.title_current";
            case UNLOCKED -> "city.players.gui.levels.entry.title_unlocked";
            case LOCKED -> "city.players.gui.levels.entry.title_locked";
        };
        String titleFallback = switch (row.rowState()) {
            case CURRENT -> "<gold>Livello {level} (attuale)</gold>";
            case UNLOCKED -> "<green>Livello {level}</green>";
            case LOCKED -> "<gray>Livello {level}</gray>";
        };
        List<UiLoreStyle.StyledLine> loreLines = new ArrayList<>();
        addSection(loreLines, "Stato attuale:");
        addBullet(loreLines, UiLoreStyle.LineType.STATUS, "Livello citta attuale: " + summary.currentLevel());
        addBullet(loreLines, UiLoreStyle.LineType.STATUS, "Livello raggiunto con XP: " + summary.earnedLevel());
        addBullet(loreLines, UiLoreStyle.LineType.STATUS, "XP citta attuale: " + format(summary.currentXp()));
        addBullet(loreLines, UiLoreStyle.LineType.STATUS,
                "Avanzamento: " + progressBarLine(progressPercentForRow(row, summary), PROGRESS_BAR_WIDTH)
                        + " " + progressPercentForRow(row, summary) + "%");

        addSection(loreLines, "Requisiti:");
        addBullet(loreLines, UiLoreStyle.LineType.STATUS, "XP richiesta: " + format(row.requiredXp()));
        if (row.level() == summary.currentLevel() + 1) {
            addBullet(loreLines, UiLoreStyle.LineType.FOCUS, "Prossimo livello da sbloccare: " + row.level());
            addBullet(loreLines, UiLoreStyle.LineType.FOCUS, "XP richiesta per il prossimo unlock: " + format(summary.nextRequiredXp()));
        }
        if (row.level() > summary.currentLevel()) {
            addBullet(loreLines, UiLoreStyle.LineType.WARNING,
                    "XP mancanti: " + format(Math.max(0.0D, row.requiredXp() - summary.currentXp())));
        } else {
            int nextLevel = Math.min(cityLevelService.settings().levelCap(), summary.currentLevel() + 1);
            if (nextLevel > summary.currentLevel()) {
                double nextLevelXp = requiredXpForLevel(nextLevel);
                addBullet(loreLines, UiLoreStyle.LineType.FOCUS,
                        "Prossimo livello: " + nextLevel);
                addBullet(loreLines, UiLoreStyle.LineType.WARNING,
                        "XP mancanti: " + format(Math.max(0.0D, nextLevelXp - summary.currentXp())));
            } else {
                addBullet(loreLines, UiLoreStyle.LineType.REWARD, "Hai gia raggiunto il livello massimo.");
            }
        }

        if (row.regnoExtraClaim()) {
            addBullet(loreLines, UiLoreStyle.LineType.SPECIAL, "Bonus REGNO: +1 chunk claim");
        }
        if (row.checkpointStage() != null) {
            addBullet(loreLines, UiLoreStyle.LineType.FOCUS, "Stage target: " + row.checkpointStage().displayName());
            addBullet(loreLines, UiLoreStyle.LineType.REWARD, "Saldo banca richiesto: " + format(row.requiredBalance()));
            addBullet(loreLines, UiLoreStyle.LineType.REWARD, "Saldo citta attuale: " + format(summary.currentBalance()));
            addBullet(loreLines, UiLoreStyle.LineType.STATUS,
                    "Approvazione staff: " + (row.staffApprovalRequired() ? "richiesta" : "non richiesta"));
            if (row.spawnRequired()) {
                addBullet(loreLines, UiLoreStyle.LineType.FOCUS, "Warp citta: richiesto");
            }
            if (row.requiredDefenseTier() != null) {
                addBullet(loreLines, UiLoreStyle.LineType.FOCUS,
                        "Requisito invasione: " + row.requiredDefenseTier().id().toUpperCase());
                addBullet(loreLines,
                        row.defenseTierCompleted() ? UiLoreStyle.LineType.REWARD : UiLoreStyle.LineType.WARNING,
                        row.defenseTierCompleted()
                                ? "Requisito invasione completato"
                                : "Requisito invasione non completato"
                );
            }
            if (row.latestRequest() != null) {
                addBullet(loreLines, UiLoreStyle.LineType.STATUS,
                        "Stato richiesta: " + requestStatusLabel(row.latestRequest().status()));
                if (row.latestRequest().status() != CityLevelRepository.RequestStatus.PENDING && row.latestRequest().note() != null && !row.latestRequest().note().isBlank()) {
                    addBullet(loreLines, UiLoreStyle.LineType.INFO, "Nota staff: " + row.latestRequest().note());
                }
            }
            addSection(loreLines, "Costi e limiti:");
            addBullet(loreLines, UiLoreStyle.LineType.REWARD, "Costo upgrade: " + format(row.upgradeCost()));
            if (row.monthlyTax() > 0.0D) {
                addBullet(loreLines, UiLoreStyle.LineType.TIME,
                        "Tassa mensile: " + format(row.monthlyTax()));
            }
            addBullet(loreLines, UiLoreStyle.LineType.INFO, "Claim massimi: " + row.claimCap());
            addBullet(loreLines, UiLoreStyle.LineType.INFO, "Membri massimi: " + row.memberCap());
            addBullet(loreLines, UiLoreStyle.LineType.INFO, "Spawner massimi: " + row.spawnerCap());

            addSection(loreLines, "Azione:");
            if (row.level() == summary.currentLevel() + 1 && row.actionable()) {
                addBullet(loreLines, UiLoreStyle.LineType.ACTION,
                        row.staffApprovalRequired()
                                ? "Clicca per aprire la richiesta staff"
                                : "Clicca per aprire la conferma");
                if (!row.staffApprovalRequired() && upgradeReady(row, summary)) {
                    addBullet(loreLines, UiLoreStyle.LineType.REWARD,
                            "Livello pronto: serve conferma del capo");
                }
            } else if (row.level() == summary.currentLevel() + 1) {
                addBullet(loreLines, UiLoreStyle.LineType.WARNING, "Accumula ancora XP per sbloccare questo livello");
            } else {
                addBullet(loreLines, UiLoreStyle.LineType.WARNING, "Sblocca prima il livello precedente");
            }
        } else {
            addBullet(loreLines, UiLoreStyle.LineType.FOCUS, "Stage: " + row.stageForLevel().displayName());
            addSection(loreLines, "Costi e limiti:");
            addBullet(loreLines, UiLoreStyle.LineType.INFO, "Claim massimi: " + row.claimCap());
            addBullet(loreLines, UiLoreStyle.LineType.INFO, "Membri massimi: " + row.memberCap());
            addBullet(loreLines, UiLoreStyle.LineType.INFO, "Spawner massimi: " + row.spawnerCap());
            addSection(loreLines, "Azione:");
            if (row.level() == summary.currentLevel() + 1 && row.actionable()) {
                addBullet(loreLines, UiLoreStyle.LineType.ACTION, "Clicca per aprire la conferma");
            } else if (row.level() == summary.currentLevel() + 1) {
                addBullet(loreLines, UiLoreStyle.LineType.WARNING, "Accumula ancora XP per sbloccare questo livello");
            } else {
                addBullet(loreLines, UiLoreStyle.LineType.WARNING, "Sblocca prima il livello precedente");
            }
        }

        Material material = switch (row.rowState()) {
            case CURRENT -> Material.NETHER_STAR;
            case UNLOCKED -> Material.EMERALD;
            case LOCKED -> Material.GRAY_DYE;
        };

        addSection(loreLines, "Riepilogo:");
        addBullet(loreLines, UiLoreStyle.LineType.STATUS, "Pagina: " + currentIndex + "/" + totalLevels);
        addBullet(loreLines, UiLoreStyle.LineType.INFO, "Stage attuale: " + summary.currentStage().displayName());
        addBullet(loreLines, UiLoreStyle.LineType.INFO, "Claim attuali: " + summary.claimCap());
        addBullet(loreLines, UiLoreStyle.LineType.INFO, "Membri attuali: " + summary.memberCap());
        addBullet(loreLines, UiLoreStyle.LineType.INFO, "Spawner attuali: " + summary.spawnerCap());

        return GuiItemFactory.item(material, configUtils.msg(
                titlePath,
                titleFallback,
                Placeholder.unparsed("level", Integer.toString(row.level()))
        ), UiLoreStyle.renderFramedComponents(loreLines));
    }

    private void renderMilestones(Inventory inventory, List<CityLevelRowView> rows, CityLevelSummaryView summary) {
        List<MilestoneEntry> milestones = milestoneEntries(rows, summary);
        for (int i = 0; i < SLOT_MILESTONES.length; i++) {
            if (i < milestones.size()) {
                inventory.setItem(SLOT_MILESTONES[i], milestoneItem(milestones.get(i), summary));
            } else {
                inventory.setItem(SLOT_MILESTONES[i], emptyMilestoneItem());
            }
        }
    }

    private List<MilestoneEntry> milestoneEntries(List<CityLevelRowView> rows, CityLevelSummaryView summary) {
        List<MilestoneEntry> milestones = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        for (int i = 0; i < rows.size(); i++) {
            CityLevelRowView row = rows.get(i);
            if (row.checkpointStage() == null || row.level() <= summary.currentLevel()) {
                continue;
            }
            milestones.add(new MilestoneEntry(i, row));
            if (milestones.size() >= SLOT_MILESTONES.length) {
                break;
            }
        }
        return List.copyOf(milestones);
    }

    private ItemStack milestoneItem(MilestoneEntry entry, CityLevelSummaryView summary) {
        CityLevelRowView row = entry.row();
        List<UiLoreStyle.StyledLine> lore = new ArrayList<>();
        addSection(lore, "Stato attuale:");
        addBullet(lore, UiLoreStyle.LineType.STATUS, "Livello citta attuale: " + summary.currentLevel());
        addBullet(lore, UiLoreStyle.LineType.STATUS, "Livello raggiunto con XP: " + summary.earnedLevel());
        addBullet(lore, UiLoreStyle.LineType.STATUS, "XP citta attuale: " + format(summary.currentXp()));
        addBullet(lore, UiLoreStyle.LineType.STATUS,
                "Avanzamento: " + progressBarLine(progressPercentForRow(row, summary), PROGRESS_BAR_WIDTH)
                        + " " + progressPercentForRow(row, summary) + "%");

        addSection(lore, "Requisiti:");
        addBullet(lore, UiLoreStyle.LineType.FOCUS, "Stage target: " + row.checkpointStage().displayName());
        addBullet(lore, UiLoreStyle.LineType.STATUS, "XP richiesta: " + format(row.requiredXp()));
        addBullet(lore, UiLoreStyle.LineType.WARNING,
                "XP mancanti: " + format(Math.max(0.0D, row.requiredXp() - summary.currentXp())));
        addBullet(lore, UiLoreStyle.LineType.REWARD, "Saldo banca richiesto: " + format(row.requiredBalance()));
        addBullet(lore, UiLoreStyle.LineType.STATUS,
                "Approvazione staff: " + (row.staffApprovalRequired() ? "richiesta" : "non richiesta"));
        if (row.spawnRequired()) {
            addBullet(lore, UiLoreStyle.LineType.FOCUS, "Warp citta: richiesto");
        }
        if (row.requiredDefenseTier() != null) {
            addBullet(lore, UiLoreStyle.LineType.FOCUS,
                    "Requisito invasione: " + row.requiredDefenseTier().id().toUpperCase());
            addBullet(lore,
                    row.defenseTierCompleted() ? UiLoreStyle.LineType.REWARD : UiLoreStyle.LineType.WARNING,
                    row.defenseTierCompleted() ? "Requisito invasione completato" : "Requisito invasione non completato"
            );
        }
        addSection(lore, "Costi e limiti:");
        addBullet(lore, UiLoreStyle.LineType.REWARD, "Costo upgrade: " + format(row.upgradeCost()));
        if (row.monthlyTax() > 0.0D) {
            addBullet(lore, UiLoreStyle.LineType.TIME, "Tassa mensile: " + format(row.monthlyTax()));
        }
        addBullet(lore, UiLoreStyle.LineType.INFO, "Claim massimi: " + row.claimCap());
        addBullet(lore, UiLoreStyle.LineType.INFO, "Membri massimi: " + row.memberCap());
        addBullet(lore, UiLoreStyle.LineType.INFO, "Spawner massimi: " + row.spawnerCap());
        if (row.level() == summary.currentLevel() + 1 && row.staffApprovalRequired()) {
            addBullet(lore, UiLoreStyle.LineType.SPECIAL, "Serve approvazione staff");
        } else if (row.level() == summary.currentLevel() + 1 && upgradeReady(row, summary)) {
            addBullet(lore, UiLoreStyle.LineType.ACTION, "Pronto per conferma del capo");
        }
        addSection(lore, "Azione:");
        addBullet(lore, UiLoreStyle.LineType.ACTION,
                row.level() == summary.currentLevel() + 1
                        ? "Questo e il prossimo checkpoint futuro"
                        : "Clicca per aprire questo milestone");
        return GuiItemFactory.item(
                milestoneMaterial(row.checkpointStage()),
                MiniMessageHelper.parse("<gold>" + row.checkpointStage().displayName() + "</gold>"),
                UiLoreStyle.renderFramedComponents(lore)
        );
    }

    private void addSection(List<UiLoreStyle.StyledLine> lore, String title) {
        if (!lore.isEmpty()) {
            lore.add(UiLoreStyle.spacer());
        }
        lore.add(UiLoreStyle.line(UiLoreStyle.LineType.FOCUS, title));
    }

    private void addBullet(List<UiLoreStyle.StyledLine> lore, UiLoreStyle.LineType type, String text) {
        lore.add(UiLoreStyle.line(type, "• " + text));
    }

    private ItemStack emptyMilestoneItem() {
        return GuiItemFactory.item(
                Material.GRAY_STAINED_GLASS_PANE,
                MiniMessageHelper.parse("<gray>Milestone vuoto</gray>"),
                UiLoreStyle.renderComponents(
                        UiLoreStyle.line(UiLoreStyle.LineType.INFO, "Nessun altro stage futuro da mostrare."),
                        UiLoreStyle.line(UiLoreStyle.LineType.INFO, "La timeline continua qui sotto."),
                        List.of()
                )
        );
    }

    private Material milestoneMaterial(TownStage stage) {
        if (stage == null) {
            return Material.PAPER;
        }
        return switch (stage.principalStage()) {
            case AVAMPOSTO -> Material.CAMPFIRE;
            case BORGO -> Material.BRICKS;
            case VILLAGGIO -> Material.BELL;
            case CITTA -> Material.LODESTONE;
            case REGNO -> Material.BEACON;
            default -> Material.PAPER;
        };
    }

    private int milestoneSlotIndex(int slot) {
        for (int i = 0; i < SLOT_MILESTONES.length; i++) {
            if (SLOT_MILESTONES[i] == slot) {
                return i;
            }
        }
        return -1;
    }

    private boolean upgradeReady(CityLevelRowView row, CityLevelSummaryView summary) {
        return row != null
                && summary != null
                && row.actionable()
                && !row.staffApprovalRequired()
                && row.level() == summary.currentLevel() + 1
                && summary.currentBalance() >= row.requiredBalance()
                && (row.requiredDefenseTier() == null || row.defenseTierCompleted());
    }

    private int progressPercentForRow(CityLevelRowView row, CityLevelSummaryView summary) {
        if (row == null || summary == null) {
            return 0;
        }
        if (row.level() <= summary.currentLevel() && summary.currentLevel() < cityLevelService.settings().levelCap()) {
            double fromXp = requiredXpForLevel(summary.currentLevel());
            double toXp = requiredXpForLevel(summary.currentLevel() + 1);
            return progressPercent(summary.currentXp(), fromXp, toXp);
        }
        double fromXp = row.level() <= 1 ? 0.0D : requiredXpForLevel(row.level() - 1);
        return progressPercent(summary.currentXp(), fromXp, row.requiredXp());
    }

    private int progressPercent(double currentXp, double fromXp, double toXp) {
        double span = Math.max(1.0D, toXp - fromXp);
        double progress = Math.max(0.0D, Math.min(span, currentXp - fromXp));
        return (int) Math.round((progress / span) * 100.0D);
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

    private void openUpgradeConfirmation(Player player, CityLevelRowView row, CityLevelSummaryView summary) {
        CityLevelService.LevelUnlockCheck check = cityLevelService.checkLevelUnlock(summary.townId(), row.level(), false);
        StringBuilder body = new StringBuilder();
        body.append("<gold>Livello ").append(check.targetLevel()).append("</gold><newline>");
        body.append("<gray>Livello sbloccato attuale:</gray> <white>").append(summary.currentLevel()).append("</white><newline>");
        body.append("<gray>Livello raggiunto con XP:</gray> <white>").append(summary.earnedLevel()).append("</white><newline>");
        body.append("<gray>XP citta:</gray> <white>").append(format(summary.currentXp())).append("</white><newline>");
        body.append("<gray>XP richiesta:</gray> <white>").append(format(check.requiredXp())).append("</white>");
        if (check.checkpointStage() != null) {
            body.append("<newline><gray>Checkpoint stage:</gray> <white>").append(check.checkpointStage().displayName()).append("</white>");
        }
        if (check.requiredBalance() > 0.0D) {
            body.append("<newline><gray>Saldo citta:</gray> <white>").append(format(summary.currentBalance())).append("</white>");
            body.append("<newline><gray>Saldo richiesto:</gray> <white>").append(format(check.requiredBalance())).append("</white>");
            body.append("<newline><gray>Costo upgrade:</gray> <white>").append(format(check.upgradeCost())).append("</white>");
        }
        if (check.monthlyTax() > 0.0D) {
            body.append("<newline><gray>Tassa mensile nuovo stage:</gray> <white>")
                    .append(format(check.monthlyTax())).append("</white>");
        }
        if (check.requiredDefenseTier() != null) {
            body.append("<newline><gray>Requisito invasione:</gray> <white>")
                    .append(check.requiredDefenseTier().id().toUpperCase()).append("</white>");
        }
        if (check.spawnRequired()) {
            body.append("<newline><gray>Warp richiesto:</gray> <white>/city setwarp</white>");
        }
        body.append("<newline><yellow>Il capo deve confermare questo livello in modo esplicito.</yellow>");
        if (check.staffApprovalRequired()) {
            body.append("<newline><yellow>Questo checkpoint richiede anche approvazione staff.</yellow>");
        }
        DialogConfirmHelper.open(
                plugin,
                player,
                MiniMessageHelper.parse("<gold>Conferma livello citta</gold>"),
                MiniMessageHelper.parse(body.toString()),
                MiniMessageHelper.parse(check.staffApprovalRequired() ? "<green>Crea richiesta</green>" : "<green>Conferma livello</green>"),
                MiniMessageHelper.parse("<red>Annulla</red>"),
                confirmed -> performUpgradeAttempt(confirmed, check),
                configUtils.msg("city.players.gui.levels.errors.staff_readonly", "<red>Dialog non disponibile.</red>")
        );
    }

    private void performUpgradeAttempt(Player player, CityLevelService.LevelUnlockCheck check) {
        if (check.staffApprovalRequired()) {
            CityLevelRequestService.RequestResult result = requestService.createRequest(player);
            if (!result.success()) {
                player.sendMessage(configUtils.msg(
                        "city.players.gui.levels.actions.request_failed",
                        "<red>Richiesta non creata:</red> <gray>{reason}</gray>",
                        Placeholder.unparsed("reason", mapReason(result.reason()))
                ));
            } else {
                TownLevelRequest request = result.request();
                player.sendMessage(configUtils.msg(
                        "city.players.gui.levels.actions.request_success",
                        "<green>Richiesta creata:</green> <yellow>#{id}</yellow> <gray>per</gray> <white>{stage}</white>",
                        Placeholder.unparsed("id", Long.toString(request.id())),
                        Placeholder.unparsed("stage", request.targetStage().displayName())
                ));
            }
        } else {
            CityLevelService.UpgradeAttempt attempt = cityLevelService.tryUpgrade(player);
            if (!attempt.success()) {
                player.sendMessage(configUtils.msg(
                        "city.players.gui.levels.actions.upgrade_failed",
                        "<red>Upgrade non riuscito:</red> <gray>{reason}</gray>",
                        Placeholder.unparsed("reason", mapReason(attempt.reason()))
                ));
            } else {
                TownStage upgraded = attempt.upgradedStage();
                String unlocked = upgraded == null ? "Livello " + check.targetLevel() : upgraded.displayName();
                player.sendMessage(configUtils.msg(
                        "city.players.gui.levels.actions.upgrade_success",
                        "<green>Upgrade completato:</green> <yellow>{stage}</yellow>",
                        Placeholder.unparsed("stage", unlocked)
                ));
            }
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            CityLevelGuiSession current = sessionsByViewer.get(player.getUniqueId());
            if (current != null) {
                openList(player, current);
            }
        }, 2L);
    }

    private ItemStack navPrev(boolean enabled, ItemStack transparentBase, int currentIndex, int totalLevels) {
        return TexturedGuiSupport.overlayButton(
                transparentBase,
                configUtils.msg(
                        enabled ? "city.players.gui.levels.nav.prev" : "city.players.gui.levels.nav.prev_disabled",
                        enabled ? "<yellow>Pagina precedente</yellow>" : "<gray>Pagina precedente</gray>"
                ),
                levelNavLore(currentIndex, totalLevels),
                GuiItemFactory.customHead(enabled ? GuiHeadTextures.NAV_LEFT : GuiHeadTextures.NAV_DISABLED, Component.empty())
        );
    }

    private ItemStack navNext(boolean enabled, ItemStack transparentBase, int currentIndex, int totalLevels) {
        return TexturedGuiSupport.overlayButton(
                transparentBase,
                configUtils.msg(
                        enabled ? "city.players.gui.levels.nav.next" : "city.players.gui.levels.nav.next_disabled",
                        enabled ? "<yellow>Pagina successiva</yellow>" : "<gray>Pagina successiva</gray>"
                ),
                levelNavLore(currentIndex, totalLevels),
                GuiItemFactory.customHead(enabled ? GuiHeadTextures.NAV_RIGHT : GuiHeadTextures.NAV_DISABLED_RIGHT, Component.empty())
        );
    }

    private ItemStack closeButton(ItemStack transparentBase, CityLevelGuiSession session) {
        return TexturedGuiSupport.overlayButton(
                transparentBase,
                configUtils.msg("city.players.gui.levels.nav.back", "<yellow>Indietro</yellow>"),
                List.of(),
                GuiItemFactory.customHead(GuiHeadTextures.BACK, Component.empty())
        );
    }

    private double requiredXpForLevel(int level) {
        long requiredXpScaled = it.patric.cittaexp.levels.CityLevelCurve.requiredXpScaledForLevel(
                level,
                cityLevelService.settings().xpPerLevel(),
                cityLevelService.settings().xpScale(),
                cityLevelService.settings().curveExponent()
        );
        return cityLevelService.toXp(requiredXpScaled);
    }

    private String requestStatusLabel(CityLevelRepository.RequestStatus status) {
        if (status == null) {
            return "-";
        }
        return switch (status) {
            case PENDING -> "In attesa";
            case APPROVED -> "Approvata";
            case REJECTED -> "Rifiutata";
            case CANCELLED -> "Annullata";
        };
    }

    private List<Component> levelNavLore(int currentIndex, int totalLevels) {
        return List.of(
                configUtils.msg(
                        "city.players.gui.levels.nav.page",
                        "<gold>Pagina {current}/{total}</gold>",
                        Placeholder.unparsed("current", Integer.toString(currentIndex + 1)),
                        Placeholder.unparsed("total", Integer.toString(totalLevels))
                )
        );
    }

    private int defaultLevelIndex(int townId) {
        List<CityLevelRowView> rows = timelineBuilder.build(townId).rows();
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).rowState() == CityLevelRowView.RowState.CURRENT) {
                return i;
            }
        }
        return 0;
    }

    private void returnFromLevels(Player player, CityLevelGuiSession session) {
        GuiNavigationUtils.openCityHub(plugin, player);
    }

    private LevelOpenResult createLevelsInventory(Holder holder, Component fallbackTitle) {
        TexturedGuiSupport.OpenResult result = TexturedGuiSupport.createInventory(
                plugin,
                holder,
                holder::bind,
                GUI_SIZE,
                fallbackTitle,
                configUtils.missingKeyFallback("city.players.gui.levels.itemsadder.title_plain", "Archivio delle Ascese"),
                configUtils.cfgInt("city.players.gui.levels.itemsadder.title_offset", 0),
                configUtils.cfgInt("city.players.gui.levels.itemsadder.texture_offset", -8),
                configUtils.cfgString("city.players.gui.levels.itemsadder.font_image_id", LEVELS_FONT_IMAGE_ID),
                "city-levels"
        );
        return new LevelOpenResult(result.inventory(), result.texturedWrapper());
    }

    private static boolean containsSlot(int[] slots, int target) {
        for (int slot : slots) {
            if (slot == target) {
                return true;
            }
        }
        return false;
    }

    private String mapReason(String reason) {
        return ReasonMessageMapper.text(configUtils, "city.level.reasons.", reason);
    }

    private String format(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private record MilestoneEntry(int rowIndex, CityLevelRowView row) {
    }

    private static final class Holder implements InventoryHolder {
        private final UUID sessionId;
        private Inventory inventory;

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

    private record LevelOpenResult(Inventory inventory, dev.lone.itemsadder.api.FontImages.TexturedInventoryWrapper texturedWrapper) {
    }
}
