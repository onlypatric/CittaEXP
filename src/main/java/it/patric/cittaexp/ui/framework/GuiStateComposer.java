package it.patric.cittaexp.ui.framework;

import it.patric.cittaexp.preview.PreviewScenario;
import it.patric.cittaexp.preview.ThemeMode;
import it.patric.cittaexp.ui.contract.CityViewReadPort;
import it.patric.cittaexp.ui.contract.UiCapabilityGate;
import it.patric.cittaexp.ui.contract.UiViewState;
import java.util.Objects;
import java.util.UUID;

public final class GuiStateComposer {

    private final CityViewReadPort readPort;
    private final UiCapabilityGate capabilityGate;

    public GuiStateComposer(CityViewReadPort readPort, UiCapabilityGate capabilityGate) {
        this.readPort = Objects.requireNonNull(readPort, "readPort");
        this.capabilityGate = Objects.requireNonNull(capabilityGate, "capabilityGate");
    }

    public UiViewState compose(UUID viewerId, PreviewScenario scenario, ThemeMode themeMode) {
        return new UiViewState(
                Objects.requireNonNull(scenario, "scenario"),
                Objects.requireNonNull(themeMode, "themeMode"),
                readPort.snapshot(Objects.requireNonNull(viewerId, "viewerId"), scenario),
                capabilityGate.guiAvailable(),
                capabilityGate.dialogAvailable(),
                capabilityGate.itemsAdderAvailable()
        );
    }
}
