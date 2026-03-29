package it.patric.cittaexp.cityoverlaytest;

public enum OverlayMissionStatus {
    LOCKED("Bloccata"),
    AVAILABLE("Disponibile"),
    IN_PROGRESS("In corso"),
    CLAIMABLE("Riscattabile"),
    COMPLETED("Completata");

    private final String label;

    OverlayMissionStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
