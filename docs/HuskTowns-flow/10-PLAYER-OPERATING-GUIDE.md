# 10 - Player Operating Guide (alto livello)

## Obiettivo del documento
Spiegare come un player dovrebbe usare CittaEXP nel tempo, con focus pratico su:
- fondazione citta,
- crescita membri,
- gestione quotidiana,
- interazione con staff e richieste.

Questo documento e pensato per i player, non per amministratori tecnici.

## Contratto comandi player (attuale)
- `/city create`
- `/city info [city|gui]`
- `/city invite <player|accept|deny ...>`
- `/city request <join|approve|reject|upgrade|unfreeze|delete|list|cancel ...>`
- `/city kick <player>`
- `/city leave`
- `/city warp <set|info|city>`

## Flusso operativo nel tempo

## Fase 1: avvio (giorno 0)
1. Decidi nome e tag della citta.
2. Esegui `/city create`.
3. Completa il wizard di creazione (dialog + setup richiesto dal server).
4. Verifica stato con `/city info`.
5. Imposta subito un punto comodo con `/city warp set`.

Outcome atteso:
- citta attiva,
- leader definito,
- punto warp iniziale pronto.

## Fase 2: primi membri (giorni 1-3)
1. Invita i primi giocatori con `/city invite <player>`.
2. I giocatori invitati usano:
   - `/city invite accept <id>` per entrare,
   - `/city invite deny <id>` per rifiutare.
3. In alternativa, i player esterni possono chiedere ingresso con:
   - `/city request join <city> [nota]`.
4. Leader/moderatori gestiscono le richieste:
   - `/city request approve <id>`
   - `/city request reject <id> [nota]`

Outcome atteso:
- roster iniziale stabile,
- solo membri utili e attivi.

## Fase 3: gestione ordinaria (settimanale)
Routine consigliata:
1. Controlla periodicamente `/city info gui` per stato reale.
2. Aggiorna il warp se la base cambia con `/city warp set`.
3. Pulisci inattivi o comportamenti scorretti:
   - `/city kick <player>`
4. Tieni ordinata la coda richieste:
   - `/city request list`
   - `/city request cancel <ticketId>` se hai aperto richieste errate.

Obiettivo:
- evitare caos organizzativo,
- mantenere la citta sempre usabile per tutti i membri attivi.

## Fase 4: crescita e governance (medio termine)
Quando la citta cresce:
1. Usa il canale request per azioni sensibili:
   - `/city request upgrade <motivo>`
   - `/city request unfreeze <motivo>`
   - `/city request delete <motivo>`
2. Monitora stato ticket con `/city request list`.
3. Coordina con lo staff per review/approvazione.

Nota:
- alcune azioni sono staff-gated e non si completano istantaneamente.

## Fase 5: casi speciali

### Se vuoi entrare in una citta senza invito
1. `/city request join <city> [nota]`
2. Attendi review dei moderatori della citta.

### Se vuoi uscire dalla tua citta
1. `/city leave`
2. Verifica di non avere ruoli/obblighi pendenti prima di uscire.

### Se la citta e in freeze
1. Le azioni mutanti possono essere bloccate.
2. Apri richiesta dedicata:
   - `/city request unfreeze <motivo>`

## Checklist player semplice
Ogni sessione utile:
1. Controlla stato citta (`/city info` o `/city info gui`).
2. Controlla richieste aperte (`/city request list`).
3. Verifica che il warp sia corretto (`/city warp info`).
4. Esegui azioni membership solo quando serve (`invite`, `approve`, `kick`).

## Errori comuni da evitare
1. Aprire richieste senza motivo chiaro.
2. Invitare troppi player senza filtro.
3. Dimenticare di aggiornare il warp quando la base si sposta.
4. Lasciare ticket pendenti troppo a lungo.

## Regola d'oro
Usa `/city` come unico entrypoint player.
I comandi nativi HuskTowns (`/town`) possono essere bloccati lato server per mantenere UX coerente.
