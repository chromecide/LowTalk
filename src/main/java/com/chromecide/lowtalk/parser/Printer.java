package com.chromecide.lowtalk.parser;

import com.chromecide.lowtalk.model.Dialogue;
import com.chromecide.lowtalk.model.Expr;
import com.chromecide.lowtalk.model.Node;
import com.chromecide.lowtalk.model.Option;
import com.chromecide.lowtalk.model.Statement;
import com.chromecide.lowtalk.model.Text;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Turns the model back into .talk source and expression text. The inverse of the parsers, used by converters. */
public final class Printer {
    private static final Pattern LOOKS_LIKE_SPEAKER = Pattern.compile("^([A-Za-z][A-Za-z0-9_' ]{0,30}?)\\s*:\\s+.*$");

    private Printer() {}

    // ---- expressions

    public static String expr(Expr e) {
        return expr(e, 0);
    }

    private static int precedence(Expr e) {
        return switch (e) {
            case Expr.Ternary t -> 0;
            case Expr.Binary b -> switch (b.op()) {
                case "or" -> 1;
                case "and" -> 2;
                case "==", "!=", "<", "<=", ">", ">=" -> 4;
                case "+", "-" -> 5;
                default -> 6;
            };
            case Expr.Unary u -> u.op().equals("not") ? 3 : 7;
            default -> 9;
        };
    }

    private static String expr(Expr e, int parentPrecedence) {
        String s = switch (e) {
            case Expr.Literal l -> literal(l.value());
            case Expr.Var v -> v.scope().equals(ExprParser.DEFAULT_SCOPE) ? "$" + v.name() : "$" + v.scope() + "." + v.name();
            case Expr.Unary u -> u.op().equals("not") ? "not " + expr(u.operand(), 3) : "-" + expr(u.operand(), 7);
            case Expr.Binary b -> {
                int p = precedence(b);
                // left-associative: the right operand needs parens at equal precedence
                yield expr(b.left(), p) + " " + b.op() + " " + expr(b.right(), p + 1);
            }
            case Expr.Call c -> {
                StringBuilder sb = new StringBuilder(c.function()).append('(');
                for (int i = 0; i < c.args().size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(expr(c.args().get(i), 0));
                }
                yield sb.append(')').toString();
            }
            case Expr.Ternary t -> expr(t.cond(), 1) + " ? " + expr(t.ifTrue(), 0) + " : " + expr(t.ifFalse(), 0);
        };
        return precedence(e) < parentPrecedence ? "(" + s + ")" : s;
    }

    private static String literal(Object v) {
        if (v instanceof String s) return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
        if (v instanceof Double d) {
            if (d == Math.rint(d) && Math.abs(d) < 1e15) return String.valueOf(d.longValue());
            return String.valueOf(d);
        }
        return String.valueOf(v);
    }

    // ---- text

    public static String text(Text t) {
        StringBuilder sb = new StringBuilder();
        for (Text.Part p : t.parts()) {
            switch (p) {
                case Text.Part.Plain pl -> sb.append(pl.text().replace("{", "{{").replace("}", "}}").replace("[", "[[").replace("]", "]]"));
                case Text.Part.Interp in -> {
                    if (in.expr() instanceof Expr.Call c && c.args().isEmpty() && (c.function().equals("player") || c.function().equals("npc"))) {
                        sb.append('{').append(c.function()).append('}');
                    } else {
                        sb.append('{').append(expr(in.expr())).append('}');
                    }
                }
                case Text.Part.Pick pk -> {
                    sb.append('[');
                    for (int i = 0; i < pk.choices().size(); i++) {
                        if (i > 0) sb.append('|');
                        sb.append(text(pk.choices().get(i)));
                    }
                    sb.append(']');
                }
            }
        }
        return sb.toString();
    }

    // ---- dialogue

    public static String dialogue(Dialogue d) {
        StringBuilder sb = new StringBuilder();
        for (String b : d.bindings()) sb.append("npc: ").append(b).append('\n');
        for (Dialogue.Start s : d.starts()) {
            boolean defaultStart = s.condition() == null && d.starts().size() == 1 && s.node().equals(d.nodes().keySet().iterator().next());
            if (defaultStart) continue;
            sb.append("start: ").append(s.node());
            if (s.condition() != null) sb.append(" when ").append(expr(s.condition()));
            sb.append('\n');
        }
        if (d.speaker() != null) sb.append("speaker: ").append(d.speaker()).append('\n');
        if (d.title() != null) sb.append("title: ").append(d.title()).append('\n');
        if (d.scope() != null && !d.scope().equals(d.id())) sb.append("scope: ").append(d.scope()).append('\n');
        for (Map.Entry<String, String> e : d.otherDirectives().entrySet()) sb.append(e.getKey()).append(": ").append(e.getValue()).append('\n');
        for (String inc : d.includes()) sb.append("include: ").append(inc).append('\n');
        for (Node n : d.nodeList()) {
            sb.append('\n').append("== ").append(n.name()).append('\n');
            block(n.body(), 0, sb);
        }
        return sb.toString();
    }

    public static String block(List<Statement> body) {
        StringBuilder sb = new StringBuilder();
        block(body, 0, sb);
        return sb.toString();
    }

    private static void block(List<Statement> body, int indent, StringBuilder sb) {
        String pad = " ".repeat(indent);
        for (Statement s : body) {
            switch (s) {
                case Statement.Line l -> {
                    String t = text(l.text());
                    if (l.speaker() != null) {
                        sb.append(pad).append(l.speaker()).append(": ").append(t).append('\n');
                    } else {
                        boolean escape = t.startsWith("->") || t.startsWith("<<") || t.startsWith("==") || t.startsWith("\\") || LOOKS_LIKE_SPEAKER.matcher(t).matches();
                        sb.append(pad).append(escape ? "\\" : "").append(t).append('\n');
                    }
                }
                case Statement.Choice c -> {
                    for (Option o : c.options()) {
                        sb.append(pad).append("-> ").append(text(o.text()));
                        if (o.guard() != null) sb.append(" <<if ").append(expr(o.guard())).append(">>");
                        if (o.showGuard() != null) sb.append(" <<show if ").append(expr(o.showGuard())).append(">>");
                        if (o.once()) sb.append(" <<once>>");
                        sb.append('\n');
                        block(o.body(), indent + 4, sb);
                    }
                }
                case Statement.Conditional c -> {
                    boolean first = true;
                    for (Statement.Branch b : c.branches()) {
                        if (first) sb.append(pad).append("<<if ").append(expr(b.condition())).append(">>\n");
                        else if (b.condition() != null) sb.append(pad).append("<<elseif ").append(expr(b.condition())).append(">>\n");
                        else sb.append(pad).append("<<else>>\n");
                        first = false;
                        block(b.body(), indent + 2, sb);
                    }
                    sb.append(pad).append("<<endif>>\n");
                }
                case Statement.Once o -> {
                    sb.append(pad).append("<<once>>\n");
                    block(o.body(), indent + 2, sb);
                    sb.append(pad).append("<<endonce>>\n");
                }
                case Statement.Random r -> {
                    sb.append(pad).append("<<random>>\n");
                    for (int i = 0; i < r.alternatives().size(); i++) {
                        if (i > 0) sb.append(pad).append("<<or>>\n");
                        block(r.alternatives().get(i), indent + 2, sb);
                    }
                    sb.append(pad).append("<<endrandom>>\n");
                }
                case Statement.Set set -> sb.append(pad).append("<<set ").append(expr(set.target())).append(" = ").append(expr(set.value())).append(">>\n");
                case Statement.Jump j -> sb.append(pad).append("<<jump ").append(j.node()).append(">>\n");
                case Statement.End e -> sb.append(pad).append("<<end>>\n");
                case Statement.Input in -> sb.append(pad).append("<<input ").append(expr(in.target())).append(' ').append(arg(text(in.prompt()))).append(">>\n");
                case Statement.Wait w -> sb.append(pad).append("<<wait ").append(expr(w.seconds())).append(">>\n");
                case Statement.Command cmd -> {
                    sb.append(pad).append("<<").append(cmd.name());
                    for (Text a : cmd.args()) sb.append(' ').append(arg(text(a)));
                    sb.append(">>\n");
                }
            }
        }
    }

    /** Quote a command argument when it has spaces or quotes. */
    public static String arg(String s) {
        if (s.isEmpty() || s.chars().anyMatch(Character::isWhitespace) || s.indexOf('"') >= 0 || s.indexOf('\'') >= 0 || s.indexOf('>') >= 0) {
            return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
        return s;
    }
}
