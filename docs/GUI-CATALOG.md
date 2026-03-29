# CittaEXP GUI Catalog (Inventory + Dialog)

Stato: runtime attuale (snapshot codebase)

Questo documento descrive tutte le interfacce GUI oggi presenti nel plugin, con:
- entrypoint
- access policy (player/staff)
- layout slot
- azioni principali

## 1) Inventory GUI (chest-style)

## 1.1 `/city` - Membri citta (root)
- Service: `CityPlayersGuiService`
- Entry: `/city` (player), `/cittaexp staff city players <citta>` (staff target)
- Size: `9x6` (54)
- Access:
  - player: propria citta
  - staff: citta target in read/admin mode
- Layout:
  - `0..44`: lista membri (PLAYER_HEAD)
  - `45`: prev page
  - `47`: `Azioni` (player) / `Relazioni (read-only)` (staff)
  - `49`: page indicator
  - `51`: `Progressione`
  - `53`: next page
- Click:
  - click membro -> apre dettaglio membro
  - click `Azioni` -> menu Azioni citta (player)
  - click `Relazioni` -> GUI relazioni (staff view)
  - click `Progressione` -> Progression Hub

## 1.2 `/city` - Dettaglio membro
- Service: `CityPlayersGuiService`
- Entry: da lista membri
- Size: `9x6`
- Layout:
  - `13`: head membro
  - `22`: info ruolo/stato
  - `31`: status online/last seen
  - `29`: ACL vault deposit ON/OFF (se autorizzato)
  - `33`: ACL vault withdraw ON/OFF (se autorizzato)
  - `30`: promote (se autorizzato)
  - `32`: demote (se autorizzato)
  - `39`: evict (se autorizzato)
  - `41`: transfer (se autorizzato)
  - `45`: back
  - `53`: close
- Click:
  - azioni governance -> dialog conferma
  - ACL vault -> toggle immediato + refresh

## 1.3 `/city` - Azioni citta
- Service: `CityPlayersGuiService`
- Entry: bottone `Azioni` dalla root `/city`
- Size: `9x6`
- Layout:
  - `9`: Relazioni
  - `10`: Banca citta
  - `11`: Leave
  - `12`: Lista citta
  - `13`: Gestione territori (`/city section` map)
  - `14`: Impostazioni claim
  - `15`: Modifica citta (Dialog edit)
  - `16`: Elimina citta (Dialog disband)
  - `20`: Item Vault
  - `18`: back
  - `26`: close
- Note:
  - in staff-view alcune azioni mutanti sono bloccate

## 1.4 `/city` - Banca citta
- Service: `CityPlayersGuiService`
- Entry: Azioni -> Banca
- Size: `9x3` (27)
- Layout:
  - `11`: Deposita
  - `13`: Saldo attuale
  - `15`: Preleva
  - `18`: back
  - `26`: close
- Click:
  - Deposita/Preleva aprono Dialog input importo (regex positivo)
  - dispatch via bridge town command, poi refresh GUI

## 1.5 `/city section` - Claim Map
- Service: `CitySectionMapGuiService`
- Entry: `/city section` o Azioni -> Gestione territori
- Size: `9x6`
- Layout:
  - `0..44`: viewport mappa chunk (9x5)
  - `45`: su
  - `46`: sinistra
  - `47`: centra su player
  - `48`: destra
  - `49`: giu
  - `50`: legenda own claim
  - `51`: legenda wilderness
  - `52`: legenda blocked
  - `53`: close
- Click:
  - left su cella: claim/unclaim contestuale
  - right su own claim: ciclo tipo `CLAIM -> FARM -> PLOT -> CLAIM`
- Vincoli:
  - range max 8 chunk
  - check cap/stage
  - check adiacenza
  - blocco HuskClaims overlay

## 1.6 `/city` - Impostazioni claim (submenu)
- Service: `CityClaimSettingsMenuService`
- Entry: Azioni -> Impostazioni claim
- Size: `9x3`
- Layout:
  - `11`: privacy warp toggle / locked by stage
  - `13`: CLAIM settings
  - `14`: FARM settings
  - `15`: PLOT settings
  - `18`: back
  - `26`: close
- Click:
  - privacy: toggle immediato (se policy consente)
  - CLAIM/FARM/PLOT: apre Dialog flag rules

## 1.7 `/city list` - Lista citta
- Service: `CityTownListGuiService`
- Entry: `/city list` o Azioni -> Lista citta
- Size: `9x6`
- Layout:
  - `0..44`: entry citta
  - `45`: prev
  - `47`: sort cycle
  - `49`: page indicator
  - `51`: close
  - `53`: next
- Sort modes:
  - XP custom
  - Tier HuskTowns
  - Money
  - Founded
  - Members
- Click entry:
  - tenta warp spawn town
  - blocco se spawn assente/privato (non-own town)

## 1.8 `/city` - Relazioni citta
- Service: `CityRelationsGuiService`
- Entry: Azioni -> Relazioni (player) / root slot relazioni in staff-view
- Size: `9x6`
- Layout:
  - `0..44`: altre citta (no admin, no self)
  - `45`: prev
  - `47`: back
  - `49`: page
  - `51`: close
  - `53`: next
- Ordinamento:
  - ALLY -> ENEMY -> NEUTRAL -> name
- Click entry:
  - player own-town: apre Dialog set relation
  - staff-view: read-only (no mutate)

## 1.9 `/city` - Progressione Hub
- Service: `CityProgressionHubGuiService`
- Entry: root `/city` -> bottone `Progressione`
- Size: `9x6`
- Layout main:
  - header `0..8`:
    - `0` summary town
    - `1` daily summary
    - `2` weekly summary
    - `3` monthly summary
    - `4` top daily
    - `5` top weekly
    - `6` top monthly
    - `7` weekly streak
    - `8` monthly event
  - body `9..44`: challenge cards (paginated, 36/page)
  - footer:
    - `45` prev
    - `46` livelli
    - `47` back (/city)
    - `48` recap corrente
    - `49` page
    - `50` archivio recap
    - `51` close
    - `52` sfide (detail)
    - `53` next
- Note:
  - in staff-view bottoni mutanti livelli/sfide sono read-only lock

## 1.10 Progressione Hub - Recap dettaglio
- Service: `CityProgressionHubGuiService`
- Entry: hub -> `Recap corrente` oppure click recap da archivio
- Size: `9x3`
- Layout:
  - `13`: recap item
  - `18`: back
  - `26`: close

## 1.11 Progressione Hub - Archivio recap
- Service: `CityProgressionHubGuiService`
- Entry: hub -> `Archivio recap`
- Size: `9x6`
- Layout:
  - `0..44`: recap history page
  - `45`: prev
  - `47`: back
  - `49`: page
  - `51`: close
  - `53`: next
- Click entry recap: apre recap dettaglio

## 1.12 Progressione - Dettaglio livelli
- Service: `CityLevelGuiService`
- Entry: Progressione Hub -> bottone Livelli
- Size: `9x6`
- Layout:
  - `0..44`: timeline livelli (45 righe/page)
  - `45`: prev
  - `47`: back
  - `49`: page indicator + summary
  - `51`: close
  - `53`: next
- Click row checkpoint:
  - se staff-gated -> crea request staff
  - altrimenti -> tenta upgrade diretto

## 1.13 Progressione - Dettaglio sfide
- Service: `CityChallengeGuiService`
- Entry: Progressione Hub -> bottone Sfide
- Size: `9x6`
- Layout list:
  - `0..44`: challenge cards
  - `45`: prev
  - `47`: back
  - `49`: page
  - `50`: gestione mayor (solo mayor)
  - `51`: close
  - `53`: next

## 1.14 Sfide - Gestione mayor
- Service: `CityChallengeGuiService`
- Entry: Sfide -> slot mayor
- Size: `9x6`
- Layout:
  - `22`: season status
  - `24`: veto status
  - `30`: reroll weekly package
  - `31`: milestone status
  - `45`: back
  - `53`: close
- Azioni:
  - reroll weekly full package
  - set veto category (dialog)

## 1.15 `/city` - Item Vault
- Service: `CityItemVaultGuiService`
- Entry: Azioni -> Item Vault
- Size: `9x6`
- Layout:
  - `0..44`: slot vault (45/page)
  - `45`: prev
  - `47`: back
  - `49`: page/perm summary
  - `51`: close
  - `53`: next
- Interazioni:
  - click top inventory (vault): withdraw verso player inventory (se ACL)
  - click bottom inventory (player): deposit nel vault (se ACL)

## 2) Dialog API flows (non chest inventory)

## 2.1 `/city create` - CityCreateDialogFlow
- Step 1: intro (Prossimo/Annulla)
- Step 2: form `city_name` + `city_tag`
- Validazioni:
  - nome: solo alfanumerico, no spazi/speciali
  - tag: 3 lettere
- Submit:
  - createTown via HuskTowns API
  - trace in YAML locale

## 2.2 `/city` member actions confirm
- Service: `DialogConfirmHelper` (riusato da CityPlayersGuiService)
- Usato per: promote/demote/evict/transfer/leave
- Pattern: `Conferma` / `Annulla`

## 2.3 Banca importo
- Service: CityPlayersGuiService
- Dialog text input `amount`
- Parsing: `parsePositiveAmount`
- Usato da: deposit / withdraw

## 2.4 Modifica citta (TownEditDialogFlow)
- Root dialog con bottoni:
  - Bio
  - Greeting
  - Farewell
  - Color (RGB)
  - Rename (se permesso)
- Sub-dialog:
  - text edit fields
  - color picker + preview

## 2.5 Rinomina citta (TownRenameDialogFlow)
- Input: nuovo nome + nuovo tag
- Access: flow edit (permesso rename)

## 2.6 Disband citta (TownDisbandDialogFlow)
- Bool confirm obbligatorio
- Access: solo mayor
- Submit: `town disband confirm` bridge

## 2.7 Relazioni set (CityRelationsGuiService)
- Multi-action dialog:
  - Ally
  - Enemy
  - Neutral
  - Annulla

## 2.8 Claim rules toggle (ClaimRulesDialogService)
- Dynamic buttons da `flagSet` HuskTowns
- Stato ON/OFF in sessione
- Bottoni:
  - toggle per ogni flag
  - Salva
  - Annulla
- Scope: claim type selezionato (CLAIM/FARM/PLOT)

## 3) Staff vs Player behavior

- Player:
  - usa `/city` come entrypoint principale
  - puo mutare dati della propria citta se ha privilegi HuskTowns richiesti
- Staff target view (`/cittaexp staff city players <citta>`):
  - accesso a root/dettagli in modalita staff
  - varie sezioni mutanti sono bloccate o read-only (es. progression mutate, relations set)

## 4) Session model (stabilita GUI)

Quasi tutte le GUI usano sessioni per viewer (`sessionId + viewerId + townId`) e validano la sessione a click-time.
Se sessione mismatch/scaduta:
- chiusura inventory o errore user-safe
- richiesta riapertura `/city`

## 5) Note per redesign

- Le GUI sono gia modulari per macro-area (`playersgui`, `progressiongui`, `levelsgui`, `challenges`, `manageclaim`, `citylistgui`, `relationsgui`, `vault`, `claimsettings`).
- Le stringhe user-facing sono quasi totalmente config-driven (`messages/config`), quindi refactor UX puo restare data-driven.
- I Dialog sono gia centralizzati con helper (`DialogViewUtils`, `DialogInputUtils`, `DialogConfirmHelper`).
