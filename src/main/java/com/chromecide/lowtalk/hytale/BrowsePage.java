package com.chromecide.lowtalk.hytale;

import com.chromecide.lowtalk.LowTalkPlugin;
import com.chromecide.lowtalk.model.Dialogue;
import com.chromecide.lowtalk.ui.PageLayout;
import com.chromecide.lowtalk.ui.UiEvent;
import com.chromecide.lowtalk.ui.UiItem;
import com.chromecide.lowtalk.ui.UiModel;
import com.chromecide.lowtalk.ui.UiOption;
import com.chromecide.lowtalk.ui.UiPage;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The LowTalk tool used on nothing: every loaded dialogue, where it came from and what it is attached to, with
 * Edit and Test for each, plus New and Reload. The way in to dialogues that are not yet on any NPC, prop or block.
 */
public class BrowsePage extends UiPage<BrowsePage.Data> {
    private static final String LAYOUT = "Pages/LowTalk/BrowsePage.ui";
    private static final String ROW = "Pages/LowTalk/BrowseRow.ui";
    private static final String FILTER_ALL = "all";
    private static final String FILTER_UNATTACHED = "unattached";
    private static final String FILTER_ATTACHED = "attached";
    private static final String ROW_DIALOGUE = "dialogue";

    /** Which control carries which value. The only place this page names anything on screen. */
    @Nonnull
    private static PageLayout layout() {
        return PageLayout.of(LAYOUT)
                .property("filters", "#Filter", "Entries")
                .property("filter", "#Filter", "Value")
                .property("showTests", "#ShowTests #CheckBox", "Value")
                .property("emptyVisible", "#Empty", "Visible")
                .property("status", "#Status", "Text")
                .container("rows", "#Rows")
                    .template(ROW_DIALOGUE, ROW)
                        .field("id", "#Id", "Text")
                        .field("source", "#Source", "Text")
                        .field("attached", "#Attached", "Text")
                        .field("notAttached", "#NotAttached", "Visible")
                        .command("EDIT", "#EditButton", UiEvent.ACTIVATE, Map.of("Id", "id"))
                        .command("TEST", "#TestButton", UiEvent.ACTIVATE, Map.of("Id", "id"))
                        .done()
                    .done()
                .command("FILTER", "#Filter", UiEvent.VALUE_CHANGED)
                    .reads("Value", "#Filter", "Value").done()
                .command("TESTS", "#ShowTests #CheckBox", UiEvent.VALUE_CHANGED)
                    .reads("Checked", "#ShowTests #CheckBox", "Value").done()
                .command("NEW", "#NewButton", UiEvent.ACTIVATE).done()
                .command("RELOAD", "#ReloadButton", UiEvent.ACTIVATE).done()
                .command("CLOSE", "#CloseButton", UiEvent.ACTIVATE).done()
                .build();
    }

    public static class Data {
        public static final BuilderCodec<Data> CODEC = BuilderCodec.builder(Data.class, Data::new)
                .append(new KeyedCodec<>("Action", Codec.STRING, false), (d, s) -> d.action = s, d -> d.action).add()
                .append(new KeyedCodec<>("Id", Codec.STRING, false), (d, s) -> d.id = s, d -> d.id).add()
                .append(new KeyedCodec<>("@Value", Codec.STRING, false), (d, s) -> d.value = s, d -> d.value).add()
                .append(new KeyedCodec<>("@Checked", Codec.BOOLEAN, false), (d, b) -> d.checked = b, d -> d.checked).add()
                .build();
        private String action;
        private String id;
        private String value;
        private Boolean checked;
    }

    private final LowTalkPlugin plugin;
    private final Ref<EntityStore> playerEntity;
    private String filter = FILTER_ALL;
    /** The bundled test-corridor dialogues are hidden unless asked for; they are not content anyone edits. */
    private boolean showTests = false;
    private String status = "";

    private BrowsePage(@Nonnull LowTalkPlugin plugin, @Nonnull PlayerRef playerRef, @Nonnull Ref<EntityStore> playerEntity) {
        super(playerRef, CustomPageLifetime.CanDismiss, Data.CODEC, layout());
        this.plugin = plugin;
        this.playerEntity = playerEntity;
    }

    public static void open(@Nonnull LowTalkPlugin plugin, @Nonnull PlayerRef player, @Nonnull Ref<EntityStore> playerEntity,
                            @Nonnull Store<EntityStore> store) {
        plugin.getSessions().end(player.getUuid());
        Player p = store.getComponent(playerEntity, Player.getComponentType());
        if (p == null) return;
        p.getPageManager().openCustomPage(playerEntity, store, new BrowsePage(plugin, player, playerEntity));
    }

    @Override
    protected void populate(@Nonnull UiModel model) {
        model.set("filters", List.of(
                UiOption.of(FILTER_ALL, "all dialogues"),
                UiOption.of(FILTER_UNATTACHED, "not attached to anything"),
                UiOption.of(FILTER_ATTACHED, "attached to an NPC, prop or block")));
        model.set("filter", filter);
        model.set("showTests", showTests);

        Set<String> onProps = plugin.getPropBindings().dialogues();
        Set<String> onBlocks = plugin.getBlockBindings().dialogues();
        List<Dialogue> all = plugin.getRegistry().all();
        all.sort((a, b) -> a.id().compareToIgnoreCase(b.id()));
        List<UiItem> rows = new ArrayList<>();
        for (Dialogue d : all) {
            if (!showTests && isTest(d)) continue;
            String attached = attachedTo(d, onProps, onBlocks);
            boolean isAttached = attached != null;
            if (FILTER_UNATTACHED.equals(filter) && isAttached) continue;
            if (FILTER_ATTACHED.equals(filter) && !isAttached) continue;
            rows.add(UiItem.of(ROW_DIALOGUE)
                    .with("id", d.id())
                    .with("source", source(d))
                    .with("attached", isAttached ? attached : "")
                    .with("notAttached", (Object) Boolean.valueOf(!isAttached)));
        }
        model.setItems("rows", rows);
        model.set("emptyVisible", rows.isEmpty());
        model.set("status", status);
    }

    @Nonnull
    @Override
    protected String commandOf(@Nonnull Data data) {
        return data.action == null ? "" : data.action;
    }

    /** The test-corridor dialogues: files in the dialogues folder's tests/ subfolder, or ids starting with test_. */
    private boolean isTest(Dialogue d) {
        if (d.id().startsWith("test_")) return true;
        DialogueRegistry.Loaded l = plugin.getRegistry().loadedFor(d.id());
        return l != null && l.file() != null && l.file().getParent() != null
                && "tests".equals(l.file().getParent().getFileName().toString());
    }

    /** "elder.talk (asset pack MyPack)" or "elder.talk" for the server folder. */
    private String source(Dialogue d) {
        DialogueRegistry.Loaded l = plugin.getRegistry().loadedFor(d.id());
        if (l == null) return d.file() == null ? "" : d.file();
        String name = l.file() != null ? l.file().getFileName().toString() : l.display();
        return l.pack() == null || l.pack().isEmpty() ? name : name + "  (" + l.pack() + ")";
    }

    /** What the dialogue is attached to, or null when nothing opens it by itself. */
    static String attachedTo(Dialogue d, Set<String> onProps, Set<String> onBlocks) {
        List<String> parts = new ArrayList<>();
        for (String b : d.bindings()) if (!"none".equalsIgnoreCase(b)) parts.add(b);
        if (com.chromecide.lowtalk.hytale.integrations.JoinTriggers.JOIN.equalsIgnoreCase(
                d.otherDirectives().get(com.chromecide.lowtalk.hytale.integrations.JoinTriggers.DIRECTIVE))) parts.add("on join");
        if (onProps.contains(d.id())) parts.add("prop");
        if (onBlocks.contains(d.id())) parts.add("block");
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    @Override
    protected void onCommand(@Nonnull String command, @Nonnull Data data,
                             @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        switch (command) {
            case "FILTER" -> {
                if (data.value != null) filter = data.value;
                refresh();
            }
            case "TESTS" -> {
                showTests = Boolean.TRUE.equals(data.checked);
                refresh();
            }
            case "EDIT" -> {
                Dialogue d = data.id == null ? null : plugin.getRegistry().byId(data.id);
                if (d == null) { status = "That dialogue is no longer loaded."; refresh(); return; }
                World world = store.getExternalData().getWorld();
                world.execute(() -> { // the next page replaces this one; see DialogueEditorPage.test for why not close() first
                    if (!playerEntity.isValid()) return;
                    DialogueEditorPage.open(plugin, d, playerRef, playerEntity, store, narrator(d));
                });
            }
            case "TEST" -> {
                Dialogue d = data.id == null ? null : plugin.getRegistry().byId(data.id);
                if (d == null) { status = "That dialogue is no longer loaded."; refresh(); return; }
                World world = store.getExternalData().getWorld();
                world.execute(() -> {
                    if (!playerEntity.isValid()) return;
                    if (plugin.getSessions().openFor(d, playerRef, playerEntity, store, world, null, com.chromecide.lowtalk.api.Opener.COMMAND) == null) {
                        close();
                        playerRef.sendMessage(LowTalkCommand.msg(plugin, "endedAtOnce").param("passage", "start"));
                    }
                });
            }
            case "NEW" -> {
                World world = store.getExternalData().getWorld();
                world.execute(() -> {
                    if (!playerEntity.isValid()) return;
                    NewDialoguePage.openUnattached(plugin, playerRef, playerEntity, store);
                });
            }
            case "RELOAD" -> {
                DialogueRegistry.LoadReport report = plugin.reloadDialogues();
                status = report.loaded() + " of " + report.files() + " file(s) loaded, " + report.errors() + " error(s), "
                        + report.warnings() + " warning(s)" + (report.messages().isEmpty() ? "" : ": " + String.join("  ", report.messages()));
                refresh();
            }
            case "CLOSE" -> close();
            default -> {}
        }
    }

    /** The voice used when editing or testing without an NPC: the dialogue's speaker, else its title, else Narrator. */
    static NpcInfo narrator(Dialogue d) {
        String name = d.speaker() != null ? d.speaker() : (d.title() != null ? d.title() : "Narrator");
        return new NpcInfo(null, NpcInfo.NONE, "none", name, Set.of());
    }
}
