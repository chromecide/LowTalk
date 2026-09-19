package com.chromecide.lowtalk.editor;

import com.chromecide.lowtalk.parser.Validator;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Everything the in-game editor can add to a passage, in the order and the words the menu shows it in.
 *
 * <p>A creator asks "how do I make it rain?", not "which command is it?". So every built-in command has its own
 * entry here, named for what it does rather than what it is called: the command's own name is an implementation
 * detail that belongs in the row it adds, not in the menu that offers it. Nothing is allowed to hide behind a
 * general "any other command" entry, which is a dead end for anyone who does not already know the answer; that
 * entry survives only for commands other mods provide, which cannot be named here ahead of time.
 *
 * <p>{@code AddMenuTest} fails the build if a built-in command is missing from this table, so a command added
 * later cannot quietly become undiscoverable.
 */
public final class AddMenu {

    /** How the entries are blocked up. The word is shown, so it is part of what the creator reads. */
    public static final List<String> GROUPS =
            List.of("Say", "Ask", "Items", "NPC", "Player", "World", "Screen", "Progress", "Flow", "Advanced");

    /** Separates the group from what the entry does. */
    public static final String SEPARATOR = " · ";

    /**
     * One entry of the menu.
     *
     * @param group   the block it belongs to, one of {@link #GROUPS}
     * @param text    what it does, in the creator's words
     * @param kind    the statement it adds
     * @param command the built-in command it adds, when {@code kind} is {@code COMMAND}; null for the entry that
     *                offers whatever commands other mods have registered
     * @param tooltip a longer note, or null to take the command's own description from the reference
     */
    public record Item(String group, String text, DialogueDraft.Kind kind, @Nullable String command,
                       @Nullable String tooltip) {

        /** The label as the dropdown shows it: "World · Make it rain or clear the sky". */
        public String label() {
            return group + SEPARATOR + text;
        }

        /** The value the page sends back when this entry is chosen. */
        public String value() {
            return command == null ? kind.name() : kind.name() + ":" + command;
        }
    }

    private static final List<Item> ITEMS = List.of(
            say("A line the NPC says", DialogueDraft.Kind.LINE,
                    "A line the NPC says. Leave the speaker empty and it is the NPC's own voice."),
            say("An option the player can pick", DialogueDraft.Kind.OPTION,
                    "A choice in the list at the bottom. Edit what happens when it is picked."),

            new Item("Ask", "Ask the player to type something", DialogueDraft.Kind.INPUT, null,
                    "Opens a text box and stores what they type in a variable."),

            command("Items", "Give the player an item", "give"),
            command("Items", "Take an item away", "take"),
            command("Items", "Open this NPC's shop", "shop"),
            command("Items", "Teach the player a recipe", "learn"),

            command("NPC", "Play an animation", "anim"),
            command("NPC", "Play a sound", "sound"),
            command("NPC", "Change how this NPC feels about the player", "attitude"),
            command("NPC", "Make this NPC stop fighting", "calm"),
            command("NPC", "Rename this NPC", "npc_name"),
            command("NPC", "Put this NPC into a role state", "state"),
            command("NPC", "Spawn another NPC", "spawn"),
            command("NPC", "Retire this NPC", "despawn"),

            command("Player", "Heal the player", "heal"),
            command("Player", "Apply an effect", "effect"),
            command("Player", "Remove an effect", "cure"),
            command("Player", "Change a stat", "stat"),
            command("Player", "Teleport the player", "teleport"),

            command("World", "Make it rain or clear the sky", "weather"),
            command("World", "Set the time of day", "time"),

            command("Screen", "Show a title across the screen", "title"),
            command("Screen", "Show a notification in the corner", "notify"),
            command("Screen", "Play a particle effect", "vfx"),
            command("Screen", "Shake the camera", "camera"),
            command("Screen", "Play music", "music"),

            command("Progress", "Start an objective", "objective"),
            command("Progress", "Change the player's standing", "reputation"),
            new Item("Progress", "Set a variable", DialogueDraft.Kind.SET, null,
                    "Remember something about this player, such as $met = true."),

            new Item("Flow", "If / else block", DialogueDraft.Kind.IF, null,
                    "Show different lines depending on a variable, an item, an objective or the time."),
            new Item("Flow", "Once block (first time only)", DialogueDraft.Kind.ONCE, null,
                    "Runs the first time this player reaches it and never again."),
            new Item("Flow", "Random block (one of several)", DialogueDraft.Kind.RANDOM, null,
                    "Picks one of its alternatives each time, so an NPC does not repeat itself."),
            new Item("Flow", "Wait a few seconds", DialogueDraft.Kind.WAIT, null,
                    "Pauses before the next line, with no Continue button."),
            new Item("Flow", "Jump to a passage", DialogueDraft.Kind.JUMP, null,
                    "Continue at another passage of this dialogue."),
            new Item("Flow", "End the conversation", DialogueDraft.Kind.END, null,
                    "Closes the window."),

            command("Advanced", "Run a server command", "run"),
            new Item("Advanced", "A command from another mod...", DialogueDraft.Kind.COMMAND, null,
                    "Commands registered by other plugins on this server, with their arguments named."));

    private AddMenu() {}

    /** The menu, in the order it is shown. */
    public static List<Item> items() {
        return ITEMS;
    }

    /** The built-in commands this menu offers an entry for. */
    public static Set<String> commands() {
        Set<String> out = new LinkedHashSet<>();
        for (Item i : ITEMS) if (i.command() != null) out.add(i.command());
        return out;
    }

    /** Built-in commands with no entry of their own. Empty, and the test keeps it that way. */
    public static List<String> missingCommands() {
        List<String> out = new ArrayList<>(Validator.BUILTIN_COMMANDS.keySet());
        out.removeAll(commands());
        java.util.Collections.sort(out);
        return out;
    }

    private static Item say(String text, DialogueDraft.Kind kind, String tooltip) {
        return new Item("Say", text, kind, null, tooltip);
    }

    private static Item command(String group, String text, String name) {
        return new Item(group, text, DialogueDraft.Kind.COMMAND, name, null);
    }
}
