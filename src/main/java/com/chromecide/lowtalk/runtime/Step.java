package com.chromecide.lowtalk.runtime;

import java.util.List;

/** What the player should see next. Produced by {@link Conversation}. */
public sealed interface Step {

    /** A line. {@code last} means nothing follows it, so the button should read Leave rather than Continue. */
    record Say(String speaker, String text, boolean last) implements Step {
        public Say(String speaker, String text) {
            this(speaker, text, false);
        }
    }

    /** An optional line plus buttons. {@code line} is null when the options stand alone. */
    record Choose(Say line, List<Shown> options) implements Step {}

    /** One button. {@code index} is what to pass back to choose(); disabled buttons are greyed. */
    record Shown(int index, String text, boolean enabled) {}

    /** A text box. */
    record Ask(String prompt) implements Step {}
    /** Pause: show {@code line} (may be null) with no Continue button, then call next() after {@code seconds}. */
    record Wait(Say line, double seconds) implements Step {}

    /** Close the window. */
    record Finish() implements Step {}
}
