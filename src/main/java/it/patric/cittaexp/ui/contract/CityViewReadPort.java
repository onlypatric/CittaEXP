package it.patric.cittaexp.ui.contract;

import it.patric.cittaexp.preview.PreviewScenario;
import java.util.List;
import java.util.UUID;

public interface CityViewReadPort {

    CityViewSnapshot snapshot(UUID viewerId, PreviewScenario scenario);

    enum CityTier {
        BORGO,
        VILLAGGIO,
        REGNO
    }

    record MemberView(String name, String role, boolean online) {
    }

    record RoleFlagView(String roleName, boolean canInvite, boolean canKick, boolean canManageMembers) {
    }

    record CityViewSnapshot(
            String cityName,
            String cityTag,
            CityTier tier,
            boolean frozen,
            boolean capital,
            int rank,
            long score,
            int onlineMembers,
            int maxMembers,
            String nextTaxDue,
            String monthlyTaxLabel,
            List<MemberView> members,
            List<RoleFlagView> roleFlags
    ) {
    }
}
