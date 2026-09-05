package com.chromecide.lowtalk.hytale;

import com.chromecide.lowtalk.model.Dialogue;
import com.chromecide.lowtalk.parser.DialogueParser;
import com.chromecide.lowtalk.parser.ParseException;
import com.chromecide.lowtalk.parser.Validator;
import com.hypixel.hytale.logger.HytaleLogger;

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
 * Loads every .talk file from the dialogues folder and answers "which dialogue does this NPC use".
 * Reloading swaps the whole set atomically; sessions already running keep their old tree.
 */
public class DialogueRegistry {

    public record LoadReport(int files, int loaded, int errors, int warnings, List<String> messages) {}

    private static final String[] BUNDLED_EXAMPLES = {"rootling_merchant.talk", "village_elder.talk", "fortune_teller.talk"};

    private final Path folder;
    private final HytaleLogger logger;
    private final Set<String> extraCommands;
    private final Set<String> extraFunctions;

    private volatile Map<String, Dialogue> byId = Map.of();
    private volatile Map<String, List<Dialogue>> byRole = Map.of();
    private volatile Map<String, List<Dialogue>> byTag = Map.of();

    public DialogueRegistry(@Nonnull Path folder, @Nonnull HytaleLogger logger,
                            @Nonnull Set<String> extraCommands, @Nonnull Set<String> extraFunctions) {
        this.folder = folder;
        this.logger = logger;
        this.extraCommands = extraCommands;
        this.extraFunctions = extraFunctions;
    }

    public Path getFolder() {
        return folder;
    }

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

    private static final String[] TEST_DIALOGUES = {
            "test_basics.talk", "test_memory.talk", "test_input.talk", "test_items.talk", "test_feedback.talk",
            "test_body.talk", "test_progress.talk", "test_travel.talk", "test_random.talk", "test_format.talk", "_shared.talk", "test_world.talk", "test_npc.talk"
    };

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
                    Path dev = Path.of("examples", "tests", name);
                    if (Files.exists(dev)) in = Files.newInputStream(dev);
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

    public LoadReport reload() {
        return reload(true);
    }

    /** @param checkAssets false during plugin setup, when the server's asset maps are not loaded yet */
    public LoadReport reload(boolean checkAssets) {
        Map<String, Dialogue> ids = new LinkedHashMap<>();
        Map<String, List<Dialogue>> roles = new LinkedHashMap<>();
        Map<String, List<Dialogue>> tags = new LinkedHashMap<>();
        List<String> messages = new ArrayList<>();
        int files = 0;
        int errors = 0;
        int warnings = 0;
        Validator validator = new Validator(extraCommands, extraFunctions);

        List<Path> paths = new ArrayList<>();
        try {
            Files.createDirectories(folder);
            try (Stream<Path> s = Files.walk(folder)) {
                s.filter(p -> p.toString().endsWith(".talk")).sorted().forEach(paths::add);
            }
        } catch (IOException e) {
            messages.add("cannot read " + folder + ": " + e);
            errors++;
        }

        DialogueParser.IncludeResolver resolver = path -> {
            try {
                Path p = folder.resolve(path).normalize();
                if (!p.startsWith(folder.normalize()) || !Files.isRegularFile(p)) return null;
                return Files.readString(p, StandardCharsets.UTF_8);
            } catch (IOException e) {
                return null;
            }
        };
        for (Path p : paths) {
            if (p.getFileName().toString().startsWith("_")) continue; // shared nodes, only used through include:
            files++;
            String rel = folder.relativize(p).toString();
            try {
                Dialogue d = DialogueParser.parse(rel, Files.readString(p, StandardCharsets.UTF_8), resolver);
                List<Validator.Problem> problems = validator.validate(d);
                boolean bad = false;
                for (Validator.Problem pr : problems) {
                    messages.add(pr.toString());
                    if (pr.error()) {
                        bad = true;
                        errors++;
                    } else {
                        warnings++;
                    }
                }
                if (bad) {
                    messages.add("skipped " + rel + " because of errors");
                    continue;
                }
                if (checkAssets) {
                    for (String w : AssetChecks.check(d)) {
                        messages.add(w);
                        warnings++;
                    }
                }
                if (ids.containsKey(d.id())) {
                    messages.add("error " + rel + ": another file already uses the id '" + d.id() + "'");
                    errors++;
                    continue;
                }
                ids.put(d.id(), d);
                for (String b : d.bindings()) {
                    if (b.startsWith("@")) {
                        tags.computeIfAbsent(b.substring(1), k -> new ArrayList<>()).add(d);
                    } else {
                        roles.computeIfAbsent(b, k -> new ArrayList<>()).add(d);
                    }
                }
            } catch (ParseException e) {
                messages.add("error " + e.getMessage());
                errors++;
            } catch (IOException e) {
                messages.add("error " + rel + ": " + e);
                errors++;
            }
        }

        byId = Collections.unmodifiableMap(ids);
        byRole = Collections.unmodifiableMap(roles);
        byTag = Collections.unmodifiableMap(tags);
        return new LoadReport(files, ids.size(), errors, warnings, messages);
    }

    /** Run only the asset id checks over everything loaded, once assets are available. */
    public List<String> checkAssets() {
        List<String> out = new ArrayList<>();
        for (Dialogue d : byId.values()) out.addAll(AssetChecks.check(d));
        return out;
    }

    @Nullable
    /** Every loaded dialogue, in load order. */
    public List<Dialogue> all() {
        return new ArrayList<>(byId.values());
    }

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
