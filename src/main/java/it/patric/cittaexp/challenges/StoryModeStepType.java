package it.patric.cittaexp.challenges;

public enum StoryModeStepType {
    TASK,
    MILESTONE,
    PROOF;

    public static StoryModeStepType fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return TASK;
        }
        try {
            return StoryModeStepType.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return TASK;
        }
    }
}
