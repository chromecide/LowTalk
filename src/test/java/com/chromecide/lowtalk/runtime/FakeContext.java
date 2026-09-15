package com.chromecide.lowtalk.runtime;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** In-memory Context for tests. */
public class FakeContext implements Context {

    public final Map<String, Object> vars = new HashMap<>();
    public final Set<String> visited = new HashSet<>();
    public final Set<String> once = new HashSet<>();
    public final Map<String, Function<List<Object>, Object>> functions = new HashMap<>();
    public String playerName = "Chromecide";
    public String npcName = "Rootling Merchant";

    /** What random(n) returns in tests; set it to steer <<random>> and [a|b] picks. */
    public int nextRandom = 0;
    public FakeContext() {
        functions.put("player", a -> playerName);
        functions.put("npc", a -> npcName);
        functions.put("random", a -> (double) nextRandom);
    }

    @Override
    public Object getVar(String scope, String name) {
        return vars.get(scope + "." + name);
    }

    /** Which variables hold text a player typed, kept the way the real context keeps it: against the variable. */
    public final java.util.Set<String> playerText = new java.util.HashSet<>();

    /** Set false to stand in for a server that has switched input off. */
    public boolean allowInput = true;

    @Override
    public boolean allowsPlayerInput() {
        return allowInput;
    }

    @Override
    public void markPlayerText(String scope, String name, boolean typed) {
        if (typed) playerText.add(scope + "." + name);
        else playerText.remove(scope + "." + name);
    }

    @Override
    public boolean isPlayerText(String scope, String name) {
        return playerText.contains(scope + "." + name);
    }

    @Override
    public void setVar(String scope, String name, Object value) {
        vars.put(scope + "." + name, value);
    }

    @Override
    public Object call(String function, List<Object> args) {
        Function<List<Object>, Object> f = functions.get(function);
        if (f == null) throw new RuntimeError("unknown function " + function + "()");
        return f.apply(args);
    }

    @Override
    public boolean hasVisited(String node) {
        return visited.contains(node);
    }

    @Override
    public void markVisited(String node) {
        visited.add(node);
    }

    @Override
    public boolean onceDone(String key) {
        return once.contains(key);
    }

    @Override
    public void markOnce(String key) {
        once.add(key);
    }
}
