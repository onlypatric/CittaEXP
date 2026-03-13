# HuskTowns/CittaEXP Command Mapping (stato attuale, riorganizzato)

Fonte HuskTowns: [Commands](https://william278.net/docs/husktowns/commands), [API](https://william278.net/docs/husktowns/api), [Towns API](https://william278.net/docs/husktowns/towns-api), [Claims API](https://william278.net/docs/husktowns/claims-api)

Legenda:
- `completato`: copertura funzionale presente nel wrapper CittaEXP
- `parziale`: copertura presente ma non 1:1 o con limitazioni volontarie
- `non implementato`: non esposto nel wrapper CittaEXP
- `migrato in GUI`: path testuale rimosso, funzione disponibile in `/city` GUI-first

## 1) Contratto comandi reale esposto oggi

### 1.1 Player (`/city`)

| Comando | Mapping HuskTowns | Tipo | Stato |
|---|---|---|---|
| `/city` | apre GUI principale membri/azioni città | custom overlay | completato |
| `/city create` | `createTown(player, name)` + metadata/tag CittaEXP | custom API | completato |
| `/city list` | apre browser città GUI (ordinamenti + warp click) | custom overlay + API | completato |
| `/city invite <player>` | bridge `town invite <player>` con precheck membership/privilegio/cap membri | bridge + precheck | completato |
| `/city invite accept [player]` | bridge `town invite accept [player]` | bridge | completato |
| `/city invite decline [player]` | bridge `town invite decline [player]` | bridge | completato |
| `/city section` | apre claim map GUI 9x6 (claim/unclaim/edit type) | custom overlay + API | completato |
| `/city chat [message]` | bridge `town chat [message]` con precheck `CHAT` | bridge + precheck | completato |
| `/city level` | stato progression custom L8 | custom overlay | completato |
| `/city level upgrade` | upgrade stage custom + update town HuskTowns | custom overlay + API | completato |
| `/city level request` | crea request staff locale per stage staff-gated | custom overlay | completato |
| `/city setwarp` | `editTown(...): setSpawn(...)` con validazione inside-claim | custom API | completato |
| `/city warp [town]` | teleport spawn via manager HuskTowns | custom API | completato |

Comandi testuali rimossi dal player (hard-cut, migrati in GUI):
- `/city promote`
- `/city demote`
- `/city evict`
- `/city transfer`
- `/city leave`
- `/city edit`
- `/city rename`
- `/city disband`
- `/city rules`
- `/city deposit`
- `/city withdraw`
- `/city nearby`

### 1.2 Staff/Ops (`/cittaexp`)

| Comando | Tipo | Stato |
|---|---|---|
| `/cittaexp probe` | diagnostica runtime/store/integrazione | completato |
| `/cittaexp staff level list [status]` | lista richieste upgrade | completato |
| `/cittaexp staff level approve <requestId> [note]` | approvazione request + applicazione side-effect | completato |
| `/cittaexp staff level reject <requestId> <reason>` | rifiuto request | completato |
| `/cittaexp staff city players <citta>` | apertura GUI membri su città target (staff override) | completato |

### 1.3 Policy comandi nativi HuskTowns

- `/town level` bloccato ai player: usare `/city level`.
- `/town privacy` bloccato ai player: usare GUI `/city`.
- `/town` resta backbone autoritativo, ma UX player primaria è `/city`.

## 2) Cosa fa oggi la GUI `/city` (feature operative)

### 2.1 GUI membri + moderazione

| Feature GUI `/city` | Mapping HuskTowns | Stato |
|---|---|---|
| Lista membri town propria (9x6 paginata) | `Town#getMembers`, username async | completato |
| Dettaglio membro | read town/member + role ladder | completato |
| Promote/Demote/Evict/Transfer | own-town: bridge `town ...`; staff-target: override `editTown(...)` | completato |
| Leave città | bridge `town leave` con dialog conferma | completato |
| Edit città | Dialog root + `setBio/setGreeting/setFarewell/setTextColor` + rename flow | completato |

### 2.2 Azioni città (sottomenu)

| Voce Azioni | Mapping | Stato |
|---|---|---|
| Banca città | saldo + dialog importo -> bridge `town deposit/withdraw` | completato |
| Lista città | apre `/city list` GUI | completato |
| Gestione territori | apre `/city section` claim map GUI | completato |
| Impostazioni claim | sottomenu dedicato (privacy + claim rules) | completato |
| Elimina città | apre dialog disband e bridge `town disband confirm` | completato |

### 2.3 Nuovo flow “Impostazioni claim”

| Feature | Dettaglio | Stato |
|---|---|---|
| Switch privacy warp | toggle immediato (con gate stage policy) | completato |
| CLAIM/FARM/PLOT settings | Dialog con testo + toggle flag dinamici + Salva/Annulla | completato |
| Flag source | dinamico da `HuskTownsAPI.getFlagSet()` | completato |
| Save batch | apply regole claim type in unica mutazione `editTown(...)` | completato |

### 2.4 Claim map GUI (`/city section`)

| Feature | Dettaglio | Stato |
|---|---|---|
| Mappa 9x6 chunk | viewport navigabile | completato |
| LMB | claim/unclaim | completato |
| RMB | ciclo tipo claim `CLAIM -> FARM -> PLOT` | completato |
| Overlay HuskClaims | chunk bloccati evidenziati e non claimabili | completato |
| Guard anti-overlap HuskClaims↔HuskTowns | hook nativo preferito + fallback event-driven CittaEXP (create/resize) | completato |
| Regole extra | range max, adiacenza claim (eccetto primo), cap stage, protezione spawn-claim | completato |

## 3) Mapping comandi HuskTowns: copertura e backlog

### 3.1 `/town` coperti (diretti o via GUI)

| Comando HuskTowns | Stato wrapper CittaEXP | Note |
|---|---|---|
| `town leave` | completato | migrato in GUI `/city` |
| `town deposit/withdraw` | completato | migrato in GUI `/city` -> Banca |
| `town disband` | completato | migrato in GUI `/city` -> Azioni -> Elimina città |
| `town rules` | completato | migrato in GUI `/city` -> Impostazioni claim |
| `town create` | completato | via `/city create` |
| `town claim/unclaim/edit` | completato | via `/city section` GUI |
| `town invite/accept/decline` | completato | via `/city invite ...` |
| `town promote/demote/evict/transfer` | completato | migrato in GUI `/city` |
| `town spawn [town]` | completato | via `/city warp [town]` |
| `town setspawn` | completato | via `/city setwarp` |
| `town privacy` | parziale | policy GUI-first, comando nativo bloccato ai player |
| `town level` | parziale | sostituito da `/city level` custom |
| `town chat` | completato | via `/city chat` |
| `town list` | completato | via `/city list` GUI |

### 3.2 `/town` non ancora esposti nel wrapper

| Comando HuskTowns | Stato |
|---|---|
| `/town help` | non implementato |
| `/town about` | non implementato |
| `/town autoclaim` | non implementato |
| `/town clearspawn` | non implementato |
| `/town player <player>` | non implementato |
| `/town deeds` | non implementato |
| `/town census` | non implementato (coperto solo in parte via GUI membri) |
| `/town relations ...` | non implementato |
| `/town war ...` | non implementato |
| `/town log` | non implementato |

### 3.3 `/admintown`

Intero namespace `/admintown` nel wrapper CittaEXP: **non implementato**.

## 4) Nota operativa per i prossimi step

- Il percorso ufficiale lato player è ormai GUI-first (`/city` root).
- Le mutazioni sensibili non dovrebbero tornare in comandi testuali se già coperte da Dialog/GUI.
- Se introduciamo nuove feature, priorità a:
  - integrazione nel menu Azioni,
  - conferma dialog per azioni distruttive,
  - mapping esplicito HuskTowns -> CittaEXP nel presente file.
