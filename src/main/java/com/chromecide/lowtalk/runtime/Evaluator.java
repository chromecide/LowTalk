package com.chromecide.lowtalk.runtime;

import com.chromecide.lowtalk.model.Expr;
import com.chromecide.lowtalk.model.Text;

import java.util.ArrayList;
import java.util.List;

/** Evaluates expressions and renders interpolated text against a Context. */
public final class Evaluator {

    private Evaluator() {}

    public static Object eval(Expr e, Context ctx) {
        return switch (e) {
            case Expr.Literal l -> l.value();
            case Expr.Var v -> {
                Object o = Values.normalise(ctx.getVar(v.scope(), v.name()));
                yield o == null ? Boolean.FALSE : o;
            }
            case Expr.Unary u -> {
                Object o = eval(u.operand(), ctx);
                yield switch (u.op()) {
                    case "not" -> !Values.truthy(o);
                    case "-" -> -Values.number(o);
                    default -> throw new RuntimeError("unknown unary operator " + u.op());
                };
            }
            case Expr.Binary b -> evalBinary(b, ctx);
            case Expr.Call c -> {
                List<Object> args = new ArrayList<>(c.args().size());
                for (Expr a : c.args()) args.add(eval(a, ctx));
                yield Values.normalise(ctx.call(c.function(), args));
            }
        };
    }

    private static Object evalBinary(Expr.Binary b, Context ctx) {
        switch (b.op()) {
            case "and" -> {
                return Values.truthy(eval(b.left(), ctx)) && Values.truthy(eval(b.right(), ctx));
            }
            case "or" -> {
                return Values.truthy(eval(b.left(), ctx)) || Values.truthy(eval(b.right(), ctx));
            }
            default -> {}
        }
        Object l = eval(b.left(), ctx);
        Object r = eval(b.right(), ctx);
        return switch (b.op()) {
            case "==" -> Values.equal(l, r);
            case "!=" -> !Values.equal(l, r);
            case "<" -> Values.number(l) < Values.number(r);
            case "<=" -> Values.number(l) <= Values.number(r);
            case ">" -> Values.number(l) > Values.number(r);
            case ">=" -> Values.number(l) >= Values.number(r);
            case "+" -> (l instanceof String || r instanceof String) && !(bothNumeric(l, r))
                    ? Values.text(l) + Values.text(r)
                    : Values.number(l) + Values.number(r);
            case "-" -> Values.number(l) - Values.number(r);
            case "*" -> Values.number(l) * Values.number(r);
            case "/" -> {
                double d = Values.number(r);
                if (d == 0.0) throw new RuntimeError("division by zero");
                yield Values.number(l) / d;
            }
            default -> throw new RuntimeError("unknown operator " + b.op());
        };
    }

    private static boolean bothNumeric(Object l, Object r) {
        return isNumeric(l) && isNumeric(r);
    }

    private static boolean isNumeric(Object o) {
        if (o instanceof Number) return true;
        if (o instanceof String s) {
            try {
                Double.parseDouble(s.trim());
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return false;
    }

    public static String render(Text text, Context ctx) {
        if (text.isStatic()) {
            return text.debugString();
        }
        StringBuilder sb = new StringBuilder();
        for (Text.Part p : text.parts()) {
            if (p instanceof Text.Part.Plain pl) sb.append(pl.text());
            else if (p instanceof Text.Part.Interp in) sb.append(Values.text(eval(in.expr(), ctx)));
        }
        return sb.toString();
    }
}
