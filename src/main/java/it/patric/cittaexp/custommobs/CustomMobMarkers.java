package it.patric.cittaexp.custommobs;

import java.util.Optional;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class CustomMobMarkers {

    private final NamespacedKey mobIdKey;
    private final NamespacedKey spawnSourceKey;
    private final NamespacedKey ownerTownIdKey;
    private final NamespacedKey sessionIdKey;
    private final NamespacedKey encounterIdKey;
    private final NamespacedKey waveIdKey;
    private final NamespacedKey defenseTierKey;
    private final NamespacedKey variantIdKey;
    private final NamespacedKey traitIdKey;
    private final NamespacedKey bossFlagKey;
    private final NamespacedKey phaseIdKey;
    private final NamespacedKey summonedKey;
    private final NamespacedKey parentMobIdKey;
    private final NamespacedKey projectileOwnerUuidKey;

    public CustomMobMarkers(Plugin plugin) {
        this.mobIdKey = new NamespacedKey(plugin, "mob_id");
        this.spawnSourceKey = new NamespacedKey(plugin, "spawn_source");
        this.ownerTownIdKey = new NamespacedKey(plugin, "owner_town_id");
        this.sessionIdKey = new NamespacedKey(plugin, "session_id");
        this.encounterIdKey = new NamespacedKey(plugin, "encounter_id");
        this.waveIdKey = new NamespacedKey(plugin, "wave_id");
        this.defenseTierKey = new NamespacedKey(plugin, "defense_tier");
        this.variantIdKey = new NamespacedKey(plugin, "variant_id");
        this.traitIdKey = new NamespacedKey(plugin, "trait_id");
        this.bossFlagKey = new NamespacedKey(plugin, "boss_flag");
        this.phaseIdKey = new NamespacedKey(plugin, "phase_id");
        this.summonedKey = new NamespacedKey(plugin, "summoned");
        this.parentMobIdKey = new NamespacedKey(plugin, "parent_mob_uuid");
        this.projectileOwnerUuidKey = new NamespacedKey(plugin, "projectile_owner_uuid");
    }

    public void apply(PersistentDataContainer container, ResolvedCustomMobSpec spec, CustomMobSpawnContext context) {
        container.set(mobIdKey, PersistentDataType.STRING, spec.id());
        if (context.spawnSource() != null && !context.spawnSource().isBlank()) {
            container.set(spawnSourceKey, PersistentDataType.STRING, context.spawnSource());
        }
        if (context.ownerTownId() != null) {
            container.set(ownerTownIdKey, PersistentDataType.INTEGER, context.ownerTownId());
        }
        if (context.sessionId() != null && !context.sessionId().isBlank()) {
            container.set(sessionIdKey, PersistentDataType.STRING, context.sessionId());
        }
        if (context.encounterId() != null && !context.encounterId().isBlank()) {
            container.set(encounterIdKey, PersistentDataType.STRING, context.encounterId());
        }
        if (context.waveId() != null && !context.waveId().isBlank()) {
            container.set(waveIdKey, PersistentDataType.STRING, context.waveId());
        }
        if (context.defenseTier() != null && !context.defenseTier().isBlank()) {
            container.set(defenseTierKey, PersistentDataType.STRING, context.defenseTier());
        }
        if (spec.selectedVariantId() != null && !spec.selectedVariantId().isBlank()) {
            container.set(variantIdKey, PersistentDataType.STRING, spec.selectedVariantId());
        }
        if (spec.selectedTraitId() != null && !spec.selectedTraitId().isBlank()) {
            container.set(traitIdKey, PersistentDataType.STRING, spec.selectedTraitId());
        }
        if (spec.boss()) {
            container.set(bossFlagKey, PersistentDataType.BYTE, (byte) 1);
        }
        if (context.summoned()) {
            container.set(summonedKey, PersistentDataType.BYTE, (byte) 1);
        }
        if (context.parentMobUuid() != null) {
            container.set(parentMobIdKey, PersistentDataType.STRING, context.parentMobUuid().toString());
        }
    }

    public void updatePhase(Entity entity, String phaseId) {
        if (entity == null) {
            return;
        }
        PersistentDataContainer container = entity.getPersistentDataContainer();
        if (phaseId == null || phaseId.isBlank()) {
            container.remove(phaseIdKey);
            return;
        }
        container.set(phaseIdKey, PersistentDataType.STRING, phaseId.trim());
    }

    public void applyProjectileOwner(PersistentDataContainer container, UUID ownerUuid) {
        if (container == null) {
            return;
        }
        if (ownerUuid == null) {
            container.remove(projectileOwnerUuidKey);
            return;
        }
        container.set(projectileOwnerUuidKey, PersistentDataType.STRING, ownerUuid.toString());
    }

    public Optional<String> mobId(Entity entity) {
        return getString(entity, mobIdKey);
    }

    public Optional<String> variantId(Entity entity) {
        return getString(entity, variantIdKey);
    }

    public Optional<String> traitId(Entity entity) {
        return getString(entity, traitIdKey);
    }

    public Optional<String> spawnSource(Entity entity) {
        return getString(entity, spawnSourceKey);
    }

    public Optional<Integer> ownerTownId(Entity entity) {
        return Optional.ofNullable(entity.getPersistentDataContainer().get(ownerTownIdKey, PersistentDataType.INTEGER));
    }

    public Optional<String> waveId(Entity entity) {
        return getString(entity, waveIdKey);
    }

    public boolean isSummoned(Entity entity) {
        Byte value = entity.getPersistentDataContainer().get(summonedKey, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    public Optional<UUID> parentMobUuid(Entity entity) {
        return getString(entity, parentMobIdKey).map(UUID::fromString);
    }

    public Optional<String> sessionId(Entity entity) {
        return getString(entity, sessionIdKey);
    }

    public Optional<String> encounterId(Entity entity) {
        return getString(entity, encounterIdKey);
    }

    public Optional<String> defenseTier(Entity entity) {
        return getString(entity, defenseTierKey);
    }

    public Optional<String> phaseId(Entity entity) {
        return getString(entity, phaseIdKey);
    }

    public boolean isBoss(Entity entity) {
        Byte value = entity.getPersistentDataContainer().get(bossFlagKey, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    public NamespacedKey mobIdKey() {
        return mobIdKey;
    }

    public NamespacedKey spawnSourceKey() {
        return spawnSourceKey;
    }

    public NamespacedKey ownerTownIdKey() {
        return ownerTownIdKey;
    }

    public NamespacedKey waveIdKey() {
        return waveIdKey;
    }

    private Optional<String> getString(Entity entity, NamespacedKey key) {
        return Optional.ofNullable(entity.getPersistentDataContainer().get(key, PersistentDataType.STRING));
    }
}
