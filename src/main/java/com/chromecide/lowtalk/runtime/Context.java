package com.chromecide.lowtalk.runtime;

import java.util.List;

/**
 * Everything the interpreter needs from the outside world. The Hytale layer implements this
 * against real players, NPCs, and saved variables; tests use an in-memory fake.
 */
public interface Context {

    /** Current value of a variable, or null if it has never been set. */
    Object getVar(String scope, String name);

    void setVar(String scope, String name, Object value);

    /**
     * Call a function such as has("Food_Bread", 2). Throws {@link RuntimeError} if the function
     * does not exist. Built-ins player() and npc() must always be available.
     */
    Object call(String function, List<Object> args);

    boolean hasVisited(String node);

    /**
     * True if a command has a handler right now. Options whose body uses a command nobody provides are hidden, so a
     * dialogue written against a plugin keeps working, minus that plugin's choices, when the plugin is removed.
     */
    default boolean hasCommand(String name) {
        return true;
    }

    /** Report something the author should know about (logged by the server, ignored in tests). */
    default void warn(String message) {}

    /**
     * Remember whether a variable holds text a player typed at an input prompt.
     *
     * <p>The mark belongs to the variable, not to the conversation that set it. A player types into
     * {@code $player.name} while talking to one NPC and walks away; the text is still theirs when another
     * dialogue reads it tomorrow, and {@code <<run>>} has to keep refusing it. An ordinary {@code <<set>>} by
     * the author clears the mark, because the value is then the author's and not the player's.
     */
    default void markPlayerText(String scope, String name, boolean typed) {}

    /** True when this variable holds text a player typed, whenever and wherever they typed it. */
    default boolean isPlayerText(String scope, String name) {
        return false;
    }

    /**
     * Whether players may be asked to type anything at all.
     *
     * <p>A server that would rather not hold text its players wrote can switch input off, and dialogues that ask
     * for it stop asking. Everything else about them keeps working.
     */
    default boolean allowsPlayerInput() {
        return true;
    }

    void markVisited(String node);

    boolean onceDone(String key);

    void markOnce(String key);
}
