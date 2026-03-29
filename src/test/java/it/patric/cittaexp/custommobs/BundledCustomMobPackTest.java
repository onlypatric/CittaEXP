package it.patric.cittaexp.custommobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class BundledCustomMobPackTest {

    private static final Path RESOURCES = Path.of("src/main/resources");

    @Test
    void bundledPackManifestsReferenceExistingDefaults() throws Exception {
        assertManifestAndDefaults("custom-mobs");
        assertManifestAndDefaults("mob-abilities");
        assertManifestAndDefaults("mob-traits");
        assertManifestAndDefaults("mob-variants");
    }

    @Test
    void bundledRegistriesLoadWithoutWarningsAndExposeAdvancedFeatures() {
        MobAbilityRegistry.LoadResult abilities = MobAbilityRegistry.load(resourceDir("mob-abilities").toFile());
        MobTraitRegistry.LoadResult traits = MobTraitRegistry.load(resourceDir("mob-traits").toFile(), abilities.registry());
        MobVariantRegistry.LoadResult variants = MobVariantRegistry.load(resourceDir("mob-variants").toFile(), abilities.registry());
        CustomMobRegistry.LoadResult mobs = CustomMobRegistry.load(
                resourceDir("custom-mobs").toFile(),
                abilities.registry(),
                traits.registry(),
                variants.registry()
        );

        assertTrue(abilities.warnings().isEmpty(), () -> "ability warnings: " + abilities.warnings());
        assertTrue(traits.warnings().isEmpty(), () -> "trait warnings: " + traits.warnings());
        assertTrue(variants.warnings().isEmpty(), () -> "variant warnings: " + variants.warnings());
        assertTrue(mobs.warnings().isEmpty(), () -> "mob warnings: " + mobs.warnings());

        assertEquals(21, abilities.loadedCount());
        assertEquals(11, traits.loadedCount());
        assertEquals(21, variants.loadedCount());
        assertEquals(25, mobs.loadedCount());

        MobAbilitySpec projectile = abilities.registry().get("defense_anchor_bolt").orElseThrow();
        assertTrue(projectile.actions().stream().anyMatch(action -> action instanceof MobActionSpec.LaunchProjectileActionSpec projectileAction
                && projectileAction.count() >= 1
                && !projectileAction.hitActions().isEmpty()));

        MobAbilitySpec summon = abilities.registry().get("defense_frontier_reinforce").orElseThrow();
        assertTrue(summon.actions().stream().anyMatch(action -> action instanceof MobActionSpec.SummonActionSpec summonAction
                && summonAction.spawnRef().startsWith("custom:")));

        CustomMobSpec boss = mobs.registry().get("frontier_war_ram").orElseThrow();
        assertTrue(boss.boss());
        assertEquals(3, boss.phaseRules().size());
        assertTrue(boss.phaseRules().stream().allMatch(rule -> !rule.label().isBlank()));
        assertTrue(boss.bossbarSpec().enabled());
        assertFalse(boss.localBroadcasts().spawnMessage().isBlank());

        CustomMobSpec finalBoss = mobs.registry().get("last_siege_king").orElseThrow();
        assertEquals(Set.of("king_phase_i", "king_phase_ii", "king_phase_iii"),
                finalBoss.phaseRules().stream().map(MobPhaseRule::phaseId).collect(java.util.stream.Collectors.toSet()));
    }

    private static void assertManifestAndDefaults(String directoryName) throws Exception {
        Path dir = resourceDir(directoryName);
        Path manifestPath = dir.resolve("pack-manifest.yml");
        Path defaultsPath = dir.resolve("defaults.txt");
        assertTrue(Files.exists(manifestPath), () -> "missing manifest for " + directoryName);
        assertTrue(Files.exists(defaultsPath), () -> "missing defaults.txt for " + directoryName);

        YamlConfiguration manifest = YamlConfiguration.loadConfiguration(manifestPath.toFile());
        List<String> manifestDefaults = manifest.getStringList("defaults").stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
        List<String> textDefaults = Files.readAllLines(defaultsPath).stream()
                .map(String::trim)
                .filter(value -> !value.isBlank() && !value.startsWith("#"))
                .toList();

        assertEquals(Set.copyOf(manifestDefaults), Set.copyOf(textDefaults), () -> "manifest/defaults mismatch for " + directoryName);
        for (String entry : manifestDefaults) {
            assertTrue(Files.exists(dir.resolve(entry)), () -> "missing bundled file " + directoryName + "/" + entry);
        }
    }

    private static Path resourceDir(String name) {
        Path directory = RESOURCES.resolve(name);
        assertTrue(Files.isDirectory(directory), () -> "missing resource directory " + directory);
        return directory;
    }
}
