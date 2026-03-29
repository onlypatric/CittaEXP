package it.patric.cittaexp.challenges;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class ChallengeTaxonomyService {

    private final Map<ChallengeObjectiveType, ChallengeObjectivePolicy> policies;

    public ChallengeTaxonomyService(CityChallengeSettings settings) {
        this.policies = buildPolicies(settings == null ? Map.of() : settings.taxonomyPolicies());
    }

    public ChallengeObjectivePolicy policyFor(ChallengeObjectiveType objectiveType) {
        if (objectiveType == null) {
            return defaultPolicy();
        }
        return policies.getOrDefault(objectiveType, defaultPolicy());
    }

    public boolean isAllowed(ChallengeDefinition definition, ChallengeMode mode) {
        if (definition == null || mode == null) {
            return false;
        }
        if (!definition.cyclesSupported().contains(mode)) {
            return false;
        }
        return isAllowed(definition.objectiveType(), mode);
    }

    public boolean isAllowed(ChallengeObjectiveType objectiveType, ChallengeMode mode) {
        return policyFor(objectiveType).allowsMode(mode);
    }

    public boolean cityXpEnabled(ChallengeObjectiveType objectiveType) {
        return policyFor(objectiveType).cityXpEnabled();
    }

    private static Map<ChallengeObjectiveType, ChallengeObjectivePolicy> buildPolicies(
            Map<ChallengeObjectiveType, CityChallengeSettings.TaxonomyPolicySettings> overrides
    ) {
        Map<ChallengeObjectiveType, ChallengeObjectivePolicy> result = new EnumMap<>(ChallengeObjectiveType.class);
        for (ChallengeObjectiveType type : ChallengeObjectiveType.values()) {
            result.put(type, defaultPolicy());
        }

        // M2 demote policy lock.
        result.put(
                ChallengeObjectiveType.PLAYTIME_MINUTES,
                new ChallengeObjectivePolicy(
                        ChallengeProgressionRole.SUPPORT,
                        EnumSet.of(ChallengeMode.DAILY_STANDARD),
                        false,
                        true
                )
        );
        result.put(
                ChallengeObjectiveType.XP_PICKUP,
                new ChallengeObjectivePolicy(
                        ChallengeProgressionRole.SUPPORT,
                        EnumSet.of(ChallengeMode.DAILY_STANDARD, ChallengeMode.WEEKLY_STANDARD),
                        false,
                        true
                )
        );
        result.put(
                ChallengeObjectiveType.ECONOMY_ADVANCED,
                new ChallengeObjectivePolicy(
                        ChallengeProgressionRole.SUPPORT,
                        EnumSet.of(
                                ChallengeMode.WEEKLY_STANDARD,
                                ChallengeMode.MONTHLY_STANDARD,
                                ChallengeMode.MONTHLY_LEDGER_CONTRACT,
                                ChallengeMode.MONTHLY_LEDGER_GRAND,
                                ChallengeMode.MONTHLY_LEDGER_MYSTERY
                        ),
                        false,
                        true
                )
        );
        result.put(
                ChallengeObjectiveType.RESOURCE_CONTRIBUTION,
                new ChallengeObjectivePolicy(
                        ChallengeProgressionRole.SUPPORT,
                        EnumSet.of(
                                ChallengeMode.WEEKLY_STANDARD,
                                ChallengeMode.MONTHLY_STANDARD,
                                ChallengeMode.MONTHLY_LEDGER_CONTRACT,
                                ChallengeMode.MONTHLY_LEDGER_GRAND,
                                ChallengeMode.MONTHLY_LEDGER_MYSTERY
                        ),
                        false,
                        true
                )
        );
        result.put(
                ChallengeObjectiveType.VAULT_DELIVERY,
                new ChallengeObjectivePolicy(
                        ChallengeProgressionRole.SUPPORT,
                        EnumSet.of(
                                ChallengeMode.WEEKLY_STANDARD,
                                ChallengeMode.MONTHLY_STANDARD,
                                ChallengeMode.MONTHLY_LEDGER_CONTRACT,
                                ChallengeMode.MONTHLY_LEDGER_GRAND,
                                ChallengeMode.MONTHLY_LEDGER_MYSTERY
                        ),
                        false,
                        true
                )
        );

        if (overrides != null) {
            for (Map.Entry<ChallengeObjectiveType, CityChallengeSettings.TaxonomyPolicySettings> entry : overrides.entrySet()) {
                ChallengeObjectiveType type = entry.getKey();
                CityChallengeSettings.TaxonomyPolicySettings override = entry.getValue();
                if (type == null || override == null) {
                    continue;
                }
                ChallengeObjectivePolicy base = result.getOrDefault(type, defaultPolicy());
                ChallengeProgressionRole role = override.progressionRole() == null
                        ? base.progressionRole()
                        : override.progressionRole();
                Set<ChallengeMode> allowedModes = override.allowedModes() == null || override.allowedModes().isEmpty()
                        ? base.allowedModes()
                        : override.allowedModes();
                boolean raceEligible = override.raceEligible() == null
                        ? base.raceEligible()
                        : override.raceEligible();
                boolean cityXpEnabled = override.cityXpEnabled() == null
                        ? base.cityXpEnabled()
                        : override.cityXpEnabled();
                result.put(type, new ChallengeObjectivePolicy(role, allowedModes, raceEligible, cityXpEnabled));
            }
        }

        return Map.copyOf(result);
    }

    private static ChallengeObjectivePolicy defaultPolicy() {
        return new ChallengeObjectivePolicy(
                ChallengeProgressionRole.CORE,
                EnumSet.allOf(ChallengeMode.class),
                true,
                true
        );
    }
}
