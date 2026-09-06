package com.chromecide.lowtalk.hytale;

import com.chromecide.lowtalk.LowTalkPlugin;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.support.StateSupport;
import org.joml.Vector3d;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Gives NPCs bound to a dialogue the same "Press [key] to talk" prompt the game's own talkative NPCs show. A vanilla
 * role only marks a player interactable from its own behaviour tree (SetInteractable), which a bound NPC has no
 * reason to do and a frozen one never runs, so this does the same thing through the game's own StateSupport for
 * every bound NPC near each player: Interactable component on the NPC, hint text sent to that player's client, and
 * the mark withdrawn again when the player walks away.
 */
public final class NpcHintSystem extends EntityTickingSystem<EntityStore> {
    /** How close a player must be before the prompt appears; the client only shows it when they look at the NPC anyway. */
    private static final double RANGE = 6.0;
    private static final float INTERVAL_SECONDS = 0.25f;

    private final LowTalkPlugin plugin;
    private final Query<EntityStore> query = Query.and(Player.getComponentType(), TransformComponent.getComponentType());
    private final Map<UUID, Float> timers = new HashMap<>();
    private final Map<UUID, Map<UUID, Ref<EntityStore>>> marked = new HashMap<>();

    public NpcHintSystem(@Nonnull LowTalkPlugin plugin) {
        this.plugin = plugin;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return query;
    }

    @Override
    public boolean isParallel(int archetypeChunkSize, int taskCount) {
        return false;
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> store,
                     @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        LowTalkConfig settings = plugin.getSettings();
        if (!settings.isUseHook() || !settings.isShowHint()) return;
        Ref<EntityStore> playerEntity = chunk.getReferenceTo(index);
        PlayerRef playerRef = store.getComponent(playerEntity, PlayerRef.getComponentType());
        TransformComponent transform = chunk.getComponent(index, TransformComponent.getComponentType());
        if (playerRef == null || transform == null) return;
        UUID playerId = playerRef.getUuid();
        float t = timers.merge(playerId, dt, Float::sum);
        if (t < INTERVAL_SECONDS) return;
        timers.put(playerId, 0.0f);

        Vector3d pos = transform.getPosition();
        Map<UUID, Ref<EntityStore>> near = new HashMap<>();
        for (Ref<EntityStore> ref : new ArrayList<>(TargetUtil.getAllEntitiesInSphere(pos, RANGE, store))) {
            if (!ref.isValid() || store.getComponent(ref, NPCEntity.getComponentType()) == null) continue;
            NpcInfo npc = NpcInfo.of(ref, store, playerRef, plugin.getStore());
            if (npc == null || plugin.getRegistry().candidates(npc.role(), npc.tags()).isEmpty()) continue;
            near.put(npc.id(), ref);
        }
        Map<UUID, Ref<EntityStore>> before = marked.getOrDefault(playerId, Map.of());
        List<Ref<EntityStore>> gone = new ArrayList<>();
        for (Map.Entry<UUID, Ref<EntityStore>> e : before.entrySet()) if (!near.containsKey(e.getKey())) gone.add(e.getValue());
        if (near.isEmpty()) marked.remove(playerId); else marked.put(playerId, near);
        if (near.isEmpty() && gone.isEmpty()) return;

        String hint = settings.getHintKey();
        List<Ref<EntityStore>> show = new ArrayList<>(near.values());
        // Component changes wait for the command buffer, as the game's own systems do while a tick is iterating.
        commandBuffer.run(s -> {
            if (!playerEntity.isValid()) return;
            for (Ref<EntityStore> ref : show) mark(ref, playerEntity, true, hint, s);
            for (Ref<EntityStore> ref : gone) mark(ref, playerEntity, false, hint, s);
        });
        prune();
    }

    private static void mark(Ref<EntityStore> npc, Ref<EntityStore> player, boolean on, String hint, Store<EntityStore> store) {
        if (!npc.isValid()) return;
        StateSupport state = store.getComponent(npc, StateSupport.getComponentType());
        if (state == null) return;
        state.setInteractable(npc, player, on, hint, true, store);
    }

    /** Forget players who are gone. */
    private void prune() {
        Set<UUID> online = new HashSet<>();
        for (PlayerRef p : com.hypixel.hytale.server.core.universe.Universe.get().getPlayers()) online.add(p.getUuid());
        for (Iterator<UUID> it = timers.keySet().iterator(); it.hasNext(); ) if (!online.contains(it.next())) it.remove();
        for (Iterator<UUID> it = marked.keySet().iterator(); it.hasNext(); ) if (!online.contains(it.next())) it.remove();
    }
}
