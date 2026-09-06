package com.chromecide.lowtalk.hytale;

import com.chromecide.lowtalk.LowTalkPlugin;
import com.chromecide.lowtalk.editor.DialogueDraft;
import com.chromecide.lowtalk.model.Dialogue;
import com.chromecide.lowtalk.model.Option;
import com.chromecide.lowtalk.model.Statement;
import com.chromecide.lowtalk.parser.Printer;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * The dialogue window in edit mode. A creator holding the LowTalk tool clicks an NPC and sees the NPC's dialogue one
 * node at a time, like a player would, but every line and option is a field. Following an option walks into its
 * node; "+ new node" on an option's target creates one. Save prints the draft back to the file the dialogue came
 * from and hot-reloads it; Test runs the unsaved draft from the current node with this NPC.
 *
 * Built on the game's own custom-UI page mechanism, the same one the dialogue window and the Trigger Volume
 * inspector use: rows are appended to a scrolling group and every field is bound to an event.
 */
public class DialogueEditorPage extends InteractiveCustomUIPage<DialogueEditorPage.Data> {
    public static final String TOOL_ITEM = "LowTalk_Tool";
    private static final String LAYOUT = "Pages/LowTalk/EditorPage.ui";
    private static final String ROW_LINE = "Pages/LowTalk/EditLine.ui";
    private static final String ROW_OPTION = "Pages/LowTalk/EditOption.ui";
    private static final String ROW_RAW = "Pages/LowTalk/EditRaw.ui";
    private static final int MAX_CRUMBS = 6;

    public enum Action {
        LINE_TEXT, LINE_SPEAKER, OPT_TEXT, OPT_TARGET, GO, UP, DOWN, DEL,
        ADD_LINE, ADD_OPTION, ADD_NODE, RENAME, JUMP, BACK, DELETE_NODE,
        SAVE, TEST, DISCARD, CLOSE
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

    /** What a rendered row refers to. */
    private record RowRef(int statement, int option) {}

    private final LowTalkPlugin plugin;
    private final UUID npcId;
    private final String npcName;
    private DialogueDraft draft;
    private String node;
    private final List<String> crumbs = new ArrayList<>();
    private final List<RowRef> rows = new ArrayList<>();
    private String status = "";

    public DialogueEditorPage(@Nonnull LowTalkPlugin plugin, @Nonnull PlayerRef playerRef, @Nonnull Dialogue dialogue,
                              @Nonnull UUID npcId, @Nonnull String npcName) {
        super(playerRef, CustomPageLifetime.CanDismiss, Data.CODEC);
        this.plugin = plugin;
        this.npcId = npcId;
        this.npcName = npcName;
        this.draft = new DialogueDraft(dialogue);
        this.node = draft.startNode();
    }

    /** Open the editor for a dialogue on an NPC. World thread. */
    public static void open(@Nonnull LowTalkPlugin plugin, @Nonnull Dialogue dialogue, @Nonnull PlayerRef player,
                            @Nonnull Ref<EntityStore> playerEntity, @Nonnull Store<EntityStore> store, @Nonnull NpcInfo npc) {
        plugin.getSessions().end(player.getUuid());
        Player p = store.getComponent(playerEntity, Player.getComponentType());
        if (p == null) return;
        p.getPageManager().openCustomPage(playerEntity, store, new DialogueEditorPage(plugin, player, dialogue, npc.id(), npc.name()));
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder evt, @Nonnull Store<EntityStore> store) {
        cmd.append(LAYOUT);
        bind(evt, "#RenameButton", Action.RENAME, "#NodeName.Value");
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#NodeJump", new EventData().append("Action", Action.JUMP.name()).append("@Value", "#NodeJump.Value"), false);
        bind(evt, "#BackButton", Action.BACK, null);
        bind(evt, "#DeleteNodeButton", Action.DELETE_NODE, null);
        bind(evt, "#AddLineButton", Action.ADD_LINE, null);
        bind(evt, "#AddOptionButton", Action.ADD_OPTION, null);
        bind(evt, "#AddNodeButton", Action.ADD_NODE, null);
        bind(evt, "#SaveButton", Action.SAVE, null);
        bind(evt, "#TestButton", Action.TEST, null);
        bind(evt, "#DiscardButton", Action.DISCARD, null);
        bind(evt, "#CloseButton", Action.CLOSE, null);
        render(cmd, evt);
    }

    private static void bind(UIEventBuilder evt, String selector, Action action, @Nullable String valueSelector) {
        EventData data = new EventData().append("Action", action.name());
        if (valueSelector != null) data.append("@Value", valueSelector);
        evt.addEventBinding(CustomUIEventBindingType.Activating, selector, data, false);
    }

    private void refresh() {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder evt = new UIEventBuilder();
        render(cmd, evt);
        sendUpdate(cmd, evt, false);
    }

    private void render(UICommandBuilder cmd, UIEventBuilder evt) {
        String display = draft.base().file();
        cmd.set("#PageTitle.Text", (draft.isDirty() ? "* " : "") + draft.id() + "  (" + display + ")  with " + npcName);
        cmd.set("#NodeName.Value", node);
        List<DropdownEntryInfo> nodeEntries = new ArrayList<>();
        for (String n : draft.nodeNames()) nodeEntries.add(new DropdownEntryInfo(LocalizableString.fromString(n), n));
        nodeEntries.add(new DropdownEntryInfo(LocalizableString.fromString("+ new node"), DialogueDraft.TARGET_NEW));
        cmd.set("#NodeJump.Entries", nodeEntries);
        cmd.set("#NodeJump.Value", node);
        StringBuilder crumbText = new StringBuilder();
        List<String> shown = crumbs.size() > MAX_CRUMBS ? crumbs.subList(crumbs.size() - MAX_CRUMBS, crumbs.size()) : crumbs;
        if (crumbs.size() > MAX_CRUMBS) crumbText.append("... > ");
        for (String c : shown) crumbText.append(c).append(" > ");
        crumbText.append(node);
        List<String> unreachable = draft.unreachableNodes();
        if (!unreachable.isEmpty()) crumbText.append("      unreachable: ").append(String.join(", ", unreachable));
        cmd.set("#Crumbs.Text", crumbText.toString());
        cmd.set("#Status.Text", status);

        cmd.clear("#Rows");
        rows.clear();
        List<Statement> body = draft.body(node);
        int row = 0;
        for (int i = 0; i < body.size(); i++) {
            Statement s = body.get(i);
            if (s instanceof Statement.Line l) {
                String sel = "#Rows[" + row + "]";
                cmd.append("#Rows", ROW_LINE);
                cmd.set(sel + " #Speaker.Value", l.speaker() == null ? "" : l.speaker());
                cmd.set(sel + " #Text.Value", Printer.text(l.text()));
                evt.addEventBinding(CustomUIEventBindingType.ValueChanged, sel + " #Speaker", rowData(Action.LINE_SPEAKER, row).append("@Value", sel + " #Speaker.Value"), false);
                evt.addEventBinding(CustomUIEventBindingType.ValueChanged, sel + " #Text", rowData(Action.LINE_TEXT, row).append("@Value", sel + " #Text.Value"), false);
                bindRowButtons(evt, sel, row);
                rows.add(new RowRef(i, -1));
                row++;
            } else if (s instanceof Statement.Choice c) {
                for (int o = 0; o < c.options().size(); o++) {
                    Option opt = c.options().get(o);
                    String sel = "#Rows[" + row + "]";
                    cmd.append("#Rows", ROW_OPTION);
                    cmd.set(sel + " #Label.Value", Printer.text(opt.text()));
                    cmd.set(sel + " #Target.Entries", targetEntries(opt));
                    cmd.set(sel + " #Target.Value", DialogueDraft.optionTarget(opt));
                    evt.addEventBinding(CustomUIEventBindingType.ValueChanged, sel + " #Label", rowData(Action.OPT_TEXT, row).append("@Value", sel + " #Label.Value"), false);
                    evt.addEventBinding(CustomUIEventBindingType.ValueChanged, sel + " #Target", rowData(Action.OPT_TARGET, row).append("@Value", sel + " #Target.Value"), false);
                    evt.addEventBinding(CustomUIEventBindingType.Activating, sel + " #Go", rowData(Action.GO, row), false);
                    bindRowButtons(evt, sel, row);
                    rows.add(new RowRef(i, o));
                    row++;
                }
            } else {
                String sel = "#Rows[" + row + "]";
                cmd.append("#Rows", ROW_RAW);
                cmd.set(sel + " #Text.Text", Printer.block(List.of(s)).strip().replace('\n', ' '));
                bindRowButtons(evt, sel, row);
                rows.add(new RowRef(i, -1));
                row++;
            }
        }
    }

    private static EventData rowData(Action action, int row) {
        return new EventData().append("Action", action.name()).append("Row", String.valueOf(row));
    }

    private static void bindRowButtons(UIEventBuilder evt, String sel, int row) {
        evt.addEventBinding(CustomUIEventBindingType.Activating, sel + " #Up", rowData(Action.UP, row), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, sel + " #Down", rowData(Action.DOWN, row), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, sel + " #Del", rowData(Action.DEL, row), false);
    }

    private List<DropdownEntryInfo> targetEntries(Option opt) {
        List<DropdownEntryInfo> entries = new ArrayList<>();
        entries.add(new DropdownEntryInfo(LocalizableString.fromString("(end)"), DialogueDraft.TARGET_END));
        entries.add(new DropdownEntryInfo(LocalizableString.fromString("(back to these options)"), DialogueDraft.TARGET_CONTINUE));
        for (String n : draft.nodeNames()) entries.add(new DropdownEntryInfo(LocalizableString.fromString("-> " + n), n));
        entries.add(new DropdownEntryInfo(LocalizableString.fromString("+ new node"), DialogueDraft.TARGET_NEW));
        if (DialogueDraft.TARGET_CUSTOM.equals(DialogueDraft.optionTarget(opt))) {
            entries.add(new DropdownEntryInfo(LocalizableString.fromString("(custom: " + Printer.block(opt.body()).strip().replace('\n', ' ') + ")"), DialogueDraft.TARGET_CUSTOM));
        }
        return entries;
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
        RowRef r = rowRef(data.row);
        String value = data.value == null ? "" : data.value;
        try {
            switch (action) {
                case LINE_TEXT -> { if (r != null) draft.setLineText(node, r.statement(), value); }
                case LINE_SPEAKER -> { if (r != null) draft.setLineSpeaker(node, r.statement(), value); }
                case OPT_TEXT -> { if (r != null && r.option() >= 0) draft.setOptionText(node, r.statement(), r.option(), value); }
                case OPT_TARGET -> {
                    if (r == null || r.option() < 0) return;
                    String lead = draft.setOptionTarget(node, r.statement(), r.option(), value);
                    if (DialogueDraft.TARGET_NEW.equals(value) && lead != null) go(lead);
                    refresh();
                }
                case GO -> {
                    if (r == null || r.option() < 0) return;
                    String target = DialogueDraft.optionTarget(draft.options(node, r.statement()).get(r.option()));
                    if (draft.hasNode(target)) { go(target); refresh(); }
                    else status = target.equals(DialogueDraft.TARGET_END) ? "That option ends the conversation." : "That option has no node to walk into.";
                    if (!draft.hasNode(target)) refresh();
                }
                case UP, DOWN -> {
                    if (r == null) return;
                    int delta = action == Action.UP ? -1 : 1;
                    if (r.option() >= 0) draft.moveOption(node, r.statement(), r.option(), delta);
                    else draft.moveStatement(node, r.statement(), delta);
                    refresh();
                }
                case DEL -> {
                    if (r == null) return;
                    if (r.option() >= 0) draft.deleteOption(node, r.statement(), r.option());
                    else draft.deleteStatement(node, r.statement());
                    refresh();
                }
                case ADD_LINE -> { draft.addLine(node); refresh(); }
                case ADD_OPTION -> {
                    int ci = draft.choiceIndex(node);
                    if (ci >= 0 && draft.options(node, ci).size() >= DialoguePage.OPTION_SLOTS) {
                        status = "A node can show at most " + DialoguePage.OPTION_SLOTS + " options.";
                    } else {
                        draft.addOption(node, "New option");
                    }
                    refresh();
                }
                case ADD_NODE -> { go(draft.newNode()); refresh(); }
                case RENAME -> {
                    String to = value.trim();
                    if (to.equals(node)) return;
                    if (draft.renameNode(node, to)) { node = to; status = ""; }
                    else status = "Node names use letters, digits, _ and -, and must be unique.";
                    refresh();
                }
                case JUMP -> {
                    if (DialogueDraft.TARGET_NEW.equals(value)) go(draft.newNode());
                    else if (draft.hasNode(value) && !value.equals(node)) go(value);
                    else return;
                    refresh();
                }
                case BACK -> {
                    if (crumbs.isEmpty()) return;
                    node = crumbs.remove(crumbs.size() - 1);
                    if (!draft.hasNode(node)) node = draft.startNode();
                    refresh();
                }
                case DELETE_NODE -> {
                    String victim = node;
                    if (!draft.deleteNode(victim)) { status = "The last node cannot be deleted."; refresh(); return; }
                    node = crumbs.isEmpty() ? draft.startNode() : crumbs.remove(crumbs.size() - 1);
                    if (!draft.hasNode(node)) node = draft.startNode();
                    status = "Deleted node " + victim + "; options that led there now end the conversation.";
                    refresh();
                }
                case SAVE -> { save(); refresh(); }
                case TEST -> test(ref, store);
                case DISCARD -> {
                    Dialogue current = plugin.getRegistry().byId(draft.id());
                    if (current != null) {
                        draft = new DialogueDraft(current);
                        if (!draft.hasNode(node)) node = draft.startNode();
                        status = "Changes discarded.";
                    }
                    refresh();
                }
                case CLOSE -> close();
            }
        } catch (RuntimeException e) {
            plugin.getLogger().at(Level.WARNING).withCause(e).log("dialogue editor: %s failed", action);
            status = "Something went wrong: " + e.getMessage();
            refresh();
        }
    }

    private void go(String target) {
        crumbs.add(node);
        node = target;
        status = "";
    }

    @Nullable
    private RowRef rowRef(@Nullable String row) {
        if (row == null) return null;
        try {
            int i = Integer.parseInt(row);
            return i >= 0 && i < rows.size() ? rows.get(i) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Print the draft back to where the dialogue came from and reload it. */
    private void save() {
        Dialogue d = draft.toDialogue();
        DialogueRegistry registry = plugin.getRegistry();
        DialogueRegistry.Loaded loaded = registry.loadedFor(d.id());
        if (loaded == null) { status = "This dialogue is no longer loaded; nothing was saved."; return; }
        try {
            if (loaded.file() != null) {
                boolean hadComments = false;
                if (Files.exists(loaded.file())) {
                    for (String line : Files.readAllLines(loaded.file(), StandardCharsets.UTF_8)) {
                        if (line.stripLeading().startsWith("#")) { hadComments = true; break; }
                    }
                }
                String text = Printer.dialogue(d);
                Files.writeString(loaded.file(), text, StandardCharsets.UTF_8);
                DialogueRegistry.LoadReport report = registry.loadFile(loaded.file(), text, true);
                status = report.ok()
                        ? "Saved " + loaded.display() + (hadComments ? ". Comments in the file were dropped (the editor rewrites it)." : ".")
                        + (report.warnings() > 0 ? " " + report.warnings() + " warning(s), see the server log." : "")
                        : "Saved, but the file did not load: " + String.join(" ", report.messages());
            } else {
                Path json = registry.findAssetFile(d.id() + ".json");
                if (json == null) { status = "Could not find " + d.id() + ".json to write to."; return; }
                com.chromecide.lowtalk.hytale.json.DialogueAsset asset = com.chromecide.lowtalk.hytale.json.JsonConvert.toAsset(d);
                asset.setId(d.id());
                String out = com.chromecide.lowtalk.hytale.json.JsonCodecs.DIALOGUE
                        .encode(asset, com.hypixel.hytale.codec.EmptyExtraInfo.EMPTY).asDocument()
                        .toJson(org.bson.json.JsonWriterSettings.builder().indent(true).build());
                Files.writeString(json, out + "\n", StandardCharsets.UTF_8);
                var store = com.chromecide.lowtalk.hytale.json.JsonDialogues.store();
                if (store != null) store.loadAssetsFromPaths(registry.packNameFor(json), List.of(json));
                status = "Saved " + json.getFileName() + ".";
            }
            Dialogue reloaded = registry.byId(d.id());
            if (reloaded != null) {
                draft = new DialogueDraft(reloaded);
                if (!draft.hasNode(node)) node = draft.startNode();
            }
        } catch (IOException e) {
            status = "Could not write the file: " + e.getMessage();
        }
    }

    /** Close the editor and play the unsaved draft from this node with the NPC. */
    private void test(Ref<EntityStore> ref, Store<EntityStore> store) {
        Dialogue d = draft.toDialogue();
        String startAt = node;
        World world = store.getExternalData().getWorld();
        close();
        world.execute(() -> {
            if (!ref.isValid()) return;
            NpcInfo npc = new NpcInfo(null, npcId, d.bindings().isEmpty() ? null : d.bindings().get(0), npcName, java.util.Set.of());
            DialogueSession s = plugin.getSessions().openAt(d, startAt, playerRef, ref, store, world, npc);
            if (s == null) playerRef.sendMessage(com.hypixel.hytale.server.core.Message.raw("LowTalk: the dialogue ended at once from node " + startAt + "."));
        });
    }
}
