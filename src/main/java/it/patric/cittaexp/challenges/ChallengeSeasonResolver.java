package it.patric.cittaexp.challenges;

import java.time.LocalDate;
import java.util.Locale;

public final class ChallengeSeasonResolver {

    private ChallengeSeasonResolver() {
    }

    public static String seasonKey(LocalDate date) {
        if (date == null) {
            date = LocalDate.now();
        }
        Season season = seasonFor(date);
        return date.getYear() + "-" + season.name();
    }

    public static String seasonLabel(LocalDate date) {
        if (date == null) {
            date = LocalDate.now();
        }
        return switch (seasonFor(date)) {
            case SPRING -> "Primavera";
            case SUMMER -> "Estate";
            case AUTUMN -> "Autunno";
            case WINTER -> "Inverno";
        };
    }

    public static Season seasonFor(LocalDate date) {
        int month = date.getMonthValue();
        if (month >= 3 && month <= 5) {
            return Season.SPRING;
        }
        if (month >= 6 && month <= 8) {
            return Season.SUMMER;
        }
        if (month >= 9 && month <= 11) {
            return Season.AUTUMN;
        }
        return Season.WINTER;
    }

    public static boolean sameSeason(LocalDate left, LocalDate right) {
        if (left == null || right == null) {
            return false;
        }
        return seasonKey(left).toLowerCase(Locale.ROOT).equals(seasonKey(right).toLowerCase(Locale.ROOT));
    }

    public enum Season {
        SPRING,
        SUMMER,
        AUTUMN,
        WINTER
    }
}
