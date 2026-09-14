package com.chromecide.lowtalk.parser;

import com.chromecide.lowtalk.model.Dialogue;
import com.chromecide.lowtalk.model.Statement;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A command typed into a line's words is text, not a parse failure.
 *
 * The in-game editor lets an author type anything into a line, so it could write a file that the parser then
 * refused, which dropped the whole dialogue out of the registry and left the editor unable to save. A line keeps
 * its words, the file still loads, and the validator explains what the author probably meant instead.
 */
class CommandInTextTest {

    private static final String SRC = "npc: none\n== start\nRoasting by a tar pit and then...<<wait 2>>\nHere I am.\n";

    @Test
    void aCommandInsideALineIsKeptAsText() {
        Dialogue d = DialogueParser.parse("t.talk", SRC, null);
        List<Statement> body = d.nodes().get("start").body();
        assertInstanceOf(Statement.Line.class, body.get(0));
        assertTrue(plain(d).contains("<<wait 2>>"), "the line keeps the words the author typed");
    }

    @Test
    void suchALineSurvivesTheRoundTrip() {
        Dialogue d = DialogueParser.parse("t.talk", SRC, null);
        String printed = Printer.dialogue(d);
        Dialogue again = DialogueParser.parse("t.talk", printed, null);
        assertEquals(plain(d), plain(again), "printing and reparsing must not change the line");
    }

    @Test
    void theValidatorWarnsAboutItAndDoesNotError() {
        Dialogue d = DialogueParser.parse("t.talk", SRC, null);
        List<Validator.Problem> problems = new Validator(Set.of("wait"), Set.of()).validate(d);
        assertTrue(problems.stream().noneMatch(Validator.Problem::error), "a command in text is not an error");
        assertTrue(problems.stream().anyMatch(p -> p.message().contains("<<wait 2>>")),
                "the author is told which snippet looks like a command: " + problems);
    }

    @Test
    void aLineThatOpensWithACommandIsStillACommand() {
        Dialogue d = DialogueParser.parse("t.talk", "npc: none\n== start\n<<wait 2>>\nHi.\n", null);
        assertInstanceOf(Statement.Wait.class, d.nodes().get("start").body().get(0));
    }

    private static String plain(Dialogue d) {
        StringBuilder sb = new StringBuilder();
        for (Statement s : d.nodes().get("start").body()) {
            if (s instanceof Statement.Line l) sb.append(Printer.text(l.text())).append('\n');
        }
        return sb.toString();
    }
}
