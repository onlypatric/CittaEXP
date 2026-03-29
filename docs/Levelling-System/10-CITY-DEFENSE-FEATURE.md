# City Mob Invasions (City Defense Feature)

Sistema PvE cooperativo in cui **una città può avviare volontariamente un'invasione di mob** per ottenere **City XP (custom) e risorse**.  
I giocatori devono **difendere il City Guardian (villager)** situato nel centro della città mentre affrontano **wave progressive di mob**.

Il sistema è pensato come **tower-defense cooperativo** con **livelli di invasione**, difficoltà crescente e meccaniche speciali dei mob.

---

# Core Idea

Le città possono **attivare un'invasione** scegliendo un **livello di invasione**.

Ogni livello definisce:

- livello minimo della città
- ricompense
- numero di wave
- composizione dei mob
- abilità speciali dei nemici

Struttura concettuale:

```

City Invasion
├ invasionLevel
├ minCityLevel
├ waves[]
├ rewards
└ difficultyModifiers

```

---

# Livelli di Invasione

Ogni invasione ha più livelli con difficoltà crescente.

Esempio:

```

Level 1 Invasion
min city level: 3
reward:

* 150 CityXP
* iron ingots
* food crates
  waves: 3

```
```

Level 2 Invasion
min city level: 6
reward:

* 350 CityXP
* rare materials
* defense tokens
  waves: 5

```
```

Level 3 Invasion
min city level: 10
reward:

* 800 CityXP
* epic materials
* city buffs
  waves: 7

```

Possibile scaling:

```

CityLevel → unlock invasion tiers

```

---

# Defense Objective

Durante l'invasione i player devono **proteggere il City Guardian**.

Il Guardian è un **villager speciale** spawnato nel centro città.

Se il Guardian muore → **la città perde l'invasione**.

---

# City Guardian

Spawnato vicino al centro città.

Funzione:

```

City Core

```

Configurazione consigliata:

```

AI disabled
persistent entity
custom name visible

```

Opzionale:

```

Guardian HP System

```

invece della morte immediata.

Esempio:

```

CityGuardianHP = 300

```

Quando viene colpito:

```

guardianHP -= damage

```

Se:

```

guardianHP <= 0

```

→ invasion defeat.

Questo evita che un singolo creeper termini subito l'evento.

---

# Anti-Cheese System

Per evitare exploit durante la difesa:

### Construction restriction

```

block placement forbidden within radius 10-15

```

Eventi da bloccare:

```

BlockPlaceEvent
BlockBreakEvent
PlayerBucketEmptyEvent
PlayerBucketFillEvent

```

Previene:

```

lava traps
water traps
pillar towers
mob suffocation builds
cheese walls

```

---

# Wave System

Ogni invasione è composta da più wave.

```

Invasion
├ Wave 1
├ Wave 2
├ Wave 3
└ Boss Wave

```

Struttura wave:

```

Wave
├ mobTypes
├ mobCount
├ spawnPoints
├ spawnDelay
└ specialModifiers

```

Esempio:

```

Wave 1
10 zombies
5 skeletons

```
```

Wave 2
15 zombies
8 skeletons
3 spiders

```
```

Wave 3
10 armored zombies
5 jump spiders

```
```

Boss Wave
1 elite brute
10 support mobs

```

---

# Victory Conditions

```

all waves completed
AND
guardian alive

```

---

# Defeat Conditions

```

guardian dies
guardianHP <= 0
(optional) city destroyed structures

```

---

# Special Mob Mechanics

Per rendere le invasioni interessanti, i mob possono avere **abilità speciali**.

Queste abilità possono essere assegnate a determinati tipi di mob o a mob elite.

---

## Block Breakers

Alcuni mob possono **distruggere blocchi difensivi**.

Comportamento:

```

detect obstacle
break block after X seconds
continue path

```

Mob suggeriti:

```

Brutes
Ravager variants
Siege zombies

```

Questo evita che i player blocchino l'invasione con muri.

---

## Exploder Mobs

Mob che **esplodono vicino al Guardian o ai player**.

Varianti:

```

Creepers
Suicide mobs
Demolition mobs

```

Possibili modificatori:

```

larger explosion
delayed fuse
chain explosions

```

Uso principale:

```

destroy defenses
damage clustered players
pressure city core

```

---

## Jumpers / Climbers

Mob capaci di **superare barriere difensive**.

Esempi:

```

jump spiders
leaping zombies
climbing mobs

```

Abilità:

```

high jump
wall climb
gap leap

```

Questo impedisce difese troppo semplici.

---

## Aura Mobs

Mob con **aura passive** che influenzano l'area.

Esempi:

```

speed aura
damage aura
regen aura
resistance aura

```

Effetto:

```

buff nearby mobs
increase wave difficulty

```

Tipicamente usati come:

```

support mobs
mini commanders

```

---

## Debuff Mobs

Mob che **applicano effetti negativi ai player vicini**.

Possibili debuff:

```

slowness
weakness
poison
blindness
mining fatigue

```

Esempio:

```

Plague carrier
Shadow priest
Cursed skeleton

```

Effetto:

```

players must prioritize killing them

```

---

## Elite Mobs

Versioni potenziate dei mob normali.

Caratteristiche:

```

higher HP
custom abilities
armor
special attacks

```

Esempi:

```

elite zombie
elite brute
mini boss

```

---

## Boss Mobs

Ultima wave può contenere un boss.

Esempio:

```

Siege Commander
Ancient Ravager
Corrupted Golem

```

Meccaniche possibili:

```

spawn minions
charge attacks
area slam
rage phase

```

---

# Spawn Behaviour

I mob devono avere un obiettivo chiaro.

Target principale:

```

City Guardian

```

Comportamento:

```

spawn outside city
move toward guardian
attack players if intercepted

```

Questo crea un vero flusso di difesa.

---

# Reward System

Quando l'invasione viene completata:

```

CityXP (custom system)
materials
rare drops
defense tokens
temporary city buffs

```

Esempio:

```

+250 CityXP
+iron crate
+food supply

```

---

# Difficulty Scaling

La difficoltà può scalare con:

```

city level
number of players defending
completed invasions

```

Possibili modificatori:

```

more mobs
elite spawn chance
boss variants
special abilities

```

---

# Session Lifecycle

```

City starts invasion
↓
Guardian spawns
↓
Players gather
↓
Wave system starts
↓
Mobs attack city
↓
Players defend guardian
↓
Victory or Defeat
↓
Cleanup + rewards

```

---

# Performance Considerations

Per evitare lag:

```

limit max mobs per wave
despawn mobs reaching core
track mobs in session
avoid world scans

```

---

# Gameplay Loop

```

City unlocks invasion tier
↓
Players start invasion
↓
Defend city against waves
↓
Earn CityXP and resources
↓
Upgrade city
↓
Unlock stronger invasions

```
