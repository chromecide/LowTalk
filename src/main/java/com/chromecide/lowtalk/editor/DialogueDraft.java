package com.chromecide.lowtalk.editor;

import com.chromecide.lowtalk.model.Dialogue;
import com.chromecide.lowtalk.model.Node;
import com.chromecide.lowtalk.model.Option;
import com.chromecide.lowtalk.model.Pos;
import com.chromecide.lowtalk.model.Statement;
import com.chromecide.lowtalk.model.Text;
import com.chromecide.lowtalk.parser.ParseException;
import com.chromecide.lowtalk.parser.TextParser;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * A dialogue being edited in place: the same model the runtime uses, with mutable node bodies and the handful of
 * operations the in-game editor offers (change a line, add or retarget an option, move, delete, new node, rename).
 * {@link #toDialogue()} produces a normal {@link Dialogue} again, which the printer turns back into a .talk file.
 * Pure model code, no Hytale types, so it is unit-testable.
 */
public final class DialogueDraft {
    /** Where an option leads, as the editor shows it. */
    public static final String TARGET_END = "$end";
    public static final String TARGET_CONTINUE = "$continue";
    public static final String TARGET_CUSTOM = "$custom";
    public static final String TARGET_NEW = "$new";

    private final Dialogue base;
    private final LinkedHashMap<String, List<Statement>> nodes = new LinkedHashMap<>();
    private List<Dialogue.Start> starts;
    private boolean dirty = false;

    public DialogueDraft(@Nonnull Dialogue base) {
        this.base = base;
        for (Node n : base.nodeList()) nodes.put(n.name(), copyBody(n.body()));
        this.starts = new ArrayList<>(base.starts());
    }

    public String id() { return base.id(); }
    public Dialogue base() { return base; }
    public boolean isDirty() { return dirty; }
    public List<String> nodeNames() { return new ArrayList<>(nodes.keySet()); }
    public boolean hasNode(String name) { return nodes.containsKey(name); }

    /** The node a fresh conversation would start in (the unguarded start, else the first node). */
    public String startNode() {
        for (Dialogue.Start s : starts) if (s.condition() == null) return nodes.containsKey(s.node()) ? s.node() : firstNode();
        return firstNode();
    }

    private String firstNode() {
        return nodes.isEmpty() ? null : nodes.keySet().iterator().next();
    }

    /** The statements of a node; the live list, so callers must not keep it across edits. */
    public List<Statement> body(String node) {
        List<Statement> b = nodes.get(node);
        if (b == null) throw new IllegalArgumentException("no node " + node);
        return b;
    }

    // ---- lines

    public void setLineText(String node, int index, String raw) {
        Statement s = body(node).get(index);
        if (!(s instanceof Statement.Line l)) return;
        body(node).set(index, new Statement.Line(l.pos(), l.speaker(), parseText(raw, l.pos())));
        dirty = true;
    }

    public void setLineSpeaker(String node, int index, @Nullable String speaker) {
        Statement s = body(node).get(index);
        if (!(s instanceof Statement.Line l)) return;
        String sp = speaker == null || speaker.isBlank() ? null : speaker.trim();
        body(node).set(index, new Statement.Line(l.pos(), sp, l.text()));
        dirty = true;
    }

    /** Add an empty line. It goes before the node's choice if the node ends in one, so it is spoken before the options. */
    public int addLine(String node) {
        List<Statement> b = body(node);
        int at = b.size();
        if (at > 0 && b.get(at - 1) instanceof Statement.Choice) at--;
        b.add(at, new Statement.Line(pos(), null, Text.plain("")));
        dirty = true;
        return at;
    }

    // ---- options

    /** Index of the choice statement options are added to (the last one), or -1. */
    public int choiceIndex(String node) {
        List<Statement> b = body(node);
        for (int i = b.size() - 1; i >= 0; i--) if (b.get(i) instanceof Statement.Choice) return i;
        return -1;
    }

    public List<Option> options(String node, int choiceIndex) {
        return ((Statement.Choice) body(node).get(choiceIndex)).options();
    }

    /** Add an option that ends the conversation; returns the choice statement's index. */
    public int addOption(String node, String label) {
        List<Statement> b = body(node);
        int ci = choiceIndex(node);
        Option opt = new Option(pos(), Text.plain(label), null, null, false, List.of(new Statement.End(pos())));
        if (ci < 0) {
            b.add(new Statement.Choice(pos(), List.of(opt)));
            ci = b.size() - 1;
        } else {
            Statement.Choice c = (Statement.Choice) b.get(ci);
            List<Option> opts = new ArrayList<>(c.options());
            opts.add(opt);
            b.set(ci, new Statement.Choice(c.pos(), List.copyOf(opts)));
        }
        dirty = true;
        return ci;
    }

    public void setOptionText(String node, int choiceIndex, int optionIndex, String raw) {
        replaceOption(node, choiceIndex, optionIndex, o -> new Option(o.pos(), parseText(raw, o.pos()), o.guard(), o.showGuard(), o.once(), o.body()));
    }

    /** What an option does when picked: a node name, {@link #TARGET_END}, {@link #TARGET_CONTINUE} or {@link #TARGET_CUSTOM}. */
    public static String optionTarget(Option o) {
        List<Statement> b = o.body();
        if (b.isEmpty()) return TARGET_CONTINUE;
        if (b.size() == 1 && b.get(0) instanceof Statement.End) return TARGET_END;
        if (b.size() == 1 && b.get(0) instanceof Statement.Jump j) return j.node();
        return TARGET_CUSTOM;
    }

    /**
     * Point an option at a node, at the end, or at "continue after the options". {@link #TARGET_NEW} creates a node
     * first. Returns the node the option now leads to, or null. A custom body is only replaced when asked for
     * something else, so {@link #TARGET_CUSTOM} is a no-op.
     */
    @Nullable
    public String setOptionTarget(String node, int choiceIndex, int optionIndex, String target) {
        if (TARGET_CUSTOM.equals(target)) return null;
        String lead = null;
        List<Statement> body;
        if (TARGET_END.equals(target)) body = List.of(new Statement.End(pos()));
        else if (TARGET_CONTINUE.equals(target)) body = List.of();
        else {
            lead = TARGET_NEW.equals(target) ? newNode() : target;
            if (!nodes.containsKey(lead)) return null;
            body = List.of(new Statement.Jump(pos(), lead));
        }
        List<Statement> b = body;
        replaceOption(node, choiceIndex, optionIndex, o -> new Option(o.pos(), o.text(), o.guard(), o.showGuard(), o.once(), b));
        return lead;
    }

    public void moveOption(String node, int choiceIndex, int optionIndex, int delta) {
        Statement.Choice c = (Statement.Choice) body(node).get(choiceIndex);
        List<Option> opts = new ArrayList<>(c.options());
        int to = optionIndex + delta;
        if (to < 0 || to >= opts.size()) return;
        Option o = opts.remove(optionIndex);
        opts.add(to, o);
        body(node).set(choiceIndex, new Statement.Choice(c.pos(), List.copyOf(opts)));
        dirty = true;
    }

    /** Remove an option; a choice left with no options is removed too. */
    public void deleteOption(String node, int choiceIndex, int optionIndex) {
        Statement.Choice c = (Statement.Choice) body(node).get(choiceIndex);
        List<Option> opts = new ArrayList<>(c.options());
        if (optionIndex < 0 || optionIndex >= opts.size()) return;
        opts.remove(optionIndex);
        if (opts.isEmpty()) body(node).remove(choiceIndex);
        else body(node).set(choiceIndex, new Statement.Choice(c.pos(), List.copyOf(opts)));
        dirty = true;
    }

    private void replaceOption(String node, int choiceIndex, int optionIndex, UnaryOperator<Option> f) {
        Statement.Choice c = (Statement.Choice) body(node).get(choiceIndex);
        List<Option> opts = new ArrayList<>(c.options());
        if (optionIndex < 0 || optionIndex >= opts.size()) return;
        opts.set(optionIndex, f.apply(opts.get(optionIndex)));
        body(node).set(choiceIndex, new Statement.Choice(c.pos(), List.copyOf(opts)));
        dirty = true;
    }

    // ---- statements

    public void moveStatement(String node, int index, int delta) {
        List<Statement> b = body(node);
        int to = index + delta;
        if (index < 0 || index >= b.size() || to < 0 || to >= b.size()) return;
        Statement s = b.remove(index);
        b.add(to, s);
        dirty = true;
    }

    public void deleteStatement(String node, int index) {
        List<Statement> b = body(node);
        if (index < 0 || index >= b.size()) return;
        b.remove(index);
        dirty = true;
    }

    // ---- nodes

    /** Create an empty node with a fresh name and return the name. */
    public String newNode() {
        int n = nodes.size() + 1;
        String name;
        do {
            name = "node_" + n++;
        } while (nodes.containsKey(name));
        nodes.put(name, new ArrayList<>(List.of(new Statement.Line(pos(), null, Text.plain("")))));
        dirty = true;
        return name;
    }

    /** True if the name is usable as a node name: letters, digits, underscore and dash, and not taken. */
    public boolean canRename(String from, String to) {
        return to != null && to.matches("[A-Za-z_][A-Za-z0-9_-]*") && (to.equals(from) || !nodes.containsKey(to));
    }

    /** Rename a node and every jump and start directive that points at it. */
    public boolean renameNode(String from, String to) {
        if (!nodes.containsKey(from) || !canRename(from, to)) return false;
        if (from.equals(to)) return true;
        LinkedHashMap<String, List<Statement>> renamed = new LinkedHashMap<>();
        for (Map.Entry<String, List<Statement>> e : nodes.entrySet()) {
            renamed.put(e.getKey().equals(from) ? to : e.getKey(), retarget(e.getValue(), from, to));
        }
        nodes.clear();
        nodes.putAll(renamed);
        List<Dialogue.Start> newStarts = new ArrayList<>();
        for (Dialogue.Start s : starts) newStarts.add(s.node().equals(from) ? new Dialogue.Start(s.pos(), to, s.condition()) : s);
        starts = newStarts;
        dirty = true;
        return true;
    }

    /** Remove a node; jumps to it become ends. The last node cannot be removed. */
    public boolean deleteNode(String name) {
        if (nodes.size() <= 1 || !nodes.containsKey(name)) return false;
        nodes.remove(name);
        for (Map.Entry<String, List<Statement>> e : nodes.entrySet()) e.setValue(retarget(e.getValue(), name, null));
        List<Dialogue.Start> newStarts = new ArrayList<>();
        for (Dialogue.Start s : starts) if (!s.node().equals(name)) newStarts.add(s);
        if (newStarts.isEmpty()) newStarts.add(new Dialogue.Start(pos(), firstNode(), null));
        starts = newStarts;
        dirty = true;
        return true;
    }

    /** Nodes that no jump, option or start reaches. */
    public List<String> unreachableNodes() {
        java.util.Set<String> reached = new java.util.HashSet<>();
        for (Dialogue.Start s : starts) reached.add(s.node());
        if (starts.isEmpty() && firstNode() != null) reached.add(firstNode());
        for (List<Statement> b : nodes.values()) collectJumps(b, reached);
        List<String> out = new ArrayList<>();
        for (String n : nodes.keySet()) if (!reached.contains(n)) out.add(n);
        return out;
    }

    // ---- output

    public Dialogue toDialogue() {
        LinkedHashMap<String, Node> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<Statement>> e : nodes.entrySet()) {
            out.put(e.getKey(), new Node(pos(), e.getKey(), List.copyOf(e.getValue())));
        }
        List<Dialogue.Start> st = starts.isEmpty() ? List.of(new Dialogue.Start(pos(), firstNode(), null)) : List.copyOf(starts);
        return new Dialogue(base.file(), base.id(), base.bindings(), st, base.speaker(), base.title(), base.scope(),
                base.otherDirectives(), base.includes(), out);
    }

    // ---- helpers

    private Pos pos() {
        return new Pos(base.file(), 0);
    }

    private static Text parseText(String raw, Pos pos) {
        String s = raw == null ? "" : raw;
        try {
            return TextParser.parse(s, pos);
        } catch (ParseException e) {
            return Text.plain(s); // unbalanced braces or brackets: keep the words, the printer escapes them
        }
    }

    private static List<Statement> copyBody(List<Statement> body) {
        return new ArrayList<>(body);
    }

    /** Copy of a body with jumps to {@code from} pointing at {@code to} (null: replaced by an end). */
    private static List<Statement> retarget(List<Statement> body, String from, @Nullable String to) {
        List<Statement> out = new ArrayList<>(body.size());
        for (Statement s : body) out.add(retarget(s, from, to));
        return out;
    }

    private static Statement retarget(Statement s, String from, @Nullable String to) {
        return switch (s) {
            case Statement.Jump j -> j.node().equals(from) ? (to == null ? new Statement.End(j.pos()) : new Statement.Jump(j.pos(), to)) : j;
            case Statement.Choice c -> {
                List<Option> opts = new ArrayList<>();
                for (Option o : c.options()) opts.add(new Option(o.pos(), o.text(), o.guard(), o.showGuard(), o.once(), retarget(o.body(), from, to)));
                yield new Statement.Choice(c.pos(), List.copyOf(opts));
            }
            case Statement.Conditional c -> {
                List<Statement.Branch> br = new ArrayList<>();
                for (Statement.Branch b : c.branches()) br.add(new Statement.Branch(b.pos(), b.condition(), retarget(b.body(), from, to)));
                yield new Statement.Conditional(c.pos(), List.copyOf(br));
            }
            case Statement.Once o -> new Statement.Once(o.pos(), o.key(), retarget(o.body(), from, to));
            case Statement.Random r -> {
                List<List<Statement>> alts = new ArrayList<>();
                for (List<Statement> a : r.alternatives()) alts.add(retarget(a, from, to));
                yield new Statement.Random(r.pos(), List.copyOf(alts));
            }
            default -> s;
        };
    }

    private static void collectJumps(List<Statement> body, java.util.Set<String> out) {
        for (Statement s : body) {
            switch (s) {
                case Statement.Jump j -> out.add(j.node());
                case Statement.Choice c -> { for (Option o : c.options()) collectJumps(o.body(), out); }
                case Statement.Conditional c -> { for (Statement.Branch b : c.branches()) collectJumps(b.body(), out); }
                case Statement.Once o -> collectJumps(o.body(), out);
                case Statement.Random r -> { for (List<Statement> a : r.alternatives()) collectJumps(a, out); }
                default -> {}
            }
        }
    }
}
