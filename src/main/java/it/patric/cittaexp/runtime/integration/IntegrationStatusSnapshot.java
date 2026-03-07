package it.patric.cittaexp.runtime.integration;

import java.util.List;

public record IntegrationStatusSnapshot(
        List<AdapterStatus> adapters,
        RankingScanStats rankingScan
) {

    public IntegrationStatusSnapshot {
        adapters = List.copyOf(adapters);
    }

    public boolean allAvailable() {
        return adapters.stream().allMatch(value -> value.state() == AdapterState.AVAILABLE);
    }
}
