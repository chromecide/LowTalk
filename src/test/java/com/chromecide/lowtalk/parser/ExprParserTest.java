package com.chromecide.lowtalk.parser;

import com.chromecide.lowtalk.model.Expr;
import com.chromecide.lowtalk.model.Pos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExprParserTest {

    private static final Pos POS = new Pos("test.talk", 1);

    private static Expr p(String s) {
        return ExprParser.parse(s, POS);
    }

    @Test
    void literals() {
        assertEquals(new Expr.Literal(1.0), p("1"));
        assertEquals(new Expr.Literal(2.5), p("2.5"));
        assertEquals(new Expr.Literal("hi"), p("\"hi\""));
        assertEquals(new Expr.Literal("it's"), p("'it\\'s'"));
        assertEquals(new Expr.Literal(true), p("true"));
        assertEquals(new Expr.Literal(false), p("false"));
    }

    @Test
    void variableScopes() {
        assertEquals(new Expr.Var("local", "met"), p("$met"));
        assertEquals(new Expr.Var("player", "met"), p("$player.met"));
        assertEquals(new Expr.Var("npc", "helpers"), p("$npc.helpers"));
        assertEquals(new Expr.Var("world", "season"), p("$world.season"));
        assertEquals(new Expr.Var("tmp", "answer"), p("$tmp.answer"));
        // Unknown prefix is just a dotted player variable.
        assertEquals(new Expr.Var("local", "quest.stage"), p("$quest.stage"));
    }

    @Test
    void precedence() {
        // 1 + 2 * 3 == 7 and not false
        Expr e = p("1 + 2 * 3 == 7 and not false");
        assertInstanceOf(Expr.Binary.class, e);
        Expr.Binary and = (Expr.Binary) e;
        assertEquals("and", and.op());
        Expr.Binary eq = (Expr.Binary) and.left();
        assertEquals("==", eq.op());
        Expr.Binary plus = (Expr.Binary) eq.left();
        assertEquals("+", plus.op());
        assertEquals("*", ((Expr.Binary) plus.right()).op());
        assertEquals(new Expr.Unary("not", new Expr.Literal(false)), and.right());
    }

    @Test
    void orIsLowerThanAnd() {
        Expr.Binary e = (Expr.Binary) p("$a or $b and $c");
        assertEquals("or", e.op());
        assertEquals("and", ((Expr.Binary) e.right()).op());
    }

    @Test
    void parenthesesAndUnaryMinus() {
        Expr.Binary e = (Expr.Binary) p("($a or $b) and -$c < 0");
        assertEquals("and", e.op());
        assertEquals("or", ((Expr.Binary) e.left()).op());
        Expr.Binary lt = (Expr.Binary) e.right();
        assertEquals(new Expr.Unary("-", new Expr.Var("local", "c")), lt.left());
    }

    @Test
    void calls() {
        assertEquals(new Expr.Call("hour", List.of()), p("hour()"));
        assertEquals(new Expr.Call("has", List.of(new Expr.Literal("Food_Bread"), new Expr.Literal(2.0))), p("has(\"Food_Bread\", 2)"));
        Expr.Binary e = (Expr.Binary) p("objective(\"Q\") == \"complete\"");
        assertEquals("==", e.op());
    }

    @Test
    void bareWordIsAnError() {
        ParseException ex = assertThrows(ParseException.class, () -> p("met"));
        assertTrue(ex.getMessage().contains("$met"), ex.getMessage());
    }

    @Test
    void trailingGarbageIsAnError() {
        assertThrows(ParseException.class, () -> p("1 2"));
        assertThrows(ParseException.class, () -> p("$a =="));
        assertThrows(ParseException.class, () -> p("has(\"x\""));
        assertThrows(ParseException.class, () -> p("\"unterminated"));
        assertThrows(ParseException.class, () -> p("$"));
    }
}
