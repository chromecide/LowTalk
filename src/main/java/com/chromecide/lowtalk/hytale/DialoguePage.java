package com.chromecide.lowtalk.hytale;

import com.chromecide.lowtalk.hytale.presentation.DialogueLayout;
import com.chromecide.lowtalk.hytale.presentation.History;
import com.chromecide.lowtalk.runtime.Step;
import com.chromecide.lowtalk.ui.PageLayout;
import com.chromecide.lowtalk.ui.UiBox;
import com.chromecide.lowtalk.ui.UiEvent;
import com.chromecide.lowtalk.ui.UiItem;
import com.chromecide.lowtalk.ui.UiModel;
import com.chromecide.lowtalk.ui.UiPage;
import com.chromecide.lowtalk.ui.UiText;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * The conversation window. Shows one {@link Step} at a time: a speaker, a line, and either a
 * Continue button, option buttons, or a text box.
 *
 * <p>The page says what should be on screen and nothing about how; which control carries which value
 * is in {@link #layoutFor}. Each step re-describes the whole window and only the difference is sent.
 */
public class DialoguePage extends UiPage<DialoguePage.Data> {

    private static final String LINE_NPC = "Pages/LowTalk/LineNpc.ui";
    private static final String LINE_PLAYER = "Pages/LowTalk/LinePlayer.ui";
    private static final String LINE_SYSTEM = "Pages/LowTalk/LineSystem.ui";
    /** Template names in the model; the layout maps each to the document above that draws it. */
    private static final String SAID_NPC = "npc";
    private static final String SAID_PLAYER = "player";
    private static final String SAID_SYSTEM = "system";
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

    /** Which control carries which value. The only place this page names anything on screen. */
    @Nonnull
    static PageLayout layoutFor(@Nonnull DialogueLayout dialogueLayout) {
        return PageLayout.of(dialogueLayout.uiFile())
                .property("title", "#NpcTitle", "Text")
                .property("portrait", "#Portrait", "Background")
                .property("portraitVisible", "#PortraitBox", "Visible")
                .property("continueVisible", "#ContinueRow", "Visible")
                .property("continueText", "#ContinueButton", "Text")
                .property("inputVisible", "#InputRow", "Visible")
                .property("inputValue", "#Input", "Value")
                .property("closeVisible", "#CloseRow", "Visible")
                .box("bar", "#Bar", "Anchor")
                .container("transcript", "#Transcript")
                    .template(SAID_NPC, LINE_NPC)
                        .field("speaker", "#Speaker", "Text")
                        .field("text", "#Text", "Text")
                        .done()
                    .template(SAID_PLAYER, LINE_PLAYER)
                        .field("speaker", "#Speaker", "Text")
                        .field("text", "#Text", "Text")
                        .done()
                    .template(SAID_SYSTEM, LINE_SYSTEM)
                        .field("text", "#Text", "Text")
                        .done()
                    .revealsLatestWith("ScrollChildIndexIntoView")
                    .done()
                .pool("options", "#Opt", OPTION_SLOTS)
                    .field("text", "", "Text")
                    .field("disabled", "", "Disabled")
                    .done()
                .command(Action.CLOSE.name(), "#CloseButton", UiEvent.ACTIVATE).done()
                .command(Action.CONTINUE.name(), "#ContinueButton", UiEvent.ACTIVATE).done()
                .command(Action.OK.name(), "#OkButton", UiEvent.ACTIVATE)
                    .reads("Text", "#Input", "Value").done()
                .command(Action.KEY.name(), "#Input", UiEvent.KEY_DOWN)
                    .reads("Text", "#Input", "Value").done()
                // The client only delivers KeyDown for text fields ("Target element in CustomUI event binding
                // has no compatible KeyDown event" for a Group or a TextButton), so the bar layouts number
                // their options as a visual cue only. HOTKEY stays wired for the day a focusable element exists.
                .poolCommand(Action.CHOOSE.name(), "#Opt", OPTION_SLOTS, UiEvent.ACTIVATE, "Slot")
                .build();
    }

    private final DialogueSession session;
    private final String title;
    private final String portrait;
    private final DialogueLayout dialogueLayout;
    private final History historyMode;
    private Step current;
    /** For the current Choose step: slot number -> option index, or -1 for a disabled slot. */
    private final List<Integer> slotToOption = new ArrayList<>();
    /** Everything said so far, as rows, so the transcript survives the window being rebuilt. */
    private final List<UiItem> said = new ArrayList<>();
    private volatile boolean open = true;
    private volatile long openedAt = System.currentTimeMillis();

    public DialoguePage(@Nonnull PlayerRef playerRef, @Nonnull DialogueSession session, @Nonnull String title, String portrait,
                        @Nonnull DialogueLayout layout, @Nonnull History historyMode) {
        super(playerRef, CustomPageLifetime.CanDismiss, Data.CODEC, layoutFor(layout));
        this.session = session;
        this.title = title;
        this.portrait = portrait == null || portrait.isBlank() ? null : portrait.trim();
        this.dialogueLayout = layout;
        this.historyMode = historyMode;
    }

    public boolean isOpen() {
        return open;
    }

    public DialogueLayout getLayout() {
        return dialogueLayout;
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
        if (historyMode == History.LATEST) {
            // Only what the NPC says now stays on screen: forget earlier lines.
            said.clear();
        }
        switch (step) {
            case Step.Say say -> say(SAID_NPC, say.speaker(), say.text());
            case Step.Choose choose -> {
                if (choose.line() != null) say(SAID_NPC, choose.line().speaker(), choose.line().text());
            }
            case Step.Ask ask -> say(SAID_NPC, null, ask.prompt());
            case Step.Wait wait -> {
                if (wait.line() != null) say(SAID_NPC, wait.line().speaker(), wait.line().text());
            }
            case Step.Finish f -> {}
        }
    }

    /** The player chose an option or typed an answer; echo it into the transcript (full history only). */
    public void playerSaid(@Nonnull String text) {
        if (historyMode == History.LATEST) return;
        say(SAID_PLAYER, playerRef.getUsername(), text);
    }

    public void showNarration(@Nonnull String text) {
        if (!open) return;
        say(SAID_SYSTEM, null, text);
        refresh();
    }

    private void say(String template, String speaker, String text) {
        said.add(UiItem.of(template)
                .with("speaker", speaker == null ? "" : speaker)
                .with("text", text == null ? "" : text));
    }

    @Override
    protected void populate(@Nonnull UiModel model) {
        model.set("title", title);
        if (portrait != null) {
            // A plain path relative to Common/UI/Custom/, naming the base file; the client picks its @2x
            // variant itself. PatchStyle objects and "UI/Custom/..." forms do not resolve.
            model.set("portrait", texturePath(portrait));
            model.set("portraitVisible", true);
        } else {
            model.set("portraitVisible", false);
        }
        model.setItems("transcript", said);
        model.set("closeVisible", true);
        model.set("continueVisible", false);
        model.set("inputVisible", false);

        List<UiItem> options = List.of();
        slotToOption.clear();
        boolean wantsContinue = false;
        boolean wantsInput = false;
        switch (current) {
            case Step.Say say -> {
                wantsContinue = !say.last();
                model.set("continueVisible", wantsContinue);
                model.set("continueText", say.button() != null && !say.button().isBlank()
                        ? UiText.of(say.button().trim())
                        : UiText.key("server.lowtalk.ui.continue"));
            }
            case Step.Choose choose -> options = optionRows(choose);
            case Step.Ask ask -> {
                wantsInput = true;
                model.set("inputValue", "");
                model.set("inputVisible", true);
            }
            case Step.Wait wait -> {} // only Leave while the pause runs
            case Step.Finish f -> {}
            case null -> {}
        }
        model.setItems("options", options);
        if (dialogueLayout.isBar()) model.set("bar", bar(options.size(), wantsInput, wantsContinue));
    }

    private List<UiItem> optionRows(@Nonnull Step.Choose choose) {
        List<UiItem> rows = new ArrayList<>();
        for (Step.Shown o : choose.options()) {
            if (rows.size() >= OPTION_SLOTS) break;
            // Bar layouts number every visible option, disabled ones included, so the numbers stay contiguous;
            // a disabled option is greyed by the button itself rather than by a blank prefix.
            String prefix = dialogueLayout.isBar()
                    ? (rows.size() + 1) + ".  "
                    : (o.enabled() ? OPTION_PREFIX : DISABLED_PREFIX);
            rows.add(UiItem.of("option")
                    .with("text", prefix + o.text())
                    .with("disabled", (Object) Boolean.valueOf(!o.enabled())));
            slotToOption.add(o.enabled() ? o.index() : -1);
        }
        return rows;
    }

    /** Bar layout geometry: the bar grows with the step so the transcript always keeps room for a few lines. */
    private static final int BAR_EDGE = 36;
    private static final int BAR_SIDE = 110;
    private static final int BAR_FIXED = 28 + 36;      // padding, title row and its gap
    private static final int BAR_TRANSCRIPT = 104;     // about four lines of text
    private static final int BAR_OPTION = 28;
    private static final int BAR_INPUT = 40;
    private static final int BAR_CONTINUE = 36;

    private UiBox bar(int optionRows, boolean input, boolean cont) {
        int height = BAR_FIXED + BAR_TRANSCRIPT + optionRows * BAR_OPTION
                + (input ? BAR_INPUT : 0) + (cont ? BAR_CONTINUE : 0);
        UiBox box = UiBox.height(height).sides(BAR_SIDE, BAR_SIDE);
        return dialogueLayout == DialogueLayout.TOP ? box.fromTop(BAR_EDGE) : box.fromBottom(BAR_EDGE);
    }

    @Nonnull
    @Override
    protected String commandOf(@Nonnull Data data) {
        return data.action == null ? "" : data.action;
    }

    @Override
    protected void onCommand(@Nonnull String command, @Nonnull Data data,
                             @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        open = true; // receiving an event proves the window is on screen
        Action action;
        try {
            action = Action.valueOf(command);
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
