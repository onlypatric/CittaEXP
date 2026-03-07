package it.patric.cittaexp.runtime.integration;

public record RankingScanStats(
        int scannedEntries,
        int validCityKeys,
        int invalidKeys,
        long lastScanEpochMilli
) {

    public static RankingScanStats empty() {
        return new RankingScanStats(0, 0, 0, 0L);
    }
}
