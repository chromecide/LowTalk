package com.chromecide.lowtalk.editor;

import com.chromecide.lowtalk.model.Dialogue;
import com.chromecide.lowtalk.model.Option;
import com.chromecide.lowtalk.model.Statement;
import com.chromecide.lowtalk.parser.DialogueParser;
import com.chromecide.lowtalk.parser.Printer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DialogueDraftTest {
    private static final String SRC = """
            npc: Kweebec_Merchant
            speaker: Mara

            == start
            Hello {player}.
            -> Ask about the well
                <<jump well>>
            -> Leave
                <<end>>

            == well
            The well is dry.
            """;

    private static Dialogue parse(String src) {
        return DialogueParser.parse("edit.talk", src);
    }

    @Test
    void roundTripWithoutEditsIsStable() {
        Dialogue d = parse(SRC);
        DialogueDraft draft = new DialogueDraft(d);
        assertFalse(draft.isDirty());
        String printed = Printer.dialogue(draft.toDialogue());
        assertEquals(Printer.dialogue(d), printed);
        assertEquals(List.of("start", "well"), draft.nodeNames());
        assertEquals("start", draft.startNode());
    }

    @Test
    void editsSurviveAPrintAndParse() {
        DialogueDraft draft = new DialogueDraft(parse(SRC));
        draft.setLineText("start", 0, "Welcome, {player}!");
        draft.setLineSpeaker("start", 0, "Mara the Elder");
        int ci = draft.choiceIndex("start");
        assertEquals(1, ci);
        assertEquals("well", DialogueDraft.optionTarget(draft.options("start", ci).get(0)));
        assertEquals(DialogueDraft.TARGET_END, DialogueDraft.optionTarget(draft.options("start", ci).get(1)));

        draft.addOption("start", "Tell me more");
        String created = draft.setOptionTarget("start", ci, 2, DialogueDraft.TARGET_NEW);
        assertNotNull(created);
        assertTrue(draft.hasNode(created));
        draft.setLineText(created, 0, "There is more to tell [later|another day].");
        draft.moveOption("start", ci, 2, -1);

        Dialogue out = parse(Printer.dialogue(draft.toDialogue()));
        Statement.Line first = (Statement.Line) out.node("start").body().get(0);
        assertEquals("Mara the Elder", first.speaker());
        assertEquals("Welcome, {player}!", Printer.text(first.text()));
        Statement.Choice choice = (Statement.Choice) out.node("start").body().get(1);
        assertEquals(3, choice.options().size());
        assertEquals("Tell me more", Printer.text(choice.options().get(1).text()));
        assertEquals(created, DialogueDraft.optionTarget(choice.options().get(1)));
        assertEquals("There is more to tell [later|another day].", Printer.text(((Statement.Line) out.node(created).body().get(0)).text()));
    }

    @Test
    void renameUpdatesJumpsAndDeleteTurnsJumpsIntoEnds() {
        DialogueDraft draft = new DialogueDraft(parse(SRC));
        assertTrue(draft.renameNode("well", "the_well"));
        assertFalse(draft.canRename("start", "the_well"));
        assertFalse(draft.canRename("start", "has space"));
        Option o = draft.options("start", 1).get(0);
        assertEquals("the_well", DialogueDraft.optionTarget(o));
        assertTrue(draft.unreachableNodes().isEmpty());

        assertTrue(draft.deleteNode("the_well"));
        assertEquals(DialogueDraft.TARGET_END, DialogueDraft.optionTarget(draft.options("start", 1).get(0)));
        assertFalse(draft.deleteNode("start"), "the last node stays");
        assertNotNull(parse(Printer.dialogue(draft.toDialogue())));
    }

    @Test
    void brokenInterpolationIsKeptAsPlainText() {
        DialogueDraft draft = new DialogueDraft(parse(SRC));
        draft.setLineText("start", 0, "Price is {unfinished");
        Dialogue out = parse(Printer.dialogue(draft.toDialogue()));
        assertEquals("Price is {{unfinished", Printer.text(((Statement.Line) out.node("start").body().get(0)).text()));
    }

    @Test
    void newLineGoesBeforeTheOptions() {
        DialogueDraft draft = new DialogueDraft(parse(SRC));
        int at = draft.addLine("start");
        assertEquals(1, at);
        assertInstanceOf(Statement.Choice.class, draft.body("start").get(2));
        draft.deleteOption("start", 2, 1);
        draft.deleteOption("start", 2, 0);
        assertEquals(2, draft.body("start").size(), "an emptied choice disappears");
    }
}
