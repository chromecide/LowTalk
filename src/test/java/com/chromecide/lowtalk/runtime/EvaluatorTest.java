package com.chromecide.lowtalk.runtime;

import com.chromecide.lowtalk.model.Pos;
import com.chromecide.lowtalk.parser.ExprParser;
import com.chromecide.lowtalk.parser.TextParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EvaluatorTest {

    private static final Pos POS = new Pos("t.talk", 1);
    private final FakeContext ctx = new FakeContext();

    private Object eval(String s) {
        return Evaluator.eval(ExprParser.parse(s, POS), ctx);
    }

    @Test
    void arithmeticAndComparison() {
        assertEquals(7.0, eval("1 + 2 * 3"));
        assertEquals(2.0, eval("(1 + 3) / 2"));
        assertEquals(-4.0, eval("-4"));
        assertEquals(true, eval("3 > 2 and 2 >= 2"));
        assertEquals(false, eval("3 < 2 or 1 == 2"));
        assertEquals(true, eval("not false"));
    }

    @Test
    void unsetVariablesAreFalseAndZero() {
        assertEquals(false, eval("$missing"));
        assertEquals(true, eval("not $missing"));
        assertEquals(1.0, eval("$missing + 1"));
        assertEquals(true, eval("$missing == false"));
    }

    @Test
    void stringsConcatenateAndCompare() {
        ctx.setVar("local", "name", "Bram");
        assertEquals("Hi Bram", eval("\"Hi \" + $name"));
        assertEquals(true, eval("$name == \"Bram\""));
        assertEquals(true, eval("$name != \"bram\""));
        assertEquals("a3", eval("\"a\" + 3"));
    }

    @Test
    void numericStringsStillAdd() {
        ctx.setVar("local", "n", "2");
        assertEquals(5.0, eval("$n + 3"));
    }

    @Test
    void divisionByZeroIsAnError() {
        assertThrows(RuntimeError.class, () -> eval("1 / 0"));
    }

    @Test
    void functions() {
        ctx.functions.put("has", a -> a.get(0).equals("Food_Bread") && ((Double) a.get(1)) <= 3);
        assertEquals(true, eval("has(\"Food_Bread\", 2)"));
        assertEquals(false, eval("has(\"Food_Bread\", 4)"));
        RuntimeError e = assertThrows(RuntimeError.class, () -> eval("nope()"));
        assertTrue(e.getMessage().contains("nope"));
    }

    @Test
    void textRendering() {
        ctx.setVar("local", "coins", 3.0);
        ctx.setVar("local", "half", 2.5);
        String s = Evaluator.render(TextParser.parse("{player} meets {npc} with {$coins} coins and {$half} more, {{braces}}", POS), ctx);
        assertEquals("Chromecide meets Rootling Merchant with 3 coins and 2.5 more, {braces}", s);
    }
}
