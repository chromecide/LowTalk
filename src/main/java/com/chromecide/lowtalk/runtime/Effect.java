package com.chromecide.lowtalk.runtime;

import com.chromecide.lowtalk.model.Pos;

import java.util.List;

/** A side effect for the host to apply: any <<command>> that is not set/jump/end/input/once. */
public record Effect(Pos pos, String name, List<String> args) {}
