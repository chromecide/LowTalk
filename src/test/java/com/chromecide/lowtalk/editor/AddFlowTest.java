package com.chromecide.lowtalk.editor;

import com.chromecide.lowtalk.editor.DialogueDraft.Scope;
import com.chromecide.lowtalk.model.Dialogue;
import com.chromecide.lowtalk.model.Statement;
import com.chromecide.lowtalk.parser.DialogueParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Picking a thing from the Add menu and pressing Add, exactly as the page does it: the menu's value is split into
 * a kind and a command, the kind is added, and the command is written over the fresh statement.
 */
class AddFlowTest {

    private static final String SRC = """
            npc: Kweebec_Merchant

            == start
            Hello.
            """;

    /** What DialogueEditorPage does in its ADD case. */
    private static String addFromMenu(DialogueDraft draft, Scope sc, AddMenu.Item item) {
        String chosen = item.value();
        int colon = chosen.indexOf(':');
        String command = colon < 0 ? null : chosen.substring(colon + 1);
        DialogueDraft.Kind kind = DialogueDraft.Kind.valueOf(colon < 0 ? chosen : chosen.substring(0, colon));
        int added = draft.add(sc, kind);
        if (command != null) return draft.setCommand(sc, added, command, CommandSpecs.defaults(command));
        return null;
    }

    @Test
    void everyCommandInTheMenuActuallyAddsThatCommand() {
        Dialogue d = DialogueParser.parse("edit.talk", SRC);
        List<String> wrong = new ArrayList<>();
        for (AddMenu.Item item : AddMenu.items()) {
            if (item.command() == null) continue;
            DialogueDraft draft = new DialogueDraft(d);
            Scope sc = Scope.node("start");
            String error = addFromMenu(draft, sc, item);
            List<Statement> body = draft.view(sc);
            Statement last = body.get(body.size() - 1);
            String got = last instanceof Statement.Command c ? c.name() : last.getClass().getSimpleName();
            if (!item.command().equals(got)) {
                wrong.add(item.command() + " added a '" + got + "'" + (error == null ? "" : " (" + error + ")"));
            }
        }
        assertTrue(wrong.isEmpty(), "the Add menu does not add what it says:\n  " + String.join("\n  ", wrong));
    }
}
