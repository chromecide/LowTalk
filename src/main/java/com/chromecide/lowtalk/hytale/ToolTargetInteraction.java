package com.chromecide.lowtalk.hytale;

import com.chromecide.lowtalk.LowTalkPlugin;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.BlockEntity;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * The LowTalk tool's last step when the game's UseEntity interaction ignored the target: entities without an
 * interactions component, which is every unbound prop, or nothing at all. Opens the prop binding page for a prop
 * and the dialogue browser for nothing. Entities the
 * UseEntity step already handled (NPCs, bound props) never reach this, unless LowTalk cancelled that step itself,
 * so anything carrying an interactions component is left alone here.
 * Registered under the type name {@link #TYPE_ID}; used by LowTalk_Tool_Use.json.
 */
public class ToolTargetInteraction extends SimpleInstantInteraction {
    public static final String TYPE_ID = "LowTalkTarget";
    public static final BuilderCodec<ToolTargetInteraction> CODEC = BuilderCodec.builder(
                    ToolTargetInteraction.class, ToolTargetInteraction::new, SimpleInstantInteraction.CODEC)
            .documentation("LowTalk: the tool on an entity the UseEntity interaction ignores, such as an unbound prop.")
            .build();

    private static LowTalkPlugin plugin;

    public static void install(LowTalkPlugin owner) {
        plugin = owner;
    }

    public ToolTargetInteraction() {
        super();
    }

    @Override
    protected void firstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context, @Nonnull CooldownHandler cooldownHandler) {
        if (type != InteractionType.Use || plugin == null) {
            fail(context);
            return;
        }
        Ref<EntityStore> target = context.getTargetEntity();
        Ref<EntityStore> actor = context.getEntity();
        CommandBuffer<EntityStore> buffer = context.getCommandBuffer();
        if (actor == null || !actor.isValid() || buffer == null) {
            fail(context);
            return;
        }
        PlayerRef player = buffer.getComponent(actor, PlayerRef.getComponentType());
        if (player == null) {
            fail(context);
            return;
        }
        if (target == null || !target.isValid()) {
            // A usable block (door, chest, lever...) was handled by the UseBlock step, which LowTalk cancelled to
            // open the bind page; the chain still falls through to here, so stand down for those.
            if (usableBlockInFront(context, buffer)) return;
            // Nothing (or a plain block) in front of the tool: the dialogue browser.
            if (!player.hasPermission(LowTalkCommand.CREATOR)) {
                player.sendMessage(LowTalkCommand.msg(plugin, "toolNeedsPermission").param("permission", LowTalkCommand.CREATOR));
                return;
            }
            BrowsePage.open(plugin, player, actor, buffer.getExternalData().getStore());
            return;
        }
        if (buffer.getComponent(target, Interactions.getComponentType()) != null) return; // the UseEntity step owned this one
        if (!PropSupport.isProp(target, buffer)) {
            player.sendMessage(LowTalkCommand.msg(plugin, "propOnly"));
            return;
        }
        UUID id = PropSupport.idOf(target, buffer);
        if (id == null) {
            player.sendMessage(LowTalkCommand.msg(plugin, "propOnly"));
            return;
        }
        if (!player.hasPermission(LowTalkCommand.CREATOR)) {
            player.sendMessage(LowTalkCommand.msg(plugin, "toolNeedsPermission").param("permission", LowTalkCommand.CREATOR));
            return;
        }
        String label = describe(target, buffer);
        BindPropPage.open(plugin, player, actor, buffer.getExternalData().getStore(), id, label);
    }

    /** True if the tool is aimed at a loaded block whose type has a Use interaction. */
    private static boolean usableBlockInFront(InteractionContext context, CommandBuffer<EntityStore> buffer) {
        com.hypixel.hytale.protocol.BlockPosition pos = context.getTargetBlock();
        if (pos == null) return false;
        try {
            com.hypixel.hytale.server.core.universe.world.World world = buffer.getExternalData().getWorld();
            com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk chunk =
                    world.getChunkIfLoaded(com.hypixel.hytale.math.util.ChunkUtil.indexChunkFromBlock(pos.x, pos.z));
            com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType type = chunk == null ? null : chunk.getBlockType(pos.x, pos.y, pos.z);
            return type != null && type.getInteractions() != null && type.getInteractions().containsKey(InteractionType.Use);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** "Recipe_Book_Magic_Air (prop)" or "prop": what the bind page shows as the target. */
    static String describe(@Nonnull Ref<EntityStore> target, @Nonnull CommandBuffer<EntityStore> buffer) {
        BlockEntity block = buffer.getComponent(target, BlockEntity.getComponentType());
        if (block != null && block.getBlockTypeKey() != null) return block.getBlockTypeKey() + " (prop)";
        ItemComponent item = buffer.getComponent(target, ItemComponent.getComponentType());
        if (item != null && item.getItemStack() != null) return item.getItemStack().getItemId() + " (prop)";
        return "prop";
    }

    private static void fail(InteractionContext context) {
        context.getState().state = InteractionState.Failed;
    }
}
