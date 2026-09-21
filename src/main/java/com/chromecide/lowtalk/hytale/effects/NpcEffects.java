package com.chromecide.lowtalk.hytale.effects;

import com.chromecide.lowtalk.LowTalkPlugin;
import com.chromecide.lowtalk.hytale.EffectRegistry;
import com.chromecide.lowtalk.hytale.functions.BuiltinFunctions;
import com.chromecide.lowtalk.runtime.RuntimeError;
import com.chromecide.lowtalk.hytale.EntitySaving;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.Frozen;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.support.DisplayNameSupport;
import com.hypixel.hytale.server.npc.role.support.MarkedEntitySupport;
import com.hypixel.hytale.server.npc.role.support.StateSupport;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
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
            markForSaving(npcRef, store);
            return null;
        });

        // <<calm>>  this NPC forgets whatever it was fighting
        effects.register("calm", (session, effect) -> {
            Store<EntityStore> store = BuiltinFunctions.store(session.getContext());
            Ref<EntityStore> npcRef = BuiltinEffects.npcRef(session, store);
            if (npcRef == null) throw new RuntimeError(effect.pos(), "<<calm>> needs an NPC (this dialogue has none)");
            MarkedEntitySupport marks = store.getComponent(npcRef, MarkedEntitySupport.getComponentType());
            if (marks == null) return null;   // a role that never marks a target has nothing to forget
            Ref<EntityStore>[] targets = marks.getEntityTargets();
            if (targets == null) return null;
            for (int slot = 0; slot < targets.length; slot++) {
                if (targets[slot] != null) marks.clearMarkedEntity(slot);
            }
            markForSaving(npcRef, store);
            return null;
        });

        // <<state Name [SubState]>>  puts the NPC's role into a state defined in its role JSON
        effects.register("state", (session, effect) -> {
            Store<EntityStore> store = BuiltinFunctions.store(session.getContext());
            Ref<EntityStore> npcRef = BuiltinEffects.npcRef(session, store);
            if (npcRef == null) throw new RuntimeError(effect.pos(), "<<state>> needs an NPC (this dialogue has none)");
            String state = effect.args().get(0);
            String sub = effect.args().size() > 1 ? effect.args().get(1) : null;
            StateSupport support = StateSupport.get(npcRef, store);
            // setState ignores a name the role does not have, so a typo used to do nothing at all and say
            // nothing about it. A creator has no way to see that from in game, and the NPC simply stays put.
            if (support.getStateHelper().getStateIndex(state) < 0) {
                throw new RuntimeError(effect.pos(), "this NPC's role has no state called '" + state + "'");
            }
            try {
                support.setState(npcRef, state, sub, store);
            } catch (NullPointerException e) {
                // A name in the role's state map with no sub-states behind it -- "start", the engine's own
                // placeholder, is one -- throws inside the game rather than being refused. The creator does
                // not need the stack trace's worth of that; they need to know the state is not usable.
                throw new RuntimeError(effect.pos(), "this NPC's role lists a state called '" + state
                        + "' but defines no sub-states for it, so it cannot be entered");
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
            // A tag is recognised by its leading @ wherever it sits, the way a title's style is recognised by
            // being one. Without it a spawned NPC can only ever have the dialogue its role already had, so
            // "spawn a guard, then talk to the guard" could not be written at all.
            String tag = null;
            List<String> placement = new ArrayList<>();
            for (String a : effect.args().subList(1, effect.args().size())) {
                String t = a.trim();
                if (t.startsWith("@") && t.length() > 1) {
                    if (tag != null) throw new RuntimeError(effect.pos(), "<<spawn>> takes one @tag, not two");
                    tag = t.substring(1);
                } else {
                    placement.add(t);
                }
            }
            double right = number(effect, placement, 0, 0.0);
            double up = number(effect, placement, 1, 0.0);
            double forward = number(effect, placement, 2, 2.0);
            float yaw = transform.getRotation().yaw();
            double fx = -Math.sin(yaw), fz = Math.cos(yaw); // unit vector the player faces, on the ground plane
            double rx = fz, rz = -fx;
            Vector3d p = transform.getPosition();
            Vector3d pos = new Vector3d(p.x + fx * forward + rx * right, p.y + up, p.z + fz * forward + rz * right);
            Rotation3f facePlayer = Rotation3f.lookAt(pos, p);
            var pair = NPCPlugin.get().spawnNPC(store, role, null, pos, new Rotation3f(0.0f, facePlayer.yaw(), 0.0f));
            if (pair == null) throw new RuntimeError(effect.pos(), "could not spawn an NPC with role '" + role + "'");
            if (tag != null) {
                var uuid = store.getComponent(pair.first(),
                        com.hypixel.hytale.server.core.entity.UUIDComponent.getComponentType());
                if (uuid == null) {
                    throw new RuntimeError(effect.pos(), "the NPC spawned but has no id, so it cannot be tagged '" + tag + "'");
                }
                plugin.getStore().addTag(plugin.getStore().npc(uuid.getUuid()), tag);
                plugin.getStore().flush();
            }
            plugin.getLogger().at(Level.INFO).log("<<spawn>> %s%s for %s", role,
                    tag == null ? "" : " tagged @" + tag, session.getPlayer().getUsername());
            return null;
        });
    }

    /** A placement number, by position among the arguments that are not the tag. */
    private static double number(com.chromecide.lowtalk.runtime.Effect effect, List<String> placement, int index, double def) {
        if (placement.size() <= index) return def;
        try {
            return Double.parseDouble(placement.get(index).trim());
        } catch (NumberFormatException e) {
            throw new RuntimeError(effect.pos(), "<<spawn>> expects numbers for right, up and forward; got '"
                    + placement.get(index) + "'");
        }
    }

    private static double offset(com.chromecide.lowtalk.runtime.Effect effect, int index, double def) {
        if (effect.args().size() <= index) return def;
        try {
            return Double.parseDouble(effect.args().get(index).trim());
        } catch (NumberFormatException e) {
            throw new RuntimeError(effect.pos(), "<<spawn>> offsets must be numbers, got " + effect.args().get(index));
        }
    }

    private static void markForSaving(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        EntitySaving.markForSaving(ref, store);
    }
}
