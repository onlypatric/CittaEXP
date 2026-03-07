package it.patric.cittaexp.runtime.dependency;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RequiredDependenciesComponentTest {

    @Test
    void inspectReportsMissingPlugin() {
        PluginManager pluginManager = mock(PluginManager.class);
        Plugin vault = enabledPlugin("Vault");
        Plugin classificheExp = enabledPlugin("ClassificheEXP");
        when(pluginManager.getPlugin("Vault")).thenReturn(vault);
        when(pluginManager.getPlugin("HuskClaims")).thenReturn(null);
        when(pluginManager.getPlugin("ClassificheEXP")).thenReturn(classificheExp);

        RequiredDependencySnapshot snapshot = RequiredDependenciesComponent.inspect(pluginManager);

        assertFalse(snapshot.allAvailable());
        assertEquals(DependencyState.MISSING, snapshot.dependencies().stream()
                .filter(dep -> dep.name().equals("HuskClaims"))
                .findFirst()
                .orElseThrow()
                .state());
    }

    @Test
    void inspectReportsAllAvailable() {
        PluginManager pluginManager = mock(PluginManager.class);
        Plugin vault = enabledPlugin("Vault");
        Plugin huskClaims = enabledPlugin("HuskClaims");
        Plugin classificheExp = enabledPlugin("ClassificheEXP");
        when(pluginManager.getPlugin("Vault")).thenReturn(vault);
        when(pluginManager.getPlugin("HuskClaims")).thenReturn(huskClaims);
        when(pluginManager.getPlugin("ClassificheEXP")).thenReturn(classificheExp);

        RequiredDependencySnapshot snapshot = RequiredDependenciesComponent.inspect(pluginManager);

        assertTrue(snapshot.allAvailable());
    }

    private static Plugin enabledPlugin(String name) {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getName()).thenReturn(name);
        when(plugin.isEnabled()).thenReturn(true);
        return plugin;
    }
}
