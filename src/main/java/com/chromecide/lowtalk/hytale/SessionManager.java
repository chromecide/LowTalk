package com.chromecide.lowtalk.hytale;

import com.chromecide.lowtalk.LowTalkPlugin;

import com.chromecide.lowtalk.model.Dialogue;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** One session per player at a time. */
public class SessionManager {

    private final LowTalkPlugin plugin;
    private final Map<UUID, DialogueSession> sessions = new ConcurrentHashMap<>();

    public SessionManager(@Nonnull LowTalkPlugin plugin) {
        this.plugin = plugin;
    }

    @Nullable
    public DialogueSession get(UUID playerId) {
        return sessions.get(playerId);
    }

    /** World thread only. Ends any session the player already has. */
    @Nullable
    public DialogueSession open(@Nonnull Dialogue dialogue, @Nonnull PlayerRef player, @Nonnull Ref<EntityStore> playerEntity,
                                @Nonnull Store<EntityStore> store, @Nonnull World world, @Nonnull NpcInfo npc) {
        DialogueSession existing = sessions.remove(player.getUuid());
        if (existing != null) existing.end();
        DialogueSession s = DialogueSession.open(plugin, plugin.getFunctions(), dialogue, player, playerEntity, store, world, npc.id(), npc.name());
        if (s != null) sessions.put(player.getUuid(), s);
        return s;
    }

    public void removeEnded(DialogueSession s) {
        sessions.remove(s.getPlayer().getUuid(), s);
    }

    public void end(UUID playerId) {
        DialogueSession s = sessions.remove(playerId);
        if (s != null) s.end();
    }

    /** End every session (reload, shutdown). Safe from any thread; each session closes on its own world thread. */
    public void endAll() {
        List<DialogueSession> all = new ArrayList<>(sessions.values());
        sessions.clear();
        for (DialogueSession s : all) {
            s.getWorld().execute(s::end);
        }
    }

    public boolean isTalkingTo(UUID npcId) {
        for (DialogueSession s : sessions.values()) {
            if (!s.isEnded() && s.getNpcId().equals(npcId)) return true;
        }
        return false;
    }

    public int count() {
        return sessions.size();
    }
}
