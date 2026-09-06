package com.chromecide.lowtalk.hytale;

import com.chromecide.lowtalk.LowTalkPlugin;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.builtin.adventure.objectives.ObjectivePlugin;
import com.hypixel.hytale.builtin.adventure.reputation.ReputationPlugin;
import com.hypixel.hytale.builtin.adventure.reputation.assets.ReputationGroup;
import com.hypixel.hytale.server.core.entity.Frozen;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import java.util.UUID;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.server.npc.role.support.DisplayNameSupport;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import org.joml.Vector3d;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * A flat world with one straight corridor. Each station along it is an NPC whose nameplate is
 * the station label and whose dialogue explains what to test and what should happen.
 * Built once with /lowtalk testworld build; /lowtalk testworld teleports you there.
 */
public final class TestWorld {

    public static final String WORLD_NAME = "lowtalk_test";

    /** A test station: where it stands, what NPC role plays it, the tag its dialogue binds to, and the nameplate. */
    public record Station(int x, String role, String tag, String label) {}

    /** LowTalk's own static talker role, shipped in the asset pack (Server/NPC/Roles/LowTalk/). */
    public static final String TESTER = "LowTalk_Tester";

    public static final List<Station> STATIONS = List.of(
            new Station(6, TESTER, "test_basics", "1 - Basics: choices, hubs, Continue, Leave"),
            new Station(16, TESTER, "test_memory", "2A - Memory: per-NPC variables"),
            new Station(18, TESTER, "test_memory", "2B - Memory: talk to A first, then me"),
            new Station(28, TESTER, "test_input", "3 - Text input"),
            new Station(38, TESTER, "test_items", "4 - Items: give, take, has, count"),
            new Station(48, TESTER, "test_feedback", "5 - Feedback: notify, title, sound, anim"),
            new Station(58, TESTER, "test_body", "6 - Body: heal, effects, stats"),
            new Station(68, TESTER, "test_progress", "7 - Objectives and reputation"),
            new Station(78, "Kweebec_Merchant", "test_travel", "8 - Shop and teleport"),
            new Station(88, TESTER, "test_random", "9 - Random, chance, ordinal, time"),
            new Station(98, TESTER, "test_format", "10 - Format extras"),
            new Station(108, TESTER, "test_world", "11 - Weather, time, translation"),
            new Station(118, TESTER, "test_npc", "12 - NPC control and objectives"),
            new Station(128, TESTER, "test_media", "13 - Music, effects, camera")
    );

    private static final int CORRIDOR_START = -4;
    private static final int CORRIDOR_END = 134;
    private static final int HALF_WIDTH = 2;       // floor spans z = -2 .. 2
    private static final int FLOOR_Y = 0;          // the flat world's single layer is y = 0
    private static final int WALL_HEIGHT = 3;
    private static final String FLOOR = "Build_GreyDark_Cube";
    private static final String STRIPE = "Build_White_Cube";
    private static final String WALL = "Build_Grey_Cube";
    private static final String WALL_TOP = "Build_Black_Cube";

    private TestWorld() {}

    /** Create the world if needed, then build the corridor and spawn the stations. Reports progress through {@code out}. */
    public static void build(@Nonnull LowTalkPlugin plugin, @Nonnull Consumer<String> out) {
        withWorld(plugin, out, world -> world.execute(() -> {
            Store<EntityStore> store = world.getEntityStore().getStore();
            boolean built = Boolean.TRUE.equals(plugin.getStore().get(plugin.getStore().world(), WORLD_NAME, "built"));
            Object storedEnd = plugin.getStore().get(plugin.getStore().world(), WORLD_NAME, "end");
            if (built && storedEnd instanceof Number n && n.intValue() == CORRIDOR_END) {
                out.accept("Rebuilding the corridor in place (blocks are reset, stations respawn).");
            } else if (built) {
                out.accept("The corridor has grown since it was built; extending it and respawning the stations.");
            }
            world.getWorldConfig().setSpawningNPC(false);
            // A flat world has no natural weather, so give it a baseline sky for the weather station to return to.
            world.getWorldConfig().setForcedWeather(plugin.getSettings().getClearSkyWeather());
            world.getWorldConfig().markChanged();
            com.hypixel.hytale.builtin.weather.resources.WeatherResource weather =
                    store.getResource(com.hypixel.hytale.builtin.weather.resources.WeatherResource.getResourceType());
            if (weather != null) weather.setForcedWeather(plugin.getSettings().getClearSkyWeather());
            WorldTimeResource time = store.getResource(WorldTimeResource.getResourceType());
            if (time != null) time.setDayTime(0.5, world, store);

            Set<Long> chunks = new HashSet<>();
            for (int x = CORRIDOR_START - 1; x <= CORRIDOR_END + 1; x++) {
                for (int z = -HALF_WIDTH - 1; z <= HALF_WIDTH + 1; z++) {
                    chunks.add(ChunkUtil.indexChunkFromBlock(x, z));
                }
            }
            List<CompletableFuture<WorldChunk>> loads = new ArrayList<>();
            for (long index : chunks) loads.add(world.getChunkAsync(index));
            out.accept("Loading " + chunks.size() + " chunks...");
            CompletableFuture.allOf(loads.toArray(new CompletableFuture[0])).thenRunAsync(() -> {
                try {
                    int placed = placeBlocks(world);
                    out.accept("Placed " + placed + " blocks.");
                    for (Ref<EntityStore> ref : corridorNpcs(store)) store.removeEntity(ref, RemoveReason.REMOVE);
                    int spawned = spawnStations(plugin, world, store, out);
                    plugin.getStore().set(plugin.getStore().world(), WORLD_NAME, "built", true);
                    plugin.getStore().set(plugin.getStore().world(), WORLD_NAME, "end", (double) CORRIDOR_END);
                    plugin.getStore().flush();
                    out.accept("Test corridor ready with " + spawned + " station NPC(s). Run /lowtalk reload, then /lowtalk testworld go.");
                } catch (RuntimeException e) {
                    out.accept("Build failed: " + e);
                    plugin.getLogger().at(Level.WARNING).log("Test world build failed: %s", e.toString());
                }
            }, world).exceptionally(t -> {
                out.accept("Chunk loading failed: " + t.getMessage());
                return null;
            });
        }));
    }

    /** Teleport the player to the corridor entrance. */
    public static void teleport(@Nonnull LowTalkPlugin plugin, @Nonnull PlayerRef player, @Nonnull Consumer<String> out) {
        withWorld(plugin, out, world -> {
            Ref<EntityStore> ref = player.getReference();
            if (ref == null || !ref.isValid()) return;
            Store<EntityStore> store = ref.getStore();
            World from = store.getExternalData().getWorld();
            from.execute(() -> {
                if (!ref.isValid()) return;
                Teleport t = new Teleport(world, new Vector3d(CORRIDOR_START + 1.5, FLOOR_Y + 1.0, 0.5), new Rotation3f(0.0f, 0.0f, 0.0f));
                store.addComponent(ref, Teleport.getComponentType(), t);
                out.accept("Off you go. Walk along the corridor; each NPC is a test station.");
            });
        });
    }

    /** The reputation group the tester role belongs to (shipped in Server/NPC/Reputation/Groups). */
    public static final String TEST_REPUTATION_GROUP = "LowTalk_Testers";
    /** Items the stations hand out. */
    private static final String[] TEST_ITEMS = {"Food_Bread"};

    /**
     * Put the player back to a clean slate so the corridor can be walked again: dialogue memory, standing with the
     * test group, active objectives, health, entity effects and the items the stations give. Runs on the player's
     * world thread (the caller is a player command).
     */
    public static void resetPlayer(@Nonnull LowTalkPlugin plugin, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                   @Nonnull PlayerRef playerRef, @Nonnull Consumer<String> out) {
        List<String> done = new ArrayList<>();
        UUID uuid = playerRef.getUuid();
        int forgotten = plugin.getStore().resetPlayer(uuid);
        done.add(forgotten + " dialogue record(s) forgotten");

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            out.accept("Reset: " + String.join(", ", done) + ".");
            return;
        }

        ReputationPlugin rep = ReputationPlugin.get();
        ReputationGroup group = rep == null ? null : ReputationGroup.getAssetMap().getAsset(TEST_REPUTATION_GROUP);
        if (group != null) {
            int current = rep.getReputationValue(store, ref, TEST_REPUTATION_GROUP);
            if (current != Integer.MIN_VALUE && current != group.getInitialReputationValue()) {
                rep.changeReputation(player, TEST_REPUTATION_GROUP, group.getInitialReputationValue() - current, store);
                done.add("standing with " + TEST_REPUTATION_GROUP + " back to " + group.getInitialReputationValue());
            }
        }

        ObjectivePlugin objectives = ObjectivePlugin.get();
        Set<UUID> active = player.getPlayerConfigData().getActiveObjectiveUUIDs();
        if (objectives != null && active != null && !active.isEmpty()) {
            int cancelled = 0;
            for (UUID id : new ArrayList<>(active)) {
                try {
                    objectives.cancelObjective(id, store);
                    cancelled++;
                } catch (RuntimeException e) {
                    plugin.getLogger().at(Level.WARNING).withCause(e).log("[testworld] could not cancel objective %s", id);
                }
            }
            done.add(cancelled + " objective(s) cancelled");
        }

        EntityStatMap stats = store.getComponent(ref, EntityStatMap.getComponentType());
        int health = EntityStatType.getAssetMap().getIndex("Health");
        if (stats != null && health >= 0 && stats.get(health) != null) {
            stats.setStatValue(health, stats.get(health).getMax());
            done.add("health restored");
        }

        EffectControllerComponent effects = store.getComponent(ref, EffectControllerComponent.getComponentType());
        if (effects != null && effects.getActiveEffectIndexes().length > 0) {
            effects.clearEffects(ref, store);
            done.add("effects cleared");
        }

        for (String item : TEST_ITEMS) {
            var tx = InventoryComponent.getCombined(store, ref, InventoryComponent.HOTBAR_FIRST).removeItemStack(new ItemStack(item, 999), false, false);
            if (tx.succeeded()) done.add(item + " removed");
        }

        out.accept("Reset: " + String.join(", ", done) + ".");
    }

    /** Remove every NPC in the corridor and spawn the stations again on their marks. */
    public static void respawn(@Nonnull LowTalkPlugin plugin, @Nonnull Consumer<String> out) {
        withWorld(plugin, out, world -> world.execute(() -> {
            Store<EntityStore> store = world.getEntityStore().getStore();
            int removed = 0;
            for (Ref<EntityStore> ref : corridorNpcs(store)) {
                store.removeEntity(ref, RemoveReason.REMOVE);
                removed++;
            }
            int spawned = spawnStations(plugin, world, store, out);
            out.accept("Removed " + removed + " NPC(s) and spawned " + spawned + " fresh station(s).");
        }));
    }

    private static List<Ref<EntityStore>> corridorNpcs(Store<EntityStore> store) {
        Vector3d centre = new Vector3d((CORRIDOR_START + CORRIDOR_END) / 2.0, FLOOR_Y + 1.0, 0.5);
        List<Ref<EntityStore>> out = new ArrayList<>();
        for (Ref<EntityStore> ref : new ArrayList<>(TargetUtil.getAllEntitiesInSphere(centre, (CORRIDOR_END - CORRIDOR_START), store))) {
            if (ref.isValid() && store.getComponent(ref, NPCEntity.getComponentType()) != null) out.add(ref);
        }
        return out;
    }

    /** Freeze every NPC in the corridor (for a corridor built before stations were frozen at spawn). */
    public static void freezeAll(@Nonnull LowTalkPlugin plugin, @Nonnull Consumer<String> out) {
        withWorld(plugin, out, world -> world.execute(() -> {
            Store<EntityStore> store = world.getEntityStore().getStore();
            int frozen = 0;
            for (Ref<EntityStore> ref : corridorNpcs(store)) {
                if (!store.getArchetype(ref).contains(Frozen.getComponentType())) {
                    store.ensureComponent(ref, Frozen.getComponentType());
                    frozen++;
                }
            }
            out.accept("Froze " + frozen + " NPC(s) in the corridor.");
        }));
    }

    private static void withWorld(LowTalkPlugin plugin, Consumer<String> out, Consumer<World> then) {
        Universe universe = Universe.get();
        World existing = universe.getWorld(WORLD_NAME);
        if (existing != null) {
            then.accept(existing);
            return;
        }
        CompletableFuture<World> future;
        try {
            if (universe.isWorldLoadable(WORLD_NAME)) {
                out.accept("Loading world " + WORLD_NAME + "...");
                future = universe.loadWorld(WORLD_NAME);
            } else {
                out.accept("Creating flat world " + WORLD_NAME + "...");
                future = universe.addWorld(WORLD_NAME, "Flat", null);
            }
        } catch (RuntimeException e) {
            out.accept("Could not create the world: " + e.getMessage());
            return;
        }
        future.whenComplete((world, error) -> {
            if (error != null || world == null) {
                out.accept("World creation failed: " + (error == null ? "unknown" : error.getMessage()));
                plugin.getLogger().at(Level.WARNING).log("Test world creation failed: %s", String.valueOf(error));
                return;
            }
            then.accept(world);
        });
    }

    private static int placeBlocks(World world) {
        Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
        int placed = 0;
        Set<Integer> stationXs = new HashSet<>();
        for (Station s : STATIONS) stationXs.add(s.x());
        for (int x = CORRIDOR_START; x <= CORRIDOR_END; x++) {
            boolean stripe = stationXs.contains(x);
            for (int z = -HALF_WIDTH; z <= HALF_WIDTH; z++) {
                placed += set(world, chunkStore, x, FLOOR_Y, z, stripe ? STRIPE : FLOOR) ? 1 : 0;
                // Clear the walkway so an end cap from a shorter corridor, or anything built inside, is removed.
                for (int y = 1; y <= WALL_HEIGHT; y++) {
                    placed += set(world, chunkStore, x, FLOOR_Y + y, z, BlockType.EMPTY_KEY) ? 1 : 0;
                }
            }
            for (int side = -1; side <= 1; side += 2) {
                int z = side * (HALF_WIDTH + 1);
                placed += set(world, chunkStore, x, FLOOR_Y, z, WALL) ? 1 : 0;
                for (int y = 1; y <= WALL_HEIGHT; y++) {
                    placed += set(world, chunkStore, x, FLOOR_Y + y, z, y == WALL_HEIGHT ? WALL_TOP : WALL) ? 1 : 0;
                }
            }
        }
        // End caps so nobody wanders off the edges.
        for (int z = -HALF_WIDTH - 1; z <= HALF_WIDTH + 1; z++) {
            for (int y = 0; y <= WALL_HEIGHT; y++) {
                placed += set(world, chunkStore, CORRIDOR_START - 1, FLOOR_Y + y, z, WALL) ? 1 : 0;
                placed += set(world, chunkStore, CORRIDOR_END + 1, FLOOR_Y + y, z, WALL) ? 1 : 0;
            }
        }
        return placed;
    }

    private static boolean set(World world, Store<ChunkStore> chunkStore, int x, int y, int z, String blockId) {
        int index = BlockType.getAssetMap().getIndex(blockId);
        BlockType type = BlockType.getAssetMap().getAsset(blockId);
        if (type == null || index == Integer.MIN_VALUE) throw new IllegalArgumentException("unknown block " + blockId);
        Ref<ChunkStore> section = chunkStore.getExternalData().getChunkSectionReferenceAtBlock(x, y, z);
        if (section == null) return false;
        return BlockOperations.setBlock(chunkStore.getExternalData(), section, x, y, z, index, type, 0, 0, 0);
    }

    private static int spawnStations(LowTalkPlugin plugin, World world, Store<EntityStore> store, Consumer<String> out) {
        NPCPlugin npcs = NPCPlugin.get();
        int spawned = 0;
        for (Station s : STATIONS) {
            Vector3d pos = new Vector3d(s.x() + 0.5, FLOOR_Y + 1.0, 0.5);
            var pair = npcs.spawnNPC(store, s.role(), null, pos, new Rotation3f(0.0f, (float) Math.PI / 2.0f, 0.0f));
            if (pair == null) {
                out.accept("Could not spawn " + s.role() + " for station '" + s.label() + "'");
                continue;
            }
            Ref<EntityStore> ref = pair.first();
            DisplayNameSupport.setDisplayName(ref, s.label(), store); // the NPC plugin's own path: nameplate + display name, persisted
            // Stations stand still forever; the conversation hold leaves pre-frozen NPCs frozen.
            store.ensureComponent(ref, Frozen.getComponentType());
            UUIDComponent uuid = store.getComponent(ref, UUIDComponent.getComponentType());
            if (uuid != null) {
                VariableStore vs = plugin.getStore();
                vs.addTag(vs.npc(uuid.getUuid()), s.tag());
            }
            NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
            if (npc != null) spawned++;
        }
        plugin.getStore().flush();
        return spawned;
    }
}
