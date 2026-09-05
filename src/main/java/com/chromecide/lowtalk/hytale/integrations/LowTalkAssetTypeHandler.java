package com.chromecide.lowtalk.hytale.integrations;

import com.chromecide.lowtalk.LowTalkPlugin;
import com.chromecide.lowtalk.hytale.DialogueRegistry;
import com.hypixel.hytale.assetstore.AssetPack;
import com.hypixel.hytale.assetstore.AssetUpdateQuery;
import com.hypixel.hytale.builtin.asseteditor.EditorClient;
import com.hypixel.hytale.builtin.asseteditor.assettypehandler.AssetTypeHandler;
import com.hypixel.hytale.builtin.asseteditor.event.AssetEditorSelectAssetEvent;
import com.hypixel.hytale.builtin.asseteditor.AssetPath;
import com.hypixel.hytale.protocol.packets.asseteditor.AssetEditorAssetType;
import com.hypixel.hytale.protocol.packets.asseteditor.AssetEditorEditorType;
import com.hypixel.hytale.protocol.packets.asseteditor.AssetEditorPopupNotificationType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.AssetModule;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;

/**
 * Makes .talk files a first-class asset type in the game's own Asset Editor. Creators open an asset pack in the
 * editor, add files under {@code Server/LowTalk/Dialogues}, and edit them in the editor's text mode. Every save is
 * parsed, validated and hot-loaded here; problems come back as editor notifications with file and line numbers.
 * Same shape as the game's NPCRole handler in the NPCEditor plugin.
 */
public final class LowTalkAssetTypeHandler extends AssetTypeHandler {
    public static final String TYPE_ID = "LowTalk";
    private static final int MAX_PROBLEMS_SHOWN = 4;

    private final LowTalkPlugin plugin;

    public LowTalkAssetTypeHandler(@Nonnull LowTalkPlugin plugin) {
        super(new AssetEditorAssetType(TYPE_ID, null, false, DialogueRegistry.PACK_DIR, ".talk", AssetEditorEditorType.Text));
        this.plugin = plugin;
    }

    @Override
    public AssetLoadResult loadAsset(AssetPath path, Path dataPath, byte[] data, AssetUpdateQuery updateQuery, EditorClient editorClient) {
        String source = data == null ? null : new String(data, StandardCharsets.UTF_8);
        DialogueRegistry.LoadReport report = plugin.getRegistry().loadFile(dataPath, source, true);
        report(editorClient, dataPath, report, false);
        return AssetLoadResult.ASSETS_CHANGED;
    }

    @Override
    public AssetLoadResult unloadAsset(AssetPath path, AssetUpdateQuery updateQuery) {
        Path file = resolve(path);
        if (file != null && plugin.getRegistry().unloadFile(file)) {
            plugin.getLogger().at(Level.INFO).log("Asset Editor removed dialogue file %s", path.path());
        }
        return AssetLoadResult.ASSETS_CHANGED;
    }

    @Override
    public AssetLoadResult restoreOriginalAsset(AssetPath originalAssetPath, AssetUpdateQuery updateQuery) {
        Path file = resolve(originalAssetPath);
        if (file != null) plugin.getRegistry().loadFile(file, null, true);
        return AssetLoadResult.ASSETS_CHANGED;
    }

    @Override
    public AssetUpdateQuery getDefaultUpdateQuery() {
        return AssetUpdateQuery.DEFAULT_NO_REBUILD;
    }

    /** Always save; problems are reported through notifications so work in progress is never lost. */
    @Override
    public boolean isValidData(byte[] data) {
        return true;
    }

    /** When a creator clicks a dialogue in the editor, show what is loaded from it or why it is not. */
    public static void onSelect(@Nonnull LowTalkPlugin plugin, @Nonnull AssetEditorSelectAssetEvent event) {
        if (!TYPE_ID.equals(event.getAssetType()) || event.getAssetFilePath() == null) return;
        Path file = resolve(event.getAssetFilePath());
        if (file == null) return;
        DialogueRegistry.Loaded loaded = plugin.getRegistry().forFile(file);
        if (loaded != null) {
            var d = loaded.dialogue();
            String bindings = d.bindings().isEmpty() ? "no npc: binding" : "bound to " + String.join(", ", d.bindings());
            event.getEditorClient().sendPopupNotification(AssetEditorPopupNotificationType.Info,
                    Message.raw("LowTalk: '" + d.id() + "' is loaded, " + d.nodes().size() + " node(s), " + bindings + "."));
            return;
        }
        if (file.getFileName().toString().startsWith("_")) {
            event.getEditorClient().sendPopupNotification(AssetEditorPopupNotificationType.Info,
                    Message.raw("LowTalk: files starting with _ are shared through include: and are not dialogues on their own."));
            return;
        }
        // Not loaded: re-run the checks so the creator sees why.
        DialogueRegistry.LoadReport report = plugin.getRegistry().loadFile(file, null, true);
        report(event.getEditorClient(), file, report, true);
    }

    private static void report(@Nullable EditorClient client, Path file, DialogueRegistry.LoadReport report, boolean quietWhenFine) {
        LowTalkPlugin plugin = LowTalkPlugin.get();
        String name = file.getFileName().toString();
        for (String m : report.messages()) {
            plugin.getLogger().at(m.startsWith("error") ? Level.WARNING : Level.INFO).log("[asset editor] %s", m);
        }
        if (client == null) return;
        if (report.errors() > 0) {
            client.sendPopupNotification(AssetEditorPopupNotificationType.Error,
                    Message.raw("LowTalk: " + name + " has " + report.errors() + " error(s) and is not loaded.\n" + firstProblems(report.messages(), true)));
        } else if (report.warnings() > 0) {
            client.sendPopupNotification(AssetEditorPopupNotificationType.Warning,
                    Message.raw("LowTalk: " + name + " loaded with " + report.warnings() + " warning(s).\n" + firstProblems(report.messages(), false)));
        } else if (!quietWhenFine) {
            client.sendPopupNotification(AssetEditorPopupNotificationType.Success,
                    Message.raw("LowTalk: " + name + " loaded. Talk to a bound NPC or use /lowtalk open to try it."));
        }
    }

    private static String firstProblems(List<String> messages, boolean errorsOnly) {
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (String m : messages) {
            if (errorsOnly && !m.startsWith("error")) continue;
            if (m.startsWith("skipped")) continue;
            if (shown == MAX_PROBLEMS_SHOWN) {
                sb.append("...");
                break;
            }
            if (shown > 0) sb.append('\n');
            sb.append(m);
            shown++;
        }
        return sb.toString();
    }

    /** Absolute file for an editor path, or null if its pack is unknown. */
    @Nullable
    private static Path resolve(AssetPath path) {
        try {
            AssetPack pack = AssetModule.get().getAssetPack(path.packId());
            return pack == null ? null : pack.getRoot().resolve(path.path()).toAbsolutePath().normalize();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
