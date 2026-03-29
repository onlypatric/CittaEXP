package it.patric.cittaexp.challenges;

public enum ChallengeDifficulty {
    EASY("easy"),
    NORMAL("normal"),
    HARD("hard");

    private final String id;

    ChallengeDifficulty(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
