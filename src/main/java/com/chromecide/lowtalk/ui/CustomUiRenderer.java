package com.chromecide.lowtalk.ui;

import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * Draws a model onto the custom-UI documents the client has today: a tree of named controls addressed
 * by selector, mutated one property at a time.
 *
 * <p>All the knowledge of that style of drawing lives here — row indexes, hiding the unused controls of
 * a pool, turning text into a message at the last moment, scrolling a container. None of it is in the
 * pages, and none of it needs to survive into whatever draws these models next.
 */
public final class CustomUiRenderer implements UiRenderer {

    /** How a drawing that happens outside {@code build} reaches the player. */
    @FunctionalInterface
    public interface Sink {
        void send(@Nonnull UICommandBuilder commands, @Nonnull UIEventBuilder events);
    }

    @Nonnull
    private final Sink sink;
    /** What each container was last drawn with, so a list that only grew can be appended to. */
    @Nonnull
    private final java.util.Map<String, List<UiItem>> drawn = new java.util.HashMap<>();
    @Nullable
    private UICommandBuilder buildCommands;
    @Nullable
    private UIEventBuilder buildEvents;

    public CustomUiRenderer(@Nonnull Sink sink) {
        this.sink = sink;
    }

    /**
     * Point the next {@link #build} at the builders the page was handed. The page lifecycle supplies
     * them rather than letting us make our own, so a full draw has to be told where to go.
     */
    public void into(@Nonnull UICommandBuilder commands, @Nonnull UIEventBuilder events) {
        this.buildCommands = commands;
        this.buildEvents = events;
    }

    @Override
    public void build(@Nonnull PageLayout layout, @Nonnull UiModel model) {
        UICommandBuilder cmd = buildCommands;
        UIEventBuilder evt = buildEvents;
        if (cmd == null || evt == null) {
            throw new IllegalStateException("build() without into(): the page must pass on the builders it was given");
        }
        cmd.append(layout.document());
        drawn.clear();
        for (PageLayout.Command command : layout.commands()) {
            evt.addEventBinding(eventType(command.event()), command.selector(), eventData(command), command.locksInput());
        }
        // A rebuilt page starts empty on the client, so the first draw is the whole model, not a difference.
        model.markAllChanged();
        draw(layout, model, cmd, evt, true);
        model.accept();
        this.buildCommands = null;
        this.buildEvents = null;
    }

    @Override
    public void update(@Nonnull PageLayout layout, @Nonnull UiModel model) {
        if (!model.hasChanges()) return;
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder evt = new UIEventBuilder();
        draw(layout, model, cmd, evt, false);
        model.accept();
        sink.send(cmd, evt);
    }

    private void draw(@Nonnull PageLayout layout, @Nonnull UiModel model, @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder evt, boolean building) {
        for (String name : model.changedProperties()) {
            if (!building && layout.seedOnly().contains(name)) continue;
            PageLayout.Target target = layout.properties().get(name);
            if (target != null) {
                set(cmd, target.selector() + "." + target.property(), model.get(name));
                continue;
            }
            PageLayout.Target box = layout.boxes().get(name);
            if (box != null && model.get(name) instanceof UiBox value) {
                cmd.setObject(box.selector() + "." + box.property(), anchor(value));
            }
        }
        for (String name : model.changedLists()) {
            List<UiItem> items = model.items(name);
            PageLayout.Container container = layout.containers().get(name);
            if (container != null) {
                drawContainer(cmd, evt, container, items);
                continue;
            }
            PageLayout.Pool pool = layout.pools().get(name);
            if (pool != null) drawPool(cmd, pool, items);
        }
        for (UiModel.Cell cell : model.changedCells()) {
            // A list that was replaced outright has already been drawn whole; anything else is a single field.
            if (model.changedLists().contains(cell.list())) continue;
            PageLayout.Container container = layout.containers().get(cell.list());
            if (container == null) continue;
            List<UiItem> items = model.items(cell.list());
            if (cell.index() < 0 || cell.index() >= items.size()) continue;
            UiItem item = items.get(cell.index());
            PageLayout.Template template = container.templates().get(item.template());
            if (template == null) continue;
            PageLayout.Target target = template.fields().get(cell.field());
            if (target == null) continue;
            set(cmd, container.selector() + "[" + cell.index() + "]" + joinSelector(target.selector())
                    + "." + target.property(), item.get(cell.field()));
        }
    }

    private void drawContainer(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder evt,
                               @Nonnull PageLayout.Container container, @Nonnull List<UiItem> items) {
        // The documents cannot repeat a template against a list, so in general a changed list is redrawn
        // whole. A transcript only ever grows, though, and redrawing one every line would re-send the
        // whole conversation on every line; so when the new list starts with the old one, add the rest.
        List<UiItem> last = drawn.getOrDefault(container.selector(), List.of());
        int from = 0;
        if (items.size() > last.size() && items.subList(0, last.size()).equals(last)) {
            from = last.size();
        } else {
            cmd.clear(container.selector());
        }
        drawn.put(container.selector(), List.copyOf(items));
        for (int i = from; i < items.size(); i++) {
            UiItem item = items.get(i);
            PageLayout.Template template = container.templates().get(item.template());
            if (template == null) {
                throw new IllegalStateException("no template '" + item.template() + "' in container "
                        + container.selector());
            }
            cmd.append(container.selector(), template.document());
            String row = container.selector() + "[" + i + "]";
            for (Map.Entry<String, Object> field : item.fields().entrySet()) {
                PageLayout.Target target = template.fields().get(field.getKey());
                if (target == null) continue;
                set(cmd, row + joinSelector(target.selector()) + "." + target.property(), field.getValue());
            }
            // A row's buttons are bound as the row is drawn, carrying that row's own values back, so the
            // handler never has to work out which index was clicked.
            for (PageLayout.RowCommand command : template.commands()) {
                String control = row + joinSelector(command.selector());
                EventData data = new EventData().append(UiPage.COMMAND_PARAM, command.id());
                if (command.rowIndexParam() != null) data = data.append(command.rowIndexParam(), String.valueOf(i));
                for (Map.Entry<String, String> constant : command.constants().entrySet()) {
                    data = data.append(constant.getKey(), constant.getValue());
                }
                for (Map.Entry<String, String> param : command.paramsFromFields().entrySet()) {
                    Object value = item.get(param.getValue());
                    data = data.append(param.getKey(), value instanceof UiText text && text.literal() != null
                            ? text.literal() : String.valueOf(value));
                }
                for (Map.Entry<String, String> read : command.reads().entrySet()) {
                    // A read starting with '.' is this control's own value; anything else names a sibling.
                    String target = read.getValue();
                    data = data.append("@" + read.getKey(),
                            target.startsWith(".") ? control + target : row + " " + target);
                }
                evt.addEventBinding(eventType(command.event()), control, data, command.locksInput());
            }
        }
        if (container.revealProperty() != null && !items.isEmpty()) {
            cmd.set(container.selector() + "." + container.revealProperty(), items.size() - 1);
        }
    }

    private void drawPool(@Nonnull UICommandBuilder cmd, @Nonnull PageLayout.Pool pool,
                          @Nonnull List<UiItem> items) {
        for (int i = 0; i < pool.size(); i++) {
            String slot = pool.prefix() + i;
            if (i >= items.size()) {
                cmd.set(slot + "." + pool.visibleProperty(), false);
                continue;
            }
            UiItem item = items.get(i);
            for (Map.Entry<String, Object> field : item.fields().entrySet()) {
                PageLayout.Target target = pool.fields().get(field.getKey());
                if (target == null) continue;
                set(cmd, slot + joinSelector(target.selector()) + "." + target.property(), field.getValue());
            }
            cmd.set(slot + "." + pool.visibleProperty(), true);
        }
    }

    /** An empty selector means the row control itself rather than something inside it. */
    @Nonnull
    private static String joinSelector(@Nonnull String selector) {
        return selector.isEmpty() ? "" : " " + selector;
    }

    private static void set(@Nonnull UICommandBuilder cmd, @Nonnull String selector, @Nullable Object value) {
        switch (value) {
            case null -> cmd.setNull(selector);
            case UiText text -> {
                if (text.isTranslation() || text.color() != null) {
                    cmd.set(selector, message(text));
                } else {
                    cmd.set(selector, text.literal() == null ? "" : text.literal());
                }
            }
            case Boolean b -> cmd.set(selector, b.booleanValue());
            case Integer n -> cmd.set(selector, n.intValue());
            case Double n -> cmd.set(selector, n.doubleValue());
            case Float n -> cmd.set(selector, n.floatValue());
            case List<?> list -> cmd.set(selector, entries(list));
            default -> throw new IllegalArgumentException("cannot draw " + value.getClass().getName()
                    + " at " + selector);
        }
    }

    /** Dropdown entries, built here for the same reason messages are: the server's type cannot be compared. */
    @Nonnull
    private static List<DropdownEntryInfo> entries(@Nonnull List<?> options) {
        List<DropdownEntryInfo> out = new java.util.ArrayList<>(options.size());
        for (Object option : options) {
            if (option instanceof UiOption o) {
                out.add(o.tooltip() == null
                        ? new DropdownEntryInfo(label(o.label()), o.value())
                        : new DropdownEntryInfo(label(o.label()), o.value(), label(o.tooltip())));
            } else if (option instanceof UiText text) {
                out.add(new DropdownEntryInfo(label(text), text.literal() == null ? "" : text.literal()));
            }
        }
        return out;
    }

    @Nonnull
    private static LocalizableString label(@Nonnull UiText text) {
        if (!text.isTranslation()) return LocalizableString.fromString(text.literal() == null ? "" : text.literal());
        String key = text.key();
        return text.params().isEmpty()
                ? LocalizableString.fromMessageId(key == null ? "" : key)
                : LocalizableString.fromMessageId(key == null ? "" : key, text.params());
    }

    /** Built here, at the last moment, because a message cannot be compared and so cannot be held. */
    @Nonnull
    private static Message message(@Nonnull UiText text) {
        String key = text.key();
        Message message = key == null
                ? Message.raw(text.literal() == null ? "" : text.literal())
                : Message.translation(key);
        for (Map.Entry<String, String> param : text.params().entrySet()) {
            message = message.param(param.getKey(), param.getValue());
        }
        if (text.color() != null) message = message.color(text.color());
        return message;
    }

    @Nonnull
    private static Anchor anchor(@Nonnull UiBox box) {
        Anchor anchor = new Anchor();
        if (box.height() != null) anchor.setHeight(Value.of(box.height()));
        if (box.left() != null) anchor.setLeft(Value.of(box.left()));
        if (box.right() != null) anchor.setRight(Value.of(box.right()));
        if (box.top() != null) anchor.setTop(Value.of(box.top()));
        if (box.bottom() != null) anchor.setBottom(Value.of(box.bottom()));
        return anchor;
    }

    @Nonnull
    private static EventData eventData(@Nonnull PageLayout.Command command) {
        EventData data = new EventData().append(UiPage.COMMAND_PARAM, command.id());
        for (Map.Entry<String, String> constant : command.constants().entrySet()) {
            data = data.append(constant.getKey(), constant.getValue());
        }
        for (Map.Entry<String, String> read : command.reads().entrySet()) {
            // The documents read a control's current value by naming it with a leading '@'.
            data = data.append("@" + read.getKey(), read.getValue());
        }
        return data;
    }

    @Nonnull
    private static CustomUIEventBindingType eventType(@Nonnull UiEvent event) {
        return switch (event) {
            case ACTIVATE -> CustomUIEventBindingType.Activating;
            case VALUE_CHANGED -> CustomUIEventBindingType.ValueChanged;
            case KEY_DOWN -> CustomUIEventBindingType.KeyDown;
        };
    }
}
