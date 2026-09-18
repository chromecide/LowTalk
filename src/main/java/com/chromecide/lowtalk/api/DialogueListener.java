package com.chromecide.lowtalk.api;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Notifications about dialogues as they run. Every method has an empty default; override what you need. */
public interface DialogueListener {

    /** A dialogue window opened for a player. */
    default void onStart(@Nonnull DialogueContext ctx) {}

    /** The player entered a node, including the start node and every jump. */
    default void onNode(@Nonnull DialogueContext ctx, @Nonnull String node) {}

    /** The player picked an option. {@code text} is the rendered button text. */
    default void onChoice(@Nonnull DialogueContext ctx, @Nonnull String text) {}

    /**
     * A command ran, or tried to. {@code error} is null when it succeeded, and otherwise says what went wrong.
     *
     * <p>Commands fail one at a time without stopping the conversation, so a dialogue can carry on looking
     * healthy while an effect never happens. Nothing outside the dialogue could see that before this.
     *
     * @param command the command name, without the angle brackets
     * @param args    its arguments as written, already rendered
     * @param error   null when it ran, else the reason it did not
     */
    default void onCommand(@Nonnull DialogueContext ctx, @Nonnull String command, @Nonnull java.util.List<String> args,
                           @Nullable String error) {}

    /**
     * The dialogue stopped because something went wrong, rather than because it was finished with.
     *
     * <p>{@link #onEnd} still follows, so a listener that only cares that the conversation is over needs no
     * change; this says the ending was a failure, which {@code onEnd} alone cannot distinguish from the player
     * walking away.
     */
    default void onFailed(@Nonnull DialogueContext ctx, @Nonnull String message) {}

    /** The dialogue ended, by any route: end, leave, escape, shop hand-off, reload, disconnect, failure. */
    default void onEnd(@Nonnull DialogueContext ctx) {}
}
