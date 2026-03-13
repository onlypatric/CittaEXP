package it.patric.cittaexp.utils;

import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;

public final class DialogInputUtils {

    private static final Pattern DECIMAL_PATTERN = Pattern.compile("^(?:0|[1-9]\\d*)(?:[.,]\\d{1,2})?$");

    private DialogInputUtils() {
    }

    public static DialogInput text(
            String key,
            int width,
            Component label,
            boolean required,
            String initialValue,
            int maxLength
    ) {
        return DialogInput.text(
                key,
                width,
                label,
                required,
                initialValue == null ? "" : initialValue,
                maxLength,
                null
        );
    }

    public static String normalized(DialogResponseView response, String fieldKey, PluginConfigUtils cfg) {
        return cfg.normalizeText(response.getText(fieldKey));
    }

    public static String normalizedUpper(
            DialogResponseView response,
            String fieldKey,
            PluginConfigUtils cfg
    ) {
        return normalized(response, fieldKey, cfg).toUpperCase(Locale.ROOT);
    }

    public static Optional<BigDecimal> parsePositiveAmount(DialogResponseView response, String fieldKey, PluginConfigUtils cfg) {
        String rawAmount = normalized(response, fieldKey, cfg);
        if (!DECIMAL_PATTERN.matcher(rawAmount).matches()) {
            return Optional.empty();
        }
        try {
            BigDecimal amount = new BigDecimal(rawAmount.replace(',', '.'));
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                return Optional.empty();
            }
            return Optional.of(amount);
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }
}
