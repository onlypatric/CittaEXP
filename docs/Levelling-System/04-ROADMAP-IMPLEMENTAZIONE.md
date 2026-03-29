# 04 - Roadmap Implementazione

## Goal
Portare il levelling in produzione con qualità “human-test ready”, mantenendo architettura town-first e GUI-first.

## M1 - Runtime daily completo (server-wide)
- Challenge model operativo con objective core: `mob_kill`, `block_mine`, `crop_harvest`, `playtime_minutes`, `xp_pickup`.
- Daily mix attivo:
  - 3 `DAILY_STANDARD` (tutto il giorno),
  - 2 race server-wide (`16:00`, `21:00` Europe/Rome, first commit wins).
- Reward engine esteso:
  - XP città,
  - command console templated,
  - item in `Item Vault` città.
- `Item Vault` 9x6 paginata + ACL per-player (`can_deposit`, `can_withdraw`) gestibile dal mayor.
- Ledger challenge/reward idempotente + audit vault.
- DoD:
  - progress consistente su restart,
  - nessun doppio reward via `grant_key`,
  - race con winner unico per instance.

## M2 - Engine challenge runtime `COMPLETATO`
- [x] Scheduler cicli daily/weekly/monthly.
- [x] Rotazione sfide da pool configurabile.
- [x] Assegnazione globale identica per tutte le città nel ciclo.
- [x] Rollout categorie:
  - [x] v1: categorie 1-6
  - [x] v1.5: categorie 9, 10, 14, 17
  - [x] v2: categorie 7, 8, 11, 12, 13, 15, 16, 18
- [x] DoD:
  - [x] rollover stabile Europe/Rome
  - [x] snapshot challenge leggibile da GUI

## M3 - Anti-abuse core `COMPLETATO`
- [x] Anti block-farm con `placed-block tracking` (persistito, TTL + prune).
- [x] Anti mob-farm via deny-list `spawn reason`.
- [x] Cooldown/throttle per player (`500ms` default).
- [x] Cap contributo per player (`0.35` target instance, policy deny).
- [x] Daily town category cap (`ratio 2.0` su target daily bucket).
- [x] Anti-AFK sui sampler `playtime_minutes` e `transport_distance`.
- [x] DoD:
  - [x] exploit comuni bloccati,
  - [x] reason-code stabili e localizzati in diagnostica/probe.

## M4 - GUI progression completa `COMPLETATO`
- [x] Progression Hub unico in `/city` (bottom bar), separato dal menu Azioni.
- [x] Dashboard levelling con:
  - [x] sfide attive,
  - [x] progress città per ciclo,
  - [x] top contributor per ciclo attivo,
  - [x] reward preview safe (XP/item/count comandi).
- [x] Rimozione shortcut duplicati `Livelli`/`Sfide` dal menu Azioni.
- [x] DoD:
  - [x] zero testo hardcoded user-facing,
  - [x] zero `CommandException` nei path hub.

## M5 - Reward e ranking integration
- Accredito XP/challenge reward su progression city.
- Sync ranking board (villaggi/regni) con policy definita.
- Eventuali reward economy/perk progressivi.
- DoD:
  - ledger reward idempotente,
  - ranking coerente dopo retry/fallback.

## M6 - Event layer + hardening `COMPLETATO`
- [x] Streak weekly complete-based con bonus XP applicato solo alle reward weekly.
- [x] Event layer mensile globale in due finestre (`1-15`, `16-fine mese`).
- [x] Reward evento configurabili e toggle-able (`top1/top2/top3/threshold`) con idempotenza grant key.
- [x] Probe esteso con statistiche streak/eventi.
- [x] Bot release gate script (`fast`, `full-positive`, `full-negative`, `full`) + report JSON.
- [x] Hardening `scripts/start.sh` (Java check, fail-fast deps, summary e `PREPARE_ONLY` readiness).
- [x] DoD:
  - [x] bonus streak idempotente e persistito,
  - [x] finalize evento senza doppio payout su restart,
  - [x] runbook operativo aggiornato per human testing.

## M9 - Fairness + Reward model `COMPLETATO`
- [x] Scaling target per `active_contributors_7d` su cicli per-city.
- [x] Doppia soglia progress `100%/140%` con cap tecnico a 140%.
- [x] Reward personali light on-completion (`>=5%` o threshold assoluta) con grant idempotenti.
- [x] Modello competitivo race/event con `Top1+Top2+Top3+Threshold` toggleabile.
- [x] Probe M9 con metriche fairness fallback e personal reward grants/skips.

## M11 - Storage + Observability + UX finale `COMPLETATO`
- [x] `ChallengeStore` introdotto come layer unico runtime (`service -> store`, no SQL diretto nel service).
- [x] Topologia operativa `MySQL + SQLite` con outbox/replay e modalità degraded.
- [x] Diagnostica store in `/cittaexp probe` (`mode/mysql/outbox/lastSync/replayLag`).
- [x] Contributor visibility estesa:
  - [x] contributo personale + eleggibilità reward,
  - [x] top contributor per istanza,
  - [x] carry ratio per ciclo.
- [x] Recap post-ciclo + archivio 90 cicli in `Progressione Hub`.
- [x] KPI M11 esposti in probe (`completion/abandonment/anti-abuse/winner-distribution/duplicate-grant/contribution-spread/carry/reroll/veto`).
