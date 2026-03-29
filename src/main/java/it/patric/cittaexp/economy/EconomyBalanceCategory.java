package it.patric.cittaexp.economy;

public enum EconomyBalanceCategory {

    PLAYER_CITY_CREATE(Flow.COST),
    TOWN_RENAME(Flow.COST),
    TOWN_STAGE_UPGRADE(Flow.COST),
    TOWN_STAGE_REQUIRED_BALANCE(Flow.COST),
    TOWN_MONTHLY_TAX(Flow.COST),
    DEFENSE_START_COST(Flow.COST),
    DEFENSE_MONEY_REWARD(Flow.REWARD),
    CHALLENGE_MONEY_REWARD(Flow.REWARD),
    RACE_REWARD(Flow.REWARD),
    MILESTONE_MONEY_REWARD(Flow.REWARD),
    ECONOMIC_ITEM_REWARD(Flow.REWARD);

    public enum Flow {
        COST,
        REWARD
    }

    private final Flow flow;

    EconomyBalanceCategory(Flow flow) {
        this.flow = flow;
    }

    public boolean cost() {
        return flow == Flow.COST;
    }

    public boolean reward() {
        return flow == Flow.REWARD;
    }
}
