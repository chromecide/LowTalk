package com.chromecide.lowtalk.editor;

import com.chromecide.lowtalk.model.Statement;
import com.chromecide.lowtalk.parser.Printer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What each command's arguments mean, so the in-game editor can show them as named fields instead of one box of
 * text. A creator who picks "vfx" sees a particle, a scale and a number of seconds, not "Cinematic_Pink_Smoke 1".
 *
 * <p>Everything here is about presentation: the .talk text is still the truth, and a command whose arguments do not
 * fit its spec (more of them than the spec names, or a command another plugin added) falls back to the plain text
 * box. Nothing in this class talks to the server; the data set ids are just names the editor asks the game for.
 */
public final class CommandSpecs {

    /** How one argument is edited. */
    public enum Type {
        /** Free text, quoted when it needs to be. */
        TEXT,
        /** A number; still free text, because a dialogue may interpolate one. */
        NUMBER,
        /** One of a short fixed list, shown as a dropdown on the row. */
        CHOICE,
        /** An id from one of the game's lists, typed with a filtered picker beside it. */
        ASSET
    }

    /**
     * One argument of one command.
     *
     * @param label    the word shown to the left of the field, short enough for a narrow column
     * @param type     how it is edited
     * @param dataset  for {@link Type#ASSET}, the data set the picker reads
     * @param choices  for {@link Type#CHOICE}, the values offered; the first is not a default, just a list
     * @param optional true when leaving it empty is fine
     */
    public record Arg(String label, Type type, @Nullable String dataset, List<String> choices, boolean optional) {}

    public record Spec(String command, List<Arg> args) {
        public int size() {
            return args.size();
        }

        public Arg arg(int i) {
            return args.get(i);
        }
    }

    // Data set ids. These are the constants JsonDialogues registers; they are repeated as plain strings so that this
    // class, and its tests, never need the server on the class path.
    public static final String NPCS = "LowTalkNpcs";
    public static final String WEATHERS = "LowTalkWeathers";
    public static final String ROLES = "LowTalkRoles";
    public static final String ANIMATIONS = "LowTalkAnimations";
    public static final String STATS = "LowTalkStats";
    public static final String RECIPES = "LowTalkRecipes";
    public static final String WARPS = "LowTalkWarps";
    public static final String TIMES = "LowTalkTimes";
    public static final String REPUTATION_GROUPS = "LowTalkReputationGroups";
    public static final String SHOPS = "LowTalkShops";
    public static final String MUSIC = "LowTalkMusic";
    public static final String PARTICLES = "LowTalkParticles";
    public static final String CAMERA_EFFECTS = "LowTalkCameraEffects";
    public static final String ITEMS = "LowTalkItems";
    public static final String SOUNDS = "LowTalkSounds";
    public static final String ENTITY_EFFECTS = "LowTalkEntityEffects";
    public static final String OBJECTIVES = "LowTalkObjectives";
    public static final String OBJECTIVE_LINES = "LowTalkObjectiveLines";

    private static final List<String> ATTITUDES = List.of("ignore", "hostile", "neutral", "friendly", "revered");
    private static final List<String> ANIMATION_SLOTS = List.of("Emote", "Status", "Action", "Movement", "Face", "ServerAction");
    private static final List<String> NOTIFY_STYLES = List.of("default", "success", "warning", "danger");
    private static final List<String> TITLE_SIZES = List.of("minor", "major");
    private static final List<String> OBJECTIVE_VERBS = List.of("start", "cancel", "line", "task");

    private static final Map<String, Spec> SPECS = new java.util.concurrent.ConcurrentHashMap<>();

    private static void spec(String command, Arg... args) {
        SPECS.put(command, new Spec(command, List.of(args)));
    }

    private static Arg text(String label) {
        return new Arg(label, Type.TEXT, null, List.of(), false);
    }

    private static Arg optionalText(String label) {
        return new Arg(label, Type.TEXT, null, List.of(), true);
    }

    private static Arg number(String label) {
        return new Arg(label, Type.NUMBER, null, List.of(), false);
    }

    private static Arg optionalNumber(String label) {
        return new Arg(label, Type.NUMBER, null, List.of(), true);
    }

    private static Arg asset(String label, String dataset) {
        return new Arg(label, Type.ASSET, dataset, List.of(), false);
    }

    private static Arg optionalAsset(String label, String dataset) {
        return new Arg(label, Type.ASSET, dataset, List.of(), true);
    }

    private static Arg choice(String label, List<String> choices) {
        return new Arg(label, Type.CHOICE, null, choices, false);
    }

    private static Arg optionalChoice(String label, List<String> choices) {
        return new Arg(label, Type.CHOICE, null, choices, true);
    }

    static {
        // ---- items and shops
        spec("give", asset("item", ITEMS), optionalNumber("count"));
        spec("take", asset("item", ITEMS), optionalNumber("count"));
        spec("shop", optionalAsset("shop", SHOPS));
        // ---- the npc
        spec("attitude", choice("attitude", ATTITUDES));
        spec("anim", asset("animation", ANIMATIONS), optionalChoice("slot", ANIMATION_SLOTS));
        spec("sound", asset("sound", SOUNDS));
        spec("npc_name", text("name"));
        spec("state", text("state"), optionalText("sub-state"));
        spec("despawn");
        spec("spawn", asset("role", ROLES), optionalNumber("right"), optionalNumber("up"), optionalNumber("forward"));
        // ---- progress
        spec("objective", choice("do", OBJECTIVE_VERBS), asset("objective", OBJECTIVES));
        spec("reputation", number("by"), optionalAsset("group", REPUTATION_GROUPS));
        spec("learn", asset("recipe", RECIPES));
        // ---- feedback
        spec("notify", text("text"), optionalText("detail"), optionalChoice("style", NOTIFY_STYLES));
        spec("title", text("text"), optionalText("under it"), optionalChoice("size", TITLE_SIZES), optionalNumber("seconds"));
        // ---- the player's body
        spec("effect", asset("effect", ENTITY_EFFECTS));
        spec("cure", asset("effect", ENTITY_EFFECTS));
        spec("heal", optionalNumber("amount"));
        spec("stat", asset("stat", STATS), text("change"));
        // ---- the world
        spec("teleport", asset("warp", WARPS));
        spec("weather", asset("weather", WEATHERS), optionalChoice("for", List.of("player")));
        spec("time", asset("time", TIMES));
        spec("run", text("command"));
        // ---- media
        spec("music", asset("music", MUSIC));
        spec("vfx", asset("particle", PARTICLES), optionalNumber("scale"), optionalNumber("seconds"));
        spec("camera", asset("effect", CAMERA_EFFECTS), optionalNumber("strength"));
    }

    private CommandSpecs() {}

    /**
     * Give another plugin's command named fields in the in-game editor. Each label is the word shown beside the
     * field, optionally followed by a colon and the id of a data set to pick the value from, e.g.
     * {@code register("bounty", "target:MyModTargets", "reward")}. A label ending in "?" is optional.
     */
    public static void register(String command, String... labels) {
        List<Arg> args = new ArrayList<>();
        for (String raw : labels) {
            String label = raw == null ? "" : raw.trim();
            int colon = label.indexOf(':');
            String dataset = colon < 0 ? null : label.substring(colon + 1).trim();
            if (colon >= 0) label = label.substring(0, colon).trim();
            boolean optional = label.endsWith("?");
            if (optional) label = label.substring(0, label.length() - 1).trim();
            args.add(new Arg(label, dataset == null || dataset.isEmpty() ? Type.TEXT : Type.ASSET,
                    dataset == null || dataset.isEmpty() ? null : dataset, List.of(), optional));
        }
        SPECS.put(command, new Spec(command, List.copyOf(args)));
    }

    /** The spec for a command, or null when it has none and the editor should show plain text. */
    @Nullable
    public static Spec of(@Nullable String command) {
        return command == null ? null : SPECS.get(command);
    }

    /** The first argument that is picked from one of the game's lists, or -1. At most one per command. */
    public static int assetSlot(@Nullable Spec spec) {
        if (spec == null) return -1;
        for (int i = 0; i < spec.size(); i++) if (spec.arg(i).type() == Type.ASSET) return i;
        return -1;
    }

    /**
     * Which list feeds the picker for an argument. Only {@code objective} varies: what may follow it depends on
     * whether the author is starting an objective, an objective line, or advancing a task.
     */
    @Nullable
    public static String datasetFor(@Nullable Spec spec, int index, List<String> values) {
        if (spec == null || index < 0 || index >= spec.size()) return null;
        Arg a = spec.arg(index);
        if (a.type() != Type.ASSET) return null;
        if ("objective".equals(spec.command()) && index == 1) {
            String verb = values.isEmpty() ? "" : values.get(0).trim().toLowerCase(Locale.ROOT);
            return switch (verb) {
                case "line" -> OBJECTIVE_LINES;
                case "task" -> null; // task ids live inside an objective, so the game has no list of them
                default -> OBJECTIVES;
            };
        }
        return a.dataset();
    }

    /**
     * A command's arguments as one editable value per named argument, or null when they do not fit the spec and the
     * editor should fall back to the text box. Shorter argument lists are padded with empties, so an author always
     * sees every argument a command takes, including the ones they have not filled in yet.
     */
    @Nullable
    public static List<String> values(Statement.Command c) {
        Spec spec = of(c.name());
        if (spec == null) return null;
        List<String> raw = new ArrayList<>();
        for (var a : c.args()) raw.add(Printer.text(a));
        List<String> normalised = normalise(spec, raw);
        if (normalised == null || normalised.size() > spec.size()) return null;
        List<String> out = new ArrayList<>(normalised);
        while (out.size() < spec.size()) out.add("");
        return out;
    }

    /**
     * Put arguments the runtime accepts in any order into the slots the editor shows them in. The commands that do
     * this are the ones whose trailing words are recognised by shape: a notify style, a title's size and seconds,
     * and the verb an objective starts with. Returns null when the arguments cannot be laid out at all.
     */
    @Nullable
    private static List<String> normalise(Spec spec, List<String> raw) {
        switch (spec.command()) {
            case "notify" -> {
                if (raw.isEmpty()) return raw;
                String detail = "";
                String style = "";
                for (String a : raw.subList(1, raw.size())) {
                    if (NOTIFY_STYLES.contains(a.trim().toLowerCase(Locale.ROOT))) style = a.trim().toLowerCase(Locale.ROOT);
                    else if (detail.isEmpty()) detail = a;
                    else return null;
                }
                return List.of(raw.get(0), detail, style);
            }
            case "title" -> {
                if (raw.isEmpty()) return raw;
                String under = "";
                String size = "";
                String seconds = "";
                for (String a : raw.subList(1, raw.size())) {
                    String t = a.trim();
                    if (TITLE_SIZES.contains(t.toLowerCase(Locale.ROOT))) size = t.toLowerCase(Locale.ROOT);
                    else if (t.matches("\\d+(\\.\\d+)?")) seconds = t;
                    else if (under.isEmpty()) under = a;
                    else return null;
                }
                return List.of(raw.get(0), under, size, seconds);
            }
            case "objective" -> {
                if (raw.size() != 1) return raw;
                String only = raw.get(0).trim();
                // <<objective Some_Id>> means start it; <<objective start>> is half-written.
                return OBJECTIVE_VERBS.contains(only.toLowerCase(Locale.ROOT))
                        ? List.of(only.toLowerCase(Locale.ROOT), "")
                        : List.of("start", raw.get(0));
            }
            default -> {
                return raw;
            }
        }
    }

    /**
     * The .talk text for a set of argument values: quoted where it has to be, with empty arguments at the end
     * dropped so a half-written command stays short, and empty ones in the middle kept so the rest keep their place.
     */
    public static String join(Spec spec, List<String> values) {
        int last = -1;
        for (int i = 0; i < values.size(); i++) if (!values.get(i).isBlank()) last = i;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i <= last; i++) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(Printer.arg(values.get(i).trim()));
        }
        return sb.toString();
    }

    /** The arguments a freshly added command starts with, as .talk text. */
    public static String defaults(String command) {
        return switch (command) {
            case "give", "take" -> "Food_Bread 1";
            case "objective" -> "start";
            case "attitude" -> "friendly";
            case "heal" -> "";
            default -> "";
        };
    }
}
