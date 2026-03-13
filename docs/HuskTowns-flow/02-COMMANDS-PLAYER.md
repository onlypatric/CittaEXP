# 02 - Comandi Player (`/town`)

## Struttura
Root player: `/town`.
Permessi Bukkit + privilegi ruolo town.

## Lifecycle e membership
- `/town create <name>`
- `/town about [town]` (overview; `/town` senza argomenti apre la tua)
- `/town list [sort asc|desc] [page]`
- `/town invite <player|accept|decline [target]>`
- `/town promote <member>`
- `/town demote <member>`
- `/town evict <member>`
- `/town transfer <member>`
- `/town leave`
- `/town disband [confirm]`

## Claims
- `/town claim [x z] [-m]`
- `/town unclaim [x z|all confirm] [-m]`
- `/town autoclaim`
- `/town map [x z] [world]`
- `/town deeds`

## Claims speciali
- `/town farm`
- `/town plot <members|claim|add|remove ...>`
- `/town rules [flag claim_type true|false] [-m]`

## Spawn e privacy
- `/town spawn [town]`
- `/town setspawn`
- `/town clearspawn`
- `/town privacy <public|private>`

## Economia town
- `/town deposit <amount>`
- `/town withdraw <amount>`
- `/town level [confirm]`

## Metadati e social
- `/town bio <text>`
- `/town greeting <text>`
- `/town farewell <text>`
- `/town color [#hex]`
- `/town rename <name>`
- `/town chat [message]`
- `/town player <player>`
- `/town census [name]`
- `/town logs [page]`

## Optional features
- `/town relations ...` se relations enabled
- `/town war ...` se wars enabled

## Implicazione wrapper `/city`
Se vuoi wrapper completo, i blocchi minimi da mappare sono:
1. lifecycle,
2. claims,
3. rank/transfer,
4. spawn,
5. economy.
