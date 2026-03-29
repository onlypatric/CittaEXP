package it.patric.cittaexp.integration.husktowns;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.william278.husktowns.HuskTowns;
import net.william278.husktowns.api.BukkitHuskTownsAPI;
import net.william278.husktowns.api.HuskTownsAPI;
import net.william278.husktowns.claim.Claim;
import net.william278.husktowns.claim.ClaimWorld;
import net.william278.husktowns.claim.ChunkTrustPreset;
import net.william278.husktowns.claim.Chunk;
import net.william278.husktowns.claim.Position;
import net.william278.husktowns.claim.TownClaim;
import net.william278.husktowns.manager.WarManager;
import net.william278.husktowns.town.Member;
import net.william278.husktowns.town.Privilege;
import net.william278.husktowns.town.Spawn;
import net.william278.husktowns.town.Town;
import net.william278.husktowns.user.CommandUser;
import net.william278.husktowns.user.OnlineUser;
import net.william278.husktowns.war.Declaration;
import net.william278.husktowns.war.War;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.jetbrains.annotations.Nullable;

public final class HuskTownsApiHook {

    private final BukkitHuskTownsAPI huskTownsApi;
    private final HuskTowns huskTownsPlugin;

    public HuskTownsApiHook() throws HuskTownsAPI.NotRegisteredException {
        this.huskTownsApi = BukkitHuskTownsAPI.getInstance();
        this.huskTownsPlugin = resolvePluginInstance(this.huskTownsApi);
    }

    public BukkitHuskTownsAPI api() {
        return huskTownsApi;
    }

    public HuskTowns plugin() {
        return huskTownsPlugin;
    }

    public boolean reloadPlugin() {
        try {
            huskTownsPlugin.reload();
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public Optional<TownClaim> getClaimAt(Location location) {
        return huskTownsApi.getClaimAt(location);
    }

    public Optional<TownClaim> getClaimAt(org.bukkit.Chunk chunk) {
        return huskTownsApi.getClaimAt(chunk);
    }

    public Optional<ClaimWorld> getClaimWorld(World world) {
        return huskTownsApi.getClaimWorld(world);
    }

    public List<TownClaim> getTownClaims(World world, int townId) {
        return getClaimWorld(world)
                .map(claimWorld -> claimWorld.getTownClaims(townId, huskTownsPlugin))
                .orElse(List.of());
    }

    public Map<UUID, Integer> getTownMembers(int townId) {
        return getTownById(townId).map(Town::getMembers).orElse(Map.of());
    }

    public java.util.List<Town> getTowns() {
        return huskTownsApi.getTowns();
    }

    public java.util.List<Town> getPlayableTowns() {
        int adminTownId = huskTownsPlugin.getAdminTown().getId();
        return huskTownsApi.getTowns().stream()
                .filter(town -> town.getId() != adminTownId)
                .toList();
    }

    public Optional<Member> getUserTown(Player player) {
        return huskTownsApi.getUserTown(player);
    }

    public boolean isOperationAllowedBlockBreak(Player player, Location location) {
        Object onlineUser = huskTownsApi.getOnlineUser(player);
        Object position = huskTownsApi.getPosition(location);
        try {
            for (Method method : huskTownsApi.getClass().getMethods()) {
                if (!method.getName().equals("isOperationAllowed") || method.getParameterCount() != 3) {
                    continue;
                }
                Class<?>[] params = method.getParameterTypes();
                if (!params[0].isInstance(onlineUser) || !params[2].isInstance(position)) {
                    continue;
                }
                Field field = params[1].getField("BLOCK_BREAK");
                Object blockBreak = field.get(null);
                Object result = method.invoke(huskTownsApi, onlineUser, blockBreak, position);
                if (result instanceof Boolean allowed) {
                    return allowed;
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // Fallback handled below.
        }
        return true;
    }

    public boolean hasPrivilege(Player player, Privilege privilege) {
        return huskTownsApi.isPrivilegeAllowed(privilege, huskTownsApi.getOnlineUser(player));
    }

    public void deleteClaimAt(Player player, Location location) {
        huskTownsApi.deleteClaimAt(player, location);
    }

    public void deleteClaimAt(Player player, org.bukkit.Chunk chunk) {
        huskTownsApi.deleteClaimAt(player, chunk);
    }

    public void setClaimTypeAt(Player player, Location location, Claim.Type type) {
        huskTownsApi.editClaimAt(location, townClaim -> townClaim.claim().setType(type));
    }

    public void setClaimTypeAt(Player player, org.bukkit.Chunk chunk, org.bukkit.World world, Claim.Type type) {
        huskTownsApi.editClaimAt(
                Chunk.at(chunk.getX(), chunk.getZ()),
                huskTownsApi.getWorld(world),
                townClaim -> townClaim.claim().setType(type)
        );
    }

    public void createClaimAt(Player player, org.bukkit.Chunk chunk, World world, boolean showMap) {
        huskTownsPlugin.getManager().claims().createClaim(
                huskTownsApi.getOnlineUser(player),
                huskTownsApi.getWorld(world),
                Chunk.at(chunk.getX(), chunk.getZ()),
                showMap
        );
    }

    public void setChunkTrust(Player player, org.bukkit.Chunk chunk, World world, String targetName, ChunkTrustPreset preset) {
        huskTownsPlugin.getManager().claims().setChunkTrust(
                huskTownsApi.getOnlineUser(player),
                huskTownsApi.getWorld(world),
                Chunk.at(chunk.getX(), chunk.getZ()),
                targetName,
                preset
        );
    }

    public void removeChunkTrust(Player player, org.bukkit.Chunk chunk, World world, String targetName) {
        huskTownsPlugin.getManager().claims().removeChunkTrust(
                huskTownsApi.getOnlineUser(player),
                huskTownsApi.getWorld(world),
                Chunk.at(chunk.getX(), chunk.getZ()),
                targetName
        );
    }

    public Map<UUID, ChunkTrustPreset> listChunkTrust(org.bukkit.Chunk chunk, World world) {
        return huskTownsPlugin.getManager().claims().listChunkTrust(
                huskTownsApi.getWorld(world),
                Chunk.at(chunk.getX(), chunk.getZ())
        );
    }

    public void setTownSpawn(Player player, int townId, Location location) {
        String fallbackServerId = huskTownsPlugin.getServerName() == null || huskTownsPlugin.getServerName().isBlank()
                ? "default"
                : huskTownsPlugin.getServerName();
        huskTownsApi.editTown(huskTownsApi.getOnlineUser(player), townId, town -> {
            String serverId = town.getSpawn().map(Spawn::getServer).orElse(fallbackServerId);
            town.setSpawn(Spawn.of(huskTownsApi.getPosition(location), serverId));
        });
    }

    public void teleportToTownSpawn(Player player, @Nullable String townName) {
        huskTownsPlugin.getManager().towns().teleportToTownSpawn(huskTownsApi.getOnlineUser(player), townName);
    }

    public void inviteMember(Player player, String targetName) {
        huskTownsPlugin.getManager().towns().inviteMember(huskTownsApi.getOnlineUser(player), targetName);
    }

    public void handleInviteReply(Player player, boolean accepted, @Nullable String inviterName) {
        huskTownsPlugin.getManager().towns().handleInviteReply(huskTownsApi.getOnlineUser(player), accepted, inviterName);
    }

    public void leaveTown(Player player) {
        huskTownsPlugin.getManager().towns().leaveTown(huskTownsApi.getOnlineUser(player));
    }

    public void disbandTown(Player player, boolean confirmed) {
        huskTownsPlugin.getManager().towns().deleteTownConfirm(huskTownsApi.getOnlineUser(player), confirmed);
    }

    public void depositTownMoney(Player player, BigDecimal amount) {
        huskTownsPlugin.getManager().towns().depositMoney(huskTownsApi.getOnlineUser(player), amount);
    }

    public void withdrawTownMoney(Player player, BigDecimal amount) {
        huskTownsPlugin.getManager().towns().withdrawMoney(huskTownsApi.getOnlineUser(player), amount);
    }

    public void promoteMember(Player player, String memberName) {
        huskTownsPlugin.getManager().towns().promoteMember(huskTownsApi.getOnlineUser(player), memberName);
    }

    public void demoteMember(Player player, String memberName) {
        huskTownsPlugin.getManager().towns().demoteMember(huskTownsApi.getOnlineUser(player), memberName);
    }

    public void evictMember(Player player, String memberName) {
        huskTownsPlugin.getManager().towns().removeMember(huskTownsApi.getOnlineUser(player), memberName);
    }

    public void transferTownOwnership(Player player, String memberName) {
        huskTownsPlugin.getManager().towns().transferOwnership(huskTownsApi.getOnlineUser(player), memberName);
    }

    public void sendTownChatMessage(Player player, @Nullable String message) {
        huskTownsPlugin.getManager().towns().sendChatMessage(huskTownsApi.getOnlineUser(player), message);
    }

    public void setSpawnPrivacy(Player player, boolean isPublic) {
        huskTownsPlugin.getManager().towns().setSpawnPrivacy(huskTownsApi.getOnlineUser(player), isPublic);
    }

    public void editTown(Player player, int townId, Consumer<Town> editor) {
        huskTownsApi.editTown(huskTownsApi.getOnlineUser(player), townId, editor);
    }

    public void editTown(UUID actorId, int townId, Consumer<Town> editor) {
        huskTownsApi.editTown(huskTownsApi.getOnlineUser(actorId), townId, editor);
    }

    public void updateTownDirect(Town town) {
        huskTownsPlugin.updateTown(town);
        huskTownsPlugin.getDatabase().updateTown(town);
    }

    public void createAdminClaim(Player player, org.bukkit.Chunk chunk, World world, boolean showMap) {
        huskTownsPlugin.getManager().admin().createAdminClaim(
                huskTownsApi.getOnlineUser(player),
                huskTownsApi.getWorld(world),
                Chunk.at(chunk.getX(), chunk.getZ()),
                showMap
        );
    }

    public void deleteAdminClaim(Player player, org.bukkit.Chunk chunk, World world, boolean showMap) {
        huskTownsPlugin.getManager().admin().deleteClaim(
                huskTownsApi.getOnlineUser(player),
                huskTownsApi.getWorld(world),
                Chunk.at(chunk.getX(), chunk.getZ()),
                showMap
        );
    }

    public void deleteAllTownClaimsAdmin(Player player, String townName) {
        huskTownsPlugin.getManager().admin().deleteAllClaims(huskTownsApi.getOnlineUser(player), townName);
    }

    public void deleteTownAdmin(Player player, String townName) {
        huskTownsPlugin.getManager().admin().deleteTown(huskTownsApi.getOnlineUser(player), townName);
    }

    public void takeOverTownAdmin(Player player, String townName) {
        huskTownsPlugin.getManager().admin().takeOverTown(huskTownsApi.getOnlineUser(player), townName);
    }

    public void setTownBalanceAdmin(CommandUser user, String townName, BigDecimal amount) {
        huskTownsPlugin.getManager().admin().setTownBalance(user, townName, amount);
    }

    public void changeTownBalanceAdmin(CommandUser user, String townName, BigDecimal amount) {
        huskTownsPlugin.getManager().admin().changeTownBalance(user, townName, amount);
    }

    public void setTownLevelAdmin(Player player, String townName, int level) {
        huskTownsPlugin.getManager().admin().setTownLevel(huskTownsApi.getOnlineUser(player), townName, level);
    }

    public void setTownBonusAdmin(CommandUser user, String townName, Town.Bonus bonus, int amount) {
        huskTownsPlugin.getManager().admin().setTownBonus(user, townName, bonus, amount);
    }

    public void addTownBonusAdmin(CommandUser user, String townName, Town.Bonus bonus, int amount) {
        huskTownsPlugin.getManager().admin().addTownBonus(user, townName, bonus, amount);
    }

    public void removeTownBonusAdmin(CommandUser user, String townName, Town.Bonus bonus, int amount) {
        huskTownsPlugin.getManager().admin().removeTownBonus(user, townName, bonus, amount);
    }

    public void clearTownBonusAdmin(CommandUser user, String townName, Town.Bonus bonus) {
        huskTownsPlugin.getManager().admin().clearTownBonus(user, townName, bonus);
    }

    public void viewTownBonusAdmin(CommandUser user, String townName) {
        huskTownsPlugin.getManager().admin().viewTownBonus(user, townName);
    }

    public void listAdvancementsAdmin(CommandUser user, @Nullable String username) {
        huskTownsPlugin.getManager().admin().listAdvancements(user, username);
    }

    public void resetAdvancementsAdmin(CommandUser user, String username) {
        huskTownsPlugin.getManager().admin().resetAdvancements(user, username);
    }

    public Map<String, Boolean> getTownClaimRules(int townId, Claim.Type claimType) {
        Optional<Town> town = getTownById(townId);
        if (town.isEmpty()) {
            return Map.of();
        }
        net.william278.husktowns.claim.Rules rules = town.get().getRules().get(claimType);
        if (rules == null) {
            return Map.of();
        }
        return rules.getCalculatedFlags(huskTownsPlugin.getFlags()).entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().getName(), String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toMap(
                        entry -> entry.getKey().getName(),
                        Map.Entry::getValue,
                        (a, b) -> b,
                        LinkedHashMap::new
                ));
    }

    public boolean applyTownClaimRules(
            Player player,
            int townId,
            Claim.Type claimType,
            Map<String, Boolean> ruleValues
    ) {
        if (ruleValues == null || ruleValues.isEmpty()) {
            return false;
        }
        Optional<Town> town = getTownById(townId);
        if (town.isEmpty()) {
            return false;
        }
        try {
            huskTownsApi.editTown(huskTownsApi.getOnlineUser(player), townId, mutableTown -> {
                net.william278.husktowns.claim.Rules rules = mutableTown.getRules().get(claimType);
                if (rules == null) {
                    return;
                }
                ruleValues.forEach((flagId, value) ->
                        huskTownsApi.getFlag(flagId).ifPresent(flag -> rules.setFlag(flag, value)));
            });
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    public Component getClaimMapComponent(Location location) {
        return huskTownsApi.getClaimMapComponent(location);
    }

    public Optional<Town> getTownByName(String townName) {
        if (townName == null || townName.isBlank()) {
            return Optional.empty();
        }
        return huskTownsApi.getTown(townName.trim());
    }

    public Optional<Town> getTownById(int townId) {
        return huskTownsApi.getTown(townId);
    }

    public boolean isRelationsEnabled() {
        return huskTownsPlugin.getSettings().getTowns().getRelations().isEnabled();
    }

    public boolean warsEnabled() {
        return isRelationsEnabled() && warSettings().enabled();
    }

    public WarSettingsView warSettings() {
        var wars = huskTownsPlugin.getSettings().getTowns().getRelations().getWars();
        return new WarSettingsView(
                wars.isEnabled(),
                wars.getCooldown(),
                wars.getDeclarationExpiry(),
                wars.getMinimumWager(),
                wars.getRequiredOnlineMembership(),
                wars.getWarZoneRadius()
        );
    }

    public Optional<WarManager> warManager() {
        return huskTownsPlugin.getManager().wars();
    }

    public java.util.List<War> getActiveWars() {
        return warManager().map(WarManager::getActiveWars).map(List::copyOf).orElse(List.of());
    }

    public Optional<War> currentWarForTown(int townId) {
        return getTownById(townId).flatMap(Town::getCurrentWar);
    }

    public Optional<Declaration> pendingDeclarationForTown(int townId) {
        return getTownById(townId).flatMap(town -> warManager().flatMap(manager -> manager.getPendingDeclaration(town)));
    }

    public java.util.List<Declaration> getPendingDeclarations() {
        return warManager().map(WarManager::getPendingDeclarations).map(List::copyOf).orElse(List.of());
    }

    public boolean sendWarDeclaration(Player actor, String targetTownName, java.math.BigDecimal wager) {
        if (actor == null || targetTownName == null || targetTownName.isBlank() || wager == null) {
            return false;
        }
        Optional<WarManager> manager = warManager();
        if (manager.isEmpty()) {
            return false;
        }
        try {
            manager.get().sendWarDeclaration(huskTownsApi.getOnlineUser(actor), targetTownName.trim(), wager);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    public boolean acceptWarDeclaration(Player actor) {
        if (actor == null) {
            return false;
        }
        Optional<WarManager> manager = warManager();
        if (manager.isEmpty()) {
            return false;
        }
        try {
            manager.get().acceptWarDeclaration(huskTownsApi.getOnlineUser(actor));
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    public boolean surrenderWar(Player actor) {
        if (actor == null) {
            return false;
        }
        Optional<WarManager> manager = warManager();
        if (manager.isEmpty()) {
            return false;
        }
        try {
            manager.get().surrenderWar(huskTownsApi.getOnlineUser(actor));
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    public boolean adjustTownBalance(int townId, BigDecimal delta) {
        if (delta == null) {
            return false;
        }
        BigDecimal safeDelta = delta.setScale(2, RoundingMode.HALF_UP);
        if (safeDelta.compareTo(BigDecimal.ZERO) == 0) {
            return true;
        }
        Optional<Town> target = getTownById(townId);
        if (target.isEmpty()) {
            return false;
        }
        Town town = target.get();
        OnlineUser editor = huskTownsPlugin.getOnlineUsers().stream().findAny().orElse(null);
        if (editor != null) {
            try {
                huskTownsPlugin.getManager().editTown(editor, town, updated ->
                        updated.setMoney(updated.getMoney().add(safeDelta).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)));
                return true;
            } catch (RuntimeException ignored) {
                // Fallback below.
            }
        }
        town.setMoney(town.getMoney().add(safeDelta).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
        huskTownsPlugin.updateTown(town);
        huskTownsPlugin.getDatabase().updateTown(town);
        return true;
    }

    public CompletableFuture<Boolean> forceTownRelationBilateral(
            UUID actorId,
            int sourceTownId,
            int targetTownId,
            Town.Relation relation
    ) {
        Optional<Town> sourceTown = getTownById(sourceTownId);
        Optional<Town> targetTown = getTownById(targetTownId);
        if (sourceTown.isEmpty() || targetTown.isEmpty() || relation == null) {
            return CompletableFuture.completedFuture(false);
        }
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        try {
            huskTownsPlugin.getManager().editTown(
                    huskTownsApi.getOnlineUser(actorId),
                    sourceTown.get(),
                    town -> town.setRelationWith(targetTown.get(), relation),
                    updatedSource -> huskTownsPlugin.getManager().editTown(
                            huskTownsApi.getOnlineUser(actorId),
                            targetTown.get(),
                            town -> town.setRelationWith(updatedSource, relation),
                            updatedTarget -> future.complete(true)
                    )
            );
        } catch (RuntimeException ex) {
            future.complete(false);
        }
        return future;
    }

    public Optional<Location> toLocation(Position position) {
        if (position == null || position.getWorld() == null) {
            return Optional.empty();
        }
        World world = pluginWorld(position.getWorld().getUuid(), position.getWorld().getName());
        if (world == null) {
            return Optional.empty();
        }
        return Optional.of(new Location(world, position.getX(), position.getY(), position.getZ(), position.getYaw(), position.getPitch()));
    }

    public List<RelationView> getTownRelationsView(int townId) {
        Optional<Town> ownTown = getTownById(townId);
        if (ownTown.isEmpty()) {
            return List.of();
        }

        int adminTownId = huskTownsPlugin.getAdminTown().getId();
        Town source = ownTown.get();
        return huskTownsApi.getTowns().stream()
                .filter(town -> town.getId() != townId)
                .filter(town -> town.getId() != adminTownId)
                .map(town -> new RelationView(
                        town,
                        source.getRelationWith(town),
                        town.getRelationWith(source)
                ))
                .toList();
    }

    public boolean setTownRelation(Player actor, int ownTownId, String otherTownName, Town.Relation relation) {
        Optional<Member> member = getUserTown(actor);
        if (member.isEmpty() || member.get().town().getId() != ownTownId) {
            return false;
        }
        if (otherTownName == null || otherTownName.isBlank()) {
            return false;
        }
        Optional<Town> sourceTown = getTownById(ownTownId);
        Optional<Town> targetTown = getTownByName(otherTownName.trim());
        if (sourceTown.isEmpty() || targetTown.isEmpty() || relation == null) {
            return false;
        }
        if (sourceTown.get().getId() == targetTown.get().getId()) {
            return false;
        }
        try {
            huskTownsPlugin.getManager().towns().setTownRelation(
                    huskTownsApi.getOnlineUser(actor),
                    relation,
                    otherTownName.trim()
            );
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    public CompletableFuture<Optional<String>> getUsername(UUID userId) {
        return huskTownsApi.getUsername(userId);
    }

    public int normalizeMayorRoleWeights() {
        int mayorWeight = huskTownsPlugin.getRoles().getMayorRole().getWeight();
        int updated = 0;
        for (Town town : getTowns()) {
            UUID mayorId = town.getMayor();
            Integer currentWeight = town.getMembers().get(mayorId);
            if (currentWeight == null || currentWeight == mayorWeight) {
                continue;
            }
            town.getMembers().put(mayorId, mayorWeight);
            huskTownsPlugin.updateTown(town);
            huskTownsPlugin.getDatabase().updateTown(town);
            updated++;
        }
        return updated;
    }

    public Optional<Claim.Type> parseClaimType(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return Claim.Type.fromId(raw.toUpperCase(Locale.ROOT));
    }

    private HuskTowns resolvePluginInstance(BukkitHuskTownsAPI api) {
        try {
            Field pluginField = HuskTownsAPI.class.getDeclaredField("plugin");
            pluginField.setAccessible(true);
            Object raw = pluginField.get(api);
            if (raw instanceof HuskTowns huskPlugin) {
                return huskPlugin;
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Impossibile risolvere istanza HuskTowns da API", exception);
        }
        throw new IllegalStateException("Istanza HuskTowns non disponibile da API");
    }

    public record RelationView(
            Town town,
            Town.Relation relation,
            Town.Relation reciprocalRelation
    ) {
    }

    public record WarSettingsView(
            boolean enabled,
            long cooldownHours,
            long declarationExpiryMinutes,
            double minimumWager,
            double requiredOnlineMembership,
            long warZoneRadius
    ) {
    }

    private World pluginWorld(UUID worldId, String worldName) {
        World byId = worldId == null ? null : org.bukkit.Bukkit.getWorld(worldId);
        if (byId != null) {
            return byId;
        }
        return worldName == null || worldName.isBlank() ? null : org.bukkit.Bukkit.getWorld(worldName);
    }
}
