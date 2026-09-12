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
import java.util.logging.Level;

/**
 * Dialogues bound to placed blocks, by world and position: using the block talks. Kept in {@code blocks.json} in
 * the plugin's data folder, never in the world's chunks, so removing the mod leaves every block exactly as it was.
 * Only blocks whose type has a Use interaction (doors, chests, signs, benches, ...) can be bound, because those are
 * the only ones the server reports a use for.
 */
public final class BlockBindings {

    /** "instead": the block's own action is suppressed; "also": both happen (right for levers and doors). */
    public static final String MODE_INSTEAD = "instead";
    public static final String MODE_ALSO = "also";

    public record Binding(String dialogue, String mode) {
        public boolean suppressesBlock() { return !MODE_ALSO.equalsIgnoreCase(mode); }
    }

    /** One line of blocks.json. */
    private static final class Entry {
        String world;
        int x, y, z;
        String dialogue;
        String mode;
    }

    private static final class File {
        List<Entry> blocks = new ArrayList<>();
    }

    private final Path file;
    private final java.util.function.Consumer<String> warn;
    private final java.util.function.Consumer<String> info;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<String, Binding> byKey = new LinkedHashMap<>();
    private volatile boolean dirty = false;

    public BlockBindings(@Nonnull Path dataFolder, @Nonnull HytaleLogger logger) {
        this(dataFolder, m -> logger.at(Level.WARNING).log("%s", m), m -> logger.at(Level.INFO).log("%s", m));
    }

    /** For tests and hosts without the game's logger. */
    public BlockBindings(@Nonnull Path dataFolder, @Nonnull java.util.function.Consumer<String> warn, @Nonnull java.util.function.Consumer<String> info) {
        this.file = dataFolder.resolve("blocks.json");
        this.warn = warn;
        this.info = info;
        load();
    }

    public static String key(String world, int x, int y, int z) {
        return world + "|" + x + "|" + y + "|" + z;
    }

    public static boolean isMode(@Nullable String mode) {
        return MODE_INSTEAD.equalsIgnoreCase(mode) || MODE_ALSO.equalsIgnoreCase(mode);
    }

    @Nullable
    public synchronized Binding get(String world, int x, int y, int z) {
        return byKey.get(key(world, x, y, z));
    }

    public synchronized void set(String world, int x, int y, int z, @Nonnull String dialogue, @Nonnull String mode) {
        byKey.put(key(world, x, y, z), new Binding(dialogue, MODE_ALSO.equalsIgnoreCase(mode) ? MODE_ALSO : MODE_INSTEAD));
        dirty = true;
    }

    public synchronized boolean remove(String world, int x, int y, int z) {
        boolean had = byKey.remove(key(world, x, y, z)) != null;
        if (had) dirty = true;
        return had;
    }

    /** Every binding, as "world x y z -> dialogue (mode)" lines. */
    public synchronized List<String> describeAll() {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Binding> e : byKey.entrySet()) {
            String[] k = e.getKey().split("\\|", 4);
            out.add(k[0] + " " + k[1] + " " + k[2] + " " + k[3] + "  ->  " + e.getValue().dialogue() + " (" + e.getValue().mode() + ")");
        }
        return out;
    }

    public synchronized int size() { return byKey.size(); }

    private void load() {
        if (!Files.isRegularFile(file)) return;
        try {
            File f = gson.fromJson(Files.readString(file, StandardCharsets.UTF_8), File.class);
            if (f == null || f.blocks == null) return;
            for (Entry e : f.blocks) {
                if (e == null || e.world == null || e.dialogue == null) continue;
                byKey.put(key(e.world, e.x, e.y, e.z), new Binding(e.dialogue, isMode(e.mode) ? e.mode.toLowerCase() : MODE_INSTEAD));
            }
            info.accept(byKey.size() + " block binding(s) loaded");
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
            for (Map.Entry<String, Binding> e : byKey.entrySet()) {
                String[] k = e.getKey().split("\\|", 4);
                Entry en = new Entry();
                en.world = k[0]; en.x = Integer.parseInt(k[1]); en.y = Integer.parseInt(k[2]); en.z = Integer.parseInt(k[3]);
                en.dialogue = e.getValue().dialogue(); en.mode = e.getValue().mode();
                f.blocks.add(en);
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
