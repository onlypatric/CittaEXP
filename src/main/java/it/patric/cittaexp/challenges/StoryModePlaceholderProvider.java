package it.patric.cittaexp.challenges;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class StoryModePlaceholderProvider {

    public record StoryModeQuestPlaceholder(
            String title,
            String description,
            int progressPercent,
            StoryQuestState state
    ) {
    }

    public record StoryModeChapterPlaceholder(
            String id,
            int number,
            String title,
            int progressPercent,
            StoryChapterState state,
            List<StoryModeQuestPlaceholder> quests
    ) {
    }

    public enum StoryChapterState {
        COMPLETED,
        IN_PROGRESS,
        UNLOCKED,
        LOCKED
    }

    public enum StoryQuestState {
        COMPLETED,
        IN_PROGRESS,
        AVAILABLE,
        LOCKED
    }

    private static final int PAGE_SIZE = 20;
    private static final List<Integer> CHAPTER_PROGRESS = List.of(
            100, 82, 67, 41, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0
    );
    private static final List<Map.Entry<String, String>> QUEST_TEMPLATES = List.of(
            Map.entry("Raccogli risorse", "Porta a casa materiali utili per far crescere la citta."),
            Map.entry("Metti al sicuro il deposito", "Consegna risorse rare e difendi le scorte del villaggio."),
            Map.entry("Allena i combattenti", "Sconfiggi creature ostili e aumenta la forza della citta."),
            Map.entry("Espandi la produzione", "Completa una serie di lavori per far girare meglio la citta."),
            Map.entry("Sostieni la comunita", "Contribuisci con oggetti o servizi utili ai membri."),
            Map.entry("Missione speciale", "Affronta una prova piu lunga e sblocca il prossimo capitolo.")
    );

    private final List<StoryModeChapterPlaceholder> chapters;

    public StoryModePlaceholderProvider() {
        this.chapters = buildChapters();
    }

    public List<StoryModeChapterPlaceholder> page(int page) {
        int safePage = Math.max(0, page);
        int start = safePage * PAGE_SIZE;
        if (start >= chapters.size()) {
            return List.of();
        }
        int end = Math.min(chapters.size(), start + PAGE_SIZE);
        return chapters.subList(start, end);
    }

    public int maxPage() {
        if (chapters.isEmpty()) {
            return 0;
        }
        return Math.max(0, (chapters.size() - 1) / PAGE_SIZE);
    }

    public StoryModeChapterPlaceholder chapter(String id) {
        if (id == null || id.isBlank()) {
            return chapters.isEmpty() ? null : chapters.getFirst();
        }
        for (StoryModeChapterPlaceholder chapter : chapters) {
            if (chapter.id().equalsIgnoreCase(id)) {
                return chapter;
            }
        }
        return chapters.isEmpty() ? null : chapters.getFirst();
    }

    public String firstChapterIdForPage(int page) {
        List<StoryModeChapterPlaceholder> entries = page(page);
        return entries.isEmpty() ? null : entries.getFirst().id();
    }

    private static List<StoryModeChapterPlaceholder> buildChapters() {
        List<StoryModeChapterPlaceholder> result = new ArrayList<>();
        int previousProgress = 100;
        for (int index = 0; index < CHAPTER_PROGRESS.size(); index++) {
            int number = index + 1;
            int progress = Math.max(0, Math.min(100, CHAPTER_PROGRESS.get(index)));
            StoryChapterState state;
            if (number == 1) {
                state = progress >= 100 ? StoryChapterState.COMPLETED
                        : (progress > 0 ? StoryChapterState.IN_PROGRESS : StoryChapterState.UNLOCKED);
            } else if (previousProgress < 50) {
                state = StoryChapterState.LOCKED;
                progress = 0;
            } else if (progress >= 100) {
                state = StoryChapterState.COMPLETED;
            } else if (progress > 0) {
                state = StoryChapterState.IN_PROGRESS;
            } else {
                state = StoryChapterState.UNLOCKED;
            }
            List<StoryModeQuestPlaceholder> quests = buildQuests(number, state, progress);
            result.add(new StoryModeChapterPlaceholder(
                    "story.chapter." + number,
                    number,
                    "Capitolo " + number,
                    progress,
                    state,
                    quests
            ));
            previousProgress = progress;
        }
        return List.copyOf(result);
    }

    private static List<StoryModeQuestPlaceholder> buildQuests(
            int chapterNumber,
            StoryChapterState chapterState,
            int chapterProgress
    ) {
        List<StoryModeQuestPlaceholder> quests = new ArrayList<>();
        for (int i = 0; i < QUEST_TEMPLATES.size(); i++) {
            Map.Entry<String, String> template = QUEST_TEMPLATES.get(i);
            StoryQuestState state;
            int progress;
            if (chapterState == StoryChapterState.LOCKED) {
                state = StoryQuestState.LOCKED;
                progress = 0;
            } else if (chapterState == StoryChapterState.COMPLETED) {
                state = StoryQuestState.COMPLETED;
                progress = 100;
            } else if (i == 0 && chapterProgress > 0) {
                state = StoryQuestState.IN_PROGRESS;
                progress = chapterProgress;
            } else if (i == 1 && chapterProgress >= 50) {
                state = StoryQuestState.IN_PROGRESS;
                progress = Math.max(50, Math.min(95, chapterProgress - 5));
            } else {
                state = chapterState == StoryChapterState.UNLOCKED
                        ? StoryQuestState.AVAILABLE
                        : StoryQuestState.LOCKED;
                progress = 0;
            }
            quests.add(new StoryModeQuestPlaceholder(
                    template.getKey(),
                    template.getValue() + " <gray>[Capitolo " + chapterNumber + "]</gray>",
                    progress,
                    state
            ));
        }
        return List.copyOf(quests);
    }
}
