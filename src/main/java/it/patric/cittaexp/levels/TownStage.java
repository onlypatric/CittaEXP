package it.patric.cittaexp.levels;

import java.util.Arrays;
import java.util.Optional;

public enum TownStage {

    BORGO_I("BORGO I", 1, 0.0D, 9, 3, 2, 0.0D, false, 1),
    BORGO_II("BORGO II", 10, 10_000.0D, 18, 5, 3, 0.0D, false, 2),
    BORGO_III("BORGO III", 20, 25_000.0D, 27, 7, 4, 25_000.0D, false, 3),
    VILLAGGIO_I("VILLAGGIO I", 30, 50_000.0D, 45, 10, 5, 50_000.0D, true, 4),
    VILLAGGIO_II("VILLAGGIO II", 50, 75_000.0D, 63, 13, 7, 75_000.0D, false, 5),
    VILLAGGIO_III("VILLAGGIO III", 70, 100_000.0D, 81, 16, 9, 100_000.0D, false, 6),
    REGNO_I("REGNO I", 100, 250_000.0D, 162, 50, 12, 250_000.0D, true, 7);

    private final String displayName;
    private final int requiredLevel;
    private final double upgradeCost;
    private final int claimCap;
    private final int memberCap;
    private final int spawnerCap;
    private final double monthlyTax;
    private final boolean staffApprovalRequired;
    private final int huskTownLevel;

    TownStage(
            String displayName,
            int requiredLevel,
            double upgradeCost,
            int claimCap,
            int memberCap,
            int spawnerCap,
            double monthlyTax,
            boolean staffApprovalRequired,
            int huskTownLevel
    ) {
        this.displayName = displayName;
        this.requiredLevel = requiredLevel;
        this.upgradeCost = upgradeCost;
        this.claimCap = claimCap;
        this.memberCap = memberCap;
        this.spawnerCap = spawnerCap;
        this.monthlyTax = monthlyTax;
        this.staffApprovalRequired = staffApprovalRequired;
        this.huskTownLevel = huskTownLevel;
    }

    public String displayName() {
        return displayName;
    }

    public int requiredLevel() {
        return requiredLevel;
    }

    public double upgradeCost() {
        return upgradeCost;
    }

    public int claimCap() {
        return claimCap;
    }

    public int memberCap() {
        return memberCap;
    }

    public int spawnerCap() {
        return spawnerCap;
    }

    public double monthlyTax() {
        return monthlyTax;
    }

    public boolean staffApprovalRequired() {
        return staffApprovalRequired;
    }

    public int huskTownLevel() {
        return huskTownLevel;
    }

    public Optional<TownStage> next() {
        int index = ordinal() + 1;
        TownStage[] values = values();
        if (index >= values.length) {
            return Optional.empty();
        }
        return Optional.of(values[index]);
    }

    public static TownStage fromDbValue(String value) {
        if (value == null || value.isBlank()) {
            return BORGO_I;
        }
        return Arrays.stream(values())
                .filter(stage -> stage.name().equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElse(BORGO_I);
    }
}
