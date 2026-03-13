# Backlog: Deleghe Individuali Permessi Città (senza promozione ruolo)

## Obiettivo
Consentire al capo città di concedere permessi puntuali a singoli membri senza cambiare il loro ruolo HuskTowns.

## Stato attuale
- HuskTowns è role-based per i privilegi town-wide (`UUID -> roleWeight`).
- Non esiste un override per-player nativo sui privilegi globali della città.
- Eccezione nativa: permessi per-player sui `PLOT` (plot members/managers), ma solo a livello claim `PLOT`.

## Proposta overlay CittaEXP (futura)
- Introdurre un layer `deleghe individuali` applicato ai path `/city` del wrapper.
- Esempi di deleghe candidate:
  - invitare membri,
  - impostare warp città,
  - gestire claim/rules specifiche,
  - funzioni banca (deposit/withdraw) separate.
- UX prevista:
  - gestione da GUI `/city` (scheda membro -> deleghe),
  - audit dedicato per grant/revoke,
  - reason-code localizzati.

## Vincoli
- Le deleghe CittaEXP non cambiano i privilegi nativi HuskTowns fuori dai path wrapper.
- Nessuna escalation implicita su comandi nativi `/town` non wrappati.

## Decisione rimandata
Definire la matrice completa `azione -> delegabile sì/no` e i limiti per ruolo/stage in una wave dedicata.
