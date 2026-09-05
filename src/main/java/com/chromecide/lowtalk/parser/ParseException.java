package com.chromecide.lowtalk.parser;

import com.chromecide.lowtalk.model.Pos;

public class ParseException extends RuntimeException {
    private final Pos pos;

    public ParseException(Pos pos, String message) {
        super(pos + ": " + message);
        this.pos = pos;
    }

    public Pos getPos() {
        return pos;
    }
}
