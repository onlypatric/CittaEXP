# 05 - Economia e Level System

## Economia town nativa
HuskTowns supporta:
- coffer balance town,
- deposit/withdraw,
- level-up con costo da `levels.yml`,
- hook Vault opzionale (`economy_hook`).

## Level rewards native per livello
`levels.yml` definisce per ogni livello:
- `level_money_requirements`,
- `level_member_limits`,
- `level_claim_limits`,
- `level_crop_growth_rate_bonus`,
- `level_mob_spawner_rate_bonus`.

## Cosa manca rispetto a tax engines avanzati
Non e nativo un modello tipo:
- tassa mensile schedulata con catch-up,
- fallback treasury->wallet leader,
- freeze automatico business-specific per insolvenza residua.

## Strategia consigliata
- usare HuskTowns per wallet/coffers/level base,
- implementare tax cycle avanzato in CittaEXP overlay,
- non sovrascrivere il level model nativo: estenderlo.
