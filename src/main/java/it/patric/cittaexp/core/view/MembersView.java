package it.patric.cittaexp.core.view;

import java.util.List;
import java.util.UUID;

public record MembersView(UUID cityId, List<MemberRow> members) {

    public record MemberRow(UUID playerUuid, String playerName, String roleName, boolean online, boolean leader) {
    }
}
