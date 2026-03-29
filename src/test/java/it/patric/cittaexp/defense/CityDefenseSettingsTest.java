package it.patric.cittaexp.defense;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.patric.cittaexp.custommobs.CustomMobFlags;
import it.patric.cittaexp.custommobs.MobAbilityRegistry;
import it.patric.cittaexp.custommobs.CustomMobRegistry;
import it.patric.cittaexp.custommobs.CustomMobSpec;
import it.patric.cittaexp.itemsadder.ItemsAdderRewardResolver;
import it.patric.cittaexp.custommobs.MobTraitRegistry;
import it.patric.cittaexp.custommobs.MobVariantRegistry;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

class CityDefenseSettingsTest {

    private static final Path RESOURCES = Path.of("src/main/resources");

    @Test
    void legacyVanillaConfigStillParsesAsVanillaRefs() throws IOException {
        File file = writeTempDefense("""
                enabled: true
                levels:
                  l1:
                    regularMobs:
                      - ZOMBIE
                      - SKELETON
                    eliteMobs:
                      - HUSK
                    bossMob: RAVAGER
                """);

        CityDefenseSettings settings = CityDefenseSettings.load(file, Logger.getLogger("test"), emptyRegistry());
        CityDefenseLevelSpec spec = settings.level(CityDefenseTier.L1);

        assertTrue(spec.regularMobs().stream().allMatch(CityDefenseSpawnRef::isVanilla));
        assertEquals(EntityType.ZOMBIE, spec.regularMobs().get(0).vanillaType());
        assertTrue(spec.bossMob().isVanilla());
        assertEquals(EntityType.RAVAGER, spec.bossMob().vanillaType());
    }

    @Test
    void mixedVanillaAndCustomRefsAreLoaded() throws IOException {
        File file = writeTempDefense("""
                enabled: true
                retry:
                  exemptFromTier: L16
                levels:
                  l1:
                    rewardMoney: 150
                    maxDurationSeconds: 1800
                    regularMobs:
                      - custom:ember_guard
                      - ZOMBIE
                    eliteMobs:
                      - custom:ember_elite
                    bossMob: custom:siege_lord
                """);

        CityDefenseSettings settings = CityDefenseSettings.load(file, Logger.getLogger("test"), registryOf(
                simpleMob("ember_guard", EntityType.ZOMBIE),
                simpleMob("ember_elite", EntityType.SKELETON),
                simpleMob("siege_lord", EntityType.RAVAGER)
        ));
        CityDefenseLevelSpec spec = settings.level(CityDefenseTier.L1);

        assertEquals("ember_guard", spec.regularMobs().get(0).customMobId());
        assertEquals(EntityType.ZOMBIE, spec.regularMobs().get(1).vanillaType());
        assertEquals("ember_elite", spec.eliteMobs().get(0).customMobId());
        assertEquals("siege_lord", spec.bossMob().customMobId());
        assertEquals(150L, spec.rewardMoney().longValue());
        assertEquals(1800L, spec.maxDurationSeconds());
        assertEquals(CityDefenseTier.L16, settings.retryPolicy().exemptFromTier());
    }

    @Test
    void firstClearUniqueRewardsAreParsed() throws IOException {
        File file = writeTempDefense("""
                enabled: true
                levels:
                  l1:
                    firstClearUniqueRewards:
                      - item: cittaexp:frontier_sigillum
                        amount: 1
                      - item: cittaexp:war_banner_fragment
                        amount: 2
                """);

        CityDefenseSettings settings = CityDefenseSettings.load(file, Logger.getLogger("test"), emptyRegistry());
        CityDefenseLevelSpec spec = settings.level(CityDefenseTier.L1);

        assertEquals(2, spec.firstClearUniqueRewards().size());
        ItemsAdderRewardResolver.RewardSpec first = spec.firstClearUniqueRewards().get(0);
        assertEquals("cittaexp:frontier_sigillum", first.itemId());
        assertEquals(1, first.amount());
        assertEquals("cittaexp:war_banner_fragment", spec.firstClearUniqueRewards().get(1).itemId());
        assertEquals(2, spec.firstClearUniqueRewards().get(1).amount());
    }

    @Test
    void spawnBreachAndRecoverySettingsAreParsed() throws IOException {
        File file = writeTempDefense("""
                enabled: true
                spawn:
                  minDistanceFromGuardian: 25
                  maxDistanceFromGuardian: 50
                  frontCountMin: 2
                  frontCountMax: 4
                  groupSizeMin: 3
                  groupSizeMax: 6
                  frontJitterRadius: 9
                  intraGroupSpacing: 4
                  allowInsideClaims: true
                  allowOutsideClaims: true
                breach:
                  enabled: true
                  pathFailSeconds: 7
                  breakCooldownSeconds: 5
                  maxBreaksPerMob: 9
                  maxVerticalDelta: 3
                  searchDepth: 2
                  vanillaBreacherTypes:
                    - ZOMBIE
                    - HUSK
                recovery:
                  stuckSampleSeconds: 4
                  stuckDistanceThreshold: 2.5
                  stuckTimeoutSeconds: 10
                  repositionStepDistance: 9
                  maxAttemptsPerWave: 4
                levels:
                  l1: {}
                """);

        CityDefenseSettings settings = CityDefenseSettings.load(file, Logger.getLogger("test"), emptyRegistry());

        assertEquals(25, settings.spawnSettings().minDistanceFromGuardian());
        assertEquals(50, settings.spawnSettings().maxDistanceFromGuardian());
        assertEquals(2, settings.spawnSettings().frontCountMin());
        assertEquals(4, settings.spawnSettings().frontCountMax());
        assertEquals(3, settings.spawnSettings().groupSizeMin());
        assertEquals(6, settings.spawnSettings().groupSizeMax());
        assertEquals(9, settings.spawnSettings().frontJitterRadius());
        assertEquals(4, settings.spawnSettings().intraGroupSpacing());
        assertTrue(settings.spawnSettings().allowInsideClaims());
        assertTrue(settings.spawnSettings().allowOutsideClaims());
        assertTrue(settings.breachSettings().enabled());
        assertEquals(7L, settings.breachSettings().pathFailSeconds());
        assertEquals(5L, settings.breachSettings().breakCooldownSeconds());
        assertEquals(9, settings.breachSettings().maxBreaksPerMob());
        assertEquals(3, settings.breachSettings().maxVerticalDelta());
        assertEquals(2, settings.breachSettings().searchDepth());
        assertTrue(settings.breachSettings().isVanillaBreacher(EntityType.ZOMBIE));
        assertTrue(settings.breachSettings().isVanillaBreacher(EntityType.HUSK));
        assertEquals(4L, settings.recoverySettings().stuckSampleSeconds());
        assertEquals(2.5D, settings.recoverySettings().stuckDistanceThreshold());
        assertEquals(10L, settings.recoverySettings().stuckTimeoutSeconds());
        assertEquals(9, settings.recoverySettings().repositionStepDistance());
        assertEquals(4, settings.recoverySettings().maxAttemptsPerWave());
    }

    @Test
    void invalidCustomRefsFallbackToTierDefaults() throws IOException {
        File file = writeTempDefense("""
                enabled: true
                levels:
                  l1:
                    regularMobs:
                      - custom:missing_wave
                    eliteMobs:
                      - custom:missing_elite
                    bossMob: custom:missing_boss
                """);

        CityDefenseSettings settings = CityDefenseSettings.load(file, Logger.getLogger("test"), emptyRegistry());
        CityDefenseLevelSpec spec = settings.level(CityDefenseTier.L1);

        assertTrue(spec.regularMobs().stream().allMatch(CityDefenseSpawnRef::isVanilla));
        assertEquals(EntityType.ZOMBIE, spec.regularMobs().get(0).vanillaType());
        assertTrue(spec.eliteMobs().stream().allMatch(CityDefenseSpawnRef::isVanilla));
        assertEquals(EntityType.RAVAGER, spec.bossMob().vanillaType());
        assertTrue(spec.rewardMoney().signum() > 0);
        assertTrue(spec.maxDurationSeconds() >= 1800L);
    }

    @Test
    void bundledDefenseConfigCustomRefsResolveAgainstBundledCustomMobRegistry() {
        CustomMobRegistry registry = bundledRegistry();
        File defenseFile = RESOURCES.resolve("defense.yml").toFile();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(defenseFile);
        ConfigurationSection levels = yaml.getConfigurationSection("levels");
        assertTrue(levels != null);

        for (CityDefenseTier tier : CityDefenseTier.values()) {
            ConfigurationSection row = levels.getConfigurationSection(tier.id());
            assertTrue(row != null, () -> "missing row for " + tier.id());
            assertRefsResolve(row.getStringList("regularMobs"), registry);
            assertRefsResolve(row.getStringList("eliteMobs"), registry);
            String bossRef = row.getString("bossMob");
            assertTrue(bossRef != null && !bossRef.isBlank(), () -> "missing bossMob for " + tier.id());
            assertRefResolves(bossRef, registry);
        }

        assertEquals("frontier_ram", stripCustomPrefix(levels.getConfigurationSection("l1").getString("bossMob")));
        assertEquals("frontier_war_ram", stripCustomPrefix(levels.getConfigurationSection("l4").getString("bossMob")));
        assertEquals("crimson_breachlord", stripCustomPrefix(levels.getConfigurationSection("l8").getString("bossMob")));
        assertEquals("cinder_hierophant", stripCustomPrefix(levels.getConfigurationSection("l13").getString("bossMob")));
        assertEquals("abyssal_herald", stripCustomPrefix(levels.getConfigurationSection("l18").getString("bossMob")));
        assertEquals("last_siege_king", stripCustomPrefix(levels.getConfigurationSection("l25").getString("bossMob")));
    }

    private static File writeTempDefense(String yaml) throws IOException {
        File dir = Files.createTempDirectory("cittaexp-defense-settings").toFile();
        File file = new File(dir, "defense.yml");
        Files.writeString(file.toPath(), yaml);
        return file;
    }

    private static CustomMobRegistry emptyRegistry() {
        return new CustomMobRegistry(Map.of());
    }

    private static CustomMobRegistry bundledRegistry() {
        Path abilitiesDir = RESOURCES.resolve("mob-abilities");
        Path traitsDir = RESOURCES.resolve("mob-traits");
        Path variantsDir = RESOURCES.resolve("mob-variants");
        Path mobsDir = RESOURCES.resolve("custom-mobs");
        MobAbilityRegistry.LoadResult abilities = MobAbilityRegistry.load(abilitiesDir.toFile());
        MobTraitRegistry.LoadResult traits = MobTraitRegistry.load(traitsDir.toFile(), abilities.registry());
        MobVariantRegistry.LoadResult variants = MobVariantRegistry.load(variantsDir.toFile(), abilities.registry());
        CustomMobRegistry.LoadResult mobs = CustomMobRegistry.load(mobsDir.toFile(), abilities.registry(), traits.registry(), variants.registry());
        assertTrue(abilities.warnings().isEmpty(), () -> "ability warnings: " + abilities.warnings());
        assertTrue(traits.warnings().isEmpty(), () -> "trait warnings: " + traits.warnings());
        assertTrue(variants.warnings().isEmpty(), () -> "variant warnings: " + variants.warnings());
        assertTrue(mobs.warnings().isEmpty(), () -> "mob warnings: " + mobs.warnings());
        return mobs.registry();
    }

    private static void assertRefsResolve(List<String> refs, CustomMobRegistry registry) {
        for (String ref : refs) {
            assertRefResolves(ref, registry);
        }
    }

    private static void assertRefResolves(String ref, CustomMobRegistry registry) {
        CityDefenseSpawnRef spawnRef = CityDefenseSpawnRef.parse(ref);
        if (spawnRef.isCustom()) {
            assertTrue(registry.get(spawnRef.customMobId()).isPresent(), () -> "missing custom mob ref " + spawnRef.customMobId());
        } else {
            assertTrue(spawnRef.vanillaType() != null, () -> "invalid vanilla ref " + ref);
        }
    }

    private static String stripCustomPrefix(String ref) {
        return ref == null ? null : ref.replaceFirst("(?i)^custom:", "");
    }

    private static CustomMobRegistry registryOf(CustomMobSpec... specs) {
        java.util.LinkedHashMap<String, CustomMobSpec> map = new java.util.LinkedHashMap<>();
        for (CustomMobSpec spec : specs) {
            map.put(spec.id(), spec);
        }
        return new CustomMobRegistry(map);
    }

    private static CustomMobSpec simpleMob(String id, EntityType type) {
        return new CustomMobSpec(
                id,
                type,
                id,
                true,
                Map.of(),
                Map.of(),
                CustomMobFlags.DEFAULT,
                Set.of(),
                false,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                null
        );
    }
}
