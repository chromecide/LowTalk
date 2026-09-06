package com.chromecide.lowtalk.parser;

import com.chromecide.lowtalk.model.Dialogue;
import com.chromecide.lowtalk.model.Expr;
import com.chromecide.lowtalk.model.Node;
import com.chromecide.lowtalk.model.Option;
import com.chromecide.lowtalk.model.Pos;
import com.chromecide.lowtalk.model.Statement;
import com.chromecide.lowtalk.model.Text;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Static checks that the parser cannot do on its own: jump targets exist, start nodes exist,
 * built-in commands have the right shape, and text typed by players never reaches <<run>>.
 */
public final class Validator {

    public record Problem(Pos pos, boolean error, String message) {
        @Override
        public String toString() {
            return (error ? "error" : "warning") + " " + pos + ": " + message;
        }
    }

    /** Built-in commands and their argument counts: name -> [min, max]. -1 max means unlimited. */
    public static final Map<String, int[]> BUILTIN_COMMANDS = Map.ofEntries(
            Map.entry("give", new int[] {1, 2}),
            Map.entry("take", new int[] {1, 2}),
            Map.entry("shop", new int[] {0, 0}),
            Map.entry("attitude", new int[] {1, 1}),
            Map.entry("objective", new int[] {1, 2}),
            Map.entry("anim", new int[] {1, 2}),
            Map.entry("sound", new int[] {1, 1}),
            Map.entry("run", new int[] {1, 1}),
            Map.entry("reputation", new int[] {1, 2}),
            Map.entry("notify", new int[] {1, 3}),
            Map.entry("title", new int[] {1, 4}),
            Map.entry("effect", new int[] {1, 1}),
            Map.entry("cure", new int[] {1, 1}),
            Map.entry("stat", new int[] {1, 2}),
            Map.entry("heal", new int[] {0, 1}),
            Map.entry("learn", new int[] {1, 1}),
            Map.entry("teleport", new int[] {1, 3}),
            Map.entry("weather", new int[] {1, 2}),
            Map.entry("time", new int[] {1, 2}),
            Map.entry("npc_name", new int[] {1, 1}),
            Map.entry("state", new int[] {1, 2}),
            Map.entry("despawn", new int[] {0, 0}),
            Map.entry("spawn", new int[] {1, 4})
    );
    public static final Set<String> OBJECTIVE_VERBS = Set.of("start", "cancel", "line", "task");

    /** The dialogue window has this many option slots. */
    public static final int MAX_OPTIONS = 8;

    public static final Set<String> ATTITUDES = Set.of("ignore", "hostile", "neutral", "friendly", "revered");
    /** Commands that hand the player something of value; repeatable access to them is usually a mistake. */
    public static final Set<String> REWARD_COMMANDS = Set.of("give", "reputation", "learn", "objective", "run");

    public static final Set<String> BUILTIN_FUNCTIONS = Set.of(
            "player", "npc", "has", "count", "visited", "objective", "attitude", "perm", "hour", "random", "chance", "ordinal", "plural", "reputation", "rank", "stat", "max_stat", "effect", "knows", "objective_line", "t", "weather"
    );

    private final Set<String> extraCommands;
    private final Set<String> extraFunctions;

    public Validator() {
        this(Set.of(), Set.of());
    }

    public Validator(Set<String> extraCommands, Set<String> extraFunctions) {
        this.extraCommands = extraCommands;
        this.extraFunctions = extraFunctions;
    }

    public List<Problem> validate(Dialogue d) {
        List<Problem> out = new ArrayList<>();
        Set<String> inputVars = new HashSet<>();
        for (Node n : d.nodeList()) {
            collectInputVars(n.body(), inputVars);
        }

        for (Dialogue.Start s : d.starts()) {
            if (!d.nodes().containsKey(s.node())) {
                out.add(new Problem(s.pos(), true, "start node '" + s.node() + "' does not exist"));
            }
            if (s.condition() != null) checkExpr(s.condition(), s.pos(), out);
        }
        boolean hasFallback = d.starts().stream().anyMatch(s -> s.condition() == null);
        if (!hasFallback) {
            out.add(new Problem(d.starts().get(0).pos(), false, "every start: has a 'when' guard; add an unguarded start: as a fallback"));
        }
        if (d.bindings().isEmpty()) {
            out.add(new Problem(new Pos(d.file(), 1), false, "no npc: binding; this dialogue can only be opened by command"));
        }

        for (Node n : d.nodeList()) {
            checkBlock(n.body(), d, inputVars, out, false);
        }
        checkReachability(d, out);
        checkVariables(d, inputVars, out);
        return out;
    }

    /** Nodes that no start: and no jump ever reach. */
    private void checkReachability(Dialogue d, List<Problem> out) {
        Set<String> reachable = new HashSet<>();
        for (Dialogue.Start s : d.starts()) reachable.add(s.node());
        for (Node n : d.nodeList()) collectJumps(n.body(), reachable);
        for (Node n : d.nodeList()) {
            if (!reachable.contains(n.name())) {
                out.add(new Problem(n.pos(), false, "node '" + n.name() + "' is never reached by a start: or a jump"));
            }
        }
    }

    private static void collectJumps(List<Statement> body, Set<String> out) {
        for (Statement s : body) {
            switch (s) {
                case Statement.Jump j -> out.add(j.node());
                case Statement.Choice c -> c.options().forEach(o -> collectJumps(o.body(), out));
                case Statement.Conditional c -> c.branches().forEach(b -> collectJumps(b.body(), out));
                case Statement.Once o -> collectJumps(o.body(), out);
                case Statement.Random r -> r.alternatives().forEach(a -> collectJumps(a, out));
                default -> {}
            }
        }
    }

    /** Variables that are read somewhere but never set or input anywhere in this file (likely typos). */
    private void checkVariables(Dialogue d, Set<String> inputVars, List<Problem> out) {
        Set<String> written = new HashSet<>(inputVars);
        Map<String, Pos> read = new java.util.LinkedHashMap<>();
        for (Dialogue.Start st : d.starts()) {
            if (st.condition() != null) for (Expr.Var v : vars(st.condition())) read.putIfAbsent(key(v), st.pos());
        }
        for (Node n : d.nodeList()) scanVars(n.body(), written, read);
        for (Map.Entry<String, Pos> e : read.entrySet()) {
            String k = e.getKey();
            // player/npc/world scopes may legitimately be written by other files or plugins.
            if (!written.contains(k) && (k.startsWith("local.") || k.startsWith("tmp."))) {
                out.add(new Problem(e.getValue(), false, "$" + k.substring(k.indexOf('.') + 1) + " is read but never set in this file"));
            }
        }
    }

    private static String key(Expr.Var v) {
        return v.scope() + "." + v.name();
    }

    private static void scanVars(List<Statement> body, Set<String> written, Map<String, Pos> read) {
        for (Statement s : body) {
            switch (s) {
                case Statement.Set set -> {
                    for (Expr.Var v : vars(set.value())) read.putIfAbsent(key(v), set.pos());
                    written.add(key(set.target()));
                }
                case Statement.Line l -> textVars(l.text(), l.pos(), read);
                case Statement.Input in -> textVars(in.prompt(), in.pos(), read);
                case Statement.Command c -> c.args().forEach(t -> textVars(t, c.pos(), read));
                case Statement.Choice c -> {
                    for (Option o : c.options()) {
                        textVars(o.text(), o.pos(), read);
                        if (o.guard() != null) for (Expr.Var v : vars(o.guard())) read.putIfAbsent(key(v), o.pos());
                        if (o.showGuard() != null) for (Expr.Var v : vars(o.showGuard())) read.putIfAbsent(key(v), o.pos());
                        scanVars(o.body(), written, read);
                    }
                }
                case Statement.Conditional c -> {
                    for (Statement.Branch b : c.branches()) {
                        if (b.condition() != null) for (Expr.Var v : vars(b.condition())) read.putIfAbsent(key(v), b.pos());
                        scanVars(b.body(), written, read);
                    }
                }
                case Statement.Once o -> scanVars(o.body(), written, read);
                case Statement.Random r -> r.alternatives().forEach(a -> scanVars(a, written, read));
                case Statement.Wait w -> {
                    for (Expr.Var v : vars(w.seconds())) read.putIfAbsent(key(v), w.pos());
                }
                case Statement.Jump j -> {}
                case Statement.End e -> {}
            }
        }
    }

    private static void textVars(Text t, Pos pos, Map<String, Pos> read) {
        for (Text.Part p : t.parts()) {
            if (p instanceof Text.Part.Interp in) for (Expr.Var v : vars(in.expr())) read.putIfAbsent(key(v), pos);
            else if (p instanceof Text.Part.Pick pk) pk.choices().forEach(c -> textVars(c, pos, read));
        }
    }

    private void collectInputVars(List<Statement> body, Set<String> out) {
        for (Statement s : body) {
            switch (s) {
                case Statement.Input in -> out.add(in.target().scope() + "." + in.target().name());
                case Statement.Choice c -> c.options().forEach(o -> collectInputVars(o.body(), out));
                case Statement.Conditional c -> c.branches().forEach(b -> collectInputVars(b.body(), out));
                case Statement.Once o -> collectInputVars(o.body(), out);
                case Statement.Random r -> r.alternatives().forEach(a -> collectInputVars(a, out));
                default -> {}
            }
        }
    }

    private void checkBlock(List<Statement> body, Dialogue d, Set<String> inputVars, List<Problem> out, boolean inOption) {
        for (int i = 0; i < body.size(); i++) {
            Statement s = body.get(i);
            boolean last = i == body.size() - 1;
            switch (s) {
                case Statement.Line l -> checkText(l.text(), l.pos(), out);
                case Statement.Choice c -> {
                    if (c.options().size() > MAX_OPTIONS) {
                        out.add(new Problem(c.pos(), true, "a choice can show at most " + MAX_OPTIONS + " options, this one has " + c.options().size()));
                    }
                    if (!last) {
                        out.add(new Problem(c.pos(), false, "statements after a set of options are never reached; put them inside the options or before them"));
                    }
                    for (Option o : c.options()) {
                        checkText(o.text(), o.pos(), out);
                        if (o.guard() != null) checkExpr(o.guard(), o.pos(), out);
                        if (o.showGuard() != null) checkExpr(o.showGuard(), o.pos(), out);
                        checkBlock(o.body(), d, inputVars, out, true);
                        checkFarmable(o, out);
                    }
                }
                case Statement.Conditional c -> {
                    for (Statement.Branch b : c.branches()) {
                        if (b.condition() != null) checkExpr(b.condition(), b.pos(), out);
                        checkBlock(b.body(), d, inputVars, out, inOption);
                    }
                }
                case Statement.Once o -> checkBlock(o.body(), d, inputVars, out, inOption);
                case Statement.Random r -> {
                    if (r.alternatives().size() < 2) {
                        out.add(new Problem(r.pos(), false, "<<random>> has only one alternative; separate alternatives with <<or>>"));
                    }
                    for (List<Statement> a : r.alternatives()) checkBlock(a, d, inputVars, out, inOption);
                }
                case Statement.Wait w -> {
                    checkExpr(w.seconds(), w.pos(), out);
                    if (w.seconds() instanceof Expr.Literal lit && lit.value() instanceof Double dd && (dd < 0 || dd > 30)) {
                        out.add(new Problem(w.pos(), false, "<<wait>> of " + plainNumber(dd) + " seconds is outside 0 to 30"));
                    }
                }
                case Statement.Set set -> {
                    checkExpr(set.value(), set.pos(), out);
                    if (set.target().scope().equals("tmp") && false) { /* reserved */ }
                }
                case Statement.Jump j -> {
                    if (!d.nodes().containsKey(j.node())) {
                        out.add(new Problem(j.pos(), true, "jump to unknown node '" + j.node() + "'" + Suggest.hint(j.node(), d.nodes().keySet())));
                    }
                    if (!last) {
                        out.add(new Problem(j.pos(), false, "statements after <<jump>> are never reached"));
                    }
                }
                case Statement.End e -> {
                    if (!last) {
                        out.add(new Problem(e.pos(), false, "statements after <<end>> are never reached"));
                    }
                }
                case Statement.Input in -> checkText(in.prompt(), in.pos(), out);
                case Statement.Command cmd -> checkCommand(cmd, inputVars, out);
            }
        }
    }

    /**
     * An option that hands out a reward, can be picked again and again (no once, no guard, no state change
     * in its body, and it returns to the hub), is farmable. Warn so authors add <<once>> or a guard.
     */
    private static void checkFarmable(Option o, List<Problem> out) {
        if (o.once() || o.guard() != null || o.showGuard() != null) return;
        String reward = firstReward(o.body());
        if (reward == null) return;
        if (containsStateChange(o.body())) return;
        out.add(new Problem(o.pos(), false, "this option gives a reward (<<" + reward + ">>) but nothing stops the player picking it again; "
                + "add <<once>> or an <<if>> guard, or set a variable in its body"));
    }
    private static String firstReward(List<Statement> body) {
        for (Statement s : body) {
            switch (s) {
                case Statement.Command c -> {
                    if (REWARD_COMMANDS.contains(c.name())) {
                        if (c.name().equals("reputation") && !c.args().isEmpty() && c.args().get(0).debugString().startsWith("-")) continue;
                        if (c.name().equals("objective") && c.args().size() == 2) {
                            String verb = c.args().get(0).debugString().toLowerCase(java.util.Locale.ROOT);
                            if (verb.equals("cancel") || verb.equals("task")) continue; // taking away, or progressing, not handing out
                        }
                        return c.name();
                    }
                }
                case Statement.Conditional c -> {
                    for (Statement.Branch b : c.branches()) {
                        String r = firstReward(b.body());
                        if (r != null) return r;
                    }
                }
                case Statement.Random r -> {
                    for (List<Statement> a : r.alternatives()) {
                        String x = firstReward(a);
                        if (x != null) return x;
                    }
                }
                default -> {}
            }
        }
        return null;
    }
    /** A set, a once-block, an end, or a take (paying for the reward) means the author is managing repeats. */
    private static boolean containsStateChange(List<Statement> body) {
        for (Statement s : body) {
            switch (s) {
                case Statement.Set set -> {
                    if (!set.target().scope().equals("tmp")) return true;
                }
                case Statement.Once o -> {
                    return true;
                }
                case Statement.End e -> {
                    return true;
                }
                case Statement.Command c -> {
                    if (c.name().equals("take")) return true;
                }
                case Statement.Conditional c -> {
                    for (Statement.Branch b : c.branches()) if (containsStateChange(b.body())) return true;
                }
                case Statement.Random r -> {
                    for (List<Statement> a : r.alternatives()) if (containsStateChange(a)) return true;
                }
                default -> {}
            }
        }
        return false;
    }
    private void checkCommand(Statement.Command cmd, Set<String> inputVars, List<Problem> out) {
        for (Text t : cmd.args()) checkText(t, cmd.pos(), out);
        int[] arity = BUILTIN_COMMANDS.get(cmd.name());
        if (arity == null) {
            if (!extraCommands.contains(cmd.name())) {
                java.util.Set<String> known = new java.util.HashSet<>(BUILTIN_COMMANDS.keySet());
                known.addAll(extraCommands);
                known.addAll(java.util.List.of("set", "jump", "end", "input", "if", "elseif", "else", "endif", "once", "endonce", "random", "or", "endrandom", "wait"));
                String hint = Suggest.hint(cmd.name(), known);
                out.add(new Problem(cmd.pos(), !hint.isEmpty(), "unknown command <<" + cmd.name() + ">>" + hint
                        + (hint.isEmpty() ? "; it will only work if a plugin provides it" : "")));
            }
            return;
        }
        int n = cmd.args().size();
        if (n < arity[0] || (arity[1] >= 0 && n > arity[1])) {
            out.add(new Problem(cmd.pos(), true, "<<" + cmd.name() + ">> expects " + describeArity(arity) + " argument(s), got " + n));
            return;
        }
        switch (cmd.name()) {
            case "attitude" -> {
                Text a = cmd.args().get(0);
                if (a.isStatic() && !ATTITUDES.contains(a.debugString().toLowerCase())) {
                    out.add(new Problem(cmd.pos(), true, "<<attitude>> must be one of " + ATTITUDES + ", got '" + a.debugString() + "'"));
                }
            }
            case "give", "take" -> {
                if (n == 2) {
                    Text c = cmd.args().get(1);
                    if (c.isStatic() && !c.debugString().matches("\\d+")) {
                        out.add(new Problem(cmd.pos(), true, "<<" + cmd.name() + ">> count must be a whole number, got '" + c.debugString() + "'"));
                    }
                }
            }
            case "objective" -> {
                if (n == 2) {
                    Text v = cmd.args().get(0);
                    if (v.isStatic() && !OBJECTIVE_VERBS.contains(v.debugString().toLowerCase(java.util.Locale.ROOT))) {
                        out.add(new Problem(cmd.pos(), true, "<<objective>> verb must be one of " + OBJECTIVE_VERBS + ", got '" + v.debugString() + "'"));
                    }
                }
            }
            case "time" -> {
                Text v = cmd.args().get(0);
                if (v.isStatic()) {
                    String w = v.debugString().toLowerCase(java.util.Locale.ROOT);
                    boolean named = Set.of("dawn", "day", "morning", "noon", "midday", "dusk", "night", "evening", "midnight", "pause", "stop", "resume", "start", "unpause").contains(w);
                    boolean hour = w.matches("\\d+(\\.\\d+)?");
                    if (!named && !hour) {
                        out.add(new Problem(cmd.pos(), true, "<<time>> expects dawn, noon, dusk, midnight, an hour 0-24, pause or resume; got '" + w + "'"));
                    }
                }
            }
            case "run" -> {
                Text t = cmd.args().get(0);
                for (Text.Part p : t.parts()) {
                    if (p instanceof Text.Part.Interp in) {
                        for (Expr.Var v : vars(in.expr())) {
                            if (inputVars.contains(v.scope() + "." + v.name())) {
                                out.add(new Problem(cmd.pos(), true, "<<run>> must not include $" + v.name() + ", which is set from player text input"));
                            }
                        }
                    }
                }
            }
            default -> {}
        }
    }

    private static String describeArity(int[] a) {
        if (a[1] < 0) return "at least " + a[0];
        if (a[0] == a[1]) return String.valueOf(a[0]);
        return a[0] + " to " + a[1];
    }

    private void checkText(Text t, Pos pos, List<Problem> out) {
        for (Text.Part p : t.parts()) {
            if (p instanceof Text.Part.Interp in) checkExpr(in.expr(), pos, out);
            else if (p instanceof Text.Part.Pick pk) pk.choices().forEach(c -> checkText(c, pos, out));
        }
    }
    private static String plainNumber(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    private void checkExpr(Expr e, Pos pos, List<Problem> out) {
        switch (e) {
            case Expr.Call c -> {
                if (!BUILTIN_FUNCTIONS.contains(c.function()) && !extraFunctions.contains(c.function())) {
                    java.util.Set<String> known = new java.util.HashSet<>(BUILTIN_FUNCTIONS);
                    known.addAll(extraFunctions);
                    String hint = Suggest.hint(c.function(), known);
                    out.add(new Problem(pos, !hint.isEmpty(), "unknown function " + c.function() + "()" + hint
                            + (hint.isEmpty() ? "; it will only work if a plugin provides it" : "")));
                }
                c.args().forEach(a -> checkExpr(a, pos, out));
            }
            case Expr.Unary u -> checkExpr(u.operand(), pos, out);
            case Expr.Binary b -> {
                checkExpr(b.left(), pos, out);
                checkExpr(b.right(), pos, out);
            }
            case Expr.Literal l -> {}
            case Expr.Var v -> {}
            case Expr.Ternary t -> {
                checkExpr(t.cond(), pos, out);
                checkExpr(t.ifTrue(), pos, out);
                checkExpr(t.ifFalse(), pos, out);
            }
        }
    }

    static List<Expr.Var> vars(Expr e) {
        List<Expr.Var> out = new ArrayList<>();
        collectVars(e, out);
        return out;
    }

    private static void collectVars(Expr e, List<Expr.Var> out) {
        switch (e) {
            case Expr.Var v -> out.add(v);
            case Expr.Unary u -> collectVars(u.operand(), out);
            case Expr.Binary b -> {
                collectVars(b.left(), out);
                collectVars(b.right(), out);
            }
            case Expr.Call c -> c.args().forEach(a -> collectVars(a, out));
            case Expr.Literal l -> {}
            case Expr.Ternary t -> {
                collectVars(t.cond(), out);
                collectVars(t.ifTrue(), out);
                collectVars(t.ifFalse(), out);
            }
        }
    }
}
