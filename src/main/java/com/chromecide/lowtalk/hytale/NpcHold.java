package com.chromecide.lowtalk.hytale;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.entity.Frozen;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Keeps an NPC still and facing the player while they talk, using the same Frozen component
 * as /npc freeze, and lets it go afterwards. Only NPCs LowTalk froze itself are thawed.
 *
 * The Frozen component is saved with the entity, so a crash mid-conversation could leave an NPC
 * frozen. The world record keeps a list of NPCs LowTalk is holding; they are thawed the next
 * time anyone interacts with them, and /lowtalk thaw frees one by hand.
 */
public final class NpcHold {

    private NpcHold() {}

    /** Freeze the NPC and turn it toward the player. Runs on the next world tick. */
    public static void hold(@Nonnull World world, @Nonnull UUID npcId, @Nonnull UUID playerId, @Nonnull VariableStore variables) {
        world.execute(() -> {
            EntityStore entities = world.getEntityStore();
            Store<EntityStore> store = entities.getStore();
            Ref<EntityStore> npc = entities.getRefFromUUID(npcId);
            Ref<EntityStore> player = entities.getRefFromUUID(playerId);
            if (npc == null || !npc.isValid()) return;

            if (!store.getArchetype(npc).contains(Frozen.getComponentType())) {
                store.addComponent(npc, Frozen.getComponentType(), Frozen.get());
                variables.addHeld(npcId);
                variables.flush();
            }
            if (player != null && player.isValid()) {
                face(store, npc, player);
            }
        });
    }

    /** Thaw the NPC if LowTalk froze it. Runs on the next world tick. */
    public static void release(@Nonnull World world, @Nonnull UUID npcId, @Nonnull VariableStore variables) {
        if (!variables.isHeld(npcId)) return;
        world.execute(() -> {
            EntityStore entities = world.getEntityStore();
            Ref<EntityStore> npc = entities.getRefFromUUID(npcId);
            if (npc != null && npc.isValid()) {
                entities.getStore().tryRemoveComponent(npc, Frozen.getComponentType());
            }
            variables.removeHeld(npcId);
            variables.flush();
        });
    }

    /** Thaw right now, on the world thread, regardless of who froze it. */
    public static boolean thawNow(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> npc, @Nonnull UUID npcId, @Nonnull VariableStore variables) {
        boolean was = store.getArchetype(npc).contains(Frozen.getComponentType());
        if (was) store.tryRemoveComponent(npc, Frozen.getComponentType());
        variables.removeHeld(npcId);
        variables.flush();
        return was;
    }

    private static void face(Store<EntityStore> store, Ref<EntityStore> npc, Ref<EntityStore> player) {
        TransformComponent npcT = store.getComponent(npc, TransformComponent.getComponentType());
        TransformComponent playerT = store.getComponent(player, TransformComponent.getComponentType());
        if (npcT == null || playerT == null) return;
        Vector3d from = npcT.getPosition();
        Vector3d to = playerT.getPosition();
        Rotation3f look = Rotation3f.lookAt(from, to);
        npcT.setRotation(new Rotation3f(0.0f, look.yaw(), 0.0f));
    }
}
