package com.chromecide.lowtalk.hytale;

import com.chromecide.lowtalk.hytale.presentation.DialogueLayout;
import com.chromecide.lowtalk.runtime.Step;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
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

    private static final String LINE_NPC = "Pages/LowTalk/LineNpc.ui";
    private static final String LINE_PLAYER = "Pages/LowTalk/LinePlayer.ui";
    private static final String LINE_SYSTEM = "Pages/LowTalk/LineSystem.ui";
    public static final int OPTION_SLOTS = 8;
    private static final int KEY_RETURN = 13;
    private static final int KEY_KP_ENTER = 1073741912;
    private static final String OPTION_PREFIX = "›  ";
    private static final String DISABLED_PREFIX = "   ";
    /** SDL keycodes: the digit row and the keypad, 1 to 8, for picking options in the bar layouts. */
    private static final int KEY_1 = 49;
    private static final int KEY_KP_1 = 1073741913;

    public enum Action { CONTINUE, CHOOSE, OK, KEY, CLOSE, HOTKEY }

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
    private final DialogueLayout layout;
    private Step current;
    /** For the current Choose step: slot number -> option index, or -1 for a disabled slot. */
    private final List<Integer> slotToOption = new ArrayList<>();
    /** Transcript entries waiting to be appended on the next render. */
    private final List<String[]> pendingLines = new ArrayList<>();
    /** Everything shown so far, so the transcript survives the window being rebuilt (after a shop, say). */
    private final List<String[]> history = new ArrayList<>();
    private int transcriptCount = 0;
    private volatile boolean open = true;
    private volatile long openedAt = System.currentTimeMillis();

    public DialoguePage(@Nonnull PlayerRef playerRef, @Nonnull DialogueSession session, @Nonnull String title, String portrait,
                        @Nonnull DialogueLayout layout) {
        super(playerRef, CustomPageLifetime.CanDismiss, Data.CODEC);
        this.session = session;
        this.title = title;
        this.portrait = portrait == null || portrait.isBlank() ? null : portrait.trim();
        this.layout = layout;
    }

    public boolean isOpen() {
        return open;
    }

    public DialogueLayout getLayout() {
        return layout;
    }

    /** A portrait path as the client resolves it: relative to Common/UI/Custom/, base file name without a size suffix. */
    static String texturePath(String portrait) {
        String p = portrait.trim().replace('\\', '/');
        if (p.startsWith("Common/UI/Custom/")) p = p.substring("Common/UI/Custom/".length());
        if (p.startsWith("UI/Custom/")) p = p.substring("UI/Custom/".length());
        if (p.startsWith("/")) p = p.substring(1);
        return p.replace("@2x.", ".").replace("@3x.", ".");
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
        String[] line = {layout, speaker == null ? "" : speaker, text == null ? "" : text};
        pendingLines.add(line);
        history.add(line);
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
        cmd.append(layout.uiFile());
        cmd.set("#NpcTitle.Text", title);
        cmd.clear("#Transcript");
        transcriptCount = 0;
        // A rebuilt window starts empty on the client; replay what was said so far, then whatever is pending.
        pendingLines.clear();
        pendingLines.addAll(history);
        if (portrait != null) {
            // Verified against the client: a plain path string, relative to Common/UI/Custom/, naming the base file
            // (the client picks its @2x variant itself). PatchStyle objects and "UI/Custom/..." forms do not resolve.
            cmd.set("#Portrait.Background", texturePath(portrait));
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
        // Number keys for options: the client only delivers KeyDown for text fields ("Target element in CustomUI
        // event binding has no compatible KeyDown event" for a Group or a TextButton), so the bar layouts number
        // their options as a visual cue only. The HOTKEY action stays wired for the day a focusable element exists.
        render(cmd);
    }

    /** Push the current step to an already-open window. */
    public void refresh() {
        if (!open) return;
        UICommandBuilder cmd = new UICommandBuilder();
        render(cmd);
        sendUpdate(cmd, null, false);
    }

    /** Bar layout geometry: the bar grows with the step so the transcript always keeps room for a few lines. */
    private static final int BAR_EDGE = 36;
    private static final int BAR_SIDE = 110;
    private static final int BAR_FIXED = 28 + 36;      // padding, title row and its gap
    private static final int BAR_TRANSCRIPT = 104;     // about four lines of text
    private static final int BAR_OPTION = 28;
    private static final int BAR_INPUT = 40;
    private static final int BAR_CONTINUE = 36;

    private void sizeBar(UICommandBuilder cmd, int optionRows, boolean input, boolean cont) {
        if (!layout.isBar()) return;
        int height = BAR_FIXED + BAR_TRANSCRIPT + optionRows * BAR_OPTION + (input ? BAR_INPUT : 0) + (cont ? BAR_CONTINUE : 0);
        Anchor a = new Anchor();
        a.setHeight(Value.of(height));
        a.setLeft(Value.of(BAR_SIDE));
        a.setRight(Value.of(BAR_SIDE));
        if (layout == DialogueLayout.TOP) a.setTop(Value.of(BAR_EDGE));
        else a.setBottom(Value.of(BAR_EDGE));
        cmd.setObject("#Bar.Anchor", a);
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
            case Step.Say say -> {
                cmd.set("#ContinueRow.Visible", !say.last());
                if (say.button() != null && !say.button().isBlank()) {
                    cmd.set("#ContinueButton.Text", say.button().trim());
                } else {
                    cmd.set("#ContinueButton.Text", com.hypixel.hytale.server.core.Message.translation("server.lowtalk.ui.continue"));
                }
                sizeBar(cmd, 0, false, !say.last());
            }
            case Step.Choose choose -> {
                sizeBar(cmd, Math.min(choose.options().size(), OPTION_SLOTS), false, false);
                int slot = 0;
                for (Step.Shown o : choose.options()) {
                    if (slot >= OPTION_SLOTS) break;
                    String prefix = layout.isBar()
                            ? (o.enabled() ? (slot + 1) + ".  " : "    ")
                            : (o.enabled() ? OPTION_PREFIX : DISABLED_PREFIX);
                    cmd.set("#Opt" + slot + ".Text", prefix + o.text());
                    cmd.set("#Opt" + slot + ".Visible", true);
                    slotToOption.add(o.enabled() ? o.index() : -1);
                    slot++;
                }
            }
            case Step.Ask ask -> {
                cmd.set("#Input.Value", "");
                cmd.set("#InputRow.Visible", true);
                sizeBar(cmd, 0, true, false);
            }
            case Step.Wait wait -> sizeBar(cmd, 0, false, false); // only Leave while the pause runs
            case Step.Finish f -> sizeBar(cmd, 0, false, false);
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
                chooseSlot(slot);
            }
            case HOTKEY -> {
                if (!(current instanceof Step.Choose) || data.keycode == null || Boolean.TRUE.equals(data.repeat)) return;
                int k = data.keycode;
                int slot = k >= KEY_1 && k < KEY_1 + OPTION_SLOTS ? k - KEY_1
                        : k >= KEY_KP_1 && k < KEY_KP_1 + OPTION_SLOTS ? k - KEY_KP_1 : -1;
                if (slot >= 0) chooseSlot(slot);
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

    private void chooseSlot(int slot) {
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
