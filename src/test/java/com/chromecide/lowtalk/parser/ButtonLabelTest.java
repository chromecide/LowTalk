package com.chromecide.lowtalk.parser;

import com.chromecide.lowtalk.hytale.json.JsonConvert;
import com.chromecide.lowtalk.hytale.json.LowTalkJson;
import com.chromecide.lowtalk.model.Dialogue;
import com.chromecide.lowtalk.model.Statement;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** `text => label` names the Continue button after a line, and survives printing and the JSON round trip. */
class ButtonLabelTest {

    private static Statement.Line firstLine(Dialogue d) {
        return (Statement.Line) d.nodes().get("start").body().get(0);
    }

    @Test
    void parsesTrailingLabelOnBareAndSpokenLines() {
        Dialogue d = DialogueParser.parse("t.talk", "npc: X\n== start\nWe held the bridge.  => And then?\nElder: On the tenth the river rose. => ...\nNo label here.\n", null);
        Statement.Line a = (Statement.Line) d.nodes().get("start").body().get(0);
        Statement.Line b = (Statement.Line) d.nodes().get("start").body().get(1);
        Statement.Line c = (Statement.Line) d.nodes().get("start").body().get(2);
        assertEquals("We held the bridge.", Printer.text(a.text()));
        assertEquals("And then?", a.button());
        assertEquals("Elder", b.speaker());
        assertEquals("On the tenth the river rose.", Printer.text(b.text()));
        assertEquals("...", b.button());
        assertNull(c.button());
    }

    @Test
    void printerWritesItBackAndTheJsonCarriesIt() {
        Dialogue d = DialogueParser.parse("t.talk", "npc: X\n== start\nGo west.  => Go on\n", null);
        String printed = Printer.dialogue(d);
        assertTrue(printed.contains("Go west.  => Go on"), printed);
        Dialogue again = DialogueParser.parse("t.talk", printed, null);
        assertEquals("Go on", firstLine(again).button());
        LowTalkJson asset = JsonConvert.toAsset(d);
        Dialogue fromJson = JsonConvert.toModel(asset, "t.json");
        assertEquals("Go on", firstLine(fromJson).button());
    }

    @Test
    void arrowInsideTextWithoutLabelIsNotALabel() {
        Dialogue d = DialogueParser.parse("t.talk", "npc: X\n== start\n=> not a label\n", null);
        Statement.Line l = firstLine(d);
        assertNull(l.button());
        assertEquals("=> not a label", Printer.text(l.text()));
    }
}
