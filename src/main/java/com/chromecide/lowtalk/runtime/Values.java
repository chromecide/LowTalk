package com.chromecide.lowtalk.runtime;

/** Value semantics shared by the evaluator and the Hytale layer. Values are Double, String, Boolean, or null. */
public final class Values {

    private Values() {}

    public static boolean truthy(Object v) {
        if (v == null) return false;
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.doubleValue() != 0.0;
        if (v instanceof String s) return !s.isEmpty();
        return true;
    }

    public static double number(Object v) {
        if (v == null) return 0.0;
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof Boolean b) return b ? 1.0 : 0.0;
        if (v instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException e) {
                throw new RuntimeError("'" + s + "' is not a number");
            }
        }
        throw new RuntimeError("cannot treat " + v + " as a number");
    }

    /** How a value reads inside text: 3.0 -> "3", true -> "true", null -> "". */
    public static String text(Object v) {
        if (v == null) return "";
        if (v instanceof Double d) {
            if (d == Math.rint(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
                return String.valueOf(d.longValue());
            }
            return String.valueOf(d);
        }
        return String.valueOf(v);
    }

    /** Loose equality: numbers by value, strings by content, booleans by truthiness with booleans. */
    public static boolean equal(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return !truthy(a) && !truthy(b);
        if (a instanceof Number || b instanceof Number) {
            try {
                return number(a) == number(b);
            } catch (RuntimeError e) {
                return false;
            }
        }
        if (a instanceof Boolean || b instanceof Boolean) {
            return truthy(a) == truthy(b);
        }
        return a.toString().equals(b.toString());
    }

    /** Normalise anything a plugin hands us into one of the four value types. */
    public static Object normalise(Object v) {
        if (v == null || v instanceof Double || v instanceof String || v instanceof Boolean) return v;
        if (v instanceof Number n) return n.doubleValue();
        return v.toString();
    }
}
