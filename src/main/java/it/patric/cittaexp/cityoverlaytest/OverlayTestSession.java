package it.patric.cittaexp.cityoverlaytest;

import java.util.UUID;

final class OverlayTestSession {

    enum MissionFilter {
        ALL("Tutte"),
        AVAILABLE("Disponibili"),
        CLAIMABLE("Claimabili"),
        COMPLETED("Completate");

        private final String label;

        MissionFilter(String label) {
            this.label = label;
        }

        MissionFilter next() {
            MissionFilter[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        String label() {
            return label;
        }
    }

    enum MissionSort {
        PRIORITY("Priorita"),
        TIME_LEFT("Tempo residuo"),
        PROGRESS("Progresso"),
        XP_REWARD("XP reward");

        private final String label;

        MissionSort(String label) {
            this.label = label;
        }

        MissionSort next() {
            MissionSort[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        String label() {
            return label;
        }
    }

    enum TerritoryMapMode {
        CLAIM("Claim"),
        TYPE("Tipo"),
        INFO("Info");

        private final String label;

        TerritoryMapMode(String label) {
            this.label = label;
        }

        TerritoryMapMode next() {
            TerritoryMapMode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        String label() {
            return label;
        }
    }

    private final UUID sessionId;
    private final UUID viewerId;
    private OverlayTestRoute route;
    private MissionFilter missionFilter;
    private MissionSort missionSort;
    private String selectedMissionKey;
    private String selectedMemberName;
    private TerritoryMapMode territoryMapMode;
    private int cityListPage;
    private String selectedDefenseTier;

    OverlayTestSession(UUID sessionId, UUID viewerId) {
        this.sessionId = sessionId;
        this.viewerId = viewerId;
        this.route = new OverlayTestRoute(OverlayTestView.HUB, null);
        this.missionFilter = MissionFilter.ALL;
        this.missionSort = MissionSort.PRIORITY;
        this.selectedMissionKey = "daily";
        this.selectedMemberName = "Patric";
        this.territoryMapMode = TerritoryMapMode.CLAIM;
        this.cityListPage = 0;
        this.selectedDefenseTier = "L1";
    }

    UUID sessionId() {
        return sessionId;
    }

    UUID viewerId() {
        return viewerId;
    }

    OverlayTestRoute route() {
        return route;
    }

    void route(OverlayTestRoute route) {
        this.route = route;
    }

    MissionFilter missionFilter() {
        return missionFilter;
    }

    void missionFilter(MissionFilter missionFilter) {
        this.missionFilter = missionFilter;
    }

    MissionSort missionSort() {
        return missionSort;
    }

    void missionSort(MissionSort missionSort) {
        this.missionSort = missionSort;
    }

    String selectedMissionKey() {
        return selectedMissionKey;
    }

    void selectedMissionKey(String selectedMissionKey) {
        this.selectedMissionKey = selectedMissionKey;
    }

    String selectedMemberName() {
        return selectedMemberName;
    }

    void selectedMemberName(String selectedMemberName) {
        this.selectedMemberName = selectedMemberName;
    }

    TerritoryMapMode territoryMapMode() {
        return territoryMapMode;
    }

    void territoryMapMode(TerritoryMapMode territoryMapMode) {
        this.territoryMapMode = territoryMapMode;
    }

    int cityListPage() {
        return cityListPage;
    }

    void cityListPage(int cityListPage) {
        this.cityListPage = cityListPage;
    }

    String selectedDefenseTier() {
        return selectedDefenseTier;
    }

    void selectedDefenseTier(String selectedDefenseTier) {
        this.selectedDefenseTier = selectedDefenseTier;
    }
}
