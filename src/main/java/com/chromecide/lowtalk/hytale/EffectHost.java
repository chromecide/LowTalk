package com.chromecide.lowtalk.hytale;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/** What an effect handler can see. Implemented by the live session and by the headless test runner. */
public interface EffectHost {

    @Nonnull HytaleContext getContext();

    @Nonnull PlayerRef getPlayer();

    @Nonnull UUID getNpcId();

    @Nonnull World getWorld();

    /**
     * Where the conversation is happening when no entity stands for it: the middle of the block a dialogue is
     * bound to, or a trigger volume's own position. Null when there is nothing but the player, as for a join
     * dialogue or one opened from the browser. Effects that happen somewhere prefer the NPC, then this, then the
     * player.
     */
    @Nullable org.joml.Vector3d getOrigin();

    /** The effect took over the screen (e.g. opened the shop); the conversation is over, leave the window alone. */
    void detach();

    /**
     * Tell the conversation another page is about to replace its window; it waits and resumes with the statements
     * after the command when that page closes, or ends if there are none. Pair with a page whose dismissal calls
     * {@code resumeFromPage()} on the session.
     */
    void suspendForPage();

    /** The page that replaced the window closed; bring the conversation back. */
    void resumeFromPage();

    /** The effect moved the player on (e.g. teleport); end the conversation and close the window. */
    void end();
}
