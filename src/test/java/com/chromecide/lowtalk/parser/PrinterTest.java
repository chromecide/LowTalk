package com.chromecide.lowtalk.parser;

import com.chromecide.lowtalk.model.Dialogue;
import com.chromecide.lowtalk.model.Expr;
import com.chromecide.lowtalk.model.Pos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The printer must produce source that parses back to the same model. */
class PrinterTest {

    private static final Pos POS = new Pos("t.talk", 1);

    private static void roundTripExpr(String src) {
        Expr e = ExprParser.parse(src, POS);
        String printed = Printer.expr(e);
        Expr again = ExprParser.parse(printed, POS);
        assertEquals(e, again, "expression round trip: " + src + " -> " + printed);
    }

    @Test
    void expressionsRoundTrip() {
        roundTripExpr("$met");
        roundTripExpr("$player.stage >= 2 and not $done");
        roundTripExpr("(1 + 2) * 3 - 4 / 2");
        roundTripExpr("1 + (2 * 3)");
        roundTripExpr("1 - (2 - 3)");
        roundTripExpr("has(\"Food_Bread\", 2) or chance(0.5)");
        roundTripExpr("$a ? \"yes\" : \"no\"");
        roundTripExpr("($a or $b) ? 1 : $c ? 2 : 3");
        roundTripExpr("\"quote \\\" inside\" == $x");
        roundTripExpr("-$n * 2");
        roundTripExpr("not ($a and $b)");
    }

    @Test
    void textRoundTrip() {
        for (String src : new String[] {
                "Hello {player}, you hold {count(\"Food_Bread\")} bread.",
                "[Hi|Hello|Hey] there, {npc}.",
                "Braces {{like this}} and brackets [[like this]] stay literal.",
                "{$gold > 10 ? \"rich\" : \"poor\"} traveller",
        }) {
            var t = TextParser.parse(src, POS);
            String printed = Printer.text(t);
            assertEquals(t, TextParser.parse(printed, POS), src + " -> " + printed);
        }
    }

    @Test
    void wholeDialogueRoundTrip() {
        String src = """
                npc: Kweebec_Merchant
                npc: @elder
                start: returning when $met
                start: start
                speaker: Merchant
                title: The Merchant
                portrait: Portraits/merchant.png
                on: join

                == start
                Well met, {player}.
                Elder: Sit, child.
                \\Note: this line starts with a word and colon.
                <<set $met = true>>
                <<once>>
                  First time only.
                <<endonce>>
                <<random>>
                  One.
                <<or>>
                  Two.
                <<endrandom>>
                <<if $player.stage == 1>>
                  Stage one.
                <<elseif $player.stage == 2>>
                  Stage two.
                <<else>>
                  Later.
                <<endif>>
                <<give Food_Bread 2>>
                <<notify "Hello there" "detail" success>>
                <<input $tmp.name "What is your name?">>
                <<wait 2>>
                -> Trade <<if has("Food_Bread")>> <<once>>
                    <<take Food_Bread>>
                    <<jump start>>
                -> Greyed <<show if $rich>>
                    Nope.
                -> Leave
                    <<end>>

                == returning
                Back again?
                <<jump start>>
                """;
        Dialogue d = DialogueParser.parse("t.talk", src);
        String printed = Printer.dialogue(d);
        Dialogue again = DialogueParser.parse("t.talk", printed);
        // Positions differ (line numbers), so compare the printed forms and the shape.
        assertEquals(printed, Printer.dialogue(again), printed);
        assertEquals(d.bindings(), again.bindings());
        assertEquals(d.nodes().keySet(), again.nodes().keySet());
        assertEquals(d.speaker(), again.speaker());
        assertEquals(d.title(), again.title());
        assertEquals(d.otherDirectives(), again.otherDirectives());
        assertEquals(d.starts().size(), again.starts().size());
        assertTrue(new Validator().validate(again).stream().noneMatch(Validator.Problem::error));
    }
}
