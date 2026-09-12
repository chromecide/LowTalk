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
 * interactions component, which is every unbound prop. Opens the prop binding page for creators. Entities the
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
        if (target == null || !target.isValid() || actor == null || !actor.isValid() || buffer == null) {
            fail(context);
            return;
        }
        if (buffer.getComponent(target, Interactions.getComponentType()) != null) return; // the UseEntity step owned this one
        PlayerRef player = buffer.getComponent(actor, PlayerRef.getComponentType());
        if (player == null) {
            fail(context);
            return;
        }
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
