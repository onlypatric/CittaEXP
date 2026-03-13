# 06 - Roles, Privileges, Permissions

## Doppio layer autorizzativo
1. Permission Bukkit (`husktowns.command.*`)
2. Privilegi ruolo town (`roles.yml`)

Entrambi influenzano l'esecuzione reale dei comandi.

## Ruoli e privilegi
- Ruoli per peso gerarchico.
- Privilegi per azioni specifiche (invite, claim, set_spawn, withdraw, level_up, ecc.).

## Implicazioni per wrapper CittaEXP
- il wrapper deve mappare bene errori privilege-denied,
- le policy overlay (freeze, ticket gate) si applicano in aggiunta,
- evitare policy duplicate che confliggono con gerarchia HuskTowns.

## Pattern sicuro
- controllo pre-policy CittaEXP,
- chiamata backend HuskTowns,
- mapping errori normalizzato verso i18n user-safe.
