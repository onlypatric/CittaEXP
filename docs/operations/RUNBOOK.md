# CittaEXP Runbook Operativo

## Prerequisiti runtime
- Paper `1.21.11`
- Java `21`
- Plugin obbligatori abilitati:
  - Vault
  - HuskClaims
  - ClassificheEXP
- Opzionale:
  - ItemsAdder (enhancement visuale)

## Startup checklist
1. Verificare presenza jar `CittaEXP` e dipendenze in `plugins/`.
2. Avviare server.
3. Controllare log enable:
   - nessun errore dependency guard
   - persistence mode iniziale valido
4. Eseguire `/cittaexp probe` e verificare:
   - capability GUI/DIALOG/PERSISTENCE
   - stato dependency (Vault/HuskClaims/ClassificheEXP)
   - stato replay outbox (pending/conflicts)

## Diagnostica rapida
- `dependency MISSING/DISABLED`
  - installare/abilitare plugin mancante, riavviare server.
- `PERSISTENCE UNAVAILABLE`
  - verificare config `persistence.yml`, connessione MySQL, permessi file sqlite.
- `outbox pending` cresce senza decremento
  - verificare raggiungibilita MySQL e errori replay in log.
- `outbox conflicts` > 0
  - aprire audit, verificare revision conflict e applicare remediation staff.

## Procedure operative
- Freeze manuale/Unfreeze:
  - usare workflow ticket staff (nessun edit diretto DB).
- Upgrade tier:
  - sempre tramite richiesta + approvazione staff.
- Delete villaggio/regno:
  - richiesta ticket + conferma staff + verifica claim.

## Recovery
- MySQL down:
  - plugin passa in SQLite fallback writable.
  - mantenere monitoraggio outbox pending.
- MySQL up:
  - replay automatico batch.
  - confermare `outbox pending=0`.

## Safe restart
1. Nessuna operazione staff critica in corso.
2. Verificare outbox in stato consistente.
3. Arrestare server in modo pulito.
4. Riavviare e rieseguire probe.

## Alerting raccomandato
- dependency unavailable
- persistence mode `UNAVAILABLE`
- outbox conflicts > 0
- tax cycle failure
- ranking sync failure
