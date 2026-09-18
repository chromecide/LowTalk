package com.chromecide.lowtalk.parser;

import com.chromecide.lowtalk.model.Dialogue;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An include resolved from a dialogue that lives in an asset pack.
 *
 * <p>Includes are resolved against the including file's name, and a dialogue in a pack is named with the pack's
 * label in front: {@code Chromecide:MyPack/greeter.talk}. That name is what the parser hands back to the
 * resolver, so a resolver that treats it as a path under the pack's own root goes looking for the pack inside
 * itself. {@code include:} then failed for every dialogue in every asset pack, which is where creators are told
 * to put them.
 */
class IncludeInPackTest {

    /** Stands in for a pack: files by the name they are displayed under, with the label in front. */
    private static DialogueParser.IncludeResolver pack(String label, Map<String, String> filesByRelativePath) {
        return path -> {
            String prefix = label + "/";
            String rel = path.startsWith(prefix) ? path.substring(prefix.length()) : path;
            return filesByRelativePath.get(rel);
        };
    }

    @Test
    void aDialogueInsideAPackCanIncludeItsNeighbour() {
        Dialogue d = DialogueParser.parse("Chromecide:MyPack/greeter.talk", """
                include: _shared

                == start
                Hello.
                <<jump shared_bit>>
                """, pack("Chromecide:MyPack", Map.of("_shared.talk", """
                == shared_bit
                A line from the shared file.
                """)));

        assertNotNull(d.node("shared_bit"), "the included passage should be part of the dialogue");
        assertTrue(d.nodes().containsKey("start"));
    }

    /** The including file's own passages win, so a pack can override a shared one. */
    @Test
    void theIncludingFileWinsOnAClash() {
        Dialogue d = DialogueParser.parse("Chromecide:MyPack/greeter.talk", """
                include: _shared

                == both
                Mine.
                """, pack("Chromecide:MyPack", Map.of("_shared.talk", """
                == both
                Theirs.
                """)));

        assertEquals(1, d.nodes().size());
        assertNotNull(d.node("both"));
    }

    /** The server's own folder has no label, and must keep working exactly as before. */
    @Test
    void aDialogueOutsideAPackStillIncludes() {
        Dialogue d = DialogueParser.parse("greeter.talk", """
                include: _shared

                == start
                Hello.
                """, pack("", Map.of("_shared.talk", """
                == shared_bit
                A line from the shared file.
                """)));

        assertNotNull(d.node("shared_bit"));
    }
}
