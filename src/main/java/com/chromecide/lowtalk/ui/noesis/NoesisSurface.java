package com.chromecide.lowtalk.ui.noesis;

import com.chromecide.lowtalk.ui.UiItem;
import com.chromecide.lowtalk.ui.UiModel;
import com.chromecide.lowtalk.ui.UiOption;
import com.chromecide.lowtalk.ui.UiText;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.ServersidePageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.UIDataValue;
import com.hypixel.hytale.protocol.packets.interface_.UIObjectDataValue;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.modules.ui.CommandDefinition;
import com.hypixel.hytale.server.core.modules.ui.Ui;
import com.hypixel.hytale.server.core.modules.ui.UICommandHandler;
import com.hypixel.hytale.server.core.modules.ui.UIPage;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws a {@link UiModel} as a server-side Noesis page.
 *
 * <p>The second renderer the pages were written for. It is not a {@code UiRenderer}: a Noesis surface is
 * owned by the server's own UI module, which asks it for a data context and calls it when the page opens
 * and closes, so the page lifecycle comes from above rather than from us. What it shares with the custom
 * UI renderer is everything that matters — it is handed the same model and the same differences, and the
 * page that filled them does not know which of the two is drawing.
 *
 * <p>Spike. It compiles against the real API and the mapping is worked out, but nothing has watched it
 * render: the client half is behind a feature flag Hypixel has not switched on for us.
 */
public class NoesisSurface extends UIPage {

    @Nonnull
    private final NoesisLayout layout;
    @Nonnull
    private final UiModel model;
    @Nonnull
    private final Map<String, CommandDefinition> commands = new LinkedHashMap<>();

    public NoesisSurface(@Nonnull String id, @Nonnull NoesisLayout layout, @Nonnull UiModel model,
                         @Nonnull ServersidePageLifetime lifetime, @Nonnull UICommandHandler handler) {
        super(id, layout.xaml(), lifetime);
        this.layout = layout;
        this.model = model;
        // Every command the layout declares is registered up front and handed to the same place, so the
        // page keeps answering "a command called X happened" exactly as it does today.
        for (String commandId : layout.commands()) {
            commands.put(commandId, registerCommand(commandId, handler));
        }
    }

    /** The whole model, as the object the page binds to. */
    @Override
    public UIObjectDataValue createDataContext(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        Ui.ObjBuilder root = Ui.obj(layout.rootType());
        for (Map.Entry<String, Object> property : model.properties().entrySet()) {
            put(root, layout.pathOf(property.getKey()), property.getValue());
        }
        for (Map.Entry<String, List<UiItem>> list : model.lists().entrySet()) {
            root.put(layout.pathOf(list.getKey()), listValue(list.getKey(), list.getValue()));
        }
        for (Map.Entry<String, CommandDefinition> command : commands.entrySet()) {
            root.put(command.getKey(), command.getValue());
        }
        model.accept();
        return root.build();
    }

    /** Send whatever the page changed since the last time. */
    public void pushChanges() {
        for (String name : model.changedProperties()) {
            setProperty(layout.pathOf(name), value(model.get(name)));
        }
        for (String name : model.changedLists()) {
            setProperty(layout.pathOf(name), listValue(name, model.items(name)));
        }
        for (UiModel.Cell cell : model.changedCells()) {
            if (model.changedLists().contains(cell.list())) continue;
            UiItem item = model.items(cell.list()).get(cell.index());
            setProperty(layout.pathOf(cell.list()) + "[" + cell.index() + "]." + capitalise(cell.field()),
                    value(item.get(cell.field())));
        }
        model.accept();
    }

    @Nonnull
    private UIDataValue listValue(@Nonnull String name, @Nonnull List<UiItem> items) {
        UIDataValue[] values = new UIDataValue[items.size()];
        for (int i = 0; i < items.size(); i++) {
            UiItem item = items.get(i);
            Ui.ObjBuilder row = Ui.obj(layout.typeOfItem(item.template()));
            for (Map.Entry<String, Object> field : item.fields().entrySet()) {
                put(row, capitalise(field.getKey()), field.getValue());
            }
            values[i] = row.build();
        }
        // A list of rows that are not all the same kind is expressed by giving the list a base type and
        // each row its own: the XAML then picks a template per row. That is what our template names are.
        return Ui.list(layout.rootType() + ".Item", values);
    }

    private void put(@Nonnull Ui.ObjBuilder builder, @Nonnull String name, Object v) {
        builder.put(name, value(v));
    }

    /** One model value as the client's typed equivalent. */
    @Nonnull
    private static UIDataValue value(Object v) {
        return switch (v) {
            case null -> Ui.stringVal("");
            case UiText text -> text.isTranslation() || text.color() != null
                    // Rich text, which the custom-UI documents could never carry from the server.
                    ? Ui.formattedMessageVal(message(text).getFormattedMessage())
                    : Ui.stringVal(text.literal() == null ? "" : text.literal());
            case Boolean b -> Ui.boolVal(b);
            case Integer n -> Ui.intVal(n);
            case Double n -> Ui.doubleVal(n);
            case Float n -> Ui.floatVal(n);
            case List<?> list -> options(list);
            default -> throw new IllegalArgumentException("cannot send " + v.getClass().getName());
        };
    }

    @Nonnull
    private static UIDataValue options(@Nonnull List<?> list) {
        UIDataValue[] values = new UIDataValue[list.size()];
        for (int i = 0; i < list.size(); i++) {
            Object o = list.get(i);
            if (o instanceof UiOption option) {
                values[i] = Ui.obj("LowTalk.Option")
                        .put("Value", option.value())
                        .put("Label", value(option.label()))
                        .build();
            } else {
                values[i] = value(o);
            }
        }
        return Ui.list("LowTalk.Option", values);
    }

    @Nonnull
    private static Message message(@Nonnull UiText text) {
        String key = text.key();
        Message m = key == null ? Message.raw(text.literal() == null ? "" : text.literal()) : Message.translation(key);
        for (Map.Entry<String, String> p : text.params().entrySet()) m = m.param(p.getKey(), p.getValue());
        if (text.color() != null) m = m.color(text.color());
        return m;
    }

    @Nonnull
    private static String capitalise(@Nonnull String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
