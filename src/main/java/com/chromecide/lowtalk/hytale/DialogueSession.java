package com.chromecide.lowtalk.hytale;

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
public class DialogueSession {

    public interface Host {
        LowTalkConfig config();
        EffectRegistry effects();
        VariableStore store();
        HytaleLogger logger();
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
    private boolean ended = false;

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
        this.page = new DialoguePage(player, this, title);
    }

    /**
     * Start a dialogue and open the window. World thread only.
     * @return the session, or null if it ended immediately or could not start
     */
    @Nullable
    public static DialogueSession open(@Nonnull Host host, @Nonnull FunctionRegistry functions, @Nonnull Dialogue dialogue,
                                       @Nonnull PlayerRef player, @Nonnull Ref<EntityStore> playerEntity, @Nonnull Store<EntityStore> store,
                                       @Nonnull World world, @Nonnull UUID npcId, @Nonnull String npcName) {
        HytaleContext ctx = new HytaleContext(dialogue, player, npcId, npcName, host.store(), functions);
        DialogueSession s = new DialogueSession(host, dialogue, player, world, npcId, npcName, ctx);
        Conversation.Result first;
        try {
            first = s.conversation.start();
        } catch (RuntimeError e) {
            s.fail(e);
            return null;
        }
        s.applyEffects(first.effects());
        if (s.ended) return null;
        if (first.step() instanceof Step.Finish) {
            s.finish();
            return null;
        }
        s.page.show(first.step());
        Player playerComponent = store.getComponent(playerEntity, Player.getComponentType());
        if (playerComponent == null) {
            s.finish();
            return null;
        }
        s.page.markOpened();
        playerComponent.getPageManager().openCustomPage(playerEntity, store, s.page);
        if (host.config().isLogConversations()) {
            host.logger().at(Level.INFO).log("%s opened '%s' with %s", player.getUsername(), dialogue.id(), npcName);
        }
        return s;
    }

    public PlayerRef getPlayer() { return player; }
    public Dialogue getDialogue() { return dialogue; }
    public UUID getNpcId() { return npcId; }
    public String getNpcName() { return npcName; }
    public World getWorld() { return world; }
    public HytaleContext getContext() { return context; }
    public Conversation getConversation() { return conversation; }
    public boolean isEnded() { return ended; }

    /** Diagnostic line in the server log, prefixed with the player and dialogue. */
    void log(String message) {
        host.logger().at(Level.INFO).log("[%s/%s] %s", player.getUsername(), dialogue.id(), message);
    }

    // ---- page callbacks

    void onContinue() {
        advance(conversation::next);
    }

    void onChoose(int index) {
        if (host.config().isLogConversations()) {
            host.logger().at(Level.FINE).log("%s chose option %d in '%s'", player.getUsername(), index, dialogue.id());
        }
        advance(() -> conversation.choose(index));
    }

    void onAnswer(String text) {
        advance(() -> conversation.answer(text));
    }

    void onLeave() {
        log("leave pressed");
        finish();
    }

    void onDismissed() {
        // Escape: the window is gone, so the conversation is over.
        if (!ended) {
            log("ended by dismiss");
            ended = true;
            host.store().flush();
            host.sessionEnded(this);
        }
    }

    /** Close from the server side (reload, NPC gone, player disconnect). */
    public void end() {
        finish();
    }

    /**
     * End the conversation without touching the window, for effects that open another page
     * (the barter shop) in its place. Effects after this one in the same step are still applied.
     */
    public void detach() {
        if (ended) return;
        ended = true;
        host.store().flush();
        host.sessionEnded(this);
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
        applyEffects(r.effects());
        if (ended) return; // an effect (e.g. shop) took over the screen
        if (r.step() instanceof Step.Finish) {
            finish();
            return;
        }
        page.show(r.step());
        log("step -> " + r.step().getClass().getSimpleName());
        page.refresh();
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
        host.store().flush();
        host.sessionEnded(this);
    }
}
