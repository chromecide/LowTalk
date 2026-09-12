package com.chromecide.lowtalk.hytale.presentation;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * How one conversation is shown: its layout and which HUD parts are hidden while it is open. The HUD names are the
 * game's own ({@code Reticle}, {@code Hotbar}, {@code Compass}, ...); unknown names are ignored when applied.
 */
public record Presentation(@Nonnull DialogueLayout layout, @Nonnull Set<String> hideHud, @Nonnull History history) {

    public Presentation {
        hideHud = Collections.unmodifiableSet(new LinkedHashSet<>(hideHud));
    }

    public static Presentation of(DialogueLayout layout, List<String> hideHud, History history) {
        return new Presentation(layout, new LinkedHashSet<>(hideHud), history);
    }

    /**
     * The values a pack, a plugin or the server config can set; a null field means "not decided here". Resolution
     * takes each field from the most specific level that decided it.
     */
    public record Defaults(DialogueLayout layout, List<String> hideHud, History history) {
        public static final Defaults NONE = new Defaults(null, null, null);

        public Defaults(DialogueLayout layout, List<String> hideHud) {
            this(layout, hideHud, null);
        }

        public boolean isEmpty() { return layout == null && hideHud == null && history == null; }
    }
}
