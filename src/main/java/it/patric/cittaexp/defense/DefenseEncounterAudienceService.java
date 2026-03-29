package it.patric.cittaexp.defense;

import it.patric.cittaexp.custommobs.CustomMobBossRuntimeSnapshot;
import it.patric.cittaexp.integration.husktowns.HuskTownsApiHook;
import it.patric.cittaexp.text.MiniMessageHelper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

final class DefenseEncounterAudienceService {

    private final HuskTownsApiHook huskTownsApiHook;

    DefenseEncounterAudienceService(HuskTownsApiHook huskTownsApiHook) {
        this.huskTownsApiHook = huskTownsApiHook;
    }

    Audience audienceForDefenseSession(int townId, Location guardianLocation, double radiusSquared) {
        return Audience.audience(viewersForDefenseSession(townId, guardianLocation, radiusSquared));
    }

    void broadcastLocal(int townId, Location guardianLocation, double radiusSquared, Component message) {
        audienceForDefenseSession(townId, guardianLocation, radiusSquared).sendMessage(message);
    }

    void sendActionBarLocal(int townId, Location guardianLocation, double radiusSquared, Component message) {
        audienceForDefenseSession(townId, guardianLocation, radiusSquared).sendActionBar(message);
    }

    void showTitleLocal(int townId, Location guardianLocation, double radiusSquared, Title title) {
        audienceForDefenseSession(townId, guardianLocation, radiusSquared).showTitle(title);
    }

    void syncSessionBar(
            DefenseSessionBarState sessionBarState,
            int townId,
            Location guardianLocation,
            double radiusSquared,
            Component title,
            float progress,
            BossBar.Color color,
            BossBar.Overlay overlay
    ) {
        BossBar bar = sessionBarState.bossBar();
        if (bar == null) {
            bar = BossBar.bossBar(title, progress, color, overlay);
            sessionBarState.bossBar(bar);
        } else {
            bar.name(title);
            bar.progress(progress);
            bar.color(color);
            bar.overlay(overlay);
        }

        List<Player> viewers = viewersForDefenseSession(townId, guardianLocation, radiusSquared);
        Set<UUID> visibleNow = ConcurrentHashMap.newKeySet();
        for (Player viewer : viewers) {
            viewer.showBossBar(bar);
            visibleNow.add(viewer.getUniqueId());
        }
        for (UUID previousViewerId : new ArrayList<>(sessionBarState.viewerIds())) {
            if (visibleNow.contains(previousViewerId)) {
                continue;
            }
            Player viewer = Bukkit.getPlayer(previousViewerId);
            if (viewer != null) {
                viewer.hideBossBar(bar);
            }
        }
        sessionBarState.viewerIds().clear();
        sessionBarState.viewerIds().addAll(visibleNow);
    }

    void clearSessionBar(DefenseSessionBarState sessionBarState) {
        BossBar bossBar = sessionBarState.bossBar();
        if (bossBar != null) {
            for (UUID viewerId : new ArrayList<>(sessionBarState.viewerIds())) {
                Player viewer = Bukkit.getPlayer(viewerId);
                if (viewer != null) {
                    viewer.hideBossBar(bossBar);
                }
            }
        }
        sessionBarState.bossBar(null);
        sessionBarState.viewerIds().clear();
    }

    void syncBossBar(
            BossEncounterState encounter,
            int townId,
            String townName,
            Location guardianLocation,
            double radiusSquared,
            LivingEntity boss,
            CustomMobBossRuntimeSnapshot snapshot
    ) {
        if (!snapshot.bossbarEnabled()) {
            clearBossBar(encounter);
            return;
        }
        float progress = boss.getAttribute(Attribute.MAX_HEALTH) == null || boss.getAttribute(Attribute.MAX_HEALTH).getValue() <= 0.0D
                ? 0.0F
                : (float) Math.max(0.0D, Math.min(1.0D, boss.getHealth() / boss.getAttribute(Attribute.MAX_HEALTH).getValue()));
        Component title = MiniMessageHelper.parse(
                snapshot.bossbarSpec().titleTemplate(),
                Placeholder.parsed("boss", snapshot.displayName()),
                Placeholder.unparsed("phase", snapshot.phaseLabel()),
                Placeholder.unparsed("town", townName)
        );
        BossBar bar = encounter.bossBar();
        if (bar == null) {
            bar = BossBar.bossBar(title, progress, snapshot.bossbarSpec().color(), snapshot.bossbarSpec().overlay());
            encounter.bossBar(bar);
        } else {
            bar.name(title);
            bar.progress(progress);
            bar.color(snapshot.bossbarSpec().color());
            bar.overlay(snapshot.bossbarSpec().overlay());
        }

        List<Player> viewers = viewersForDefenseSession(townId, guardianLocation, radiusSquared);
        Set<UUID> visibleNow = ConcurrentHashMap.newKeySet();
        for (Player viewer : viewers) {
            viewer.showBossBar(bar);
            visibleNow.add(viewer.getUniqueId());
        }
        for (UUID previousViewerId : new ArrayList<>(encounter.viewerIds())) {
            if (visibleNow.contains(previousViewerId)) {
                continue;
            }
            Player viewer = Bukkit.getPlayer(previousViewerId);
            if (viewer != null) {
                viewer.hideBossBar(bar);
            }
        }
        encounter.viewerIds().clear();
        encounter.viewerIds().addAll(visibleNow);
    }

    void clearBossBar(BossEncounterState encounter) {
        BossBar bossBar = encounter.bossBar();
        if (bossBar != null) {
            for (UUID viewerId : new ArrayList<>(encounter.viewerIds())) {
                Player viewer = Bukkit.getPlayer(viewerId);
                if (viewer != null) {
                    viewer.hideBossBar(bossBar);
                }
            }
        }
        encounter.bossBar(null);
        encounter.viewerIds().clear();
    }

    private List<Player> viewersForDefenseSession(int townId, Location guardianLocation, double radiusSquared) {
        if (guardianLocation == null || guardianLocation.getWorld() == null) {
            return List.of();
        }
        List<Player> viewers = new ArrayList<>();
        Collection<UUID> memberIds = huskTownsApiHook.getTownMembers(townId).keySet();
        for (UUID memberId : memberIds) {
            Player online = Bukkit.getPlayer(memberId);
            if (online == null || !online.isOnline()) {
                continue;
            }
            if (!sameWorld(guardianLocation, online.getLocation())) {
                continue;
            }
            if (guardianLocation.distanceSquared(online.getLocation()) > radiusSquared) {
                continue;
            }
            viewers.add(online);
        }
        return List.copyOf(viewers);
    }

    private static boolean sameWorld(Location a, Location b) {
        if (a == null || b == null || a.getWorld() == null || b.getWorld() == null) {
            return false;
        }
        return a.getWorld().getUID().equals(b.getWorld().getUID());
    }
}
