package it.patric.cittaexp.levels;

public enum SubTier {
    I("I", 0),
    II("II", 2),
    III("III", 4);

    private final String displayName;
    private final int requiredSeals;

    SubTier(String displayName, int requiredSeals) {
        this.displayName = displayName;
        this.requiredSeals = requiredSeals;
    }

    public String displayName() {
        return displayName;
    }

    public int requiredSeals() {
        return requiredSeals;
    }
}

