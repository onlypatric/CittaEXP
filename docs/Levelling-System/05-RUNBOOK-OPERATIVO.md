# 05 - Runbook Operativo

## Checklist pre-release
- Config challenge validata (pool, target, reward, weight).
- Verifica cicli M2: daily (`3 + 2 race`), weekly (`3`), monthly (`2`).
- Config files presenti e coerenti:
  - `challenges.yml`
  - `challenge-rewards.yml`
  - `vault.yml`
- Timezone scheduler verificata (`Europe/Rome`).
- Guard anti-abuso attive.
- i18n completa per tutti i nuovi path.
- Probe/diagnostica espone stato engine e backlog errori.

## Checklist smoke (10 minuti)
1. Avvio server senza eccezioni.
2. Apertura `/city` e click su bottone bottom bar `Progressione`.
3. Verifica hub progression: summary città + blocchi `DAILY/WEEKLY/MONTHLY`.
4. Verifica top contributor per ciclo e reward preview safe sulle sfide attive.
5. Dall'hub aprire `Dettaglio Livelli` e tornare al hub con back.
6. Dall'hub aprire `Dettaglio Sfide` e tornare al hub con back.
7. Apertura `/city -> Azioni -> Item Vault` con test deposito/prelievo.
8. Verifica ACL vault per-player da GUI membri (toggle deposit/withdraw).
9. Verifica messaggi localizzati su success/fail + assenza missing keys.
10. Probe `/cittaexp probe`: sezione anti-abuse con contatori coerenti.
11. Probe `/cittaexp probe`: sezioni `challenges-streak` e `challenges-events` presenti e coerenti.

## Smoke M6 (streak + eventi)
1. Completa tutte le weekly attive con una citta:
  - streak aumenta di 1,
  - reward XP weekly include grant `streak-bonus` (no duplicati).
2. Salta un ciclo weekly:
  - al rollover successivo streak della citta viene azzerata.
3. Evento mensile finestra A/B:
  - verifica leaderboard con posizione citta,
  - a fine finestra payout top/threshold applicato una sola volta.
4. Riavvio server dopo finalize evento:
  - nessun doppio payout (idempotenza ledger).
5. Progression hub:
  - card streak visibile,
  - card evento mensile visibile (finestra, posizione, threshold, tempo residuo).

## Smoke anti-abuse rapido (M3)
1. Piazza e rompi un blocco nello stesso punto:
  - progresso `block_mine` non deve avanzare (reason `abuse:block-placed`).
2. Uccidi un mob da spawner/spawn egg:
  - progresso `mob_kill` bloccato (reason `abuse:mob-spawn-reason`).
3. Spam dello stesso objective in <500ms:
  - eventi extra scartati (`abuse:player-throttle`).
4. Supera il 35% contributo player su un'istanza:
  - ulteriori contributi negati (`abuse:player-cap-reached`).
5. Resta AFK oltre 120s e attendi sampler:
  - niente progresso `playtime/transport` (`abuse:player-afk`).
6. Forza cap giornaliero categoria:
  - progresso extra negato (`abuse:town-daily-cap`).

## Smoke detection/anti-abuse M10
1. Apri un contenitore struttura con loot table valida:
  - `structure_loot` avanza di `+1`.
2. Riapri lo stesso contenitore nello stesso ciclo:
  - nessun incremento (dedupe `container+cycle`).
3. Entra in una struttura valida (chunk change):
  - `structure_discovery` avanza di `+1`.
4. Riesci dalla struttura e rientra nella stessa struttura nello stesso ciclo:
  - nessun incremento (dedupe `structure+cycle`).
5. Verifica sampled confinement:
  - solo `playtime`, `transport`, `villager_trade`, `economy_advanced` usano path sampled.
6. Probe `/cittaexp probe`:
  - controllare `suspicionWarningHits/suspicionClampHits/suspicionReviewFlags`,
  - controllare `dedupeDrops/structureLootValidHits/explorationValidHits`.

## Smoke M11 (store + recap/history + KPI)
1. Avvia con MySQL spento:
  - plugin in `degraded` senza hard crash,
  - challenge runtime operativo (SQLite).
2. Riporta MySQL online:
  - outbox replay in progress fino a `outboxPending=0` o trend in discesa.
3. `/city -> Progressione`:
  - `Recap corrente` apribile,
  - `Archivio recap` paginato apribile e drill-down su singolo recap.
4. In lista sfide:
  - contributo personale + top contributor per istanza visibili.
5. Probe `/cittaexp probe`:
  - sezione `challenges-m11` con KPI valorizzati,
  - diagnostica store (`mode/mysql/outbox/lastSync/replayLag`) coerente.

## Checklist full validation
- Bot `fast`:
  - smoke runtime + `testFast` + check output JSON.
- Bot `full-positive`:
  - lifecycle sfide positivo con streak/event payout.
- Bot `full-negative`:
  - path errore/permessi con mapping localizzato.
- Bot `full`:
  - aggregato profili + DB integrity sqlite/mysql.
- DB integrity:
  - nessun orphan,
  - nessun doppio award,
  - ledger coerente.

### Comandi gate M6
```bash
cd /Users/patric/Documents/Minecraft/CittaEXP
./scripts/run-bot-suite.sh                         # default fast/sqlite
BOT_SUITE=full-positive ./scripts/run-bot-suite.sh
BOT_SUITE=full-negative ./scripts/run-bot-suite.sh
BOT_SUITE=full BOT_DB_MODE=both ./scripts/run-bot-suite.sh
./scripts/release-gate.sh
```

## Incident response rapida
- Sintomo: progresso fermo.
  - verificare scheduler e backlog queue.
- Sintomo: progress anomalo alto.
  - verificare anti-abuse counters e caps.
- Sintomo: reward duplicate.
  - verificare chiavi idempotenza + replay logs.

## Metriche operative minime
- challenge completate/giorno,
- contributori unici/città,
- eventi scartati da anti-abuso,
- tempo medio completamento challenge weekly.
