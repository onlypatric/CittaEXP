1. Design Summary

Il pacchetto è diviso in 5 archi da 5 tier: `Frontiera Ferrata` (`L1-L5`), `Breccia Cremisi` (`L6-L10`), `Cenere Rituale` (`L11-L15`), `Marea dell’Omen` (`L16-L20`), `Trono dell’Ultimo Assedio` (`L21-L25`). La curva cresce in quattro fasce leggibili: `L1-L5` come difesa cittadina avanzata ma ancora leggibile; `L6-L10` come vero assedio con breacher, ranged e boss di rottura; `L11-L15` come pressione di controllo e summon; `L16-L20` come endgame cittadino con disruption e mobilità; `L21-L25` come assedio estremo quasi full custom, con boss maggiori e picco assoluto a `L25`.

Il riuso è intenzionale: 5 archi, ciascuno con 2 minion base, 1 elite, 1 mini-boss, 1 boss finale. Le ability sono poche ma riutilizzate bene per ruolo: pressione sul Guardian, volley ranged, slam di breccia, blast di morte, debuff, burst rituale, pull di controllo, harpoon shot, rally, summon di rinforzi, sunder pesante e pulse finale. Traits e variants non moltiplicano i file inutilmente: i trait danno ruolo funzionale, le variant danno tema, naming e piccoli override di equipaggiamento. La composizione si appoggia ai ruoli reali già leggibili dei mob vanilla: pillager come ranged pressure, witch come support/debuff e healer nei raid, evoker come summoner, ravager come sfondatore resistente al knockback, piglin brute come melee charger costante, warden come apice con pressione ranged da sonic boom. ([Minecraft Wiki][1])

2. Implementation Delta

* Estendere `CityDefenseTier` a `L1..L25`
* Aggiornare `defense.yml`:

  * curva globale ritoccata
  * `levels.l1` fino a `levels.l25`
  * nuovi pool misti vanilla/custom
* Aggiornare GUI/config label per `l1..l25`
* Aggiungere authored content:

  * `custom-mobs`: 25 file
  * `mob-abilities`: 18 file
  * `mob-traits`: 8 file
  * `mob-variants`: 8 file
* Upgrade operativo:

  * parser/config deve accettare `custom:<mob_id>` in tutti i tier
  * GUI e serializer non devono più assumere `L1..L3`
  * nessun nuovo campo richiesto a `CityDefenseLevelSpec`

3. Tier Ladder

* `L1 — Livello città 10 — Bastione di Frontiera — arco: Frontiera Ferrata — boss: frontier_ram — intensità: ingresso avanzato`
* `L2 — Livello città 20 — Fossato in Allarme — arco: Frontiera Ferrata — boss: frontier_ram — intensità: pressione costante`
* `L3 — Livello città 30 — Muro del Predone — arco: Frontiera Ferrata — boss: frontier_war_ram — intensità: assedio leggibile`
* `L4 — Livello città 40 — Ariete del Confine — arco: Frontiera Ferrata — boss: frontier_war_ram — intensità: breccia strutturata`
* `L5 — Livello città 50 — Forte delle Braci — arco: Frontiera Ferrata — boss: frontier_war_ram — intensità: chiusura early`
* `L6 — Livello città 60 — Breccia Ferrata — arco: Breccia Cremisi — boss: crimson_breach_brute — intensità: media dura`
* `L7 — Livello città 70 — Varchi Cremisi — arco: Breccia Cremisi — boss: crimson_breach_brute — intensità: assalto coordinato`
* `L8 — Livello città 80 — Caduta del Bastione — arco: Breccia Cremisi — boss: crimson_breachlord — intensità: rottura pesante`
* `L9 — Livello città 90 — Corsa degli Assediatori — arco: Breccia Cremisi — boss: crimson_breachlord — intensità: pressione alta`
* `L10 — Livello città 100 — Signore della Breccia — arco: Breccia Cremisi — boss: crimson_breachlord — intensità: chiusura mid`
* `L11 — Livello città 110 — Canto di Cenere — arco: Cenere Rituale — boss: ash_behemoth — intensità: controllo e summon`
* `L12 — Livello città 120 — Corte dei Sepolti — arco: Cenere Rituale — boss: ash_behemoth — intensità: pressione ibrida`
* `L13 — Livello città 130 — Rito delle Ossa — arco: Cenere Rituale — boss: cinder_hierophant — intensità: disruption seria`
* `L14 — Livello città 140 — Piaga del Braciere — arco: Cenere Rituale — boss: cinder_hierophant — intensità: late midgame`
* `L15 — Livello città 150 — Ierofante Cinerico — arco: Cenere Rituale — boss: cinder_hierophant — intensità: chiusura rituale`
* `L16 — Livello città 160 — Risacca Oscura — arco: Marea dell’Omen — boss: omen_tidebreaker — intensità: endgame iniziale`
* `L17 — Livello città 170 — Ferri dell’Abisso — arco: Marea dell’Omen — boss: omen_tidebreaker — intensità: controllo mobile`
* `L18 — Livello città 180 — Marea del Presagio — arco: Marea dell’Omen — boss: abyssal_herald — intensità: assedio lungo`
* `L19 — Livello città 190 — Strappo Profondo — arco: Marea dell’Omen — boss: abyssal_herald — intensità: endgame alto`
* `L20 — Livello città 200 — Araldo Abissale — arco: Marea dell’Omen — boss: abyssal_herald — intensità: chiusura endgame`
* `L21 — Livello città 210 — Corona di Ferro — arco: Trono dell’Ultimo Assedio — boss: doom_juggernaut — intensità: regime estremo`
* `L22 — Livello città 220 — Guardia del Tramonto — arco: Trono dell’Ultimo Assedio — boss: doom_juggernaut — intensità: pre-apice`
* `L23 — Livello città 230 — Guerra del Trono — arco: Trono dell’Ultimo Assedio — boss: last_siege_king — intensità: assedio maggiore`
* `L24 — Livello città 240 — Ultimo Bastione — arco: Trono dell’Ultimo Assedio — boss: last_siege_king — intensità: quasi massimo`
* `L25 — Livello città 250 — Re dell’Ultimo Assedio — arco: Trono dell’Ultimo Assedio — boss: last_siege_king — intensità: apice assoluto`

4. Defense YAML

```yaml id="v9n0g8"
enabled: true
globalActiveCap: 2
antiCheeseRadius: 18
maxMobsAlive: 140
playerDeathPenaltyPercent: 0.15

timing:
  prepSeconds: 60
  interWaveSeconds: 14
  sessionTimeoutSeconds: 3600

spawn:
  minPoints: 6
  radiusMin: 22
  radiusMax: 38

defenderScaling:
  perExtraDefender: 0.20
  maxMultiplier: 3.75

levels:
  l1:
    minCityLevel: 10
    waves: 4
    guardianHp: 450
    startCost: 20000
    cooldownSeconds: 7200
    rewardXp: 250
    rewardItems:
      IRON_INGOT: 96
      BREAD: 64
    baseMobCount: 9
    waveIncrement: 3
    bossSupportCount: 6
    eliteChance: 0.08
    regularMobs:
      - ZOMBIE
      - SKELETON
      - SPIDER
      - custom:frontier_raider
    eliteMobs:
      - HUSK
      - custom:frontier_shieldbearer
    bossMob: custom:frontier_ram

  l2:
    minCityLevel: 20
    waves: 5
    guardianHp: 650
    startCost: 45000
    cooldownSeconds: 10800
    rewardXp: 400
    rewardItems:
      IRON_INGOT: 128
      GOLD_INGOT: 32
      COOKED_BEEF: 64
    baseMobCount: 10
    waveIncrement: 3
    bossSupportCount: 7
    eliteChance: 0.10
    regularMobs:
      - ZOMBIE
      - SKELETON
      - SPIDER
      - CREEPER
      - custom:frontier_raider
      - custom:frontier_bolter
    eliteMobs:
      - HUSK
      - STRAY
      - custom:frontier_shieldbearer
    bossMob: custom:frontier_ram

  l3:
    minCityLevel: 30
    waves: 6
    guardianHp: 900
    startCost: 80000
    cooldownSeconds: 14400
    rewardXp: 650
    rewardItems:
      IRON_INGOT: 160
      GOLD_INGOT: 64
      REDSTONE: 128
      COOKED_BEEF: 96
    baseMobCount: 11
    waveIncrement: 4
    bossSupportCount: 8
    eliteChance: 0.12
    regularMobs:
      - ZOMBIE
      - SKELETON
      - CREEPER
      - PILLAGER
      - custom:frontier_raider
      - custom:frontier_bolter
    eliteMobs:
      - STRAY
      - VINDICATOR
      - custom:frontier_shieldbearer
    bossMob: custom:frontier_war_ram

  l4:
    minCityLevel: 40
    waves: 7
    guardianHp: 1200
    startCost: 130000
    cooldownSeconds: 21600
    rewardXp: 1000
    rewardItems:
      GOLD_INGOT: 96
      LAPIS_LAZULI: 128
      DIAMOND: 8
      COOKED_BEEF: 96
    baseMobCount: 12
    waveIncrement: 4
    bossSupportCount: 9
    eliteChance: 0.14
    regularMobs:
      - SKELETON
      - CREEPER
      - PILLAGER
      - custom:frontier_raider
      - custom:frontier_bolter
    eliteMobs:
      - VINDICATOR
      - WITCH
      - custom:frontier_shieldbearer
    bossMob: custom:frontier_war_ram

  l5:
    minCityLevel: 50
    waves: 8
    guardianHp: 1600
    startCost: 200000
    cooldownSeconds: 32400
    rewardXp: 1500
    rewardItems:
      GOLD_INGOT: 128
      DIAMOND: 12
      EMERALD: 16
      COOKED_BEEF: 128
    baseMobCount: 13
    waveIncrement: 5
    bossSupportCount: 10
    eliteChance: 0.16
    regularMobs:
      - PILLAGER
      - SKELETON
      - CREEPER
      - custom:frontier_raider
      - custom:frontier_bolter
    eliteMobs:
      - VINDICATOR
      - WITCH
      - custom:frontier_shieldbearer
    bossMob: custom:frontier_war_ram

  l6:
    minCityLevel: 60
    waves: 9
    guardianHp: 2100
    startCost: 300000
    cooldownSeconds: 43200
    rewardXp: 2200
    rewardItems:
      DIAMOND: 16
      EMERALD: 24
      BLAZE_ROD: 32
      COOKED_BEEF: 128
    baseMobCount: 15
    waveIncrement: 5
    bossSupportCount: 12
    eliteChance: 0.19
    regularMobs:
      - PILLAGER
      - CREEPER
      - custom:breach_sapper
      - custom:breach_marksman
    eliteMobs:
      - WITCH
      - custom:breach_vanguard
    bossMob: custom:crimson_breach_brute

  l7:
    minCityLevel: 70
    waves: 10
    guardianHp: 2700
    startCost: 430000
    cooldownSeconds: 54000
    rewardXp: 3000
    rewardItems:
      DIAMOND: 20
      EMERALD: 32
      ENDER_PEARL: 64
      BLAZE_ROD: 48
    baseMobCount: 16
    waveIncrement: 6
    bossSupportCount: 13
    eliteChance: 0.22
    regularMobs:
      - PILLAGER
      - CREEPER
      - STRAY
      - custom:breach_sapper
      - custom:breach_marksman
    eliteMobs:
      - WITCH
      - WITHER_SKELETON
      - custom:breach_vanguard
    bossMob: custom:crimson_breach_brute

  l8:
    minCityLevel: 80
    waves: 11
    guardianHp: 3400
    startCost: 600000
    cooldownSeconds: 64800
    rewardXp: 4100
    rewardItems:
      DIAMOND: 24
      EMERALD: 40
      OBSIDIAN: 128
      ENDER_PEARL: 64
    baseMobCount: 17
    waveIncrement: 6
    bossSupportCount: 14
    eliteChance: 0.24
    regularMobs:
      - PILLAGER
      - VINDICATOR
      - CREEPER
      - custom:breach_sapper
      - custom:breach_marksman
    eliteMobs:
      - WITCH
      - WITHER_SKELETON
      - custom:breach_vanguard
    bossMob: custom:crimson_breachlord

  l9:
    minCityLevel: 90
    waves: 11
    guardianHp: 4200
    startCost: 820000
    cooldownSeconds: 86400
    rewardXp: 5500
    rewardItems:
      DIAMOND: 28
      EMERALD: 48
      BLAZE_ROD: 64
      ENDER_PEARL: 96
    baseMobCount: 19
    waveIncrement: 6
    bossSupportCount: 15
    eliteChance: 0.26
    regularMobs:
      - PILLAGER
      - ENDERMAN
      - STRAY
      - custom:breach_sapper
      - custom:breach_marksman
    eliteMobs:
      - PIGLIN_BRUTE
      - WITCH
      - custom:breach_vanguard
    bossMob: custom:crimson_breachlord

  l10:
    minCityLevel: 100
    waves: 12
    guardianHp: 5200
    startCost: 1100000
    cooldownSeconds: 108000
    rewardXp: 7200
    rewardItems:
      DIAMOND: 32
      EMERALD: 56
      NETHERITE_SCRAP: 2
      ENDER_PEARL: 96
    baseMobCount: 20
    waveIncrement: 7
    bossSupportCount: 16
    eliteChance: 0.28
    regularMobs:
      - ENDERMAN
      - PILLAGER
      - WITHER_SKELETON
      - custom:breach_sapper
      - custom:breach_marksman
    eliteMobs:
      - PIGLIN_BRUTE
      - WITCH
      - custom:breach_vanguard
    bossMob: custom:crimson_breachlord

  l11:
    minCityLevel: 110
    waves: 12
    guardianHp: 6500
    startCost: 1450000
    cooldownSeconds: 129600
    rewardXp: 9300
    rewardItems:
      DIAMOND: 36
      EMERALD: 64
      NETHERITE_SCRAP: 3
      BLAZE_ROD: 80
    baseMobCount: 22
    waveIncrement: 7
    bossSupportCount: 18
    eliteChance: 0.31
    regularMobs:
      - SKELETON
      - WITHER_SKELETON
      - custom:ash_cultist
      - custom:ash_hexer
    eliteMobs:
      - WITCH
      - custom:ash_revenant
    bossMob: custom:ash_behemoth

  l12:
    minCityLevel: 120
    waves: 13
    guardianHp: 7900
    startCost: 1900000
    cooldownSeconds: 151200
    rewardXp: 11800
    rewardItems:
      DIAMOND: 40
      EMERALD: 72
      ECHO_SHARD: 8
      NETHERITE_SCRAP: 4
    baseMobCount: 23
    waveIncrement: 7
    bossSupportCount: 19
    eliteChance: 0.33
    regularMobs:
      - SKELETON
      - WITHER_SKELETON
      - ENDERMAN
      - custom:ash_cultist
      - custom:ash_hexer
    eliteMobs:
      - WITCH
      - custom:ash_revenant
    bossMob: custom:ash_behemoth

  l13:
    minCityLevel: 130
    waves: 14
    guardianHp: 9500
    startCost: 2450000
    cooldownSeconds: 172800
    rewardXp: 14800
    rewardItems:
      DIAMOND: 44
      EMERALD: 80
      ECHO_SHARD: 12
      TOTEM_OF_UNDYING: 1
    baseMobCount: 25
    waveIncrement: 8
    bossSupportCount: 20
    eliteChance: 0.35
    regularMobs:
      - WITHER_SKELETON
      - ENDERMAN
      - custom:ash_cultist
      - custom:ash_hexer
    eliteMobs:
      - WITCH
      - custom:ash_revenant
      - PIGLIN_BRUTE
    bossMob: custom:cinder_hierophant

  l14:
    minCityLevel: 140
    waves: 14
    guardianHp: 11300
    startCost: 3100000
    cooldownSeconds: 194400
    rewardXp: 18300
    rewardItems:
      DIAMOND: 48
      EMERALD: 88
      NETHERITE_SCRAP: 5
      TOTEM_OF_UNDYING: 1
    baseMobCount: 26
    waveIncrement: 8
    bossSupportCount: 22
    eliteChance: 0.37
    regularMobs:
      - WITHER_SKELETON
      - ENDERMAN
      - PILLAGER
      - custom:ash_cultist
      - custom:ash_hexer
    eliteMobs:
      - WITCH
      - PIGLIN_BRUTE
      - custom:ash_revenant
    bossMob: custom:cinder_hierophant

  l15:
    minCityLevel: 150
    waves: 15
    guardianHp: 13300
    startCost: 3900000
    cooldownSeconds: 216000
    rewardXp: 22400
    rewardItems:
      DIAMOND: 52
      EMERALD: 96
      ECHO_SHARD: 16
      TOTEM_OF_UNDYING: 2
    baseMobCount: 28
    waveIncrement: 8
    bossSupportCount: 24
    eliteChance: 0.39
    regularMobs:
      - WITHER_SKELETON
      - ENDERMAN
      - custom:ash_cultist
      - custom:ash_hexer
    eliteMobs:
      - WITCH
      - PIGLIN_BRUTE
      - custom:ash_revenant
    bossMob: custom:cinder_hierophant

  l16:
    minCityLevel: 160
    waves: 15
    guardianHp: 15600
    startCost: 4800000
    cooldownSeconds: 237600
    rewardXp: 27200
    rewardItems:
      DIAMOND: 56
      EMERALD: 104
      NETHERITE_SCRAP: 6
      TOTEM_OF_UNDYING: 2
    baseMobCount: 30
    waveIncrement: 9
    bossSupportCount: 26
    eliteChance: 0.42
    regularMobs:
      - DROWNED
      - custom:tide_reaver
      - custom:omen_harpooner
    eliteMobs:
      - STRAY
      - custom:omen_channeler
    bossMob: custom:omen_tidebreaker

  l17:
    minCityLevel: 170
    waves: 16
    guardianHp: 18200
    startCost: 5900000
    cooldownSeconds: 259200
    rewardXp: 32800
    rewardItems:
      DIAMOND: 60
      EMERALD: 112
      ECHO_SHARD: 20
      NETHERITE_SCRAP: 6
    baseMobCount: 31
    waveIncrement: 9
    bossSupportCount: 28
    eliteChance: 0.44
    regularMobs:
      - DROWNED
      - STRAY
      - ENDERMAN
      - custom:tide_reaver
      - custom:omen_harpooner
    eliteMobs:
      - WITCH
      - custom:omen_channeler
    bossMob: custom:omen_tidebreaker

  l18:
    minCityLevel: 180
    waves: 17
    guardianHp: 21100
    startCost: 7200000
    cooldownSeconds: 302400
    rewardXp: 39200
    rewardItems:
      DIAMOND: 64
      EMERALD: 120
      TOTEM_OF_UNDYING: 2
      ENCHANTED_GOLDEN_APPLE: 1
    baseMobCount: 33
    waveIncrement: 9
    bossSupportCount: 30
    eliteChance: 0.46
    regularMobs:
      - DROWNED
      - ENDERMAN
      - STRAY
      - custom:tide_reaver
      - custom:omen_harpooner
    eliteMobs:
      - WITCH
      - WITHER_SKELETON
      - custom:omen_channeler
    bossMob: custom:abyssal_herald

  l19:
    minCityLevel: 190
    waves: 17
    guardianHp: 24400
    startCost: 8700000
    cooldownSeconds: 345600
    rewardXp: 46500
    rewardItems:
      DIAMOND: 68
      EMERALD: 128
      NETHERITE_SCRAP: 8
      ENCHANTED_GOLDEN_APPLE: 1
    baseMobCount: 35
    waveIncrement: 10
    bossSupportCount: 32
    eliteChance: 0.48
    regularMobs:
      - DROWNED
      - ENDERMAN
      - PILLAGER
      - custom:tide_reaver
      - custom:omen_harpooner
    eliteMobs:
      - WITCH
      - PIGLIN_BRUTE
      - custom:omen_channeler
    bossMob: custom:abyssal_herald

  l20:
    minCityLevel: 200
    waves: 18
    guardianHp: 28000
    startCost: 10400000
    cooldownSeconds: 388800
    rewardXp: 54800
    rewardItems:
      DIAMOND: 72
      EMERALD: 144
      ECHO_SHARD: 24
      TOTEM_OF_UNDYING: 3
    baseMobCount: 36
    waveIncrement: 10
    bossSupportCount: 34
    eliteChance: 0.50
    regularMobs:
      - DROWNED
      - ENDERMAN
      - WITHER_SKELETON
      - custom:tide_reaver
      - custom:omen_harpooner
    eliteMobs:
      - WITCH
      - PIGLIN_BRUTE
      - custom:omen_channeler
    bossMob: custom:abyssal_herald

  l21:
    minCityLevel: 210
    waves: 18
    guardianHp: 32000
    startCost: 12300000
    cooldownSeconds: 432000
    rewardXp: 64200
    rewardItems:
      DIAMOND: 80
      EMERALD: 160
      NETHERITE_SCRAP: 10
      ENCHANTED_GOLDEN_APPLE: 1
      TOTEM_OF_UNDYING: 3
    baseMobCount: 38
    waveIncrement: 10
    bossSupportCount: 36
    eliteChance: 0.52
    regularMobs:
      - WITHER_SKELETON
      - SKELETON
      - custom:throne_legionnaire
      - custom:throne_ballista
    eliteMobs:
      - PIGLIN_BRUTE
      - custom:doom_guard
    bossMob: custom:doom_juggernaut

  l22:
    minCityLevel: 220
    waves: 19
    guardianHp: 36500
    startCost: 14500000
    cooldownSeconds: 475200
    rewardXp: 74800
    rewardItems:
      DIAMOND: 88
      EMERALD: 176
      ECHO_SHARD: 32
      NETHERITE_SCRAP: 10
      TOTEM_OF_UNDYING: 3
    baseMobCount: 39
    waveIncrement: 11
    bossSupportCount: 38
    eliteChance: 0.54
    regularMobs:
      - WITHER_SKELETON
      - ENDERMAN
      - custom:throne_legionnaire
      - custom:throne_ballista
    eliteMobs:
      - PIGLIN_BRUTE
      - WITCH
      - custom:doom_guard
    bossMob: custom:doom_juggernaut

  l23:
    minCityLevel: 230
    waves: 20
    guardianHp: 41500
    startCost: 17000000
    cooldownSeconds: 518400
    rewardXp: 86700
    rewardItems:
      DIAMOND: 96
      EMERALD: 192
      NETHERITE_SCRAP: 12
      ENCHANTED_GOLDEN_APPLE: 2
      TOTEM_OF_UNDYING: 4
    baseMobCount: 41
    waveIncrement: 11
    bossSupportCount: 40
    eliteChance: 0.56
    regularMobs:
      - WITHER_SKELETON
      - ENDERMAN
      - custom:throne_legionnaire
      - custom:throne_ballista
    eliteMobs:
      - PIGLIN_BRUTE
      - WITCH
      - custom:doom_guard
    bossMob: custom:last_siege_king

  l24:
    minCityLevel: 240
    waves: 20
    guardianHp: 47000
    startCost: 19800000
    cooldownSeconds: 561600
    rewardXp: 100000
    rewardItems:
      DIAMOND: 112
      EMERALD: 224
      ECHO_SHARD: 40
      NETHERITE_SCRAP: 14
      TOTEM_OF_UNDYING: 4
    baseMobCount: 42
    waveIncrement: 12
    bossSupportCount: 42
    eliteChance: 0.58
    regularMobs:
      - WITHER_SKELETON
      - ENDERMAN
      - PILLAGER
      - custom:throne_legionnaire
      - custom:throne_ballista
    eliteMobs:
      - PIGLIN_BRUTE
      - WITCH
      - custom:doom_guard
    bossMob: custom:last_siege_king

  l25:
    minCityLevel: 250
    waves: 22
    guardianHp: 53000
    startCost: 23000000
    cooldownSeconds: 604800
    rewardXp: 115000
    rewardItems:
      DIAMOND: 128
      EMERALD: 256
      ECHO_SHARD: 48
      NETHERITE_SCRAP: 16
      ENCHANTED_GOLDEN_APPLE: 3
      TOTEM_OF_UNDYING: 5
    baseMobCount: 44
    waveIncrement: 12
    bossSupportCount: 46
    eliteChance: 0.60
    regularMobs:
      - WITHER_SKELETON
      - ENDERMAN
      - custom:throne_legionnaire
      - custom:throne_ballista
      - custom:doom_guard
    eliteMobs:
      - PIGLIN_BRUTE
      - WITCH
      - custom:doom_guard
    bossMob: custom:last_siege_king
```

5. Custom Mobs

FILE: src/main/resources/custom-mobs/frontier_raider.yml

```yml id="u1ngci"
id: frontier_raider
type: ZOMBIE
name: "<gray>Predone di Frontiera</gray>"
showName: true
boss: false
packId: cittaexp_defense_v3
arc: frontier
pools:
  - defense
  - regular
themes:
  - frontier
  - siege
stats:
  GENERIC_MAX_HEALTH: 32.0
  GENERIC_ATTACK_DAMAGE: 5.0
  GENERIC_MOVEMENT_SPEED: 0.26
equipment:
  helmet: IRON_HELMET
  chestplate: LEATHER_CHESTPLATE
  leggings: LEATHER_LEGGINGS
  boots: LEATHER_BOOTS
  mainHand: IRON_SWORD
flags:
  silent: false
  collidable: true
  invulnerable: false
tags:
  - frontier
  - regular
mainAbilities: []
secondaryAbilities: []
passiveAbilities:
  - defense_spawn_presence
traits:
  - trait_guardian_hunter
variants:
  - variant_veteran
```

FILE: src/main/resources/custom-mobs/frontier_bolter.yml

```yml id="k6q6l6"
id: frontier_bolter
type: SKELETON
name: "<gray>Balestriere di Frontiera</gray>"
showName: true
boss: false
packId: cittaexp_defense_v3
arc: frontier
pools:
  - defense
  - regular
themes:
  - frontier
  - siege
stats:
  GENERIC_MAX_HEALTH: 28.0
  GENERIC_ATTACK_DAMAGE: 4.0
  GENERIC_MOVEMENT_SPEED: 0.25
equipment:
  helmet: CHAINMAIL_HELMET
  chestplate: LEATHER_CHESTPLATE
  leggings: LEATHER_LEGGINGS
  boots: CHAINMAIL_BOOTS
  mainHand: CROSSBOW
flags:
  silent: false
  collidable: true
  invulnerable: false
tags:
  - frontier
  - ranged
mainAbilities: []
secondaryAbilities: []
passiveAbilities:
  - defense_spawn_presence
traits:
  - trait_marksman
variants:
  - variant_bastion
```

FILE: src/main/resources/custom-mobs/frontier_shieldbearer.yml

```yml id="nkt0fe"
id: frontier_shieldbearer
type: HUSK
name: "<gold>Portascudi del Confine</gold>"
showName: true
boss: false
packId: cittaexp_defense_v3
arc: frontier
pools:
  - defense
  - elite
themes:
  - frontier
  - siege
stats:
  GENERIC_MAX_HEALTH: 48.0
  GENERIC_ATTACK_DAMAGE: 7.0
  GENERIC_MOVEMENT_SPEED: 0.24
equipment:
  helmet: IRON_HELMET
  chestplate: IRON_CHESTPLATE
  leggings: IRON_LEGGINGS
  boots: IRON_BOOTS
  mainHand: IRON_AXE
flags:
  silent: false
  collidable: true
  invulnerable: false
tags:
  - frontier
  - elite
mainAbilities: []
secondaryAbilities: []
passiveAbilities:
  - defense_spawn_presence
traits:
  - trait_guardian_hunter
  - trait_breacher
  - trait_overseer
variants:
  - variant_ironclad
  - variant_bastion
```

FILE: src/main/resources/custom-mobs/frontier_ram.yml

```yml id="ex4fhk"
id: frontier_ram
type: RAVAGER
name: "<dark_red>Ariete del Confine</dark_red>"
showName: true
boss: false
packId: cittaexp_defense_v3
arc: frontier
pools:
  - defense
  - miniboss
themes:
  - frontier
  - siege
stats:
  GENERIC_MAX_HEALTH: 140.0
  GENERIC_ATTACK_DAMAGE: 10.0
  GENERIC_MOVEMENT_SPEED: 0.30
equipment: {}
flags:
  silent: false
  collidable: true
  invulnerable: false
tags:
  - frontier
  - miniboss
mainAbilities:
  - defense_breach_slam
  - defense_guardian_sunder
secondaryAbilities: []
passiveAbilities:
  - defense_spawn_presence
traits: []
variants:
  - variant_bastion
```

FILE: src/main/resources/custom-mobs/frontier_war_ram.yml

```yml id="nv2h8v"
id: frontier_war_ram
type: RAVAGER
name: "<dark_red>Ariete di Guerra</dark_red>"
showName: true
boss: true
packId: cittaexp_defense_v3
arc: frontier
pools:
  - defense
  - boss
themes:
  - frontier
  - siege
stats:
  GENERIC_MAX_HEALTH: 240.0
  GENERIC_ATTACK_DAMAGE: 14.0
  GENERIC_MOVEMENT_SPEED: 0.33
equipment: {}
flags:
  silent: false
  collidable: true
  invulnerable: false
tags:
  - frontier
  - boss
mainAbilities:
  - defense_guardian_sunder
  - defense_breach_slam
secondaryAbilities: []
passiveAbilities:
  - defense_spawn_presence
traits: []
variants:
  - variant_bastion
  - variant_ironclad
bossbar:
  enabled: true
  title: "<dark_red>{boss}</dark_red> <gray>· {phase}</gray>"
  color: RED
  overlay: PROGRESS
localBroadcasts:
  spawn: "<dark_red>Un ariete di guerra si abbatte sulle mura della città.</dark_red>"
  phase: "<gold>L'ariete di guerra entra in una nuova fase di furia.</gold>"
  defeat: "<green>L'ariete di guerra crolla tra le macerie.</green>"
phaseBindings:
  - phase: 1
    id: ram_phase_i
    label: Fase I
    hpBelow: 1.0
    addAbilities: []
    removeAbilities: []
    entryActions:
      - type: particles
        particle: SMOKE
        count: 30
  - phase: 2
    id: ram_phase_ii
    label: Fase II
    hpBelow: 0.66
    addAbilities:
      - defense_frontier_reinforce
    removeAbilities: []
    namePrefix: "<red>Furente</red>"
    entryActions:
      - type: sound
        sound: ENTITY_RAVAGER_ROAR
        volume: 1.4
        pitch: 0.8
  - phase: 3
    id: ram_phase_iii
    label: Fase III
    hpBelow: 0.33
    addAbilities:
      - defense_final_pulse
      - defense_phase_rally
    removeAbilities: []
    nameSuffix: "<dark_red>della Rovina</dark_red>"
    entryActions:
      - type: particles
        particle: EXPLOSION
        count: 12
```

FILE: src/main/resources/custom-mobs/breach_sapper.yml

```yml id="dohccy"
id: breach_sapper
type: ZOMBIE
name: "<red>Zappatore Cremisi</red>"
showName: true
boss: false
packId: cittaexp_defense_v3
arc: breach
pools:
  - defense
  - regular
themes:
  - breach
  - siege
stats:
  GENERIC_MAX_HEALTH: 36.0
  GENERIC_ATTACK_DAMAGE: 6.0
  GENERIC_MOVEMENT_SPEED: 0.27
equipment:
  helmet: CHAINMAIL_HELMET
  chestplate: IRON_CHESTPLATE
  leggings: CHAINMAIL_LEGGINGS
  boots: IRON_BOOTS
  mainHand: STONE_AXE
flags:
  silent: false
  collidable: true
  invulnerable: false
tags:
  - breach
  - sapper
mainAbilities: []
secondaryAbilities: []
passiveAbilities:
  - defense_spawn_presence
traits:
  - trait_guardian_hunter
  - trait_sapper
variants:
  - variant_scorched
```

FILE: src/main/resources/custom-mobs/breach_marksman.yml

```yml id="q03omk"
id: breach_marksman
type: STRAY
name: "<red>Tiratore della Breccia</red>"
showName: true
boss: false
packId: cittaexp_defense_v3
arc: breach
pools:
  - defense
  - regular
themes:
  - breach
  - siege
stats:
  GENERIC_MAX_HEALTH: 34.0
  GENERIC_ATTACK_DAMAGE: 5.0
  GENERIC_MOVEMENT_SPEED: 0.26
equipment:
  helmet: CHAINMAIL_HELMET
  chestplate: CHAINMAIL_CHESTPLATE
  leggings: IRON_LEGGINGS
  boots: CHAINMAIL_BOOTS
  mainHand: CROSSBOW
flags:
  silent: false
  collidable: true
  invulnerable: false
tags:
  - breach
  - ranged
mainAbilities:
  - defense_anchor_bolt
secondaryAbilities: []
passiveAbilities:
  - defense_spawn_presence
traits:
  - trait_marksman
variants:
  - variant_scorched
```

FILE: src/main/resources/custom-mobs/breach_vanguard.yml

```yml id="crwdgv"
id: breach_vanguard
type: VINDICATOR
name: "<gold>Avanguardia Cremisi</gold>"
showName: true
boss: false
packId: cittaexp_defense_v3
arc: breach
pools:
  - defense
  - elite
themes:
  - breach
  - siege
stats:
  GENERIC_MAX_HEALTH: 58.0
  GENERIC_ATTACK_DAMAGE: 8.0
  GENERIC_MOVEMENT_SPEED: 0.29
equipment:
  helmet: IRON_HELMET
  chestplate: IRON_CHESTPLATE
  leggings: IRON_LEGGINGS
  boots: IRON_BOOTS
  mainHand: IRON_AXE
flags:
  silent: false
  collidable: true
  invulnerable: false
tags:
  - breach
  - elite
mainAbilities: []
secondaryAbilities: []
passiveAbilities:
  - defense_spawn_presence
traits:
  - trait_breacher
  - trait_overseer
variants:
  - variant_ironclad
  - variant_scorched
```

FILE: src/main/resources/custom-mobs/crimson_breach_brute.yml

```yml id="vjlwmu"
id: crimson_breach_brute
type: PIGLIN_BRUTE
name: "<dark_red>Bruto della Breccia</dark_red>"
showName: true
boss: false
packId: cittaexp_defense_v3
arc: breach
pools:
  - defense
  - miniboss
themes:
  - breach
  - siege
stats:
  GENERIC_MAX_HEALTH: 150.0
  GENERIC_ATTACK_DAMAGE: 12.0
  GENERIC_MOVEMENT_SPEED: 0.31
equipment:
  helmet: GOLDEN_HELMET
  chestplate: IRON_CHESTPLATE
  leggings: CHAINMAIL_LEGGINGS
  boots: GOLDEN_BOOTS
  mainHand: GOLDEN_AXE
flags:
  silent: false
  collidable: true
  invulnerable: false
tags:
  - breach
  - miniboss
mainAbilities:
  - defense_breach_slam
  - defense_guardian_sunder
  - defense_phase_rally
secondaryAbilities: []
passiveAbilities:
  - defense_spawn_presence
traits: []
variants:
  - variant_scorched
  - variant_ironclad
```

FILE: src/main/resources/custom-mobs/crimson_breachlord.yml

```yml id="w16hqo"
id: crimson_breachlord
type: PIGLIN_BRUTE
name: "<dark_red>Signore della Breccia</dark_red>"
showName: true
boss: true
packId: cittaexp_defense_v3
arc: breach
pools:
  - defense
  - boss
themes:
  - breach
  - siege
stats:
  GENERIC_MAX_HEALTH: 280.0
  GENERIC_ATTACK_DAMAGE: 16.0
  GENERIC_MOVEMENT_SPEED: 0.33
equipment:
  helmet: GOLDEN_HELMET
  chestplate: DIAMOND_CHESTPLATE
  leggings: DIAMOND_LEGGINGS
  boots: GOLDEN_BOOTS
  mainHand: GOLDEN_AXE
flags:
  silent: false
  collidable: true
  invulnerable: false
tags:
  - breach
  - boss
mainAbilities:
  - defense_guardian_sunder
  - defense_breach_slam
secondaryAbilities:
  - defense_royal_barrage
passiveAbilities:
  - defense_spawn_presence
traits: []
variants:
  - variant_scorched
  - variant_royal
bossbar:
  enabled: true
  title: "<dark_red>{boss}</dark_red> <gray>· {phase}</gray>"
  color: RED
  overlay: PROGRESS
localBroadcasts:
  spawn: "<dark_red>Il Signore della Breccia guida l'assalto contro la città.</dark_red>"
  phase: "<gold>Il Signore della Breccia cambia tattica e spinge più a fondo.</gold>"
  defeat: "<green>Il Signore della Breccia cade tra le rovine del varco.</green>"
phaseBindings:
  - phase: 1
    id: breachlord_phase_i
    label: Fase I
    hpBelow: 1.0
    addAbilities: []
    removeAbilities: []
    entryActions:
      - type: particles
        particle: CRIT
        count: 24
  - phase: 2
    id: breachlord_phase_ii
    label: Fase II
    hpBelow: 0.66
    addAbilities:
      - defense_breach_reinforce
    removeAbilities: []
    namePrefix: "<red>Fendente</red>"
    entryActions:
      - type: sound
        sound: ENTITY_PIGLIN_BRUTE_ANGRY
        volume: 1.3
        pitch: 0.9
  - phase: 3
    id: breachlord_phase_iii
    label: Fase III
    hpBelow: 0.33
    addAbilities:
      - defense_final_pulse
      - defense_phase_rally
    removeAbilities: []
    nameSuffix: "<dark_red>del Varco</dark_red>"
    entryActions:
      - type: particles
        particle: EXPLOSION
        count: 12
```

FILE: src/main/resources/custom-mobs/ash_cultist.yml

```yml id="1lvi8n"
id: ash_cultist
type: SKELETON
name: "<dark_gray>Cultista di Cenere</dark_gray>"
showName: true
boss: false
packId: cittaexp_defense_v3
arc: ash
pools:
  - defense
  - regular
themes:
  - ash
  - ritual
stats:
  GENERIC_MAX_HEALTH: 40.0
  GENERIC_ATTACK_DAMAGE: 6.0
  GENERIC_MOVEMENT_SPEED: 0.26
equipment:
  helmet: LEATHER_HELMET
  chestplate: CHAINMAIL_CHESTPLATE
  leggings: LEATHER_LEGGINGS
  boots: CHAINMAIL_BOOTS
  mainHand: BOW
flags:
  silent: false
  collidable: true
  invulnerable: false
tags:
  - ash
  - ritual
mainAbilities: []
secondaryAbilities: []
passiveAbilities:
  - defense_spawn_presence
traits:
  - trait_ritualist
variants:
  - variant_ashen
```

FILE: src/main/resources/custom-mobs/ash_hexer.yml

```yml id="azpwxk"
id: ash_hexer
type: WITCH
name: "<dark_gray>Esegeta di Cenere</dark_gray>"
showName: true
boss: false
packId: cittaexp_defense_v3
arc: ash
pools:
  - defense
  - regular
themes:
  - ash
  - ritual
stats:
  GENERIC_MAX_HEALTH: 38.0
  GENERIC_ATTACK_DAMAGE: 5.0
  GENERIC_MOVEMENT_SPEED: 0.25
equipment: {}
flags:
  silent: false
  collidable: true
  invulnerable: false
tags:
  - ash
  - support
mainAbilities: []
secondaryAbilities: []
passiveAbilities:
  - defense_spawn_presence
traits:
  - trait_hexer
  - trait_ritualist
variants:
  - variant_ashen
```

FILE: src/main/resources/custom-mobs/ash_revenant.yml

```yml id="yaz3jr"
id: ash_revenant
type: WITHER_SKELETON
name: "<gold>Revenant del Braciere</gold>"
showName: true
boss: false
packId: cittaexp_defense_v3
arc: ash
pools:
  - defense
  - elite
themes:
  - ash
  - ritual
stats:
  GENERIC_MAX_HEALTH: 70.0
  GENERIC_ATTACK_DAMAGE: 10.0
  GENERIC_MOVEMENT_SPEED: 0.29
equipment:
  helmet: IRON_HELMET
  chestplate: DIAMOND_CHESTPLATE
  leggings: IRON_LEGGINGS
  boots: IRON_BOOTS
  mainHand: STONE_SWORD
flags:
  silent: false
  collidable: true
  invulnerable: false
tags:
  - ash
  - elite
mainAbilities: []
secondaryAbilities: []
passiveAbilities:
  - defense_spawn_presence
traits:
  - trait_guardian_hunter
  - trait_breacher
variants:
  - variant_ashen
  - variant_ironclad
```

FILE: src/main/resources/custom-mobs/ash_behemoth.yml

```yml id="3c0jlwm"
id: ash_behemoth
type: RAVAGER
name: "<dark_red>Behemoth di Cenere</dark_red>"
showName: true
boss: false
packId: cittaexp_defense_v3
arc: ash
pools:
  - defense
  - miniboss
themes:
  - ash
  - ritual
stats:
  GENERIC_MAX_HEALTH: 190.0
  GENERIC_ATTACK_DAMAGE: 13.0
  GENERIC_MOVEMENT_SPEED: 0.31
equipment: {}
flags:
  silent: false
  collidable: true
  invulnerable: false
tags:
  - ash
  - miniboss
mainAbilities:
  - defense_ritual_burst
  - defense_guardian_sunder
secondaryAbilities: []
passiveAbilities:
  - defense_spawn_presence
traits: []
variants:
  - variant_ashen
```

FILE: src/main/resources/custom-mobs/cinder_hierophant.yml

```yml id="n8axrg"
id: cinder_hierophant
type: EVOKER
name: "<dark_red>Ierofante Cinerico</dark_red>"
showName: true
boss: true
packId: cittaexp_defense_v3
arc: ash
pools:
  - defense
  - boss
themes:
  - ash
  - ritual
stats:
  GENERIC_MAX_HEALTH: 320.0
  GENERIC_ATTACK_DAMAGE: 8.0
  GENERIC_MOVEMENT_SPEED: 0.30
equipment: {}
flags:
  silent: false
  collidable: true
  invulnerable: false
tags:
  - ash
  - boss
mainAbilities:
  - defense_ritual_burst
  - defense_hex_fog
secondaryAbilities: []
passiveAbilities:
  - defense_spawn_presence
traits: []
variants:
  - variant_ashen
bossbar:
  enabled: true
  title: "<dark_red>{boss}</dark_red> <gray>· {phase}</gray>"
  color: PURPLE
  overlay: PROGRESS
localBroadcasts:
  spawn: "<dark_red>L'Ierofante Cinerico apre un rito davanti alle mura della città.</dark_red>"
  phase: "<gold>Il rito si approfondisce e la cenere prende forma.</gold>"
  defeat: "<green>L'Ierofante Cinerico viene spezzato e il rito si dissolve.</green>"
phaseBindings:
  - phase: 1
    id: cinder_phase_i
    label: Fase I
    hpBelow: 1.0
    addAbilities: []
    removeAbilities: []
    entryActions:
      - type: particles
        particle: SOUL
        count: 20
  - phase: 2
    id: cinder_phase_ii
    label: Fase II
    hpBelow: 0.66
    addAbilities:
      - defense_ash_reinforce
    removeAbilities: []
    namePrefix: "<red>Rituale</red>"
    entryActions:
      - type: sound
        sound: ENTITY_EVOKER_CAST_SPELL
        volume: 1.2
        pitch: 0.8
  - phase: 3
    id: cinder_phase_iii
    label: Fase III
    hpBelow: 0.33
    addAbilities:
      - defense_final_pulse
      - defense_phase_rally
    removeAbilities: []
    nameSuffix: "<dark_red>del Braciere</dark_red>"
    entryActions:
      - type: particles
        particle: FLAME
        count: 28
```

FILE: src/main/resources/custom-mobs/tide_reaver.yml

```yml id="6t78fl"
id: tide_reaver
type: DROWNED
name: "<aqua>Predatore di Risacca</aqua>"
showName: true
boss: false
packId: cittaexp_defense_v3
arc: omen
pools:
  - defense
  - regular
themes:
  - omen
  - tide
stats:
  GENERIC_MAX_HEALTH: 48.0
  GENERIC_ATTACK_DAMAGE: 7.0
  GENERIC_MOVEMENT_SPEED: 0.27
equipment:
  helmet: CHAINMAIL_HELMET
  chestplate: CHAINMAIL_CHESTPLATE
  leggings: IRON_LEGGINGS
  boots: CHAINMAIL_BOOTS
  mainHand: IRON_SWORD
flags:
  silent: false
  collidable: true
  invulnerable: false
tags:
  - omen
  - tide
mainAbilities: []
secondaryAbilities: []
passiveAbilities:
  - defense_spawn_presence
traits:
  - trait_guardian_hunter
  - trait_tidecaller
variants:
  - variant_abyssal
```

FILE: src/main/resources/custom-mobs/omen_harpooner.yml

```yml id="wkfi5j"
id: omen_harpooner
type: DROWNED
name: "<aqua>Arpioniere del Presagio</aqua>"
showName: true
boss: false
packId: cittaexp_defense_v3
arc: omen
pools:
  - defense
  - regular
themes:
  - omen
  - tide
stats:
  GENERIC_MAX_HEALTH: 42.0
  GENERIC_ATTACK_DAMAGE: 6.0
  GENERIC_MOVEMENT_SPEED: 0.27
equipment:
  helmet: TURTLE_HELMET
  chestplate: CHAINMAIL_CHESTPLATE
  leggings: CHAINMAIL_LEGGINGS
  boots: IRON_BOOTS
  mainHand: TRIDENT
flags:
  silent: false
  collidable: true
  invulnerable: false
tags:
  - omen
  - ranged
mainAbilities:
  - defense_anchor_bolt
secondaryAbilities: []
passiveAbilities:
  - defense_spawn_presence
traits: []
variants:
  - variant_abyssal
```

FILE: src/main/resources/custom-mobs/omen_channeler.yml

```yml id="8u75ip"
id: omen_channeler
type: DROWNED
name: "<gold>Canalizzatore Abissale</gold>"
showName: true
boss: false
packId: cittaexp_defense_v3
arc: omen
pools:
  - defense
  - elite
themes:
  - omen
  - tide
stats:
  GENERIC_MAX_HEALTH: 64.0
  GENERIC_ATTACK_DAMAGE: 8.0
  GENERIC_MOVEMENT_SPEED: 0.28
equipment:
  helmet: TURTLE_HELMET
  chestplate: DIAMOND_CHESTPLATE
  leggings: CHAINMAIL_LEGGINGS
  boots: DIAMOND_BOOTS
  mainHand: TRIDENT
flags:
  silent: false
  collidable: true
  invulnerable: false
tags:
  - omen
  - elite
mainAbilities: []
secondaryAbilities: []
passiveAbilities:
  - defense_spawn_presence
traits:
  - trait_hexer
  - trait_tidecaller
variants:
  - variant_abyssal
  - variant_ironclad
```

FILE: src/main/resources/custom-mobs/omen_tidebreaker.yml

```yml id="5m0gi7"
id: omen_tidebreaker
type: RAVAGER
name: "<dark_aqua>Frangimarea dell'Omen</dark_aqua>"
showName: true
boss: false
packId: cittaexp_defense_v3
arc: omen
pools:
  - defense
  - miniboss
themes:
  - omen
  - tide
stats:
  GENERIC_MAX_HEALTH: 220.0
  GENERIC_ATTACK_DAMAGE: 14.0
  GENERIC_MOVEMENT_SPEED: 0.32
equipment: {}
flags:
  silent: false
  collidable: true
  invulnerable: false
tags:
  - omen
  - miniboss
mainAbilities:
  - defense_dragnet_pull
  - defense_guardian_sunder
secondaryAbilities: []
passiveAbilities:
  - defense_spawn_presence
traits: []
variants:
  - variant_abyssal
```

FILE: src/main/resources/custom-mobs/abyssal_herald.yml

```yml id="jxl90d"
id: abyssal_herald
type: DROWNED
name: "<dark_aqua>Araldo Abissale</dark_aqua>"
showName: true
boss: true
packId: cittaexp_defense_v3
arc: omen
pools:
  - defense
  - boss
themes:
  - omen
  - tide
stats:
  GENERIC_MAX_HEALTH: 360.0
  GENERIC_ATTACK_DAMAGE: 12.0
  GENERIC_MOVEMENT_SPEED: 0.30
equipment:
  helmet: TURTLE_HELMET
  chestplate: DIAMOND_CHESTPLATE
  leggings: DIAMOND_LEGGINGS
  boots: DIAMOND_BOOTS
  mainHand: TRIDENT
flags:
  silent: false
  collidable: true
  invulnerable: false
tags:
  - omen
  - boss
mainAbilities:
  - defense_anchor_bolt
  - defense_dragnet_pull
secondaryAbilities: []
passiveAbilities:
  - defense_spawn_presence
traits: []
variants:
  - variant_abyssal
bossbar:
  enabled: true
  title: "<dark_aqua>{boss}</dark_aqua> <gray>· {phase}</gray>"
  color: BLUE
  overlay: PROGRESS
localBroadcasts:
  spawn: "<dark_aqua>L'Araldo Abissale emerge per trascinare la città nel fondo.</dark_aqua>"
  phase: "<gold>L'Araldo Abissale muta ritmo e richiama la corrente.</gold>"
  defeat: "<green>L'Araldo Abissale viene ricacciato nella sua marea.</green>"
phaseBindings:
  - phase: 1
    id: herald_phase_i
    label: Fase I
    hpBelow: 1.0
    addAbilities: []
    removeAbilities: []
    entryActions:
      - type: particles
        particle: SQUID_INK
        count: 20
  - phase: 2
    id: herald_phase_ii
    label: Fase II
    hpBelow: 0.66
    addAbilities:
      - defense_tide_reinforce
    removeAbilities: []
    namePrefix: "<aqua>Profondo</aqua>"
    entryActions:
      - type: sound
        sound: ENTITY_DROWNED_AMBIENT
        volume: 1.3
        pitch: 0.7
  - phase: 3
    id: herald_phase_iii
    label: Fase III
    hpBelow: 0.33
    addAbilities:
      - defense_final_pulse
      - defense_phase_rally
    removeAbilities: []
    nameSuffix: "<dark_aqua>della Marea</dark_aqua>"
    entryActions:
      - type: particles
        particle: CLOUD
        count: 28
```

FILE: src/main/resources/custom-mobs/throne_legionnaire.yml

```yml id="rqvaxd"
id: throne_legionnaire
type: WITHER_SKELETON
name: "<gray>Legionario del Trono</gray>"
showName: true
boss: false
packId: cittaexp_defense_v3
arc: throne
pools:
  - defense
  - regular
themes:
  - throne
  - apocalypse
stats:
  GENERIC_MAX_HEALTH: 72.0
  GENERIC_ATTACK_DAMAGE: 11.0
  GENERIC_MOVEMENT_SPEED: 0.30
equipment:
  helmet: DIAMOND_HELMET
  chestplate: DIAMOND_CHESTPLATE
  leggings: IRON_LEGGINGS
  boots: DIAMOND_BOOTS
  mainHand: IRON_SWORD
flags:
  silent: false
  collidable: true
  invulnerable: false
tags:
  - throne
  - regular
mainAbilities: []
secondaryAbilities: []
passiveAbilities:
  - defense_spawn_presence
traits:
  - trait_guardian_hunter
  - trait_overseer
variants:
  - variant_royal
```

FILE: src/main/resources/custom-mobs/throne_ballista.yml

```yml id="hnx38x"
id: throne_ballista
type: SKELETON
name: "<gray>Ballista del Trono</gray>"
showName: true
boss: false
packId: cittaexp_defense_v3
arc: throne
pools:
  - defense
  - regular
themes:
  - throne
  - apocalypse
stats:
  GENERIC_MAX_HEALTH: 56.0
  GENERIC_ATTACK_DAMAGE: 7.0
  GENERIC_MOVEMENT_SPEED: 0.27
equipment:
  helmet: DIAMOND_HELMET
  chestplate: IRON_CHESTPLATE
  leggings: DIAMOND_LEGGINGS
  boots: IRON_BOOTS
  mainHand: CROSSBOW
flags:
  silent: false
  collidable: true
  invulnerable: false
tags:
  - throne
  - ranged
mainAbilities:
  - defense_royal_barrage
secondaryAbilities: []
passiveAbilities:
  - defense_spawn_presence
traits:
  - trait_marksman
variants:
  - variant_royal
```

FILE: src/main/resources/custom-mobs/doom_guard.yml

```yml id="pjlwmk"
id: doom_guard
type: PIGLIN_BRUTE
name: "<gold>Guardia del Tramonto</gold>"
showName: true
boss: false
packId: cittaexp_defense_v3
arc: throne
pools:
  - defense
  - elite
themes:
  - throne
  - apocalypse
stats:
  GENERIC_MAX_HEALTH: 96.0
  GENERIC_ATTACK_DAMAGE: 13.0
  GENERIC_MOVEMENT_SPEED: 0.31
equipment:
  helmet: GOLDEN_HELMET
  chestplate: DIAMOND_CHESTPLATE
  leggings: DIAMOND_LEGGINGS
  boots: GOLDEN_BOOTS
  mainHand: GOLDEN_AXE
flags:
  silent: false
  collidable: true
  invulnerable: false
tags:
  - throne
  - elite
mainAbilities: []
secondaryAbilities: []
passiveAbilities:
  - defense_spawn_presence
traits:
  - trait_breacher
  - trait_overseer
variants:
  - variant_royal
  - variant_last_siege
```

FILE: src/main/resources/custom-mobs/doom_juggernaut.yml

```yml id="2gl1rk"
id: doom_juggernaut
type: RAVAGER
name: "<dark_red>Juggernaut del Tramonto</dark_red>"
showName: true
boss: false
packId: cittaexp_defense_v3
arc: throne
pools:
  - defense
  - miniboss
themes:
  - throne
  - apocalypse
stats:
  GENERIC_MAX_HEALTH: 300.0
  GENERIC_ATTACK_DAMAGE: 16.0
  GENERIC_MOVEMENT_SPEED: 0.33
equipment: {}
flags:
  silent: false
  collidable: true
  invulnerable: false
tags:
  - throne
  - miniboss
mainAbilities:
  - defense_breach_slam
  - defense_guardian_sunder
  - defense_phase_rally
secondaryAbilities: []
passiveAbilities:
  - defense_spawn_presence
traits: []
variants:
  - variant_last_siege
```

FILE: src/main/resources/custom-mobs/last_siege_king.yml

```yml id="8pqfup"
id: last_siege_king
type: WARDEN
name: "<dark_red>Re dell'Ultimo Assedio</dark_red>"
showName: true
boss: true
packId: cittaexp_defense_v3
arc: throne
pools:
  - defense
  - boss
themes:
  - throne
  - apocalypse
stats:
  GENERIC_MAX_HEALTH: 520.0
  GENERIC_ATTACK_DAMAGE: 18.0
  GENERIC_MOVEMENT_SPEED: 0.32
equipment: {}
flags:
  silent: false
  collidable: true
  invulnerable: false
tags:
  - throne
  - boss
mainAbilities:
  - defense_guardian_sunder
  - defense_royal_barrage
secondaryAbilities: []
passiveAbilities:
  - defense_spawn_presence
traits: []
variants:
  - variant_royal
  - variant_last_siege
bossbar:
  enabled: true
  title: "<dark_red>{boss}</dark_red> <gray>· {phase}</gray>"
  color: RED
  overlay: PROGRESS
localBroadcasts:
  spawn: "<dark_red>Il Re dell'Ultimo Assedio avanza verso il cuore della città.</dark_red>"
  phase: "<gold>Il Re dell'Ultimo Assedio muta forma di guerra.</gold>"
  defeat: "<green>Il Re dell'Ultimo Assedio cade. La città ha resistito all'apice dell'invasione.</green>"
phaseBindings:
  - phase: 1
    id: king_phase_i
    label: Fase I
    hpBelow: 1.0
    addAbilities: []
    removeAbilities: []
    entryActions:
      - type: particles
        particle: SMOKE
        count: 24
  - phase: 2
    id: king_phase_ii
    label: Fase II
    hpBelow: 0.66
    addAbilities:
      - defense_throne_reinforce
      - defense_phase_rally
    removeAbilities: []
    namePrefix: "<red>Imperiale</red>"
    entryActions:
      - type: sound
        sound: ENTITY_WARDEN_ROAR
        volume: 1.5
        pitch: 0.8
  - phase: 3
    id: king_phase_iii
    label: Fase III
    hpBelow: 0.33
    addAbilities:
      - defense_final_pulse
    removeAbilities: []
    nameSuffix: "<dark_red>del Tramonto</dark_red>"
    entryActions:
      - type: particles
        particle: EXPLOSION
        count: 16
```

6. Mob Abilities

FILE: src/main/resources/mob-abilities/defense_spawn_presence.yml

```yml id="iw2y67"
id: defense_spawn_presence
trigger: ON_SPAWN
cooldownTicks: 0
targeting:
  type: SELF
actions:
  - type: particles
    particle: SMOKE
    count: 10
  - type: sound
    sound: ENTITY_ZOMBIE_AMBIENT
    volume: 0.8
    pitch: 0.9
```

FILE: src/main/resources/mob-abilities/defense_guardian_strike.yml

```yml id="l9j2zc"
id: defense_guardian_strike
trigger: ON_TARGET
cooldownTicks: 100
targeting:
  type: GUARDIAN
conditions:
  - type: targetExists
actions:
  - type: particles
    particle: CRIT
    count: 8
  - type: sound
    sound: ENTITY_ZOMBIE_ATTACK_IRON_DOOR
    volume: 1.0
    pitch: 0.9
  - type: directDamage
    amount: 4.0
```

FILE: src/main/resources/mob-abilities/defense_crossbow_volley.yml

```yml id="v78zir"
id: defense_crossbow_volley
trigger: ON_TARGET
cooldownTicks: 80
targeting:
  type: CURRENT_TARGET
conditions:
  - type: targetExists
actions:
  - type: sound
    sound: ITEM_CROSSBOW_LOADING_END
    volume: 1.0
    pitch: 1.0
  - type: launchProjectile
    projectile: ARROW
    speed: 1.8
    spread: 0.08
    count: 1
```

FILE: src/main/resources/mob-abilities/defense_breach_slam.yml

```yml id="bqs2xt"
id: defense_breach_slam
trigger: ON_HIT
cooldownTicks: 70
targeting:
  type: CURRENT_TARGET
conditions:
  - type: targetExists
actions:
  - type: particles
    particle: CLOUD
    count: 14
  - type: sound
    sound: ENTITY_RAVAGER_ROAR
    volume: 1.2
    pitch: 0.9
  - type: directDamage
    amount: 3.0
  - type: knockback
    strength: 1.6
  - type: push
    strength: 0.8
```

FILE: src/main/resources/mob-abilities/defense_sapper_blast.yml

```yml id="l6x7hi"
id: defense_sapper_blast
trigger: ON_DEATH
cooldownTicks: 0
targeting:
  type: PLAYERS_IN_RADIUS
  radius: 4
  maxTargets: 6
actions:
  - type: particles
    particle: EXPLOSION
    count: 8
  - type: sound
    sound: ENTITY_GENERIC_EXPLODE
    volume: 1.1
    pitch: 1.0
  - type: directDamage
    amount: 6.0
  - type: push
    strength: 1.2
```

FILE: src/main/resources/mob-abilities/defense_hex_fog.yml

```yml id="vjdjlwm"
id: defense_hex_fog
trigger: ON_HURT
cooldownTicks: 120
targeting:
  type: PLAYERS_IN_RADIUS
  radius: 6
  maxTargets: 3
conditions:
  - type: hpBelow
    ratio: 0.85
actions:
  - type: particles
    particle: SMOKE
    count: 18
  - type: sound
    sound: ENTITY_WITCH_THROW
    volume: 1.0
    pitch: 0.9
  - type: applyPotion
    potion: SLOW
    durationTicks: 80
    amplifier: 1
  - type: applyPotion
    potion: WEAKNESS
    durationTicks: 80
    amplifier: 0
```

FILE: src/main/resources/mob-abilities/defense_ritual_burst.yml

```yml id="s42e8w"
id: defense_ritual_burst
trigger: ON_IDLE
cooldownTicks: 140
targeting:
  type: PLAYERS_IN_RADIUS
  radius: 7
  maxTargets: 4
conditions:
  - type: waveAtLeast
    wave: 2
actions:
  - type: particles
    particle: SOUL
    count: 16
  - type: sound
    sound: ENTITY_EVOKER_CAST_SPELL
    volume: 1.0
    pitch: 0.8
  - type: directDamage
    amount: 4.0
  - type: applyPotion
    potion: BLINDNESS
    durationTicks: 40
    amplifier: 0
```

FILE: src/main/resources/mob-abilities/defense_dragnet_pull.yml

```yml id="0zg5m7"
id: defense_dragnet_pull
trigger: ON_TARGET
cooldownTicks: 110
targeting:
  type: PLAYERS_IN_RADIUS
  radius: 6
  maxTargets: 3
conditions:
  - type: waveAtLeast
    wave: 2
actions:
  - type: particles
    particle: SQUID_INK
    count: 16
  - type: sound
    sound: ENTITY_DROWNED_SHOOT
    volume: 1.0
    pitch: 0.8
  - type: pull
    strength: 1.6
  - type: applyPotion
    potion: SLOW
    durationTicks: 60
    amplifier: 1
```

FILE: src/main/resources/mob-abilities/defense_anchor_bolt.yml

```yml id="xwjb2k"
id: defense_anchor_bolt
trigger: ON_TARGET
cooldownTicks: 90
targeting:
  type: CURRENT_TARGET
conditions:
  - type: targetExists
actions:
  - type: sound
    sound: ENTITY_DROWNED_SHOOT
    volume: 1.0
    pitch: 1.0
  - type: launchProjectile
    projectile: TRIDENT
    speed: 1.6
    spread: 0.05
    count: 1
```

FILE: src/main/resources/mob-abilities/defense_phase_rally.yml

```yml id="7xg0ql"
id: defense_phase_rally
trigger: ON_HURT
cooldownTicks: 200
targeting:
  type: SELF
conditions:
  - type: hpBelow
    ratio: 0.60
actions:
  - type: particles
    particle: CRIT
    count: 18
  - type: sound
    sound: ENTITY_PIGLIN_BRUTE_ANGRY
    volume: 1.1
    pitch: 0.8
  - type: applyPotionToSelf
    potion: DAMAGE_RESISTANCE
    durationTicks: 100
    amplifier: 1
  - type: applyPotionToSelf
    potion: INCREASE_DAMAGE
    durationTicks: 100
    amplifier: 0
```

FILE: src/main/resources/mob-abilities/defense_guardian_sunder.yml

```yml id="6mnh5k"
id: defense_guardian_sunder
trigger: ON_TARGET
cooldownTicks: 80
targeting:
  type: GUARDIAN
conditions:
  - type: targetExists
  - type: waveAtLeast
    wave: 3
actions:
  - type: particles
    particle: EXPLOSION
    count: 6
  - type: sound
    sound: ENTITY_RAVAGER_ATTACK
    volume: 1.2
    pitch: 0.8
  - type: directDamage
    amount: 10.0
```

FILE: src/main/resources/mob-abilities/defense_royal_barrage.yml

```yml id="zq4v2y"
id: defense_royal_barrage
trigger: ON_IDLE
cooldownTicks: 90
targeting:
  type: NEAREST_PLAYER
conditions:
  - type: targetExists
  - type: waveAtLeast
    wave: 4
actions:
  - type: sound
    sound: ITEM_CROSSBOW_LOADING_END
    volume: 1.0
    pitch: 0.8
  - type: launchProjectile
    projectile: ARROW
    speed: 2.0
    spread: 0.18
    count: 3
```

FILE: src/main/resources/mob-abilities/defense_final_pulse.yml

```yml id="7i1d7g"
id: defense_final_pulse
trigger: ON_HURT
cooldownTicks: 220
targeting:
  type: PLAYERS_IN_RADIUS
  radius: 8
  maxTargets: 5
conditions:
  - type: hpBelow
    ratio: 0.35
actions:
  - type: particles
    particle: EXPLOSION
    count: 18
  - type: sound
    sound: ENTITY_WARDEN_SONIC_BOOM
    volume: 1.4
    pitch: 0.9
  - type: directDamage
    amount: 8.0
  - type: knockback
    strength: 1.8
  - type: applyPotion
    potion: WEAKNESS
    durationTicks: 60
    amplifier: 1
```

FILE: src/main/resources/mob-abilities/defense_frontier_reinforce.yml

```yml id="rz4hqd"
id: defense_frontier_reinforce
trigger: ON_HURT
cooldownTicks: 240
targeting:
  type: SELF
conditions:
  - type: hpBelow
    ratio: 0.70
actions:
  - type: sound
    sound: ENTITY_PILLAGER_CELEBRATE
    volume: 1.0
    pitch: 0.8
  - type: summon
    ref: custom:frontier_raider
    count: 2
  - type: summon
    ref: custom:frontier_bolter
    count: 1
```

FILE: src/main/resources/mob-abilities/defense_breach_reinforce.yml

```yml id="mcnkzc"
id: defense_breach_reinforce
trigger: ON_HURT
cooldownTicks: 240
targeting:
  type: SELF
conditions:
  - type: hpBelow
    ratio: 0.70
actions:
  - type: sound
    sound: ENTITY_VINDICATOR_AMBIENT
    volume: 1.0
    pitch: 0.8
  - type: summon
    ref: custom:breach_sapper
    count: 2
  - type: summon
    ref: custom:breach_marksman
    count: 1
```

FILE: src/main/resources/mob-abilities/defense_ash_reinforce.yml

```yml id="hpcv2s"
id: defense_ash_reinforce
trigger: ON_HURT
cooldownTicks: 240
targeting:
  type: SELF
conditions:
  - type: hpBelow
    ratio: 0.70
actions:
  - type: sound
    sound: ENTITY_EVOKER_PREPARE_SUMMON
    volume: 1.0
    pitch: 0.8
  - type: summon
    ref: custom:ash_cultist
    count: 2
  - type: summon
    ref: custom:ash_hexer
    count: 1
```

FILE: src/main/resources/mob-abilities/defense_tide_reinforce.yml

```yml id="6g35g2"
id: defense_tide_reinforce
trigger: ON_HURT
cooldownTicks: 240
targeting:
  type: SELF
conditions:
  - type: hpBelow
    ratio: 0.70
actions:
  - type: sound
    sound: ENTITY_DROWNED_AMBIENT_WATER
    volume: 1.0
    pitch: 0.8
  - type: summon
    ref: custom:tide_reaver
    count: 2
  - type: summon
    ref: custom:omen_harpooner
    count: 1
```

FILE: src/main/resources/mob-abilities/defense_throne_reinforce.yml

```yml id="0bqknu"
id: defense_throne_reinforce
trigger: ON_HURT
cooldownTicks: 260
targeting:
  type: SELF
conditions:
  - type: hpBelow
    ratio: 0.70
actions:
  - type: sound
    sound: ENTITY_WITHER_AMBIENT
    volume: 1.1
    pitch: 0.9
  - type: summon
    ref: custom:throne_legionnaire
    count: 2
  - type: summon
    ref: custom:throne_ballista
    count: 1
```

7. Mob Traits

FILE: src/main/resources/mob-traits/trait_guardian_hunter.yml

```yml id="jqmqsr"
id: trait_guardian_hunter
nameSuffix: "d'Assedio"
tagsAdd:
  - guardian_hunter
addAbilities:
  - defense_guardian_strike
removeAbilities: []
```

FILE: src/main/resources/mob-traits/trait_marksman.yml

```yml id="e8kr4g"
id: trait_marksman
nameSuffix: "Tiratore"
tagsAdd:
  - marksman
addAbilities:
  - defense_crossbow_volley
removeAbilities: []
```

FILE: src/main/resources/mob-traits/trait_breacher.yml

```yml id="jlwmht"
id: trait_breacher
nameSuffix: "di Breccia"
tagsAdd:
  - breacher
addAbilities:
  - defense_breach_slam
removeAbilities: []
```

FILE: src/main/resources/mob-traits/trait_sapper.yml

```yml id="l3x2yc"
id: trait_sapper
nameSuffix: "Demolitore"
tagsAdd:
  - sapper
addAbilities:
  - defense_sapper_blast
removeAbilities: []
```

FILE: src/main/resources/mob-traits/trait_hexer.yml

```yml id="j5bqth"
id: trait_hexer
nameSuffix: "delle Nebbie"
tagsAdd:
  - hexer
addAbilities:
  - defense_hex_fog
removeAbilities: []
```

FILE: src/main/resources/mob-traits/trait_ritualist.yml

```yml id="uwfgi6"
id: trait_ritualist
nameSuffix: "del Rito"
tagsAdd:
  - ritualist
addAbilities:
  - defense_ritual_burst
removeAbilities: []
```

FILE: src/main/resources/mob-traits/trait_tidecaller.yml

```yml id="5083wn"
id: trait_tidecaller
nameSuffix: "della Corrente"
tagsAdd:
  - tidecaller
addAbilities:
  - defense_dragnet_pull
removeAbilities: []
```

FILE: src/main/resources/mob-traits/trait_overseer.yml

```yml id="6lquab"
id: trait_overseer
nameSuffix: "Capitano"
tagsAdd:
  - overseer
addAbilities:
  - defense_phase_rally
removeAbilities: []
```

8. Mob Variants

FILE: src/main/resources/mob-variants/variant_ironclad.yml

```yml id="p1mk55"
id: variant_ironclad
namePrefix: "<gray>Corazzato</gray>"
tagsAdd:
  - ironclad
equipment:
  helmet: IRON_HELMET
  chestplate: IRON_CHESTPLATE
  leggings: IRON_LEGGINGS
  boots: IRON_BOOTS
addAbilities: []
removeAbilities: []
```

FILE: src/main/resources/mob-variants/variant_veteran.yml

```yml id="ilq7n1"
id: variant_veteran
namePrefix: "<gray>Veterano</gray>"
tagsAdd:
  - veteran
addAbilities: []
removeAbilities: []
```

FILE: src/main/resources/mob-variants/variant_scorched.yml

```yml id="ygzx1z"
id: variant_scorched
namePrefix: "<red>Arso</red>"
tagsAdd:
  - scorched
addAbilities: []
removeAbilities: []
```

FILE: src/main/resources/mob-variants/variant_ashen.yml

```yml id="7ecv4a"
id: variant_ashen
namePrefix: "<dark_gray>Cinereo</dark_gray>"
tagsAdd:
  - ashen
addAbilities: []
removeAbilities: []
```

FILE: src/main/resources/mob-variants/variant_abyssal.yml

```yml id="d3we0n"
id: variant_abyssal
namePrefix: "<dark_aqua>Abissale</dark_aqua>"
tagsAdd:
  - abyssal
addAbilities: []
removeAbilities: []
```

FILE: src/main/resources/mob-variants/variant_royal.yml

```yml id="gqo9al"
id: variant_royal
namePrefix: "<gold>Regale</gold>"
tagsAdd:
  - royal
addAbilities: []
removeAbilities: []
```

FILE: src/main/resources/mob-variants/variant_bastion.yml

```yml id="mz6v7x"
id: variant_bastion
nameSuffix: "del Bastione"
tagsAdd:
  - bastion
addAbilities: []
removeAbilities: []
```

FILE: src/main/resources/mob-variants/variant_last_siege.yml

```yml id="d64xun"
id: variant_last_siege
nameSuffix: "dell'Ultimo Assedio"
tagsAdd:
  - last_siege
addAbilities: []
removeAbilities: []
```

9. GUI Labels

* `l1: Difesa L1 — Bastione di Frontiera`
* `l2: Difesa L2 — Fossato in Allarme`
* `l3: Difesa L3 — Muro del Predone`
* `l4: Difesa L4 — Ariete del Confine`
* `l5: Difesa L5 — Forte delle Braci`
* `l6: Difesa L6 — Breccia Ferrata`
* `l7: Difesa L7 — Varchi Cremisi`
* `l8: Difesa L8 — Caduta del Bastione`
* `l9: Difesa L9 — Corsa degli Assediatori`
* `l10: Difesa L10 — Signore della Breccia`
* `l11: Difesa L11 — Canto di Cenere`
* `l12: Difesa L12 — Corte dei Sepolti`
* `l13: Difesa L13 — Rito delle Ossa`
* `l14: Difesa L14 — Piaga del Braciere`
* `l15: Difesa L15 — Ierofante Cinerico`
* `l16: Difesa L16 — Risacca Oscura`
* `l17: Difesa L17 — Ferri dell'Abisso`
* `l18: Difesa L18 — Marea del Presagio`
* `l19: Difesa L19 — Strappo Profondo`
* `l20: Difesa L20 — Araldo Abissale`
* `l21: Difesa L21 — Corona di Ferro`
* `l22: Difesa L22 — Guardia del Tramonto`
* `l23: Difesa L23 — Guerra del Trono`
* `l24: Difesa L24 — Ultimo Bastione`
* `l25: Difesa L25 — Re dell'Ultimo Assedio`

10. Self Audit

* numero totale mob: 25
* numero totale abilities: 18
* numero totale traits: 8
* numero totale variants: 8
* tutti i riferimenti incrociati esistono: sì
* tutti i boss hanno 3 fasi: sì
* tutti i tier `L1..L25` hanno boss valido: sì
* tutti i tier rispettano i livelli città `10..250`: sì

[1]: https://minecraft.fandom.com/wiki/Pillager?utm_source=chatgpt.com "Pillager - Minecraft Wiki - Fandom"
