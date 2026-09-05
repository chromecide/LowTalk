package com.chromecide.lowtalk.hytale;

import com.chromecide.lowtalk.runtime.Step;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * The conversation window. Shows one {@link Step} at a time: a speaker, a line, and either a
 * Continue button, option buttons, or a text box. Events are stamped with a generation number so
 * a click on a button from a previous step is ignored.
 */
public class DialoguePage extends InteractiveCustomUIPage<DialoguePage.Data> {

    private static final String LAYOUT = "Pages/LowTalk/DialoguePage.ui";
    private static final String OPTION = "Pages/LowTalk/OptionButton.ui";
    private static final String OPTION_DISABLED = "Pages/LowTalk/OptionDisabled.ui";
    private static final int KEY_RETURN = 13;
    private static final int KEY_KP_ENTER = 1073741912;

    public enum Action { CONTINUE, CHOOSE, OK, KEY, CLOSE }

    public static class Data {
        public static final BuilderCodec<Data> CODEC = BuilderCodec.builder(Data.class, Data::new)
                .append(new KeyedCodec<>("Action", Codec.STRING, false), (d, s) -> d.action = s, d -> d.action).add()
                .append(new KeyedCodec<>("Gen", Codec.STRING, false), (d, s) -> d.gen = s, d -> d.gen).add()
                .append(new KeyedCodec<>("Index", Codec.STRING, false), (d, s) -> d.index = s, d -> d.index).add()
                .append(new KeyedCodec<>("@Text", Codec.STRING, false), (d, s) -> d.text = s, d -> d.text).add()
                .append(new KeyedCodec<>("Keycode", Codec.INTEGER, false), (d, i) -> d.keycode = i, d -> d.keycode).add()
                .append(new KeyedCodec<>("Repeat", Codec.BOOLEAN, false), (d, b) -> d.repeat = b, d -> d.repeat).add()
                .build();
        private String action;
        private String gen;
        private String index;
        private String text;
        private Integer keycode;
        private Boolean repeat;
    }

    private final DialogueSession session;
    private final String title;
    private int generation = 0;
    private Step current;
    private volatile boolean open = true;

    public DialoguePage(@Nonnull PlayerRef playerRef, @Nonnull DialogueSession session, @Nonnull String title) {
        super(playerRef, CustomPageLifetime.CanDismiss, Data.CODEC);
        this.session = session;
        this.title = title;
    }

    public boolean isOpen() {
        return open;
    }

    /** Called before the page is opened, and again for each new step. */
    public void show(@Nonnull Step step) {
        this.current = step;
        this.generation++;
    }

    public void showNarration(@Nonnull String text) {
        if (!open) return;
        UICommandBuilder cmd = new UICommandBuilder();
        cmd.set("#Narration.Text", text);
        sendUpdate(cmd, null, false);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder evt, @Nonnull Store<EntityStore> store) {
        cmd.append(LAYOUT);
        cmd.set("#NpcTitle.Text", title);
        cmd.set("#Narration.Text", "");
        render(cmd, evt);
    }

    /** Push the current step to an already-open window. */
    public void refresh() {
        if (!open) return;
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder evt = new UIEventBuilder();
        render(cmd, evt);
        sendUpdate(cmd, evt, false);
    }

    private void render(UICommandBuilder cmd, UIEventBuilder evt) {
        String gen = String.valueOf(generation);
        cmd.clear("#Options");
        cmd.set("#InputRow.Visible", false);
        cmd.set("#ContinueRow.Visible", false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
                new EventData().append("Action", Action.CLOSE).append("Gen", gen), false);

        switch (current) {
            case Step.Say say -> {
                setLine(cmd, say);
                cmd.set("#ContinueRow.Visible", true);
                evt.addEventBinding(CustomUIEventBindingType.Activating, "#ContinueButton",
                        new EventData().append("Action", Action.CONTINUE).append("Gen", gen), false);
            }
            case Step.Choose choose -> {
                if (choose.line() != null) {
                    setLine(cmd, choose.line());
                }
                int i = 0;
                for (Step.Shown o : choose.options()) {
                    String sel = "#Options[" + i + "]";
                    if (o.enabled()) {
                        cmd.append("#Options", OPTION);
                        cmd.set(sel + ".Text", o.text());
                        evt.addEventBinding(CustomUIEventBindingType.Activating, sel,
                                new EventData().append("Action", Action.CHOOSE).append("Gen", gen).append("Index", String.valueOf(o.index())), false);
                    } else {
                        cmd.append("#Options", OPTION_DISABLED);
                        cmd.set(sel + ".Text", o.text());
                    }
                    i++;
                }
            }
            case Step.Ask ask -> {
                cmd.set("#Speaker.Text", "");
                cmd.set("#Text.Text", ask.prompt());
                cmd.set("#Input.Value", "");
                cmd.set("#InputRow.Visible", true);
                EventData ok = new EventData().append("Action", Action.OK).append("Gen", gen).append("@Text", "#Input.Value");
                evt.addEventBinding(CustomUIEventBindingType.Activating, "#OkButton", ok, false);
                evt.addEventBinding(CustomUIEventBindingType.KeyDown, "#Input",
                        new EventData().append("Action", Action.KEY).append("Gen", gen).append("@Text", "#Input.Value"), false);
            }
            case Step.Finish f -> {
                cmd.set("#Speaker.Text", "");
                cmd.set("#Text.Text", "");
            }
        }
    }

    private static void setLine(UICommandBuilder cmd, Step.Say say) {
        cmd.set("#Speaker.Text", say.speaker() == null ? "" : say.speaker());
        cmd.set("#Text.Text", say.text());
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Data data) {
        open = true; // receiving an event proves the window is on screen
        if (data.action == null) return;
        Action action;
        try {
            action = Action.valueOf(data.action);
        } catch (IllegalArgumentException e) {
            return;
        }
        if (action != Action.CLOSE && data.gen != null && !data.gen.equals(String.valueOf(generation))) {
            return; // click from a previous step
        }
        switch (action) {
            case CONTINUE -> session.onContinue();
            case CHOOSE -> {
                if (data.index == null) return;
                try {
                    session.onChoose(Integer.parseInt(data.index));
                } catch (NumberFormatException ignored) {
                    // malformed index, ignore
                }
            }
            case OK -> session.onAnswer(data.text == null ? "" : data.text);
            case KEY -> {
                if (data.keycode != null && !Boolean.TRUE.equals(data.repeat)
                        && (data.keycode == KEY_RETURN || data.keycode == KEY_KP_ENTER)) {
                    session.onAnswer(data.text == null ? "" : data.text);
                }
            }
            case CLOSE -> {
                open = false;
                session.onLeave();
            }
        }
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        open = false;
        session.onDismissed();
    }

    /** Close from the server side. World thread. */
    public void closeNow() {
        if (!open) return;
        open = false;
        close();
    }
}
