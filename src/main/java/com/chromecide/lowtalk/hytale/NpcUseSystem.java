package com.chromecide.lowtalk.hytale;

import com.chromecide.lowtalk.LowTalkPlugin;

import com.chromecide.lowtalk.model.Dialogue;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import java.util.List;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.event.events.ecs.UseEntityEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Opens a bound dialogue when a player uses an NPC. Role bindings and tag bindings each have a mode:
 * "crouch" (crouch and use) or "replace" (plain use, native interaction suppressed).
 */
public class NpcUseSystem extends EntityEventSystem<EntityStore, UseEntityEvent.Pre> {

    private final LowTalkPlugin plugin;

    public NpcUseSystem(@Nonnull LowTalkPlugin plugin) {
        super(UseEntityEvent.Pre.class);
        this.plugin = plugin;
    }

    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> store,
                       @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull UseEntityEvent.Pre event) {
        if (event.getInteractionType() != InteractionType.Use) return;

        Ref<EntityStore> playerEntity = chunk.getReferenceTo(index);
        PlayerRef player = commandBuffer.getComponent(playerEntity, PlayerRef.getComponentType());
        if (player == null) return;

        Ref<EntityStore> target = event.getTargetEntity();
        NpcInfo npc = NpcInfo.of(target, commandBuffer, player, plugin.getStore());
        if (npc == null) return;

        // A creator holding the LowTalk tool edits the NPC's dialogue instead of playing it.
        if (holdingTool(playerEntity, commandBuffer)) {
            event.setCancelled(true);
            if (!player.hasPermission(LowTalkCommand.CREATOR)) {
                player.sendMessage(LowTalkCommand.msg(plugin, "toolNeedsPermission").param("permission", LowTalkCommand.CREATOR));
                return;
            }
            List<Dialogue> bound = plugin.getRegistry().candidates(npc.role(), npc.tags());
            if (bound.isEmpty()) NewDialoguePage.open(plugin, player, playerEntity, store, npc);
            else DialogueEditorPage.open(plugin, bound.get(0), player, playerEntity, store, npc);
            return;
        }

        if (!plugin.getSettings().isUseHook()) return; // roles and interaction JSON open dialogues instead

        // An NPC left frozen by a crash mid-conversation is freed the next time anyone talks to it.
        if (plugin.getStore().isHeld(npc.id()) && !plugin.getSessions().isTalkingTo(npc.id())) {
            World w = store.getExternalData().getWorld();
            NpcHold.release(w, npc.id(), plugin.getStore());
        }

        MovementStatesComponent movement = commandBuffer.getComponent(playerEntity, MovementStatesComponent.getComponentType());
        boolean crouching = movement != null && movement.getMovementStates() != null && movement.getMovementStates().crouching;

        Dialogue chosen = pick(npc, crouching);
        if (chosen == null) return;

        event.setCancelled(true);
        World world = store.getExternalData().getWorld();
        plugin.getSessions().open(chosen, player, playerEntity, store, world, npc);
    }

    /** The first bound dialogue whose binding mode matches how the player interacted. */
    @Nullable
    private Dialogue pick(NpcInfo npc, boolean crouching) {
        LowTalkConfig config = plugin.getSettings();
        DialogueRegistry registry = plugin.getRegistry();
        for (String tag : npc.tags()) {
            for (Dialogue d : registry.forTag(tag)) {
                if (modeMatches(config.getTagBindingMode(), crouching)) return d;
            }
        }
        for (Dialogue d : registry.forRole(npc.role())) {
            if (modeMatches(config.getRoleBindingMode(), crouching)) return d;
        }
        return null;
    }

    /** True if the player's active hotbar item is the LowTalk tool. */
    static boolean holdingTool(Ref<EntityStore> playerEntity, com.hypixel.hytale.component.ComponentAccessor<EntityStore> accessor) {
        InventoryComponent.Hotbar hotbar = accessor.getComponent(playerEntity, InventoryComponent.Hotbar.getComponentType());
        if (hotbar == null) return false;
        ItemStack held = hotbar.getInventory().getItemStack((short) hotbar.getActiveSlot());
        return held != null && DialogueEditorPage.TOOL_ITEM.equals(held.getItemId());
    }

    private static boolean modeMatches(String mode, boolean crouching) {
        return LowTalkConfig.MODE_CROUCH.equalsIgnoreCase(mode) ? crouching : !crouching;
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }
}
