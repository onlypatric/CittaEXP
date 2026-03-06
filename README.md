# CittaEXP

Plugin Paper 1.21.11 in stato bootstrap per funzionalita' legate all'EXP cittadina.

## Obiettivo
- Preparare una base tecnica minima e pulita per implementazioni future.
- Centralizzare utilita' condivise appoggiandosi a `minecraft-common-lib`.

## Dipendenze
- Java 21
- Paper API `1.21.11-R0.1-SNAPSHOT`
- `minecraft-common-lib` (jar locale, baseline `3.0.0`)
- `adapter-invui` (jar locale, baseline `3.0.0`)
- `adapter-itemsadder` (jar locale, baseline `3.0.0`)

Configurazione jar locali:
- `commonLibJar` default: `../minecraft-common-lib/build/libs/minecraft-common-lib-3.0.0.jar`
- `invUiAdapterJar` default: `../minecraft-common-lib/adapter-invui/build/libs/adapter-invui-3.0.0.jar`
- `itemsAdderAdapterJar` default: `../minecraft-common-lib/adapter-itemsadder/build/libs/adapter-itemsadder-3.0.0.jar`
- override: `./gradlew build -PcommonLibJar=... -PinvUiAdapterJar=... -PitemsAdderAdapterJar=...`

## Moduli futuri (outline)
- Core domain `cittaexp.core` per regole EXP.
- Integration layer `cittaexp.integration` per hook Bukkit/Paper.
- Infrastructure `cittaexp.infrastructure` per persistenza/config/runtime wiring.
- API pubblica minima per altri plugin (da definire).

## Prossimi step
- Definire lo scope funzionale v1.
- Disegnare config e comando principale.
- Stabilire test strategy (unit + integration su MockBukkit/Paper locale).

## Framework planning docs
- `docs/frameworks/GUI-FIRST-FRAMEWORK-BLUEPRINT.md`
- `docs/frameworks/GUI-FIRST-ACTION-PLAN.md`
- `docs/frameworks/GUI-KEYS-CATALOG-DRAFT.md`

## Preview commands
- `/cittaexp <dashboard|members|roles|taxes|wizard>`
- `/cittaexp scenario <default|freeze|capital|lowfunds|kingdom>`
- `/cittaexp theme <auto|vanilla|itemsadder>`
- `/cittaexp hud <show|hide> <hudId>`
- `/cittaexp probe`

Permission:
- `cittaexp.admin.gui.preview` (default `op`)
