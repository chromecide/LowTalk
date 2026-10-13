package com.chromecide.lowtalk.hytale;

import com.chromecide.lowtalk.LowTalkPlugin;
import com.chromecide.lowtalk.hytale.json.JsonDialogues;
import com.chromecide.lowtalk.ui.PageLayout;
import com.chromecide.lowtalk.ui.UiEvent;
import com.chromecide.lowtalk.ui.UiItem;
import com.chromecide.lowtalk.ui.UiModel;
import com.chromecide.lowtalk.ui.UiPage;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
public class PickerPage extends UiPage<PickerPage.Data> {
    private static final String LAYOUT = "Pages/LowTalk/PickerPage.ui";
    private static final String ROW = "Pages/LowTalk/PickerRow.ui";
    /** How many matches to show at once. Enough to scroll, small enough to send on every keystroke. */
    private static final int SHOWN = 60;

    public enum Action { SEARCH, PICK, CLEAR, CANCEL }

    private static final String ROW_MATCH = "match";

    /** Which control carries which value. The only place this page names anything on screen. */
    @Nonnull
    private static PageLayout layout() {
        return PageLayout.of(LAYOUT)
                .property("title", "#PageTitle", "Text")
                // Seeded once: rewriting the box while the creator is typing in it would move the caret.
                .seedProperty("search", "#Search", "Value")
                .property("status", "#Status", "Text")
                .container("rows", "#Rows")
                    .template(ROW_MATCH, ROW)
                        .field("current", "#Current", "Text")
                        .field("choose", "#Choose", "Text")
                        .command(Action.PICK.name(), "#Choose", UiEvent.ACTIVATE, Map.of("Row", "row"))
                        .done()
                    .done()
                .command(Action.SEARCH.name(), "#Search", UiEvent.VALUE_CHANGED)
                    .reads("Value", "#Search", "Value").done()
                .command(Action.CLEAR.name(), "#ClearButton", UiEvent.ACTIVATE).done()
                .command(Action.CANCEL.name(), "#CancelButton", UiEvent.ACTIVATE).done()
                .build();
    }

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
        super(player, CustomPageLifetime.CanDismiss, Data.CODEC, layout());
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
    protected void populate(@Nonnull UiModel model) {
        model.set("title", title);
        model.set("search", query);
        shown = JsonDialogues.names(dataset, query, SHOWN);
        int total = JsonDialogues.size(dataset);
        List<UiItem> rows = new ArrayList<>();
        for (int i = 0; i < shown.size(); i++) {
            rows.add(UiItem.of(ROW_MATCH)
                    .with("current", shown.get(i).equals(current) ? ">" : "")
                    .with("choose", shown.get(i))
                    .with("row", String.valueOf(i)));
        }
        model.setItems("rows", rows);
        model.set("status", shown.isEmpty()
                ? "Nothing matches \"" + query + "\"; " + total + " to choose from."
                : query.isBlank() ? "The first " + shown.size() + " of " + total + ". Type to find the rest."
                : shown.size() + " of " + total + " match \"" + query + "\".");
    }

    @Nonnull
    @Override
    protected String commandOf(@Nonnull Data data) {
        return data.action == null ? "" : data.action;
    }

    @Override
    protected void onCommand(@Nonnull String command, @Nonnull Data data,
                             @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        Action action;
        try {
            action = Action.valueOf(command);
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
