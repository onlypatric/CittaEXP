# 09 - Hard Reset Plan CittaEXP allineato a HuskTowns

## Obiettivo
Pulire il codebase CittaEXP e ripartire con architettura town-first reale, senza trascinarsi path legacy.

## Principi
- HuskTowns source-of-truth su town/membership/roles/claims.
- CittaEXP overlay business-only.
- No dual-authority runtime.

## Taglio consigliato del reset
1. Tenere:
- adapter backbone HuskTowns,
- command surface `/city` core,
- staff/probe/test-mode,
- persistence overlay strettamente business.

2. Eliminare o riscrivere:
- servizi che assumono authority locale su membership/claim,
- flow preview/showcase legacy,
- fallback in-memory nei path autoritativi,
- mapping reason codes non unificato.

3. Rebuild in fasi
- Fase A: backbone + command core,
- Fase B: overlay economy/ticket,
- Fase C: progression/ranking,
- Fase D: bot matrix full + db integrity.

## Definition of done reset
- nessuna `CommandException` nei flow core,
- zero missing i18n key user-facing,
- zero write autoritativa fuori HuskTowns nei domini core,
- bot `fast/full-positive/full-negative/full` verdi,
- runbook cutover pronto.
