# CittaEXP GUI-First Framework Blueprint

## 1) Goal and boundaries

### Goal
Define a GUI-first architecture for CittaEXP before city business logic, using:
- `minecraft-common-lib` GUI/Dialog runtime contracts
- InvUI as the stable GUI backend
- ItemsAdder as optional visual enhancement

### Explicit non-goals for this phase
- No city persistence schema
- No war/claim/tax business implementation
- No hard ItemsAdder dependency
- No production command set definition

## 2) Runtime stack and dependency policy

### Mandatory runtime capabilities
- `GuiSessionService` (from common-lib)
- `DialogService` (from common-lib)
- `CapabilityRegistry` (for runtime capability visibility)

### GUI backend policy
- Primary backend: InvUI through common-lib adapter
- Fallback policy: always keep GUI functional on vanilla-safe assets
- Failure mode: if ItemsAdder is missing or unstable, only visual layer degrades

### ItemsAdder policy
- Local reference jar: `/Users/patric/Documents/Minecraft/EXTERNAL-LIBS/ItemsAdder_4.0.16-beta-11.jar`
- Scope in first cycle: visual assets only (icons, textured items, optional HUD hints)
- No user flow must depend on ItemsAdder-only API

## 3) Layered GUI framework (CittaEXP)

## 3.1 UI Contract layer (domain-agnostic)
Package target: `it.patric.cittaexp.ui.contract`

Lock these contracts first:
- `UiScreenKey`: canonical screen ids (`city.main.dashboard`, etc.)
- `UiActionKey`: canonical interaction ids (`city.open.members`, `city.roles.toggle.invite`)
- `UiAssetKey`: logical asset ids independent from backend (`ICON_CITY`, `CTA_UPGRADE`, `STATE_FREEZE_ON`)
- `UiViewState`: immutable state payload passed to renderers
- `UiPermissionGate`: permission checker contract
- `UiCapabilityGate`: capability checker contract (GUI available, IA visual available)
- `CityViewReadPort`: read-only city view provider, initially mock/in-memory

Contract constraints:
- No Bukkit class leakage in contract DTOs
- No persistence entities in UI contract objects
- All keys must be stable and string-based for telemetry and testing

## 3.2 GUI Framework layer
Package target: `it.patric.cittaexp.ui.framework`

### `GuiCatalog`
Responsibilities:
- Register all GUI definitions and metadata
- Expose one lookup API by `UiScreenKey`
- Keep screen metadata: title key, layout rows, required gates, default state builder

### `GuiFlowOrchestrator`
Responsibilities:
- Open/reopen/submenu/back flow rules
- Guard checks before open (permission, capability, city context)
- Route dialog return events back to target screens

### `GuiStateComposer`
Responsibilities:
- Build `UiViewState` from read ports + runtime flags
- Compute CTA enabled/disabled state
- Publish semantic tags for freeze/capital/rank badges

### `GuiActionRouter`
Responsibilities:
- Resolve click intent (`UiActionKey`) to application use case command
- Keep interaction rules decoupled from slot coordinates
- Emit explicit failure causes for denied actions (permission/freeze/precondition)

## 3.3 Theme and Asset layer
Package target: `it.patric.cittaexp.ui.theme`

### Theme source policy
- Layout and action topology: Java via `GuiDsl` (type-safe)
- Visual mapping: YAML theme files

### Theme schema (lock now)
- `theme.id`
- `assets.<UiAssetKey>.material`
- `assets.<UiAssetKey>.display_name`
- `assets.<UiAssetKey>.lore[]`
- `assets.<UiAssetKey>.custom_model_data` (optional)
- `assets.<UiAssetKey>.itemsadder` (optional `namespace:id`)

### Asset resolution chain
1. Try `ItemsAdderResolver` only when IA capability is reported available
2. Fallback to `VanillaResolver`
3. If both fail: return explicit placeholder asset (never break session)

## 4) GUI MVP screen map (before full city logic)

## 4.1 `city.main.dashboard`
Purpose:
- Single entry point for city status summary

Must show:
- City name/type
- Freeze status
- Rank snapshot (read-only)
- Quick links to members/roles/taxes/wizard

## 4.2 `city.members.list`
Purpose:
- Member roster and role visibility

Must show:
- Member head/icon
- Online status
- Assigned role label

## 4.3 `city.roles.manage`
Purpose:
- Role matrix management UX

Must show:
- Role list
- Flag toggles (invite, kick, manage members, etc.)
- Guarded actions for leader-only operations

## 4.4 `city.taxes.status`
Purpose:
- Monthly costs and freeze risk information

Must show:
- Current tax tier
- Next due date placeholder
- Freeze warning state

## 4.5 `city.creation.wizard`
Purpose:
- Guided city creation flow

Flow contract:
1. GUI start screen
2. Dialog for city name
3. Dialog for 3-letter tag
4. GUI step for banner/armor selection
5. Confirmation screen

## 5) Data flow and interaction flow contracts

### Open flow
- Caller requests `UiScreenKey`
- `GuiFlowOrchestrator` validates gates
- `GuiCatalog` provides definition
- `GuiStateComposer` builds initial state
- `GuiSessionService.open` opens session

### Click flow
- GUI interaction event arrives
- `GuiActionRouter` resolves `UiActionKey`
- Action executes or returns deny reason
- `GuiStateComposer` recomputes state
- Session update applied with optimistic revision

### Dialog bridge flow
- `OpenDialogAction` issued from GUI slot
- `DialogService` opens and tracks session
- On submit/close, orchestrator routes result
- GUI refresh uses latest `UiViewState`

## 6) ItemsAdder integration roadmap

## Step 1 (inside CittaEXP)
Deliverables:
- `UiAssetResolver` contract
- `VanillaResolver` default implementation
- `ItemsAdderResolver` optional implementation
- Capability-aware selector in theme layer

Acceptance criteria:
- Same GUI flows work with/without IA
- IA affects visuals only

## Step 2 (future in minecraft-common-lib)
Target:
- Propose reusable `adapter-itemsadder-gui` or visual bridge layer
- Keep `GuiPort` and `GuiSessionService` contracts unchanged

Acceptance criteria:
- Adapter can be bound through existing capability/binding model
- No functional regression in non-IA servers

## 7) Test scenarios to lock before coding

- Navigation: open -> submenu -> back -> close, state preserved
- State gating: freeze disables forbidden CTAs only
- Dialog bridge: wizard values return to GUI consistently
- Capability fallback: IA missing still yields fully usable menus
- Visual parity: IA on/off changes look only, not behavior
- Revision safety: stale revision updates do not corrupt sessions
- Guard enforcement: permission/city-context denies are explicit and deterministic

## 8) Definition of done for this blueprint phase

This planning phase is complete when:
- All contracts in section 3.1 are named and stabilized
- Screen keys in section 4 are accepted as canonical
- Fallback chain in section 3.3 is accepted as mandatory
- Step 1 and Step 2 roadmap boundaries are accepted
