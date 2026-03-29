package it.patric.cittaexp.custommobs;

import it.patric.cittaexp.utils.EntityGlowService;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;

public final class CustomMobCombatListener implements Listener {

    private final MobEffectsEngine effectsEngine;
    private final CustomMobMarkers markers;
    private final EntityGlowService glowService;

    public CustomMobCombatListener(MobEffectsEngine effectsEngine, CustomMobMarkers markers, EntityGlowService glowService) {
        this.effectsEngine = effectsEngine;
        this.markers = markers;
        this.glowService = glowService;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEntityTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity caster)) {
            return;
        }
        if (markers.mobId(caster).isEmpty()) {
            return;
        }
        effectsEngine.onTarget(caster, event.getTarget(), event.getEntity());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (effectsEngine.isSyntheticDamage(event.getEntity())) {
            return;
        }
        Entity resolvedDamager = resolveDamager(event.getDamager());
        if (resolvedDamager instanceof LivingEntity damager && markers.mobId(damager).isPresent()) {
            if (event.getEntity() instanceof LivingEntity victim) {
                effectsEngine.onHit(damager, victim, resolvedDamager);
            }
        }
        if (event.getEntity() instanceof LivingEntity victim && markers.mobId(victim).isPresent()) {
            effectsEngine.onHurt(victim, resolvedDamager);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (glowService.isManagedGlow(victim)) {
            glowService.clearGlow(victim);
        }
        if (markers.mobId(victim).isEmpty()) {
            return;
        }
        Entity triggerSource = victim.getKiller();
        effectsEngine.onDeath(victim, triggerSource);
    }

    private Entity resolveDamager(Entity damager) {
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity entityShooter) {
            return entityShooter;
        }
        return damager;
    }
}
