# CittaEXP Integration Contracts

## Dipendenze obbligatorie
- `Vault` (economia)
- `HuskClaims` (claim/expand)
- `ClassificheEXP` (ranking read-only)

Policy:
- se una dipendenza obbligatoria e `missing` o `disabled`, il plugin fallisce in enable con motivo esplicito.
- se API/plugin non bindabile (`Vault Economy`, `LeaderboardApiProvider`) il plugin fallisce in enable (`integration.failFast=true`).
- probe runtime espone lo stato dependency-by-dependency.

## Vault contract
- Operazioni richieste:
  - balance(player)
  - withdraw(player, amount, reason)
  - deposit(player, amount, reason)
- Regole:
  - amount in currency intera (`long`) definita da config staff.
  - nessun prelievo parziale: o successo completo o failure.

## HuskClaims contract
- Operazioni richieste:
  - detect claim alla posizione di creazione
  - create auto-claim `100x100` se area libera
  - expand claim su richiesta (con costo configurato)
- Regole:
  - validazione hard area iniziale (claim valido e area coerente)
  - create/expand via template command configurabile in `integration.yml`
  - error mapping deterministico verso error taxonomy CittaEXP

## ClassificheEXP contract
- Operazioni richieste:
  - fetch city ranking by cityId
  - fetch top kingdom
  - fetch top N snapshot
- Regole:
  - chiave canonica ranking: `city:<uuid>`
  - `findByCityId` ritorna `Optional.empty()` se la citta non compare nel top-scan
  - read-only: CittaEXP non scrive mai punteggi classifica
  - snapshot con `sourceVersion` e `fetchedAt` per tracciabilita

## Adapter port interni
- `VaultEconomyPort`
- `HuskClaimsPort`
- `RankingPort`
- `StaffApprovalPort` (queue backend)

## Failure handling
- Timeout/retry:
  - retry limitato su call esterne transienti.
  - nessun retry infinito sul main thread.
- Degradazione:
  - non prevista per dipendenze obbligatorie (fail-fast).
- Logging:
  - log strutturato con `dependency`, `operation`, `error_code`, `correlation_id`.
- Taxonomy errori integrazione:
  - `DEPENDENCY_UNAVAILABLE`
  - `EXTERNAL_INTEGRATION_ERROR`
  - `VALIDATION_ERROR`
