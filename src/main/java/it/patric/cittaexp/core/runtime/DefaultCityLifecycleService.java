package it.patric.cittaexp.core.runtime;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import it.patric.cittaexp.core.model.AuditEvent;
import it.patric.cittaexp.core.model.City;
import it.patric.cittaexp.core.model.CityInvitation;
import it.patric.cittaexp.core.model.CityMember;
import it.patric.cittaexp.core.model.CityRole;
import it.patric.cittaexp.core.model.CityStatus;
import it.patric.cittaexp.core.model.CityTier;
import it.patric.cittaexp.core.model.ClaimBinding;
import it.patric.cittaexp.core.model.FreezeReason;
import it.patric.cittaexp.core.model.InvitationStatus;
import it.patric.cittaexp.core.model.JoinRequest;
import it.patric.cittaexp.core.model.JoinRequestStatus;
import it.patric.cittaexp.core.model.MemberStatus;
import it.patric.cittaexp.core.model.RolePermissionSet;
import it.patric.cittaexp.core.port.HuskClaimsPort;
import it.patric.cittaexp.core.service.CityLifecycleDiagnosticsService;
import it.patric.cittaexp.core.service.CityLifecycleService;
import it.patric.cittaexp.core.service.CityModerationService;
import it.patric.cittaexp.core.service.ClaimService;
import it.patric.cittaexp.core.service.MembershipService;
import it.patric.cittaexp.core.service.RoleService;
import it.patric.cittaexp.persistence.domain.AuditEventRecord;
import it.patric.cittaexp.persistence.domain.CityInvitationRecord;
import it.patric.cittaexp.persistence.domain.CityMemberRecord;
import it.patric.cittaexp.persistence.domain.CityRecord;
import it.patric.cittaexp.persistence.domain.CityRoleRecord;
import it.patric.cittaexp.persistence.domain.ClaimBindingRecord;
import it.patric.cittaexp.persistence.domain.FreezeCaseRecord;
import it.patric.cittaexp.persistence.domain.JoinRequestRecord;
import it.patric.cittaexp.persistence.port.CityReadPort;
import it.patric.cittaexp.persistence.port.CityTxPort;
import it.patric.cittaexp.persistence.port.CityWritePort;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class DefaultCityLifecycleService implements
        CityLifecycleService,
        MembershipService,
        RoleService,
        ClaimService,
        CityModerationService,
        CityLifecycleDiagnosticsService {

    private static final Gson GSON = new Gson();
    private static final long INVITE_EXPIRY_MILLIS = 72L * 60L * 60L * 1000L;

    private static final String ROLE_LEADER = "leader";
    private static final String ROLE_UFFICIALE = "ufficiale";
    private static final String ROLE_RECRUITER = "recruiter";
    private static final String ROLE_MEMBER = "membro";

    private final CityReadPort readPort;
    private final CityWritePort writePort;
    private final CityTxPort txPort;
    private final HuskClaimsPort huskClaimsPort;
    private final Logger logger;

    public DefaultCityLifecycleService(
            CityReadPort readPort,
            CityWritePort writePort,
            CityTxPort txPort,
            HuskClaimsPort huskClaimsPort,
            Logger logger
    ) {
        this.readPort = Objects.requireNonNull(readPort, "readPort");
        this.writePort = Objects.requireNonNull(writePort, "writePort");
        this.txPort = Objects.requireNonNull(txPort, "txPort");
        this.huskClaimsPort = Objects.requireNonNull(huskClaimsPort, "huskClaimsPort");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public City createCity(CreateCityCommand command) {
        try {
            return createCityAsync(command).join();
        } catch (CompletionException ex) {
            Throwable cause = unwrapCompletion(ex);
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("city-create-failed", cause);
        }
    }

    public CompletableFuture<City> createCityAsync(CreateCityCommand command) {
        Objects.requireNonNull(command, "command");
        UUID creator = Objects.requireNonNull(command.creatorUuid(), "creatorUuid");
        String name = normalizeCityName(command.cityName());
        String tag = normalizeCityTag(command.cityTag());
        logger.info(
                "[CittaEXP][city-create] request received"
                        + " player=" + creator
                        + " cityName=" + name
                        + " tag=" + tag
                        + " world=" + command.world()
                        + " x=" + command.centerX()
                        + " z=" + command.centerZ()
        );

        if (readPort.findActiveMember(creator).isPresent()) {
            logger.warning("[CittaEXP][city-create] rejected: player already in city player=" + creator);
            throw new IllegalStateException("player-already-in-city");
        }
        if (readPort.findCityByName(name).isPresent() || readPort.findCityByTag(tag).isPresent()) {
            logger.warning(
                    "[CittaEXP][city-create] rejected: city name/tag conflict name="
                            + name + " tag=" + tag
            );
            throw new IllegalStateException("city-name-tag-conflict");
        }
        if (!huskClaimsPort.available()) {
            logger.warning("[CittaEXP][city-create] rejected: huskclaims unavailable");
            throw new IllegalStateException("huskclaims-unavailable");
        }

        UUID cityId = UUID.randomUUID();
        logger.info(
                "[CittaEXP][city-create] creating claim cityId="
                        + cityId + " world=" + command.world()
                        + " center=" + command.centerX() + "," + command.centerZ()
        );
        return huskClaimsPort.createAutoClaim100x100Async(
                creator,
                command.world(),
                command.centerX(),
                command.centerZ()
        ).thenCompose(result -> {
            logger.info(
                    "[CittaEXP][city-create] claim result cityId="
                            + cityId
                            + " success=" + result.success()
                            + " reason=" + safe(result.reason())
                            + " min=" + result.minX() + "," + result.minZ()
                            + " max=" + result.maxX() + "," + result.maxZ()
            );
            if (!result.success()) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("claim-create-failed:" + safe(result.reason()))
                );
            }
            long now = System.currentTimeMillis();
            ClaimBinding claim = new ClaimBinding(
                    cityId,
                    command.world(),
                    result.minX(),
                    result.minZ(),
                    result.maxX(),
                    result.maxZ(),
                    (result.maxX() - result.minX() + 1) * (result.maxZ() - result.minZ() + 1),
                    now,
                    now
            );
            CityRecord city = new CityRecord(
                    cityId,
                    name,
                    tag,
                    creator,
                    CityTier.BORGO,
                    CityStatus.ACTIVE,
                    false,
                    false,
                    0L,
                    1,
                    maxMembersForTier(CityTier.BORGO),
                    now,
                    now,
                    0
            );
            try {
                logger.info("[CittaEXP][city-create] persisting city aggregate cityId=" + cityId);
                txPort.withTransaction(connection -> {
                    expect(writePort.createCity(city), "create-city");
                    bootstrapDefaultRoles(cityId, now);
                    expect(writePort.upsertMember(new CityMemberRecord(cityId, creator, ROLE_LEADER, now, true)), "create-leader-member");
                    expect(writePort.upsertClaimBinding(toClaimBindingRecord(claim)), "create-claim-binding");

                    appendAudit(audit(cityId, "CITY", "city_created", creator, jsonPayload("name", name, "tag", tag), now));
                    appendAudit(audit(cityId, "CITY", "claim_created", creator, jsonPayload("world", command.world()), now));
                    return null;
                });
                logger.info(
                        "[CittaEXP][city-create] completed cityId="
                                + cityId + " name=" + name + " tag=" + tag
                );
                return CompletableFuture.completedFuture(toCity(city));
            } catch (RuntimeException writeFailure) {
                logger.log(
                        Level.SEVERE,
                        "[CittaEXP][city-create] persistence failed cityId="
                                + cityId
                                + " name=" + name
                                + " tag=" + tag
                                + " error=" + safe(writeFailure.getMessage()),
                        writeFailure
                );
                return huskClaimsPort.deleteClaimAtAsync(command.world(), command.centerX(), command.centerZ())
                        .handle((rollbackOk, rollbackError) -> {
                            boolean rollbackSuccess = rollbackError == null && Boolean.TRUE.equals(rollbackOk);
                            if (rollbackSuccess) {
                                logger.info("[CittaEXP][city-create] claim rollback OK cityId=" + cityId);
                            } else {
                                logger.log(
                                        Level.SEVERE,
                                        "[CittaEXP][city-create] claim rollback FAILED cityId="
                                                + cityId
                                                + " error="
                                                + (rollbackError == null ? "unknown" : rollbackError.getClass().getSimpleName()),
                                        rollbackError
                                );
                            }
                            String reason = rollbackSuccess
                                    ? "city-create-db-failed-rollback-ok"
                                    : "city-create-db-failed-rollback-failed";
                            return new IllegalStateException(reason, writeFailure);
                        })
                        .thenCompose(error -> CompletableFuture.failedFuture(error));
            }
        });
    }

    @Override
    public City transferLeadership(UUID cityId, UUID currentLeader, UUID nextLeader, String reason) {
        CityRecord city = requireCity(cityId);
        if (!city.leaderUuid().equals(currentLeader)) {
            throw new IllegalStateException("current-leader-mismatch");
        }
        CityMemberRecord next = requireActiveMember(cityId, nextLeader);
        long now = System.currentTimeMillis();

        CityRecord updated = updateCityRecord(city, city.name(), city.tag(), next.playerUuid(), city.status(), city.frozen(), city.memberCount(), now);
        txPort.withTransaction(connection -> {
            expect(writePort.updateCity(updated, city.revision()), "transfer-leadership");
            expect(writePort.upsertMember(new CityMemberRecord(cityId, currentLeader, ROLE_UFFICIALE, now, true)), "demote-old-leader");
            expect(writePort.upsertMember(new CityMemberRecord(cityId, next.playerUuid(), ROLE_LEADER, next.joinedAtEpochMilli(), true)), "promote-new-leader");
            appendAudit(audit(cityId, "CITY", "leader_transferred", currentLeader, jsonPayload("nextLeader", nextLeader.toString(), "reason", safe(reason)), now));
            return null;
        });
        return toCity(updated);
    }

    @Override
    public City requestTierUpgrade(UUID cityId, UUID requester, CityTier targetTier, String reason) {
        CityRecord city = requireCity(cityId);
        ensureCityNotFrozen(city);
        ensureCanRequestUpgrade(city, requester);

        if (targetTier.ordinal() != city.tier().ordinal() + 1) {
            throw new IllegalStateException("invalid-tier-upgrade-path");
        }

        long now = System.currentTimeMillis();
        appendAudit(audit(city.cityId(), "CITY", "tier_upgrade_requested", requester,
                jsonPayload("from", city.tier().name(), "to", targetTier.name(), "reason", safe(reason)), now));
        return toCity(city);
    }

    @Override
    public City requestDeletion(UUID cityId, UUID requester, String reason) {
        CityRecord city = requireCity(cityId);
        ensureLeader(city, requester);
        long now = System.currentTimeMillis();
        CityRecord updated = updateCityRecord(city, city.name(), city.tag(), city.leaderUuid(), CityStatus.PENDING_DELETE, city.frozen(), city.memberCount(), now);
        expect(writePort.updateCity(updated, city.revision()), "request-deletion");
        appendAudit(audit(city.cityId(), "CITY", "city_deletion_requested", requester, jsonPayload("reason", safe(reason)), now));
        return toCity(updated);
    }

    @Override
    public CityInvitation invite(UUID cityId, UUID invitedBy, UUID invitedPlayer) {
        CityRecord city = requireCity(cityId);
        ensureCityNotFrozen(city);
        ensureCanInvite(city, invitedBy);
        if (readPort.findActiveMember(invitedPlayer).isPresent()) {
            throw new IllegalStateException("invited-player-already-in-city");
        }

        long now = System.currentTimeMillis();
        CityInvitationRecord invitation = new CityInvitationRecord(
                UUID.randomUUID(),
                cityId,
                invitedPlayer,
                invitedBy,
                InvitationStatus.PENDING,
                now,
                now + INVITE_EXPIRY_MILLIS,
                now
        );
        expect(writePort.upsertInvitation(invitation), "invite-player");
        appendAudit(audit(cityId, "CITY", "member_invited", invitedBy,
                jsonPayload("player", invitedPlayer.toString()), now));
        return toInvitation(invitation);
    }

    @Override
    public CityMember acceptInvite(UUID invitationId, UUID playerUuid) {
        CityInvitationRecord invitation = requireInvitation(invitationId);
        if (!invitation.invitedPlayerUuid().equals(playerUuid)) {
            throw new IllegalStateException("invitation-target-mismatch");
        }
        if (invitation.status() != InvitationStatus.PENDING) {
            throw new IllegalStateException("invitation-not-pending");
        }
        if (invitation.expiresAtEpochMilli() < System.currentTimeMillis()) {
            expect(writePort.updateInvitationStatus(invitationId, InvitationStatus.EXPIRED, System.currentTimeMillis()), "expire-invitation");
            throw new IllegalStateException("invitation-expired");
        }
        if (readPort.findActiveMember(playerUuid).isPresent()) {
            throw new IllegalStateException("player-already-in-city");
        }

        CityRecord city = requireCity(invitation.cityId());
        ensureCityNotFrozen(city);
        if (city.memberCount() >= city.maxMembers()) {
            throw new IllegalStateException("city-member-limit-reached");
        }

        long now = System.currentTimeMillis();
        CityRecord updatedCity = updateCityRecord(city, city.name(), city.tag(), city.leaderUuid(), city.status(), city.frozen(), city.memberCount() + 1, now);
        CityMemberRecord member = new CityMemberRecord(city.cityId(), playerUuid, ROLE_MEMBER, now, true);

        txPort.withTransaction(connection -> {
            expect(writePort.updateInvitationStatus(invitationId, InvitationStatus.ACCEPTED, now), "accept-invitation");
            expect(writePort.upsertMember(member), "add-member-from-invitation");
            expect(writePort.updateCity(updatedCity, city.revision()), "bump-member-count");
            appendAudit(audit(city.cityId(), "CITY", "member_joined", playerUuid,
                    jsonPayload("source", "invite", "invitationId", invitationId.toString()), now));
            return null;
        });

        return toCityMember(member);
    }

    @Override
    public void declineInvite(UUID invitationId, UUID playerUuid, String note) {
        CityInvitationRecord invitation = requireInvitation(invitationId);
        if (!invitation.invitedPlayerUuid().equals(playerUuid)) {
            throw new IllegalStateException("invitation-target-mismatch");
        }
        if (invitation.status() != InvitationStatus.PENDING) {
            throw new IllegalStateException("invitation-not-pending");
        }
        long now = System.currentTimeMillis();
        expect(writePort.updateInvitationStatus(invitationId, InvitationStatus.DECLINED, now), "decline-invitation");
        appendAudit(audit(invitation.cityId(), "CITY", "member_invite_declined", playerUuid,
                jsonPayload("invitationId", invitationId.toString(), "note", safe(note)), now));
    }

    @Override
    public JoinRequest requestJoin(UUID cityId, UUID playerUuid, String message) {
        if (readPort.findActiveMember(playerUuid).isPresent()) {
            throw new IllegalStateException("player-already-in-city");
        }
        CityRecord city = requireCity(cityId);
        if (readPort.findPendingJoinRequest(cityId, playerUuid).isPresent()) {
            throw new IllegalStateException("pending-join-request-exists");
        }

        long now = System.currentTimeMillis();
        JoinRequestRecord request = new JoinRequestRecord(
                UUID.randomUUID(),
                cityId,
                playerUuid,
                JoinRequestStatus.PENDING,
                safe(message),
                null,
                now,
                0L
        );
        expect(writePort.upsertJoinRequest(request), "join-request-create");
        appendAudit(audit(cityId, "CITY", "join_request_created", playerUuid, jsonPayload("message", safe(message)), now));
        return toJoinRequest(request);
    }

    @Override
    public CityMember approveJoinRequest(UUID requestId, UUID reviewerUuid, String note) {
        JoinRequestRecord request = requireJoinRequest(requestId);
        if (request.status() != JoinRequestStatus.PENDING) {
            throw new IllegalStateException("join-request-not-pending");
        }
        if (isExpired(request.requestedAtEpochMilli())) {
            expect(writePort.updateJoinRequestStatus(requestId, JoinRequestStatus.CANCELLED, reviewerUuid, System.currentTimeMillis()), "cancel-expired-request");
            throw new IllegalStateException("join-request-expired");
        }

        CityRecord city = requireCity(request.cityId());
        ensureCityNotFrozen(city);
        ensureCanManageMembers(city, reviewerUuid);
        if (readPort.findActiveMember(request.playerUuid()).isPresent()) {
            throw new IllegalStateException("player-already-in-city");
        }
        if (city.memberCount() >= city.maxMembers()) {
            throw new IllegalStateException("city-member-limit-reached");
        }

        long now = System.currentTimeMillis();
        CityRecord updatedCity = updateCityRecord(city, city.name(), city.tag(), city.leaderUuid(), city.status(), city.frozen(), city.memberCount() + 1, now);
        CityMemberRecord member = new CityMemberRecord(city.cityId(), request.playerUuid(), ROLE_MEMBER, now, true);

        txPort.withTransaction(connection -> {
            expect(writePort.updateJoinRequestStatus(request.requestId(), JoinRequestStatus.APPROVED, reviewerUuid, now), "join-request-approve");
            expect(writePort.upsertMember(member), "join-request-add-member");
            expect(writePort.updateCity(updatedCity, city.revision()), "join-request-bump-city");
            appendAudit(audit(city.cityId(), "CITY", "member_joined", request.playerUuid(),
                    jsonPayload("source", "join_request", "requestId", requestId.toString(), "note", safe(note)), now));
            return null;
        });

        return toCityMember(member);
    }

    @Override
    public void rejectJoinRequest(UUID requestId, UUID reviewerUuid, String note) {
        JoinRequestRecord request = requireJoinRequest(requestId);
        if (request.status() != JoinRequestStatus.PENDING) {
            throw new IllegalStateException("join-request-not-pending");
        }
        CityRecord city = requireCity(request.cityId());
        ensureCityNotFrozen(city);
        ensureCanManageMembers(city, reviewerUuid);

        long now = System.currentTimeMillis();
        expect(writePort.updateJoinRequestStatus(requestId, JoinRequestStatus.REJECTED, reviewerUuid, now), "join-request-reject");
        appendAudit(audit(city.cityId(), "CITY", "join_request_rejected", reviewerUuid,
                jsonPayload("requestId", requestId.toString(), "note", safe(note)), now));
    }

    @Override
    public void kickMember(UUID cityId, UUID actorUuid, UUID targetUuid, String reason) {
        CityRecord city = requireCity(cityId);
        ensureCityNotFrozen(city);
        ensureCanKick(city, actorUuid);
        if (city.leaderUuid().equals(targetUuid)) {
            throw new IllegalStateException("cannot-kick-leader");
        }
        CityMemberRecord target = requireActiveMember(cityId, targetUuid);
        long now = System.currentTimeMillis();
        CityRecord updatedCity = updateCityRecord(city, city.name(), city.tag(), city.leaderUuid(), city.status(), city.frozen(), Math.max(0, city.memberCount() - 1), now);

        txPort.withTransaction(connection -> {
            expect(writePort.deleteMember(cityId, target.playerUuid()), "kick-delete-member");
            expect(writePort.updateCity(updatedCity, city.revision()), "kick-update-city");
            appendAudit(audit(cityId, "CITY", "member_kicked", actorUuid,
                    jsonPayload("target", targetUuid.toString(), "reason", safe(reason)), now));
            return null;
        });
    }

    @Override
    public void leaveCity(UUID cityId, UUID playerUuid, String reason) {
        CityRecord city = requireCity(cityId);
        if (city.leaderUuid().equals(playerUuid)) {
            throw new IllegalStateException("leader-must-transfer-before-leave");
        }
        CityMemberRecord member = requireActiveMember(cityId, playerUuid);

        long now = System.currentTimeMillis();
        CityRecord updatedCity = updateCityRecord(city, city.name(), city.tag(), city.leaderUuid(), city.status(), city.frozen(), Math.max(0, city.memberCount() - 1), now);
        txPort.withTransaction(connection -> {
            expect(writePort.deleteMember(cityId, member.playerUuid()), "leave-delete-member");
            expect(writePort.updateCity(updatedCity, city.revision()), "leave-update-city");
            appendAudit(audit(cityId, "CITY", "member_left", playerUuid, jsonPayload("reason", safe(reason)), now));
            return null;
        });
    }

    @Override
    public List<CityMember> listActiveMembers(UUID cityId) {
        return readPort.listMembers(cityId).stream()
                .filter(CityMemberRecord::active)
                .map(this::toCityMember)
                .toList();
    }

    @Override
    public CityRole createRole(UUID cityId, UUID actorUuid, String roleKey, String displayName, int priority, RolePermissionSet permissions) {
        CityRecord city = requireCity(cityId);
        ensureCityNotFrozen(city);
        ensureCanManageRoles(city, actorUuid);
        String normalizedRoleKey = normalizeRoleKey(roleKey);
        if (ROLE_LEADER.equals(normalizedRoleKey)) {
            throw new IllegalStateException("leader-role-is-immutable");
        }

        long now = System.currentTimeMillis();
        CityRoleRecord role = new CityRoleRecord(cityId, normalizedRoleKey, requireText(displayName, "displayName"), priority, toPermissionsJson(permissions));
        expect(writePort.upsertRole(role), "create-role");
        appendAudit(audit(cityId, "CITY", "role_updated", actorUuid, jsonPayload("roleKey", normalizedRoleKey, "action", "create"), now));
        return toCityRole(role, now);
    }

    @Override
    public CityRole updateRole(UUID cityId, UUID actorUuid, String roleKey, String displayName, int priority, RolePermissionSet permissions) {
        CityRecord city = requireCity(cityId);
        ensureCityNotFrozen(city);
        ensureCanManageRoles(city, actorUuid);
        String normalizedRoleKey = normalizeRoleKey(roleKey);
        if (ROLE_LEADER.equals(normalizedRoleKey)) {
            throw new IllegalStateException("leader-role-is-immutable");
        }

        long now = System.currentTimeMillis();
        CityRoleRecord role = new CityRoleRecord(cityId, normalizedRoleKey, requireText(displayName, "displayName"), priority, toPermissionsJson(permissions));
        expect(writePort.upsertRole(role), "update-role");
        appendAudit(audit(cityId, "CITY", "role_updated", actorUuid, jsonPayload("roleKey", normalizedRoleKey, "action", "update"), now));
        return toCityRole(role, now);
    }

    @Override
    public void deleteRole(UUID cityId, UUID actorUuid, String roleKey) {
        CityRecord city = requireCity(cityId);
        ensureCityNotFrozen(city);
        ensureCanManageRoles(city, actorUuid);
        String normalizedRoleKey = normalizeRoleKey(roleKey);
        if (ROLE_LEADER.equals(normalizedRoleKey)) {
            throw new IllegalStateException("leader-role-is-immutable");
        }
        boolean roleInUse = readPort.listMembers(cityId).stream()
                .anyMatch(member -> member.active() && normalizeRoleKey(member.roleKey()).equals(normalizedRoleKey));
        if (roleInUse) {
            throw new IllegalStateException("role-in-use");
        }

        expect(writePort.deleteRole(cityId, normalizedRoleKey), "delete-role");
        appendAudit(audit(cityId, "CITY", "role_updated", actorUuid, jsonPayload("roleKey", normalizedRoleKey, "action", "delete"), System.currentTimeMillis()));
    }

    @Override
    public List<CityRole> listRoles(UUID cityId) {
        long now = System.currentTimeMillis();
        return readPort.listRoles(cityId).stream().map(role -> toCityRole(role, now)).toList();
    }

    @Override
    public ClaimBinding ensureInitialClaim100x100(UUID cityId, UUID leaderUuid, String world, int centerX, int centerZ) {
        if (!huskClaimsPort.available()) {
            throw new IllegalStateException("huskclaims-unavailable");
        }
        HuskClaimsPort.ClaimCreationResult result;
        try {
            result = huskClaimsPort.createAutoClaim100x100Async(leaderUuid, world, centerX, centerZ).join();
        } catch (CompletionException ex) {
            Throwable cause = unwrapCompletion(ex);
            throw new IllegalStateException("claim-create-failed:" + cause.getClass().getSimpleName(), cause);
        }
        if (!result.success()) {
            throw new IllegalStateException("claim-create-failed:" + safe(result.reason()));
        }
        int width = (result.maxX() - result.minX() + 1);
        int height = (result.maxZ() - result.minZ() + 1);
        long now = System.currentTimeMillis();
        return new ClaimBinding(
                cityId,
                world,
                result.minX(),
                result.minZ(),
                result.maxX(),
                result.maxZ(),
                width * height,
                now,
                now
        );
    }

    @Override
    public ClaimBinding expand(UUID cityId, UUID actorUuid, int chunks, String reason) {
        CityRecord city = requireCity(cityId);
        ensureCityNotFrozen(city);
        ensureCanExpandClaims(city, actorUuid);
        ClaimBindingRecord current = readPort.findClaimBinding(cityId)
                .orElseThrow(() -> new IllegalStateException("claim-binding-missing"));
        int centerX = (current.minX() + current.maxX()) / 2;
        int centerZ = (current.minZ() + current.maxZ()) / 2;
        boolean expanded;
        try {
            expanded = huskClaimsPort.expandClaimAsync(current.worldName(), centerX, centerZ, chunks).join();
        } catch (CompletionException ex) {
            Throwable cause = unwrapCompletion(ex);
            throw new IllegalStateException("claim-expand-failed:" + cause.getClass().getSimpleName(), cause);
        }
        if (!expanded) {
            throw new IllegalStateException("claim-expand-failed");
        }
        int expandedArea = current.area() + (Math.max(1, chunks) * 256);
        long now = System.currentTimeMillis();
        ClaimBindingRecord updated = new ClaimBindingRecord(
                current.cityId(),
                current.worldName(),
                current.minX(),
                current.minZ(),
                current.maxX(),
                current.maxZ(),
                expandedArea,
                current.createdAtEpochMilli(),
                now
        );
        expect(writePort.upsertClaimBinding(updated), "claim-expand-upsert");
        appendAudit(audit(cityId, "CITY", "claim_expanded", actorUuid, jsonPayload("chunks", String.valueOf(chunks), "reason", safe(reason)), now));
        return toClaimBinding(updated);
    }

    @Override
    public City freezeCity(String cityReference, UUID actorUuid, String reason) {
        CityRecord city = resolveCity(cityReference);
        if (city.status() == CityStatus.FROZEN || city.frozen()) {
            return toCity(city);
        }
        long now = System.currentTimeMillis();
        FreezeCaseRecord freezeCase = new FreezeCaseRecord(
                UUID.randomUUID(),
                city.cityId(),
                FreezeReason.STAFF_MANUAL,
                safe(reason),
                true,
                actorUuid,
                null,
                now,
                0L
        );
        CityRecord updated = updateCityRecord(city, city.name(), city.tag(), city.leaderUuid(), CityStatus.FROZEN, true, city.memberCount(), now);
        txPort.withTransaction(connection -> {
            expect(writePort.upsertFreezeCase(freezeCase), "freeze-upsert");
            expect(writePort.updateCity(updated, city.revision()), "freeze-update-city");
            appendAudit(audit(city.cityId(), "CITY", "city_frozen", actorUuid, jsonPayload("reason", safe(reason)), now));
            return null;
        });
        return toCity(updated);
    }

    @Override
    public City unfreezeCity(String cityReference, UUID actorUuid, String reason) {
        CityRecord city = resolveCity(cityReference);
        if (city.status() != CityStatus.FROZEN && !city.frozen()) {
            return toCity(city);
        }
        long now = System.currentTimeMillis();
        CityRecord updated = updateCityRecord(city, city.name(), city.tag(), city.leaderUuid(), CityStatus.ACTIVE, false, city.memberCount(), now);

        txPort.withTransaction(connection -> {
            readPort.findActiveFreezeCase(city.cityId())
                    .ifPresent(freezeCase -> expect(writePort.closeFreezeCase(freezeCase.caseId(), actorUuid, now), "freeze-close"));
            expect(writePort.updateCity(updated, city.revision()), "unfreeze-update-city");
            appendAudit(audit(city.cityId(), "CITY", "city_unfrozen", actorUuid, jsonPayload("reason", safe(reason)), now));
            return null;
        });
        return toCity(updated);
    }

    @Override
    public City deleteCity(String cityReference, UUID actorUuid, String reason) {
        CityRecord city = resolveCity(cityReference);
        long now = System.currentTimeMillis();

        ClaimBindingRecord claimBinding = readPort.findClaimBinding(city.cityId()).orElse(null);
        if (claimBinding != null) {
            int centerX = (claimBinding.minX() + claimBinding.maxX()) / 2;
            int centerZ = (claimBinding.minZ() + claimBinding.maxZ()) / 2;
            boolean deletedClaim;
            try {
                deletedClaim = huskClaimsPort.deleteClaimAtAsync(claimBinding.worldName(), centerX, centerZ).join();
            } catch (CompletionException ex) {
                Throwable cause = unwrapCompletion(ex);
                throw new IllegalStateException("claim-delete-failed:" + cause.getClass().getSimpleName(), cause);
            }
            if (!deletedClaim) {
                throw new IllegalStateException("claim-delete-failed");
            }
        }

        expect(writePort.hardDeleteCityAggregate(city.cityId()), "hard-delete-city");
        appendAudit(audit(city.cityId(), "CITY", "city_deleted", actorUuid, jsonPayload("reason", safe(reason)), now));
        return toCity(city);
    }

    @Override
    public boolean isCityFrozen(UUID cityId) {
        CityRecord city = requireCity(cityId);
        return city.status() == CityStatus.FROZEN || city.frozen();
    }

    @Override
    public List<String> listCityReferences() {
        Set<String> refs = new LinkedHashSet<>();
        for (CityRecord city : readPort.listCities()) {
            refs.add(city.name());
            refs.add(city.tag());
        }
        return List.copyOf(refs);
    }

    @Override
    public Snapshot snapshot() {
        return new Snapshot(
                true,
                readPort.countActiveFreezeCases(),
                readPort.countPendingInvitations(),
                readPort.countPendingJoinRequests()
        );
    }

    public Optional<City> cityByReference(String cityReference) {
        try {
            return Optional.of(toCity(resolveCity(cityReference)));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    public Optional<City> cityByPlayer(UUID playerUuid) {
        return readPort.findActiveMember(playerUuid)
                .flatMap(member -> readPort.findCityById(member.cityId()))
                .map(this::toCity);
    }

    private void bootstrapDefaultRoles(UUID cityId, long now) {
        expect(writePort.upsertRole(new CityRoleRecord(cityId, ROLE_LEADER, "Capo", 100, toPermissionsJson(allPermissions()))), "role-leader");
        expect(writePort.upsertRole(new CityRoleRecord(cityId, ROLE_UFFICIALE, "Ufficiale", 80, toPermissionsJson(new RolePermissionSet(true, true, true, true, true, true, true)))), "role-ufficiale");
        expect(writePort.upsertRole(new CityRoleRecord(cityId, ROLE_RECRUITER, "Recruiter", 40, toPermissionsJson(new RolePermissionSet(true, false, false, false, false, false, false)))), "role-recruiter");
        expect(writePort.upsertRole(new CityRoleRecord(cityId, ROLE_MEMBER, "Membro", 10, toPermissionsJson(noPermissions()))), "role-member");
        appendAudit(audit(cityId, "CITY", "role_updated", null, jsonPayload("action", "bootstrap_default"), now));
    }

    private void ensureCityNotFrozen(CityRecord city) {
        if (city.status() == CityStatus.FROZEN || city.frozen()) {
            throw new IllegalStateException("city-freeze-restricted");
        }
    }

    private void ensureCanInvite(CityRecord city, UUID actorUuid) {
        if (city.leaderUuid().equals(actorUuid)) {
            return;
        }
        if (!permissionsFor(city.cityId(), actorUuid).canInvite()) {
            throw new IllegalStateException("permission-denied-invite");
        }
    }

    private void ensureCanKick(CityRecord city, UUID actorUuid) {
        if (city.leaderUuid().equals(actorUuid)) {
            return;
        }
        if (!permissionsFor(city.cityId(), actorUuid).canKick()) {
            throw new IllegalStateException("permission-denied-kick");
        }
    }

    private void ensureCanManageMembers(CityRecord city, UUID actorUuid) {
        if (city.leaderUuid().equals(actorUuid)) {
            return;
        }
        if (!permissionsFor(city.cityId(), actorUuid).canManageMembers()) {
            throw new IllegalStateException("permission-denied-manage-members");
        }
    }

    private void ensureCanManageRoles(CityRecord city, UUID actorUuid) {
        if (city.leaderUuid().equals(actorUuid)) {
            return;
        }
        if (!permissionsFor(city.cityId(), actorUuid).canManageRoles()) {
            throw new IllegalStateException("permission-denied-manage-roles");
        }
    }

    private void ensureCanExpandClaims(CityRecord city, UUID actorUuid) {
        if (city.leaderUuid().equals(actorUuid)) {
            return;
        }
        if (!permissionsFor(city.cityId(), actorUuid).canExpandClaims()) {
            throw new IllegalStateException("permission-denied-expand-claim");
        }
    }

    private void ensureCanRequestUpgrade(CityRecord city, UUID actorUuid) {
        if (city.leaderUuid().equals(actorUuid)) {
            return;
        }
        if (!permissionsFor(city.cityId(), actorUuid).canRequestUpgrade()) {
            throw new IllegalStateException("permission-denied-request-upgrade");
        }
    }

    private void ensureLeader(CityRecord city, UUID actorUuid) {
        if (!city.leaderUuid().equals(actorUuid)) {
            throw new IllegalStateException("leader-required");
        }
    }

    private RolePermissionSet permissionsFor(UUID cityId, UUID actorUuid) {
        CityMemberRecord member = requireActiveMember(cityId, actorUuid);
        String roleKey = normalizeRoleKey(member.roleKey());
        if (ROLE_LEADER.equals(roleKey)) {
            return allPermissions();
        }
        return readPort.listRoles(cityId).stream()
                .filter(role -> normalizeRoleKey(role.roleKey()).equals(roleKey))
                .findFirst()
                .map(role -> fromPermissionsJson(role.permissionsJson()))
                .orElseGet(DefaultCityLifecycleService::noPermissions);
    }

    private CityRecord resolveCity(String cityReference) {
        String raw = requireText(cityReference, "cityReference");
        try {
            UUID id = UUID.fromString(raw);
            return requireCity(id);
        } catch (IllegalArgumentException ignored) {
            // not a uuid reference
        }

        return readPort.findCityByName(raw)
                .or(() -> readPort.findCityByTag(raw))
                .orElseThrow(() -> new IllegalStateException("city-not-found"));
    }

    private CityRecord requireCity(UUID cityId) {
        return readPort.findCityById(cityId)
                .orElseThrow(() -> new IllegalStateException("city-not-found"));
    }

    private CityMemberRecord requireActiveMember(UUID cityId, UUID playerUuid) {
        return readPort.findMember(cityId, playerUuid)
                .filter(CityMemberRecord::active)
                .orElseThrow(() -> new IllegalStateException("member-not-found"));
    }

    private CityInvitationRecord requireInvitation(UUID invitationId) {
        return readPort.findInvitation(invitationId)
                .orElseThrow(() -> new IllegalStateException("invitation-not-found"));
    }

    private JoinRequestRecord requireJoinRequest(UUID requestId) {
        return readPort.findJoinRequest(requestId)
                .orElseThrow(() -> new IllegalStateException("join-request-not-found"));
    }

    private CityRecord updateCityRecord(
            CityRecord city,
            String name,
            String tag,
            UUID leaderUuid,
            CityStatus status,
            boolean frozen,
            int memberCount,
            long now
    ) {
        return new CityRecord(
                city.cityId(),
                name,
                tag,
                leaderUuid,
                city.tier(),
                status,
                city.capital(),
                frozen,
                city.treasuryBalance(),
                memberCount,
                city.maxMembers(),
                city.createdAtEpochMilli(),
                now,
                city.revision()
        );
    }

    private void expect(it.patric.cittaexp.persistence.domain.PersistenceWriteOutcome outcome, String op) {
        if (!outcome.success()) {
            logger.warning(
                    "[CittaEXP][city] persistence op failed op="
                            + op
                            + " conflict=" + outcome.conflict()
                            + " message=" + outcome.message()
            );
            throw new IllegalStateException(op + ":" + outcome.message());
        }
    }

    private void appendAudit(AuditEvent event) {
        expect(writePort.appendAuditEvent(new AuditEventRecord(
                event.eventId(),
                event.aggregateType(),
                event.aggregateId(),
                event.eventType(),
                event.actorUuid(),
                event.payloadJson(),
                event.occurredAtEpochMilli()
        )), "append-audit");
    }

    private static AuditEvent audit(UUID cityId, String aggregateType, String eventType, UUID actorUuid, String payloadJson, long now) {
        return new AuditEvent(
                UUID.randomUUID(),
                aggregateType,
                cityId == null ? "n/a" : cityId.toString(),
                eventType,
                actorUuid,
                payloadJson,
                now
        );
    }

    private static String normalizeCityName(String name) {
        String value = requireText(name, "cityName");
        if (value.length() > 32) {
            throw new IllegalStateException("city-name-too-long");
        }
        return value;
    }

    private static String normalizeCityTag(String tag) {
        String value = requireText(tag, "cityTag").toUpperCase(Locale.ROOT);
        if (value.length() != 3) {
            throw new IllegalStateException("city-tag-invalid-length");
        }
        return value;
    }

    private static String normalizeRoleKey(String roleKey) {
        return requireText(roleKey, "roleKey").toLowerCase(Locale.ROOT);
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) {
            throw new IllegalStateException(field + "-blank");
        }
        return normalized;
    }

    private static boolean isExpired(long createdAt) {
        return createdAt + INVITE_EXPIRY_MILLIS < System.currentTimeMillis();
    }

    private static int maxMembersForTier(CityTier tier) {
        return switch (tier) {
            case BORGO -> 10;
            case VILLAGGIO -> 14;
            case REGNO -> 50;
        };
    }

    private static RolePermissionSet allPermissions() {
        return new RolePermissionSet(true, true, true, true, true, true, true);
    }

    private static RolePermissionSet noPermissions() {
        return new RolePermissionSet(false, false, false, false, false, false, false);
    }

    private static String toPermissionsJson(RolePermissionSet permissions) {
        JsonObject root = new JsonObject();
        root.addProperty("canInvite", permissions.canInvite());
        root.addProperty("canKick", permissions.canKick());
        root.addProperty("canManageMembers", permissions.canManageMembers());
        root.addProperty("canManageRoles", permissions.canManageRoles());
        root.addProperty("canRequestUpgrade", permissions.canRequestUpgrade());
        root.addProperty("canExpandClaims", permissions.canExpandClaims());
        root.addProperty("canManageSettings", permissions.canManageSettings());
        return GSON.toJson(root);
    }

    private static RolePermissionSet fromPermissionsJson(String permissionsJson) {
        try {
            JsonObject root = GSON.fromJson(permissionsJson, JsonObject.class);
            return new RolePermissionSet(
                    bool(root, "canInvite"),
                    bool(root, "canKick"),
                    bool(root, "canManageMembers"),
                    bool(root, "canManageRoles"),
                    bool(root, "canRequestUpgrade"),
                    bool(root, "canExpandClaims"),
                    bool(root, "canManageSettings")
            );
        } catch (RuntimeException ex) {
            return noPermissions();
        }
    }

    private static boolean bool(JsonObject root, String key) {
        return root != null && root.has(key) && root.get(key).getAsBoolean();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static Throwable unwrapCompletion(Throwable throwable) {
        if (throwable instanceof CompletionException completionException && completionException.getCause() != null) {
            return completionException.getCause();
        }
        return throwable;
    }

    private static String jsonPayload(String key, String value) {
        JsonObject root = new JsonObject();
        root.addProperty(key, value == null ? "" : value);
        return GSON.toJson(root);
    }

    private static String jsonPayload(String key1, String value1, String key2, String value2) {
        JsonObject root = new JsonObject();
        root.addProperty(key1, value1 == null ? "" : value1);
        root.addProperty(key2, value2 == null ? "" : value2);
        return GSON.toJson(root);
    }

    private static String jsonPayload(String key1, String value1, String key2, String value2, String key3, String value3) {
        JsonObject root = new JsonObject();
        root.addProperty(key1, value1 == null ? "" : value1);
        root.addProperty(key2, value2 == null ? "" : value2);
        root.addProperty(key3, value3 == null ? "" : value3);
        return GSON.toJson(root);
    }

    private City toCity(CityRecord city) {
        return new City(
                city.cityId(),
                city.name(),
                city.tag(),
                city.leaderUuid(),
                city.tier(),
                city.status(),
                city.capital(),
                city.treasuryBalance(),
                city.memberCount(),
                city.maxMembers(),
                city.revision(),
                city.createdAtEpochMilli(),
                city.updatedAtEpochMilli()
        );
    }

    private CityRole toCityRole(CityRoleRecord role, long updatedAt) {
        return new CityRole(
                role.cityId(),
                role.roleKey(),
                role.displayName(),
                role.priority(),
                fromPermissionsJson(role.permissionsJson()),
                updatedAt
        );
    }

    private CityMember toCityMember(CityMemberRecord member) {
        return new CityMember(
                member.cityId(),
                member.playerUuid(),
                member.roleKey(),
                member.active() ? MemberStatus.ACTIVE : MemberStatus.LEFT,
                member.joinedAtEpochMilli(),
                member.joinedAtEpochMilli()
        );
    }

    private CityInvitation toInvitation(CityInvitationRecord invitation) {
        return new CityInvitation(
                invitation.invitationId(),
                invitation.cityId(),
                invitation.invitedPlayerUuid(),
                invitation.invitedByUuid(),
                invitation.status(),
                invitation.createdAtEpochMilli(),
                invitation.expiresAtEpochMilli(),
                invitation.updatedAtEpochMilli()
        );
    }

    private JoinRequest toJoinRequest(JoinRequestRecord request) {
        return new JoinRequest(
                request.requestId(),
                request.cityId(),
                request.playerUuid(),
                request.status(),
                request.message(),
                request.reviewedByUuid(),
                request.requestedAtEpochMilli(),
                request.reviewedAtEpochMilli()
        );
    }

    private static ClaimBindingRecord toClaimBindingRecord(ClaimBinding claim) {
        return new ClaimBindingRecord(
                claim.cityId(),
                claim.world(),
                claim.minX(),
                claim.minZ(),
                claim.maxX(),
                claim.maxZ(),
                claim.area(),
                claim.createdAtEpochMilli(),
                claim.updatedAtEpochMilli()
        );
    }

    private static ClaimBinding toClaimBinding(ClaimBindingRecord claim) {
        return new ClaimBinding(
                claim.cityId(),
                claim.worldName(),
                claim.minX(),
                claim.minZ(),
                claim.maxX(),
                claim.maxZ(),
                claim.area(),
                claim.createdAtEpochMilli(),
                claim.updatedAtEpochMilli()
        );
    }
}
