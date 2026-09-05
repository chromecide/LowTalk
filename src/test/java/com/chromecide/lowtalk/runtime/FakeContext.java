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

    public FakeContext() {
        functions.put("player", a -> playerName);
        functions.put("npc", a -> npcName);
    }

    @Override
    public Object getVar(String scope, String name) {
        return vars.get(scope + "." + name);
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
