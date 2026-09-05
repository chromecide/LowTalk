package com.chromecide.lowtalk.parser;

import com.chromecide.lowtalk.model.Pos;

import java.util.ArrayList;
import java.util.List;

/** Tokenizer for expressions and command arguments. */
final class Lexer {

    private Lexer() {}

    static List<Token> tokenize(String s, Pos pos) {
        List<Token> out = new ArrayList<>();
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (c == '"' || c == '\'') {
                int start = i;
                StringBuilder sb = new StringBuilder();
                i++;
                boolean closed = false;
                while (i < n) {
                    char d = s.charAt(i);
                    if (d == '\\' && i + 1 < n) {
                        char e = s.charAt(i + 1);
                        sb.append(switch (e) {
                            case 'n' -> '\n';
                            case 't' -> '\t';
                            default -> e;
                        });
                        i += 2;
                    } else if (d == c) {
                        i++;
                        closed = true;
                        break;
                    } else {
                        sb.append(d);
                        i++;
                    }
                }
                if (!closed) {
                    throw new ParseException(pos, "unterminated string starting at column " + (start + 1));
                }
                out.add(new Token(Token.Kind.STRING, sb.toString()));
            } else if (Character.isDigit(c) || (c == '.' && i + 1 < n && Character.isDigit(s.charAt(i + 1)))) {
                int start = i;
                while (i < n && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) i++;
                out.add(new Token(Token.Kind.NUMBER, s.substring(start, i)));
            } else if (c == '$') {
                int start = i;
                i++;
                while (i < n && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_' || s.charAt(i) == '.')) i++;
                if (i == start + 1) {
                    throw new ParseException(pos, "'$' must be followed by a variable name");
                }
                String text = s.substring(start, i);
                if (text.endsWith(".")) {
                    throw new ParseException(pos, "variable name cannot end with '.': " + text);
                }
                out.add(new Token(Token.Kind.VAR, text));
            } else if (Character.isLetter(c) || c == '_') {
                int start = i;
                while (i < n && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_')) i++;
                out.add(new Token(Token.Kind.WORD, s.substring(start, i)));
            } else {
                String two = i + 1 < n ? s.substring(i, i + 2) : "";
                if (two.equals("==") || two.equals("!=") || two.equals("<=") || two.equals(">=")) {
                    out.add(new Token(Token.Kind.OP, two));
                    i += 2;
                } else if ("+-*/<>(),=?:".indexOf(c) >= 0) {
                    out.add(new Token(Token.Kind.OP, String.valueOf(c)));
                    i++;
                } else {
                    throw new ParseException(pos, "unexpected character '" + c + "' in expression");
                }
            }
        }
        out.add(new Token(Token.Kind.END, ""));
        return out;
    }
}
