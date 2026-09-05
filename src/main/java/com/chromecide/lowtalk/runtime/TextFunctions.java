package com.chromecide.lowtalk.runtime;

/** Pure text helpers exposed to dialogues as functions: ordinal(), plural(). */
public final class TextFunctions {

    private TextFunctions() {}

    /** 1 -> "1st", 2 -> "2nd", 3 -> "3rd", 4 -> "4th", 11 -> "11th", 21 -> "21st", 112 -> "112th". */
    public static String ordinal(double value) {
        long n = Math.round(value);
        long abs = Math.abs(n);
        long lastTwo = abs % 100;
        String suffix;
        if (lastTwo >= 11 && lastTwo <= 13) {
            suffix = "th";
        } else {
            suffix = switch ((int) (abs % 10)) {
                case 1 -> "st";
                case 2 -> "nd";
                case 3 -> "rd";
                default -> "th";
            };
        }
        return n + suffix;
    }

    /** plural(1, "loaf", "loaves") -> "loaf"; plural(2, "loaf", "loaves") -> "loaves"; plural(2, "coin") -> "coins". */
    public static String plural(double value, String singular, String pluralForm) {
        boolean one = Math.abs(value - 1.0) < 1e-9;
        if (one) return singular;
        if (pluralForm != null && !pluralForm.isEmpty()) return pluralForm;
        return singular + "s";
    }
}
