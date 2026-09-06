package com.chromecide.lowtalk.parser;

import com.chromecide.lowtalk.model.Expr;
import com.chromecide.lowtalk.model.Pos;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Recursive descent parser for the expression language.
 *
 * <pre>
 *   ternary := or ( "?" ternary ":" ternary )?
 *   or      := and ( "or" and )*
 *   and     := not ( "and" not )*
 *   not     := "not" not | compare
 *   compare := add ( ( "==" | "!=" | "<" | "<=" | ">" | ">=" ) add )?
 *   add     := mul ( ( "+" | "-" ) mul )*
 *   mul     := unary ( ( "*" | "/" ) unary )*
 *   unary   := "-" unary | primary
 *   primary := number | string | true | false | var | ident "(" args ")" | "(" or ")"
 * </pre>
 */
public final class ExprParser {

    private static final Set<String> COMPARE = Set.of("==", "!=", "<", "<=", ">", ">=");
    static final Set<String> VAR_SCOPES = Set.of("player", "npc", "world", "tmp");
    /** Scope of a bare $name: this player with this NPC. */
    public static final String DEFAULT_SCOPE = "local";

    private final List<Token> tokens;
    private final Pos pos;
    private int i = 0;

    private ExprParser(List<Token> tokens, Pos pos) {
        this.tokens = tokens;
        this.pos = pos;
    }

    public static Expr parse(String source, Pos pos) {
        ExprParser p = new ExprParser(Lexer.tokenize(source, pos), pos);
        Expr e = p.parseTernary();
        if (!p.atEnd()) {
            throw new ParseException(pos, "unexpected '" + p.peek().text() + "' in expression: " + source);
        }
        return e;
    }

    /** Parse a variable reference on its own, e.g. the target of set or input. */
    public static Expr.Var parseVar(String source, Pos pos) {
        Expr e = parse(source.trim(), pos);
        if (e instanceof Expr.Var v) {
            return v;
        }
        throw new ParseException(pos, "expected a variable like $name, got: " + source);
    }

    private Expr parseTernary() {
        Expr cond = parseOr();
        if (peekOp("?")) {
            next();
            Expr a = parseTernary();
            expectOp(":");
            Expr b = parseTernary();
            return new Expr.Ternary(cond, a, b);
        }
        return cond;
    }

    private Expr parseOr() {
        Expr left = parseAnd();
        while (matchWord("or")) {
            left = new Expr.Binary("or", left, parseAnd());
        }
        return left;
    }

    private Expr parseAnd() {
        Expr left = parseNot();
        while (matchWord("and")) {
            left = new Expr.Binary("and", left, parseNot());
        }
        return left;
    }

    private Expr parseNot() {
        if (matchWord("not")) {
            return new Expr.Unary("not", parseNot());
        }
        return parseCompare();
    }

    private Expr parseCompare() {
        Expr left = parseAdd();
        Token t = peek();
        if (t.kind() == Token.Kind.OP && COMPARE.contains(t.text())) {
            i++;
            return new Expr.Binary(t.text(), left, parseAdd());
        }
        return left;
    }

    private Expr parseAdd() {
        Expr left = parseMul();
        while (peekOp("+") || peekOp("-")) {
            String op = next().text();
            left = new Expr.Binary(op, left, parseMul());
        }
        return left;
    }

    private Expr parseMul() {
        Expr left = parseUnary();
        while (peekOp("*") || peekOp("/")) {
            String op = next().text();
            left = new Expr.Binary(op, left, parseUnary());
        }
        return left;
    }

    private Expr parseUnary() {
        if (peekOp("-")) {
            next();
            return new Expr.Unary("-", parseUnary());
        }
        return parsePrimary();
    }

    private Expr parsePrimary() {
        Token t = next();
        switch (t.kind()) {
            case NUMBER -> {
                String s = t.text();
                if (s.contains(".")) return new Expr.Literal(Double.parseDouble(s));
                try {
                    return new Expr.Literal((double) Long.parseLong(s));
                } catch (NumberFormatException e) {
                    return new Expr.Literal(Double.parseDouble(s));
                }
            }
            case STRING -> {
                return new Expr.Literal(t.text());
            }
            case VAR -> {
                return toVar(t.text());
            }
            case WORD -> {
                if (t.text().equals("true")) return new Expr.Literal(Boolean.TRUE);
                if (t.text().equals("false")) return new Expr.Literal(Boolean.FALSE);
                if (peekOp("(")) {
                    next();
                    List<Expr> args = new ArrayList<>();
                    if (!peekOp(")")) {
                        args.add(parseTernary());
                        while (peekOp(",")) {
                            next();
                            args.add(parseTernary());
                        }
                    }
                    expectOp(")");
                    return new Expr.Call(t.text(), args);
                }
                String fn = Suggest.closest(t.text(), Validator.BUILTIN_FUNCTIONS);
                if (fn != null) {
                    throw new ParseException(pos, "unknown word '" + t.text() + "' in expression (did you mean " + fn + "()?)");
                }
                throw new ParseException(pos, "unknown word '" + t.text() + "' in expression (did you mean $" + t.text() + " or " + t.text() + "()?)");
            }
            case OP -> {
                if (t.text().equals("(")) {
                    Expr inner = parseTernary();
                    expectOp(")");
                    return inner;
                }
                throw new ParseException(pos, "unexpected '" + t.text() + "' in expression");
            }
            case END -> throw new ParseException(pos, "expression ended unexpectedly");
        }
        throw new ParseException(pos, "unexpected token in expression");
    }

    /** "$name" -> local scope (this player with this NPC); "$player.name", "$npc.name" etc. -> that scope. */
    static Expr.Var toVar(String dollarName) {
        String body = dollarName.substring(1);
        int dot = body.indexOf('.');
        if (dot > 0) {
            String scope = body.substring(0, dot);
            String name = body.substring(dot + 1);
            if (VAR_SCOPES.contains(scope) && !name.isEmpty() && !name.contains(".")) {
                return new Expr.Var(scope, name);
            }
            // Not a known scope: treat the whole thing as a local variable name (dots allowed).
        }
        return new Expr.Var(DEFAULT_SCOPE, body);
    }

    // ---- token helpers

    private boolean atEnd() {
        return peek().kind() == Token.Kind.END;
    }

    private Token peek() {
        return tokens.get(Math.min(i, tokens.size() - 1));
    }

    private Token next() {
        Token t = peek();
        if (t.kind() != Token.Kind.END) i++;
        return t;
    }

    private boolean peekOp(String op) {
        Token t = peek();
        return t.kind() == Token.Kind.OP && t.text().equals(op);
    }

    private boolean matchWord(String word) {
        Token t = peek();
        if (t.kind() == Token.Kind.WORD && t.text().equals(word)) {
            i++;
            return true;
        }
        return false;
    }

    private void expectOp(String op) {
        if (!peekOp(op)) {
            throw new ParseException(pos, "expected '" + op + "' but found '" + peek().text() + "'");
        }
        next();
    }
}
