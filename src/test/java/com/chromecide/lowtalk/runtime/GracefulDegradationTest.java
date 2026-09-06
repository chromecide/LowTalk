package com.chromecide.lowtalk.runtime;

import com.chromecide.lowtalk.model.Dialogue;
import com.chromecide.lowtalk.parser.DialogueParser;
import com.chromecide.lowtalk.parser.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** A dialogue written against a plugin keeps working, minus that plugin's choices, when the plugin is gone. */
class GracefulDegradationTest {
    private static final String SRC = """
            npc: X
            == start
            Need a hand?
            -> Come with me.
                <<follow>>
                <<end>>
            -> Wait here.
                <<if $met>>
                  <<stay>>
                <<endif>>
                <<end>>
            -> Not right now.
                <<end>>
            """;

    @Test
    void optionsNeedingAMissingCommandAreHidden() {
        FakeContext ctx = new FakeContext() {
            @Override
            public boolean hasCommand(String name) {
                return !Set.of("follow", "stay").contains(name);
            }

            @Override
            public void warn(String message) {
                warnings.add(message);
            }

            final List<String> warnings = new java.util.ArrayList<>();
        };
        Dialogue d = DialogueParser.parse("t.talk", SRC);
        Conversation c = new Conversation(d, ctx);
        Step.Choose choose = (Step.Choose) c.start().step();
        assertEquals(List.of("Not right now."), choose.options().stream().map(Step.Shown::text).toList(),
                "the follow option and the nested stay option are hidden");
    }

    @Test
    void everythingShowsWhenTheCommandsExist() {
        Dialogue d = DialogueParser.parse("t.talk", SRC);
        Step.Choose choose = (Step.Choose) new Conversation(d, new FakeContext()).start().step();
        assertEquals(3, choose.options().size());
    }

    @Test
    void typoLikeUnknownNamesAreWarningsNotErrors() {
        Dialogue d = DialogueParser.parse("t.talk", "npc: X\n== start\nHi.\n<<stay>>\n<<if following()>>\nYes.\n<<endif>>\n");
        List<Validator.Problem> problems = new Validator().validate(d);
        assertTrue(problems.stream().anyMatch(p -> p.message().contains("did you mean stat?")), problems.toString());
        assertTrue(problems.stream().noneMatch(Validator.Problem::error), "unknown names never block loading: " + problems);
    }
}
