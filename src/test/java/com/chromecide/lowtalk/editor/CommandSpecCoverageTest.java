package com.chromecide.lowtalk.editor;

import com.chromecide.lowtalk.parser.Validator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

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
