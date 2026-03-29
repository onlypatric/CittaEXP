package it.patric.cittaexp.challenges;

public record ChallengeRewardBundle(
        String id,
        ChallengeRewardSpec completion,
        ChallengeRewardSpec winner,
        ChallengeRewardSpec excellence
) {
    public static ChallengeRewardBundle empty(String id) {
        return new ChallengeRewardBundle(id, ChallengeRewardSpec.EMPTY, ChallengeRewardSpec.EMPTY, ChallengeRewardSpec.EMPTY);
    }
}
