# 02 - Catalogo Sfide

## Design goals
- Ogni sfida deve essere comprensibile in meno di 10 secondi.
- Nessuna categoria deve dominare da sola tutta la progressione.
- La città deve poter avanzare anche con player casual.

## Sfide cooperative (pool base)

### 1) Contributo risorse città
- Esempio:
  - 8000 cobblestone
  - 1200 iron
  - 200 gold
- Motivo: onboarding facile, economia interna, contributo utile per tutti.

### 2) Mining globale
- Esempio:
  - 6000 stone
  - 1200 coal
  - 80 diamonds
- Evento target: `BlockBreakEvent`.

### 3) Kill mobs globale
- Esempio:
  - 400 zombie
  - 250 skeleton
  - 100 creeper
  - oppure 1200 mob totali
- Evento target: `EntityDeathEvent`.

### 4) Farming globale
- Esempio:
  - 2000 wheat
  - 1500 carrots
  - 1500 potatoes
- Evento target: raccolta crop maturi.

### 5) Boss challenge
- Esempio:
  - 1 Wither
  - 1 Warden
  - estensione: Trial Chamber challenge
- Reward: XP molto alta.

### 6) Trade challenge
- Esempio:
  - 200 trade villager
  - oppure 400 emerald guadagnati
- Evento target: trade economy hook.

## Pool esteso (da research, integrato)

### 7) Exploration challenges
- Esempi:
  - `discover_trial_chamber`
  - `discover_stronghold`
  - `discover_ancient_city`
  - `discover_ocean_monument`
- Uso consigliato: weekly + seasonal event.

### 8) Archaeology challenges
- Esempi:
  - `brush_100_suspicious_sand`
  - `find_20_pottery_shards`
  - `excavate_10_trail_ruins_items`
- Hook candidati: interazione/blocchi drop archeologia.

### 9) Structure loot challenges
- Esempi:
  - `open_20_shipwreck_chests`
  - `open_10_bastion_chests`
  - `open_15_trial_vaults`
  - `open_3_ominous_vaults`

### 10) Rare mob encounter
- Esempi:
  - `kill_10_breeze`
  - `kill_2_warden`
  - `kill_3_elder_guardian`
  - `kill_20_piglin_brutes`

### 11) Transportation challenges
- Esempi:
  - `travel_200000_blocks`
  - `visit_25_biomes`
  - `ride_strider_500_blocks`
  - `sail_2000_blocks`
- Hook candidati: movement + vehicle movement.

### 12) Dimension challenges
- Esempi:
  - `enter_nether_10_times`
  - `kill_50_nether_mobs`
  - `collect_100_blaze_rods`
  - `complete_2_end_cities`

### 13) Farming ecosystem
- Esempi:
  - `collect_300_honey`
  - `collect_200_eggs`
  - `harvest_300_pumpkins`
  - `breed_200_animals`

### 14) Fishing challenges
- Esempi:
  - `catch_200_fish`
  - `catch_20_treasure_items`
  - `catch_10_enchanted_books`
- Hook candidato: `PlayerFishEvent`.

### 15) Animal / interaction challenges
- Esempi:
  - `tame_20_wolves`
  - `breed_100_animals`
  - `duplicate_5_allays`
  - `sniffer_find_10_seeds`

### 16) Economy advanced
- Esempi:
  - `sell_200_items`
  - `earn_10000_emeralds`
  - `complete_500_villager_trades`
  - `trade_50_librarian_books`

### 17) Construction challenges
- Esempi:
  - `place_5000_stone_blocks`
  - `place_1000_glass_blocks`
  - `place_200_lanterns`
  - `build_5_bridges`
- Hook candidato: `BlockPlaceEvent`.

### 18) Redstone / automation
- Esempi:
  - `craft_200_pistons`
  - `place_100_redstone_components`
  - `generate_5000_items_from_farms`

## Bundle consigliato per ciclo
- Daily: 3 sfide piccole (mix combat + gathering + utility).
- Weekly: 2-3 sfide principali (obbligo categorie diverse).
- Monthly/Event: 1-2 sfide epiche/server-wide (boss, exploration, construction contest).

## Reward model (linea guida)
- Daily: XP bassa, progressione stabile.
- Weekly: XP media, avanzamento stage.
- Monthly/Event: XP alta + reward vanity/ranking.

## Mapping operativo (v1 -> v2)
- V1 (subito): categorie 1-6 già semplici da tracciare.
- V1.5: categorie 9, 10, 14, 17 (alto valore, effort moderato).
- V2: categorie 7, 8, 11, 12, 13, 15, 16, 18 (più dipendenze hook e anti-abuso).

## Source research
- Documento sorgente: [extra-challenges.md](/Users/patric/Documents/Minecraft/CittaEXP/docs/research/extra-challenges.md)
