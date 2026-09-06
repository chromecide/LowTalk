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
import java.util.ArrayList;
import java.util.List;

/**
 * The conversation window. Shows one {@link Step} at a time: a speaker, a line, and either a
 * Continue button, option buttons, or a text box.
 *
 * The layout has a fixed pool of option buttons and every event is bound once when the page
 * opens; each step only changes text and visibility. Events carry a generation number so a click
 * that belongs to a previous step is ignored.
 */
public class DialoguePage extends InteractiveCustomUIPage<DialoguePage.Data> {

    private static final String LAYOUT = "Pages/LowTalk/DialoguePage.ui";
    private static final String LINE_NPC = "Pages/LowTalk/LineNpc.ui";
    private static final String LINE_PLAYER = "Pages/LowTalk/LinePlayer.ui";
    private static final String LINE_SYSTEM = "Pages/LowTalk/LineSystem.ui";
    public static final int OPTION_SLOTS = 8;
    private static final int KEY_RETURN = 13;
    private static final int KEY_KP_ENTER = 1073741912;
    private static final String OPTION_PREFIX = "›  ";
    private static final String DISABLED_PREFIX = "   ";

    public enum Action { CONTINUE, CHOOSE, OK, KEY, CLOSE }

    public static class Data {
        public static final BuilderCodec<Data> CODEC = BuilderCodec.builder(Data.class, Data::new)
                .append(new KeyedCodec<>("Action", Codec.STRING, false), (d, s) -> d.action = s, d -> d.action).add()
                .append(new KeyedCodec<>("Slot", Codec.STRING, false), (d, s) -> d.slot = s, d -> d.slot).add()
                .append(new KeyedCodec<>("@Text", Codec.STRING, false), (d, s) -> d.text = s, d -> d.text).add()
                .append(new KeyedCodec<>("Keycode", Codec.INTEGER, false), (d, i) -> d.keycode = i, d -> d.keycode).add()
                .append(new KeyedCodec<>("Repeat", Codec.BOOLEAN, false), (d, b) -> d.repeat = b, d -> d.repeat).add()
                .build();
        private String action;
        private String slot;
        private String text;
        private Integer keycode;
        private Boolean repeat;
    }

    private final DialogueSession session;
    private final String title;
    private final String portrait;
    private Step current;
    /** For the current Choose step: slot number -> option index, or -1 for a disabled slot. */
    private final List<Integer> slotToOption = new ArrayList<>();
    /** Transcript entries waiting to be appended on the next render. */
    private final List<String[]> pendingLines = new ArrayList<>();
    private int transcriptCount = 0;
    private volatile boolean open = true;
    private volatile long openedAt = System.currentTimeMillis();

    public DialoguePage(@Nonnull PlayerRef playerRef, @Nonnull DialogueSession session, @Nonnull String title, String portrait) {
        super(playerRef, CustomPageLifetime.CanDismiss, Data.CODEC);
        this.session = session;
        this.title = title;
        this.portrait = portrait == null || portrait.isBlank() ? null : portrait.trim();
    }

    public boolean isOpen() {
        return open;
    }

    /** Called before the page is opened, and again for each new step. */
    public void show(@Nonnull Step step) {
        this.current = step;
        switch (step) {
            case Step.Say say -> queueLine(LINE_NPC, say.speaker(), say.text());
            case Step.Choose choose -> {
                if (choose.line() != null) queueLine(LINE_NPC, choose.line().speaker(), choose.line().text());
            }
            case Step.Ask ask -> queueLine(LINE_NPC, null, ask.prompt());
            case Step.Wait wait -> {
                if (wait.line() != null) queueLine(LINE_NPC, wait.line().speaker(), wait.line().text());
            }
            case Step.Finish f -> {}
        }
    }

    /** The player chose an option or typed an answer; echo it into the transcript. */
    public void playerSaid(@Nonnull String text) {
        queueLine(LINE_PLAYER, playerRef.getUsername(), text);
    }

    public void showNarration(@Nonnull String text) {
        if (!open) return;
        UICommandBuilder cmd = new UICommandBuilder();
        queueLine(LINE_SYSTEM, null, text);
        flushLines(cmd);
        sendUpdate(cmd, null, false);
    }

    private void queueLine(String layout, String speaker, String text) {
        pendingLines.add(new String[] {layout, speaker == null ? "" : speaker, text == null ? "" : text});
    }

    private void flushLines(UICommandBuilder cmd) {
        for (String[] line : pendingLines) {
            cmd.append("#Transcript", line[0]);
            String sel = "#Transcript[" + transcriptCount + "]";
            if (!line[0].equals(LINE_SYSTEM)) {
                cmd.set(sel + " #Speaker.Text", line[1]);
            }
            cmd.set(sel + " #Text.Text", line[2]);
            transcriptCount++;
        }
        pendingLines.clear();
        if (transcriptCount > 0) {
            cmd.set("#Transcript.ScrollChildIndexIntoView", transcriptCount - 1);
        }
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder evt, @Nonnull Store<EntityStore> store) {
        cmd.append(LAYOUT);
        cmd.set("#NpcTitle.Text", title);
        cmd.clear("#Transcript");
        transcriptCount = 0;
        if (portrait != null) {
            // A background is a patch style, not a bare path: the client shows a red X for a string here.
            cmd.setObject("#Portrait.Background", new com.hypixel.hytale.server.core.ui.PatchStyle(com.hypixel.hytale.server.core.ui.Value.of(portrait)));
            cmd.set("#PortraitBox.Visible", true);
        }
        // Bind everything once; later steps only change text and visibility.
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton", new EventData().append("Action", Action.CLOSE), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ContinueButton", new EventData().append("Action", Action.CONTINUE), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#OkButton",
                new EventData().append("Action", Action.OK).append("@Text", "#Input.Value"), false);
        evt.addEventBinding(CustomUIEventBindingType.KeyDown, "#Input",
                new EventData().append("Action", Action.KEY).append("@Text", "#Input.Value"), false);
        for (int i = 0; i < OPTION_SLOTS; i++) {
            evt.addEventBinding(CustomUIEventBindingType.Activating, "#Opt" + i,
                    new EventData().append("Action", Action.CHOOSE).append("Slot", String.valueOf(i)), false);
        }
        render(cmd);
    }

    /** Push the current step to an already-open window. */
    public void refresh() {
        if (!open) return;
        UICommandBuilder cmd = new UICommandBuilder();
        render(cmd);
        sendUpdate(cmd, null, false);
    }

    private void render(UICommandBuilder cmd) {
        slotToOption.clear();
        for (int i = 0; i < OPTION_SLOTS; i++) {
            cmd.set("#Opt" + i + ".Visible", false);
        }
        cmd.set("#InputRow.Visible", false);
        cmd.set("#ContinueRow.Visible", false);
        cmd.set("#CloseRow.Visible", true);
        flushLines(cmd);

        switch (current) {
            case Step.Say say -> cmd.set("#ContinueRow.Visible", !say.last());
            case Step.Choose choose -> {
                int slot = 0;
                for (Step.Shown o : choose.options()) {
                    if (slot >= OPTION_SLOTS) break;
                    cmd.set("#Opt" + slot + ".Text", (o.enabled() ? OPTION_PREFIX : DISABLED_PREFIX) + o.text());
                    cmd.set("#Opt" + slot + ".Visible", true);
                    slotToOption.add(o.enabled() ? o.index() : -1);
                    slot++;
                }
            }
            case Step.Ask ask -> {
                cmd.set("#Input.Value", "");
                cmd.set("#InputRow.Visible", true);
            }
            case Step.Wait wait -> {} // only Leave while the pause runs
            case Step.Finish f -> {}
        }
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
        switch (action) {
            case CONTINUE -> {
                if (current instanceof Step.Say) session.onContinue();
            }
            case CHOOSE -> {
                if (!(current instanceof Step.Choose) || data.slot == null) return;
                int slot;
                try {
                    slot = Integer.parseInt(data.slot);
                } catch (NumberFormatException e) {
                    return;
                }
                if (slot < 0 || slot >= slotToOption.size()) return;
                int optionIndex = slotToOption.get(slot);
                if (optionIndex < 0) return; // disabled option
                if (current instanceof Step.Choose ch) {
                    for (Step.Shown o : ch.options()) {
                        if (o.index() == optionIndex) session.choiceMade(o.text());
                    }
                }
                session.onChoose(optionIndex);
            }
            case OK -> {
                if (current instanceof Step.Ask) answer(data.text);
            }
            case KEY -> {
                if (current instanceof Step.Ask && data.keycode != null && !Boolean.TRUE.equals(data.repeat)
                        && (data.keycode == KEY_RETURN || data.keycode == KEY_KP_ENTER)) {
                    answer(data.text);
                }
            }
            case CLOSE -> session.onLeave();
        }
    }

    private void answer(String raw) {
        String text = raw == null ? "" : raw.trim();
        playerSaid(text.isEmpty() ? "..." : text);
        session.onAnswer(text);
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        long age = System.currentTimeMillis() - openedAt;
        session.log("page dismissed after " + age + " ms (open=" + open + ")");
        open = false;
        session.onDismissed();
    }

    /** Called right before the page is handed to the page manager. */
    public void markOpened() {
        openedAt = System.currentTimeMillis();
        open = true;
    }

    private volatile boolean closeRequested = false;

    /** Close from the server side. World thread. Safe to call more than once. */
    public void closeNow() {
        if (closeRequested) return;
        closeRequested = true;
        open = false;
        close();
    }
}
