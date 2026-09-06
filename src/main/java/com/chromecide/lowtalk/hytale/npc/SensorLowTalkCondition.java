package com.chromecide.lowtalk.hytale.npc;

import com.chromecide.lowtalk.LowTalkPlugin;
import com.chromecide.lowtalk.hytale.NpcInfo;
import com.chromecide.lowtalk.hytale.integrations.DialogueExpressions;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.SensorBase;
import com.hypixel.hytale.server.npc.instructions.ExecutionSupport;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Runtime of {@link BuilderSensorLowTalkCondition}. */
public class SensorLowTalkCondition extends SensorBase {
    private final String dialogueId;
    private final String condition;

    public SensorLowTalkCondition(@Nonnull BuilderSensorLowTalkCondition builder, @Nonnull BuilderSupport support) {
        super(builder);
        String d = builder.getDialogue(support);
        String c = builder.getCondition(support);
        this.dialogueId = d == null ? "" : d.trim();
        this.condition = c == null ? "" : c.trim();
    }

    @Override
    public boolean matches(@Nonnull Ref<EntityStore> ref, @Nonnull ExecutionSupport executionSupport, double dt, @Nonnull Store<EntityStore> store) {
        if (!super.matches(ref, executionSupport, dt, store)) return false;
        LowTalkPlugin plugin = LowTalkPlugin.get();
        if (plugin == null) return false;
        Ref<EntityStore> playerEntity = executionSupport.getStateSupport().getInteractionIterationTarget();
        if (playerEntity == null || !playerEntity.isValid()) return false;
        PlayerRef playerRef = store.getComponent(playerEntity, PlayerRef.getComponentType());
        if (playerRef == null) return false;
        NpcInfo npc = NpcInfo.of(ref, store, playerRef, plugin.getStore());
        String dialogue = dialogueId;
        if (dialogue.isEmpty()) {
            var candidates = plugin.getRegistry().candidates(npc.role(), npc.tags());
            if (candidates.isEmpty()) return false;
            dialogue = candidates.get(0).id();
        }
        return DialogueExpressions.test(plugin, dialogue, condition, playerRef, npc.id(), npc.name());
    }

    @Nullable
    @Override
    public InfoProvider getSensorInfo() {
        return null;
    }
}
