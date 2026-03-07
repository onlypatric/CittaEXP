package it.patric.cittaexp.core.service;

public interface CityLifecycleDiagnosticsService {

    Snapshot snapshot();

    record Snapshot(
            boolean cityCommandsEnabled,
            long activeFreezeCases,
            long pendingInvitations,
            long pendingJoinRequests
    ) {
    }
}
