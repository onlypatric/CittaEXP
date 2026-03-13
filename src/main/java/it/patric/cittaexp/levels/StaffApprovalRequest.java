package it.patric.cittaexp.levels;

import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.io.Serializable;

public record StaffApprovalRequest(
        long id,
        int townId,
        StaffApprovalType type,
        String payload,
        CityLevelRepository.RequestStatus status,
        UUID requestedBy,
        UUID reviewedBy,
        String reason,
        String note,
        Instant createdAt,
        Instant reviewedAt
) implements Serializable {

    public static String encodePayload(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (builder.length() > 0) {
                builder.append(';');
            }
            builder.append(entry.getKey()).append('=');
            String value = entry.getValue() == null ? "" : entry.getValue();
            builder.append(Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }
        return builder.toString();
    }

    public static Map<String, String> decodePayload(String payload) {
        Map<String, String> values = new LinkedHashMap<>();
        if (payload == null || payload.isBlank()) {
            return values;
        }
        String[] entries = payload.split(";");
        for (String entry : entries) {
            int split = entry.indexOf('=');
            if (split <= 0) {
                continue;
            }
            String key = entry.substring(0, split);
            String raw = entry.substring(split + 1);
            try {
                String value = new String(Base64.getUrlDecoder().decode(raw), java.nio.charset.StandardCharsets.UTF_8);
                values.put(key, value);
            } catch (IllegalArgumentException ignored) {
                // Skip malformed payload segments.
            }
        }
        return values;
    }

    public String payloadValue(String key) {
        return decodePayload(payload).get(key);
    }

    public StaffApprovalRequest withStatus(
            CityLevelRepository.RequestStatus newStatus,
            UUID reviewer,
            String newNote,
            Instant reviewedAtInstant
    ) {
        return new StaffApprovalRequest(
                id,
                townId,
                type,
                payload,
                newStatus,
                requestedBy,
                reviewer,
                reason,
                newNote,
                createdAt,
                reviewedAtInstant
        );
    }
}
