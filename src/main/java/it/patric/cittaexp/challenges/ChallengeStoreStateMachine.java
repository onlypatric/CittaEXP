package it.patric.cittaexp.challenges;

public final class ChallengeStoreStateMachine {

    public enum State {
        BOOTING("booting"),
        SQLITE_ONLY("sqlite-only"),
        NORMAL("mysql-primary"),
        RECOVERY("mysql-recovery"),
        DEGRADED("sqlite-degraded");

        private final String modeLabel;

        State(String modeLabel) {
            this.modeLabel = modeLabel;
        }

        public String modeLabel() {
            return modeLabel;
        }
    }

    private volatile State state = State.BOOTING;

    public State state() {
        return state;
    }

    public String modeLabel() {
        return state.modeLabel();
    }

    public void toSqliteOnly() {
        state = State.SQLITE_ONLY;
    }

    public void toNormal() {
        state = State.NORMAL;
    }

    public void toRecovery() {
        state = State.RECOVERY;
    }

    public void toDegraded() {
        state = State.DEGRADED;
    }
}

