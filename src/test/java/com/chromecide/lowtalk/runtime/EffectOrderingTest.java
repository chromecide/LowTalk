package com.chromecide.lowtalk.runtime;

import com.chromecide.lowtalk.model.Dialogue;
import com.chromecide.lowtalk.parser.DialogueParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A condition sees what an effect above it in the same passage did.
 *
 * <p>It did not, until 2026-09-19. The walk evaluated every condition and collected the effects, and the host
 * ran them once the walk was over, so this read false:
 *
 * <pre>
 *   &lt;&lt;give Food_Bread&gt;&gt;
 *   &lt;&lt;if has("Food_Bread")&gt;&gt;
 * </pre>
 *
 * <p>Silently, which is the part that matters: no error, no warning, just the wrong branch. It was found by a
 * test harness asking whether a recipe was known immediately after teaching it, and the answer had been no
 * since the language shipped.
 */
class EffectOrderingTest {

    /** Stands in for the game: an effect that changes something a function can then read. */
    private static Conversation.EffectSink sink(FakeContext ctx, List<String> ran) {
        return effect -> {
            ran.add(effect.name());
            if (effect.name().equals("give")) ctx.vars.put("player.bread", Boolean.TRUE);
            if (effect.name().equals("take")) ctx.vars.put("player.bread", Boolean.FALSE);
        };
    }

    @Test
    void anEffectIsVisibleToTheConditionBelowIt() {
        FakeContext ctx = new FakeContext();
        List<String> ran = new ArrayList<>();
        Dialogue d = DialogueParser.parse("t.talk", """
                == a
                <<give Food_Bread>>
                <<if $player.bread>>
                You have the bread.
                <<else>>
                No bread.
                <<endif>>
                """);
        Conversation c = new Conversation(d, ctx);
        c.onEffect(sink(ctx, ran));

        Step.Say said = assertInstanceOf(Step.Say.class, c.start().step());
        assertEquals("You have the bread.", said.text());
        assertEquals(List.of("give"), ran, "and the effect really did run, once");
    }

    /** The other direction, so the test cannot pass by the variable simply defaulting true. */
    @Test
    void andSoIsOneThatTakesSomethingAway() {
        FakeContext ctx = new FakeContext();
        ctx.vars.put("player.bread", Boolean.TRUE);
        Dialogue d = DialogueParser.parse("t.talk", """
                == a
                <<take Food_Bread>>
                <<if $player.bread>>
                Still have it.
                <<else>>
                Gone.
                <<endif>>
                """);
        Conversation c = new Conversation(d, ctx);
        c.onEffect(sink(ctx, new ArrayList<>()));

        assertEquals("Gone.", assertInstanceOf(Step.Say.class, c.start().step()).text());
    }

    /** Effects run in the order written, including across a jump into another passage. */
    @Test
    void effectsRunInTheOrderTheyAreWritten() {
        FakeContext ctx = new FakeContext();
        List<String> ran = new ArrayList<>();
        Dialogue d = DialogueParser.parse("t.talk", """
                == a
                <<give first>>
                <<jump b>>

                == b
                <<give second>>
                Done.
                """);
        Conversation c = new Conversation(d, ctx);
        c.onEffect(effect -> ran.add(effect.name() + " " + effect.args().get(0)));

        c.start();
        assertEquals(List.of("give first", "give second"), ran);
    }

    /** Without a sink nothing changes: the effects come back in the result, as the test runner expects. */
    @Test
    void withoutASinkTheEffectsAreStillHandedBack() {
        Dialogue d = DialogueParser.parse("t.talk", """
                == a
                <<give Food_Bread>>
                Hello.
                """);
        Conversation c = new Conversation(d, new FakeContext());
        Conversation.Result r = c.start();

        assertEquals(1, r.effects().size());
        assertEquals("give", r.effects().get(0).name());
        assertTrue(r.step() instanceof Step.Say);
    }
}
