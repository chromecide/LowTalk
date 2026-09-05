package com.chromecide.parley;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import javax.annotation.Nonnull;
import java.util.logging.Level;

/**
 * Parley: hand-written branching dialogue for NPCs.
 *
 * This is the entry point. The parser and runtime live in their own packages and have no
 * dependency on the server; everything that touches Hytale is under {@code hytale}.
 */
public class ParleyPlugin extends JavaPlugin {

    private static ParleyPlugin instance;

    public ParleyPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
    }

    @Override
    protected void setup() {
        getLogger().at(Level.INFO).log("Parley loaded. Dialogue support arrives in milestone 2.");
    }

    public static ParleyPlugin get() {
        return instance;
    }
}
