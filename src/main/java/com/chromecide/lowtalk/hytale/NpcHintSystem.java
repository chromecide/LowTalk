package com.chromecide.lowtalk.hytale;

import com.chromecide.lowtalk.LowTalkPlugin;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.InteractableUpdate;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.Interactable;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
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
 * Gives NPCs bound to a dialogue the same "Press [key] to talk" prompt the game's own talkative NPCs show. The game
 * marks an NPC with the {@code Interactable} component and sends the hint text to each viewer with an
 * {@code InteractableUpdate}; a vanilla role does that from its own behaviour tree (SetInteractable), which a bound
 * NPC has no reason to do and a frozen one never runs. This system does the same two things for every bound NPC near
 * each player, and withdraws the mark when the last nearby player walks away. NPCs whose role has its own
 * interaction instruction (like the shipped LowTalk_Talker) are left alone: they already manage their prompt.
 */
public final class NpcHintSystem extends EntityTickingSystem<EntityStore> {
    /** How close a player must be before the prompt appears; the client only shows it when they look at the NPC anyway. */
    private static final double RANGE = 6.0;
    private static final float INTERVAL_SECONDS = 0.25f;

    private final LowTalkPlugin plugin;
    private final Query<EntityStore> query = Query.and(Player.getComponentType(), TransformComponent.getComponentType());
    private final Map<UUID, Float> timers = new HashMap<>();
    /** Player → NPC id → hint text already delivered to that player's client. */
    private final Map<UUID, Map<UUID, Boolean>> marked = new HashMap<>();
    /** NPC id → players currently near it, so the component goes when the last one leaves. */
    private final Map<UUID, Set<UUID>> watchers = new HashMap<>();

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
            if (!ref.isValid()) continue;
            NPCEntity entity = store.getComponent(ref, NPCEntity.getComponentType());
            if (entity == null || (entity.getRole() != null && entity.getRole().getInteractionInstruction() != null)) continue;
            NpcInfo npc = NpcInfo.of(ref, store, playerRef, plugin.getStore());
            if (npc == null || plugin.getRegistry().candidates(npc.role(), npc.tags()).isEmpty()) continue;
            near.put(npc.id(), ref);
        }
        Map<UUID, Boolean> mine = marked.computeIfAbsent(playerId, k -> new HashMap<>());
        List<Ref<EntityStore>> show = new ArrayList<>();
        List<UUID> showIds = new ArrayList<>();
        for (Map.Entry<UUID, Ref<EntityStore>> e : near.entrySet()) {
            if (Boolean.TRUE.equals(mine.get(e.getKey()))) continue; // marked and hint delivered
            show.add(e.getValue());
            showIds.add(e.getKey());
            watchers.computeIfAbsent(e.getKey(), k -> new HashSet<>()).add(playerId);
        }
        List<UUID> leftIds = new ArrayList<>();
        for (UUID id : mine.keySet()) if (!near.containsKey(id)) leftIds.add(id);
        for (UUID id : leftIds) {
            mine.remove(id);
            Set<UUID> w = watchers.get(id);
            if (w != null) { w.remove(playerId); if (w.isEmpty()) watchers.remove(id); }
        }
        if (mine.isEmpty()) marked.remove(playerId);
        if (show.isEmpty() && leftIds.isEmpty()) return;

        String hint = settings.getHintKey();
        // Component changes wait for the command buffer, as the game's own systems do while a tick is iterating.
        commandBuffer.run(s -> {
            if (!playerEntity.isValid()) return;
            EntityTrackerSystems.EntityViewer viewer = s.getComponent(playerEntity, EntityTrackerSystems.EntityViewer.getComponentType());
            for (int i = 0; i < show.size(); i++) {
                Ref<EntityStore> npc = show.get(i);
                if (!npc.isValid()) continue;
                if (!s.getArchetype(npc).contains(Interactable.getComponentType())) s.ensureComponent(npc, Interactable.getComponentType());
                if (viewer != null && viewer.visible.contains(npc)) {
                    viewer.queueUpdate(npc, new InteractableUpdate(hint));
                    Map<UUID, Boolean> m = marked.get(playerId);
                    if (m != null) m.put(showIds.get(i), Boolean.TRUE);
                } else {
                    marked.computeIfAbsent(playerId, k -> new HashMap<>()).putIfAbsent(showIds.get(i), Boolean.FALSE); // retry next pass
                }
            }
            for (UUID id : leftIds) {
                if (watchers.containsKey(id)) continue; // someone else is still close
                Ref<EntityStore> npc = findNpc(id, pos, s);
                if (npc != null && npc.isValid() && s.getArchetype(npc).contains(Interactable.getComponentType())) {
                    s.removeComponent(npc, Interactable.getComponentType());
                }
            }
        });
        prune();
    }

    /** The NPC with this id, if it is still around the player. */
    private static Ref<EntityStore> findNpc(UUID id, Vector3d around, Store<EntityStore> store) {
        for (Ref<EntityStore> ref : new ArrayList<>(TargetUtil.getAllEntitiesInSphere(around, RANGE * 4, store))) {
            if (!ref.isValid()) continue;
            var uuid = store.getComponent(ref, com.hypixel.hytale.server.core.entity.UUIDComponent.getComponentType());
            if (uuid != null && id.equals(uuid.getUuid())) return ref;
        }
        return null;
    }

    /** Forget players who are gone. */
    private void prune() {
        Set<UUID> online = new HashSet<>();
        for (PlayerRef p : Universe.get().getPlayers()) online.add(p.getUuid());
        for (Iterator<UUID> it = timers.keySet().iterator(); it.hasNext(); ) if (!online.contains(it.next())) it.remove();
        for (Iterator<Map.Entry<UUID, Map<UUID, Boolean>>> it = marked.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Map<UUID, Boolean>> e = it.next();
            if (online.contains(e.getKey())) continue;
            for (UUID npc : e.getValue().keySet()) {
                Set<UUID> w = watchers.get(npc);
                if (w != null) { w.remove(e.getKey()); if (w.isEmpty()) watchers.remove(npc); }
            }
            it.remove();
        }
    }
}
