package it.patric.cittaexp.custommobs;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import it.patric.cittaexp.utils.EntityGlowService;

public final class CustomMobSubsystem {

    public record LoadReport(
            int mobsLoaded,
            int abilitiesLoaded,
            int traitsLoaded,
            int variantsLoaded,
            int skippedFiles,
            List<String> warnings
    ) {
    }

    public record Diagnostics(
            int mobsLoaded,
            int abilitiesLoaded,
            int traitsLoaded,
            int variantsLoaded,
            int skippedFiles,
            int warningCount,
            int activeRuntimeMobs,
            int activeProjectileRuntimes,
            long activeBossRuntimeMobs
    ) {
    }

    public record EntityDump(
            UUID entityId,
            String entityType,
            String worldName,
            String locationSummary,
            boolean valid,
            boolean dead,
            boolean customMob,
            String mobId,
            String variantId,
            String traitId,
            String spawnSource,
            Integer ownerTownId,
            String sessionId,
            String encounterId,
            String waveId,
            String defenseTier,
            String phaseId,
            boolean boss,
            boolean summoned,
            UUID parentMobUuid,
            int ownedProjectileCount,
            Optional<MobEffectsEngine.RuntimeMobSnapshot> runtimeSnapshot,
            Optional<CustomMobBossRuntimeSnapshot> bossSnapshot
    ) {
    }

    private record LoadedRegistries(
            CustomMobRegistry mobRegistry,
            MobAbilityRegistry abilityRegistry,
            MobTraitRegistry traitRegistry,
            MobVariantRegistry variantRegistry,
            LoadReport report
    ) {
    }

    private final Plugin plugin;
    private final File mobsDir;
    private final File abilitiesDir;
    private final File traitsDir;
    private final File variantsDir;
    private final CustomMobMarkers markers;
    private volatile CustomMobRegistry mobRegistry;
    private volatile MobAbilityRegistry abilityRegistry;
    private volatile MobTraitRegistry traitRegistry;
    private volatile MobVariantRegistry variantRegistry;
    private final MobEffectsEngine effectsEngine;
    private final CustomMobSpawnService spawnService;
    private final CustomMobCombatListener combatListener;
    private volatile LoadReport report;

    private CustomMobSubsystem(
            Plugin plugin,
            File mobsDir,
            File abilitiesDir,
            File traitsDir,
            File variantsDir,
            CustomMobMarkers markers,
            CustomMobRegistry mobRegistry,
            MobAbilityRegistry abilityRegistry,
            MobTraitRegistry traitRegistry,
            MobVariantRegistry variantRegistry,
            MobEffectsEngine effectsEngine,
            CustomMobSpawnService spawnService,
            CustomMobCombatListener combatListener,
            LoadReport report
    ) {
        this.plugin = plugin;
        this.mobsDir = mobsDir;
        this.abilitiesDir = abilitiesDir;
        this.traitsDir = traitsDir;
        this.variantsDir = variantsDir;
        this.markers = markers;
        this.mobRegistry = mobRegistry;
        this.abilityRegistry = abilityRegistry;
        this.traitRegistry = traitRegistry;
        this.variantRegistry = variantRegistry;
        this.effectsEngine = effectsEngine;
        this.spawnService = spawnService;
        this.combatListener = combatListener;
        this.report = report;
    }

    public static CustomMobSubsystem bootstrap(Plugin plugin) {
        File dataFolder = plugin.getDataFolder();
        File mobsDir = new File(dataFolder, "custom-mobs");
        File abilitiesDir = new File(dataFolder, "mob-abilities");
        File traitsDir = new File(dataFolder, "mob-traits");
        File variantsDir = new File(dataFolder, "mob-variants");
        ensureDirectory(mobsDir);
        ensureDirectory(abilitiesDir);
        ensureDirectory(traitsDir);
        ensureDirectory(variantsDir);
        seedBundledDefaults(plugin, "custom-mobs");
        seedBundledDefaults(plugin, "mob-abilities");
        seedBundledDefaults(plugin, "mob-traits");
        seedBundledDefaults(plugin, "mob-variants");

        LoadedRegistries loaded = loadRegistries(mobsDir, abilitiesDir, traitsDir, variantsDir);
        logLoad(plugin, loaded.report(), "loaded");

        CustomMobMarkers markers = new CustomMobMarkers(plugin);
        EntityGlowService glowService = new EntityGlowService(plugin);
        MobEffectsEngine effectsEngine = new MobEffectsEngine(plugin, loaded.abilityRegistry(), markers);
        CustomMobSpawnService spawnService = new CustomMobSpawnService(
                plugin,
                loaded.mobRegistry(),
                loaded.traitRegistry(),
                loaded.variantRegistry(),
                markers,
                effectsEngine
        );
        effectsEngine.attachSpawnService(spawnService);
        effectsEngine.start();

        return new CustomMobSubsystem(
                plugin,
                mobsDir,
                abilitiesDir,
                traitsDir,
                variantsDir,
                markers,
                loaded.mobRegistry(),
                loaded.abilityRegistry(),
                loaded.traitRegistry(),
                loaded.variantRegistry(),
                effectsEngine,
                spawnService,
                new CustomMobCombatListener(effectsEngine, markers, glowService),
                loaded.report()
        );
    }

    public synchronized LoadReport lint() {
        LoadedRegistries loaded = loadRegistries(mobsDir, abilitiesDir, traitsDir, variantsDir);
        logLoad(plugin, loaded.report(), "lint");
        return loaded.report();
    }

    public synchronized LoadReport reload() {
        LoadedRegistries loaded = loadRegistries(mobsDir, abilitiesDir, traitsDir, variantsDir);
        this.mobRegistry = loaded.mobRegistry();
        this.abilityRegistry = loaded.abilityRegistry();
        this.traitRegistry = loaded.traitRegistry();
        this.variantRegistry = loaded.variantRegistry();
        this.report = loaded.report();
        this.effectsEngine.reloadAbilityRegistry(loaded.abilityRegistry());
        this.spawnService.reloadRegistries(loaded.mobRegistry(), loaded.traitRegistry(), loaded.variantRegistry());
        logLoad(plugin, loaded.report(), "reloaded");
        return loaded.report();
    }

    public Diagnostics diagnostics() {
        LoadReport currentReport = report;
        long activeBossRuntimeMobs = effectsEngine.runtimeSnapshots().stream()
                .filter(MobEffectsEngine.RuntimeMobSnapshot::boss)
                .count();
        return new Diagnostics(
                mobRegistry.all().size(),
                abilityRegistry.all().size(),
                traitRegistry.all().size(),
                variantRegistry.all().size(),
                currentReport.skippedFiles(),
                currentReport.warnings().size(),
                effectsEngine.activeRuntimeMobCount(),
                effectsEngine.activeProjectileCount(),
                activeBossRuntimeMobs
        );
    }

    public Optional<EntityDump> dumpEntity(UUID entityId) {
        if (entityId == null) {
            return Optional.empty();
        }
        Entity entity = Bukkit.getEntity(entityId);
        Optional<MobEffectsEngine.RuntimeMobSnapshot> runtimeSnapshot = effectsEngine.runtimeSnapshot(entityId);
        Optional<CustomMobBossRuntimeSnapshot> bossSnapshot = effectsEngine.bossSnapshot(entityId);
        int ownedProjectileCount = (int) effectsEngine.projectileSnapshots().stream()
                .filter(snapshot -> entityId.equals(snapshot.ownerEntityId()))
                .count();
        if (entity == null && runtimeSnapshot.isEmpty() && bossSnapshot.isEmpty() && ownedProjectileCount == 0) {
            return Optional.empty();
        }

        String entityType = entity == null ? "-" : entity.getType().name();
        String worldName = entity == null || entity.getWorld() == null ? "-" : entity.getWorld().getName();
        String locationSummary = entity == null ? "-" : formatLocation(entity.getLocation());
        boolean valid = entity != null && entity.isValid();
        boolean dead = entity instanceof LivingEntity living && living.isDead();
        String mobId = entity == null ? runtimeSnapshot.map(MobEffectsEngine.RuntimeMobSnapshot::mobId).orElse("-") : markers.mobId(entity).orElse(runtimeSnapshot.map(MobEffectsEngine.RuntimeMobSnapshot::mobId).orElse("-"));
        String variantId = entity == null ? "-" : markers.variantId(entity).orElse("-");
        String traitId = entity == null ? "-" : markers.traitId(entity).orElse("-");
        String spawnSource = entity == null ? "-" : markers.spawnSource(entity).orElse("-");
        Integer ownerTownId = entity == null ? null : markers.ownerTownId(entity).orElse(null);
        String sessionId = entity == null ? runtimeSnapshot.map(MobEffectsEngine.RuntimeMobSnapshot::sessionId).orElse("-") : markers.sessionId(entity).orElse(runtimeSnapshot.map(MobEffectsEngine.RuntimeMobSnapshot::sessionId).orElse("-"));
        String encounterId = entity == null ? runtimeSnapshot.map(MobEffectsEngine.RuntimeMobSnapshot::encounterId).orElse("-") : markers.encounterId(entity).orElse(runtimeSnapshot.map(MobEffectsEngine.RuntimeMobSnapshot::encounterId).orElse("-"));
        String waveId = entity == null ? runtimeSnapshot.map(MobEffectsEngine.RuntimeMobSnapshot::waveId).orElse("-") : markers.waveId(entity).orElse(runtimeSnapshot.map(MobEffectsEngine.RuntimeMobSnapshot::waveId).orElse("-"));
        String defenseTier = entity == null ? runtimeSnapshot.map(MobEffectsEngine.RuntimeMobSnapshot::defenseTier).orElse("-") : markers.defenseTier(entity).orElse(runtimeSnapshot.map(MobEffectsEngine.RuntimeMobSnapshot::defenseTier).orElse("-"));
        String phaseId = entity == null ? runtimeSnapshot.map(MobEffectsEngine.RuntimeMobSnapshot::phaseId).orElse("-") : markers.phaseId(entity).orElse(runtimeSnapshot.map(MobEffectsEngine.RuntimeMobSnapshot::phaseId).orElse("-"));
        boolean boss = entity != null && markers.isBoss(entity) || bossSnapshot.isPresent() || runtimeSnapshot.map(MobEffectsEngine.RuntimeMobSnapshot::boss).orElse(false);
        boolean summoned = entity != null && markers.isSummoned(entity);
        UUID parentMobUuid = entity == null ? null : markers.parentMobUuid(entity).orElse(null);
        boolean customMob = !"-".equals(mobId);
        return Optional.of(new EntityDump(
                entityId,
                entityType,
                worldName,
                locationSummary,
                valid,
                dead,
                customMob,
                mobId,
                variantId,
                traitId,
                spawnSource,
                ownerTownId,
                sessionId,
                encounterId,
                waveId,
                defenseTier,
                phaseId,
                boss,
                summoned,
                parentMobUuid,
                ownedProjectileCount,
                runtimeSnapshot,
                bossSnapshot
        ));
    }

    private static LoadedRegistries loadRegistries(
            File mobsDir,
            File abilitiesDir,
            File traitsDir,
            File variantsDir
    ) {
        MobAbilityRegistry.LoadResult abilityLoad = MobAbilityRegistry.load(abilitiesDir);
        MobTraitRegistry.LoadResult traitLoad = MobTraitRegistry.load(traitsDir, abilityLoad.registry());
        MobVariantRegistry.LoadResult variantLoad = MobVariantRegistry.load(variantsDir, abilityLoad.registry());
        CustomMobRegistry.LoadResult mobLoad = CustomMobRegistry.load(mobsDir, abilityLoad.registry(), traitLoad.registry(), variantLoad.registry());

        List<String> warnings = new ArrayList<>();
        warnings.addAll(abilityLoad.warnings());
        warnings.addAll(traitLoad.warnings());
        warnings.addAll(variantLoad.warnings());
        warnings.addAll(mobLoad.warnings());
        LoadReport report = new LoadReport(
                mobLoad.loadedCount(),
                abilityLoad.loadedCount(),
                traitLoad.loadedCount(),
                variantLoad.loadedCount(),
                mobLoad.skippedCount() + abilityLoad.skippedCount() + traitLoad.skippedCount() + variantLoad.skippedCount(),
                List.copyOf(warnings)
        );
        return new LoadedRegistries(
                mobLoad.registry(),
                abilityLoad.registry(),
                traitLoad.registry(),
                variantLoad.registry(),
                report
        );
    }

    private static void logLoad(Plugin plugin, LoadReport report, String verb) {
        for (String warning : report.warnings()) {
            plugin.getLogger().warning("[custom-mobs] " + warning);
        }
        plugin.getLogger().info("[custom-mobs] " + verb
                + " mobs=" + report.mobsLoaded()
                + " abilities=" + report.abilitiesLoaded()
                + " traits=" + report.traitsLoaded()
                + " variants=" + report.variantsLoaded()
                + " skipped=" + report.skippedFiles()
                + " warnings=" + report.warnings().size());
    }

    private static String formatLocation(Location location) {
        return location.getWorld().getName()
                + ":"
                + location.getBlockX() + ","
                + location.getBlockY() + ","
                + location.getBlockZ();
    }

    private static void ensureDirectory(File directory) {
        try {
            Files.createDirectories(directory.toPath());
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot create directory: " + directory.getAbsolutePath(), exception);
        }
    }

    private static void seedBundledDefaults(Plugin plugin, String directoryName) {
        String manifestPath = directoryName + "/pack-manifest.yml";
        saveBundledResource(plugin, manifestPath);
        String indexPath = directoryName + "/defaults.txt";
        List<String> defaultsFromManifest = readDefaultsFromManifest(plugin, manifestPath);
        if (!defaultsFromManifest.isEmpty()) {
            saveBundledResource(plugin, indexPath);
            for (String entry : defaultsFromManifest) {
                saveBundledResource(plugin, directoryName + "/" + entry);
            }
            return;
        }
        try (InputStream stream = plugin.getResource(indexPath)) {
            if (stream == null) {
                return;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
                saveBundledResource(plugin, indexPath);
                for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                    String trimmed = line.trim();
                    if (trimmed.isBlank() || trimmed.startsWith("#")) {
                        continue;
                    }
                    saveBundledResource(plugin, directoryName + "/" + trimmed);
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot seed bundled defaults for " + directoryName, exception);
        }
    }

    private static List<String> readDefaultsFromManifest(Plugin plugin, String manifestPath) {
        try (InputStream stream = plugin.getResource(manifestPath)) {
            if (stream == null) {
                return List.of();
            }
            try (InputStreamReader reader = new InputStreamReader(stream)) {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(reader);
                return List.copyOf(yaml.getStringList("defaults"));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read bundled manifest " + manifestPath, exception);
        }
    }

    private static void saveBundledResource(Plugin plugin, String resourcePath) {
        if (plugin.getResource(resourcePath) == null) {
            plugin.getLogger().warning("[custom-mobs] missing bundled resource " + resourcePath);
            return;
        }
        plugin.saveResource(resourcePath, true);
    }

    public CustomMobRegistry mobRegistry() {
        return mobRegistry;
    }

    public MobAbilityRegistry abilityRegistry() {
        return abilityRegistry;
    }

    public MobTraitRegistry traitRegistry() {
        return traitRegistry;
    }

    public MobVariantRegistry variantRegistry() {
        return variantRegistry;
    }

    public MobEffectsEngine effectsEngine() {
        return effectsEngine;
    }

    public CustomMobSpawnService spawnService() {
        return spawnService;
    }

    public Listener combatListener() {
        return combatListener;
    }

    public LoadReport report() {
        return report;
    }

    public void stop() {
        effectsEngine.clearRuntimeState();
    }
}
