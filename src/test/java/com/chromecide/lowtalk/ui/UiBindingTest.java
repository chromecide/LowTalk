package com.chromecide.lowtalk.ui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pages talk to their layouts by name: {@code cmd.set("#Rows[3] #Name.Text", ...)} reaches into a template and
 * sets a property of one control. Nothing checks that the control is there or that it has that property, and the
 * client does not complain: it drops the connection, which arrives as a crash with no message. That is how a
 * reward row addressed four slots in a two-slot template, and how a command row would have addressed a dropdown
 * that had become a label.
 *
 * <p>So: every {@code #Control.Property} written in the pages must name a control some layout defines, and a
 * property that kind of control actually has.
 */
class UiBindingTest {

    private static final Path LAYOUTS = Path.of("src/main/resources/Common/UI/Custom/Pages/LowTalk");
    private static final Path PAGES = Path.of("src/main/java/com/chromecide/lowtalk/hytale");

    /** {@code Label #Tag {}, $C.@TextField #V0 {}, DropdownBox #Kind {} } */
    private static final Pattern CONTROL =
            Pattern.compile("^[ \\t]*([$A-Za-z][\\w.@]*)[ \\t]+#(\\w+)[ \\t]*\\{", Pattern.MULTILINE);

    /** {@code "#Name.Text"} or {@code sel + " #Name.Text"}, inside a string literal. */
    private static final Pattern USE = Pattern.compile("#(\\w+)(?:\\[[^\\]]*\\])?\\.(\\w+)");

    /**
     * What each kind of control can be told. Anchor and Visible are on everything, so they are not listed per
     * type; the rest is what separates a label from a field from a list.
     */
    private static final Map<String, Set<String>> PROPERTIES = Map.of(
            "Label", Set.of("Text"),
            "Title", Set.of("Text"),
            // Disabled greys a button and stops it raising its event. The game sets it on its own buttons all
            // over (RespawnPage, the pack browser, the trigger-volume inspector), so it is a real property; it
            // only looks new here because the option buttons are addressed as "#Opt" + i and so were never a
            // literal for the scan to find.
            "TextButton", Set.of("Text", "Background", "Disabled"),
            "SecondaryTextButton", Set.of("Text", "Background", "Disabled"),
            "TextField", Set.of("Value", "PlaceholderText"),
            "DropdownBox", Set.of("Value", "Entries"),
            "CheckBoxWithLabel", Set.of("Value", "Text"),
            "Group", Set.of("ScrollChildIndexIntoView", "Background"),
            "SimpleContainer", Set.of("ScrollChildIndexIntoView", "Background"),
            "Bar", Set.of("Background"));

    /** Properties every control has, whatever it is. */
    private static final Set<String> UNIVERSAL = Set.of("Visible", "Anchor");

    /**
     * Names the pages address that no layout of ours declares, because they come from inside one of the game's own
     * macros, which lives in Assets.zip and not in this repository. {@code $C.@CheckBoxWithLabel #ShowTests} draws
     * a {@code #CheckBox} of the game's making, and that is the control the value is set on.
     */
    private static final Set<String> PROVIDED_BY_THE_GAME = Set.of("CheckBox");

    /**
     * layout file name -> control name -> the kind of control it is.
     *
     * <p>Kept per layout on purpose. The same name means different things in different layouts: {@code #Name} is a
     * label in EditCommand and a text field in BindPropPage, and a check that pooled them would accept setting a
     * label's Value because some other layout has a field by that name.
     */
    private static Map<String, Map<String, String>> byLayout() throws IOException {
        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        try (Stream<Path> files = Files.walk(LAYOUTS)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".ui")).toList()) {
                Map<String, String> here = out.computeIfAbsent(p.getFileName().toString(), k -> new LinkedHashMap<>());
                Matcher m = CONTROL.matcher(Files.readString(p, StandardCharsets.UTF_8));
                while (m.find()) here.putIfAbsent(m.group(2), simple(m.group(1)));
            }
        }
        return out;
    }

    /** The .ui file names written in one source file, as "EditCommand.ui". */
    private static Set<String> layoutLiterals(String src) {
        Set<String> out = new LinkedHashSet<>();
        Matcher m = Pattern.compile("\"[\\w/]*?(\\w+\\.ui)\"").matcher(src);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    /**
     * The layouts a page may address: the ones it names itself, plus the ones named by any class it mentions. A
     * page does not always hold its own layout name — DialoguePage appends {@code layout.uiFile()}, and the three
     * bar layouts are listed in DialogueLayout — and a check that only read the page's own file would call every
     * control of those layouts undeclared.
     */
    private static Set<String> layoutsOf(String src, Map<String, String> sourceByClass) {
        Set<String> out = new LinkedHashSet<>(layoutLiterals(src));
        for (Map.Entry<String, String> e : sourceByClass.entrySet()) {
            if (Pattern.compile("\\b" + Pattern.quote(e.getKey()) + "\\b").matcher(src).find()) {
                out.addAll(layoutLiterals(e.getValue()));
            }
        }
        return out;
    }

    /** "$C.@SecondaryTextButton" and "SecondaryTextButton" are the same kind of thing. */
    private static String simple(String type) {
        int at = type.lastIndexOf('@');
        String s = at < 0 ? type : type.substring(at + 1);
        int dot = s.lastIndexOf('.');
        return dot < 0 ? s : s.substring(dot + 1);
    }

    @Test
    void everyControlThePagesAddressIsDeclaredByALayout() throws IOException {
        Map<String, Map<String, String>> layouts = byLayout();
        List<String> unknown = new ArrayList<>();
        for (Use u : uses()) {
            if (PROVIDED_BY_THE_GAME.contains(u.control())) continue;
            boolean found = u.layouts().stream().anyMatch(l -> layouts.getOrDefault(l, Map.of()).containsKey(u.control()));
            if (!found) {
                unknown.add(u + " - none of the layouts it uses " + u.layouts() + " declares #" + u.control());
            }
        }
        assertTrue(unknown.isEmpty(),
                "the client drops the connection when a page addresses a control that is not there:\n  "
                        + String.join("\n  ", unknown));
    }

    @Test
    void everyPropertyThePagesSetBelongsToThatKindOfControl() throws IOException {
        Map<String, Map<String, String>> layouts = byLayout();
        List<String> wrong = new ArrayList<>();
        for (Use u : uses()) {
            if (PROVIDED_BY_THE_GAME.contains(u.control()) || UNIVERSAL.contains(u.property())) continue;
            List<String> offenders = new ArrayList<>();
            boolean declaredSomewhere = false;
            for (String layout : u.layouts()) {
                String type = layouts.getOrDefault(layout, Map.of()).get(u.control());
                if (type == null) continue;
                declaredSomewhere = true;
                Set<String> allowed = PROPERTIES.get(type);
                if (allowed != null && !allowed.contains(u.property())) {
                    offenders.add(type + " in " + layout);
                }
            }
            // every layout of this page that has the control disagrees with the property
            if (declaredSomewhere && offenders.size() == countDeclaring(layouts, u)) {
                wrong.add(u + " - #" + u.control() + " is " + offenders + ", which has no " + u.property());
            }
        }
        assertTrue(wrong.isEmpty(),
                "a property set on the wrong kind of control drops the client with no message:\n  "
                        + String.join("\n  ", wrong));
    }

    private static int countDeclaring(Map<String, Map<String, String>> layouts, Use u) {
        int n = 0;
        for (String layout : u.layouts()) if (layouts.getOrDefault(layout, Map.of()).containsKey(u.control())) n++;
        return n;
    }

    /** The table above is only worth anything if it covers the controls we actually use. */
    @Test
    void everyKindOfControlWeUseIsInTheTable() throws IOException {
        Set<String> types = new LinkedHashSet<>();
        for (Map<String, String> here : byLayout().values()) types.addAll(here.values());
        List<String> missing = types.stream().filter(t -> !PROPERTIES.containsKey(t)).sorted().toList();
        assertTrue(missing.isEmpty(), "add these to PROPERTIES so they are checked rather than waved through: " + missing);
    }

    @Test
    void theScanFindsSomethingToCheck() throws IOException {
        assertFalse(byLayout().isEmpty(), "no layouts found; the layout scan is broken");
        assertTrue(uses().size() > 50, "only " + uses().size() + " bindings found; the source scan is broken");
    }

    /**
     * A page that declares its bindings in a PageLayout writes the control and the property as separate
     * arguments, so the literal scan cannot see it. If the declaration scan ever stops matching, that page
     * silently stops being checked and nothing else here would notice — so check it is still matching.
     */
    @Test
    void theDeclarationScanFindsThePagesBuiltOnPageLayout() throws IOException {
        List<Use> uses = uses();
        assertTrue(uses.stream().anyMatch(u -> u.file().equals("DialoguePage.java") && u.control().equals("NpcTitle")),
                "DialoguePage declares #NpcTitle in its PageLayout but the declaration scan did not find it");
        assertTrue(uses.stream().anyMatch(u -> u.file().equals("DialoguePage.java") && u.control().equals("Opt7")),
                "a pool of 8 should be checked as #Opt0..#Opt7; #Opt7 was not among them");
        assertTrue(uses.stream().anyMatch(u -> u.file().equals("BrowsePage.java") && u.control().equals("EditButton")),
                "a button declared inside a row template was not found");
        assertTrue(uses.stream().anyMatch(u -> u.file().equals("BrowsePage.java") && u.control().equals("ShowTests")),
                "a selector naming more than one control was not split into its parts");
        assertTrue(uses.stream().anyMatch(u -> u.file().equals("EditorLayout.java") && u.control().equals("VB")),
                "the editor's condition rows were not found; EditorLayout holds the most names of anything here");
        assertTrue(uses.stream().anyMatch(u -> u.file().equals("EditorLayout.java") && u.control().equals("B3")),
                "the editor's fourth argument slot was not found");
        long editor = uses.stream().filter(u -> u.file().equals("EditorLayout.java")).count();
        assertTrue(editor > 150, "only " + editor + " editor bindings found; that file declares far more");
    }

    private record Use(String file, String control, String property, Set<String> layouts) {
        @Override
        public String toString() {
            return file + ": #" + control + "." + property;
        }
    }

    private static List<Use> uses() throws IOException {
        Map<String, String> sourceByClass = new LinkedHashMap<>();
        try (Stream<Path> files = Files.walk(PAGES)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String name = p.getFileName().toString();
                sourceByClass.put(name.substring(0, name.length() - ".java".length()),
                        Files.readString(p, StandardCharsets.UTF_8));
            }
        }
        List<Use> out = new ArrayList<>();
        for (Map.Entry<String, String> e : sourceByClass.entrySet()) {
            String src = e.getValue();
            Set<String> layouts = layoutsOf(src, sourceByClass);
            if (layouts.isEmpty()) continue;             // not a page: it addresses no layout at all
            for (String literal : stringLiterals(src)) {
                Matcher m = USE.matcher(literal);
                while (m.find()) out.add(new Use(e.getKey() + ".java", m.group(1), m.group(2), layouts));
            }
            out.addAll(declaredUses(e.getKey() + ".java", src, layouts));
        }
        return out;
    }

    // ---- pages that declare their bindings in a PageLayout --------------------------------------
    //
    // A page built on PageLayout names a control and a property as two separate arguments, so the scan for
    // "#Name.Text" literals above finds nothing in it. These read the declarations instead. This is strictly
    // more than the literal scan could see: a pool is written "#Opt" with a size, so #Opt0..#Opt7 were never
    // literals anywhere and were never checked at all.

    /** {@code .property("title", "#NpcTitle", "Text")}, and box/reads/field, which have the same shape. */
    private static final Pattern DECLARED_TARGET =
            Pattern.compile("\\.(?:property|box|reads|field)\\(\\s*\\x22[^\\x22]*\\x22\\s*,\\s*\\x22([^\\x22]*#[^\\x22]*)\\x22\\s*,\\s*\\x22(\\w+)\\x22\\s*\\)");

    /**
     * {@code .container("transcript", "#Transcript")}, {@code .command("CLOSE", "#CloseButton", ...)} and the
     * row-level forms of the same. A control that only raises a command still has to be there.
     */
    private static final Pattern DECLARED_CONTROL =
            Pattern.compile("\\.(?:container|command|rowCommand|rowButton)\\(\\s*\\x22[^\\x22]*\\x22\\s*,\\s*\\x22([^\\x22]*#[^\\x22]*)\\x22");

    /** {@code .revealsLatestWith("ScrollChildIndexIntoView")} applies to the container just opened. */
    private static final Pattern DECLARED_REVEAL =
            Pattern.compile("\\.container\\(\\s*\\x22[^\\x22]*\\x22\\s*,\\s*\\x22(#\\w+)\\x22[\\s\\S]*?\\.revealsLatestWith\\(\\s*\\x22(\\w+)\\x22");

    /** {@code .pool("options", "#Opt", 8) ... .done()} and its fields. */
    private static final Pattern DECLARED_POOL =
            Pattern.compile("\\.pool\\(\\s*\\x22[^\\x22]*\\x22\\s*,\\s*\\x22(#\\w+)\\x22\\s*,\\s*(\\w+)\\s*\\)([\\s\\S]*?)\\.done\\(\\)");

    /** {@code .poolCommand("CHOOSE", "#Opt", 8, ...)}. */
    private static final Pattern DECLARED_POOL_COMMAND =
            Pattern.compile("\\.poolCommand\\(\\s*\\x22[^\\x22]*\\x22\\s*,\\s*\\x22(#\\w+)\\x22\\s*,\\s*(\\w+)");

    /** A field inside a pool block: the selector may be empty, meaning the pooled control itself. */
    private static final Pattern POOL_FIELD =
            Pattern.compile("\\.field\\(\\s*\\x22[^\\x22]*\\x22\\s*,\\s*\\x22(#?\\w*)\\x22\\s*,\\s*\\x22(\\w+)\\x22\\s*\\)");

    private static final Pattern POOL_HIDDEN = Pattern.compile("\\.hiddenWith\\(\\s*\\x22(\\w+)\\x22");


    /**
     * A pool size is usually written as the page's own constant rather than a number, so resolve it from
     * the declaration in the same file. A size that cannot be resolved is a broken scan, not an empty pool.
     */
    private static int sizeOf(String src, String token) {
        if (token.matches("\\d+")) return Integer.parseInt(token);
        Matcher m = Pattern.compile("\\b" + Pattern.quote(token) + "\\s*=\\s*(\\d+)").matcher(src);
        if (m.find()) return Integer.parseInt(m.group(1));
        throw new IllegalStateException("pool size '" + token + "' is not a number and has no constant in this file");
    }


    /**
     * A selector may walk down more than one control — "#ShowTests #CheckBox" sets the value of a check box
     * inside a labelled group. The property belongs to the last control named; the ones before it only have
     * to exist, so they are recorded against Visible, which every control has.
     */
    private static void addSelector(List<Use> out, String file, String selector, String property, Set<String> layouts) {
        Matcher controls = Pattern.compile("#(\\w+)").matcher(selector);
        List<String> names = new ArrayList<>();
        while (controls.find()) names.add(controls.group(1));
        for (int i = 0; i < names.size(); i++) {
            out.add(new Use(file, names.get(i), i == names.size() - 1 ? property : "Visible", layouts));
        }
    }

    private static List<Use> declaredUses(String file, String rawSrc, Set<String> layouts) {
        String src = stripComments(rawSrc);
        List<Use> out = new ArrayList<>();

        Matcher m = DECLARED_TARGET.matcher(src);
        while (m.find()) addSelector(out, file, m.group(1), m.group(2), layouts);

        // A control that only raises a command, or holds a list, still has to exist. Visible is universal, so
        // naming it here checks the control is declared without claiming anything about its kind.
        m = DECLARED_CONTROL.matcher(src);
        while (m.find()) addSelector(out, file, m.group(1), "Visible", layouts);

        m = DECLARED_REVEAL.matcher(src);
        while (m.find()) addSelector(out, file, m.group(1), m.group(2), layouts);

        m = DECLARED_POOL.matcher(src);
        while (m.find()) {
            String prefix = m.group(1).substring(1);
            int size = sizeOf(src, m.group(2));
            String body = m.group(3);
            Matcher hidden = POOL_HIDDEN.matcher(body);
            String visible = hidden.find() ? hidden.group(1) : "Visible";
            for (int i = 0; i < size; i++) {
                out.add(new Use(file, prefix + i, visible, layouts));
                Matcher f = POOL_FIELD.matcher(body);
                while (f.find()) {
                    String selector = f.group(1);
                    String control = selector.isEmpty() ? prefix + i : selector.substring(1);
                    out.add(new Use(file, control, f.group(2), layouts));
                }
            }
        }

        m = DECLARED_POOL_COMMAND.matcher(src);
        while (m.find()) {
            String prefix = m.group(1).substring(1);
            int size = sizeOf(src, m.group(2));
            for (int i = 0; i < size; i++) out.add(new Use(file, prefix + i, "Visible", layouts));
        }
        return out;
    }

    /** String literals only: a "#Foo.Bar" in a comment is not a binding. */
    private static List<String> stringLiterals(String src) {
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("\"((?:\\\\.|[^\"\\\\])*)\"").matcher(stripComments(src));
        while (m.find()) out.add(m.group(1));
        return out;
    }

    private static String stripComments(String src) {
        return src.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)^[ \\t]*//.*$", " ");
    }
}
