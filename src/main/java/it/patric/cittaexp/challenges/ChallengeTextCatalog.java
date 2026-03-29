package it.patric.cittaexp.challenges;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

public final class ChallengeTextCatalog {

    private static final String FILE_NAME = "challenge-texts.yml";

    private final Map<String, String> objectiveNames;
    private final Map<String, String> objectiveDescriptions;
    private final Map<String, String> definitionDescriptions;
    private final Map<String, String> narrativeTemplates;

    private ChallengeTextCatalog(
            Map<String, String> objectiveNames,
            Map<String, String> objectiveDescriptions,
            Map<String, String> definitionDescriptions,
            Map<String, String> narrativeTemplates
    ) {
        this.objectiveNames = objectiveNames;
        this.objectiveDescriptions = objectiveDescriptions;
        this.definitionDescriptions = definitionDescriptions;
        this.narrativeTemplates = narrativeTemplates;
    }

    public static ChallengeTextCatalog load(Plugin plugin) {
        plugin.saveResource(FILE_NAME, false);
        File file = new File(plugin.getDataFolder(), FILE_NAME);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        Map<String, String> names = new HashMap<>();
        Map<String, String> descriptions = new HashMap<>();
        Map<String, String> challengeDescriptions = new HashMap<>();
        Map<String, String> narrativeTemplates = new HashMap<>();

        ConfigurationSection objectives = yaml.getConfigurationSection("objectives");
        if (objectives != null) {
            for (String objectiveId : objectives.getKeys(false)) {
                ConfigurationSection section = objectives.getConfigurationSection(objectiveId);
                if (section == null) {
                    continue;
                }
                String key = normalize(objectiveId);
                names.put(key, section.getString("name", ""));
                descriptions.put(key, readTextBlock(section, "description"));
            }
        }

        ConfigurationSection definitions = yaml.getConfigurationSection("definitions");
        if (definitions != null) {
            for (String challengeId : definitions.getKeys(false)) {
                ConfigurationSection section = definitions.getConfigurationSection(challengeId);
                if (section == null) {
                    continue;
                }
                challengeDescriptions.put(normalize(challengeId), readTextBlock(section, "description"));
            }
        }
        ConfigurationSection narratives = yaml.getConfigurationSection("narratives");
        if (narratives != null) {
            for (String objectiveId : narratives.getKeys(false)) {
                String template = readSectionTextOrList(narratives, objectiveId);
                if (template != null && !template.isBlank()) {
                    narrativeTemplates.put(normalize(objectiveId), template);
                }
            }
        }
        return new ChallengeTextCatalog(
                Map.copyOf(names),
                Map.copyOf(descriptions),
                Map.copyOf(challengeDescriptions),
                Map.copyOf(narrativeTemplates)
        );
    }

    public String objectiveLabel(ChallengeObjectiveType type) {
        String objectiveId = type == null ? "unknown" : normalize(type.id());
        String value = objectiveNames.getOrDefault(objectiveId, "");
        if (value == null || value.isBlank()) {
            return ChallengeObjectivePresentation.defaultName(type);
        }
        return value;
    }

    public String objectiveDescription(
            String challengeId,
            ChallengeObjectiveType type,
            int target,
            String challengeName,
            String focusLabel,
            String variantLabel
    ) {
        String objectiveId = type == null ? "unknown" : normalize(type.id());
        String challengeKey = normalize(challengeId);
        String template = definitionDescriptions.getOrDefault(challengeKey, "");
        if (template == null || template.isBlank()) {
            template = objectiveDescriptions.getOrDefault(objectiveId, "");
        }
        if (template == null || template.isBlank()) {
            template = ChallengeObjectivePresentation.defaultDescription(type);
        }
        return renderTemplate(template, target, challengeId, challengeName, objectiveLabel(type), focusLabel, variantLabel);
    }

    public String narrativeTemplate(ChallengeObjectiveType type) {
        String objectiveId = type == null ? "" : normalize(type.id());
        return narrativeTemplates.getOrDefault(objectiveId, "");
    }

    private static String renderTemplate(
            String template,
            int target,
            String challengeId,
            String challengeName,
            String objectiveName,
            String focusLabel,
            String variantLabel
    ) {
        return template
                .replace("{target}", Integer.toString(Math.max(0, target)))
                .replace("{challenge_id}", safe(challengeId))
                .replace("{challenge}", safe(challengeName))
                .replace("{objective}", safe(objectiveName))
                .replace("{focus_label}", safe(focusLabel))
                .replace("{variant_label}", safe(variantLabel));
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String readTextBlock(ConfigurationSection section, String key) {
        if (section == null || key == null || key.isBlank()) {
            return "";
        }
        String listKey = key + "Lines";
        if (section.isList(listKey)) {
            return joinLines(section.getStringList(listKey));
        }
        if (section.isList(key)) {
            return joinLines(section.getStringList(key));
        }
        return section.getString(key, "");
    }

    private static String readSectionTextOrList(ConfigurationSection section, String key) {
        if (section == null || key == null || key.isBlank()) {
            return "";
        }
        if (section.isList(key)) {
            return joinLines(section.getStringList(key));
        }
        return section.getString(key, "");
    }

    private static String joinLines(java.util.List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append('\n');
            }
            out.append(line);
        }
        return out.toString();
    }
}
