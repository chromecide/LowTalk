package com.chromecide.lowtalk.hytale.compat;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;

/**
 * Showing a title across the screen, on both Hytale lines.
 *
 * <p>0.7.0-pre.3 replaced the {@code boolean isMajor} argument of
 * {@code EventTitleUtil.showEventTitleToPlayer} with an {@code EventTitleStyle}, and marked the boolean overload
 * for removal. The release line has only the boolean, and does not have the enum at all: naming
 * {@code EventTitleStyle} in our source would not compile against 0.6.7.
 *
 * <p>So the choice is made at runtime, once, by reflection. That is the whole reason this class exists: one method,
 * one argument, resolved at class-load and cached. A title is a thing a conversation does now and then, so the cost
 * of a reflective call never shows up, and keeping it here means the rest of the mod calls one ordinary method and
 * one source tree still builds for both lines.
 *
 * <p>The styled overload is preferred where it exists, so a pre.3 server is driven by the API it means to keep,
 * and the styles a creator may name are the game's own enum constants read at runtime rather than a list of our
 * own: when Hytale adds a style, {@code <<title>>} accepts it with no change here.
 */
public final class EventTitles {

    private static final String UTIL = "com.hypixel.hytale.server.core.util.EventTitleUtil";
    private static final String STYLE = "com.hypixel.hytale.protocol.packets.interface_.EventTitleStyle";

    /** Which call was found. Reported so a test can tell, and so the log says which line we are on. */
    public enum Flavour {
        /** 0.7.0-pre.3 and later: the fourth argument is an EventTitleStyle. */
        STYLED,
        /** 0.6.x: the fourth argument is a boolean. */
        BOOLEAN,
        /** Neither was found; titles cannot be shown. */
        MISSING
    }

    private static final Flavour FLAVOUR;
    private static final Method METHOD;
    /** The style constants by lower-case name, empty on the boolean call. */
    private static final java.util.Map<String, Object> STYLES;
    /** The names as the game spells them, in the enum's own order. */
    private static final java.util.List<String> NAMES;

    /** What older dialogues wrote, and what it means in the game's vocabulary. */
    private static final java.util.Map<String, String> ALIASES =
            java.util.Map.of("minor", "default");

    /** The names to offer when the game has no enum to ask: what the boolean could express. */
    private static final java.util.List<String> BOOLEAN_NAMES = java.util.List.of("Default", "Major");

    static {
        Flavour flavour = Flavour.MISSING;
        Method method = null;
        java.util.Map<String, Object> styles = new java.util.LinkedHashMap<>();
        java.util.List<String> names = new java.util.ArrayList<>();
        try {
            Class<?> util = Class.forName(UTIL);
            try {
                Class<?> style = Class.forName(STYLE);
                method = util.getMethod("showEventTitleToPlayer", PlayerRef.class, Message.class, Message.class,
                        style, String.class, float.class, float.class, float.class);
                for (Object c : style.getEnumConstants()) {
                    String name = ((Enum<?>) c).name();
                    styles.put(name.toLowerCase(java.util.Locale.ROOT), c);
                    names.add(name);
                }
                flavour = styles.isEmpty() ? Flavour.MISSING : Flavour.STYLED;
            } catch (ClassNotFoundException | NoSuchMethodException noStyle) {
                method = util.getMethod("showEventTitleToPlayer", PlayerRef.class, Message.class, Message.class,
                        boolean.class, String.class, float.class, float.class, float.class);
                names.addAll(BOOLEAN_NAMES);
                flavour = Flavour.BOOLEAN;
            }
        } catch (ClassNotFoundException | NoSuchMethodException | RuntimeException e) {
            flavour = Flavour.MISSING;
            method = null;
        }
        FLAVOUR = flavour;
        METHOD = flavour == Flavour.MISSING ? null : method;
        STYLES = java.util.Map.copyOf(styles);
        NAMES = java.util.List.copyOf(names);
    }

    private EventTitles() {}

    /** Which of the two calls this server has. */
    public static Flavour flavour() {
        return FLAVOUR;
    }

    /** True when a title can be shown at all. */
    public static boolean available() {
        return FLAVOUR != Flavour.MISSING;
    }

    /** The styles this server can show, spelled as the game spells them. */
    public static java.util.List<String> styleNames() {
        return NAMES;
    }

    /** The style a dialogue gets when it names none. */
    public static String defaultStyle() {
        return NAMES.isEmpty() ? "default" : NAMES.get(0);
    }

    /** True when this server can show a title in that style, by any accepted spelling. */
    public static boolean knows(@Nullable String style) {
        return resolveName(style) != null;
    }

    /**
     * The game's own spelling of a style a creator wrote, or null when it is none of them. Case is ignored, and
     * {@code minor} still reads as {@code Default} so dialogues written before styles existed keep working.
     */
    @Nullable
    public static String resolveName(@Nullable String style) {
        if (style == null || style.isBlank()) return defaultStyle();
        String key = style.trim().toLowerCase(java.util.Locale.ROOT);
        key = ALIASES.getOrDefault(key, key);
        if (FLAVOUR == Flavour.STYLED) {
            Object c = STYLES.get(key);
            return c == null ? null : ((Enum<?>) c).name();
        }
        // Strictly the names this server has. Anything else is not a style, and must not be treated as one: the
        // style and the second line share a slot in <<title>>, so a word waved through here would be taken for
        // the style and the line the creator wrote would vanish.
        for (String n : NAMES) if (n.equalsIgnoreCase(key)) return n;
        return null;
    }

    /**
     * Show a title to one player.
     *
     * @param style the game's style name, any accepted spelling, or null or empty for the default. The styles a
     *              server has are its own: a 0.7 style named to a 0.6 server is refused rather than guessed at,
     *              because guessing would show the wrong title and say nothing about why.
     * @param zone  the zone name the client shows with it, or null for none
     * @throws IllegalStateException    when this server has neither call, so a caller can say so rather than
     *                                  appearing to have worked
     * @throws IllegalArgumentException when the style is none this server knows
     */
    public static void showToPlayer(@Nonnull PlayerRef player, @Nonnull Message primary, @Nonnull Message secondary,
                                    @Nullable String style, @Nullable String zone,
                                    float seconds, float fadeIn, float fadeOut) {
        if (METHOD == null) {
            throw new IllegalStateException("this server has no EventTitleUtil.showEventTitleToPlayer to call");
        }
        String name = resolveName(style);
        if (name == null) {
            throw new IllegalArgumentException("no title style called '" + style + "'; this server has "
                    + String.join(", ", NAMES));
        }
        Object arg = FLAVOUR == Flavour.STYLED
                ? STYLES.get(name.toLowerCase(java.util.Locale.ROOT))
                : Boolean.valueOf(!name.equalsIgnoreCase("default"));
        try {
            METHOD.invoke(null, player, primary, secondary, arg, zone, seconds, fadeIn, fadeOut);
        } catch (java.lang.reflect.InvocationTargetException e) {
            // The game's own failure, not ours: pass it on as it happened.
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) throw re;
            if (cause instanceof Error err) throw err;
            throw new IllegalStateException("showing a title failed", cause);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("EventTitleUtil.showEventTitleToPlayer is not callable", e);
        }
    }
}
