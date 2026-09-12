package com.chromecide.lowtalk.hytale;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;

import javax.annotation.Nonnull;
import java.util.UUID;

/** What an effect handler can see. Implemented by the live session and by the headless test runner. */
public interface EffectHost {

    @Nonnull HytaleContext getContext();

    @Nonnull PlayerRef getPlayer();

    @Nonnull UUID getNpcId();

    @Nonnull World getWorld();

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
