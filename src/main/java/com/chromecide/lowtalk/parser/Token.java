package com.chromecide.lowtalk.parser;

record Token(Kind kind, String text) {
    enum Kind { NUMBER, STRING, VAR, WORD, OP, END }
}
