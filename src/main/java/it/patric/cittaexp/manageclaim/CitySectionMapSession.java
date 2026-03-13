package it.patric.cittaexp.manageclaim;

import java.util.UUID;

public final class CitySectionMapSession {

    private final UUID sessionId;
    private final UUID viewerId;
    private final int townId;
    private final String townName;
    private final String worldName;
    private int centerChunkX;
    private int centerChunkZ;

    public CitySectionMapSession(
            UUID sessionId,
            UUID viewerId,
            int townId,
            String townName,
            String worldName,
            int centerChunkX,
            int centerChunkZ
    ) {
        this.sessionId = sessionId;
        this.viewerId = viewerId;
        this.townId = townId;
        this.townName = townName;
        this.worldName = worldName;
        this.centerChunkX = centerChunkX;
        this.centerChunkZ = centerChunkZ;
    }

    public UUID sessionId() {
        return sessionId;
    }

    public UUID viewerId() {
        return viewerId;
    }

    public int townId() {
        return townId;
    }

    public String townName() {
        return townName;
    }

    public String worldName() {
        return worldName;
    }

    public int centerChunkX() {
        return centerChunkX;
    }

    public int centerChunkZ() {
        return centerChunkZ;
    }

    public void move(int deltaX, int deltaZ) {
        this.centerChunkX += deltaX;
        this.centerChunkZ += deltaZ;
    }

    public void centerTo(int chunkX, int chunkZ) {
        this.centerChunkX = chunkX;
        this.centerChunkZ = chunkZ;
    }
}
