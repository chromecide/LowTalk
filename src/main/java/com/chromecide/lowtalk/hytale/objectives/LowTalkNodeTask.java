package com.chromecide.lowtalk.hytale.objectives;

import com.hypixel.hytale.builtin.adventure.objectives.Objective;
import com.hypixel.hytale.builtin.adventure.objectives.task.CountObjectiveTask;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.builtin.adventure.objectives.transaction.TransactionRecord;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Runtime state of a {@link LowTalkNodeTaskAsset}: a counter, advanced by {@link ObjectiveNodes} when the node is reached. */
public class LowTalkNodeTask extends CountObjectiveTask {
    @Nonnull
    public static final BuilderCodec<LowTalkNodeTask> CODEC = BuilderCodec.builder(LowTalkNodeTask.class, LowTalkNodeTask::new, CountObjectiveTask.CODEC).build();

    public LowTalkNodeTask(@Nonnull LowTalkNodeTaskAsset asset, int taskSetIndex, int taskIndex) {
        super(asset, taskSetIndex, taskIndex);
    }

    protected LowTalkNodeTask() {
    }

    @Override
    public LowTalkNodeTaskAsset getAsset() {
        return (LowTalkNodeTaskAsset) super.getAsset();
    }

    /**
     * Nothing to register: progress comes from the dialogue runtime. If the objective was started with an NPC as its
     * marker (as <<objective>> does), put a tracker marker on that NPC so the player knows who to talk to.
     */
    @Nullable
    @Override
    protected TransactionRecord[] setup0(@Nonnull Objective objective, @Nonnull World world, @Nonnull Store<EntityStore> store) {
        if (objective.getMarkerUUID() != null) {
            try {
                org.joml.Vector3d position = objective.getPosition(store);
                if (position != null) {
                    addMarker(new com.hypixel.hytale.builtin.adventure.objectives.markers.ObjectiveTaskMarker(
                            "LowTalk_" + objective.getObjectiveUUID() + "_" + taskIndex,
                            new com.hypixel.hytale.math.vector.Transform(position), "Home.png",
                            com.hypixel.hytale.server.core.Message.translation(getAsset().getDescriptionKey(objective.getObjectiveId(), taskSetIndex, taskIndex))));
                }
            } catch (RuntimeException ignored) {
                // no marker, the tracker text still shows
            }
        }
        return null;
    }
}
