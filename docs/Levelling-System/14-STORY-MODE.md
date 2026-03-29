# 14 - Story Mode

## Obiettivo
Ridefinire il sistema oggi noto tecnicamente come `Atlas` in un layer prodotto chiamato **Story Mode**: una progressione narrativa, permanente e cooperativa che accompagna la crescita della città lungo tutto il suo ciclo di vita.

Story Mode non sostituisce il resto del sistema città. È il layer che dà direzione alla città nel lungo periodo, mentre gli altri layer mantengono viva la rotazione, la competizione e il farming cooperativo.

---

## Posizionamento nel sistema città

### Nome prodotto vs backend tecnico
- **Nome player-facing:** `Story Mode`
- **Backend tecnico attuale:** `CityAtlasService` + `atlas.yml`

Il rename è concettuale e UX. Il runtime Atlas resta il motore sottostante finché non verrà fatta un’eventuale rifattorizzazione tecnica dedicata.

### Ruolo rispetto agli altri layer
- **Story Mode**: progressione permanente della città, a capitoli.
- **Board giornaliera/settimanale/mensile**: contenuto ciclico che mantiene il server attivo ogni giorno.
- **Race**: picchi competitivi a finestre fisse.
- **Season Codex**: arco stagionale lungo e prestigioso.
- **City Defense / Mob Invasion**: contenuto PvE cooperativo separato, collegabile a Story Mode in capitoli futuri.

### Principio di design
Story Mode è il pezzo che dice alla città: “questo è il prossimo grande passo della tua storia”.

Non nasce per sostituire le missioni cicliche. Nasce per dare un asse narrativo e strutturale a tutta la crescita della città.

## Stato Runtime
- `Story Mode` è la surface player-facing principale sopra `Atlas`.
- Il catalogo runtime vive in `atlas.yml -> storyMode`.
- I capitoli base live sono `40`.
- Il gate attivo è:
  - livello minimo capitolo;
  - capitolo precedente completato al `100%`.
- Dopo il capitolo `40` è attivo un primo layer `Elite`.
- Le prove `DEFENSE` sono supportate runtime e leggono le vittorie del sistema `City Defense`.

---

## Connessione con la progressione città

### Livelli città
La città continua a crescere tramite **City XP**.  
Story Mode usa il livello città come gate principale di sblocco dei capitoli.

### Stage città attuali
Il sistema città attuale è organizzato in questi stage principali:

| Stage | Range livelli |
| --- | --- |
| Avamposto | 1-9 |
| Borgo | 10-24 |
| Villaggio | 25-44 |
| Cittadina | 45-69 |
| Città | 70-99 |
| Regno | 100+ |

### Relazione tra stage e Story Mode
Story Mode non sostituisce gli stage.  
Gli stage definiscono la maturità politica e strutturale della città.  
Story Mode definisce il suo percorso narrativo e i suoi grandi obiettivi cooperativi.

In pratica:
- il **livello città** sblocca i capitoli;
- gli **stage** determinano il tipo di contenuti che la città è credibile e pronta ad affrontare;
- i **Seals** e i gate di promozione continuano a essere collegati al layer permanente sottostante oggi gestito da Atlas.

### Regola di sblocco capitoli
Story Mode usa una progressione a **40 capitoli base**.

Regola:
- si sblocca **1 capitolo ogni 5 livelli città**;
- ogni capitolo richiede:
  - **livello minimo città**;
  - **progressione sufficiente del capitolo precedente**.

Formula di gate consigliata:
- `Capitolo 1` -> livello `1`
- `Capitolo 2` -> livello `6`
- `Capitolo 3` -> livello `11`
- `Capitolo N` -> livello `1 + ((N - 1) * 5)`

Gate sequenziale:
- il capitolo successivo non si apre solo per livello;
- richiede anche uno stato minimo del precedente, così il percorso resta davvero narrativo e non solo numerico.

### Copertura capitoli base
I 40 capitoli base coprono indicativamente il percorso città da `lv 1` a `lv 200`.

Distribuzione naturale sugli stage:

| Stage | Capitoli medi sbloccabili |
| --- | --- |
| Avamposto | 1-2 |
| Borgo | 3-5 |
| Villaggio | 6-9 |
| Cittadina | 10-14 |
| Città | 15-20 |
| Regno | 21-40 |

### Elite endgame
Dopo il capitolo 40 parte un layer di **capitoli elite endgame**.

Questo serve a:
- evitare che Story Mode termini troppo presto rispetto alla vita del server;
- dare un obiettivo stabile alle città già consolidate;
- offrire contenuti più selettivi, più prestigiosi e più collegabili a sistemi come invasioni, eventi o grandi progetti civici.

I capitoli elite non sono ancora definiti uno a uno in questo documento.  
Qui vengono fissati come **fase successiva al capitolo 40**, pensata per il late game di `Regno`.

---

## Come Story Mode si incastra con il resto del prodotto

### 1. Board missioni
Le board cicliche e Story Mode devono convivere con ruoli diversi:
- **Board** = attività quotidiana, varietà, pressione temporale, reward rapidi.
- **Story Mode** = avanzamento di lungo periodo, identità della città, grandi step di maturazione.

Uso corretto:
- le board tengono i player attivi tutti i giorni;
- Story Mode dà una direzione a medio-lungo termine.

### 2. Race
Le race non devono essere il cuore di Story Mode.

Relazione corretta:
- la race è contenuto competitivo a finestra;
- Story Mode è contenuto progressivo e cooperativo.

Collegamento possibile:
- alcune race possono supportare un capitolo;
- ma Story Mode non deve dipendere strutturalmente dalla race per esistere.

### 3. Season Codex
Season Codex è il layer stagionale ad arco lungo.  
Story Mode è la progressione permanente della città.

Relazione:
- Story Mode costruisce fondamenta permanenti;
- Season Codex costruisce un arco prestigio temporaneo;
- i due sistemi possono condividere temi, ma non devono essere la stessa cosa.

### 4. City Defense / Mob Invasion
City Defense è un sistema PvE cooperativo separato, con sessioni a wave, Guardian, reward e rischio reale.

Relazione con Story Mode:
- non è obbligatoria nei capitoli iniziali;
- è un **future hook opzionale**;
- alcuni capitoli futuri possono includere una `mob invasion` come requisito o come prova speciale di capitolo.

Questo è particolarmente naturale per capitoli:
- `Frontier`
- `Defense`
- `Siege`
- `Guardian`
- `Capitale sotto assedio`

Quindi:
- **oggi** Story Mode può vivere senza invasioni;
- **domani** le invasioni possono diventare uno dei tipi di contenuto validi per capitoli avanzati o elite.

### 5. Reward e Item Vault
Story Mode si lega bene al sistema reward già esistente:
- `XP città`
- `materiali`
- `reward via command`
- `Item Vault città`

Per il prodotto:
- le reward materiali di Story Mode hanno senso se finiscono nel **vault città**;
- i reward più importanti possono supportare espansione, costruzione, difesa e prestigio.

### 6. Seals e promozione stage
Il layer Story Mode si collega anche ai `City Seals` perché il motore sottostante Atlas oggi è la fonte canonica del progresso permanente.

Nel modello prodotto:
- Story Mode rappresenta il percorso che, sotto il cofano, può continuare ad alimentare seals/gate di stage;
- in futuro, alcuni capitoli chiave possono diventare prerequisiti espliciti di stage promotion.

---

## Cosa supporta oggi il plugin

Questa sezione serve a chiarire quali pezzi Story Mode può già riusare.

### Progressione città
- livelli città
- stage principali città
- gating/promozione stage
- City XP
- Seals / gate permanenti

### Missioni e contenuti ciclici
- board giornaliere
- board settimanali
- board mensili
- race giornaliere
- race settimanali
- race mensili
- season codex
- challenge/eventi con progressione cooperativa

### Reward
- XP città
- item reward
- reward via command template
- item vault città
- claim/idempotenza reward

### Cooperazione e governance città
- progress town-first
- GUI-first su `/city`
- progression hub
- sezioni missioni integrate
- gestione città e routing GUI

### Sicurezza runtime
- anti-abuse
- dedupe
- throttling
- contribution caps
- reason codes e probe

### Storage e affidabilità
- persistenza challenge
- backend primario/fallback
- replay/ledger
- retention e storico

### PvE cooperativo separato
- City Defense / Mob Invasion
- wave
- guardian
- reward XP/materiali
- gating per livello città

Story Mode, quindi, non parte da zero.  
Si appoggia a un plugin che già supporta progressione città, contenuti cooperativi, reward, persistenza e UX city-first.

---

## Modello Story Mode

### Unità principale: il capitolo
Il capitolo è l’unità base di Story Mode.

Ogni capitolo deve avere:
- identità
- livello minimo
- gate sequenziale
- macrotema
- tipi di contenuto ammessi
- stato progresso

### Tipi di contenuto ammessi in un capitolo
Un capitolo può contenere:
- quest cooperative permanenti
- task tematici di raccolta o combattimento
- task di costruzione o sviluppo
- task civici o logistici
- hook futuri verso `mob invasion`

Un capitolo non è definito dalla singola quest.  
È definito dal suo ruolo nella storia della città.

### Tipi di stato capitolo
Per la UI e per il prodotto, uno stato capitolo può essere:
- bloccato
- sbloccato
- in corso
- completato
- elite

---

## Bridge con Atlas attuale

### Stato attuale
Il backend Atlas oggi è organizzato in 6 macrochapter:
- Extraction
- Combat
- Agronomy
- Industry
- Frontier
- Civic

### Reinterpretazione in Story Mode
Story Mode non deve presentare questi 6 macrochapter come UX finale.

Invece:
- i `40 capitoli` diventano il layer narrativo visibile;
- sotto, i dati Atlas possono continuare a esistere come:
  - progress permanente;
  - famiglie;
  - target;
  - seals;
  - reward band.

### Bridge futuro consigliato
Nel passaggio reale:
- più capitoli Story Mode potranno mappare allo stesso macrochapter Atlas;
- un capitolo potrà consumare più famiglie Atlas;
- alcune famiglie Atlas potranno diventare “mattoni” di più capitoli narrativi.

Questo consente di:
- preservare il runtime esistente;
- migliorare la leggibilità player-facing;
- evitare un hard-reset concettuale del sistema permanente.

---

## Catalogo placeholder capitoli base

I capitoli sotto sono **placeholder di struttura**, non contenuto definitivo.

| Capitolo | Livelli città | Stato | Macrotema placeholder | Tipi di contenuto ammessi |
| --- | --- | --- | --- | --- |
| 1 | 1-5 | base | Fondazione | raccolta base, farming base |
| 2 | 6-10 | base | Prime scorte | mining base, contributi base |
| 3 | 11-15 | base | Crescita iniziale | costruzione, raccolta |
| 4 | 16-20 | base | Prime difese | combattimento base, supporto |
| 5 | 21-25 | base | Borgo attivo | produzione, agricoltura |
| 6 | 26-30 | base | Nuove rotte | trasporto, esplorazione leggera |
| 7 | 31-35 | base | Risorse preziose | mining intermedio |
| 8 | 36-40 | base | Caccia organizzata | mob combat, rare kill leggere |
| 9 | 41-45 | base | Villaggio stabile | civic, supporto città |
| 10 | 46-50 | base | Infrastrutture | costruzione, industria |
| 11 | 51-55 | base | Terre fertili | farming avanzato |
| 12 | 56-60 | base | Officine | craft, redstone, produzione |
| 13 | 61-65 | base | Frontiera vicina | esplorazione, frontier |
| 14 | 66-70 | base | Difesa civica | civic, supporto, combattimento |
| 15 | 71-75 | base | Città emergente | progressione mista |
| 16 | 76-80 | base | Rete logistica | trasporto, contribution |
| 17 | 81-85 | base | Economia viva | trade, civic, market |
| 18 | 86-90 | base | Reparti speciali | combat avanzato |
| 19 | 91-95 | base | Espansione urbana | industry, construction |
| 20 | 96-100 | base | Prestigio cittadino | quest ibride, reward forti |
| 21 | 101-105 | base | Regno nascente | macro-obiettivi cooperativi |
| 22 | 106-110 | base | Miniere profonde | extraction avanzata |
| 23 | 111-115 | base | Addestramento reale | combat avanzato |
| 24 | 116-120 | base | Campi del regno | agronomy avanzata |
| 25 | 121-125 | base | Laboratori | potion, craft, supporto |
| 26 | 126-130 | base | Vie del commercio | civic, economy |
| 27 | 131-135 | base | Terre esterne | frontier, structure |
| 28 | 136-140 | base | Sala del comando | civic, contribution |
| 29 | 141-145 | base | Cuore industriale | redstone, industry |
| 30 | 146-150 | base | Guardie del regno | combat + supporto |
| 31 | 151-155 | base | Espansione remota | exploration, dimension |
| 32 | 156-160 | base | Spedizioni | frontier, vault, raid-like |
| 33 | 161-165 | base | Linea di rifornimento | logistics, food, civic |
| 34 | 166-170 | base | Reparti elite | hard combat, rare mob |
| 35 | 171-175 | base | Opere maggiori | monument, construction |
| 36 | 176-180 | base | Prove del regno | multi-step, cooperative |
| 37 | 181-185 | base | Dominio territoriale | civic, expansion |
| 38 | 186-190 | base | Crocevia del potere | mixed late game |
| 39 | 191-195 | base | Ultime preparazioni | mixed pre-elite |
| 40 | 196-200 | base | Finale del regno | chapter finale, unlock elite |

---

## Elite endgame dopo il capitolo 40

Dopo il capitolo 40 il sistema entra nella fase **elite**.

Questa fase serve a:
- estendere Story Mode nel late game;
- introdurre capitoli più rari, più lenti e più prestigiosi;
- collegare eventualmente:
  - invasioni città;
  - grandi progetti;
  - contenuti boss o siege;
  - reward estetiche o di prestigio.

In questa fase documentale, i capitoli elite non vengono ancora enumerati uno a uno.  
Vengono fissati come secondo layer della progressione Story Mode.

---

## Direzione implementativa futura

Quando il sistema verrà collegato davvero al runtime:
- `Story Mode` diventerà il nome ufficiale della sezione oggi chiamata Atlas;
- la GUI 9x6 userà i capitoli come entry point primario;
- il backend Atlas esistente continuerà a fornire progresso, famiglie, seals e reward;
- i contenuti di ogni capitolo verranno definiti singolarmente;
- alcuni capitoli potranno agganciare `City Defense / Mob Invasion` come prova speciale.

Il punto importante è questo:

**Story Mode non nasce come feature laterale.  
Nasce come il layer che unisce la crescita città, la narrativa cooperativa e il contenuto permanente del plugin.**
