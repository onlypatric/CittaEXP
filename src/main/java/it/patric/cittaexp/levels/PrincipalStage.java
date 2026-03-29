package it.patric.cittaexp.levels;

public enum PrincipalStage {
    AVAMPOSTO("Avamposto"),
    BORGO("Borgo"),
    VILLAGGIO("Villaggio"),
    CITTADINA("Cittadina"),
    CITTA("Citta"),
    REGNO("Regno");

    private final String displayName;

    PrincipalStage(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}

