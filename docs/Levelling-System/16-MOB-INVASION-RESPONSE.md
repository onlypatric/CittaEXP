1. Design Summary

Il modello passa da 3 a 10 tier con una curva netta a quattro fasi: early (`L1-L3`) come difesa di bordo con orde semplici e primi disruptor; mid (`L4-L5`) come vero assedio con ranged, support e summon; late (`L6-L8`) come crisi cittadina dove il boss smette di essere solo “grosso” e diventa una minaccia di controllo area; endgame (`L9-L10`) come prova di regno, rara, costosa e lunga. Ho ritoccato anche `L1-L3` per rendere la progressione coerente: `L3` non salta più subito al Warden, ma chiude il blocco early con un assedio duro a base Ravager/illager, lasciando il cambio qualitativo a `L4-L6`. La scelta dei pool segue i ruoli reali dei mob: Pillager per pressione ranged, Ravager per sfondamento e knockback, Witches come supporto che curano gli illager nei raid, Evoker come summoner con Vex/fangs, Piglin Brute come melee che carica sempre, Warden come apex boss con zero knockback e sonic boom. ([Minecraft Wiki][1])

2. Implementation Delta

* Estendere `CityDefenseTier` a:

  * `L1, L2, L3, L4, L5, L6, L7, L8, L9, L10`
* Nessuna modifica necessaria a `CityDefenseLevelSpec`
* Aggiornare `defense.yml`:

  * ritocco parametri globali per sostenere sessioni più lunghe e più pesanti
  * aggiunta `levels.l4` fino a `levels.l10`
  * lieve rituning di `l3`
* Aggiornare GUI/config label:

  * aggiungere titoli player-facing `l4..l10`
* Compatibilità:

  * `l1..l3` restano compatibili come chiavi
  * serve solo allineare enum, parser/config e tutte le GUI che oggi assumono max `L3`
  * consiglio operativo: abbassare `globalActiveCap` da `3` a `2` e alzare `maxMobsAlive` a `90`, così i tier alti reggono meglio senza trasformare tre sessioni simultanee in un picco ingestibile

3. YAML

```yaml
enabled: true
globalActiveCap: 2
antiCheeseRadius: 16
maxMobsAlive: 90
playerDeathPenaltyPercent: 0.12

timing:
  prepSeconds: 60
  interWaveSeconds: 12
  sessionTimeoutSeconds: 2100

spawn:
  minPoints: 5
  radiusMin: 20
  radiusMax: 32

defenderScaling:
  perExtraDefender: 0.22
  maxMultiplier: 3.0

levels:
  l1:
    minCityLevel: 3
    waves: 3
    guardianHp: 250
    startCost: 5000
    cooldownSeconds: 7200
    rewardXp: 150
    rewardItems:
      IRON_INGOT: 96
      BREAD: 64
    baseMobCount: 8
    waveIncrement: 3
    bossSupportCount: 6
    eliteChance: 0.10
    regularMobs:
      - ZOMBIE
      - SKELETON
      - SPIDER
    eliteMobs:
      - HUSK
      - STRAY
    bossMob: RAVAGER

  l2:
    minCityLevel: 6
    waves: 5
    guardianHp: 450
    startCost: 15000
    cooldownSeconds: 21600
    rewardXp: 350
    rewardItems:
      IRON_INGOT: 128
      GOLD_INGOT: 48
      COOKED_BEEF: 64
    baseMobCount: 11
    waveIncrement: 4
    bossSupportCount: 8
    eliteChance: 0.14
    regularMobs:
      - ZOMBIE
      - SKELETON
      - SPIDER
      - CREEPER
    eliteMobs:
      - HUSK
      - STRAY
      - PILLAGER
    bossMob: RAVAGER

  l3:
    minCityLevel: 10
    waves: 7
    guardianHp: 720
    startCost: 35000
    cooldownSeconds: 43200
    rewardXp: 800
    rewardItems:
      GOLD_INGOT: 128
      REDSTONE: 128
      DIAMOND: 12
      COOKED_BEEF: 96
    baseMobCount: 14
    waveIncrement: 5
    bossSupportCount: 10
    eliteChance: 0.18
    regularMobs:
      - ZOMBIE
      - SKELETON
      - SPIDER
      - CREEPER
      - PILLAGER
    eliteMobs:
      - HUSK
      - STRAY
      - VINDICATOR
      - WITCH
    bossMob: RAVAGER

  l4:
    minCityLevel: 15
    waves: 8
    guardianHp: 950
    startCost: 70000
    cooldownSeconds: 64800
    rewardXp: 1300
    rewardItems:
      GOLD_INGOT: 160
      REDSTONE: 192
      LAPIS_LAZULI: 128
      DIAMOND: 16
      COOKED_BEEF: 128
    baseMobCount: 16
    waveIncrement: 6
    bossSupportCount: 12
    eliteChance: 0.22
    regularMobs:
      - ZOMBIE
      - SKELETON
      - CREEPER
      - PILLAGER
      - DROWNED
    eliteMobs:
      - VINDICATOR
      - WITCH
      - WITHER_SKELETON
    bossMob: EVOKER

  l5:
    minCityLevel: 21
    waves: 9
    guardianHp: 1300
    startCost: 125000
    cooldownSeconds: 86400
    rewardXp: 1900
    rewardItems:
      GOLD_INGOT: 224
      LAPIS_LAZULI: 128
      DIAMOND: 24
      ENDER_PEARL: 64
      COOKED_BEEF: 128
    baseMobCount: 19
    waveIncrement: 6
    bossSupportCount: 14
    eliteChance: 0.26
    regularMobs:
      - SKELETON
      - CREEPER
      - PILLAGER
      - DROWNED
      - ENDERMAN
    eliteMobs:
      - VINDICATOR
      - WITCH
      - WITHER_SKELETON
      - PIGLIN_BRUTE
    bossMob: EVOKER

  l6:
    minCityLevel: 30
    waves: 10
    guardianHp: 1750
    startCost: 210000
    cooldownSeconds: 129600
    rewardXp: 2700
    rewardItems:
      DIAMOND: 32
      EMERALD: 16
      ENDER_PEARL: 96
      BLAZE_ROD: 48
      COOKED_BEEF: 160
    baseMobCount: 22
    waveIncrement: 7
    bossSupportCount: 16
    eliteChance: 0.30
    regularMobs:
      - CREEPER
      - PILLAGER
      - DROWNED
      - ENDERMAN
      - SKELETON
    eliteMobs:
      - VINDICATOR
      - WITCH
      - WITHER_SKELETON
      - PIGLIN_BRUTE
      - STRAY
    bossMob: WARDEN

  l7:
    minCityLevel: 42
    waves: 11
    guardianHp: 2300
    startCost: 320000
    cooldownSeconds: 172800
    rewardXp: 3800
    rewardItems:
      DIAMOND: 48
      EMERALD: 32
      ENDER_PEARL: 128
      BLAZE_ROD: 64
      NETHERITE_SCRAP: 2
      COOKED_BEEF: 160
    baseMobCount: 25
    waveIncrement: 7
    bossSupportCount: 18
    eliteChance: 0.34
    regularMobs:
      - PILLAGER
      - CREEPER
      - ENDERMAN
      - DROWNED
      - SKELETON
    eliteMobs:
      - VINDICATOR
      - WITCH
      - WITHER_SKELETON
      - PIGLIN_BRUTE
      - HUSK
    bossMob: WARDEN

  l8:
    minCityLevel: 58
    waves: 12
    guardianHp: 3000
    startCost: 480000
    cooldownSeconds: 216000
    rewardXp: 5200
    rewardItems:
      DIAMOND: 64
      EMERALD: 48
      ENDER_PEARL: 160
      BLAZE_ROD: 96
      NETHERITE_SCRAP: 4
      GOLD_INGOT: 320
    baseMobCount: 28
    waveIncrement: 8
    bossSupportCount: 20
    eliteChance: 0.38
    regularMobs:
      - PILLAGER
      - ENDERMAN
      - DROWNED
      - CREEPER
      - SKELETON
    eliteMobs:
      - VINDICATOR
      - WITCH
      - WITHER_SKELETON
      - PIGLIN_BRUTE
      - STRAY
    bossMob: WARDEN

  l9:
    minCityLevel: 78
    waves: 13
    guardianHp: 3900
    startCost: 700000
    cooldownSeconds: 259200
    rewardXp: 7000
    rewardItems:
      DIAMOND: 96
      EMERALD: 64
      ENDER_PEARL: 192
      BLAZE_ROD: 128
      NETHERITE_SCRAP: 8
      GOLD_INGOT: 384
    baseMobCount: 31
    waveIncrement: 8
    bossSupportCount: 24
    eliteChance: 0.42
    regularMobs:
      - PILLAGER
      - ENDERMAN
      - DROWNED
      - SKELETON
      - CREEPER
    eliteMobs:
      - VINDICATOR
      - WITCH
      - WITHER_SKELETON
      - PIGLIN_BRUTE
      - STRAY
      - HUSK
    bossMob: WARDEN

  l10:
    minCityLevel: 100
    waves: 15
    guardianHp: 5500
    startCost: 1000000
    cooldownSeconds: 345600
    rewardXp: 9500
    rewardItems:
      DIAMOND: 128
      EMERALD: 96
      ENDER_PEARL: 256
      BLAZE_ROD: 160
      NETHERITE_SCRAP: 12
      GOLD_INGOT: 512
      COOKED_BEEF: 192
    baseMobCount: 34
    waveIncrement: 9
    bossSupportCount: 28
    eliteChance: 0.48
    regularMobs:
      - PILLAGER
      - ENDERMAN
      - DROWNED
      - SKELETON
      - CREEPER
      - SPIDER
    eliteMobs:
      - VINDICATOR
      - WITCH
      - WITHER_SKELETON
      - PIGLIN_BRUTE
      - STRAY
      - HUSK
    bossMob: WARDEN
```

4. UI Labels

* `l1: Guardia di Confine`

  * `Difesa L1 — Guardia di Confine`
* `l2: Allarme di Ferro`

  * `Difesa L2 — Allarme di Ferro`
* `l3: Assedio delle Braci`

  * `Difesa L3 — Assedio delle Braci`
* `l4: Stendardo Nero`

  * `Difesa L4 — Stendardo Nero`
* `l5: Notte dei Vex`

  * `Difesa L5 — Notte dei Vex`
* `l6: Bastione Cremisi`

  * `Difesa L6 — Bastione Cremisi`
* `l7: Mura del Crepuscolo`

  * `Difesa L7 — Mura del Crepuscolo`
* `l8: Dominio di Cenere`

  * `Difesa L8 — Dominio di Cenere`
* `l9: Ultimo Baluardo`

  * `Difesa L9 — Ultimo Baluardo`
* `l10: Corona in Guerra`

  * `Difesa L10 — Corona in Guerra`

[1]: https://minecraft.fandom.com/wiki/Pillager?utm_source=chatgpt.com "Pillager - Minecraft Wiki - Fandom"
