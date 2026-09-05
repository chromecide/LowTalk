package com.chromecide.lowtalk.runtime;

import com.chromecide.lowtalk.model.Dialogue;
import com.chromecide.lowtalk.parser.DialogueParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConversationTest {

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
    void linesThenFinish() {
        Conversation c = conv("== a\nHello.\nElder: Sit.\n");
        Step.Say s1 = say(c.start().step());
        assertEquals("Rootling Merchant", s1.speaker());
        assertEquals("Hello.", s1.text());
        Step.Say s2 = say(c.next().step());
        assertEquals("Elder", s2.speaker());
        assertInstanceOf(Step.Finish.class, c.next().step());
        assertTrue(c.isFinished());
    }

    @Test
    void lineBeforeEndIsShownThenFinishes() {
        Conversation c = conv("== a\n-> Go\n    Farewell.\n    <<end>>\n");
        choose(c.start().step());
        assertEquals("Farewell.", say(c.choose(0).step()).text());
        assertInstanceOf(Step.Finish.class, c.next().step());
    }

    @Test
    void lineBeforeInputIsShownFirst() {
        Conversation c = conv("== a\nYour name?\n<<input $tmp.n \"Type it\">>\nHi {$tmp.n}.\n");
        assertEquals("Your name?", say(c.start().step()).text());
        assertInstanceOf(Step.Ask.class, c.next().step());
        assertEquals("Hi Bo.", say(c.answer("Bo").step()).text());
    }

    @Test
    void defaultSpeakerDirective() {
        Conversation c = conv("speaker: Bram\n== a\nHi.\n");
        assertEquals("Bram", say(c.start().step()).speaker());
    }

    @Test
    void lineFollowedByOptionsIsShownTogether() {
        Conversation c = conv("""
                == a
                What now?
                -> Buy
                    Sold.
                    <<end>>
                -> Leave
                    <<end>>
                """);
        Step.Choose ch = choose(c.start().step());
        assertEquals("What now?", ch.line().text());
        assertEquals(List.of("Buy", "Leave"), ch.options().stream().map(Step.Shown::text).toList());
        Step.Say sold = say(c.choose(0).step());
        assertEquals("Sold.", sold.text());
        assertInstanceOf(Step.Finish.class, c.next().step());
    }

    @Test
    void hubReturnsToSameOptions() {
        Conversation c = conv("""
                == hub
                -> Ask
                    An answer.
                -> Go
                    <<end>>
                """);
        Step.Choose ch = choose(c.start().step());
        assertNull(ch.line());
        // The line spoken inside the option is shown together with the hub it returns to.
        Step.Choose again = choose(c.choose(0).step());
        assertEquals("An answer.", again.line().text());
        assertEquals(2, again.options().size());
        assertInstanceOf(Step.Finish.class, c.choose(1).step());
    }

    @Test
    void guardsHideOrDisable() {
        ctx.functions.put("has", a -> false);
        Conversation c = conv("""
                == a
                -> Always
                    <<end>>
                -> Hidden <<if $secret>>
                    <<end>>
                -> Greyed <<show if has("Key")>>
                    <<end>>
                """);
        Step.Choose ch = choose(c.start().step());
        assertEquals(2, ch.options().size());
        assertEquals("Always", ch.options().get(0).text());
        assertTrue(ch.options().get(0).enabled());
        assertEquals(2, ch.options().get(1).index());
        assertFalse(ch.options().get(1).enabled());
        assertThrows(RuntimeError.class, () -> c.choose(1));
        assertThrows(RuntimeError.class, () -> c.choose(2));
    }

    @Test
    void conditionalsAndSet() {
        Conversation c = conv("""
                == a
                <<if $met>>
                  Again.
                <<else>>
                  First.
                  <<set $met = true>>
                <<endif>>
                <<set $n = $n + 1>>
                """);
        assertEquals("First.", say(c.start().step()).text());
        assertInstanceOf(Step.Finish.class, c.next().step());
        assertEquals(true, ctx.getVar("player", "met"));
        assertEquals(1.0, ctx.getVar("player", "n"));

        Conversation c2 = conv("== a\n<<if $met>>\n  Again.\n<<else>>\n  First.\n<<endif>>\n");
        assertEquals("Again.", say(c2.start().step()).text());
    }

    @Test
    void jumpAndVisited() {
        Conversation c = conv("== a\nOne.\n<<jump b>>\n== b\nTwo.\n<<end>>\n");
        assertEquals("One.", say(c.start().step()).text());
        assertEquals("Two.", say(c.next().step()).text());
        assertEquals("b", c.getCurrentNode());
        assertTrue(ctx.visited.contains("a"));
        assertTrue(ctx.visited.contains("b"));
    }

    @Test
    void guardedStarts() {
        String src = """
                start: returning when $met
                start: first
                == first
                First.
                == returning
                Back.
                """;
        assertEquals("First.", say(conv(src).start().step()).text());
        ctx.setVar("player", "met", true);
        assertEquals("Back.", say(conv(src).start().step()).text());
    }

    @Test
    void onceBlockRunsOnce() {
        String src = "== a\n<<once>>\n  Welcome.\n<<endonce>>\nHi.\n";
        assertEquals("Welcome.", say(conv(src).start().step()).text());
        assertEquals("Hi.", say(conv(src).start().step()).text());
    }

    @Test
    void inputStoresAnswer() {
        Conversation c = conv("== a\n<<input $tmp.name \"Name?\">>\nHello {$tmp.name}.\n");
        Step.Ask ask = assertInstanceOf(Step.Ask.class, c.start().step());
        assertEquals("Name?", ask.prompt());
        assertEquals("Hello Bram.", say(c.answer("Bram").step()).text());
    }

    @Test
    void commandsBecomeEffectsWithRenderedArgs() {
        ctx.setVar("player", "n", 2.0);
        Conversation c = conv("== a\n<<give Food_Bread {$n}>>\n<<run \"/say hi {player}\">>\nDone.\n");
        Conversation.Result r = c.start();
        assertEquals("Done.", say(r.step()).text());
        assertEquals(2, r.effects().size());
        assertEquals("give", r.effects().get(0).name());
        assertEquals(List.of("Food_Bread", "2"), r.effects().get(0).args());
        assertEquals(List.of("/say hi Chromecide"), r.effects().get(1).args());
        // Effects are handed over once.
        assertTrue(c.next().effects().isEmpty());
    }

    @Test
    void runtimeErrorsCarryPosition() {
        // The condition is evaluated while the first line is still held, so start() fails.
        Conversation c = conv("== a\nHi.\n<<if nope()>>\n  x\n<<endif>>\n");
        RuntimeError e = assertThrows(RuntimeError.class, c::start);
        assertEquals(3, e.getPos().line());
    }

    @Test
    void wrongCallOrderIsAnError() {
        Conversation c = conv("== a\n-> x\n    <<end>>\n");
        choose(c.start().step());
        assertThrows(RuntimeError.class, c::next);
        assertThrows(RuntimeError.class, () -> c.answer("x"));
    }

    @Test
    void merchantExamplePlaysThrough() throws IOException {
        ctx.functions.put("hour", a -> 12.0);
        ctx.functions.put("has", a -> false);
        Dialogue d = DialogueParser.parse("rootling_merchant.talk", Files.readString(Path.of("examples/rootling_merchant.talk")));
        Conversation c = new Conversation(d, ctx);

        Step.Choose hub = choose(c.start().step());
        assertTrue(hub.line().text().startsWith("Well met"));
        assertEquals(4, hub.options().size());

        // "I'm hungry" -> charity, no essence -> free bread. Two lines, then back to the hub.
        Step.Say hm = say(c.choose(1).step());
        assertTrue(hm.text().startsWith("Hm."));
        Conversation.Result r2 = c.next();
        Step.Choose back = choose(r2.step());
        assertTrue(back.line().text().startsWith("Take this"));
        assertTrue(r2.effects().stream().anyMatch(e -> e.name().equals("give") && e.args().equals(List.of("Food_Bread", "1"))), r2.effects().toString());
        assertEquals(3, back.options().size(), "the bread option is hidden after it was given");

        // Second visit uses the guarded returning start.
        Conversation c2 = new Conversation(d, ctx);
        Step.Choose second = choose(c2.start().step());
        assertTrue(second.line().text().contains("Back again"));
    }
}
