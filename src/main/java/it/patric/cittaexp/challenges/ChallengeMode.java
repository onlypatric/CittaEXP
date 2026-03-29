package it.patric.cittaexp.challenges;

public enum ChallengeMode {
    DAILY_STANDARD(ChallengeCycleType.DAILY, false),
    DAILY_SPRINT_1830(ChallengeCycleType.DAILY, true),
    DAILY_SPRINT_2130(ChallengeCycleType.DAILY, true),
    DAILY_RACE_1600(ChallengeCycleType.DAILY, true),
    DAILY_RACE_2100(ChallengeCycleType.DAILY, true),
    WEEKLY_CLASH(ChallengeCycleType.WEEKLY, true),
    MONTHLY_CROWN(ChallengeCycleType.MONTHLY, true),
    MONTHLY_LEDGER_GRAND(ChallengeCycleType.MONTHLY, false),
    MONTHLY_LEDGER_CONTRACT(ChallengeCycleType.MONTHLY, false),
    MONTHLY_LEDGER_MYSTERY(ChallengeCycleType.MONTHLY, false),
    SEASON_CODEX_ACT_I(ChallengeCycleType.SEASONAL, false),
    SEASON_CODEX_ACT_II(ChallengeCycleType.SEASONAL, false),
    SEASON_CODEX_ACT_III(ChallengeCycleType.SEASONAL, false),
    SEASON_CODEX_ELITE(ChallengeCycleType.SEASONAL, false),
    SEASON_CODEX_HIDDEN_RELIC(ChallengeCycleType.SEASONAL, false),
    SEASONAL_RACE(ChallengeCycleType.SEASONAL, true),
    WEEKLY_STANDARD(ChallengeCycleType.WEEKLY, false),
    MONTHLY_STANDARD(ChallengeCycleType.MONTHLY, false),
    MONTHLY_EVENT_A(ChallengeCycleType.MONTHLY, false),
    MONTHLY_EVENT_B(ChallengeCycleType.MONTHLY, false);

    private final ChallengeCycleType cycleType;
    private final boolean race;

    ChallengeMode(ChallengeCycleType cycleType, boolean race) {
        this.cycleType = cycleType;
        this.race = race;
    }

    public boolean race() {
        return race;
    }

    public ChallengeCycleType cycleType() {
        return cycleType;
    }

    public boolean weeklyStandard() {
        return this == WEEKLY_STANDARD;
    }

    public boolean monthlyEvent() {
        return this == MONTHLY_EVENT_A || this == MONTHLY_EVENT_B || this == MONTHLY_CROWN;
    }

    public boolean dailyRace() {
        return this == DAILY_RACE_1600
                || this == DAILY_RACE_2100
                || this == DAILY_SPRINT_1830
                || this == DAILY_SPRINT_2130;
    }

    public boolean seasonalRace() {
        return this == SEASONAL_RACE;
    }

    public boolean m8Race() {
        return this == DAILY_SPRINT_1830
                || this == DAILY_SPRINT_2130
                || this == WEEKLY_CLASH
                || this == MONTHLY_CROWN;
    }

    public boolean monthlyLedger() {
        return this == MONTHLY_LEDGER_GRAND
                || this == MONTHLY_LEDGER_CONTRACT
                || this == MONTHLY_LEDGER_MYSTERY;
    }

    public boolean seasonCodex() {
        return this == SEASON_CODEX_ACT_I
                || this == SEASON_CODEX_ACT_II
                || this == SEASON_CODEX_ACT_III
                || this == SEASON_CODEX_ELITE
                || this == SEASON_CODEX_HIDDEN_RELIC;
    }

    public boolean legacyMode() {
        return this == MONTHLY_EVENT_A
                || this == MONTHLY_EVENT_B
                || this == SEASONAL_RACE
                || this == DAILY_RACE_1600
                || this == DAILY_RACE_2100
                || this == MONTHLY_STANDARD;
    }
}
