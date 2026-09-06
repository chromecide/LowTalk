package com.chromecide.lowtalk.hytale;

import com.chromecide.lowtalk.runtime.RuntimeError;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Functions callable from expressions (has(), objective(), ...). Plugins add their own here. */
public class FunctionRegistry {

    @FunctionalInterface
    public interface Function {
        Object call(@Nonnull HytaleContext ctx, @Nonnull List<Object> args);
    }

    private final Map<String, Function> functions = new ConcurrentHashMap<>();

    public void register(@Nonnull String name, @Nonnull Function f) {
        functions.put(name, f);
    }

    public boolean has(String name) {
        return functions.containsKey(name);
    }

    public Set<String> names() {
        return java.util.Collections.unmodifiableSet(functions.keySet()); // live: commands registered later still count as known
    }

    public Object call(HytaleContext ctx, String name, List<Object> args) {
        Function f = functions.get(name);
        if (f == null) {
            // A plugin that used to provide this is gone: read as false so conditions fall through instead of aborting.
            ctx.warn("unknown function " + name + "() in dialogue " + ctx.getDialogueId() + " (no plugin provides it); it reads as false");
            return Boolean.FALSE;
        }
        return f.call(ctx, args);
    }
}
