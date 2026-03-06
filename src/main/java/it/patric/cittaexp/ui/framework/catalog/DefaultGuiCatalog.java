package it.patric.cittaexp.ui.framework.catalog;

import dev.patric.commonlib.api.gui.DialogResponseBinding;
import dev.patric.commonlib.api.gui.GuiAction;
import dev.patric.commonlib.api.gui.GuiDefinition;
import dev.patric.commonlib.api.gui.GuiDsl;
import dev.patric.commonlib.api.gui.GuiItemView;
import dev.patric.commonlib.api.gui.SlotInteractionPolicy;
import it.patric.cittaexp.dialog.CreationDialogTemplates;
import it.patric.cittaexp.ui.contract.CityViewReadPort;
import it.patric.cittaexp.ui.contract.UiActionKey;
import it.patric.cittaexp.ui.contract.UiAssetKey;
import it.patric.cittaexp.ui.contract.UiAssetResolver;
import it.patric.cittaexp.ui.contract.UiScreenKey;
import it.patric.cittaexp.ui.contract.UiViewState;
import it.patric.cittaexp.ui.framework.GuiActionRouter;
import it.patric.cittaexp.ui.framework.GuiCatalog;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class DefaultGuiCatalog implements GuiCatalog {

    private final UiAssetResolver assets;
    private final GuiActionRouter actions;

    public DefaultGuiCatalog(UiAssetResolver assets, GuiActionRouter actions) {
        this.assets = Objects.requireNonNull(assets, "assets");
        this.actions = Objects.requireNonNull(actions, "actions");
    }

    @Override
    public Map<UiScreenKey, GuiDefinition> build(UiViewState state) {
        Map<UiScreenKey, GuiDefinition> definitions = new LinkedHashMap<>();
        definitions.put(UiScreenKey.DASHBOARD, dashboard(state));
        definitions.put(UiScreenKey.MEMBERS, members(state));
        definitions.put(UiScreenKey.ROLES, roles(state));
        definitions.put(UiScreenKey.TAXES, taxes(state));
        definitions.put(UiScreenKey.WIZARD_START, wizardStart(state));
        definitions.put(UiScreenKey.WIZARD_BANNER_ARMOR, wizardBannerArmor(state));
        definitions.put(UiScreenKey.WIZARD_CONFIRM, wizardConfirm(state));
        return Map.copyOf(definitions);
    }

    private GuiDefinition dashboard(UiViewState state) {
        CityViewReadPort.CityViewSnapshot city = state.city();
        GuiDsl.Builder builder = GuiDsl.chest(UiScreenKey.DASHBOARD.key(), 6)
                .title("<gold>CittaEXP <gray>- " + city.cityName() + " [" + city.cityTag() + "]")
                .fill(0, 8, pane("BLACK_STAINED_GLASS_PANE"), SlotInteractionPolicy.LOCKED)
                .fill(45, 53, pane("BLACK_STAINED_GLASS_PANE"), SlotInteractionPolicy.LOCKED)
                .subMenuSlot(20, assets.resolve(UiAssetKey.ICON_MEMBERS, state), UiScreenKey.MEMBERS.key())
                .subMenuSlot(22, assets.resolve(UiAssetKey.ICON_ROLES, state), UiScreenKey.ROLES.key())
                .subMenuSlot(24, assets.resolve(UiAssetKey.ICON_TAXES, state), UiScreenKey.TAXES.key())
                .subMenuSlot(31, assets.resolve(UiAssetKey.ICON_WIZARD, state), UiScreenKey.WIZARD_START.key())
                .button(53, assets.resolve(UiAssetKey.CTA_CANCEL, state), actions.actions(UiActionKey.CLOSE));

        builder.slot(11, slot -> slot
                .item(assets.resolve(city.frozen() ? UiAssetKey.STATE_FREEZE_ON : UiAssetKey.STATE_FREEZE_OFF, state))
                .interaction(SlotInteractionPolicy.LOCKED));

        builder.slot(13, slot -> slot
                .item(assets.resolve(city.capital() ? UiAssetKey.STATE_CAPITAL_ON : UiAssetKey.STATE_CAPITAL_OFF, state))
                .interaction(SlotInteractionPolicy.LOCKED));

        builder.slot(15, slot -> slot
                .item(item(
                        assets.resolve(UiAssetKey.ICON_CITY, state).materialKey(),
                        "<aqua>Rank #" + city.rank(),
                        List.of(
                                "<gray>Score: <white>" + city.score(),
                                "<gray>Online: <white>" + city.onlineMembers() + "/" + city.maxMembers(),
                                "<gray>Scenario: <white>" + state.scenario().key()
                        )
                ))
                .interaction(SlotInteractionPolicy.LOCKED));

        return builder.build();
    }

    private GuiDefinition members(UiViewState state) {
        GuiDsl.Builder builder = GuiDsl.chest(UiScreenKey.MEMBERS.key(), 6)
                .title("<aqua>Members <gray>- " + state.city().cityName())
                .fill(0, 8, pane("CYAN_STAINED_GLASS_PANE"), SlotInteractionPolicy.LOCKED)
                .fill(45, 53, pane("CYAN_STAINED_GLASS_PANE"), SlotInteractionPolicy.LOCKED)
                .backSlot(45, assets.resolve(UiAssetKey.CTA_BACK, state))
                .button(53, assets.resolve(UiAssetKey.CTA_CANCEL, state), actions.actions(UiActionKey.CLOSE));

        int slot = 10;
        for (CityViewReadPort.MemberView member : state.city().members()) {
            if (slot >= 44) {
                break;
            }
            builder.slot(slot, s -> s
                    .item(item(
                            "PLAYER_HEAD",
                            "<white>" + member.name(),
                            List.of(
                                    "<gray>Role: <yellow>" + member.role(),
                                    "<gray>Status: " + (member.online() ? "<green>Online" : "<red>Offline")
                            )
                    ))
                    .interaction(SlotInteractionPolicy.LOCKED));
            slot += (slot % 9 == 7) ? 3 : 1;
        }

        return builder.build();
    }

    private GuiDefinition roles(UiViewState state) {
        GuiDsl.Builder builder = GuiDsl.chest(UiScreenKey.ROLES.key(), 6)
                .title("<yellow>Roles <gray>- " + state.city().cityName())
                .fill(0, 8, pane("YELLOW_STAINED_GLASS_PANE"), SlotInteractionPolicy.LOCKED)
                .fill(45, 53, pane("YELLOW_STAINED_GLASS_PANE"), SlotInteractionPolicy.LOCKED)
                .backSlot(45, assets.resolve(UiAssetKey.CTA_BACK, state))
                .button(53, assets.resolve(UiAssetKey.CTA_CANCEL, state), actions.actions(UiActionKey.CLOSE));

        int slot = 10;
        for (CityViewReadPort.RoleFlagView role : state.city().roleFlags()) {
            if (slot >= 44) {
                break;
            }
            builder.slot(slot, s -> s
                    .item(item(
                            assets.resolve(UiAssetKey.ICON_ROLES, state).materialKey(),
                            "<white>" + role.roleName(),
                            List.of(
                                    flagLine("Invite", role.canInvite()),
                                    flagLine("Kick", role.canKick()),
                                    flagLine("Manage", role.canManageMembers())
                            )
                    ))
                    .interaction(SlotInteractionPolicy.LOCKED));
            slot += 2;
        }

        builder.switchSlot(
                31,
                "roles.toggle.invite",
                item("LIME_DYE", "<green>Invite Enabled", List.of("<gray>Template interactive toggle")),
                item("GRAY_DYE", "<red>Invite Disabled", List.of("<gray>Template interactive toggle")),
                !state.city().frozen()
        );

        return builder.build();
    }

    private GuiDefinition taxes(UiViewState state) {
        CityViewReadPort.CityViewSnapshot city = state.city();
        return GuiDsl.chest(UiScreenKey.TAXES.key(), 6)
                .title("<gold>Taxes <gray>- " + city.cityName())
                .fill(0, 8, pane("ORANGE_STAINED_GLASS_PANE"), SlotInteractionPolicy.LOCKED)
                .fill(45, 53, pane("ORANGE_STAINED_GLASS_PANE"), SlotInteractionPolicy.LOCKED)
                .slot(13, s -> s
                        .item(item(
                                assets.resolve(UiAssetKey.ICON_TAXES, state).materialKey(),
                                "<gold>Monthly Tax",
                                List.of(
                                        "<gray>Tier: <white>" + city.tier().name(),
                                        "<gray>Cost: <white>" + city.monthlyTaxLabel(),
                                        "<gray>Next due: <white>" + city.nextTaxDue()
                                )
                        ))
                        .interaction(SlotInteractionPolicy.LOCKED))
                .slot(31, s -> s
                        .item(item(
                                city.frozen() ? "REDSTONE_BLOCK" : "LIME_CONCRETE",
                                city.frozen() ? "<red>Freeze restrictions active" : "<green>No freeze restrictions",
                                List.of(
                                        "<gray>Invites, upgrades, war and claims",
                                        "<gray>are blocked only during freeze"
                                )
                        ))
                        .interaction(SlotInteractionPolicy.LOCKED))
                .backSlot(45, assets.resolve(UiAssetKey.CTA_BACK, state))
                .button(53, assets.resolve(UiAssetKey.CTA_CANCEL, state), actions.actions(UiActionKey.CLOSE))
                .build();
    }

    private GuiDefinition wizardStart(UiViewState state) {
        return GuiDsl.chest(UiScreenKey.WIZARD_START.key(), 6)
                .title("<light_purple>Creation Wizard <gray>- Step 1")
                .fill(0, 8, pane("PURPLE_STAINED_GLASS_PANE"), SlotInteractionPolicy.LOCKED)
                .fill(45, 53, pane("PURPLE_STAINED_GLASS_PANE"), SlotInteractionPolicy.LOCKED)
                .dialogInputSlot(
                        20,
                        assets.resolve(UiAssetKey.CTA_DIALOG_NAME, state),
                        CreationDialogTemplates.TEMPLATE_CITY_NAME,
                        List.of(new DialogResponseBinding("city_name", "wizard.city_name", true))
                )
                .dialogInputSlot(
                        24,
                        assets.resolve(UiAssetKey.CTA_DIALOG_TAG, state),
                        CreationDialogTemplates.TEMPLATE_CITY_TAG,
                        List.of(new DialogResponseBinding("city_tag", "wizard.city_tag", true))
                )
                .subMenuSlot(31, assets.resolve(UiAssetKey.CTA_NEXT, state), UiScreenKey.WIZARD_BANNER_ARMOR.key())
                .backSlot(45, assets.resolve(UiAssetKey.CTA_BACK, state))
                .button(53, assets.resolve(UiAssetKey.CTA_CANCEL, state), actions.actions(UiActionKey.CLOSE))
                .build();
    }

    private GuiDefinition wizardBannerArmor(UiViewState state) {
        return GuiDsl.chest(UiScreenKey.WIZARD_BANNER_ARMOR.key(), 6)
                .title("<light_purple>Creation Wizard <gray>- Step 2")
                .fill(0, 8, pane("MAGENTA_STAINED_GLASS_PANE"), SlotInteractionPolicy.LOCKED)
                .fill(45, 53, pane("MAGENTA_STAINED_GLASS_PANE"), SlotInteractionPolicy.LOCKED)
                .slot(20, s -> s
                        .item(assets.resolve(UiAssetKey.CTA_BANNER, state))
                        .interaction(SlotInteractionPolicy.BUTTON_ONLY)
                        .actions(actions.actions(UiActionKey.OPEN_WIZARD_CONFIRM)))
                .slot(24, s -> s
                        .item(assets.resolve(UiAssetKey.CTA_ARMOR, state))
                        .interaction(SlotInteractionPolicy.BUTTON_ONLY)
                        .actions(actions.actions(UiActionKey.OPEN_WIZARD_CONFIRM)))
                .subMenuSlot(31, assets.resolve(UiAssetKey.CTA_NEXT, state), UiScreenKey.WIZARD_CONFIRM.key())
                .backSlot(45, assets.resolve(UiAssetKey.CTA_BACK, state))
                .button(53, assets.resolve(UiAssetKey.CTA_CANCEL, state), actions.actions(UiActionKey.CLOSE))
                .build();
    }

    private GuiDefinition wizardConfirm(UiViewState state) {
        return GuiDsl.chest(UiScreenKey.WIZARD_CONFIRM.key(), 6)
                .title("<light_purple>Creation Wizard <gray>- Confirm")
                .fill(0, 8, pane("PINK_STAINED_GLASS_PANE"), SlotInteractionPolicy.LOCKED)
                .fill(45, 53, pane("PINK_STAINED_GLASS_PANE"), SlotInteractionPolicy.LOCKED)
                .slot(22, s -> s
                        .item(item(
                                assets.resolve(UiAssetKey.CTA_CONFIRM, state).materialKey(),
                                "<green>Submit Template Flow",
                                List.of(
                                        "<gray>Name/Tag values are bound in GUI state",
                                        "<gray>through DialogResponseBinding",
                                        "<gray>Scope: preview-only, no persistence"
                                )
                        ))
                        .interaction(SlotInteractionPolicy.LOCKED))
                .slot(31, s -> s
                        .item(assets.resolve(UiAssetKey.CTA_CONFIRM, state))
                        .interaction(SlotInteractionPolicy.BUTTON_ONLY)
                        .actions(actions.actions(UiActionKey.OPEN_DASHBOARD)))
                .backSlot(45, assets.resolve(UiAssetKey.CTA_BACK, state))
                .button(53, assets.resolve(UiAssetKey.CTA_CANCEL, state), actions.actions(UiActionKey.CLOSE))
                .build();
    }

    private static GuiItemView pane(String material) {
        return item(material, " ", List.of());
    }

    private static GuiItemView item(String material, String display, List<String> lore) {
        return new GuiItemView(material, display, lore);
    }

    private static String flagLine(String label, boolean enabled) {
        return "<gray>" + label + ": " + (enabled ? "<green>ON" : "<red>OFF");
    }
}
