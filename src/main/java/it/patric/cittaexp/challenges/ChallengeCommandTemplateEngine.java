package it.patric.cittaexp.challenges;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class ChallengeCommandTemplateEngine {

    public record RenderResult(boolean success, String command, String reasonCode) {
        public static RenderResult success(String command) {
            return new RenderResult(true, command, "");
        }

        public static RenderResult failed(String reasonCode) {
            return new RenderResult(false, "", reasonCode);
        }
    }

    private static final Pattern RANDOM_RANGE = Pattern.compile("randomrange\\(\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*\\)");

    private final CityChallengeSettings.TemplateFormatterSettings formatterSettings;
    private final ChallengeDifficultyResolver difficultyResolver;

    public ChallengeCommandTemplateEngine(CityChallengeSettings settings) {
        this.formatterSettings = settings.templateFormatter();
        this.difficultyResolver = new ChallengeDifficultyResolver(settings);
    }

    public RenderResult render(
            String template,
            ChallengeInstance instance,
            int townId,
            String townName,
            UUID playerId,
            int amount
    ) {
        if (template == null || template.isBlank() || instance == null) {
            return RenderResult.failed("template:invalid-randomrange");
        }

        ChallengeDifficulty difficulty = difficultyResolver.resolve(instance.cycleType(), instance.target());
        if (difficulty == null) {
            return RenderResult.failed("template:invalid-difficulty-context");
        }

        String command = applyStandardPlaceholders(template, instance, townId, townName, playerId, amount);
        command = command
                .replace("{difficulty}", difficulty.id())
                .replace("{difficulty_multiplier}", Double.toString(difficultyResolver.multiplier(difficulty)));

        RenderResult randomResolved = resolveRandomRange(command);
        if (!randomResolved.success()) {
            return randomResolved;
        }
        if (randomResolved.command().contains("randomrange(")) {
            return RenderResult.failed("template:invalid-randomrange");
        }
        return RenderResult.success(randomResolved.command());
    }

    private RenderResult resolveRandomRange(String source) {
        if (!formatterSettings.enabled() || source == null || source.isBlank()) {
            return RenderResult.success(source == null ? "" : source);
        }
        Matcher matcher = RANDOM_RANGE.matcher(source);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            long rawMin;
            long rawMax;
            try {
                rawMin = Long.parseLong(matcher.group(1));
                rawMax = Long.parseLong(matcher.group(2));
            } catch (NumberFormatException exception) {
                return RenderResult.failed("template:invalid-randomrange");
            }
            if (rawMin > rawMax) {
                return RenderResult.failed("template:invalid-randomrange");
            }
            long min = Math.max(rawMin, formatterSettings.randomRangeMinAllowed());
            long max = Math.min(rawMax, formatterSettings.randomRangeMaxAllowed());
            if (min > max) {
                return RenderResult.failed("template:range-out-of-bounds");
            }
            long value = min == max
                    ? min
                    : ThreadLocalRandom.current().nextLong(min, max + 1);
            matcher.appendReplacement(buffer, Long.toString(value));
        }
        matcher.appendTail(buffer);
        return RenderResult.success(buffer.toString());
    }

    private String applyStandardPlaceholders(
            String command,
            ChallengeInstance instance,
            int townId,
            String townName,
            UUID playerId,
            int amount
    ) {
        Player online = playerId == null ? null : Bukkit.getPlayer(playerId);
        String playerName = online != null ? online.getName() : "-";
        return command
                .replace("{town_id}", Integer.toString(townId))
                .replace("{town_name}", townName == null ? "-" : townName)
                .replace("{player_uuid}", playerId == null ? "-" : playerId.toString())
                .replace("{player_name}", playerName)
                .replace("{challenge_id}", instance.challengeId())
                .replace("{challenge_name}", instance.challengeName() == null || instance.challengeName().isBlank()
                        ? instance.challengeId()
                        : instance.challengeName())
                .replace("{cycle_key}", instance.cycleKey())
                .replace("{amount}", Integer.toString(amount));
    }
}
