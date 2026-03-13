package it.patric.cittaexp.playersgui;

import it.patric.cittaexp.integration.husktowns.HuskTownsApiHook;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import net.william278.husktowns.town.Town;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class CityPlayersActionService {

    public enum ActionType {
        PROMOTE,
        DEMOTE,
        EVICT,
        TRANSFER
    }

    public record ActionResult(boolean success, String reasonCode) {
        public static ActionResult ok() {
            return new ActionResult(true, "ok");
        }

        public static ActionResult fail(String code) {
            return new ActionResult(false, code);
        }
    }

    private final Plugin plugin;
    private final HuskTownsApiHook huskTownsApiHook;
    private final HuskTownsRoleLadderService roleLadderService;

    public CityPlayersActionService(
            Plugin plugin,
            HuskTownsApiHook huskTownsApiHook,
            HuskTownsRoleLadderService roleLadderService
    ) {
        this.plugin = plugin;
        this.huskTownsApiHook = huskTownsApiHook;
        this.roleLadderService = roleLadderService;
    }

    public ActionResult executeStaffOverride(Player actor, int townId, UUID targetPlayerId, ActionType actionType) {
        Optional<Town> optionalTown = huskTownsApiHook.getTownById(townId);
        if (optionalTown.isEmpty()) {
            return ActionResult.fail("town-not-found");
        }
        Town town = optionalTown.get();
        Map<UUID, Integer> members = town.getMembers();
        Integer currentWeight = members.get(targetPlayerId);
        if (currentWeight == null) {
            return ActionResult.fail("member-not-found");
        }

        HuskTownsRoleLadderService.RoleLadderSnapshot ladder = roleLadderService.snapshot();
        int mayorWeight = ladder.mayorWeight();

        try {
            huskTownsApiHook.editTown(actor, townId, editable -> {
                Map<UUID, Integer> editableMembers = editable.getMembers();
                Integer current = editableMembers.get(targetPlayerId);
                if (current == null) {
                    throw new IllegalStateException("member-not-found");
                }

                switch (actionType) {
                    case PROMOTE -> {
                        OptionalInt higher = ladder.higherThan(current);
                        if (higher.isEmpty()) {
                            throw new IllegalStateException("cannot-promote-max-role");
                        }
                        editableMembers.put(targetPlayerId, higher.getAsInt());
                    }
                    case DEMOTE -> {
                        if (editable.getMayor().equals(targetPlayerId)) {
                            throw new IllegalStateException("cannot-demote-mayor");
                        }
                        OptionalInt lower = ladder.lowerThan(current);
                        if (lower.isEmpty()) {
                            throw new IllegalStateException("cannot-demote-min-role");
                        }
                        editableMembers.put(targetPlayerId, lower.getAsInt());
                    }
                    case EVICT -> {
                        if (editable.getMayor().equals(targetPlayerId)) {
                            throw new IllegalStateException("cannot-evict-mayor");
                        }
                        editableMembers.remove(targetPlayerId);
                    }
                    case TRANSFER -> {
                        if (editable.getMayor().equals(targetPlayerId)) {
                            throw new IllegalStateException("already-mayor");
                        }
                        OptionalInt lowerMayor = ladder.lowerThan(mayorWeight);
                        if (lowerMayor.isEmpty()) {
                            throw new IllegalStateException("no-lower-role-for-old-mayor");
                        }
                        UUID oldMayor = editable.getMayor();
                        editableMembers.put(oldMayor, lowerMayor.getAsInt());
                        editableMembers.put(targetPlayerId, mayorWeight);
                    }
                    default -> throw new IllegalStateException("unsupported-action");
                }
            });
            plugin.getLogger().info("[CittaEXP][audit][players] action=" + actionType.name().toLowerCase()
                    + " actor=" + actor.getUniqueId()
                    + " townId=" + townId
                    + " target=" + targetPlayerId
                    + " result=success reason=ok");
            return ActionResult.ok();
        } catch (IllegalStateException stateException) {
            plugin.getLogger().warning("[CittaEXP][audit][players] action=" + actionType.name().toLowerCase()
                    + " actor=" + actor.getUniqueId()
                    + " townId=" + townId
                    + " target=" + targetPlayerId
                    + " result=failed reason=" + stateException.getMessage());
            return ActionResult.fail(stateException.getMessage() == null ? "operation-failed" : stateException.getMessage());
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("[CittaEXP][audit][players] action=" + actionType.name().toLowerCase()
                    + " actor=" + actor.getUniqueId()
                    + " townId=" + townId
                    + " target=" + targetPlayerId
                    + " result=failed reason=runtime-" + exception.getClass().getSimpleName());
            return ActionResult.fail("operation-failed");
        }
    }
}
