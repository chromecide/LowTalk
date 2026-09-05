package com.chromecide.lowtalk.hytale;

import com.chromecide.lowtalk.LowTalkPlugin;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.HolderSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import javax.annotation.Nonnull;
import java.util.logging.Level;

/**
 * Closes any conversation whose NPC is removed from the world (despawned, killed, or unloaded with its chunk),
 * so the player is not left talking to nothing. Same shape as the game's own holder systems.
 */
public final class NpcGoneSystem extends HolderSystem<EntityStore> {
    private final LowTalkPlugin plugin;
    private final Query<EntityStore> query = Query.and(NPCEntity.getComponentType(), UUIDComponent.getComponentType());

    public NpcGoneSystem(@Nonnull LowTalkPlugin plugin) {
        this.plugin = plugin;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return query;
    }

    @Override
    public void onEntityAdd(@Nonnull Holder<EntityStore> holder, @Nonnull AddReason reason, @Nonnull Store<EntityStore> store) {
    }

    @Override
    public void onEntityRemoved(@Nonnull Holder<EntityStore> holder, @Nonnull RemoveReason reason, @Nonnull Store<EntityStore> store) {
        UUIDComponent uuid = holder.getComponent(UUIDComponent.getComponentType());
        if (uuid == null) return;
        if (!plugin.getSessions().isTalkingTo(uuid.getUuid())) return;
        int ended = plugin.getSessions().endTalkingTo(uuid.getUuid());
        if (ended > 0 && plugin.getSettings().isLogConversations()) {
            plugin.getLogger().at(Level.INFO).log("NPC %s was removed (%s); ended %d conversation(s)", uuid.getUuid(), reason, ended);
        }
    }
}
