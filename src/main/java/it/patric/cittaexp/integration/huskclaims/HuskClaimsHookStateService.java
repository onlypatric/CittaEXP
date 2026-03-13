package it.patric.cittaexp.integration.huskclaims;

import java.io.File;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

public final class HuskClaimsHookStateService {

    public enum GuardMode {
        NATIVE_HOOK_ACTIVE("native"),
        FALLBACK_GUARD_ACTIVE("fallback"),
        HUSKCLAIMS_UNAVAILABLE("off");

        private final String probeValue;

        GuardMode(String probeValue) {
            this.probeValue = probeValue;
        }

        public String probeValue() {
            return probeValue;
        }
    }

    public record Diagnostics(
            GuardMode mode,
            boolean huskClaimsPresent,
            boolean huskTownsHookEnabled,
            String reasonCode,
            String configPath
    ) {
    }

    private static final String HUSKCLAIMS_PLUGIN = "HuskClaims";

    private final Diagnostics diagnostics;

    public HuskClaimsHookStateService(Plugin plugin) {
        this.diagnostics = detect(plugin);
    }

    public Diagnostics diagnostics() {
        return diagnostics;
    }

    public GuardMode mode() {
        return diagnostics.mode();
    }

    public boolean isFallbackActive() {
        return diagnostics.mode() == GuardMode.FALLBACK_GUARD_ACTIVE;
    }

    static GuardMode resolveMode(boolean huskClaimsPresent, boolean huskTownsHookEnabled) {
        if (!huskClaimsPresent) {
            return GuardMode.HUSKCLAIMS_UNAVAILABLE;
        }
        return huskTownsHookEnabled ? GuardMode.NATIVE_HOOK_ACTIVE : GuardMode.FALLBACK_GUARD_ACTIVE;
    }

    private Diagnostics detect(Plugin plugin) {
        Plugin huskClaims = plugin.getServer().getPluginManager().getPlugin(HUSKCLAIMS_PLUGIN);
        if (huskClaims == null) {
            return new Diagnostics(
                    GuardMode.HUSKCLAIMS_UNAVAILABLE,
                    false,
                    false,
                    "missing-plugin:HuskClaims",
                    "-"
            );
        }
        if (!huskClaims.isEnabled()) {
            return new Diagnostics(
                    GuardMode.HUSKCLAIMS_UNAVAILABLE,
                    false,
                    false,
                    "disabled-plugin:HuskClaims",
                    "-"
            );
        }

        File configFile = new File(huskClaims.getDataFolder(), "config.yml");
        String configPath = configFile.getAbsolutePath();
        boolean hookEnabled = true;
        String reasonCode = "native-hook-enabled";

        if (configFile.exists()) {
            try {
                YamlConfiguration yml = YamlConfiguration.loadConfiguration(configFile);
                hookEnabled = yml.getBoolean("hooks.husktowns.enabled", true);
                reasonCode = hookEnabled ? "native-hook-enabled" : "native-hook-disabled";
            } catch (RuntimeException ex) {
                hookEnabled = false;
                reasonCode = "hook-config-read-failed";
            }
        } else {
            reasonCode = "hook-config-missing-default-true";
        }

        return new Diagnostics(
                resolveMode(true, hookEnabled),
                true,
                hookEnabled,
                reasonCode,
                configPath
        );
    }
}
