package it.patric.cittaexp.challenges;

import java.time.Instant;
import java.util.UUID;

public record StaffEventSubmission(
        String eventId,
        int townId,
        UUID submittedBy,
        Instant submittedAt,
        String worldName,
        double x,
        double y,
        double z,
        String note,
        StaffEventReviewStatus reviewStatus,
        UUID reviewedBy,
        Instant reviewedAt,
        String staffNote,
        Integer placement,
        boolean rewardGranted,
        Instant rewardGrantedAt
) {
}
