package com.chromecide.lowtalk.editor;

import com.chromecide.lowtalk.parser.Reference;
import com.chromecide.lowtalk.parser.Validator;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The add menu is the only way a creator finds out what a dialogue can do. A command with no entry of its own is
 * one a creator can only reach by already knowing its name, which is the opposite of the point.
 */
class AddMenuTest {

    @Test
    void everyBuiltInCommandHasItsOwnEntry() {
        List<String> missing = AddMenu.missingCommands();
        assertTrue(missing.isEmpty(),
                "these commands are not offered anywhere in the add menu, so nobody will find them: " + missing);
    }

    @Test
    void noCommandIsOfferedTwice() {
        Set<String> seen = new HashSet<>();
        for (AddMenu.Item i : AddMenu.items()) {
            if (i.command() == null) continue;
            assertTrue(seen.add(i.command()), i.command() + " appears in the add menu more than once");
        }
    }

    @Test
    void theMenuOffersNothingTheRuntimeDoesNotHave() {
        for (String c : AddMenu.commands()) {
            assertTrue(Validator.BUILTIN_COMMANDS.containsKey(c),
                    "the add menu offers <<" + c + ">>, which is not a built-in command");
        }
    }

    @Test
    void entriesReadAsWhatTheyDoRatherThanWhatTheyAreCalled() {
        for (AddMenu.Item i : AddMenu.items()) {
            assertTrue(AddMenu.GROUPS.contains(i.group()), i.group() + " is not one of the menu's groups");
            assertFalse(i.text().isBlank(), "an entry with no words");
            // "npc_name" or "vfx" as the whole label is the under-the-hood name leaking into the menu
            assertFalse(i.text().equals(i.command()),
                    "the entry for " + i.command() + " is just the command name; say what it does instead");
            assertTrue(Character.isUpperCase(i.text().charAt(0)), i.text() + " should read as a sentence");
        }
    }

    @Test
    void everyEntryCarriesSomethingToShowOnHover() {
        for (AddMenu.Item i : AddMenu.items()) {
            boolean described = i.tooltip() != null
                    || (i.command() != null && Reference.lookup(i.command()) != null);
            assertTrue(described, i.label() + " has neither a tooltip nor a reference entry");
        }
    }

    /** The value is what the page parses back into a statement kind and a command name. */
    @Test
    void theValueRoundTripsToTheKindAndCommand() {
        for (AddMenu.Item i : AddMenu.items()) {
            String v = i.value();
            int colon = v.indexOf(':');
            assertEquals(i.kind(), DialogueDraft.Kind.valueOf(colon < 0 ? v : v.substring(0, colon)));
            assertEquals(i.command(), colon < 0 ? null : v.substring(colon + 1));
        }
    }

    /** A command the editor offers but cannot lay out as fields would open as a raw text box. */
    @Test
    void theCommandsOfferedCanBeShownAsFields() {
        for (String c : AddMenu.commands()) {
            if (c.equals("run")) continue;   // deliberately one quoted string, edited as text
            assertTrue(CommandSpecs.of(c) != null || Reference.lookup(c) != null,
                    "<<" + c + ">> is offered by the menu but the editor knows nothing about its arguments");
        }
    }
}
