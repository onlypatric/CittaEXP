package it.patric.cittaexp.permission;

import it.patric.cittaexp.ui.contract.UiPermissionGate;
import java.util.Objects;
import org.bukkit.command.CommandSender;

public final class StaffUiPermissionGate implements UiPermissionGate {

    private final String permission;

    public StaffUiPermissionGate(String permission) {
        this.permission = Objects.requireNonNull(permission, "permission");
    }

    @Override
    public boolean canOpenPreview(CommandSender sender) {
        return sender != null && sender.hasPermission(permission);
    }

    public String permission() {
        return permission;
    }
}
