package com.chromecide.lowtalk.hytale.functions;

import com.chromecide.lowtalk.hytale.FunctionRegistry;
import com.chromecide.lowtalk.hytale.HytaleContext;
import com.chromecide.lowtalk.runtime.RuntimeError;
import com.chromecide.lowtalk.runtime.TextFunctions;
import com.chromecide.lowtalk.runtime.Values;
import com.hypixel.hytale.builtin.adventure.objectives.Objective;
import com.hypixel.hytale.builtin.adventure.objectives.components.ObjectiveHistoryComponent;
import com.hypixel.hytale.builtin.adventure.objectives.ObjectivePlugin;
import com.hypixel.hytale.builtin.adventure.reputation.ReputationPlugin;
import com.hypixel.hytale.builtin.adventure.reputation.assets.ReputationRank;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
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

        functions.register("reputation", (ctx, args) -> (double) reputation(ctx, args.isEmpty() ? null : Values.text(args.get(0))));
        functions.register("rank", (ctx, args) -> rank(ctx, args.isEmpty() ? null : Values.text(args.get(0))));
        functions.register("stat", (ctx, args) -> {
            EntityStatValue v = stat(ctx, string(args, 0, "stat"));
            return v == null ? 0.0 : (double) v.get();
        });
        functions.register("max_stat", (ctx, args) -> {
            EntityStatValue v = stat(ctx, string(args, 0, "max_stat"));
            return v == null ? 0.0 : (double) v.getMax();
        });
        functions.register("effect", (ctx, args) -> {
            String id = string(args, 0, "effect");
            EntityEffect effect = EntityEffect.getAssetMap().getAsset(id);
            if (effect == null) return false;
            Ref<EntityStore> ref = playerEntity(ctx);
            EffectControllerComponent effects = ref.getStore().getComponent(ref, EffectControllerComponent.getComponentType());
            return effects != null && effects.hasEffect(effect);
        });
        functions.register("knows", (ctx, args) -> {
            Ref<EntityStore> ref = playerEntity(ctx);
            Player player = ref.getStore().getComponent(ref, Player.getComponentType());
            Set<String> known = player == null ? null : player.getPlayerConfigData().getKnownRecipes();
            return known != null && known.contains(string(args, 0, "knows"));
        });
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

    /**
     * Reputation with this NPC's group, or with a named group. 0 when the plugin is missing, the group does not
     * exist, or the NPC belongs to no reputation group (the plugin signals all of those with Integer.MIN_VALUE).
     */
    public static int reputation(HytaleContext ctx, String group) {
        ReputationPlugin rep = ReputationPlugin.get();
        if (rep == null) return 0;
        Ref<EntityStore> playerRef = playerEntity(ctx);
        Store<EntityStore> store = playerRef.getStore();
        try {
            int value;
            if (group != null && !group.isBlank()) {
                value = rep.getReputationValue(store, playerRef, group);
            } else {
                Ref<EntityStore> npcRef = store.getExternalData().getRefFromUUID(ctx.getNpcId());
                if (npcRef == null || !npcRef.isValid()) return 0;
                value = rep.getReputationValue(store, playerRef, npcRef);
            }
            return value == Integer.MIN_VALUE ? 0 : value;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** The reputation rank id ("Friendly", "Hostile", ...) with this NPC's group or a named group; "" if none. */
    public static String rank(HytaleContext ctx, String group) {
        ReputationPlugin rep = ReputationPlugin.get();
        if (rep == null) return "";
        Ref<EntityStore> playerRef = playerEntity(ctx);
        Store<EntityStore> store = playerRef.getStore();
        try {
            ReputationRank r;
            if (group != null && !group.isBlank()) {
                r = rep.getReputationRank(store, playerRef, group);
            } else {
                Ref<EntityStore> npcRef = store.getExternalData().getRefFromUUID(ctx.getNpcId());
                if (npcRef == null || !npcRef.isValid()) return "";
                r = rep.getReputationRank(store, playerRef, npcRef);
            }
            return r == null ? "" : r.getId();
        } catch (RuntimeException e) {
            return "";
        }
    }

    public static EntityStatValue stat(HytaleContext ctx, String name) {
        Ref<EntityStore> ref = playerEntity(ctx);
        Store<EntityStore> store = ref.getStore();
        EntityStatMap stats = store.getComponent(ref, EntityStatMap.getComponentType());
        if (stats == null) return null;
        int index = EntityStatType.getAssetMap().getIndex(name);
        if (index < 0) throw new RuntimeError("unknown stat " + name);
        return stats.get(index);
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
