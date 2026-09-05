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

    /** The effect took over the screen (e.g. opened the shop); the conversation is over. */
    void detach();
}
