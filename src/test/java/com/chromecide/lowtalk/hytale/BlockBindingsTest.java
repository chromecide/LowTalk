package com.chromecide.lowtalk.hytale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class BlockBindingsTest {

    @Test
    void bindingsSurviveAReload(@TempDir Path dir) throws Exception {
        java.util.function.Consumer<String> log = m -> {};
        BlockBindings b = new BlockBindings(dir, log, log);
        assertNull(b.get("default", 1, 2, 3));
        b.set("default", 1, 2, 3, "lore_keeper", "also");
        b.set("default", -4, 70, 9, "elder", "instead");
        b.set("lowtalk_test", 0, 0, 0, "x", "nonsense"); // unknown mode falls back to instead
        b.flush();
        assertTrue(Files.isRegularFile(dir.resolve("blocks.json")));

        BlockBindings again = new BlockBindings(dir, log, log);
        assertEquals(3, again.size());
        assertEquals("lore_keeper", again.get("default", 1, 2, 3).dialogue());
        assertFalse(again.get("default", 1, 2, 3).suppressesBlock(), "also lets the block act");
        assertTrue(again.get("default", -4, 70, 9).suppressesBlock());
        assertEquals("instead", again.get("lowtalk_test", 0, 0, 0).mode());
        assertNull(again.get("other", 1, 2, 3), "a different world is a different block");

        assertTrue(again.remove("default", 1, 2, 3));
        assertFalse(again.remove("default", 1, 2, 3));
        again.flush();
        assertEquals(2, new BlockBindings(dir, log, log).size());
        assertEquals(2, again.describeAll().size());
    }

    /**
     * Rebuilding the test corridor replaces every block in it, so a binding left inside points at whatever the
     * build put there. Leaving it is not harmless: a bound door was knocked into the corridor wall, the corridor
     * was rebuilt over it, and the binding stayed in the file. The next restart then looked exactly like block
     * bindings having stopped surviving restarts, and an hour went into a bug that was not there.
     */
    @Test
    void aVolumeRebuildTakesTheBindingsInsideItWithIt(@TempDir Path dir) {
        java.util.function.Consumer<String> log = m -> {};
        BlockBindings b = new BlockBindings(dir, log, log);
        b.set("lowtalk_test", 45, 1, 3, "test_npc", "instead");      // inside the corridor
        b.set("lowtalk_test", 45, 2, 3, "test_basics", "instead");   // inside the corridor
        b.set("lowtalk_test", 500, 1, 3, "far_away", "instead");     // beyond the end of it
        b.set("default", 45, 1, 3, "another_world", "instead");      // same position, another world

        assertEquals(2, b.removeWithin("lowtalk_test", -6, 146, -2, 5, -4, 4));

        assertNull(b.get("lowtalk_test", 45, 1, 3));
        assertNull(b.get("lowtalk_test", 45, 2, 3));
        assertNotNull(b.get("lowtalk_test", 500, 1, 3), "outside the box is left alone");
        assertNotNull(b.get("default", 45, 1, 3), "and so is another world at the same position");
        assertEquals(0, b.removeWithin("lowtalk_test", -6, 146, -2, 5, -4, 4), "and it is idempotent");
    }

    /** What a near miss reports: the bindings in the column of the block that was used. */
    @Test
    void aColumnListsWhatIsBoundAtEachHeight(@TempDir Path dir) {
        java.util.function.Consumer<String> log = m -> {};
        BlockBindings b = new BlockBindings(dir, log, log);
        b.set("lowtalk_test", 45, 1, 3, "test_npc", "instead");
        b.set("lowtalk_test", 45, 2, 3, "test_basics", "instead");
        b.set("lowtalk_test", 46, 1, 3, "next_along", "instead");

        var column = b.inColumn("lowtalk_test", 45, 3);
        assertEquals(2, column.size());
        assertEquals("test_npc", column.get(1).dialogue());
        assertEquals("test_basics", column.get(2).dialogue());
        assertTrue(b.inColumn("lowtalk_test", 44, 3).isEmpty());
    }
}
