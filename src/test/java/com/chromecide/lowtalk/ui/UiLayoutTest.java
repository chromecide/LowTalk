package com.chromecide.lowtalk.ui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pre-flight check for the .ui layout files. The client rejects a layout it cannot parse and
 * drops the player during join, so a typo here locks everyone out of the server.
 *
 * The rules are harvested from Hytale's own layouts (src/test/resources/lowtalk/*.txt):
 * element types, enum-like property values, and the @definitions in Common.ui.
 */
class UiLayoutTest {

    private static final Path LAYOUTS = Path.of("src/main/resources/Common/UI/Custom");
    private static final Pattern ELEMENT = Pattern.compile("^\\s*([A-Z][A-Za-z]+)\\s*(#[A-Za-z0-9_]+\\s*)?\\{");
    private static final Pattern PROPERTY = Pattern.compile("\\b([A-Z][A-Za-z]+)\\s*:\\s*([A-Z][A-Za-z]*)\\s*[;,)]");
    private static final Pattern COMMON_REF = Pattern.compile("\\$C\\.@([A-Za-z0-9_]+)");
    private static final Pattern IMPORT = Pattern.compile("^\\s*\\$([A-Za-z]+)\\s*=\\s*\"([^\"]+)\"\\s*;");

    @Test
    void layoutsUseOnlyKnownVocabulary() throws IOException {
        Map<String, Set<String>> vocabulary = loadVocabulary();
        Set<String> elements = loadList("ui-elements.txt");
        Set<String> commonNames = loadList("common-ui-names.txt");
        List<String> problems = new ArrayList<>();

        List<Path> files;
        try (Stream<Path> s = Files.walk(LAYOUTS)) {
            files = s.filter(p -> p.toString().endsWith(".ui")).sorted().toList();
        }
        assertTrue(!files.isEmpty(), "no layout files found under " + LAYOUTS);

        for (Path file : files) {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            int depth = 0;
            for (int i = 0; i < lines.size(); i++) {
                String raw = lines.get(i);
                String line = raw.contains("//") ? raw.substring(0, raw.indexOf("//")) : raw;
                String where = file.getFileName() + ":" + (i + 1);

                Matcher imp = IMPORT.matcher(line);
                if (imp.find()) {
                    String target = imp.group(2);
                    boolean gameFile = target.endsWith("Common.ui") || target.endsWith("Sounds.ui");
                    if (!gameFile && !Files.exists(file.getParent().resolve(target).normalize())) {
                        problems.add(where + ": imports missing file " + target);
                    }
                }

                Matcher el = ELEMENT.matcher(line);
                if (el.find() && !elements.contains(el.group(1))) {
                    problems.add(where + ": unknown element type " + el.group(1));
                }

                Matcher ref = COMMON_REF.matcher(line);
                while (ref.find()) {
                    if (!commonNames.contains(ref.group(1))) {
                        problems.add(where + ": Common.ui has no @" + ref.group(1));
                    }
                }

                Matcher prop = PROPERTY.matcher(line);
                while (prop.find()) {
                    Set<String> allowed = vocabulary.get(prop.group(1));
                    if (allowed != null && !allowed.contains(prop.group(2))) {
                        problems.add(where + ": " + prop.group(1) + " cannot be " + prop.group(2)
                                + " (Hytale uses " + String.join("/", allowed) + ")");
                    }
                }

                for (char c : line.toCharArray()) {
                    if (c == '{') depth++;
                    if (c == '}') depth--;
                    if (depth < 0) problems.add(where + ": unexpected '}'");
                }
            }
            if (depth != 0) problems.add(file.getFileName() + ": unbalanced braces (" + depth + ")");
        }

        assertTrue(problems.isEmpty(), "Layout problems:\n" + String.join("\n", problems));
    }

    private static Map<String, Set<String>> loadVocabulary() throws IOException {
        Map<String, Set<String>> out = new HashMap<>();
        for (String line : Files.readAllLines(Path.of("src/test/resources/lowtalk/ui-vocabulary.txt"))) {
            if (line.startsWith("#") || !line.contains("=")) continue;
            String key = line.substring(0, line.indexOf('='));
            out.put(key, new HashSet<>(List.of(line.substring(line.indexOf('=') + 1).split(","))));
        }
        return out;
    }

    private static Set<String> loadList(String name) throws IOException {
        Set<String> out = new HashSet<>();
        for (String line : Files.readAllLines(Path.of("src/test/resources/lowtalk/" + name))) {
            if (!line.startsWith("#") && !line.isBlank()) out.add(line.trim());
        }
        return out;
    }
}
