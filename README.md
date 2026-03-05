# CittaEXP

Plugin Paper 1.21.11 in stato bootstrap per funzionalita' legate all'EXP cittadina.

## Obiettivo
- Preparare una base tecnica minima e pulita per implementazioni future.
- Centralizzare utilita' condivise appoggiandosi a `minecraft-common-lib`.

## Dipendenze
- Java 21
- Paper API `1.21.11-R0.1-SNAPSHOT`
- `minecraft-common-lib` (jar locale)

Configurazione jar common-lib:
- default: `../minecraft-common-lib/build/libs/minecraft-common-lib-2.1.0.jar`
- override: `./gradlew build -PcommonLibJar=/path/to/minecraft-common-lib-2.1.0.jar`

## Moduli futuri (outline)
- Core domain `cittaexp.core` per regole EXP.
- Integration layer `cittaexp.integration` per hook Bukkit/Paper.
- Infrastructure `cittaexp.infrastructure` per persistenza/config/runtime wiring.
- API pubblica minima per altri plugin (da definire).

## Prossimi step
- Definire lo scope funzionale v1.
- Disegnare config e comando principale.
- Stabilire test strategy (unit + integration su MockBukkit/Paper locale).
