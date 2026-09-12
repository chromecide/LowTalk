package com.chromecide.lowtalk.parser;

import com.chromecide.lowtalk.model.Dialogue;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ValidatorTest {

    private static List<Validator.Problem> check(String src) {
        Dialogue d = DialogueParser.parse("test.talk", src);
        return new Validator().validate(d);
    }

    private static boolean hasError(List<Validator.Problem> ps, String fragment) {
        return ps.stream().anyMatch(p -> p.error() && p.message().contains(fragment));
    }

    private static boolean hasWarning(List<Validator.Problem> ps, String fragment) {
        return ps.stream().anyMatch(p -> !p.error() && p.message().contains(fragment));
    }

    @Test
    void cleanDialogueHasNoProblems() {
        List<Validator.Problem> ps = check("""
                npc: Kweebec_Merchant
                == a
                Hi.
                -> Go
                    <<end>>
                """);
        assertTrue(ps.isEmpty(), ps.toString());
    }

    @Test
    void unknownJumpAndStartAreErrors() {
        List<Validator.Problem> ps = check("npc: X\nstart: nowhere\n== a\n<<jump missing>>\n");
        assertTrue(hasError(ps, "start passage 'nowhere'"));
        assertTrue(hasError(ps, "unknown passage 'missing'"));
    }

    @Test
    void unreachableStatementsAreWarnings() {
        List<Validator.Problem> ps = check("""
                npc: X
                == a
                <<end>>
                Never shown.
                == b
                -> One
                    Hi.
                After options.
                """);
        assertTrue(hasWarning(ps, "after <<end>>"));
        assertTrue(hasWarning(ps, "after a set of options"));
    }

    @Test
    void builtinCommandShapes() {
        List<Validator.Problem> ps = check("""
                npc: X
                == a
                <<attitude grumpy>>
                <<give Food_Bread lots>>
                <<shop now>>
                <<objective>>
                """);
        assertTrue(hasError(ps, "<<attitude>> must be one of"));
        assertTrue(hasError(ps, "count must be a whole number"));
        assertTrue(hasError(ps, "<<shop>> expects 0"));
        assertTrue(hasError(ps, "<<objective>> expects 1"));
    }

    @Test
    void playerInputNeverReachesRun() {
        List<Validator.Problem> ps = check("""
                npc: X
                == a
                <<input $tmp.name "Name?">>
                <<run "/say {$tmp.name}">>
                <<run "/say {player}">>
                """);
        assertEquals(1, ps.stream().filter(Validator.Problem::error).count(), ps.toString());
        assertTrue(hasError(ps, "set from player text input"));
    }

    @Test
    void unknownCommandsAndFunctionsWarnUnlessRegistered() {
        String src = "npc: X\n== a\n<<if magic()>>\n  <<sparkle>>\n<<endif>>\n";
        List<Validator.Problem> ps = check(src);
        assertTrue(hasWarning(ps, "unknown function magic()"));
        assertTrue(hasWarning(ps, "unknown command <<sparkle>>"));

        Dialogue d = DialogueParser.parse("test.talk", src);
        List<Validator.Problem> registered = new Validator(Set.of("sparkle"), Set.of("magic")).validate(d);
        assertTrue(registered.isEmpty(), registered.toString());
    }

    @Test
    void missingBindingAndFallbackStartWarn() {
        List<Validator.Problem> ps = check("start: a when $x\n== a\nHi.\n");
        assertTrue(hasWarning(ps, "no npc: binding"));
        assertTrue(hasWarning(ps, "unguarded start"));
    }
}
