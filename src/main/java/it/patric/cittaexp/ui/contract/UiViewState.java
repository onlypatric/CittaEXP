package it.patric.cittaexp.ui.contract;

import it.patric.cittaexp.preview.PreviewScenario;
import it.patric.cittaexp.preview.ThemeMode;
import java.util.Objects;

public record UiViewState(
        PreviewScenario scenario,
        ThemeMode themeMode,
        CityViewReadPort.CityViewSnapshot city,
        boolean guiAvailable,
        boolean dialogAvailable,
        boolean itemsAdderAvailable
) {

    public UiViewState {
        scenario = Objects.requireNonNull(scenario, "scenario");
        themeMode = Objects.requireNonNull(themeMode, "themeMode");
        city = Objects.requireNonNull(city, "city");
    }
}
