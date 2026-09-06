package com.chromecide.lowtalk.editor;

import com.chromecide.lowtalk.editor.DialogueDraft.Kind;
import com.chromecide.lowtalk.editor.DialogueDraft.Scope;
import com.chromecide.lowtalk.model.Dialogue;
import com.chromecide.lowtalk.model.Option;
import com.chromecide.lowtalk.model.Statement;
import com.chromecide.lowtalk.parser.DialogueParser;
import com.chromecide.lowtalk.parser.Printer;
import com.chromecide.lowtalk.parser.Validator;
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

    private static Dialogue roundTrip(DialogueDraft draft) {
        String printed = Printer.dialogue(draft.toDialogue());
        Dialogue d = parse(printed);
        for (Validator.Problem p : new Validator(java.util.Set.of(), java.util.Set.of()).validate(d)) {
            assertFalse(p.error(), "validator error after round trip: " + p + "\n" + printed);
        }
        return d;
    }

    @Test
    void roundTripWithoutEditsIsStable() {
        Dialogue d = parse(SRC);
        DialogueDraft draft = new DialogueDraft(d);
        assertFalse(draft.isDirty());
        assertEquals(Printer.dialogue(d), Printer.dialogue(draft.toDialogue()));
        assertEquals(List.of("start", "well"), draft.nodeNames());
        assertEquals("start", draft.startNode());
        assertEquals(List.of("Kweebec_Merchant"), draft.bindings());
    }

    @Test
    void editsSurviveAPrintAndParse() {
        DialogueDraft draft = new DialogueDraft(parse(SRC));
        Scope start = Scope.node("start");
        draft.setLineText(start, 0, "Welcome, {player}!");
        draft.setLineSpeaker(start, 0, "Mara the Elder");
        assertEquals("well", DialogueDraft.optionTarget(draft.options(start, 1).get(0)));
        assertEquals(DialogueDraft.TARGET_END, DialogueDraft.optionTarget(draft.options(start, 1).get(1)));

        int ci = draft.add(start, Kind.OPTION);
        assertEquals(1, ci);
        draft.setOptionText(start, ci, 2, "Tell me more");
        String created = draft.setOptionTarget(start, ci, 2, DialogueDraft.TARGET_NEW);
        assertNotNull(created);
        assertTrue(draft.hasNode(created));
        draft.setLineText(Scope.node(created), 0, "There is more to tell [later|another day].");
        draft.moveOption(start, ci, 2, -1);

        Dialogue out = roundTrip(draft);
        Statement.Line first = (Statement.Line) out.node("start").body().get(0);
        assertEquals("Mara the Elder", first.speaker());
        assertEquals("Welcome, {player}!", Printer.text(first.text()));
        Statement.Choice choice = (Statement.Choice) out.node("start").body().get(1);
        assertEquals(3, choice.options().size());
        assertEquals("Tell me more", Printer.text(choice.options().get(1).text()));
        assertEquals(created, DialogueDraft.optionTarget(choice.options().get(1)));
    }

    @Test
    void optionConditionsAndOnce() {
        DialogueDraft draft = new DialogueDraft(parse(SRC));
        Scope start = Scope.node("start");
        assertNull(draft.setOptionGuard(start, 1, 0, "$met and not $player.paid"));
        assertNull(draft.setOptionShowGuard(start, 1, 1, "has(\"Food_Bread\")"));
        draft.setOptionOnce(start, 1, 0, true);
        assertNotNull(draft.setOptionGuard(start, 1, 0, "$met and ("), "a broken expression is reported");
        Dialogue out = roundTrip(draft);
        Option o = ((Statement.Choice) out.node("start").body().get(1)).options().get(0);
        assertEquals("$met and not $player.paid", Printer.expr(o.guard()));
        assertTrue(o.once());
        assertEquals("has(\"Food_Bread\")", Printer.expr(((Statement.Choice) out.node("start").body().get(1)).options().get(1).showGuard()));
        assertNull(draft.setOptionGuard(start, 1, 0, ""));
        assertNull(((Statement.Choice) roundTrip(draft).node("start").body().get(1)).options().get(0).guard());
    }

    @Test
    void scopesReachIntoOptionBodiesAndBlocks() {
        DialogueDraft draft = new DialogueDraft(parse(SRC));
        Scope start = Scope.node("start");
        Scope firstOptionBody = start.into(1, 0);
        assertEquals(1, draft.view(firstOptionBody).size());
        int at = draft.add(firstOptionBody, Kind.COMMAND);
        assertNull(draft.setCommand(firstOptionBody, at, "give", "Food_Bread 2"));
        assertEquals(DialogueDraft.TARGET_CUSTOM, DialogueDraft.optionTarget(draft.options(start, 1).get(0)));
        assertEquals("start > option \"Ask about the well\"", draft.describe(firstOptionBody));

        int ifAt = draft.add(start, Kind.IF);
        assertNull(draft.setBranchCondition(start, ifAt, 0, "$player.gold > 5"));
        int elseAt = draft.addBranch(start, ifAt);
        assertEquals(1, elseAt);
        Scope elseBody = start.into(ifAt, elseAt);
        draft.setLineText(elseBody, 0, "You are broke.");
        int onceAt = draft.add(start, Kind.ONCE);
        draft.setLineText(start.into(onceAt, 0), 0, "First time only.");
        int randomAt = draft.add(start, Kind.RANDOM);
        draft.addAlternative(start, randomAt);
        assertEquals(3, ((Statement.Random) draft.view(start).get(randomAt)).alternatives().size());
        int setAt = draft.add(start, Kind.SET);
        assertNull(draft.setSet(start, setAt, "$player.visits", "$player.visits + 1"));
        assertNotNull(draft.setSet(start, setAt, "nonsense", ""));
        int waitAt = draft.add(start, Kind.WAIT);
        assertNull(draft.setWait(start, waitAt, "2"));
        int inputAt = draft.add(start, Kind.INPUT);
        assertNull(draft.setInput(start, inputAt, "$name", "What is your name?"));

        Dialogue out = roundTrip(draft);
        List<Statement> body = out.node("start").body();
        Statement.Conditional cond = (Statement.Conditional) body.get(ifAt);
        assertEquals(2, cond.branches().size());
        assertNull(cond.branches().get(1).condition());
        assertEquals("You are broke.", Printer.text(((Statement.Line) cond.branches().get(1).body().get(0)).text()));
        Statement.Choice choice = (Statement.Choice) body.stream().filter(x -> x instanceof Statement.Choice).findFirst().orElseThrow();
        assertEquals(body.size() - 1, body.indexOf(choice), "new blocks go before the options");
        Statement.Command give = (Statement.Command) choice.options().get(0).body().get(0);
        assertEquals("give", give.name(), "the command went before the option's jump");
        assertEquals("Food_Bread 2", DialogueDraft.argsText(give));
        assertInstanceOf(Statement.Jump.class, choice.options().get(0).body().get(1));
        Scope optionBodyNow = start.into(draft.view(start).size() - 1, 0);
        draft.setCommandArg(optionBodyNow, 0, 0, "Food_Apple");
        assertEquals("Food_Apple 2", DialogueDraft.argsText((Statement.Command) draft.view(optionBodyNow).get(0)));
    }

    @Test
    void headerFieldsAreEditable() {
        DialogueDraft draft = new DialogueDraft(parse(SRC));
        draft.setSpeaker("Old Mara");
        draft.setTitle("The Well");
        draft.setBindings("Kweebec_Merchant, @elder");
        draft.setDirective("portrait", "Portraits/mara.png");
        draft.setStartNode("well");
        Dialogue out = roundTrip(draft);
        assertEquals("Old Mara", out.speaker());
        assertEquals("The Well", out.title());
        assertEquals(List.of("Kweebec_Merchant", "@elder"), out.bindings());
        assertEquals("Portraits/mara.png", out.otherDirectives().get("portrait"));
        assertEquals("well", new DialogueDraft(out).startNode());
        draft.setBindings("none");
        assertTrue(new DialogueDraft(roundTrip(draft)).bindings().isEmpty());
    }

    @Test
    void renameUpdatesJumpsAndDeleteTurnsJumpsIntoEnds() {
        DialogueDraft draft = new DialogueDraft(parse(SRC));
        Scope start = Scope.node("start");
        assertTrue(draft.renameNode("well", "the_well"));
        assertFalse(draft.canRename("start", "the_well"));
        assertFalse(draft.canRename("start", "has space"));
        assertEquals("the_well", DialogueDraft.optionTarget(draft.options(start, 1).get(0)));
        assertTrue(draft.unreachableNodes().isEmpty());
        assertTrue(draft.deleteNode("the_well"));
        assertEquals(DialogueDraft.TARGET_END, DialogueDraft.optionTarget(draft.options(start, 1).get(0)));
        assertFalse(draft.deleteNode("start"), "the last node stays");
        roundTrip(draft);
    }

    @Test
    void newLineGoesBeforeTheOptionsAndEmptiedChoicesVanish() {
        DialogueDraft draft = new DialogueDraft(parse(SRC));
        Scope start = Scope.node("start");
        assertEquals(1, draft.add(start, Kind.LINE));
        assertInstanceOf(Statement.Choice.class, draft.view(start).get(2));
        draft.deleteOption(start, 2, 1);
        draft.deleteOption(start, 2, 0);
        assertEquals(2, draft.view(start).size());
        draft.setLineText(start, 0, "Price is {unfinished");
        assertEquals("Price is {{unfinished", Printer.text(((Statement.Line) roundTrip(draft).node("start").body().get(0)).text()));
    }
}
