package it.patric.cittaexp.vault;

import it.patric.cittaexp.challenges.ChallengeStore;
import it.patric.cittaexp.integration.husktowns.HuskTownsApiHook;
import it.patric.cittaexp.permissions.TownMemberPermission;
import it.patric.cittaexp.permissions.TownMemberPermissionService;
import it.patric.cittaexp.text.TimedTitleHelper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class CityItemVaultService {

    public record VaultState(int townId, ItemStack[] slots, Map<UUID, VaultAclEntry> acl) {
    }

    public record MutationResult(boolean success, String reason, ItemStack transferred) {
    }

    public record ContributionEvent(int townId, int amount, Material material) {
    }

    public record PendingRewardClaim(long entryId, int townId, ItemStack stack, String note, Instant createdAt) {
    }

    public record RedeemResult(boolean success, String reason, int redeemedStacks, int redeemedItems, int remainingStacks) {
    }

    private final Plugin plugin;
    private final ChallengeStore repository;
    private final HuskTownsApiHook huskTownsApiHook;
    private final TownMemberPermissionService permissionService;
    private final CityVaultSettings settings;
    private final ExecutorService ioExecutor;
    private final ConcurrentHashMap<Integer, ItemStack[]> slotsCache;
    private final ConcurrentHashMap<Integer, Map<UUID, VaultAclEntry>> aclCache;
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private volatile Consumer<ContributionEvent> contributionListener;

    public CityItemVaultService(
            Plugin plugin,
            ChallengeStore repository,
            HuskTownsApiHook huskTownsApiHook,
            TownMemberPermissionService permissionService,
            CityVaultSettings settings
    ) {
        this.plugin = plugin;
        this.repository = repository;
        this.huskTownsApiHook = huskTownsApiHook;
        this.permissionService = permissionService;
        this.settings = settings;
        this.ioExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "cittaexp-vault-io");
            thread.setDaemon(true);
            return thread;
        });
        this.slotsCache = new ConcurrentHashMap<>();
        this.aclCache = new ConcurrentHashMap<>();
        this.contributionListener = null;
    }

    public void start() {
        if (!settings.enabled()) {
            plugin.getLogger().info("[vault] disabilitato da configurazione.");
            return;
        }
        ioExecutor.execute(repository::initialize);
    }

    public void stop() {
        ioExecutor.shutdown();
        try {
            ioExecutor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    public CompletableFuture<Void> warmTown(int townId) {
        return loadVaultState(townId).thenApply(state -> null);
    }

    public CompletableFuture<VaultState> loadVaultState(int townId) {
        return CompletableFuture.supplyAsync(() -> {
            ensureTownLoaded(townId);
            ItemStack[] slots = cloneSlots(slotsCache.get(townId));
            Map<UUID, VaultAclEntry> acl = new HashMap<>(permissionService.vaultAclSnapshot(townId));
            return new VaultState(townId, slots, acl);
        }, ioExecutor);
    }

    public VaultAclEntry aclSnapshot(int townId, UUID playerId) {
        Map<UUID, VaultAclEntry> acl = permissionService.vaultAclSnapshot(townId);
        return acl.getOrDefault(playerId, new VaultAclEntry(playerId, false, false, Instant.EPOCH));
    }

    public CompletableFuture<VaultAclEntry> toggleDepositAcl(int townId, UUID actorId, UUID targetPlayerId) {
        return CompletableFuture.supplyAsync(() -> {
            ensureTownLoaded(townId);
            VaultAclEntry current = aclSnapshot(townId, targetPlayerId);
            boolean updatedAllowed = !current.canDeposit();
            boolean changed = permissionService.setPermission(
                    townId,
                    actorId,
                    resolveMayorId(townId),
                    targetPlayerId,
                    TownMemberPermission.ITEM_VAULT_DEPOSIT,
                    updatedAllowed
            ).join();
            if (!changed) {
                return current;
            }
            Map<UUID, VaultAclEntry> acl = permissionService.vaultAclSnapshot(townId);
            aclCache.put(townId, new HashMap<>(acl));
            VaultAclEntry updated = acl.getOrDefault(targetPlayerId, new VaultAclEntry(
                    targetPlayerId,
                    false,
                    current.canWithdraw(),
                    Instant.now()
            ));
            repository.appendVaultAudit(townId, actorId, "acl-toggle-deposit", targetPlayerId, null, 0, "toggle");
            return updated;
        }, ioExecutor);
    }

    public CompletableFuture<VaultAclEntry> toggleWithdrawAcl(int townId, UUID actorId, UUID targetPlayerId) {
        return CompletableFuture.supplyAsync(() -> {
            ensureTownLoaded(townId);
            VaultAclEntry current = aclSnapshot(townId, targetPlayerId);
            boolean updatedAllowed = !current.canWithdraw();
            boolean changed = permissionService.setPermission(
                    townId,
                    actorId,
                    resolveMayorId(townId),
                    targetPlayerId,
                    TownMemberPermission.ITEM_VAULT_WITHDRAW,
                    updatedAllowed
            ).join();
            if (!changed) {
                return current;
            }
            Map<UUID, VaultAclEntry> acl = permissionService.vaultAclSnapshot(townId);
            aclCache.put(townId, new HashMap<>(acl));
            VaultAclEntry updated = acl.getOrDefault(targetPlayerId, new VaultAclEntry(
                    targetPlayerId,
                    current.canDeposit(),
                    false,
                    Instant.now()
            ));
            repository.appendVaultAudit(townId, actorId, "acl-toggle-withdraw", targetPlayerId, null, 0, "toggle");
            return updated;
        }, ioExecutor);
    }

    public boolean canOpen(int townId, UUID mayorId, UUID playerId) {
        return permissionService.isAllowed(townId, mayorId, playerId, TownMemberPermission.ITEM_VAULT_OPEN);
    }

    public boolean canDeposit(int townId, UUID mayorId, UUID playerId) {
        return permissionService.isAllowed(townId, mayorId, playerId, TownMemberPermission.ITEM_VAULT_DEPOSIT);
    }

    public boolean canWithdraw(int townId, UUID mayorId, UUID playerId) {
        return permissionService.isAllowed(townId, mayorId, playerId, TownMemberPermission.ITEM_VAULT_WITHDRAW);
    }

    public boolean canRedeemChallengeRewards(int townId, UUID mayorId, UUID playerId) {
        return permissionService.isAllowed(townId, mayorId, playerId, TownMemberPermission.CITY_CHALLENGES_REDEEM);
    }

    public int redeemPendingRewardsCommand(Player player) {
        if (player == null) {
            return com.mojang.brigadier.Command.SINGLE_SUCCESS;
        }
        java.util.Optional<net.william278.husktowns.town.Member> member = huskTownsApiHook.getUserTown(player);
        if (member.isEmpty()) {
            player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                    .deserialize("<red>Devi essere membro attivo di una citta.</red>"));
            return com.mojang.brigadier.Command.SINGLE_SUCCESS;
        }
        int townId = member.get().town().getId();
        UUID mayorId = member.get().town().getMayor();
        if (!canRedeemChallengeRewards(townId, mayorId, player.getUniqueId())) {
            player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                    .deserialize("<red>Non hai il permesso per riscattare le reward residue della citta.</red>"));
            return com.mojang.brigadier.Command.SINGLE_SUCCESS;
        }
        redeemPendingRewardsToPlayer(townId, player.getUniqueId(), player)
                .whenComplete((result, error) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (error != null || result == null) {
                        player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                                .deserialize("<red>Riscatto reward non riuscito.</red>"));
                        return;
                    }
                    switch (result.reason()) {
                        case "empty" -> player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                                .deserialize("<gray>Non ci sono reward residue da riscattare.</gray>"));
                        case "inventory-full" -> player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                                .deserialize("<red>Non hai spazio sufficiente nel tuo inventario.</red> <gray>Libera qualche slot e riprova con </gray><gold>/city redeem</gold><gray>.</gray>"));
                        case "claim-failed", "player-unavailable" -> player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                                .deserialize("<red>Riscatto reward non riuscito.</red>"));
                        default -> {
                            String message = "<green>Reward riscattate.</green> <gray>Stack consegnate: </gray><white>"
                                    + result.redeemedStacks()
                                    + "</white><gray> · Item consegnati: </gray><white>"
                                    + result.redeemedItems()
                                    + "</white>";
                            if (result.remainingStacks() > 0) {
                                message += "<gray> · Restano </gray><white>" + result.remainingStacks()
                                        + "</white><gray> stack in attesa.</gray>";
                            }
                            player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(message));
                        }
                    }
                }));
        return com.mojang.brigadier.Command.SINGLE_SUCCESS;
    }

    public CompletableFuture<MutationResult> depositFromPlayerInventory(
            int townId,
            UUID actorId,
            ItemStack stack
    ) {
        return CompletableFuture.supplyAsync(() -> {
            if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
                return new MutationResult(false, "empty-stack", null);
            }
            ensureTownLoaded(townId);
            ItemStack[] slots = slotsCache.get(townId);
            if (!canFitEntireStack(slots, stack, settings.maxStackSize())) {
                return new MutationResult(false, "vault-full", null);
            }
            ItemStack remaining = stack.clone();
            int moved = moveIntoVault(slots, remaining, settings.maxStackSize());
            if (moved != stack.getAmount() || remaining.getAmount() > 0) {
                return new MutationResult(false, "vault-full", null);
            }
            persistSlots(townId, slots);
            repository.appendVaultAudit(townId, actorId, "deposit", actorId, ItemStackCodec.encode(stack), moved, "manual");
            Consumer<ContributionEvent> listener = contributionListener;
            if (listener != null) {
                try {
                    listener.accept(new ContributionEvent(townId, moved, stack.getType()));
                } catch (RuntimeException ignored) {
                }
            }
            ItemStack transferred = stack.clone();
            transferred.setAmount(moved);
            return new MutationResult(true, "deposited", transferred);
        }, ioExecutor);
    }

    public CompletableFuture<MutationResult> withdrawFromSlot(
            int townId,
            UUID actorId,
            int absoluteSlot
    ) {
        return CompletableFuture.supplyAsync(() -> {
            ensureTownLoaded(townId);
            ItemStack[] slots = slotsCache.get(townId);
            if (absoluteSlot < 0 || absoluteSlot >= slots.length) {
                return new MutationResult(false, "slot-out-of-range", null);
            }
            ItemStack item = slots[absoluteSlot];
            if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
                return new MutationResult(false, "slot-empty", null);
            }
            slots[absoluteSlot] = null;
            persistSlots(townId, slots);
            repository.appendVaultAudit(townId, actorId, "withdraw", actorId, ItemStackCodec.encode(item), item.getAmount(), "manual");
            return new MutationResult(true, "withdrawn", item.clone());
        }, ioExecutor);
    }

    public CompletableFuture<Boolean> rollbackWithdraw(
            int townId,
            UUID actorId,
            int preferredSlot,
            ItemStack stack
    ) {
        return CompletableFuture.supplyAsync(() -> {
            if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
                return false;
            }
            ensureTownLoaded(townId);
            ItemStack[] slots = slotsCache.get(townId);
            if (!canFitEntireStack(slots, stack, settings.maxStackSize())) {
                return false;
            }
            ItemStack clone = stack.clone();
            if (preferredSlot >= 0 && preferredSlot < slots.length) {
                ItemStack existing = slots[preferredSlot];
                if (existing == null || existing.getType().isAir()) {
                    slots[preferredSlot] = clone;
                    persistSlots(townId, slots);
                    repository.appendVaultAudit(townId, actorId, "withdraw-rollback", actorId, ItemStackCodec.encode(stack), stack.getAmount(), "inventory-full");
                    return true;
                }
            }
            int moved = moveIntoVault(slots, clone, settings.maxStackSize());
            if (moved != stack.getAmount() || clone.getAmount() > 0) {
                return false;
            }
            persistSlots(townId, slots);
            repository.appendVaultAudit(townId, actorId, "withdraw-rollback", actorId, ItemStackCodec.encode(stack), stack.getAmount(), "inventory-full");
            return true;
        }, ioExecutor);
    }

    public CompletableFuture<Boolean> depositRewardItems(
            int townId,
            Map<Material, Integer> rewards,
            String note
    ) {
        return CompletableFuture.supplyAsync(() -> {
            if (rewards == null || rewards.isEmpty()) {
                return true;
            }
            return depositRewardStacksInternal(townId, normalizeRewardMaterialStacks(rewards), note);
        }, ioExecutor);
    }

    public CompletableFuture<Boolean> depositRewardStacks(
            int townId,
            java.util.List<ItemStack> rewards,
            String note
    ) {
        return CompletableFuture.supplyAsync(() -> {
            if (rewards == null || rewards.isEmpty()) {
                return true;
            }
            return depositRewardStacksInternal(townId, normalizeRewardStacks(rewards), note);
        }, ioExecutor);
    }

    public CompletableFuture<List<PendingRewardClaim>> loadPendingRewardClaims(int townId) {
        return CompletableFuture.supplyAsync(() -> repository.loadPendingRewardEntries(townId).stream()
                .map(entry -> {
                    try {
                        ItemStack stack = ItemStackCodec.decode(entry.itemBase64());
                        if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
                            return null;
                        }
                        return new PendingRewardClaim(entry.entryId(), entry.townId(), stack, entry.note(), entry.createdAt());
                    } catch (Exception ignored) {
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .toList(), ioExecutor);
    }

    public CompletableFuture<RedeemResult> redeemPendingRewardsToPlayer(int townId, UUID actorId, Player player) {
        if (player == null) {
            return CompletableFuture.completedFuture(new RedeemResult(false, "player-unavailable", 0, 0, 0));
        }
        return loadPendingRewardClaims(townId).thenCompose(entries -> {
            if (entries.isEmpty()) {
                return CompletableFuture.completedFuture(new RedeemResult(false, "empty", 0, 0, 0));
            }
            return supplySync(() -> selectRedeemableEntries(player, entries)).thenCompose(selection -> {
                if (selection.selected().isEmpty()) {
                    return CompletableFuture.completedFuture(new RedeemResult(
                            false,
                            "inventory-full",
                            0,
                            0,
                            entries.size()
                    ));
                }
                List<Long> selectedIds = selection.selected().stream()
                        .map(PendingRewardClaim::entryId)
                        .toList();
                return CompletableFuture.supplyAsync(() -> repository.deletePendingRewardEntries(townId, selectedIds), ioExecutor)
                        .thenCompose(removed -> {
                            if (removed <= 0) {
                                return CompletableFuture.completedFuture(new RedeemResult(false, "claim-failed", 0, 0, entries.size()));
                            }
                            return supplySync(() -> addStacksToPlayer(player, selection.selected())).thenCompose(delivery -> {
                                CompletableFuture<Void> rollbackFuture = delivery.leftovers().isEmpty()
                                        ? CompletableFuture.completedFuture(null)
                                        : CompletableFuture.runAsync(() -> queuePendingRewardStacksInternal(townId, delivery.leftovers(),
                                                "redeem-rollback"), ioExecutor);
                                return rollbackFuture.thenApply(ignored -> {
                                    int deliveredStacks = Math.max(0, selection.selected().size() - delivery.leftovers().size());
                                    int deliveredItems = delivery.deliveredItems();
                                    repository.appendVaultAudit(
                                            townId,
                                            actorId,
                                            "redeem",
                                            actorId,
                                            null,
                                            deliveredItems,
                                            "pending-reward"
                                    );
                                    int remaining = Math.max(0, entries.size() - deliveredStacks);
                                    return new RedeemResult(deliveredStacks > 0, "redeemed", deliveredStacks, deliveredItems, remaining);
                                });
                            });
                        });
            });
        });
    }

    public CompletableFuture<Integer> countMaterial(
            int townId,
            Material material
    ) {
        return CompletableFuture.supplyAsync(() -> {
            if (material == null || material.isAir()) {
                return 0;
            }
            ensureTownLoaded(townId);
            return countMaterial(slotsCache.get(townId), material);
        }, ioExecutor);
    }

    public CompletableFuture<Boolean> consumeMaterial(
            int townId,
            Material material,
            int amount,
            UUID actorId,
            String note
    ) {
        return CompletableFuture.supplyAsync(() -> {
            int requested = Math.max(0, amount);
            if (material == null || material.isAir() || requested <= 0) {
                return false;
            }
            ensureTownLoaded(townId);
            ItemStack[] slots = slotsCache.get(townId);
            if (countMaterial(slots, material) < requested) {
                return false;
            }
            int remaining = requested;
            for (int i = 0; i < slots.length && remaining > 0; i++) {
                ItemStack item = slots[i];
                if (item == null || item.getType() != material || item.getAmount() <= 0) {
                    continue;
                }
                int take = Math.min(item.getAmount(), remaining);
                int newAmount = item.getAmount() - take;
                if (newAmount <= 0) {
                    slots[i] = null;
                } else {
                    item.setAmount(newAmount);
                }
                remaining -= take;
            }
            if (remaining > 0) {
                return false;
            }
            persistSlots(townId, slots);
            ItemStack consumed = new ItemStack(material, requested);
            repository.appendVaultAudit(
                    townId,
                    actorId,
                    "challenge-consume",
                    actorId,
                    ItemStackCodec.encode(consumed),
                    requested,
                    note == null || note.isBlank() ? "quest-consume" : note
            );
            return true;
        }, ioExecutor);
    }

    public int slotCount() {
        return settings.slotCount();
    }

    public void setContributionListener(Consumer<ContributionEvent> contributionListener) {
        this.contributionListener = contributionListener;
    }

    private void ensureTownLoaded(int townId) {
        slotsCache.computeIfAbsent(townId, key -> loadSlotsFromDb(key.intValue()));
        aclCache.computeIfAbsent(townId, key -> new HashMap<>(permissionService.vaultAclSnapshot(key.intValue())));
    }

    private boolean depositRewardStacksInternal(int townId, List<ItemStack> rewards, String note) {
        ensureTownLoaded(townId);
        ItemStack[] slots = slotsCache.get(townId);
        int movedTotal = 0;
        List<ItemStack> overflow = new ArrayList<>();
        for (ItemStack reward : rewards) {
            if (reward == null || reward.getType().isAir() || reward.getAmount() <= 0) {
                continue;
            }
            ItemStack clone = reward.clone();
            movedTotal += moveIntoVault(slots, clone, settings.maxStackSize());
            if (clone.getAmount() > 0) {
                overflow.add(clone);
            }
        }
        if (movedTotal > 0) {
            persistSlots(townId, slots);
            repository.appendVaultAudit(townId, null, "reward", null, null, movedTotal, note);
        }
        if (!overflow.isEmpty()) {
            queuePendingRewardStacksInternal(townId, overflow, note);
            notifyPendingRewardQueue(townId, overflow);
        }
        return movedTotal > 0 || !overflow.isEmpty();
    }

    private void queuePendingRewardStacksInternal(int townId, List<ItemStack> overflow, String note) {
        List<String> encoded = new ArrayList<>();
        int total = 0;
        for (ItemStack stack : normalizeRewardStacks(overflow)) {
            if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
                continue;
            }
            encoded.add(ItemStackCodec.encode(stack));
            total += stack.getAmount();
        }
        if (encoded.isEmpty()) {
            return;
        }
        repository.appendPendingRewardEntries(townId, encoded, note, Instant.now());
        repository.appendVaultAudit(townId, null, "reward-pending", null, null, total, note);
    }

    private void notifyPendingRewardQueue(int townId, List<ItemStack> overflow) {
        int total = overflow.stream()
                .filter(stack -> stack != null && !stack.getType().isAir())
                .mapToInt(ItemStack::getAmount)
                .sum();
        if (total <= 0) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Map<UUID, Integer> members = huskTownsApiHook.getTownMembers(townId);
            if (members.isEmpty()) {
                return;
            }
            String message = "<yellow>Il vault citta e pieno.</yellow> <gray>"
                    + total
                    + " item reward non sono entrati e possono essere riscattati con </gray><gold>/city redeem</gold><gray>.</gray>";
            Component title = MINI_MESSAGE.deserialize("<yellow><bold>Vault pieno</bold></yellow>");
            Component subtitle = MINI_MESSAGE.deserialize("<gray>Una parte delle reward e in attesa.</gray> <gold>/city redeem</gold>");
            List<Player> onlineMembers = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (members.containsKey(online.getUniqueId())) {
                    online.sendMessage(MINI_MESSAGE.deserialize(message));
                    onlineMembers.add(online);
                }
            }
            TimedTitleHelper.show(onlineMembers, title, subtitle);
        });
    }

    private List<ItemStack> normalizeRewardMaterialStacks(Map<Material, Integer> rewards) {
        List<ItemStack> result = new ArrayList<>();
        if (rewards == null || rewards.isEmpty()) {
            return result;
        }
        for (Map.Entry<Material, Integer> entry : rewards.entrySet()) {
            Material material = entry.getKey();
            int amount = Math.max(0, entry.getValue());
            if (material == null || material.isAir() || amount <= 0) {
                continue;
            }
            ItemStack stack = new ItemStack(material, amount);
            result.addAll(splitStack(stack));
        }
        return result;
    }

    private List<ItemStack> normalizeRewardStacks(List<ItemStack> rewards) {
        List<ItemStack> result = new ArrayList<>();
        if (rewards == null || rewards.isEmpty()) {
            return result;
        }
        for (ItemStack reward : rewards) {
            result.addAll(splitStack(reward));
        }
        return result;
    }

    private List<ItemStack> splitStack(ItemStack reward) {
        List<ItemStack> result = new ArrayList<>();
        if (reward == null || reward.getType().isAir() || reward.getAmount() <= 0) {
            return result;
        }
        int remaining = reward.getAmount();
        int max = Math.max(1, reward.getMaxStackSize());
        while (remaining > 0) {
            int part = Math.min(max, remaining);
            ItemStack clone = reward.clone();
            clone.setAmount(part);
            result.add(clone);
            remaining -= part;
        }
        return result;
    }

    private SelectionResult selectRedeemableEntries(Player player, List<PendingRewardClaim> entries) {
        if (player == null || !player.isOnline()) {
            return new SelectionResult(List.of());
        }
        ItemStack[] simulated = cloneSlots(player.getInventory().getContents());
        List<PendingRewardClaim> selected = new ArrayList<>();
        for (PendingRewardClaim entry : entries) {
            ItemStack stack = entry.stack() == null ? null : entry.stack().clone();
            if (!canFitInventoryStack(simulated, stack)) {
                continue;
            }
            moveIntoInventory(simulated, stack);
            selected.add(entry);
        }
        return new SelectionResult(selected);
    }

    private DeliveryResult addStacksToPlayer(Player player, List<PendingRewardClaim> selected) {
        if (player == null || !player.isOnline() || selected == null || selected.isEmpty()) {
            return new DeliveryResult(List.of(), 0);
        }
        List<ItemStack> leftovers = new ArrayList<>();
        int deliveredItems = 0;
        for (PendingRewardClaim claim : selected) {
            ItemStack stack = claim.stack() == null ? null : claim.stack().clone();
            if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
                continue;
            }
            Map<Integer, ItemStack> failed = player.getInventory().addItem(stack);
            int failedAmount = failed.values().stream()
                    .filter(item -> item != null && !item.getType().isAir())
                    .mapToInt(ItemStack::getAmount)
                    .sum();
            deliveredItems += Math.max(0, stack.getAmount() - failedAmount);
            leftovers.addAll(normalizeRewardStacks(new ArrayList<>(failed.values())));
        }
        return new DeliveryResult(leftovers, deliveredItems);
    }

    private ItemStack[] loadSlotsFromDb(int townId) {
        ItemStack[] slots = new ItemStack[settings.slotCount()];
        Map<Integer, String> encoded = repository.loadVaultSlots(townId);
        for (Map.Entry<Integer, String> entry : encoded.entrySet()) {
            int index = entry.getKey();
            if (index < 0 || index >= slots.length) {
                continue;
            }
            try {
                slots[index] = ItemStackCodec.decode(entry.getValue());
            } catch (Exception ignored) {
                slots[index] = null;
            }
        }
        return slots;
    }

    private UUID resolveMayorId(int townId) {
        return huskTownsApiHook.getTownById(townId)
                .map(net.william278.husktowns.town.Town::getMayor)
                .orElse(new UUID(0L, 0L));
    }

    private static ItemStack[] cloneSlots(ItemStack[] source) {
        if (source == null) {
            return new ItemStack[0];
        }
        ItemStack[] clone = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            clone[i] = source[i] == null ? null : source[i].clone();
        }
        return clone;
    }

    private void persistSlots(int townId, ItemStack[] slots) {
        Map<Integer, String> encoded = new HashMap<>();
        for (int i = 0; i < slots.length; i++) {
            ItemStack item = slots[i];
            if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
                continue;
            }
            encoded.put(i, ItemStackCodec.encode(item));
        }
        repository.replaceVaultSlots(townId, encoded);
    }

    private static int countMaterial(ItemStack[] slots, Material material) {
        if (slots == null || material == null || material.isAir()) {
            return 0;
        }
        int total = 0;
        for (ItemStack item : slots) {
            if (item == null || item.getType() != material || item.getAmount() <= 0) {
                continue;
            }
            total += item.getAmount();
        }
        return total;
    }

    private static boolean canFitEntireStack(ItemStack[] slots, ItemStack source, int maxStackSize) {
        if (slots == null || source == null || source.getType().isAir() || source.getAmount() <= 0) {
            return false;
        }
        int remaining = source.getAmount();
        for (ItemStack existing : slots) {
            if (existing == null || existing.getType().isAir()) {
                continue;
            }
            if (!existing.isSimilar(source)) {
                continue;
            }
            int targetMax = Math.max(1, Math.min(maxStackSize, existing.getMaxStackSize()));
            int space = Math.max(0, targetMax - existing.getAmount());
            remaining -= space;
            if (remaining <= 0) {
                return true;
            }
        }
        int targetMax = Math.max(1, Math.min(maxStackSize, source.getMaxStackSize()));
        for (ItemStack existing : slots) {
            if (existing != null && !existing.getType().isAir()) {
                continue;
            }
            remaining -= targetMax;
            if (remaining <= 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean canFitInventoryStack(ItemStack[] slots, ItemStack source) {
        if (slots == null || source == null || source.getType().isAir() || source.getAmount() <= 0) {
            return false;
        }
        int remaining = source.getAmount();
        for (ItemStack existing : slots) {
            if (existing == null || existing.getType().isAir()) {
                continue;
            }
            if (!existing.isSimilar(source)) {
                continue;
            }
            int space = Math.max(0, existing.getMaxStackSize() - existing.getAmount());
            remaining -= space;
            if (remaining <= 0) {
                return true;
            }
        }
        for (ItemStack existing : slots) {
            if (existing != null && !existing.getType().isAir()) {
                continue;
            }
            remaining -= Math.max(1, source.getMaxStackSize());
            if (remaining <= 0) {
                return true;
            }
        }
        return false;
    }

    private static void moveIntoInventory(ItemStack[] slots, ItemStack source) {
        int remaining = source.getAmount();
        for (int i = 0; i < slots.length && remaining > 0; i++) {
            ItemStack existing = slots[i];
            if (existing == null || existing.getType().isAir() || !existing.isSimilar(source)) {
                continue;
            }
            int space = Math.max(0, existing.getMaxStackSize() - existing.getAmount());
            if (space <= 0) {
                continue;
            }
            int add = Math.min(space, remaining);
            existing.setAmount(existing.getAmount() + add);
            remaining -= add;
        }
        for (int i = 0; i < slots.length && remaining > 0; i++) {
            if (slots[i] != null && !slots[i].getType().isAir()) {
                continue;
            }
            int add = Math.min(Math.max(1, source.getMaxStackSize()), remaining);
            ItemStack clone = source.clone();
            clone.setAmount(add);
            slots[i] = clone;
            remaining -= add;
        }
        source.setAmount(remaining);
    }

    private static int moveIntoVault(ItemStack[] slots, ItemStack source, int maxStackSize) {
        int moved = 0;
        int remaining = source.getAmount();
        if (remaining <= 0) {
            return 0;
        }
        for (int i = 0; i < slots.length && remaining > 0; i++) {
            ItemStack existing = slots[i];
            if (existing == null || existing.getType().isAir()) {
                continue;
            }
            if (!existing.isSimilar(source)) {
                continue;
            }
            int targetMax = Math.max(1, Math.min(maxStackSize, existing.getMaxStackSize()));
            int space = targetMax - existing.getAmount();
            if (space <= 0) {
                continue;
            }
            int add = Math.min(space, remaining);
            existing.setAmount(existing.getAmount() + add);
            remaining -= add;
            moved += add;
        }
        for (int i = 0; i < slots.length && remaining > 0; i++) {
            if (slots[i] != null && !slots[i].getType().isAir()) {
                continue;
            }
            int targetMax = Math.max(1, Math.min(maxStackSize, source.getMaxStackSize()));
            int add = Math.min(targetMax, remaining);
            ItemStack clone = source.clone();
            clone.setAmount(add);
            slots[i] = clone;
            remaining -= add;
            moved += add;
        }
        source.setAmount(remaining);
        return moved;
    }

    private <T> CompletableFuture<T> supplySync(java.util.concurrent.Callable<T> callable) {
        CompletableFuture<T> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                future.complete(callable.call());
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
        return future;
    }

    private record SelectionResult(List<PendingRewardClaim> selected) {
    }

    private record DeliveryResult(List<ItemStack> leftovers, int deliveredItems) {
    }
}
