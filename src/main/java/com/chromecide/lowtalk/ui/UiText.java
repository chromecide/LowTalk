package com.chromecide.lowtalk.ui;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A piece of display text, held in a form that can be compared.
 *
 * <p>The view model diffs itself so a step only sends what changed, and {@code Message} has no
 * {@code equals} — two messages built from the same key are never equal, so a model holding messages
 * re-sends everything, every time, silently. So the model holds this instead: either a literal string
 * or a translation key with its parameters, both of which compare properly. A renderer turns it into
 * whatever its backend wants at the moment it draws.
 */
public final class UiText {

    @Nonnull
    public static final UiText EMPTY = new UiText("", null, Map.of(), null);

    @Nullable
    private final String literal;
    @Nullable
    private final String key;
    @Nonnull
    private final Map<String, String> params;
    @Nullable
    private final String color;

    private UiText(@Nullable String literal, @Nullable String key, @Nonnull Map<String, String> params,
                   @Nullable String color) {
        this.literal = literal;
        this.key = key;
        this.params = params;
        this.color = color;
    }

    /** Text shown exactly as given. */
    @Nonnull
    public static UiText of(@Nullable String literal) {
        return literal == null || literal.isEmpty() ? EMPTY : new UiText(literal, null, Map.of(), null);
    }

    /** Text looked up in the language files. */
    @Nonnull
    public static UiText key(@Nonnull String key) {
        return new UiText(null, key, Map.of(), null);
    }

    /** A copy of this translation with one more parameter. Literals have no parameters and ignore this. */
    @Nonnull
    public UiText param(@Nonnull String name, @Nullable String value) {
        if (key == null) return this;
        Map<String, String> next = new LinkedHashMap<>(params);
        next.put(name, value == null ? "" : value);
        return new UiText(null, key, Collections.unmodifiableMap(next), color);
    }

    /** The same text drawn in a colour, as "#rrggbb". */
    @Nonnull
    public UiText color(@Nullable String hex) {
        return new UiText(literal, key, params, hex);
    }

    @Nullable
    public String color() {
        return color;
    }

    public boolean isTranslation() {
        return key != null;
    }

    /** The literal text, or null when this is a translation. */
    @Nullable
    public String literal() {
        return literal;
    }

    /** The translation key, or null when this is a literal. */
    @Nullable
    public String key() {
        return key;
    }

    @Nonnull
    public Map<String, String> params() {
        return params;
    }

    public boolean isBlank() {
        return key == null && (literal == null || literal.isBlank());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UiText other)) return false;
        return Objects.equals(literal, other.literal)
                && Objects.equals(key, other.key)
                && Objects.equals(color, other.color)
                && params.equals(other.params);
    }

    @Override
    public int hashCode() {
        return Objects.hash(literal, key, params, color);
    }

    @Override
    public String toString() {
        return key == null ? "UiText[" + literal + "]" : "UiText[" + key + params + "]";
    }
}
