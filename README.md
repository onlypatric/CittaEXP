# CittaEXP

Plugin Paper 1.21.11 per gestione completa citta (borghi/villaggi/regni), con GUI-first, persistenza resiliente e workflow staff.

## Obiettivo
- Consegnare un plugin citta full-scope (war esclusa) pronto per produzione.
- Mantenere architettura modulare con `minecraft-common-lib`.

## Dipendenze
- Java 21
- Paper API `1.21.11-R0.1-SNAPSHOT`
- `minecraft-common-lib` (jar locale, baseline `3.0.0`)
- `adapter-itemsadder` (jar locale, baseline `3.0.0`)
- Runtime dependency obbligatorie: `Vault`, `HuskClaims`, `ClassificheEXP`

Configurazione jar locali build-time:
- `commonLibJar` default: `../minecraft-common-lib/build/libs/minecraft-common-lib-3.0.0.jar`
- `itemsAdderAdapterJar` default: `../minecraft-common-lib/adapter-itemsadder/build/libs/adapter-itemsadder-3.0.0.jar`
- override: `./gradlew build -PcommonLibJar=... -PitemsAdderAdapterJar=...`

## Stato corrente
- GUI preview e Dialog showcase disponibili.
- Foundation persistence MySQL primario + SQLite fallback + replay outbox.
- Message layer MiniMessage in italiano via `messages.yml`.
- Guard runtime dipendenze obbligatorie.

## Framework planning docs
- `docs/frameworks/GUI-FIRST-FRAMEWORK-BLUEPRINT.md`
- `docs/frameworks/GUI-FIRST-ACTION-PLAN.md`
- `docs/frameworks/GUI-KEYS-CATALOG-DRAFT.md`

## Full-scope docs
- `docs/architecture/DATA-MODEL.md`
- `docs/architecture/DOMAIN-SERVICES.md`
- `docs/architecture/INTEGRATION-CONTRACTS.md`
- `docs/roadmap/FULL-DELIVERY-PLAN.md`
- `docs/operations/RUNBOOK.md`

## Preview commands
- `/cittaexp <dashboard|members|roles|taxes|wizard>`
- `/cittaexp scenario <default|freeze|capital|lowfunds|kingdom>`
- `/cittaexp theme <auto|vanilla|itemsadder>`
- `/cittaexp hud <show|hide> <hudId>`
- `/cittaexp probe`

Permission:
- `cittaexp.admin.gui.preview` (default `op`)
