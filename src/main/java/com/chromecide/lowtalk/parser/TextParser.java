package com.chromecide.lowtalk.parser;

import com.chromecide.lowtalk.model.Expr;
import com.chromecide.lowtalk.model.Pos;
import com.chromecide.lowtalk.model.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits spoken text into plain parts and {interpolations}.
 * {player} and {npc} become calls to the player() and npc() functions; anything else is an expression.
 * Use {{ and }} for literal braces.
 */
public final class TextParser {

    private TextParser() {}

    public static Text parse(String raw, Pos pos) {
        List<Text.Part> parts = new ArrayList<>();
        StringBuilder plain = new StringBuilder();
        int i = 0;
        int n = raw.length();
        while (i < n) {
            char c = raw.charAt(i);
            if (c == '{') {
                if (i + 1 < n && raw.charAt(i + 1) == '{') {
                    plain.append('{');
                    i += 2;
                    continue;
                }
                int close = raw.indexOf('}', i + 1);
                if (close < 0) {
                    throw new ParseException(pos, "unclosed '{' in text: " + raw);
                }
                String inner = raw.substring(i + 1, close).trim();
                if (inner.isEmpty()) {
                    throw new ParseException(pos, "empty {} in text");
                }
                if (!plain.isEmpty()) {
                    parts.add(new Text.Part.Plain(plain.toString()));
                    plain.setLength(0);
                }
                Expr expr = switch (inner) {
                    case "player" -> new Expr.Call("player", List.of());
                    case "npc" -> new Expr.Call("npc", List.of());
                    default -> ExprParser.parse(inner, pos);
                };
                parts.add(new Text.Part.Interp(expr));
                i = close + 1;
            } else if (c == '}') {
                if (i + 1 < n && raw.charAt(i + 1) == '}') {
                    plain.append('}');
                    i += 2;
                } else {
                    throw new ParseException(pos, "stray '}' in text (use }} for a literal brace)");
                }
            } else {
                plain.append(c);
                i++;
            }
        }
        if (!plain.isEmpty() || parts.isEmpty()) {
            parts.add(new Text.Part.Plain(plain.toString()));
        }
        return new Text(List.copyOf(parts));
    }
}
