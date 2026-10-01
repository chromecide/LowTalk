package com.chromecide.lowtalk.ui;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * A page that describes what it is showing and lets a renderer work out how to show it.
 *
 * <p>A subclass answers one question — given where the page is now, what should be on screen? — by
 * filling in a {@link UiModel}. It never names a control, never decides what changed and never sends a
 * command of its own. {@link #refresh()} re-asks the question and the model sends only the difference.
 *
 * <p>The event payload stays the page's own typed record, because that is transport: the renderer puts
 * the command's name in {@link #COMMAND_PARAM} and the page's codec reads it back out along with
 * whatever else that command carries.
 */
public abstract class UiPage<D> extends InteractiveCustomUIPage<D> {

    /** The event field carrying the name of the command that was raised. */
    public static final String COMMAND_PARAM = "Action";

    @Nonnull
    protected final UiModel model = new UiModel();
    @Nonnull
    private final PageLayout layout;
    @Nonnull
    private final UiRenderer renderer;

    protected UiPage(@Nonnull PlayerRef playerRef, @Nonnull CustomPageLifetime lifetime,
                     @Nonnull BuilderCodec<D> eventDataCodec, @Nonnull PageLayout layout) {
        super(playerRef, lifetime, eventDataCodec);
        this.layout = layout;
        this.renderer = new CustomUiRenderer((commands, events) -> sendUpdate(commands, events, false));
    }

    @Nonnull
    protected PageLayout pageLayout() {
        return layout;
    }

    /**
     * Describe what should be on screen now. Called for the first draw and again for every refresh, so
     * it should set every property it owns rather than only the ones it thinks have moved — working out
     * the difference is the model's job, not the page's.
     */
    protected abstract void populate(@Nonnull UiModel model);

    /** The command the player raised, named by the layout. */
    protected abstract void onCommand(@Nonnull String command, @Nonnull D data,
                                      @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store);

    /** The name the page's codec decoded into, or null if this event carried no command. */
    @Nonnull
    protected abstract String commandOf(@Nonnull D data);

    @Override
    public final void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                            @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        populate(model);
        if (renderer instanceof CustomUiRenderer custom) custom.into(commandBuilder, eventBuilder);
        renderer.build(layout, model);
    }

    /** Re-describe the page and send whatever changed. */
    public void refresh() {
        populate(model);
        renderer.update(layout, model);
    }

    /**
     * Send what has already been written into the model, without re-describing the page.
     *
     * <p>For the narrow case of changing one field of one row as the creator types: re-describing would
     * rebuild every row and send the lot, which is what this exists to avoid.
     */
    protected void pushChanges() {
        renderer.update(layout, model);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull D data) {
        String command = commandOf(data);
        if (command.isEmpty()) return;
        onCommand(command, data, ref, store);
    }
}
