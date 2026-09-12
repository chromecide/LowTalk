package com.chromecide.lowtalk.hytale;

import com.chromecide.lowtalk.LowTalkPlugin;
import com.chromecide.lowtalk.editor.DialogueDraft;
import com.chromecide.lowtalk.editor.DialogueDraft.Kind;
import com.chromecide.lowtalk.editor.DialogueDraft.Scope;
import com.chromecide.lowtalk.hytale.json.JsonDialogues;
import com.chromecide.lowtalk.model.Dialogue;
import com.chromecide.lowtalk.model.Option;
import com.chromecide.lowtalk.model.Statement;
import com.chromecide.lowtalk.parser.Printer;
import com.chromecide.lowtalk.parser.Reference;
import com.chromecide.lowtalk.parser.Validator;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.logging.Level;

/**
 * The dialogue window in edit mode. A creator holding the LowTalk tool clicks an NPC and sees the NPC's dialogue one
 * scope at a time (a node, an option's body, a branch of an if, a once or random block), like a player would, but
 * every line, option and command is a field with the game's own pickers behind it. Save prints the draft back to the
 * file the dialogue came from and hot-reloads it; Test runs the unsaved draft from the current node with this NPC.
 *
 * Built on the game's custom-UI page mechanism, the same one the dialogue window and the Trigger Volume inspector
 * use: rows are appended to a scrolling group and every field is bound to an event.
 */
public class DialogueEditorPage extends InteractiveCustomUIPage<DialogueEditorPage.Data> {
    public static final String TOOL_ITEM = "LowTalk_Tool";
    private static final String LAYOUT = "Pages/LowTalk/EditorPage.ui";
    private static final String ROW_LINE = "Pages/LowTalk/EditLine.ui";
    private static final String ROW_OPTION = "Pages/LowTalk/EditOption.ui";
    private static final String ROW_OPTION_COND = "Pages/LowTalk/EditOptionCond.ui";
    private static final String ROW_COMMAND = "Pages/LowTalk/EditCommand.ui";
    private static final String ROW_SET = "Pages/LowTalk/EditSet.ui";
    private static final String ROW_JUMP = "Pages/LowTalk/EditJump.ui";
    private static final String ROW_INPUT = "Pages/LowTalk/EditInput.ui";
    private static final String ROW_WAIT = "Pages/LowTalk/EditWait.ui";
    private static final String ROW_BRANCH = "Pages/LowTalk/EditBranch.ui";
    private static final String ROW_BLOCK = "Pages/LowTalk/EditBlock.ui";
    private static final String HEADER = "Pages/LowTalk/EditHeader.ui";
    private static final int MAX_CRUMBS = 5;
    private static final String NONE = "$none";

    /** Which game list feeds the picker for a command's arguments, by argument position. */
    private static final Map<String, String[]> PICKERS = new java.util.concurrent.ConcurrentHashMap<>(Map.ofEntries(
            Map.entry("attitude", new String[] {JsonDialogues.DATASET_ATTITUDES}),
            Map.entry("anim", new String[] {JsonDialogues.DATASET_ANIMATIONS, JsonDialogues.DATASET_ANIMATION_SLOTS}),
            Map.entry("weather", new String[] {JsonDialogues.DATASET_WEATHERS}),
            Map.entry("spawn", new String[] {JsonDialogues.DATASET_ROLES}),
            Map.entry("music", new String[] {JsonDialogues.DATASET_MUSIC}),
            Map.entry("vfx", new String[] {JsonDialogues.DATASET_PARTICLES}),
            Map.entry("camera", new String[] {JsonDialogues.DATASET_CAMERA_EFFECTS}),
            Map.entry("stat", new String[] {JsonDialogues.DATASET_STATS}),
            Map.entry("learn", new String[] {JsonDialogues.DATASET_RECIPES}),
            Map.entry("teleport", new String[] {JsonDialogues.DATASET_WARPS}),
            Map.entry("reputation", new String[] {JsonDialogues.DATASET_REPUTATION_GROUPS}),
            Map.entry("shop", new String[] {JsonDialogues.DATASET_SHOPS}),
            Map.entry("notify", new String[] {null, JsonDialogues.DATASET_NOTIFY_STYLES}),
            Map.entry("title", new String[] {null, JsonDialogues.DATASET_NOTIFY_STYLES}),
            Map.entry("time", new String[] {JsonDialogues.DATASET_TIMES}),
            Map.entry("objective", new String[] {null, JsonDialogues.DATASET_DIALOGUES})));

    /** Let another plugin's command get a picker: one data set id per argument position, null for none. */
    public static void registerPicker(String command, String... dataSetsByArgument) {
        PICKERS.put(command, dataSetsByArgument.clone());
    }

    public enum Action {
        LINE_TEXT, LINE_SPEAKER, LINE_BUTTON,
        OPT_TEXT, OPT_TARGET, OPT_GO, OPT_MORE, OPT_IF, OPT_SHOW, OPT_ONCE, OPT_BODY,
        CMD_NAME, CMD_ARGS, CMD_PICK, SET, JUMP_NODE, JUMP_GO, INPUT, WAIT,
        BRANCH_COND, BRANCH_BODY, BRANCH_ADD, BRANCH_DEL, BLOCK_BODY, ALT_ADD, ALT_DEL,
        UP, DOWN, DEL, ADD_KIND, ADD, ADD_NODE, RENAME, JUMP, BACK, HEADER, DELETE_NODE,
        H_BINDINGS, H_NPC_PICK, H_SPEAKER, H_TITLE, H_START, H_ON, H_PORTRAIT, H_SCOPE, H_LAYOUT,
        SAVE, TEST, DISCARD, CLOSE
    }

    public static class Data {
        public static final BuilderCodec<Data> CODEC = BuilderCodec.builder(Data.class, Data::new)
                .append(new KeyedCodec<>("Action", Codec.STRING, false), (d, s) -> d.action = s, d -> d.action).add()
                .append(new KeyedCodec<>("Row", Codec.STRING, false), (d, s) -> d.row = s, d -> d.row).add()
                .append(new KeyedCodec<>("@Value", Codec.STRING, false), (d, s) -> d.value = s, d -> d.value).add()
                .append(new KeyedCodec<>("@Value2", Codec.STRING, false), (d, s) -> d.value2 = s, d -> d.value2).add()
                .build();
        private String action;
        private String row;
        private String value;
        private String value2;
    }

    /** What a rendered row refers to: a statement in the current scope and, for options/branches/alternatives, which one. */
    private record RowRef(int statement, int sub) {}

    private final LowTalkPlugin plugin;
    private final UUID npcId;
    private final String npcName;
    private DialogueDraft draft;
    private Scope scope;
    private boolean header = false;
    private final List<Scope> crumbs = new ArrayList<>();
    private final List<RowRef> rows = new ArrayList<>();
    /** Options whose condition row is open, as "statement:option" in the current scope. */
    private final Set<String> expanded = new HashSet<>();
    private String status = "";
    private String addKind = Kind.LINE.name();

    public DialogueEditorPage(@Nonnull LowTalkPlugin plugin, @Nonnull PlayerRef playerRef, @Nonnull Dialogue dialogue,
                              @Nonnull UUID npcId, @Nonnull String npcName) {
        super(playerRef, CustomPageLifetime.CanDismiss, Data.CODEC);
        this.plugin = plugin;
        this.npcId = npcId;
        this.npcName = npcName;
        this.draft = new DialogueDraft(dialogue);
        this.scope = Scope.node(draft.startNode());
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
        change(evt, "#NodeJump", Action.JUMP, "#NodeJump.Value");
        change(evt, "#AddKind", Action.ADD_KIND, null); // remembers the kind; the button adds
        bind(evt, "#AddButton", Action.ADD, "#AddKind.Value");
        bind(evt, "#BackButton", Action.BACK, null);
        bind(evt, "#HeaderButton", Action.HEADER, null);
        bind(evt, "#DeleteNodeButton", Action.DELETE_NODE, null);
        bind(evt, "#AddNodeButton", Action.ADD_NODE, null);
        bind(evt, "#SaveButton", Action.SAVE, null);
        bind(evt, "#TestButton", Action.TEST, null);
        bind(evt, "#DiscardButton", Action.DISCARD, null);
        bind(evt, "#CloseButton", Action.CLOSE, null);
        List<DropdownEntryInfo> kinds = new ArrayList<>();
        kinds.add(entry("Line (something said)", Kind.LINE.name(), null));
        kinds.add(entry("Option (something the player can say)", Kind.OPTION.name(), null));
        kinds.add(entry("Command (give, objective, anim, ...)", Kind.COMMAND.name(), null));
        kinds.add(entry("Set a variable", Kind.SET.name(), null));
        kinds.add(entry("If / else block", Kind.IF.name(), null));
        kinds.add(entry("Once block (first time only)", Kind.ONCE.name(), null));
        kinds.add(entry("Random block (one of several)", Kind.RANDOM.name(), null));
        kinds.add(entry("Ask the player to type something", Kind.INPUT.name(), null));
        kinds.add(entry("Wait a few seconds", Kind.WAIT.name(), null));
        kinds.add(entry("Jump to a passage", Kind.JUMP.name(), null));
        kinds.add(entry("End the conversation", Kind.END.name(), null));
        cmd.set("#AddKind.Entries", kinds);
        cmd.set("#AddKind.Value", addKind);
        render(cmd, evt);
    }

    private static void bind(UIEventBuilder evt, String selector, Action action, @Nullable String valueSelector) {
        EventData data = new EventData().append("Action", action.name());
        if (valueSelector != null) data.append("@Value", valueSelector);
        evt.addEventBinding(CustomUIEventBindingType.Activating, selector, data, false);
    }

    private static void change(UIEventBuilder evt, String selector, Action action, @Nullable String valueSelector) {
        EventData data = new EventData().append("Action", action.name());
        data.append("@Value", valueSelector == null ? selector + ".Value" : valueSelector);
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, selector, data, false);
    }

    private static DropdownEntryInfo entry(String label, String value, @Nullable String tooltip) {
        return tooltip == null
                ? new DropdownEntryInfo(LocalizableString.fromString(label), value)
                : new DropdownEntryInfo(LocalizableString.fromString(label), value, LocalizableString.fromString(tooltip));
    }

    private void refresh() {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder evt = new UIEventBuilder();
        render(cmd, evt);
        sendUpdate(cmd, evt, false);
    }

    // ---- rendering

    private void render(UICommandBuilder cmd, UIEventBuilder evt) {
        if (!draft.exists(scope)) { scope = Scope.node(draft.startNode()); crumbs.clear(); }
        cmd.set("#PageTitle.Text", (draft.isDirty() ? "* " : "") + draft.id() + "  (" + draft.base().file() + ")  with " + npcName);
        cmd.set("#NodeName.Value", scope.node());
        List<DropdownEntryInfo> nodeEntries = new ArrayList<>();
        for (String n : draft.nodeNames()) nodeEntries.add(entry(n, n, null));
        nodeEntries.add(entry("+ new passage", DialogueDraft.TARGET_NEW, null));
        cmd.set("#NodeJump.Entries", nodeEntries);
        cmd.set("#NodeJump.Value", scope.node());
        cmd.set("#Crumbs.Text", crumbText());
        cmd.set("#Status.Text", status);
        cmd.set("#AddRow.Visible", !header);
        cmd.clear("#Rows");
        rows.clear();
        if (header) renderHeader(cmd, evt);
        else renderScope(cmd, evt);
    }

    private String crumbText() {
        StringBuilder sb = new StringBuilder();
        List<Scope> shown = crumbs.size() > MAX_CRUMBS ? crumbs.subList(crumbs.size() - MAX_CRUMBS, crumbs.size()) : crumbs;
        if (crumbs.size() > MAX_CRUMBS) sb.append("... > ");
        for (Scope c : shown) sb.append(draft.describe(c)).append("  >  ");
        sb.append(header ? "dialogue settings" : draft.describe(scope));
        List<String> unreachable = draft.unreachableNodes();
        if (!unreachable.isEmpty()) sb.append("      unreachable: ").append(String.join(", ", unreachable));
        return sb.toString();
    }

    private void renderHeader(UICommandBuilder cmd, UIEventBuilder evt) {
        cmd.append("#Rows", HEADER);
        String sel = "#Rows[0]";
        String bindings = String.join(", ", draft.bindings());
        if ("none".equals(draft.directive("npc"))) bindings = "none";
        cmd.set(sel + " #Bindings.Value", bindings);
        change(evt, sel + " #Bindings", Action.H_BINDINGS, null);
        List<DropdownEntryInfo> npcs = new ArrayList<>();
        npcs.add(entry("+ add an NPC or tag...", NONE, null));
        for (String n : JsonDialogues.names(JsonDialogues.DATASET_NPCS)) npcs.add(entry(n, n, null));
        cmd.set(sel + " #NpcPick.Entries", npcs);
        cmd.set(sel + " #NpcPick.Value", NONE);
        change(evt, sel + " #NpcPick", Action.H_NPC_PICK, null);
        cmd.set(sel + " #Speaker.Value", nz(draft.speaker()));
        change(evt, sel + " #Speaker", Action.H_SPEAKER, null);
        cmd.set(sel + " #DialogTitle.Value", nz(draft.title()));
        change(evt, sel + " #DialogTitle", Action.H_TITLE, null);
        List<DropdownEntryInfo> nodes = new ArrayList<>();
        for (String n : draft.nodeNames()) nodes.add(entry(n, n, null));
        cmd.set(sel + " #StartNode.Entries", nodes);
        cmd.set(sel + " #StartNode.Value", draft.startNode());
        change(evt, sel + " #StartNode", Action.H_START, null);
        List<DropdownEntryInfo> on = new ArrayList<>();
        on.add(entry("when the player talks to the NPC", NONE, null));
        on.add(entry("when a player joins the world (no NPC)", "join", null));
        cmd.set(sel + " #On.Entries", on);
        cmd.set(sel + " #On.Value", draft.directive("on") == null ? NONE : draft.directive("on"));
        change(evt, sel + " #On", Action.H_ON, null);
        cmd.set(sel + " #Portrait.Value", nz(draft.directive("portrait")));
        change(evt, sel + " #Portrait", Action.H_PORTRAIT, null);
        cmd.set(sel + " #Scope.Value", draft.scope() == null || draft.scope().equals(draft.id()) ? "" : draft.scope());
        change(evt, sel + " #Scope", Action.H_SCOPE, null);
        List<DropdownEntryInfo> layouts = new ArrayList<>();
        layouts.add(entry("default (" + plugin.getPresentation().explainLayoutDefault(draft.id()) + ")", NONE, null));
        layouts.add(entry("bottom bar (NPC stays visible)", "bottom", null));
        layouts.add(entry("top bar", "top", null));
        layouts.add(entry("window (centred, dimmed screen)", "window", null));
        cmd.set(sel + " #Layout.Entries", layouts);
        String l = draft.directive("layout");
        cmd.set(sel + " #Layout.Value", l == null || !com.chromecide.lowtalk.hytale.presentation.DialogueLayout.isValid(l) ? NONE : l.trim().toLowerCase());
        change(evt, sel + " #Layout", Action.H_LAYOUT, null);
    }

    private void renderScope(UICommandBuilder cmd, UIEventBuilder evt) {
        List<Statement> body = draft.view(scope);
        int row = 0;
        for (int i = 0; i < body.size(); i++) {
            Statement s = body.get(i);
            switch (s) {
                case Statement.Line l -> {
                    String sel = append(cmd, ROW_LINE, row);
                    cmd.set(sel + " #Speaker.Value", nz(l.speaker()));
                    cmd.set(sel + " #Text.Value", Printer.text(l.text()));
                    rowChange(evt, sel + " #Speaker", Action.LINE_SPEAKER, row);
                    rowChange(evt, sel + " #Text", Action.LINE_TEXT, row);
                    cmd.set(sel + " #Button.Value", nz(l.button()));
                    rowChange(evt, sel + " #Button", Action.LINE_BUTTON, row);
                    row = standard(evt, sel, row, new RowRef(i, -1));
                }
                case Statement.Choice c -> {
                    for (int o = 0; o < c.options().size(); o++) {
                        Option opt = c.options().get(o);
                        String sel = append(cmd, ROW_OPTION, row);
                        cmd.set(sel + " #Label.Value", Printer.text(opt.text()));
                        cmd.set(sel + " #Target.Entries", targetEntries(opt));
                        cmd.set(sel + " #Target.Value", DialogueDraft.optionTarget(opt));
                        rowChange(evt, sel + " #Label", Action.OPT_TEXT, row);
                        rowChange(evt, sel + " #Target", Action.OPT_TARGET, row);
                        rowClick(evt, sel + " #Go", Action.OPT_GO, row);
                        rowClick(evt, sel + " #More", Action.OPT_MORE, row);
                        row = standard(evt, sel, row, new RowRef(i, o));
                        if (expanded.contains(i + ":" + o)) {
                            String sel2 = append(cmd, ROW_OPTION_COND, row);
                            cmd.set(sel2 + " #If.Value", opt.guard() == null ? "" : Printer.expr(opt.guard()));
                            cmd.set(sel2 + " #ShowIf.Value", opt.showGuard() == null ? "" : Printer.expr(opt.showGuard()));
                            cmd.set(sel2 + " #Once.Text", opt.once() ? "once: yes" : "once: no");
                            rowChange(evt, sel2 + " #If", Action.OPT_IF, row);
                            rowChange(evt, sel2 + " #ShowIf", Action.OPT_SHOW, row);
                            rowClick(evt, sel2 + " #Once", Action.OPT_ONCE, row);
                            rowClick(evt, sel2 + " #Body", Action.OPT_BODY, row);
                            rows.add(new RowRef(i, o));
                            row++;
                        }
                    }
                }
                case Statement.Command c -> {
                    String sel = append(cmd, ROW_COMMAND, row);
                    cmd.set(sel + " #Name.Entries", commandEntries());
                    cmd.set(sel + " #Name.Value", c.name());
                    cmd.set(sel + " #Args.Value", DialogueDraft.argsText(c));
                    String dataset = pickerFor(c.name());
                    List<DropdownEntryInfo> picks = new ArrayList<>();
                    int argIndex = pickerArg(c.name());
                    String current = argIndex >= 0 && c.args().size() > argIndex ? Printer.text(c.args().get(argIndex)) : "";
                    if (dataset != null) {
                        picks.add(entry(argIndex == 0 ? "pick..." : "pick argument " + (argIndex + 1) + "...", NONE, null));
                        boolean seen = false;
                        for (String n : JsonDialogues.names(dataset)) { picks.add(entry(n, n, null)); if (n.equals(current)) seen = true; }
                        if (!seen && !current.isEmpty()) picks.add(entry(current, current, null));
                    }
                    cmd.set(sel + " #Pick.Entries", picks);
                    cmd.set(sel + " #Pick.Value", dataset == null || current.isEmpty() ? NONE : current);
                    cmd.set(sel + " #Pick.Visible", dataset != null);
                    evt.addEventBinding(CustomUIEventBindingType.ValueChanged, sel + " #Name",
                            rowData(Action.CMD_NAME, row).append("@Value", sel + " #Name.Value").append("@Value2", sel + " #Args.Value"), false);
                    evt.addEventBinding(CustomUIEventBindingType.ValueChanged, sel + " #Args",
                            rowData(Action.CMD_ARGS, row).append("@Value", sel + " #Name.Value").append("@Value2", sel + " #Args.Value"), false);
                    rowChange(evt, sel + " #Pick", Action.CMD_PICK, row);
                    row = standard(evt, sel, row, new RowRef(i, -1));
                }
                case Statement.Set st -> {
                    String sel = append(cmd, ROW_SET, row);
                    cmd.set(sel + " #Var.Value", Printer.expr(st.target()));
                    cmd.set(sel + " #Value.Value", Printer.expr(st.value()));
                    for (String f : List.of("#Var", "#Value")) {
                        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, sel + " " + f,
                                rowData(Action.SET, row).append("@Value", sel + " #Var.Value").append("@Value2", sel + " #Value.Value"), false);
                    }
                    row = standard(evt, sel, row, new RowRef(i, -1));
                }
                case Statement.Jump j -> {
                    String sel = append(cmd, ROW_JUMP, row);
                    List<DropdownEntryInfo> nodes = new ArrayList<>();
                    for (String n : draft.nodeNames()) nodes.add(entry(n, n, null));
                    cmd.set(sel + " #Node.Entries", nodes);
                    cmd.set(sel + " #Node.Value", j.node());
                    rowChange(evt, sel + " #Node", Action.JUMP_NODE, row);
                    rowClick(evt, sel + " #Go", Action.JUMP_GO, row);
                    row = standard(evt, sel, row, new RowRef(i, -1));
                }
                case Statement.Input in -> {
                    String sel = append(cmd, ROW_INPUT, row);
                    cmd.set(sel + " #Var.Value", Printer.expr(in.target()));
                    cmd.set(sel + " #Prompt.Value", Printer.text(in.prompt()));
                    for (String f : List.of("#Var", "#Prompt")) {
                        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, sel + " " + f,
                                rowData(Action.INPUT, row).append("@Value", sel + " #Var.Value").append("@Value2", sel + " #Prompt.Value"), false);
                    }
                    row = standard(evt, sel, row, new RowRef(i, -1));
                }
                case Statement.Wait w -> {
                    String sel = append(cmd, ROW_WAIT, row);
                    cmd.set(sel + " #Seconds.Value", Printer.expr(w.seconds()));
                    rowChange(evt, sel + " #Seconds", Action.WAIT, row);
                    row = standard(evt, sel, row, new RowRef(i, -1));
                }
                case Statement.End e -> {
                    String sel = append(cmd, ROW_BLOCK, row);
                    cmd.set(sel + " #Tag.Text", "end");
                    cmd.set(sel + " #Summary.Text", "the conversation ends here");
                    cmd.set(sel + " #Body.Visible", false);
                    cmd.set(sel + " #AddAlt.Visible", false);
                    row = standard(evt, sel, row, new RowRef(i, -1));
                }
                case Statement.Conditional c -> {
                    for (int b = 0; b < c.branches().size(); b++) {
                        Statement.Branch br = c.branches().get(b);
                        String sel = append(cmd, ROW_BRANCH, row);
                        cmd.set(sel + " #Tag.Text", b == 0 ? "if" : br.condition() == null ? "else" : "else if");
                        cmd.set(sel + " #Cond.Value", br.condition() == null ? "" : Printer.expr(br.condition()));
                        cmd.set(sel + " #Cond.Visible", !(b > 0 && br.condition() == null));
                        cmd.set(sel + " #AddBranch.Visible", b == c.branches().size() - 1);
                        rowChange(evt, sel + " #Cond", Action.BRANCH_COND, row);
                        rowClick(evt, sel + " #Body", Action.BRANCH_BODY, row);
                        rowClick(evt, sel + " #AddBranch", Action.BRANCH_ADD, row);
                        rowClick(evt, sel + " #Up", Action.UP, row);
                        rowClick(evt, sel + " #Down", Action.DOWN, row);
                        rowClick(evt, sel + " #Del", Action.BRANCH_DEL, row);
                        cmd.set(sel + " #Up.Visible", b == 0);
                        cmd.set(sel + " #Down.Visible", b == 0);
                        rows.add(new RowRef(i, b));
                        row++;
                    }
                }
                case Statement.Once o -> {
                    String sel = append(cmd, ROW_BLOCK, row);
                    cmd.set(sel + " #Tag.Text", "once");
                    cmd.set(sel + " #Summary.Text", summary(o.body()));
                    cmd.set(sel + " #AddAlt.Visible", false);
                    rowClick(evt, sel + " #Body", Action.BLOCK_BODY, row);
                    row = standard(evt, sel, row, new RowRef(i, 0));
                }
                case Statement.Random r -> {
                    for (int a = 0; a < r.alternatives().size(); a++) {
                        String sel = append(cmd, ROW_BLOCK, row);
                        cmd.set(sel + " #Tag.Text", a == 0 ? "random" : "or");
                        cmd.set(sel + " #Summary.Text", summary(r.alternatives().get(a)));
                        cmd.set(sel + " #AddAlt.Visible", a == r.alternatives().size() - 1);
                        rowClick(evt, sel + " #Body", Action.BLOCK_BODY, row);
                        rowClick(evt, sel + " #AddAlt", Action.ALT_ADD, row);
                        rowClick(evt, sel + " #Up", Action.UP, row);
                        rowClick(evt, sel + " #Down", Action.DOWN, row);
                        rowClick(evt, sel + " #Del", Action.ALT_DEL, row);
                        cmd.set(sel + " #Up.Visible", a == 0);
                        cmd.set(sel + " #Down.Visible", a == 0);
                        rows.add(new RowRef(i, a));
                        row++;
                    }
                }
            }
        }
    }

    private static String append(UICommandBuilder cmd, String layout, int row) {
        cmd.append("#Rows", layout);
        return "#Rows[" + row + "]";
    }

    /** Bind the up/down/delete buttons and register the row; returns the next row number. */
    private int standard(UIEventBuilder evt, String sel, int row, RowRef ref) {
        rowClick(evt, sel + " #Up", Action.UP, row);
        rowClick(evt, sel + " #Down", Action.DOWN, row);
        rowClick(evt, sel + " #Del", Action.DEL, row);
        rows.add(ref);
        return row + 1;
    }

    private static EventData rowData(Action action, int row) {
        return new EventData().append("Action", action.name()).append("Row", String.valueOf(row));
    }

    private static void rowClick(UIEventBuilder evt, String selector, Action action, int row) {
        evt.addEventBinding(CustomUIEventBindingType.Activating, selector, rowData(action, row), false);
    }

    private static void rowChange(UIEventBuilder evt, String selector, Action action, int row) {
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, selector, rowData(action, row).append("@Value", selector + ".Value"), false);
    }

    private static String summary(List<Statement> body) {
        String s = Printer.block(body).strip().replace('\n', ' ');
        return s.length() > 70 ? s.substring(0, 67) + "..." : s;
    }

    private static String nz(@Nullable String s) {
        return s == null ? "" : s;
    }

    private List<DropdownEntryInfo> targetEntries(Option opt) {
        List<DropdownEntryInfo> entries = new ArrayList<>();
        entries.add(entry("(end)", DialogueDraft.TARGET_END, "closes the window"));
        entries.add(entry("(back to these options)", DialogueDraft.TARGET_CONTINUE, "shows this passage's options again"));
        for (String n : draft.nodeNames()) entries.add(entry("-> " + n, n, null));
        entries.add(entry("+ new passage", DialogueDraft.TARGET_NEW, null));
        if (DialogueDraft.TARGET_CUSTOM.equals(DialogueDraft.optionTarget(opt))) {
            entries.add(entry("(custom)", DialogueDraft.TARGET_CUSTOM, summary(opt.body())));
        }
        return entries;
    }

    private List<DropdownEntryInfo> commandEntries() {
        Set<String> names = new TreeSet<>(Validator.BUILTIN_COMMANDS.keySet());
        names.addAll(plugin.getEffects().names());
        List<DropdownEntryInfo> out = new ArrayList<>();
        for (String n : names) {
            Reference.Entry ref = Reference.lookup(n);
            out.add(entry(n, n, ref == null ? null : ref.usage() + "  -  " + ref.description()));
        }
        return out;
    }

    @Nullable
    private static String pickerFor(String command) {
        String[] p = PICKERS.get(command);
        if (p == null) return null;
        for (String d : p) if (d != null) return d;
        return null;
    }

    private static int pickerArg(String command) {
        String[] p = PICKERS.get(command);
        if (p == null) return -1;
        for (int i = 0; i < p.length; i++) if (p[i] != null) return i;
        return -1;
    }

    // ---- events

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
        String value2 = data.value2 == null ? "" : data.value2;
        try {
            boolean redraw = handle(action, r, value, value2, ref, store);
            if (redraw) refresh();
        } catch (RuntimeException e) {
            plugin.getLogger().at(Level.WARNING).withCause(e).log("dialogue editor: %s failed", action);
            status = "Something went wrong: " + e.getMessage();
            refresh();
        }
    }

    /** Apply one event; returns true when the window must be redrawn. */
    private boolean handle(Action action, @Nullable RowRef r, String value, String value2, Ref<EntityStore> ref, Store<EntityStore> store) {
        switch (action) {
            // typing into fields changes the draft without redrawing, so the caret stays where it is
            case LINE_TEXT -> { if (r != null) draft.setLineText(scope, r.statement(), value); return false; }
            case LINE_SPEAKER -> { if (r != null) draft.setLineSpeaker(scope, r.statement(), value); return false; }
            case LINE_BUTTON -> { if (r != null) draft.setLineButton(scope, r.statement(), value); return false; }
            case OPT_TEXT -> { if (r != null) draft.setOptionText(scope, r.statement(), r.sub(), value); return false; }
            case OPT_IF -> { if (r != null) return problem(draft.setOptionGuard(scope, r.statement(), r.sub(), value)); return false; }
            case OPT_SHOW -> { if (r != null) return problem(draft.setOptionShowGuard(scope, r.statement(), r.sub(), value)); return false; }
            case CMD_NAME, CMD_ARGS -> { if (r != null) return problem(draft.setCommand(scope, r.statement(), value, value2)) || action == Action.CMD_NAME; return false; }
            case SET -> { if (r != null) return problem(draft.setSet(scope, r.statement(), value, value2)); return false; }
            case INPUT -> { if (r != null) return problem(draft.setInput(scope, r.statement(), value, value2)); return false; }
            case WAIT -> { if (r != null) return problem(draft.setWait(scope, r.statement(), value)); return false; }
            case BRANCH_COND -> { if (r != null) return problem(draft.setBranchCondition(scope, r.statement(), r.sub(), value)); return false; }
            case H_BINDINGS -> { draft.setBindings(value); return false; }
            case H_SPEAKER -> { draft.setSpeaker(value); return false; }
            case H_TITLE -> { draft.setTitle(value); return false; }
            case H_PORTRAIT -> { draft.setDirective("portrait", value); return false; }
            case H_SCOPE -> { draft.setScope(value); return false; }

            case OPT_TARGET -> {
                if (r == null) return false;
                String lead = draft.setOptionTarget(scope, r.statement(), r.sub(), value);
                if (DialogueDraft.TARGET_NEW.equals(value) && lead != null) go(Scope.node(lead));
                return true;
            }
            case OPT_GO -> {
                if (r == null) return false;
                Option opt = draft.options(scope, r.statement()).get(r.sub());
                String target = DialogueDraft.optionTarget(opt);
                if (draft.hasNode(target)) go(Scope.node(target));
                else if (DialogueDraft.TARGET_CUSTOM.equals(target)) go(scope.into(r.statement(), r.sub()));
                else status = DialogueDraft.TARGET_END.equals(target) ? "That option ends the conversation." : "That option shows this passage's options again.";
                return true;
            }
            case OPT_MORE -> {
                if (r == null) return false;
                String key = r.statement() + ":" + r.sub();
                if (!expanded.remove(key)) expanded.add(key);
                return true;
            }
            case OPT_ONCE -> {
                if (r == null) return false;
                Option opt = draft.options(scope, r.statement()).get(r.sub());
                draft.setOptionOnce(scope, r.statement(), r.sub(), !opt.once());
                return true;
            }
            case OPT_BODY -> { if (r != null) go(scope.into(r.statement(), r.sub())); return true; }
            case CMD_PICK -> {
                if (r == null || NONE.equals(value)) return false;
                Statement s = draft.view(scope).get(r.statement());
                if (s instanceof Statement.Command c) draft.setCommandArg(scope, r.statement(), Math.max(0, pickerArg(c.name())), value);
                return true;
            }
            case JUMP_NODE -> { if (r != null) draft.setJump(scope, r.statement(), value); return false; }
            case JUMP_GO -> {
                if (r == null) return false;
                Statement s = draft.view(scope).get(r.statement());
                if (s instanceof Statement.Jump j && draft.hasNode(j.node())) go(Scope.node(j.node()));
                return true;
            }
            case BRANCH_BODY, BLOCK_BODY -> { if (r != null) go(scope.into(r.statement(), Math.max(0, r.sub()))); return true; }
            case BRANCH_ADD -> { if (r != null) draft.addBranch(scope, r.statement()); return true; }
            case BRANCH_DEL -> { if (r != null) draft.deleteBranch(scope, r.statement(), r.sub()); return true; }
            case ALT_ADD -> { if (r != null) draft.addAlternative(scope, r.statement()); return true; }
            case ALT_DEL -> { if (r != null) draft.deleteAlternative(scope, r.statement(), r.sub()); return true; }
            case UP, DOWN -> {
                if (r == null) return false;
                int delta = action == Action.UP ? -1 : 1;
                Statement s = draft.view(scope).get(r.statement());
                if (s instanceof Statement.Choice && r.sub() >= 0) draft.moveOption(scope, r.statement(), r.sub(), delta);
                else draft.move(scope, r.statement(), delta);
                return true;
            }
            case DEL -> {
                if (r == null) return false;
                Statement s = draft.view(scope).get(r.statement());
                if (s instanceof Statement.Choice && r.sub() >= 0) draft.deleteOption(scope, r.statement(), r.sub());
                else draft.delete(scope, r.statement());
                return true;
            }
            case ADD_KIND -> { if (!value.isEmpty()) addKind = value; return false; }
            case ADD -> {
                if (header) { status = "Go back to a passage to add to it."; return true; }
                String kindName = value.isEmpty() ? addKind : value;
                Kind kind;
                try {
                    kind = Kind.valueOf(kindName);
                } catch (IllegalArgumentException e) {
                    return false;
                }
                addKind = kindName;
                if (kind == Kind.OPTION) {
                    int count = 0;
                    for (Statement s : draft.view(scope)) if (s instanceof Statement.Choice c) count += c.options().size();
                    if (count >= DialoguePage.OPTION_SLOTS) { status = "A passage can show at most " + DialoguePage.OPTION_SLOTS + " options."; return true; }
                }
                draft.add(scope, kind);
                status = "";
                return true;
            }
            case ADD_NODE -> { go(Scope.node(draft.newNode())); header = false; return true; }
            case RENAME -> {
                String to = value.trim();
                if (to.equals(scope.node())) return false;
                if (draft.renameNode(scope.node(), to)) { scope = new Scope(to, scope.path()); status = ""; }
                else status = "Passage names use letters, digits, _ and -, and must be unique.";
                return true;
            }
            case JUMP -> {
                if (DialogueDraft.TARGET_NEW.equals(value)) go(Scope.node(draft.newNode()));
                else if (draft.hasNode(value) && !(value.equals(scope.node()) && scope.isRoot() && !header)) go(Scope.node(value));
                else return false;
                header = false;
                return true;
            }
            case BACK -> {
                if (header) { header = false; return true; }
                if (crumbs.isEmpty()) { if (!scope.isRoot()) scope = scope.parent(); return true; }
                scope = crumbs.remove(crumbs.size() - 1);
                return true;
            }
            case HEADER -> { header = !header; return true; }
            case DELETE_NODE -> {
                String victim = scope.node();
                if (!draft.deleteNode(victim)) { status = "The last passage cannot be deleted."; return true; }
                scope = crumbs.isEmpty() ? Scope.node(draft.startNode()) : crumbs.remove(crumbs.size() - 1);
                if (!draft.exists(scope)) scope = Scope.node(draft.startNode());
                status = "Deleted passage " + victim + "; options that led there now end the conversation.";
                return true;
            }
            case H_NPC_PICK -> {
                if (NONE.equals(value)) return false;
                List<String> b = new ArrayList<>(draft.bindings());
                if (!b.contains(value)) b.add(value);
                draft.setBindings(String.join(", ", b));
                return true;
            }
            case H_START -> { draft.setStartNode(value); return true; }
            case H_ON -> { draft.setDirective("on", NONE.equals(value) ? null : value); return true; }
            case H_LAYOUT -> { draft.setDirective("layout", NONE.equals(value) ? null : value); return false; }
            case SAVE -> { save(); return true; }
            case TEST -> { test(ref, store); return false; }
            case DISCARD -> {
                Dialogue current = plugin.getRegistry().byId(draft.id());
                if (current != null) {
                    draft = new DialogueDraft(current);
                    if (!draft.exists(scope)) scope = Scope.node(draft.startNode());
                    status = "Changes discarded.";
                }
                return true;
            }
            case CLOSE -> { close(); return false; }
        }
        return false;
    }

    /** Show a problem in the status line; true when there was one (so the window redraws with it). */
    private boolean problem(@Nullable String message) {
        if (message == null) { if (!status.isEmpty()) { status = ""; return true; } return false; }
        status = message;
        return true;
    }

    private void go(Scope target) {
        if (!header) crumbs.add(scope);
        header = false;
        scope = target;
        expanded.clear();
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

    // ---- save and test

    /** Print the draft back to where the dialogue came from and reload it. */
    private void save() {
        Dialogue d = draft.toDialogue();
        DialogueRegistry registry = plugin.getRegistry();
        DialogueRegistry.Loaded loaded = registry.loadedFor(d.id());
        if (loaded == null) { status = "This dialogue is no longer loaded; nothing was saved."; return; }
        List<Validator.Problem> problems = new Validator(plugin.getEffects().names(), plugin.getFunctions().names()).validate(d);
        for (Validator.Problem p : problems) {
            if (p.error()) { status = "Not saved: " + p.message(); return; }
        }
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
                        + (report.warnings() > 0 ? " " + report.warnings() + " warning(s): " + String.join(" ", report.messages()) : "")
                        : "Saved, but the file did not load: " + String.join(" ", report.messages());
            } else {
                Path json = registry.findAssetFile(d.id() + ".json");
                if (json == null) { status = "Could not find " + d.id() + ".json to write to."; return; }
                com.chromecide.lowtalk.hytale.json.LowTalkJson asset = com.chromecide.lowtalk.hytale.json.JsonConvert.toAsset(d);
                asset.setId(d.id());
                String out = com.chromecide.lowtalk.hytale.json.JsonCodecs.DIALOGUE
                        .encode(asset, com.hypixel.hytale.codec.EmptyExtraInfo.EMPTY).asDocument()
                        .toJson(org.bson.json.JsonWriterSettings.builder().indent(true).build());
                Files.writeString(json, out + "\n", StandardCharsets.UTF_8);
                var store = JsonDialogues.store();
                if (store != null) store.loadAssetsFromPaths(registry.packNameFor(json), List.of(json));
                status = "Saved " + json.getFileName() + ".";
            }
            Dialogue reloaded = registry.byId(d.id());
            if (reloaded != null) {
                draft = new DialogueDraft(reloaded);
                if (!draft.exists(scope)) scope = Scope.node(draft.startNode());
            }
        } catch (IOException e) {
            status = "Could not write the file: " + e.getMessage();
        }
    }

    /** Close the editor and play the unsaved draft from this node with the NPC. */
    private void test(Ref<EntityStore> ref, Store<EntityStore> store) {
        Dialogue d = draft.toDialogue();
        for (Validator.Problem p : new Validator(plugin.getEffects().names(), plugin.getFunctions().names()).validate(d)) {
            if (p.error()) { status = "Cannot test yet: " + p.message(); refresh(); return; }
        }
        String startAt = scope.node();
        World world = store.getExternalData().getWorld();
        close();
        world.execute(() -> {
            if (!ref.isValid()) return;
            NpcInfo npc = new NpcInfo(null, npcId, d.bindings().isEmpty() ? null : d.bindings().get(0), npcName, java.util.Set.of());
            DialogueSession s = plugin.getSessions().openAt(d, startAt, playerRef, ref, store, world, npc);
            if (s == null) playerRef.sendMessage(com.hypixel.hytale.server.core.Message.raw("LowTalk: the dialogue ended at once from passage " + startAt + "."));
        });
    }
}
