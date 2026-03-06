package it.patric.cittaexp.capability;

import dev.patric.commonlib.api.capability.CapabilityRegistry;
import dev.patric.commonlib.api.capability.StandardCapabilities;
import it.patric.cittaexp.ui.contract.UiCapabilityGate;
import java.util.Objects;

public final class RuntimeUiCapabilityGate implements UiCapabilityGate {

    private final CapabilityRegistry capabilityRegistry;

    public RuntimeUiCapabilityGate(CapabilityRegistry capabilityRegistry) {
        this.capabilityRegistry = Objects.requireNonNull(capabilityRegistry, "capabilityRegistry");
    }

    @Override
    public boolean guiAvailable() {
        return capabilityRegistry.isAvailable(StandardCapabilities.GUI);
    }

    @Override
    public boolean dialogAvailable() {
        return capabilityRegistry.isAvailable(StandardCapabilities.DIALOG);
    }

    @Override
    public boolean itemsAdderAvailable() {
        return capabilityRegistry.isAvailable(StandardCapabilities.ITEMSADDER);
    }
}
