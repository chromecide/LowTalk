package com.chromecide.lowtalk.parser;

import com.chromecide.lowtalk.model.Dialogue;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Suggestions in error messages, the in-game reference, and the outline shown to creators. */
class CreatorAidsTest {

    @Test
    void suggestionsCatchTypos() {
        Set<String> names = Validator.BUILTIN_COMMANDS.keySet();
        assertEquals("give", Suggest.closest("gvie", names));
        assertEquals("objective", Suggest.closest("objectve", names));
        assertEquals("objective", Suggest.closest("obj", names));
        assertEquals("reputation", Suggest.closest("reputaton", names));
        assertNull(Suggest.closest("frobnicate", names));
        assertEquals(" (did you mean teleport?)", Suggest.hint("telport", names));
        assertEquals("", Suggest.hint("give", names));
    }

    @Test
    void validatorSuggestsCommandsFunctionsAndNodes() {
        Dialogue d = DialogueParser.parse("t.talk", "npc: X\n== start\nHi {hass(\"Food_Bread\")}.\n<<gvie Food_Bread>>\n<<jump strat>>\n");
        List<Validator.Problem> problems = new Validator().validate(d);
        assertTrue(problems.stream().anyMatch(p -> p.message().contains("did you mean give?")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.message().contains("did you mean has?")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.message().contains("did you mean start?")), problems.toString());
    }

    @Test
    void blankBindingsAreErrors() {
        Dialogue d = DialogueParser.parse("t.talk", "npc: @\n== a\nHi.\n");
        assertTrue(new Validator().validate(d).stream().anyMatch(p -> p.error() && p.message().contains("binding is empty")));
    }

    @Test
    void curlyQuotesAreExplained() {
        ParseException e = assertThrows(ParseException.class,
                () -> DialogueParser.parse("t.talk", "== a\n<<if $name == “Bob”>>\nHi.\n<<endif>>\n"));
        assertTrue(e.getMessage().contains("curly quote"), e.getMessage());
    }

    @Test
    void referenceCoversEveryBuiltin() {
        for (String c : Validator.BUILTIN_COMMANDS.keySet()) {
            assertNotNull(Reference.lookup(c), "no reference entry for command " + c);
        }
        for (String f : Validator.BUILTIN_FUNCTIONS) {
            assertNotNull(Reference.lookup(f), "no reference entry for function " + f);
        }
        for (Reference.Entry e : Reference.commands()) {
            assertTrue(Validator.BUILTIN_COMMANDS.containsKey(e.name()), "reference documents unknown command " + e.name());
        }
        for (Reference.Entry e : Reference.functions()) {
            assertTrue(Validator.BUILTIN_FUNCTIONS.contains(e.name()), "reference documents unknown function " + e.name());
        }
        assertEquals("<<give Item_Id [count]>>", Reference.lookup("<<give>>").usage());
        assertEquals("has", Reference.lookup("has()").name());
        assertNotNull(Reference.lookup("npc"));
        assertNotNull(Reference.lookup("if"));
    }

    @Test
    void outlineSummarisesADialogue() {
        Dialogue d = DialogueParser.parse("t.talk", """
                npc: Kweebec_Merchant
                == start
                Hello {$player.name}.
                -> Trade <<if $met>>
                    <<set $met = true>>
                    <<give Food_Bread>>
                -> Leave
                    <<end>>
                == orphan
                Nobody jumps here.
                """);
        Outline o = Outline.of(d);
        assertEquals(2, o.optionsPerNode().get("start"));
        assertEquals(0, o.optionsPerNode().get("orphan"));
        assertTrue(o.variablesRead().contains("$player.name"));
        assertTrue(o.variablesRead().contains("$met"));
        assertTrue(o.variablesWritten().contains("$met"));
        assertEquals(Set.of("orphan"), o.unreachableNodes());
        assertEquals(Set.of("give"), o.commandsUsed());
        List<String> lines = o.lines();
        assertTrue(lines.get(0).startsWith("t: 2 passage(s), bound to Kweebec_Merchant"), lines.get(0));
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("Never reached: orphan")));
    }
}
