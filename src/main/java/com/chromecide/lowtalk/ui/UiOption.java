package com.chromecide.lowtalk.ui;

import javax.annotation.Nonnull;

/**
 * One choice in a dropdown: the value that comes back, and the label the player reads.
 *
 * <p>A record, so a list of these compares by value and an unchanged dropdown is not re-sent. The
 * server's own entry type cannot be used here for exactly that reason — its label is a
 * {@code LocalizableString}, which is a class with no {@code equals}, so two identical lists of entries
 * never compare equal and every draw would re-send the whole dropdown. The renderer converts to it at
 * the last moment, the same way text becomes a message.
 */
public record UiOption(@Nonnull String value, @Nonnull UiText label, @javax.annotation.Nullable UiText tooltip) {

    @Nonnull
    public static UiOption of(@Nonnull String value, @Nonnull String label) {
        return new UiOption(value, UiText.of(label), null);
    }

    /** The same choice with the longer explanation shown on hover. */
    @Nonnull
    public UiOption explain(@Nonnull String tooltip) {
        return new UiOption(value, label, UiText.of(tooltip));
    }

    /** A choice whose label comes from the language files. */
    @Nonnull
    public static UiOption key(@Nonnull String value, @Nonnull String translationKey) {
        return new UiOption(value, UiText.key(translationKey), null);
    }

    /** A choice that shows its own value. */
    @Nonnull
    public static UiOption same(@Nonnull String value) {
        return new UiOption(value, UiText.of(value), null);
    }
}
