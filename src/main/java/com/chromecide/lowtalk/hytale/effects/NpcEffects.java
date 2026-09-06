package com.chromecide.lowtalk.hytale.effects;

import com.chromecide.lowtalk.LowTalkPlugin;
import com.chromecide.lowtalk.hytale.EffectRegistry;
import com.chromecide.lowtalk.hytale.functions.BuiltinFunctions;
import com.chromecide.lowtalk.runtime.RuntimeError;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.Frozen;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.support.DisplayNameSupport;
import com.hypixel.hytale.server.npc.role.support.StateSupport;
import org.joml.Vector3d;

import javax.annotation.Nonnull;
import java.util.logging.Level;

/**
 * Control of the NPC being talked to (and spawning new ones), through the NPC plugin's own support classes:
 * DisplayNameSupport for names, StateSupport for role states, NPCEntity for despawning, NPCPlugin for spawning.
 */
public final class NpcEffects {
    private NpcEffects() {}

    public static void register(@Nonnull EffectRegistry effects, @Nonnull LowTalkPlugin plugin) {

        // <<npc_name "Elder Mara">>  or  <<npc_name clear>>  (persists with the NPC)
        effects.register("npc_name", (session, effect) -> {
            Store<EntityStore> store = BuiltinFunctions.store(session.getContext());
            Ref<EntityStore> npcRef = BuiltinEffects.npcRef(session, store);
            if (npcRef == null) throw new RuntimeError(effect.pos(), "<<npc_name>> needs an NPC (this dialogue has none)");
            String name = effect.args().get(0);
            if (name.equalsIgnoreCase("clear")) {
                DisplayNameSupport.setDisplayName(npcRef, null, true, store);
            } else {
                DisplayNameSupport.setDisplayName(npcRef, name, store);
            }
            return null;
        });

        // <<state Name [SubState]>>  puts the NPC's role into a state defined in its role JSON
        effects.register("state", (session, effect) -> {
            Store<EntityStore> store = BuiltinFunctions.store(session.getContext());
            Ref<EntityStore> npcRef = BuiltinEffects.npcRef(session, store);
            if (npcRef == null) throw new RuntimeError(effect.pos(), "<<state>> needs an NPC (this dialogue has none)");
            String state = effect.args().get(0);
            String sub = effect.args().size() > 1 ? effect.args().get(1) : null;
            try {
                StateSupport.get(npcRef, store).setState(npcRef, state, sub, store);
            } catch (RuntimeException e) {
                throw new RuntimeError(effect.pos(), "could not enter state '" + state + "': " + e.getMessage());
            }
            return null;
        });

        // <<despawn>>  ends the conversation and removes the NPC the way the game retires NPCs
        effects.register("despawn", (session, effect) -> {
            Store<EntityStore> store = BuiltinFunctions.store(session.getContext());
            Ref<EntityStore> npcRef = BuiltinEffects.npcRef(session, store);
            if (npcRef == null) throw new RuntimeError(effect.pos(), "<<despawn>> needs an NPC (this dialogue has none)");
            NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
            session.end();
            if (npc != null) {
                // The NPC plugin retires despawning NPCs from its per-NPC tick, which a frozen NPC never gets; thaw it
                // first, the way /npc thaw does, so the game's own despawn (animation included) can run.
                store.tryRemoveComponent(npcRef, Frozen.getComponentType());
                npc.setToDespawn();
                npc.setDespawnTime(0.0f);
            }
            return null;
        });

        // <<spawn Role_Id [right up forward]>>  spawns near the player; offsets are blocks relative to where they face
        effects.register("spawn", (session, effect) -> {
            String role = effect.args().get(0);
            Ref<EntityStore> playerRef = BuiltinFunctions.playerEntity(session.getContext());
            Store<EntityStore> store = playerRef.getStore();
            TransformComponent transform = store.getComponent(playerRef, TransformComponent.getComponentType());
            if (transform == null) return null;
            double right = offset(effect, 1, 0.0);
            double up = offset(effect, 2, 0.0);
            double forward = offset(effect, 3, 2.0);
            float yaw = transform.getRotation().yaw();
            double fx = -Math.sin(yaw), fz = Math.cos(yaw); // unit vector the player faces, on the ground plane
            double rx = fz, rz = -fx;
            Vector3d p = transform.getPosition();
            Vector3d pos = new Vector3d(p.x + fx * forward + rx * right, p.y + up, p.z + fz * forward + rz * right);
            Rotation3f facePlayer = Rotation3f.lookAt(pos, p);
            var pair = NPCPlugin.get().spawnNPC(store, role, null, pos, new Rotation3f(0.0f, facePlayer.yaw(), 0.0f));
            if (pair == null) throw new RuntimeError(effect.pos(), "could not spawn an NPC with role '" + role + "'");
            plugin.getLogger().at(Level.INFO).log("<<spawn>> %s for %s", role, session.getPlayer().getUsername());
            return null;
        });
    }

    private static double offset(com.chromecide.lowtalk.runtime.Effect effect, int index, double def) {
        if (effect.args().size() <= index) return def;
        try {
            return Double.parseDouble(effect.args().get(index).trim());
        } catch (NumberFormatException e) {
            throw new RuntimeError(effect.pos(), "<<spawn>> offsets must be numbers, got " + effect.args().get(index));
        }
    }
}
