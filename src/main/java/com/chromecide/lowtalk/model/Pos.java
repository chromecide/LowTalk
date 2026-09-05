package com.chromecide.lowtalk.model;

/** A location in a dialogue source file, for error messages. Lines are 1-based. */
public record Pos(String file, int line) {
    @Override
    public String toString() {
        return file + ":" + line;
    }
}
