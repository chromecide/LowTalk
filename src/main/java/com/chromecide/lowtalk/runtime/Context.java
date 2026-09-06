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

    void markVisited(String node);

    boolean onceDone(String key);

    void markOnce(String key);
}
