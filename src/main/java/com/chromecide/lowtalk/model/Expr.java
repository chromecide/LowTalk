package com.chromecide.lowtalk.model;

import java.util.List;

/** Expression syntax tree. Evaluated by the runtime against a Context. */
public sealed interface Expr {

    record Literal(Object value) implements Expr {}

    /** A variable reference such as $met, $npc.helpers, $world.season, $tmp.answer. */
    record Var(String scope, String name) implements Expr {}

    record Unary(String op, Expr operand) implements Expr {}

    record Binary(String op, Expr left, Expr right) implements Expr {}

    record Call(String function, List<Expr> args) implements Expr {}
}
