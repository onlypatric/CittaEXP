package it.patric.cittaexp.permissions;

import it.patric.cittaexp.challenges.ChallengeStore;
import it.patric.cittaexp.vault.VaultAclEntry;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.william278.husktowns.events.MemberLeaveEvent;
import net.william278.husktowns.events.TownDisbandEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public final class TownMemberPermissionService implements Listener {

    private final Plugin plugin;
    private final ChallengeStore challengeStore;
    private final TownMemberPermissionRepository repository;
    private final ExecutorService ioExecutor;
    private final ConcurrentMap<Integer, Map<UUID, EnumMap<TownMemberPermission, Boolean>>> cacheByTown;
    private final Set<Integer> legacyVaultMigratedTowns;

    public TownMemberPermissionService(
            Plugin plugin,
            ChallengeStore challengeStore
    ) {
        this.plugin = plugin;
        this.challengeStore = challengeStore;
        this.repository = new TownMemberPermissionRepository(plugin);
        this.ioExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "cittaexp-town-member-permissions");
            thread.setDaemon(true);
            return thread;
        });
        this.cacheByTown = new ConcurrentHashMap<>();
        this.legacyVaultMigratedTowns = ConcurrentHashMap.newKeySet();
    }

    public void start() {
        repository.initialize();
    }

    public void stop() {
        ioExecutor.shutdown();
        try {
            ioExecutor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        cacheByTown.clear();
        legacyVaultMigratedTowns.clear();
    }

    public boolean isAllowed(int townId, UUID mayorId, UUID playerId, TownMemberPermission permission) {
        if (permission == null || playerId == null) {
            return false;
        }
        if (playerId.equals(mayorId)) {
            return true;
        }
        EnumMap<TownMemberPermission, Boolean> snapshot = permissionsForPlayer(townId, playerId);
        return snapshot.getOrDefault(permission, false);
    }

    public Map<TownMemberPermission, Boolean> effectivePermissionsForPlayer(int townId, UUID mayorId, UUID playerId) {
        EnumMap<TownMemberPermission, Boolean> result = new EnumMap<>(TownMemberPermission.class);
        for (TownMemberPermission permission : TownMemberPermission.values()) {
            result.put(permission, isAllowed(townId, mayorId, playerId, permission));
        }
        return result;
    }

    public Map<UUID, VaultAclEntry> vaultAclSnapshot(int townId) {
        Map<UUID, EnumMap<TownMemberPermission, Boolean>> town = ensureTownLoaded(townId);
        Map<UUID, VaultAclEntry> result = new HashMap<>();
        for (Map.Entry<UUID, EnumMap<TownMemberPermission, Boolean>> entry : town.entrySet()) {
            EnumMap<TownMemberPermission, Boolean> values = entry.getValue();
            boolean canDeposit = values.getOrDefault(TownMemberPermission.ITEM_VAULT_DEPOSIT, false);
            boolean canWithdraw = values.getOrDefault(TownMemberPermission.ITEM_VAULT_WITHDRAW, false);
            if (!canDeposit && !canWithdraw) {
                continue;
            }
            result.put(entry.getKey(), new VaultAclEntry(entry.getKey(), canDeposit, canWithdraw, Instant.EPOCH));
        }
        return result;
    }

    public CompletableFuture<Boolean> setPermission(
            int townId,
            UUID actorId,
            UUID mayorId,
            UUID targetPlayerId,
            TownMemberPermission permission,
            boolean allowed
    ) {
        return CompletableFuture.supplyAsync(() -> {
            if (permission == null || actorId == null || targetPlayerId == null) {
                return false;
            }
            if (!actorId.equals(mayorId) || targetPlayerId.equals(mayorId)) {
                return false;
            }
            Map<UUID, EnumMap<TownMemberPermission, Boolean>> town = ensureTownLoaded(townId);
            EnumMap<TownMemberPermission, Boolean> values = town.computeIfAbsent(
                    targetPlayerId,
                    ignored -> new EnumMap<>(TownMemberPermission.class)
            );
            if (allowed) {
                values.put(permission, true);
            } else {
                values.remove(permission);
                if (values.isEmpty()) {
                    town.remove(targetPlayerId);
                }
            }
            if (permission == TownMemberPermission.ITEM_VAULT_DEPOSIT
                    || permission == TownMemberPermission.ITEM_VAULT_WITHDRAW) {
                EnumMap<TownMemberPermission, Boolean> current = town.computeIfAbsent(
                        targetPlayerId,
                        ignored -> new EnumMap<>(TownMemberPermission.class)
                );
                boolean canDeposit = current.getOrDefault(TownMemberPermission.ITEM_VAULT_DEPOSIT, false);
                boolean canWithdraw = current.getOrDefault(TownMemberPermission.ITEM_VAULT_WITHDRAW, false);
                if (allowed && (canDeposit || canWithdraw)) {
                    current.put(TownMemberPermission.ITEM_VAULT_OPEN, true);
                    repository.setPermission(townId, targetPlayerId, TownMemberPermission.ITEM_VAULT_OPEN, true, Instant.now());
                }
                if (current.isEmpty()) {
                    town.remove(targetPlayerId);
                }
            }
            repository.setPermission(townId, targetPlayerId, permission, allowed, Instant.now());
            plugin.getLogger().info("[member-permissions] actor=" + actorId
                    + " townId=" + townId
                    + " target=" + targetPlayerId
                    + " permission=" + permission.name()
                    + " allowed=" + allowed);
            return true;
        }, ioExecutor);
    }

    public void deleteTown(int townId) {
        cacheByTown.remove(townId);
        legacyVaultMigratedTowns.remove(townId);
        repository.deleteTown(townId);
    }

    public void deletePlayer(int townId, UUID playerId) {
        Map<UUID, EnumMap<TownMemberPermission, Boolean>> town = cacheByTown.get(townId);
        if (town != null) {
            town.remove(playerId);
        }
        repository.deletePlayer(townId, playerId);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMemberLeave(MemberLeaveEvent event) {
        deletePlayer(event.getTown().getId(), event.getUser().getUuid());
    }

    @EventHandler(ignoreCancelled = true)
    public void onTownDisband(TownDisbandEvent event) {
        deleteTown(event.getTown().getId());
    }

    private EnumMap<TownMemberPermission, Boolean> permissionsForPlayer(int townId, UUID playerId) {
        Map<UUID, EnumMap<TownMemberPermission, Boolean>> town = ensureTownLoaded(townId);
        EnumMap<TownMemberPermission, Boolean> values = town.get(playerId);
        return values == null ? new EnumMap<>(TownMemberPermission.class) : new EnumMap<>(values);
    }

    private Map<UUID, EnumMap<TownMemberPermission, Boolean>> ensureTownLoaded(int townId) {
        Map<UUID, EnumMap<TownMemberPermission, Boolean>> town = cacheByTown.computeIfAbsent(
                townId,
                repository::loadTownPermissions
        );
        migrateLegacyVaultAclIfNeeded(townId, town);
        return town;
    }

    private void migrateLegacyVaultAclIfNeeded(
            int townId,
            Map<UUID, EnumMap<TownMemberPermission, Boolean>> town
    ) {
        if (!legacyVaultMigratedTowns.add(townId)) {
            return;
        }
        Map<UUID, VaultAclEntry> legacy = challengeStore.loadVaultAcl(townId);
        if (legacy.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        for (Map.Entry<UUID, VaultAclEntry> entry : legacy.entrySet()) {
            EnumMap<TownMemberPermission, Boolean> values = town.computeIfAbsent(
                    entry.getKey(),
                    ignored -> new EnumMap<>(TownMemberPermission.class)
            );
            boolean touched = false;
            if (entry.getValue().canDeposit()
                    && !values.getOrDefault(TownMemberPermission.ITEM_VAULT_DEPOSIT, false)) {
                values.put(TownMemberPermission.ITEM_VAULT_DEPOSIT, true);
                repository.setPermission(townId, entry.getKey(), TownMemberPermission.ITEM_VAULT_DEPOSIT, true, now);
                touched = true;
            }
            if (entry.getValue().canWithdraw()
                    && !values.getOrDefault(TownMemberPermission.ITEM_VAULT_WITHDRAW, false)) {
                values.put(TownMemberPermission.ITEM_VAULT_WITHDRAW, true);
                repository.setPermission(townId, entry.getKey(), TownMemberPermission.ITEM_VAULT_WITHDRAW, true, now);
                touched = true;
            }
            if (touched) {
                values.put(TownMemberPermission.ITEM_VAULT_OPEN, true);
                repository.setPermission(townId, entry.getKey(), TownMemberPermission.ITEM_VAULT_OPEN, true, now);
            }
            if (values.isEmpty()) {
                town.remove(entry.getKey());
            }
        }
    }
}
