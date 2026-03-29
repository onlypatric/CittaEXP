package it.patric.cittaexp.levelsgui;

import it.patric.cittaexp.defense.CityDefenseService;
import it.patric.cittaexp.integration.husktowns.HuskTownsApiHook;
import it.patric.cittaexp.levels.CityLevelService;
import it.patric.cittaexp.text.MiniMessageHelper;
import it.patric.cittaexp.text.TimedTitleHelper;
import it.patric.cittaexp.text.UiChatStyle;
import it.patric.cittaexp.utils.PluginConfigUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.kyori.adventure.text.Component;
import net.william278.husktowns.town.Member;
import net.william278.husktowns.town.Town;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

public final class CityLevelUpgradeNoticeService implements Listener {

    private static final long CHECK_INTERVAL_TICKS = 20L * 30L;

    private final Plugin plugin;
    private final HuskTownsApiHook huskTownsApiHook;
    private final CityLevelService cityLevelService;
    private final PluginConfigUtils configUtils;
    private final ConcurrentMap<Integer, Integer> announcedLevelByTown = new ConcurrentHashMap<>();
    private int taskId = -1;

    public CityLevelUpgradeNoticeService(
            Plugin plugin,
            HuskTownsApiHook huskTownsApiHook,
            CityLevelService cityLevelService,
            CityDefenseService cityDefenseService
    ) {
        this.plugin = plugin;
        this.huskTownsApiHook = huskTownsApiHook;
        this.cityLevelService = cityLevelService;
        this.configUtils = new PluginConfigUtils(plugin);
    }

    public void start() {
        if (taskId >= 0) {
            return;
        }
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::refreshAllTowns, CHECK_INTERVAL_TICKS, CHECK_INTERVAL_TICKS);
    }

    public void stop() {
        if (taskId >= 0) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        announcedLevelByTown.clear();
    }

    public void refreshTown(int townId, boolean broadcastIfNew) {
        Optional<Availability> availability = availableUpgrade(townId);
        if (availability.isEmpty()) {
            announcedLevelByTown.remove(townId);
            return;
        }
        Availability available = availability.get();
        Integer previous = announcedLevelByTown.put(available.townId(), available.level());
        if (broadcastIfNew && !java.util.Objects.equals(previous, available.level())) {
            broadcastAvailableUpgrade(available);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Optional<Member> member = huskTownsApiHook.getUserTown(player);
        if (member.isEmpty()) {
            return;
        }
        refreshTown(member.get().town().getId(), false);
        availableUpgrade(member.get().town().getId()).ifPresent(availability -> player.sendMessage(MiniMessageHelper.parse(reminderMessage(availability))));
    }

    private void refreshAllTowns() {
        for (Town town : huskTownsApiHook.getPlayableTowns()) {
            refreshTown(town.getId(), true);
        }
    }

    private Optional<Availability> availableUpgrade(int townId) {
        CityLevelService.LevelUnlockCheck check = cityLevelService.nextUnlockCheck(townId, false);
        if (check.targetLevel() <= 0 || check.staffApprovalRequired() || !check.canUnlockNow()) {
            return Optional.empty();
        }
        return Optional.of(new Availability(
                check.townId(),
                check.townName(),
                check.targetLevel(),
                check.checkpointStage() == null ? null : check.checkpointStage().displayName(),
                check.requiredBalance(),
                check.upgradeCost(),
                check.monthlyTax(),
                check.requiredDefenseTier() == null ? null : check.requiredDefenseTier().id().toUpperCase()
        ));
    }

    private void broadcastAvailableUpgrade(Availability availability) {
        Optional<Town> townOptional = huskTownsApiHook.getTownById(availability.townId());
        if (townOptional.isEmpty()) {
            return;
        }
        String message = reminderMessage(availability);
        Component title = configUtils.msg(
                "city.level.notifications.upgrade_available.title",
                "<gold><bold>UPGRADE DISPONIBILE</bold></gold>"
        );
        Component subtitle = configUtils.msg(
                "city.level.notifications.upgrade_available.subtitle",
                "<yellow>Livello {level}</yellow> <gray>puo essere confermato ora</gray>",
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("level", Integer.toString(availability.level()))
        );
        for (UUID memberId : townOptional.get().getMembers().keySet()) {
            Player online = Bukkit.getPlayer(memberId);
            if (online != null) {
                online.sendMessage(MiniMessageHelper.parse(message));
                TimedTitleHelper.show(online, title, subtitle);
            }
        }
    }

    private String reminderMessage(Availability availability) {
        List<UiChatStyle.DetailLine> details = new java.util.ArrayList<>();
        details.add(UiChatStyle.detail(UiChatStyle.DetailType.CONTEXT, "Citta': " + availability.townName()));
        details.add(UiChatStyle.detail(UiChatStyle.DetailType.CONTEXT, "Livello pronto: " + availability.level()));
        if (availability.stageName() != null && !availability.stageName().isBlank()) {
            details.add(UiChatStyle.detail(UiChatStyle.DetailType.CONTEXT, "Checkpoint stage: " + availability.stageName()));
        }
        details.add(UiChatStyle.detail(UiChatStyle.DetailType.REWARD, "Saldo richiesto: " + format(availability.requiredBalance())));
        details.add(UiChatStyle.detail(UiChatStyle.DetailType.REWARD, "Costo upgrade: " + format(availability.upgradeCost())));
        if (availability.monthlyTax() > 0.0D) {
            details.add(UiChatStyle.detail(UiChatStyle.DetailType.STATUS, "Tassa mensile del nuovo stage: " + format(availability.monthlyTax())));
        }
        if (availability.requiredDefenseTier() != null && !availability.requiredDefenseTier().isBlank()) {
            details.add(UiChatStyle.detail(UiChatStyle.DetailType.STATUS, "Requisito invasione gia completato: " + availability.requiredDefenseTier()));
        }
        details.add(UiChatStyle.detail(UiChatStyle.DetailType.NOTE, "Apri /city -> Livelli e conferma l'upgrade dal capo della citta."));
        return UiChatStyle.render(
                "LIVELLO CITTA DISPONIBILE",
                "Livello " + availability.level(),
                "Il prossimo livello puo essere confermato ora",
                details
        );
    }

    private String format(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private record Availability(
            int townId,
            String townName,
            int level,
            String stageName,
            double requiredBalance,
            double upgradeCost,
            double monthlyTax,
            String requiredDefenseTier
    ) {
    }
}
