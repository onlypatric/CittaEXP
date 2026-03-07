# CittaEXP Full Delivery Plan (100% scope)

## Milestone 1 - Core Domain & Data
- Stabilizzare model/enums/ports/service contracts.
- Completare schema SQL definitivo e migration namespace `cittaexp-core`.
- Implementare repository/use case base su MySQL + fallback SQLite replay.
- DoD:
  - test invarianti dominio verdi
  - schema boot idempotente
  - probe persistence completo

## Milestone 2 - External Integrations Hard-Required
Stato: `COMPLETATO` (7 marzo 2026)
- Implementare adapter runtime per Vault, HuskClaims, ClassificheEXP.
- Applicare dependency guard fail-fast in enable.
- Mappare errori esterni in taxonomy CittaEXP.
- DoD:
  - test assenza plugin -> fail esplicito
  - test smoke su server locale con dipendenze reali

## Milestone 3 - City Lifecycle completo
Stato: `COMPLETATO` (7 marzo 2026, aggiornato governance wave)
- Creazione citta (wizard + dialog), auto-claim 100x100, membership flow completo.
- Ruoli custom completi con matrix permission.
- Freeze engine operativo con gate azioni.
- Governance aggiuntiva:
  - `Vice` unico per citta con successione automatica su uscita leader.
  - Gerarchia moderazione `CAPO > VICE > ruoli custom`.
  - Comandi player estesi: `/city vice ...` e `/city perms ...`.
  - Permessi claim per-membro (`access/container/build`) con sync HuskClaims API.
- DoD:
  - create/invite/join/kick/leave role-based funzionanti
  - freeze gates verificati da test
  - successione vice->leader verificata
  - sync permessi claim verificato su lifecycle membership

## Milestone 4 - Economia, Tasse, Capitale
- Tax policy config + scheduler mensile.
- Addebito Vault capo + freeze insolvenza.
- Sync capitale da ranking + bonus mensile.
- Ledger economico e audit integrati.
- DoD:
  - ciclo mensile end-to-end testato
  - capitale dinamica con revoca/assegnazione stabile

## Milestone 5 - Staff Queue & Governance
- Queue ticket in-game: `UPGRADE`, `UNFREEZE`, `DELETE`.
- GUI/command staff per review, con audit trail.
- Policy delete borgo autonoma; villaggio/regno staff-gated.
- DoD:
  - workflow request->pending->review->effect completo
  - storico ticket/audit consultabile

## Milestone 6 - GUI/UX Complete + i18n
- Consolidare GUI esistenti con dati reali.
- Aggiungere schermate approvals/claim/upgrade/delete flows.
- Coprire tutti i messaggi user-facing in italiano via `messages.yml`.
- DoD:
  - nessun testo hardcoded user-facing
  - command e GUI allineati a stato reale dominio

## Milestone 7 - Hardening & Release
- Integration + load tests su dataset realistico.
- Runbook operativo finale.
- Packaging release e acceptance checklist.
- DoD:
  - zero critical test failures
  - startup/disable clean con scheduler safe
  - release candidate firmata e validata su server test

## Bot Harness Automation
- Suite fast: `./gradlew testFast`
  - include domain/command/persistence sqlite/adapters senza dipendenze docker.
- Suite full: `./gradlew testFull`
  - include test MySQL con Testcontainers (`@Tag(\"full\")`, skip automatico se Docker assente).
- Output:
  - machine-readable: XML JUnit in `build/test-results/`.
  - human-readable: report HTML in `build/reports/tests/`.

## Sequenza dipendenze
- M2 dipende da M1
- M3 dipende da M1+M2
- M4 dipende da M2+M3
- M5 dipende da M3+M4
- M6 dipende da M3+M4+M5
- M7 dipende da tutti i milestone precedenti
