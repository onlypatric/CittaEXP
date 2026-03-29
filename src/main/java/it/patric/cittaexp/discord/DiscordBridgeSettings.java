package it.patric.cittaexp.discord;

import it.patric.cittaexp.utils.YamlResourceBackfill;
import it.patric.cittaexp.levels.TownStage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

public final class DiscordBridgeSettings {

    private final boolean enabled;
    private final String token;
    private final String tokenEnvVar;
    private final long guildId;
    private final Long categoryId;
    private final Long verificationCategoryId;
    private final Long verificationChannelId;
    private final Long verifiedRoleId;
    private final String verificationUrl;
    private final TownStage minStage;
    private final String roleNameTemplate;
    private final String channelNameTemplate;
    private final long reconcileIntervalSeconds;
    private final long linkCodeTtlMinutes;
    private final List<Long> staffRoleIds;
    private final boolean verboseLogging;
    private final boolean provisionAllTowns;

    private DiscordBridgeSettings(
            boolean enabled,
            String token,
            String tokenEnvVar,
            long guildId,
            Long categoryId,
            Long verificationCategoryId,
            Long verificationChannelId,
            Long verifiedRoleId,
            String verificationUrl,
            TownStage minStage,
            String roleNameTemplate,
            String channelNameTemplate,
            long reconcileIntervalSeconds,
            long linkCodeTtlMinutes,
            List<Long> staffRoleIds,
            boolean verboseLogging,
            boolean provisionAllTowns
    ) {
        this.enabled = enabled;
        this.token = token == null ? "" : token.trim();
        this.tokenEnvVar = tokenEnvVar == null ? "CITTAEXP_DISCORD_TOKEN" : tokenEnvVar.trim();
        this.guildId = guildId;
        this.categoryId = categoryId;
        this.verificationCategoryId = verificationCategoryId;
        this.verificationChannelId = verificationChannelId;
        this.verifiedRoleId = verifiedRoleId;
        this.verificationUrl = verificationUrl == null ? "" : verificationUrl.trim();
        this.minStage = minStage == null ? TownStage.VILLAGGIO_I : minStage;
        this.roleNameTemplate = roleNameTemplate == null || roleNameTemplate.isBlank()
                ? "Citta • {townTag}"
                : roleNameTemplate;
        this.channelNameTemplate = channelNameTemplate == null || channelNameTemplate.isBlank()
                ? "citta-{townTag}-{townSlug}"
                : channelNameTemplate;
        this.reconcileIntervalSeconds = Math.max(60L, reconcileIntervalSeconds);
        this.linkCodeTtlMinutes = Math.max(1L, linkCodeTtlMinutes);
        this.staffRoleIds = List.copyOf(staffRoleIds == null ? List.of() : staffRoleIds);
        this.verboseLogging = verboseLogging;
        this.provisionAllTowns = provisionAllTowns;
    }

    public static DiscordBridgeSettings load(Plugin plugin) {
        YamlResourceBackfill.ensure(plugin, "discord.yml");
        File file = new File(plugin.getDataFolder(), "discord.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        return fromConfiguration(plugin, yaml);
    }

    static DiscordBridgeSettings fromConfiguration(YamlConfiguration yaml) {
        return fromConfiguration(null, yaml);
    }

    static DiscordBridgeSettings fromConfiguration(Plugin plugin, YamlConfiguration yaml) {
        String tokenEnvVar = stringOrDefault(yaml.getString("tokenEnvVar"), "CITTAEXP_DISCORD_TOKEN");
        String token = resolveToken(plugin, yaml, tokenEnvVar);
        long guildId = parseSnowflake(yaml.get("guildId"));
        Long categoryId = parseOptionalSnowflake(yaml.get("categoryId"));
        Long verificationCategoryId = parseOptionalSnowflake(yaml.get("verificationCategoryId"));
        Long verificationChannelId = parseOptionalSnowflake(yaml.get("verificationChannelId"));
        Long verifiedRoleId = parseOptionalSnowflake(yaml.get("verifiedRoleId"));
        String verificationUrl = stringOrDefault(yaml.getString("verificationUrl"), "");
        TownStage minStage = TownStage.fromDbValue(stringOrDefault(yaml.getString("minStage"), "VILLAGGIO_I").toUpperCase(Locale.ROOT));
        return new DiscordBridgeSettings(
                yaml.getBoolean("enabled", false),
                token,
                tokenEnvVar,
                guildId,
                categoryId,
                verificationCategoryId,
                verificationChannelId,
                verifiedRoleId,
                verificationUrl,
                minStage,
                stringOrDefault(yaml.getString("naming.role"), "Citta • {townTag}"),
                stringOrDefault(yaml.getString("naming.channel"), "citta-{townTag}-{townSlug}"),
                yaml.getLong("reconcileIntervalSeconds", 1800L),
                yaml.getLong("linkCodeTtlMinutes", 15L),
                parseSnowflakeList(yaml.getStringList("staffRoleIds")),
                yaml.getBoolean("verboseLogging", true),
                yaml.getBoolean("provisionAllTowns", true)
        );
    }

    private static String resolveToken(Plugin plugin, YamlConfiguration yaml, String tokenEnvVar) {
        String envToken = System.getenv(tokenEnvVar);
        if (envToken != null && !envToken.isBlank()) {
            return envToken.trim();
        }
        String dedicated = yaml.getString("token", "");
        if (dedicated != null && !dedicated.isBlank()) {
            return dedicated.trim();
        }
        if (plugin == null) {
            return "";
        }
        String legacy = plugin.getConfig().getString("discord.token", "");
        return legacy == null ? "" : legacy.trim();
    }

    private static List<Long> parseSnowflakeList(List<String> raw) {
        List<Long> ids = new ArrayList<>();
        if (raw == null) {
            return ids;
        }
        for (String entry : raw) {
            long value = parseSnowflake(entry);
            if (value > 0L) {
                ids.add(value);
            }
        }
        return ids;
    }

    private static long parseSnowflake(Object raw) {
        if (raw == null) {
            return 0L;
        }
        if (raw instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(raw.toString().trim());
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static Long parseOptionalSnowflake(Object raw) {
        long parsed = parseSnowflake(raw);
        return parsed > 0L ? parsed : null;
    }

    private static String stringOrDefault(String raw, String fallback) {
        return raw == null || raw.isBlank() ? fallback : raw;
    }

    public boolean enabled() {
        return enabled;
    }

    public String token() {
        return token;
    }

    public String tokenEnvVar() {
        return tokenEnvVar;
    }

    public long guildId() {
        return guildId;
    }

    public Long categoryId() {
        return categoryId;
    }

    public Long verificationCategoryId() {
        return verificationCategoryId;
    }

    public Long verificationChannelId() {
        return verificationChannelId;
    }

    public Long verifiedRoleId() {
        return verifiedRoleId;
    }

    public String verificationUrl() {
        return verificationUrl;
    }

    public TownStage minStage() {
        return minStage;
    }

    public String roleNameTemplate() {
        return roleNameTemplate;
    }

    public String channelNameTemplate() {
        return channelNameTemplate;
    }

    public long reconcileIntervalSeconds() {
        return reconcileIntervalSeconds;
    }

    public long linkCodeTtlMinutes() {
        return linkCodeTtlMinutes;
    }

    public List<Long> staffRoleIds() {
        return staffRoleIds;
    }

    public boolean tokenConfigured() {
        return !token.isBlank();
    }

    public boolean guildConfigured() {
        return guildId > 0L;
    }

    public boolean verboseLogging() {
        return verboseLogging;
    }

    public boolean provisionAllTowns() {
        return provisionAllTowns;
    }

    public boolean categoryConfigured() {
        return categoryId != null && categoryId > 0L;
    }

    public boolean verificationCategoryConfigured() {
        return verificationCategoryId != null && verificationCategoryId > 0L;
    }

    public boolean verificationChannelConfigured() {
        return verificationChannelId != null && verificationChannelId > 0L;
    }

    public boolean verifiedRoleConfigured() {
        return verifiedRoleId != null && verifiedRoleId > 0L;
    }
}
