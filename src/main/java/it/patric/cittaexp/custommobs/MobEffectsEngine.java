package it.patric.cittaexp.custommobs;

import it.patric.cittaexp.utils.EntityGlowService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;

public final class MobEffectsEngine {

    private record CooldownKey(UUID entityId, String abilityId) {
    }

    public record RuntimeMobSnapshot(
            UUID entityId,
            String mobId,
            boolean boss,
            String displayName,
            int phaseNumber,
            String phaseId,
            String phaseLabel,
            String sessionId,
            String encounterId,
            String waveId,
            Integer waveNumber,
            String defenseTier,
            java.util.Set<String> tags,
            java.util.List<String> activeAbilityIds,
            java.util.Set<String> activeFlags
    ) {
    }

    private static final long IDLE_TICK_PERIOD = 20L;
    private static final double GUARDIAN_TARGET_DISTANCE = 128.0D;

    private final Plugin plugin;
    private volatile MobAbilityRegistry abilityRegistry;
    private final CustomMobMarkers markers;
    private final CustomMobRuntimeScheduler runtimeScheduler;
    private final ProjectileRuntimeService projectileRuntimeService;
    private final EntityGlowService glowService;
    private final ConcurrentMap<CooldownKey, Long> cooldownExpiryMillis;
    private final ConcurrentMap<UUID, RuntimeState> runtimeStates;
    private final ConcurrentMap<UUID, Integer> syntheticDamageDepth;
    private CustomMobSpawnService spawnService;
    private CustomMobRuntimeScheduler.TaskHandle idleTask;

    public MobEffectsEngine(
            Plugin plugin,
            MobAbilityRegistry abilityRegistry,
            CustomMobMarkers markers
    ) {
        this.plugin = plugin;
        this.abilityRegistry = abilityRegistry;
        this.markers = markers;
        this.runtimeScheduler = new CustomMobRuntimeScheduler(plugin);
        this.projectileRuntimeService = new ProjectileRuntimeService();
        this.glowService = new EntityGlowService(plugin);
        this.cooldownExpiryMillis = new ConcurrentHashMap<>();
        this.runtimeStates = new ConcurrentHashMap<>();
        this.syntheticDamageDepth = new ConcurrentHashMap<>();
    }

    public void attachSpawnService(CustomMobSpawnService spawnService) {
        this.spawnService = spawnService;
    }

    public void start() {
        if (idleTask != null) {
            idleTask.cancel();
        }
        idleTask = runtimeScheduler.scheduleRepeating(IDLE_TICK_PERIOD, IDLE_TICK_PERIOD, this::tickIdle);
    }

    public void registerSpawnedMob(
            LivingEntity entity,
            CustomMobSpec baseSpec,
            ResolvedCustomMobSpec resolvedSpec,
            CustomMobSpawnContext spawnContext
    ) {
        RuntimeState state = new RuntimeState(baseSpec, resolvedSpec, spawnContext);
        runtimeStates.put(entity.getUniqueId(), state);
        refreshPhase(entity, state, true);
        dispatch(entity, MobTriggerType.ON_SPAWN, buildContext(entity, null, entity, MobTriggerSourceKind.SELF), state);
    }

    public void onTarget(LivingEntity caster, LivingEntity target, Entity triggerSource) {
        RuntimeState state = runtimeStates.get(caster.getUniqueId());
        if (state == null) {
            return;
        }
        dispatch(caster, MobTriggerType.ON_TARGET, buildContext(caster, target, triggerSource, classifySource(triggerSource, state.spawnContext())), state);
    }

    public void onHit(LivingEntity caster, LivingEntity target, Entity triggerSource) {
        RuntimeState state = runtimeStates.get(caster.getUniqueId());
        if (state == null) {
            return;
        }
        dispatch(caster, MobTriggerType.ON_HIT, buildContext(caster, target, triggerSource, classifySource(triggerSource, state.spawnContext())), state);
    }

    public void onHurt(LivingEntity victim, Entity triggerSource) {
        RuntimeState state = runtimeStates.get(victim.getUniqueId());
        if (state == null) {
            return;
        }
        refreshPhase(victim, state, true);
        dispatch(victim, MobTriggerType.ON_HURT, buildContext(victim, extractLivingTarget(triggerSource), triggerSource, classifySource(triggerSource, state.spawnContext())), state);
    }

    public void onDeath(LivingEntity victim, Entity triggerSource) {
        RuntimeState state = runtimeStates.remove(victim.getUniqueId());
        if (state == null) {
            return;
        }
        projectileRuntimeService.cancelForOwner(victim.getUniqueId());
        dispatch(victim, MobTriggerType.ON_DEATH, buildContext(victim, extractLivingTarget(triggerSource), triggerSource, classifySource(triggerSource, state.spawnContext())), state);
        cooldownExpiryMillis.keySet().removeIf(key -> key.entityId().equals(victim.getUniqueId()));
    }

    private void tickIdle() {
        for (Map.Entry<UUID, RuntimeState> entry : new ArrayList<>(runtimeStates.entrySet())) {
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (!(entity instanceof LivingEntity living) || !living.isValid() || living.isDead()) {
                runtimeStates.remove(entry.getKey());
                continue;
            }
            refreshPhase(living, entry.getValue(), true);
            dispatch(living, MobTriggerType.ON_IDLE, buildContext(living, resolveCurrentTarget(living), living, MobTriggerSourceKind.SELF), entry.getValue());
        }
    }

    public Optional<CustomMobBossRuntimeSnapshot> bossSnapshot(UUID entityId) {
        RuntimeState state = runtimeStates.get(entityId);
        Entity entity = Bukkit.getEntity(entityId);
        if (state == null || !(entity instanceof LivingEntity living) || !state.resolvedSpec().boss()) {
            return Optional.empty();
        }
        refreshPhase(living, state, false);
        MobPhaseRule currentPhase = state.currentPhaseRule();
        String phaseId = currentPhase == null ? "phase_1" : currentPhase.phaseId();
        String phaseLabel = currentPhase == null ? "Phase I" : currentPhase.label();
        return Optional.of(new CustomMobBossRuntimeSnapshot(
                entityId,
                state.baseSpec().id(),
                state.effectiveDisplayName(),
                state.currentPhase(),
                phaseId,
                phaseLabel,
                state.resolvedSpec().bossbarSpec().enabled(),
                state.resolvedSpec().bossbarSpec(),
                state.resolvedSpec().localBroadcasts()
        ));
    }

    public boolean isSyntheticDamage(Entity entity) {
        return entity != null && syntheticDamageDepth.getOrDefault(entity.getUniqueId(), 0) > 0;
    }

    private void dispatch(LivingEntity caster, MobTriggerType trigger, MobCastContext context, RuntimeState state) {
        for (String abilityId : state.activeAbilityIds()) {
            Optional<MobAbilitySpec> abilityOptional = abilityRegistry.get(abilityId);
            if (abilityOptional.isEmpty()) {
                continue;
            }
            MobAbilitySpec ability = abilityOptional.get();
            if (ability.trigger() != trigger) {
                continue;
            }
            if (!isCooldownReady(caster.getUniqueId(), ability)) {
                continue;
            }
            if (!conditionsPass(caster, context, state, ability)) {
                continue;
            }
            Collection<LivingEntity> targets = resolveTargets(caster, context, ability.targeting(), state.spawnContext());
            if (targets.isEmpty() && requiresTarget(ability)) {
                continue;
            }
            runActions(caster, context, state, targets, ability.actions());
            armCooldown(caster.getUniqueId(), ability);
        }
    }

    private boolean conditionsPass(LivingEntity caster, MobCastContext context, RuntimeState state, MobAbilitySpec ability) {
        for (MobConditionSpec condition : ability.conditions()) {
            switch (condition) {
                case MobConditionSpec.HpBelowConditionSpec hpBelow -> {
                    double ratio = safeHealthRatio(caster);
                    if (ratio >= hpBelow.ratio()) {
                        return false;
                    }
                }
                case MobConditionSpec.HpAboveConditionSpec hpAbove -> {
                    double ratio = safeHealthRatio(caster);
                    if (ratio <= hpAbove.ratio()) {
                        return false;
                    }
                }
                case MobConditionSpec.TargetExistsConditionSpec ignored -> {
                    if (context.primaryTarget() == null || !context.primaryTarget().isValid() || context.primaryTarget().isDead()) {
                        return false;
                    }
                }
                case MobConditionSpec.HasTagConditionSpec hasTag -> {
                    if (!state.resolvedSpec().tags().contains(hasTag.tag().toLowerCase(Locale.ROOT))) {
                        return false;
                    }
                }
                case MobConditionSpec.WaveAtLeastConditionSpec wave -> {
                    if (context.waveNumber() == null || context.waveNumber() < wave.wave()) {
                        return false;
                    }
                }
                case MobConditionSpec.TriggerSourceConditionSpec source -> {
                    if (!source.allowedKinds().contains(context.triggerSourceKind())) {
                        return false;
                    }
                }
                case MobConditionSpec.HasTraitConditionSpec trait -> {
                    if (!trait.traitId().equalsIgnoreCase(state.resolvedSpec().selectedTraitId())) {
                        return false;
                    }
                }
                case MobConditionSpec.HasVariantConditionSpec variant -> {
                    if (!variant.variantId().equalsIgnoreCase(state.resolvedSpec().selectedVariantId())) {
                        return false;
                    }
                }
                case MobConditionSpec.TempFlagPresentConditionSpec flag -> {
                    if (!state.hasFlag(flag.flag())) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private void refreshPhase(LivingEntity entity, RuntimeState state, boolean runEntryActions) {
        if (state.resolvedSpec().phaseRules().isEmpty()) {
            if (state.currentPhase() == 0) {
                state.setCurrentPhase(1, null, state.resolvedSpec().abilityIds(), state.resolvedSpec().displayName());
                markers.updatePhase(entity, "phase_1");
            }
            return;
        }
        double ratio = safeHealthRatio(entity);
        MobPhaseRule targetPhase = state.resolvedSpec().phaseRules().getFirst();
        for (MobPhaseRule rule : state.resolvedSpec().phaseRules()) {
            if (ratio <= rule.hpBelowRatio()) {
                targetPhase = rule;
            }
        }
        if (state.currentPhase() > targetPhase.phaseNumber()) {
            return;
        }
        if (state.currentPhase() == targetPhase.phaseNumber()) {
            return;
        }
        List<String> activeAbilities = new ArrayList<>(state.resolvedSpec().abilityIds());
        String effectiveName = state.resolvedSpec().displayName();
        for (MobPhaseRule rule : state.resolvedSpec().phaseRules()) {
            if (rule.phaseNumber() > targetPhase.phaseNumber()) {
                break;
            }
            activeAbilities.removeAll(rule.removeAbilities());
            activeAbilities.addAll(rule.addAbilities());
            effectiveName = composeName(effectiveName, rule.namePrefix(), rule.nameSuffix());
        }
        state.setCurrentPhase(
                targetPhase.phaseNumber(),
                targetPhase,
                List.copyOf(new java.util.LinkedHashSet<>(activeAbilities)),
                effectiveName
        );
        markers.updatePhase(entity, targetPhase.phaseId());
        if (!effectiveName.isBlank()) {
            entity.customName(it.patric.cittaexp.text.MiniMessageHelper.parse(effectiveName));
            entity.setCustomNameVisible(true);
        }
        if (runEntryActions && !targetPhase.entryActions().isEmpty()) {
            MobCastContext phaseContext = buildContext(entity, resolveCurrentTarget(entity), entity, MobTriggerSourceKind.SELF);
            runActions(entity, phaseContext, state, List.of(entity), targetPhase.entryActions());
        }
    }

    private String composeName(String baseName, String prefix, String suffix) {
        String current = baseName == null ? "" : baseName.trim();
        String out = current;
        if (prefix != null && !prefix.isBlank()) {
            out = prefix.trim() + (out.isBlank() ? "" : " " + out);
        }
        if (suffix != null && !suffix.isBlank()) {
            out = out.isBlank() ? suffix.trim() : out + " " + suffix.trim();
        }
        return out.trim();
    }

    private Collection<LivingEntity> resolveTargets(
            LivingEntity caster,
            MobCastContext context,
            MobTargeterSpec targeting,
            CustomMobSpawnContext spawnContext
    ) {
        return switch (targeting.type()) {
            case SELF -> List.of(caster);
            case CURRENT_TARGET -> context.primaryTarget() == null ? List.of() : List.of(context.primaryTarget());
            case GUARDIAN -> resolveGuardianTarget(spawnContext);
            case NEAREST_PLAYER -> resolveNearestPlayer(caster.getLocation(), spawnContext, targeting.maxTargets());
            case PLAYERS_IN_RADIUS -> resolvePlayersInRadius(caster.getLocation(), targeting.radius(), targeting.maxTargets());
            case ENTITIES_IN_RADIUS -> resolveEntitiesInRadius(caster, targeting.radius(), targeting.maxTargets());
        };
    }

    private Collection<LivingEntity> resolveGuardianTarget(CustomMobSpawnContext spawnContext) {
        if (spawnContext.targetGuardianUuid() == null) {
            return List.of();
        }
        Entity entity = Bukkit.getEntity(spawnContext.targetGuardianUuid());
        if (entity instanceof LivingEntity living && living.isValid() && !living.isDead()) {
            return List.of(living);
        }
        return List.of();
    }

    private Collection<LivingEntity> resolveNearestPlayer(Location location, CustomMobSpawnContext spawnContext, int maxTargets) {
        List<Player> players = new ArrayList<>(location.getWorld().getPlayers());
        players.removeIf(player -> !player.isValid() || player.isDead());
        players.sort(Comparator.comparingDouble(player -> player.getLocation().distanceSquared(location)));
        if (spawnContext.targetGuardianUuid() != null) {
            players.removeIf(player -> player.getLocation().distanceSquared(location) > GUARDIAN_TARGET_DISTANCE * GUARDIAN_TARGET_DISTANCE);
        }
        if (players.isEmpty()) {
            return List.of();
        }
        return List.of(players.getFirst());
    }

    private Collection<LivingEntity> resolvePlayersInRadius(Location location, double radius, int maxTargets) {
        double radiusSquared = radius * radius;
        List<LivingEntity> players = new ArrayList<>();
        for (Player player : location.getWorld().getPlayers()) {
            if (!player.isValid() || player.isDead()) {
                continue;
            }
            if (player.getLocation().distanceSquared(location) <= radiusSquared) {
                players.add(player);
            }
        }
        return trimTargets(players, maxTargets);
    }

    private Collection<LivingEntity> resolveEntitiesInRadius(LivingEntity caster, double radius, int maxTargets) {
        double radiusSquared = radius * radius;
        List<LivingEntity> targets = new ArrayList<>();
        for (Entity nearby : caster.getNearbyEntities(radius, radius, radius)) {
            if (!(nearby instanceof LivingEntity living) || !living.isValid() || living.isDead()) {
                continue;
            }
            if (living.getUniqueId().equals(caster.getUniqueId())) {
                continue;
            }
            if (living.getLocation().distanceSquared(caster.getLocation()) <= radiusSquared) {
                targets.add(living);
            }
        }
        return trimTargets(targets, maxTargets);
    }

    private List<LivingEntity> trimTargets(List<LivingEntity> targets, int maxTargets) {
        if (maxTargets > 0 && targets.size() > maxTargets) {
            return List.copyOf(targets.subList(0, maxTargets));
        }
        return List.copyOf(targets);
    }

    private boolean requiresTarget(MobAbilitySpec ability) {
        return switch (ability.targeting().type()) {
            case SELF -> false;
            default -> true;
        };
    }

    private void runActions(
            LivingEntity caster,
            MobCastContext context,
            RuntimeState state,
            Collection<LivingEntity> targets,
            List<MobActionSpec> actions
    ) {
        for (MobActionSpec action : actions) {
            try {
                runAction(caster, context, state, targets, action);
            } catch (Exception exception) {
                plugin.getLogger().warning("[custom-mobs] action failed mob=" + state.baseSpec().id()
                        + " action=" + action.type()
                        + " reason=" + exception.getMessage());
            }
        }
    }

    private void runAction(
            LivingEntity caster,
            MobCastContext context,
            RuntimeState state,
            Collection<LivingEntity> targets,
            MobActionSpec action
    ) {
        switch (action) {
            case MobActionSpec.ParticlesActionSpec particles -> spawnParticles(caster, targets, particles);
            case MobActionSpec.SoundActionSpec sound -> playSound(caster, targets, sound);
            case MobActionSpec.ApplyPotionActionSpec potion -> applyPotion(caster, targets, potion);
            case MobActionSpec.DirectDamageActionSpec damage -> applyDamage(caster, targets, damage);
            case MobActionSpec.SummonActionSpec summon -> summonEntities(caster, context, summon);
            case MobActionSpec.LaunchProjectileActionSpec projectile -> launchProjectile(caster, context, state, projectile);
            case MobActionSpec.MotionActionSpec motion -> applyMotion(caster, targets, motion);
            case MobActionSpec.SetTempFlagActionSpec flag -> state.setFlag(flag.flag(), flag.durationTicks());
        }
    }

    private void spawnParticles(LivingEntity caster, Collection<LivingEntity> targets, MobActionSpec.ParticlesActionSpec particles) {
        if (targets.isEmpty()) {
            caster.getWorld().spawnParticle(particles.particle(), caster.getLocation().add(0.0D, 1.0D, 0.0D),
                    particles.count(), particles.offsetX(), particles.offsetY(), particles.offsetZ(), particles.extra());
            return;
        }
        for (LivingEntity target : targets) {
            target.getWorld().spawnParticle(particles.particle(), target.getLocation().add(0.0D, 1.0D, 0.0D),
                    particles.count(), particles.offsetX(), particles.offsetY(), particles.offsetZ(), particles.extra());
        }
    }

    private void playSound(LivingEntity caster, Collection<LivingEntity> targets, MobActionSpec.SoundActionSpec sound) {
        org.bukkit.Sound bukkitSound = CustomMobTypeResolver.parseSound(sound.soundKey());
        if (targets.isEmpty()) {
            caster.getWorld().playSound(caster.getLocation(), bukkitSound, sound.volume(), sound.pitch());
            return;
        }
        for (LivingEntity target : targets) {
            target.getWorld().playSound(target.getLocation(), bukkitSound, sound.volume(), sound.pitch());
        }
    }

    private void applyPotion(LivingEntity caster, Collection<LivingEntity> targets, MobActionSpec.ApplyPotionActionSpec potion) {
        org.bukkit.potion.PotionEffectType type = CustomMobTypeResolver.parsePotionEffectType(potion.effectKey());
        Collection<LivingEntity> actualTargets = targets.isEmpty() ? List.of(caster) : targets;
        for (LivingEntity target : actualTargets) {
            target.addPotionEffect(new PotionEffect(
                    type,
                    Math.max(1, potion.durationTicks()),
                    Math.max(0, potion.amplifier()),
                    potion.ambient(),
                    potion.particles(),
                    potion.icon()
            ));
            if (potion.fireTicks() > 0) {
                target.setFireTicks(Math.max(target.getFireTicks(), potion.fireTicks()));
            }
        }
    }

    private void applyDamage(LivingEntity caster, Collection<LivingEntity> targets, MobActionSpec.DirectDamageActionSpec damage) {
        for (LivingEntity target : targets) {
            UUID targetId = target.getUniqueId();
            syntheticDamageDepth.merge(targetId, 1, Integer::sum);
            try {
                target.damage(damage.amount(), caster);
            } finally {
                syntheticDamageDepth.compute(targetId, (ignored, depth) -> {
                    if (depth == null || depth <= 1) {
                        return null;
                    }
                    return depth - 1;
                });
            }
        }
    }

    private void summonEntities(LivingEntity caster, MobCastContext context, MobActionSpec.SummonActionSpec summon) {
        if (spawnService == null) {
            return;
        }
        for (int i = 0; i < summon.count(); i++) {
            Location spawnAt = caster.getLocation().clone().add(
                    randomSpread(summon.spreadRadius()),
                    0.0D,
                    randomSpread(summon.spreadRadius())
            );
            LivingEntity summoned = spawnFromToken(summon.spawnRef(), spawnAt, context.spawnContext().summonedChild(caster.getUniqueId()));
            if (summoned instanceof Mob mob && context.spawnContext().targetGuardianUuid() != null) {
                Entity guardian = Bukkit.getEntity(context.spawnContext().targetGuardianUuid());
                if (guardian instanceof LivingEntity livingGuardian) {
                    mob.setTarget(livingGuardian);
                }
            }
        }
    }

    private LivingEntity spawnFromToken(String spawnRef, Location location, CustomMobSpawnContext context) {
        String trimmed = spawnRef.trim();
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("custom:")) {
            return spawnService.spawn(trimmed.substring("custom:".length()), location, context);
        }
        EntityType entityType = CustomMobTypeResolver.parseEntityType(trimmed);
        Entity entity = location.getWorld().spawnEntity(location, entityType);
        if (!(entity instanceof LivingEntity living)) {
            entity.remove();
            throw new IllegalStateException("Summon spawn produced non-living entity for " + trimmed);
        }
        glowService.applyRedGlow(living);
        if (context.spawnAdapter() != null) {
            context.spawnAdapter().afterSpawn(living, context);
        }
        return living;
    }

    private void launchProjectile(
            LivingEntity caster,
            MobCastContext context,
            RuntimeState state,
            MobActionSpec.LaunchProjectileActionSpec projectile
    ) {
        Vector baseDirection = resolveProjectileDirection(caster, context.primaryTarget());
        for (int i = 0; i < Math.max(1, projectile.count()); i++) {
            Vector direction = withProjectileSpread(baseDirection, projectile.spread());
            launchProjectileSingle(caster, context, state, projectile, direction);
        }
    }

    private void launchProjectileSingle(
            LivingEntity caster,
            MobCastContext context,
            RuntimeState state,
            MobActionSpec.LaunchProjectileActionSpec projectile,
            Vector direction
    ) {
        Location origin = caster.getEyeLocation().clone();
        double step = Math.max(0.15D, projectile.speed());
        int maxSteps = Math.max(1, (int) Math.ceil(projectile.range() / step));
        ProjectileRuntimeService.RuntimeHandle tracked = projectileRuntimeService.create(caster.getUniqueId(), context.spawnContext());
        final CustomMobRuntimeScheduler.TaskHandle[] taskRef = new CustomMobRuntimeScheduler.TaskHandle[1];
        taskRef[0] = runtimeScheduler.scheduleRepeating(1L, 1L, new Runnable() {
            private int steps = 0;
            private Location current = origin.clone();

            @Override
            public void run() {
                if (!caster.isValid() || caster.isDead() || steps >= maxSteps) {
                    tracked.cancel();
                    return;
                }
                current.add(direction.clone().multiply(step));
                current.getWorld().spawnParticle(projectile.particle(), current, Math.max(1, projectile.particleCount()), 0.0D, 0.0D, 0.0D, 0.0D);
                LivingEntity hit = firstHit(caster, current, projectile.hitboxRadius());
                if (hit != null) {
                    MobCastContext hitContext = new MobCastContext(
                            caster,
                            hit,
                            caster,
                            MobTriggerSourceKind.PROJECTILE,
                            current.clone(),
                            context.spawnContext(),
                            context.ownerTownId(),
                            context.waveId(),
                            context.waveNumber(),
                            context.targetGuardianUuid(),
                            context.runtimeVars()
                    );
                    runActions(caster, hitContext, state, List.of(hit), projectile.hitActions());
                    tracked.cancel();
                    return;
                }
                steps++;
            }
        });
        tracked.bind(taskRef[0]);
    }

    private LivingEntity firstHit(LivingEntity caster, Location point, double radius) {
        double radiusSquared = radius * radius;
        for (Entity entity : caster.getWorld().getNearbyEntities(point, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living) || living.isDead() || !living.isValid()) {
                continue;
            }
            if (living.getUniqueId().equals(caster.getUniqueId())) {
                continue;
            }
            if (living.getLocation().distanceSquared(point) <= radiusSquared) {
                return living;
            }
        }
        return null;
    }

    private void applyMotion(LivingEntity caster, Collection<LivingEntity> targets, MobActionSpec.MotionActionSpec motion) {
        for (LivingEntity target : targets) {
            Vector direction = switch (motion.motionType()) {
                case KNOCKBACK -> target.getLocation().toVector().subtract(caster.getLocation().toVector()).normalize();
                case PULL -> caster.getLocation().toVector().subtract(target.getLocation().toVector()).normalize();
                case PUSH -> target.getLocation().getDirection().normalize();
                default -> new Vector(0.0D, 0.0D, 0.0D);
            };
            if (!Double.isFinite(direction.getX()) || !Double.isFinite(direction.getY()) || !Double.isFinite(direction.getZ())) {
                continue;
            }
            target.setVelocity(target.getVelocity().add(direction.multiply(motion.strength())));
        }
    }

    private Vector resolveProjectileDirection(LivingEntity caster, LivingEntity target) {
        Vector direction;
        if (target != null && target.isValid() && !target.isDead()) {
            direction = target.getEyeLocation().toVector().subtract(caster.getEyeLocation().toVector());
        } else {
            direction = caster.getEyeLocation().getDirection();
        }
        if (direction.lengthSquared() <= 1.0E-6D) {
            return new Vector(0.0D, 0.0D, 1.0D);
        }
        return direction.normalize();
    }

    private Vector withProjectileSpread(Vector baseDirection, double spread) {
        if (spread <= 0.0D) {
            return baseDirection.clone();
        }
        Vector adjusted = baseDirection.clone().add(new Vector(
                randomSpread(spread),
                randomSpread(spread),
                randomSpread(spread)
        ));
        if (adjusted.lengthSquared() <= 1.0E-6D) {
            return baseDirection.clone();
        }
        return adjusted.normalize();
    }

    private double randomSpread(double spreadRadius) {
        if (spreadRadius <= 0.0D) {
            return 0.0D;
        }
        return ThreadLocalRandom.current().nextDouble(-spreadRadius, spreadRadius);
    }

    private MobCastContext buildContext(LivingEntity caster, LivingEntity primaryTarget, Entity triggerSource, MobTriggerSourceKind sourceKind) {
        RuntimeState state = runtimeStates.get(caster.getUniqueId());
        CustomMobSpawnContext spawnContext = state == null ? CustomMobSpawnContext.simple("runtime") : state.spawnContext();
        return new MobCastContext(
                caster,
                primaryTarget,
                triggerSource,
                sourceKind,
                caster.getLocation().clone(),
                spawnContext,
                spawnContext.ownerTownId(),
                spawnContext.waveId(),
                spawnContext.waveNumber(),
                spawnContext.targetGuardianUuid(),
                Map.of()
        );
    }

    private LivingEntity resolveCurrentTarget(LivingEntity caster) {
        if (caster instanceof Mob mob) {
            return mob.getTarget();
        }
        return null;
    }

    private MobTriggerSourceKind classifySource(Entity source, CustomMobSpawnContext context) {
        if (source == null) {
            return MobTriggerSourceKind.NONE;
        }
        if (context.targetGuardianUuid() != null && source.getUniqueId().equals(context.targetGuardianUuid())) {
            return MobTriggerSourceKind.GUARDIAN;
        }
        if (source instanceof Player) {
            return MobTriggerSourceKind.PLAYER;
        }
        if (source instanceof Projectile projectile) {
            return MobTriggerSourceKind.PROJECTILE;
        }
        if (source instanceof LivingEntity) {
            return MobTriggerSourceKind.MOB;
        }
        return MobTriggerSourceKind.ENVIRONMENT;
    }

    private LivingEntity extractLivingTarget(Entity source) {
        if (source instanceof LivingEntity livingEntity) {
            return livingEntity;
        }
        if (source instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity livingShooter) {
            return livingShooter;
        }
        return null;
    }

    private boolean isCooldownReady(UUID entityId, MobAbilitySpec ability) {
        if (ability.cooldownTicks() <= 0) {
            return true;
        }
        long now = System.currentTimeMillis();
        Long expiry = cooldownExpiryMillis.get(new CooldownKey(entityId, ability.id()));
        if (expiry == null) {
            return true;
        }
        if (expiry <= now) {
            cooldownExpiryMillis.remove(new CooldownKey(entityId, ability.id()), expiry);
            return true;
        }
        return false;
    }

    private void armCooldown(UUID entityId, MobAbilitySpec ability) {
        if (ability.cooldownTicks() <= 0) {
            return;
        }
        long expiry = System.currentTimeMillis() + (ability.cooldownTicks() * 50L);
        cooldownExpiryMillis.put(new CooldownKey(entityId, ability.id()), expiry);
    }

    private double safeHealthRatio(LivingEntity entity) {
        double maxHealth = entity.getAttribute(Attribute.MAX_HEALTH) == null ? entity.getMaxHealth() : entity.getAttribute(Attribute.MAX_HEALTH).getValue();
        if (maxHealth <= 0.0D) {
            return 1.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, entity.getHealth() / maxHealth));
    }

    public void clearRuntimeState() {
        if (idleTask != null) {
            idleTask.cancel();
            idleTask = null;
        }
        projectileRuntimeService.clear();
        cooldownExpiryMillis.clear();
        runtimeStates.clear();
    }

    public void cancelRuntimeForSession(String sessionId) {
        projectileRuntimeService.cancelForSession(sessionId);
    }

    public int activeRuntimeMobCount() {
        return runtimeStates.size();
    }

    public int activeProjectileCount() {
        return projectileRuntimeService.activeCount();
    }

    public List<ProjectileRuntimeService.ProjectileRuntimeSnapshot> projectileSnapshots() {
        return projectileRuntimeService.snapshots();
    }

    public Optional<RuntimeMobSnapshot> runtimeSnapshot(UUID entityId) {
        RuntimeState state = runtimeStates.get(entityId);
        if (state == null) {
            return Optional.empty();
        }
        return Optional.of(snapshot(entityId, state));
    }

    public List<RuntimeMobSnapshot> runtimeSnapshots() {
        List<RuntimeMobSnapshot> snapshots = new ArrayList<>();
        for (Map.Entry<UUID, RuntimeState> entry : runtimeStates.entrySet()) {
            snapshots.add(snapshot(entry.getKey(), entry.getValue()));
        }
        return List.copyOf(snapshots);
    }

    public Map<String, MobAbilitySpec> abilities() {
        return abilityRegistry.all();
    }

    public void reloadAbilityRegistry(MobAbilityRegistry abilityRegistry) {
        this.abilityRegistry = abilityRegistry;
    }

    private RuntimeMobSnapshot snapshot(UUID entityId, RuntimeState state) {
        String phaseId = state.currentPhaseRule() == null ? "phase_1" : state.currentPhaseRule().phaseId();
        String phaseLabel = state.currentPhaseRule() == null ? "Phase I" : state.currentPhaseRule().label();
        return new RuntimeMobSnapshot(
                entityId,
                state.baseSpec().id(),
                state.resolvedSpec().boss(),
                state.effectiveDisplayName(),
                Math.max(1, state.currentPhase()),
                phaseId,
                phaseLabel,
                state.spawnContext().sessionId(),
                state.spawnContext().encounterId(),
                state.spawnContext().waveId(),
                state.spawnContext().waveNumber(),
                state.spawnContext().defenseTier(),
                state.resolvedSpec().tags(),
                state.activeAbilityIds(),
                state.activeFlags()
        );
    }

    private static final class RuntimeState {
        private final CustomMobSpec baseSpec;
        private final ResolvedCustomMobSpec resolvedSpec;
        private final CustomMobSpawnContext spawnContext;
        private final ConcurrentMap<String, Long> tempFlags;
        private volatile int currentPhase;
        private volatile MobPhaseRule currentPhaseRule;
        private volatile List<String> activeAbilityIds;
        private volatile String effectiveDisplayName;

        private RuntimeState(CustomMobSpec baseSpec, ResolvedCustomMobSpec resolvedSpec, CustomMobSpawnContext spawnContext) {
            this.baseSpec = baseSpec;
            this.resolvedSpec = resolvedSpec;
            this.spawnContext = spawnContext;
            this.tempFlags = new ConcurrentHashMap<>();
            this.currentPhase = 0;
            this.currentPhaseRule = null;
            this.activeAbilityIds = resolvedSpec.abilityIds();
            this.effectiveDisplayName = resolvedSpec.displayName();
        }

        private CustomMobSpec baseSpec() {
            return baseSpec;
        }

        private ResolvedCustomMobSpec resolvedSpec() {
            return resolvedSpec;
        }

        private CustomMobSpawnContext spawnContext() {
            return spawnContext;
        }

        private int currentPhase() {
            return currentPhase;
        }

        private MobPhaseRule currentPhaseRule() {
            return currentPhaseRule;
        }

        private List<String> activeAbilityIds() {
            return activeAbilityIds;
        }

        private String effectiveDisplayName() {
            return effectiveDisplayName;
        }

        private void setCurrentPhase(int currentPhase, MobPhaseRule phaseRule, List<String> activeAbilityIds, String effectiveDisplayName) {
            this.currentPhase = currentPhase;
            this.currentPhaseRule = phaseRule;
            this.activeAbilityIds = activeAbilityIds;
            this.effectiveDisplayName = effectiveDisplayName == null ? "" : effectiveDisplayName;
        }

        private boolean hasFlag(String flag) {
            Long expiry = tempFlags.get(flag);
            if (expiry == null) {
                return false;
            }
            if (expiry <= System.currentTimeMillis()) {
                tempFlags.remove(flag, expiry);
                return false;
            }
            return true;
        }

        private void setFlag(String flag, int durationTicks) {
            tempFlags.put(flag, System.currentTimeMillis() + (Math.max(1, durationTicks) * 50L));
        }

        private java.util.Set<String> activeFlags() {
            java.util.Set<String> flags = new java.util.LinkedHashSet<>();
            for (String flag : tempFlags.keySet()) {
                if (hasFlag(flag)) {
                    flags.add(flag);
                }
            }
            return java.util.Set.copyOf(flags);
        }
    }
}
