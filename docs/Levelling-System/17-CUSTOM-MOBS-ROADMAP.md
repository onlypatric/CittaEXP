# 17 - Custom Mobs Roadmap

## Scopo
Chiudere in modo definitivo il sottosistema `custom mobs` di CittaEXP, partendo dallo stato reale gia implementato e concentrandosi solo su cio che manca davvero per dichiarare il blocco pronto a sostenere una libreria ampia di mob custom authored.

Il target finale non e piu soltanto:
- `spawn by mobId`
- `ability engine`
- `Mob invasion` custom-enabled

Il target finale e:
- runtime Paper-native abbastanza solido da non costringerci a rifare il core quando inizieremo a generare molti mob custom
- authored layer canonico, validabile e stabile
- lifecycle robusto per boss, summon, projectile e sessioni difesa
- diagnostica e tooling staff sufficienti a operare il sistema senza debug manuale nel codice

---

## Audit stato reale al 2026-03-20

### Gia implementato

#### Core custom mobs
- registry mob: `src/main/java/it/patric/cittaexp/custommobs/CustomMobRegistry.java`
- registry abilities: `src/main/java/it/patric/cittaexp/custommobs/MobAbilityRegistry.java`
- registry traits: `src/main/java/it/patric/cittaexp/custommobs/MobTraitRegistry.java`
- registry variants: `src/main/java/it/patric/cittaexp/custommobs/MobVariantRegistry.java`
- spawn service: `src/main/java/it/patric/cittaexp/custommobs/CustomMobSpawnService.java`
- markers PDC: `src/main/java/it/patric/cittaexp/custommobs/CustomMobMarkers.java`
- bootstrap subsystem: `src/main/java/it/patric/cittaexp/custommobs/CustomMobSubsystem.java`

#### Combat toolkit runtime
- cast context: `src/main/java/it/patric/cittaexp/custommobs/MobCastContext.java`
- trigger set: `src/main/java/it/patric/cittaexp/custommobs/MobTriggerType.java`
- targeters: `src/main/java/it/patric/cittaexp/custommobs/MobTargeterSpec.java`
- conditions: `src/main/java/it/patric/cittaexp/custommobs/MobConditionSpec.java`
- actions: `src/main/java/it/patric/cittaexp/custommobs/MobActionSpec.java`
- runtime dispatcher: `src/main/java/it/patric/cittaexp/custommobs/MobEffectsEngine.java`
- listeners di wiring: `src/main/java/it/patric/cittaexp/custommobs/CustomMobCombatListener.java`

#### Boss layer iniziale
- phase rules: `src/main/java/it/patric/cittaexp/custommobs/MobPhaseRule.java`
- bossbar spec: `src/main/java/it/patric/cittaexp/custommobs/CustomMobBossbarSpec.java`
- local broadcasts spec: `src/main/java/it/patric/cittaexp/custommobs/CustomMobLocalBroadcasts.java`
- boss snapshot runtime: `src/main/java/it/patric/cittaexp/custommobs/CustomMobBossRuntimeSnapshot.java`

#### Integrazione con Mob invasion
- spawn ref astratto: `src/main/java/it/patric/cittaexp/defense/CityDefenseSpawnRef.java`
- spec difesa custom-aware: `src/main/java/it/patric/cittaexp/defense/CityDefenseLevelSpec.java`
- parser difesa con supporto `custom:`: `src/main/java/it/patric/cittaexp/defense/CityDefenseSettings.java`
- runtime difesa boss-aware: `src/main/java/it/patric/cittaexp/defense/CityDefenseService.java`
- wiring plugin: `src/main/java/it/patric/cittaexp/CittaExpPlugin.java`

#### Authored content
- cartelle resources presenti:
  - `src/main/resources/custom-mobs/`
  - `src/main/resources/mob-abilities/`
  - `src/main/resources/mob-traits/`
  - `src/main/resources/mob-variants/`
- seeding nel data folder presente via `defaults.txt`: `src/main/java/it/patric/cittaexp/custommobs/CustomMobSubsystem.java`
- `defense.yml` usa gia `custom:` refs: `src/main/resources/defense.yml`

#### Test presenti
- parser/registry custom mobs: `src/test/java/it/patric/cittaexp/custommobs/CustomMobRegistryTest.java`
- parser difesa spawn refs: `src/test/java/it/patric/cittaexp/defense/CityDefenseSettingsTest.java`
- spawn ref unit tests: `src/test/java/it/patric/cittaexp/defense/CityDefenseSpawnRefTest.java`

### Conclusione dell'audit
Il blocco `custom mobs` non e piu in fase embrionale. Il core esiste gia e copre:
- spawn custom
- ability runtime
- traits/variants
- boss phases iniziali
- bossbar locale
- integrazione in `Mob invasion`

Quindi il documento precedente non e piu accurato quando descrive il sistema come ancora solo "vanilla + roadmap".

Il lavoro residuo non e costruire da zero. Il lavoro residuo e chiudere i gap di hardening, Paper-native runtime, tooling e authored canonicalization.

---

## Gap residui da chiudere davvero

### 1. Scheduler e runtime non ancora Paper/Folia-safe

#### Problema
Il runtime custom mobs usa ancora scheduler Bukkit tradizionali:
- idle loop: `src/main/java/it/patric/cittaexp/custommobs/MobEffectsEngine.java`
- projectile loop: `src/main/java/it/patric/cittaexp/custommobs/MobEffectsEngine.java`
- session tick difesa: `src/main/java/it/patric/cittaexp/defense/CityDefenseService.java`

Questo e sufficiente su Paper classico, ma non e il punto d'arrivo giusto se vogliamo chiudere il sistema con una base moderna.

#### Paper API da usare
- [Scheduling](https://docs.papermc.io/paper/dev/scheduler/)
- [Supporting Paper and Folia](https://docs.papermc.io/paper/dev/folia-support/)

#### Deliverable
Introdurre un piccolo layer runtime dedicato, ad esempio `CustomMobRuntimeScheduler`, con policy esplicita:
- task entity-bound per idle pulse e cooldown/flag expiry dei mob
- task location-bound o region-bound per projectile runtime
- nessun accesso world/entity da thread async
- un solo punto di astrazione per differenziare Paper classico e Folia

#### Codebase refs
- `src/main/java/it/patric/cittaexp/custommobs/MobEffectsEngine.java`
- `src/main/java/it/patric/cittaexp/defense/CityDefenseService.java`
- `src/main/java/it/patric/cittaexp/CittaExpPlugin.java`

### 2. Resolver di tipi ancora troppo fragili e in parte deprecated

#### Problema
Il parser/runtime usa ancora risoluzioni legacy o deprecate:
- `Attribute.valueOf(...)`: `src/main/java/it/patric/cittaexp/custommobs/CustomMobParsingSupport.java`
- `Sound.valueOf(...)`: `src/main/java/it/patric/cittaexp/custommobs/MobEffectsEngine.java`
- `PotionEffectType.getByName(...)`: `src/main/java/it/patric/cittaexp/custommobs/MobEffectsEngine.java`

Questo lascia warning e validation poco robusta.

#### Paper API da usare
- [Registries](https://docs.papermc.io/paper/dev/registries/)
- [Persistent data container (PDC)](https://docs.papermc.io/paper/dev/pdc/)
  - non per la risoluzione, ma per allineare identity/runtime state con chiavi stabili namespace-based

#### Deliverable
Aggiungere un resolver centralizzato, ad esempio `CustomMobTypeResolver`, che normalizzi e validi:
- `EntityType`
- `Attribute`
- `Sound`
- `PotionEffectType`
- `Particle`

Obiettivo:
- eliminare i warning di compilazione attuali
- avere errori parser coerenti e centralizzati
- preparare il path per key-based resolution quando utile

#### Codebase refs
- `src/main/java/it/patric/cittaexp/custommobs/CustomMobParsingSupport.java`
- `src/main/java/it/patric/cittaexp/custommobs/MobEffectsEngine.java`
- `src/main/java/it/patric/cittaexp/custommobs/MobAbilityRegistry.java`

### 3. Audience orchestration locale non ancora centralizzata

#### Problema
Bossbar e messaggi locali della difesa vengono gestiti con loop manuali sui player online:
- `src/main/java/it/patric/cittaexp/defense/CityDefenseService.java`

Funziona, ma il blocco non e ancora chiuso bene. Manca un servizio dedicato per:
- audience locale sessione difesa
- bossbar attach/detach/update
- broadcast locale coerente
- futura riusabilita per mini-boss non-difesa

#### Paper API da usare
- [Audiences](https://docs.papermc.io/paper/dev/component-api/audiences/)
- [Event API: listeners](https://docs.papermc.io/paper/dev/event-api/listeners/)
- Adventure BossBar API gia usata indirettamente nel runtime

#### Deliverable
Introdurre un servizio tipo `DefenseEncounterAudienceService` o `CustomMobAudienceService` che esponga:
- `Audience audienceForDefenseSession(...)`
- `showBossBar(...)`
- `updateBossBar(...)`
- `hideBossBar(...)`
- `broadcastLocal(...)`

#### Codebase refs
- `src/main/java/it/patric/cittaexp/defense/CityDefenseService.java`
- `src/main/java/it/patric/cittaexp/cityhubgui/CityHubGuiService.java`
- `src/main/resources/config.yml`

### 4. Stato boss sparso dentro `CityDefenseService`

#### Problema
Il boss runtime esiste, ma lo stato di incontro e ancora sparso in `SessionState`:
- `activeBossId`
- `bossBar`
- `bossBarViewers`
- `lastBossPhaseNumber`
- `lastBossPhaseLabel`
- `lastBossName`

Questo rende piu difficile estendere:
- phase telemetry
- multi-boss future-proofing
- diagnostics
- cleanup completo encounter-scoped

#### Paper API da usare
- nessuna API nuova richiesta qui; e refactoring di dominio
- ma il servizio risultante deve lavorare bene con [Audiences](https://docs.papermc.io/paper/dev/component-api/audiences/) e [Scheduling](https://docs.papermc.io/paper/dev/scheduler/)

#### Deliverable
Estrarre un tipo vero:
- `BossEncounterState`

Contenuto minimo:
- `bossUuid`
- `townId`
- `sessionId`
- `waveNumber`
- `currentPhase`
- `phaseLabel`
- `bossbarHandle`
- `viewerSet`
- eventuali runtime flags/counters encounter-scoped

#### Codebase refs
- `src/main/java/it/patric/cittaexp/defense/CityDefenseService.java`
- `src/main/java/it/patric/cittaexp/custommobs/MobEffectsEngine.java`

### 5. Projectile runtime non ancora tracciato e cancellabile a livello encounter/sessione

#### Problema
I projectile authored sono implementati come task tick-based anonimi nel motore:
- `src/main/java/it/patric/cittaexp/custommobs/MobEffectsEngine.java`

Mancano:
- registry dei projectile runtime attivi
- cancellazione esplicita su fine sessione difesa
- tracciamento encounter-aware per diagnostica
- marker opzionali su projectile entity se in futuro si passa a projectile veri o hybrid visual/entity

#### Paper API da usare
- [Scheduling](https://docs.papermc.io/paper/dev/scheduler/)
- [Particles](https://docs.papermc.io/paper/dev/particles/)
- [Persistent data container (PDC)](https://docs.papermc.io/paper/dev/pdc/)

#### Deliverable
Introdurre `ProjectileRuntimeService`:
- registra projectile runtime per `caster/session/wave`
- cancella tutto su `endSession()`
- espone contatori per diagnostics
- rende il lifecycle leggibile e testabile

#### Codebase refs
- `src/main/java/it/patric/cittaexp/custommobs/MobEffectsEngine.java`
- `src/main/java/it/patric/cittaexp/defense/CityDefenseService.java`

### 6. Schema PDC ancora incompleto per analytics e tooling

#### Problema
Il PDC copre gia:
- `mob_id`
- `spawn_source`
- `owner_town_id`
- `wave_id`
- `variant_id`
- `trait_id`
- `summoned`
- `parent_mob_uuid`

Ma per chiudere bene il sistema mancano marker utili a:
- diagnostics
- linking projectile/summon/boss encounter
- future challenge hooks
- content balancing

#### Paper API da usare
- [Persistent data container (PDC)](https://docs.papermc.io/paper/dev/pdc/)

#### Deliverable
Estendere i marker almeno con:
- `session_id`
- `defense_tier`
- `boss_flag`
- `phase_id`
- `projectile_owner_uuid`
- `encounter_id`

#### Codebase refs
- `src/main/java/it/patric/cittaexp/custommobs/CustomMobMarkers.java`
- `src/main/java/it/patric/cittaexp/custommobs/CustomMobSpawnContext.java`
- `src/main/java/it/patric/cittaexp/defense/CityDefenseService.java`

### 7. Registry query e pool authoring ancora incompleti

#### Problema
Le registry esistono, ma mancano query piu utili per authored generation e pack orchestration:
- `findByTag`
- `findByPool`
- supporto a `pool` authored per mob
- indici secondari su tag/archetype/theme

Questa parte diventa importante proprio adesso che l'obiettivo successivo e generare molti custom mobs.

#### Paper API da usare
- nessuna API Paper specifica qui; e architettura authored/runtime
- opzionalmente [Registries](https://docs.papermc.io/paper/dev/registries/) se alcuni tipi authored si spostano verso key-based lookup

#### Deliverable
Estendere:
- `CustomMobRegistry`
- `MobTraitRegistry`
- `MobVariantRegistry`

Con:
- metadati di pool/tag/theme
- query API stabili
- eventuale `packId` o `arcId`

#### Codebase refs
- `src/main/java/it/patric/cittaexp/custommobs/CustomMobRegistry.java`
- `src/main/java/it/patric/cittaexp/custommobs/MobTraitRegistry.java`
- `src/main/java/it/patric/cittaexp/custommobs/MobVariantRegistry.java`
- `src/main/resources/custom-mobs/`

### 8. Canonicalizzazione authored ancora non chiusa

#### Problema
La cartella `src/main/resources/custom-mobs/` contiene gia piu set authored. Il seeding runtime pero dipende da `defaults.txt`.

Quindi oggi c'e un rischio concreto:
- contenuto presente in resources ma non seeded nel data folder
- piu naming convention simultanee
- piu archi authored non ancora normalizzati in un catalogo canonico unico

#### Paper API da usare
- nessuna API runtime specifica; qui il tema e content pipeline
- [Plugin configuration](https://docs.papermc.io/paper/dev/configuration/) e solo di contorno, non il cuore

#### Deliverable
Chiudere la content pipeline con:
- un solo set canonico per arco/tier
- `defaults.txt` completi e allineati
- naming convention fisse per file, id e archi tematici
- eventuale `pack-manifest` authored per versione/contenuto incluso

#### Codebase refs
- `src/main/resources/custom-mobs/defaults.txt`
- `src/main/resources/mob-abilities/defaults.txt`
- `src/main/resources/mob-traits/defaults.txt`
- `src/main/resources/mob-variants/defaults.txt`
- `src/main/java/it/patric/cittaexp/custommobs/CustomMobSubsystem.java`

### 9. Diagnostics e tooling staff mancanti

#### Problema
Il runtime carica i pack e logga un recap all'avvio, ma non esiste ancora un toolkit staff per operarlo davvero:
- probe custom mobs
- lint authored
- reload controlled
- spawn test manuale
- dump runtime state encounter/boss/projectiles

Per chiudere il sistema prima di generare contenuti, questo e necessario.

#### Paper API da usare
- [Command API Introduction](https://docs.papermc.io/paper/dev/command-api/basics/introduction/)
- [Command trees](https://docs.papermc.io/paper/dev/command-api/basics/command-trees/)
- [Arguments and literals](https://docs.papermc.io/paper/dev/command-api/basics/arguments-and-literals/)
- [Suggestions](https://docs.papermc.io/paper/dev/command-api/basics/suggestions/)
- [Custom arguments](https://docs.papermc.io/paper/dev/command-api/basics/custom-arguments/)

#### Deliverable
Aggiungere `staff custommobs` con almeno:
- `probe`
- `lint`
- `reload`
- `spawn <mobId>`
- `dump <uuid>`

E integrare nel `probe` generale almeno:
- loaded counts
- skipped counts
- active runtime mobs
- active boss encounters
- active projectile runtimes

#### Codebase refs
- `src/main/java/it/patric/cittaexp/command/CittaExpCommandTree.java`
- `src/main/java/it/patric/cittaexp/custommobs/CustomMobSubsystem.java`
- `src/main/java/it/patric/cittaexp/custommobs/MobEffectsEngine.java`
- `src/main/java/it/patric/cittaexp/defense/CityDefenseService.java`

### 10. Test harness runtime ancora insufficiente

#### Problema
I test attuali coprono bene il parsing, ma non chiudono ancora i casi piu delicati:
- transizioni di fase reali
- summon tracking in difesa
- cleanup projectile/sessione
- bossbar lifecycle
- local broadcast targeting locale

#### Paper API da usare
- [Event API: listeners](https://docs.papermc.io/paper/dev/event-api/listeners/)
- [Custom events](https://docs.papermc.io/paper/dev/event-api/custom-events/)
  - solo se scegliamo di introdurre piccoli event hook interni per rendere testabili i passaggi encounter-critical

#### Deliverable
Espandere il test plan con:
- integration tests runtime custom mobs
- boss phase tests
- defense session tests con summon e projectile cleanup
- parser/content lint tests sui pack canonici bundled

#### Codebase refs
- `src/test/java/it/patric/cittaexp/custommobs/`
- `src/test/java/it/patric/cittaexp/defense/`
- `src/main/resources/custom-mobs/`

### 11. `paper-plugin.yml` / Folia declaration non presenti

#### Problema
Nel progetto c'e solo `plugin.yml` e non c'e ancora una dichiarazione esplicita Paper/Folia-oriented.

#### Paper API da usare
- [Supporting Paper and Folia](https://docs.papermc.io/paper/dev/folia-support/)

#### Deliverable
Valutare introduzione di `paper-plugin.yml` e dichiarazione di supporto solo quando il refactor scheduler del punto 1 e completato.

Questo non va fatto subito "per marketing"; va fatto solo quando il runtime e davvero coerente.

#### Codebase refs
- `src/main/resources/plugin.yml`

---

## Riferimenti Paper API da usare davvero

### Da usare subito
- [PDC](https://docs.papermc.io/paper/dev/pdc/)
  - marker entity/projectile/encounter
- [Audiences](https://docs.papermc.io/paper/dev/component-api/audiences/)
  - audience locale difesa, broadcast encounter
- [Scheduling](https://docs.papermc.io/paper/dev/scheduler/)
  - idle tick, projectile runtime, cleanup scheduling
- [Supporting Paper and Folia](https://docs.papermc.io/paper/dev/folia-support/)
  - shape corretta del runtime scheduler
- [Particles](https://docs.papermc.io/paper/dev/particles/)
  - visual authored, projectile trail, phase telegraph
- [Registries](https://docs.papermc.io/paper/dev/registries/)
  - resolver robusti per sound/particle/typed values
- [Listeners](https://docs.papermc.io/paper/dev/event-api/listeners/)
  - wiring trigger runtime e hooks encounter
- [Command API](https://docs.papermc.io/paper/dev/command-api/basics/introduction/)
  - tooling staff custommobs

### Da usare solo se servono davvero
- [Custom events](https://docs.papermc.io/paper/dev/event-api/custom-events/)
  - solo per rendere piu testabile l'encounter runtime o aprire hook interni chiari
- [Plugin configuration](https://docs.papermc.io/paper/dev/configuration/)
  - di supporto per manifest o policy authored, non come core runtime
- [Lifecycle API](https://docs.papermc.io/paper/dev/api/lifecycle/)
  - non necessaria oggi per chiudere i custom mobs

### Da non introdurre nel core custom mobs
- Dialog API
- Menu Type API
- Plugin messaging
- Datapack discovery
- Data components

Non riducono il codice che ci manca oggi nel dominio `custom mobs`.

---

## Roadmap finale per chiudere davvero il sistema

### Fase A - Runtime hardening Paper-native
Obiettivo:
- sostituire scheduler Bukkit raw con un runtime scheduler dedicato e preparato a Folia
- introdurre projectile runtime tracking/cancellation

Deliverable:
- `CustomMobRuntimeScheduler`
- `ProjectileRuntimeService`
- lifecycle encounter-aware per projectile e idle tasks

Refs:
- Paper: Scheduling, Supporting Paper and Folia, Particles
- Codebase:
  - `src/main/java/it/patric/cittaexp/custommobs/MobEffectsEngine.java`
  - `src/main/java/it/patric/cittaexp/defense/CityDefenseService.java`

### Fase B - Resolver, parsing e PDC schema finali
Obiettivo:
- eliminare parsing deprecated e rendere il schema PDC completo

Deliverable:
- `CustomMobTypeResolver`
- marker nuovi `session_id`, `defense_tier`, `boss_flag`, `phase_id`, `encounter_id`, `projectile_owner_uuid`
- error model parser unico

Refs:
- Paper: Registries, PDC
- Codebase:
  - `src/main/java/it/patric/cittaexp/custommobs/CustomMobParsingSupport.java`
  - `src/main/java/it/patric/cittaexp/custommobs/CustomMobMarkers.java`
  - `src/main/java/it/patric/cittaexp/custommobs/MobEffectsEngine.java`

### Fase C - Audience e boss encounter orchestration
Obiettivo:
- togliere la logica encounter/bossbar da `CityDefenseService` e centralizzarla

Deliverable:
- `BossEncounterState`
- `DefenseEncounterAudienceService`
- gestione locale di bossbar e broadcast incontro

Refs:
- Paper: Audiences, Event API listeners
- Codebase:
  - `src/main/java/it/patric/cittaexp/defense/CityDefenseService.java`
  - `src/main/resources/config.yml`

### Fase D - Registry query + content pipeline canonica
Obiettivo:
- preparare il sistema alla generazione di molti mob custom senza caos authored

Deliverable:
- query `findByTag`, `findByPool`, `findByArc`
- metadati authored di pool/theme/arc
- defaults manifest completi e coerenti
- un solo set canonico seeded

Refs:
- Paper: nessuna API centrale, solo supporto config
- Codebase:
  - `src/main/java/it/patric/cittaexp/custommobs/CustomMobRegistry.java`
  - `src/main/resources/custom-mobs/`
  - `src/main/resources/*/defaults.txt`

### Fase E - Tooling staff e diagnostics
Obiettivo:
- poter lavorare sui custom mobs senza aprire il codice ad ogni problema

Deliverable:
- `staff custommobs probe`
- `staff custommobs lint`
- `staff custommobs reload`
- `staff custommobs spawn <mobId>`
- `staff custommobs dump <uuid>`
- integrazione nel `probe` generale

Refs:
- Paper: Command API
- Codebase:
  - `src/main/java/it/patric/cittaexp/command/CittaExpCommandTree.java`
  - `src/main/java/it/patric/cittaexp/custommobs/CustomMobSubsystem.java`
  - `src/main/java/it/patric/cittaexp/custommobs/MobEffectsEngine.java`

### Fase F - Test closure e dichiarazione platform support
Obiettivo:
- chiudere i test runtime e solo dopo dichiarare il supporto Paper/Folia nel packaging plugin

Deliverable:
- integration tests su phase/summon/projectile/cleanup
- lint test sui bundled packs
- eventuale `paper-plugin.yml` quando il runtime scheduler e davvero coerente

Refs:
- Paper: Supporting Paper and Folia, Event API
- Codebase:
  - `src/test/java/it/patric/cittaexp/custommobs/`
  - `src/test/java/it/patric/cittaexp/defense/`
  - `src/main/resources/plugin.yml`

---

## Definition of Done del blocco custom mobs

Il sistema `custom mobs` si considera davvero chiuso quando sono vere tutte queste condizioni:
- nessun parsing runtime core usa `valueOf/getByName` deprecated per tipi authored chiave
- idle/projectile runtime non dipendono da scheduler Bukkit raw sparsi nel motore
- projectile e summon sono encounter-aware e cleanup-safe
- boss encounter state non e piu sparso dentro `CityDefenseService`
- audience locale difesa e bossbar sono gestite da un servizio dedicato
- PDC copre identity, provenance, encounter e projectile lineage
- esiste tooling staff per `probe/lint/reload/spawn/dump`
- il seeded authored pack e canonico e coerente con i manifest `defaults.txt`
- i test coprono parser + runtime + defense lifecycle
- solo a quel punto si valuta il packaging Paper/Folia dichiarato

---

## Conclusione operativa
Il sistema non e lontano dalla chiusura, ma non e ancora finito nel senso corretto del termine.

Il grosso manca in quattro blocchi:
1. runtime scheduler/lifecycle
2. resolver/parser e PDC finali
3. audience/boss encounter orchestration
4. tooling + diagnostics + test closure

Una volta chiusi questi quattro blocchi, la generazione massiva di mob custom avra una base stabile e non costringera a rifare il core.
