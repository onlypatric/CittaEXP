package it.patric.cittaexp.challenges;

import java.util.Locale;
import org.bukkit.Material;

public final class ChallengeLoreFormatter {

    public record ProgressPhase(
            boolean excellencePhase,
            int percent,
            int current,
            int total,
            String barMiniMessage
    ) {
    }

    private ChallengeLoreFormatter() {
    }

    public static String modeLabel(ChallengeMode mode) {
        if (mode == null) {
            return "Sfida";
        }
        return switch (mode) {
            case DAILY_STANDARD -> "Giornaliera";
            case DAILY_SPRINT_1830, DAILY_SPRINT_2130, DAILY_RACE_1600, DAILY_RACE_2100 -> "Competizione Giornaliera";
            case WEEKLY_CLASH -> "Clash Settimanale";
            case MONTHLY_CROWN -> "Corona Mensile";
            case MONTHLY_LEDGER_GRAND -> "Mensile | Grande Progetto";
            case MONTHLY_LEDGER_CONTRACT -> "Mensile | Contratto";
            case MONTHLY_LEDGER_MYSTERY -> "Mensile | Mistero";
            case SEASON_CODEX_ACT_I, SEASON_CODEX_ACT_II, SEASON_CODEX_ACT_III -> "Stagionale";
            case SEASON_CODEX_ELITE -> "Stagionale | Elite";
            case SEASON_CODEX_HIDDEN_RELIC -> "Stagionale | Segreta";
            case SEASONAL_RACE -> "Competizione Stagionale (Legacy)";
            case WEEKLY_STANDARD -> "Settimanale";
            case MONTHLY_STANDARD -> "Mensile";
            case MONTHLY_EVENT_A, MONTHLY_EVENT_B -> "Evento Mensile";
        };
    }

    public static int percent(int progress, int target) {
        if (target <= 0) {
            return 0;
        }
        int safeProgress = Math.max(0, Math.min(target, progress));
        return (int) Math.floor((safeProgress * 100.0D) / target);
    }

    public static String progressBarMiniMessage(int percent, int width) {
        int safeWidth = Math.max(10, width);
        int safePercent = Math.max(0, Math.min(100, percent));
        int filled = (int) Math.round((safePercent / 100.0D) * safeWidth);
        StringBuilder out = new StringBuilder();
        out.append("<green>");
        for (int i = 0; i < filled; i++) {
            out.append("█");
        }
        out.append("</green><dark_gray>");
        for (int i = filled; i < safeWidth; i++) {
            out.append("█");
        }
        out.append("</dark_gray>");
        return out.toString();
    }

    public static ProgressPhase phase(int progress, int target, int excellenceTarget, int width) {
        int safeTarget = Math.max(1, target);
        int safeExcellenceTarget = Math.max(safeTarget, excellenceTarget);
        int safeProgress = Math.max(0, Math.min(safeExcellenceTarget, progress));
        if (safeProgress < safeTarget) {
            int percent = percent(safeProgress, safeTarget);
            return new ProgressPhase(false, percent, safeProgress, safeTarget, baseBar(percent, width));
        }
        int phaseTotal = Math.max(1, safeExcellenceTarget - safeTarget);
        int phaseCurrent = Math.max(0, Math.min(phaseTotal, safeProgress - safeTarget));
        int percent = percent(phaseCurrent, phaseTotal);
        return new ProgressPhase(true, percent, phaseCurrent, phaseTotal, excellenceBar(percent, width));
    }

    private static String baseBar(int percent, int width) {
        int safeWidth = Math.max(10, width);
        int safePercent = Math.max(0, Math.min(100, percent));
        int filled = (int) Math.round((safePercent / 100.0D) * safeWidth);
        StringBuilder out = new StringBuilder();
        out.append("<green>");
        for (int i = 0; i < filled; i++) {
            out.append("█");
        }
        out.append("</green><dark_gray>");
        for (int i = filled; i < safeWidth; i++) {
            out.append("█");
        }
        out.append("</dark_gray>");
        return out.toString();
    }

    private static String excellenceBar(int percent, int width) {
        int safeWidth = Math.max(10, width);
        int safePercent = Math.max(0, Math.min(100, percent));
        int filled = (int) Math.round((safePercent / 100.0D) * safeWidth);
        StringBuilder out = new StringBuilder();
        out.append("<gradient:#f1c40f:#e74c3c>");
        for (int i = 0; i < filled; i++) {
            out.append("█");
        }
        out.append("</gradient><dark_gray>");
        for (int i = filled; i < safeWidth; i++) {
            out.append("█");
        }
        out.append("</dark_gray>");
        return out.toString();
    }

    public static String materialLabel(Material material) {
        if (material == null) {
            return "Item";
        }
        return keyLabel(material.name());
    }

    public static String keyLabel(String key) {
        if (key == null || key.isBlank()) {
            return "-";
        }
        String[] parts = key.toLowerCase(Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    public static String variantLabel(ChallengeVariantType type) {
        if (type == null) {
            return "Globale";
        }
        return switch (type) {
            case GLOBAL -> "Globale";
            case SPECIFIC -> "Specifica";
        };
    }
}
