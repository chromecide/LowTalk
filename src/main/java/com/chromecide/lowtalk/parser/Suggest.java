package com.chromecide.lowtalk.parser;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Locale;

/** "Did you mean ...?" helper: the closest known name to a misspelling, if it is close enough to be a typo. */
public final class Suggest {
    private Suggest() {}

    /** The closest candidate, or null when nothing is within a plausible typo distance. */
    @Nullable
    public static String closest(String typed, Collection<String> candidates) {
        if (typed == null || typed.isEmpty()) return null;
        String t = typed.toLowerCase(Locale.ROOT);
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String c : candidates) {
            if (c.equalsIgnoreCase(typed)) return c;
            int d = distance(t, c.toLowerCase(Locale.ROOT));
            if (d < bestDistance || (d == bestDistance && best != null && c.compareTo(best) < 0)) {
                bestDistance = d;
                best = c;
            }
        }
        if (best == null) return null;
        // Allow one edit for short names, two for longer ones, and always accept a prefix match ("obj" -> "objective").
        int allowed = t.length() <= 4 ? 1 : 2;
        if (bestDistance <= allowed) return best;
        for (String c : candidates) {
            if (t.length() >= 3 && c.toLowerCase(Locale.ROOT).startsWith(t)) return c;
        }
        return null;
    }

    /** Appends " (did you mean X?)" when there is a suggestion, else "". */
    public static String hint(String typed, Collection<String> candidates) {
        String s = closest(typed, candidates);
        return s == null || s.equalsIgnoreCase(typed) ? "" : " (did you mean " + s + "?)";
    }

    /** Edit distance where a swapped pair of adjacent letters counts as one edit (optimal string alignment). */
    static int distance(String a, String b) {
        int n = a.length(), m = b.length();
        int[][] d = new int[n + 1][m + 1];
        for (int i = 0; i <= n; i++) d[i][0] = i;
        for (int j = 0; j <= m; j++) d[0][j] = j;
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
                }
            }
        }
        return d[n][m];
    }
}
