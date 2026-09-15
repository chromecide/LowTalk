package com.chromecide.lowtalk.hytale;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The settings a server gets before anybody edits anything. */
class ConfigDefaultsTest {

    @Test
    void runningServerCommandsIsOffUntilAnOwnerAsksForIt() {
        // <<run>> acts with the console's authority and dialogues arrive in packs written by other people, so a
        // freshly installed server must not hand an operator's power to a file it just downloaded
        assertFalse(new LowTalkConfig().isAllowRun(),
                "AllowRunCommand must default to off; a config with the key missing must not grant console access");
    }

    @Test
    void askingPlayersToTypeIsOn() {
        // this one is on: it is half of what makes a dialogue a conversation, and the text it collects is
        // guarded rather than dangerous
        assertTrue(new LowTalkConfig().isAllowInput());
    }
}
