# 04 - Claims e Rules

## Modello claim
- Unit base: chunk.
- Claim registrati per world claimabile.
- Vincoli configurabili: adjacency, distanza minima tra town, mondi non claimabili.

## Tipi claim
- standard town claim,
- farm claim,
- plot claim,
- admin claims.

## Regole operative
- rules/flags configurabili (es. build/container/interact ecc.),
- plot members e manager gestibili via command,
- map e inspector strumenti nativi per debug/access.

## Authority check per integrazioni
Per validazioni critical (es. warp inside claim, perk inside claim):
- usare check live da API/backbone HuskTowns,
- non usare cache statica come authority.

## Anti-pattern da evitare
- duplicare ownership claim in un secondo DB come fonte primaria,
- mantenere sync manuale fragile tra plugin overlay e stato claim reale.
