package com.chromecide.lowtalk.runtime;

import com.chromecide.lowtalk.model.Dialogue;
import com.chromecide.lowtalk.parser.DialogueParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Runtime behaviour of text variation, random blocks, once-options, ternaries and wait. */
class FormatAdditionsRuntimeTest {

    private final FakeContext ctx = new FakeContext();

    private Conversation conv(String src) {
        Dialogue d = DialogueParser.parse("t.talk", src);
        return new Conversation(d, ctx);
    }

    private static Step.Say say(Step s) {
        return assertInstanceOf(Step.Say.class, s);
    }

    private static Step.Choose choose(Step s) {
        return assertInstanceOf(Step.Choose.class, s);
    }

    @Test
    void ternaryInText() {
        ctx.vars.put("local.met", true);
        Conversation c = conv("== a\n{$met ? \"Back again\" : \"Hello\"}, {player}.\n");
        assertEquals("Back again, Chromecide.", say(c.start().step()).text());

        FakeContext fresh = new FakeContext();
        Conversation c2 = new Conversation(DialogueParser.parse("t.talk", "== a\n{$met ? \"Back again\" : \"Hello\"}.\n"), fresh);
        assertEquals("Hello.", say(c2.start().step()).text());
    }

    @Test
    void textVariationUsesRandom() {
        ctx.nextRandom = 1;
        Conversation c = conv("== a\n[Hi|Hello|Hey] there.\n");
        assertEquals("Hello there.", say(c.start().step()).text());

        ctx.nextRandom = 2;
        Conversation c2 = conv("== a\n[Hi|Hello|Hey] there.\n");
        assertEquals("Hey there.", say(c2.start().step()).text());

        ctx.nextRandom = 99; // out of range is clamped rather than crashing
        Conversation c3 = conv("== a\n[Hi|Hello] there.\n");
        assertEquals("Hello there.", say(c3.start().step()).text());
    }

    @Test
    void randomBlockRunsOneAlternative() {
        String src = "== a\n<<random>>\n  One.\n<<or>>\n  Two.\n  <<set $x = 2>>\n<<or>>\n  Three.\n<<endrandom>>\nDone.\n";
        ctx.nextRandom = 1;
        Conversation c = conv(src);
        assertEquals("Two.", say(c.start().step()).text());
        Step.Say done = say(c.next().step());
        assertEquals("Done.", done.text());
        assertTrue(done.last());
        assertEquals(2.0, ctx.vars.get("local.x"));

        ctx.nextRandom = 0;
        Conversation c2 = conv(src);
        assertEquals("One.", say(c2.start().step()).text());
    }

    @Test
    void onceOptionHidesItselfAfterBeingChosen() {
        String src = "== a\nPick.\n-> Secret <<once>>\n    Told you.\n-> Other\n    Fine.\n-> Leave\n    <<end>>\n";
        Conversation c = conv(src);
        Step.Choose first = choose(c.start().step());
        assertEquals(3, first.options().size());
        assertEquals("Secret", first.options().get(0).text());

        // The body's line is shown together with the hub's options again.
        Step.Choose again = choose(c.choose(0).step());
        assertEquals("Told you.", again.line().text());
        assertEquals(2, again.options().size(), "the once-option is gone from the hub");
        assertEquals("Other", again.options().get(0).text());
        assertThrows(RuntimeError.class, () -> c.choose(0), "and cannot be chosen by index either");

        // A new conversation for the same player and NPC remembers it.
        Conversation c2 = conv(src);
        assertEquals(2, choose(c2.start().step()).options().size());

        // A different player (fresh context) still sees it.
        Conversation c3 = new Conversation(DialogueParser.parse("t.talk", src), new FakeContext());
        assertEquals(3, choose(c3.start().step()).options().size());
    }

    @Test
    void waitPausesBetweenLines() {
        Conversation c = conv("== a\nOne.\n<<wait 2>>\nTwo.\n");
        Step.Wait w = assertInstanceOf(Step.Wait.class, c.start().step());
        assertNotNull(w.line());
        assertEquals("One.", w.line().text());
        assertEquals(2.0, w.seconds());
        Step.Say two = say(c.next().step());
        assertEquals("Two.", two.text());
        assertTrue(two.last());
    }

    @Test
    void waitWithNoLineBeforeIt() {
        ctx.vars.put("local.delay", 1.5);
        Conversation c = conv("== a\n<<wait $delay>>\nHello.\n");
        Step.Wait w = assertInstanceOf(Step.Wait.class, c.start().step());
        assertNull(w.line());
        assertEquals(1.5, w.seconds());
        assertEquals("Hello.", say(c.next().step()).text());
    }
}
