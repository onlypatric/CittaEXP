package it.patric.cittaexp.shop;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CityShopFormatParser {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("^([BS])(\\d+)(K?)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern QUANTITY_PATTERN = Pattern.compile("^Q\\s*:\\s*(\\d+)$", Pattern.CASE_INSENSITIVE);

    private CityShopFormatParser() {
    }

    public static Optional<PriceSpec> parsePrices(String rawLine) {
        String line = normalize(rawLine);
        if (line.isEmpty()) {
            return Optional.empty();
        }
        String[] rawTokens = line.split(":");
        Long buy = null;
        Long sell = null;
        for (String token : rawTokens) {
            String trimmed = normalize(token);
            if (trimmed.isEmpty()) {
                return Optional.empty();
            }
            Matcher matcher = TOKEN_PATTERN.matcher(trimmed);
            if (!matcher.matches()) {
                return Optional.empty();
            }
            char side = Character.toUpperCase(matcher.group(1).charAt(0));
            long value;
            try {
                value = Long.parseLong(matcher.group(2));
            } catch (NumberFormatException exception) {
                return Optional.empty();
            }
            if (value <= 0L) {
                return Optional.empty();
            }
            if (!matcher.group(3).isEmpty()) {
                if (value > Long.MAX_VALUE / 1000L) {
                    return Optional.empty();
                }
                value *= 1000L;
            }
            if (side == 'B') {
                if (buy != null) {
                    return Optional.empty();
                }
                buy = value;
            } else {
                if (sell != null) {
                    return Optional.empty();
                }
                sell = value;
            }
        }
        if (buy == null && sell == null) {
            return Optional.empty();
        }
        return Optional.of(new PriceSpec(buy, sell));
    }

    public static Optional<Integer> parseTradeQuantity(String rawLine) {
        String line = normalize(rawLine);
        Matcher matcher = QUANTITY_PATTERN.matcher(line);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        try {
            int quantity = Integer.parseInt(matcher.group(1));
            return quantity > 0 ? Optional.of(quantity) : Optional.empty();
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    public static String formatPriceLine(PriceSpec spec) {
        if (spec == null || (spec.buyPrice() == null && spec.sellPrice() == null)) {
            return "";
        }
        if (spec.buyPrice() != null && spec.sellPrice() != null) {
            return formatToken('B', spec.buyPrice()) + " : " + formatToken('S', spec.sellPrice());
        }
        if (spec.buyPrice() != null) {
            return formatToken('B', spec.buyPrice());
        }
        return formatToken('S', spec.sellPrice());
    }

    public static String formatQuantityLine(int quantity) {
        return "Q:" + Math.max(1, quantity);
    }

    public static boolean looksLikeShopAttempt(String line1, String line4) {
        String first = normalize(line1).toUpperCase(Locale.ROOT);
        String fourth = normalize(line4).toUpperCase(Locale.ROOT);
        return first.startsWith("B") || first.startsWith("S") || fourth.startsWith("Q:");
    }

    private static String formatToken(char side, long value) {
        if (value >= 1000L && value % 1000L == 0L) {
            return side + Long.toString(value / 1000L) + 'K';
        }
        return side + Long.toString(value);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    public record PriceSpec(Long buyPrice, Long sellPrice) {
    }
}
