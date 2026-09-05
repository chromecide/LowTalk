package com.chromecide.lowtalk.parser;

import com.chromecide.lowtalk.model.Dialogue;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Static checks for the world, NPC and objective commands and the on: directive. */
class IntegrationCommandsTest {

    private static List<Validator.Problem> validate(String body) {
        Dialogue d = DialogueParser.parse("t.talk", "npc: X\n== a\nHi.\n" + body);
        return new Validator().validate(d);
    }

    private static boolean hasError(List<Validator.Problem> problems, String fragment) {
        return problems.stream().anyMatch(p -> p.error() && p.message().contains(fragment));
    }

    @Test
    void newCommandsAreKnownWithTheRightArity() {
        assertTrue(validate("<<weather Zone1_Cloudy_Medium>>\n<<weather clear player>>\n<<time noon>>\n<<time dusk 5>>\n"
                + "<<time pause>>\n<<npc_name \"Elder\">>\n<<state Idle>>\n<<despawn>>\n<<spawn Kweebec_Merchant 1 0 2>>\n"
                + "<<objective Objective_Gather>>\n<<objective cancel Objective_Gather>>\n<<objective line ObjectiveLine_Test>>\n")
                .stream().noneMatch(Validator.Problem::error));

        assertTrue(hasError(validate("<<weather>>\n"), "expects"));
        assertTrue(hasError(validate("<<despawn now>>\n"), "expects"));
        assertTrue(hasError(validate("<<spawn A 1 2 3 4>>\n"), "expects"));
        assertTrue(hasError(validate("<<objective finish X>>\n"), "verb must be one of"));
        assertTrue(hasError(validate("<<time teatime>>\n"), "expects dawn"));
        assertFalse(hasError(validate("<<time 19.5>>\n"), "expects dawn"));
    }

    @Test
    void newFunctionsAreKnown() {
        List<Validator.Problem> problems = validate("Weather {weather()}, {t(\"lowtalk.test.hello\")}, {objective_line(\"ObjectiveLine_Test\")}.\n");
        assertTrue(problems.stream().noneMatch(p -> p.message().contains("unknown function")), problems.toString());
    }

    @Test
    void onJoinDirectiveIsKeptForTheHost() {
        Dialogue d = DialogueParser.parse("t.talk", "on: join\nspeaker: Guide\n== a\nWelcome.\n<<end>>\n");
        assertEquals("join", d.otherDirectives().get("on"));
        assertEquals("Guide", d.speaker());
    }

    @Test
    void objectiveVerbsAreNotFlaggedAsRewardsWhenTheyRemoveThings() {
        // cancelling is not a reward; starting one in a hub still is
        List<Validator.Problem> cancel = validate("-> Give up\n    <<objective cancel Objective_Gather>>\n    <<jump a>>\n-> Leave\n    <<end>>\n");
        assertTrue(cancel.stream().noneMatch(p -> p.message().contains("picking it again")), cancel.toString());
    }
}
