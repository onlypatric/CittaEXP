package it.patric.cittaexp.guitest;

public enum MissionBoardStatus {
    AVAILABLE("Disponibile"),
    CLAIMABLE("Riscattabile"),
    COMPLETED("Completata");

    private final String displayName;

    MissionBoardStatus(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
