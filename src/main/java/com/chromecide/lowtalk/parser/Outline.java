package com.chromecide.lowtalk.parser;

import com.chromecide.lowtalk.model.Dialogue;
import com.chromecide.lowtalk.model.Expr;
import com.chromecide.lowtalk.model.Node;
import com.chromecide.lowtalk.model.Option;
import com.chromecide.lowtalk.model.Statement;
import com.chromecide.lowtalk.model.Text;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A short structural summary of a dialogue, for creators: nodes, options, variables, what is unreachable. */
public record Outline(
        String id,
        List<String> bindings,
        Map<String, Integer> optionsPerNode,
        Set<String> variablesRead,
        Set<String> variablesWritten,
        Set<String> unreachableNodes,
        Set<String> commandsUsed
) {
    public static Outline of(Dialogue d) {
        Map<String, Integer> options = new LinkedHashMap<>();
        Set<String> read = new LinkedHashSet<>();
        Set<String> written = new LinkedHashSet<>();
        Set<String> commands = new LinkedHashSet<>();
        Set<String> reachable = new HashSet<>();
        for (Dialogue.Start s : d.starts()) reachable.add(s.node());
        for (Node n : d.nodeList()) {
            int[] count = {0};
            walk(n.body(), count, read, written, commands, reachable);
            options.put(n.name(), count[0]);
        }
        Set<String> unreachable = new LinkedHashSet<>();
        for (Node n : d.nodeList()) if (!reachable.contains(n.name())) unreachable.add(n.name());
        return new Outline(d.id(), d.bindings(), options, read, written, unreachable, commands);
    }

    private static void walk(List<Statement> body, int[] options, Set<String> read, Set<String> written, Set<String> commands, Set<String> jumps) {
        for (Statement s : body) {
            switch (s) {
                case Statement.Line l -> textVars(l.text(), read);
                case Statement.Choice c -> {
                    for (Option o : c.options()) {
                        options[0]++;
                        textVars(o.text(), read);
                        if (o.guard() != null) vars(o.guard(), read);
                        if (o.showGuard() != null) vars(o.showGuard(), read);
                        walk(o.body(), options, read, written, commands, jumps);
                    }
                }
                case Statement.Conditional c -> {
                    for (Statement.Branch b : c.branches()) {
                        if (b.condition() != null) vars(b.condition(), read);
                        walk(b.body(), options, read, written, commands, jumps);
                    }
                }
                case Statement.Once o -> walk(o.body(), options, read, written, commands, jumps);
                case Statement.Random r -> r.alternatives().forEach(a -> walk(a, options, read, written, commands, jumps));
                case Statement.Set set -> {
                    vars(set.value(), read);
                    written.add("$" + name(set.target()));
                }
                case Statement.Input in -> {
                    textVars(in.prompt(), read);
                    written.add("$" + name(in.target()));
                }
                case Statement.Jump j -> jumps.add(j.node());
                case Statement.Wait w -> vars(w.seconds(), read);
                case Statement.Command c -> {
                    commands.add(c.name());
                    c.args().forEach(t -> textVars(t, read));
                }
                case Statement.End e -> {}
            }
        }
    }

    private static void textVars(Text t, Set<String> read) {
        for (Text.Part p : t.parts()) {
            if (p instanceof Text.Part.Interp in) vars(in.expr(), read);
            else if (p instanceof Text.Part.Pick pk) pk.choices().forEach(c -> textVars(c, read));
        }
    }

    private static void vars(Expr e, Set<String> read) {
        for (Expr.Var v : Validator.vars(e)) read.add("$" + name(v));
    }

    private static String name(Expr.Var v) {
        return v.scope().equals(ExprParser.DEFAULT_SCOPE) ? v.name() : v.scope() + "." + v.name();
    }

    /** A few chat-sized lines. */
    public List<String> lines() {
        List<String> out = new ArrayList<>();
        out.add(id + ": " + optionsPerNode.size() + " passage(s), " + (bindings.isEmpty() ? "no npc: binding (command only)" : "bound to " + String.join(", ", bindings)));
        StringBuilder nodes = new StringBuilder();
        int shown = 0;
        for (Map.Entry<String, Integer> e : optionsPerNode.entrySet()) {
            if (shown == 8) {
                nodes.append(", ...");
                break;
            }
            if (shown > 0) nodes.append(", ");
            nodes.append(e.getKey());
            if (e.getValue() > 0) nodes.append(" (").append(e.getValue()).append(" options)");
            shown++;
        }
        out.add("Passages: " + nodes);
        if (!variablesWritten.isEmpty()) out.add("Sets: " + String.join(", ", variablesWritten));
        Set<String> onlyRead = new LinkedHashSet<>(variablesRead);
        onlyRead.removeAll(variablesWritten);
        if (!onlyRead.isEmpty()) out.add("Reads but never sets here: " + String.join(", ", onlyRead));
        if (!commandsUsed.isEmpty()) out.add("Commands: " + String.join(", ", commandsUsed));
        if (!unreachableNodes.isEmpty()) out.add("Never reached: " + String.join(", ", unreachableNodes));
        return out;
    }
}
