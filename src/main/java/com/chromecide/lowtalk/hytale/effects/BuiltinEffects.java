package com.chromecide.lowtalk.hytale.effects;

import com.chromecide.lowtalk.LowTalkPlugin;
import com.chromecide.lowtalk.hytale.EffectRegistry;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.console.ConsoleSender;

import javax.annotation.Nonnull;
import java.util.logging.Level;

/** The <<commands>> LowTalk ships with. Milestone 2 has run; give, take, shop, attitude, objective, anim, sound follow in milestone 3. */
public final class BuiltinEffects {

    private BuiltinEffects() {}

    public static void register(@Nonnull EffectRegistry effects, @Nonnull LowTalkPlugin plugin) {
        effects.register("run", (session, effect) -> {
            String command = effect.args().get(0).trim();
            if (command.startsWith("/")) command = command.substring(1);
            if (command.isEmpty()) return null;
            plugin.getLogger().at(Level.INFO).log("<<run>> for %s: /%s", session.getPlayer().getUsername(), command);
            CommandManager.get().handleCommand(ConsoleSender.INSTANCE, command);
            return null;
        });
    }
}
