## Checklist Implementazione Eventi

Questo documento unisce il contenuto utile di:
- [18-EVENTS.md](/Users/patric/Documents/Minecraft/CittaEXP/docs/Levelling-System/18-EVENTS.md)
- [18-REFINED-IMPLEMENTATION-RESEARCH.md](/Users/patric/Documents/Minecraft/CittaEXP/docs/Levelling-System/18-REFINED-IMPLEMENTATION-RESEARCH.md)

L'obiettivo e semplice:
- chiarire cosa implementare davvero adesso
- separare i contenuti subito fattibili da quelli che richiedono nuovi domini
- avere una checklist a fasi usabile come backlog tecnico

## Stato corrente

Gia avviato in codice:
- [x] documento checklist unificato
- [x] catalogo preset staff events introdotto
- [x] separazione preset `AUTO_RACE` e `JUDGED_BUILD`
- [x] `create auto` supporta scelta preset
- [x] `create judged` supporta scelta preset

Preset gia pronti nel codebase:
- [x] `bonifica_gallerie`
- [x] `raccolto_stagione`
- [x] `spedizione_oceanica`
- [x] `difesa_civica`
- [x] `porta_citta`
- [x] `mercato_cittadino`
- [x] `quartiere_agricolo_modello`
- [x] `distretto_industriale_ordinato`

Nota tecnica importante:
- Il wrapper `AUTO_RACE` attuale supporta bene solo preset compatibili con il modello competitivo gia esistente.
- Eventi come `Convoglio del Vault Imperiale`, `Fiera dei Mestieri` e `Frontiera del Nether` restano obiettivi P1 di contenuto, ma richiedono prima un ampliamento del wrapper staff event o un kind evento piu adatto.

## Principi

- Gli eventi devono vivere nel normale survival delle citta.
- Nessun mondo separato, arena separata o minigioco staccato.
- Prima si massimizza il riuso del motore challenge e degli staff events v1.
- Solo dopo si introducono nuovi kind evento.
- Paper API va usata dove serve davvero; quando il plugin ha gia una source of truth migliore, si usa quella.

## Fondamentali tecnici da tenere

- Per i vault event, la source of truth deve restare il ledger interno del vault, non eventi inventory raw.
- Per gli eventi territoriali, la source of truth deve essere il subsystem claim/town interno, non Paper.
- Per structure discovery, la source of truth deve restare la logica plugin; `StructuresLocateEvent` non basta.
- Hook Paper utili davvero:
  - `BlockBreakEvent`
  - `BlockPlaceEvent`
  - `PlayerTradeEvent`
  - `CraftItemEvent`
  - `BrewEvent`
  - `PlayerFishEvent`
  - `EntityDeathEvent`
  - `RaidFinishEvent`
  - `PlayerChangedWorldEvent`
  - `PlayerMoveEvent` solo con throttling
  - `InventoryClickEvent`
  - `AsyncChatEvent` se serve input testo
  - `BukkitScheduler`
- Hook da non usare come base principale:
  - `VillagerAcquireTradeEvent`
  - `StructuresLocateEvent`
  - Paper `Dialog` come fondazione del subsystem, finche resta troppo experimental

## Priorita di contenuto

### Wave P1

`AUTO_RACE`
- [ ] Convoglio del Vault Imperiale
- [ ] Raccolto di Stagione
- [ ] Fiera dei Mestieri
- [ ] Spedizione Oceanica
- [ ] Frontiera del Nether

`JUDGED_BUILD`
- [ ] Porta della Citta
- [ ] Mercato Cittadino

### Wave P2

`AUTO_RACE`
- [ ] Bonifica delle Gallerie
- [ ] Difesa Civica

`JUDGED_BUILD`
- [ ] Quartiere Agricolo Modello
- [ ] Distretto Industriale Ordinato

### Wave P3

Nuovi kind
- [ ] Contratti della Corona (`TOWN_CONTRACT`)
- [ ] Espansione dei Confini (`TERRITORIAL_SNAPSHOT`)
- [ ] Piano Carovane del Nether (`TOWN_CONTRACT`)
- [ ] Rotta dei Porti (`TOWN_CONTRACT`)
- [ ] Grande Strada Reale (`TERRITORIAL_SNAPSHOT` esteso)
- [ ] Rete di Avamposti (`TERRITORIAL_SNAPSHOT` esteso)

## Fase 1

Obiettivo:
- aggiungere contenuti nuovi subito
- riusare al massimo gli staff events v1
- evitare nuovi domini nel primo sprint

### 1. Preset catalog staff events

- [x] introdurre un catalogo preset unico per staff events
- [x] separare preset `AUTO_RACE` e `JUDGED_BUILD`
- [ ] per ogni preset definire:
  - [x] key
  - [x] titolo
  - [x] sottotitolo
  - [x] descrizione
  - [x] payload default
  - [ ] reward preset target
  - [ ] testi player-facing

### 2. Preset `AUTO_RACE` P1

- [ ] Convoglio del Vault Imperiale
- [ ] Raccolto di Stagione
- [ ] Fiera dei Mestieri
- [ ] Spedizione Oceanica
- [ ] Frontiera del Nether

Stato reale attuale del wrapper:
- [x] Bonifica delle Gallerie
- [x] Raccolto di Stagione
- [x] Spedizione Oceanica
- [x] Difesa Civica

### 3. Preset `JUDGED_BUILD` P1

- [x] Porta della Citta
- [x] Mercato Cittadino

### 4. Flow staff create

- [x] `create auto` deve poter partire da preset
- [x] `create judged` deve poter partire da preset
- [ ] ridurre i campi manuali ripetitivi
- [x] rendere evidente quali preset sono gia pronti

### 5. UI player eventi

- [ ] migliorare la card evento in tab `Eventi`
- [ ] mostrare meglio:
  - [ ] stato
  - [ ] timer
  - [ ] tipo evento
  - [ ] reward mode
- [ ] se `AUTO_RACE` e attivo, aprire la linked challenge
- [ ] se `JUDGED_BUILD`, aprire dialog evento + submit flow

### 6. UI staff minima

- [ ] rendere piu utile `events list`
- [ ] valutare una GUI staff minima per:
  - [ ] bozze
  - [ ] pubblicati
  - [ ] attivi
  - [ ] review
  - [ ] conclusi

### 7. Reward preset eventi

- [ ] definire preset reward per:
  - [ ] logistica/vault
  - [ ] agricolo
  - [ ] commercio
  - [ ] esplorazione
  - [ ] build judged

### 8. Test Fase 1

- [ ] creare almeno un evento per ogni preset P1
- [ ] verificare create -> publish -> start -> close
- [ ] verificare card e dialog
- [ ] verificare reward finali
- [ ] verificare che la tab `Eventi` non regredisca

## Fase 2

Obiettivo:
- alzare la qualita del sistema eventi
- aumentare il valore gameplay
- migliorare scoring e review senza nuovi domini grossi

### 1. Preset `AUTO_RACE` P2

- [ ] Bonifica delle Gallerie
- [ ] Difesa Civica

### 2. Preset `JUDGED_BUILD` P2

- [ ] Quartiere Agricolo Modello
- [ ] Distretto Industriale Ordinato

### 3. Scoring e anti-abuse migliori

- [ ] `Fiera dei Mestieri`: diversity scoring opzionale
- [ ] `Raccolto di Stagione`: score composito, non solo harvest
- [ ] `Frontiera del Nether`: combinare kill + loot + travel
- [ ] `Spedizione Oceanica`: ridurre dipendenza da RNG puro

### 4. Review judged piu forte

- [ ] note staff migliori
- [ ] placement piu leggibile
- [ ] publish-results piu chiaro
- [ ] reward manuale piu rapida

### 5. Submission UX

- [ ] update submission migliore
- [ ] conferma posizione + nota
- [ ] teleport staff rapido alla submission

### 6. Test Fase 2

- [ ] exploit test su preset piu sensibili
- [ ] test review flow completo
- [ ] test reward manuale judged

## Fase 3

Obiettivo:
- aggiungere nuovi modelli evento veri
- coprire i casi che non si modellano bene con `AUTO_RACE` e `JUDGED_BUILD`

### 1. Nuovo kind `TOWN_CONTRACT`

- [ ] definire dominio:
  - [ ] selezione contratto per citta
  - [ ] lock dopo prima consegna
  - [ ] progress per linee
  - [ ] ranking finale
- [ ] definire store:
  - [ ] `town_contract_selection`
  - [ ] `town_contract_progress_line`
- [ ] definire GUI/dialog player
- [ ] definire GUI/dialog staff
- [ ] primo contenuto:
  - [ ] Contratti della Corona

### 2. Espansioni `TOWN_CONTRACT`

- [ ] Piano Carovane del Nether
- [ ] Rotta dei Porti

### 3. Nuovo kind `TERRITORIAL_SNAPSHOT`

- [ ] definire dominio:
  - [ ] baseline snapshot
  - [ ] final snapshot
  - [ ] minimum hold duration
  - [ ] scoring delta
- [ ] definire source of truth su claim
- [ ] definire store snapshot
- [ ] definire finalizzazione e ranking
- [ ] primo contenuto:
  - [ ] Espansione dei Confini

### 4. Espansioni `TERRITORIAL_SNAPSHOT`

- [ ] Grande Strada Reale
- [ ] Rete di Avamposti

### 5. Test Fase 3

- [ ] restart durante evento
- [ ] citta create o sciolte durante evento
- [ ] exploit flip claim
- [ ] finalizzazione coerente

## Ordine consigliato di implementazione

- [ ] 1. Preset catalog staff events
- [ ] 2. Convoglio del Vault Imperiale
- [ ] 3. Raccolto di Stagione
- [ ] 4. Fiera dei Mestieri
- [ ] 5. Spedizione Oceanica
- [ ] 6. Porta della Citta
- [ ] 7. Mercato Cittadino
- [ ] 8. Frontiera del Nether
- [ ] 9. `TOWN_CONTRACT`
- [ ] 10. `TERRITORIAL_SNAPSHOT`

## Sprint iniziale consigliato

Se bisogna partire subito senza aprire un refactor grosso, il primo sprint deve essere questo:

- [ ] preset catalog staff events
- [ ] Convoglio del Vault Imperiale
- [ ] Raccolto di Stagione
- [ ] Fiera dei Mestieri
- [ ] Porta della Citta
- [ ] Mercato Cittadino
- [ ] reward preset eventi
- [ ] piccole rifiniture UI player/staff per tab `Eventi`

## Nota pratica

I contenuti qui segnati come P1 non sono tutti equivalenti:
- alcuni sono gia quasi completamente compatibili col motore attuale
- altri richiedono ancora di allargare il wrapper `AUTO_RACE` oltre i soli template competitivi esistenti

Quindi il principio corretto e:
- prima sbloccare preset e judged build
- poi allargare il supporto ai nuovi auto event piu specializzati
- solo dopo introdurre i nuovi kind veri
