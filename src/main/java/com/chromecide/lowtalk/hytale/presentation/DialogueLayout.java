package com.chromecide.lowtalk.hytale.presentation;

import javax.annotation.Nullable;

/** Where the conversation is drawn on the player's screen. */
public enum DialogueLayout {
    /** A centred window over a dimmed screen, the original look. */
    WINDOW("window", "Pages/LowTalk/DialoguePage.ui"),
    /** A bar along the bottom of the screen; the world and the NPC stay visible. */
    BOTTOM("bottom", "Pages/LowTalk/DialogueBottom.ui"),
    /** The same bar along the top of the screen. */
    TOP("top", "Pages/LowTalk/DialogueTop.ui");

    public static final DialogueLayout DEFAULT = BOTTOM;

    private final String key;
    private final String uiFile;

    DialogueLayout(String key, String uiFile) {
        this.key = key;
        this.uiFile = uiFile;
    }

    /** The word used in {@code layout:} directives, JSON and the config. */
    public String key() { return key; }

    /** The layout file the page appends, relative to Common/UI/Custom/. */
    public String uiFile() { return uiFile; }

    /** True for the bar layouts, which number their options and take number keys. */
    public boolean isBar() { return this != WINDOW; }

    /** Parse a directive or config value; null when absent, blank or unknown. Case-insensitive. */
    @Nullable
    public static DialogueLayout parse(@Nullable String value) {
        if (value == null) return null;
        String v = value.trim().toLowerCase();
        for (DialogueLayout l : values()) if (l.key.equals(v)) return l;
        return null;
    }

    public static boolean isValid(@Nullable String value) {
        return parse(value) != null;
    }

    public static String keys() {
        StringBuilder sb = new StringBuilder();
        for (DialogueLayout l : values()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(l.key);
        }
        return sb.toString();
    }
}
