package it.patric.cittaexp.levels;

public record CityScanResult(
        int townId,
        long awardedXpScaled,
        int previousLevel,
        int currentLevel,
        long currentXpScaled,
        String error
) {

    public static CityScanResult noChange(int townId, int level, long xpScaled) {
        return new CityScanResult(townId, 0L, level, level, xpScaled, null);
    }

    public static CityScanResult error(int townId, String error) {
        return new CityScanResult(townId, 0L, 0, 0, 0L, error);
    }
}
