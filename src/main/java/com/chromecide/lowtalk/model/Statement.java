package com.chromecide.lowtalk.model;

import java.util.List;

/** One statement in a node body. */
public sealed interface Statement {

    Pos pos();

    /** A spoken line. Speaker is null for the default speaker. */
    record Line(Pos pos, String speaker, Text text) implements Statement {}

    /** One or more consecutive options, shown together as buttons. */
    record Choice(Pos pos, List<Option> options) implements Statement {}

    /** if / elseif / else. Branches are tried in order; an else branch has a null condition. */
    record Conditional(Pos pos, List<Branch> branches) implements Statement {}

    record Branch(Pos pos, Expr condition, List<Statement> body) {}

    /** A block that runs at most once per player. */
    record Once(Pos pos, String key, List<Statement> body) implements Statement {}

    record Set(Pos pos, Expr.Var target, Expr value) implements Statement {}

    record Jump(Pos pos, String node) implements Statement {}

    record End(Pos pos) implements Statement {}

    /** Ask the player for text and store it in the target variable. */
    record Input(Pos pos, Expr.Var target, Text prompt) implements Statement {}

    /** Any other <<command args>>. Built-ins (give, take, shop, ...) and plugin commands both land here. */
    record Command(Pos pos, String name, List<Text> args) implements Statement {}
}
