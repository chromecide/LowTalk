package com.chromecide.lowtalk.hytale;

import com.chromecide.lowtalk.api.DialogueContext;
import com.chromecide.lowtalk.model.Dialogue;
import com.chromecide.lowtalk.runtime.Context;
import com.chromecide.lowtalk.runtime.RuntimeError;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The runtime Context backed by real players, NPCs, saved variables, and registered functions. */
public class HytaleContext implements Context, DialogueContext {

    private final Dialogue dialogue;
    private final PlayerRef player;
    private final UUID npcId;
    private final String npcName;
    private final VariableStore store;
    private final FunctionRegistry functions;
    private final VariableStore.Record localRecord;
    private final VariableStore.Record playerRecord;
    private final VariableStore.Record npcRecord;
    private final VariableStore.Record worldRecord;
    private final Map<String, Object> tmp = new HashMap<>();

    public HytaleContext(@Nonnull Dialogue dialogue, @Nonnull PlayerRef player, @Nonnull UUID npcId, @Nonnull String npcName,
                         @Nonnull VariableStore store, @Nonnull FunctionRegistry functions) {
        this.dialogue = dialogue;
        this.player = player;
        this.npcId = npcId;
        this.npcName = npcName;
        this.store = store;
        this.functions = functions;
        this.localRecord = store.pair(player.getUuid(), npcId);
        this.playerRecord = store.player(player.getUuid());
        this.npcRecord = store.npc(npcId);
        this.worldRecord = store.world();
    }

    public Dialogue getDialogue() { return dialogue; }
    @Override public String getDialogueId() { return dialogue.id(); }
    @Override public PlayerRef getPlayer() { return player; }
    @Override public UUID getNpcId() { return npcId; }

    @Override
    public boolean hasCommand(String name) {
        com.chromecide.lowtalk.LowTalkPlugin p = com.chromecide.lowtalk.LowTalkPlugin.get();
        return p == null || p.getEffects().has(name);
    }

    @Override
    public void warn(String message) {
        com.chromecide.lowtalk.LowTalkPlugin p = com.chromecide.lowtalk.LowTalkPlugin.get();
        if (p != null) p.getLogger().at(java.util.logging.Level.WARNING).atMostEvery(30, java.util.concurrent.TimeUnit.SECONDS).log("%s", message);
    }

    @Override
    public com.hypixel.hytale.component.Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> getEntityStore() {
        com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> ref = player.getReference();
        return ref == null || !ref.isValid() ? null : ref.getStore();
    }

    @Override
    public com.hypixel.hytale.server.core.universe.world.World getWorld() {
        var store = getEntityStore();
        return store == null ? null : store.getExternalData().getWorld();
    }

    @Override
    public com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> getNpcRef() {
        if (npcId.getMostSignificantBits() == 0L && npcId.getLeastSignificantBits() == 0L) return null;
        var store = getEntityStore();
        if (store == null) return null;
        var ref = store.getExternalData().getRefFromUUID(npcId);
        return ref != null && ref.isValid() ? ref : null;
    }
    @Override public String getNpcName() { return npcName; }
    public VariableStore getStore() { return store; }

    private VariableStore.Record recordFor(String scope) {
        return switch (scope) {
            case "local" -> localRecord;
            case "player" -> playerRecord;
            case "npc" -> npcRecord;
            case "world" -> worldRecord;
            default -> throw new RuntimeError("unknown variable scope " + scope);
        };
    }

    @Override
    public Object getVar(String scope, String name) {
        if (scope.equals("tmp")) return tmp.get(name);
        return store.get(recordFor(scope), dialogue.scope(), name);
    }

    @Override
    public void setVar(String scope, String name, Object value) {
        if (scope.equals("tmp")) {
            tmp.put(name, value);
            return;
        }
        store.set(recordFor(scope), dialogue.scope(), name, value);
    }

    @Override
    public Object call(String function, List<Object> args) {
        return switch (function) {
            case "player" -> player.getUsername();
            case "npc" -> npcName;
            default -> functions.call(this, function, args);
        };
    }

    @Override
    public boolean hasVisited(String node) {
        return store.hasVisited(localRecord, dialogue.id(), node);
    }

    @Override
    public void markVisited(String node) {
        store.markVisited(localRecord, dialogue.id(), node);
    }

    @Override
    public boolean onceDone(String key) {
        return store.onceDone(localRecord, dialogue.id(), key);
    }

    @Override
    public void markOnce(String key) {
        store.markOnce(localRecord, dialogue.id(), key);
    }
}
