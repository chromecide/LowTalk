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
            Map.entry("objective", new int[] {1, 1}),
            Map.entry("anim", new int[] {1, 1}),
            Map.entry("sound", new int[] {1, 1}),
            Map.entry("run", new int[] {1, 1})
    );

    /** The dialogue window has this many option slots. */
    public static final int MAX_OPTIONS = 8;

    public static final Set<String> ATTITUDES = Set.of("ignore", "hostile", "neutral", "friendly", "revered");

    public static final Set<String> BUILTIN_FUNCTIONS = Set.of(
            "player", "npc", "has", "count", "visited", "objective", "attitude", "perm", "hour", "random", "chance", "ordinal", "plural"
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
        return out;
    }

    private void collectInputVars(List<Statement> body, Set<String> out) {
        for (Statement s : body) {
            switch (s) {
                case Statement.Input in -> out.add(in.target().scope() + "." + in.target().name());
                case Statement.Choice c -> c.options().forEach(o -> collectInputVars(o.body(), out));
                case Statement.Conditional c -> c.branches().forEach(b -> collectInputVars(b.body(), out));
                case Statement.Once o -> collectInputVars(o.body(), out);
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
                    }
                }
                case Statement.Conditional c -> {
                    for (Statement.Branch b : c.branches()) {
                        if (b.condition() != null) checkExpr(b.condition(), b.pos(), out);
                        checkBlock(b.body(), d, inputVars, out, inOption);
                    }
                }
                case Statement.Once o -> checkBlock(o.body(), d, inputVars, out, inOption);
                case Statement.Set set -> {
                    checkExpr(set.value(), set.pos(), out);
                    if (set.target().scope().equals("tmp") && false) { /* reserved */ }
                }
                case Statement.Jump j -> {
                    if (!d.nodes().containsKey(j.node())) {
                        out.add(new Problem(j.pos(), true, "jump to unknown node '" + j.node() + "'"));
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

    private void checkCommand(Statement.Command cmd, Set<String> inputVars, List<Problem> out) {
        for (Text t : cmd.args()) checkText(t, cmd.pos(), out);
        int[] arity = BUILTIN_COMMANDS.get(cmd.name());
        if (arity == null) {
            if (!extraCommands.contains(cmd.name())) {
                out.add(new Problem(cmd.pos(), false, "unknown command <<" + cmd.name() + ">>; it will only work if a plugin provides it"));
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
        }
    }

    private void checkExpr(Expr e, Pos pos, List<Problem> out) {
        switch (e) {
            case Expr.Call c -> {
                if (!BUILTIN_FUNCTIONS.contains(c.function()) && !extraFunctions.contains(c.function())) {
                    out.add(new Problem(pos, false, "unknown function " + c.function() + "(); it will only work if a plugin provides it"));
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
        }
    }
}
