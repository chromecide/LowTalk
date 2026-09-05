package com.chromecide.lowtalk.parser;

import com.chromecide.lowtalk.model.Dialogue;
import com.chromecide.lowtalk.model.Expr;
import com.chromecide.lowtalk.model.Option;
import com.chromecide.lowtalk.model.Pos;
import com.chromecide.lowtalk.model.Statement;
import com.chromecide.lowtalk.model.Text;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Parser coverage for text variation, random blocks, once-options, ternaries, wait, and include. */
class FormatAdditionsParserTest {

    private static final Pos POS = new Pos("t.talk", 1);

    private static Dialogue parse(String src) {
        return DialogueParser.parse("t.talk", src);
    }

    @Test
    void ternaryExpression() {
        Expr e = ExprParser.parse("$a ? 1 : 2", POS);
        Expr.Ternary t = assertInstanceOf(Expr.Ternary.class, e);
        assertInstanceOf(Expr.Var.class, t.cond());
        assertEquals(1.0, ((Expr.Literal) t.ifTrue()).value());
        assertEquals(2.0, ((Expr.Literal) t.ifFalse()).value());

        Expr nested = ExprParser.parse("$a ? $b ? 1 : 2 : 3", POS);
        Expr.Ternary outer = assertInstanceOf(Expr.Ternary.class, nested);
        assertInstanceOf(Expr.Ternary.class, outer.ifTrue());

        Expr inCall = ExprParser.parse("plural($n, $n > 1 ? \"loaves\" : \"loaf\")", POS);
        assertInstanceOf(Expr.Call.class, inCall);
        assertThrows(ParseException.class, () -> ExprParser.parse("$a ? 1", POS));
    }

    @Test
    void textVariation() {
        Text t = TextParser.parse("[Hi|Hello|Hey], {player}", POS);
        assertEquals(3, t.parts().size());
        Text.Part.Pick pick = assertInstanceOf(Text.Part.Pick.class, t.parts().get(0));
        assertEquals(3, pick.choices().size());
        assertEquals("Hello", pick.choices().get(1).debugString());
        assertFalse(t.isStatic());

        Text literal = TextParser.parse("Press [[E]] to open", POS);
        assertTrue(literal.isStatic());
        assertEquals("Press [E] to open", literal.debugString());

        Text withInterp = TextParser.parse("[{$n} coins|nothing]", POS);
        Text.Part.Pick p2 = assertInstanceOf(Text.Part.Pick.class, withInterp.parts().get(0));
        assertEquals(2, p2.choices().size());

        assertThrows(ParseException.class, () -> TextParser.parse("[only one]", POS));
        assertThrows(ParseException.class, () -> TextParser.parse("[a|b", POS));
        assertThrows(ParseException.class, () -> TextParser.parse("stray ] here", POS));
    }

    @Test
    void randomBlock() {
        Dialogue d = parse("""
                == a
                <<random>>
                  One.
                <<or>>
                  Two.
                  <<set $x = 1>>
                <<or>>
                  Three.
                <<endrandom>>
                Done.
                """);
        List<Statement> body = d.node("a").body();
        Statement.Random r = assertInstanceOf(Statement.Random.class, body.get(0));
        assertEquals(3, r.alternatives().size());
        assertEquals(2, r.alternatives().get(1).size());
        assertInstanceOf(Statement.Line.class, body.get(1));

        assertThrows(ParseException.class, () -> parse("== a\n<<or>>\nx\n"));
        assertThrows(ParseException.class, () -> parse("== a\n<<random>>\nx\n"));
        assertThrows(ParseException.class, () -> parse("== a\n<<random 3>>\nx\n<<endrandom>>\n"));
    }

    @Test
    void waitStatement() {
        Dialogue d = parse("== a\nOne.\n<<wait 2.5>>\nTwo.\n");
        Statement.Wait w = assertInstanceOf(Statement.Wait.class, d.node("a").body().get(1));
        assertEquals(2.5, ((Expr.Literal) w.seconds()).value());
        assertThrows(ParseException.class, () -> parse("== a\n<<wait>>\n"));
    }

    @Test
    void onceOptionsInAnyModifierOrder() {
        Dialogue d = parse("""
                == a
                -> Plain
                    x
                -> Secret <<once>>
                    y
                -> Both <<once>> <<if $met>>
                    z
                -> Reversed <<if $met>> <<once>>
                    w
                -> Greyed <<show if $rich>> <<once>>
                    v
                """);
        List<Option> options = ((Statement.Choice) d.node("a").body().get(0)).options();
        assertFalse(options.get(0).once());
        assertTrue(options.get(1).once());
        assertNull(options.get(1).guard());
        assertEquals("Secret", options.get(1).text().debugString());
        assertTrue(options.get(2).once());
        assertNotNull(options.get(2).guard());
        assertEquals("Both", options.get(2).text().debugString());
        assertTrue(options.get(3).once());
        assertNotNull(options.get(3).guard());
        assertTrue(options.get(4).once());
        assertNotNull(options.get(4).showGuard());
        assertNotEquals(options.get(1).onceKey(), options.get(2).onceKey());

        assertThrows(ParseException.class, () -> parse("== a\n-> Twice <<once>> <<once>>\n    x\n"));
        assertThrows(ParseException.class, () -> parse("== a\n-> Args <<once now>>\n    x\n"));
    }

    @Test
    void includesMergeNodesWithLocalWinning() {
        Map<String, String> files = Map.of(
                "dir/_shared.talk", "== goodbye\nBye from shared.\n<<end>>\n== hello\nShared hello.\n",
                "dir/_more.talk", "include: _shared\n== extra\nExtra.\n"
        );
        DialogueParser.IncludeResolver resolver = files::get;
        Dialogue d = DialogueParser.parse("dir/main.talk",
                "npc: X\ninclude: _more\n== hello\nLocal hello.\n<<jump goodbye>>\n", resolver);
        assertEquals(List.of("_more"), d.includes());
        assertTrue(d.nodes().containsKey("goodbye"), "node from a nested include");
        assertTrue(d.nodes().containsKey("extra"));
        Statement.Line local = (Statement.Line) d.node("hello").body().get(0);
        assertEquals("Local hello.", local.text().debugString(), "the including file's node wins");
        assertEquals("dir/_shared.talk", d.node("goodbye").pos().file(), "included nodes keep their own file for errors");
        assertTrue(new Validator().validate(d).stream().noneMatch(Validator.Problem::error));

        ParseException missing = assertThrows(ParseException.class,
                () -> DialogueParser.parse("dir/main.talk", "include: nope\n== a\nx\n", resolver));
        assertTrue(missing.getMessage().contains("nope"));

        Map<String, String> loop = Map.of(
                "a.talk", "include: b\n== a\nx\n",
                "b.talk", "include: a\n== b\ny\n");
        assertThrows(ParseException.class, () -> DialogueParser.parse("a.talk", loop.get("a.talk"), loop::get));
    }

    @Test
    void includePathsAreRelativeToTheIncludingFile() {
        assertEquals("dir/_shared.talk", DialogueParser.resolveInclude("dir/main.talk", "_shared"));
        assertEquals("dir/_shared.talk", DialogueParser.resolveInclude("dir/main.talk", "_shared.talk"));
        assertEquals("_shared.talk", DialogueParser.resolveInclude("main.talk", "_shared"));
    }

    @Test
    void validatorWarningsForNewStatements() {
        Dialogue d = parse("npc: X\n== a\n<<random>>\n  Only one.\n<<endrandom>>\n<<wait 60>>\nDone.\n");
        List<Validator.Problem> problems = new Validator().validate(d);
        assertTrue(problems.stream().anyMatch(p -> !p.error() && p.message().contains("only one alternative")));
        assertTrue(problems.stream().anyMatch(p -> !p.error() && p.message().contains("outside 0 to 30")));
        assertTrue(problems.stream().noneMatch(Validator.Problem::error));

        Dialogue ok = parse("npc: X\n== a\nHi {$x ? \"a\" : \"b\"} [c|d].\n<<set $x = 1>>\n");
        assertTrue(new Validator().validate(ok).stream().noneMatch(Validator.Problem::error));
    }
}
