package com.chromecide.lowtalk.editor;

import com.chromecide.lowtalk.model.Expr;
import com.chromecide.lowtalk.parser.Printer;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;

/**
 * The shapes a condition usually takes, so the in-game editor can offer them as a thing, a comparison and a value
 * instead of a line of code. "The player has an item", "an objective is finished", "this NPC's attitude is
 * friendly": each is a subject the creator picks, an argument that belongs to it, and for some of them a
 * comparison and a value.
 *
 * <p>A condition that does not fit one of these shapes, anything with "and", "or", brackets or arithmetic in it,
 * is left as text for the creator to write by hand. The text is always the truth; this only reads and writes it.
 */
public final class ConditionShapes {

    /** What a condition is about. */
    public enum Kind {
        VARIABLE("a variable", null),
        ITEM("the player has", "has"),
        ITEM_COUNT("how many they have of", "count"),
        VISITED("they have seen the passage", "visited"),
        OBJECTIVE("the objective", "objective"),
        OBJECTIVE_LINE("they can start the objective line", "objective_line"),
        ATTITUDE("this NPC's attitude to them", "attitude"),
        REPUTATION("their standing with", "reputation"),
        RANK("their rank with", "rank"),
        STAT("their", "stat"),
        EFFECT("they are under", "effect"),
        KNOWS("they know how to make", "knows"),
        PERMISSION("they have permission", "perm"),
        HOUR("the hour of the day", "hour"),
        WEATHER("the weather they see", "weather"),
        CHANCE("a chance of", "chance");

        private final String label;
        private final String function;

        Kind(String label, @Nullable String function) {
            this.label = label;
            this.function = function;
        }

        public String label() {
            return label;
        }

        @Nullable
        public String function() {
            return function;
        }

        /** True when the condition is the whole answer by itself, with nothing to compare it to. */
        public boolean isYesOrNo() {
            return switch (this) {
                case ITEM, VISITED, OBJECTIVE_LINE, EFFECT, KNOWS, PERMISSION, CHANCE, VARIABLE -> true;
                default -> false;
            };
        }

        /** True when it may also be compared with something: a variable is either set or equal to a value. */
        public boolean canCompare() {
            return !isYesOrNo() || this == VARIABLE;
        }

        /** The argument that belongs to the subject, or null when it takes none. */
        @Nullable
        public CommandSpecs.Arg argument() {
            return switch (this) {
                case VARIABLE -> new CommandSpecs.Arg("variable", CommandSpecs.Type.CHOICE, null, List.of(), false, true);
                case ITEM, ITEM_COUNT -> new CommandSpecs.Arg("item", CommandSpecs.Type.ASSET, CommandSpecs.ITEMS, List.of(), false);
                case VISITED -> new CommandSpecs.Arg("passage", CommandSpecs.Type.CHOICE, null, List.of(), false);
                case OBJECTIVE -> new CommandSpecs.Arg("objective", CommandSpecs.Type.ASSET, CommandSpecs.OBJECTIVES, List.of(), false);
                case OBJECTIVE_LINE -> new CommandSpecs.Arg("line", CommandSpecs.Type.ASSET, CommandSpecs.OBJECTIVE_LINES, List.of(), false);
                case REPUTATION, RANK -> new CommandSpecs.Arg("group", CommandSpecs.Type.ASSET, CommandSpecs.REPUTATION_GROUPS, List.of(), true);
                case STAT -> new CommandSpecs.Arg("stat", CommandSpecs.Type.ASSET, CommandSpecs.STATS, List.of(), false);
                case EFFECT -> new CommandSpecs.Arg("effect", CommandSpecs.Type.ASSET, CommandSpecs.ENTITY_EFFECTS, List.of(), false);
                case KNOWS -> new CommandSpecs.Arg("recipe", CommandSpecs.Type.ASSET, CommandSpecs.RECIPES, List.of(), false);
                case PERMISSION -> new CommandSpecs.Arg("permission", CommandSpecs.Type.TEXT, null, List.of(), false);
                case CHANCE -> new CommandSpecs.Arg("in one", CommandSpecs.Type.NUMBER, null, List.of(), false);
                case ATTITUDE, HOUR, WEATHER -> null;
            };
        }

        /** What it is compared with, or null when this kind is only ever a yes or a no. */
        @Nullable
        public CommandSpecs.Arg value() {
            if (!canCompare()) return null;
            return switch (this) {
                case OBJECTIVE -> new CommandSpecs.Arg("is", CommandSpecs.Type.CHOICE, null,
                        List.of("none", "active", "complete"), false);
                case ATTITUDE -> new CommandSpecs.Arg("is", CommandSpecs.Type.CHOICE, null,
                        List.of("ignore", "hostile", "neutral", "friendly", "revered"), false);
                case WEATHER -> new CommandSpecs.Arg("is", CommandSpecs.Type.ASSET, CommandSpecs.WEATHERS, List.of(), false);
                case ITEM_COUNT, HOUR, REPUTATION -> new CommandSpecs.Arg("", CommandSpecs.Type.NUMBER, null, List.of(), false);
                default -> new CommandSpecs.Arg("", CommandSpecs.Type.TEXT, null, List.of(), false);
            };
        }
    }

    /**
     * One condition as the editor shows it.
     *
     * @param kind  what it is about
     * @param arg   the subject's own argument ("Food_Bread", "$met", a passage name); empty when it takes none
     * @param op    "" for a plain yes, "not" for a plain no, or a comparison such as "==" or "&gt;="
     * @param value what it is compared with; empty for a yes-or-no condition
     */
    public record Shape(Kind kind, String arg, String op, String value) {}

    /** The comparisons offered, as symbol and the words shown for it. */
    public static final List<String[]> COMPARISONS = List.of(
            new String[] {"==", "is"},
            new String[] {"!=", "is not"},
            new String[] {">", "is more than"},
            new String[] {">=", "is at least"},
            new String[] {"<", "is less than"},
            new String[] {"<=", "is at most"});

    /** The two answers a yes-or-no condition can want. */
    public static final List<String[]> YES_NO = List.of(
            new String[] {"", "yes"},
            new String[] {"not", "no"});

    private ConditionShapes() {}

    /** The shape of a condition, or null when it is too involved to show as fields. */
    @Nullable
    public static Shape read(@Nullable Expr e) {
        if (e == null) return null;
        String op = "";
        String value = "";
        Expr subject = e;
        if (subject instanceof Expr.Unary u) {
            if (!"not".equals(u.op())) return null;
            op = "not";
            subject = u.operand();
        }
        if (subject instanceof Expr.Binary b) {
            if (!op.isEmpty()) return null;     // "not (a == b)" is not one of our shapes
            if (COMPARISONS.stream().noneMatch(c -> c[0].equals(b.op()))) return null;
            op = b.op();
            value = plain(b.right());
            if (value == null) return null;
            subject = b.left();
        }
        Kind kind = kindOf(subject);
        if (kind == null) return null;
        // a yes-or-no condition compared with something, or a value condition with nothing to compare with
        if (comparesWithValue(op) && kind.value() == null) return null;
        if (!comparesWithValue(op) && !kind.isYesOrNo()) return null;
        String arg = argOf(subject, kind);
        return arg == null ? null : new Shape(kind, arg, op, value);
    }

    /** The condition as .talk text. */
    public static String write(Shape s) {
        String subject = switch (s.kind()) {
            case VARIABLE -> s.arg().isBlank() ? "$flag" : s.arg().trim();
            case ATTITUDE, HOUR, WEATHER -> s.kind().function() + "()";
            case REPUTATION, RANK -> s.arg().isBlank()
                    ? s.kind().function() + "()"
                    : s.kind().function() + "(" + quote(s.arg()) + ")";
            case CHANCE -> "chance(" + (s.arg().isBlank() ? "0.5" : s.arg().trim()) + ")";
            default -> s.kind().function() + "(" + quote(s.arg()) + ")";
        };
        if (s.value().isBlank()) return "not".equals(s.op()) ? "not " + subject : subject;
        String op = s.op().isBlank() || "not".equals(s.op()) ? "==" : s.op();
        return subject + " " + op + " " + literal(s.kind(), s.value());
    }

    /** The comparisons this kind may use: yes and no, a real comparison, or (for a variable) both. */
    public static List<String[]> comparisons(Kind kind) {
        if (!kind.canCompare()) return YES_NO;
        if (!kind.isYesOrNo()) return COMPARISONS;
        List<String[]> both = new java.util.ArrayList<>(YES_NO);
        both.addAll(COMPARISONS);
        return both;
    }

    /** True when this comparison compares with something, rather than just asking yes or no. */
    public static boolean comparesWithValue(String op) {
        return !op.isBlank() && !"not".equals(op);
    }

    @Nullable
    private static Kind kindOf(Expr subject) {
        if (subject instanceof Expr.Var) return Kind.VARIABLE;
        if (!(subject instanceof Expr.Call call)) return null;
        for (Kind k : Kind.values()) {
            if (k.function() != null && k.function().equals(call.function())) {
                CommandSpecs.Arg a = k.argument();
                int max = a == null ? 0 : 1;
                if (call.args().size() > max) return null;
                if (k == Kind.VARIABLE) return null;
                return k;
            }
        }
        return null;
    }

    /** The subject's own argument as editable text, or null when it is not a plain one. */
    @Nullable
    private static String argOf(Expr subject, Kind kind) {
        if (kind == Kind.VARIABLE) return Printer.expr(subject);
        Expr.Call call = (Expr.Call) subject;
        if (call.args().isEmpty()) return "";
        return plain(call.args().get(0));
    }

    /** A literal as the text to show in a field, or null when it is not a literal at all. */
    @Nullable
    private static String plain(Expr e) {
        if (!(e instanceof Expr.Literal lit)) return null;
        Object v = lit.value();
        if (v == null) return "";
        if (v instanceof Double d && d == Math.floor(d) && !d.isInfinite()) return String.valueOf(d.longValue());
        return String.valueOf(v);
    }

    /** Write a value back the way the language wants it: numbers and true/false bare, everything else quoted. */
    private static String literal(Kind kind, String value) {
        String v = value.trim();
        if (kind.value() != null && kind.value().type() == CommandSpecs.Type.NUMBER) return v.isEmpty() ? "0" : v;
        if (v.matches("-?\\d+(\\.\\d+)?")) return v;
        String lower = v.toLowerCase(Locale.ROOT);
        if (lower.equals("true") || lower.equals("false")) return lower;
        if (v.startsWith("$") || v.contains("(")) return v;      // another variable or a call, left as written
        return quote(v);
    }

    private static String quote(String s) {
        return "\"" + s.trim().replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
