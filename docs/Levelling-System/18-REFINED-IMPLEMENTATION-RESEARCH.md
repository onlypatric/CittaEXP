## Sezione 1. Mappa finale dei contenuti evento

### `AUTO_RACE`

| Evento                        | Valore gameplay | Costo tecnico | Rischio exploit | Priorità consigliata |
| ----------------------------- | --------------: | ------------: | --------------: | -------------------: |
| Convoglio del Vault Imperiale |            alto |         basso |           medio |                   P1 |
| Raccolto di Stagione          |            alto |         basso |           basso |                   P1 |
| Fiera dei Mestieri            |            alto |   basso/medio |           medio |                   P1 |
| Spedizione Oceanica           |            alto |         basso |           basso |                   P1 |
| Frontiera del Nether          |            alto |         basso |           medio |                   P2 |
| Bonifica delle Gallerie       |            alto |         basso |           medio |                   P2 |
| Difesa Civica                 |      medio/alto |         medio |           medio |                   P2 |
| Cantiere Civico Lampo         |           medio |   basso/medio |            alto |                   P3 |

### `JUDGED_BUILD`

| Evento                         | Valore gameplay | Costo tecnico | Rischio exploit | Priorità consigliata |
| ------------------------------ | --------------: | ------------: | --------------: | -------------------: |
| Porta della Città              |            alto |         basso |           basso |                   P1 |
| Mercato Cittadino              |            alto |         basso |           basso |                   P1 |
| Quartiere Agricolo Modello     |            alto |         basso |           basso |                   P2 |
| Distretto Industriale Ordinato |      medio/alto |         basso |           basso |                   P2 |

### `nuovo kind richiesto`

| Evento                    | Kind consigliato              | Valore gameplay | Costo tecnico | Rischio exploit | Priorità consigliata |
| ------------------------- | ----------------------------- | --------------: | ------------: | --------------: | -------------------: |
| Espansione dei Confini    | `TERRITORIAL_SNAPSHOT`        |       altissimo |    medio/alto |            alto |                   P2 |
| Grande Strada Reale       | `TERRITORIAL_SNAPSHOT` esteso |            alto |          alto |            alto |                   P3 |
| Rete di Avamposti         | `TERRITORIAL_SNAPSHOT` esteso |            alto |          alto |            alto |                   P3 |
| Contratti della Corona    | `TOWN_CONTRACT`               |       altissimo |         medio |           medio |                   P2 |
| Piano Carovane del Nether | `TOWN_CONTRACT`               |            alto |         medio |           medio |                   P3 |
| Rotta dei Porti           | `TOWN_CONTRACT`               |            alto |         medio |           medio |                   P3 |

### Sintesi finale

La prima wave più implementabile è:

1. `Convoglio del Vault Imperiale`
2. `Raccolto di Stagione`
3. `Fiera dei Mestieri`
4. `Spedizione Oceanica`
5. `Porta della Città`

Questi 5 coprono i pattern migliori del subsystem:

* wrapper competitivo puro
* mix di objective già esistenti
* judged submission/review
* nessun nuovo dominio pesante

---

## Sezione 2. Hook tecnici necessari per ciascun tipo evento

### 2.1 Vault / logistica

Eventi: `Convoglio del Vault Imperiale`, base di `Contratti della Corona`

**Tracking richiesto**

* deposito materiali nel vault città
* progress per città su linee item/material
* opzionale cap per materiale dominante

**Objective types esistenti che bastano**

* `VAULT_DELIVERY`
* `RESOURCE_CONTRIBUTION`

**Riusa hook già comuni del plugin**

* **Sì, e dovrebbe essere la source of truth.**
  Se il vault città ha già un ledger transazionale interno, il tracking deve partire da lì, non da Paper inventory events. Questo evita falsi positivi da drag, hopper, rollback, withdraw/redeposit e storage intermedi.

**Serve nuovo listener**

* **No**, se il vault ledger già emette eventi/callback interne.
* **Solo opzionale** `org.bukkit.event.inventory.InventoryMoveItemEvent` se il vault è fisicamente un’inventory Paper/Bukkit e vuoi osservare movimenti automatici; ma questo evento vede solo trasferimenti raw tra inventory, tipicamente hopper o spostamenti diretti, non il concetto di “consegna valida a città”. ([PaperMC][1])

**Serve nuovo dominio/store**

* `AUTO_RACE`: no
* `TOWN_CONTRACT`: sì, perché ogni città deve poter scegliere 1 contratto, lockarlo e tracciare linee diverse

**Rischi di tracking ambiguo**

* depositi seguiti da ritiro
* materiali già stockpiled
* loop hopper
* consegna fatta fuori dal vault ufficiale

**Decisione tecnica**

* Per `AUTO_RACE`, usa **solo** eventi/plugin hook del vault interno.
* Per `TOWN_CONTRACT`, aggiungi uno store per `town_contract_selection` e `town_contract_progress_line`; Paper resta accessorio, non centrale.

---

### 2.2 Mining / costruzione

Eventi: `Bonifica delle Gallerie`, `Cantiere Civico Lampo`

**Tracking richiesto**

* blocchi minati
* blocchi piazzati
* opzionale distinzione per materiale/focus
* opzionale validazione claim-only

**Objective types esistenti che bastano**

* `BLOCK_MINE`
* `PLACE_BLOCK`
* `CONSTRUCTION`
* `REDSTONE_AUTOMATION`

**Riusa hook già comuni del plugin**

* se il motore challenge già traccia mine/place, basta riusarlo

**Serve nuovo listener**

* `org.bukkit.event.block.BlockBreakEvent` per mining player-originated: è l’hook base corretto per conteggiare rotture reali da player. La classe espone il player, il blocco e la semantica di drop/exp. ([PaperMC][2])
* `org.bukkit.event.block.BlockPlaceEvent` per place: è l’hook base per placement reale da player. ([PaperMC][3])

**Serve nuovo dominio/store**

* no

**Rischi di tracking ambiguo**

* block place + instant break farming
* materiali cheap spam
* macchine/redstone che alterano blocchi senza player action
* build fuori claim se l’evento dovrebbe essere urbano

**Decisione tecnica**

* `BLOCK_MINE`: conteggia solo `BlockBreakEvent` con player valido e regole anti-abuse esistenti.
* `PLACE_BLOCK`/`CONSTRUCTION`: conteggia solo `BlockPlaceEvent`, idealmente con whitelist materiali e filtro world/claim.
* `REDSTONE_AUTOMATION`: non tentare una metrica “generica redstone noise”. Mantienila o come objective già esistente del plugin, o come sottoinsieme ben definito di blocchi placed/built; non c’è una singola Paper API elegante che misuri “automazione” come concetto.

---

### 2.3 Farming / animali

Eventi: `Raccolto di Stagione`, parti di `Quartiere Agricolo Modello`

**Tracking richiesto**

* raccolta crop
* ecosistema agricolo
* craft cibo
* opzionale interazioni animali

**Objective types esistenti che bastano**

* `CROP_HARVEST`
* `FARMING_ECOSYSTEM`
* `FOOD_CRAFT`
* `ANIMAL_INTERACTION`

**Riusa hook già comuni del plugin**

* sì, soprattutto per `CROP_HARVEST` se hai già normalizzazione maturità crop

**Serve nuovo listener**

* per il lato crop, di solito basta il listener già usato da `BLOCK_MINE`/harvest interno; per Paper il blocco base resta `BlockBreakEvent` se il raccolto avviene con break player-originated. ([PaperMC][2])
* per craft cibo: `org.bukkit.event.inventory.CraftItemEvent`, che viene chiamato quando una recipe è completata nella crafting matrix. ([PaperMC][4])
* per lato animali:

  * `org.bukkit.event.player.PlayerInteractEntityEvent` per feed/interazione player->entity ([PaperMC][5])
  * `org.bukkit.event.entity.EntityBreedEvent` per breeding completato ([PaperMC][6])

**Serve nuovo dominio/store**

* no

**Rischi di tracking ambiguo**

* crop rotti non maturi
* farm massivi troppo efficienti
* animal spam con loop minimali
* crafting via sistemi non player-driven, se il server usa automazioni custom

**Decisione tecnica**

* `Raccolto di Stagione` funziona bene come punteggio composito:

  * harvest
  * craft food
  * opzionale breeding/interactions
* `FARMING_ECOSYSTEM` deve restare una logica plugin-level composita, non un singolo listener Paper.

---

### 2.4 Trade / crafting / brewing

Eventi: `Fiera dei Mestieri`

**Tracking richiesto**

* trade effettivamente completati
* craft
* brew
* opzionale diversity professioni/merchant

**Objective types esistenti che bastano**

* `VILLAGER_TRADE`
* `ITEM_CRAFT`
* `BREW_POTION`

**Riusa hook già comuni del plugin**

* sì per `ITEM_CRAFT` e `BREW_POTION` se già presenti

**Serve nuovo listener**

* per il trade completato usa **`io.papermc.paper.event.player.PlayerTradeEvent`**, non `VillagerAcquireTradeEvent`: `PlayerTradeEvent` è l’evento corretto quando un player commercia con villager o wandering trader. ([PaperMC][7])
* `org.bukkit.event.inventory.CraftItemEvent` per item craft ([PaperMC][4])
* `org.bukkit.event.inventory.BrewEvent` per brewing completato ([PaperMC][8])
* `org.bukkit.event.entity.VillagerAcquireTradeEvent` è utile solo se vuoi osservare quando un villager acquisisce una nuova trade, non per conteggiare una transazione player completata. ([PaperMC][9])

**Serve nuovo dominio/store**

* no per versione base
* sì opzionale solo se vuoi `diversity scoring` persistente per professione/recipe

**Rischi di tracking ambiguo**

* spam su un solo trade
* village industrializzati dominano troppo
* crafting side-channel fuori tema
* brew duplicato o exploit inventory se fai tracking nel punto sbagliato

**Decisione tecnica**

* Versione P1: score composito `PlayerTradeEvent + CraftItemEvent + BrewEvent`
* Versione P2: aggiungi scorer plugin-level `uniqueProfessionCount` o `uniqueRecipeBands`

---

### 2.5 Fishing / oceano / esplorazione

Eventi: `Spedizione Oceanica`, parti di `Rotta dei Porti`

**Tracking richiesto**

* pesca riuscita
* ocean activity
* structure discovery / loot
* opzionale travel

**Objective types esistenti che bastano**

* `FISH_CATCH`
* `OCEAN_ACTIVITY`
* `STRUCTURE_DISCOVERY`
* `STRUCTURE_LOOT`

**Riusa hook già comuni del plugin**

* sì per ocean activity e structure logic, se già presenti

**Serve nuovo listener**

* `org.bukkit.event.player.PlayerFishEvent`: espone `getState()` e `getCaught()`; è il gancio Paper corretto per distinguere un catch valido e l’item/entity catturato. ([PaperMC][10])

**Serve nuovo dominio/store**

* no

**Rischi di tracking ambiguo**

* structure discovery non ha un evento Paper “player discovered structure X”
* locate/map usage non coincide con discovery reale
* loot RNG troppo volatile

**Decisione tecnica**

* `FISH_CATCH`: sì, Paper event diretto
* `STRUCTURE_DISCOVERY`: **logica interna plugin**, basata su location/chunk/poi/registry lookup al primo ingresso o primo claim di scoperta
* `io.papermc.paper.event.world.StructuresLocateEvent` **non** è discovery; scatta quando vengono locate strutture via `/locate`, eye of ender, mappe, delfini o API `World.locateNearestStructure(...)`. Quindi è al massimo un hook ausiliario/anti-cheat, non la base del tracking. ([PaperMC][11])
* `PlayerAdvancementDoneEvent` può essere usato solo come segnale debole/ausiliario per alcuni advancement exploration-related; non è un sostituto di una struttura-discovery engine. Inoltre una delle sue constructor è già marcata deprecated for removal. ([PaperMC][12])

---

### 2.6 Nether / travel / distance

Eventi: `Frontiera del Nether`, `Piano Carovane del Nether`

**Tracking richiesto**

* ingresso/uscita dimensione
* attività nel Nether
* travel distance

**Objective types esistenti che bastano**

* `NETHER_ACTIVITY`
* `DIMENSION_TRAVEL`
* `TRANSPORT_DISTANCE`

**Riusa hook già comuni del plugin**

* sì per `NETHER_ACTIVITY` e distance se il plugin ha già sampler/accumulator

**Serve nuovo listener**

* `org.bukkit.event.player.PlayerPortalEvent` per il momento in cui il player sta per teletrasportarsi tramite portale ([PaperMC][13])
* `org.bukkit.event.player.PlayerChangedWorldEvent` per il cambio mondo effettivo dopo la transizione ([PaperMC][14])
* `org.bukkit.event.player.PlayerMoveEvent` solo se serve campionare movement/distance, ma non come source of truth ad alta frequenza per logiche complesse; usalo con throttling e accumulator plugin-level. `PlayerMoveEvent` è l’evento generico di movimento. ([PaperMC][15])

**Serve nuovo dominio/store**

* no

**Rischi di tracking ambiguo**

* portali bounce/spam
* movimento minimo farmato avanti/indietro
* chunk boundary / lag causing overcount

**Decisione tecnica**

* `DIMENSION_TRAVEL`: score solo su transizioni valide (`PlayerChangedWorldEvent`)
* `TRANSPORT_DISTANCE`: accumulatore plugin-level con sampling o delta location, non 1:1 su ogni move event
* `NETHER_ACTIVITY`: combinare presence + altre azioni, non solo “tempo nel Nether”

---

### 2.7 PvE / raid

Eventi: `Bonifica delle Gallerie`, `Difesa Civica`, parte di `Frontiera del Nether`

**Tracking richiesto**

* mob kill
* rare/boss kill
* raid completion
* XP pickup opzionale

**Objective types esistenti che bastano**

* `MOB_KILL`
* `RARE_MOB_KILL`
* `BOSS_KILL`
* `RAID_WIN`
* `XP_PICKUP`

**Riusa hook già comuni del plugin**

* sì per normalizzazione entity category e anti-farm

**Serve nuovo listener**

* `org.bukkit.event.entity.EntityDeathEvent`: espone l’entità morta, i drop e `DamageSource`; è il gancio base per attribuire kill/qualifica. ([PaperMC][16])
* `org.bukkit.event.raid.RaidFinishEvent`: viene chiamato quando un raid è completato con risultato chiaro ed espone `getWinners()`; attenzione che la lista non include eroi offline al termine. ([PaperMC][17])
* `org.bukkit.event.player.PlayerExpChangeEvent`: traccia cambi “naturali” di XP e ha sia `getAmount()` sia `getSource()`, utile per un `XP_PICKUP` più pulito. ([PaperMC][18])

**Serve nuovo dominio/store**

* no

**Rischi di tracking ambiguo**

* mob farm artificiali
* kill credit su danno ambientale
* raid finish attribuito a pochi online
* XP da fonti non desiderate

**Decisione tecnica**

* per `MOB_KILL`, usa `EntityDeathEvent` + attribution policy interna
* per `RAID_WIN`, usa `RaidFinishEvent`, ma il credit città deve essere plugin-level, non solo “winners list”
* per `XP_PICKUP`, filtra fonti con `getSource()` dove utile

---

### 2.8 GUI / dialog / input player-staff

Eventi: `JUDGED_BUILD`, scelta contratto, note submission

**Tracking richiesto**

* click GUI
* submit location/note
* review action
* eventuale input testuale

**Riusa hook già comuni del plugin**

* sì, la GUI inventario custom è la via più stabile

**Serve nuovo listener**

* `org.bukkit.event.inventory.InventoryClickEvent` per card/dialog/action buttons; la doc avverte che durante l’evento non tutte le operazioni inventory sono sicure, e per open/close/refresh bisogna schedulare al tick successivo. ([PaperMC][19])
* per input testuale:

  * **non** usare Conversation API: è deprecata for removal; Paper raccomanda `AsyncChatEvent` o `Dialog`. ([PaperMC][20])
  * `io.papermc.paper.dialog.Dialog` esiste e può essere creato a runtime, ma `Dialog.create(...)` è marcato `@Experimental`; quindi lo userei solo se vuoi investire in un flusso moderno e accetti churn API. ([PaperMC][21])

**Serve nuovo dominio/store**

* no per `JUDGED_BUILD`
* sì per `TOWN_CONTRACT`

**Rischi di tracking ambiguo**

* doppio submit
* GUI refresh race condition
* chat input intercettato fuori contesto

**Decisione tecnica**

* breve termine: inventory GUI + dialog lore + eventuale input via `AsyncChatEvent` scoped per player/sessione
* non usare Paper `Dialog` come base del subsystem quest finché vuoi massima stabilità API

---

### 2.9 Territoriali

Eventi: `Espansione dei Confini`, `Grande Strada Reale`, `Rete di Avamposti`

**Tracking richiesto**

* delta claim
* contiguità / nuclei / footprint stradale
* minimum hold duration
* snapshot baseline/final

**Objective types esistenti che bastano**

* nessuno, da soli

**Riusa hook già comuni del plugin**

* solo se il plugin città espone già hook interni su claim add/remove

**Serve nuovo listener**

* **non Paper**: serve hook interno del sistema claim/town, tipo:

  * `onTownClaimAdded`
  * `onTownClaimRemoved`
  * eventuale `onTownMerge`, `onTownDisband`

**Serve nuovo dominio/store**

* sì, obbligatorio:

  * snapshot baseline/final
  * delta records
  * scoring rules

**Rischi di tracking ambiguo**

* claim flip
* claim/remove/claim
* infill vs frontiera
* road fake footprint con spam block place

**Decisione tecnica**

* questi eventi sono guidati da **dominio plugin**, non da Paper API
* Paper serve solo per scheduling, GUI, command flow e review teleport utility

---

## Sezione 3. Paper API map

| Use case                          | API/evento/classe Paper                                                                                                           | Perché serve                                                                                                                                                                                                                                               | Rischio o limite                                                                 | Note implementative                         |
| --------------------------------- | --------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------- | ------------------------------------------- |
| Block break                       | `org.bukkit.event.block.BlockBreakEvent`                                                                                          | Hook standard per mining player-originated; espone player, block, exp/drop semantics. ([PaperMC][2])                                                                                                                                                       | Non copre automazioni o cambi block non causati da player                        | Base per `BLOCK_MINE`                       |
| Block place                       | `org.bukkit.event.block.BlockPlaceEvent`                                                                                          | Hook standard per placement player-originated. ([PaperMC][3])                                                                                                                                                                                              | Spam di blocchi cheap                                                            | Base per `PLACE_BLOCK` / `CONSTRUCTION`     |
| Inventory GUI clicks              | `org.bukkit.event.inventory.InventoryClickEvent`                                                                                  | Hook base per GUI custom; Paper avverte che open/close/refresh vanno schedulati next tick. ([PaperMC][19])                                                                                                                                                 | Race conditions se modifichi inventory dentro l’handler                          | Cancella evento e applica mutazioni dopo    |
| Hopper / raw inventory transfer   | `org.bukkit.event.inventory.InventoryMoveItemEvent`                                                                               | Utile solo se osservi movimenti raw fra inventory. ([PaperMC][1])                                                                                                                                                                                          | Non rappresenta “delivery valida a città”                                        | Non usarlo come source of truth del vault   |
| Villager trade completato         | `io.papermc.paper.event.player.PlayerTradeEvent`                                                                                  | Hook corretto per trade player-villager / wandering trader. ([PaperMC][7])                                                                                                                                                                                 | Se il punteggio richiede varietà, serve logic plugin aggiuntiva                  | Base migliore per `VILLAGER_TRADE`          |
| Villager acquisisce nuova trade   | `org.bukkit.event.entity.VillagerAcquireTradeEvent`                                                                               | Utile solo per setup/meta villager, non per trade completata. ([PaperMC][9])                                                                                                                                                                               | Sbagliato come source of truth per eventi commercio                              | Non usarlo per score principale             |
| Crafting                          | `org.bukkit.event.inventory.CraftItemEvent`                                                                                       | Trigger quando una recipe è completata nella crafting matrix. ([PaperMC][4])                                                                                                                                                                               | Non copre logiche custom non passate da crafting matrix standard                 | Base per `ITEM_CRAFT` / `FOOD_CRAFT`        |
| Brewing                           | `org.bukkit.event.inventory.BrewEvent`                                                                                            | Trigger a brewing completato. ([PaperMC][8])                                                                                                                                                                                                               | Se vuoi attribuire quantità esatte, serve normalizzare outputs                   | Base per `BREW_POTION`                      |
| Fishing                           | `org.bukkit.event.player.PlayerFishEvent`                                                                                         | Espone state e caught item/entity. ([PaperMC][10])                                                                                                                                                                                                         | Devi filtrare stati non validi                                                   | Base per `FISH_CATCH`                       |
| XP pickup / gain                  | `org.bukkit.event.player.PlayerExpChangeEvent`                                                                                    | Espone amount e source dell’XP naturale. ([PaperMC][18])                                                                                                                                                                                                   | Non è “generic every XP mutation”, ma XP naturale                                | Buono per `XP_PICKUP`                       |
| Mob kill / PvE                    | `org.bukkit.event.entity.EntityDeathEvent`                                                                                        | Espone entità morta e `DamageSource`. ([PaperMC][16])                                                                                                                                                                                                      | Attribuzione kill resta logica plugin                                            | Base per `MOB_KILL`                         |
| Raid completion                   | `org.bukkit.event.raid.RaidFinishEvent`                                                                                           | Trigger a raid concluso con risultato chiaro; espone winners. ([PaperMC][17])                                                                                                                                                                              | Winners list non include heroes offline al termine                               | City credit va calcolato plugin-side        |
| Player move / distance            | `org.bukkit.event.player.PlayerMoveEvent`                                                                                         | Evento base per movement. ([PaperMC][15])                                                                                                                                                                                                                  | Molto frequente; overcount/perf se usato raw                                     | Usare sampler/throttling                    |
| Dimension travel (portal trigger) | `org.bukkit.event.player.PlayerPortalEvent`                                                                                       | Intercetta il teleport via portale prima della transizione. ([PaperMC][13])                                                                                                                                                                                | Non è cambio mondo concluso                                                      | Buono per pre-validation                    |
| Dimension travel (world switched) | `org.bukkit.event.player.PlayerChangedWorldEvent`                                                                                 | Conferma il cambio mondo effettivo. ([PaperMC][14])                                                                                                                                                                                                        | Nessun dettaglio sul portale originario                                          | Buono per score `DIMENSION_TRAVEL`          |
| Structure locate                  | `io.papermc.paper.event.world.StructuresLocateEvent`                                                                              | Intercetta locate/mappa/eye/API locate, non discovery reale. ([PaperMC][11])                                                                                                                                                                               | Non equivale a “giocatore ha scoperto struttura”                                 | Usarlo solo come hook ausiliario            |
| Advancement exploration signal    | `org.bukkit.event.player.PlayerAdvancementDoneEvent`                                                                              | Può dare segnali exploration-related. ([PaperMC][12])                                                                                                                                                                                                      | Non è mappa affidabile delle strutture; una constructor è deprecated for removal | Solo fallback debole                        |
| Entity interaction                | `org.bukkit.event.player.PlayerInteractEntityEvent`                                                                               | Base per feed/interact verso animali o entità. ([PaperMC][5])                                                                                                                                                                                              | Interazione non implica sempre progresso valido                                  | Usare con filtri                            |
| Breeding                          | `org.bukkit.event.entity.EntityBreedEvent`                                                                                        | Segnale chiaro di breeding completato. ([PaperMC][6])                                                                                                                                                                                                      | Serve normalizzare per specie/abuse                                              | Buono per animal objectives                 |
| Scheduling                        | `org.bukkit.scheduler.BukkitScheduler` / `BukkitRunnable.runTask*`                                                                | Serve per start/end eventi e refresh GUI deferred; la doc di InventoryClickEvent consiglia next-tick scheduling e `BukkitScheduler` indica che l’overload con `BukkitRunnable` è deprecated in favore di `BukkitRunnable.runTask(Plugin)`. ([PaperMC][19]) | Attenzione a task duplicati e idempotenza close/reward                           | Centralizza in `StaffEventSchedulerService` |
| Command registration              | `io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents.COMMANDS` + `io.papermc.paper.command.brigadier.Commands#register` | API moderna per registrare comandi Brigadier nel lifecycle Paper. ([PaperMC][22])                                                                                                                                                                          | Richiede command tree più formale                                                | Bene per nuovi subcommand eventi            |
| Text input moderno                | `io.papermc.paper.event.player.AsyncChatEvent`                                                                                    | Paper raccomanda questo approccio al posto della vecchia Conversation API. ([PaperMC][20])                                                                                                                                                                 | Evento può essere async o sync; controllare `isAsynchronous()`                   | Buono per note submission                   |
| Dialog system Paper               | `io.papermc.paper.dialog.Dialog` + `Audience.showDialog(...)`                                                                     | Esiste un dialog system nativo e i player, come Audience, ereditano `showDialog`. ([PaperMC][21])                                                                                                                                                          | `Dialog.create(...)` è experimental; keyed parts hanno deprecations              | Non lo metterei come base del subsystem ora |
| Staff review teleport             | `org.bukkit.entity.Entity#teleportAsync(...)`                                                                                     | Utile per TP staff verso submission/build senza sync chunk load sul thread chiamante. ([PaperMC][23])                                                                                                                                                      | Gestire future/completion e failure                                              | Comodo in review flow                       |

---

## Sezione 4. Deep dive sui 5 eventi migliori da implementare subito

### 4.1 `Convoglio del Vault Imperiale`

**1. design player-facing**

* Evento competitivo città vs città
* La città consegna categorie di materiali al vault
* Card leggibile: tema, materiali validi, posizione, tempo residuo

**2. lifecycle tecnico**

* `DRAFT -> PUBLISHED -> ACTIVE -> COMPLETED -> ARCHIVED`
* `publish` crea o collega la `ChallengeInstance`
* `start` marca attivo
* `close` congela ranking
* `publish-results` broadcasta top 3 e completatori

**3. dominio dati minimo**

* Riusa `StaffEvent`
* `payloadJson`:

  * `templateKey`
  * `themeKey`
  * `materialWeights`
  * `softCaps`
  * `rewardPreset`
* Nessun nuovo store se il vault ledger esiste già

**4. service da toccare**

* `StaffEventService`
* `CityChallengeService`
* nuovo `StaffEventTemplateCatalog`
* opzionale `VaultEventScoringService`

**5. repository/store da toccare**

* `StaffEventRepository`
* nessun nuovo repository obbligatorio

**6. GUI/dialog da toccare**

* `CityChallengeGuiService`
* dialog evento in `RACES`
* sezione “Obiettivo”, “Classifica”, “Ricompense”, “Tempo”

**7. comandi staff da usare o estendere**

* usare gli esistenti:

  * `create auto`
  * `publish`
  * `start`
  * `close`
  * `publish-results`
* estensione utile:

  * preset templates, es. `create auto vault_convoy`

**8. Paper API necessarie**

* Nessuna come source of truth per il punteggio
* Solo opzionale `InventoryMoveItemEvent` se vuoi osservare movimenti raw del contenitore; non come base del tracking. ([PaperMC][1])
* GUI custom via `InventoryClickEvent` con next-tick scheduling quando fai refresh/open. ([PaperMC][19])

**9. acceptance criteria**

* una consegna valida al vault incrementa il punteggio città
* withdraw/redeposit non duplica score
* close evento blocca progress ulteriore
* reward finale è idempotente

**10. edge cases**

* città sciolta durante evento
* ledger replay dopo restart
* materiali non nel manifest
* reward lost in vault -> `/city redeem`

---

### 4.2 `Raccolto di Stagione`

**1. design player-facing**

* Evento cooperativo/competitivo basato su produzione agricola reale
* Punteggio composito, non solo crop count
* Perfetto per città early-mid

**2. lifecycle tecnico**

* `AUTO_RACE` standard
* finestra 24h o weekend

**3. dominio dati minimo**

* solo `StaffEvent.payloadJson` con:

  * `harvestWeights`
  * `foodCraftWeights`
  * opzionale `animalWeights`

**4. service da toccare**

* `StaffEventService`
* `CityChallengeService`
* `ChallengeNarrativeFormatter`

**5. repository/store da toccare**

* nessuno nuovo

**6. GUI/dialog da toccare**

* card evento
* dialog con breakdown:

  * raccolto
  * cibo
  * allevamento opzionale

**7. comandi staff da usare o estendere**

* `create auto harvest_festival`
* `publish`
* `start`
* `close`

**8. Paper API necessarie**

* `BlockBreakEvent` per harvest player-originated. ([PaperMC][2])
* `CraftItemEvent` per `FOOD_CRAFT`. ([PaperMC][4])
* `PlayerInteractEntityEvent` e `EntityBreedEvent` se vuoi includere `ANIMAL_INTERACTION`/breeding. ([PaperMC][5])

**9. acceptance criteria**

* raccolto maturo valido incrementa il progress
* craft di cibo valido incrementa il progress
* score finale aggrega le categorie con pesi configurabili

**10. edge cases**

* crop non maturi
* animal feed spam senza breeding
* craft shift-click massivi
* automazioni server custom

---

### 4.3 `Fiera dei Mestieri`

**1. design player-facing**

* Evento città su commercio, craft, alchimia
* Tema: botteghe e professioni attive
* Board con score composito

**2. lifecycle tecnico**

* `AUTO_RACE`
* 24h o 48h

**3. dominio dati minimo**

* `payloadJson`:

  * `tradeWeight`
  * `craftWeight`
  * `brewWeight`
  * `scoringProfile: BASIC | DIVERSIFIED`

**4. service da toccare**

* `StaffEventService`
* `CityChallengeService`
* opzionale `TradeFairScoringService`

**5. repository/store da toccare**

* nessuno nuovo per `BASIC`
* opzionale store leggero per diversity per professione/recipe

**6. GUI/dialog da toccare**

* dialog con 3 sezioni:

  * Mestieri
  * Artigianato
  * Alchimia

**7. comandi staff da usare o estendere**

* `create auto trade_fair`
* `publish`
* `start`
* `close`

**8. Paper API necessarie**

* `PlayerTradeEvent` per trade completati veri. ([PaperMC][7])
* `CraftItemEvent` per craft. ([PaperMC][4])
* `BrewEvent` per brewing completato. ([PaperMC][8])
* `VillagerAcquireTradeEvent` **non** è il gancio giusto per score principale. ([PaperMC][9])

**9. acceptance criteria**

* ogni trade completato incrementa il punteggio
* craft e brew entrano nello stesso score composito
* l’evento si può creare solo da preset, senza nuovo dominio

**10. edge cases**

* spam di un solo trade
* villager zombified/recycled loop
* brewing interrotto
* recipe modificate da altri plugin

---

### 4.4 `Spedizione Oceanica`

**1. design player-facing**

* Evento exploration-heavy:

  * pesca
  * attività oceanica
  * loot
  * discovery

**2. lifecycle tecnico**

* `AUTO_RACE`
* ideale weekend 48h

**3. dominio dati minimo**

* `payloadJson`:

  * `profile: COASTAL | DEEP_SEA`
  * `lootWeights`
  * `discoveryWeights`

**4. service da toccare**

* `StaffEventService`
* `CityChallengeService`
* `ChallengeNarrativeFormatter`
* eventuale `StructureDiscoveryService` se già esiste

**5. repository/store da toccare**

* nessuno nuovo se la discovery engine già esiste
* opzionale store dedupe per “prima scoperta città”

**6. GUI/dialog da toccare**

* progress separato:

  * pesca
  * esplorazione
  * loot

**7. comandi staff da usare o estendere**

* `create auto ocean_expedition`
* `publish`
* `start`
* `close`

**8. Paper API necessarie**

* `PlayerFishEvent` per catch valido; usa `getState()` e `getCaught()`. ([PaperMC][10])
* per locate structures esiste `StructuresLocateEvent`, ma non va usato come discovery engine. ([PaperMC][11])
* `PlayerAdvancementDoneEvent` può essere solo un segnale debole e non sostituisce una detection plugin-level. ([PaperMC][12])

**9. acceptance criteria**

* pesca valida incrementa il progress
* structure discovery è deduplicata per città/struttura
* loot count è attribuito solo a fonti valide

**10. edge cases**

* same structure visitata da più player città
* locate command usato prima della visita reale
* loot RNG sbilanciato
* player fuori città che collabora ma non deve dare credit

---

### 4.5 `Porta della Città`

**1. design player-facing**

* Contest build judged
* una submission attiva per città
* submit dal punto fisico della build, con nota breve

**2. lifecycle tecnico**

* `DRAFT -> PUBLISHED -> ACTIVE -> REVIEW -> COMPLETED -> ARCHIVED`
* `ACTIVE`: build + submit
* `REVIEW`: sopralluogo staff e placement

**3. dominio dati minimo**

* Riusa:

  * `StaffEvent`
  * `StaffEventSubmission`
* `payloadJson` esteso:

  * `templateKey: CITY_GATE`
  * `rubric`
  * `noteMaxLength`
  * `allowResubmitUntil`

**4. service da toccare**

* `StaffEventService`
* `CityChallengeGuiService`
* nuovo `JudgedBuildTemplateCatalog`
* opzionale `SubmissionDialogService`

**5. repository/store da toccare**

* `StaffEventRepository`
* reuse `StaffEventSubmission`
* nessun nuovo store serio

**6. GUI/dialog da toccare**

* card evento in `RACES`
* dialog:

  * brief
  * rubric
  * reward placement
  * CTA `Invia candidatura`
* staff review GUI: lista submission, TP, accept/reject/place/reward

**7. comandi staff da usare o estendere**

* già bastano:

  * `create judged`
  * `submissions`
  * `review`
  * `place`
  * `reward`
  * `publish-results`

**8. Paper API necessarie**

* GUI custom via `InventoryClickEvent`; per open/close/refresh usa next-tick scheduling. ([PaperMC][19])
* per nota testuale:

  * evita Conversation API, che è deprecated for removal; meglio `AsyncChatEvent` con sessione temporanea scoped al player. ([PaperMC][20])
* Paper `Dialog` esiste ma lo terrei fuori dalla prima implementazione perché `Dialog.create(...)` è experimental. ([PaperMC][21])
* per TP staff alle submission: `Entity.teleportAsync(...)` è utile nel review flow. ([PaperMC][23])

**9. acceptance criteria**

* solo una submission attiva per città
* submit valida solo in claim città
* staff può revieware, assegnare placement, dare reward una sola volta

**10. edge cases**

* submit fuori claim
* città perde il claim dopo submit
* doppio click reward
* player chiude GUI mentre è in attesa nota

---

## Sezione 5. Nuovi kind evento

### 5.1 `TERRITORIAL_SNAPSHOT`

**Perché serve davvero**

* Gli eventi territoriali non sono “azioni contate”, ma **delta di stato città**:

  * claim net gain
  * frontiera mantenuta
  * nuclei/outpost
  * footprint strade

**Perché `AUTO_RACE` o `JUDGED_BUILD` non bastano**

* `AUTO_RACE` assume obiettivi uniformi e progressivi, non baseline/final state
* `JUDGED_BUILD` richiede review umana e non modella ranking automatico territoriale

**Shape minima del dominio**

* `StaffEvent.kind = TERRITORIAL_SNAPSHOT`
* runtime per città:

  * baseline snapshot
  * final snapshot
  * delta set
  * eligible held chunks
  * score

**Shape minima del payload**

```json
{
  "scoringMode": "NET_CLAIMS",
  "minimumHoldSeconds": 3600,
  "eligibleWorlds": ["world"],
  "allowInfill": true,
  "countOnlyClaimedAtClose": true,
  "rewardPreset": "territorial_weekend"
}
```

**Service minimi**

* `TerritorialSnapshotService`
* `TerritorialScoringService`
* `TerritorialSnapshotScheduler`
* `TownClaimChangeBridge`

**Repository minimi**

* `TerritorialSnapshotRepository`
* opzionale `TerritorialDeltaRepository`

**GUI/dialog minimi**

* card in `RACES`
* dialog:

  * regole scoring
  * net gain
  * frontier held
  * leaderboard

**Comandi staff minimi**

* `create territory`
* `publish`
* `start`
* `close`
* `publish-results`

**Hook Paper API pertinenti**

* **Nessun hook Paper risolve i claim**: la source of truth deve essere il subsystem città/claim interno.
* Paper serve per:

  * scheduling start/end: `BukkitScheduler` / `BukkitRunnable` ([PaperMC][24])
  * GUI/actions: `InventoryClickEvent` ([PaperMC][19])
  * staff TP review/debug: `teleportAsync` opzionale ([PaperMC][23])

**Rischi principali**

* claim flip exploit
* claim/remove/claim spam
* regole poco chiare su infill/frontiera
* restart server a metà evento

**Decisione netta**

* introdurlo solo dopo aver formalizzato:

  * claim delta event interno
  * snapshot idempotenti
  * score su net gain + hold duration

---

### 5.2 `TOWN_CONTRACT`

**Perché serve davvero**

* Qui il cuore è la **scelta per-città**:

  * ogni città prende 1 contratto
  * ogni contratto ha linee merce diverse
  * il progress non è uguale per tutte le città

**Perché `AUTO_RACE` o `JUDGED_BUILD` non bastano**

* `AUTO_RACE` non modella:

  * accept contract
  * lock contract
  * progress line-by-line diverso per città
* `JUDGED_BUILD` è fuori tema

**Shape minima del dominio**

* `StaffEvent.kind = TOWN_CONTRACT`
* runtime per città:

  * selected contract
  * selection status
  * line progress
  * completedAt
  * score

**Shape minima del payload**

```json
{
  "contracts": [
    {
      "key": "stone_iron_supply",
      "title": "Fornitura Muraria",
      "lines": [
        {"material": "STONE", "amount": 4096, "weight": 1.0},
        {"material": "IRON_INGOT", "amount": 512, "weight": 3.0}
      ]
    },
    {
      "key": "food_textiles",
      "title": "Rifornimento Civile",
      "lines": [
        {"material": "BREAD", "amount": 768, "weight": 2.0},
        {"material": "WHITE_WOOL", "amount": 1024, "weight": 1.5}
      ]
    }
  ],
  "lockOnFirstDelivery": true,
  "rewardPreset": "contract_weekend"
}
```

**Service minimi**

* `TownContractService`
* `TownContractSelectionService`
* `TownContractScoringService`
* `TownContractVaultBridge`

**Repository minimi**

* `TownContractRepository`
* `TownContractProgressRepository`

**GUI/dialog minimi**

* card in `RACES`
* dialog:

  * elenco contratti
  * conferma scelta
  * progress line-by-line
  * stato lockato / completato
  * rank

**Comandi staff minimi**

* `create contract`
* `publish`
* `start`
* `close`
* opzionale `force-select`

**Hook Paper API pertinenti**

* Ancora una volta, la source of truth dev’essere il vault ledger interno.
* Paper può aiutare solo in 2 punti:

  * GUI custom: `InventoryClickEvent` ([PaperMC][19])
  * se il vault è inventory-driven, `InventoryMoveItemEvent` è un hook raw ma non basta per il significato di consegna valida. ([PaperMC][1])

**Rischi principali**

* città sceglie male e resta bloccata
* exploit redeposit
* UI poco chiara nella selezione
* contratti sbilanciati

**Decisione netta**

* vale la pena introdurlo prima di `TERRITORIAL_SNAPSHOT`, perché:

  * riusa meglio il core vault
  * crea strategia reale
  * ha rischio inferiore
  * costa meno di un dominio claim-delta

---

## Sezione 6. Gap analysis implementativa

### 6.1 Cose già realizzabili quasi subito con l’architettura attuale

| Gap / tema                                              | Gravità | Sforzo | Workaround temporaneo                                      |
| ------------------------------------------------------- | ------: | -----: | ---------------------------------------------------------- |
| Preset `AUTO_RACE` riusando `linkedChallengeInstanceId` |   bassa |  basso | hardcode preset + config texts/rewards                     |
| Preset `JUDGED_BUILD` a tema                            |   bassa |  basso | solo payload template + rubric                             |
| Card/dialog migliori in tab `RACES`                     |   media |  basso | estendere `CityChallengeGuiService` senza cambiare dominio |
| Publish/start/close scheduler staff events              |   media |  basso | riuso servizi esistenti + centralizzazione task ids        |

### 6.2 Cose che richiedono refactor medio

| Gap / tema                                 | Gravità | Sforzo | Workaround temporaneo                                            |
| ------------------------------------------ | ------: | -----: | ---------------------------------------------------------------- |
| Template catalog per eventi staff          |   media |  medio | switch hardcoded nei command handler                             |
| Input note submission robusto              |   media |  medio | `AsyncChatEvent` scoped per player/sessione                      |
| `Fiera dei Mestieri` con diversity scoring |   media |  medio | versione `BASIC` senza diversity                                 |
| Dialog actions non più read-only           |   media |  medio | action buttons solo per staff events, non per tutte le challenge |

### 6.3 Cose che richiedono nuovo dominio serio

| Gap / tema                                       | Gravità |     Sforzo | Workaround temporaneo                                           |
| ------------------------------------------------ | ------: | ---------: | --------------------------------------------------------------- |
| `TOWN_CONTRACT`                                  |    alta |      medio | simulare male con auto race fissa, ma perdi scelta città        |
| `TERRITORIAL_SNAPSHOT`                           |    alta | medio/alto | nessun workaround pulito                                        |
| `Grande Strada Reale` con connettività/footprint |    alta |       alto | convertirlo in judged build e perdere l’automatismo             |
| `Rete di Avamposti`                              |    alta |       alto | trasformarla in contest build/claim manuale, poco soddisfacente |

### Conclusione della gap analysis

* **Subito**: auto race preset + judged build preset
* **Dopo poco**: dialog actions + template catalog + input flow pulito
* **Solo dopo**: nuovi kind veri

---

## Sezione 7. Piano pratico per un engineer

### Step 1: cosa implementare subito

**Deliverable**

* preset `AUTO_RACE`:

  * `vault_convoy`
  * `harvest_festival`
  * `trade_fair_basic`
  * `ocean_expedition`
* preset `JUDGED_BUILD`:

  * `city_gate`
  * `city_market`
* migliorie GUI staff event in `RACES`

**Classi probabili da toccare**

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
  * `AutoRaceTemplateFactory`
  * `JudgedBuildTemplateCatalog`

**API Paper rilevanti**

* `BlockBreakEvent`, `BlockPlaceEvent`, `CraftItemEvent`, `BrewEvent`, `PlayerTradeEvent`, `PlayerFishEvent`, `InventoryClickEvent`, `BukkitScheduler`/`BukkitRunnable` ([PaperMC][2])

**Rischio principale**

* preset spalmati in troppi punti hardcoded

**Test da fare**

* 1 evento auto end-to-end per famiglia
* 1 judged build con submit/review/reward
* test idempotenza reward
* test chiusura anticipata
* test GUI refresh safe after click

---

### Step 2: cosa implementare dopo

**Deliverable**

* `trade_fair_diversified`
* action dialog:

  * submit/update/withdraw
  * contract preview scaffold
* input note robusto
* refactor command registration staff events

**Classi probabili da toccare**

* `CityChallengeGuiService`
* `StaffEventService`
* `StaffEventCommand*`
* nuove:

  * `SubmissionInputSessionService`
  * `TradeFairScoringService`

**API Paper rilevanti**

* `AsyncChatEvent` per input scoped ([PaperMC][20])
* `LifecycleEvents.COMMANDS` + `Commands.register(...)` per command tree moderno ([PaperMC][22])
* `InventoryClickEvent` per CTA dialog ([PaperMC][19])

**Rischio principale**

* flussi input/GUI troppo stati-centrati e fragili

**Test da fare**

* player interrompe input
* doppio submit concorrente
* restart durante sessione input
* command aliases / permission gates

---

### Step 3: cosa tenere come espansione

**Deliverable**

* `TOWN_CONTRACT`
* `TERRITORIAL_SNAPSHOT`
* eventi:

  * `Contratti della Corona`
  * `Espansione dei Confini`

**Classi probabili da toccare**

* `StaffEventKind`
* `StaffEventService`
* `StaffEventRepository`
* nuove:

  * `TownContractService`
  * `TownContractRepository`
  * `TownContractProgressRepository`
  * `TerritorialSnapshotService`
  * `TerritorialSnapshotRepository`
  * `TerritorialScoringService`
  * `TownClaimChangeBridge`

**API Paper rilevanti**

* soprattutto scheduling, GUI e utility:

  * `BukkitScheduler` / `BukkitRunnable` ([PaperMC][24])
  * `InventoryClickEvent` ([PaperMC][19])
  * `Entity.teleportAsync(...)` per review/admin tooling ([PaperMC][23])
* **Non esiste una Paper API che sostituisca il dominio claim o il vault ledger**

**Rischio principale**

* modellare come “listener aggiuntivi” qualcosa che in realtà richiede state machine + store

**Test da fare**

* snapshot baseline/final con restart in mezzo
* claim flip exploit
* contract lock after first delivery
* reward idempotence
* close/publish-results ripetuti

---

### Direzione finale consigliata

Ordine migliore:

1. **Preset `AUTO_RACE` + `JUDGED_BUILD`**
2. **Template catalog + input flow + dialog actions**
3. **`TOWN_CONTRACT`**
4. **`TERRITORIAL_SNAPSHOT`**

Motivo:

* massimizzi riuso del subsystem esistente
* usi Paper dove serve davvero
* non cerchi di modellare vault/claim con API che non rappresentano il dominio
* i nuovi kind arrivano solo quando il costo è giustificato

Se vuoi, nel prossimo passo posso trasformare questa proposta in uno **scheletro Java concreto**, con:

* enum nuovi
* payload DTO
* repository interfaces
* service contracts
* command signatures
* checklist di implementazione file-per-file.

[1]: https://jd.papermc.io/paper/1.21.11/org/bukkit/event/inventory/InventoryMoveItemEvent.html "InventoryMoveItemEvent (paper-api 1.21.11-R0.1-SNAPSHOT API)"
[2]: https://jd.papermc.io/paper/1.21.11/org/bukkit/event/block/BlockBreakEvent.html "BlockBreakEvent (paper-api 1.21.11-R0.1-SNAPSHOT API)"
[3]: https://jd.papermc.io/paper/1.21.11/org/bukkit/event/block/BlockPlaceEvent.html "BlockPlaceEvent (paper-api 1.21.11-R0.1-SNAPSHOT API)"
[4]: https://jd.papermc.io/paper/1.21.11/org/bukkit/event/inventory/CraftItemEvent.html "CraftItemEvent (paper-api 1.21.11-R0.1-SNAPSHOT API)"
[5]: https://jd.papermc.io/paper/1.21.11/org/bukkit/event/player/PlayerInteractEntityEvent.html "PlayerInteractEntityEvent (paper-api 1.21.11-R0.1-SNAPSHOT API)"
[6]: https://jd.papermc.io/paper/1.21.11/org/bukkit/event/entity/EntityBreedEvent.html "EntityBreedEvent (paper-api 1.21.11-R0.1-SNAPSHOT API)"
[7]: https://jd.papermc.io/paper/1.21.11/io/papermc/paper/event/player/PlayerTradeEvent.html "PlayerTradeEvent (paper-api 1.21.11-R0.1-SNAPSHOT API)"
[8]: https://jd.papermc.io/paper/1.21.11/org/bukkit/event/inventory/BrewEvent.html "BrewEvent (paper-api 1.21.11-R0.1-SNAPSHOT API)"
[9]: https://jd.papermc.io/paper/1.21.11/org/bukkit/event/entity/VillagerAcquireTradeEvent.html "VillagerAcquireTradeEvent (paper-api 1.21.11-R0.1-SNAPSHOT API)"
[10]: https://jd.papermc.io/paper/1.21.11/org/bukkit/event/player/PlayerFishEvent.html "PlayerFishEvent (paper-api 1.21.11-R0.1-SNAPSHOT API)"
[11]: https://jd.papermc.io/paper/1.21.11/io/papermc/paper/event/world/StructuresLocateEvent.html "StructuresLocateEvent (paper-api 1.21.11-R0.1-SNAPSHOT API)"
[12]: https://jd.papermc.io/paper/1.21.11/org/bukkit/event/player/PlayerAdvancementDoneEvent.html "PlayerAdvancementDoneEvent (paper-api 1.21.11-R0.1-SNAPSHOT API)"
[13]: https://jd.papermc.io/paper/1.21.11/org/bukkit/event/player/PlayerPortalEvent.html "PlayerPortalEvent (paper-api 1.21.11-R0.1-SNAPSHOT API)"
[14]: https://jd.papermc.io/paper/1.21.11/org/bukkit/event/player/PlayerChangedWorldEvent.html "PlayerChangedWorldEvent (paper-api 1.21.11-R0.1-SNAPSHOT API)"
[15]: https://jd.papermc.io/paper/1.21.11/org/bukkit/event/player/PlayerMoveEvent.html "PlayerMoveEvent (paper-api 1.21.11-R0.1-SNAPSHOT API)"
[16]: https://jd.papermc.io/paper/1.21.11/org/bukkit/event/entity/EntityDeathEvent.html "EntityDeathEvent (paper-api 1.21.11-R0.1-SNAPSHOT API)"
[17]: https://jd.papermc.io/paper/1.21.11/org/bukkit/event/raid/RaidFinishEvent.html "RaidFinishEvent (paper-api 1.21.11-R0.1-SNAPSHOT API)"
[18]: https://jd.papermc.io/paper/1.21.11/org/bukkit/event/player/PlayerExpChangeEvent.html "PlayerExpChangeEvent (paper-api 1.21.11-R0.1-SNAPSHOT API)"
[19]: https://jd.papermc.io/paper/1.21.11/org/bukkit/event/inventory/InventoryClickEvent.html "InventoryClickEvent (paper-api 1.21.11-R0.1-SNAPSHOT API)"
[20]: https://jd.papermc.io/paper/1.21.11/deprecated-list.html "Deprecated List (paper-api 1.21.11-R0.1-SNAPSHOT API)"
[21]: https://jd.papermc.io/paper/1.21.11/io/papermc/paper/dialog/Dialog.html "Dialog (paper-api 1.21.11-R0.1-SNAPSHOT API)"
[22]: https://jd.papermc.io/paper/1.21.11/io/papermc/paper/plugin/lifecycle/event/types/LifecycleEvents.html "LifecycleEvents (paper-api 1.21.11-R0.1-SNAPSHOT API)"
[23]: https://jd.papermc.io/paper/1.21.11/org/bukkit/entity/Entity.html "Entity (paper-api 1.21.11-R0.1-SNAPSHOT API)"
[24]: https://jd.papermc.io/paper/1.21.11/org/bukkit/scheduler/BukkitScheduler.html "BukkitScheduler (paper-api 1.21.11-R0.1-SNAPSHOT API)"
