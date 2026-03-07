# CittaEXP Domain Services

## Service boundaries
- `CityLifecycleService`
  - create city (wizard), transfer leader, request tier upgrade, request deletion.
- `MembershipService`
  - invite/accept invite, request join/approve/reject, kick, leave.
- `RoleService`
  - CRUD ruoli custom + aggiornamento permission set.
- `TaxService`
  - gestione policy tasse + ciclo mensile + freeze automatico per insolvenza.
- `CapitalService`
  - sync ranking, assegnazione/revoca capitale, bonus economico capitale.
- `ClaimService`
  - auto-claim 100x100 in creazione e expand claim con costo.
- `StaffApprovalService`
  - queue ticket + review action (approve/reject/cancel) + audit.
- `RankingReadService`
  - query read-only ranking snapshot e top kingdom.

## Transaction policy
- Ogni use case applicativo usa `CityTxPort` per boundary transazionale.
- Operazioni multi-step persistono prima stato dominio, poi scrivono `AuditEvent` e `city_outbox` (se fallback SQLite).
- Retry idempotente obbligatorio su operazioni che coinvolgono economia e claim.

## Guardie applicative
- Precondizioni globali:
  - dipendenze obbligatorie disponibili (`Vault`, `HuskClaims`, `ClassificheEXP`).
  - city non `DELETED`.
- Guardie role-based:
  - invite/kick/manage members governati da `RolePermissionSet`.
- Guardie freeze:
  - blocco su invite/kick/claim expand/upgrade/shop.
- Guardie tier:
  - upgrade validi solo `BORGO->VILLAGGIO->REGNO`.

## Eventi e audit
Eventi minimi da registrare in audit:
- city_created, leader_transferred, tier_upgrade_requested, tier_upgrade_applied
- member_invited, member_joined, member_kicked, member_left
- role_created, role_updated, role_deleted
- tax_cycle_started, tax_charged, city_frozen_tax_default, city_unfrozen
- claim_created, claim_expanded
- capital_assigned, capital_revoked
- approval_ticket_opened, approval_ticket_reviewed
- city_deleted

## Error taxonomy
- `VALIDATION_ERROR` (input/constraint)
- `PERMISSION_DENIED`
- `FREEZE_RESTRICTED`
- `DEPENDENCY_UNAVAILABLE`
- `CONFLICT_STALE_REVISION`
- `EXTERNAL_INTEGRATION_ERROR`
- `INTERNAL_ERROR`

## Completion criteria (service layer)
- Tutti i service sopra implementati con test unit + integration.
- Nessun percorso business critico senza audit event.
- Ogni percorso mutante usa transazione + optimistic locking.
