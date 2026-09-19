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

    /** What started this conversation: an NPC, a block, a trigger, a role, a command, another plugin. */
    @Nonnull Opener getOpener();

    /**
     * Where the conversation is happening, when it is somewhere rather than with someone: the middle of the
     * block or prop that opened it. Null for an NPC conversation, where the NPC is the place.
     *
     * <p>With {@link #getOpener()} this is what lets a listener tell one block from another — which block
     * was used, not merely that one was.
     */
    @Nullable org.joml.Vector3d getOrigin();

    /** A variable's value (Double, String, Boolean), or null if unset. */
    @Nullable Object getVar(@Nonnull String scope, @Nonnull String name);

    void setVar(@Nonnull String scope, @Nonnull String name, @Nullable Object value);

    boolean hasVisited(@Nonnull String node);

    /** The NPC entity, or null for a narrator conversation or an NPC that is gone. World thread only. */
    @Nullable com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> getNpcRef();

    /** The world the conversation happens in, or null if the player has left. */
    @Nullable com.hypixel.hytale.server.core.universe.world.World getWorld();

    /** The entity store of that world, or null if the player has left. World thread only. */
    @Nullable com.hypixel.hytale.component.Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> getEntityStore();
}
