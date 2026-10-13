package com.chromecide.lowtalk.ui;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One row of a list in the view model: which template draws it, and the values of its fields.
 *
 * <p>Immutable, and compares by value, so a list of these can be diffed against the last one that was
 * drawn. Field values follow the same rules as model properties — see {@link UiModel#set}.
 */
public final class UiItem {

    @Nonnull
    private final String template;
    @Nonnull
    private final Map<String, Object> fields;

    private UiItem(@Nonnull String template, @Nonnull Map<String, Object> fields) {
        this.template = template;
        this.fields = fields;
    }

    /** A row drawn by the named template, with no fields set yet. */
    @Nonnull
    public static UiItem of(@Nonnull String template) {
        return new UiItem(template, Map.of());
    }

    /** A copy of this row with one more field set. */
    @Nonnull
    public UiItem with(@Nonnull String field, @Nullable Object value) {
        UiModel.checkValue(field, value);
        Map<String, Object> next = new LinkedHashMap<>(fields);
        next.put(field, value);
        return new UiItem(template, Collections.unmodifiableMap(next));
    }

    /** Convenience for the common case of a text field. */
    @Nonnull
    public UiItem with(@Nonnull String field, @Nullable String value) {
        return with(field, (Object) UiText.of(value));
    }

    @Nonnull
    public String template() {
        return template;
    }

    @Nonnull
    public Map<String, Object> fields() {
        return fields;
    }

    @Nullable
    public Object get(@Nonnull String field) {
        return fields.get(field);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UiItem other)) return false;
        return template.equals(other.template) && fields.equals(other.fields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(template, fields);
    }

    @Override
    public String toString() {
        return "UiItem[" + template + fields + "]";
    }
}
