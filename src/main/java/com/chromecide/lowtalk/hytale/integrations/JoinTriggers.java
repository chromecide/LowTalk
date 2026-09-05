package com.chromecide.lowtalk.hytale.integrations;

import com.chromecide.lowtalk.LowTalkPlugin;
import com.chromecide.lowtalk.model.Dialogue;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Dialogues with the header directive {@code on: join} open on their own when a player finishes loading into a
 * world (the game's PlayerReadyEvent). They run without an NPC, so {@code speaker:} names the voice. Authors gate
 * repeats the usual way: a guarded {@code start:}, a once-block, or an early {@code <<end>>} (a dialogue that ends
 * before saying anything never opens a window).
 */
public final class JoinTriggers {
    public static final String DIRECTIVE = "on";
    public static final String JOIN = "join";

    private JoinTriggers() {}

    public static void onPlayerReady(@Nonnull LowTalkPlugin plugin, @Nonnull PlayerReadyEvent event) {
        Ref<EntityStore> ref = event.getPlayerRef();
        if (ref == null || !ref.isValid()) return;
        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();
        if (world == null) return;
        world.execute(() -> {
            if (!ref.isValid()) return;
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef == null) return;
            if (plugin.getSessions().get(playerRef.getUuid()) != null) return;
            for (Dialogue d : plugin.getRegistry().all()) {
                if (!JOIN.equalsIgnoreCase(d.otherDirectives().get(DIRECTIVE))) continue;
                if (plugin.getSessions().openFor(d, playerRef, ref, store, world, null) != null) return; // one at a time
            }
        });
    }
}
