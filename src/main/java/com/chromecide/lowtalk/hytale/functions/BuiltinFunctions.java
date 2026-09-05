package com.chromecide.lowtalk.hytale.functions;

import com.chromecide.lowtalk.hytale.FunctionRegistry;
import com.chromecide.lowtalk.hytale.HytaleContext;
import com.chromecide.lowtalk.runtime.RuntimeError;
import com.chromecide.lowtalk.runtime.TextFunctions;
import com.chromecide.lowtalk.runtime.Values;
import com.hypixel.hytale.builtin.adventure.objectives.Objective;
import com.hypixel.hytale.builtin.adventure.objectives.components.ObjectiveHistoryComponent;
import com.hypixel.hytale.builtin.adventure.objectives.ObjectivePlugin;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.attitude.Attitude;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.role.support.WorldSupport;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Expression functions LowTalk ships with. All run on the world thread. */
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
        functions.register("ordinal", (ctx, args) -> TextFunctions.ordinal(Values.number(arg(args, 0, "ordinal"))));
        functions.register("plural", (ctx, args) -> TextFunctions.plural(Values.number(arg(args, 0, "plural")),
                string(args, 1, "plural"), args.size() > 2 ? Values.text(args.get(2)) : null));

        functions.register("count", (ctx, args) -> (double) countItems(ctx, string(args, 0, "count")));
        functions.register("has", (ctx, args) -> {
            int needed = args.size() > 1 ? (int) Values.number(args.get(1)) : 1;
            return countItems(ctx, string(args, 0, "has")) >= needed;
        });
        functions.register("hour", (ctx, args) -> {
            Store<EntityStore> store = store(ctx);
            WorldTimeResource time = store.getResource(WorldTimeResource.getResourceType());
            return time == null ? 12.0 : (double) time.getGameDateTime().getHour();
        });
        functions.register("objective", (ctx, args) -> objectiveState(ctx, string(args, 0, "objective")));
        functions.register("attitude", (ctx, args) -> currentAttitude(ctx));
    }

    // ---- helpers shared with the effects

    public static Ref<EntityStore> playerEntity(HytaleContext ctx) {
        Ref<EntityStore> ref = ctx.getPlayer().getReference();
        if (ref == null || !ref.isValid()) throw new RuntimeError("player is not in the world");
        return ref;
    }

    public static Store<EntityStore> store(HytaleContext ctx) {
        return playerEntity(ctx).getStore();
    }

    public static int countItems(HytaleContext ctx, String itemId) {
        Ref<EntityStore> ref = playerEntity(ctx);
        Store<EntityStore> store = ref.getStore();
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return 0;
        ItemContainer container = player.getInventory().getCombinedHotbarFirst();
        int[] total = {0};
        container.forEach((slot, stack) -> {
            if (stack != null && itemId.equals(stack.getItemId())) {
                total[0] += stack.getQuantity();
            }
        });
        return total[0];
    }

    public static String objectiveState(HytaleContext ctx, String objectiveId) {
        Ref<EntityStore> ref = playerEntity(ctx);
        Store<EntityStore> store = ref.getStore();
        ObjectivePlugin objectives = ObjectivePlugin.get();
        if (objectives == null) return "none";
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null && objectives.getObjectiveDataStore() != null) {
            Set<UUID> active = player.getPlayerConfigData().getActiveObjectiveUUIDs();
            if (active != null) {
                for (UUID id : active) {
                    Objective o = objectives.getObjectiveDataStore().getObjective(id);
                    if (o != null && objectiveId.equals(o.getObjectiveId())) {
                        return o.isCompleted() ? "complete" : "active";
                    }
                }
            }
        }
        ObjectiveHistoryComponent history = store.getComponent(ref, objectives.getObjectiveHistoryComponentType());
        if (history != null && history.getObjectiveHistoryMap() != null && history.getObjectiveHistoryMap().containsKey(objectiveId)) {
            return "complete";
        }
        return "none";
    }

    public static String currentAttitude(HytaleContext ctx) {
        Ref<EntityStore> playerRef = playerEntity(ctx);
        Store<EntityStore> store = playerRef.getStore();
        Ref<EntityStore> npcRef = store.getExternalData().getRefFromUUID(ctx.getNpcId());
        if (npcRef == null || !npcRef.isValid()) return "unknown";
        try {
            WorldSupport support = WorldSupport.get(npcRef, store);
            Attitude a = support.getAttitude(npcRef, playerRef, store);
            return a == null ? "unknown" : a.name().toLowerCase(Locale.ROOT);
        } catch (RuntimeException e) {
            return "unknown";
        }
    }

    static Object arg(List<Object> args, int i, String fn) {
        if (i >= args.size()) throw new RuntimeError(fn + "() is missing argument " + (i + 1));
        return args.get(i);
    }

    static String string(List<Object> args, int i, String fn) {
        return Values.text(arg(args, i, fn));
    }
}
