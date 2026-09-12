package com.chromecide.lowtalk.hytale.presentation;

import com.chromecide.lowtalk.model.Dialogue;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Decides how a dialogue is presented. Four levels, most specific first:
 * <ol>
 *   <li>the dialogue's own {@code layout:} directive (JSON {@code Layout}, the editor's Layout dropdown);</li>
 *   <li>the asset pack it came from: {@code Server/LowTalk/Settings.json} in that pack;</li>
 *   <li>defaults a plugin registered for its pack through the API (same shape as the file);</li>
 *   <li>the server config.</li>
 * </ol>
 * The server config's {@code ForceLayout}, when set, overrides all of them, for owners who want one look across
 * every mod. Which HUD parts are hidden follows the same chain, skipping the dialogue level.
 */
public final class PresentationResolver {

    /** How the resolver finds the pack a dialogue came from ("" for the server's own folder). */
    private final Function<Dialogue, String> packOf;
    private final Function<String, String> packOfId;
    private final Map<String, Presentation.Defaults> packFiles = new ConcurrentHashMap<>();
    private final Map<String, Presentation.Defaults> apiDefaults = new ConcurrentHashMap<>();
    private volatile Presentation.Defaults serverDefaults = Presentation.Defaults.NONE;
    private volatile DialogueLayout force;

    public PresentationResolver(@Nonnull Function<Dialogue, String> packOf, @Nonnull Function<String, String> packOfId) {
        this.packOf = packOf;
        this.packOfId = packOfId;
    }

    // ---- inputs

    public void setServerDefaults(@Nonnull Presentation.Defaults defaults, @Nullable DialogueLayout force) {
        this.serverDefaults = defaults;
        this.force = force;
    }

    /** Replace the file-based defaults of every pack (a reload). */
    public void setPackFiles(@Nonnull Map<String, Presentation.Defaults> byPack) {
        packFiles.clear();
        packFiles.putAll(byPack);
    }

    public void setApiDefaults(@Nonnull String pack, @Nullable Presentation.Defaults defaults) {
        if (defaults == null || defaults.isEmpty()) apiDefaults.remove(pack);
        else apiDefaults.put(pack, defaults);
    }

    @Nullable
    public Presentation.Defaults apiDefaults(@Nonnull String pack) {
        return apiDefaults.get(pack);
    }

    // ---- resolution

    @Nonnull
    public Presentation resolve(@Nonnull Dialogue dialogue) {
        String pack = packOf.apply(dialogue);
        if (pack == null) pack = "";
        Presentation.Defaults fromFile = packFiles.getOrDefault(pack, Presentation.Defaults.NONE);
        Presentation.Defaults fromApi = apiDefaults.getOrDefault(pack, Presentation.Defaults.NONE);

        DialogueLayout layout = force;
        if (layout == null) layout = DialogueLayout.parse(dialogue.otherDirectives().get("layout"));
        if (layout == null) layout = fromFile.layout();
        if (layout == null) layout = fromApi.layout();
        if (layout == null) layout = serverDefaults.layout();
        if (layout == null) layout = DialogueLayout.DEFAULT;

        List<String> hide = fromFile.hideHud();
        if (hide == null) hide = fromApi.hideHud();
        if (hide == null) hide = serverDefaults.hideHud();
        if (hide == null) hide = List.of();

        History history = History.parse(dialogue.otherDirectives().get("history"));
        if (history == null) history = fromFile.history();
        if (history == null) history = fromApi.history();
        if (history == null) history = serverDefaults.history();
        if (history == null) history = History.DEFAULT;

        return Presentation.of(layout, new ArrayList<>(hide), history);
    }

    /** What a dialogue with this id would get without a directive of its own, as "bottom, from pack X". */
    @Nonnull
    public String explainLayoutDefault(@Nonnull String dialogueId) {
        String pack = packOfId.apply(dialogueId);
        if (pack == null) pack = "";
        if (force != null) return force.key() + ", forced by the server config";
        Presentation.Defaults f = packFiles.get(pack);
        if (f != null && f.layout() != null) return f.layout().key() + ", from the pack's Settings.json";
        Presentation.Defaults a = apiDefaults.get(pack);
        if (a != null && a.layout() != null) return a.layout().key() + ", set by the pack's plugin";
        if (serverDefaults.layout() != null) return serverDefaults.layout().key() + ", the server config";
        return DialogueLayout.DEFAULT.key() + ", built in";
    }

    /** What a dialogue with this id would get for history without a directive of its own. */
    @Nonnull
    public String explainHistoryDefault(@Nonnull String dialogueId) {
        String pack = packOfId.apply(dialogueId);
        if (pack == null) pack = "";
        Presentation.Defaults f = packFiles.get(pack);
        if (f != null && f.history() != null) return f.history().key() + ", from the pack's Settings.json";
        Presentation.Defaults a = apiDefaults.get(pack);
        if (a != null && a.history() != null) return a.history().key() + ", set by the pack's plugin";
        if (serverDefaults.history() != null) return serverDefaults.history().key() + ", the server config";
        return History.DEFAULT.key() + ", built in";
    }

    /** The level that decided a dialogue's layout, for {@code /lowtalk} diagnostics. */
    @Nonnull
    public String explainLayout(@Nonnull Dialogue dialogue) {
        String pack = packOf.apply(dialogue);
        if (pack == null) pack = "";
        if (force != null) return "ForceLayout in the server config";
        if (DialogueLayout.parse(dialogue.otherDirectives().get("layout")) != null) return "the dialogue's layout: directive";
        Presentation.Defaults f = packFiles.get(pack);
        if (f != null && f.layout() != null) return "Settings.json of pack " + pack;
        Presentation.Defaults a = apiDefaults.get(pack);
        if (a != null && a.layout() != null) return "API defaults for pack " + pack;
        if (serverDefaults.layout() != null) return "the server config";
        return "the built-in default";
    }
}
