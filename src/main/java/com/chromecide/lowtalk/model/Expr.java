package com.chromecide.lowtalk.model;

import java.util.List;

/** Expression syntax tree. Evaluated by the runtime against a Context. */
public sealed interface Expr {

    record Literal(Object value) implements Expr {}

    /** A variable reference: $met (local: this player with this NPC), $player.x, $npc.x, $world.x, $tmp.x. */
    record Var(String scope, String name) implements Expr {}

    record Unary(String op, Expr operand) implements Expr {}

    record Binary(String op, Expr left, Expr right) implements Expr {}

    record Call(String function, List<Expr> args) implements Expr {}
    /** cond ? ifTrue : ifFalse */
    record Ternary(Expr cond, Expr ifTrue, Expr ifFalse) implements Expr {}
}
