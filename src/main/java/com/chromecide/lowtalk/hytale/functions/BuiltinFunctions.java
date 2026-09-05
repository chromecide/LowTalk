package com.chromecide.lowtalk.hytale.functions;

import com.chromecide.lowtalk.hytale.FunctionRegistry;
import com.chromecide.lowtalk.runtime.RuntimeError;
import com.chromecide.lowtalk.runtime.Values;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Expression functions LowTalk ships with. Milestone 2 has the pure ones; item, objective, and attitude checks follow in milestone 3. */
public final class BuiltinFunctions {

    private BuiltinFunctions() {}

    public static void register(@Nonnull FunctionRegistry functions) {
        functions.register("visited", (ctx, args) -> ctx.hasVisited(string(args, 0, "visited")));
        functions.register("random", (ctx, args) -> {
            int n = (int) Values.number(arg(args, 0, "random"));
            if (n <= 0) throw new RuntimeError("random(n) needs n > 0");
            return (double) ThreadLocalRandom.current().nextInt(n);
        });
        functions.register("chance", (ctx, args) -> ThreadLocalRandom.current().nextDouble() < Values.number(arg(args, 0, "chance")));
        functions.register("perm", (ctx, args) -> ctx.getPlayer().hasPermission(string(args, 0, "perm")));
    }

    static Object arg(List<Object> args, int i, String fn) {
        if (i >= args.size()) throw new RuntimeError(fn + "() is missing argument " + (i + 1));
        return args.get(i);
    }

    static String string(List<Object> args, int i, String fn) {
        return Values.text(arg(args, i, fn));
    }
}
