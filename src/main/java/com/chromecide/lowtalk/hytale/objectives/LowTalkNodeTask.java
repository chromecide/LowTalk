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

    /** Nothing to register: progress comes from the dialogue runtime, not from an event hook. */
    @Nullable
    @Override
    protected TransactionRecord[] setup0(@Nonnull Objective objective, @Nonnull World world, @Nonnull Store<EntityStore> store) {
        return null;
    }
}
