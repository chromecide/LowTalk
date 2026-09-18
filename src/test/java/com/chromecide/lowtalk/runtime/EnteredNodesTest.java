package com.chromecide.lowtalk.runtime;

import com.chromecide.lowtalk.model.Dialogue;
import com.chromecide.lowtalk.parser.DialogueParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every passage the conversation enters, in order.
 *
 * <p>This exists because asking where the conversation *is* answers a different question. A passage that does
 * its work and jumps onward is entered and left inside one step, so anything that samples the current node
 * afterwards never sees it. {@code onNode} promised listeners "every jump" and quietly did not deliver one:
 * a station split into a passage per title style reported nothing at all, because each one jumped straight back.
 */
class EnteredNodesTest {

    private final FakeContext ctx = new FakeContext();

    private Conversation conv(String src) {
        Dialogue d = DialogueParser.parse("t.talk", src);
        return new Conversation(d, ctx);
    }

    /** The case that was broken: a passage whose whole job is to run something and jump back. */
    @Test
    void aPassageThatJumpsStraightOnwardIsStillReported() {
        Conversation c = conv("""
                == start
                -> Do the thing
                    <<jump work>>
                -> Leave
                    <<end>>

                == work
                <<heal>>
                <<jump start>>
                """);
        c.start();
        assertEquals(List.of("start"), c.drainEnteredNodes());

        c.choose(0);
        // work is entered and left within the one advance; the conversation comes to rest back at start
        assertEquals(List.of("work", "start"), c.drainEnteredNodes(),
                "the passage that ran the command must be reported, not just where it settled");
        assertEquals("start", c.getCurrentNode());
    }

    @Test
    void drainingTakesEachNodeOnlyOnce() {
        Conversation c = conv("== start\nHello.\n");
        c.start();
        assertEquals(List.of("start"), c.drainEnteredNodes());
        assertTrue(c.drainEnteredNodes().isEmpty(), "a node already reported must not be reported again");
    }

    @Test
    void aChainOfJumpsIsReportedInOrder() {
        Conversation c = conv("""
                == start
                -> Go
                    <<jump one>>

                == one
                <<jump two>>

                == two
                <<jump three>>

                == three
                Done.
                """);
        c.start();
        c.drainEnteredNodes();
        c.choose(0);
        assertEquals(List.of("one", "two", "three"), c.drainEnteredNodes());
    }
}
