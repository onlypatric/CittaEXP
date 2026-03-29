# GUI Custom Playbook (CittaEXP)

## Obiettivo
Questa cartella raccoglie la guida operativa per creare nuove GUI custom del plugin in modo coerente, mantenibile e testabile.

## Architettura (regola base)
- `CittaEXP` gestisce logica, routing, permessi, click handler e sessioni.
- `ItemsAdder` fornisce asset grafici (texture/font image) nel resourcepack.
- La GUI non viene "creata da ItemsAdder": viene sempre aperta dal codice Java del plugin.

## Standard runtime
- Entrypoint player: sempre `/city` (GUI-first).
- Nessun nuovo comando player se non strettamente necessario.
- Staff tools solo per debug/diagnostica o gestione operativa.
- Tutto i18n in `config.yml` (niente testo hardcoded user-facing).

## Struttura consigliata file
- Asset texture GUI:
  - `plugins/ItemsAdder/contents/<namespace>/resourcepack/assets/<namespace>/textures/gui/*.png`
- Registrazione font image:
  - `plugins/ItemsAdder/contents/<namespace>/configs/guis.yml` (o `font_images.yml`)
- Codice plugin:
  - `src/main/java/it/patric/cittaexp/<modulo-gui>/...`
- Messaggi:
  - `src/main/resources/config.yml`

## Vincoli pratici per texture GUI con ItemsAdder
- Usare PNG lowercase.
- Per font image GUI, tenere dimensioni entro range sicuro (`<= 256x256`).
- Offset verticale via ItemsAdder (`y_position` nella font image).
- Offset orizzontale da codice Java (offset constructor/API del wrapper).
- Se il titolo GUI e blank, usare stringa vuota `""` (evitare stringhe "solo spazi").

## Workflow implementazione (checklist)
1. Definire UX (slot map, navigazione, azioni mutate/read-only, back flow).
2. Preparare asset in `ItemsAdder/contents/<namespace>`.
3. Registrare font image nel relativo YAML.
4. Rigenerare pack (`/iazip`) e ricaricare (`/iareload`) in ambiente test.
5. Implementare service GUI Java:
   - open/render/click/refresh
   - session state viewer-based
   - gate permessi/policy
6. Collegare routing nel punto canonico (`/city` o sezione parent).
7. Aggiungere chiavi i18n e lore/feedback.
8. Test smoke:
   - open/close/back
   - click multipli
   - no `CommandException`
   - no missing message key

## Pattern consigliati nel codice
- Un service per GUI (`<Feature>GuiService`).
- Una session per viewer (`<Feature>Session`).
- View model separati per rendering (`<Feature>View`).
- Utility comuni per item/footer/navigation (riuso, no duplicazioni).
- No operazioni bloccanti sul main thread.

## Diagnostica minima da avere
- Log warning chiari per fallback grafici.
- Messaggi user-safe in caso di errore.
- Probe/staff diagnostics per stati critici (se modulo complesso).

## Regole UX
- Home pulita, progressive disclosure.
- CTA chiare e non duplicate.
- Azioni distruttive in layer separato con conferma esplicita.
- Coerenza visiva: colori e semantica slot uniformi tra GUI.

## Note per prossime wave
- Se serve tuning live offset X/Y, prevedere parametri di debug solo staff.
- Rimuovere i comandi/debug temporanei appena validata la GUI.
- Documentare sempre mapping: entrypoint -> submenu -> dialog -> side effect.
