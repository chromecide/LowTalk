package com.chromecide.lowtalk.hytale.npc;

import com.chromecide.lowtalk.LowTalkPlugin;
import com.chromecide.lowtalk.hytale.NpcInfo;
import com.chromecide.lowtalk.model.Dialogue;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.ActionBase;
import com.hypixel.hytale.server.npc.instructions.ExecutionSupport;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/** Runtime of {@link BuilderActionLowTalkOpenDialogue}: opens the dialogue for the interaction target. */
public class ActionLowTalkOpenDialogue extends ActionBase {
    private final String dialogueId;

    public ActionLowTalkOpenDialogue(@Nonnull BuilderActionLowTalkOpenDialogue builder, @Nonnull BuilderSupport support) {
        super(builder);
        String id = builder.getDialogue(support);
        this.dialogueId = id == null ? "" : id.trim();
    }

    @Override
    public boolean canExecute(@Nonnull Ref<EntityStore> ref, @Nonnull ExecutionSupport executionSupport, @Nullable InfoProvider sensorInfo,
                              double dt, @Nonnull Store<EntityStore> store) {
        return super.canExecute(ref, executionSupport, sensorInfo, dt, store) && target(executionSupport, sensorInfo) != null;
    }

    @Override
    public boolean execute(@Nonnull Ref<EntityStore> ref, @Nonnull ExecutionSupport executionSupport, @Nullable InfoProvider sensorInfo,
                           double dt, @Nonnull Store<EntityStore> store) {
        super.execute(ref, executionSupport, sensorInfo, dt, store);
        LowTalkPlugin plugin = LowTalkPlugin.get();
        Ref<EntityStore> playerEntity = target(executionSupport, sensorInfo);
        if (plugin == null || playerEntity == null || !playerEntity.isValid()) return true;
        PlayerRef playerRef = store.getComponent(playerEntity, PlayerRef.getComponentType());
        if (playerRef == null) return true;
        World world = store.getExternalData().getWorld();
        NpcInfo npc = NpcInfo.of(ref, store, playerRef, plugin.getStore());
        Dialogue d;
        if (dialogueId.isEmpty()) {
            List<Dialogue> candidates = plugin.getRegistry().candidates(npc.role(), npc.tags());
            d = candidates.isEmpty() ? null : candidates.get(0);
            if (d == null) {
                plugin.getLogger().at(Level.WARNING).atMostEvery(30, TimeUnit.SECONDS)
                        .log("LowTalkOpenDialogue on %s: no Dialogue given and nothing is bound to this NPC", npc.role());
                return true;
            }
        } else {
            d = plugin.getRegistry().byId(dialogueId);
            if (d == null) {
                plugin.getLogger().at(Level.WARNING).atMostEvery(30, TimeUnit.SECONDS)
                        .log("LowTalkOpenDialogue: dialogue '%s' is not loaded", dialogueId);
                return true;
            }
        }
        if (plugin.getSessions().get(playerRef.getUuid()) != null) return true; // already talking
        plugin.getSessions().openFor(d, playerRef, playerEntity, store, world, npc);
        return true;
    }

    /** The player this NPC is interacting with, else the sensor's target. */
    @Nullable
    private static Ref<EntityStore> target(@Nonnull ExecutionSupport executionSupport, @Nullable InfoProvider sensorInfo) {
        Ref<EntityStore> t = executionSupport.getStateSupport().getInteractionIterationTarget();
        if (t == null && sensorInfo != null && sensorInfo.getPositionProvider() != null) t = sensorInfo.getPositionProvider().getTarget();
        return t;
    }
}
