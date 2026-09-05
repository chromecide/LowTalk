package com.chromecide.lowtalk.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A parsed .talk file. */
public record Dialogue(
        String file,
        String id,
        List<String> bindings,
        List<Start> starts,
        String speaker,
        String title,
        String scope,
        Map<String, String> otherDirectives,
        LinkedHashMap<String, Node> nodes
) {
    /** A start directive; condition is null for the unguarded fallback. */
    public record Start(Pos pos, String node, Expr condition) {}

    public Node node(String name) {
        return nodes.get(name);
    }

    public List<Node> nodeList() {
        return List.copyOf(nodes.values());
    }
}
