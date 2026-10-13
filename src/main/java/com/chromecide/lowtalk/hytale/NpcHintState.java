package com.chromecide.lowtalk.hytale;

import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Who has been shown which "press to talk" prompt, for one world.
 *
 * <p>This is a {@link Resource} rather than three fields on {@link NpcHintSystem} because a system
 * instance is shared by every world. {@code EntityStore.REGISTRY} is static, {@code addStore} attaches
 * each world's store to that one registry without cloning systems, and {@code World} is a ticking thread
 * of its own — so plain maps held by the system were being written by every world thread at once. A
 * resource is per store, so each world now gets its own copy and the threads no longer meet.
 *
 * <p>Nothing here is persisted: it is a picture of who is standing near what right now, rebuilt within a
 * quarter of a second of a restart. Registered with a supplier rather than a codec for that reason.
 *
 * <p>The state is deliberately still keyed by UUID rather than moved onto the entities themselves. The
 * per-player half would sit naturally on the player, but {@link #watchers} is a reverse index from NPC to
 * the players near it, and keying that by player UUID on the NPC would strand an entry whenever someone
 * disconnected — the same leak in a new place. Doing it properly means viewer refs and a second system
 * that drops invalid ones; that is a larger change and is written up separately.
 */
public final class NpcHintState implements Resource<EntityStore> {

    /** Player -> seconds accumulated since that player was last checked. */
    final Map<UUID, Float> timers = new HashMap<>();

    /** Player -> NPC id -> hint text delivered to that player's client, or "" while delivery is pending. */
    final Map<UUID, Map<UUID, String>> marked = new HashMap<>();

    /** NPC id -> players currently near it, so the mark comes off when the last one leaves. */
    final Map<UUID, Set<UUID>> watchers = new HashMap<>();

    @Nullable
    @Override
    public Resource<EntityStore> clone() {
        NpcHintState copy = new NpcHintState();
        copy.timers.putAll(this.timers);
        this.marked.forEach((player, hints) -> copy.marked.put(player, new HashMap<>(hints)));
        this.watchers.forEach((npc, players) -> copy.watchers.put(npc, new HashSet<>(players)));
        return copy;
    }

    @Nonnull
    @Override
    public String toString() {
        return "NpcHintState{players=" + this.marked.size() + ", markedNpcs=" + this.watchers.size() + "}";
    }
}
