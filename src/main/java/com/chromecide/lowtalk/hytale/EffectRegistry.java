package com.chromecide.lowtalk.hytale;

import com.chromecide.lowtalk.runtime.Effect;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Handlers for <<commands>> (give, shop, run, ...). Plugins add their own here. Always run on the world thread. */
public class EffectRegistry {

    @FunctionalInterface
    public interface Handler {
        /** @return a short narration line to show the player, or null for nothing. */
        String apply(@Nonnull DialogueSession session, @Nonnull Effect effect);
    }

    private final Map<String, Handler> handlers = new ConcurrentHashMap<>();

    public void register(@Nonnull String name, @Nonnull Handler h) {
        handlers.put(name, h);
    }

    public boolean has(String name) {
        return handlers.containsKey(name);
    }

    public Set<String> names() {
        return Set.copyOf(handlers.keySet());
    }

    public Handler get(String name) {
        return handlers.get(name);
    }
}
