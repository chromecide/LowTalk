package com.chromecide.lowtalk.ui;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a page is showing, as data: named properties and named lists of rows.
 *
 * <p>Pages write to this and never name a control. A renderer reads it against a {@link PageLayout} and
 * draws it however its backend draws things. That split is the whole point: the model and the page logic
 * are the part worth keeping when the drawing changes.
 *
 * <p>The model remembers what changed since the last draw, so a step sends only the difference. For that
 * to work every value has to compare properly, which is why text lives in {@link UiText} rather than in a
 * server {@code Message}.
 */
public final class UiModel {

    private final Map<String, Object> properties = new LinkedHashMap<>();
    private final Map<String, List<UiItem>> lists = new LinkedHashMap<>();
    private final Set<String> changedProperties = new LinkedHashSet<>();
    private final Set<String> changedLists = new LinkedHashSet<>();
    private final Set<Cell> changedCells = new LinkedHashSet<>();

    /** One field of one row, for the times a page wants to change that alone. */
    public record Cell(@Nonnull String list, int index, @Nonnull String field) {}

    /**
     * The value types a property or a row field may hold. All of them compare by value, which is what
     * the difference tracking depends on.
     */
    static void checkValue(@Nonnull String name, @Nullable Object value) {
        if (value == null
                || value instanceof UiText
                || value instanceof Boolean
                || value instanceof Integer
                || value instanceof Double
                || value instanceof Float
                || value instanceof UiBox) {
            return;
        }
        if (value instanceof List<?> list) {
            for (Object element : list) {
                if (element instanceof UiOption || element instanceof UiText) continue;
                throw new IllegalArgumentException("ui list '" + name + "' holds a "
                        + (element == null ? "null" : element.getClass().getName())
                        + "; a dropdown's entries are UiOption. The server's own entry type must not be held here:"
                        + " its label has no equals, so an unchanged list would be re-sent on every draw.");
            }
            return;
        }
        throw new IllegalArgumentException("ui value '" + name + "' is a " + value.getClass().getName()
                + "; allowed are UiText, Boolean, Integer, Double, Float, UiBox and List. Plain Strings are not"
                + " allowed so that literal and translated text stay distinguishable — wrap it in UiText.of().");
    }

    /** Set a property. Does nothing if the value is already what is being set. */
    @Nonnull
    public UiModel set(@Nonnull String name, @Nullable Object value) {
        checkValue(name, value);
        if (properties.containsKey(name) && java.util.Objects.equals(properties.get(name), value)) return this;
        properties.put(name, value);
        changedProperties.add(name);
        return this;
    }

    /** Set a text property from a plain string. */
    @Nonnull
    public UiModel set(@Nonnull String name, @Nullable String value) {
        return set(name, (Object) UiText.of(value));
    }

    /** Set a text property from a translation key. */
    @Nonnull
    public UiModel setKey(@Nonnull String name, @Nonnull String translationKey) {
        return set(name, (Object) UiText.key(translationKey));
    }

    @Nullable
    public Object get(@Nonnull String name) {
        return properties.get(name);
    }

    /** Replace a whole list. Does nothing if the rows are already exactly these. */
    @Nonnull
    public UiModel setItems(@Nonnull String name, @Nonnull List<UiItem> items) {
        List<UiItem> copy = List.copyOf(items);
        if (copy.equals(lists.get(name))) return this;
        lists.put(name, copy);
        changedLists.add(name);
        return this;
    }

    /**
     * Change one field of one row without redrawing the list.
     *
     * <p>Replacing a whole list is the normal way to change it, but some rows carry a control whose contents
     * are refreshed as the creator works — a dropdown narrowing to what they have typed. Redrawing every row
     * for that would send the whole list on each keystroke, so this marks the single field instead.
     */
    @Nonnull
    public UiModel setItemField(@Nonnull String name, int index, @Nonnull String field, @Nullable Object value) {
        checkValue(field, value);
        List<UiItem> items = lists.get(name);
        if (items == null || index < 0 || index >= items.size()) return this;
        UiItem updated = items.get(index).with(field, value);
        if (updated.equals(items.get(index))) return this;
        List<UiItem> copy = new java.util.ArrayList<>(items);
        copy.set(index, updated);
        lists.put(name, List.copyOf(copy));
        // Deliberately not marking the whole list: only this field travels.
        changedCells.add(new Cell(name, index, field));
        return this;
    }

    /** Row fields written since the last {@link #accept()}, for lists that were not replaced outright. */
    @Nonnull
    public Set<Cell> changedCells() {
        return Collections.unmodifiableSet(changedCells);
    }

    @Nonnull
    public List<UiItem> items(@Nonnull String name) {
        return lists.getOrDefault(name, List.of());
    }

    @Nonnull
    public Map<String, Object> properties() {
        return Collections.unmodifiableMap(properties);
    }

    @Nonnull
    public Map<String, List<UiItem>> lists() {
        return Collections.unmodifiableMap(lists);
    }

    /** Property names written since the last {@link #accept()}. */
    @Nonnull
    public Set<String> changedProperties() {
        return Collections.unmodifiableSet(changedProperties);
    }

    /** List names written since the last {@link #accept()}. */
    @Nonnull
    public Set<String> changedLists() {
        return Collections.unmodifiableSet(changedLists);
    }

    public boolean hasChanges() {
        return !changedProperties.isEmpty() || !changedLists.isEmpty() || !changedCells.isEmpty();
    }

    /** Called by a renderer once it has drawn the current difference. */
    public void accept() {
        changedProperties.clear();
        changedLists.clear();
        changedCells.clear();
    }

    /**
     * Send this property again on the next draw even though the model's value has not moved.
     *
     * <p>For a control the player can change without telling the server — a dropdown with nothing bound to
     * its change event — the model and the screen drift apart, and a page that wants to put such a control
     * back to a known value has to say so, because the difference alone cannot see it. A backend with
     * two-way binding keeps the model in step and would not need this.
     */
    @Nonnull
    public UiModel resend(@Nonnull String name) {
        if (properties.containsKey(name)) changedProperties.add(name);
        return this;
    }

    /**
     * Treat everything as changed. A rebuilt page starts empty on the client, so the next draw has to
     * send the whole model rather than the difference.
     */
    public void markAllChanged() {
        changedProperties.addAll(properties.keySet());
        changedLists.addAll(lists.keySet());
    }
}
