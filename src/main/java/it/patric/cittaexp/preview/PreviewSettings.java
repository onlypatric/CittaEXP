package it.patric.cittaexp.preview;

import java.util.Objects;

public final class PreviewSettings {

    private volatile PreviewScenario scenario = PreviewScenario.DEFAULT;
    private volatile ThemeMode themeMode = ThemeMode.AUTO;

    public PreviewScenario scenario() {
        return scenario;
    }

    public ThemeMode themeMode() {
        return themeMode;
    }

    public void setScenario(PreviewScenario scenario) {
        this.scenario = Objects.requireNonNull(scenario, "scenario");
    }

    public void setThemeMode(ThemeMode themeMode) {
        this.themeMode = Objects.requireNonNull(themeMode, "themeMode");
    }
}
