package it.patric.cittaexp.levelsgui;

import java.util.UUID;

public final class CityLevelGuiSession {

    private final UUID sessionId;
    private final UUID viewerId;
    private final int townId;
    private final String townName;
    private final boolean staffView;
    private int page;

    public CityLevelGuiSession(
            UUID sessionId,
            UUID viewerId,
            int townId,
            String townName,
            boolean staffView
    ) {
        this.sessionId = sessionId;
        this.viewerId = viewerId;
        this.townId = townId;
        this.townName = townName;
        this.staffView = staffView;
        this.page = 0;
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

    public boolean staffView() {
        return staffView;
    }

    public int page() {
        return page;
    }

    public void page(int page) {
        this.page = Math.max(0, page);
    }
}
