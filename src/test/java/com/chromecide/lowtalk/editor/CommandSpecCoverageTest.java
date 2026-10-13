package com.chromecide.lowtalk.editor;

import com.chromecide.lowtalk.parser.Validator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every built-in command has an argument spec, even one with no arguments.
 *
 * <p>{@code CommandSpecs.of()} returning null means "the editor has no names for this command\'s arguments",
 * and the editor falls back to a free text box. For a command that takes none that is a box for an argument
 * it does not take, which is how {@code <<calm>>} shipped for an hour before a tester asked what went in it.
 *
 * <p>The Add menu and the in-game reference already had tests like this one, and were both done when the
 * command was added. This place did not, and was missed. That is the whole argument for the test.
 */
class CommandSpecCoverageTest {

    /**
     * The editor must not offer an argument the parser will refuse, nor let a required one be left out.
     * Both sides describe the same command and nothing compares them, so they drift: {@code <<shop>>} was
     * registered as taking none while the runtime, the reference and the JSON round-trip all read an
     * optional shop id, and the editor duly offered a picker for it. Filling it in produced
     * "<<shop>> expects 0 argument(s), got 1" and the dialogue could not be saved at all.
     *
     * <p>The editor is allowed to be narrower than the language — several commands offer fewer arguments
     * than the parser accepts, which limits what can be authored but saves cleanly. It is the other
     * direction that produces a file the editor itself cannot save.
     */
    @Test
    void noEditorSpecOffersArgumentsTheParserWouldRefuse() {
        List<String> wrong = new ArrayList<>();
        for (Map.Entry<String, int[]> e : Validator.BUILTIN_COMMANDS.entrySet()) {
            CommandSpecs.Spec spec = CommandSpecs.of(e.getKey());
            if (spec == null) continue;                 // covered by the test above
            int max = spec.args().size();
            int min = (int) spec.args().stream().filter(a -> !a.optional()).count();
            int[] arity = e.getValue();
            if (arity[1] >= 0 && max > arity[1]) {
                wrong.add(e.getKey() + ": the editor offers " + max + " argument(s) but the parser accepts at most "
                        + arity[1] + ", so filling the last one makes the dialogue unsaveable");
            }
            if (min < arity[0]) {
                wrong.add(e.getKey() + ": the editor can leave " + min + " argument(s) filled but the parser requires "
                        + arity[0]);
            }
        }
        assertTrue(wrong.isEmpty(), "editor specs and parser arities disagree:\n  " + String.join("\n  ", wrong));
    }

    @Test
    void everyBuiltInCommandHasAnEditorSpec() {
        List<String> missing = new ArrayList<>();
        for (String command : Validator.BUILTIN_COMMANDS.keySet()) {
            if (CommandSpecs.of(command) == null) missing.add(command);
        }
        assertTrue(missing.isEmpty(),
                "these commands have no CommandSpecs entry, so the editor shows a plain box for their "
                        + "arguments — add spec(\"name\", ...), or spec(\"name\") when it takes none:\\n  "
                        + String.join("\\n  ", missing));
    }
}
