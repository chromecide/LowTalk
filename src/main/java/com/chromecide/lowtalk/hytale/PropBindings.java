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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Dialogues bound to props: the block and item stand-ins the game spawns as entities (Entity Spawn page, prefabs).
 * Keyed by the entity's UUID, so a prop keeps its dialogue when the Entity Tool moves it. Kept in {@code props.json}
 * in the plugin's data folder. The prop itself only gains the game's own interactions component so the interact
 * key reaches it; without the mod that component points at an unknown interaction and does nothing.
 */
public final class PropBindings {
    /** The root interaction a bound prop's interact key runs; shipped as a no-op in LowTalk's asset pack. */
    public static final String USE_INTERACTION = "LowTalk_Prop_Use";

    /** hint: translation key of the "Press [key] to ..." prompt, or null for none. */
    public record Binding(String dialogue, @Nullable String name, @Nullable String hint) {}

    private static final class Entry {
        String uuid;
        String dialogue;
        String name;
        String hint;
    }

    private static final class File {
        List<Entry> props = new ArrayList<>();
    }

    private final Path file;
    private final java.util.function.Consumer<String> warn;
    private final java.util.function.Consumer<String> info;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<UUID, Binding> byId = new LinkedHashMap<>();
    private volatile boolean dirty = false;

    public PropBindings(@Nonnull Path dataFolder, @Nonnull HytaleLogger logger) {
        this(dataFolder, m -> logger.at(Level.WARNING).log("%s", m), m -> logger.at(Level.INFO).log("%s", m));
    }

    /** For tests and hosts without the game's logger. */
    public PropBindings(@Nonnull Path dataFolder, @Nonnull java.util.function.Consumer<String> warn, @Nonnull java.util.function.Consumer<String> info) {
        this.file = dataFolder.resolve("props.json");
        this.warn = warn;
        this.info = info;
        load();
    }

    @Nullable
    public synchronized Binding get(@Nonnull UUID id) {
        return byId.get(id);
    }

    public synchronized void set(@Nonnull UUID id, @Nonnull String dialogue, @Nullable String name, @Nullable String hint) {
        String n = name == null || name.isBlank() ? null : name.trim();
        String h = hint == null || hint.isBlank() ? null : hint.trim();
        byId.put(id, new Binding(dialogue, n, h));
        dirty = true;
    }

    public synchronized boolean remove(@Nonnull UUID id) {
        boolean had = byId.remove(id) != null;
        if (had) dirty = true;
        return had;
    }

    /** Every binding, as "uuid -> dialogue [name]" lines. */
    public synchronized List<String> describeAll() {
        List<String> out = new ArrayList<>();
        for (Map.Entry<UUID, Binding> e : byId.entrySet()) {
            Binding b = e.getValue();
            out.add(e.getKey() + "  ->  " + b.dialogue() + (b.name() == null ? "" : "  [" + b.name() + "]"));
        }
        return out;
    }

    public synchronized int size() { return byId.size(); }

    /** The ids of every dialogue some prop opens. */
    public synchronized java.util.Set<String> dialogues() {
        java.util.Set<String> out = new java.util.HashSet<>();
        for (Binding b : byId.values()) out.add(b.dialogue());
        return out;
    }

    private void load() {
        if (!Files.isRegularFile(file)) return;
        try {
            File f = gson.fromJson(Files.readString(file, StandardCharsets.UTF_8), File.class);
            if (f == null || f.props == null) return;
            for (Entry e : f.props) {
                if (e == null || e.uuid == null || e.dialogue == null) continue;
                try {
                    byId.put(UUID.fromString(e.uuid), new Binding(e.dialogue, e.name == null || e.name.isBlank() ? null : e.name,
                            e.hint == null || e.hint.isBlank() ? null : e.hint));
                } catch (IllegalArgumentException bad) {
                    warn.accept("props.json: skipping entry with bad uuid '" + e.uuid + "'");
                }
            }
            info.accept(byId.size() + " prop binding(s) loaded");
        } catch (Exception e) {
            warn.accept("Could not read " + file + ": " + e);
        }
    }

    /** Write the file if anything changed. Safe from any thread. */
    public void flush() {
        String json;
        synchronized (this) {
            if (!dirty) return;
            File f = new File();
            for (Map.Entry<UUID, Binding> e : byId.entrySet()) {
                Entry en = new Entry();
                en.uuid = e.getKey().toString();
                en.dialogue = e.getValue().dialogue();
                en.name = e.getValue().name();
                en.hint = e.getValue().hint();
                f.props.add(en);
            }
            json = gson.toJson(f);
            dirty = false;
        }
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            warn.accept("Could not write " + file + ": " + ex);
            dirty = true;
        }
    }
}
