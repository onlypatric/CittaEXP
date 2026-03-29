package it.patric.cittaexp.playersgui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CityPlayersSession {

    private final UUID sessionId;
    private final UUID viewerId;
    private int townId;
    private String townName;
    private UUID mayorId;
    private boolean staffView;
    private CityPlayersBackTarget backTarget;
    private int page;
    private List<CityPlayersMemberView> members;

    public CityPlayersSession(
            UUID sessionId,
            UUID viewerId,
            int townId,
            String townName,
            UUID mayorId,
            boolean staffView,
            CityPlayersBackTarget backTarget,
            List<CityPlayersMemberView> members
    ) {
        this.sessionId = sessionId;
        this.viewerId = viewerId;
        this.townId = townId;
        this.townName = townName;
        this.mayorId = mayorId;
        this.staffView = staffView;
        this.backTarget = backTarget;
        this.page = 0;
        this.members = new ArrayList<>(members);
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

    public UUID mayorId() {
        return mayorId;
    }

    public boolean staffView() {
        return staffView;
    }

    public CityPlayersBackTarget backTarget() {
        return backTarget;
    }

    public void backTarget(CityPlayersBackTarget backTarget) {
        this.backTarget = backTarget == null ? CityPlayersBackTarget.LIST : backTarget;
    }

    public int page() {
        return page;
    }

    public void page(int page) {
        this.page = Math.max(0, page);
    }

    public List<CityPlayersMemberView> members() {
        return members;
    }

    public void updateTown(int townId, String townName, UUID mayorId, List<CityPlayersMemberView> members) {
        this.townId = townId;
        this.townName = townName;
        this.mayorId = mayorId;
        this.members = new ArrayList<>(members);
        int maxPage = Math.max(0, (members.size() - 1) / 45);
        this.page = Math.min(this.page, maxPage);
    }
}
