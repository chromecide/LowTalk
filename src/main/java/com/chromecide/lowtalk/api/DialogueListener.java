package com.chromecide.lowtalk.api;

import javax.annotation.Nonnull;

/** Notifications about dialogues as they run. Every method has an empty default; override what you need. */
public interface DialogueListener {

    /** A dialogue window opened for a player. */
    default void onStart(@Nonnull DialogueContext ctx) {}

    /** The player entered a node, including the start node and every jump. */
    default void onNode(@Nonnull DialogueContext ctx, @Nonnull String node) {}

    /** The player picked an option. {@code text} is the rendered button text. */
    default void onChoice(@Nonnull DialogueContext ctx, @Nonnull String text) {}

    /** The dialogue ended, by any route: end, leave, escape, shop hand-off, reload, disconnect. */
    default void onEnd(@Nonnull DialogueContext ctx) {}
}
