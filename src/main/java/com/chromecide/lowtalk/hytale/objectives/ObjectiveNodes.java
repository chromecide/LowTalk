package com.chromecide.lowtalk.hytale.objectives;

import com.chromecide.lowtalk.LowTalkPlugin;
import com.hypixel.hytale.builtin.adventure.objectives.Objective;
import com.hypixel.hytale.builtin.adventure.objectives.ObjectiveDataStore;
import com.hypixel.hytale.builtin.adventure.objectives.ObjectivePlugin;
import com.hypixel.hytale.builtin.adventure.objectives.task.ObjectiveTask;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/** Registers the LowTalkNode task type with the objectives plugin and advances such tasks when nodes are reached. */
public final class ObjectiveNodes {
    private ObjectiveNodes() {}

    /** Call from the plugin's setup(); the objectives plugin must already be set up (declared as a dependency). */
    public static void register(@Nonnull LowTalkPlugin plugin) {
        ObjectivePlugin objectives = ObjectivePlugin.get();
        if (objectives == null) {
            plugin.getLogger().at(Level.WARNING).log("Objectives plugin not available; the LowTalkNode task type is not registered");
            return;
        }
        objectives.registerTask(LowTalkNodeTaskAsset.TYPE_ID, LowTalkNodeTaskAsset.class, LowTalkNodeTaskAsset.CODEC,
                LowTalkNodeTask.class, LowTalkNodeTask.CODEC, LowTalkNodeTask::new);
    }

    /**
     * The player reached a node: advance every active LowTalkNode task that names this dialogue and node.
     * World thread, with the player's entity in {@code store}.
     * @return how many tasks were advanced
     */
    public static int nodeReached(@Nonnull LowTalkPlugin plugin, @Nonnull PlayerRef playerRef, @Nonnull String dialogueId, @Nonnull String node) {
        ObjectivePlugin objectives = ObjectivePlugin.get();
        if (objectives == null || objectives.getObjectiveDataStore() == null) return 0;
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) return 0;
        Store<EntityStore> store = ref.getStore();
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return 0;
        Set<UUID> active = player.getPlayerConfigData().getActiveObjectiveUUIDs();
        if (active == null || active.isEmpty()) return 0;
        ObjectiveDataStore data = objectives.getObjectiveDataStore();
        UUID playerId = playerRef.getUuid();
        int advanced = 0;
        for (UUID objectiveId : new ArrayList<>(active)) {
            Objective objective = data.getObjective(objectiveId);
            if (objective == null || !objective.getActivePlayerUUIDs().contains(playerId)) continue;
            ObjectiveTask[] tasks = objective.getCurrentTasks();
            if (tasks == null) continue;
            for (ObjectiveTask t : tasks) {
                if (t instanceof LowTalkNodeTask task && !task.isComplete()
                        && dialogueId.equals(task.getAsset().getDialogue()) && node.equals(task.getAsset().getNode())) {
                    try {
                        task.increaseTaskCompletion(store, ref, 1, objective);
                        advanced++;
                    } catch (RuntimeException e) {
                        plugin.getLogger().at(Level.WARNING).log("Could not advance objective task for %s at %s/%s: %s",
                                playerRef.getUsername(), dialogueId, node, e.toString());
                    }
                }
            }
        }
        return advanced;
    }
}
