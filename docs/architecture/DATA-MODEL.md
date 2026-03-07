# CittaEXP Data Model (Full Scope, War esclusa)

## Obiettivo
Definire il modello dati definitivo del plugin CittaEXP per coprire il ciclo vita completo citta, membership, ruoli, tasse, capitale, claim, approvazioni staff e audit.

## Aggregate principali
- `City`
  - Identita: `city_id (UUID)`
  - Campi: `name`, `tag`, `leader_uuid`, `tier`, `status`, `capital`, `treasury_balance`, `member_count`, `max_members`, `revision`, timestamps.
- `CityMember`
  - Identita: `(city_id, player_uuid)`
  - Campi: `role_key`, `status`, `joined_at`, `updated_at`.
- `CityRole`
  - Identita: `(city_id, role_key)`
  - Campi: `display_name`, `priority`, `permissions` (`RolePermissionSet`), `updated_at`.
- `CityGovernance`
  - Identita: `city_id`
  - Campi: `vice_uuid`, `updated_at`.
- `MemberClaimPermission`
  - Identita: `(city_id, player_uuid)`
  - Campi: `perm_access`, `perm_container`, `perm_build`, `updated_by`, `updated_at`.
- `TaxPolicy`
  - Config globale tasse in valuta Vault.
- `CapitalState`
  - Regno attualmente capitale + metadata fonte classifica.
- `ClaimBinding`
  - Bound claim ufficiale della citta (world + bounds + area).
- `RankingSnapshot`
  - Snapshot read-only da ClassificheEXP.
- `StaffApprovalTicket`
  - Ticket approvazione per `UPGRADE`, `UNFREEZE`, `DELETE`.
- `FreezeCase`
  - Storico freeze (apertura/chiusura).
- `CityDeletionCase`
  - Workflow eliminazione citta con gate staff.
- `CityInvitation`
  - Inviti membership con scadenza.
- `JoinRequest`
  - Richieste ingresso player -> citta.
- `CityTreasuryLedgerEntry`
  - Ledger append-only per movimenti economici.
- `AuditEvent`
  - Audit append-only per operazioni sensibili.

## Enum e stati canonici
- `CityTier`: `BORGO`, `VILLAGGIO`, `REGNO`
- `CityStatus`: `ACTIVE`, `FROZEN`, `PENDING_DELETE`, `DELETED`
- `TicketType`: `UPGRADE`, `UNFREEZE`, `DELETE`
- `TicketStatus`: `PENDING`, `APPROVED`, `REJECTED`, `CANCELLED`
- `MemberStatus`: `ACTIVE`, `LEFT`, `KICKED`
- `FreezeReason`: `TAX_DEFAULT`, `STAFF_MANUAL`, `COMPLIANCE_BLOCK`

## Invarianti dominio (hard)
- Un player puo appartenere a una sola citta attiva.
- `name` e `tag` citta unici case-insensitive.
- Ogni citta ha un solo leader attivo.
- Ogni citta puo avere al massimo un solo vice attivo.
- I ruoli sono per-citta (no global roles), `role_key` unico dentro la citta.
- Ruoli di sistema immutabili: `CAPO` e `VICE`.
- Gerarchia moderazione: `CAPO > VICE > ruoli custom`.
- Freeze blocca solo azioni vietate: invite, kick, claim expand, upgrade, shop regno.
- Uscita/rimozione leader:
  - con vice attivo -> successione automatica vice->capo.
  - senza vice e citta non vuota -> operazione bloccata.
- Se esce l'ultimo membro: hard-delete citta + claim + dati correlati.
- Claim permissions membri:
  - capo/vice sempre trust massimo operativo,
  - membri default `access`,
  - override granulari `access/container/build` per utente.
- La capitale e sempre un `REGNO` e coincide con top rank corrente.
- Eliminazione `BORGO` autonoma; `VILLAGGIO/REGNO` richiede ticket staff approvato.

## SQL schema definitivo (runtime)
Tabelle principali:
- `cities`
- `city_members`
- `city_roles`
- `city_governance`
- `city_member_claim_permissions`
- `tax_policy`
- `city_treasury_ledger`
- `capital_state`
- `claim_bindings`
- `ranking_snapshot`
- `staff_approval_tickets`
- `freeze_cases`
- `city_deletion_cases`
- `city_invitations`
- `join_requests`
- `audit_events`
- `city_outbox` (fallback/replay)

Indici obbligatori:
- lookup rapidi per `city_id`, status queue (`staff_approval_tickets.status`), outbox replay (`city_outbox.replay_status, occurred_at`), audit stream (`aggregate_type, aggregate_id, occurred_at`).

## State machine sintetiche
- `CityStatus`
  - `ACTIVE -> FROZEN` (tax default/staff)
  - `FROZEN -> ACTIVE` (ticket UNFREEZE approvato o override staff)
  - `ACTIVE/FROZEN -> PENDING_DELETE` (delete request)
  - `PENDING_DELETE -> DELETED` (conferma finale)
- `TicketStatus`
  - `PENDING -> APPROVED|REJECTED|CANCELLED`

## Note di compatibilita
- War e alleanze sono fuori scope: nessuna entita runtime dedicata.
- Ranking resta read-only; nessuna ownership del punteggio in CittaEXP.
