# CittaEXP - Sweep Utils (riduzione duplicati)

## Obiettivo
Identificare dove possiamo accorciare il codice con utility condivise, evitando soluzioni duplicate.

## Baseline rapida (stato attuale)
- Java files: `65`
- Java LOC: `11.316`
- Pattern duplicati rilevati:
  - helper locali `msg(...)`: `6`
  - helper locali `requirePlayer(...)`: `14`
  - creazione item GUI (`new ItemStack(...)`): `21`
  - bridge comando diretto (`player.performCommand(...)`): `4`

## Duplicazioni principali e util proposte

## 1) Messaggistica/config duplicata (priorita alta)
`STATO: COMPLETATO`
### Evidenza
- Helper `msg(...)` locali in:
  - `citylistgui/CityTownListGuiService`
  - `claimsettings/ClaimRulesDialogService`
  - `manageclaim/CitySectionMapGuiService`
  - `integration/huskclaims/HuskClaimsTownFallbackGuardListener`
  - `playersgui/CityPlayersGuiService`
  - `claimsettings/CityClaimSettingsMenuService`
- `PluginConfigUtils` esiste gia, ma non e usato in modo uniforme.

### Refactor
- Estendere `PluginConfigUtils` come facade unica:
  - `msg(path, fallback, tags...)`
  - `msgList(path, fallbackList, tags...)`
  - `cfgBool/cfgLong/cfgDouble/cfgString`
  - `missingKeyFallback(path, fallback)`
- Rimuovere i metodi `msg(...)` locali dalle classi GUI/listener.

### Impatto atteso
- Riduzione boilerplate nei servizi GUI e listener.
- Meno bug su placeholder non valorizzati.

### Risultato implementato
- `PluginConfigUtils` esteso con:
  - `msgList(path, fallbackList, tags...)`
  - `cfgBool/cfgLong/cfgDouble/cfgString`
  - `missingKeyFallback(path, fallback)` (usato anche da `msg(...)`)
- Rimossi i wrapper locali `msg(...)` nelle classi target:
  - `citylistgui/CityTownListGuiService`
  - `claimsettings/ClaimRulesDialogService`
  - `manageclaim/CitySectionMapGuiService`
  - `integration/huskclaims/HuskClaimsTownFallbackGuardListener`
  - `playersgui/CityPlayersGuiService`
  - `claimsettings/CityClaimSettingsMenuService`

## 2) Guardie comando Brigadier duplicate (priorita alta)
`STATO: COMPLETATO`
### Evidenza
- `requirePlayer(...)` ripetuto in 14 classi comando, spesso identico.

### Refactor
- Nuovo util in `utils`:
  - `CommandGuards.player(ctx, permission, playerOnlyKey, deniedKey)`
  - opzionale: `CommandGuards.playerInTown(...)`, `CommandGuards.staff(...)`
- Tenere in un punto unico i messaggi standard player-only/permesso negato.

### Impatto atteso
- Meno codice per ogni nuovo comando.
- Comportamento coerente e i18n uniforme.

### Risultato implementato
- Introdotto `utils/CommandGuards` con guardia unica:
  - `cityPlayer(ctx)` (permesso, player-only e no-permission centralizzati).
- Rimossi i metodi locali `requirePlayer(...)` da tutte le command class coinvolte.
- Rimossi i `CITY_PERMISSION` locali usati solo dai vecchi wrapper.
- Verifica compilazione eseguita: `./gradlew compileJava -x test` -> `BUILD SUCCESSFUL`.

## 3) GUI item builder ripetuto (priorita alta)
`STATO: COMPLETATO`
### Evidenza
- Pattern ripetuto in:
  - `playersgui/CityPlayersGuiService`
  - `citylistgui/CityTownListGuiService`
  - `manageclaim/CitySectionMapGuiService`
  - `claimsettings/CityClaimSettingsMenuService`
- Sequenza tipica ripetuta: `new ItemStack -> getItemMeta -> displayName/lore -> setItemMeta`.

### Refactor
- Nuovo `GuiItemFactory` in `utils`:
  - `item(material, name, lore...)`
  - `head(uuid, name, lore...)`
  - `navPrev(enabled)`, `navNext(enabled)`, `closeButton()`, `filler()`, `pageInfo(...)`
- Nuovo `GuiLayoutUtils`:
  - fill slots
  - footer standard
  - builder per paginazione 45-entry.

### Impatto atteso
- Forte riduzione LOC nelle classi GUI grandi.
- UI piu consistente.

### Risultato implementato
- Nuove utility introdotte:
  - `utils/GuiItemFactory` (item base, item con lore, head player, filler pane)
  - `utils/GuiLayoutUtils` (fill inventario con esclusioni slot)
- Migrazione effettuata su:
  - `playersgui/CityPlayersGuiService`
  - `citylistgui/CityTownListGuiService`
  - `manageclaim/CitySectionMapGuiService`
  - `claimsettings/CityClaimSettingsMenuService`
- Rimossi builder `ItemStack + ItemMeta` ridondanti e loop filler duplicati.
- Verifica compilazione: `./gradlew compileJava -x test` -> `BUILD SUCCESSFUL`.

## 4) Reason-code e fallback utente sparsi (priorita media)
`STATO: COMPLETATO`
### Evidenza
- Mapping manuali in command/service vari (livelli, claim map, GUI actions).

### Refactor
- `ReasonMessageMapper` centralizzato:
  - input: `reasonCode`
  - output: key/fallback i18n
- Uso in comandi, GUI click handlers, listener fallback.

### Impatto atteso
- Meno switch duplicati.
- UX errori piu uniforme.

### Risultato implementato
- Introdotto `utils/ReasonMessageMapper` unico per risoluzione reason-code -> testo localizzato.
- Migrazione completata in path command/GUI:
  - `levels/CityLevelCommand`
  - `levels/StaffLevelCommand`
  - `playersgui/CityPlayersGuiService`
  - `claimsettings/CityClaimSettingsMenuService`
  - `manageclaim/CitySectionMapGuiService` (lookup reason centralizzato sui messaggi policy).
- Fallback utente standardizzato: key i18n con fallback humanized del reason-code.
- Compilazione verificata: `./gradlew compileJava -x test` -> `BUILD SUCCESSFUL`.

## 5) Dialog input patterns ripetuti (priorita media)
`STATO: COMPLETATO`
### Evidenza
- Dialog numerici (banca), conferme, input testuali in classi diverse.
- Parte conferma gia centralizzata in `DialogConfirmHelper`, ma non copre tutti i casi input.

### Refactor
- Aggiungere `DialogInputUtils`:
  - number input con regex e bounds
  - text input con min/max + normalizzazione
  - result parser standard (`ok|validation_error|cancel`).

### Impatto atteso
- Meno codice nei flow create/edit/bank.
- Validazioni coerenti.

### Risultato implementato
- Nuove utility introdotte:
  - `utils/DialogInputUtils`:
    - `text(...)` per input testuali con default coerenti,
    - `normalized(...)` / `normalizedUpper(...)`,
    - `parsePositiveAmount(...)` con regex numerica condivisa.
  - `utils/DialogViewUtils`:
    - apertura dialog riflessiva centralizzata con fallback user-safe.
- Migrazione completata sui flow principali:
  - `createtown/CityCreateDialogFlow`
  - `renametown/TownRenameDialogFlow`
  - `edittown/TownEditDialogFlow`
  - `disbandtown/TownDisbandDialogFlow`
  - `claimsettings/ClaimRulesDialogService`
  - `utils/DialogConfirmHelper`
  - `playersgui/CityPlayersGuiService` (dialog banca: parse importo centralizzato)
- Eliminata reflection duplicata `showDialog(...)` nei flow dialog migrati.
- Compilazione verificata: `./gradlew compileJava -x test` -> `BUILD SUCCESSFUL`.

## 6) Command bridge non uniformato (priorita media)
`STATO: COMPLETATO`
### Evidenza
- Esiste `CommandBridgeUtils`, ma in alcuni punti si usa `performCommand(...)` inline.

### Refactor
- Usare sempre `CommandBridgeUtils` (log + feedback uniforme).
- Aggiungere metodi typed:
  - `dispatchTown(player, "deposit", amount)`
  - `dispatchTown(player, "withdraw", amount)`.

### Impatto atteso
- Riduce duplicati e string concatenation fragile.

### Risultato implementato
- `CommandBridgeUtils` esteso con metodi typed:
  - `dispatchTown(...)` con overload `action`, `action+arg`, `action+amount`.
  - `dispatchTownCommand(...)` per comandi Brigadier (return uniforme `Command.SINGLE_SUCCESS`).
- Rimossi tutti i `performCommand(...)` inline nei path runtime CittaEXP:
  - `disbandtown/TownDisbandDialogFlow`
  - `playersgui/CityPlayersGuiService`
- Migrazione dei command bridge a metodi typed:
  - `membership/InviteCommand`
  - `membership/ModerationCommand`
  - `membership/RankCommand`
  - `membership/TransferCommand`
  - `chat/TownChatCommand`
  - `economy/TreasuryCommand`
- Eliminata l’API `dispatchPlayerCommand(...)` non piu usata.
- Verifica compilazione: `./gradlew compileJava -x test` -> `BUILD SUCCESSFUL`.

## 7) Codice probabilmente legacy/non raggiungibile (priorita alta, con verifica)
`STATO: COMPLETATO`
### Evidenza
- Classi comando presenti ma non referenziate nel command tree attuale:
  - `membership/TransferCommand`
  - `membership/RankCommand`
  - `membership/ModerationCommand`
  - `edittown/EditCommand`
  - `renametown/RenameCommand`
  - `economy/TreasuryCommand`
  - `nearby/NearbyCommand`

### Refactor
- Hard cut: rimuovere classi non usate dopo verifica finale routing.
- Se serve, tenere solo logica riusabile spostata in servizi chiamati da GUI.

### Impatto atteso
- Riduzione netta LOC immediata.
- Codebase piu leggibile.

### Risultato implementato
- Verifica routing completata:
  - nessun riferimento runtime ai comandi legacy nel tree attuale (`CityCommandTree` / `CittaExpCommandTree`).
  - ricerca riferimenti su codebase: classi presenti solo come definizione locale, senza chiamanti.
- Hard cut eseguito (rimozione fisica file):
  - `membership/TransferCommand`
  - `membership/RankCommand`
  - `membership/ModerationCommand`
  - `edittown/EditCommand`
  - `renametown/RenameCommand`
  - `economy/TreasuryCommand`
  - `nearby/NearbyCommand`
- Verifica compilazione: `./gradlew compileJava -x test` -> `BUILD SUCCESSFUL`.

## Piano consigliato (ordine)
1. Unificare `PluginConfigUtils` + eliminare `msg(...)` locali.
2. Introdurre `GuiItemFactory`/`GuiLayoutUtils` e migrare GUI principali.
3. Introdurre `CommandGuards` e togliere `requirePlayer(...)` duplicati.
4. Centralizzare `ReasonMessageMapper`.
5. Ripulire comandi legacy non registrati.

## Riduzione LOC realistica (stima)
- Fase 1-3: ~`-500 / -900` LOC
- Fase 4-5: ~`-300 / -700` LOC
- Totale sweep utils + hard cleanup: ~`-800 / -1.600` LOC

## Nota operativa
- Completati: punti `1`, `2`, `3`, `4`, `5`, `6`, `7`.
