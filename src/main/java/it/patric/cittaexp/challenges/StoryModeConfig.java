package it.patric.cittaexp.challenges;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public record StoryModeConfig(
        boolean enabled,
        int chapterUnlockEveryLevels,
        int completionGatePercent,
        Map<String, StoryModeTaskTemplate> templates,
        List<StoryModeChapterDefinition> baseChapters,
        List<StoryModeChapterDefinition> eliteChapters
) {
    public static final StoryModeConfig DISABLED = new StoryModeConfig(false, 5, 100, Map.of(), List.of(), List.of());

    public List<StoryModeChapterDefinition> allChapters() {
        List<StoryModeChapterDefinition> all = new ArrayList<>();
        if (baseChapters != null) {
            all.addAll(baseChapters);
        }
        if (eliteChapters != null) {
            all.addAll(eliteChapters);
        }
        all.sort(Comparator.comparingInt(StoryModeChapterDefinition::order));
        return List.copyOf(all);
    }

    public StoryModeChapterDefinition chapter(String chapterId) {
        if (chapterId == null || chapterId.isBlank()) {
            return null;
        }
        for (StoryModeChapterDefinition definition : allChapters()) {
            if (definition.id() != null && definition.id().equalsIgnoreCase(chapterId)) {
                return definition;
            }
        }
        return null;
    }

    public int maxPage(int pageSize) {
        int safeSize = Math.max(1, pageSize);
        int count = Math.max(0, allChapters().size());
        return count == 0 ? 0 : (count - 1) / safeSize;
    }
}
