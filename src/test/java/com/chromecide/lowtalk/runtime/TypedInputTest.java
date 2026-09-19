package com.chromecide.lowtalk.runtime;

import com.chromecide.lowtalk.model.Dialogue;
import com.chromecide.lowtalk.model.Statement;
import com.chromecide.lowtalk.parser.DialogueParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A number box comes back until it is given a number.
 *
 * <p>Before this, a player who typed a word into a box whose prompt said "type a number" ended the
 * conversation — not there, but in whichever later line first treated the answer as a number. The player saw
 * the dialogue close and an apology. Player-written text is the one input a creator cannot validate, and it
 * was the one input that could kill a conversation.
 */
class TypedInputTest {

    private final FakeContext ctx = new FakeContext();

    private Conversation conv(String src) {
        Dialogue d = DialogueParser.parse("t.talk", src);
        return new Conversation(d, ctx);
    }

    @Test
    void aNumberBoxRefusesAWordAndAsksAgain() {
        Conversation c = conv("== a\n<<input $tmp.n \"How many?\" number>>\nYou said {$tmp.n}.\n");
        Step.Ask first = assertInstanceOf(Step.Ask.class, c.start().step());
        assertEquals("How many?", first.prompt());

        Step.Ask again = assertInstanceOf(Step.Ask.class, c.answer("asdfasdf").step());
        assertTrue(again.prompt().contains("a number"), "and says why it came back: " + again.prompt());
        assertEquals(null, ctx.vars.get("tmp.n"), "nothing is stored until it is a number");

        Step.Say said = assertInstanceOf(Step.Say.class, c.answer("3").step());
        assertEquals("You said 3.", said.text());
    }

    /** Negative and decimal answers are numbers too, and blank is not. */
    @Test
    void whatCountsAsANumber() {
        Conversation c = conv("== a\n<<input $tmp.n \"How many?\" number>>\nGot {$tmp.n}.\n");
        c.start();
        assertInstanceOf(Step.Ask.class, c.answer("").step(), "blank comes back");
        assertInstanceOf(Step.Ask.class, c.answer("  ").step(), "so does whitespace");
        assertInstanceOf(Step.Say.class, c.answer("-2.5").step(), "a negative decimal is a number");
    }

    /** A plain input still takes anything, which is the whole point of it. */
    @Test
    void aTextBoxTakesWhateverItIsGiven() {
        Conversation c = conv("== a\n<<input $tmp.word \"Your name?\">>\nHello {$tmp.word}.\n");
        c.start();
        Step.Say said = assertInstanceOf(Step.Say.class, c.answer("asdfasdf").step());
        assertEquals("Hello asdfasdf.", said.text());
    }

    /**
     * The kind survives being written out and read back, which is how the editor saves.
     *
     * <p>The editor rebuilds an input from its text when the prompt changes, and the JSON format is a
     * separate encoding again. A number box that quietly became a text box on save would put the bug back
     * without anyone touching the runtime.
     */
    @Test
    void theKindSurvivesPrintingAndParsing() {
        Dialogue d = DialogueParser.parse("t.talk", "== a\n<<input $tmp.n \"How many?\" number>>\nDone.\n");
        String printed = com.chromecide.lowtalk.parser.Printer.dialogue(d);
        assertTrue(printed.contains("number"), "printed as: " + printed);

        Dialogue again = DialogueParser.parse("t.talk", printed);
        Statement.Input in = assertInstanceOf(Statement.Input.class, again.node("a").body().get(0));
        assertEquals(Statement.InputKind.NUMBER, in.kind());
    }

    /** And a plain one stays plain rather than gaining a kind it never had. */
    @Test
    void aTextInputPrintsWithoutAKind() {
        Dialogue d = DialogueParser.parse("t.talk", "== a\n<<input $tmp.word \"Name?\">>\nDone.\n");
        String printed = com.chromecide.lowtalk.parser.Printer.dialogue(d);
        assertTrue(printed.contains("<<input $tmp.word Name?>>"), "printed as: " + printed);
        assertEquals(Statement.InputKind.TEXT,
                assertInstanceOf(Statement.Input.class,
                        DialogueParser.parse("t.talk", printed).node("a").body().get(0)).kind());
    }

    /**
     * A prompt that is the bare word "number" is a prompt, not a kind.
     *
     * <p>The kind is recognised only after a closing quote, which is why. An unquoted multi-word prompt is
     * already a parse error, so this single word is the whole of the overlap between the two -- small, but
     * the sort of thing that turns someone's working dialogue into a box that refuses their answers.
     */
    @Test
    void aBarePromptThatReadsNumberIsStillAPrompt() {
        Dialogue d = DialogueParser.parse("t.talk", "== a\n<<input $tmp.n number>>\nDone.\n");
        Statement.Input in = assertInstanceOf(Statement.Input.class, d.node("a").body().get(0));
        assertEquals(Statement.InputKind.TEXT, in.kind());
        assertEquals("number", com.chromecide.lowtalk.parser.Printer.text(in.prompt()));
    }
}
