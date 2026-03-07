package it.patric.cittaexp.runtime.dependency;

import dev.patric.commonlib.api.CommonComponent;
import dev.patric.commonlib.api.CommonContext;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

public final class RequiredDependenciesComponent implements CommonComponent, RequiredDependencyStatusService {

    private static final List<String> REQUIRED = List.of("Vault", "HuskClaims", "ClassificheEXP");

    private volatile RequiredDependencySnapshot snapshot = new RequiredDependencySnapshot(List.of());

    @Override
    public String id() {
        return "cittaexp-required-dependencies";
    }

    @Override
    public void onLoad(CommonContext context) {
        context.services().register(RequiredDependencyStatusService.class, this);
        this.snapshot = inspect(context.plugin().getServer().getPluginManager());
    }

    @Override
    public void onEnable(CommonContext context) {
        this.snapshot = inspect(context.plugin().getServer().getPluginManager());
        if (!snapshot.allAvailable()) {
            String details = snapshot.dependencies().stream()
                    .filter(dep -> dep.state() != DependencyState.AVAILABLE)
                    .map(dep -> dep.name() + ":" + dep.state())
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("n/a");
            throw new IllegalStateException("Required dependencies unavailable: " + details);
        }
    }

    @Override
    public RequiredDependencySnapshot snapshot() {
        return snapshot;
    }

    static RequiredDependencySnapshot inspect(PluginManager pluginManager) {
        List<ExternalDependencyStatus> rows = new ArrayList<>();
        for (String dependency : REQUIRED) {
            Plugin plugin = pluginManager.getPlugin(dependency);
            if (plugin == null) {
                rows.add(new ExternalDependencyStatus(
                        dependency,
                        DependencyState.MISSING,
                        "n/a",
                        "missing-plugin"
                ));
                continue;
            }
            if (!plugin.isEnabled()) {
                rows.add(new ExternalDependencyStatus(
                        dependency,
                        DependencyState.DISABLED,
                        safeVersion(plugin),
                        "plugin-disabled"
                ));
                continue;
            }
            rows.add(new ExternalDependencyStatus(
                    dependency,
                    DependencyState.AVAILABLE,
                    safeVersion(plugin),
                    ""
            ));
        }
        return new RequiredDependencySnapshot(rows);
    }

    private static String safeVersion(Plugin plugin) {
        try {
            if (plugin.getDescription() == null || plugin.getDescription().getVersion() == null) {
                return "unknown";
            }
            return plugin.getDescription().getVersion();
        } catch (RuntimeException ex) {
            return "unknown";
        }
    }
}
