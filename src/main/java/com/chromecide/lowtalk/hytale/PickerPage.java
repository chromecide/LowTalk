package com.chromecide.lowtalk.hytale;

import com.chromecide.lowtalk.LowTalkPlugin;
import com.chromecide.lowtalk.hytale.json.JsonDialogues;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Choosing one id out of a list too long for a dropdown. A stock server has more than three thousand items and a
 * thousand sound events, and a dropdown is handed its entries once, so the only way to search a list that size is
 * to send the matches again as the creator types. That is what the game's own particle, sound and entity pickers
 * do, and this is the same thing for a command's arguments.
 *
 * <p>It replaces the page that opened it and puts that page back when it closes, so the dialogue being edited, its
 * unsaved draft and the passage on screen all survive the trip.
 */
public class PickerPage extends InteractiveCustomUIPage<PickerPage.Data> {
    private static final String LAYOUT = "Pages/LowTalk/PickerPage.ui";
    private static final String ROW = "Pages/LowTalk/PickerRow.ui";
    /** How many matches to show at once. Enough to scroll, small enough to send on every keystroke. */
    private static final int SHOWN = 60;

    public enum Action { SEARCH, PICK, CLEAR, CANCEL }

    public static class Data {
        public static final BuilderCodec<Data> CODEC = BuilderCodec.builder(Data.class, Data::new)
                .append(new KeyedCodec<>("Action", Codec.STRING, false), (d, s) -> d.action = s, d -> d.action).add()
                .append(new KeyedCodec<>("Row", Codec.STRING, false), (d, s) -> d.row = s, d -> d.row).add()
                .append(new KeyedCodec<>("@Value", Codec.STRING, false), (d, s) -> d.value = s, d -> d.value).add()
                .build();
        private String action;
        private String row;
        private String value;
    }

    private final LowTalkPlugin plugin;
    private final String title;
    private final String dataset;
    private final String current;
    private final Consumer<String> chosen;
    private final CustomUIPage back;
    private String query = "";
    private List<String> shown = List.of();

    private PickerPage(LowTalkPlugin plugin, PlayerRef player, String title, String dataset, String current,
                       Consumer<String> chosen, CustomUIPage back) {
        super(player, CustomPageLifetime.CanDismiss, Data.CODEC);
        this.plugin = plugin;
        this.title = title;
        this.dataset = dataset;
        this.current = current == null ? "" : current;
        this.chosen = chosen;
        this.back = back;
    }

    /**
     * Open a picker over the current page. {@code chosen} is given the id the creator picked, or an empty string if
     * they chose to leave the argument empty; it is not called at all when they cancel. Either way the page that
     * was open before is put back afterwards. World thread.
     */
    public static void open(@Nonnull LowTalkPlugin plugin, @Nonnull PlayerRef player, @Nonnull Ref<EntityStore> playerEntity,
                            @Nonnull Store<EntityStore> store, @Nonnull String title, @Nonnull String dataset,
                            @Nullable String current, @Nonnull Consumer<String> chosen, @Nonnull CustomUIPage back) {
        Player p = store.getComponent(playerEntity, Player.getComponentType());
        if (p == null) return;
        p.getPageManager().openCustomPage(playerEntity, store, new PickerPage(plugin, player, title, dataset, current, chosen, back));
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder evt,
                      @Nonnull Store<EntityStore> store) {
        cmd.append(LAYOUT);
        cmd.set("#PageTitle.Text", title);
        cmd.set("#Search.Value", query);
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#Search",
                new EventData().append("Action", Action.SEARCH.name()).append("@Value", "#Search.Value"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ClearButton",
                new EventData().append("Action", Action.CLEAR.name()), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#CancelButton",
                new EventData().append("Action", Action.CANCEL.name()), false);
        render(cmd, evt);
    }

    /** The matches for what has been typed. The search box itself is never rewritten, so the caret stays put. */
    private void render(UICommandBuilder cmd, UIEventBuilder evt) {
        shown = JsonDialogues.names(dataset, query, SHOWN);
        int total = JsonDialogues.size(dataset);
        cmd.clear("#Rows");
        for (int i = 0; i < shown.size(); i++) {
            String sel = "#Rows[" + i + "]";
            cmd.append("#Rows", ROW);
            cmd.set(sel + " #Current.Text", shown.get(i).equals(current) ? ">" : "");
            cmd.set(sel + " #Choose.Text", shown.get(i));
            evt.addEventBinding(CustomUIEventBindingType.Activating, sel + " #Choose",
                    new EventData().append("Action", Action.PICK.name()).append("Row", String.valueOf(i)), false);
        }
        cmd.set("#Status.Text", shown.isEmpty()
                ? "Nothing matches \"" + query + "\"; " + total + " to choose from."
                : query.isBlank() ? "The first " + shown.size() + " of " + total + ". Type to find the rest."
                : shown.size() + " of " + total + " match \"" + query + "\".");
    }

    private void refresh() {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder evt = new UIEventBuilder();
        render(cmd, evt);
        sendUpdate(cmd, evt, false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Data data) {
        if (data.action == null) return;
        Action action;
        try {
            action = Action.valueOf(data.action);
        } catch (IllegalArgumentException e) {
            return;
        }
        try {
            switch (action) {
                case SEARCH -> {
                    query = data.value == null ? "" : data.value;
                    refresh();
                }
                case PICK -> {
                    int i = index(data.row);
                    if (i >= 0 && i < shown.size()) chosen.accept(shown.get(i));
                    goBack(ref, store);
                }
                case CLEAR -> {
                    chosen.accept("");
                    goBack(ref, store);
                }
                case CANCEL -> goBack(ref, store);
            }
        } catch (RuntimeException e) {
            plugin.getLogger().at(Level.WARNING).withCause(e).log("picker: %s failed", action);
            goBack(ref, store);
        }
    }

    /** Put back the page this one replaced. */
    private void goBack(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player p = store.getComponent(ref, Player.getComponentType());
        if (p != null) p.getPageManager().openCustomPage(ref, store, back);
    }

    private static int index(@Nullable String raw) {
        try {
            return raw == null ? -1 : Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
