package it.patric.cittaexp.levels;

public final class CityLevelCurve {

    private CityLevelCurve() {
    }

    public static long requiredXpScaledForLevel(
            int level,
            int xpPerLevel,
            int xpScale,
            double exponent
    ) {
        int safeLevel = Math.max(1, level);
        if (safeLevel <= 1) {
            return 0L;
        }
        int levelOffset = safeLevel - 1;
        double safeExponent = sanitizeExponent(exponent);
        double baseXp = Math.max(1, xpPerLevel);
        double scaled = baseXp * Math.pow(levelOffset, safeExponent) * Math.max(1, xpScale);
        if (!Double.isFinite(scaled) || scaled <= 0.0D) {
            return 0L;
        }
        return Math.max(0L, Math.min(Long.MAX_VALUE, Math.round(scaled)));
    }

    public static int levelFromXpScaled(
            long xpScaled,
            int levelCap,
            int xpPerLevel,
            int xpScale,
            double exponent
    ) {
        long safeXp = Math.max(0L, xpScaled);
        int low = 1;
        int high = Math.max(1, levelCap);
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            long required = requiredXpScaledForLevel(mid, xpPerLevel, xpScale, exponent);
            if (safeXp >= required) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return low;
    }

    private static double sanitizeExponent(double exponent) {
        if (!Double.isFinite(exponent) || exponent <= 0.0D) {
            return 1.30D;
        }
        return exponent;
    }
}

