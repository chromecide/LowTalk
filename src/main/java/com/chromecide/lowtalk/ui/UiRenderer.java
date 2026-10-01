package com.chromecide.lowtalk.ui;

import javax.annotation.Nonnull;

/**
 * Draws a {@link UiModel} according to a {@link PageLayout}.
 *
 * <p>There is one implementation today, against the custom-UI documents the client has now. The reason
 * this is an interface is that the server-driven interface Hytale is building — a view model, bound
 * collections and named commands pushed to the client — is the same shape as what is above this line,
 * so when it arrives the change should be a second implementation and a second set of layouts, not a
 * rewrite of every page.
 */
public interface UiRenderer {

    /**
     * First draw: load the document, bind the commands, and send every value in the model. A page is
     * empty on the client until this runs, and is empty again after it is rebuilt.
     */
    void build(@Nonnull PageLayout layout, @Nonnull UiModel model);

    /** Later draws: send only what changed since the last one. */
    void update(@Nonnull PageLayout layout, @Nonnull UiModel model);
}
