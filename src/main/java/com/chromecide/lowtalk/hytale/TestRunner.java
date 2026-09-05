package com.chromecide.lowtalk.hytale;

import com.chromecide.lowtalk.LowTalkPlugin;

import com.chromecide.lowtalk.model.Dialogue;
import com.chromecide.lowtalk.runtime.Conversation;
import com.chromecide.lowtalk.runtime.Effect;
import com.chromecide.lowtalk.runtime.RuntimeError;
import com.chromecide.lowtalk.runtime.Step;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Plays a dialogue without a window, driven by a script of choices, through the real functions
 * and (optionally) the real effects. Used by /lowtalk test so a change can be verified from the
 * console instead of by clicking through the game.
 *
 * Script tokens are consumed in order: at a choice, a token is a 1-based number or a prefix of an
 * option's text; at a text prompt, the token is the answer; lines continue automatically.
 */
public final class TestRunner implements EffectHost {

    private static final int MAX_STEPS = 200;

    private final LowTalkPlugin plugin;
    private final HytaleContext context;
    private final World world;
    private final UUID npcId;
    private final boolean applyEffects;
    private final Consumer<String> out;
    private boolean detached = false;

    public TestRunner(@Nonnull LowTalkPlugin plugin, @Nonnull Dialogue dialogue, @Nonnull PlayerRef player, @Nonnull World world,
                      @Nonnull UUID npcId, @Nonnull String npcName, boolean applyEffects, @Nonnull Consumer<String> out) {
        this.plugin = plugin;
        this.context = new HytaleContext(dialogue, player, npcId, npcName, plugin.getStore(), plugin.getFunctions());
        this.world = world;
        this.npcId = npcId;
        this.applyEffects = applyEffects;
        this.out = out;
    }

    /** World thread only. Returns true if the dialogue reached its end. */
    public boolean run(@Nonnull List<String> script) {
        Conversation conv = new Conversation(context.getDialogue(), context);
        List<String> tokens = new ArrayList<>(script);
        out.accept("--- " + context.getDialogue().id() + (applyEffects ? " (effects applied)" : " (effects listed only)") + " ---");
        Conversation.Result r;
        try {
            r = conv.start();
            for (int step = 0; step < MAX_STEPS; step++) {
                report(r.effects());
                if (detached) {
                    out.accept("(an effect took over the screen; conversation over)");
                    return true;
                }
                switch (r.step()) {
                    case Step.Say say -> {
                        out.accept(say.speaker() + ": " + say.text() + (say.last() ? "  [end]" : ""));
                        r = conv.next();
                    }
                    case Step.Choose choose -> {
                        if (choose.line() != null) {
                            out.accept(choose.line().speaker() + ": " + choose.line().text());
                        }
                        List<Step.Shown> options = choose.options();
                        for (int i = 0; i < options.size(); i++) {
                            Step.Shown o = options.get(i);
                            out.accept("  " + (i + 1) + ". " + o.text() + (o.enabled() ? "" : "  (disabled)"));
                        }
                        if (tokens.isEmpty()) {
                            out.accept("(waiting for a choice; script ended here)");
                            return false;
                        }
                        String token = tokens.remove(0);
                        Step.Shown pick = match(token, options);
                        if (pick == null) {
                            out.accept("error: no option matches '" + token + "'");
                            return false;
                        }
                        out.accept("> " + pick.text());
                        r = conv.choose(pick.index());
                    }
                    case Step.Ask ask -> {
                        String answer = tokens.isEmpty() ? "" : tokens.remove(0);
                        out.accept(ask.prompt() + "  > " + answer);
                        r = conv.answer(answer);
                    }
                    case Step.Finish f -> {
                        out.accept("(finished at node " + conv.getCurrentNode() + ")");
                        return true;
                    }
                }
            }
            out.accept("error: stopped after " + MAX_STEPS + " steps (loop?)");
            return false;
        } catch (RuntimeError e) {
            out.accept("error: " + e.getMessage());
            return false;
        }
    }

    private static Step.Shown match(String token, List<Step.Shown> options) {
        try {
            int n = Integer.parseInt(token.trim());
            if (n >= 1 && n <= options.size() && options.get(n - 1).enabled()) return options.get(n - 1);
        } catch (NumberFormatException ignored) {
            // text match below
        }
        String lower = token.trim().toLowerCase(Locale.ROOT);
        for (Step.Shown o : options) {
            if (o.enabled() && o.text().toLowerCase(Locale.ROOT).startsWith(lower)) return o;
        }
        return null;
    }

    private void report(List<Effect> effects) {
        for (Effect e : effects) {
            String line = "<<" + e.name() + (e.args().isEmpty() ? "" : " " + String.join(" ", e.args())) + ">>";
            if (!applyEffects) {
                out.accept("  effect: " + line + (plugin.getEffects().has(e.name()) ? "" : "  (no handler!)"));
                continue;
            }
            EffectRegistry.Handler h = plugin.getEffects().get(e.name());
            if (h == null) {
                out.accept("  effect: " + line + "  (no handler!)");
                continue;
            }
            try {
                String narration = h.apply(this, e);
                out.accept("  effect: " + line + (narration == null ? "" : "  -> " + narration));
            } catch (RuntimeException ex) {
                out.accept("  effect: " + line + "  FAILED: " + ex.getMessage());
            }
        }
    }

    @Override public HytaleContext getContext() { return context; }
    @Override public PlayerRef getPlayer() { return context.getPlayer(); }
    @Override public UUID getNpcId() { return npcId; }
    @Override public World getWorld() { return world; }
    @Override public void detach() { detached = true; }
}
