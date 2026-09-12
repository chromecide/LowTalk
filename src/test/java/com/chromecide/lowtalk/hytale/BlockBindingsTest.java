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
}
