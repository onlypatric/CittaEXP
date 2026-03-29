package it.patric.cittaexp.cityoverlaytest;

import java.util.List;

public record OverlayTestDataset(
        String cityName,
        String stageLabel,
        int cityLevel,
        long cityXp,
        double treasury,
        List<OverlayMissionCard> missionCards,
        List<String> memberNames,
        List<String> cityNames
) {
}
