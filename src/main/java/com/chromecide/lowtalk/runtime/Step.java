package com.chromecide.lowtalk.runtime;

import java.util.List;

/** What the player should see next. Produced by {@link Conversation}. */
public sealed interface Step {

    /** A line with a Continue button. */
    record Say(String speaker, String text) implements Step {}

    /** An optional line plus buttons. {@code line} is null when the options stand alone. */
    record Choose(Say line, List<Shown> options) implements Step {}

    /** One button. {@code index} is what to pass back to choose(); disabled buttons are greyed. */
    record Shown(int index, String text, boolean enabled) {}

    /** A text box. */
    record Ask(String prompt) implements Step {}

    /** Close the window. */
    record Finish() implements Step {}
}
