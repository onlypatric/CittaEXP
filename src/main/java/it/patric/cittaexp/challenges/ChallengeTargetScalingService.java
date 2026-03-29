package it.patric.cittaexp.challenges;

import java.util.List;

public final class ChallengeTargetScalingService {

    public record CityScaling(
            int townMembersTotal,
            double multiplier,
            boolean fallbackApplied
    ) {
    }

    private final CityChallengeSettings.TargetScalingSettings scalingSettings;
    private final CityChallengeSettings.DualThresholdSettings dualThresholdSettings;

    public ChallengeTargetScalingService(CityChallengeSettings settings) {
        this.scalingSettings = settings.targetScaling();
        this.dualThresholdSettings = settings.dualThreshold();
    }

    public CityScaling resolveForTownMembers(int townMembersTotal, boolean fallbackApplied) {
        int safeMembers = Math.max(1, townMembersTotal);
        return new CityScaling(safeMembers, multiplierForTownMembers(safeMembers), fallbackApplied);
    }

    public double multiplierForTownMembers(int townMembersTotal) {
        int safeMembers = Math.max(1, townMembersTotal);
        List<CityChallengeSettings.CitySizeBracket> brackets = scalingSettings.cityMembersBrackets();
        if (brackets == null || brackets.isEmpty()) {
            return 1.0D;
        }
        for (CityChallengeSettings.CitySizeBracket bracket : brackets) {
            if (bracket == null) {
                continue;
            }
            if (safeMembers >= bracket.minMembers() && safeMembers <= bracket.maxMembers()) {
                return Math.max(0.01D, bracket.multiplier());
            }
        }
        return 1.0D;
    }

    public int scaleTarget(int baseTarget, int step, double multiplier) {
        int safeBase = Math.max(1, baseTarget);
        int safeStep = Math.max(1, step);
        double scaledRaw = safeBase * Math.max(0.01D, multiplier);
        long rounded = Math.max(1L, Math.round(scaledRaw));
        long snapped = ((rounded + (safeStep / 2L)) / safeStep) * safeStep;
        return (int) Math.max(1L, snapped);
    }

    public int scaleTargetByOnline(int baseTarget, int step, int onlinePlayers) {
        int safeBase = Math.max(1, baseTarget);
        CityChallengeSettings.ServerWideOnlineScaling online = scalingSettings.serverWideOnline();
        if (online == null || !online.enabled()) {
            return safeBase;
        }
        int safeOnline = Math.max(0, onlinePlayers);
        double divisor = online.playersDivisor() <= 0.0D ? 30.0D : online.playersDivisor();
        double raw = online.baseFactor() + (safeOnline / divisor);
        double factor = clamp(raw, online.minFactor(), online.maxFactor());
        return scaleTarget(safeBase, step, factor);
    }

    public int excellenceTarget(int scaledTarget) {
        int safeScaled = Math.max(1, scaledTarget);
        double multiplier = Math.max(1.0D, dualThresholdSettings.excellenceMultiplier());
        return Math.max(safeScaled, (int) Math.round(safeScaled * multiplier));
    }

    private static double clamp(double value, double min, double max) {
        double safeMin = Math.min(min, max);
        double safeMax = Math.max(min, max);
        if (value < safeMin) {
            return safeMin;
        }
        if (value > safeMax) {
            return safeMax;
        }
        return value;
    }
}

