# 08 - Config e Operativita

## File chiave
- `config.yml`
- `levels.yml`
- `roles.yml`
- `flags.yml`
- `rules.yml`
- `messages-xx-xx.yml`
- `server.yml` (cross-server)

## Leve operative importanti
- `economy_hook`
- `require_first_level_collateral`
- `minimum_chunk_separation`
- `require_claim_adjacency`
- `unclaimable_worlds`
- `relations.enabled` e `wars.enabled`

## Procedura minima di bootstrap
1. install jar,
2. primo boot,
3. tuning config,
4. secondo boot + verify status command.

## Checklist health
- API bind ok,
- db ok,
- worlds claimabili corretti,
- roles/privilegi allineati al gameplay target.
