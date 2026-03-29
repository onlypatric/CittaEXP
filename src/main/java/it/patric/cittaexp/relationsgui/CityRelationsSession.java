package it.patric.cittaexp.relationsgui;

import java.util.UUID;

public final class CityRelationsSession {

    private final UUID sessionId;
    private final UUID viewerId;
    private final int townId;
    private final String townName;
    private final boolean staffView;
    private final boolean samplePreview;
    private int page;

    public CityRelationsSession(
            UUID sessionId,
            UUID viewerId,
            int townId,
            String townName,
            boolean staffView,
            boolean samplePreview
    ) {
        this.sessionId = sessionId;
        this.viewerId = viewerId;
        this.townId = townId;
        this.townName = townName;
        this.staffView = staffView;
        this.samplePreview = samplePreview;
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

    public boolean samplePreview() {
        return samplePreview;
    }

    public int page() {
        return page;
    }

    public void page(int page) {
        this.page = Math.max(0, page);
    }
}
