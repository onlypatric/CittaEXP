## Sezione 1. Reverse engineering del sistema attuale

### 1.1 Cosa esiste davvero oggi

**Daily**

* Sono la board corta da sessione.
* `DAILY_STANDARD` è cooperativa per città, facile, 8 missioni.
* `DAILY_SPRINT_1830` e `DAILY_SPRINT_2130` sono finestre competitive a orario fisso, reveal anticipato, durata breve, poi restano visibili come chiuse.
* Le vecchie `DAILY_RACE_*` sono legacy.

**Weekly**

* `WEEKLY_STANDARD`: board cooperativa settimanale di città.
* `WEEKLY_CLASH`: competizione globale time-boxed.

**Monthly**

* `MONTHLY_STANDARD` è di fatto la family ledger mensile di città.
* `MONTHLY_LEDGER_*` è la board mensile cooperativa, con sottotipi `grand / contract / mystery`.
* `MONTHLY_CROWN` è la competizione globale del mese.
* `MONTHLY_EVENT_A/B` sono slot già compatibili con nuovi eventi.

**Seasonal**

* `SEASON_CODEX_*` è progressione lunga per città, a capitoli/atti.
* `SEASONAL_RACE` è il layer competitivo stagionale più grosso.

**Story Mode**

* Metaprogressione permanente, chapter-based, con gating di livello e stato capitolo.
* Non è un evento temporaneo.

**Atlas**

* Metaprogressione permanente per famiglie di attività.
* Progress passivo e seal/reward.
* Non è un evento temporaneo.

**Staff events v1**

* Layer separato dal motore challenge puro.
* Oggi supporta:

  * `AUTO_RACE`: evento staff che wrappa una challenge competitiva esistente.
  * `JUDGED_BUILD`: contest con submission città + review staff manuale.

### 1.2 Come appaiono in GUI

La GUI `/city -> quest` è già una shell unificata:

* `DAILY`: mostra direttamente le daily standard.
* `WEEKLY`: board weekly standard.
* `MONTHLY`: board monthly ledger.
* `RACES`: contiene sia le challenge competitive sia gli staff events visibili.
* `CODEX`: seasonal codex.
* `ATLAS`: metaprogressione atlas.

Il click su una quest apre **dialog informativo read-only** con struttura coerente card/dialog:

* Stato
* Obiettivo / Indizi
* Progresso / Classifica / Esito
* Ricompense
* Tempo
* Azione, se serve

Questa è una base buona: i nuovi eventi non richiedono una GUI nuova, ma solo **nuove card e nuovi dialog actions** dentro la tab `RACES`.

### 1.3 Cosa è automatico, competitivo, permanente

**Automatico**

* Daily standard
* Weekly standard
* Monthly ledger
* Seasonal codex
* Parte dei monthly events se generati/procedurali

**Competitivo**

* Daily sprint/race
* Weekly clash
* Monthly crown
* Seasonal race
* `AUTO_RACE`
* Qualunque staff event con leaderboard / placement

**Permanente**

* Story Mode
* Atlas

### 1.4 Cosa manca oggi lato eventi

Il sistema attuale è forte su:

* tracking action-based
* progress città
* reward bundles
* scaling
* competizione a ranking
* submission build con review

Ma ha limiti chiari:

1. **Gli eventi staff coprono solo due forme**

   * race quantitativa
   * build judged
   * Manca tutto ciò che è **contract selection**, **snapshot territoriale**, **stato per-città dedicato**, **submission non-build ma survival-native**.

2. **Manca un dominio per eventi state-diff**

   * Claim delta
   * espansione confini
   * rete avamposti
   * footprint stradale
   * Queste non sono bene modellabili come semplice progress counter.

3. **Manca un dominio per eventi “town chooses one plan”**

   * es. una città sceglie 1 contratto tra 3 e poi lo completa.
   * `AUTO_RACE` ha un set obiettivi uguale per tutti.

4. **Il dialog è quasi tutto read-only**

   * Per gli staff events va bene come base, ma serve estenderlo con azioni contestuali:

     * invia submission
     * aggiorna nota
     * accetta contratto
     * ritira submission
     * staff review / reward / publish results

5. **I judged events sono build-centric**

   * Bene per urbanistica e landmark.
   * Poco bene per eventi territoriali o logistici.

---

## Sezione 2. Tassonomia delle future sfide evento

### Nota tecnica Paper

Dal lato implementazione Paper non vedo blocker: il tracciamento può continuare ad appoggiarsi ai normali eventi server già esposti, inclusi `BlockBreakEvent`, `BlockPlaceEvent`, `PlayerFishEvent`, `PlayerExpChangeEvent`, `VillagerAcquireTradeEvent`, `RaidFinishEvent`, oltre agli eventi inventory per GUI custom; per scheduling timed puoi continuare a usare `BukkitScheduler`; per i comandi staff Paper documenta il registration via Brigadier/LifecycleEventManager. ([PaperMC][1])

Per i flow con input testuale, eviterei la vecchia Conversation API: nelle docs Paper è deprecated e viene consigliato di usare `AsyncChatEvent` o Dialog. ([PaperMC][2])

### 2.1 Implementabili subito come `AUTO_RACE`

> Le idee marcate **quasi config-only** richiedono soprattutto preset obiettivi/reward/testi. Se `create auto` accetta già payload parametrico + linked challenge instance, il codice nuovo può essere minimo.

| Nome evento                       | Fantasy lato player                             | Attività concreta nel survival                    | Tipo città incentivata                      | Kind      | Mode/cycle adatto                   | Objective types riusati                                                 | Nuovi hook richiesti                     | Rischio abuso | Difficoltà tecnica             | Valore gameplay |
| --------------------------------- | ----------------------------------------------- | ------------------------------------------------- | ------------------------------------------- | --------- | ----------------------------------- | ----------------------------------------------------------------------- | ---------------------------------------- | ------------- | ------------------------------ | --------------- |
| **Convoglio del Vault Imperiale** | “Riempiamo i magazzini della città”             | Deposito materiali tematici nel vault città       | città organizzate, miners/farmers/logistica | AUTO_RACE | weekend 24-48h o monthly event slot | `VAULT_DELIVERY`, `RESOURCE_CONTRIBUTION`                               | nessuno                                  | medio         | basso                          | alto            |
| **Bonifica delle Gallerie**       | “Ripuliamo le miniere e scaviamo in profondità” | Mining + kill ostili in cave/deepslate + XP       | città minerarie / PvE                       | AUTO_RACE | serale 90m o weekend                | `BLOCK_MINE`, `MOB_KILL`, `XP_PICKUP`                                   | nessuno                                  | medio         | basso                          | alto            |
| **Raccolto di Stagione**          | “La città deve produrre cibo e raccolti”        | Harvest, farm upkeep, craft cibo                  | città agricole                              | AUTO_RACE | weekly special                      | `CROP_HARVEST`, `FARMING_ECOSYSTEM`, `FOOD_CRAFT`                       | nessuno                                  | basso         | basso                          | alto            |
| **Fiera dei Mestieri**            | “Attivate botteghe, alchimie, professioni”      | Trade villager, craft utili, brew                 | città mercantili                            | AUTO_RACE | weekend 24h                         | `VILLAGER_TRADE`, `ITEM_CRAFT`, `BREW_POTION`                           | opzionale: conteggio professioni uniche  | medio-alto    | basso base / medio con breadth | alto            |
| **Spedizione Oceanica**           | “La città torna al mare”                        | Pesca, loot, strutture oceaniche, attività marine | città costiere / esplorazione               | AUTO_RACE | weekend                             | `OCEAN_ACTIVITY`, `FISH_CATCH`, `STRUCTURE_DISCOVERY`, `STRUCTURE_LOOT` | nessuno                                  | basso         | basso                          | alto            |
| **Frontiera del Nether**          | “Apriamo rotte e avamposti nel Nether”          | Travel, loot, kill, attività nel Nether           | città esploratrici / combattive             | AUTO_RACE | serale 90m o weekend                | `NETHER_ACTIVITY`, `MOB_KILL`, `STRUCTURE_LOOT`, `DIMENSION_TRAVEL`     | nessuno                                  | medio         | basso                          | alto            |
| **Difesa Civica**                 | “Respingete le minacce al territorio”           | Raid, kill elite, difesa coordinata               | città PvE / cooperative                     | AUTO_RACE | evento annunciato 2h                | `RAID_WIN`, `MOB_KILL`, `RARE_MOB_KILL`, `BOSS_KILL`                    | nessuno                                  | medio         | medio                          | alto            |
| **Cantiere Civico Lampo**         | “Tutti al lavoro sul distretto”                 | Place block, construction, automazioni            | città builder/tecniche                      | AUTO_RACE | 3-6h                                | `PLACE_BLOCK`, `CONSTRUCTION`, `REDSTONE_AUTOMATION`                    | opzionale whitelist materiali/claim-only | alto          | basso base / medio con filtri  | medio           |

### 2.2 Implementabili subito come `JUDGED_BUILD`

| Nome evento                        | Fantasy lato player                                     | Attività concreta nel survival                 | Tipo città incentivata            | Kind         | Mode/cycle adatto  | Objective types riusati | Nuovi hook richiesti | Rischio abuso | Difficoltà tecnica | Valore gameplay |
| ---------------------------------- | ------------------------------------------------------- | ---------------------------------------------- | --------------------------------- | ------------ | ------------------ | ----------------------- | -------------------- | ------------- | ------------------ | --------------- |
| **Porta della Città**              | “Costruite l’ingresso simbolo del vostro centro urbano” | Build di gatehouse/ingresso reale nel claim    | città urbanistiche                | JUDGED_BUILD | mensile con review | nessuno: judged puro    | nessuno              | basso         | basso              | alto            |
| **Mercato Cittadino**              | “Create una piazza commerciale viva”                    | Build di mercato reale con botteghe e percorsi | città economiche / RP             | JUDGED_BUILD | mensile            | nessuno                 | nessuno              | basso         | basso              | alto            |
| **Quartiere Agricolo Modello**     | “Mostrate una zona produttiva bella e funzionale”       | Farm district nel survival reale               | città agricole / functional build | JUDGED_BUILD | mensile            | nessuno                 | nessuno              | basso         | basso              | alto            |
| **Distretto Industriale Ordinato** | “Mostrate industria senza caos”                         | Smeltery, storage, automazione, viabilità      | città tech / redstone             | JUDGED_BUILD | mensile            | nessuno                 | nessuno              | basso         | basso              | medio-alto      |

### 2.3 Richiedono un nuovo `event kind`

| Nome evento                   | Fantasy lato player                             | Attività concreta nel survival                       | Tipo città incentivata         | Kind consigliato                  | Mode/cycle adatto | Objective types riusati                          | Nuovi hook richiesti                            | Rischio abuso | Difficoltà tecnica | Valore gameplay |
| ----------------------------- | ----------------------------------------------- | ---------------------------------------------------- | ------------------------------ | --------------------------------- | ----------------- | ------------------------------------------------ | ----------------------------------------------- | ------------- | ------------------ | --------------- |
| **Espansione dei Confini**    | “La città cresce davvero sul territorio”        | Claim netti durante la finestra evento               | città territoriali             | `TERRITORIAL_SNAPSHOT`            | weekend / mensile | nessuno diretto                                  | hook claim add/remove + snapshot baseline/final | alto          | medio-alto         | altissimo       |
| **Grande Strada Reale**       | “Costruite una vera rete stradale urbana”       | Road footprint nei claim + connessioni tra distretti | città urbanistiche             | `TERRITORIAL_SNAPSHOT` o derivato | mensile           | `PLACE_BLOCK` solo come supporto                 | hook connettività + road material rules         | alto          | alto               | alto            |
| **Rete di Avamposti**         | “Fondiamo punti avanzati stabili”               | Nuovi nuclei claim non contigui, tenuti vivi         | città espansive / esplorazione | `TERRITORIAL_SNAPSHOT`            | mensile           | nessuno diretto                                  | hook nuclei/outpost detection + min hold time   | alto          | alto               | alto            |
| **Contratti della Corona**    | “Ogni città sceglie una commessa e la completa” | Selezione contratto + consegne in vault              | città logistiche               | `TOWN_CONTRACT`                   | 24-72h            | `VAULT_DELIVERY`, `RESOURCE_CONTRIBUTION`        | store scelta contratto per città                | medio         | medio              | altissimo       |
| **Piano Carovane del Nether** | “Importate risorse difficili dal Nether”        | Scegli contratto themed Nether + deliveries          | città endgame                  | `TOWN_CONTRACT`                   | weekend           | `VAULT_DELIVERY`, `NETHER_ACTIVITY`              | filtro dimension/origin + scelta contratto      | medio-alto    | medio-alto         | alto            |
| **Rotta dei Porti**           | “Il commercio marittimo decide il vincitore”    | Contract + ocean activity + deposito merci           | città costiere / commercio     | `TOWN_CONTRACT`                   | weekend           | `VAULT_DELIVERY`, `OCEAN_ACTIVITY`, `FISH_CATCH` | stato contratto + manifest multi-linea          | medio         | medio-alto         | alto            |

### 2.4 Lettura engineering-first della tassonomia

**Da fare quasi subito**

* Convoglio del Vault Imperiale
* Bonifica delle Gallerie
* Raccolto di Stagione
* Spedizione Oceanica
* Frontiera del Nether
* Porta della Città
* Mercato Cittadino
* Quartiere Agricolo Modello

**Da fare subito ma con 1 piccolo upgrade opzionale**

* Fiera dei Mestieri
  Base: subito.
  Versione migliore: aggiungere diversity scoring per evitare spam di 1 solo villager.

**Da non forzare nel motore attuale**

* Espansione dei Confini
* Rete di Avamposti
* Contratti della Corona

Questi tre non sono “nuove config challenge”: sono proprio **nuovi modelli di evento**.

---

## Sezione 3. Shortlist engineering-first

### 3.1 `Convoglio del Vault Imperiale`

* **Perché entra in shortlist**

  * massimo allineamento con città/vault
  * usa tracking già esistente
  * leggibilissimo in UI
  * reward chiaro
* **Kind**

  * `AUTO_RACE`
* **Lifecycle**

  * `DRAFT -> PUBLISHED -> ACTIVE -> COMPLETED -> ARCHIVED`
* **Tracking**

  * progress via `VAULT_DELIVERY` / `RESOURCE_CONTRIBUTION`
* **Reward model**

  * auto
  * `completion` per soglia minima
  * `excellence` per soglia alta
  * `winner` per top placement
* **Staff workflow**

  * create preset -> publish -> auto start -> close -> publish results
* **Player workflow**

  * apre card in `RACES`
  * vede materiali richiesti e leaderboard
  * contribuisce nel vault città
* **Rischio principale**

  * funnel di materiali stockpiled
* **Mitigazione principale**

  * theme con categorie larghe ma caps per item dominante; opzionale decay/soft cap per singolo materiale

### 3.2 `Raccolto di Stagione`

* **Perché entra**

  * fortissimo survival-native
  * utile a città early-mid game
  * costante ma non tossico
* **Kind**

  * `AUTO_RACE`
* **Lifecycle**

  * standard auto race
* **Tracking**

  * `CROP_HARVEST`, `FARMING_ECOSYSTEM`, `FOOD_CRAFT`
* **Reward model**

  * auto, con reward completion/excellence
* **Staff workflow**

  * quasi preset puro
* **Player workflow**

  * farming normale + craft cibo
* **Rischio principale**

  * farm iper-ottimizzate sbilanciano
* **Mitigazione principale**

  * mix di 2-3 objective diversi, non solo harvest grezzo

### 3.3 `Fiera dei Mestieri`

* **Perché entra**

  * porta commercio e villager al centro
  * molto “città”
  * buon contrasto con mining/farming
* **Kind**

  * `AUTO_RACE`
* **Lifecycle**

  * standard auto race
* **Tracking**

  * `VILLAGER_TRADE`, `ITEM_CRAFT`, `BREW_POTION`
* **Reward model**

  * auto
* **Staff workflow**

  * create preset, opzionale profili diversi: mercato / alchimia / artigianato
* **Player workflow**

  * trade, craft, brew
* **Rischio principale**

  * spam trade su 1 loop ottimizzato
* **Mitigazione principale**

  * fase 2: diversity score per professioni/materiali

### 3.4 `Spedizione Oceanica`

* **Perché entra**

  * usa famiglie di attività già supportate
  * gameplay vario, non solo grind
  * molto leggibile anche in lore
* **Kind**

  * `AUTO_RACE`
* **Lifecycle**

  * standard auto race
* **Tracking**

  * `OCEAN_ACTIVITY`, `FISH_CATCH`, `STRUCTURE_DISCOVERY`, `STRUCTURE_LOOT`
* **Reward model**

  * auto
* **Staff workflow**

  * preset rapido
* **Player workflow**

  * pesca, esplorazione, loot
* **Rischio principale**

  * fortuna RNG su loot
* **Mitigazione principale**

  * usare bundle di objective, non singolo loot gate

### 3.5 `Frontiera del Nether`

* **Perché entra**

  * endgame forte
  * survival puro
  * già coerente con objective types
* **Kind**

  * `AUTO_RACE`
* **Lifecycle**

  * standard auto race
* **Tracking**

  * `NETHER_ACTIVITY`, `MOB_KILL`, `STRUCTURE_LOOT`, `DIMENSION_TRAVEL`
* **Reward model**

  * auto con winner importante
* **Staff workflow**

  * preset con announce forte
* **Player workflow**

  * run nel Nether
* **Rischio principale**

  * farming in area già preparate
* **Mitigazione principale**

  * combinare kill + loot + travel, non solo 1 metrica

### 3.6 `Difesa Civica`

* **Perché entra**

  * evento memorabile
  * induce coordinazione città
  * usa raid/PvE reali
* **Kind**

  * `AUTO_RACE`
* **Lifecycle**

  * standard auto race, ma finestra breve
* **Tracking**

  * `RAID_WIN`, `MOB_KILL`, `RARE_MOB_KILL`, `BOSS_KILL`
* **Reward model**

  * auto con forte `winner`
* **Staff workflow**

  * publish a data fissa
* **Player workflow**

  * mobilitazione di città
* **Rischio principale**

  * poche città possono spawnare raid efficienti
* **Mitigazione principale**

  * usare raid win come bonus, non come unico obiettivo

### 3.7 `Porta della Città`

* **Perché entra**

  * judged build con tema chiarissimo
  * review facile
  * grande impatto visivo
* **Kind**

  * `JUDGED_BUILD`
* **Lifecycle**

  * `DRAFT -> PUBLISHED -> ACTIVE -> REVIEW -> COMPLETED -> ARCHIVED`
* **Tracking**

  * submission città esistente
* **Reward model**

  * placement manuale o semi-manuale
* **Staff workflow**

  * create judged -> review -> placement -> reward
* **Player workflow**

  * build in claim -> submit coords + nota
* **Rischio principale**

  * submission troll o location errate
* **Mitigazione principale**

  * validazione claim città + una sola submission attiva per città

### 3.8 `Quartiere Agricolo Modello`

* **Perché entra**

  * judged build che premia funzionalità urbana, non solo facciata
  * ideale per città survival vere
* **Kind**

  * `JUDGED_BUILD`
* **Lifecycle**

  * standard judged
* **Tracking**

  * submission esistente
* **Reward model**

  * placement
* **Staff workflow**

  * rubric con estetica/funzionalità/integrazione
* **Player workflow**

  * costruzione + submit
* **Rischio principale**

  * rubric poco consistente
* **Mitigazione principale**

  * rubric standardizzata nel payload

### 3.9 `Espansione dei Confini`

* **Perché entra**

  * è l’evento più “città” in assoluto
  * impossible-to-ignore lato fantasy
  * differenzia davvero CittaEXP
* **Kind**

  * nuovo `TERRITORIAL_SNAPSHOT`
* **Lifecycle**

  * `DRAFT -> PUBLISHED -> ACTIVE -> COMPLETED`
* **Tracking**

  * baseline/final snapshot + delta claims
* **Reward model**

  * auto ranking + winner
* **Staff workflow**

  * create territory preset -> publish -> start snapshot -> close -> results
* **Player workflow**

  * espansione reale del territorio
* **Rischio principale**

  * claim flip / exploit di confine
* **Mitigazione principale**

  * score su net gain + minimum hold duration

### 3.10 `Contratti della Corona`

* **Perché entra**

  * porta scelta strategica per città
  * usa vault ma con identità molto più forte
  * ottima leggibilità UI
* **Kind**

  * nuovo `TOWN_CONTRACT`
* **Lifecycle**

  * `DRAFT -> PUBLISHED -> ACTIVE -> COMPLETED`
* **Tracking**

  * città seleziona 1 contratto, poi consegna linee merce
* **Reward model**

  * auto per completamento + ranking per velocità/punteggio
* **Staff workflow**

  * definisce 3-5 manifest
* **Player workflow**

  * apre card, sceglie contratto, consegna
* **Rischio principale**

  * scelte sbagliate/abbandono
* **Mitigazione principale**

  * lock dopo prima consegna, preview chiara, contratti bilanciati

---

## Sezione 4. Esempi implementativi molto concreti

### 4.1 `Convoglio del Vault Imperiale`

#### 1. Dominio

* **Kind evento**

  * `AUTO_RACE`
* **Status lifecycle**

  * esistente: `DRAFT -> PUBLISHED -> ACTIVE -> COMPLETED -> ARCHIVED`
* **Dati persistenti necessari**

  * nessun nuovo store se riusi `linkedChallengeInstanceId`
  * nel `payloadJson`:

    * `templateKey: VAULT_CONVOY`
    * `theme: STONE_IRON_FOOD` oppure `WOOD_GLASS_WOOL`
    * `rewardPreset`
    * `broadcastKey`
* **Riusa `ChallengeInstance`**

  * sì, pienamente

#### 2. Tracking

* **Listener/eventi server**

  * nessun listener nuovo se `VAULT_DELIVERY` è già agganciato al ledger vault
* **Objective types da riusare**

  * `VAULT_DELIVERY`
  * `RESOURCE_CONTRIBUTION`
* **Nuovi objective types**

  * nessuno
* **Anti exploit**

  * usare ledger tx reali, non inventory move grezzi
  * dedupe su tx id / delivery id
  * soft cap su singolo materiale dominante
  * opzionale: no credit per withdraw/redeposit loop

#### 3. Scheduling

* **Start**

  * publish manuale + start schedulato
* **End**

  * chiusura automatica al `endAt`
* **Review**

  * non serve
* **Publish**

  * staff publish
* **Publish results**

  * automatico o staff-assisted

#### 4. GUI player

* **Tab**

  * `RACES`
* **Card**

  * titolo + sottotitolo + top 3 materiali + timer + leaderboard città
* **Dialog**

  * Stato missione
  * Obiettivo: materiali richiesti
  * Progresso: totale città / posizione
  * Ricompense
  * Tempo
  * Azione: “Consegna nel vault città”
* **Azioni player**

  * nessuna action attiva nella GUI; gameplay nel survival
* **Submission**

  * no

#### 5. GUI/dialog staff

* **Create flow**

  * `create auto` con preset `vault_convoy`
  * selezione theme
  * reward preset
  * start/end
* **Review flow**

  * no
* **Finalizzazione**

  * close / publish-results
* **Reward flow**

  * auto bundle da placement

#### 6. Comandi staff

* **Creazione**

  * `/cittaexp staff challenges events create auto vault_convoy`
* **Forcing**

  * `/cittaexp staff challenges events start <eventId>`
* **Finalizzazione**

  * `/cittaexp staff challenges events close <eventId>`
  * `/cittaexp staff challenges events publish-results <eventId>`
* **Esempio**

  * `/cittaexp staff challenges events create auto vault_convoy start 2026-04-10T18:00 end 2026-04-12T18:00 theme STONE_IRON_FOOD`

#### 7. Reward

* **Auto o manuale**

  * auto
* **XP città**

  * sì
* **Soldi**

  * sì
* **Vault items**

  * sì
* **Broadcast**

  * top 3 + città completatrici
* **Placement**

  * 1°, 2°, 3° con `winner`

#### 8. File/classi da toccare

* `StaffEventService`
* `StaffEventRepository`
* `CityChallengeService`
* `CityChallengeGuiService`
* `CityChallengeSettings`
* `ChallengeNarrativeFormatter`
* `challenge-texts.yml`
* `challenge-rewards.yml`
* **Nuove**

  * `StaffEventTemplateCatalog`
  * `StaffEventTemplateKey`
  * `AutoRaceTemplateFactory`

**Stima**

* se `AUTO_RACE` è già parametrico: **quasi solo config + preset code**
* se non lo è: piccolo strato template

---

### 4.2 `Fiera dei Mestieri`

#### 1. Dominio

* **Kind**

  * `AUTO_RACE`
* **Persistenza**

  * `payloadJson`:

    * `templateKey: TRADE_FAIR`
    * `scoringProfile: BASIC | DIVERSIFIED`
    * `allowedProfessions[]` opzionale
* **Riusa `ChallengeInstance`**

  * sì, per versione base

#### 2. Tracking

* **Listener/eventi server**

  * per la base, nessuno nuovo se `VILLAGER_TRADE` è già tracciato
  * per versione migliore: hook a `VillagerAcquireTradeEvent` solo se vuoi intelligence sui setup, non necessario per il punteggio base; per craft e brew restano i normali eventi inventory/crafting già esposti da Paper. ([PaperMC][3])
* **Objective types riusati**

  * `VILLAGER_TRADE`
  * `ITEM_CRAFT`
  * `BREW_POTION`
* **Nuovi objective types**

  * opzionale `UNIQUE_TRADE_PROFESSION_COUNT`
* **Perché gli attuali non bastano, se vuoi la variante buona**

  * `VILLAGER_TRADE` misura volume, non varietà
  * una sola catena trade ottimizzata può dominare
* **Anti exploit**

  * cooldown per trade ripetitivo sullo stesso villager opzionale
  * cap peso per singola professione
  * score composito: trade + craft + brew

#### 3. Scheduling

* weekend 24h funziona meglio
* no review

#### 4. GUI player

* **Tab**

  * `RACES`
* **Card**

  * icona emerald/brewing stand
  * sezioni “Mestieri”, “Artigianato”, “Alchimia”
* **Dialog**

  * scoreboard per categoria
  * hint: “non basta un solo loop”
* **Azioni**

  * nessuna

#### 5. GUI/dialog staff

* create preset base o diversified
* nessuna review

#### 6. Comandi staff

* `/cittaexp staff challenges events create auto trade_fair profile BASIC`
* `/cittaexp staff challenges events create auto trade_fair profile DIVERSIFIED`

#### 7. Reward

* auto
* reward media, non troppo esplosive
* ottimo evento da `daily competitive x3` o `weekly-ish x5`

#### 8. File/classi

* `ChallengeObjectiveType` solo se aggiungi diversity objective
* `CityChallengeService`
* `ChallengeNarrativeFormatter`
* `challenge-texts.yml`
* `challenge-rewards.yml`
* **Nuove opzionali**

  * `TradeFairScoringService`
  * `VillagerTradeDiversityTracker`

---

### 4.3 `Spedizione Oceanica`

#### 1. Dominio

* **Kind**

  * `AUTO_RACE`
* **Persistenza**

  * `templateKey: OCEAN_EXPEDITION`
  * `profile: COASTAL | DEEP_SEA`
* **Riusa `ChallengeInstance`**

  * sì

#### 2. Tracking

* **Listener/eventi server**

  * nessuno nuovo per la base
  * `PlayerFishEvent` copre bene il lato pesca; il resto resta nel motore challenge via objective esistenti. ([PaperMC][4])
* **Objective types**

  * `OCEAN_ACTIVITY`
  * `FISH_CATCH`
  * `STRUCTURE_DISCOVERY`
  * `STRUCTURE_LOOT`
* **Nuovi objective types**

  * nessuno
* **Anti exploit**

  * mixare più objective
  * non fare dipendere tutto da un singolo loot raro

#### 3. Scheduling

* weekend 48h
* reveal immediato, no review

#### 4. GUI player

* **Card**

  * titolo + “pesca / esplora / saccheggia”
* **Dialog**

  * tre barre separate
  * classifica città
  * reward
* **Azioni**

  * nessuna

#### 5. GUI staff

* create auto con theme coastal/deep sea
* publish -> close -> results

#### 6. Comandi staff

* `/cittaexp staff challenges events create auto ocean_expedition profile DEEP_SEA`

#### 7. Reward

* auto
* placement con bonus money/vault items a tema marino

#### 8. File/classi

* `CityChallengeService`
* `ChallengeNarrativeFormatter`
* `challenge-texts.yml`
* `challenge-rewards.yml`
* `StaffEventTemplateCatalog`

---

### 4.4 `Frontiera del Nether`

#### 1. Dominio

* **Kind**

  * `AUTO_RACE`
* **Persistenza**

  * `templateKey: NETHER_FRONTIER`
  * `difficulty: STANDARD | HARD`
* **Riusa `ChallengeInstance`**

  * sì

#### 2. Tracking

* **Listener/eventi server**

  * nessuno nuovo se `NETHER_ACTIVITY`, `DIMENSION_TRAVEL`, `STRUCTURE_LOOT` e kill sono già agganciati
* **Objective types**

  * `NETHER_ACTIVITY`
  * `MOB_KILL`
  * `STRUCTURE_LOOT`
  * `DIMENSION_TRAVEL`
* **Nuovi objective types**

  * nessuno
* **Anti exploit**

  * peso combinato
  * ridurre valore del solo travel spam
  * usare loot/kill come parte essenziale

#### 3. Scheduling

* ottimo in finestra 90-120m o weekend

#### 4. GUI player

* card aggressiva, focus su rischio
* dialog con:

  * Stato
  * Progresso città
  * Top performers
  * Tempo residuo

#### 5. GUI staff

* profilo standard/hard
* nessuna review

#### 6. Comandi

* `/cittaexp staff challenges events create auto nether_frontier difficulty HARD`

#### 7. Reward

* auto
* `winner` importante
* `vaultItems` a tema blaze/quartz/nether

#### 8. File/classi

* `CityChallengeService`
* `ChallengeTargetScalingService`
* `ChallengeNarrativeFormatter`
* `challenge-texts.yml`
* `challenge-rewards.yml`

---

### 4.5 `Porta della Città`

#### 1. Dominio

* **Kind**

  * `JUDGED_BUILD`
* **Lifecycle**

  * già esistente con `REVIEW`
* **Persistenza**

  * riuso `StaffEvent`
  * `payloadJson` esteso:

    * `templateKey: CITY_GATE`
    * `rubric: { aesthetics, recognizability, city-integration, functionality }`
    * `noteMaxLength`
    * `allowUpdateUntil`
* **Riusa `ChallengeInstance`**

  * no, submission-based

#### 2. Tracking

* **Listener/eventi server**

  * nessuno per il punteggio
* **Objective types**

  * nessuno
* **Nuovi objective types**

  * nessuno
* **Anti exploit**

  * validare che la submission sia:

    * dentro claim città
    * mondo valido
    * città submitter corrispondente
  * una sola submission attiva per città

#### 3. Scheduling

* **Start**

  * `ACTIVE` = window di build/submit
* **End**

  * `ACTIVE -> REVIEW`
* **Review**

  * manuale staff
* **Publish**

  * manuale
* **Publish results**

  * al termine review

#### 4. GUI player

* **Tab**

  * `RACES`
* **Card**

  * “Contest costruzione”
  * stato `Aperto / In review / Concluso`
* **Dialog**

  * brief tema
  * rubric semplificata
  * ricompense placement
  * tempo
  * azione: `Invia candidatura`
* **Azioni**

  * submit
  * aggiorna nota
  * ritira submission se ancora in `ACTIVE`
* **Submission**

  * posizione player attuale + nota

#### 5. GUI/dialog staff

* **Create flow**

  * `create judged` + preset `city_gate`
* **Review flow**

  * lista submission
  * teleport rapido
  * accept/reject/place
* **Finalizzazione**

  * publish results
* **Reward flow**

  * reward per placement

#### 6. Comandi staff

* `/cittaexp staff challenges events create judged city_gate`
* `/cittaexp staff challenges events submissions <eventId>`
* `/cittaexp staff challenges events review <eventId> <town>`
* `/cittaexp staff challenges events place <eventId> <town> 1`
* `/cittaexp staff challenges events reward <eventId> <town>`

#### 7. Reward

* manuale o semi-manuale
* placement 1-3
* broadcast con vincitori
* reward “vanity” forte ma anche utilità reale

#### 8. File/classi

* `StaffEventService`
* `StaffEventRepository`
* `CityChallengeGuiService`
* `challenge-texts.yml`
* **Nuove**

  * `JudgedBuildTemplateCatalog`
  * `JudgedBuildRubricRenderer`
  * `StaffEventSubmissionDialogService`

---

### 4.6 `Quartiere Agricolo Modello`

#### 1. Dominio

* **Kind**

  * `JUDGED_BUILD`
* **Persistenza**

  * stesso dominio di `JUDGED_BUILD`
  * `payloadJson`:

    * `templateKey: AGRI_DISTRICT`
    * `rubric: { productivity, aesthetics, accessibility, city-fit }`
* **Riusa `ChallengeInstance`**

  * no

#### 2. Tracking

* nessun tracking automatico necessario
* no nuovi objective

#### 3. Scheduling

* finestra lunga, es. 10-14 giorni
* review dopo chiusura

#### 4. GUI player

* **Card**

  * chiarisce che il build deve essere reale e funzionale
* **Dialog**

  * mostra rubric
  * suggerisce di submit dal cuore del quartiere
* **Azioni**

  * submit / update note

#### 5. GUI staff

* review con rubric
* opzionale staff note strutturata

#### 6. Comandi

* uguali a `JUDGED_BUILD`, preset diverso

#### 7. Reward

* placement manuale
* ottimo per reward città + bonus personale minimo al submitter

#### 8. File/classi

* stessi di `Porta della Città`
* `challenge-texts.yml` con testi distinti

---

### 4.7 `Espansione dei Confini`

#### 1. Dominio

* **Kind evento**

  * nuovo `TERRITORIAL_SNAPSHOT`
* **Status lifecycle**

  * `DRAFT -> PUBLISHED -> ACTIVE -> COMPLETED -> ARCHIVED`
* **Dati persistenti necessari**

  * tabella `staff_event_town_snapshot`

    * `event_id`
    * `town_id`
    * `phase` (`BASELINE`, `FINAL`)
    * `claimed_chunks`
    * `frontier_chunks`
    * `contiguous_regions`
    * `captured_at`
    * `snapshot_json`
  * tabella opzionale `staff_event_town_claim_delta`

    * `event_id`
    * `town_id`
    * `chunk_key`
    * `change_type`
    * `changed_at`
    * `held_until`
* **Riusa `ChallengeInstance`**

  * no, non bene

#### 2. Tracking

* **Listener/eventi server**

  * hook del subsystem claim città:

    * `onTownClaimAdded`
    * `onTownClaimRemoved`
  * se non esistono eventi pubblici, aggiungere callback/service hook
* **Objective types esistenti**

  * nessuno basta davvero
* **Nuovi objective types**

  * non basta introdurre un objective type:

    * qui il problema è **snapshot + delta territoriale**, non solo count action
* **Anti exploit**

  * score su **net gain**, non gross gain
  * `minimumHoldDuration` per chunk guadagnato
  * ignorare flip claim/remove/claim in finestra breve
  * escludere claim invalidati/cancellati entro chiusura
  * opzionale: score separato su frontier expansion, non su infill interno

#### 3. Scheduling

* **Start**

  * cattura baseline all’attivazione
* **End**

  * cattura final snapshot
* **Review**

  * non serve
* **Publish**

  * staff publish
* **Publish results**

  * automatico alla chiusura

#### 4. GUI player

* **Tab**

  * `RACES`
* **Card**

  * “+X claim netti / classifica”
* **Dialog**

  * Stato
  * Regole di scoring
  * Progresso città:

    * claim netti
    * regioni nuove
    * chunk trattenuti validi
  * Tempo
* **Azioni**

  * nessuna GUI action
* **Submission**

  * no

#### 5. GUI/dialog staff

* **Create flow**

  * create territory preset
  * set scoring mode:

    * `NET_CLAIMS`
    * `FRONTIER_HELD`
    * `OUTPOST_COUNT` futura
  * set `minimumHoldDuration`
* **Review flow**

  * non serve
* **Finalizzazione**

  * close + auto ranking
* **Reward flow**

  * auto placement

#### 6. Comandi staff

* **Creazione**

  * `/cittaexp staff challenges events create territory net_claims`
* **Forcing**

  * `/cittaexp staff challenges events start <eventId>`
  * `/cittaexp staff challenges events close <eventId>`
* **Sintassi esempio**

  * `/cittaexp staff challenges events create territory net_claims start 2026-05-01T18:00 end 2026-05-03T18:00 minHold 3600`

#### 7. Reward

* **Auto o manuale**

  * auto
* **XP città**

  * alta
* **Soldi**

  * alti
* **Vault items**

  * moderati
* **Broadcast**

  * top 3 + delta claim
* **Placement**

  * forte `winner`

#### 8. File/classi

* `StaffEventService`
* `StaffEventRepository`
* `CityChallengeGuiService`
* `challenge-texts.yml`
* `challenge-rewards.yml`
* **Nuove**

  * `StaffEventKind` (+ `TERRITORIAL_SNAPSHOT`)
  * `TerritorialSnapshotService`
  * `TerritorialSnapshotRepository`
  * `TerritorialScoringService`
  * `TownClaimChangeListener`
  * `TownClaimSnapshot`
  * `TownClaimDeltaRecord`

---

### 4.8 `Contratti della Corona`

#### 1. Dominio

* **Kind evento**

  * nuovo `TOWN_CONTRACT`
* **Lifecycle**

  * `DRAFT -> PUBLISHED -> ACTIVE -> COMPLETED -> ARCHIVED`
* **Persistenza minima**

  * tabella `staff_event_town_contract`

    * `event_id`
    * `town_id`
    * `contract_key`
    * `status` (`UNSELECTED`, `SELECTED`, `COMPLETED`)
    * `selected_by`
    * `selected_at`
    * `completed_at`
  * tabella `staff_event_town_contract_line`

    * `event_id`
    * `town_id`
    * `line_key`
    * `required_amount`
    * `delivered_amount`
    * `weight`
  * payload evento:

    * array manifest/contract definitions
* **Riusa `ChallengeInstance`**

  * non bene per il core, perché ogni città può avere un manifest diverso

#### 2. Tracking

* **Listener/eventi server**

  * hook al ledger del vault
* **Objective types esistenti**

  * `VAULT_DELIVERY`
  * `RESOURCE_CONTRIBUTION`
* **Nuovi objective types**

  * non indispensabili
* **Perché gli attuali non bastano**

  * manca la **scelta per-città**
  * manca il **manifest multi-linea con cap dedicati**
  * manca il **lock dopo selezione**
* **Anti exploit**

  * lock contratto dopo prima consegna
  * dedupe su ledger tx
  * no withdraw/redeposit
  * line items con cappatura, così non domini con 1 materiale

#### 3. Scheduling

* **Start**

  * evento `ACTIVE`, città possono accettare contratto
* **End**

  * close automatico
* **Review**

  * no
* **Publish**

  * staff
* **Publish results**

  * automatico

#### 4. GUI player

* **Tab**

  * `RACES`
* **Card**

  * mostra “3 contratti disponibili”
* **Dialog**

  * Stato
  * Contratti disponibili
  * se città non ha scelto: action `Accetta contratto`
  * se ha scelto: mostra linee e progress
  * leaderboard per punteggio o completamento
* **Azioni**

  * `Accetta contratto`
  * opzionale `Conferma`
* **Submission**

  * no submit location; scelta via dialog

#### 5. GUI/dialog staff

* **Create flow**

  * create contract event
  * definire 3-5 manifest
  * ogni manifest:

    * nome
    * linee materiali
    * target
    * peso
* **Review flow**

  * no
* **Finalizzazione**

  * close + ranking
* **Reward flow**

  * auto

#### 6. Comandi staff

* **Creazione**

  * `/cittaexp staff challenges events create contract crown_supply`
* **Forcing**

  * `/cittaexp staff challenges events start <eventId>`
  * `/cittaexp staff challenges events close <eventId>`
* **Esempio**

  * `/cittaexp staff challenges events create contract crown_supply start 2026-06-01T18:00 end 2026-06-03T18:00 profile EARLY_MID`

#### 7. Reward

* **Auto o manuale**

  * auto
* **XP città**

  * alta
* **Soldi**

  * alti
* **Vault items**

  * sì
* **Broadcast**

  * città che ha completato per prima
  * top punteggi
* **Placement**

  * winner + excellence per chi completa

#### 8. File/classi

* `StaffEventService`
* `StaffEventRepository`
* `CityChallengeGuiService`
* `challenge-texts.yml`
* `challenge-rewards.yml`
* **Nuove**

  * `StaffEventKind` (+ `TOWN_CONTRACT`)
  * `TownContractService`
  * `TownContractRepository`
  * `TownContractSelectionDialogService`
  * `TownContractScoringService`
  * `VaultDeliveryContractListener`

---

## Sezione 5. Nuovi event kind proposti

### 5.1 `TERRITORIAL_SNAPSHOT`

**Problema che risolve**

* Eventi su:

  * espansione confini
  * outpost
  * rete territoriale
  * road footprint territoriale
    non sono ben modellabili come semplici objective counters.

**Perché i kind attuali non bastano**

* `AUTO_RACE` presuppone un set obiettivi uniforme e progressivo.
* Qui serve:

  * baseline per città
  * final snapshot
  * delta netto
  * regole di tenuta temporale
* `JUDGED_BUILD` non c’entra.

**Struttura dominio minima**

* `StaffEvent.kind = TERRITORIAL_SNAPSHOT`
* payload:

  * `scoringMode`
  * `minimumHoldDuration`
  * `eligibleWorlds`
  * `allowInfill`
  * `rewardPreset`

**Tabelle/store minimi**

* `staff_event_town_snapshot`
* opzionale `staff_event_town_claim_delta`

**Service minimi**

* `TerritorialSnapshotService`
* `TerritorialSnapshotRepository`
* `TerritorialScoringService`
* `TownClaimChangeListener`

**GUI minime**

* card leaderboard
* dialog con:

  * net gain
  * valid held claims
  * rank

**Comandi minimi**

* `create territory`
* `start`
* `close`
* `publish-results`

**Casi d’uso ideali**

* Espansione dei Confini
* Rete di Avamposti
* Grande Strada Reale, se poi aggiungi scoring footprint

**Perché vale la pena**

* È il kind più distintivo per un plugin città.
* Non replica minigame esterni.
* Premia governance territoriale reale.

---

### 5.2 `TOWN_CONTRACT`

**Problema che risolve**

* Eventi in cui ogni città:

  * sceglie 1 contratto tra più opzioni
  * ha manifest dedicato
  * progredisce su linee merce personalizzate

**Perché i kind attuali non bastano**

* `AUTO_RACE` non modella bene:

  * scelta del contratto
  * lock dopo scelta
  * progress per-città diverso
* `JUDGED_BUILD` irrilevante.

**Struttura dominio minima**

* `StaffEvent.kind = TOWN_CONTRACT`
* payload:

  * `contracts[]`
  * ogni contract:

    * `key`
    * `title`
    * `lines[]`
    * `rewardWeight`
    * `difficultyBand`

**Tabelle/store minimi**

* `staff_event_town_contract`
* `staff_event_town_contract_line`

**Service minimi**

* `TownContractService`
* `TownContractRepository`
* `TownContractScoringService`
* `VaultDeliveryContractListener`

**GUI minime**

* dialog con:

  * elenco contratti
  * conferma scelta
  * progress line-by-line
  * rank

**Comandi minimi**

* `create contract`
* `start`
* `close`
* opzionale `force-select <eventId> <town> <contractKey>`

**Casi d’uso ideali**

* Contratti della Corona
* Piano Carovane del Nether
* Rotta dei Porti

**Perché vale la pena**

* Introduce strategia per città senza uscire dal survival.
* Riusa il vault, che è già core del plugin.
* Dà identità agli eventi senza richiedere moderazione continua.

---

### 5.3 Kind che **non** introdurrei ora

**Non introdurrei subito un generic `JUDGED_SUBMISSION`**

* Per harbor, market, farm district, city gate, `JUDGED_BUILD` basta ancora.
* Il vero gap non è “tipo di review”, ma:

  * rubric payload più ricca
  * azioni dialog migliori
  * template catalog

Quindi:

* prima **estendi `JUDGED_BUILD`**
* dopo, solo se emergono submission non-build reali e frequenti, valuti un kind nuovo

---

## Sezione 6. Proposta di roadmap tecnica

### Fase 1

**Obiettivi**

* quick wins
* massimo riuso del motore esistente
* zero nuovi kind

**Deliverable**

* catalogo preset `AUTO_RACE`

  * Convoglio del Vault Imperiale
  * Raccolto di Stagione
  * Spedizione Oceanica
  * Frontiera del Nether
  * Difesa Civica
* catalogo preset `JUDGED_BUILD`

  * Porta della Città
  * Mercato Cittadino
  * Quartiere Agricolo Modello
* card/dialog staff event più leggibili in `RACES`
* template registry + texts/rewards preset

**Classi toccate**

* `StaffEventService`
* `StaffEventRepository`
* `CityChallengeService`
* `CityChallengeGuiService`
* `ChallengeNarrativeFormatter`
* `CityChallengeSettings`
* `challenge-texts.yml`
* `challenge-rewards.yml`
* nuove:

  * `StaffEventTemplateCatalog`
  * `StaffEventTemplateKey`

**Rischio principale**

* proliferazione preset hardcodati male

**Come testarla**

* staging server
* 1 evento auto per famiglia
* 1 judged build end-to-end
* verificare:

  * card
  * dialog
  * start/close
  * ranking
  * reward
  * redeem safety

---

### Fase 2

**Obiettivi**

* eventi più ricchi
* medio sforzo
* aumentare varietà senza rivoluzionare il dominio

**Deliverable**

* `Fiera dei Mestieri` versione migliorata
* rubric configurabili per `JUDGED_BUILD`
* action dialog:

  * aggiorna nota
  * ritira submission
* opzionale nuovo objective type:

  * `UNIQUE_TRADE_PROFESSION_COUNT`
* primo `TOWN_CONTRACT`

**Classi toccate**

* tutto il set fase 1
* `ChallengeObjectiveType`
* `CityChallengeService`
* nuove:

  * `TownContractService`
  * `TownContractRepository`
  * `TownContractSelectionDialogService`
  * `TradeFairScoringService`

**Rischio principale**

* complexity creep in GUI/dialog

**Come testarla**

* test su:

  * città senza contratto
  * città che seleziona contratto
  * first delivery lock
  * leaderboard finale
  * cancel/close/publish results

---

### Fase 3

**Obiettivi**

* nuovi kind forti
* payoff alto
* refactor più grosso

**Deliverable**

* `TERRITORIAL_SNAPSHOT`
* `Espansione dei Confini`
* base per `Rete di Avamposti`
* eventuale refactor comune `StaffEventRuntimeService`

**Classi toccate**

* `StaffEventService`
* `StaffEventRepository`
* `CityChallengeGuiService`
* nuove:

  * `TerritorialSnapshotService`
  * `TerritorialSnapshotRepository`
  * `TerritorialScoringService`
  * `TownClaimChangeListener`
  * `StaffEventKind` update

**Rischio principale**

* exploit claim flip / edge cases territoriali

**Come testarla**

* suite di scenari manuali:

  * claim gain netto
  * claim gain poi remove
  * flip rapido
  * chunk mantenuto oltre soglia
  * chiusura evento con città offline
  * reward idempotence

---

## Sezione 7. Appendice “quasi ticket”

### Ticket 1 — Preset AUTO_RACE `Convoglio del Vault Imperiale`

**Scopo**

* introdurre un preset evento staff basato su consegne nel vault città

**Regole**

* evento competitivo per città
* progress su materiali themed
* ranking per punteggio totale
* reward auto a fine evento

**Backend**

* nuovo `templateKey = VAULT_CONVOY`
* mapping preset -> objective bundle
* riuso `linkedChallengeInstanceId`

**GUI/dialog**

* card in `RACES`
* dialog con materiali, rank, reward, timer

**Permessi**

* staff: `cittaexp.staff.events.manage`
* player: nessuno nuovo

**Acceptance criteria**

* staff crea evento da preset
* evento parte e chiude correttamente
* le consegne nel vault incrementano il progress
* leaderboard si aggiorna
* reward finale assegnato una sola volta

**Edge cases**

* città sciolta durante evento
* materiale depositato e poi ritirato
* close manuale prima del termine
* reward lost in vault -> redeem

---

### Ticket 2 — Preset AUTO_RACE `Fiera dei Mestieri`

**Scopo**

* creare evento commercio/artigianato/alchimia

**Regole**

* score composito da trade + craft + brew
* variante base senza diversity
* variante futura con diversity

**Backend**

* preset `TRADE_FAIR`
* profilo `BASIC`
* profilo `DIVERSIFIED` feature-flagged

**GUI/dialog**

* sezioni separate Mestieri / Artigianato / Alchimia

**Permessi**

* staff manage
* player nessuno nuovo

**Acceptance criteria**

* trade, craft e brew contribuiscono al progress
* card e dialog mostrano score composito
* preset base non richiede nuovi listener
* preset diversified resta disattivabile

**Edge cases**

* spam trade su singolo villager
* craft massivo da autocrafter se presente nel server meta
* potion brew interrotte o inventory state inconsistente

---

### Ticket 3 — Preset JUDGED_BUILD `Porta della Città`

**Scopo**

* introdurre contest build staff-guided per ingressi cittadini

**Regole**

* una submission attiva per città
* submission valida solo dentro claim città
* review manuale staff
* placement 1/2/3

**Backend**

* riuso `JUDGED_BUILD`
* payload rubric `CITY_GATE`
* update dialog actions per submit/update/withdraw

**GUI/dialog**

* card evento in `RACES`
* dialog con rubric e CTA `Invia candidatura`

**Permessi**

* staff manage/review/reward
* città: `CITY_EVENTS_SUBMIT`

**Acceptance criteria**

* città può inviare submission con nota
* staff può listare, revieware, piazzare, premiare
* publish results mostra i vincitori
* rewardGranted previene doppia reward

**Edge cases**

* player submit fuori claim
* città cambia claim ownership dopo submit
* submission aggiornata a fine timer
* placement assegnato a città senza review accettata

---

### Ticket 4 — Nuovo kind `TOWN_CONTRACT`

**Scopo**

* introdurre eventi in cui ogni città sceglie un contratto diverso

**Regole**

* la città seleziona 1 contratto tra N
* dopo la prima consegna il contratto è lockato
* progress line-by-line
* ranking per completamento/punteggio

**Backend**

* nuovo `StaffEventKind.TOWN_CONTRACT`
* repository per selection + lines
* listener su vault ledger

**GUI/dialog**

* dialog con lista contratti e progress del contratto scelto

**Permessi**

* staff manage
* città: riuso `CITY_EVENTS_SUBMIT` oppure nuovo `CITY_EVENTS_CONTRACT_SELECT`

**Acceptance criteria**

* città può selezionare un contratto una sola volta
* le consegne corrette incrementano solo le linee del contratto scelto
* ranking finale calcolato correttamente
* reward non duplicata

**Edge cases**

* città tenta di cambiare contratto dopo prima consegna
* close evento con contratto incompleto
* consegna oltre target
* cancel evento con contratti già selezionati

---

### Ticket 5 — Nuovo kind `TERRITORIAL_SNAPSHOT`

**Scopo**

* introdurre eventi basati su crescita reale del territorio città

**Regole**

* baseline al start
* final snapshot alla chiusura
* score su net claim gain
* chunk contano solo se mantenuti oltre soglia minima

**Backend**

* nuovo `StaffEventKind.TERRITORIAL_SNAPSHOT`
* snapshot repository
* hook claim add/remove
* scoring service

**GUI/dialog**

* card con `+claim netti`
* dialog con regole e classifica

**Permessi**

* staff manage
* player nessuno nuovo

**Acceptance criteria**

* il sistema salva baseline e final snapshot per tutte le città partecipanti
* claim gain netto viene calcolato correttamente
* flip claim/remove rapido non genera score valido
* ranking e reward finali sono coerenti

**Edge cases**

* città creata o sciolta durante evento
* chunk claimato e perso prima della min hold duration
* restart server durante evento
* chiusura manuale anticipata

---

### Chiusura netta

La mia raccomandazione è:

* **Fase 1**: spingi forte su preset `AUTO_RACE` + `JUDGED_BUILD`
* **Fase 2**: aggiungi `TOWN_CONTRACT`
* **Fase 3**: introduci `TERRITORIAL_SNAPSHOT`

Perché:

* ottieni valore subito
* resti coerente col sistema quest esistente
* non forzi il motore a fare cose per cui non è stato modellato
* i due nuovi kind che propongo hanno un motivo reale, non estetico

Se vuoi, nel prossimo passaggio posso trasformare questa proposta in un **design doc tecnico interno**, con:

* schema classi
* payload JSON example
* enum/prototype Java
* command tree Brigadier
* acceptance tests per service.

[1]: https://jd.papermc.io/paper/1.21.11/org/bukkit/event/block/BlockBreakEvent.html?utm_source=chatgpt.com "BlockBreakEvent (paper-api 1.21.11-R0.1-SNAPSHOT API)"
[2]: https://jd.papermc.io/paper/1.21.11/deprecated-list.html?utm_source=chatgpt.com "Deprecated List (paper-api 1.21.11-R0.1-SNAPSHOT API)"
[3]: https://jd.papermc.io/paper/1.21.11/org/bukkit/event/entity/VillagerAcquireTradeEvent.html?utm_source=chatgpt.com "VillagerAcquireTradeEvent (paper-api 1.21.11- ..."
[4]: https://jd.papermc.io/paper/1.21.11/org/bukkit/event/player/PlayerFishEvent.html?utm_source=chatgpt.com "PlayerFishEvent (paper-api 1.21.11-R0.1-SNAPSHOT API)"
