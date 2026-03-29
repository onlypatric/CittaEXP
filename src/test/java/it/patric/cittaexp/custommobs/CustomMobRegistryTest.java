package it.patric.cittaexp.custommobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

class CustomMobRegistryTest {

    @Test
    void emptyDirectoriesProduceEmptyRegistries() throws IOException {
        File root = Files.createTempDirectory("cittaexp-custommobs-empty").toFile();
        File mobsDir = new File(root, "custom-mobs");
        File abilitiesDir = new File(root, "mob-abilities");
        assertTrue(mobsDir.mkdirs() || mobsDir.isDirectory());
        assertTrue(abilitiesDir.mkdirs() || abilitiesDir.isDirectory());

        MobAbilityRegistry.LoadResult abilities = MobAbilityRegistry.load(abilitiesDir);
        CustomMobRegistry.LoadResult mobs = CustomMobRegistry.load(mobsDir, abilities.registry());

        assertEquals(0, abilities.loadedCount());
        assertEquals(0, mobs.loadedCount());
        assertTrue(abilities.registry().all().isEmpty());
        assertTrue(mobs.registry().all().isEmpty());
    }

    @Test
    void validAbilityAndMobFilesAreLoaded() throws IOException {
        File root = Files.createTempDirectory("cittaexp-custommobs-valid").toFile();
        File mobsDir = new File(root, "custom-mobs");
        File abilitiesDir = new File(root, "mob-abilities");
        assertTrue(mobsDir.mkdirs() || mobsDir.isDirectory());
        assertTrue(abilitiesDir.mkdirs() || abilitiesDir.isDirectory());

        Files.writeString(new File(abilitiesDir, "spawn_burst.yml").toPath(), """
                id: spawn_burst
                trigger: ON_SPAWN
                cooldownTicks: 40
                targeting: SELF
                actions:
                  - type: particles
                    particle: FLAME
                    count: 12
                  - type: sound
                    sound: ENTITY_BLAZE_SHOOT
                    volume: 1.0
                    pitch: 1.2
                  - type: applyPotionToSelf
                    effect: SPEED
                    durationTicks: 120
                    amplifier: 1
                """);

        Files.writeString(new File(mobsDir, "ember_guard.yml").toPath(), """
                id: ember_guard
                type: ZOMBIE
                name: <red>Ember Guard
                showName: true
                boss: false
                stats:
                  GENERIC_MAX_HEALTH: 40.0
                equipment:
                  helmet: IRON_HELMET
                  mainHand: IRON_SWORD
                flags:
                  silent: false
                  collidable: true
                  invulnerable: false
                tags:
                  - frontline
                abilities:
                  onSpawn:
                    - spawn_burst
                """);

        MobAbilityRegistry.LoadResult abilities = MobAbilityRegistry.load(abilitiesDir);
        CustomMobRegistry.LoadResult mobs = CustomMobRegistry.load(mobsDir, abilities.registry());

        assertEquals(1, abilities.loadedCount());
        assertEquals(1, mobs.loadedCount());
        assertTrue(abilities.warnings().isEmpty());
        assertTrue(mobs.warnings().isEmpty());

        MobAbilitySpec ability = abilities.registry().get("spawn_burst").orElseThrow();
        assertEquals(MobTriggerType.ON_SPAWN, ability.trigger());
        assertEquals(3, ability.actions().size());

        CustomMobSpec mob = mobs.registry().get("ember_guard").orElseThrow();
        assertEquals(EntityType.ZOMBIE, mob.entityType());
        assertEquals(40.0D, mob.attributes().get("GENERIC_MAX_HEALTH"));
        assertTrue(mob.mainAbilityIds().contains("spawn_burst"));
    }

    @Test
    void metadataQueriesResolveByTagPoolArcThemeAndPack() throws IOException {
        File root = Files.createTempDirectory("cittaexp-custommobs-metadata").toFile();
        File mobsDir = new File(root, "custom-mobs");
        File abilitiesDir = new File(root, "mob-abilities");
        assertTrue(mobsDir.mkdirs() || mobsDir.isDirectory());
        assertTrue(abilitiesDir.mkdirs() || abilitiesDir.isDirectory());

        Files.writeString(new File(mobsDir, "breach_guard.yml").toPath(), """
                id: breach_guard
                type: ZOMBIE
                name: <red>Breach Guard
                packId: defense_codex_v2
                arc: breach
                pools:
                  - defense
                  - elite
                themes:
                  - breach
                  - ember
                tags:
                  - breach
                  - elite
                """);

        CustomMobRegistry.LoadResult mobs = CustomMobRegistry.load(mobsDir, new MobAbilityRegistry(Map.of()));
        CustomMobSpec mob = mobs.registry().get("breach_guard").orElseThrow();

        assertEquals("defense_codex_v2", mob.metadata().packId());
        assertEquals("breach", mob.metadata().arcId());
        assertEquals(1, mobs.registry().findByPack("defense_codex_v2").size());
        assertEquals(1, mobs.registry().findByArc("breach").size());
        assertEquals(1, mobs.registry().findByPool("elite").size());
        assertEquals(1, mobs.registry().findByTheme("ember").size());
        assertEquals(1, mobs.registry().findByTag("breach").size());
    }

    @Test
    void invalidAndDuplicateFilesAreSkippedWithWarnings() throws IOException {
        File root = Files.createTempDirectory("cittaexp-custommobs-invalid").toFile();
        File mobsDir = new File(root, "custom-mobs");
        File abilitiesDir = new File(root, "mob-abilities");
        assertTrue(mobsDir.mkdirs() || mobsDir.isDirectory());
        assertTrue(abilitiesDir.mkdirs() || abilitiesDir.isDirectory());

        Files.writeString(new File(abilitiesDir, "good.yml").toPath(), """
                id: burst
                trigger: ON_SPAWN
                cooldownTicks: 0
                targeting: SELF
                actions:
                  - type: particles
                    particle: FLAME
                """);
        Files.writeString(new File(abilitiesDir, "duplicate.yml").toPath(), """
                id: burst
                trigger: ON_SPAWN
                cooldownTicks: 0
                targeting: SELF
                actions:
                  - type: sound
                    sound: ENTITY_BLAZE_SHOOT
                """);
        Files.writeString(new File(abilitiesDir, "invalid.yml").toPath(), """
                id: broken
                trigger: NOPE
                cooldownTicks: 0
                targeting: SELF
                actions:
                  - type: particles
                    particle: FLAME
                """);

        MobAbilityRegistry.LoadResult abilities = MobAbilityRegistry.load(abilitiesDir);
        assertEquals(1, abilities.loadedCount());
        assertEquals(2, abilities.skippedCount());
        assertEquals(2, abilities.warnings().size());

        Files.writeString(new File(mobsDir, "bad_reference.yml").toPath(), """
                id: bad_ref
                type: ZOMBIE
                abilities:
                  onSpawn:
                    - missing_ability
                """);
        Files.writeString(new File(mobsDir, "good_mob.yml").toPath(), """
                id: good_mob
                type: ZOMBIE
                abilities:
                  onSpawn:
                    - burst
                """);
        Files.writeString(new File(mobsDir, "duplicate_mob.yml").toPath(), """
                id: good_mob
                type: SKELETON
                """);

        CustomMobRegistry.LoadResult mobs = CustomMobRegistry.load(mobsDir, abilities.registry());
        assertEquals(1, mobs.loadedCount());
        assertEquals(2, mobs.skippedCount());
        assertEquals(2, mobs.warnings().size());
    }

    @Test
    void traitsVariantsAndStructuredAbilityConfigAreLoaded() throws IOException {
        File root = Files.createTempDirectory("cittaexp-custommobs-advanced").toFile();
        File mobsDir = new File(root, "custom-mobs");
        File abilitiesDir = new File(root, "mob-abilities");
        File traitsDir = new File(root, "mob-traits");
        File variantsDir = new File(root, "mob-variants");
        assertTrue(mobsDir.mkdirs() || mobsDir.isDirectory());
        assertTrue(abilitiesDir.mkdirs() || abilitiesDir.isDirectory());
        assertTrue(traitsDir.mkdirs() || traitsDir.isDirectory());
        assertTrue(variantsDir.mkdirs() || variantsDir.isDirectory());

        Files.writeString(new File(abilitiesDir, "siege_burst.yml").toPath(), """
                id: siege_burst
                trigger: ON_HURT
                cooldownTicks: 60
                targeting:
                  type: PLAYERS_IN_RADIUS
                  radius: 8
                  maxTargets: 2
                conditions:
                  - type: hpBelow
                    ratio: 0.8
                  - type: waveAtLeast
                    wave: 3
                actions:
                  - type: directDamage
                    amount: 4.0
                  - type: setTempFlag
                    flag: enraged
                    durationTicks: 40
                """);

        Files.writeString(new File(traitsDir, "ashen.yml").toPath(), """
                id: ashen
                weight: 3
                namePrefix: <gray>Ashen</gray>
                addAbilities:
                  - siege_burst
                tags:
                  - bruiser
                """);

        Files.writeString(new File(variantsDir, "vanguard.yml").toPath(), """
                id: vanguard
                weight: 2
                nameSuffix: <gold>Vanguard</gold>
                stats:
                  GENERIC_MAX_HEALTH: 60.0
                tags:
                  - frontline
                """);

        Files.writeString(new File(mobsDir, "siege_guard.yml").toPath(), """
                id: siege_guard
                type: ZOMBIE
                name: <red>Siege Guard
                traits:
                  - ashen
                variants:
                  - vanguard
                mainAbilities:
                  - siege_burst
                """);

        MobAbilityRegistry.LoadResult abilities = MobAbilityRegistry.load(abilitiesDir);
        MobTraitRegistry.LoadResult traits = MobTraitRegistry.load(traitsDir, abilities.registry());
        MobVariantRegistry.LoadResult variants = MobVariantRegistry.load(variantsDir, abilities.registry());
        CustomMobRegistry.LoadResult mobs = CustomMobRegistry.load(mobsDir, abilities.registry(), traits.registry(), variants.registry());

        MobAbilitySpec ability = abilities.registry().get("siege_burst").orElseThrow();
        assertEquals(MobTriggerType.ON_HURT, ability.trigger());
        assertEquals(MobTargeterType.PLAYERS_IN_RADIUS, ability.targeting().type());
        assertEquals(2, ability.actions().size());
        assertEquals(2, ability.conditions().size());

        CustomMobSpec mob = mobs.registry().get("siege_guard").orElseThrow();
        assertTrue(mob.traitIds().contains("ashen"));
        assertTrue(mob.variantIds().contains("vanguard"));
        assertTrue(mob.mainAbilityIds().contains("siege_burst"));
        assertEquals(1, traits.registry().findByPool("traits").size());
        assertEquals(1, variants.registry().findByPool("variants").size());
    }

    @Test
    void bossPhaseBindingsBossbarAndLocalBroadcastsAreLoaded() throws IOException {
        File root = Files.createTempDirectory("cittaexp-custommobs-boss").toFile();
        File mobsDir = new File(root, "custom-mobs");
        File abilitiesDir = new File(root, "mob-abilities");
        assertTrue(mobsDir.mkdirs() || mobsDir.isDirectory());
        assertTrue(abilitiesDir.mkdirs() || abilitiesDir.isDirectory());

        Files.writeString(new File(abilitiesDir, "boss_burst.yml").toPath(), """
                id: boss_burst
                trigger: ON_IDLE
                cooldownTicks: 40
                targeting: SELF
                actions:
                  - type: particles
                    particle: FLAME
                    count: 10
                """);

        Files.writeString(new File(mobsDir, "boss.yml").toPath(), """
                id: siege_boss
                type: RAVAGER
                name: <red>Siege Boss
                showName: true
                boss: true
                mainAbilities:
                  - boss_burst
                bossbar:
                  enabled: true
                  title: <red>{boss}</red> <gray>{phase}</gray>
                  color: RED
                  overlay: NOTCHED_10
                localBroadcasts:
                  spawn: spawn-msg
                  phase: phase-msg
                  defeat: defeat-msg
                phaseBindings:
                  - phase: 1
                    id: open
                    label: Phase I
                    hpBelow: 1.0
                    addAbilities: []
                    removeAbilities: []
                    entryActions:
                      - type: sound
                        sound: ENTITY_RAVAGER_ROAR
                  - phase: 2
                    id: rage
                    label: Phase II
                    hpBelow: 0.5
                    addAbilities:
                      - boss_burst
                    removeAbilities: []
                    namePrefix: <gold>Raging</gold>
                """);

        MobAbilityRegistry.LoadResult abilities = MobAbilityRegistry.load(abilitiesDir);
        CustomMobRegistry.LoadResult mobs = CustomMobRegistry.load(mobsDir, abilities.registry());

        CustomMobSpec mob = mobs.registry().get("siege_boss").orElseThrow();
        assertEquals(2, mob.phaseRules().size());
        assertEquals("open", mob.phaseRules().get(0).phaseId());
        assertEquals("rage", mob.phaseRules().get(1).phaseId());
        assertTrue(mob.bossbarSpec().enabled());
        assertEquals(BossBar.Overlay.NOTCHED_10, mob.bossbarSpec().overlay());
        assertEquals("spawn-msg", mob.localBroadcasts().spawnMessage());
        assertEquals("phase-msg", mob.localBroadcasts().phaseMessage());
        assertEquals("defeat-msg", mob.localBroadcasts().defeatMessage());
    }

    @Test
    void equipmentSupportsStructuredItemsWithEnchantments() throws IOException {
        File root = Files.createTempDirectory("cittaexp-custommobs-enchanted-equipment").toFile();
        File mobsDir = new File(root, "custom-mobs");
        File abilitiesDir = new File(root, "mob-abilities");
        assertTrue(mobsDir.mkdirs() || mobsDir.isDirectory());
        assertTrue(abilitiesDir.mkdirs() || abilitiesDir.isDirectory());

        Files.writeString(new File(mobsDir, "lancer.yml").toPath(), """
                id: lancer
                type: ZOMBIE
                name: <red>Lancer
                equipment:
                  chestplate:
                    material: IRON_CHESTPLATE
                    enchants:
                      protection: 3
                      unbreaking: 2
                  mainHand:
                    material: IRON_SPEAR
                    enchants:
                      lunge: 1
                      mending: 1
                """);

        CustomMobRegistry.LoadResult mobs = CustomMobRegistry.load(mobsDir, new MobAbilityRegistry(Map.of()));
        assertTrue(mobs.warnings().isEmpty(), () -> "mob warnings: " + mobs.warnings());
        CustomMobSpec mob = mobs.registry().get("lancer").orElseThrow();
        CustomMobEquipmentItem chest = mob.equipment().get(org.bukkit.inventory.EquipmentSlot.CHEST);
        CustomMobEquipmentItem hand = mob.equipment().get(org.bukkit.inventory.EquipmentSlot.HAND);

        assertEquals(Material.IRON_CHESTPLATE, chest.material());
        assertEquals(3, chest.enchants().get("protection"));
        assertEquals(2, chest.enchants().get("unbreaking"));
        assertEquals(Material.IRON_SPEAR, hand.material());
        assertEquals(1, hand.enchants().get("lunge"));
        assertEquals(1, hand.enchants().get("mending"));
    }
}
