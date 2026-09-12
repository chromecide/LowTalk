package com.chromecide.lowtalk.hytale;

import com.chromecide.lowtalk.LowTalkPlugin;
import com.chromecide.lowtalk.model.Dialogue;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import org.joml.Vector3i;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Using a block that has a dialogue bound to it opens the dialogue. The server only reports a use for blocks whose
 * type has a Use interaction, so that is the set of blocks that can be bound. A creator holding the LowTalk tool
 * gets the binding page instead, the block counterpart of clicking an NPC with the tool.
 */
public class BlockUseSystem extends EntityEventSystem<EntityStore, UseBlockEvent.Pre> {
    private final LowTalkPlugin plugin;

    public BlockUseSystem(@Nonnull LowTalkPlugin plugin) {
        super(UseBlockEvent.Pre.class);
        this.plugin = plugin;
    }

    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> store,
                       @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull UseBlockEvent.Pre event) {
        if (event.getInteractionType() != InteractionType.Use) return;
        Ref<EntityStore> playerEntity = chunk.getReferenceTo(index);
        PlayerRef player = commandBuffer.getComponent(playerEntity, PlayerRef.getComponentType());
        if (player == null) return;
        World world = store.getExternalData().getWorld();
        plugin.getLogger().at(java.util.logging.Level.INFO).log("[debug block use] type=%s tool=%s", event.getInteractionType(), NpcUseSystem.holdingTool(playerEntity, commandBuffer));
        Vector3i pos = event.getTargetBlock();
        String blockId = event.getBlockType() == null ? "?" : String.valueOf(event.getBlockType().getId());

        if (NpcUseSystem.holdingTool(playerEntity, commandBuffer)) {
            event.setCancelled(true);
            if (!player.hasPermission(LowTalkCommand.CREATOR)) {
                player.sendMessage(LowTalkCommand.msg(plugin, "toolNeedsPermission").param("permission", LowTalkCommand.CREATOR));
                return;
            }
            BindBlockPage.open(plugin, player, playerEntity, store, world.getName(), pos, blockId);
            return;
        }

        BlockBindings.Binding binding = plugin.getBlockBindings().get(world.getName(), pos.x, pos.y, pos.z);
        if (binding == null) return;
        Dialogue d = plugin.getRegistry().byId(binding.dialogue());
        if (d == null) {
            plugin.getLogger().at(java.util.logging.Level.WARNING).log("Block at %s %d %d %d is bound to '%s', which is not loaded",
                    world.getName(), pos.x, pos.y, pos.z, binding.dialogue());
            return;
        }
        if (binding.suppressesBlock()) event.setCancelled(true);
        plugin.getSessions().openFor(d, player, playerEntity, store, world, null);
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }
}
