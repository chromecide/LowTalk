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
 * <p>The styled overload is preferred where it exists, so a pre.3 server is driven by the API it means to keep.
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
    /** The enum constants for the styled call, or null for the boolean one. */
    private static final Object MAJOR;
    private static final Object DEFAULT;

    static {
        Flavour flavour = Flavour.MISSING;
        Method method = null;
        Object major = null;
        Object plain = null;
        try {
            Class<?> util = Class.forName(UTIL);
            try {
                Class<?> style = Class.forName(STYLE);
                method = util.getMethod("showEventTitleToPlayer", PlayerRef.class, Message.class, Message.class,
                        style, String.class, float.class, float.class, float.class);
                major = constant(style, "Major");
                plain = constant(style, "Default");
                flavour = major != null && plain != null ? Flavour.STYLED : Flavour.MISSING;
            } catch (ClassNotFoundException | NoSuchMethodException noStyle) {
                method = util.getMethod("showEventTitleToPlayer", PlayerRef.class, Message.class, Message.class,
                        boolean.class, String.class, float.class, float.class, float.class);
                flavour = Flavour.BOOLEAN;
            }
        } catch (ClassNotFoundException | NoSuchMethodException | RuntimeException e) {
            flavour = Flavour.MISSING;
            method = null;
        }
        FLAVOUR = flavour;
        METHOD = flavour == Flavour.MISSING ? null : method;
        MAJOR = major;
        DEFAULT = plain;
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

    /**
     * Show a title to one player.
     *
     * @param major true for the larger treatment the game uses to announce something of consequence
     * @param zone  the zone name the client shows with it, or null for none
     * @throws IllegalStateException when this server has neither call, so a caller can say so rather than
     *                               appearing to have worked
     */
    public static void showToPlayer(@Nonnull PlayerRef player, @Nonnull Message primary, @Nonnull Message secondary,
                                    boolean major, @Nullable String zone,
                                    float seconds, float fadeIn, float fadeOut) {
        if (METHOD == null) {
            throw new IllegalStateException("this server has no EventTitleUtil.showEventTitleToPlayer to call");
        }
        Object style = FLAVOUR == Flavour.STYLED ? (major ? MAJOR : DEFAULT) : Boolean.valueOf(major);
        try {
            METHOD.invoke(null, player, primary, secondary, style, zone, seconds, fadeIn, fadeOut);
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

    @Nullable
    private static Object constant(Class<?> style, String name) {
        for (Object c : style.getEnumConstants()) {
            if (c instanceof Enum<?> e && e.name().equals(name)) return c;
        }
        return null;
    }
}
