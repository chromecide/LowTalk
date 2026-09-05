package com.chromecide.lowtalk.model;

import java.util.List;

/**
 * A player choice. {@code guard} hides the option when false; {@code showGuard} shows it disabled
 * when false. Either may be null.
 */
public record Option(Pos pos, Text text, Expr guard, Expr showGuard, List<Statement> body) {}
