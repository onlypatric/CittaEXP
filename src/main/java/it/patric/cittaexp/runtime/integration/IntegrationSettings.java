package it.patric.cittaexp.runtime.integration;

import java.util.Objects;

public record IntegrationSettings(
        boolean failFast,
        RankingSettings ranking,
        ClaimSettings claim
) {

    public IntegrationSettings {
        ranking = Objects.requireNonNull(ranking, "ranking");
        claim = Objects.requireNonNull(claim, "claim");
    }

    public record RankingSettings(int scanLimit) {

        public RankingSettings {
            if (scanLimit < 1) {
                throw new IllegalArgumentException("scanLimit must be >= 1");
            }
        }
    }

    public record ClaimSettings(
            int autoWidth,
            int autoHeight,
            String createCommandTemplate,
            String expandCommandTemplate
    ) {

        public ClaimSettings {
            if (autoWidth < 1 || autoHeight < 1) {
                throw new IllegalArgumentException("auto claim size must be >= 1");
            }
            createCommandTemplate = createCommandTemplate == null ? "" : createCommandTemplate.trim();
            expandCommandTemplate = expandCommandTemplate == null ? "" : expandCommandTemplate.trim();
        }
    }
}
