package com.chromecide.lowtalk.hytale;

import com.chromecide.lowtalk.model.Dialogue;
import com.chromecide.lowtalk.parser.DialogueParser;
import com.chromecide.lowtalk.parser.ParseException;
import com.chromecide.lowtalk.parser.Validator;
import com.hypixel.hytale.assetstore.AssetPack;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.AssetModule;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Stream;

/**
 * Loads .talk files and answers "which dialogue does this NPC use".
 *
 * Two kinds of source: the plugin's own dialogues folder (server-side files, copied examples, the test corridor),
 * and every loaded asset pack's {@code Server/LowTalk/Dialogues} folder, which is where creators author in the
 * game's Asset Editor. A full {@link #reload} rescans everything; the Asset Editor hot-loads single files through
 * {@link #loadFile} and {@link #unloadFile}. Sessions already running keep their old tree.
 */
public class DialogueRegistry {
    public record LoadReport(int files, int loaded, int errors, int warnings, List<String> messages) {
        public boolean ok() {
            return errors == 0;
        }
    }

    /** Where dialogues live inside an asset pack. */
    public static final String PACK_DIR = "Server/LowTalk/Dialogues";

    private static final String[] BUNDLED_EXAMPLES = {"rootling_merchant.talk", "village_elder.talk", "fortune_teller.talk"};
    private static final String[] TEST_DIALOGUES = {
            "test_basics.talk", "test_memory.talk", "test_input.talk", "test_items.talk", "test_feedback.talk",
            "test_body.talk", "test_progress.talk", "test_travel.talk", "test_random.talk", "test_format.talk", "_shared.talk", "test_world.talk", "test_npc.talk", "test_media.talk", "test_talker.talk"
    };

    /** A root folder that holds .talk files; {@code label} prefixes file names in messages ("" for the plugin folder). */
    private record Source(String label, Path root) {
        String display(Path file) {
            String rel = root.relativize(file).toString().replace('\\', '/');
            // A slash, not a colon: the dialogue id is everything after the last slash of this display name.
            return label.isEmpty() ? rel : label + "/" + rel;
        }
    }

    /** One loaded file. */
    public record Loaded(Path file, String display, Dialogue dialogue) {}

    private final Path folder;
    private final HytaleLogger logger;
    private final Set<String> extraCommands;
    private final Set<String> extraFunctions;

    /** Loaded files by absolute path, in load order. Guarded by {@code this}. */
    private final Map<Path, Loaded> files = new LinkedHashMap<>();
    /** Dialogues that arrived as JSON assets through the game's asset pipeline, by id. Guarded by {@code this}. */
    private final Map<String, Loaded> assets = new LinkedHashMap<>();
    private volatile Map<String, Dialogue> byId = Map.of();
    private volatile Map<String, List<Dialogue>> byRole = Map.of();
    private volatile Map<String, List<Dialogue>> byTag = Map.of();

    public DialogueRegistry(@Nonnull Path folder, @Nonnull HytaleLogger logger,
                            @Nonnull Set<String> extraCommands, @Nonnull Set<String> extraFunctions) {
        this.folder = folder.toAbsolutePath().normalize();
        this.logger = logger;
        this.extraCommands = extraCommands;
        this.extraFunctions = extraFunctions;
    }

    public Path getFolder() {
        return folder;
    }

    // ---- bundled files

    /** Copy the bundled examples into the folder if it has no dialogues yet. */
    public void copyExamplesIfEmpty() {
        try {
            Files.createDirectories(folder);
            try (Stream<Path> s = Files.list(folder)) {
                if (s.anyMatch(p -> p.toString().endsWith(".talk"))) return;
            }
            for (String name : BUNDLED_EXAMPLES) {
                InputStream in = getClass().getClassLoader().getResourceAsStream("lowtalk-examples/" + name);
                if (in == null) in = DialogueRegistry.class.getResourceAsStream("/lowtalk-examples/" + name);
                if (in == null) {
                    logger.at(Level.INFO).log("Bundled example %s not found on the classpath; skipping", name);
                    continue;
                }
                try (InputStream stream = in) {
                    Files.write(folder.resolve(name), stream.readAllBytes());
                    logger.at(Level.INFO).log("Copied example dialogue %s into %s", name, folder);
                }
            }
        } catch (IOException e) {
            logger.at(Level.WARNING).log("Could not copy example dialogues: %s", e.toString());
        }
    }

    /** Copy the test-corridor dialogues into dialogues/tests/, overwriting, so they match this build. */
    public void copyTestDialogues() {
        Path dir = folder.resolve("tests");
        try {
            Files.createDirectories(dir);
            for (String name : TEST_DIALOGUES) {
                InputStream in = getClass().getClassLoader().getResourceAsStream("lowtalk-examples/tests/" + name);
                if (in == null) in = DialogueRegistry.class.getResourceAsStream("/lowtalk-examples/tests/" + name);
                if (in == null) {
                    // Dev server runs from source: fall back to the project's examples folder.
                    for (Path dev : new Path[] {Path.of("examples", "tests", name), Path.of("..", "examples", "tests", name), Path.of("..", "..", "examples", "tests", name)}) {
                        if (Files.exists(dev)) {
                            in = Files.newInputStream(dev);
                            break;
                        }
                    }
                }
                if (in == null) {
                    logger.at(Level.WARNING).log("Bundled test dialogue %s not found", name);
                    continue;
                }
                try (InputStream stream = in) {
                    Files.write(dir.resolve(name), stream.readAllBytes());
                }
            }
        } catch (IOException e) {
            logger.at(Level.WARNING).log("Could not copy test dialogues: %s", e.toString());
        }
    }

    // ---- sources

    private List<Source> sources() {
        List<Source> out = new ArrayList<>();
        out.add(new Source("", folder));
        AssetModule assets = null;
        try {
            assets = AssetModule.get();
        } catch (RuntimeException ignored) {
            // no asset module (unit tests, very early startup)
        }
        if (assets != null) {
            for (AssetPack pack : assets.getAssetPacks()) {
                try {
                    Path root = pack.getRoot().resolve(PACK_DIR);
                    if (Files.isDirectory(root)) out.add(new Source(pack.getName(), root.toAbsolutePath().normalize()));
                } catch (RuntimeException ignored) {
                    // packs inside archives may not resolve; they cannot be edited anyway
                }
            }
        }
        return out;
    }

    /** The source whose root contains the file, or the file's own folder as a last resort. */
    private Source sourceFor(Path file) {
        Path abs = file.toAbsolutePath().normalize();
        for (Source s : sources()) {
            if (abs.startsWith(s.root())) return s;
        }
        return new Source(abs.getParent().getFileName() == null ? "" : abs.getParent().getFileName().toString(), abs.getParent());
    }

    private static DialogueParser.IncludeResolver resolverFor(Source src) {
        return path -> {
            try {
                Path p = src.root().resolve(path).normalize();
                if (!p.startsWith(src.root()) || !Files.isRegularFile(p)) return null;
                return Files.readString(p, StandardCharsets.UTF_8);
            } catch (IOException e) {
                return null;
            }
        };
    }

    // ---- loading

    public LoadReport reload() {
        return reload(true);
    }

    /** Rescan every source. @param checkAssets false during plugin setup, when the server's asset maps are not loaded yet */
    public synchronized LoadReport reload(boolean checkAssets) {
        Report acc = new Report();
        files.clear();
        for (Source src : sources()) {
            List<Path> paths = new ArrayList<>();
            try {
                if (src.label().isEmpty()) Files.createDirectories(src.root());
                try (Stream<Path> s = Files.walk(src.root())) {
                    s.filter(p -> p.toString().endsWith(".talk")).sorted().forEach(paths::add);
                }
            } catch (IOException e) {
                acc.messages.add("cannot read " + src.root() + ": " + e);
                acc.errors++;
                continue;
            }
            for (Path p : paths) loadOne(src, p, null, checkAssets, acc);
        }
        rebuildIndexes();
        return acc.toReport(byId.size());
    }

    /**
     * Load or replace one file (the Asset Editor saved it). {@code content} may carry the bytes just written so the
     * file need not be re-read. Files starting with "_" are include-only and are not registered.
     */
    public synchronized LoadReport loadFile(@Nonnull Path file, @Nullable String content, boolean checkAssets) {
        Path abs = file.toAbsolutePath().normalize();
        files.remove(abs);
        Report acc = new Report();
        loadOne(sourceFor(abs), abs, content, checkAssets, acc);
        rebuildIndexes();
        return acc.toReport(byId.size());
    }

    /**
     * Load or replace a dialogue that came from a JSON asset (already converted to the model). Validated like a file;
     * an id already taken by a .talk file is reported and the asset is skipped.
     */
    public synchronized LoadReport loadAsset(@Nonnull String id, @Nonnull String display, @Nonnull Dialogue d, boolean checkAssets) {
        assets.remove(id);
        Report acc = new Report();
        acc.files++;
        Validator validator = new Validator(extraCommands, extraFunctions);
        boolean bad = false;
        for (Validator.Problem pr : validator.validate(d)) {
            acc.messages.add(pr.toString());
            if (pr.error()) {
                bad = true;
                acc.errors++;
            } else {
                acc.warnings++;
            }
        }
        if (!bad) {
            if (checkAssets) {
                for (String w : AssetChecks.check(d)) {
                    acc.messages.add(w);
                    acc.warnings++;
                }
            }
            Loaded clash = null;
            for (Loaded other : files.values()) if (other.dialogue().id().equals(id)) clash = other;
            if (clash != null) {
                acc.messages.add("error " + display + ": the .talk file " + clash.display() + " already uses the id '" + id + "'");
                acc.errors++;
            } else {
                assets.put(id, new Loaded(null, display, d));
            }
        } else {
            acc.messages.add("skipped " + display + " because of errors");
        }
        rebuildIndexes();
        return acc.toReport(byId.size());
    }

    /** Forget an asset-sourced dialogue (deleted or renamed). */
    public synchronized boolean removeAsset(@Nonnull String id) {
        boolean removed = assets.remove(id) != null;
        if (removed) rebuildIndexes();
        return removed;
    }

    /** Forget one file (deleted or renamed in the Asset Editor). */
    public synchronized boolean unloadFile(@Nonnull Path file) {
        boolean removed = files.remove(file.toAbsolutePath().normalize()) != null;
        if (removed) rebuildIndexes();
        return removed;
    }

    /** What is loaded from this file, or null when it failed or is include-only. */
    @Nullable
    public synchronized Loaded forFile(@Nonnull Path file) {
        return files.get(file.toAbsolutePath().normalize());
    }

    private void loadOne(Source src, Path file, @Nullable String content, boolean checkAssets, Report acc) {
        Path abs = file.toAbsolutePath().normalize();
        if (abs.getFileName().toString().startsWith("_")) return; // shared nodes, only used through include:
        acc.files++;
        String display = src.display(abs);
        Validator validator = new Validator(extraCommands, extraFunctions);
        try {
            String source = content != null ? content : Files.readString(abs, StandardCharsets.UTF_8);
            Dialogue d = DialogueParser.parse(display, source, resolverFor(src));
            boolean bad = false;
            for (Validator.Problem pr : validator.validate(d)) {
                acc.messages.add(pr.toString());
                if (pr.error()) {
                    bad = true;
                    acc.errors++;
                } else {
                    acc.warnings++;
                }
            }
            if (bad) {
                acc.messages.add("skipped " + display + " because of errors");
                return;
            }
            if (checkAssets) {
                for (String w : AssetChecks.check(d)) {
                    acc.messages.add(w);
                    acc.warnings++;
                }
            }
            for (Loaded other : files.values()) {
                if (other.dialogue().id().equals(d.id())) {
                    acc.messages.add("error " + display + ": another file (" + other.display() + ") already uses the id '" + d.id() + "'");
                    acc.errors++;
                    return;
                }
            }
            files.put(abs, new Loaded(abs, display, d));
        } catch (ParseException e) {
            acc.messages.add("error " + e.getMessage());
            acc.errors++;
        } catch (IOException e) {
            acc.messages.add("error " + display + ": " + e);
            acc.errors++;
        }
    }

    private void rebuildIndexes() {
        Map<String, Dialogue> ids = new LinkedHashMap<>();
        Map<String, List<Dialogue>> roles = new LinkedHashMap<>();
        Map<String, List<Dialogue>> tags = new LinkedHashMap<>();
        List<Loaded> all = new ArrayList<>(files.values());
        for (Loaded a : assets.values()) {
            boolean taken = false;
            for (Loaded f : files.values()) if (f.dialogue().id().equals(a.dialogue().id())) taken = true;
            if (!taken) all.add(a); // a .talk file with the same id wins; the clash was reported when the asset loaded
        }
        for (Loaded l : all) {
            Dialogue d = l.dialogue();
            ids.put(d.id(), d);
            for (String b : d.bindings()) {
                if (b.startsWith("@")) {
                    tags.computeIfAbsent(b.substring(1), k -> new ArrayList<>()).add(d);
                } else {
                    roles.computeIfAbsent(b, k -> new ArrayList<>()).add(d);
                }
            }
        }
        byId = Collections.unmodifiableMap(ids);
        byRole = Collections.unmodifiableMap(roles);
        byTag = Collections.unmodifiableMap(tags);
    }

    private static final class Report {
        int files;
        int errors;
        int warnings;
        final List<String> messages = new ArrayList<>();

        LoadReport toReport(int loaded) {
            return new LoadReport(files, loaded, errors, warnings, List.copyOf(messages));
        }
    }

    /** Run only the asset id checks over everything loaded, once assets are available. */
    public List<String> checkAssets() {
        List<String> out = new ArrayList<>();
        for (Dialogue d : byId.values()) out.addAll(AssetChecks.check(d));
        return out;
    }

    // ---- lookups

    /** Where a loaded dialogue came from: a file (.talk) or an asset (file null), or null if not loaded. */
    @Nullable
    public synchronized Loaded loadedFor(@Nonnull String id) {
        for (Loaded l : files.values()) if (l.dialogue().id().equals(id)) return l;
        return assets.get(id);
    }

    /** The first file with this name in any dialogue source (plugin folder, then the asset packs), or null. */
    @Nullable
    public Path findAssetFile(@Nonnull String fileName) {
        for (Source src : sources()) {
            if (!Files.isDirectory(src.root())) continue;
            try (Stream<Path> walk = Files.walk(src.root())) {
                Path hit = walk.filter(p -> p.getFileName() != null && p.getFileName().toString().equals(fileName)).findFirst().orElse(null);
                if (hit != null) return hit;
            } catch (IOException e) {
                logger.at(Level.WARNING).log("Could not search %s: %s", src.root(), e.getMessage());
            }
        }
        return null;
    }

    /** The asset pack (source label) a file under a pack's dialogue folder belongs to; the server's own folder is "". */
    @Nonnull
    public String packNameFor(@Nonnull Path file) {
        Source src = sourceFor(file.toAbsolutePath().normalize());
        return src == null ? "" : src.label();
    }

    /** Every loaded dialogue, in load order. */
    public List<Dialogue> all() {
        return new ArrayList<>(byId.values());
    }

    @Nullable
    public Dialogue byId(String id) {
        return byId.get(id);
    }

    public List<String> ids() {
        return List.copyOf(byId.keySet());
    }

    public List<Dialogue> forRole(@Nullable String role) {
        if (role == null) return List.of();
        List<Dialogue> l = byRole.get(role);
        return l == null ? List.of() : l;
    }

    public List<Dialogue> forTag(String tag) {
        List<Dialogue> l = byTag.get(tag);
        return l == null ? List.of() : l;
    }

    /** All dialogues bound to an NPC: tag bindings first (more specific), then role bindings. */
    public List<Dialogue> candidates(@Nullable String role, Set<String> tags) {
        List<Dialogue> out = new ArrayList<>();
        for (String t : tags) out.addAll(forTag(t));
        out.addAll(forRole(role));
        return out;
    }
}
