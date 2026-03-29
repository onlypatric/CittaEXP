package it.patric.cittaexp.levels;

import java.util.Arrays;
import java.util.Optional;

public enum TownStage {

    AVAMPOSTO_I("Avamposto I", PrincipalStage.AVAMPOSTO, SubTier.I, 1, 0.0D, 9, 3, 2, 0, 0.0D, false, 1),
    AVAMPOSTO_II("Avamposto II", PrincipalStage.AVAMPOSTO, SubTier.II, 4, 1_200.0D, 12, 4, 2, 0, 0.0D, false, 1),
    AVAMPOSTO_III("Avamposto III", PrincipalStage.AVAMPOSTO, SubTier.III, 7, 2_500.0D, 15, 4, 3, 0, 0.0D, false, 1),

    BORGO_I("Borgo I", PrincipalStage.BORGO, SubTier.I, 10, 4_000.0D, 18, 5, 3, 0, 0.0D, false, 2),
    BORGO_II("Borgo II", PrincipalStage.BORGO, SubTier.II, 15, 6_000.0D, 23, 6, 4, 0, 250.0D, false, 2),
    BORGO_III("Borgo III", PrincipalStage.BORGO, SubTier.III, 20, 8_000.0D, 27, 7, 4, 0, 500.0D, false, 3),

    VILLAGGIO_I("Villaggio I", PrincipalStage.VILLAGGIO, SubTier.I, 25, 14_000.0D, 36, 9, 5, 0, 1_000.0D, true, 4),
    VILLAGGIO_II("Villaggio II", PrincipalStage.VILLAGGIO, SubTier.II, 32, 22_000.0D, 47, 10, 5, 0, 1_800.0D, false, 4),
    VILLAGGIO_III("Villaggio III", PrincipalStage.VILLAGGIO, SubTier.III, 39, 32_000.0D, 53, 11, 6, 0, 2_800.0D, false, 4),

    CITTADINA_I("Cittadina I", PrincipalStage.CITTADINA, SubTier.I, 45, 40_000.0D, 59, 12, 7, 20, 3_400.0D, false, 5),
    CITTADINA_II("Cittadina II", PrincipalStage.CITTADINA, SubTier.II, 54, 46_000.0D, 67, 14, 7, 30, 3_900.0D, false, 5),
    CITTADINA_III("Cittadina III", PrincipalStage.CITTADINA, SubTier.III, 63, 52_000.0D, 75, 15, 8, 40, 4_500.0D, false, 5),

    CITTA_I("Citta I", PrincipalStage.CITTA, SubTier.I, 70, 60_000.0D, 81, 16, 9, 60, 5_200.0D, false, 6),
    CITTA_II("Citta II", PrincipalStage.CITTA, SubTier.II, 80, 72_000.0D, 108, 27, 10, 80, 6_000.0D, false, 6),
    CITTA_III("Citta III", PrincipalStage.CITTA, SubTier.III, 90, 86_000.0D, 135, 39, 11, 100, 7_000.0D, false, 6),

    REGNO_I("Regno I", PrincipalStage.REGNO, SubTier.I, 100, 102_000.0D, 162, 50, 12, 130, 8_200.0D, true, 7),
    REGNO_II("Regno II", PrincipalStage.REGNO, SubTier.II, 125, 124_000.0D, 188, 78, 15, 165, 9_600.0D, false, 7),
    REGNO_III("Regno III", PrincipalStage.REGNO, SubTier.III, 150, 150_000.0D, 212, 107, 17, 200, 11_200.0D, true, 7);

    private final String displayName;
    private final PrincipalStage principalStage;
    private final SubTier subTier;
    private final int requiredLevel;
    private final double upgradeCost;
    private final int claimCap;
    private final int memberCap;
    private final int spawnerCap;
    private final int shopCap;
    private final double monthlyTax;
    private final boolean staffApprovalRequired;
    private final int huskTownLevel;

    TownStage(
            String displayName,
            PrincipalStage principalStage,
            SubTier subTier,
            int requiredLevel,
            double upgradeCost,
            int claimCap,
            int memberCap,
            int spawnerCap,
            int shopCap,
            double monthlyTax,
            boolean staffApprovalRequired,
            int huskTownLevel
    ) {
        this.displayName = displayName;
        this.principalStage = principalStage;
        this.subTier = subTier;
        this.requiredLevel = requiredLevel;
        this.upgradeCost = upgradeCost;
        this.claimCap = claimCap;
        this.memberCap = memberCap;
        this.spawnerCap = spawnerCap;
        this.shopCap = shopCap;
        this.monthlyTax = monthlyTax;
        this.staffApprovalRequired = staffApprovalRequired;
        this.huskTownLevel = huskTownLevel;
    }

    public String displayName() {
        return displayName;
    }

    public PrincipalStage principalStage() {
        return principalStage;
    }

    public SubTier subTier() {
        return subTier;
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

    public int shopCap() {
        return shopCap;
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

    public boolean isPrincipalComplete() {
        return subTier == SubTier.III;
    }

    public Optional<TownStage> nextSubTier() {
        TownStage[] values = values();
        int next = ordinal() + 1;
        if (next >= values.length) {
            return Optional.empty();
        }
        TownStage candidate = values[next];
        if (candidate.principalStage != principalStage) {
            return Optional.empty();
        }
        return Optional.of(candidate);
    }

    public Optional<TownStage> nextPrincipalStart() {
        TownStage[] values = values();
        for (int i = ordinal() + 1; i < values.length; i++) {
            TownStage stage = values[i];
            if (stage.principalStage != principalStage && stage.subTier == SubTier.I) {
                return Optional.of(stage);
            }
        }
        return Optional.empty();
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
            return AVAMPOSTO_I;
        }
        return Arrays.stream(values())
                .filter(stage -> stage.name().equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElse(AVAMPOSTO_I);
    }

    public static TownStage forLevel(int level) {
        TownStage current = AVAMPOSTO_I;
        for (TownStage stage : values()) {
            if (level >= stage.requiredLevel) {
                current = stage;
            }
        }
        return current;
    }
}
