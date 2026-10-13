package com.chromecide.lowtalk;

import com.hypixel.hytale.codec.util.Documentation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every {@code .documentation(...)} we register has to survive being turned into schema text.
 *
 * <p>The server strips a small amount of markdown out of these to produce the plain description the Asset
 * Editor and the Node Editor show. On the release line that stripper <b>throws</b> on an emphasis marker
 * that opens and never closes — one unmatched {@code *} in one help string, and schema generation fails.
 * It is not hypothetical: 0.7.0-pre.5.1 was released to fix exactly that, caused by a single character in
 * one of the game's own assets.
 *
 * <p>The failure surfaces in the editor rather than anywhere near the typo, and nothing in a normal build
 * goes near this code, so it is worth one test. This calls the server's own stripper rather than guessing
 * at its rules, so it keeps telling the truth as those rules change. The test classpath resolves the
 * release-line server, which is the strictest reader of these strings and so the one worth passing.
 */
class DocumentationStringsTest {

    private static final Path SOURCES = Path.of("src/main/java");

    /** {@code .documentation("...")}, honouring escaped quotes. Ours are single literals, never joined. */
    private static final Pattern DOCUMENTATION =
            Pattern.compile("\\.documentation\\(\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

    private record Doc(String file, String text) {}

    private static List<Doc> documentationStrings() throws IOException {
        List<Doc> out = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SOURCES)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                Matcher m = DOCUMENTATION.matcher(Files.readString(p, StandardCharsets.UTF_8));
                while (m.find()) out.add(new Doc(p.getFileName().toString(), unescape(m.group(1))));
            }
        }
        return out;
    }

    /** The literal as the compiler would see it; only the escapes we actually write are handled. */
    private static String unescape(String literal) {
        return literal.replace("\\\"", "\"").replace("\\n", "\n").replace("\\t", "\t").replace("\\\\", "\\");
    }

    @Test
    void everyDocumentationStringSurvivesTheSchemaStripper() throws IOException {
        List<String> broken = new ArrayList<>();
        for (Doc doc : documentationStrings()) {
            try {
                Documentation.stripMarkdown(doc.text());
            } catch (RuntimeException e) {
                broken.add(doc.file() + ": " + e.getMessage() + "\n      " + doc.text());
            }
        }
        assertTrue(broken.isEmpty(),
                "these would break schema generation, and the error would appear in the editor rather than here."
                        + " An emphasis marker with a space before it opens and has to be closed:"
                        + " \"*Attribute\" throws, \"*Attribute*\" does not:\n  "
                        + String.join("\n  ", broken));
    }

    /** The check above is worth nothing if the scan stops finding anything, so say what it found. */
    @Test
    void theScanFindsTheDocumentationStrings() throws IOException {
        int found = documentationStrings().size();
        assertTrue(found > 90, "only " + found + " documentation strings found; the scan is broken");
    }
}
