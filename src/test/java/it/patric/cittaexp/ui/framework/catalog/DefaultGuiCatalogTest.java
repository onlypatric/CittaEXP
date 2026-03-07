package it.patric.cittaexp.ui.framework.catalog;

import dev.patric.commonlib.api.gui.GuiDefinition;
import dev.patric.commonlib.api.gui.GuiItemView;
import dev.patric.commonlib.api.gui.SlotDefinition;
import dev.patric.commonlib.api.gui.SlotInteractionPolicy;
import it.patric.cittaexp.data.InMemoryCityViewReadPort;
import it.patric.cittaexp.preview.PreviewScenario;
import it.patric.cittaexp.preview.ThemeMode;
import it.patric.cittaexp.ui.contract.UiAssetKey;
import it.patric.cittaexp.ui.contract.UiAssetResolver;
import it.patric.cittaexp.ui.contract.UiScreenKey;
import it.patric.cittaexp.ui.contract.UiViewState;
import it.patric.cittaexp.ui.framework.GuiActionRouter;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultGuiCatalogTest {

    private static final EnumSet<UiScreenKey> BACK_AND_CANCEL_SCREENS = EnumSet.of(
            UiScreenKey.MEMBERS,
            UiScreenKey.ROLES,
            UiScreenKey.TAXES,
            UiScreenKey.WIZARD_START,
            UiScreenKey.WIZARD_BANNER_ARMOR,
            UiScreenKey.WIZARD_CONFIRM
    );

    @Test
    void buildAllScreensAcrossScenariosAndThemesWithoutCollisions() {
        DefaultGuiCatalog catalog = new DefaultGuiCatalog(assetResolver(), new GuiActionRouter());
        InMemoryCityViewReadPort readPort = new InMemoryCityViewReadPort();
        UUID viewer = UUID.fromString("00000000-0000-0000-0000-000000000001");

        for (PreviewScenario scenario : PreviewScenario.values()) {
            for (ThemeMode themeMode : ThemeMode.values()) {
                UiViewState state = new UiViewState(
                        scenario,
                        themeMode,
                        readPort.snapshot(viewer, scenario),
                        true,
                        true,
                        true
                );

                Map<UiScreenKey, GuiDefinition> definitions = assertDoesNotThrow(() -> catalog.build(state));
                assertEquals(UiScreenKey.values().length, definitions.size());
            }
        }
    }

    @Test
    void footerActionSlotsAreReservedAsExpected() {
        DefaultGuiCatalog catalog = new DefaultGuiCatalog(assetResolver(), new GuiActionRouter());
        UiViewState state = new UiViewState(
                PreviewScenario.DEFAULT,
                ThemeMode.VANILLA,
                new InMemoryCityViewReadPort().snapshot(UUID.randomUUID(), PreviewScenario.DEFAULT),
                true,
                true,
                false
        );

        Map<UiScreenKey, GuiDefinition> definitions = catalog.build(state);
        for (UiScreenKey screenKey : UiScreenKey.values()) {
            GuiDefinition definition = definitions.get(screenKey);
            assertNotNull(definition);

            SlotDefinition cancel = definition.slots().get(53);
            assertNotNull(cancel, "missing cancel slot for " + screenKey.key());
            assertEquals(SlotInteractionPolicy.BUTTON_ONLY, cancel.interaction());

            if (BACK_AND_CANCEL_SCREENS.contains(screenKey)) {
                SlotDefinition back = definition.slots().get(45);
                assertNotNull(back, "missing back slot for " + screenKey.key());
                assertEquals(SlotInteractionPolicy.BUTTON_ONLY, back.interaction());
            } else {
                SlotDefinition footer = definition.slots().get(45);
                assertNotNull(footer, "missing footer slot for " + screenKey.key());
                assertEquals(SlotInteractionPolicy.LOCKED, footer.interaction());
            }

            assertTrue(definition.slots().containsKey(52), "missing footer slot 52 for " + screenKey.key());
        }
    }

    private static UiAssetResolver assetResolver() {
        return (UiAssetKey key, UiViewState state) -> new GuiItemView("STONE", key.name(), List.of("preview"));
    }
}
