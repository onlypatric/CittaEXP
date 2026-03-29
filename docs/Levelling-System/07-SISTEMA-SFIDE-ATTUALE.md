# Sistema Sfide Città (stato attuale, versione condivisibile)

> Questo documento è scritto per lettori che **non hanno accesso alla codebase**.
> È una descrizione funzionale/operativa del sistema, non un riferimento al codice sorgente.

## 1) Panoramica veloce
Il sistema sfide città è un motore runtime asincrono, town-first (`town_id`), con questi blocchi principali:
- generazione sfide cicliche (daily/weekly/monthly/event)
- tracking progresso per città
- anti-abuse (throttle, cap player, anti-farm, anti-afk)
- reward idempotenti (XP città, comandi console, item vault)
- UI GUI-first dentro `/city` (hub progressione + lista sfide)

Componente tecnico principale (nome interno): `CityChallengeService`.

## 2) Cicli e scheduler
La configurazione è centralizzata in un file YAML dedicato alle challenge (nome interno: `challenges.yml`).

Cicli supportati:
- `DAILY_STANDARD`
- `DAILY_RACE_1600`
- `DAILY_RACE_2100`
- `WEEKLY_STANDARD`
- `MONTHLY_STANDARD`
- `MONTHLY_EVENT_A`
- `MONTHLY_EVENT_B`

Timezone: `Europe/Rome`.

Rollover/attivazioni (default):
- daily standard: dopo `00:05`
- race: `16:00` e `21:00`
- weekly: lunedì `00:10`
- monthly: giorno 1 `00:15`
- monthly event:
  - window A: giorni 1-15
  - window B: giorni 16-fine mese

Generazione challenge:
- selezione pesata deterministica (`weight`) tramite seed fisso per ciclo
- quindi stesso seed => stessa selezione globale per tutte le città

## 3) Catalogo challenge e varianti (M7)
Il catalogo è definito nella sezione `definitions` del file di configurazione challenge.

Stato attuale:
- definizioni: `20`
- con `targetRange`: `18`
- fisse (senza varianza):
  - `boss_challenge`
  - `exploration_challenge`

### 3.1 Varianza target
Per quasi tutte le challenge esiste:
- `targetRange.min`
- `targetRange.max`
- `targetRange.step`

Il target finale è deterministico per istanza (`instanceId`), quindi stabile anche su restart.

### 3.2 Modalità globale/specifica
Per obiettivi idonei è disponibile `variant`:
- `mode`: `GLOBAL_ONLY | SPECIFIC_ONLY | MIXED`
- `specificChance`: probabilità specifica (default 0.50)
- `specificChanceByCycle`: override per ciclo
- `focusType`: `MATERIAL | ENTITY_TYPE`
- `focusPool`: lista focus possibili con `rarity`

Tipi attualmente abilitati a modalità specifica (MIXED):
- `block_mine`
- `mob_kill`
- `rare_mob_kill`
- `crop_harvest`
- `fish_catch`
- `animal_interaction`
- `construction`
- `redstone_automation`

Gli altri restano global-only.

### 3.3 Scaling target su challenge specifiche
Quando l’istanza è `SPECIFIC`, il target viene scalato con moltiplicatore per rarità:
- `COMMON = 1.0`
- `UNCOMMON = 0.8`
- `RARE = 0.6`

Config: `specificVariants.targetScaling`.

## 4) Runtime tracking progresso
Listener eventi principali in `CityChallengeListener`:
- kill mob (`EntityDeathEvent`)
- block mine (`BlockBreakEvent`)
- construction/redstone (`BlockPlaceEvent`)
- xp pickup (`PlayerExpChangeEvent`)
- fishing (`PlayerFishEvent`)
- breed/tame (`EntityBreedEvent`, `EntityTameEvent`)
- dimension travel (`PlayerChangedWorldEvent`)
- archaeology (`PlayerInteractEvent` con brush)
- structure discovery (advancements)
- structure loot (`InventoryOpenEvent`)
- sampler periodico per playtime/statistiche

Per ogni evento viene creato `ChallengeObjectiveContext` con metadati (es. `materialKey`, `entityTypeKey`, `mobSpawnReason`).

Match challenge:
- prima per `objectiveType`
- se istanza `SPECIFIC`, conta solo se `focusKey` combacia con il contesto evento

## 5) Anti-abuse
Motore: `ChallengeAntiAbuseService`.

Guardie attive:
- anti block-farm con placed-block tracker (`abuse:block-placed`)
- deny spawn reason su mob (`SPAWNER`, `SPAWN_EGG`, `CUSTOM`)
- throttle player+instance+objective (`throttleMs`, default 500)
- cap contributo player per instance (`playerContributionCapRatio`, default 0.35)
- anti-AFK su obiettivi sampled (`afkIdleSeconds`, default 120)
- cap giornaliero per bucket categoria città (`dailyTownCategoryCapRatio`)

Persistenza anti-abuse:
- `challenge_placed_blocks`
- `town_challenge_player_contrib`
- `town_challenge_daily_category_cap`
- `town_player_objective_checkpoint`

## 6) Reward engine
I reward bundle sono definiti in un file YAML separato (nome interno: `challenge-rewards.yml`).

Tipi reward:
- `XP_CITY`
- `COMMAND_CONSOLE`
- `ITEM_VAULT`

### 6.1 Idempotenza
Ogni grant usa `grant_key` univoco in `challenge_reward_ledger`.
Quindi replay/restart non duplicano reward.

### 6.2 XP città
- converte XP in scala interna (`xpScale` di level service)
- apply via `cityLevelService.grantXpBonus(...)`

### 6.3 Weekly streak bonus
Se challenge weekly standard e città in streak:
- applica bonus XP separato (`streak-bonus`) con grant key dedicata

### 6.4 Command template
Template engine supporta:
- placeholder standard (`{town_id}`, `{town_name}`, `{player_name}`, ecc.)
- `{difficulty}`, `{difficulty_multiplier}`
- funzione `randomrange(min,max)`

Se template invalido:
- comando skippato
- ledger segnato come `SKIPPED[...]`
- warning log con reason code

## 7) Race e Monthly Event
### 7.1 Race daily
Policy: `first commit wins`.
Implementazione: insert winner atomico in `challenge_winners`.
Solo il primo completamento riceve reward winner.

### 7.2 Monthly event
A fine finestra evento:
- calcolo leaderboard città
- payout configurabile:
  - top1
  - top2
  - top3
  - threshold

Ogni payout resta idempotente via ledger.

## 8) Persistence (`challenges.db`)
DB locale SQLite: `plugins/CittaEXP/challenges.db`.

Tabelle core:
- `challenge_definitions`
- `challenge_instances`
- `town_challenge_progress`
- `challenge_winners`
- `challenge_reward_ledger`
- `challenge_cycle_state`

Tabelle anti-abuse/supporto:
- `challenge_placed_blocks`
- `town_player_objective_checkpoint`
- `town_challenge_player_contrib`
- `town_challenge_daily_category_cap`

Estensioni M7 su `challenge_instances`:
- `variant_type`
- `focus_type`
- `focus_key`
- `focus_label`

Migrazione: idempotente tramite `ensureColumn(...)`.

## 9) UX e comandi
Percorso player:
- `/city` -> GUI principale
- da lì Progressione Hub -> dettaglio sfide

Rendering challenge (GUI):
- obiettivo human-friendly
- barra progresso
- modalità (`Globale` / `Specifica`)
- focus (se specifica)
- reward summary
- tempo residuo

Comandi staff utili:
- `/cittaexp probe` (diagnostica completa challenge + anti-abuse + event + streak)
- `/cittaexp staff challenges regenerate` (reset runtime state e rigenerazione cicli attivi)

## 10) Primo avvio e restart
Primo avvio:
- crea schema `challenges.db`
- carica config/cataloghi
- genera le challenge mancanti per i cicli correnti (in base all’orario)

Restart:
- ricarica instances/progress/ledger/state da DB
- mantiene target/focus delle istanze esistenti
- non duplica reward grazie a `grant_key`

## 11) Limitazioni attuali (utili per review)
- alcune objective restano global-only per scelta di affidabilità (es. `structure_loot`, `dimension_travel`, ecc.)
- split 50/50 è deterministico per istanza, non “random runtime live”
- niente filtro struttura avanzato per `structure_loot` (solo evento apertura inventario)
- il sampler statistiche dipende dai contatori Bukkit e dal checkpoint store

## 12) Allegati consigliati per reviewer esterno
Se vuoi fare una review completa senza accesso al repository, conviene condividere questi artefatti insieme a questo documento:
- estratto della configurazione challenge (definitions + scheduler + anti-abuse)
- estratto dei testi utente challenge (descrizioni/lore)
- estratto dei reward bundle (XP/item/command)
- 1 dump di esempio di `challenges.db` (solo schema + righe fake/anonymized)
- 1 log di prova con: generation cycle, completion, reward grant, eventuale drop anti-abuse

## 13) Checklist review (non tecnica)
Per una review di prodotto/game-design, verifica:
- varietà giornaliera percepita (global vs specific) senza ripetizioni eccessive
- chiarezza obiettivi in GUI (cosa fare, quanto manca, tempo residuo)
- proporzione reward vs effort (daily/weekly/monthly/event)
- fairness fra città piccole e grandi (cap anti-abuse, soglie, race)
- qualità della telemetria staff (`probe`) per debug rapido
