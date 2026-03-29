package it.patric.cittaexp.custommobs;

public record CustomMobLocalBroadcasts(
        String spawnMessage,
        String phaseMessage,
        String defeatMessage
) {
    public static final CustomMobLocalBroadcasts EMPTY = new CustomMobLocalBroadcasts("", "", "");
}
