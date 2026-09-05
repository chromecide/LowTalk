package com.chromecide.lowtalk.hytale;

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
public class HytaleContext implements Context {

    private final Dialogue dialogue;
    private final PlayerRef player;
    private final UUID npcId;
    private final String npcName;
    private final VariableStore store;
    private final FunctionRegistry functions;
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
        this.playerRecord = store.player(player.getUuid());
        this.npcRecord = store.npc(npcId);
        this.worldRecord = store.world();
    }

    public Dialogue getDialogue() { return dialogue; }
    public PlayerRef getPlayer() { return player; }
    public UUID getNpcId() { return npcId; }
    public String getNpcName() { return npcName; }
    public VariableStore getStore() { return store; }

    private VariableStore.Record recordFor(String scope) {
        return switch (scope) {
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
        return store.hasVisited(playerRecord, dialogue.id(), node);
    }

    @Override
    public void markVisited(String node) {
        store.markVisited(playerRecord, dialogue.id(), node);
    }

    @Override
    public boolean onceDone(String key) {
        return store.onceDone(playerRecord, dialogue.id(), key);
    }

    @Override
    public void markOnce(String key) {
        store.markOnce(playerRecord, dialogue.id(), key);
    }
}
