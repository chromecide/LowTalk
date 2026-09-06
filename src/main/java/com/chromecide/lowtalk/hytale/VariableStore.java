package com.chromecide.lowtalk.hytale;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Persistent variables: one JSON file per player, per NPC, plus one for the world.
 * Records are cached for the life of the server and written atomically when dirty.
 */
public class VariableStore {

    /** On-disk shape shared by all three kinds of record. */
    public static class Record {
        /** scope -> name -> value (Double, String, or Boolean). */
        public Map<String, Map<String, Object>> scopes = new LinkedHashMap<>();
        /** dialogue id -> visited node names. Players only. */
        public Map<String, List<String>> visited = new LinkedHashMap<>();
        /** dialogue id -> once keys. Players only. */
        public Map<String, List<String>> once = new LinkedHashMap<>();
        /** NPCs only: tags applied with /lowtalk tag. */
        public List<String> tags = new ArrayList<>();
        /** World only: NPCs currently frozen by LowTalk for a conversation. */
        public List<String> held = new ArrayList<>();

        transient boolean dirty = false;
    }

    private static final String WORLD_KEY = "world";

    private final Path root;
    private final HytaleLogger logger;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final Map<String, Record> cache = new ConcurrentHashMap<>();

    public VariableStore(@Nonnull Path root, @Nonnull HytaleLogger logger) {
        this.root = root;
        this.logger = logger;
    }

    public Record player(@Nonnull UUID id) {
        return load("players/" + id);
    }

    /** This player's memory with one specific NPC: bare $variables, visited nodes, once keys. */
    public Record pair(@Nonnull UUID playerId, @Nonnull UUID npcId) {
        return load("players/" + playerId + "/" + npcId);
    }

    public Record npc(@Nonnull UUID id) {
        return load("npcs/" + id);
    }

    public Record world() {
        return load(WORLD_KEY);
    }

    // ---- variables

    @Nullable
    public Object get(Record r, String scope, String name) {
        synchronized (r) {
            Map<String, Object> m = r.scopes.get(scope);
            return m == null ? null : m.get(name);
        }
    }

    public void set(Record r, String scope, String name, @Nullable Object value) {
        synchronized (r) {
            Map<String, Object> m = r.scopes.computeIfAbsent(scope, k -> new LinkedHashMap<>());
            if (value == null) m.remove(name);
            else m.put(name, value);
            r.dirty = true;
        }
    }

    public boolean hasVisited(Record r, String dialogue, String node) {
        synchronized (r) {
            List<String> v = r.visited.get(dialogue);
            return v != null && v.contains(node);
        }
    }

    public void markVisited(Record r, String dialogue, String node) {
        synchronized (r) {
            List<String> v = r.visited.computeIfAbsent(dialogue, k -> new ArrayList<>());
            if (!v.contains(node)) {
                v.add(node);
                r.dirty = true;
            }
        }
    }

    public boolean onceDone(Record r, String dialogue, String key) {
        synchronized (r) {
            List<String> v = r.once.get(dialogue);
            return v != null && v.contains(key);
        }
    }

    public void markOnce(Record r, String dialogue, String key) {
        synchronized (r) {
            List<String> v = r.once.computeIfAbsent(dialogue, k -> new ArrayList<>());
            if (!v.contains(key)) {
                v.add(key);
                r.dirty = true;
            }
        }
    }

    public Set<String> tags(Record r) {
        synchronized (r) {
            return Set.copyOf(r.tags);
        }
    }

    /** Every tag on every NPC record on disk or in memory, for editor autocomplete. */
    public java.util.Set<String> allTags() {
        java.util.Set<String> out = new java.util.TreeSet<>();
        Path dir = root.resolve("npcs");
        if (Files.isDirectory(dir)) {
            try (java.util.stream.Stream<Path> s = Files.list(dir)) {
                s.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
                    String name = p.getFileName().toString();
                    try {
                        out.addAll(tags(npc(UUID.fromString(name.substring(0, name.length() - 5)))));
                    } catch (IllegalArgumentException ignored) {
                        // not an NPC record
                    }
                });
            } catch (IOException ignored) {
                // no tags to offer
            }
        }
        return out;
    }

    public boolean addTag(Record r, String tag) {
        synchronized (r) {
            if (r.tags.contains(tag)) return false;
            r.tags.add(tag);
            r.dirty = true;
            return true;
        }
    }

    public boolean removeTag(Record r, String tag) {
        synchronized (r) {
            boolean removed = r.tags.remove(tag);
            if (removed) r.dirty = true;
            return removed;
        }
    }

    public boolean isHeld(UUID npcId) {
        Record w = world();
        synchronized (w) {
            return w.held.contains(npcId.toString());
        }
    }

    public void addHeld(UUID npcId) {
        Record w = world();
        synchronized (w) {
            if (!w.held.contains(npcId.toString())) {
                w.held.add(npcId.toString());
                w.dirty = true;
            }
        }
    }

    public void removeHeld(UUID npcId) {
        Record w = world();
        synchronized (w) {
            if (w.held.remove(npcId.toString())) w.dirty = true;
        }
    }

    /** Wipe a player's variables for one dialogue scope (used by /lowtalk reset). */
    public void resetScope(Record r, String scope, String dialogueId) {
        synchronized (r) {
            r.scopes.remove(scope);
            r.visited.remove(dialogueId);
            r.once.remove(dialogueId);
            r.dirty = true;
        }
    }

    /** Forget everything about one player: their variables and every NPC's memory of them. */
    public int resetPlayer(@Nonnull UUID playerId) {
        String prefix = "players/" + playerId;
        cache.keySet().removeIf(k -> k.equals(prefix) || k.startsWith(prefix + "/"));
        int removed = 0;
        try {
            Path file = root.resolve(prefix + ".json");
            if (Files.deleteIfExists(file)) removed++;
            Path dir = root.resolve(prefix);
            if (Files.isDirectory(dir)) {
                try (var s = Files.list(dir)) {
                    for (Path p : s.toList()) {
                        if (Files.deleteIfExists(p)) removed++;
                    }
                }
                Files.deleteIfExists(dir);
            }
        } catch (IOException e) {
            logger.at(Level.WARNING).log("Could not reset %s: %s", prefix, e.toString());
        }
        return removed;
    }

    // ---- persistence

    private Record load(String key) {
        return cache.computeIfAbsent(key, k -> {
            Path p = root.resolve(k + ".json");
            if (!Files.exists(p)) return new Record();
            try {
                Record r = gson.fromJson(Files.readString(p, StandardCharsets.UTF_8), Record.class);
                if (r == null) return new Record();
                if (r.scopes == null) r.scopes = new LinkedHashMap<>();
                if (r.visited == null) r.visited = new LinkedHashMap<>();
                if (r.once == null) r.once = new LinkedHashMap<>();
                if (r.tags == null) r.tags = new ArrayList<>();
                if (r.held == null) r.held = new ArrayList<>();
                return r;
            } catch (Exception e) {
                logger.at(Level.WARNING).log("Could not read %s: %s", p, e.toString());
                return new Record();
            }
        });
    }

    /** Write every dirty record. Safe to call from any thread. */
    public void flush() {
        for (Map.Entry<String, Record> e : cache.entrySet()) {
            Record r = e.getValue();
            String json;
            synchronized (r) {
                if (!r.dirty) continue;
                json = gson.toJson(r);
                r.dirty = false;
            }
            Path p = root.resolve(e.getKey() + ".json");
            try {
                Files.createDirectories(p.getParent());
                Path tmp = p.resolveSibling(p.getFileName() + ".tmp");
                Files.writeString(tmp, json, StandardCharsets.UTF_8);
                Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException ex) {
                logger.at(Level.WARNING).log("Could not write %s: %s", p, ex.toString());
                synchronized (r) {
                    r.dirty = true;
                }
            }
        }
    }

    /** For /lowtalk vars: a readable dump of one record. */
    public Map<String, Map<String, Object>> snapshotScopes(Record r) {
        synchronized (r) {
            Map<String, Map<String, Object>> out = new LinkedHashMap<>();
            r.scopes.forEach((k, v) -> out.put(k, new HashMap<>(v)));
            return out;
        }
    }
}
