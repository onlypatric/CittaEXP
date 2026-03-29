# 13 - Roadmap Evoluzione Citta (v2, decision-complete)

## Obiettivo
Trasformare i contenuti di `12-EVOLUZIONE-CITTA.md` in un piano esecutivo **M1..M12** pronto per implementazione, mantenendo tutte le informazioni funzionali senza perdita e senza entrare in dettagli di codice.

## Decisioni bloccate
- Rollout: **hard-cut unico**.
- Audience: **team interno CittaEXP**.
- UX: **GUI-first** su `/city`, nessun nuovo path player command-centrico.
- Scope wave: **solo documentale** (nessuna modifica Java/runtime in questa fase).
- Fonte canonica idee: `12-EVOLUZIONE-CITTA.md` resta invariato come documento sorgente.

## Allineamento con roadmap quest esistente
Questa roadmap estende e dettaglia la direzione già definita in `04-ROADMAP-IMPLEMENTAZIONE.md`, concentrandosi su evoluzione prodotto/struttura del sistema città (Atlas/Board/Race/Season) con taglio decision-complete.

---

## Milestone M1 — Progressione Città a due assi (XP + Seals)
### Scope
Definire il modello progressione città con doppio asse (`City XP`, `City Seals`), stage target e regole promozione.

### Checklist
- [x] Formalizzare stage target (`Avamposto`, `Borgo`, `Villaggio`, `Cittadina`, `Città`, `Regno`).
- [x] Definire requisiti promozione stage (livello, treasury, infrastruttura, seals, famiglie Atlas).
- [x] Definire regola emissione `City Seals` (60% capitolo/famiglie).
- [x] Esplicitare unlock principali per stage.

### Deliverable
- Specifica stage/tier finale con tabelle requisiti.
- Regole ufficiali di avanzamento stage, inclusi blocchi e prerequisiti.

### Dipendenze
- Nessuna (milestone fondazionale).

### DoD
- Tabella stage e promozioni completa, senza `TBD`.
- Regole Seals e gating stage documentate e verificabili.

### Rischi + mitigazioni
- Rischio: stage troppo aggressivi o troppo lenti.
- Mitigazione: includere range tuning e criteri di revisione post-smoke.

### Gate
- [x] spec pronta
- [x] impatti runtime esplicitati
- [x] test plan definito
- [x] rollback path definito

**Stato:** COMPLETATA il 2026-03-15 (runtime allineato).

---

## Milestone M2 — Tassonomia Quest + Regole di demote delle attività passive
### Scope
Consolidare il framework in 4 layer (`Atlas`, `Board`, `Races`, `Season Codex`) e fissare il ruolo non-core di attività passive.

### Checklist
- [x] Congelare tassonomia ufficiale dei layer.
- [x] Definire cosa non è più core progression (`playtime`, `xp pickup`, parte economy/resource).
- [x] Definire nuovo ruolo “filler/supporto” per quest passive.

### Deliverable
- Documento di tassonomia e policy di priorità contenuti.
- Matrice core vs supporto per ogni objective family.

### Policy lock runtime (M2)
| Objective | Ruolo | Mode consentite | Race | XP città |
| --- | --- | --- | --- | --- |
| `PLAYTIME_MINUTES` | SUPPORT | `DAILY_STANDARD` | vietata | disabilitata |
| `XP_PICKUP` | SUPPORT | `DAILY_STANDARD`, `WEEKLY_STANDARD` | vietata | disabilitata |
| `ECONOMY_ADVANCED` | SUPPORT | `WEEKLY_STANDARD`, `MONTHLY_STANDARD` | vietata | disabilitata |
| `RESOURCE_CONTRIBUTION` | SUPPORT | `WEEKLY_STANDARD`, `MONTHLY_STANDARD` | vietata | disabilitata |

### Dipendenze
- M1 completata.

### DoD
- Ogni family classificata come `core`, `supporto`, o `bloccata per race`.
- Nessuna ambiguità su fonti XP principali.

### Rischi + mitigazioni
- Rischio: perdita engagement su contenuti casual.
- Mitigazione: mantenere missioni passive come filler con reward non-centrate su XP.

### Gate
- [x] spec pronta
- [x] impatti runtime esplicitati
- [x] test plan definito
- [x] rollback path definito

**Stato:** COMPLETATA il 2026-03-15 (policy runtime attive).

---

## Milestone M3 — City Atlas (modello reward + capitoli)
### Scope
Definire il modello Atlas fisso/permanente con tier standard (I/II/III/IV elite), reward band e capitoli.

### Checklist
- [x] Definire reward band per tier Atlas.
- [x] Definire capitoli Atlas e obiettivo di prodotto per ciascuno.
- [x] Definire policy XP alta/media/bassa/no-XP per famiglie.

### Deliverable
- Spec capitoli Atlas completa (Extraction, Combat, Agronomy, Industry, Frontier, Civic).
- Policy reward per tier e per chapter.

### Dipendenze
- M1, M2.

### DoD
- Ogni chapter ha ruolo, intensità XP e reward focus esplicitati.
- Tier IV elite regolato con criteri di accesso/documentati.

### Rischi + mitigazioni
- Rischio: Atlas eccessivamente grindy.
- Mitigazione: introdurre pacing per tier e cap progressivo stage-based.

### Gate
- [x] spec pronta
- [x] impatti runtime esplicitati
- [x] test plan definito
- [x] rollback path definito

**Stato:** COMPLETATA il 2026-03-15 (runtime Atlas live con chapter/tier/reward policy).

---

## Milestone M4 — City Atlas (catalogo famiglie e target)
### Scope
Portare il catalogo famiglie Atlas a livello implementabile, con tier target e naming scheme.

### Checklist
- [x] Validare tutte le famiglie Atlas per chapter.
- [x] Definire target I/II/III (ed eventuale IV elite) per famiglia.
- [x] Allineare naming scheme definitivo IDs.

### Deliverable
- Catalogo completo famiglie Atlas con target e naming canonico.

### Dipendenze
- M3.

### DoD
- 0 famiglie senza target o naming.
- Mapping chapter -> famiglie -> tier tracciabile.

### Rischi + mitigazioni
- Rischio: overlap tra famiglie simili.
- Mitigazione: introdurre regole anti-duplicazione di family scope.

### Gate
- [x] spec pronta
- [x] impatti runtime esplicitati
- [x] test plan definito
- [x] rollback path definito

**Stato:** COMPLETATA il 2026-03-15 (catalogo `atlas.yml` canonico + tracking/claim runtime).

---

## Milestone M5 — City Board Generator (filosofia, algoritmo, formula target)
### Scope
Definire generazione guidata del Board (no random puro), input algoritmo, weighting, hard-rules e formula target.

### Checklist
- [x] Definire input del generatore (stage, season, stato città, pool eligible).
- [x] Definire weighting e hard-rules (varietà, cooldown, no-cluster).
- [x] Definire formula target con multiplier stage/cycle/base template/eccezioni.

### Deliverable
- Specifica generatore Board con pseudoflow e priorità di selezione.
- Formula target canonica con parametri di tuning.

### Dipendenze
- M2, M4.

### DoD
- Processo di selezione deterministico e ripetibile.
- Formula target completa con eccezioni documentate.

### Rischi + mitigazioni
- Rischio: generatore produce missioni ripetitive.
- Mitigazione: hard-rules su diversità + cooldown family/template.

### Gate
- [x] spec pronta
- [x] impatti runtime esplicitati
- [x] test plan definito
- [x] rollback path definito

**Stato:** COMPLETATA il 2026-03-15 (`m5-v1` live con formula, segnali pesati 50/30/20, hard-rules e diagnostica probe).

---

## Milestone M6 — Daily + Weekly board definitive
### Scope
Chiudere la struttura daily/weekly: slot, reset, eligibilità, bonus primi completamenti, tipi forti.

### Checklist
- [x] Definire composizione finale Daily Board (slot e categorie).
- [x] Definire composizione Weekly Operations (slot, peso attività, vincoli).
- [x] Definire bonus primi completamenti e limiti.

### Deliverable
- Blueprint operativo daily/weekly definitivo.

### Policy lock runtime (M6)
| Area | Lock |
| --- | --- |
| Daily board | `3` standard + `2` race hidden (reveal a finestra) |
| Weekly board | `3` missioni per ciclo |
| First-completion | race winner bonus + weekly first-completion bonus (1x per citta/settimana, idempotente) |
| UI Missioni | tab Daily piramide 5 slot con placeholder hidden race; tab Weekly mostra stato bonus (`Disponibile`/`Riscosso`) |

### Dipendenze
- M5.

### DoD
- Daily/weekly completamente specificate per generazione e reward.
- Nessun campo “da decidere” per slot/reset/bonus.

### Rischi + mitigazioni
- Rischio: weekly troppo simili alle daily.
- Mitigazione: imporre objective families dedicate weekly.

### Gate
- [x] spec pronta
- [x] impatti runtime esplicitati
- [x] test plan definito
- [x] rollback path definito

**Stato:** COMPLETATA il 2026-03-15 (runtime lock 3+2/3, bonus weekly-first live, probe M6 attivo).

---

## Milestone M7 — Monthly Ledger (Grand Project + Contracts + Mystery)
### Scope
Rendere il monthly un layer composito (non singola quest grossa), con `Grand Project`, `Contracts`, `Mystery Contract`.

### Checklist
- [x] Definire i tipi di Grand Project (Monument/Expedition/War Supply/Industrial).
- [x] Definire monthly contracts secondari.
- [x] Definire mystery contract e regole di reveal.

### Deliverable
- Struttura mensile finale con varianti e pacing.

### Policy lock runtime (M7)
| Area | Lock |
| --- | --- |
| Monthly Ledger | `1` Grand + `5` Contracts + `1` Mystery per città/ciclo mensile |
| Grand | stato unico a completion; evoluzione multi-step predisposta nel dominio/runtime state |
| Legacy monthly event | `MONTHLY_EVENT_A/B` invalidati al cutover e non più emessi |

### Dipendenze
- M5.

### DoD
- Monthly documentato come sistema multi-blocco con criteri emissione.
- Reward monthly coerenti con difficoltà e durata.

### Rischi + mitigazioni
- Rischio: monthly troppo complesso da leggere.
- Mitigazione: UI summary compatta + drill-down dedicato.

### Gate
- [x] spec pronta
- [x] impatti runtime esplicitati
- [x] test plan definito
- [x] rollback path definito

**Stato:** COMPLETATA il 2026-03-15 (Monthly Ledger live con emissione `1+5+1`, cutover legacy applicato).

---

## Milestone M8 — City Races definitive (daily/weekly/monthly crown)
### Scope
Definire race windows, durata, eligibilità, rotazioni tema e reward competitive.

### Checklist
- [x] Definire Daily Sprint (orari, durata, eligible, rotazione settimanale).
- [x] Definire Weekly Clash (formato e governance).
- [x] Definire Monthly Crown (evento competitivo lungo).
- [x] Definire reward race (winner/podio/soglia).

### Deliverable
- Specifica race end-to-end con policy timeline e payout.

### Policy lock runtime (M8)
| Area | Lock |
| --- | --- |
| Daily Sprint | finestre `18:30` e `21:30`, reveal `T-10`, timeout `45m`, gate `Villaggio+` |
| Weekly Clash | Sabato `21:00`, timeout `90m`, gate `Cittadina+` |
| Monthly Crown | prima domenica `18:00`, timeout `120m`, gate `Città+` |
| Reward race | winner-only su race M8 (top2/top3/threshold non applicati) |

### Dipendenze
- M6, M7.

### DoD
- Ogni race mode ha timing, obiettivi ammessi, e reward policy complete.
- Criteri winner/finalizzazione definiti.

### Rischi + mitigazioni
- Rischio: race sbilanciate verso mega-città.
- Mitigazione: scaling e target tuning separati per modalità competitive.

### Gate
- [x] spec pronta
- [x] impatti runtime esplicitati
- [x] test plan definito
- [x] rollback path definito

**Stato:** COMPLETATA il 2026-03-15 (Race Suite M8 live con gating stage e finalize winner-only).

---

## Milestone M9 — Season Codex (3 mesi)
### Scope
Definire progressione stagionale trimestrale, capitoli, punti stagione, milestone e temi.

### Checklist
- [x] Definire struttura stagione (chapter flow e unlock progressivo).
- [x] Definire Seasonal Points.
- [x] Definire milestone stagionali e reward bands.
- [x] Definire temi per stagione (Primavera/Estate/Autunno/Inverno).

### Deliverable
- Framework stagionale completo, pronto per emissione live.

### Policy lock runtime (M9)
| Area | Lock |
| --- | --- |
| Ciclo stagione | 3 mesi, 3 act progressivi + elite + hidden relic |
| Unlock | Act successivo al `50%` completion del precedente |
| Emissione per città | `8 + 8 + 8 + 3 + 1` (Act I/II/III, Elite, Hidden Relic) |
| Seasonal Points | `minor=5`, `major=10`, `elite=20`, `hidden relic=25` |
| Legacy seasonal race | `SEASONAL_RACE` invalidata al cutover e non più emessa |

### Dipendenze
- M1, M2, M8.

### DoD
- Stagione completa con regole inizio/fine, scoring e milestone.
- Mapping temi -> famiglie challenge presente.

### Rischi + mitigazioni
- Rischio: season disallineata con contenuti board/race.
- Mitigazione: tabella di coerenza season-theme vs pool eligible.

### Gate
- [x] spec pronta
- [x] impatti runtime esplicitati
- [x] test plan definito
- [x] rollback path definito

**Stato:** COMPLETATA il 2026-03-15 (Season Codex live con SP, milestone e unlock progressivi).

---

## Milestone M10 — Reward Model unificato (XP, materiali, token)
### Scope
Consolidare classi reward e regole “quest fisse vs cicliche”, inclusi token e policy low/no XP.

### Checklist
- [x] Definire reward classes (`XP`, `Material`, `Token`).
- [x] Definire quali quest devono dare XP alta vs bassa/zero.
- [x] Definire struttura payout per completion/excellence/competitiva.

### Deliverable
- Matrice reward completa per family/ciclo/tier.

### Dipendenze
- M3-M9.

### DoD
- Ogni missione mappata a reward class e intensità XP.
- Policy anti-overreward documentata.

### Rischi + mitigazioni
- Rischio: inflazione reward.
- Mitigazione: cap e bands per ciclo + review periodica KPI.

### Gate
- [x] spec pronta
- [x] impatti runtime esplicitati
- [x] test plan definito
- [x] rollback path definito

**Stato:** COMPLETATA il 2026-03-15 (reward policy runtime unificata, preview safe con descrizioni comando, token vanity via console command).

---

## Milestone M11 — Storage abstraction + failover operativo
### Scope
Completare storage challenge con MySQL primario + SQLite fallback/replay, cutover hard e diagnostica operativa.

### Checklist
- [x] Definire backend contract e state machine (`normal/degraded/recovery`).
- [x] Definire cutover hard con full reset challenge state.
- [x] Definire criteri osservabilità store/outbox/replay per probe.

### Deliverable
- Runtime storage challenge con fallback/replay idempotente e KPI M11 in probe.

### Dipendenze
- M5-M10.

### DoD
- MySQL up/down gestito senza blocchi runtime.
- Replay/outbox senza duplicazioni reward (`grant_key`).

### Rischi + mitigazioni
- Rischio: drift tra backend durante recovery.
- Mitigazione: outbox idempotente + reason-code diagnostici.

### Gate
- [x] spec pronta
- [x] impatti runtime esplicitati
- [x] test plan definito
- [x] rollback path definito

**Stato:** COMPLETATA il 2026-03-15 (M11 cutover full reset, state machine store attiva, probe M11 allineato).

---

## Milestone M12 — UX/UI finale + configurazione + consolidamento
### Scope
Finalizzare UX `/city missions`, tab principali (Atlas/Board/Race/Season), pulizia macroaree, configurazione finale e risultato atteso.

### Checklist
- [x] Definire UX tabs finali e information hierarchy.
- [x] Definire cosa eliminare/fondere dal sistema attuale.
- [x] Definire configurazione finale consigliata (reset, race windows, stage gating).
- [x] Definire risultato finale target di prodotto.

### Deliverable
- Specifica UX e configurazione finale pronta per hard-cut.

### Dipendenze
- M1..M11.

### DoD
- Percorso utente completo e coerente (GUI-first) documentato.
- Tutte le aree legacy coperte da piano di sostituzione.

### Rischi + mitigazioni
- Rischio: inconsistenza navigazione tra GUI nuove/legacy.
- Mitigazione: routing matrix unica + regole back/parent fisse.

### Gate
- [x] spec pronta
- [x] impatti runtime esplicitati
- [x] test plan definito
- [x] rollback path definito

**Stato:** COMPLETATA il 2026-03-15 (no-biome policy, lore missioni player-first, routing `/city` hard-cut con parent canonico).

---

## Piano cutover hard-cut
1. **Freeze configurazione**: congelare versioni di `challenges.yml` e testi missione per evitare drift durante il cutover.
2. **Switch unico**: attivare nuova tassonomia (Atlas/Board/Race/Season) in un solo rilascio.
3. **Reset cicli attivi**: invalidare missioni legacy attive e rigenerare con modello nuovo.
4. **Conferma post-switch**: validare GUI tabs, pool missioni e reward preview su smoke completo.
5. **Fallback**: mantenere snapshot configurazione pre-cutover per rollback veloce.

---

## Acceptance globale
- [x] Copertura completa delle sezioni `#1..#21` di `12-EVOLUZIONE-CITTA.md`.
- [x] Milestone `M1..M12` complete di `Scope`, `Checklist`, `Deliverable`, `Dipendenze`, `DoD`, `Gate`, `Rischi+mitigazioni`.
- [x] Nessun `TBD` bloccante nelle milestone.
- [x] Piano cutover hard-cut definito.
- [x] Compatibilità dichiarata con `04-ROADMAP-IMPLEMENTAZIONE.md`.

---

## Matrice copertura (no-loss): sezione originale -> milestone nuova

| Sezione sorgente (`12-EVOLUZIONE-CITTA.md`) | Milestone target |
| --- | --- |
| `#1 Ridisegno Progressione Città` | M1 |
| `#2 Nuova Tassonomia Quest` | M2 |
| `#3 Regola fondamentale del redesign` | M2 |
| `#4 City Atlas — catalogo fisso` | M3-M4 |
| `#5 City Board — rotazione guidata` | M5 |
| `#6 Generazione Board — algoritmo` | M5 |
| `#7 Base target system` | M5 |
| `#8 Pool pronto da reimplementare` | M6-M7 |
| `#9 Quest fisse vs cicliche — reward` | M10 |
| `#10 Daily Board definitivo` | M6 |
| `#11 Weekly Operations definitive` | M6 |
| `#12 Monthly Ledger definitivo` | M7 |
| `#13 City Races definitive` | M8 |
| `#14 Season Codex — 3 mesi` | M9 |
| `#15 Full redesign quest pronte` | M4-M7 |
| `#16 Naming scheme definitivo` | M4 |
| `#17 Anti-abuse definitivo` | M11 |
| `#18 UI definitiva` | M12 |
| `#19 Cosa eliminerei o fonderei` | M12 |
| `#20 Configurazione finale consigliata` | M12 |
| `#21 Risultato finale` | M12 |

Esito atteso no-loss: **0 sezioni orfane**.

---

## Appendice — Backlog post-M12
- Bilanciamento live data-driven (micro-tuning dei target per family/cycle).
- Telemetria avanzata per completion heatmap per stage.
- Eventi speciali fuori calendario season (one-shot server events).
- Pacchetti reward cosmetici città di lungo periodo.
- Iterazioni UX su narrativa missioni e onboarding città nuove.
