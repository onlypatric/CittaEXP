# GUI Custom Head Migration (CittaEXP)

Di seguito trovi la checklist completa degli elementi GUI attuali da poter migrare a teste custom.

## Filler pane (sfondo GUI)

Descrizione: Slot di riempimento in quasi tutte le GUI inventory (player list, detail, azioni, banca, section map, city list, claim settings).

Item attuale: `BLACK_STAINED_GLASS_PANE`

Testa custom:

## /city membri - pagina precedente

Descrizione: Navigazione pagine lista membri (`/city` root), slot footer prev.

Item attuale: `ARROW` (attivo) / `GRAY_DYE` (disabilitato)

Testa custom:
- ARROW LEFT: `eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMWExZWYzOThhMTdmMWFmNzQ3NzAxNDUxN2Y3ZjE0MWQ4ODZkZjQxYTMyYzczOGNjOGE4M2ZiNTAyOTdiZDkyMSJ9fX0=`
- DISABLED: `eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzZlYmFhNDFkMWQ0MDVlYjZiNjA4NDViYjlhYzcyNGFmNzBlODVlYWM4YTk2YTU1NDRiOWUyM2FkNmM5NmM2MiJ9fX0=`
## /city membri - pagina successiva

Descrizione: Navigazione pagine lista membri (`/city` root), slot footer next.

Item attuale: `ARROW` (attivo) / `GRAY_DYE` (disabilitato)

Testa custom:
- ARROW RIGHT: `eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYWM5YzY3YTlmMTY4NWNkMWRhNDNlODQxZmU3ZWJiMTdmNmFmNmVhMTJhN2UxZjI3MjJmNWU3ZjA4OThkYjlmMyJ9fX0=`
- DISABLED: `eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODM5OWU1ZGE4MmVmNzc2NWZkNWU0NzJmMzE0N2VkMTE4ZDk4MTg4NzczMGVhN2JiODBkN2ExYmVkOThkNWJhIn19fQ==`
## /city membri - indicatore pagina

Descrizione: Indicatore pagina e conteggio membri nella lista membri.

Item attuale: `PAPER`

Testa custom: `eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZmEyYWZhN2JiMDYzYWMxZmYzYmJlMDhkMmM1NThhN2RmMmUyYmFjZGYxNWRhYzJhNjQ2NjJkYzQwZjhmZGJhZCJ9fX0=`

## /city membri - bottone Azioni

Descrizione: Apertura sottomenu “Azioni Città” dalla lista membri.

Item attuale: `GUNPOWDER`

Testa custom: `eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjA0YTZmYzhmMGNkY2IxMzMyYWQ5ODM1NGVjYmExZGI1OTUyNTM2NDJiNmI2MTgyMjU4YmIxODM2MjVkMTg5MiJ9fX0=`

## /city membri - testa membro (entry lista)

Descrizione: Entry membro in lista con skin reale player e lore ruolo/stato.

Item attuale: `PLAYER_HEAD` (skin reale del membro)

Testa custom: Nessuna, dipende dalla skin reale del membro

## /city dettaglio membro - info membro

Descrizione: Card informazioni nome/ruolo/peso nella schermata dettaglio membro.

Item attuale: `NAME_TAG`

Testa custom: `eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOGExNjAzOGJjOGU2NTE4YWZhOTE0OThkYWI3Njc1YzAxY2IzMWExMjVkMjFjNDliODYxMjk0ZDM5ZTFjNTYwYyJ9fX0=`

## /city dettaglio membro - stato online/offline

Descrizione: Indicatore online/offline nella schermata dettaglio membro.

Item attuale: `LIME_DYE` (online) / `GRAY_DYE` (offline)

Testa custom:
- Online: `eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjA5ZDZhMzQ4YTk5MjNlYmJiMzQyM2IxOTY5NzNkNDk1YjFhYTVjMDU2ZTRiMzUxYjEyMGQzNmQ4MzMxYTU0MiJ9fX0=`
- Offline: `eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNmY1NWE1NzYzOWM5ZjdkYmIyNDZiMjhhMjkyY2ZmMjk4MzQ0Yzk2OWNmZDQyNTI3MDlkNGE1ZmE2NjMxMjhiOSJ9fX0=`

## /city dettaglio membro - Promuovi

Descrizione: Azione di promozione ruolo dal dettaglio membro (con dialog conferma).

Item attuale: `LIME_DYE`

Testa custom:
- `eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTAzZjkzOTFhMWI1MzVkOTdhNGUxZDgzYzQyZGYyMzg4YTc3ZTFiZWFhYzU5NzJlMDcxYzRkN2VkN2M1YzQ5MiJ9fX0=`

## /city dettaglio membro - Demuovi

Descrizione: Azione di demozione ruolo dal dettaglio membro (con dialog conferma).

Item attuale: `ORANGE_DYE`

Testa custom:
- `eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTFjOWIxNmQ2ZDBmM2U4OTA4MTc3NTc5YTMwOGMwNTJmOGQwM2I2Y2VhMzg4MDZkYTgxM2Q3OGM0OTRjMDVkZSJ9fX0=`

## /city dettaglio membro - Espelli

Descrizione: Azione di espulsione membro dal dettaglio membro (con dialog conferma).

Item attuale: `BARRIER`

Testa custom: `eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNWUwY2E1OTI2ODU1ZTRjYTg3ZTEwNmJkYzZiNTczYzZmYzE0MjAxODI2Mjg3ODFhOTNjYjg0ZTg2ZWIxMGJmNiJ9fX0=`

## /city dettaglio membro - Trasferisci capo

Descrizione: Azione trasferimento leadership dal dettaglio membro (con dialog conferma).

Item attuale: `NETHER_STAR`

Testa custom: `eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNmY3N2Q5ZjFkZWI5NTMwYzhmY2ZkODhhYzk3ZTFiYWQ3NTVkNTEyZDBkZmZkMjdiMjAxNzExMTk1ZGVlMWI1YyJ9fX0=`

## /city dettaglio membro - Indietro

Descrizione: Ritorno alla lista membri.

Item attuale: `ARROW`

Testa custom: PLAYER_SKULL senza texture custom

## /city dettaglio membro - Chiudi

Descrizione: Chiusura GUI dettaglio membro.

Item attuale: `BARRIER`

Testa custom: `eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZWQwYTE0MjA4NDRjZTIzN2E0NWQyZTdlNTQ0ZDEzNTg0MWU5ZjgyZDA5ZTIwMzI2N2NmODg5NmM4NTE1ZTM2MCJ9fX0=`

## /city azioni - Banca città

Descrizione: Apertura schermata banca città.

Item attuale: `GOLD_INGOT`

Testa custom: `eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOWYyNjI4NzA4NjZlMzYxNzU3ZWQ4ZGNmYWJkMTAzZWJkNjRkNTczODQ1NWQwYzkyOWIyNjYyNzRlN2M0YzdkYyJ9fX0=`

## /city azioni - Abbandona città

Descrizione: Avvio flow leave con dialog conferma.

Item attuale: `RED_DYE`

Testa custom: `eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNWUwY2E1OTI2ODU1ZTRjYTg3ZTEwNmJkYzZiNTczYzZmYzE0MjAxODI2Mjg3ODFhOTNjYjg0ZTg2ZWIxMGJmNiJ9fX0=`

## /city azioni - Lista città

Descrizione: Apertura GUI `/city list`.

Item attuale: `COMPASS`

Testa custom: `eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZmYwZGJlZTM3MDg3N2ZlYjRhNjJhYzlmOTNlMTNmYTVkZTA0ZDMxM2VmODNmZWVlODE1ZjZlZDNhN2FiY2E1YyJ9fX0=`

## /city azioni - Gestione territori

Descrizione: Apertura GUI `/city section` (mappa claim).

Item attuale: `MAP`

Testa custom: `eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTU3N2M0ZGUxZjUxYTcwNzIyMDIzZTg1NmI1NDNjZDU3MGYxZDBlZTZiOWQxNjdiNTkwMjhjZTFiYzkyZTQ1OCJ9fX0=`

## /city azioni - Impostazioni claim

Descrizione: Apertura menu impostazioni claim/privacy.

Item attuale: `COMPARATOR`

Testa custom: `eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjA0YTZmYzhmMGNkY2IxMzMyYWQ5ODM1NGVjYmExZGI1OTUyNTM2NDJiNmI2MTgyMjU4YmIxODM2MjVkMTg5MiJ9fX0=`

## /city azioni - Modifica città

Descrizione: Apertura dialog edit città (bio/greeting/farewell/color/rename).

Item attuale: `WRITABLE_BOOK`

Testa custom: `eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODY3MjA4NWZmNWRmMGVlODUyMTM2ZTJkNDFhNzY5MjI5MjkxZDY2NmIyOGU5ZmIwNGQ2YzkyZjE1OGU0MmJiIn19fQ==`

## /city azioni - Elimina città

Descrizione: Avvio flow disband con dialog conferma.

Item attuale: `TNT`

Testa custom: `eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmUwZmQxMDE5OWU4ZTRmY2RhYmNhZTRmODVjODU5MTgxMjdhN2M1NTUzYWQyMzVmMDFjNTZkMThiYjk0NzBkMyJ9fX0=`

## /city azioni - Indietro

Descrizione: Ritorno alla GUI membri.

Item attuale: `ARROW`

Testa custom: `eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMWJmMjdmOTQ0YWFjOWVhOTZkOWZlZWU0N2Q5ZjI5MzM3MGY1OGU1ZjUzNDJkMGE4MDFjYmVhM2VjMjNjMmI0OSJ9fX0=`

## /city azioni - Chiudi

Descrizione: Chiusura GUI azioni.

Item attuale: `BARRIER`

Testa custom: `eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZWQwYTE0MjA4NDRjZTIzN2E0NWQyZTdlNTQ0ZDEzNTg0MWU5ZjgyZDA5ZTIwMzI2N2NmODg5NmM4NTE1ZTM2MCJ9fX0=`

## /city banca - Bilancio città

Descrizione: Visualizzazione saldo town bank.

Item attuale: `PAPER`

Testa custom: `eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOWYyNjI4NzA4NjZlMzYxNzU3ZWQ4ZGNmYWJkMTAzZWJkNjRkNTczODQ1NWQwYzkyOWIyNjYyNzRlN2M0YzdkYyJ9fX0=`

## /city banca - Deposita (abilitato)

Descrizione: Apertura dialog deposito importo.

Item attuale: `LIME_DYE`

Testa custom: `eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOGVlYjE4ZTY2OTVjMzliNDQxZDA0ZjdjYTUzZWRhMTM0NmE1YTk0N2Q0ZmU4YmEzN2IzM2I5MjMyODhiNGUzMCJ9fX0=`

## /city banca - Deposita (bloccato)

Descrizione: Stato deposito bloccato per mancanza permesso.

Item attuale: `GRAY_DYE`

Testa custom: `eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDgzNjUzOGY1NTU1Y2M2MTAzODhiOTdjNmJkMzY4ZTE2YWExZmZiNWQ3YjRiZDNjODZmZTA5MmU4ZTBkNGNjNyJ9fX0=`

## /city banca - Preleva (abilitato)

Descrizione: Apertura dialog prelievo importo.

Item attuale: `ORANGE_DYE`

Testa custom: `eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjg1NmNjYWI2ZjcyYjZmMzczMzI4NDBhMjc3OWJiNDI3NThiYmIyYzAxNWY1NGNlNTM3NDBjYjBmMWNiNGNhMSJ9fX0=`

## /city banca - Preleva (bloccato)

Descrizione: Stato prelievo bloccato per mancanza permesso.

Item attuale: `GRAY_DYE`

Testa custom: `eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDgzNjUzOGY1NTU1Y2M2MTAzODhiOTdjNmJkMzY4ZTE2YWExZmZiNWQ3YjRiZDNjODZmZTA5MmU4ZTBkNGNjNyJ9fX0=`

## /city banca - Indietro

Descrizione: Ritorno alla GUI azioni città.

Item attuale: `ARROW`

Testa custom: `eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjA0YTZmYzhmMGNkY2IxMzMyYWQ5ODM1NGVjYmExZGI1OTUyNTM2NDJiNmI2MTgyMjU4YmIxODM2MjVkMTg5MiJ9fX0=`

## /city banca - Chiudi

Descrizione: Chiusura GUI banca.

Item attuale: `BARRIER`

Testa custom: `eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZWQwYTE0MjA4NDRjZTIzN2E0NWQyZTdlNTQ0ZDEzNTg0MWU5ZjgyZDA5ZTIwMzI2N2NmODg5NmM4NTE1ZTM2MCJ9fX0=`

## /city claim settings - Privacy warp (no spawn)

Descrizione: Indicatore privacy non modificabile finché spawn non impostato.

Item attuale: `GRAY_DYE`

Testa custom: inalterato

## /city claim settings - Privacy warp public/private

Descrizione: Toggle privacy warp (quando consentito dal tier).

Item attuale: `LIME_DYE` (public) / `ORANGE_DYE` (private)

Testa custom: inalterato

## /city claim settings - Privacy warp bloccata da tier

Descrizione: Stato “public forzato” per tier che non può cambiare privacy.

Item attuale: `PAPER`

Testa custom: inalterato

## /city claim settings - Impostazioni CLAIM

Descrizione: Apertura dialog rules per claim type `CLAIM`.

Item attuale: `WHITE_STAINED_GLASS_PANE`

Testa custom: `eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjQ1NTlkNzU0NjRiMmU0MGE1MThlNGRlOGU2Y2YzMDg1ZjBhM2NhMGIxYjcwMTI2MTRjNGNkOTZmZWQ2MDM3OCJ9fX0=`

## /city claim settings - Impostazioni FARM

Descrizione: Apertura dialog rules per claim type `FARM`.

Item attuale: `LIME_STAINED_GLASS_PANE`

Testa custom: `eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvY2VjZDVjYzQwMzU2ZGE3N2ZkMjU1NzI3NjNlMzkzNjE3Njc2NzEwZDNiNjEwZjY1ZDZmNTgwNjI0NmQ5Nzc3NyJ9fX0=`

## /city claim settings - Impostazioni PLOT

Descrizione: Apertura dialog rules per claim type `PLOT`.

Item attuale: `BLUE_STAINED_GLASS_PANE`

Testa custom: `eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZmYwZGJlZTM3MDg3N2ZlYjRhNjJhYzlmOTNlMTNmYTVkZTA0ZDMxM2VmODNmZWVlODE1ZjZlZDNhN2FiY2E1YyJ9fX0=`

## /city claim settings - Indietro

Descrizione: Ritorno alla GUI azioni città.

Item attuale: `ARROW`

Testa custom: `eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjA0YTZmYzhmMGNkY2IxMzMyYWQ5ODM1NGVjYmExZGI1OTUyNTM2NDJiNmI2MTgyMjU4YmIxODM2MjVkMTg5MiJ9fX0=`

## /city claim settings - Chiudi

Descrizione: Chiusura GUI impostazioni claim.

Item attuale: `BARRIER`

Testa custom: `eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZWQwYTE0MjA4NDRjZTIzN2E0NWQyZTdlNTQ0ZDEzNTg0MWU5ZjgyZDA5ZTIwMzI2N2NmODg5NmM4NTE1ZTM2MCJ9fX0=`

## /city list - entry città

Descrizione: Entry città con testa del leader, nome+tag colorato e lore completa.

Item attuale: `PLAYER_HEAD` (skin reale del leader)

Testa custom: inalterato

## /city list - pagina precedente

Descrizione: Navigazione pagine lista città.

Item attuale: `ARROW` (attivo) / `GRAY_DYE` (disabilitato)

Testa custom:

## /city list - pagina successiva

Descrizione: Navigazione pagine lista città.

Item attuale: `ARROW` (attivo) / `GRAY_DYE` (disabilitato)

Testa custom:
- ARROW LEFT: `eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMWExZWYzOThhMTdmMWFmNzQ3NzAxNDUxN2Y3ZjE0MWQ4ODZkZjQxYTMyYzczOGNjOGE4M2ZiNTAyOTdiZDkyMSJ9fX0=`
- DISABLED: `eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzZlYmFhNDFkMWQ0MDVlYjZiNjA4NDViYjlhYzcyNGFmNzBlODVlYWM4YTk2YTU1NDRiOWUyM2FkNmM5NmM2MiJ9fX0=`

## /city list - sort cycle

Descrizione: Cambia ordinamento (`XP_CUSTOM`, `TIER`, `MONEY`, `FOUNDED`, `MEMBERS`).

Item attuale: `HOPPER`

Testa custom: inalterato

## /city list - indicatore pagina/sort

Descrizione: Mostra pagina, numero città e sort corrente.

Item attuale: `PAPER`

Testa custom: `eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZmEyYWZhN2JiMDYzYWMxZmYzYmJlMDhkMmM1NThhN2RmMmUyYmFjZGYxNWRhYzJhNjQ2NjJkYzQwZjhmZGJhZCJ9fX0=`

## /city list - Chiudi

Descrizione: Chiusura GUI lista città.

Item attuale: `BARRIER`

Testa custom: `eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZWQwYTE0MjA4NDRjZTIzN2E0NWQyZTdlNTQ0ZDEzNTg0MWU5ZjgyZDA5ZTIwMzI2N2NmODg5NmM4NTE1ZTM2MCJ9fX0=`

## /city section - cella claim propria

Descrizione: Cella mappa chunk della tua città.

Item attuale: `GREEN_STAINED_GLASS_PANE`

Testa custom: `eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZmMxYmQ3Y2UyZDUyN2VjNmY3NmI4ZTgxZjE5ZDY5ZDFiOWZmNGQ1YjZjZDZjYzc2NGVlZDE5NDVhNmM2YyJ9fX0=`

## /city section - cella wilderness claimabile

Descrizione: Cella mappa chunk claimabile.

Item attuale: `GRAY_STAINED_GLASS_PANE`

Testa custom: `eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzQzOGQwOGJkMDQwNWMwNWY0N2VhODZkNjY2NDM0MzRmZGQyZThjNDZmZjFlNmY4ODJiYjliZjg5MWM3ZDNhNSJ9fX0=`

## /city section - cella bloccata/altrui/policy

Descrizione: Cella mappa chunk non claimabile (altra città/admin/policy).

Item attuale: `RED_STAINED_GLASS_PANE`

Testa custom: `eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNGU0YjhiOGQyMzYyYzg2NGUwNjIzMDE0ODdkOTRkMzI3MmE2YjU3MGFmYmY4MGMyYzViMTQ4Yzk1NDU3OWQ0NiJ9fX0=`

## /city section - cella bloccata da HuskClaims

Descrizione: Cella mappa chunk bloccata da claim HuskClaims.

Item attuale: `RED_TERRACOTTA`

Testa custom: inalterato

## /city section - navigazione su

Descrizione: Sposta viewport mappa verso nord.

Item attuale: `ARROW`

Testa custom: `eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNGNlMzZmY2IxZTVmNmIzNjUxN2ZiYmViOWNiZjRiMGMwNWMzMGQ4YmRiNTE1NDgyNGU2MGU2ZDU1MGY1MjhlOSJ9fX0=`

## /city section - navigazione giù

Descrizione: Sposta viewport mappa verso sud.

Item attuale: `ARROW`

Testa custom: `eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzM1YzFlZjEyN2Y1YzUyY2IzODlhOGFjNmRmM2Y2ZmM2NmNkMzdmMjYwOTRiM2UxZTc2ZDAxNzcxMTViYjA4ZiJ9fX0=`

## /city section - navigazione sinistra

Descrizione: Sposta viewport mappa verso ovest.

Item attuale: `ARROW`

Testa custom: `eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMWExZWYzOThhMTdmMWFmNzQ3NzAxNDUxN2Y3ZjE0MWQ4ODZkZjQxYTMyYzczOGNjOGE4M2ZiNTAyOTdiZDkyMSJ9fX0=`

## /city section - navigazione destra

Descrizione: Sposta viewport mappa verso est.

Item attuale: `ARROW`

Testa custom: `eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYWM5YzY3YTlmMTY4NWNkMWRhNDNlODQxZmU3ZWJiMTdmNmFmNmVhMTJhN2UxZjI3MjJmNWU3ZjA4OThkYjlmMyJ9fX0=`

## /city section - centra su player

Descrizione: Centra la mappa sul chunk del player.

Item attuale: `COMPASS`

Testa custom: `eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODZmY2FlZmExOTY2OWQ4YmUwMmNmNWJhOWE3ZjJjZjZkMjdlNjM2NDEwNDk2ZmZjZmE2MmIwM2RjZWI5ZDM3OCJ9fX0=`

## /city section - legenda claim proprio

Descrizione: Legenda stato “Tuo claim”.

Item attuale: `GREEN_STAINED_GLASS_PANE`

Testa custom: `eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZmMxYmQ3Y2UyZDUyN2VjNmY3NmI4ZTgxZjE5ZDY5ZDFiOWZmNGQ1YjZjZDZjYzc2NGVlZDE5NDVhNmM2YyJ9fX0=`

## /city section - legenda wilderness

Descrizione: Legenda stato “Wilderness claimabile”.

Item attuale: `GRAY_STAINED_GLASS_PANE`

Testa custom: `eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzQzOGQwOGJkMDQwNWMwNWY0N2VhODZkNjY2NDM0MzRmZGQyZThjNDZmZjFlNmY4ODJiYjliZjg5MWM3ZDNhNSJ9fX0=`

## /city section - legenda bloccato/altrui

Descrizione: Legenda stato “Bloccato/altrui”.

Item attuale: `RED_STAINED_GLASS_PANE`

Testa custom: `eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNGU0YjhiOGQyMzYyYzg2NGUwNjIzMDE0ODdkOTRkMzI3MmE2YjU3MGFmYmY4MGMyYzViMTQ4Yzk1NDU3OWQ0NiJ9fX0=`

## /city section - Chiudi

Descrizione: Chiusura GUI mappa claim.

Item attuale: `BARRIER`

Testa custom: `eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZWQwYTE0MjA4NDRjZTIzN2E0NWQyZTdlNTQ0ZDEzNTg0MWU5ZjgyZDA5ZTIwMzI2N2NmODg5NmM4NTE1ZTM2MCJ9fX0=`
