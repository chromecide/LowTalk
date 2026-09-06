package com.chromecide.lowtalk.parser;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One-line reference for every built-in command, function and keyword, shown by /lowtalk help. A test keeps it in
 * step with the validator's tables so nothing can be added to one without the other.
 */
public final class Reference {
    public record Entry(String name, String usage, String description) {
        public String line() {
            return usage + "  -  " + description;
        }
    }

    private static final Map<String, Entry> COMMANDS = new LinkedHashMap<>();
    private static final Map<String, Entry> FUNCTIONS = new LinkedHashMap<>();
    private static final Map<String, Entry> KEYWORDS = new LinkedHashMap<>();

    private static void cmd(String name, String args, String description) {
        COMMANDS.put(name, new Entry(name, "<<" + name + (args.isEmpty() ? "" : " " + args) + ">>", description));
    }

    private static void fn(String name, String args, String description) {
        FUNCTIONS.put(name, new Entry(name, name + "(" + args + ")", description));
    }

    private static void kw(String name, String usage, String description) {
        KEYWORDS.put(name, new Entry(name, usage, description));
    }

    static {
        // ---- items and shops
        cmd("give", "Item_Id [count]", "Put items in the player's inventory.");
        cmd("take", "Item_Id [count]", "Remove items; fails the option if the player lacks them, so guard with has().");
        cmd("shop", "[Shop_Id]", "Open this NPC's barter shop (or a named one); ends the conversation.");
        // ---- npc
        cmd("attitude", "friendly", "Set this NPC's attitude toward the player: ignore, hostile, neutral, friendly, revered.");
        cmd("anim", "Id [Slot]", "Play an animation on the NPC; slot Emote by default, Status is what the game uses for greetings.");
        cmd("sound", "Sound_Id", "Play a sound event at the NPC.");
        cmd("npc_name", "\"Name\" | clear", "Rename this NPC (nameplate and display name, kept with the NPC).");
        cmd("state", "State [SubState]", "Put this NPC's role into a state from its role JSON.");
        cmd("despawn", "", "End the conversation and retire this NPC.");
        cmd("spawn", "Role_Id [right up forward]", "Spawn an NPC near the player, two blocks in front by default, facing them.");
        // ---- progress
        cmd("objective", "Id | start Id | cancel Id | line Line_Id | task Task_Id", "Start or abandon an objective, start an objective line, or advance a talk-to-NPC task.");
        cmd("reputation", "+10 [Group_Id]", "Change the player's standing with this NPC's reputation group, or a named group.");
        cmd("learn", "Recipe_Id", "Teach the player a crafting recipe.");
        // ---- feedback
        cmd("notify", "\"Text\" [\"Detail\"] [success|warning|danger]", "A toast notification in the corner of the screen.");
        cmd("title", "\"Primary\" [\"Secondary\"] [major] [seconds]", "A cinematic title across the screen.");
        // ---- body
        cmd("effect", "Effect_Id", "Apply an entity effect such as regeneration or poison.");
        cmd("cure", "Effect_Id", "Remove an entity effect.");
        cmd("heal", "[amount]", "Restore health fully, or by an amount.");
        cmd("stat", "Name +20 | Name 50 | Name max", "Add to, set, or max out any entity stat.");
        // ---- world
        cmd("teleport", "warp_name | x y z", "Move the player; ends the conversation.");
        cmd("weather", "Weather_Id [player] | clear [player]", "Force a weather for the world or just this player; clear returns to the natural sky.");
        cmd("time", "dawn|noon|dusk|midnight|hour [fade seconds] | pause | resume", "Set the time of day, optionally fading, or pause and resume the clock.");
        cmd("run", "\"/command {player}\"", "Run a server command as the console; never include player-typed text.");
        // ---- media
        cmd("music", "Music_Container_Id | clear", "Force a music playlist for this player, or return to the area's music.");
        cmd("vfx", "Particle_System_Id [scale] [seconds]", "Play a particle effect at the NPC (or the player when there is no NPC).");
        cmd("camera", "Camera_Effect_Id [intensity]", "Shake this player's camera with a camera effect, intensity 0 to 1.");

        // ---- functions
        fn("player", "", "The player's name.");
        fn("npc", "", "This NPC's name.");
        fn("has", "\"Item_Id\", count", "True if the player holds at least count of the item (count defaults to 1).");
        fn("count", "\"Item_Id\"", "How many of the item the player holds.");
        fn("visited", "\"node\"", "True if the player has seen that node of this dialogue.");
        fn("objective", "\"Objective_Id\"", "\"none\", \"active\" or \"complete\".");
        fn("objective_line", "\"Line_Id\"", "True if the player can start that objective line now.");
        fn("attitude", "", "This NPC's attitude toward the player, as a string.");
        fn("perm", "\"node.name\"", "True if the player has that permission.");
        fn("hour", "", "In-game hour, 0 to 23.");
        fn("weather", "", "The weather id this player currently sees.");
        fn("random", "n", "A whole number from 0 to n-1.");
        fn("chance", "p", "True with probability p, between 0 and 1.");
        fn("reputation", "[\"Group_Id\"]", "Standing with this NPC's group, or a named group.");
        fn("rank", "[\"Group_Id\"]", "The current reputation rank id, e.g. \"Friendly\".");
        fn("stat", "\"Health\"", "An entity stat's current value.");
        fn("max_stat", "\"Health\"", "An entity stat's maximum.");
        fn("effect", "\"Effect_Id\"", "True if the player has that entity effect.");
        fn("knows", "\"Recipe_Id\"", "True if the player has learned that recipe.");
        fn("ordinal", "n", "\"1st\", \"2nd\", \"3rd\", \"11th\", \"21st\".");
        fn("plural", "n, \"loaf\", \"loaves\"", "The right word for the count; the third argument defaults to adding an s.");
        fn("t", "\"key\"", "A string from the server's language files in the player's language.");

        // ---- keywords (structure, not commands)
        kw("npc:", "npc: Role_Id  or  npc: @tag", "Header: which NPCs use this dialogue. Repeatable.");
        kw("start:", "start: node [when expr]", "Header: where to begin; guarded starts are tried first.");
        kw("speaker:", "speaker: Name", "Header: default speaker for bare lines.");
        kw("title:", "title: Text", "Header: window title.");
        kw("include:", "include: _shared", "Header: pull in nodes from a neighbouring file.");
        kw("on:", "on: join", "Header: open by itself when a player joins.");
        kw("==", "== node_name", "Start a node.");
        kw("->", "-> Option text <<if expr>> <<once>>", "An option; indented lines below it run when chosen.");
        kw("if", "<<if expr>> ... <<elseif expr>> ... <<else>> ... <<endif>>", "Conditional block.");
        kw("once", "<<once>> ... <<endonce>>", "Runs once per player; on an option, hides it after it is picked.");
        kw("random", "<<random>> ... <<or>> ... <<endrandom>>", "Runs one alternative at random.");
        kw("set", "<<set $var = expr>>", "Store a value. Scopes: $x (this NPC), $player.x, $npc.x, $world.x, $tmp.x.");
        kw("jump", "<<jump node>>", "Continue at another node.");
        kw("end", "<<end>>", "Close the window.");
        kw("input", "<<input $var \"Prompt\">>", "Ask the player for text.");
        kw("wait", "<<wait seconds>>", "Pause before the next line, no Continue button.");
        kw("text", "{player} {npc} {$var} {expr ? a : b} [one|of|these]", "Interpolation, inline conditions and random variation inside text.");
    }

    private Reference() {}

    public static List<Entry> commands() {
        return List.copyOf(COMMANDS.values());
    }

    public static List<Entry> functions() {
        return List.copyOf(FUNCTIONS.values());
    }

    public static List<Entry> keywords() {
        return List.copyOf(KEYWORDS.values());
    }

    /** Look a name up in commands, then functions, then keywords. */
    @Nullable
    public static Entry lookup(String name) {
        String n = name.trim();
        if (n.startsWith("<<")) n = n.substring(2);
        if (n.endsWith(">>")) n = n.substring(0, n.length() - 2);
        if (n.endsWith("()")) n = n.substring(0, n.length() - 2);
        Entry e = COMMANDS.get(n);
        if (e == null) e = FUNCTIONS.get(n);
        if (e == null) e = KEYWORDS.get(n);
        if (e == null) e = KEYWORDS.get(n + ":");
        return e;
    }

    public static java.util.Set<String> allNames() {
        java.util.Set<String> out = new java.util.LinkedHashSet<>(COMMANDS.keySet());
        out.addAll(FUNCTIONS.keySet());
        out.addAll(KEYWORDS.keySet());
        return out;
    }
}
