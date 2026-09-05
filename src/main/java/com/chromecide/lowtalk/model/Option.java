package com.chromecide.lowtalk.model;

import java.util.List;

/**
 * A player choice. {@code guard} hides the option when false; {@code showGuard} shows it disabled
 * when false. Either may be null. {@code once} hides the option after the player has picked it.
 */
public record Option(Pos pos, Text text, Expr guard, Expr showGuard, boolean once, List<Statement> body) {
    /** Stable per-file key for remembering that a once-option was taken. */
    public String onceKey() {
        return "opt@" + pos.line();
    }
}
