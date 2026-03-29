## Goal

Ridisegnare **tutto il sistema quest città** in modo **prod-ready, Hypixel-like, progressivo e coerente**, non più basato su rotazioni casuali scollegate.

## Assumptions

* Minecraft **1.21.11**
* Le quest sono **city-based cooperative**
* Esiste già un sistema di **City XP** e reward nel vault
* Accetti di introdurre **1 nuova valuta/meta-progression** se migliora davvero il sistema

## Approach

Prendo i pattern che funzionano davvero nei sistemi più forti di Hypixel e li fondo in un unico framework per città:

* **guide/progressione a capitoli** invece di puro random: Hypixel SkyBlock Guide sblocca lo stage successivo quando completi almeno il 50% dello stage attuale; questo è perfetto per le tue quest fisse. ([Hypixel SkyBlock Wiki][1])
* **daily board tiered**: Hypixel Faction Quests usa un board giornaliero con 5 quest, 6 tipi e 5 tier di difficoltà (D→S), ottimo modello per daily/weekly non banali. ([Hypixel SkyBlock Wiki][2])
* **bonus primi completamenti + milestone permanenti**: le Commissions di Hypixel danno un bonus ai primi completamenti giornalieri e hanno milestone permanenti che sbloccano reward e perfino slot in più. ([Hypixel SkyBlock Wiki][3])
* **famiglie fisse per mob/attività**: Slayer è una progressione a famiglie specifiche di mob, quindi il pattern “Zombie I / II / III” è giusto, ma va incastonato dentro un atlante più ampio. ([Hypixel SkyBlock Wiki][4])
* **monthly card visibile e non random**: Bingo usa una card mensile a obiettivi fissi personali più goal community; questo è il riferimento migliore per il tuo layer mensile/stagionale. ([Hypixel SkyBlock Wiki][5])
* **race a finestre note**: gli eventi a finestra oraria fissa, come Spooky Festival, concentrano la concorrenza e rendono la race leggibile. ([Hypixel SkyBlock Wiki][6])

---

# City Quest Framework v2

Il sistema diventa composto da **4 layer**, ognuno con uno scopo chiaro:

1. **City Atlas** → progressione fissa permanente
2. **City Board** → daily / weekly / monthly rotanti ma guidate
3. **City Races** → competitive first-finisher a finestre fisse
4. **Season Codex** → progressione trimestrale lunga

Il punto chiave è questo:

> **le quest fisse costruiscono identità e progresso**,
> **le quest cicliche mantengono vivo il server**,
> **le race creano picchi di concorrenza**,
> **la season lega tutto in un arco da 3 mesi**.

---

# 1) Ridisegno Progressione Città

## 1.1 Due assi, non uno

Tieni **City XP**, ma aggiungi una seconda progressione:

* **City XP** = crescita numerica continua
* **City Seals** = prove di maturità ottenute da contenuti importanti

Questo risolve un problema comune: una città non deve salire di stage solo grindando deposito o playtime.

## 1.2 Nuovi Stage

| Stage         | Livelli | Focus            | Unlock principali                              |
| ------------- | ------: | ---------------- | ---------------------------------------------- |
| **Avamposto** |     1-9 | bootstrap        | Atlas base, 3 daily, 2 weekly                  |
| **Borgo**     |   10-24 | crescita         | 4 daily, 3 weekly, first monthly               |
| **Villaggio** |   25-44 | specializzazione | Race daily, 4 weekly, seasonal base            |
| **Cittadina** |   45-69 | infrastruttura   | 5 daily, 1 elite weekly, monthly grand project |
| **Città**     |   70-99 | prestigio        | Race weekly/monthly, seasonal elite            |
| **Regno**     | 100-150 | endgame          | elite atlas, crown quests, extra civic perks   |

## 1.3 Promozione stage

Ogni promozione richiede **tutte** queste cose:

* livello minimo raggiunto
* tesoreria minima
* infrastruttura minima
* **City Seals**
* numero minimo di famiglie Atlas completate

### Requisiti consigliati

| Promozione            | Requisiti                                                                     |
| --------------------- | ----------------------------------------------------------------------------- |
| Avamposto → Borgo     | lv 10 + treasury + 1 atlas family completata                                  |
| Borgo → Villaggio     | lv 25 + warp città + **1 City Seal**                                          |
| Villaggio → Cittadina | lv 45 + 2 district families completate + **2 City Seals**                     |
| Cittadina → Città     | lv 70 + monthly grand project completato almeno 1 volta + **4 City Seals**    |
| Città → Regno         | lv 100 + seasonal chapter finale completato almeno 1 volta + **6 City Seals** |

## 1.4 Come ottieni i City Seals

Ogni macrocapitolo dell’Atlas assegna **1 Seal** quando completi il **60%** delle sue famiglie.
Questo prende il meglio della logica “guide a stage” di Hypixel: il gioco ti fa avanzare quando hai dimostrato competenza sufficiente, non completismo totale. ([Hypixel SkyBlock Wiki][1])

---

# 2) Nuova Tassonomia Quest

## A. **City Atlas** — fisso, permanente, non random

È il tuo “oro I, oro II, zombie I, zombie II” ma fatto bene.

* organizzato per **macroarea**
* ogni famiglia ha **3 tier** standard
* alcune famiglie elite hanno **4 tier**
* reward fissi
* progressione permanente
* principale fonte di **City XP strutturale** e **Seals**

## B. **City Board** — rotante ma guidato

È il contenuto ricorrente:

* **Daily Board**
* **Weekly Operations**
* **Monthly Ledger**

Non è random puro: è generato da capitolo attivo, season theme e stato città.

## C. **City Races**

Finestre competitive:

* **Daily Sprint**
* **Weekly Clash**
* **Monthly Crown**

## D. **Season Codex**

Season di 3 mesi, per città, con quest visibili, capitoli, milestone e obiettivi elite.

---

# 3) Regola fondamentale del redesign

## Cosa NON deve più essere core progression

Queste tipologie vanno **demote** a filler/supporto:

* `PLAYTIME_MINUTES`
* `XP_PICKUP`
* parte di `ECONOMY_ADVANCED`
* parte di `RESOURCE_CONTRIBUTION`

Motivo: sono poco skillful, passive, facilmente abusabili, e non danno senso di “città che evolve”.

### Nuovo ruolo

* **Playtime** → solo daily filler, no XP
* **XP Pickup** → solo daily/weekly filler, no fixed, no race
* **Economy Advanced** → weekly/monthly, reward materiali/token, poco o zero XP
* **Resource Contribution** → weekly/monthly civic, non daily spam

---

# 4) City Atlas — catalogo fisso pronto da reimplementare

## Reward band standard Atlas

### Tier standard

* **Tier I** → low XP + starter pack
* **Tier II** → medium XP + area crate
* **Tier III** → high XP + rare crate
* **Tier IV elite** → very high XP + prestige reward

### Band consigliate

| Tier     | XP default | Reward secondario                           |
| -------- | ---------: | ------------------------------------------- |
| I        |        250 | starter material pack                       |
| II       |        600 | area crate                                  |
| III      |       1400 | rare area crate                             |
| IV elite |       3000 | prestige token / relic / cosmetic city item |

Per alcune famiglie di supporto: **XP = 0 o ridotta**.

---

## 4.1 Chapter: Extraction Atlas

| ID family                | Tier I | Tier II | Tier III | XP       | Reward focus              |
| ------------------------ | -----: | ------: | -------: | -------- | ------------------------- |
| `atlas.extract.coal`     |    256 |    1024 |     3072 | sì       | fuel/torches/blast bundle |
| `atlas.extract.iron`     |    128 |     512 |     1536 | sì       | iron crate / tools        |
| `atlas.extract.copper`   |    256 |    1024 |     3072 | sì       | builder deco pack         |
| `atlas.extract.gold`     |    128 |     512 |     1536 | sì       | rails / powered rails     |
| `atlas.extract.redstone` |    256 |    1024 |     3072 | sì       | redstone kit              |
| `atlas.extract.lapis`    |    192 |     768 |     2304 | sì       | enchant/support crate     |
| `atlas.extract.diamond`  |     48 |     192 |      576 | sì       | premium ore crate         |
| `atlas.extract.emerald`  |     32 |     128 |      384 | sì       | trade/economy crate       |
| `atlas.extract.debris`   |      8 |      24 |       64 | sì, alto | elite smithing crate      |

Note:

* `gold` e `zombie` esistono come famiglie vere, quindi puoi fare esattamente `oro_i`, `oro_ii`, `oro_iii`.
* `debris` può essere sbloccata solo da **Villaggio** in su.

---

## 4.2 Chapter: Combat Atlas

| ID family               | Tier I | Tier II | Tier III | XP       | Reward focus              |
| ----------------------- | -----: | ------: | -------: | -------- | ------------------------- |
| `atlas.combat.zombie`   |     60 |     240 |      720 | sì       | combat crate              |
| `atlas.combat.skeleton` |     50 |     200 |      600 | sì       | arrows/ranged pack        |
| `atlas.combat.spider`   |     50 |     200 |      600 | sì       | utility loot              |
| `atlas.combat.creeper`  |     25 |     100 |      300 | sì       | demolition materials      |
| `atlas.combat.enderman` |     20 |      80 |      240 | sì       | travel/combat pack        |
| `atlas.combat.nether`   |     40 |     160 |      480 | sì       | blaze/quartz/nether crate |
| `atlas.combat.ocean`    |     30 |     120 |      360 | sì       | prismarine/ocean pack     |
| `atlas.combat.rare`     |      5 |      15 |       40 | sì, alto | elite crate               |

Note:

* `rare` usa la tua famiglia `RARE_MOB_KILL`
* `nether` è mix controllato: blaze, piglin brute, magma cube, wither skeleton
* `ocean` è mix: drowned, guardian, elder tasks speciali

---

## 4.3 Chapter: Agronomy Atlas

| ID family                  | Tier I | Tier II | Tier III | XP       | Reward focus      |
| -------------------------- | -----: | ------: | -------: | -------- | ----------------- |
| `atlas.agro.wheat`         |    256 |    1024 |     3072 | sì       | seeds/bone meal   |
| `atlas.agro.roots`         |    192 |     768 |     2304 | sì       | food inputs       |
| `atlas.agro.cane`          |    256 |    1024 |     3072 | sì       | sugar/paper crate |
| `atlas.agro.melon_pumpkin` |    128 |     512 |     1536 | sì       | bulk food crate   |
| `atlas.agro.nether_wart`   |    128 |     512 |     1536 | sì       | brewing inputs    |
| `atlas.agro.animals`       |     20 |      80 |      240 | sì basso | breeder pack      |
| `atlas.agro.fishing`       |     20 |      80 |      240 | sì       | fishing crate     |

---

## 4.4 Chapter: Industry Atlas

| ID family                       | Tier I | Tier II | Tier III | XP           | Reward focus            |
| ------------------------------- | -----: | ------: | -------: | ------------ | ----------------------- |
| `atlas.industry.construction`   |    256 |    1024 |     4096 | sì           | building crate          |
| `atlas.industry.monument`       |   1000 |    4000 |    12000 | sì alto      | prestige building crate |
| `atlas.industry.food`           |     48 |     192 |      576 | **basso/no** | food supply             |
| `atlas.industry.potion`         |     12 |      48 |      144 | **basso/no** | alchemy kit             |
| `atlas.industry.redstone`       |     48 |     192 |      576 | sì           | redstone crate          |
| `atlas.industry.craft_master`   |     24 |      96 |      288 | sì           | tech crate              |
| `atlas.industry.villager_trade` |     24 |      96 |      288 | **basso/no** | emerald/trade crate     |

Note:

* `food`, `potion`, `villager_trade` non devono essere grandi fonti di XP
* `monument` è una fixed quest prestigio, non filler

---

## 4.5 Chapter: Frontier Atlas

| ID family                    | Tier I | Tier II | Tier III | XP            | Reward focus          |
| ---------------------------- | -----: | ------: | -------: | ------------- | --------------------- |
| `atlas.frontier.structure`   |      1 |       4 |       10 | sì            | explorer crate        |
| `atlas.frontier.archaeology` |      8 |      32 |       96 | sì            | archaeology crate     |
| `atlas.frontier.trial_vault` |      1 |       3 |        8 | sì alto       | trial crate           |
| `atlas.frontier.dimension`   |     10 |      40 |      120 | sì            | travel pack           |
| `atlas.frontier.raid`        |      1 |       3 |        8 | sì alto       | village defense crate |
| `atlas.frontier.boss`        |      1 |       3 |        6 | sì molto alto | boss crate            |

---

## 4.6 Chapter: Civic Atlas

| ID family                  |   Tier I |   Tier II |   Tier III | XP           | Reward focus         |
| -------------------------- | -------: | --------: | ---------: | ------------ | -------------------- |
| `atlas.civic.contribution` | 1024 pts |  4096 pts |  12288 pts | **no/basso** | vault materials      |
| `atlas.civic.transport`    |   2000 m |    8000 m |    24000 m | sì basso     | logistics crate      |
| `atlas.civic.economy`      | 5000 val | 25000 val | 100000 val | **no/basso** | emerald/market crate |

### Cosa esce dal fixed atlas

Questi **non** li terrei come fixed permanenti:

* `PLAYTIME_MINUTES`
* `XP_PICKUP`

Vanno nel Board, non nell’Atlas.

---

# 5) City Board — rotazione guidata, non random

## 5.1 Filosofia

Le board devono essere:

* coerenti col livello città
* collegate all’Atlas
* influenzate dalla Season
* poco ripetitive
* non exploitabili

## 5.2 Composizione board

### Daily Board

* **1 Path Quest** → viene dal chapter Atlas meno completato
* **1 Focus Quest** → segue il tema stagionale o settimanale
* **1 Support Quest** → crafting/logistics/economy/farming
* **1 Wildcard Quest** → varietà

Da **Cittadina+**:

* +1 **Elite Daily** opzionale

### Weekly Operations

* **2 Core Quest**
* **1 Support Quest**
* **1 Frontier Quest**
* **1 Civic/Industry Quest**
* da **Città+**: +1 **Elite Weekly**

### Monthly Ledger

* **1 Grand Project** multi-step
* **4-6 Monthly Contracts**
* **1 Monthly Elite Contract** da Cittadina+

---

# 6) Generazione Board — algoritmo vero

## 6.1 Input del generatore

Per ogni reset città:

* stage città
* season attiva
* chapter Atlas meno completato
* chapter Atlas più attivo negli ultimi 14 giorni
* blacklist ultime 3 board
* archetipi già attivi nello stesso ciclo

## 6.2 Weighting consigliato

* **50%** → current season theme
* **30%** → lowest-completed Atlas chapter
* **20%** → city affinity recente

## 6.3 Hard rules

* stessa famiglia non può comparire in 2 reset consecutivi
* max **1 passive quest** per board
* almeno **1 skillful quest** per board
* niente `playtime` + `xp_pickup` nella stessa daily
* niente `economy` nella race
* niente `resource contribution` come obiettivo dominante più di 1 volta ogni 7 giorni

Questa è la parte che trasforma il sistema da random a “sembra pensato”.

---

# 7) Base target system per quest rotanti

Per non scrivere mille config diversi, usa:

## 7.1 Stage multiplier

| Stage     | Mult |
| --------- | ---: |
| Avamposto | 1.00 |
| Borgo     | 1.35 |
| Villaggio | 1.75 |
| Cittadina | 2.25 |
| Città     | 2.80 |
| Regno     | 3.50 |

## 7.2 Cycle multiplier

| Cycle         | Mult |
| ------------- | ---: |
| Daily         |  1.0 |
| Weekly        |  4.0 |
| Monthly side  | 10.0 |
| Monthly grand | 22.0 |

## 7.3 Base target per template

| Template                     | Base |
| ---------------------------- | ---: |
| specific ore mine            |   96 |
| generic ore mine             |  160 |
| mob kill specific            |   40 |
| rare mob kill                |    5 |
| crop harvest specific        |   80 |
| fish catch                   |   12 |
| animal interaction           |   15 |
| construction blocks          |   96 |
| food craft                   |   24 |
| potion brew                  |    6 |
| villager trade               |   12 |
| archaeology brush            |    6 |
| trial vault                  |    1 |
| structure discovery          |    1 |
| raid win                     |    1 |
| dimension travel             |    5 |
| transport distance           | 1000 |
| resource contribution points |  600 |
| economy value                | 2500 |
| playtime minutes             |   45 |
| xp pickup                    |  300 |

### Formula

```text
target = round(baseTarget * stageMultiplier * cycleMultiplier)
```

### Eccezioni

* `structure discovery`, `trial_vault`, `raid win`, `boss kill` restano con bracket custom
* race usa target **non scalati per città** ma fissi per fairness

---

# 8) Pool pronto da reimplementare

## 8.1 Daily-eligible

* mining specific
* mob specific
* crop specific
* fishing
* animals
* construction
* food craft
* potion brew
* villager trade
* archaeology
* dimension travel
* transport
* playtime
* xp pickup

## 8.2 Weekly-eligible

Tutto il daily pool, più:

* rare mob
* trial vault
* raid win
* structure discovery
* economy
* resource contribution
* redstone automation
* craft master
* nether expedition
* ocean operation

## 8.3 Monthly-eligible

Tutto il weekly pool, più:

* boss kill
* monument
* long exploration chain
* multi-phase project

## 8.4 Race-eligible

Solo archetipi ad alto segnale:

* specific ore
* specific mob
* crop specific
* fishing
* food craft
* potion brew
* villager trade
* archaeology
* trial vault
* raid win
* construction sprint
* redstone task controllata

## 8.5 Race-banned

* playtime
* xp pickup
* generic transport
* generic economy
* resource contribution puro

---

# 9) Quest fisse vs quest cicliche — regola reward

## 9.1 Quest che DEVONO dare XP

* mining families
* combat families
* frontier families
* construction / monument
* boss / raid / trial / structure

## 9.2 Quest che DEVONO dare poco o zero XP

* playtime
* xp pickup
* food craft
* potion brew
* villager trade
* economy
* resource contribution

## 9.3 Reward classes

### XP Rewards

Usati per contenuti che dimostrano progresso reale.

### Material Rewards

Usati per supportare la città:

* crate materiali
* redstone pack
* food crate
* combat crate
* explorer crate
* builder crate

### Quest Tokens

Nuova valuta opzionale ma fortemente consigliata.

Perché serve:

* separa la progressione XP dai reward shop
* permette reward a lungo termine
* rende utili quest “no XP”

### Reward structure consigliata

* **Fixed Atlas** → XP + crate
* **Daily** → small XP/material/token
* **Weekly** → medium XP/material/token
* **Monthly** → large XP + premium materials
* **Race** → prestige token + exclusive monthly emblem
* **Season** → big token bundles + exclusive city cosmetics + seals/elite rewards

---

# 10) Daily Board definitivo

## Reset

* ogni giorno alle **05:00 Europe/Rome**

## Slot

* Avamposto/Borgo: 4 slot
* Villaggio/Cittadina: 4 slot + 1 elite opzionale
* Città/Regno: 5 slot + 1 elite opzionale

## Bonus primi completamenti

Come le Commissions che danno bonus sui primi completamenti giornalieri, metti un **First Completion Bonus** giornaliero per le prime **2 daily** chiuse dalla città. ([Hypixel SkyBlock Wiki][3])

### Bonus

* +token
* +crate extra
* **non** troppo XP

Questo crea il login moment senza rompere il bilanciamento.

---

# 11) Weekly Operations definitive

## Reset

* ogni lunedì alle **05:00 Europe/Rome**

## Struttura

* 4 quest standard
* 1 project quest
* 1 elite da Città+

## Tipi forti per weekly

* rare mobs
* resource contribution
* redstone automation
* archaeology
* raid victory
* trial vault
* structure discovery
* ocean/nether expedition
* craft master
* monument light

Le weekly sono dove metti contenuti che richiedono cooperazione vera.

---

# 12) Monthly Ledger definitivo

Qui il redesign più forte è:

## 12.1 Smetti di fare “monthly = una grossa quest”

Fai invece:

* **1 Grand Project**
* **5 Monthly Contracts**
* **1 Elite Contract**
* **1 Mystery Contract** opzionale

Questo è molto più vicino a una “card” leggibile, che è il motivo per cui il modello Bingo funziona così bene: obiettivi fissi, visibili, con progressione sul mese. ([Hypixel SkyBlock Wiki][5])

## 12.2 Grand Project (multi-fase)

Template possibili:

### A. Monument Project

1. raccogli risorse
2. piazza palette blocchi
3. completa build requirement

### B. Expedition Project

1. scopri strutture
2. apri trial vault / archeologia
3. completa raid / boss

### C. War Supply Project

1. contribution points
2. food craft / potion brew
3. mob family finale

### D. Industrial Project

1. ore extraction
2. redstone automation
3. craft master finale

## 12.3 Monthly Contracts

Scelti da pool guidato, non random puro.

## 12.4 Mystery Contract

Una quest nascosta mensile con clue, reward alto, XP medio o token alto.
La terrei **1 sola**, non un layer intero separato.

---

# 13) City Races definitive

## 13.1 Daily Sprint

### Orari

* **18:30 Europe/Rome**
* **21:30 Europe/Rome**

### Durata

* reveal a **T-10 min**
* start a **T0**
* timeout a **45 minuti**

### Eligible

* Villaggio+

### Rotazione settimanale

| Giorno    | Tema                        |
| --------- | --------------------------- |
| Lunedì    | Mining                      |
| Martedì   | Combat                      |
| Mercoledì | Farming                     |
| Giovedì   | Industry                    |
| Venerdì   | Frontier                    |
| Sabato    | Mixed                       |
| Domenica  | Civic-lite / special sprint |

## 13.2 Weekly Clash

### Orario

* **Sabato 21:00 Europe/Rome**

### Durata

* **90 minuti**

### Eligible

* Cittadina+

### Formato

* 2 fasi

  * fase 1: raccolta / preparazione
  * fase 2: chiusura skillful

Esempio:

* mine 600 iron ore
* craft 64 lanterns

oppure:

* kill 120 zombies
* complete 1 raid

## 13.3 Monthly Crown

### Orario

* **Prima domenica del mese, 18:00 Europe/Rome**

### Durata

* **120 minuti**

### Eligible

* Città+

### Formato

Una sola quest prestigio o gauntlet a 3 fasi.

## 13.4 Reward race

Dato che hai chiesto winner-only:

* **1° città** = reward piena
* tutte le altre = niente reward principale

### Reward consigliata

* Race Tokens
* City XP moderata
* Monthly Banner / City Emblem
* Hall of Fame entry
* cosmetic city particle/banner/title

Gli eventi a finestra fissa funzionano bene proprio perché concentrano il traffico e la competizione nello stesso momento; è un pattern già fortissimo negli eventi a timer di Hypixel. ([Hypixel SkyBlock Wiki][6])

---

# 14) Season Codex — 3 mesi

## 14.1 Struttura stagione

Ogni stagione dura **3 mesi** e ha **3 Act**:

* **Act I** (settimane 1-4)
* **Act II** (settimane 5-8)
* **Act III** (settimane 9-12/13)

## 14.2 Sblocco progressivo

Come la SkyBlock Guide, l’Act successivo si sblocca al **50% del precedente**. ([Hypixel SkyBlock Wiki][1])

## 14.3 Composizione Season Codex

* **8 quest Act I**
* **8 quest Act II**
* **8 quest Act III**
* **3 Elite Seasonal Quest**
* **1 Hidden Relic Questline**

Totale: **28 contenuti stagionali**

## 14.4 Seasonal Points

Ogni quest assegna:

* minor = 5 SP
* major = 10 SP
* elite = 20 SP
* hidden relic = 25 SP

## 14.5 Milestone stagionali

* 20 SP
* 40 SP
* 70 SP
* 100 SP
* 140 SP
* 180 SP

## 14.6 Temi stagionali consigliati

| Season    | Tema dominante                              |
| --------- | ------------------------------------------- |
| Primavera | farming, build, villager, exploration light |
| Estate    | ocean, travel, fishing, structures          |
| Autunno   | combat, harvest, raids, contribution        |
| Inverno   | mining, redstone, craft, bosses             |

## 14.7 Seasonal quest design

Usa pattern tipo:

* chapter quest
* long contracts
* mastery quest
* hidden relic chain

Questo prende il meglio di:

* guide stages
* faction tiers
* monthly fixed card
* slayer-style family mastery ([Hypixel SkyBlock Wiki][4])

---

# 15) Full redesign delle quest “pronte”

## 15.1 Fixed families da implementare subito

Queste sono le famiglie fisse consigliate:

### Extraction

* coal
* iron
* copper
* gold
* redstone
* lapis
* diamond
* emerald
* debris

### Combat

* zombie
* skeleton
* spider
* creeper
* enderman
* nether
* ocean
* rare

### Agronomy

* wheat
* roots
* cane
* melon_pumpkin
* nether_wart
* animals
* fishing

### Industry

* construction
* monument
* food
* potion
* redstone
* craft_master
* villager_trade

### Frontier

* structure
* archaeology
* trial_vault
* dimension
* raid
* boss

### Civic

* contribution
* transport
* economy

## 15.2 Rotating templates da implementare subito

Daily/weekly/monthly generator:

* specific_ore
* specific_mob
* rare_mob
* crop_specific
* fish_catch
* animal_actions
* build_specific
* monument_palette
* food_craft
* potion_brew
* redstone_ops
* craft_advanced
* villager_trade
* structure_discovery
* archaeology_brush
* trial_vault_open
* dimension_travel
* raid_win
* boss_kill
* transport_distance
* resource_contribution_points
* economy_value
* playtime_minutes
* xp_pickup

---

# 16) Naming scheme definitivo

## Fixed

```text
atlas.<chapter>.<family>.<tier>
```

Esempi:

```text
atlas.extract.gold.1
atlas.extract.gold.2
atlas.combat.zombie.1
atlas.combat.zombie.2
atlas.frontier.raid.3
```

## Daily/Weekly/Monthly templates

```text
board.<cycle>.<template>.<variant>
```

Esempi:

```text
board.daily.mob_specific.zombie
board.weekly.structure.discovery
board.monthly.project.monument
```

## Races

```text
race.<cycle>.<theme>.<variant>
```

Esempi:

```text
race.daily.mining.gold_rush
race.weekly.combat.undead_surge
race.monthly.frontier.ancient_recovery
```

## Seasonal

```text
season.<season_key>.act<1|2|3>.<quest_key>
```

---

# 17) Anti-abuse definitivo

## Hard rules

* `playtime` solo se non AFK
* `mob_kill` esclude spawn illegittimi / exploit farm se vuoi quest più fair
* `structure_discovery` conta una volta per città
* `trial_vault` valida solo con open reale
* `villager_trade` con cap per villager / refresh
* `resource_contribution` usa weighted points, non item count raw
* `construction` valida solo con blocchi whitelist e cap anti-place-break
* `redstone` valida su azioni reali, non spam lever
* `transport` richiede movimento utile, non loop corto
* ogni azione può contribuire a **max 1 quest per ciclo**
* cap giornalieri su archetipi passivi

## Perché

Questa è la differenza tra “plugin che sembra alpha” e “plugin da server serio”.

---

# 18) UI definitiva

## `/city missions`

Tabs:

1. **Atlas**
2. **Board**
3. **Races**
4. **Season**
5. **History**

## Atlas tab

* chapters
* family progress
* seal progress
* next promotion requirements

## Board tab

* Daily / Weekly / Monthly
* timer reset
* contribution per member
* claim status

## Race tab

* next race countdown
* race history
* winner city
* available rewards

## Season tab

* act progress
* seasonal milestones
* hidden relic hint slot
* seasonal point bar

---

# 19) Cosa eliminerei o fonderei dal sistema attuale

## Elimina come macroaree separate

* `Monthly Event (window A/B)` → fuso dentro **Monthly Crown Race** + **Season Codex**
* `Secret Quest mensile` → diventa **Mystery Contract** mensile o **Hidden Relic** stagionale
* `Seasonal Team Phase Quests` come layer separato → diventano **Grand Projects** mensili o **Elite Seasonal**

## Motivo

Meno macroaree parallele = sistema più leggibile, più facile da bilanciare, più “Hypixel-like”.

---

# 20) Configurazione finale consigliata

## Reset

* Daily: **05:00**
* Weekly: **Lunedì 05:00**
* Monthly: **1 del mese, 05:00**
* Season: **1 Mar / 1 Giu / 1 Set / 1 Dic, 05:00**

## Race windows

* Daily Sprint: **18:30 / 21:30**
* Weekly Clash: **Sabato 21:00**
* Monthly Crown: **Prima domenica 18:00**

## Stage gating

* Race daily: Villaggio+
* Race weekly: Cittadina+
* Race monthly: Città+
* Seasonal elite: Cittadina+
* Regno unlock finale: seasonal chapter finale + seals

---

# 21) Risultato finale

Il sistema nuovo diventa:

* **non random puro**
* **legato al progresso reale della città**
* **facile da espandere**
* **leggibile in GUI**
* **bilanciabile**
* **con differenza netta tra contenuto permanente, ciclico, competitivo e stagionale**

La parte più importante del redesign è questa:

> **le quest fisse non sono più un elenco scollegato, ma un Atlante di mastery**
> **le daily/weekly/monthly non sono più pescate a caso, ma board guidate da progresso + tema**
> **le race non sono più un extra casuale, ma eventi orari riconoscibili**
> **la season non è solo una finestra lunga, ma un codex a capitoli**

Se devi implementare davvero in quest’ordine, l’ordine giusto è:

1. **City Atlas**
2. **City Seals + stage promotion**
3. **Daily Board generator**
4. **Weekly / Monthly Ledger**
5. **Race scheduler**
6. **Season Codex**

Questa è la base migliore da cui partire per un sistema città serio.

[1]: https://wiki.hypixel.net/SkyBlock_Levels "SkyBlock Levels - Hypixel SkyBlock Wiki"
[2]: https://wiki.hypixel.net/Faction_Quests "Faction Quests - Hypixel SkyBlock Wiki"
[3]: https://wiki.hypixel.net/Commissions "Commissions - Hypixel SkyBlock Wiki"
[4]: https://wiki.hypixel.net/Slayer "Slayer - Hypixel SkyBlock Wiki"
[5]: https://wiki.hypixel.net/Bingo "Bingo - Hypixel SkyBlock Wiki"
[6]: https://wiki.hypixel.net/Spooky_Festival "Spooky Festival - Hypixel SkyBlock Wiki"
