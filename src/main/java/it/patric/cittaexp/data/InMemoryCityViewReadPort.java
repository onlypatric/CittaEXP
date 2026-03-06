package it.patric.cittaexp.data;

import it.patric.cittaexp.preview.PreviewScenario;
import it.patric.cittaexp.ui.contract.CityViewReadPort;
import java.util.List;
import java.util.UUID;

public final class InMemoryCityViewReadPort implements CityViewReadPort {

    @Override
    public CityViewSnapshot snapshot(UUID viewerId, PreviewScenario scenario) {
        return switch (scenario) {
            case DEFAULT -> defaultSnapshot();
            case FREEZE -> freezeSnapshot();
            case CAPITAL -> capitalSnapshot();
            case LOWFUNDS -> lowFundsSnapshot();
            case KINGDOM -> kingdomSnapshot();
        };
    }

    private CityViewSnapshot defaultSnapshot() {
        return new CityViewSnapshot(
                "Borgo Aurora",
                "AUR",
                CityTier.BORGO,
                false,
                false,
                18,
                1240L,
                5,
                10,
                "2026-04-01",
                "2 Netherite Blocks",
                membersDefault(),
                roleFlagsDefault()
        );
    }

    private CityViewSnapshot freezeSnapshot() {
        return new CityViewSnapshot(
                "Villaggio Brina",
                "BRI",
                CityTier.VILLAGGIO,
                true,
                false,
                9,
                3250L,
                8,
                14,
                "2026-03-10",
                "4 Netherite Blocks",
                membersDefault(),
                roleFlagsRestricted()
        );
    }

    private CityViewSnapshot capitalSnapshot() {
        return new CityViewSnapshot(
                "Regno Solaris",
                "SOL",
                CityTier.REGNO,
                false,
                true,
                1,
                11840L,
                23,
                50,
                "Esente (Capitale)",
                "0 (Capital bonus attivo)",
                membersDefault(),
                roleFlagsDefault()
        );
    }

    private CityViewSnapshot lowFundsSnapshot() {
        return new CityViewSnapshot(
                "Borgo Cenere",
                "CEN",
                CityTier.BORGO,
                false,
                false,
                34,
                620L,
                3,
                10,
                "2026-03-12",
                "2 Netherite Blocks (Rischio freeze)",
                membersDefault(),
                roleFlagsDefault()
        );
    }

    private CityViewSnapshot kingdomSnapshot() {
        return new CityViewSnapshot(
                "Regno Ventoalto",
                "VEN",
                CityTier.REGNO,
                false,
                false,
                4,
                8650L,
                17,
                50,
                "2026-04-01",
                "6 Netherite Blocks (+10 Shop)",
                membersKingdom(),
                roleFlagsDefault()
        );
    }

    private List<MemberView> membersDefault() {
        return List.of(
                new MemberView("Patric", "Capo", true),
                new MemberView("Ari", "Ufficiale", true),
                new MemberView("Milo", "Recruiter", false),
                new MemberView("Nora", "Builder", true),
                new MemberView("Ivy", "Membro", false)
        );
    }

    private List<MemberView> membersKingdom() {
        return List.of(
                new MemberView("Patric", "Sovrano", true),
                new MemberView("Kira", "Generale", true),
                new MemberView("Orion", "Treasurer", false),
                new MemberView("Lyra", "Builder", true),
                new MemberView("Raven", "Guard", true)
        );
    }

    private List<RoleFlagView> roleFlagsDefault() {
        return List.of(
                new RoleFlagView("Capo", true, true, true),
                new RoleFlagView("Ufficiale", true, true, true),
                new RoleFlagView("Recruiter", true, false, false),
                new RoleFlagView("Membro", false, false, false)
        );
    }

    private List<RoleFlagView> roleFlagsRestricted() {
        return List.of(
                new RoleFlagView("Capo", false, false, false),
                new RoleFlagView("Ufficiale", false, false, false),
                new RoleFlagView("Recruiter", false, false, false),
                new RoleFlagView("Membro", false, false, false)
        );
    }
}
