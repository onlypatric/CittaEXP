package it.patric.cittaexp.ui.theme;

import dev.patric.commonlib.api.gui.GuiItemView;
import it.patric.cittaexp.preview.PreviewScenario;
import it.patric.cittaexp.preview.ThemeMode;
import it.patric.cittaexp.ui.contract.CityViewReadPort;
import it.patric.cittaexp.ui.contract.UiAssetKey;
import it.patric.cittaexp.ui.contract.UiAssetResolver;
import it.patric.cittaexp.ui.contract.UiViewState;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultUiAssetResolverTest {

    @Test
    void autoModeUsesItemsAdderResolverWhenCapabilityIsAvailable() {
        UiAssetResolver vanilla = (key, state) -> new GuiItemView("STONE", "vanilla", List.of());
        UiAssetResolver itemsAdder = (key, state) -> new GuiItemView("cittaexp:icon_city", "itemsadder", List.of());

        DefaultUiAssetResolver resolver = new DefaultUiAssetResolver(vanilla, itemsAdder);
        UiViewState state = testState(ThemeMode.AUTO, true);

        GuiItemView item = resolver.resolve(UiAssetKey.ICON_CITY, state);
        assertEquals("cittaexp:icon_city", item.materialKey());
    }

    @Test
    void autoModeFallsBackToVanillaWhenItemsAdderIsUnavailable() {
        UiAssetResolver vanilla = (key, state) -> new GuiItemView("STONE", "vanilla", List.of());
        UiAssetResolver itemsAdder = (key, state) -> new GuiItemView("cittaexp:icon_city", "itemsadder", List.of());

        DefaultUiAssetResolver resolver = new DefaultUiAssetResolver(vanilla, itemsAdder);
        UiViewState state = testState(ThemeMode.AUTO, false);

        GuiItemView item = resolver.resolve(UiAssetKey.ICON_CITY, state);
        assertEquals("STONE", item.materialKey());
    }

    private static UiViewState testState(ThemeMode mode, boolean itemsAdderAvailable) {
        return new UiViewState(
                PreviewScenario.DEFAULT,
                mode,
                new CityViewReadPort.CityViewSnapshot(
                        "City",
                        "CTY",
                        CityViewReadPort.CityTier.BORGO,
                        false,
                        false,
                        10,
                        100,
                        3,
                        10,
                        "2026-04-01",
                        "2 NB",
                        List.of(),
                        List.of()
                ),
                true,
                true,
                itemsAdderAvailable
        );
    }
}
