package com.chromecide.lowtalk.model;

import java.util.List;

public record Node(Pos pos, String name, List<Statement> body) {}
