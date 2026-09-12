package com.chromecide.lowtalk.hytale;

import com.chromecide.lowtalk.LowTalkPlugin;
import com.chromecide.lowtalk.api.DialogueListener;
import com.chromecide.lowtalk.model.Dialogue;
import com.chromecide.lowtalk.runtime.Conversation;
import com.chromecide.lowtalk.runtime.Effect;
import com.chromecide.lowtalk.runtime.RuntimeError;
import com.chromecide.lowtalk.runtime.Step;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * One player in one dialogue with one NPC. Drives the {@link Conversation} and the {@link DialoguePage}.
 * Everything here runs on the world thread (page events arrive there already).
 */
public class DialogueSession implements EffectHost {

    public interface Host {
        LowTalkConfig config();
        /** How this dialogue is shown: layout and hidden HUD parts, resolved from directive, pack, API and config. */
        com.chromecide.lowtalk.hytale.presentation.Presentation presentation(Dialogue dialogue);
        EffectRegistry effects();
        VariableStore store();
        HytaleLogger logger();
        List<DialogueListener> listeners();
        void sessionEnded(DialogueSession session);
    }

    private final Host host;
    private final Dialogue dialogue;
    private final PlayerRef player;
    private final World world;
    private final UUID npcId;
    private final String npcName;
    private final HytaleContext context;
    private final Conversation conversation;
    private final DialoguePage page;
    private final com.chromecide.lowtalk.hytale.presentation.Presentation presentation;
    /** HUD parts we hid for this conversation, to show again when it ends; null when nothing was hidden. */
    private java.util.Set<com.hypixel.hytale.protocol.packets.interface_.HudComponent> hiddenHud;
    private boolean ended = false;
    private boolean opened = false;
    /** Another page (the shop) has replaced ours; the conversation waits for it to close. */
    private boolean suspended = false;
    /** The step to show when the conversation resumes; null means it was suspended before its first step. */
    private Step resumeStep;
    private Step firstStep;
    private String lastNode;
    /** Bumped on every step so a stale <<wait>> timer cannot advance a later step. */
    private int stepSerial = 0;

    private DialogueSession(Host host, Dialogue dialogue, PlayerRef player, World world, UUID npcId, String npcName, HytaleContext context) {
        this.host = host;
        this.dialogue = dialogue;
        this.player = player;
        this.world = world;
        this.npcId = npcId;
        this.npcName = npcName;
        this.context = context;
        this.conversation = new Conversation(dialogue, context);
        String title = dialogue.title() != null ? dialogue.title() : (dialogue.speaker() != null ? dialogue.speaker() : npcName);
        this.presentation = host.presentation(dialogue);
        this.page = new DialoguePage(player, this, title, dialogue.otherDirectives().get("portrait"), presentation.layout());
    }

    public com.chromecide.lowtalk.hytale.presentation.Presentation getPresentation() { return presentation; }

    /** Hide the configured HUD parts for the length of the conversation. World thread. */
    private void hideHud() {
        if (presentation.hideHud().isEmpty() || hiddenHud != null) return;
        try {
            Ref<EntityStore> ref = player.getReference();
            if (ref == null || !ref.isValid()) return;
            Player p = ref.getStore().getComponent(ref, Player.getComponentType());
            if (p == null) return;
            java.util.Set<com.hypixel.hytale.protocol.packets.interface_.HudComponent> toHide = new java.util.LinkedHashSet<>();
            for (String name : presentation.hideHud()) {
                com.hypixel.hytale.protocol.packets.interface_.HudComponent c = hudComponent(name);
                if (c == null) {
                    log("unknown HUD part '" + name + "' in HideHud; ignored");
                } else if (p.getHudManager().getVisibleHudComponents().contains(c)) {
                    toHide.add(c);
                }
            }
            if (toHide.isEmpty()) return;
            p.getHudManager().hideHudComponents(player, toHide.toArray(new com.hypixel.hytale.protocol.packets.interface_.HudComponent[0]));
            hiddenHud = toHide;
        } catch (RuntimeException e) {
            host.logger().at(java.util.logging.Level.FINE).log("Could not hide HUD parts: %s", e.toString());
        }
    }

    /** Show again what {@link #hideHud()} hid. Safe to call more than once and from any end path. */
    private void restoreHud() {
        java.util.Set<com.hypixel.hytale.protocol.packets.interface_.HudComponent> hidden = hiddenHud;
        if (hidden == null) return;
        hiddenHud = null;
        try {
            Ref<EntityStore> ref = player.getReference();
            if (ref == null || !ref.isValid()) return;
            Player p = ref.getStore().getComponent(ref, Player.getComponentType());
            if (p != null) p.getHudManager().showHudComponents(player, hidden);
        } catch (RuntimeException e) {
            host.logger().at(java.util.logging.Level.FINE).log("Could not restore HUD parts: %s", e.toString());
        }
    }

    @Nullable
    private static com.hypixel.hytale.protocol.packets.interface_.HudComponent hudComponent(String name) {
        if (name == null) return null;
        String n = name.trim();
        for (com.hypixel.hytale.protocol.packets.interface_.HudComponent c : com.hypixel.hytale.protocol.packets.interface_.HudComponent.values()) {
            if (c.name().equalsIgnoreCase(n)) return c;
        }
        return null;
    }

    /**
     * Build the session and its page without opening the window, for callers that open the page themselves
     * (the game's OpenCustomUI interaction). Returns null if the dialogue ended before showing anything.
     */
    @Nullable
    public static DialogueSession prepare(@Nonnull Host host, @Nonnull FunctionRegistry functions, @Nonnull Dialogue dialogue,
                                          @Nonnull PlayerRef player, @Nonnull Ref<EntityStore> playerEntity, @Nonnull Store<EntityStore> store,
                                          @Nonnull World world, @Nonnull UUID npcId, @Nonnull String npcName) {
        return prepare(host, functions, dialogue, player, playerEntity, store, world, npcId, npcName, null);
    }

    /** As above, starting in {@code startNode} when given (the editor's Test button). */
    @Nullable
    public static DialogueSession prepare(@Nonnull Host host, @Nonnull FunctionRegistry functions, @Nonnull Dialogue dialogue,
                                          @Nonnull PlayerRef player, @Nonnull Ref<EntityStore> playerEntity, @Nonnull Store<EntityStore> store,
                                          @Nonnull World world, @Nonnull UUID npcId, @Nonnull String npcName, @Nullable String startNode) {
        HytaleContext ctx = new HytaleContext(dialogue, player, npcId, npcName, host.store(), functions);
        DialogueSession s = new DialogueSession(host, dialogue, player, world, npcId, npcName, ctx);
        Conversation.Result first;
        try {
            first = startNode == null ? s.conversation.start() : s.conversation.startAt(startNode);
        } catch (RuntimeError e) {
            s.fail(e);
            return null;
        }
        s.applyEffects(first.effects());
        if (s.ended) return null;
        if (s.suspended) {
            if (first.step() instanceof Step.Finish) {
                s.detach();
                return null;
            }
            s.resumeStep = first.step();
            s.firstStep = first.step();
            return s; // the shop is up; the window opens when it closes
        }
        if (first.step() instanceof Step.Finish) {
            s.finish();
            return null;
        }
        s.page.show(first.step());
        s.firstStep = first.step();
        s.page.markOpened();
        return s;
    }

    /**
     * Start a dialogue and open the window. World thread only.
     * @return the session, or null if it ended immediately or could not start
     */
    @Nullable
    public static DialogueSession open(@Nonnull Host host, @Nonnull FunctionRegistry functions, @Nonnull Dialogue dialogue,
                                       @Nonnull PlayerRef player, @Nonnull Ref<EntityStore> playerEntity, @Nonnull Store<EntityStore> store,
                                       @Nonnull World world, @Nonnull UUID npcId, @Nonnull String npcName) {
        return open(host, functions, dialogue, player, playerEntity, store, world, npcId, npcName, null);
    }

    /** As above, starting in {@code startNode} when given. */
    @Nullable
    public static DialogueSession open(@Nonnull Host host, @Nonnull FunctionRegistry functions, @Nonnull Dialogue dialogue,
                                       @Nonnull PlayerRef player, @Nonnull Ref<EntityStore> playerEntity, @Nonnull Store<EntityStore> store,
                                       @Nonnull World world, @Nonnull UUID npcId, @Nonnull String npcName, @Nullable String startNode) {
        DialogueSession s = prepare(host, functions, dialogue, player, playerEntity, store, world, npcId, npcName, startNode);
        if (s == null) return null;
        if (!s.suspended) {
            Player playerComponent = store.getComponent(playerEntity, Player.getComponentType());
            if (playerComponent == null) {
                s.finish();
                return null;
            }
            playerComponent.getPageManager().openCustomPage(playerEntity, store, s.page);
        }
        s.afterOpen();
        return s;
    }

    /** Logging, the NPC hold, listeners and the first pause timer. Call once the page is on its way to the client. */
    public void afterOpen() {
        hideHud();
        if (opened || ended) return;
        opened = true;
        if (host.config().isLogConversations()) {
            host.logger().at(Level.INFO).log("%s opened '%s' with %s", player.getUsername(), dialogue.id(), npcName);
        }
        if (host.config().isHoldNpcDuringDialogue() && npcId.getMostSignificantBits() != 0L) {
            NpcHold.hold(world, npcId, player.getUuid(), host.store());
        }
        notify(l -> l.onStart(context));
        lastNode = conversation.getCurrentNode();
        if (lastNode != null) nodeReached(lastNode);
        if (firstStep instanceof Step.Wait wait && !suspended) {
            int serial = ++stepSerial;
            long millis = Math.round(Math.min(30.0, wait.seconds()) * 1000.0);
            world.scheduleAfter(() -> {
                if (ended || serial != stepSerial) return;
                advance(conversation::next);
            }, millis, java.util.concurrent.TimeUnit.MILLISECONDS);
        }
    }

    public DialoguePage getPage() { return page; }

    @Override public PlayerRef getPlayer() { return player; }
    public Dialogue getDialogue() { return dialogue; }
    @Override public UUID getNpcId() { return npcId; }
    public String getNpcName() { return npcName; }
    @Override public World getWorld() { return world; }
    @Override public HytaleContext getContext() { return context; }
    public Conversation getConversation() { return conversation; }
    public boolean isEnded() { return ended; }

    /** Diagnostic line in the server log, prefixed with the player and dialogue. */
    void log(String message) {
        if (host.config().isLogConversations()) {
            host.logger().at(Level.INFO).log("[%s/%s] %s", player.getUsername(), dialogue.id(), message);
        }
    }

    // ---- page callbacks

    void onContinue() {
        advance(conversation::next);
    }

    void onChoose(int index) {
        advance(() -> conversation.choose(index));
    }

    /** Called by the page with the button text, for listeners and the log. */
    void choiceMade(String text) {
        log("chose \"" + text + "\"");
        page.playerSaid(text);
        notify(l -> l.onChoice(context, text));
    }

    private void notify(java.util.function.Consumer<DialogueListener> call) {
        for (DialogueListener l : host.listeners()) {
            try {
                call.accept(l);
            } catch (RuntimeException e) {
                host.logger().at(Level.WARNING).log("A dialogue listener threw: %s", e.toString());
            }
        }
    }

    private void nodeChanged() {
        String now = conversation.getCurrentNode();
        if (now != null && !now.equals(lastNode)) {
            lastNode = now;
            nodeReached(now);
        }
    }

    /** Listeners hear about the node, and any LowTalkNode objective tasks waiting on it advance. */
    private void nodeReached(String node) {
        notify(l -> l.onNode(context, node));
        try {
            int advanced = com.chromecide.lowtalk.hytale.objectives.ObjectiveNodes.nodeReached(LowTalkPlugin.get(), player, dialogue.id(), node);
            if (advanced > 0) log("advanced " + advanced + " objective task(s) at passage " + node);
        } catch (RuntimeException e) {
            host.logger().at(Level.WARNING).log("Objective task check failed at %s/%s: %s", dialogue.id(), node, e.toString());
        }
    }

    void onAnswer(String text) {
        advance(() -> conversation.answer(text));
    }

    void onLeave() {
        log("leave pressed");
        if (ended) {
            page.closeNow(); // conversation already over but the window is still up
            return;
        }
        finish();
    }

    void onDismissed() {
        if (suspended) {
            log("page replaced by another page; waiting for it to close");
            return;
        }
        // Escape: the window is gone, so the conversation is over.
        if (!ended) {
            log("ended by dismiss");
            ended = true;
            restoreHud();
            releaseNpc();
            host.store().flush();
            host.sessionEnded(this);
            notify(l -> l.onEnd(context));
        }
    }

    /** Close from the server side (reload, NPC gone, player disconnect, or an effect such as teleport). */
    @Override
    public void end() {
        finish();
    }

    /**
     * An effect is about to open another page (the shop) in place of ours. The conversation keeps its NPC hold, its
     * listeners and its place, and {@link #resumeFromPage()} brings the window back when that page closes. If the
     * conversation has nothing after the command, {@link #advance} ends it instead, as before.
     */
    @Override
    public void suspendForPage() {
        if (ended) return;
        suspended = true;
        log("suspended for another page");
    }

    /** The page that replaced ours closed (Back or Escape). Reopen the window on the next tick with the waiting step. */
    public void resumeFromPage() {
        if (ended || !suspended) return;
        world.execute(() -> {
            if (ended || !suspended) return;
            suspended = false;
            Step step = resumeStep;
            resumeStep = null;
            if (step == null || step instanceof Step.Finish) {
                detach();
                return;
            }
            try {
                Ref<EntityStore> ref = player.getReference();
                if (ref == null || !ref.isValid()) {
                    detach();
                    return;
                }
                Store<EntityStore> store = ref.getStore();
                Player playerComponent = store.getComponent(ref, Player.getComponentType());
                if (playerComponent == null) {
                    detach();
                    return;
                }
                page.show(step);
                page.markOpened();
                playerComponent.getPageManager().openCustomPage(ref, store, page);
                log("resumed after the other page closed");
                if (step instanceof Step.Wait wait) {
                    int serial = ++stepSerial;
                    long millis = Math.round(Math.min(30.0, wait.seconds()) * 1000.0);
                    world.scheduleAfter(() -> {
                        if (ended || serial != stepSerial) return;
                        advance(conversation::next);
                    }, millis, java.util.concurrent.TimeUnit.MILLISECONDS);
                }
            } catch (RuntimeException e) {
                host.logger().at(Level.WARNING).log("Could not resume the conversation after the shop: %s", e.toString());
                detach();
            }
        });
    }

    /**
     * End the conversation without touching the window, for effects that open another page
     * (the barter shop) in its place. Effects after this one in the same step are still applied.
     */
    @Override
    public void detach() {
        if (ended) return;
        ended = true;
        restoreHud(); // the page that replaces ours (the shop) is not a dialogue; give the HUD back now
        releaseNpc();
        host.store().flush();
        host.sessionEnded(this);
        notify(l -> l.onEnd(context));
    }

    // ---- internals

    private void advance(Supplier<Conversation.Result> step) {
        if (ended) return;
        Conversation.Result r;
        try {
            r = step.get();
        } catch (RuntimeError e) {
            fail(e);
            return;
        }
        nodeChanged();
        applyEffects(r.effects());
        if (ended) return; // an effect took over the screen for good
        if (suspended) {
            // The shop is up. Nothing after the command: end quietly, the shop stays. Otherwise wait for Back.
            if (r.step() instanceof Step.Finish) {
                detach();
            } else {
                resumeStep = r.step();
                log("waiting with step " + r.step().getClass().getSimpleName());
            }
            return;
        }
        if (r.step() instanceof Step.Finish) {
            finish();
            return;
        }
        page.show(r.step());
        log("step -> " + r.step().getClass().getSimpleName());
        page.refresh();
        int serial = ++stepSerial;
        if (r.step() instanceof Step.Wait wait) {
            long millis = Math.round(Math.min(30.0, wait.seconds()) * 1000.0);
            world.scheduleAfter(() -> {
                if (ended || serial != stepSerial) return;
                advance(conversation::next);
            }, millis, java.util.concurrent.TimeUnit.MILLISECONDS);
        }
    }

    private void applyEffects(List<Effect> effects) {
        for (Effect e : effects) {
            EffectRegistry.Handler h = host.effects().get(e.name());
            if (h == null) {
                host.logger().at(Level.WARNING).log("%s: no handler for <<%s>> (not implemented yet, or no plugin provides it)", e.pos(), e.name());
                continue;
            }
            try {
                String narration = h.apply(this, e);
                if (narration != null && !narration.isBlank()) {
                    page.showNarration(narration);
                }
            } catch (RuntimeException ex) {
                host.logger().at(Level.WARNING).log("%s: <<%s %s>> failed: %s", e.pos(), e.name(), String.join(" ", e.args()), ex.toString());
            }
        }
    }

    private void fail(RuntimeError e) {
        host.logger().at(Level.WARNING).log("Dialogue '%s' failed for %s: %s", dialogue.id(), player.getUsername(), e.getMessage());
        player.sendMessage(Message.raw("This conversation has a problem; the server log has details.").color(host.config().getInfoColor()));
        finish();
    }

    private void finish() {
        if (ended) return;
        log("finished");
        ended = true;
        page.closeNow();
        restoreHud();
        releaseNpc();
        host.store().flush();
        host.sessionEnded(this);
        notify(l -> l.onEnd(context));
    }

    private void releaseNpc() {
        if (npcId.getMostSignificantBits() != 0L) {
            NpcHold.release(world, npcId, host.store());
        }
    }
}
