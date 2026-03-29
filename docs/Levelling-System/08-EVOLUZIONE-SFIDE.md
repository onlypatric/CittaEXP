# 08 - Evoluzione Sfide (Spec Implementabile, Decision-Complete)

## 1) Obiettivo Documento

Questo documento definisce la specifica completa per evolvere il sistema sfide città in CittaEXP.

Obiettivo pratico:

* trasformare il sistema da engine tecnico a programma live-ops stagionale;
* mantenere coerenza town-first e GUI-first;
* fornire una base implementabile senza ulteriori decisioni aperte.

Vincolo di questa wave:

* **doc-only** (nessuna modifica runtime/plugin in questa fase).

Pubblico target:

* designer/produttori e sviluppatori che **non hanno accesso alla codebase**.

Nota di allineamento:

* il documento segue la direzione “daily + milestone + season program”, coerente con i sistemi quest più maturi che combinano quest ricorrenti, milestones e progressione stagionale. ([SpigotMC][1])

---

## 1.1 Milestone Tracker (Implementazione)

Usa queste checkbox come tracker di avanzamento strategico.

### M8 - Programma Stagionale + Varietà Curata
- [x] Layer A/B/C implementati end-to-end.
- [x] Milestone `3 weekly + 1 seasonal finale` operative.
- [x] Bundle constraints attivi per daily/weekly/monthly-event.
- [x] Cooldown su definition/category/focus attivo.
- [x] Reroll weekly (1 per città) e veto stagionale funzionanti.

### M9 - Fairness + Reward Model
- [x] Scaling target per `active_contributors_7d` attivo con i 4 bracket definiti.
- [x] Doppia soglia `100%/140%` operativa.
- [x] Reward personali light attive con soglia `>=5%` (o threshold assoluta).
- [x] Race/Event model `Top1+Top2+Top3+Threshold` attivo e toggleabile.
- [x] Grant idempotenti verificati (`grant_key`) per tutti i blocchi reward.

### M10 - Detection + Anti-Abuse
- [x] `structure_loot` su detection lootable affidabile con dedupe.
- [x] `exploration` su struttura valida con dedupe.
- [x] Sampled objectives ridotti e confinati ai casi necessari.
- [x] Anti-abuse matrix per famiglia objective attiva.
- [x] Suspicion score con soglie (`warning/clamp/review`) operativo.

### M11 - Storage + Observability + UX finale
- [x] Storage abstraction attiva (no SQL sparso).
- [x] Async-only DB I/O confermato su tutti i path.
- [x] Failover/degraded mode documentato e verificato.
- [x] Contributor visibility completa in GUI.
- [x] Post-cycle recap + history archive consultabili.
- [x] KPI minimi tracciati e disponibili a staff/probe.

### M12 - Procedural Engine + Lore Finale
- [x] Generazione challenge procedural code-first (no dipendenza runtime da `definitions` YAML).
- [x] Anti-ripetizione con signature history (`town=30g`, `global=7g`).
- [x] Matching runtime focus + dimension (biome constraints disattivati da policy M12).
- [x] Lore sfide semplificata (solo obiettivo, progresso, rewards XP/item, tempo residuo).
- [x] Cutover immediato con rigenerazione set attivo al deploy.

### M2 - Tassonomia Core/Supporto (lock runtime)
- [x] Layer tassonomici congelati: `Atlas`, `Board`, `Races`, `Season Codex`.
- [x] Obiettivi passivi demoted con policy runtime autoritativa.
- [x] City Atlas live integrato in `/city -> Missioni` con tab dedicata.
- [x] Seals Atlas fonte canonica (fallback milestone interim disattivato di default).
- [x] Claim reward Atlas manuale per tier con ledger idempotente.

### M5 - City Board Generator (runtime)
- [x] Formula target board `m5-v1` attiva (stage * cycle * base template, con eccezioni hard objective).
- [x] Selezione board pesata multi-segnale attiva (`seasonTheme=50%`, `atlasLowest=30%`, `cityAffinity14d=20%`, configurabili).
- [x] Hard-rules board applicate prima del pick finale con fallback degradato tracciato.
- [x] Ledger attività Atlas 14g persistito e usato per segnale `mostActiveChapter`.
- [x] Probe staff esteso con distribuzione segnali/hard-rules/fallback.

| Objective | Ruolo | Modes | Race | XP città |
| --- | --- | --- | --- | --- |
| `PLAYTIME_MINUTES` | SUPPORT | `DAILY_STANDARD` | OFF | OFF |
| `XP_PICKUP` | SUPPORT | `DAILY_STANDARD`, `WEEKLY_STANDARD` | OFF | OFF |
| `ECONOMY_ADVANCED` | SUPPORT | `WEEKLY_STANDARD`, `MONTHLY_STANDARD` | OFF | OFF |
| `RESOURCE_CONTRIBUTION` | SUPPORT | `WEEKLY_STANDARD`, `MONTHLY_STANDARD` | OFF | OFF |

---

## 2) Decisioni Bloccate

Queste decisioni sono definitive per la prossima implementazione.

### 2.1 Scope e UX

* Scope attuale: rifinitura documento e specifica implementativa.
* Comandi player: **GUI-only** (nessuna nuova command surface player per sfide).
* Comandi staff: diagnostica/operatività via path staff/probe.
* UX obbligatoria challenge card:

  * titolo
  * categoria
  * difficoltà
  * obiettivo concreto
  * progresso numerico + %
  * reward compresso
  * tempo residuo
  * contributo personale
  * top contributor città
* È obbligatorio prevedere:

  * **post-cycle recap**
  * **history/archive** consultabile in GUI
  * **spiegazione contributo personale** per ridurre free-riding

### 2.2 Fairness

* Metrica di scala città: `active_contributors_7d`.
* Definizione `active_contributors_7d`:

  * player unici con **almeno 1 contributo valido** challenge negli ultimi 7 giorni.
* Moltiplicatori target per bracket:

  * `1..5 -> 0.70`
  * `6..10 -> 1.00`
  * `11..20 -> 1.35`
  * `21+ -> 1.65`
* Doppia soglia progress:

  * completamento base: `100%`
  * eccellenza: `140%`
* Per obiettivi cooperativi è obbligatoria visibilità del contributo individuale, perché accountability e feedback individuale riducono il rischio di social loafing nei sistemi di gruppo. ([BIA][2])

### 2.3 Reward e competizione

* Reward personali light: `bonus XP città` + `token vanity player`.
* Soglia reward personale:

  * `>= 5%` del target istanza **oppure**
  * `>= thresholdContributionAbsolute` configurabile per challenge molto grandi.
* Race/Event reward model:

  * `Top1 + Top2 + Top3 + Threshold`, tutti toggleabili da config.
* Modello race/event:

  * winner immediato dove previsto;
  * podio e threshold finalizzati in modo idempotente.
* Le reward devono essere separate in:

  * reward città
  * reward personale
  * reward competitiva
  * reward vanity
* I plugin quest più completi tendono a separare meglio i tipi di progressione invece di fondere tutto in una sola reward stream. ([SpigotMC][1])

### 2.4 Governance sfide

* `1` reroll weekly per città.
* `1` veto stagionale categoria per città.
* Anti-abuse policy: **clamp soft + suspicion score**.
* È obbligatorio supportare:

  * cooldown per `definition`
  * cooldown per `category`
  * cooldown per `focus`
  * bundle constraints per ciclo

### 2.5 Programma stagionale e piattaforma

* Struttura milestone:

  * `3 weekly milestones`
  * `1 seasonal finale`
* Struttura live-ops:

  * `Layer A = active challenges`
  * `Layer B = milestones`
  * `Layer C = season archetypes`
* Storage target:

  * **Repository astratto obbligatorio**
  * backend primario configurabile
  * SQLite ammesso come fallback locale
* Folia:

  * architettura pronta via scheduler abstraction; Paper distingue esplicitamente scheduler standard e schedulers dedicati Folia, quindi l’astrazione è una decisione corretta da fissare ora. ([docs.papermc.io][3])

---

## 3) Architettura Funzionale (Unica Wave Completa)

## 3.1 Programma Stagionale (Layer A/B/C)

### Layer A - Sfide attive

* Daily standard.
* Daily race.
* Weekly standard.
* Monthly standard/event.

### Layer B - Milestone città

* 3 milestone weekly progressive.
* 1 milestone finale stagionale.
* milestone esempi:

  * completa `N` daily nella settimana
  * completa `N` weekly nella stagione
  * raggiungi soglia XP città stagionale
  * coinvolgi `N` contributor unici

### Layer C - Archetipi stagionali

* Rotazione “tema settimana” per modulare categorie favorite.
* Gli archetipi cambiano i pesi di selezione, non le regole core.
* Ogni archetipo deve cambiare la **fantasia di gameplay** percepita, non solo il target numerico.

Input:

* ciclo temporale
* pool definizioni
* configurazione pesi/archetipi
* storico rotazioni recenti

Output:

* set sfide attive coerente e omogeneo tra città
* milestone visibili e tracciabili in GUI
* rotazione percepita non ripetitiva

Failure mode:

* se generazione fallisce, mantenere istanze precedenti non scadute e loggare reason code.

---

## 3.2 Generazione Challenge (Varietà Curata)

Selezione deterministica con vincoli, non casualità libera.

Regole obbligatorie:

* vincoli bundle per ciclo:

  * daily: `1 easy + 1 medium + 1 off-routine`
  * weekly: `1 production + 1 combat/exploration + 1 social/economy`
  * monthly/event: `1 epic + 1 support`
* cooldown su:

  * stessa definition
  * stessa category
  * stesso focus
* rispetto veto stagionale città in fase assegnazione locale
* esclusione di obiettivi con fantasia di gameplay troppo simile nei cicli consecutivi
* limite di ripetizione `sameObjectiveFamilyWindow`

Tie-break di selezione (deterministico):

1. seed ciclo
2. peso definizione
3. ordine lessicografico definition id

Output:

* istanze riproducibili, variate e non ripetitive

Failure mode:

* pool insufficiente rispetto ai vincoli: degradare in modo controllato rimuovendo solo il vincolo meno prioritario (ordine definito in config), mai crash.

Nota:

* questa impostazione è coerente con i sistemi basati su quest pools/rotazioni curate, più efficaci della sola randomizzazione. ([SpigotMC][1])

---

## 3.3 Fairness Model (Target + Doppia Soglia + Tie-Break)

Formula target:

* `targetScaled = roundToStep(baseTarget * bracketMultiplier)`
* `bracketMultiplier` da `active_contributors_7d`

Progression:

* `0..100%` = completamento base
* `100..140%` = finestra eccellenza

Regole ranking evento/race finale:

1. maggiore percentuale completata
2. a parità, timestamp raggiungimento soglia target
3. a parità, maggiore numero contributor unici validi
4. a parità, `town_id` minore

Regole aggiuntive obbligatorie:

* challenge idonee devono supportare:

  * `base completion`
  * `excellence bonus`
* alcune challenge devono usare anche:

  * `parity bonus`: bonus se contribuiscono almeno `N` membri diversi
* il fairness model deve sempre preferire `active contributors`, mai `total members`

Output:

* competizione bilanciata tra città piccole e grandi
* riduzione carry singolo
* incentivo alla partecipazione diffusa

Failure mode:

* se metrica active contributors indisponibile, fallback temporaneo a bracket `1.00` con flag diagnostico.

---

## 3.4 Reward Model (Separazione Funzionale)

Classi reward:

1. reward città
2. reward personale light
3. reward competitiva
4. reward vanity

Regole:

* reward personale solo se contributo player `>= 5% target` oppure soglia assoluta configurata
* reward podio/threshold toggleabili da config
* tutte le grant idempotenti con `grant_key`
* bonus eccellenza separato da bonus completamento base
* preview GUI sicura:

  * mostra solo XP, item, descrizione short del benefit
  * non mostra comando raw

Output:

* reward leggibili
* reward non rumorose
* separazione chiara tra “completa per la città” e “hai contribuito tu”

Failure mode:

* errore grant singolo: marcare evento fallito con reason code, non bloccare l’intero finalize.

---

## 3.5 Race/Event Model (Winner + Podio + Threshold)

Per finestre evento/race:

* winner immediato dove previsto
* top2/top3
* threshold partecipazione/completamento
* reward eccellenza per percentuali oltre 100% quando applicabile

Finalizzazione:

* atomica per finestra
* idempotente per slot reward:

  * `event_top_1`
  * `event_top_2`
  * `event_top_3`
  * `event_threshold`
  * `event_excellence`

Output:

* modello competitivo non binario
* riduzione della sensazione “o vinci o hai perso tempo”

Failure mode:

* finalize interrotto/restart: replay senza doppie grant.

---

## 3.6 Detection Hardening (Spec Tecnica Completa)

### A) `structure_loot`

* usare container `Lootable` / `LootableInventory` e loot table reali
* dedupe per `town + container/structure + cycle`
* no doppio conteggio su reopen/reloot non valido
* il detection layer deve distinguere:

  * apertura contenitore
  * lootabile valido
  * prima assegnazione contenuto
* Questo è coerente con le API Paper sui lootable containers. ([PaperMC][4])

### B) `exploration`

* rilevazione ingresso area struttura valida
* detection basata su struttura reale / bounding area, non solo advancement
* dedupe per `town + struttura scoperta + cycle`
* avanzamento solo su prima scoperta valida per finestra/ciclo
* strutture e generated structures supportano metadata custom/PDC, utile per marker leggeri. ([docs.papermc.io][5])

### C) `sampled objectives`

* peso ridotto nel programma stagionale
* priorità a segnali hard-event
* sampled objectives ammessi solo dove non esiste detection event-driven affidabile

### D) Dedupe e contesto evento

* ogni contributo porta:

  * `instance_id`
  * `town_id`
  * `player_id`
  * `objective_context`
* dedupe key:

  * `instance_id + town_id + player_id + context_hash + time_bucket`

Output:

* detection più affidabile
* meno falsi positivi
* meno doppio conteggio

Failure mode:

* fonte detection non disponibile: degradare a modalità conservative (no grant) con diagnostica esplicita.

---

## 3.7 Anti-Abuse Matrix + Suspicion Score

Policy di base: **clamp soft**.

Interpretazione clamp:

* contributo ridotto alla quota ancora consentita
* se quota residua `0`, contributo rifiutato

Suspicion score:

* punteggio incrementale per pattern sospetti
* soglie configurabili:

  * warning
  * clamp aggressivo
  * review staff consigliata

Profili anti-abuse per famiglia objective:

* resource grind
* mob/combat
* structure/loot
* sampled/passive
* social/cooperative

Regole aggiuntive:

* ogni profilo definisce:

  * throttle
  * soft cap
  * hard cap
  * deny sources
  * clamp mode
  * suspicion increments
  * staff verbosity
* i profili devono essere configurabili, non hardcoded

Output:

* protezione robusta senza blocchi troppo duri su falsi positivi

Failure mode:

* errore nel motore anti-abuse: fallback conservativo (deny/clamp definito da config) + reason code.

---

## 3.8 Storage e Consistenza

Topologia obbligatoria:

* **storage abstraction / repository-first**
* backend primario configurabile
* SQLite supportato
* backend SQL standalone supportato
* nessun SQL sparso nel dominio

Regole operative:

* zero I/O DB sul main thread
* write batching dove possibile
* transazioni esplicite per rollover/finalize/grant
* idempotency keys su tutte le grant e finalize
* migrazioni versionate e idempotenti
* indicizzazione minima obbligatoria:

  * `(cycle_type, cycle_key)`
  * `(town_id, instance_id)`
  * `(grant_key)`
  * `(player_id, instance_id)`

Invarianti:

* nessun doppio payout
* nessuna perdita di progress su restart
* recovery automatico
* SQLite ammesso come backend semplice; Paper distingue chiaramente tra DB embedded/file-based e database standalone, quindi la vera decisione bloccante qui è astrarre il repository, non forzare subito una topologia rigida. ([docs.papermc.io][6])

Failure mode:

* backend primario down:

  * sistema entra in degraded mode
  * progress/reward mantengono idempotenza
  * recovery con replay ordinato se previsto dal backend scelto

---

## 3.9 Observability, KPI e Runbook

Diagnostica obbligatoria:

* lag sync/replay
* outbox pending/failed
* anti-abuse hit per reason
* completion/abandon rate
* winner distribution
* duplicate-grant rate
* contribution distribution
* top carry ratio
* percentuale città che usano reroll/veto
* definition fatigue score:

  * quante volte la stessa famiglia è apparsa nelle ultime `N` finestre

Staff operatività:

* snapshot ciclo attivo
* dettaglio challenge/instance
* explain decision (target/focus/scaling/anti-abuse)
* contatori suspicion/flag
* history recap per città
* recap ultima chiusura ciclo

Runbook:

* smoke checks per ciclo
* procedure failover/recovery
* checklist pre-release

Nota:

* per profiling Paper oggi indirizza verso strumenti moderni e verso un uso corretto degli scheduler; l’osservabilità del plugin deve quindi stare nel plugin stesso, non dipendere da ipotesi opache sul main thread. ([docs.papermc.io][3])

---

## 3.10 Contributor Visibility + Recap

Questa sezione è obbligatoria.

### Contributor visibility

Per ogni challenge attiva la GUI deve mostrare:

* tuo contributo
* top 3 contributor città
* soglia per reward personale
* indicazione se hai già raggiunto la soglia

### Post-cycle recap

A fine daily/weekly/event il sistema deve prevedere un recap con:

* completate / fallite
* XP ottenuta
* bonus eccellenza
* streak
* top contributor
* posizione in leaderboard
* reward vinte
* challenge più rilevante del ciclo

### History/archive

La città deve poter consultare:

* cicli precedenti
* streak storiche
* top contributor storici
* piazzamenti evento

Output:

* accountability
* memoria stagionale
* riduzione free-riding
* maggiore leggibilità del valore del sistema cooperativo

Failure mode:

* se alcuni dati storici non sono disponibili, recap degradato ma sempre renderizzabile.

---

## 4) Contratti Futuri (Da Documentare e Implementare)

Nessuna implementazione in questa wave, ma questi contratti sono fissati.

### 4.1 DTO principali

* `ChallengeSnapshot`
* `CycleProgressSnapshot`
* `ContributorSnapshot`
* `RewardPreviewSafe`
* `EventLeaderboardSnapshot`
* `ChallengeDecisionTrace`
* `CycleRecapSnapshot`
* `ChallengeHistorySnapshot`

### 4.2 Servizi target

* `ChallengeGenerationService`
* `ChallengeFairnessService`
* `ChallengeRewardService`
* `ChallengeAntiAbuseService`
* `ChallengeEventFinalizeService`
* `ChallengeMetricsService`
* `ChallengeStorageService`
* `ChallengeRecapService`
* `ChallengeHistoryService`

### 4.3 Invarianti tecniche

* town-first ovunque (`town_id` canonico)
* async-only DB I/O
* nessun blocco main-thread
* idempotenza obbligatoria su reward/finalize/replay
* GUI-first per player
* scheduler abstraction obbligatoria

### 4.4 Policy UX

* niente nuovi comandi player per sfide
* flusso ufficiale solo GUI
* staff/probe con funzioni diagnostiche e operative

---

## 5) Acceptance / KPI / Go-NoGo

## 5.1 KPI minimi da monitorare

* `completion_rate` per ciclo
* `abandonment_rate` per ciclo
* `anti_abuse_hit_rate` per reason code
* `winner_distribution`
* `duplicate_grant_rate`
* `contribution_spread`
* `reroll_usage_rate`
* `veto_usage_rate`
* `repeat_perception_proxy`:

  * stessa category/focus/definition in finestre recenti
* `personal_reward_eligibility_rate`

## 5.2 Soglie di successo (default)

* duplicate grant rate = `0`
* missing challenge cycle generation = `0`
* nessuna regressione significativa del completion rate rispetto baseline precedente
* zero path player fuori policy GUI-first
* nessuna concentrazione persistente anomala dei winner oltre soglia configurata
* contribution spread non in deterioramento persistente

## 5.3 Segnali di rollback/tuning immediato

* spike anomalo anti-abuse deny/clamp
* winner concentration eccessiva e persistente
* aumento abandonment rate oltre soglia
* replay backlog crescente senza convergenza
* calo marcato contributor spread
* eccesso di definizioni ripetute

## 5.4 Checklist Go/No-Go

- [ ] Tutte le sezioni spec hanno input/output/failure mode.
- [ ] Nessun TBD o conflitto tra regole.
- [ ] Contratti futuri dichiarati e coerenti.
- [ ] KPI e soglie definiti.
- [ ] Policy GUI-only esplicitata in tutti i flussi player.
- [ ] Contributor visibility e recap presenti.
- [ ] Storage abstraction dichiarata.
- [ ] Detection hardening separato da presentation/design.

---

## 6) Test Plan Documentale

1. Coerenza interna:

* zero TBD
* terminologia unificata
* nessuna regola in conflitto

2. Completezza implementativa:

* ogni sottosistema descrive input/output/regole/failure mode

3. Verificabilità operativa:

* KPI misurabili
* criteri Go/No-Go testabili

4. Allineamento prodotto:

* fairness, reward, anti-abuse, detection, storage, recap e contributor visibility coerenti con decisioni bloccate

---

## 7) Assunzioni

* Questa wave non modifica codice plugin/runtime
* Il documento è pensato anche per revisori esterni senza accesso repository
* Le implementazioni future resteranno town-first e async-safe
* Eventuali cambi ai parametri numerici saranno configurabili, non hardcoded
* L’astrazione storage precede la scelta definitiva del backend
* L’astrazione scheduler precede eventuale supporto ufficiale Folia

---

## 8) Nota finale di correzione rispetto al piano originale

Il tuo piano era già molto vicino, ma mancavano quattro cose importanti che facevano parte dei consigli chiave:

* **contributor visibility esplicita**
* **post-cycle recap / history**
* **riduzione del peso degli sampled objectives**
* **storage abstraction prima della topologia rigida MySQL+SQLite**

Così è molto più allineato.

[1]: https://www.spigotmc.org/resources/excellentquests-%E2%AD%90-the-3-in-1-quests-plugin.107283/?utm_source=chatgpt.com "ExcellentQuests ⭐ The 3 in 1 Quests Plugin 4.0.6"
[2]: https://bia.unibz.it/view/pdfCoverPage?download=true&filePid=13263181550001241&instCode=39UBZ_INST&utm_source=chatgpt.com "Social loafing prevention in agile software development teams ..."
[3]: https://docs.papermc.io/paper/dev/scheduler/?utm_source=chatgpt.com "Scheduling"
[4]: https://jd.papermc.io/paper/1.21.11/com/destroystokyo/paper/loottable/LootableInventory.html?utm_source=chatgpt.com "LootableInventory (paper-api 1.21.11-R0.1-SNAPSHOT API)"
[5]: https://docs.papermc.io/paper/dev/pdc/?utm_source=chatgpt.com "Persistent data container (PDC)"
[6]: https://docs.papermc.io/paper/dev/?utm_source=chatgpt.com "Development"
