**Goal**

Riempire `Story Mode` con una progressione narrativa **permanente, cooperativa e generica**, pronta da collegare al tuo sistema città senza legarla a un solo immaginario.

**Assunzioni**

* Mantengo i tuoi **40 capitoli** e il gate `1 capitolo ogni 5 livelli`.
* Riutilizzo le famiglie già implicite nel plugin: `Extraction`, `Combat`, `Agronomy`, `Industry`, `Frontier`, `Civic`.
* La lore resta **neutra**: niente fantasy hard, niente sci-fi hard, niente medioevo obbligatorio.
* Ho costruito la proposta attorno ai pilastri che ritornano spesso nei city-builder: gestione di cittadini e infrastrutture, crescita della città, economia viva e risposta a pressioni/crisi collettive. Sono elementi presenti anche nelle descrizioni ufficiali di *Frostpunk*, *Frostpunk 2*, *Cities: Skylines* e *Cities: Skylines II*. ([Steam Store][1])

**Approach**

Ti lascio una base pronta, composta da:

1. **frame narrativo globale**;
2. **struttura capitolo** riusabile;
3. **40 capitoli completi** con identità, focus e prova finale;
4. **hook elite** dopo il 40.

---

# Risultato

## 1) Frame narrativo globale di Story Mode

La città non cresce solo per sé stessa.

Ogni città fa parte del **Patto delle Città**, una rete antica ma incompleta di comunità che tengono insieme territorio, scambi, sicurezza, memoria e futuro.
All’inizio la tua città è solo un punto fragile. Con il tempo diventa un nodo stabile. Poi una forza regionale. Infine una realtà capace di sostenere altre città.

Il messaggio da far sentire ai player è questo:

> **“Non state solo costruendo una città. State rimettendo in piedi una parte del mondo.”**

Questa formula è volutamente generica:

* in un server fantasy sembra un patto tra regni;
* in un server medievale sembra una lega di città;
* in un server industriale sembra una rete commerciale;
* in uno stile più moderno sembra una federazione civica.

---

## 2) Struttura consigliata di ogni capitolo

Ogni capitolo dovrebbe avere sempre questi pezzi:

```yaml
id: 1
title: "Fondazione"
min_city_level: 1
act: "I - Le Radici"
narrative_beat: "La città smette di essere un gruppo sparso e sceglie di durare."
task_families: ["Civic", "Extraction", "Agronomy"]
signature_goal: "Costruire la prima riserva comune della città."
chapter_proof: "Raggiungere una soglia cooperativa di materiali/cibo/contributi."
completion_fantasy: "La città ora esiste davvero agli occhi del territorio."
```

### Formula gameplay consigliata per ogni capitolo

* **3 task ricorrenti** del tema del capitolo
* **1 milestone cooperativa** più lenta
* **1 prova capitolo** che chiude il passo narrativo

### Tipi di prova capitolo

Riusa queste, così non devi inventare 40 meccaniche diverse:

* `Stockpile` → raggiungi una soglia città
* `Construction` → completa un progetto civico
* `Defense` → respingi/uccidi una quota minacce
* `Supply` → consegna risorse al vault
* `Expansion` → completa task mixed di frontiera/logistica
* `Council` → contributi diffusi da più player
* `Grand Project` → obiettivo multi-step

---

## 3) Story Mode completo: 40 capitoli

## Atto I — **Le Radici**

Dalla sopravvivenza alla prima identità.

1. **Fondazione** (`lv 1-5`)
   La città smette di essere un gruppo sparso e sceglie di durare.
   **Focus:** raccolta base, cibo base, deposito comune.
   **Prova:** creare la prima riserva cittadina.

2. **Prime Scorte** (`lv 6-10`)
   La comunità capisce che resistere non basta: serve conservare.
   **Focus:** mining base, farming base, contribution.
   **Prova:** garantire una soglia minima di scorte condivise.

3. **Crescita Iniziale** (`lv 11-15`)
   I primi spazi utili diventano struttura, ordine e presenza.
   **Focus:** costruzione, raccolta, supporto città.
   **Prova:** completare il primo progetto di sviluppo civico.

4. **Prime Difese** (`lv 16-20`)
   Ciò che è stato costruito va protetto, non solo ampliato.
   **Focus:** combat base, pattugliamento, supporto.
   **Prova:** respingere una quota di minacce territoriali.

5. **Borgo Attivo** (`lv 21-25`)
   La città inizia a specializzarsi: non tutti fanno tutto.
   **Focus:** produzione, agricoltura, craft base.
   **Prova:** sostenere una filiera semplice ma continua.

6. **Nuove Rotte** (`lv 26-30`)
   Il territorio esterno smette di essere solo pericolo e diventa opportunità.
   **Focus:** trasporto, esplorazione leggera, consegne.
   **Prova:** aprire la prima rete di approvvigionamento stabile.

7. **Risorse Preziose** (`lv 31-35`)
   Per crescere davvero servono materiali che cambiano il passo della città.
   **Focus:** mining intermedio, vault, extraction.
   **Prova:** raggiungere una soglia di risorse pregiate.

8. **Caccia Organizzata** (`lv 36-40`)
   La città affronta le minacce con metodo, non più con reazione improvvisata.
   **Focus:** mob combat, rare kill leggere, supporto.
   **Prova:** completare una campagna di caccia cooperativa.

---

## Atto II — **L’Ordine Civico**

La città smette di essere fragile e diventa affidabile.

9. **Villaggio Stabile** (`lv 41-45`)
   Le persone iniziano a fidarsi della città come luogo che può durare.
   **Focus:** civic, supporto, maintenance.
   **Prova:** mantenere equilibrio tra risorse, supporto e contributi.

10. **Infrastrutture** (`lv 46-50`)
    Crescere richiede strutture che sostengano l’intero ritmo cittadino.
    **Focus:** construction, industry, civic works.
    **Prova:** completare un’infrastruttura chiave della città.

11. **Terre Fertili** (`lv 51-55`)
    Il nutrimento non è più un’urgenza: diventa un sistema.
    **Focus:** farming avanzato, agronomy, supply.
    **Prova:** garantire una produzione agricola costante.

12. **Officine** (`lv 56-60`)
    La città inizia a trasformare materia in capacità.
    **Focus:** craft, redstone, produzione, industry.
    **Prova:** sostenere una catena produttiva avanzata.

13. **Frontiera Vicina** (`lv 61-65`)
    I confini della città si allargano e chiedono presenza.
    **Focus:** esplorazione, frontier, raccolta esterna.
    **Prova:** stabilizzare una zona di espansione vicina.

14. **Difesa Civica** (`lv 66-70`)
    Difendere la città diventa responsabilità collettiva, non compito di pochi.
    **Focus:** civic, supporto, combattimento coordinato.
    **Prova:** completare una mobilitazione difensiva cittadina.

15. **Città Emergente** (`lv 71-75`)
    La città non è più solo locale: inizia a contare nel territorio.
    **Focus:** progressione mista, contribution, vault.
    **Prova:** completare un obiettivo ibrido su più famiglie.

16. **Rete Logistica** (`lv 76-80`)
    La forza della città passa dalla sua capacità di far fluire risorse e lavoro.
    **Focus:** trasporto, consegne, contribution.
    **Prova:** mantenere una linea di rifornimento ad alta soglia.

---

## Atto III — **La Connessione**

La città entra davvero nel Patto delle Città.

17. **Economia Viva** (`lv 81-85`)
    La città produce, scambia, accumula e ridistribuisce con continuità.
    **Focus:** trade, civic, market, vault.
    **Prova:** generare un ciclo economico cooperativo stabile.

18. **Reparti Speciali** (`lv 86-90`)
    Nascono gruppi dedicati ai lavori più rischiosi o più preziosi.
    **Focus:** combat avanzato, rare kill, objective strike.
    **Prova:** completare una prova militare/specialistica cittadina.

19. **Espansione Urbana** (`lv 91-95`)
    La città comincia a costruire pensando in grande, non solo in fretta.
    **Focus:** industry, construction, civic works.
    **Prova:** realizzare un’opera di espansione strutturale.

20. **Prestigio Cittadino** (`lv 96-100`)
    La città non è soltanto efficiente: è anche riconosciuta.
    **Focus:** quest ibride, reward forti, civic prestige.
    **Prova:** completare un capitolo celebrativo ad alta partecipazione.

21. **Regno Nascente** (`lv 101-105`)
    La città diventa centro di riferimento per realtà minori.
    **Focus:** macro-obiettivi cooperativi, contribution, governance.
    **Prova:** sostenere un grande progetto comune del territorio.

22. **Miniere Profonde** (`lv 106-110`)
    La crescita chiede di scendere oltre la superficie e assumersi nuovi rischi.
    **Focus:** extraction avanzata, mining raro, vault.
    **Prova:** recuperare materiali d’impatto strategico.

23. **Addestramento Reale** (`lv 111-115`)
    La forza cittadina va disciplinata, non solo aumentata.
    **Focus:** combat avanzato, training loops, defense prep.
    **Prova:** completare una campagna di addestramento militare.

24. **Campi del Regno** (`lv 116-120`)
    La città ora sostiene un territorio più ampio del proprio perimetro.
    **Focus:** agronomy avanzata, food supply, logistics.
    **Prova:** nutrire una scala di consumo superiore.

---

## Atto IV — **Il Coordinamento del Potere**

La città non reagisce più al mondo: inizia a ordinarlo.

25. **Laboratori** (`lv 121-125`)
    La città perfeziona supporto, alchimia, craft specialistico e preparazione.
    **Focus:** potion, craft, supporto, produzione fine.
    **Prova:** completare una linea di preparazione ad alto valore.

26. **Vie del Commercio** (`lv 126-130`)
    Le rotte non servono più solo la città: collegano sistemi più ampi.
    **Focus:** civic, economy, trade routes, vault flow.
    **Prova:** aprire e sostenere più flussi di scambio coordinati.

27. **Terre Esterne** (`lv 131-135`)
    La città opera fuori dal proprio nucleo con continuità e controllo.
    **Focus:** frontier, structure, expansion works.
    **Prova:** consolidare un’area esterna come estensione funzionale.

28. **Sala del Comando** (`lv 136-140`)
    A questo livello conta la capacità di coordinare, assegnare e convergere.
    **Focus:** civic, contribution, governance, mixed projects.
    **Prova:** chiudere un capitolo basato su contributi diffusi.

29. **Cuore Industriale** (`lv 141-145`)
    La città raggiunge il suo pieno battito produttivo.
    **Focus:** redstone, industry, advanced crafting.
    **Prova:** sostenere produzione ad alta intensità senza collasso.

30. **Guardie del Regno** (`lv 146-150`)
    La difesa della città diventa presidio del territorio intero.
    **Focus:** combat + supporto, organized defense.
    **Prova:** completare una grande mobilitazione protettiva.

31. **Espansione Remota** (`lv 151-155`)
    La città si estende verso spazi lontani e difficili da sostenere.
    **Focus:** exploration, dimension, frontier logistics.
    **Prova:** completare una spedizione di insediamento remoto.

32. **Spedizioni** (`lv 156-160`)
    Le missioni esterne diventano parte integrante del motore cittadino.
    **Focus:** frontier, vault, raid-like goals, recovery.
    **Prova:** riportare al centro risorse/risultati da operazioni avanzate.

---

## Atto V — **Eredità e Ascesa**

La città diventa una forza che lascia ordine dietro di sé.

33. **Linea di Rifornimento** (`lv 161-165`)
    Nessun grande centro regge senza continuità di supporto.
    **Focus:** logistics, food, civic, supply chain.
    **Prova:** mantenere una catena di rifornimento lunga e stabile.

34. **Reparti Elite** (`lv 166-170`)
    Solo i gruppi migliori affrontano le prove più dure della città.
    **Focus:** hard combat, rare mob, specialist objectives.
    **Prova:** completare un capitolo ad alta difficoltà cooperativa.

35. **Opere Maggiori** (`lv 171-175`)
    La città costruisce qualcosa che la definisce oltre l’utilità immediata.
    **Focus:** monument, construction, prestige works.
    **Prova:** completare una grande opera simbolica.

36. **Prove del Regno** (`lv 176-180`)
    La città viene misurata nella sua interezza, non in una sola specializzazione.
    **Focus:** multi-step, cooperative, mixed families.
    **Prova:** chiudere una catena capitolo a fasi multiple.

37. **Dominio Territoriale** (`lv 181-185`)
    Il territorio riconosce la città come punto di equilibrio e riferimento.
    **Focus:** civic, expansion, control, support.
    **Prova:** stabilizzare più aree o sistemi in parallelo.

38. **Crocevia del Potere** (`lv 186-190`)
    Tutte le reti passano dalla città: rifornimento, difesa, produzione, decisione.
    **Focus:** mixed late game, coordination, vault pressure.
    **Prova:** sostenere un grande obiettivo simultaneo multi-famiglia.

39. **Ultime Preparazioni** (`lv 191-195`)
    La città si organizza per il salto finale: niente sprechi, niente vuoti.
    **Focus:** mixed pre-elite, stockpile, readiness.
    **Prova:** raggiungere soglie finali di prontezza totale.

40. **Finale del Regno** (`lv 196-200`)
    La città completa il suo primo grande ciclo storico e si consacra come nodo maggiore del Patto.
    **Focus:** chapter finale, all families, grand project.
    **Prova:** superare una prova totale che unisce risorse, difesa, logistica e contributi.
    **Esito:** sblocco del layer `Elite`.

---

## 4) Copy player-facing pronta

### Subtitle della sezione

**Story Mode**
*La storia permanente della tua città. Ogni capitolo è un passo verso qualcosa di più grande.*

### Text per capitolo bloccato

**Bloccato**
*Aumenta il livello città e completa il capitolo precedente per proseguire nella storia.*

### Text per capitolo sbloccato

**Sbloccato**
*La città è pronta per il suo prossimo grande passo.*

### Text per capitolo in corso

**In corso**
*La città sta lavorando insieme per completare questo capitolo.*

### Text per capitolo completato

**Completato**
*Questo passo è ormai parte della storia della città.*

### Text per unlock elite

**Elite sbloccato**
*La città ha concluso il ciclo base di Story Mode. Da ora in poi affronterà prove rare, lente e prestigiose.*

---

## 5) Hook Elite dopo il capitolo 40

Qui non serve ancora numerarli tutti. Ti basta fissare i tipi.

### Pillar elite consigliati

* **Grandi Progetti** → opere monumentali, reti permanenti, progetti civici enormi
* **Capitoli di Assedio** → hook con `City Defense / Mob Invasion`
* **Capitoli di Dominio** → controllo e stabilizzazione di territori esterni
* **Capitoli di Prestigio** → reward estetiche, titoli, banner, statue, hall
* **Capitoli di Crisi** → prove dove la città deve reggere pressione multi-famiglia
* **Capitoli di Eredità** → la città sostiene altre città / il territorio / la season

---

## 6) Incastro perfetto con Atlas attuale

Questa mappatura ti evita di rifare tutto da zero:

* `Extraction` → Prime Scorte, Risorse Preziose, Miniere Profonde
* `Combat` → Prime Difese, Caccia Organizzata, Reparti Speciali, Guardie del Regno, Reparti Elite
* `Agronomy` → Borgo Attivo, Terre Fertili, Campi del Regno, Linea di Rifornimento
* `Industry` → Infrastrutture, Officine, Cuore Industriale, Opere Maggiori
* `Frontier` → Nuove Rotte, Frontiera Vicina, Terre Esterne, Espansione Remota, Spedizioni
* `Civic` → Villaggio Stabile, Difesa Civica, Economia Viva, Sala del Comando, Dominio Territoriale

---

## 7) La frase chiave che tiene insieme tutto

La città deve sempre sentire questo cambio di scala:

* **inizio:** “dobbiamo sopravvivere”
* **metà:** “dobbiamo diventare affidabili”
* **late:** “dobbiamo sostenere il territorio”
* **endgame:** “dobbiamo lasciare ordine, forza ed eredità”

Questo è il punto che fa sentire i player **parte di qualcosa di più grande**.