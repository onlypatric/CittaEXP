package it.patric.cittaexp.guitest;

import java.util.List;
import org.bukkit.Material;

public final class StaticMissionBoardDataProvider implements MissionBoardDataProvider {

    @Override
    public List<MissionBoardTestViewModel> load() {
        return List.of(
                new MissionBoardTestViewModel(
                        "daily",
                        "Daily",
                        Material.CLOCK,
                        MissionBoardStatus.AVAILABLE,
                        34,
                        235,
                        22,
                        "64x Bread",
                        90
                ),
                new MissionBoardTestViewModel(
                        "race",
                        "Race",
                        Material.BLAZE_POWDER,
                        MissionBoardStatus.CLAIMABLE,
                        100,
                        26,
                        48,
                        "32x Iron Ingot",
                        100
                ),
                new MissionBoardTestViewModel(
                        "weekly",
                        "Weekly",
                        Material.IRON_SWORD,
                        MissionBoardStatus.AVAILABLE,
                        58,
                        3180,
                        64,
                        "1x Diamond Chestplate",
                        80
                ),
                new MissionBoardTestViewModel(
                        "monthly",
                        "Monthly",
                        Material.NETHER_STAR,
                        MissionBoardStatus.AVAILABLE,
                        12,
                        28740,
                        112,
                        "16x Diamond",
                        70
                ),
                new MissionBoardTestViewModel(
                        "event",
                        "Event",
                        Material.FIREWORK_STAR,
                        MissionBoardStatus.COMPLETED,
                        100,
                        420,
                        85,
                        "Evento concluso",
                        75
                ),
                new MissionBoardTestViewModel(
                        "seasonal",
                        "Seasonal",
                        Material.DRAGON_EGG,
                        MissionBoardStatus.AVAILABLE,
                        7,
                        61200,
                        125,
                        "Token Vanity x3",
                        60
                )
        );
    }
}
