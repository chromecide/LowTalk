package com.chromecide.lowtalk.api;

import com.chromecide.lowtalk.LowTalkPlugin;
import com.chromecide.lowtalk.hytale.DialogueRegistry;
import com.chromecide.lowtalk.hytale.DialogueSession;
import com.chromecide.lowtalk.hytale.NpcInfo;
import com.chromecide.lowtalk.hytale.VariableStore;
import com.chromecide.lowtalk.model.Dialogue;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * The public face of LowTalk for other plugins.
 *
 * <pre>
 *   LowTalkApi api = LowTalkApi.get();
 *   api.registerFunction("reputation", (ctx, args) -> myPlugin.reputationOf(ctx.getPlayer().getUuid()));
 *   api.registerCommand("grant_title", (ctx, args) -> { myPlugin.grant(ctx.getPlayer(), args.get(0)); return "Title granted."; });
 *   api.addListener(new DialogueListener() {
 *       public void onEnd(DialogueContext ctx) { ... }
 *   });
 * </pre>
 *
 * Declare {@code "Chromecide:LowTalk": "*"} in your manifest's Dependencies so LowTalk loads first.
 * Functions and commands registered here also stop the validator warning about them.
 */
public final class LowTalkApi {

    private static LowTalkApi instance;

    private final LowTalkPlugin plugin;

    private LowTalkApi(LowTalkPlugin plugin) {
        this.plugin = plugin;
    }

    /** Available once LowTalk has finished setup. */
    @Nonnull
    public static LowTalkApi get() {
        if (instance == null) {
            LowTalkPlugin p = LowTalkPlugin.get();
            if (p == null) throw new IllegalStateException("LowTalk is not loaded; add it to your manifest Dependencies");
            instance = new LowTalkApi(p);
        }
        return instance;
    }

    // ---- extending the language

    /** A function usable in expressions: {@code <<if myfn($x, "y")>>}. Return a Double, String, or Boolean. */
    public void registerFunction(@Nonnull String name, @Nonnull BiFunction<DialogueContext, List<Object>, Object> function) {
        plugin.getFunctions().register(name, (ctx, args) -> function.apply(ctx, args));
    }

    /** A command usable in nodes: {@code <<mycmd arg1 "arg two">>}. Return a narration line to show, or null. */
    public void registerCommand(@Nonnull String name, @Nonnull BiFunction<DialogueContext, List<String>, String> command) {
        plugin.getEffects().register(name, (session, effect) -> command.apply(session.getContext(), effect.args()));
    }

    public void addListener(@Nonnull DialogueListener listener) {
        plugin.getListeners().add(listener);
    }

    public void removeListener(@Nonnull DialogueListener listener) {
        plugin.getListeners().remove(listener);
    }

    // ---- driving dialogues

    /** Loaded dialogue ids. */
    @Nonnull
    public List<String> dialogueIds() {
        return plugin.getRegistry().ids();
    }

    /**
     * Open a dialogue for a player. If they are looking at an NPC it becomes the speaker;
     * otherwise the dialogue runs with no NPC (its {@code speaker:} directive names the voice).
     * Safe to call from any thread; the work happens on the player's world thread.
     * @return false if the dialogue id is unknown or the player is not in a world
     */
    public boolean open(@Nonnull String dialogueId, @Nonnull PlayerRef player) {
        Dialogue d = plugin.getRegistry().byId(dialogueId);
        Ref<EntityStore> ref = player.getReference();
        if (d == null || ref == null) return false;
        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();
        world.execute(() -> {
            if (!ref.isValid()) return;
            plugin.getSessions().openFor(d, player, ref, store, world, NpcInfo.lookedAt(ref, store, player, plugin.getStore()));
        });
        return true;
    }

    /** End the player's current dialogue, if any. */
    public void close(@Nonnull UUID playerId) {
        plugin.getSessions().end(playerId);
    }

    /** True while the player has a dialogue window open. */
    public boolean isTalking(@Nonnull UUID playerId) {
        DialogueSession s = plugin.getSessions().get(playerId);
        return s != null && !s.isEnded();
    }

    /** Re-read every dialogue file, as /lowtalk reload does. */
    @Nonnull
    public DialogueRegistry.LoadReport reload() {
        return plugin.reloadDialogues();
    }

    // ---- variables outside a conversation

    /** Read a "player" scope variable. {@code scope} is the dialogue's scope (its file name unless it declares one). */
    @Nullable
    public Object getPlayerVar(@Nonnull UUID playerId, @Nonnull String dialogueScope, @Nonnull String name) {
        VariableStore vs = plugin.getStore();
        return vs.get(vs.player(playerId), dialogueScope, name);
    }

    public void setPlayerVar(@Nonnull UUID playerId, @Nonnull String dialogueScope, @Nonnull String name, @Nullable Object value) {
        VariableStore vs = plugin.getStore();
        vs.set(vs.player(playerId), dialogueScope, name, value);
        vs.flush();
    }

    @Nullable
    public Object getWorldVar(@Nonnull String dialogueScope, @Nonnull String name) {
        VariableStore vs = plugin.getStore();
        return vs.get(vs.world(), dialogueScope, name);
    }

    public void setWorldVar(@Nonnull String dialogueScope, @Nonnull String name, @Nullable Object value) {
        VariableStore vs = plugin.getStore();
        vs.set(vs.world(), dialogueScope, name, value);
        vs.flush();
    }
}
