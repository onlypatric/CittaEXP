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
import net.kyori.adventure.text.Component;
import net.william278.husktowns.HuskTowns;
import net.william278.husktowns.api.BukkitHuskTownsAPI;
import net.william278.husktowns.api.HuskTownsAPI;
import net.william278.husktowns.claim.Claim;
import net.william278.husktowns.claim.Chunk;
import net.william278.husktowns.claim.TownClaim;
import net.william278.husktowns.town.Member;
import net.william278.husktowns.town.Privilege;
import net.william278.husktowns.town.Spawn;
import net.william278.husktowns.town.Town;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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

    public Optional<TownClaim> getClaimAt(Location location) {
        return huskTownsApi.getClaimAt(location);
    }

    public Optional<TownClaim> getClaimAt(org.bukkit.Chunk chunk) {
        return huskTownsApi.getClaimAt(chunk);
    }

    public Map<UUID, Integer> getTownMembers(int townId) {
        return getTownById(townId).map(Town::getMembers).orElse(Map.of());
    }

    public java.util.List<Town> getTowns() {
        return huskTownsApi.getTowns();
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

    public void setSpawnPrivacy(Player player, boolean isPublic) {
        huskTownsPlugin.getManager().towns().setSpawnPrivacy(huskTownsApi.getOnlineUser(player), isPublic);
    }

    public void editTown(Player player, int townId, Consumer<Town> editor) {
        huskTownsApi.editTown(huskTownsApi.getOnlineUser(player), townId, editor);
    }

    public void editTown(UUID actorId, int townId, Consumer<Town> editor) {
        huskTownsApi.editTown(huskTownsApi.getOnlineUser(actorId), townId, editor);
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

    public CompletableFuture<Optional<String>> getUsername(UUID userId) {
        return huskTownsApi.getUsername(userId);
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
}
