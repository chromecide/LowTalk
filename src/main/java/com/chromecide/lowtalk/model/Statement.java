package com.chromecide.lowtalk.model;

import java.util.List;

/** One statement in a node body. */
public sealed interface Statement {

    Pos pos();

    /** A spoken line. Speaker is null for the default speaker. */
    /** A spoken line. {@code button} is the label of the Continue button after it, null for the default. */
    record Line(Pos pos, String speaker, Text text, String button) implements Statement {
        public Line(Pos pos, String speaker, Text text) {
            this(pos, speaker, text, null);
        }
    }

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
    /**
     * A text box whose answer is stored in {@code target}.
     *
     * <p>{@code kind} is what the author asked the player for. A number input re-asks rather than storing a
     * word, because the alternative is a conversation that dies later, in whatever line first treats the
     * answer as a number — the one thing a creator cannot validate is the one thing that could end the
     * conversation.
     */
    record Input(Pos pos, Expr.Var target, Text prompt, InputKind kind) implements Statement {
        public Input(Pos pos, Expr.Var target, Text prompt) {
            this(pos, target, prompt, InputKind.TEXT);
        }
    }

    /** What an {@link Input} accepts. */
    enum InputKind {
        /** Anything the player types, cut to the answer limit. */
        TEXT,
        /** A number, or the box comes back. */
        NUMBER
    }

    /** <<random>> ... <<or>> ... <<endrandom>>: one alternative runs, chosen at random. */
    record Random(Pos pos, List<List<Statement>> alternatives) implements Statement {}
    /** <<wait seconds>>: the host pauses before showing what follows; no Continue button. */
    record Wait(Pos pos, Expr seconds) implements Statement {}
    /** Any other <<command args>>. Built-ins (give, take, shop, ...) and plugin commands both land here. */
    record Command(Pos pos, String name, List<Text> args) implements Statement {}
}
