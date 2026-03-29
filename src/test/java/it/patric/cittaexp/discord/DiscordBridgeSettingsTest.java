package it.patric.cittaexp.discord;

import it.patric.cittaexp.levels.TownStage;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class DiscordBridgeSettingsTest {

    @Test
    void parsesConfigurationDefaults() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("enabled", true);
        yaml.set("token", "test-token");
        yaml.set("guildId", "123456789012345678");
        yaml.set("categoryId", "333333333333333333");
        yaml.set("verificationCategoryId", "444444444444444444");
        yaml.set("verificationChannelId", "555555555555555555");
        yaml.set("verifiedRoleId", "666666666666666666");
        yaml.set("verificationUrl", "https://discord.com/channels/123/555555555555555555");
        yaml.set("minStage", "VILLAGGIO_I");
        yaml.set("provisionAllTowns", true);
        yaml.set("naming.role", "Citta • {townTag}");
        yaml.set("naming.channel", "citta-{townSlug}");
        yaml.set("reconcileIntervalSeconds", 120);
        yaml.set("linkCodeTtlMinutes", 20);
        yaml.set("staffRoleIds", java.util.List.of("111", "222"));
        yaml.set("verboseLogging", false);

        DiscordBridgeSettings settings = DiscordBridgeSettings.fromConfiguration(yaml);
        Assertions.assertTrue(settings.enabled());
        Assertions.assertEquals("test-token", settings.token());
        Assertions.assertEquals(123456789012345678L, settings.guildId());
        Assertions.assertTrue(settings.categoryConfigured());
        Assertions.assertTrue(settings.verificationCategoryConfigured());
        Assertions.assertTrue(settings.verificationChannelConfigured());
        Assertions.assertTrue(settings.verifiedRoleConfigured());
        Assertions.assertEquals(666666666666666666L, settings.verifiedRoleId());
        Assertions.assertEquals("https://discord.com/channels/123/555555555555555555", settings.verificationUrl());
        Assertions.assertEquals(TownStage.VILLAGGIO_I, settings.minStage());
        Assertions.assertTrue(settings.provisionAllTowns());
        Assertions.assertEquals(2, settings.staffRoleIds().size());
        Assertions.assertFalse(settings.verboseLogging());
    }
}
