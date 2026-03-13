package it.patric.cittaexp.createtown;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import net.william278.husktowns.town.Town;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

public final class CityCreationTraceStore {

    private final Plugin plugin;
    private final File file;

    public CityCreationTraceStore(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "city-creations.yml");
    }

    public synchronized void saveTownCreation(Town town, UUID creatorUuid, String creatorName, String tag)
            throws IOException {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            throw new IOException("Impossibile creare data folder plugin");
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String base = "towns." + town.getId();
        yaml.set(base + ".id", town.getId());
        yaml.set(base + ".name", town.getName());
        yaml.set(base + ".tag", tag);
        yaml.set(base + ".creator.uuid", creatorUuid.toString());
        yaml.set(base + ".creator.name", creatorName);
        yaml.set(base + ".createdAt", Instant.now().toString());
        yaml.save(file);
    }
}
