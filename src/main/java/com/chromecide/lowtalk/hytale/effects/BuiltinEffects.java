package com.chromecide.lowtalk.hytale.effects;

import com.chromecide.lowtalk.LowTalkPlugin;
import com.chromecide.lowtalk.hytale.DialogueSession;
import com.chromecide.lowtalk.hytale.EffectRegistry;
import com.chromecide.lowtalk.hytale.HytaleContext;
import com.chromecide.lowtalk.hytale.functions.BuiltinFunctions;
import com.chromecide.lowtalk.runtime.Effect;
import com.chromecide.lowtalk.runtime.RuntimeError;
import com.hypixel.hytale.builtin.adventure.objectives.ObjectivePlugin;
import com.hypixel.hytale.builtin.adventure.shop.barter.BarterPage;
import com.hypixel.hytale.builtin.adventure.shop.barter.BarterShopAsset;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.asset.type.attitude.Attitude;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.console.ConsoleSender;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.support.WorldSupport;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;

/** The <<commands>> LowTalk ships with. Handlers run on the world thread. */
public final class BuiltinEffects {

    /** Attitude overrides last this many seconds; effectively permanent for the NPC's lifetime. */
    private static final double ATTITUDE_DURATION_SECONDS = 1.0e9;

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

        effects.register("give", (session, effect) -> {
            String itemId = effect.args().get(0);
            int count = count(effect, 1);
            if (Item.getAssetMap().getAsset(itemId) == null) {
                throw new RuntimeError(effect.pos(), "unknown item " + itemId);
            }
            Ref<EntityStore> ref = BuiltinFunctions.playerEntity(session.getContext());
            Store<EntityStore> store = ref.getStore();
            ItemStackTransaction tx = Player.giveItem(new ItemStack(itemId, count), ref, store);
            ItemStack remainder = tx.getRemainder();
            int given = count - (remainder == null ? 0 : remainder.getQuantity());
            if (given <= 0) return "Your hands are full.";
            return "You receive " + given + " " + prettyItem(itemId) + ".";
        });

        effects.register("take", (session, effect) -> {
            String itemId = effect.args().get(0);
            int count = count(effect, 1);
            HytaleContext ctx = session.getContext();
            Ref<EntityStore> ref = BuiltinFunctions.playerEntity(ctx);
            Store<EntityStore> store = ref.getStore();
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) return null;
            ItemStackTransaction tx = player.getInventory().getCombinedHotbarFirst().removeItemStack(new ItemStack(itemId, count), true, false);
            if (!tx.succeeded()) {
                plugin.getLogger().at(Level.INFO).log("<<take %s %d>> failed for %s: not enough in inventory", itemId, count, session.getPlayer().getUsername());
                return "You don't have " + count + " " + prettyItem(itemId) + ".";
            }
            return "You hand over " + count + " " + prettyItem(itemId) + ".";
        });

        effects.register("shop", (session, effect) -> {
            String shopId = effect.args().isEmpty() ? npcRole(session) : effect.args().get(0);
            if (shopId == null || BarterShopAsset.getAssetMap().getAsset(shopId) == null) {
                throw new RuntimeError(effect.pos(), "no barter shop named '" + shopId + "'");
            }
            Ref<EntityStore> ref = BuiltinFunctions.playerEntity(session.getContext());
            Store<EntityStore> store = ref.getStore();
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) return null;
            // The shop replaces the dialogue window, so the conversation is over.
            session.detach();
            player.getPageManager().openCustomPage(ref, store, new BarterPage(session.getPlayer(), shopId));
            return null;
        });

        effects.register("attitude", (session, effect) -> {
            Attitude attitude;
            try {
                attitude = Attitude.valueOf(effect.args().get(0).toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new RuntimeError(effect.pos(), "unknown attitude " + effect.args().get(0));
            }
            Ref<EntityStore> playerRef = BuiltinFunctions.playerEntity(session.getContext());
            Store<EntityStore> store = playerRef.getStore();
            Ref<EntityStore> npcRef = npcRef(session, store);
            if (npcRef == null) return null;
            WorldSupport support = WorldSupport.get(npcRef, store);
            try {
                support.overrideAttitude(playerRef, attitude, ATTITUDE_DURATION_SECONDS);
            } catch (NullPointerException e) {
                // Only roles that use attitude overrides themselves allocate the memory for them.
                plugin.getLogger().at(Level.WARNING).log("%s: this NPC's role (%s) does not support attitude overrides", effect.pos(), npcRole(session));
            }
            return null;
        });

        effects.register("objective", (session, effect) -> {
            String objectiveId = effect.args().get(0);
            ObjectivePlugin objectives = ObjectivePlugin.get();
            if (objectives == null) throw new RuntimeError(effect.pos(), "the objectives plugin is not loaded");
            Ref<EntityStore> ref = BuiltinFunctions.playerEntity(session.getContext());
            Store<EntityStore> store = ref.getStore();
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) return null;
            if (!objectives.canPlayerDoObjective(player, objectiveId)) {
                return null; // already on it
            }
            if (objectives.startObjective(objectiveId, Set.of(session.getPlayer().getUuid()), session.getWorld().getWorldConfig().getUuid(), null, store) == null) {
                throw new RuntimeError(effect.pos(), "could not start objective " + objectiveId + " (does it exist?)");
            }
            return "New objective.";
        });

        effects.register("anim", (session, effect) -> {
            Store<EntityStore> store = BuiltinFunctions.store(session.getContext());
            Ref<EntityStore> npcRef = npcRef(session, store);
            if (npcRef == null) return null;
            NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
            if (npc != null) {
                npc.playAnimation(npcRef, AnimationSlot.Emote, effect.args().get(0), store);
            }
            return null;
        });

        effects.register("sound", (session, effect) -> {
            String soundId = effect.args().get(0);
            int index = SoundEvent.getAssetMap().getIndex(soundId);
            if (index == 0) throw new RuntimeError(effect.pos(), "unknown sound event " + soundId);
            Store<EntityStore> store = BuiltinFunctions.store(session.getContext());
            Ref<EntityStore> npcRef = npcRef(session, store);
            NetworkId netId = npcRef == null ? null : store.getComponent(npcRef, NetworkId.getComponentType());
            if (netId != null) {
                SoundUtil.playSoundEventEntity(index, netId.getId(), store);
            } else {
                SoundUtil.playSoundEvent2dToPlayer(session.getPlayer(), index, com.hypixel.hytale.protocol.SoundCategory.SFX);
            }
            return null;
        });
    }

    @Nullable
    private static Ref<EntityStore> npcRef(DialogueSession session, Store<EntityStore> store) {
        Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(session.getNpcId());
        return ref != null && ref.isValid() ? ref : null;
    }

    @Nullable
    private static String npcRole(DialogueSession session) {
        Store<EntityStore> store = BuiltinFunctions.store(session.getContext());
        Ref<EntityStore> npcRef = npcRef(session, store);
        if (npcRef == null) return null;
        NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
        return npc == null ? null : npc.getRoleName();
    }

    private static int count(Effect effect, int defaultCount) {
        if (effect.args().size() < 2) return defaultCount;
        try {
            return Math.max(1, Integer.parseInt(effect.args().get(1).trim()));
        } catch (NumberFormatException e) {
            throw new RuntimeError(effect.pos(), "count must be a whole number, got " + effect.args().get(1));
        }
    }

    /** "Food_Pie_Apple" -> "Apple Pie". */
    static String prettyItem(String itemId) {
        String[] parts = itemId.split("_");
        if (parts.length <= 1) return itemId;
        StringBuilder sb = new StringBuilder();
        for (int i = parts.length - 1; i >= 1; i--) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(parts[i]);
        }
        return sb.toString();
    }
}
