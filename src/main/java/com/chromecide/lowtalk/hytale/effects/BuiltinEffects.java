package com.chromecide.lowtalk.hytale.effects;

import com.chromecide.lowtalk.LowTalkPlugin;
import com.chromecide.lowtalk.hytale.EffectHost;
import com.chromecide.lowtalk.hytale.EffectRegistry;
import com.chromecide.lowtalk.hytale.HytaleContext;
import com.chromecide.lowtalk.hytale.functions.BuiltinFunctions;
import com.chromecide.lowtalk.runtime.Effect;
import com.chromecide.lowtalk.runtime.RuntimeError;
import com.hypixel.hytale.builtin.adventure.objectives.ObjectivePlugin;
import com.hypixel.hytale.builtin.adventure.reputation.ReputationPlugin;
import com.hypixel.hytale.builtin.crafting.CraftingPlugin;
import com.hypixel.hytale.builtin.teleport.TeleportPlugin;
import com.hypixel.hytale.builtin.teleport.Warp;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.protocol.packets.interface_.Notification;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.protocol.packets.interface_.ShowEventTitle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import org.joml.Vector3d;
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
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
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
            ItemStackTransaction tx = InventoryComponent.getCombined(store, ref, InventoryComponent.HOTBAR_FIRST).removeItemStack(new ItemStack(itemId, count), true, false);
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

        // <<objective Id>> (start), <<objective start Id>>, <<objective cancel Id>>, <<objective line LineId>>,
        // <<objective task TaskId>> (advance a "talk to this NPC" task of an active objective)
        effects.register("objective", (session, effect) -> {
            String verb = "start";
            String objectiveId = effect.args().get(0);
            if (effect.args().size() > 1) {
                verb = effect.args().get(0).trim().toLowerCase(Locale.ROOT);
                objectiveId = effect.args().get(1);
            }
            ObjectivePlugin objectives = ObjectivePlugin.get();
            if (objectives == null) throw new RuntimeError(effect.pos(), "the objectives plugin is not loaded");
            Ref<EntityStore> ref = BuiltinFunctions.playerEntity(session.getContext());
            Store<EntityStore> store = ref.getStore();
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) return null;
            java.util.UUID worldId = session.getWorld().getWorldConfig().getUuid();
            switch (verb) {
                case "start" -> {
                    if (!objectives.canPlayerDoObjective(player, objectiveId)) return null; // already on it, or done
                    if (objectives.startObjective(objectiveId, Set.of(session.getPlayer().getUuid()), worldId, null, store) == null) {
                        throw new RuntimeError(effect.pos(), "could not start objective " + objectiveId + " (does it exist?)");
                    }
                    return "New objective.";
                }
                case "line" -> {
                    if (!objectives.canPlayerDoObjectiveLine(player, objectiveId)) return null;
                    if (objectives.startObjectiveLine(store, objectiveId, Set.of(session.getPlayer().getUuid()), worldId, null) == null) {
                        throw new RuntimeError(effect.pos(), "could not start objective line " + objectiveId + " (does it exist?)");
                    }
                    return "New objective.";
                }
                case "cancel" -> {
                    Set<java.util.UUID> active = player.getPlayerConfigData().getActiveObjectiveUUIDs();
                    if (active == null || objectives.getObjectiveDataStore() == null) return null;
                    for (java.util.UUID id : new java.util.ArrayList<>(active)) {
                        com.hypixel.hytale.builtin.adventure.objectives.Objective o = objectives.getObjectiveDataStore().getObjective(id);
                        if (o != null && objectiveId.equals(o.getObjectiveId())) {
                            objectives.cancelObjective(id, store);
                            return "Objective abandoned.";
                        }
                    }
                    return null;
                }
                case "task" -> {
                    if (session.getNpcId().getMostSignificantBits() == 0L) {
                        throw new RuntimeError(effect.pos(), "<<objective task>> needs an NPC (this dialogue has none)");
                    }
                    String anim = com.hypixel.hytale.builtin.adventure.npcobjectives.NPCObjectivesPlugin
                            .updateTaskCompletion(store, ref, session.getPlayer(), session.getNpcId(), objectiveId);
                    Ref<EntityStore> npcRef = npcRef(session, store);
                    if (anim != null && npcRef != null) {
                        NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
                        if (npc != null) npc.playAnimation(npcRef, AnimationSlot.Emote, anim, true, store);
                    }
                    // Completing the task may open the game's own dialog box; if it did, our window is gone.
                    if (player.getPageManager().getCustomPage() != null && player.getPageManager().getCustomPage() != ((com.chromecide.lowtalk.hytale.DialogueSession) session).getPage()) {
                        session.detach();
                    }
                    return null;
                }
                default -> throw new RuntimeError(effect.pos(), "<<objective>> expects start, cancel, line or task, got " + verb);
            }
        });


        // ---- standing

        effects.register("reputation", (session, effect) -> {
            ReputationPlugin rep = ReputationPlugin.get();
            if (rep == null) throw new RuntimeError(effect.pos(), "the reputation plugin is not loaded");
            String raw = effect.args().get(0).trim();
            int delta;
            try {
                delta = Integer.parseInt(raw.startsWith("+") ? raw.substring(1) : raw);
            } catch (NumberFormatException e) {
                throw new RuntimeError(effect.pos(), "<<reputation>> expects a number like +10 or -5, got " + raw);
            }
            Ref<EntityStore> ref = BuiltinFunctions.playerEntity(session.getContext());
            Store<EntityStore> store = ref.getStore();
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) return null;
            int result;
            if (effect.args().size() > 1) {
                String group = effect.args().get(1);
                result = rep.changeReputation(player, group, delta, store);
                if (result == Integer.MIN_VALUE) {
                    throw new RuntimeError(effect.pos(), "no reputation group called '" + group
                            + "' (groups are defined in Server/NPC/Reputation/Groups/*.json)");
                }
            } else {
                Ref<EntityStore> npcRef = npcRef(session, store);
                if (npcRef == null) throw new RuntimeError(effect.pos(), "<<reputation>> needs an NPC or a group name");
                result = rep.changeReputation(player, npcRef, delta, store);
                if (result == Integer.MIN_VALUE) {
                    throw new RuntimeError(effect.pos(), "this NPC belongs to no reputation group; the base game defines none, "
                            + "so add a Server/NPC/Reputation/Groups/*.json that lists its NPC group, or name a group: <<reputation "
                            + effect.args().get(0) + " Group_Id>>");
                }
            }
            return delta >= 0 ? "Your standing improves." : "Your standing suffers.";
        });

        // ---- feedback

        effects.register("notify", (session, effect) -> {
            // <<notify "Text" ["Detail"] [style]>>: after the text, a style word is a style wherever it sits and the
            // remaining token is the detail, so <<notify "Text" success>> works without an empty placeholder.
            String text = effect.args().get(0);
            String secondary = null;
            NotificationStyle style = NotificationStyle.Default;
            for (String a : effect.args().subList(1, effect.args().size())) {
                NotificationStyle asStyle = null;
                try {
                    asStyle = NotificationStyle.valueOf(capitalise(a.trim()));
                } catch (IllegalArgumentException ignored) {
                    // not a style word
                }
                if (asStyle != null) {
                    style = asStyle;
                } else if (secondary == null) {
                    secondary = a;
                } else {
                    throw new RuntimeError(effect.pos(), "notify style must be default, success, warning, or danger, got '" + a + "'");
                }
            }
            session.getPlayer().getPacketHandler().write(new Notification(
                    Message.raw(text).getFormattedMessage(),
                    secondary == null ? null : Message.raw(secondary).getFormattedMessage(),
                    null, null, style, null));
            return null;
        });

        effects.register("title", (session, effect) -> {
            // <<title "Primary" ["Secondary"] [major] [seconds]>>: "major"/"minor" and a number are recognised
            // wherever they sit; the remaining token is the secondary text.
            String primary = effect.args().get(0);
            String secondary = null;
            boolean major = false;
            float seconds = 3.0f;
            for (String a : effect.args().subList(1, effect.args().size())) {
                String t = a.trim();
                if (t.equalsIgnoreCase("major")) {
                    major = true;
                } else if (t.equalsIgnoreCase("minor")) {
                    major = false;
                } else if (t.matches("\\d+(\\.\\d+)?")) {
                    seconds = Float.parseFloat(t);
                } else if (secondary == null) {
                    secondary = a;
                } else {
                    throw new RuntimeError(effect.pos(), "title takes a secondary text, 'major' and a number of seconds; got an extra '" + a + "'");
                }
            }
            session.getPlayer().getPacketHandler().write(new ShowEventTitle(
                    0.5f, 0.5f, seconds, null, major,
                    Message.raw(primary).getFormattedMessage(),
                    secondary == null ? null : Message.raw(secondary).getFormattedMessage()));
            return null;
        });

        // ---- the player's body

        effects.register("effect", (session, effect) -> {
            String id = effect.args().get(0);
            EntityEffect asset = EntityEffect.getAssetMap().getAsset(id);
            if (asset == null) throw new RuntimeError(effect.pos(), "unknown entity effect " + id);
            Ref<EntityStore> ref = BuiltinFunctions.playerEntity(session.getContext());
            Store<EntityStore> store = ref.getStore();
            EffectControllerComponent controller = store.getComponent(ref, EffectControllerComponent.getComponentType());
            if (controller == null) return null;
            controller.addEffect(ref, asset, store);
            return null;
        });

        effects.register("cure", (session, effect) -> {
            String id = effect.args().get(0);
            int index = EntityEffect.getAssetMap().getIndex(id);
            if (index < 0) throw new RuntimeError(effect.pos(), "unknown entity effect " + id);
            Ref<EntityStore> ref = BuiltinFunctions.playerEntity(session.getContext());
            Store<EntityStore> store = ref.getStore();
            EffectControllerComponent controller = store.getComponent(ref, EffectControllerComponent.getComponentType());
            if (controller != null) controller.removeEffect(ref, index, store);
            return null;
        });

        effects.register("stat", (session, effect) -> {
            String name = effect.args().get(0);
            String raw = effect.args().size() > 1 ? effect.args().get(1).trim() : "max";
            EntityStatValue value = BuiltinFunctions.stat(session.getContext(), name);
            if (value == null) return null;
            Ref<EntityStore> ref = BuiltinFunctions.playerEntity(session.getContext());
            EntityStatMap stats = ref.getStore().getComponent(ref, EntityStatMap.getComponentType());
            int index = EntityStatType.getAssetMap().getIndex(name);
            if (stats == null) return null;
            if (raw.equalsIgnoreCase("max")) {
                stats.setStatValue(index, value.getMax());
            } else if (raw.startsWith("+") || raw.startsWith("-")) {
                stats.addStatValue(index, parseNumber(effect, raw));
            } else {
                stats.setStatValue(index, parseNumber(effect, raw));
            }
            return null;
        });

        effects.register("heal", (session, effect) -> {
            EntityStatValue value = BuiltinFunctions.stat(session.getContext(), "Health");
            if (value == null) return null;
            Ref<EntityStore> ref = BuiltinFunctions.playerEntity(session.getContext());
            EntityStatMap stats = ref.getStore().getComponent(ref, EntityStatMap.getComponentType());
            int index = EntityStatType.getAssetMap().getIndex("Health");
            if (stats == null) return null;
            if (effect.args().isEmpty()) {
                stats.setStatValue(index, value.getMax());
            } else {
                stats.addStatValue(index, Math.abs(parseNumber(effect, effect.args().get(0))));
            }
            return "You feel better.";
        });

        effects.register("learn", (session, effect) -> {
            String recipe = effect.args().get(0);
            Ref<EntityStore> ref = BuiltinFunctions.playerEntity(session.getContext());
            boolean learned = CraftingPlugin.learnRecipe(ref, recipe, ref.getStore());
            return learned ? "You learn a new recipe." : null;
        });

        // ---- movement

        effects.register("teleport", (session, effect) -> {
            Ref<EntityStore> ref = BuiltinFunctions.playerEntity(session.getContext());
            Store<EntityStore> store = ref.getStore();
            Transform target;
            if (effect.args().size() >= 3) {
                double x = parseNumber(effect, effect.args().get(0));
                double y = parseNumber(effect, effect.args().get(1));
                double z = parseNumber(effect, effect.args().get(2));
                target = new Transform(new Vector3d(x, y, z));
            } else {
                String name = effect.args().get(0);
                TeleportPlugin tp = TeleportPlugin.get();
                Warp warp = tp == null ? null : tp.getWarps().get(name);
                if (warp == null) throw new RuntimeError(effect.pos(), "no warp called '" + name + "' (use x y z for coordinates)");
                target = warp.getTransform();
            }
            session.end();
            store.addComponent(ref, Teleport.getComponentType(), Teleport.createForPlayer(target));
            return null;
        });

        effects.register("anim", (session, effect) -> {
            Store<EntityStore> store = BuiltinFunctions.store(session.getContext());
            Ref<EntityStore> npcRef = npcRef(session, store);
            if (npcRef == null) return null;
            NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
            if (npc == null) return null;
            AnimationSlot slot = AnimationSlot.Emote;
            if (effect.args().size() > 1) {
                try {
                    slot = AnimationSlot.valueOf(effect.args().get(1));
                } catch (IllegalArgumentException e) {
                    throw new RuntimeError(effect.pos(), "unknown animation slot " + effect.args().get(1)
                            + " (use Movement, Status, Action, Face, Emote or ServerAction)");
                }
            }
            // force: the game skips a play request when the slot already holds that animation, which made a
            // repeated <<anim Wave>> a silent no-op.
            npc.playAnimation(npcRef, slot, effect.args().get(0), true, store);
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
    static Ref<EntityStore> npcRef(EffectHost session, Store<EntityStore> store) {
        Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(session.getNpcId());
        return ref != null && ref.isValid() ? ref : null;
    }

    @Nullable
    private static String npcRole(EffectHost session) {
        Store<EntityStore> store = BuiltinFunctions.store(session.getContext());
        Ref<EntityStore> npcRef = npcRef(session, store);
        if (npcRef == null) return null;
        NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
        return npc == null ? null : npc.getRoleName();
    }

    private static float parseNumber(Effect effect, String raw) {
        try {
            return Float.parseFloat(raw.startsWith("+") ? raw.substring(1) : raw);
        } catch (NumberFormatException e) {
            throw new RuntimeError(effect.pos(), "expected a number, got " + raw);
        }
    }

    private static String capitalise(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase(Locale.ROOT);
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
