package com.chromecide.lowtalk.parser;

import com.chromecide.lowtalk.model.Dialogue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Command line validator: ./gradlew validate --args="examples" */
public final class ValidateMain {

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.err.println("usage: validate <file.talk | directory> ...");
            System.exit(2);
        }
        List<Path> files = new ArrayList<>();
        for (String a : args) {
            Path p = Path.of(a);
            if (Files.isDirectory(p)) {
                try (Stream<Path> s = Files.walk(p)) {
                    s.filter(f -> f.toString().endsWith(".talk")).sorted().forEach(files::add);
                }
            } else {
                files.add(p);
            }
        }
        int errors = 0;
        int warnings = 0;
        Validator validator = new Validator();
        DialogueParser.IncludeResolver resolver = path -> {
            try {
                Path p = Path.of(path);
                return Files.isRegularFile(p) ? Files.readString(p, StandardCharsets.UTF_8) : null;
            } catch (IOException e) {
                return null;
            }
        };
        for (Path f : files) {
            if (f.getFileName().toString().startsWith("_")) {
                System.out.println("include " + f + "  (starts with _, only used through include:)");
                continue;
            }
            String src = Files.readString(f, StandardCharsets.UTF_8);
            try {
                Dialogue d = DialogueParser.parse(f.toString(), src, resolver);
                List<Validator.Problem> problems = validator.validate(d);
                for (Validator.Problem p : problems) {
                    System.out.println(p);
                    if (p.error()) errors++;
                    else warnings++;
                }
                System.out.println((problems.isEmpty() ? "ok      " : "checked ") + f + "  (" + d.nodes().size() + " nodes, " + d.bindings().size() + " bindings)");
            } catch (ParseException e) {
                System.out.println("error " + e.getMessage());
                errors++;
            }
        }
        System.out.println(files.size() + " file(s), " + errors + " error(s), " + warnings + " warning(s)");
        System.exit(errors == 0 ? 0 : 1);
    }
}
