package com.chromecide.lowtalk.parser;

import com.chromecide.lowtalk.model.Dialogue;
import com.chromecide.lowtalk.model.Expr;
import com.chromecide.lowtalk.model.Node;
import com.chromecide.lowtalk.model.Option;
import com.chromecide.lowtalk.model.Statement;
import com.chromecide.lowtalk.model.Text;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DialogueParserTest {

    private static Dialogue parse(String src) {
        return DialogueParser.parse("test.talk", src);
    }

    @Test
    void headerDirectives() {
        Dialogue d = parse("""
                npc: Kweebec_Merchant
                npc: @elder
                start: returning when $met
                start: first
                speaker: Merchant
                title: The Merchant
                scope: village
                mood: grumpy

                == first
                Hello.
                == returning
                Again.
                """);
        assertEquals("test", d.id());
        assertEquals(List.of("Kweebec_Merchant", "@elder"), d.bindings());
        assertEquals(2, d.starts().size());
        assertEquals("returning", d.starts().get(0).node());
        assertEquals(new Expr.Var("player", "met"), d.starts().get(0).condition());
        assertEquals("first", d.starts().get(1).node());
        assertNull(d.starts().get(1).condition());
        assertEquals("Merchant", d.speaker());
        assertEquals("The Merchant", d.title());
        assertEquals("village", d.scope());
        assertEquals("grumpy", d.otherDirectives().get("mood"));
        assertEquals(List.of("first", "returning"), List.copyOf(d.nodes().keySet()));
    }

    @Test
    void defaultsWhenHeaderIsEmpty() {
        Dialogue d = parse("== only\nHi.\n");
        assertEquals("test", d.scope());
        assertEquals(1, d.starts().size());
        assertEquals("only", d.starts().get(0).node());
        assertTrue(d.bindings().isEmpty());
    }

    @Test
    void linesWithAndWithoutSpeaker() {
        Node n = parse("== a\nHello there.\nElder: Sit down.\n\\Note: not a speaker\n").node("a");
        assertEquals(3, n.body().size());
        Statement.Line l1 = (Statement.Line) n.body().get(0);
        assertNull(l1.speaker());
        assertEquals("Hello there.", l1.text().debugString());
        Statement.Line l2 = (Statement.Line) n.body().get(1);
        assertEquals("Elder", l2.speaker());
        assertEquals("Sit down.", l2.text().debugString());
        Statement.Line l3 = (Statement.Line) n.body().get(2);
        assertNull(l3.speaker());
        assertEquals("Note: not a speaker", l3.text().debugString());
    }

    @Test
    void interpolation() {
        Statement.Line l = (Statement.Line) parse("== a\nHi {player}, {npc} here. You have {$coins + 1} coins {{literal}}.\n").node("a").body().get(0);
        List<Text.Part> parts = l.text().parts();
        assertEquals(new Text.Part.Plain("Hi "), parts.get(0));
        assertEquals(new Text.Part.Interp(new Expr.Call("player", List.of())), parts.get(1));
        assertEquals(new Text.Part.Interp(new Expr.Call("npc", List.of())), parts.get(3));
        assertInstanceOf(Expr.Binary.class, ((Text.Part.Interp) parts.get(5)).expr());
        assertEquals(new Text.Part.Plain(" coins {literal}."), parts.get(6));
        assertFalse(l.text().isStatic());
    }

    @Test
    void optionsWithBodiesAndGuards() {
        Node n = parse("""
                == hub
                What now?
                -> Buy something
                    Here you go.
                    <<shop>>
                -> Secret <<if $knows>>
                    <<jump secret>>
                -> Greyed <<show if has("Key")>>
                    Locked.
                -> Leave
                    <<end>>
                == secret
                Shh.
                """).node("hub");
        assertEquals(2, n.body().size());
        Statement.Choice c = (Statement.Choice) n.body().get(1);
        assertEquals(4, c.options().size());

        Option buy = c.options().get(0);
        assertEquals("Buy something", buy.text().debugString());
        assertNull(buy.guard());
        assertEquals(2, buy.body().size());
        assertInstanceOf(Statement.Line.class, buy.body().get(0));
        assertEquals("shop", ((Statement.Command) buy.body().get(1)).name());

        Option secret = c.options().get(1);
        assertEquals("Secret", secret.text().debugString());
        assertEquals(new Expr.Var("player", "knows"), secret.guard());
        assertEquals(new Statement.Jump(secret.body().get(0).pos(), "secret"), secret.body().get(0));

        Option greyed = c.options().get(2);
        assertNull(greyed.guard());
        assertInstanceOf(Expr.Call.class, greyed.showGuard());

        Option leave = c.options().get(3);
        assertInstanceOf(Statement.End.class, leave.body().get(0));
    }

    @Test
    void optionBodyEndsWhenIndentDrops() {
        Node n = parse("""
                == a
                -> One
                    Inside one.
                -> Two
                    Inside two.
                After both (new choice group is not created, this is a line after a choice).
                """).node("a");
        // Choice, then the trailing line.
        assertEquals(2, n.body().size());
        Statement.Choice c = (Statement.Choice) n.body().get(0);
        assertEquals(2, c.options().size());
        assertEquals(1, c.options().get(0).body().size());
        assertEquals(1, c.options().get(1).body().size());
        assertInstanceOf(Statement.Line.class, n.body().get(1));
    }

    @Test
    void conditionals() {
        Node n = parse("""
                == a
                <<if $met>>
                  Back again.
                <<elseif has("Food_Bread")>>
                  Bread!
                <<else>>
                  Hello.
                  <<set $met = true>>
                <<endif>>
                Done.
                """).node("a");
        assertEquals(2, n.body().size());
        Statement.Conditional c = (Statement.Conditional) n.body().get(0);
        assertEquals(3, c.branches().size());
        assertEquals(new Expr.Var("player", "met"), c.branches().get(0).condition());
        assertInstanceOf(Expr.Call.class, c.branches().get(1).condition());
        assertNull(c.branches().get(2).condition());
        assertEquals(2, c.branches().get(2).body().size());
        Statement.Set set = (Statement.Set) c.branches().get(2).body().get(1);
        assertEquals(new Expr.Var("player", "met"), set.target());
        assertEquals(new Expr.Literal(true), set.value());
    }

    @Test
    void nestedConditionalInsideOption() {
        Node n = parse("""
                == a
                -> Ask
                    <<if $x>>
                      Yes.
                    <<else>>
                      No.
                    <<endif>>
                    <<jump a>>
                -> Go
                    <<end>>
                """).node("a");
        Statement.Choice c = (Statement.Choice) n.body().get(0);
        assertEquals(2, c.options().size());
        List<Statement> ask = c.options().get(0).body();
        assertEquals(2, ask.size());
        assertInstanceOf(Statement.Conditional.class, ask.get(0));
        assertInstanceOf(Statement.Jump.class, ask.get(1));
    }

    @Test
    void onceBlock() {
        Node n = parse("== a\n<<once>>\n  First time.\n<<endonce>>\nEvery time.\n").node("a");
        assertEquals(2, n.body().size());
        Statement.Once once = (Statement.Once) n.body().get(0);
        assertEquals(1, once.body().size());
    }

    @Test
    void commandsAndArguments() {
        Node n = parse("""
                == a
                <<give Food_Bread 2>>
                <<attitude friendly>>
                <<run "/give {player} Food_Bread 1">>
                <<input $tmp.name "What is your name?">>
                <<custom "two words" plain {$x}>>
                """).node("a");
        Statement.Command give = (Statement.Command) n.body().get(0);
        assertEquals("give", give.name());
        assertEquals(List.of("Food_Bread", "2"), give.args().stream().map(Text::debugString).toList());

        Statement.Command run = (Statement.Command) n.body().get(2);
        assertEquals(1, run.args().size());
        assertFalse(run.args().get(0).isStatic());

        Statement.Input in = (Statement.Input) n.body().get(3);
        assertEquals(new Expr.Var("tmp", "name"), in.target());
        assertEquals("What is your name?", in.prompt().debugString());

        Statement.Command custom = (Statement.Command) n.body().get(4);
        assertEquals(3, custom.args().size());
        assertEquals("two words", custom.args().get(0).debugString());
        assertFalse(custom.args().get(2).isStatic());
    }

    @Test
    void commentsAreStrippedButNotInsideQuotes() {
        Node n = parse("== a # trailing\n# whole line\nHello. # comment\n<<run \"/say # not a comment\">>\n").node("a");
        assertEquals(2, n.body().size());
        assertEquals("Hello.", ((Statement.Line) n.body().get(0)).text().debugString());
        assertEquals("/say # not a comment", ((Statement.Command) n.body().get(1)).args().get(0).debugString());
    }

    @Test
    void errorsCarryLineNumbers() {
        ParseException e = assertThrows(ParseException.class, () -> parse("== a\nHi.\n== a\nAgain.\n"));
        assertEquals(3, e.getPos().line());
        assertTrue(e.getMessage().contains("defined twice"));

        e = assertThrows(ParseException.class, () -> parse("== a\n<<if $x>>\nnever closed\n"));
        assertTrue(e.getMessage().contains("endif"));

        e = assertThrows(ParseException.class, () -> parse("== a\n<<endif>>\n"));
        assertEquals(2, e.getPos().line());

        e = assertThrows(ParseException.class, () -> parse("== a\n<<set met = true>>\n"));
        assertTrue(e.getMessage().contains("set"));

        e = assertThrows(ParseException.class, () -> parse("== a\n\tTabbed.\n"));
        assertTrue(e.getMessage().contains("tabs"));

        e = assertThrows(ParseException.class, () -> parse("no header here\n== a\nHi.\n"));
        assertEquals(1, e.getPos().line());

        e = assertThrows(ParseException.class, () -> parse("== a\nHi {unclosed\n"));
        assertTrue(e.getMessage().contains("unclosed"));

        assertThrows(ParseException.class, () -> parse(""));
    }

    @Test
    void shippedExamplesParse() throws IOException {
        for (String name : List.of("rootling_merchant", "village_elder")) {
            Path p = Path.of("examples", name + ".talk");
            Dialogue d = DialogueParser.parse(p.toString(), Files.readString(p));
            assertEquals(name, d.id());
            assertFalse(d.nodes().isEmpty());
            assertTrue(new Validator().validate(d).stream().noneMatch(Validator.Problem::error), name);
        }
    }
}
