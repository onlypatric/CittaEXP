# CittaEXP (Bootstrap Reset)

Hard cut repository: CittaEXP e stato ridotto a uno scheletro minimo per ripartire da zero con HuskTowns come backbone autoritativo.

## Stato attuale
- Dipendenza runtime unica: `HuskTowns`
- Wrapper minimale:
  - `/city create|info|invite|request|kick|leave|warp`
- Diagnostica minima:
  - `/cittaexp probe`

## Build
```bash
./gradlew clean shadowJar
```

## Start locale
```bash
./scripts/start.sh
```

## Obiettivo
Costruire nuovamente CittaEXP step-by-step sopra HuskTowns, mantenendo codice semplice e con poca complessita iniziale.
