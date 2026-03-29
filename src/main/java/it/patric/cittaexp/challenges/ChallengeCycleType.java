package it.patric.cittaexp.challenges;

public enum ChallengeCycleType {
    DAILY("daily"),
    WEEKLY("weekly"),
    MONTHLY("monthly"),
    SEASONAL("seasonal");

    private final String id;

    ChallengeCycleType(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
