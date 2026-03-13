# 01 - Capabilities di HuskTowns

## Core domain
HuskTowns e un plugin town-management che copre nativamente:
- town lifecycle (create, membership, transfer, disband),
- claim management a chunk,
- ruoli e privilegi per-member,
- economia town (coffers, deposit/withdraw, level up),
- spawn town e privacy,
- audit/log town,
- relazioni town (ally/enemy) e war opzionali.

## Authority model
Se usato come backbone autoritativo:
- identita town, membership, ruoli e claim devono essere letti/scritti da HuskTowns,
- plugin overlay (es. CittaEXP) devono evitare duplicazione authoritativa su questi domini.

## Dati business utili esposti
- `town id` (numerico) e metadati town,
- membri e ruolo attuale,
- claim world + claim chunks,
- livello town + limiti derivati,
- balance coffers,
- log/audit town.

## Cosa NON copre come business verticale completo
- tax scheduler mensile complesso (skip primo mese, catch-up, freeze policy custom),
- progression quest-driven custom con perk arbitrari,
- workflow ticket staff custom (se non costruito sopra).

## Fit per CittaEXP
Ottimo backbone per:
- lifecycle town/membership/claim,
- livello tecnico claims e permessi,
- limiti claim/membri basati sul livello.

CittaEXP puo restare overlay per:
- governance staff queue,
- tax policy avanzata,
- progression custom e ranking capital.
