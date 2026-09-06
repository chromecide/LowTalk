package com.chromecide.lowtalk.editor;

import com.chromecide.lowtalk.model.Dialogue;
import com.chromecide.lowtalk.model.Expr;
import com.chromecide.lowtalk.model.Node;
import com.chromecide.lowtalk.model.Option;
import com.chromecide.lowtalk.model.Pos;
import com.chromecide.lowtalk.model.Statement;
import com.chromecide.lowtalk.model.Text;
import com.chromecide.lowtalk.parser.DialogueParser;
import com.chromecide.lowtalk.parser.ExprParser;
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
 * A dialogue being edited in place: the same model the runtime uses, plus the operations the in-game editor offers.
 * Any list of statements the editor can show is a {@link Scope}: a node's body, an option's body, a branch of an
 * if-block, a once-block, or one alternative of a random-block. Edits rebuild the immutable records along the path.
 * {@link #toDialogue()} produces a normal {@link Dialogue} again, which the printer turns back into a .talk file.
 * Pure model code, no Hytale types, so it is unit-testable.
 */
public final class DialogueDraft {
    /** Where an option leads, as the editor shows it. */
    public static final String TARGET_END = "$end";
    public static final String TARGET_CONTINUE = "$continue";
    public static final String TARGET_CUSTOM = "$custom";
    public static final String TARGET_NEW = "$new";

    /** One step down from a statement list into a nested list: option {@code sub} of a choice, branch of an if, alternative of a random; once has one body. */
    public record Step(int statement, int sub) {}

    /** A statement list somewhere in the dialogue: the node's body when {@code path} is empty. */
    public record Scope(String node, List<Step> path) {
        public static Scope node(String node) {
            return new Scope(node, List.of());
        }

        public Scope into(int statement, int sub) {
            List<Step> p = new ArrayList<>(path);
            p.add(new Step(statement, sub));
            return new Scope(node, List.copyOf(p));
        }

        public boolean isRoot() {
            return path.isEmpty();
        }

        public Scope parent() {
            return path.isEmpty() ? this : new Scope(node, List.copyOf(path.subList(0, path.size() - 1)));
        }
    }

    /** Statement kinds the editor can add. */
    public enum Kind { LINE, OPTION, COMMAND, SET, JUMP, END, INPUT, WAIT, IF, ONCE, RANDOM }

    private final Dialogue base;
    private final LinkedHashMap<String, List<Statement>> nodes = new LinkedHashMap<>();
    private List<Dialogue.Start> starts;
    private List<String> bindings;
    private String speaker;
    private String title;
    private String scope;
    private final LinkedHashMap<String, String> other;
    private boolean dirty = false;

    public DialogueDraft(@Nonnull Dialogue base) {
        this.base = base;
        for (Node n : base.nodeList()) nodes.put(n.name(), new ArrayList<>(n.body()));
        this.starts = new ArrayList<>(base.starts());
        this.bindings = new ArrayList<>(base.bindings());
        this.speaker = base.speaker();
        this.title = base.title();
        this.scope = base.scope();
        this.other = new LinkedHashMap<>(base.otherDirectives());
    }

    public String id() { return base.id(); }
    public Dialogue base() { return base; }
    public boolean isDirty() { return dirty; }
    public List<String> nodeNames() { return new ArrayList<>(nodes.keySet()); }
    public boolean hasNode(String name) { return nodes.containsKey(name); }

    // ---- header

    public List<String> bindings() { return List.copyOf(bindings); }
    @Nullable public String speaker() { return speaker; }
    @Nullable public String title() { return title; }
    @Nullable public String scope() { return scope; }
    @Nullable public String directive(String key) { return other.get(key); }

    /** Comma or space separated role ids and @tags; "none" for a dialogue opened by roles, triggers or commands only. */
    public void setBindings(String raw) {
        List<String> out = new ArrayList<>();
        other.remove("npc");
        for (String part : (raw == null ? "" : raw).split("[,\\s]+")) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            if (p.equalsIgnoreCase("none")) { other.put("npc", "none"); continue; }
            out.add(p);
        }
        bindings = out;
        dirty = true;
    }

    public void setSpeaker(@Nullable String s) { speaker = blankToNull(s); dirty = true; }
    public void setTitle(@Nullable String s) { title = blankToNull(s); dirty = true; }
    public void setScope(@Nullable String s) { scope = blankToNull(s); dirty = true; }

    public void setDirective(String key, @Nullable String value) {
        if (blankToNull(value) == null) other.remove(key); else other.put(key, value.trim());
        dirty = true;
    }

    /** The node a fresh conversation would start in (the unguarded start, else the first node). */
    public String startNode() {
        for (Dialogue.Start s : starts) if (s.condition() == null) return nodes.containsKey(s.node()) ? s.node() : firstNode();
        return firstNode();
    }

    /** Make a node the unguarded start; guarded starts are kept. */
    public void setStartNode(String node) {
        if (!nodes.containsKey(node)) return;
        List<Dialogue.Start> out = new ArrayList<>();
        for (Dialogue.Start s : starts) if (s.condition() != null) out.add(s);
        out.add(0, new Dialogue.Start(pos(), node, null));
        starts = out;
        dirty = true;
    }

    private String firstNode() {
        return nodes.isEmpty() ? null : nodes.keySet().iterator().next();
    }

    // ---- scopes

    /** True if the scope still exists (edits elsewhere may have removed it). */
    public boolean exists(Scope sc) {
        try {
            view(sc);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** The statements in a scope, as a copy. */
    public List<Statement> view(Scope sc) {
        List<Statement> cur = nodes.get(sc.node());
        if (cur == null) throw new IllegalArgumentException("no node " + sc.node());
        for (Step st : sc.path()) cur = child(cur.get(st.statement()), st.sub());
        return new ArrayList<>(cur);
    }

    /** The nested statement list a step selects. */
    private static List<Statement> child(Statement s, int sub) {
        return switch (s) {
            case Statement.Choice c -> c.options().get(sub).body();
            case Statement.Conditional c -> c.branches().get(sub).body();
            case Statement.Once o -> o.body();
            case Statement.Random r -> r.alternatives().get(sub);
            default -> throw new IllegalArgumentException("statement has no body");
        };
    }

    /** Replace the statements of a scope, rebuilding the enclosing records. */
    public void edit(Scope sc, UnaryOperator<List<Statement>> f) {
        List<Statement> root = nodes.get(sc.node());
        if (root == null) throw new IllegalArgumentException("no node " + sc.node());
        nodes.put(sc.node(), new ArrayList<>(rebuild(root, sc.path(), 0, f)));
        dirty = true;
    }

    private static List<Statement> rebuild(List<Statement> list, List<Step> path, int depth, UnaryOperator<List<Statement>> f) {
        if (depth == path.size()) return new ArrayList<>(f.apply(new ArrayList<>(list)));
        Step st = path.get(depth);
        List<Statement> out = new ArrayList<>(list);
        Statement s = out.get(st.statement());
        out.set(st.statement(), withChild(s, st.sub(), rebuild(child(s, st.sub()), path, depth + 1, f)));
        return out;
    }

    private static Statement withChild(Statement s, int sub, List<Statement> body) {
        List<Statement> b = List.copyOf(body);
        return switch (s) {
            case Statement.Choice c -> {
                List<Option> opts = new ArrayList<>(c.options());
                Option o = opts.get(sub);
                opts.set(sub, new Option(o.pos(), o.text(), o.guard(), o.showGuard(), o.once(), b));
                yield new Statement.Choice(c.pos(), List.copyOf(opts));
            }
            case Statement.Conditional c -> {
                List<Statement.Branch> br = new ArrayList<>(c.branches());
                Statement.Branch x = br.get(sub);
                br.set(sub, new Statement.Branch(x.pos(), x.condition(), b));
                yield new Statement.Conditional(c.pos(), List.copyOf(br));
            }
            case Statement.Once o -> new Statement.Once(o.pos(), o.key(), b);
            case Statement.Random r -> {
                List<List<Statement>> alts = new ArrayList<>(r.alternatives());
                alts.set(sub, b);
                yield new Statement.Random(r.pos(), List.copyOf(alts));
            }
            default -> throw new IllegalArgumentException("statement has no body");
        };
    }

    /** Short description of what a scope step is, for breadcrumbs. */
    public String describe(Scope sc) {
        StringBuilder sb = new StringBuilder(sc.node());
        List<Statement> cur = nodes.get(sc.node());
        for (Step st : sc.path()) {
            Statement s = cur.get(st.statement());
            sb.append(" > ");
            switch (s) {
                case Statement.Choice c -> sb.append("option \"").append(shorten(c.options().get(st.sub()).text().debugString(), 24)).append('"');
                case Statement.Conditional c -> sb.append(st.sub() == 0 ? "if" : c.branches().get(st.sub()).condition() == null ? "else" : "elseif");
                case Statement.Once o -> sb.append("once");
                case Statement.Random r -> sb.append("random ").append(st.sub() + 1);
                default -> sb.append("?");
            }
            cur = child(s, st.sub());
        }
        return sb.toString();
    }

    private static String shorten(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }

    // ---- statements in a scope

    public void replace(Scope sc, int index, Statement s) {
        edit(sc, list -> { list.set(index, s); return list; });
    }

    public void move(Scope sc, int index, int delta) {
        edit(sc, list -> {
            int to = index + delta;
            if (index < 0 || index >= list.size() || to < 0 || to >= list.size()) return list;
            Statement s = list.remove(index);
            list.add(to, s);
            return list;
        });
    }

    public void delete(Scope sc, int index) {
        edit(sc, list -> { if (index >= 0 && index < list.size()) list.remove(index); return list; });
    }

    /**
     * Add a statement of a kind at the end of the scope. Anything but an option goes before a trailing choice, jump
     * or end, since nothing after those would run. Returns its index.
     */
    public int add(Scope sc, Kind kind) {
        int[] at = new int[1];
        edit(sc, list -> {
            Statement s = fresh(kind);
            int i = list.size();
            if (kind != Kind.OPTION) {
                while (i > 0 && (list.get(i - 1) instanceof Statement.Choice || list.get(i - 1) instanceof Statement.Jump || list.get(i - 1) instanceof Statement.End)) i--;
            }
            if (kind == Kind.OPTION) {
                int ci = lastChoice(list);
                if (ci >= 0) {
                    Statement.Choice c = (Statement.Choice) list.get(ci);
                    List<Option> opts = new ArrayList<>(c.options());
                    opts.add(freshOption("New option"));
                    list.set(ci, new Statement.Choice(c.pos(), List.copyOf(opts)));
                    at[0] = ci;
                    return list;
                }
            }
            list.add(i, s);
            at[0] = i;
            return list;
        });
        return at[0];
    }

    private Statement fresh(Kind kind) {
        Pos p = pos();
        return switch (kind) {
            case LINE -> new Statement.Line(p, null, Text.plain(""));
            case OPTION -> new Statement.Choice(p, List.of(freshOption("New option")));
            case COMMAND -> new Statement.Command(p, "give", List.of(Text.plain("Food_Bread"), Text.plain("1")));
            case SET -> new Statement.Set(p, new Expr.Var(ExprParser.DEFAULT_SCOPE, "flag"), new Expr.Literal(Boolean.TRUE));
            case JUMP -> new Statement.Jump(p, firstNode());
            case END -> new Statement.End(p);
            case INPUT -> new Statement.Input(p, new Expr.Var(ExprParser.DEFAULT_SCOPE, "answer"), Text.plain("What do you say?"));
            case WAIT -> new Statement.Wait(p, new Expr.Literal(1.0));
            case IF -> new Statement.Conditional(p, List.of(new Statement.Branch(p, new Expr.Var(ExprParser.DEFAULT_SCOPE, "met"), List.of(placeholderLine()))));
            case ONCE -> new Statement.Once(p, null, List.of(placeholderLine()));
            case RANDOM -> new Statement.Random(p, List.of(List.of(placeholderLine()), List.of(placeholderLine())));
        };
    }

    private Statement.Line placeholderLine() {
        return new Statement.Line(pos(), null, Text.plain("..."));
    }

    private Option freshOption(String label) {
        return new Option(pos(), Text.plain(label), null, null, false, List.of(new Statement.End(pos())));
    }

    private static int lastChoice(List<Statement> list) {
        for (int i = list.size() - 1; i >= 0; i--) if (list.get(i) instanceof Statement.Choice) return i;
        return -1;
    }

    // ---- lines

    public void setLineText(Scope sc, int index, String raw) {
        Statement s = view(sc).get(index);
        if (s instanceof Statement.Line l) replace(sc, index, new Statement.Line(l.pos(), l.speaker(), parseText(raw, l.pos())));
    }

    public void setLineSpeaker(Scope sc, int index, @Nullable String speaker) {
        Statement s = view(sc).get(index);
        if (s instanceof Statement.Line l) replace(sc, index, new Statement.Line(l.pos(), blankToNull(speaker), l.text()));
    }

    // ---- options

    public List<Option> options(Scope sc, int choiceIndex) {
        return ((Statement.Choice) view(sc).get(choiceIndex)).options();
    }

    public void setOptionText(Scope sc, int ci, int oi, String raw) {
        replaceOption(sc, ci, oi, o -> new Option(o.pos(), parseText(raw, o.pos()), o.guard(), o.showGuard(), o.once(), o.body()));
    }

    /** Set the option's hide-unless condition; blank clears it. Returns an error message or null. */
    @Nullable
    public String setOptionGuard(Scope sc, int ci, int oi, String raw) {
        Expr[] e = new Expr[1];
        String err = parseExpr(raw, e);
        if (err != null) return err;
        replaceOption(sc, ci, oi, o -> new Option(o.pos(), o.text(), e[0], o.showGuard(), o.once(), o.body()));
        return null;
    }

    /** Set the option's show-greyed-unless condition; blank clears it. Returns an error message or null. */
    @Nullable
    public String setOptionShowGuard(Scope sc, int ci, int oi, String raw) {
        Expr[] e = new Expr[1];
        String err = parseExpr(raw, e);
        if (err != null) return err;
        replaceOption(sc, ci, oi, o -> new Option(o.pos(), o.text(), o.guard(), e[0], o.once(), o.body()));
        return null;
    }

    public void setOptionOnce(Scope sc, int ci, int oi, boolean once) {
        replaceOption(sc, ci, oi, o -> new Option(o.pos(), o.text(), o.guard(), o.showGuard(), once, o.body()));
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
     * Point an option at a node, at the end, or back to the options. {@link #TARGET_NEW} creates a node first.
     * Returns the node the option now leads to, or null. {@link #TARGET_CUSTOM} keeps whatever body it has.
     */
    @Nullable
    public String setOptionTarget(Scope sc, int ci, int oi, String target) {
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
        replaceOption(sc, ci, oi, o -> new Option(o.pos(), o.text(), o.guard(), o.showGuard(), o.once(), b));
        return lead;
    }

    public void moveOption(Scope sc, int ci, int oi, int delta) {
        edit(sc, list -> {
            Statement.Choice c = (Statement.Choice) list.get(ci);
            List<Option> opts = new ArrayList<>(c.options());
            int to = oi + delta;
            if (to < 0 || to >= opts.size()) return list;
            Option o = opts.remove(oi);
            opts.add(to, o);
            list.set(ci, new Statement.Choice(c.pos(), List.copyOf(opts)));
            return list;
        });
    }

    /** Remove an option; a choice left with no options is removed too. */
    public void deleteOption(Scope sc, int ci, int oi) {
        edit(sc, list -> {
            Statement.Choice c = (Statement.Choice) list.get(ci);
            List<Option> opts = new ArrayList<>(c.options());
            if (oi < 0 || oi >= opts.size()) return list;
            opts.remove(oi);
            if (opts.isEmpty()) list.remove(ci);
            else list.set(ci, new Statement.Choice(c.pos(), List.copyOf(opts)));
            return list;
        });
    }

    private void replaceOption(Scope sc, int ci, int oi, UnaryOperator<Option> f) {
        edit(sc, list -> {
            Statement.Choice c = (Statement.Choice) list.get(ci);
            List<Option> opts = new ArrayList<>(c.options());
            if (oi < 0 || oi >= opts.size()) return list;
            opts.set(oi, f.apply(opts.get(oi)));
            list.set(ci, new Statement.Choice(c.pos(), List.copyOf(opts)));
            return list;
        });
    }

    // ---- other statements

    /** Replace a command's name and its arguments given as .talk text ("Food_Bread 2" or "\"Hello there\" success"). */
    @Nullable
    public String setCommand(Scope sc, int index, String name, String argsText) {
        String n = name == null ? "" : name.trim();
        if (!n.matches("[a-z_][a-z0-9_]*")) return "command names are lower-case words like give or objective";
        Statement s = parseStatementText("<<" + n + " " + (argsText == null ? "" : argsText.trim()) + ">>");
        if (!(s instanceof Statement.Command)) return s == null ? "could not read those arguments; quote text that has spaces" : "that is not a command";
        replace(sc, index, s);
        return null;
    }

    /** The arguments of a command as one editable .talk string. */
    public static String argsText(Statement.Command c) {
        StringBuilder sb = new StringBuilder();
        for (Text a : c.args()) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(com.chromecide.lowtalk.parser.Printer.arg(com.chromecide.lowtalk.parser.Printer.text(a)));
        }
        return sb.toString();
    }

    /** Replace one argument of a command, keeping the others (used by pickers). */
    public void setCommandArg(Scope sc, int index, int arg, String value) {
        Statement s = view(sc).get(index);
        if (!(s instanceof Statement.Command c)) return;
        List<Text> args = new ArrayList<>(c.args());
        while (args.size() <= arg) args.add(Text.plain(""));
        args.set(arg, Text.plain(value));
        replace(sc, index, new Statement.Command(c.pos(), c.name(), List.copyOf(args)));
    }

    @Nullable
    public String setSet(Scope sc, int index, String var, String value) {
        Statement s = parseStatementText("<<set " + (var == null ? "" : var.trim()) + " = " + (value == null ? "" : value.trim()) + ">>");
        if (!(s instanceof Statement.Set)) return "write a variable like $count or $player.flag, and a value like 1, true or \"text\"";
        replace(sc, index, s);
        return null;
    }

    public void setJump(Scope sc, int index, String node) {
        if (!nodes.containsKey(node)) return;
        Statement s = view(sc).get(index);
        if (s instanceof Statement.Jump j) replace(sc, index, new Statement.Jump(j.pos(), node));
    }

    @Nullable
    public String setInput(Scope sc, int index, String var, String prompt) {
        Statement s = parseStatementText("<<input " + (var == null ? "" : var.trim()) + " " + com.chromecide.lowtalk.parser.Printer.arg(prompt == null ? "" : prompt) + ">>");
        if (!(s instanceof Statement.Input)) return "write a variable like $name and a prompt";
        replace(sc, index, s);
        return null;
    }

    @Nullable
    public String setWait(Scope sc, int index, String seconds) {
        Statement s = parseStatementText("<<wait " + (seconds == null ? "" : seconds.trim()) + ">>");
        if (!(s instanceof Statement.Wait)) return "seconds must be a number or an expression";
        replace(sc, index, s);
        return null;
    }

    /** Set a branch condition (blank makes it an else, only allowed on the last branch). Returns an error or null. */
    @Nullable
    public String setBranchCondition(Scope sc, int index, int branch, String raw) {
        Statement s = view(sc).get(index);
        if (!(s instanceof Statement.Conditional c)) return null;
        Expr[] e = new Expr[1];
        String err = parseExpr(raw, e);
        if (err != null) return err;
        if (e[0] == null && branch == 0) return "the first branch needs a condition";
        if (e[0] == null && branch != c.branches().size() - 1) return "only the last branch can be an else";
        List<Statement.Branch> br = new ArrayList<>(c.branches());
        Statement.Branch b = br.get(branch);
        br.set(branch, new Statement.Branch(b.pos(), e[0], b.body()));
        replace(sc, index, new Statement.Conditional(c.pos(), List.copyOf(br)));
        return null;
    }

    /** Add an else-if branch (or an else when the last branch is not one yet) and return its index. */
    public int addBranch(Scope sc, int index) {
        Statement s = view(sc).get(index);
        if (!(s instanceof Statement.Conditional c)) return -1;
        List<Statement.Branch> br = new ArrayList<>(c.branches());
        boolean hasElse = br.get(br.size() - 1).condition() == null;
        Statement.Branch fresh = new Statement.Branch(pos(), hasElse ? new Expr.Var(ExprParser.DEFAULT_SCOPE, "flag") : null, List.of(placeholderLine()));
        int at = hasElse ? br.size() - 1 : br.size();
        br.add(at, fresh);
        replace(sc, index, new Statement.Conditional(c.pos(), List.copyOf(br)));
        return at;
    }

    /** Remove a branch; removing the last one removes the whole if. */
    public void deleteBranch(Scope sc, int index, int branch) {
        Statement s = view(sc).get(index);
        if (!(s instanceof Statement.Conditional c)) return;
        List<Statement.Branch> br = new ArrayList<>(c.branches());
        if (branch < 0 || branch >= br.size()) return;
        br.remove(branch);
        if (br.isEmpty()) { delete(sc, index); return; }
        if (br.get(0).condition() == null) br.set(0, new Statement.Branch(br.get(0).pos(), new Expr.Literal(Boolean.TRUE), br.get(0).body()));
        replace(sc, index, new Statement.Conditional(c.pos(), List.copyOf(br)));
    }

    public int addAlternative(Scope sc, int index) {
        Statement s = view(sc).get(index);
        if (!(s instanceof Statement.Random r)) return -1;
        List<List<Statement>> alts = new ArrayList<>(r.alternatives());
        alts.add(List.of(placeholderLine()));
        replace(sc, index, new Statement.Random(r.pos(), List.copyOf(alts)));
        return alts.size() - 1;
    }

    public void deleteAlternative(Scope sc, int index, int alt) {
        Statement s = view(sc).get(index);
        if (!(s instanceof Statement.Random r)) return;
        List<List<Statement>> alts = new ArrayList<>(r.alternatives());
        if (alt < 0 || alt >= alts.size()) return;
        alts.remove(alt);
        if (alts.isEmpty()) { delete(sc, index); return; }
        replace(sc, index, new Statement.Random(r.pos(), List.copyOf(alts)));
    }

    // ---- nodes

    /** Create a node with a fresh name and return the name. */
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
        return new Dialogue(base.file(), base.id(), List.copyOf(bindings), st, speaker, title, scope,
                new LinkedHashMap<>(other), base.includes(), out);
    }

    // ---- parsing helpers

    private Pos pos() {
        return new Pos(base.file(), 0);
    }

    @Nullable
    private static String blankToNull(@Nullable String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static Text parseText(String raw, Pos pos) {
        String s = raw == null ? "" : raw;
        try {
            return TextParser.parse(s, pos);
        } catch (ParseException e) {
            return Text.plain(s); // unbalanced braces or brackets: keep the words, the printer escapes them
        }
    }

    /** Parse an expression into out[0] (null for blank). Returns an error message or null. */
    @Nullable
    private String parseExpr(@Nullable String raw, Expr[] out) {
        if (raw == null || raw.isBlank()) { out[0] = null; return null; }
        try {
            out[0] = ExprParser.parse(raw.trim(), pos());
            return null;
        } catch (ParseException e) {
            return e.getMessage();
        }
    }

    /** Parse one .talk statement line through the real parser; null when it does not parse. */
    @Nullable
    public Statement parseStatementText(String line) {
        try {
            Dialogue d = DialogueParser.parse(base.file(), "npc: none\n\n== n\n" + line + "\n");
            List<Statement> body = d.node("n").body();
            return body.size() == 1 ? body.get(0) : null;
        } catch (ParseException e) {
            return null;
        }
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
