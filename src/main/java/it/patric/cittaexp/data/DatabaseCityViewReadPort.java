package it.patric.cittaexp.data;

import it.patric.cittaexp.core.model.CityRole;
import it.patric.cittaexp.core.port.RankingPort;
import it.patric.cittaexp.core.runtime.DefaultCityLifecycleService;
import it.patric.cittaexp.persistence.port.CityReadPort;
import it.patric.cittaexp.preview.PreviewScenario;
import it.patric.cittaexp.ui.contract.CityViewReadPort;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Bukkit;

public final class DatabaseCityViewReadPort implements CityViewReadPort {

    private final DefaultCityLifecycleService lifecycleService;
    private final CityReadPort cityReadPort;
    private final RankingPort rankingPort;
    private final InMemoryCityViewReadPort fallback;

    public DatabaseCityViewReadPort(
            DefaultCityLifecycleService lifecycleService,
            CityReadPort cityReadPort,
            RankingPort rankingPort
    ) {
        this.lifecycleService = Objects.requireNonNull(lifecycleService, "lifecycleService");
        this.cityReadPort = Objects.requireNonNull(cityReadPort, "cityReadPort");
        this.rankingPort = Objects.requireNonNull(rankingPort, "rankingPort");
        this.fallback = new InMemoryCityViewReadPort();
    }

    @Override
    public CityViewSnapshot snapshot(UUID viewerId, PreviewScenario scenario) {
        return lifecycleService.cityByPlayer(viewerId)
                .map(city -> {
                    List<MemberView> members = lifecycleService.listActiveMembers(city.cityId()).stream()
                            .map(member -> {
                                String name = safeName(member.playerUuid());
                                boolean online = Bukkit.getPlayer(member.playerUuid()) != null;
                                return new MemberView(name, member.roleKey(), online);
                            })
                            .toList();

                    List<RoleFlagView> roleFlags = lifecycleService.listRoles(city.cityId()).stream()
                            .map(this::toRoleFlag)
                            .toList();

                    var ranking = rankingPort.findByCityId(city.cityId()).orElse(null);
                    int rank = ranking == null ? 0 : ranking.rank();
                    long score = ranking == null ? 0L : ranking.score();

                    return new CityViewSnapshot(
                            city.name(),
                            city.tag(),
                            switch (city.tier()) {
                                case BORGO -> CityTier.BORGO;
                                case VILLAGGIO -> CityTier.VILLAGGIO;
                                case REGNO -> CityTier.REGNO;
                            },
                            city.status().name().equals("FROZEN"),
                            city.capital(),
                            rank,
                            score,
                            members.stream().mapToInt(m -> m.online() ? 1 : 0).sum(),
                            city.maxMembers(),
                            nextDueDateLabel(),
                            monthlyTaxLabel(city.tier().name()),
                            members,
                            roleFlags
                    );
                })
                .orElseGet(() -> fallback.snapshot(viewerId, scenario));
    }

    private RoleFlagView toRoleFlag(CityRole role) {
        return new RoleFlagView(
                role.displayName(),
                role.permissions().canInvite(),
                role.permissions().canKick(),
                role.permissions().canManageMembers()
        );
    }

    private static String safeName(UUID playerUuid) {
        try {
            String name = Bukkit.getOfflinePlayer(playerUuid).getName();
            if (name != null && !name.isBlank()) {
                return name;
            }
        } catch (RuntimeException ignored) {
            // fallback uuid fragment
        }
        return playerUuid.toString().substring(0, 8);
    }

    private static String nextDueDateLabel() {
        LocalDate now = LocalDate.now(ZoneId.systemDefault());
        LocalDate due = now.withDayOfMonth(1).plusMonths(1);
        return due.toString();
    }

    private static String monthlyTaxLabel(String tier) {
        return switch (tier) {
            case "BORGO" -> "2 Blocchi di Netherite";
            case "VILLAGGIO" -> "4 Blocchi di Netherite";
            case "REGNO" -> "6 Blocchi di Netherite";
            default -> "n/a";
        };
    }
}
