package com.chromecide.lowtalk.hytale;

import com.chromecide.lowtalk.LowTalkPlugin;
import com.chromecide.lowtalk.editor.CommandSpecs;
import com.chromecide.lowtalk.editor.ConditionShapes;
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
import com.chromecide.lowtalk.ui.UiItem;
import com.chromecide.lowtalk.ui.UiModel;
import com.chromecide.lowtalk.ui.UiOption;
import com.chromecide.lowtalk.ui.UiPage;
import com.chromecide.lowtalk.ui.UiText;
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
public class DialogueEditorPage extends UiPage<DialogueEditorPage.Data> {
    public static final String TOOL_ITEM = "LowTalk_Tool";
    private static final int MAX_CRUMBS = 5;
    private static final String NONE = "$none";
    /** A condition written by hand, kept as text because it is none of the shapes the fields can show. */
    private static final String RAW = "$raw";
    /** The comparison that asks for nothing but a yes; the empty string cannot be a dropdown's value. */
    private static final String OP_YES = "$yes";
    /** Which condition a row's events are about: an option's two, and an if-block's one. */
    private static final int COND_IF = 0;
    private static final int COND_SHOW = 1;
    private static final int COND_BRANCH = 2;
    /**
     * How long a list may be and still be handed to the client whole, for its own search box to filter. Longer
     * ones keep a text box and are narrowed here as the creator types: there are thousands of items and sounds,
     * and every entry would be sent again on every redraw. The game's own pages do the same, never putting more
     * than a few hundred entries in a dropdown.
     */
    private static final int WHOLE_LIST_MAX = 300;
    /** How many entries a narrowed list offers at once. */
    private static final int PICK_LIMIT = 50;

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
        COND_KIND, COND_ARG, COND_ARG_PICK, COND_ARG_FIND, COND_OP, COND_VAL, COND_VAL_PICK, COND_VAL_FIND, COND_RAW,
        CMD_ARGS, CMD_SLOT, CMD_SLOT_PICK, CMD_SLOT_FIND, CMD_PICK, SET, JUMP_NODE, JUMP_GO, INPUT, INPUT_KIND, WAIT,
        BRANCH_BODY, BRANCH_ADD, BRANCH_DEL, BLOCK_BODY, ALT_ADD, ALT_DEL,
        UP, DOWN, DEL, ADD_KIND, ADD, ADD_NODE, RENAME, JUMP, BACK, HEADER, DELETE_NODE,
        H_BINDINGS, H_NPC_PICK, H_SPEAKER, H_TITLE, H_START, H_ON, H_PORTRAIT, H_SCOPE, H_LAYOUT, H_HISTORY,
        SAVE, TEST, DISCARD, CLOSE
    }

    public static class Data {
        public static final BuilderCodec<Data> CODEC = BuilderCodec.builder(Data.class, Data::new)
                .append(new KeyedCodec<>("Action", Codec.STRING, false), (d, s) -> d.action = s, d -> d.action).add()
                .append(new KeyedCodec<>("Row", Codec.STRING, false), (d, s) -> d.row = s, d -> d.row).add()
                .append(new KeyedCodec<>("Slot", Codec.STRING, false), (d, s) -> d.slot = s, d -> d.slot).add()
                .append(new KeyedCodec<>("@Value", Codec.STRING, false), (d, s) -> d.value = s, d -> d.value).add()
                .append(new KeyedCodec<>("@Value2", Codec.STRING, false), (d, s) -> d.value2 = s, d -> d.value2).add()
                .build();
        private String action;
        private String row;
        private String slot;
        private String value;
        private String value2;
    }

    /** What a rendered row refers to: a statement in the current scope and, for options/branches/alternatives, which one. */
    private record RowRef(int statement, int sub) {}

    private final LowTalkPlugin plugin;
    /** Where this dialogue was loaded from when the editor opened. A save falls back to it if the registry has
     *  dropped the dialogue in the meantime, so a bad reload cannot strand the work on screen. */
    private final DialogueRegistry.Loaded origin;
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
    /** Statements and options the validator is unhappy with, true when it is an error rather than a warning.
     *  Recomputed on every redraw so a row is marked while it is being written, not only when Save is pressed. */
    private final java.util.Map<Object, Boolean> flagged = new java.util.IdentityHashMap<>();
    private String liveSummary = "";

    public DialogueEditorPage(@Nonnull LowTalkPlugin plugin, @Nonnull PlayerRef playerRef, @Nonnull Dialogue dialogue,
                              @Nonnull UUID npcId, @Nonnull String npcName) {
        super(playerRef, CustomPageLifetime.CanDismiss, Data.CODEC, EditorLayout.build());
        this.plugin = plugin;
        this.npcId = npcId;
        this.npcName = npcName;
        this.draft = new DialogueDraft(dialogue);
        this.scope = Scope.node(draft.startNode());
        this.origin = plugin.getRegistry().loadedFor(dialogue.id());
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
    protected void populate(@Nonnull UiModel model) {
        if (!draft.exists(scope)) { scope = Scope.node(draft.startNode()); crumbs.clear(); }
        model.set("pageTitle", (draft.isDirty() ? "* " : "") + draft.id() + "  (" + draft.base().file() + ")  with " + npcName);
        model.set("nodeName", scope.node());
        List<UiOption> nodeEntries = new ArrayList<>();
        for (String n : draft.nodeNames()) nodeEntries.add(UiOption.same(n));
        nodeEntries.add(UiOption.of(DialogueDraft.TARGET_NEW, "+ new passage"));
        model.set("nodeEntries", nodeEntries);
        model.set("node", scope.node());
        List<UiOption> kinds = new ArrayList<>();
        for (com.chromecide.lowtalk.editor.AddMenu.Item i : com.chromecide.lowtalk.editor.AddMenu.items()) kinds.add(addMenuOption(i));
        model.set("addKindEntries", kinds);
        model.set("addKind", addKind);
        model.set("crumbs", crumbText());
        revalidate();
        model.set("status", status.isEmpty() ? liveSummary : status);
        model.set("addRowVisible", !header);
        // The creator moves these two without anything telling the model, so they are put back to what the
        // editor believes on every draw, as this page has always done.
        model.resend("node").resend("addKind");
        rows.clear();
        model.setItems("rows", header ? List.of(headerRow()) : scopeRows());
    }

    @Nonnull
    @Override
    protected String commandOf(@Nonnull Data data) {
        return data.action == null ? "" : data.action;
    }

    /** One entry of the Add menu, borrowing its description from the command reference where it has one. */
    private static UiOption addMenuOption(com.chromecide.lowtalk.editor.AddMenu.Item i) {
        UiOption option = UiOption.of(i.value(), i.label());
        if (i.tooltip() != null) return option.explain(i.tooltip());
        if (i.command() != null) {
            Reference.Entry ref = Reference.lookup(i.command());
            if (ref != null) return option.explain(ref.usage() + "  -  " + ref.description());
        }
        return option;
    }

    /**
     * Check the draft as it stands and remember which rows to mark. The validator blames each problem on the
     * statement or option it found it in, so a creator sees a "!" beside the row that needs attention instead of a
     * file and line number they cannot see from in here.
     */
    private void revalidate() {
        flagged.clear();
        liveSummary = "";
        int errors = 0;
        int warnings = 0;
        String first = null;
        try {
            List<Validator.Problem> problems =
                    new Validator(plugin.getEffects().names(), plugin.getFunctions().names()).validate(draft.toDialogue());
            for (Validator.Problem p : problems) {
                if (p.error()) errors++; else warnings++;
                if (first == null || (p.error() && errors == 1)) first = p.message();
                Object subject = p.subject();
                if (subject == null) continue;
                Boolean was = flagged.get(subject);
                if (was == null || (p.error() && !was)) flagged.put(subject, p.error());
            }
        } catch (RuntimeException e) {
            plugin.getLogger().at(Level.FINE).withCause(e).log("dialogue editor: could not check the draft");
            return;
        }
        if (first == null) return;
        String counts = errors > 0 && warnings > 0 ? errors + " to fix, " + warnings + " to look at"
                : errors > 0 ? errors + " to fix"
                : warnings + " to look at";
        liveSummary = counts + " (! on the row means it must be fixed, ? is a warning): " + first;
    }

    /** The mark for a row: "!" when it or anything inside it has an error, "?" for a warning, empty when it is fine. */
    private String mark(Object subject) {
        if (flagged.isEmpty()) return "";
        List<Object> parts = new ArrayList<>();
        collect(subject, parts);
        boolean warned = false;
        for (Object o : parts) {
            Boolean bad = flagged.get(o);
            if (bad == null) continue;
            if (bad) return "!";
            warned = true;
        }
        return warned ? "?" : "";
    }

    /** The mark for exactly one statement or option, ignoring anything written inside it. */
    private String markSelf(Object subject) {
        Boolean bad = flagged.get(subject);
        return bad == null ? "" : bad ? "!" : "?";
    }

    /** Whichever of two marks matters more. */
    private static String worse(String a, String b) {
        if ("!".equals(a) || "!".equals(b)) return "!";
        return a.isEmpty() ? b : a;
    }

    /** The mark for a row that stands for a block of statements, such as one branch of an if. */
    private String markBody(List<Statement> body) {
        boolean warned = false;
        for (Statement s : body) {
            String m = mark(s);
            if ("!".equals(m)) return m;
            if (!m.isEmpty()) warned = true;
        }
        return warned ? "?" : "";
    }

    /** A statement or option and everything written inside it. */
    private static void collect(Object subject, List<Object> out) {
        out.add(subject);
        switch (subject) {
            case Option o -> o.body().forEach(x -> collect(x, out));
            case Statement.Choice c -> c.options().forEach(o -> collect(o, out));
            case Statement.Conditional c -> c.branches().forEach(b -> b.body().forEach(x -> collect(x, out)));
            case Statement.Once o -> o.body().forEach(x -> collect(x, out));
            case Statement.Random r -> r.alternatives().forEach(a -> a.forEach(x -> collect(x, out)));
            default -> {}
        }
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

    private UiItem headerRow() {
        String bindings = String.join(", ", draft.bindings());
        if ("none".equals(draft.directive("npc"))) bindings = "none";
        List<UiOption> npcs = new ArrayList<>();
        npcs.add(UiOption.of(NONE, "+ add an NPC or tag..."));
        for (String n : JsonDialogues.names(JsonDialogues.DATASET_NPCS)) npcs.add(UiOption.same(n));
        List<UiOption> nodes = new ArrayList<>();
        for (String n : draft.nodeNames()) nodes.add(UiOption.same(n));
        List<UiOption> on = List.of(
                UiOption.of(NONE, "when the player talks to the NPC"),
                UiOption.of("join", "when a player joins the world (no NPC)"));
        List<UiOption> layouts = List.of(
                UiOption.of(NONE, "default (" + plugin.getPresentation().explainLayoutDefault(draft.id()) + ")"),
                UiOption.of("bottom", "bottom bar (NPC stays visible)"),
                UiOption.of("top", "top bar"),
                UiOption.of("window", "window (centred, dimmed screen)"));
        String l = draft.directive("layout");
        List<UiOption> histories = List.of(
                UiOption.of(NONE, "default (" + plugin.getPresentation().explainHistoryDefault(draft.id()) + ")"),
                UiOption.of("full", "full transcript"),
                UiOption.of("latest", "latest line only"));
        String h = draft.directive("history");
        return UiItem.of(EditorLayout.HEADER)
                .with("bindings", bindings)
                .with("npcEntries", (Object) npcs)
                .with("npcPick", NONE)
                .with("speaker", nz(draft.speaker()))
                .with("dialogTitle", nz(draft.title()))
                .with("startEntries", (Object) nodes)
                .with("start", draft.startNode())
                .with("onEntries", (Object) on)
                .with("on", draft.directive("on") == null ? NONE : draft.directive("on"))
                .with("portrait", nz(draft.directive("portrait")))
                .with("scope", draft.scope() == null || draft.scope().equals(draft.id()) ? "" : draft.scope())
                .with("layoutEntries", (Object) layouts)
                .with("layout", l == null || !com.chromecide.lowtalk.hytale.presentation.DialogueLayout.isValid(l) ? NONE : l.trim().toLowerCase())
                .with("historyEntries", (Object) histories)
                .with("history", h == null || !com.chromecide.lowtalk.hytale.presentation.History.isValid(h) ? NONE : h.trim().toLowerCase());
    }

    /**
     * The rows of the scope being shown, and alongside them the record of what each one refers to. The two lists
     * stay in step because an event comes back carrying only a row number.
     */
    private List<UiItem> scopeRows() {
        List<UiItem> out = new ArrayList<>();
        List<Statement> body = draft.view(scope);
        for (int i = 0; i < body.size(); i++) {
            Statement s = body.get(i);
            switch (s) {
                case Statement.Line l -> {
                    out.add(UiItem.of(EditorLayout.LINE)
                            .with("bad", mark(l))
                            .with("speaker", nz(l.speaker()))
                            .with("text", Printer.text(l.text()))
                            .with("button", nz(l.button())));
                    rows.add(new RowRef(i, -1));
                }
                case Statement.Choice c -> {
                    for (int o = 0; o < c.options().size(); o++) {
                        Option opt = c.options().get(o);
                        out.add(UiItem.of(EditorLayout.OPTION)
                                .with("bad", worse(mark(opt), markSelf(c)))
                                .with("label", Printer.text(opt.text()))
                                .with("targetEntries", (Object) targetEntries(opt))
                                .with("target", DialogueDraft.optionTarget(opt)));
                        rows.add(new RowRef(i, o));
                        if (expanded.contains(i + ":" + o)) {
                            out.add(conditionOn(condRow(COND_IF), opt.guard(), "only if"));
                            rows.add(new RowRef(i, o));
                            out.add(conditionOn(condRow(COND_SHOW), opt.showGuard(), "grey unless"));
                            rows.add(new RowRef(i, o));
                            out.add(UiItem.of(EditorLayout.OPTION_COND)
                                    .with("once", opt.once() ? "once: yes" : "once: no"));
                            rows.add(new RowRef(i, o));
                        }
                    }
                }
                case Statement.Command c -> {
                    out.add(commandOn(UiItem.of(EditorLayout.COMMAND)
                            .with("bad", mark(c))
                            .with("name", c.name()), c));
                    rows.add(new RowRef(i, -1));
                }
                case Statement.Set st -> {
                    out.add(UiItem.of(EditorLayout.SET)
                            .with("bad", mark(st))
                            .with("var", Printer.expr(st.target()))
                            .with("value", Printer.expr(st.value())));
                    rows.add(new RowRef(i, -1));
                }
                case Statement.Jump j -> {
                    List<UiOption> jumpNodes = new ArrayList<>();
                    for (String n : draft.nodeNames()) jumpNodes.add(UiOption.same(n));
                    out.add(UiItem.of(EditorLayout.JUMP)
                            .with("bad", mark(j))
                            .with("nodeEntries", (Object) jumpNodes)
                            .with("node", j.node()));
                    rows.add(new RowRef(i, -1));
                }
                case Statement.Input in -> {
                    out.add(UiItem.of(EditorLayout.INPUT)
                            .with("bad", mark(in))
                            .with("var", Printer.expr(in.target()))
                            .with("prompt", Printer.text(in.prompt()))
                            .with("kindEntries", (Object) List.of(
                                    UiOption.of("text", "text").explain("Anything the player types."),
                                    UiOption.of("number", "number").explain("The box comes back until they type a number.")))
                            .with("kind", in.kind() == Statement.InputKind.NUMBER ? "number" : "text"));
                    rows.add(new RowRef(i, -1));
                }
                case Statement.Wait w -> {
                    out.add(UiItem.of(EditorLayout.WAIT)
                            .with("bad", mark(w))
                            .with("seconds", Printer.expr(w.seconds())));
                    rows.add(new RowRef(i, -1));
                }
                case Statement.End e -> {
                    out.add(UiItem.of(EditorLayout.BLOCK)
                            .with("bad", mark(e))
                            .with("tag", "end")
                            .with("summary", "the conversation ends here")
                            .with("bodyVisible", (Object) Boolean.FALSE)
                            .with("addAltVisible", (Object) Boolean.FALSE));
                    rows.add(new RowRef(i, -1));
                }
                case Statement.Conditional c -> {
                    for (int b = 0; b < c.branches().size(); b++) {
                        Statement.Branch br = c.branches().get(b);
                        UiItem item = UiItem.of(EditorLayout.BRANCH)
                                .with("slot", String.valueOf(COND_BRANCH))
                                .with("bad", worse(markBody(br.body()), b == 0 ? markSelf(c) : ""))
                                .with("tag", b == 0 ? "if" : br.condition() == null ? "else" : "else if")
                                .with("addBranchVisible", (Object) Boolean.valueOf(b == c.branches().size() - 1))
                                .with("upVisible", (Object) Boolean.valueOf(b == 0))
                                .with("downVisible", (Object) Boolean.valueOf(b == 0));
                        // the last branch of an if may be a plain else, which asks nothing
                        boolean asks = !(b > 0 && br.condition() == null);
                        out.add(asks ? conditionOn(item, br.condition(), "") : hideConditionOn(item));
                        rows.add(new RowRef(i, b));
                    }
                }
                case Statement.Once o -> {
                    out.add(UiItem.of(EditorLayout.BLOCK)
                            .with("bad", mark(o))
                            .with("tag", "once")
                            .with("summary", summary(o.body()))
                            .with("addAltVisible", (Object) Boolean.FALSE));
                    rows.add(new RowRef(i, 0));
                }
                case Statement.Random r -> {
                    for (int a = 0; a < r.alternatives().size(); a++) {
                        out.add(UiItem.of(EditorLayout.ALTERNATIVE)
                                .with("bad", worse(markBody(r.alternatives().get(a)), a == 0 ? markSelf(r) : ""))
                                .with("tag", a == 0 ? "random" : "or")
                                .with("summary", summary(r.alternatives().get(a)))
                                .with("addAltVisible", (Object) Boolean.valueOf(a == r.alternatives().size() - 1))
                                .with("upVisible", (Object) Boolean.valueOf(a == 0))
                                .with("downVisible", (Object) Boolean.valueOf(a == 0)));
                        rows.add(new RowRef(i, a));
                    }
                }
            }
        }
        return out;
    }

    /** A condition row knows which of a statement's conditions it is about; its events carry that back. */
    private static UiItem condRow(int slot) {
        return UiItem.of(EditorLayout.COND).with("slot", String.valueOf(slot));
    }

    /**
     * A command row: the command's name, then one named field per argument it takes, so that a creator reading the
     * row can see that the number after a particle is its scale and the one after that is a count of seconds. A
     * command with no argument names, or one whose arguments do not fit them, keeps the plain box of text this row
     * used to be, with the picker it used to have.
     */
    private UiItem commandOn(UiItem item, Statement.Command c) {
        CommandSpecs.Spec spec = CommandSpecs.of(c.name());
        List<String> values = CommandSpecs.values(c);
        int slots = values == null ? 0 : spec.size();
        for (int slot = 0; slot < EditorLayout.ARG_SLOTS; slot++) {
            item = item.with("arg" + slot + "Visible", (Object) Boolean.valueOf(slot < slots));
            if (slot < slots) item = argOn(item, slot, spec, values);
        }
        boolean plain = values == null;
        item = item.with("argsVisible", (Object) Boolean.valueOf(plain))
                // Nothing else in this row stretches, so without a spacer the row buttons sit next to the command
                // name rather than at the right edge with every other row's.
                .with("spacerVisible", (Object) Boolean.valueOf(!plain && slots == 0));
        if (plain) item = item.with("args", DialogueDraft.argsText(c));
        // the row-level picker belongs to the plain text box, which is all a command with no named arguments has
        return legacyPickOn(item, c, plain && spec == null);
    }

    /**
     * One named argument. A short list is simply chosen from, with no text box at all: there is nothing to type
     * when the game has thirty particles. A long one (there are thousands of items and sounds) keeps a text box,
     * because typing into it is what narrows the list beside it, and so does a value that is not a plain id, such
     * as one built from a variable.
     */
    private UiItem argOn(UiItem item, int slot, CommandSpecs.Spec spec, List<String> values) {
        CommandSpecs.Arg a = spec.arg(slot);
        return fieldOn(item.with("arg" + slot + "Label", a.label()), "arg" + slot,
                a, CommandSpecs.datasetFor(spec, slot, values), values.get(slot));
    }

    /**
     * One value, however it is best chosen: a text box, a list, or a button that opens the picker page. A fixed
     * set of words is a list; an id is a list when the game's list is short enough to hand over and a button when
     * it runs to thousands; a value that is not on its list, such as one built from a variable, keeps a text box
     * so it can still be read and edited.
     */
    private UiItem fieldOn(UiItem item, String prefix, CommandSpecs.Arg a, @Nullable String dataset, String current) {
        boolean choice = a.type() == CommandSpecs.Type.CHOICE;
        List<String> all = choice ? a.choices() : dataset == null ? List.of() : JsonDialogues.names(dataset);
        boolean tooLong = !choice && !a.open() && all.size() > WHOLE_LIST_MAX;
        boolean list = (choice || dataset != null) && !tooLong;
        boolean known = !all.isEmpty() && (current.isEmpty() || contains(all, current));
        boolean box = a.open() || !(list && known) && !(tooLong && known);
        item = item.with(prefix + "BoxVisible", (Object) Boolean.valueOf(box))
                .with(prefix + "ListVisible", (Object) Boolean.valueOf(list))
                .with(prefix + "ButtonVisible", (Object) Boolean.valueOf(tooLong));
        if (box) item = item.with(prefix + "Box", current);
        if (tooLong) item = item.with(prefix + "Button", current.isEmpty() ? "choose " + a.label() + "..." : current);
        if (!list) return item;
        List<String> offer = choice || !box ? all : JsonDialogues.names(dataset, current, PICK_LIMIT);
        return item.with(prefix + "Entries", (Object) listEntries(a, dataset, current, offer, all.size(), !box))
                .with(prefix + "List", box ? NONE : chosen(current, offer));
    }

    /**
     * A condition as fields: what it is about, its own argument, a comparison and a value. Most conditions people
     * write are one of a handful of shapes, and those are shown as lists to choose from. One that is not, with an
     * "and" or an "or" in it, keeps its text box, which is what every condition used to be.
     */
    private UiItem conditionOn(UiItem item, @Nullable com.chromecide.lowtalk.model.Expr cond, String tag) {
        if (!tag.isEmpty()) item = item.with("tag", tag);
        ConditionShapes.Shape shape = ConditionShapes.read(cond);
        boolean byHand = cond != null && shape == null;
        List<UiOption> kinds = new ArrayList<>();
        kinds.add(UiOption.of(NONE, "(no condition)").explain("always shown"));
        for (ConditionShapes.Kind k : ConditionShapes.Kind.values()) kinds.add(UiOption.of(k.name(), k.label()));
        kinds.add(UiOption.of(RAW, byHand ? "(written by hand)" : "(write it by hand)")
                .explain("for conditions with and, or, brackets or arithmetic in them"));
        boolean compares = shape != null && ConditionShapes.comparesWithValue(shape.op()) && shape.kind().value() != null;
        item = item.with("kindVisible", (Object) Boolean.TRUE)
                .with("kindEntries", (Object) kinds)
                .with("kind", byHand ? RAW : shape == null ? NONE : shape.kind().name())
                .with("rawVisible", (Object) Boolean.valueOf(byHand))
                .with("argVisible", (Object) Boolean.valueOf(shape != null && shape.kind().argument() != null))
                .with("opVisible", (Object) Boolean.valueOf(shape != null))
                .with("valVisible", (Object) Boolean.valueOf(compares));
        if (byHand) return item.with("raw", Printer.expr(cond));
        if (shape == null) return item;
        CommandSpecs.Arg argSpec = local(shape.kind().argument(), shape.kind());
        if (argSpec != null) item = fieldOn(item, "arg", argSpec, argSpec.dataset(), shape.arg());
        List<UiOption> ops = new ArrayList<>();
        for (String[] o : ConditionShapes.comparisons(shape.kind())) ops.add(UiOption.of(o[0].isEmpty() ? OP_YES : o[0], o[1]));
        item = item.with("opEntries", (Object) ops).with("op", shape.op().isEmpty() ? OP_YES : shape.op());
        if (!compares) return item;
        return fieldOn(item, "val", shape.kind().value(), shape.kind().value().dataset(), shape.value());
    }

    /** An else with no condition of its own: nothing to show. */
    private static UiItem hideConditionOn(UiItem item) {
        return item.with("kindVisible", (Object) Boolean.FALSE)
                .with("argVisible", (Object) Boolean.FALSE)
                .with("opVisible", (Object) Boolean.FALSE)
                .with("valVisible", (Object) Boolean.FALSE)
                .with("rawVisible", (Object) Boolean.FALSE);
    }

    /** Two of the condition arguments are lists of this dialogue's own: its variables and its passages. */
    @Nullable
    private CommandSpecs.Arg local(@Nullable CommandSpecs.Arg a, ConditionShapes.Kind kind) {
        if (a == null) return null;
        return switch (kind) {
            case VARIABLE -> new CommandSpecs.Arg(a.label(), a.type(), null, variablesInUse(), a.optional(), true);
            case VISITED -> new CommandSpecs.Arg(a.label(), a.type(), null, draft.nodeNames(), a.optional(), false);
            default -> a;
        };
    }

    /** The condition a row's events are about. */
    @Nullable
    private com.chromecide.lowtalk.model.Expr conditionAt(RowRef r, int slot) {
        Statement s = draft.view(scope).get(r.statement());
        if (slot == COND_BRANCH) {
            return s instanceof Statement.Conditional c && r.sub() >= 0 && r.sub() < c.branches().size()
                    ? c.branches().get(r.sub()).condition() : null;
        }
        if (!(s instanceof Statement.Choice c) || r.sub() < 0 || r.sub() >= c.options().size()) return null;
        Option o = c.options().get(r.sub());
        return slot == COND_SHOW ? o.showGuard() : o.guard();
    }

    /** Write a condition back as text; returns what was wrong with it, or null. */
    @Nullable
    private String setCondition(RowRef r, int slot, String text) {
        return switch (slot) {
            case COND_BRANCH -> draft.setBranchCondition(scope, r.statement(), r.sub(), text);
            case COND_SHOW -> draft.setOptionShowGuard(scope, r.statement(), r.sub(), text);
            default -> draft.setOptionGuard(scope, r.statement(), r.sub(), text);
        };
    }

    /** Change one part of a condition and write the whole thing back. */
    private boolean editCondition(RowRef r, int slot, java.util.function.UnaryOperator<ConditionShapes.Shape> change) {
        ConditionShapes.Shape shape = ConditionShapes.read(conditionAt(r, slot));
        if (shape == null) return false;
        ConditionShapes.Shape next = change.apply(shape);
        problem(setCondition(r, slot, next == null ? "" : ConditionShapes.write(next)));
        return true;
    }

    private static boolean contains(List<String> all, String value) {
        for (String s : all) if (s.equalsIgnoreCase(value)) return true;
        return false;
    }

    /**
     * Whether an argument is simply chosen from a list, with no text box: every fixed choice, and any of the
     * game's lists short enough to read through, as long as what is written is one of its entries. A value that
     * is not (one built from a variable, or an id from a pack that is not loaded) keeps its text box, so it can
     * still be read and edited.
     */
    private boolean chooseOnly(CommandSpecs.Spec spec, int slot, List<String> values) {
        CommandSpecs.Arg a = spec.arg(slot);
        if (a.type() == CommandSpecs.Type.CHOICE) return true;
        if (a.open()) return false;
        String dataset = CommandSpecs.datasetFor(spec, slot, values);
        if (dataset == null) return false;
        List<String> all = JsonDialogues.names(dataset);
        String current = values.get(slot);
        return !all.isEmpty() && all.size() <= WHOLE_LIST_MAX && (current.isEmpty() || all.contains(current));
    }

    /** The entries of one argument's list, with a first line that says what the list is for. */
    private List<UiOption> listEntries(CommandSpecs.Arg a, @Nullable String dataset, String current,
                                       List<String> offer, int total, boolean chooseOnly) {
        List<UiOption> entries = new ArrayList<>();
        entries.add(UiOption.of(NONE, !chooseOnly ? "or type your own (" + offer.size() + " suggestions)"
                : a.optional() ? "(not set)"
                : "choose a " + a.label() + "..."));
        boolean seen = false;
        for (String o : offer) {
            String note = JsonDialogues.note(dataset, o);
            UiOption option = UiOption.of(o, note.isEmpty() ? o : o + "  -  " + note);
            entries.add(note.isEmpty() ? option : option.explain(note));
            if (o.equalsIgnoreCase(current)) seen = true;
        }
        if (chooseOnly && !current.isEmpty() && !seen) entries.add(UiOption.same(current));
        return entries;
    }

    /** The entry value that stands for what is written, so a list shows the argument's own value as chosen. */
    private static String chosen(String current, List<String> offer) {
        if (current.isEmpty()) return NONE;
        for (String o : offer) if (o.equalsIgnoreCase(current)) return o;
        return current;
    }

    /** The picker beside the plain text box, for a command whose arguments have no names of their own. */
    private UiItem legacyPickOn(UiItem item, Statement.Command c, boolean wanted) {
        String dataset = wanted ? pickerFor(c.name()) : null;
        List<UiOption> picks = new ArrayList<>();
        if (dataset != null) {
            int slot = pickerArg(c.name());
            String typed = slot >= 0 && c.args().size() > slot ? Printer.text(c.args().get(slot)) : "";
            picks.add(UiOption.of(NONE, slot == 0 ? "pick..." : "pick argument " + (slot + 1) + "..."));
            for (String n : JsonDialogues.names(dataset, typed, PICK_LIMIT)) picks.add(UiOption.same(n));
        }
        return item.with("pickEntries", (Object) picks)
                .with("pick", NONE)
                .with("pickVisible", (Object) Boolean.valueOf(dataset != null));
    }

    /**
     * Re-send one argument's list as it is typed into, so a long one narrows without the caret moving.
     *
     * <p>This is the one place that changes a row without redrawing the scope: every keystroke would
     * otherwise rebuild and re-send every row on screen.
     */
    private void refreshList(int row, int slot) {
        RowRef r = rowRef(String.valueOf(row));
        if (r == null) return;
        Statement s = draft.view(scope).get(r.statement());
        if (!(s instanceof Statement.Command c)) return;
        CommandSpecs.Spec spec = CommandSpecs.of(c.name());
        List<String> values = CommandSpecs.values(c);
        if (spec == null || values == null || slot < 0 || slot >= spec.size()) return;
        if (chooseOnly(spec, slot, values)) return;   // a short list is already there in full
        String dataset = CommandSpecs.datasetFor(spec, slot, values);
        if (dataset == null) return;
        String current = values.get(slot);
        List<String> offer = JsonDialogues.names(dataset, current, PICK_LIMIT);
        model.setItemField("rows", row, "arg" + slot + "Entries",
                listEntries(spec.arg(slot), dataset, current, offer, JsonDialogues.size(dataset), false));
        model.setItemField("rows", row, "arg" + slot + "List", UiText.of(NONE));
        pushChanges();
    }

    /** Write one named argument back, keeping the others where they are. */
    @Nullable
    private String setArg(int statement, int slot, String value) {
        Statement s = draft.view(scope).get(statement);
        if (!(s instanceof Statement.Command c)) return null;
        CommandSpecs.Spec spec = CommandSpecs.of(c.name());
        List<String> values = CommandSpecs.values(c);
        if (spec == null || values == null || slot < 0 || slot >= values.size()) return null;
        values.set(slot, NONE.equals(value) ? "" : value);
        return draft.setCommand(scope, statement, c.name(), CommandSpecs.join(spec, values));
    }

    private static String summary(List<Statement> body) {
        String s = Printer.block(body).strip().replace('\n', ' ');
        return s.length() > 70 ? s.substring(0, 67) + "..." : s;
    }

    private static String nz(@Nullable String s) {
        return s == null ? "" : s;
    }

    private List<UiOption> targetEntries(Option opt) {
        List<UiOption> entries = new ArrayList<>();
        entries.add(UiOption.of(DialogueDraft.TARGET_END, "(end)").explain("closes the window"));
        entries.add(UiOption.of(DialogueDraft.TARGET_CONTINUE, "(back to these options)")
                .explain("shows this passage's options again"));
        for (String n : draft.nodeNames()) entries.add(UiOption.of(n, "-> " + n));
        entries.add(UiOption.of(DialogueDraft.TARGET_NEW, "+ new passage"));
        if (DialogueDraft.TARGET_CUSTOM.equals(DialogueDraft.optionTarget(opt))) {
            entries.add(UiOption.of(DialogueDraft.TARGET_CUSTOM, "(custom)").explain(summary(opt.body())));
        }
        return entries;
    }

    /** Variables this dialogue already sets, offered when a condition is about a variable. */
    private List<String> variablesInUse() {
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        for (String node : draft.nodeNames()) {
            List<Object> all = new ArrayList<>();
            for (Statement s : draft.view(Scope.node(node))) collect(s, all);
            for (Object o : all) if (o instanceof Statement.Set set) names.add(Printer.expr(set.target()));
        }
        return names.size() <= 8 ? new ArrayList<>(names) : new ArrayList<>(names).subList(0, 8);
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
    protected void onCommand(@Nonnull String command, @Nonnull Data data,
                             @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        Action action;
        try {
            action = Action.valueOf(data.action);
        } catch (IllegalArgumentException e) {
            return;
        }
        RowRef r = rowRef(data.row);
        int row = number(data.row);
        int slot = number(data.slot);
        String value = data.value == null ? "" : data.value;
        String value2 = data.value2 == null ? "" : data.value2;
        try {
            boolean redraw = handle(action, r, row, slot, value, value2, ref, store);
            if (redraw) refresh();
        } catch (RuntimeException e) {
            plugin.getLogger().at(Level.WARNING).withCause(e).log("dialogue editor: %s failed", action);
            status = "Something went wrong: " + e.getMessage();
            refresh();
        }
    }

    /** A number sent with an event, or -1 when it was absent or malformed. */
    private static int number(@Nullable String s) {
        try {
            return s == null ? -1 : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Apply one event; returns true when the window must be redrawn. */
    private boolean handle(Action action, @Nullable RowRef r, int row, int slot, String value, String value2,
                           Ref<EntityStore> ref, Store<EntityStore> store) {
        switch (action) {
            // typing into fields changes the draft without redrawing, so the caret stays where it is
            case LINE_TEXT -> { if (r != null) draft.setLineText(scope, r.statement(), value); return false; }
            case LINE_SPEAKER -> { if (r != null) draft.setLineSpeaker(scope, r.statement(), value); return false; }
            case LINE_BUTTON -> { if (r != null) draft.setLineButton(scope, r.statement(), value); return false; }
            case OPT_TEXT -> { if (r != null) draft.setOptionText(scope, r.statement(), r.sub(), value); return false; }
            case OPT_IF -> { if (r != null) return problem(draft.setOptionGuard(scope, r.statement(), r.sub(), value)); return false; }
            case OPT_SHOW -> { if (r != null) return problem(draft.setOptionShowGuard(scope, r.statement(), r.sub(), value)); return false; }
            case CMD_ARGS -> {
                // the name is no longer a field on the row, so it comes from the statement being edited
                if (r == null) return false;
                Statement s = draft.view(scope).get(r.statement());
                if (!(s instanceof Statement.Command c)) return false;
                return problem(draft.setCommand(scope, r.statement(), c.name(), value));
            }
            case CMD_SLOT -> {
                if (r == null) return false;
                Statement s = draft.view(scope).get(r.statement());
                if (!(s instanceof Statement.Command c)) return false;
                CommandSpecs.Spec spec = CommandSpecs.of(c.name());
                String failed = setArg(r.statement(), slot, value);
                if (failed != null) { status = failed; return true; }
                if (spec == null || slot < 0 || slot >= spec.size()) return false;
                // a fixed choice can change what the rest of the command means, so redraw; typing must not, or the
                // caret would jump, so only the picker beside the field is re-sent
                // a fixed choice can change what the rest of the command means, so redraw; typing must not, or
                // the caret would jump, and it no longer has to: the list beside it searches itself
                if (spec.arg(slot).type() == CommandSpecs.Type.CHOICE) return true;
                if (spec.arg(slot).type() == CommandSpecs.Type.ASSET) refreshList(row, slot);
                return false;
            }
            case COND_KIND -> {
                if (r == null) return false;
                if (NONE.equals(value)) { problem(setCondition(r, slot, "")); return true; }
                if (RAW.equals(value)) {
                    // keep whatever is there; the text box simply takes over from the fields
                    com.chromecide.lowtalk.model.Expr had = conditionAt(r, slot);
                    ConditionShapes.Shape shape = ConditionShapes.read(had);
                    if (shape != null) problem(setCondition(r, slot, "(" + ConditionShapes.write(shape) + ")"));
                    else if (had == null) problem(setCondition(r, slot, "$flag"));
                    return true;
                }
                ConditionShapes.Kind kind;
                try {
                    kind = ConditionShapes.Kind.valueOf(value);
                } catch (IllegalArgumentException e) {
                    return false;
                }
                String op = kind.isYesOrNo() ? "" : "==";
                problem(setCondition(r, slot, ConditionShapes.write(new ConditionShapes.Shape(kind, "", op, ""))));
                return true;
            }
            case COND_ARG, COND_ARG_PICK -> {
                if (r == null || NONE.equals(value)) return false;
                boolean shown = editCondition(r, slot, sh -> new ConditionShapes.Shape(sh.kind(), value, sh.op(), sh.value()));
                return shown && action != Action.COND_ARG;
            }
            case COND_VAL, COND_VAL_PICK -> {
                if (r == null || NONE.equals(value)) return false;
                boolean shown = editCondition(r, slot, sh -> new ConditionShapes.Shape(sh.kind(), sh.arg(), sh.op(), value));
                return shown && action != Action.COND_VAL;
            }
            case COND_OP -> {
                if (r == null) return false;
                String op = OP_YES.equals(value) ? "" : value;
                editCondition(r, slot, sh -> new ConditionShapes.Shape(sh.kind(), sh.arg(), op,
                        ConditionShapes.comparesWithValue(op) ? sh.value() : ""));
                return true;
            }
            case COND_RAW -> { if (r != null) problem(setCondition(r, slot, value)); return false; }
            case COND_ARG_FIND, COND_VAL_FIND -> {
                if (r == null) return false;
                ConditionShapes.Shape shape = ConditionShapes.read(conditionAt(r, slot));
                if (shape == null) return false;
                boolean forValue = action == Action.COND_VAL_FIND;
                CommandSpecs.Arg a = forValue ? shape.kind().value() : shape.kind().argument();
                if (a == null || a.dataset() == null) return false;
                int statement = r.statement();
                int sub = r.sub();
                PickerPage.open(plugin, playerRef, ref, store, "Choose " + (forValue ? "a value" : "the " + a.label()),
                        a.dataset(), forValue ? shape.value() : shape.arg(),
                        picked -> editCondition(new RowRef(statement, sub), slot, sh -> forValue
                                ? new ConditionShapes.Shape(sh.kind(), sh.arg(), sh.op(), picked)
                                : new ConditionShapes.Shape(sh.kind(), picked, sh.op(), sh.value())),
                        this);
                return false;
            }
            case CMD_SLOT_FIND -> {
                if (r == null) return false;
                Statement s = draft.view(scope).get(r.statement());
                if (!(s instanceof Statement.Command c)) return false;
                CommandSpecs.Spec spec = CommandSpecs.of(c.name());
                List<String> values = CommandSpecs.values(c);
                if (spec == null || values == null || slot < 0 || slot >= spec.size()) return false;
                String dataset = CommandSpecs.datasetFor(spec, slot, values);
                if (dataset == null) return false;
                int statement = r.statement();
                PickerPage.open(plugin, playerRef, ref, store,
                        "Choose the " + spec.arg(slot).label() + " for <<" + c.name() + ">>", dataset, values.get(slot),
                        picked -> problem(setArg(statement, slot, picked)), this);
                return false;
            }
            case CMD_SLOT_PICK -> {
                if (r == null) return false;
                Statement s = draft.view(scope).get(r.statement());
                if (!(s instanceof Statement.Command c)) return false;
                CommandSpecs.Spec spec = CommandSpecs.of(c.name());
                if (spec == null || slot < 0 || slot >= spec.size()) return false;
                List<String> before = CommandSpecs.values(c);
                // "(not set)" at the top of a short list clears the argument; "type to narrow..." at the top of a
                // long one is not a value at all, so choosing it leaves what is written alone
                if (NONE.equals(value) && (before == null || !chooseOnly(spec, slot, before))) return false;
                String failed = setArg(r.statement(), slot, value);
                if (failed != null) status = failed;
                return true;
            }
            case SET -> { if (r != null) return problem(draft.setSet(scope, r.statement(), value, value2)); return false; }
            case INPUT -> { if (r != null) return problem(draft.setInput(scope, r.statement(), value, value2)); return false; }
            case INPUT_KIND -> { if (r != null) return problem(draft.setInputKind(scope, r.statement(), value)); return false; }
            case WAIT -> { if (r != null) return problem(draft.setWait(scope, r.statement(), value)); return false; }
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
                if (!(s instanceof Statement.Command c)) return false;
                CommandSpecs.Spec spec = CommandSpecs.of(c.name());
                if (spec != null && CommandSpecs.values(c) != null) problem(setArg(r.statement(), CommandSpecs.assetSlot(spec), value));
                else draft.setCommandArg(scope, r.statement(), Math.max(0, pickerArg(c.name())), value);
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
                String chosen = value.isEmpty() ? addKind : value;
                int colon = chosen.indexOf(':');
                String command = colon < 0 ? null : chosen.substring(colon + 1);
                Kind kind;
                try {
                    kind = Kind.valueOf(colon < 0 ? chosen : chosen.substring(0, colon));
                } catch (IllegalArgumentException e) {
                    return false;
                }
                addKind = chosen;
                if (kind == Kind.OPTION) {
                    int count = 0;
                    for (Statement s : draft.view(scope)) if (s instanceof Statement.Choice c) count += c.options().size();
                    if (count >= DialoguePage.OPTION_SLOTS) { status = "A passage can show at most " + DialoguePage.OPTION_SLOTS + " options."; return true; }
                }
                int added = draft.add(scope, kind);
                if (command != null) draft.setCommand(scope, added, command, CommandSpecs.defaults(command));
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
            case H_HISTORY -> { draft.setDirective("history", NONE.equals(value) ? null : value); return false; }
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
        boolean dropped = loaded == null;
        if (dropped) loaded = origin;   // the registry lost it, most likely a reload found a problem: write it back anyway
        if (loaded == null) { status = "This dialogue has nowhere to save to; copy your text out before closing."; return; }
        List<Validator.Problem> problems = new Validator(plugin.getEffects().names(), plugin.getFunctions().names()).validate(d);
        for (Validator.Problem p : problems) {
            if (p.error()) { status = "Not saved: " + p.message(); return; }
        }
        String warning = problems.stream().filter(p -> !p.error()).map(Validator.Problem::message).findFirst().orElse(null);
        String recovered = dropped ? "The dialogue had dropped off the list; wrote it back to " + loaded.display() + ". " : "";
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
                status = recovered + (report.ok()
                        ? "Saved " + loaded.display() + (hadComments ? ". Comments in the file were dropped (the editor rewrites it)." : ".")
                        + (report.warnings() > 0 ? " " + report.warnings() + " warning(s): " + String.join(" ", report.messages()) : "")
                        : "Saved, but the file did not load: " + String.join(" ", report.messages()));
            } else {
                Path json = registry.findAssetFile(d.id() + ".json");
                if (json == null) { status = "Could not find " + d.id() + ".json to write to."; return; }
                com.chromecide.lowtalk.hytale.json.LowTalkJson asset = com.chromecide.lowtalk.hytale.json.JsonConvert.toAsset(d);
                asset.setId(d.id());
                String out = com.chromecide.lowtalk.hytale.json.JsonCodecs.DIALOGUE
                        .encode(asset, com.hypixel.hytale.codec.EmptyExtraInfo.EMPTY).asDocument()
                        .toJson(org.bson.json.JsonWriterSettings.builder().indent(true).build());
                Files.writeString(json, out + "\n", StandardCharsets.UTF_8);
                // not through the asset store: writing to one takes a lock the world thread cannot wait for
                registry.loadAsset(d.id(), json.getFileName().toString(), d, registry.packNameFor(json), true);
                status = recovered + "Saved " + json.getFileName() + "."
                        + (warning != null ? " Warning: " + warning : "");
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
        // The dialogue window replaces this page directly. Closing first would make the game wait for the client's
        // acknowledgement of the close, and it drops every event from the next page until that arrives.
        world.execute(() -> {
            if (!ref.isValid()) return;
            NpcInfo npc = new NpcInfo(null, npcId, d.bindings().isEmpty() ? null : d.bindings().get(0), npcName, java.util.Set.of());
            DialogueSession s = plugin.getSessions().openAt(d, startAt, playerRef, ref, store, world, npc, com.chromecide.lowtalk.api.Opener.COMMAND);
            if (s == null) { close(); playerRef.sendMessage(LowTalkCommand.msg(plugin, "endedAtOnce").param("passage", startAt)); }
        });
    }
}
