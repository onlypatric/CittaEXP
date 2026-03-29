## Obiettivo

Darti **un redesign unico e prod-ready** per le GUI città: più intuitive al primo impatto, più “Hypixel-like”, più orientate a **Progressione / Quest / Livelli**, con meno confusione e meno carico mentale per player giovani.

## Assunzioni

* Resti su **inventory GUI** senza resource pack globale.
* Puoi usare **ItemsAdder** per item/icone custom.
* Vuoi una **rifondazione UX**, non un restyling leggero.
* La feature da spingere è **Progressione**, mentre **Claim** è importante ma più da mayor/officer.

## Soluzione migliore

La soluzione migliore non è “abbellire tutte le GUI”, ma **cambiare il modello mentale** del plugin:

**da plugin amministrativo**
→ **a gioco dentro il gioco**

Quindi il player non deve entrare in `/city` e vedere “strumenti”.
Deve entrare e vedere **3 grandi cose**:

1. **Avanza la tua città**
2. **Guarda cosa puoi fare adesso**
3. **Vai nelle sezioni secondarie solo se ti servono**

Questo segue un principio forte di UX: meglio far **riconoscere** subito il prossimo passo, invece di costringere il giocatore a ricordare dove sta ogni funzione. La riduzione del carico mentale e la progressive disclosure aiutano soprattutto quando il sistema è ricco di opzioni. ([Nielsen Norman Group][1])

---

# Piano definitivo di innovazione

## 1) Nuova architettura generale

### Nuovo `/city`: non più “lista membri” come root

La root attuale parte dai membri.
È sbagliato per il tuo obiettivo, perché comunica:

* “questa è una GUI gestionale”
* non “questa è la tua città che cresce”

### Nuova root: **City Hub**

Una schermata 9x6 con 5 blocchi mentali chiari:

* **Progresso città**
* **Missioni / Obiettivi**
* **Territori**
* **Membri**
* **Tesoro & Vault**

Più una riga alta di stato.

### Perché

Per player nuovi e giovani, l’interfaccia deve mostrare prima:

* stato attuale
* prossima azione utile
* premio o vantaggio

La visibilità dello stato aumenta controllo e fiducia; inoltre una UI visivamente forte migliora la percezione di usabilità, ma non deve coprire il caos strutturale. ([Nielsen Norman Group][2])

---

## 2) Nuovo modello mentale: 3 livelli

### Livello 1 — Hub emozionale

Per tutti i player.

Mostra solo:

* livello città
* XP attuale / prossima soglia
* 1–3 quest attive
* reward disponibile / claimabile
* accesso rapido a membri / territori / banca

### Livello 2 — Sezioni dedicate

Ogni macro-area ha una GUI propria e coerente:

* Progressione
* Quest
* Territori
* Membri
* Economia

### Livello 3 — Gestione avanzata

Solo per mayor/officer:

* relazioni
* claim settings
* rename/edit
* disband
* transfer
* mayor tools

Così i membri normali non vedono subito opzioni che non useranno.
Questo è il punto più importante del redesign: **nascondere complessità non necessaria** finché non serve. È progressive disclosure pura. ([Nielsen Norman Group][1])

---

# 3) Nuova gerarchia delle GUI

## A. `/city` → **City Hub**

Questa diventa la schermata principale.

### Layout proposto 9x6

* `0..8`: barra stato città
* `10..16`: 3 card principali grandi
* `28..34`: 3 accessi secondari
* `45`: indietro/chiudi contestuale
* `49`: profilo città / info rapida
* `53`: aiuto contestuale

### Le 3 card grandi centrali

Le card grandi devono essere:

* **Progressione**
* **Quest**
* **Territori**

Perché sono il core loop che vuoi spingere.

### Accessi secondari sotto

* **Membri**
* **Tesoro**
* **Altro**

`Altro` contiene: relazioni, modifica città, impostazioni avanzate.

### Perché è corretto

Un hub con chiara gerarchia visiva e pochi cluster principali è più facile da scansionare. La gerarchia visiva e il contrasto migliorano la comprensione prima ancora della lettura. ([Nielsen Norman Group][3])

---

## B. Progressione: da “hub tecnico” a **Battle Pass della città**

Questa deve diventare **la GUI più forte del plugin**.

Il tuo riferimento giusto non è una lista di challenge, ma un **battlepass/progression hub** stile Hypixel: accesso rapido a progressione, reward e percorsi chiari. Hypixel usa menu dedicati e facilmente riconoscibili per sezioni come Fast Travel e Levels, invece di ammucchiare tutto in una lista piatta. ([Hypixel SkyBlock Wiki][4])

### Nuova struttura Progressione

Non più una pagina con 8 header + card + footer denso.

Dividila in 4 viste:

1. **Panoramica**
2. **Quest attive**
3. **Livelli**
4. **Ricompense**

### 1. Panoramica

Mostra:

* livello città
* XP
* bonus attivi
* obiettivo consigliato
* streak/evento
* bottone “Continua”

Il bottone “Continua” apre la cosa più rilevante in quel momento:

* reward da claimare → Ricompense
* quest quasi completata → Quest
* livello pronto → Livelli

### 2. Quest attive

Mostra massimo **6 quest grandi**
Non 36 card uguali.

Ordine:

* 3 quest consigliate
* 2 quest secondarie
* 1 slot “altre”

Perché
Il player medio non ottimizza se vede 30 task; si blocca.
Più scelta = più frizione. Ridurre opzioni visibili migliora decisione e velocità. ([Nielsen Norman Group][5])

### 3. Livelli

La timeline livelli va bene, ma deve essere:

* più leggibile
* meno lista “amministrativa”

Mostra:

* livello attuale
* prossimo checkpoint
* 3 reward future
* avanzamento percentuale

L’utente deve capire subito:

* dove sono
* quanto manca
* cosa ottengo

### 4. Ricompense

Separata dalle quest.
Le reward devono essere una schermata molto “visiva”, quasi da loot room.

Ordine:

* claimabili ora
* sbloccate ma non ritirate
* future

Questo aumenta molto il “wow effect” e rinforza il loop motivazionale.

---

## C. Claim Map: separare uso casual da uso officer

### Problema attuale

La claim map è potente, ma troppo “operativa” per l’entry normale.

### Soluzione

Nel City Hub il bottone **Territori** apre una schermata intermedia:

* **Mappa territori**
* **Warp / Centro città**
* **Permessi territori**
* **Info terreni**

Per i membri normali:

* vedono info e stato territorio
* non entrano subito in una mappa operativa complessa

Per mayor/officer:

* bottone grande “Apri mappa gestione”

### Claim Map vera

La 9x5 chunk map resta, ma va resa più leggibile:

#### Footer nuovo

* `45`: su
* `46`: sinistra
* `47`: centro player
* `48`: destra
* `49`: giù
* `50`: modalità click attuale
* `51`: legenda compatta
* `52`: aiuto
* `53`: back

#### Regola fondamentale

**Mai click ambiguo.**

Niente “left fa una cosa, right ne cicla un’altra” senza feedback fortissimo.

Meglio:

* modalità **Claim**
* modalità **Tipo terreno**
* modalità **Info**

Lo slot `50` cambia modalità.
Così il click sulla mappa fa **una sola classe di azione per volta**.

### Perché

Questo abbassa gli errori e rende il comportamento prevedibile. La prevenzione errori e la visibilità dello stato sono essenziali nelle interfacce complesse. ([Nielsen Norman Group][2])

---

## D. Membri: non più skull-wall monotona

Hai ragione: la wall di skull è monotona e poco informativa.

### Nuovo design

La sezione **Membri** deve avere 2 viste:

1. **Roster**
2. **Ruoli & gestione**

### Roster

Ogni entry membro deve comunicare subito:

* ruolo
* online/offline
* contributo recente
* stato speciale

Quindi ogni skull va accompagnata da:

* bordo colore ruolo
* icona piccola overlay
* nome + 1 riga max

### Raggruppamento

Non lista unica piatta.
Ordina per gruppi:

* Mayor
* Officer
* Membri online
* Membri offline recenti
* Membri inattivi

Anche se tecnicamente paginata, la percezione deve essere “team leggibile”, non “database utenti”.

### Dettaglio membro

Riduci il numero di azioni simultanee.
Nuova struttura:

* centro: profilo membro
* sinistra: info
* destra: azioni
* basso: azioni pericolose separate

Le azioni distruttive:

* evict
* transfer
* demote pesante

devono stare in un cluster separato, non mischiate ai toggle ACL.

---

## E. Banca + Vault: unifica sotto “Tesoro”

Oggi banca e item vault sono concettualmente separate, ma per il player sono entrambe **risorse della città**.

### Soluzione

Crea una macro-sezione:

## **Tesoro città**

con 3 accessi:

* **Saldo**
* **Deposito oggetti**
* **Ricompense quest**

Questo è importante perché l’Item Vault è legato alle quest: non deve sembrare un magazzino isolato.

### Banca

La banca 9x3 può restare, ma con UX più forte:

* saldo enorme al centro
* due bottoni grandi: deposita / preleva
* importi rapidi:

  * +1k
  * +10k
  * tutto
  * custom

Non solo dialog input.
Il dialog resta come fallback.

### Vault

Fai capire subito:

* quanti slot usati
* chi può depositare/prelevare
* se ci sono item quest importanti

Il footer deve sempre mostrare permesso chiaro.

---

## F. Relazioni: spostale nell’area avanzata

Finché le war non sono il cuore attivo del gameplay giornaliero, **Relazioni** non meritano un posto prominente nel flusso base del membro normale.

### Nuova collocazione

`Altro` → `Diplomazia`

Oppure visibile direttamente solo a mayor/officer.

### Motivazione

Una IA efficace segue il modello mentale del giocatore medio.
Se una cosa non è frequente, non deve competere per attenzione con il core loop. ([Nielsen Norman Group][5])

---

# 4) Regole UX globali da applicare a TUTTE le GUI

## Regola 1 — Ogni schermata deve rispondere a 3 domande

Appena si apre, il player deve capire:

* **Dove sono**
* **Cosa posso fare qui**
* **Qual è la cosa più utile da fare adesso**

Se una GUI non risponde a queste 3 domande entro 2–3 secondi, va rifatta.

---

## Regola 2 — Un solo CTA dominante

Ogni GUI deve avere **una sola azione primaria visivamente dominante**.

Esempi:

* Hub città → `Continua progressione`
* Quest → `Traccia / Apri dettagli`
* Livelli → `Prossimo sblocco`
* Tesoro → `Deposita`
* Claim → `Apri mappa gestione`

Non 4 call-to-action equivalenti.

---

## Regola 3 — Slot fissi coerenti in tutto il plugin

Stabilisci un design system rigido.

### Footer universale

* `45` prev / secondario
* `47` back
* `49` stato pagina / info
* `51` close solo dove serve davvero
* `53` next / primary nav

### Header universale

* `0` icona sezione
* `1..3` stato
* `4..8` info contestuale

La coerenza riduce memoria richiesta e accelera apprendimento. ([Nielsen Norman Group][6])

---

## Regola 4 — Colori semantici fissi

Senza resource pack, il colore diventa linguaggio.

Usa sempre:

* **Verde** = progresso / conferma / claimabile
* **Blu** = info / navigazione / membri
* **Oro** = reward / premium / milestone
* **Rosso** = pericolo / irreversibile
* **Viola** = sistemi speciali / quest / eventi
* **Grigio** = locked / unavailable

Mai cambiare semantica da una GUI all’altra.

---

## Regola 5 — Lore corte, massimo 3 blocchi

Player 15–16 anni leggono, ma solo se:

* breve
* formattato
* con parole concrete

Formato migliore:

* riga 1: stato
* riga 2: beneficio
* riga 3: azione click

Esempio:

* `Livello 4 → 820/1000 XP`
* `Sblocca +2 claim e 1 reward`
* `Click per vedere il prossimo livello`

---

## Regola 6 — Locked content deve dire “come si sblocca”

Mai solo “Bloccato”.

Deve dire:

* perché è bloccato
* chi può usarlo
* cosa serve per sbloccarlo

Gli errori e gli stati bloccati devono essere costruttivi e non punitivi. ([Nielsen Norman Group][7])

---

## Regola 7 — Niente doppio comportamento invisibile

Evita:

* left click fa X
* right click fa Y
* shift fa Z

a meno che non sia mostrato chiaramente nello stesso item.

Per utenti nuovi è troppo recall-based. ([Nielsen Norman Group][1])

---

## Regola 8 — Le azioni distruttive stanno sempre in un “Danger Layer”

Non nello stesso piano visivo di:

* toggle
* navigazione
* info

Quindi:

* Disband
* Transfer
* Evict
* Withdraw sensibili
* Cambi diplomazia pesanti

vanno in una schermata dedicata o in un blocco rosso separato.

---

# 5) Stile Hypixel-like senza resource pack globale

Puoi ottenere molto “wow” anche senza texture GUI custom complete.

## Usa ItemsAdder per 3 categorie di item

1. **Icone sezione**

   * progressione
   * territori
   * quest
   * tesoro
   * membri

2. **Stati**

   * locked
   * reward claimabile
   * quest completata
   * boost attivo
   * allerta

3. **Decorativi funzionali**

   * divider
   * badge ruolo
   * cornice reward
   * marker “consigliato”

### Regola artistica

Non customizzare tutto.
Customizza solo:

* entrypoint principali
* reward
* milestone
* bottoni importanti

Il resto deve restare abbastanza vanilla-like, sennò perdi leggibilità.
Un design bello aiuta, ma troppo rumore visivo peggiora il focus. ([Nielsen Norman Group][8])

---

# 6) Rinominare le sezioni in modo player-first

Ti propongo questa nomenclatura definitiva.

## Root

* `/city` → **La tua Città**

## Sezioni

* `Progressione` → **Crescita**
* `Sfide` → **Missioni**
* `Livelli` → **Potenziamenti**
* `Azioni città` → **Centro comando**
* `Banca città` + `Item Vault` → **Tesoro**
* `Relazioni` → **Diplomazia**
* `Impostazioni claim` → **Regole territori**
* `Dettaglio membro` → **Profilo membro**

### Perché

Parole concrete > termini interni.
Un player deve capire il risultato, non il nome tecnico.

---

# 7) Piano schermata per schermata

## `/city` root

**Eliminare completamente la root attuale basata sui membri.**

### Nuova root

* Hero card: stato città
* 3 card grandi: Crescita / Missioni / Territori
* 3 card piccole: Membri / Tesoro / Centro comando
* CTA dominante: **Continua**

---

## Membri

* Roster per gruppi
* Meno skull-wall
* Entry più informative
* Governance separata da info

---

## Progressione Hub

**Smontare l’header 0..8 attuale.**
È troppo denso.

Sostituirlo con:

* stato città
* bonus attivo
* next reward
* CTA dominante

---

## Livelli

* Timeline semplificata
* solo 1 focus “prossimo”
* future reward evidenziate

---

## Sfide

* massimo 6 ben leggibili per pagina principale
* schede grandi
* priorità forte
* quest “consigliata” sempre in alto

---

## Mayor challenge management

Spostare in:

* `Missioni` → `Gestione avanzata`
  Non in vista del membro normale.

---

## Claim map

* gateway intermedio
* modalità click esplicita
* info forti nel footer
* help contestuale

---

## Claim settings

Rinomina in **Regole territori**
e rendila una schermata di policy, non un menu tecnico.

---

## Banca

* quick amounts
* saldo centrale enorme
* 1 CTA primaria

---

## Vault

* dentro Tesoro
* collega esplicitamente il vault alle ricompense e alle missioni

---

## Relazioni

* ridurre priorità nel flusso standard
* promuovere solo quando le war saranno davvero centrali

---

## Dialog edit città

Non investire troppo qui adesso.
È una zona a bassa frequenza.
Va resa pulita, non scenica.

---

# 8) Fasi di implementazione prod-ready

## Fase 1 — Fondazione UX

Prima rifai:

1. `/city` root
2. `Progressione`
3. `Missioni`
4. `Territori` gateway
5. design system slot/colori/copy

Questa fase da sola cambia la percezione del plugin.

## Fase 2 — Supporto core

Poi:
6. `Livelli`
7. `Tesoro`
8. `Vault`
9. `Membri`

## Fase 3 — Advanced

Infine:
10. `Diplomazia`
11. `Regole territori`
12. `Mayor tools`
13. `Edit città`

---

# 9) Decisioni nette da prendere subito

Ti consiglio di adottare queste decisioni senza mezze misure:

* **Togliere i membri dalla root**
* **Fare di Progressione il cuore visivo e funzionale**
* **Separare il normale dall’avanzato**
* **Ridurre le quest visibili contemporaneamente**
* **Rendere la claim map modale e non ambigua**
* **Unificare banca + vault sotto Tesoro**
* **Usare uno slot system coerente in tutto il plugin**
* **Usare custom item solo per cose importanti**
* **rinominare tutte le sezioni con termini concreti**

Questa è la base più forte per ottenere sia **intuizione immediata** sia **wow effect**, senza cadere nel classico errore di fare GUI “fighe ma pesanti”. L’estetica aiuta, ma deve servire la comprensione, non sostituirla. ([Nielsen Norman Group][8])

## Risultato atteso

Con questo piano ottieni:

* onboarding naturale
* più click sulla progressione
* meno frizione per i membri normali
* più identità “gioco” e meno “pannello admin”
* base scalabile per future war e feature avanzate

Nel prossimo messaggio posso passare dal piano al livello operativo e scriverti direttamente:
**“nuova mappa completa delle GUI + slot layout preciso di ogni schermata principale”**.

[1]: https://www.nngroup.com/articles/recognition-and-recall/?utm_source=chatgpt.com "Memory Recognition and Recall in User Interfaces"
[2]: https://www.nngroup.com/articles/visibility-system-status/?utm_source=chatgpt.com "Visibility of System Status (Usability Heuristic #1)"
[3]: https://www.nngroup.com/articles/principles-visual-design/?utm_source=chatgpt.com "5 Principles of Visual Design in UX"
[4]: https://wiki.hypixel.net/Introduction?utm_source=chatgpt.com "Introduction"
[5]: https://www.nngroup.com/articles/aesthetic-minimalist-design/?utm_source=chatgpt.com "Aesthetic and Minimalist Design (Usability Heuristic #8)"
[6]: https://www.nngroup.com/articles/usability-heuristics-complex-applications/?utm_source=chatgpt.com "10 Usability Heuristics Applied to Complex Applications"
[7]: https://www.nngroup.com/articles/error-message-guidelines/?utm_source=chatgpt.com "Error-Message Guidelines"
[8]: https://www.nngroup.com/articles/aesthetic-usability-effect/?utm_source=chatgpt.com "The Aesthetic-Usability Effect"
          