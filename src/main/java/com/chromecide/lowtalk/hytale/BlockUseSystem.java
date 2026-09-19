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
 * gets the binding page instead, from the tool's own interaction step rather than from here: running the game's
 * UseBlock step with the tool would work the block as well as open the page, so pointing the tool at a lantern
 * would keep lighting and unlighting it.
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
        Vector3i pos = event.getTargetBlock();
        String blockId = event.getBlockType() == null ? "?" : String.valueOf(event.getBlockType().getId());

        // The tool no longer runs the game's UseBlock step, so it does not normally reach here; if something else
        // raises the event while the tool is held, the creator still gets the bind page rather than a conversation.
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
        org.joml.Vector3i base = pos;
        if (binding == null) {
            // whichever block of a multi-block structure the event named, the binding is on its base
            base = BlockReads.baseAt(world, pos.x, pos.y, pos.z);
            if (base.x != pos.x || base.y != pos.y || base.z != pos.z) {
                binding = plugin.getBlockBindings().get(world.getName(), base.x, base.y, base.z);
            }
        }
        if (binding == null) {
            reportNearMiss(world, pos, base, blockId);
            return;
        }
        Dialogue d = plugin.getRegistry().byId(binding.dialogue());
        if (d == null) {
            plugin.getLogger().at(java.util.logging.Level.WARNING).log("Block at %s %d %d %d is bound to '%s', which is not loaded",
                    world.getName(), pos.x, pos.y, pos.z, binding.dialogue());
            return;
        }
        if (binding.suppressesBlock()) event.setCancelled(true);
        // the block itself is where this conversation is, so a particle or a sound happens at the block
        plugin.getSessions().openFor(d, player, playerEntity, store, world, NpcInfo.atBlock(d, pos));
    }

    /**
     * Using a block that has no binding is the ordinary case and says nothing. Using one that has a binding
     * somewhere in its own column, but not where the base resolved to, is a fault worth a line in the log: the
     * base block is being resolved differently now from when the binding was recorded, and the only thing the
     * creator sees is a door that has quietly stopped talking.
     */
    private void reportNearMiss(@Nonnull World world, @Nonnull Vector3i pos, @Nonnull Vector3i base, @Nonnull String blockId) {
        var column = plugin.getBlockBindings().inColumn(world.getName(), pos.x, pos.z);
        if (column.isEmpty()) return;
        StringBuilder where = new StringBuilder();
        column.forEach((y, b) -> where.append(where.isEmpty() ? "" : ", ").append("y=").append(y)
                .append(" -> ").append(b.dialogue()));
        plugin.getLogger().at(java.util.logging.Level.WARNING).log(
                "Used %s at %s %d %d %d (base resolved to %d %d %d) and found no binding, but this column has: %s",
                blockId, world.getName(), pos.x, pos.y, pos.z, base.x, base.y, base.z, where);
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }
}
