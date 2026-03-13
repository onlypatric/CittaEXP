# 07 - API, Eventi, Hooks

## API utili
- Towns API: CRUD/logica town.
- Claims API: check claim, create/edit/delete claim.
- Operations API: operation types custom e handler.

## Eventi principali
Da API Events:
- `TownCreateEvent`, `PostTownCreateEvent`, `TownUpdateEvent`, `TownDisbandEvent`,
- `ClaimEvent`, `UnClaimEvent`, `UnClaimAllEvent`,
- `MemberJoinEvent`, `MemberLeaveEvent`, `MemberRoleChangeEvent`,
- `PlayerEnterTownEvent`, `PlayerLeaveTownEvent`.

## Hooks
- Vault,
- LuckPerms contexts,
- PlaceholderAPI,
- HuskHomes,
- Plan,
- map plugins.

## Uso consigliato in CittaEXP
- agganciare gli eventi per read-model e sincronizzazioni,
- minimizzare polling quando possibile,
- mantenere idempotenza su side effects (queue/retry).
