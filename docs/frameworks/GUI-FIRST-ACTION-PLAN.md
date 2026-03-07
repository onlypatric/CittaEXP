# CittaEXP GUI-First Action Plan

## Summary
This action plan converts the GUI-first blueprint into implementation-ready work packages while keeping city logic out of scope for now.

## Phase A - Contract lock
Deliverables:
- Final list and naming of UI contracts (`UiScreenKey`, `UiActionKey`, `UiAssetKey`, `UiViewState`, `UiPermissionGate`, `UiCapabilityGate`, `CityViewReadPort`, `UiAssetResolver`)
- Package map and ownership per subsystem
- Canonical key dictionary for screens/actions/assets

Acceptance checks:
- No contract depends on persistence/domain entities
- No contract requires ItemsAdder runtime classes
- Key names are final and reusable in tests

## Phase B - GUI framework skeleton
Deliverables:
- `GuiCatalog` API shape and registration policy
- `GuiFlowOrchestrator` flow policy and guard matrix
- `GuiStateComposer` state assembly rules
- `GuiActionRouter` routing policy and error taxonomy

Acceptance checks:
- Every screen has one owner and one state composer path
- All navigation edges are explicit (no implicit jumps)
- Every action has deny reason semantics

## Phase C - Screen MVP specification
Deliverables:
- Full specification for:
  - `city.main.dashboard`
  - `city.members.list`
  - `city.roles.manage`
  - `city.taxes.status`
  - `city.creation.wizard`
- Slot policy per screen (informational, button, input, transfer)
- Role/permission guard matrix per action

Acceptance checks:
- Every screen defines entry conditions and exit routes
- Wizard flow has deterministic return path after dialogs
- Freeze constraints are reflected in CTA policy

## Phase D - Theme and asset model
Deliverables:
- YAML schema for `UiAssetKey` mapping
- `VanillaResolver` fallback policy
- `ItemsAdderResolver` optional policy (visual-only)
- Placeholder asset strategy for missing mappings

Acceptance checks:
- No menu becomes unusable if IA assets are missing
- Asset resolution order is deterministic
- Theme schema supports both vanilla and IA identifiers

## Phase E - Verification strategy
Deliverables:
- Test matrix by scenario:
  - navigation
  - state gating
  - dialog bridge
  - capability fallback
  - visual parity
  - revision safety
- Deterministic fixtures for `CityViewReadPort` mock data

Acceptance checks:
- All flows testable without persistence layer
- Tests can run with IA absent
- Tests can run with IA present (when enabled later)

## Decision log (locked)
- GUI base: common-lib GUI stack + InvUI backend
- ItemsAdder role in cycle 1: visual layer only
- Fallback policy: automatic vanilla-safe fallback
- Scope policy: architecture first, no city business implementation in this cycle
