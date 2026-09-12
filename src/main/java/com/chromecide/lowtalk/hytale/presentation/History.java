package com.chromecide.lowtalk.hytale.presentation;

import javax.annotation.Nullable;

/** How much of the conversation stays on screen. */
public enum History {
    /** Every line so far, the player's answers included, in a scrolling transcript. */
    FULL("full"),
    /** Only what the NPC is saying right now; earlier lines and the player's answers are not shown. */
    LATEST("latest");

    public static final History DEFAULT = FULL;

    private final String key;

    History(String key) {
        this.key = key;
    }

    public String key() { return key; }

    @Nullable
    public static History parse(@Nullable String value) {
        if (value == null) return null;
        String v = value.trim().toLowerCase();
        for (History h : values()) if (h.key.equals(v)) return h;
        return null;
    }

    public static boolean isValid(@Nullable String value) {
        return parse(value) != null;
    }

    public static String keys() {
        return FULL.key + ", " + LATEST.key;
    }
}
