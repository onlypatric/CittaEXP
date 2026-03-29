# 11 - Macroaree Sistema Quest (stato attuale)

## Scopo
Questo documento riassume **le macroaree attuali** del sistema quest in modo funzionale (non tecnico), così da preparare il redesign.

## Macroaree Principali

### 1) Quest fisse
- **Secret Quest mensile** (nascosta): missione speciale con trigger predefiniti e reward alti.
- **Seasonal Team Phase Quests**: pacchetto stagionale fisso con quest multi-fase (raccolta -> costruzione -> boss).

### 2) Quest cicliche standard
- **Giornaliere (Daily Standard)**: missioni cooperative brevi, orientate al gameplay quotidiano.
- **Settimanali (Weekly Standard)**: missioni piu corpose, con obiettivi medi.
- **Mensili (Monthly Standard)**: missione principale lunga del mese.

### 3) Quest race
- **Daily Race**: 2 finestre giornaliere (rivelate a orario), vince la prima citta che completa.
- **Seasonal Race**: quest stagionali in modalita competitiva first-winner.

### 4) Quest evento
- **Monthly Event (window A/B)**: finestre evento mensili con classifica e reward competitivi (podio/soglia).

### 5) Quest stagionali
- Stagione meteorologica (Primavera/Estate/Autunno/Inverno).
- Contenuti dedicati con progressione lunga e reward premium.

## Macroaree Trasversali (di supporto)

### A) Reward
- XP citta
- Item nel vault citta
- Reward command-based (senza mostrare command raw in lore)
- Distinzione tra reward completion/excellence/winner/event

### B) Progressione e UI
- Flusso GUI-first dentro `/city`
- Vista Missioni separata per tab/ciclo
- Progressione visiva con barra e tempo residuo

### C) Integrita/Fairness
- Anti-abuse (throttle/cap/dedupe/AFK/controlli vari)
- Scaling target (in base al tipo ciclo e ad altri fattori runtime)
- Persistenza stato e recovery su restart

## Lista Unica Tipologie Quest Creabili (completa, high-level)
- `Contributo risorse`: la città deposita materiali utili nel vault comune per raggiungere un obiettivo cumulativo.
- `Mining`: la città avanza rompendo blocchi/minerali durante attività di miniera.
- `Uccisioni mob`: progresso tramite kill di mob ostili standard.
- `Uccisioni mob rari`: progresso con kill di mob rari/selettivi.
- `Boss kill`: progresso con kill di boss maggiori (es. Dragon/Wither/Warden).
- `Raccolta colture`: avanzamento con raccolta agricola (farm classica).
- `Farming ecosystem`: missione agricola mista (raccolta + gestione ciclo farming).
- `Costruzione`: avanzamento piazzando blocchi per build utili alla città.
- `Build a Monument`: variante costruzione lunga/cooperativa su grandi quantità di blocchi.
- `Automazione redstone`: progresso su azioni/attività legate a impianti redstone.
- `Pesca`: avanzamento tramite catture con canna da pesca.
- `Interazione animali`: progresso con azioni su animali (allevamento/gestione).
- `Scoperta strutture`: avanzamento visitando/scoprendo strutture naturali.
- `Archeologia`: progresso usando il pennello su contenuti archeologici.
- `Trial Vault`: progresso aprendo vault nelle Trial Chamber.
- `Raid vittoriosi`: progresso completando raid con esito vittoria villaggio.
- `Food Production`: progresso producendo/craftando cibo su target elevati.
- `Potion Brewing`: progresso producendo pozioni (anche varianti dedicate).
- `Craft Master`: progresso craftando item avanzati/tecnici.
- `Nether Expedition`: progresso su attività dedicate nel Nether.
- `Ocean Operation`: progresso su attività dedicate marine/oceaniche.
- `Secret Quest`: quest nascosta a trigger; viene scoperta/chiusa seguendo indizi.
- `Team Phase Quest`: quest multi-step stile MMO (fase risorse -> fase build -> fase boss).
- `Distanza trasporto`: progresso percorrendo distanza totale (movimento/trasporto).
- `Viaggi dimensioni`: progresso tramite viaggi validi tra dimensioni.
- `Trade villager`: progresso effettuando scambi con villager.
- `Economia avanzata`: variante economica più stretta su operazioni di trade valide.
- `Tempo online`: progresso da tempo attivo in gioco.
- `Raccolta XP`: progresso raccogliendo esperienza.
- `Structure loot` (opzionale/disattivabile): avanzamento da apertura loot in strutture; attualmente può essere lasciata off per scelta design.
- `Quest città cooperative`: ogni tipologia sopra può essere emessa come missione per singola città (non competitiva diretta).
- `Quest race giornaliere`: subset competitivo first-winner in finestre orarie.
- `Quest evento mensile`: missioni da classifica con podio/soglia reward.
- `Quest stagionali`: missioni su finestra stagionale (meteorologica) con impatto lungo termine.

## Come Funziona Oggi (alto livello)
1. Il sistema apre automaticamente i cicli (daily/weekly/monthly/event/stagione) in base al calendario.
2. Ogni citta riceve missioni attive per i cicli previsti; alcune missioni sono global race.
3. I giocatori avanzano le missioni facendo azioni normali in gioco (mining, combat, farming, craft, ecc.).
4. Il progresso viene mostrato in GUI (e in alcuni casi con feedback live), con stato completamento e tempo residuo.
5. Al completamento:
   - missioni standard -> reward citta
   - missioni race -> vince il primo completamento valido
   - eventi -> ranking/podio/soglia a fine finestra
6. A fine ciclo, le missioni scadono e si rigenerano quelle del ciclo successivo.

## Stato Sintetico per il Redesign
- Il sistema e gia diviso in blocchi utili al refactor:
  - fisse
  - cicliche
  - race
  - evento
  - stagionali
- Base pronta per la tua prossima definizione:
  - set di quest fisse
  - set daily/weekly/monthly
  - set race daily/mensili
  - set eventi
  - set stagionali

## Levelling Citta (alto livello, stato attuale)

### Formula XP -> Livello
- Curva attuale: `XP richiesta livello L = 100 * (L - 1)^1.30`
- `L1` parte da `0 XP`.
- Il sistema usa una precisione interna scalata (`xpScale=10000`) per supportare valori decimali.
- Cap livello attuale: `250`.

### Come funziona il passaggio XP-Livello
1. La citta accumula XP (materiali nei claim, reward missioni, bonus vari).
2. Quando l'XP totale supera la soglia del livello successivo, il livello aumenta automaticamente.
3. Il livello numerico (`1..250`) e separato dal **tier/stage** (BORGO, VILLAGGIO, REGNO), che invece richiede upgrade manuale ai checkpoint.
4. Ogni aumento livello aggiorna i dati di progressione e i limiti effettivi applicati alla citta.

### Tier/Stage Citta esistenti
- `BORGO I` (req livello 1)
- `BORGO II` (req livello 10)
- `BORGO III` (req livello 20)
- `VILLAGGIO I` (req livello 30)
- `VILLAGGIO II` (req livello 50)
- `VILLAGGIO III` (req livello 70)
- `REGNO I` (req livello 100)

### Regole upgrade stage (checkpoint)
- L'upgrade stage non e automatico: va richiesto quando arrivi al livello richiesto.
- Requisito banca: saldo minimo richiesto = `costoStage * 2.0`.
- Costo realmente scalato in upgrade: `costoStage`.
- Alcuni stage richiedono approvazione staff (`VILLAGGIO I`, `REGNO I`).
- Per passare a `VILLAGGIO I` e richiesto anche lo spawn/warp citta impostato.

### Limiti principali per stage (claim, membri, spawner)
- `BORGO I`: claim `9`, membri `3`, spawner `2`
- `BORGO II`: claim `18`, membri `5`, spawner `3`
- `BORGO III`: claim `27`, membri `7`, spawner `4`
- `VILLAGGIO I`: claim `45`, membri `10`, spawner `5`
- `VILLAGGIO II`: claim `63`, membri `13`, spawner `7`
- `VILLAGGIO III`: claim `81`, membri `16`, spawner `9`
- `REGNO I`: claim `162`, membri `50`, spawner `12`

### Extra progressione oltre REGNO
- Da `REGNO I` in poi, ogni livello sopra il 100 aggiunge `+1` claim extra.
- Questa estensione claim e attiva fino al cap livello configurato (`250`).

### Note policy collegate al tier
- Privacy warp:
  - da `BORGO II` in su e forzata `public`;
  - solo `BORGO I` puo avere toggle libero (se policy attiva).
- Tasse mensili:
  - iniziano da `BORGO III` e crescono per stage fino a `REGNO I`.
