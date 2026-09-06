package com.chromecide.lowtalk.hytale.json;

import com.chromecide.lowtalk.model.Dialogue;
import com.chromecide.lowtalk.model.Statement;
import com.chromecide.lowtalk.parser.DialogueParser;
import com.chromecide.lowtalk.parser.ParseException;
import com.chromecide.lowtalk.parser.Printer;
import com.chromecide.lowtalk.parser.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** .talk -> model -> JSON asset -> model must be lossless. Uses the plain data classes only, no game codecs. */
class JsonConvertTest {

    private static final String SRC = """
            npc: Kweebec_Merchant
            start: returning when $met
            start: start
            speaker: Merchant
            portrait: Portraits/m.png

            == start
            Hello {player}. [Nice|Fine] day.
            <<set $met = true>>
            <<if $player.stage == 1>>
              One.
            <<else>>
              Other.
            <<endif>>
            <<once>>
              Once.
            <<endonce>>
            <<random>>
              A.
            <<or>>
              B.
            <<endrandom>>
            <<give Food_Bread 2>>
            <<take Food_Bread>>
            <<sound SFX_Test>>
            <<effect HealthRegen_Buff_T1>>
            <<cure HealthRegen_Buff_T1>>
            <<objective Objective_Gather>>
            <<objective cancel Objective_Gather>>
            <<weather Zone1_Cloudy_Medium player>>
            <<weather clear>>
            <<notify "Hi {player}" "d" success>>
            <<notify "Plain" success>>
            <<title "Big" major 5>>
            <<input $tmp.name "Name?">>
            <<wait 1.5>>
            -> Trade <<if has("Food_Bread")>> <<once>>
                <<jump start>>
            -> Leave <<show if $rich>>
                <<end>>

            == returning
            Back.
            """;

    @Test
    void roundTripThroughAsset() {
        Dialogue d = DialogueParser.parse("t.talk", SRC);
        DialogueAsset asset = JsonConvert.toAsset(d);
        assertEquals("t", asset.getId());
        assertArrayEquals(new String[] {"Kweebec_Merchant"}, asset.npc);
        assertEquals("Merchant", asset.speaker);
        assertEquals("Portraits/m.png", asset.portrait);
        assertEquals(2, asset.start.size());
        assertEquals("$met", asset.start.get(0).when);
        assertEquals(2, asset.nodes.size());

        List<JsonStatement> body = asset.nodes.get(0).body;
        assertInstanceOf(JsonStatement.Say.class, body.get(0));
        assertEquals("Hello {player}. [Nice|Fine] day.", ((JsonStatement.Say) body.get(0)).text);
        assertInstanceOf(JsonStatement.Give.class, body.get(5));
        assertEquals(2, ((JsonStatement.Give) body.get(5)).count);
        assertInstanceOf(JsonStatement.Take.class, body.get(6));
        assertInstanceOf(JsonStatement.Sound.class, body.get(7));
        assertInstanceOf(JsonStatement.Effect.class, body.get(8));
        assertInstanceOf(JsonStatement.Cure.class, body.get(9));
        assertInstanceOf(JsonStatement.Objective.class, body.get(10));
        JsonStatement.Weather w = assertInstanceOf(JsonStatement.Weather.class, body.get(12));
        assertTrue(w.playerOnly);
        JsonStatement.Weather clear = assertInstanceOf(JsonStatement.Weather.class, body.get(13));
        assertEquals("clear", clear.weather);
        assertFalse(clear.playerOnly);
        JsonStatement.Notify notify = assertInstanceOf(JsonStatement.Notify.class, body.get(14));
        assertEquals("Hi {player}", notify.text);
        assertEquals("d", notify.detail);
        assertEquals("success", notify.style);
        assertInstanceOf(JsonStatement.ObjectiveCancel.class, body.get(11), "objective cancel is typed");
        JsonStatement.Notify plain = assertInstanceOf(JsonStatement.Notify.class, body.get(15));
        assertNull(plain.detail);
        assertEquals("success", plain.style);
        JsonStatement.Title big = assertInstanceOf(JsonStatement.Title.class, body.get(16));
        assertTrue(big.major);
        assertEquals(5.0, big.seconds);
        assertNull(big.secondary);
        JsonStatement.Choice choice = assertInstanceOf(JsonStatement.Choice.class, body.get(19));
        assertEquals("has(\"Food_Bread\")", choice.options.get(0).ifExpr);
        assertTrue(choice.options.get(0).once);
        assertEquals("$rich", choice.options.get(1).showIf);

        Dialogue back = JsonConvert.toModel(asset, "t.json");
        assertEquals(Printer.dialogue(d), Printer.dialogue(back));
        assertTrue(new Validator().validate(back).stream().noneMatch(Validator.Problem::error));
        // The dialogue that came back is the same shape, statement by statement.
        List<Statement> a = d.node("start").body();
        List<Statement> b = back.node("start").body();
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) assertEquals(a.get(i).getClass(), b.get(i).getClass(), "statement " + i);
    }

    @Test
    void badJsonIsReportedWithContext() {
        DialogueAsset asset = new DialogueAsset("bad");
        DialogueAsset.NodeEntry n = new DialogueAsset.NodeEntry();
        n.name = "start";
        JsonStatement.Jump j = new JsonStatement.Jump();
        n.body.add(j); // no Node
        asset.nodes.add(n);
        ParseException e = assertThrows(ParseException.class, () -> JsonConvert.toModel(asset, "bad.json"));
        assertTrue(e.getMessage().contains("Jump has no Node"), e.getMessage());
        assertTrue(e.getMessage().contains("start"), e.getMessage());

        DialogueAsset empty = new DialogueAsset("empty");
        assertThrows(ParseException.class, () -> JsonConvert.toModel(empty, "empty.json"));
    }

    @Test
    void defaultsAreFilledIn() {
        DialogueAsset asset = new DialogueAsset("simple");
        DialogueAsset.NodeEntry n = new DialogueAsset.NodeEntry();
        n.name = "start";
        JsonStatement.Say say = new JsonStatement.Say();
        say.text = "Hi.";
        n.body.add(say);
        asset.nodes.add(n);
        Dialogue d = JsonConvert.toModel(asset, "simple.json");
        assertEquals("simple", d.id());
        assertEquals("simple", d.scope());
        assertEquals(1, d.starts().size());
        assertEquals("start", d.starts().get(0).node());
        assertNull(d.speaker());
    }
}
