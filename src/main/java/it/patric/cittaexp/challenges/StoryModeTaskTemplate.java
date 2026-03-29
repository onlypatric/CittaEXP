package it.patric.cittaexp.challenges;

public record StoryModeTaskTemplate(
        String id,
        String title,
        String description,
        StoryModeStepType stepType,
        StoryModeProofType proofType,
        AtlasChapter atlasChapter,
        String atlasFamilyId,
        AtlasTier atlasTier,
        String defenseTier,
        boolean enabled
) {
    public boolean usesAtlasProgress() {
        return atlasFamilyId != null && !atlasFamilyId.isBlank() && atlasTier != null;
    }

    public boolean usesDefenseProgress() {
        return defenseTier != null && !defenseTier.isBlank();
    }
}
