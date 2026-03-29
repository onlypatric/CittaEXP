package it.patric.cittaexp.challenges;

public enum AtlasTier {
    I(1),
    II(2),
    III(3),
    IV_ELITE(4);

    private final int order;

    AtlasTier(int order) {
        this.order = order;
    }

    public int order() {
        return order;
    }

    public static AtlasTier fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toUpperCase(java.util.Locale.ROOT);
        for (AtlasTier value : values()) {
            if (value.name().equals(normalized)) {
                return value;
            }
            if (value.name().replace("_ELITE", "").equals(normalized)) {
                return value;
            }
        }
        return null;
    }
}
