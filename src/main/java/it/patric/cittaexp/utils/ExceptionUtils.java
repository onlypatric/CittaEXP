package it.patric.cittaexp.utils;

public final class ExceptionUtils {

    private ExceptionUtils() {
    }

    public static String safeMessage(Throwable throwable, String fallback) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return fallback;
        }
        return message;
    }
}

