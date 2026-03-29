package it.patric.cittaexp.challenges;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record StoryModeChapterDefinition(
        String id,
        int order,
        String title,
        String act,
        boolean elite,
        int minCityLevel,
        AtlasChapter primaryChapter,
        List<AtlasChapter> taskFamilies,
        String narrativeBeat,
        String signatureGoal,
        String chapterProof,
        String completionFantasy,
        List<String> taskTemplateIds,
        String milestoneTemplateId,
        String proofTemplateId,
        ChallengeRewardSpec reward
) {
    public List<String> allTemplateIds() {
        Set<String> ids = new LinkedHashSet<>();
        if (taskTemplateIds != null) {
            ids.addAll(taskTemplateIds.stream().filter(id -> id != null && !id.isBlank()).toList());
        }
        if (milestoneTemplateId != null && !milestoneTemplateId.isBlank()) {
            ids.add(milestoneTemplateId);
        }
        if (proofTemplateId != null && !proofTemplateId.isBlank()) {
            ids.add(proofTemplateId);
        }
        return List.copyOf(new ArrayList<>(ids));
    }
}
