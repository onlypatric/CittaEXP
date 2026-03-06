package it.patric.cittaexp.ui.contract;

import org.bukkit.command.CommandSender;

public interface UiPermissionGate {

    boolean canOpenPreview(CommandSender sender);
}
