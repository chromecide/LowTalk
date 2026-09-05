package com.chromecide.lowtalk.model;

import java.util.List;

/** Text with {player}, {npc}, and {expression} interpolation already split into parts. */
public record Text(List<Part> parts) {

    public sealed interface Part {
        record Plain(String text) implements Part {}
        record Interp(Expr expr) implements Part {}
        /** [one|of|these]: one alternative is chosen at random each time the text is shown. */
        record Pick(List<Text> choices) implements Part {}
    }

    public static Text plain(String s) {
        return new Text(List.of(new Part.Plain(s)));
    }

    /** True if there is no interpolation at all. */
    public boolean isStatic() {
        return parts.stream().allMatch(p -> p instanceof Part.Plain);
    }

    /** The raw text if static, otherwise a best-effort rendering with placeholders. */
    public String debugString() {
        StringBuilder sb = new StringBuilder();
        for (Part p : parts) {
            if (p instanceof Part.Plain pl) sb.append(pl.text());
            else if (p instanceof Part.Pick pk) sb.append(pk.choices().isEmpty() ? "" : pk.choices().get(0).debugString());
            else sb.append("{...}");
        }
        return sb.toString();
    }
}
