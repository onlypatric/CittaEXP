package it.patric.cittaexp.custommobs;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ProjectileRuntimeService {

    public record ProjectileRuntimeSnapshot(
            UUID runtimeId,
            UUID ownerEntityId,
            String spawnSource,
            Integer ownerTownId,
            String sessionId,
            String encounterId,
            String waveId,
            Integer waveNumber,
            Instant startedAt
    ) {
    }

    public final class RuntimeHandle {
        private final UUID runtimeId;
        private final UUID ownerEntityId;
        private final String spawnSource;
        private final Integer ownerTownId;
        private final String sessionId;
        private final String encounterId;
        private final String waveId;
        private final Integer waveNumber;
        private final Instant startedAt;
        private volatile CustomMobRuntimeScheduler.TaskHandle taskHandle;

        private RuntimeHandle(UUID runtimeId, UUID ownerEntityId, CustomMobSpawnContext context) {
            this.runtimeId = runtimeId;
            this.ownerEntityId = ownerEntityId;
            this.spawnSource = context.spawnSource();
            this.ownerTownId = context.ownerTownId();
            this.sessionId = context.sessionId();
            this.encounterId = context.encounterId();
            this.waveId = context.waveId();
            this.waveNumber = context.waveNumber();
            this.startedAt = Instant.now();
        }

        public UUID runtimeId() {
            return runtimeId;
        }

        public void bind(CustomMobRuntimeScheduler.TaskHandle taskHandle) {
            this.taskHandle = taskHandle;
        }

        public void cancel() {
            ProjectileRuntimeService.this.cancel(runtimeId);
        }

        private ProjectileRuntimeSnapshot snapshot() {
            return new ProjectileRuntimeSnapshot(runtimeId, ownerEntityId, spawnSource, ownerTownId, sessionId, encounterId, waveId, waveNumber, startedAt);
        }
    }

    private final ConcurrentMap<UUID, RuntimeHandle> activeRuntimes = new ConcurrentHashMap<>();

    public RuntimeHandle create(UUID ownerEntityId, CustomMobSpawnContext context) {
        Objects.requireNonNull(ownerEntityId, "ownerEntityId");
        Objects.requireNonNull(context, "context");
        UUID runtimeId = UUID.randomUUID();
        RuntimeHandle handle = new RuntimeHandle(runtimeId, ownerEntityId, context);
        activeRuntimes.put(runtimeId, handle);
        return handle;
    }

    public void cancel(UUID runtimeId) {
        RuntimeHandle handle = activeRuntimes.remove(runtimeId);
        if (handle == null) {
            return;
        }
        CustomMobRuntimeScheduler.TaskHandle taskHandle = handle.taskHandle;
        if (taskHandle != null && !taskHandle.cancelled()) {
            taskHandle.cancel();
        }
    }

    public void cancelForOwner(UUID ownerEntityId) {
        for (RuntimeHandle handle : new ArrayList<>(activeRuntimes.values())) {
            if (handle.ownerEntityId.equals(ownerEntityId)) {
                cancel(handle.runtimeId);
            }
        }
    }

    public void cancelForSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        for (RuntimeHandle handle : new ArrayList<>(activeRuntimes.values())) {
            if (sessionId.equals(handle.sessionId)) {
                cancel(handle.runtimeId);
            }
        }
    }

    public void clear() {
        for (RuntimeHandle handle : new ArrayList<>(activeRuntimes.values())) {
            cancel(handle.runtimeId);
        }
        activeRuntimes.clear();
    }

    public int activeCount() {
        return activeRuntimes.size();
    }

    public List<ProjectileRuntimeSnapshot> snapshots() {
        List<ProjectileRuntimeSnapshot> snapshots = new ArrayList<>();
        for (RuntimeHandle handle : activeRuntimes.values()) {
            snapshots.add(handle.snapshot());
        }
        return List.copyOf(snapshots);
    }
}
