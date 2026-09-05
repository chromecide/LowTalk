package com.chromecide.lowtalk.runtime;

import com.chromecide.lowtalk.model.Pos;

/** A problem while running a dialogue, e.g. an unknown function or a bad jump. */
public class RuntimeError extends RuntimeException {
    private final Pos pos;

    public RuntimeError(String message) {
        this(null, message);
    }

    public RuntimeError(Pos pos, String message) {
        super(pos == null ? message : pos + ": " + message);
        this.pos = pos;
    }

    public Pos getPos() {
        return pos;
    }

    RuntimeError at(Pos p) {
        return pos == null ? new RuntimeError(p, super.getMessage()) : this;
    }
}
