package com.chromecide.lowtalk.api;

import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * What a plugin-provided function or command can see and change. All calls happen on the world thread.
 * Variable scopes are "local" (this player with this NPC), "player", "npc", "world", and "tmp".
 */
public interface DialogueContext {

    @Nonnull PlayerRef getPlayer();

    /** The NPC's entity id, or a nil UUID when the dialogue was opened without an NPC. */
    @Nonnull UUID getNpcId();

    @Nonnull String getNpcName();

    @Nonnull String getDialogueId();

    /** A variable's value (Double, String, Boolean), or null if unset. */
    @Nullable Object getVar(@Nonnull String scope, @Nonnull String name);

    void setVar(@Nonnull String scope, @Nonnull String name, @Nullable Object value);

    boolean hasVisited(@Nonnull String node);
}
