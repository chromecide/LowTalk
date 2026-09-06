package com.chromecide.lowtalk.hytale.integrations;

import com.chromecide.lowtalk.LowTalkPlugin;
import com.chromecide.lowtalk.hytale.HytaleContext;
import com.chromecide.lowtalk.hytale.NpcInfo;
import com.chromecide.lowtalk.model.Dialogue;
import com.chromecide.lowtalk.model.Expr;
import com.chromecide.lowtalk.model.Pos;
import com.chromecide.lowtalk.parser.ExprParser;
import com.chromecide.lowtalk.parser.ParseException;
import com.chromecide.lowtalk.runtime.Evaluator;
import com.chromecide.lowtalk.runtime.RuntimeError;
import com.chromecide.lowtalk.runtime.Values;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Evaluates LowTalk expressions for a player outside a conversation, in the variable scope of a named dialogue.
 * Used by the trigger-volume condition and effect and by the choice requirement. There is no NPC, so use
 * {@code $player.} and {@code $world.} variables; bare {@code $x} would refer to a conversation with nobody.
 */
public final class DialogueExpressions {
    private DialogueExpressions() {}

    /** True if the expression holds for the player. Unknown dialogue, bad expression or runtime error all read false. */
    public static boolean test(@Nonnull LowTalkPlugin plugin, @Nullable String dialogueId, @Nullable String expression, @Nonnull PlayerRef player) {
        return test(plugin, dialogueId, expression, player, NpcInfo.NONE, null);
    }

    /** As above, but in the memory of a specific NPC, so bare {@code $x} variables refer to this player with that NPC. */
    public static boolean test(@Nonnull LowTalkPlugin plugin, @Nullable String dialogueId, @Nullable String expression, @Nonnull PlayerRef player,
                               @Nonnull java.util.UUID npcId, @Nullable String npcName) {
        if (expression == null || expression.isBlank()) return true;
        HytaleContext ctx = context(plugin, dialogueId, player, "condition", npcId, npcName);
        if (ctx == null) return false;
        try {
            Expr e = ExprParser.parse(expression, new Pos(dialogueId + " condition", 0));
            return Values.truthy(Evaluator.eval(e, ctx));
        } catch (ParseException | RuntimeError e) {
            warn(plugin, "condition '" + expression + "' failed: " + e.getMessage());
            return false;
        }
    }

    /** Store a value in a variable for the player, e.g. Var "$player.errand", Value "true". */
    public static boolean assign(@Nonnull LowTalkPlugin plugin, @Nullable String dialogueId, @Nullable String var, @Nullable String value, @Nonnull PlayerRef player) {
        if (var == null || var.isBlank()) return false;
        HytaleContext ctx = context(plugin, dialogueId, player, "set", NpcInfo.NONE, null);
        if (ctx == null) return false;
        try {
            Pos pos = new Pos(dialogueId + " set", 0);
            Expr.Var target = ExprParser.parseVar(var, pos);
            Object v = value == null || value.isBlank() ? Boolean.TRUE : Evaluator.eval(ExprParser.parse(value, pos), ctx);
            ctx.setVar(target.scope(), target.name(), v);
            plugin.getStore().flush();
            return true;
        } catch (ParseException | RuntimeError e) {
            warn(plugin, "set " + var + " = " + value + " failed: " + e.getMessage());
            return false;
        }
    }

    @Nullable
    private static HytaleContext context(LowTalkPlugin plugin, @Nullable String dialogueId, PlayerRef player, String what,
                                         java.util.UUID npcId, @Nullable String npcName) {
        if (dialogueId == null || dialogueId.isBlank()) {
            warn(plugin, "a LowTalk " + what + " has no Dialogue; it names the dialogue whose variables to use");
            return null;
        }
        Dialogue d = plugin.getRegistry().byId(dialogueId);
        if (d == null) {
            warn(plugin, "a LowTalk " + what + " refers to dialogue '" + dialogueId + "', which is not loaded");
            return null;
        }
        String name = npcName != null ? npcName : (d.speaker() != null ? d.speaker() : "Narrator");
        return new HytaleContext(d, player, npcId, name, plugin.getStore(), plugin.getFunctions());
    }

    private static void warn(LowTalkPlugin plugin, String message) {
        plugin.getLogger().at(Level.WARNING).atMostEvery(30, TimeUnit.SECONDS).log("%s", message);
    }
}
