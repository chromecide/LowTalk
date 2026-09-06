package com.chromecide.lowtalk.hytale.json;

import com.chromecide.lowtalk.LowTalkPlugin;
import com.chromecide.lowtalk.hytale.DialogueRegistry;
import com.chromecide.lowtalk.model.Dialogue;
import com.chromecide.lowtalk.parser.Outline;
import com.chromecide.lowtalk.parser.ParseException;
import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.event.LoadedAssetsEvent;
import com.hypixel.hytale.assetstore.event.RemovedAssetsEvent;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.builtin.asseteditor.AssetEditorPlugin;
import com.hypixel.hytale.builtin.asseteditor.AssetPath;
import com.hypixel.hytale.builtin.asseteditor.EditorClient;
import com.hypixel.hytale.builtin.asseteditor.event.AssetEditorSelectAssetEvent;
import com.hypixel.hytale.protocol.packets.asseteditor.AssetEditorPopupNotificationType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.HytaleAssetStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.logging.Level;

/**
 * The JSON dialogue asset type: registered as a normal game asset store, so it appears in the Asset Editor with the
 * schema-driven form editor and loads through the game's own asset pipeline. Loaded assets are converted to the
 * runtime model and handed to the {@link DialogueRegistry} alongside .talk files.
 */
public final class JsonDialogues {
    /** Asset store path under Server/. Shares the folder with .talk files; the store only reads .json. */
    public static final String PATH = "LowTalk/Dialogues";
    /** The Asset Editor's type id for this store is the asset class's simple name. */
    public static final String EDITOR_TYPE_ID = DialogueAsset.class.getSimpleName();

    /** Autocomplete data sets the form fields ask the server for. */
    public static final String DATASET_NPCS = "LowTalkNpcs";
    public static final String DATASET_WEATHERS = "LowTalkWeathers";
    public static final String DATASET_COMMANDS = "LowTalkCommands";
    private static final int MAX_SUGGESTIONS = 40;

    private static HytaleAssetStore<String, DialogueAsset, DefaultAssetMap<String, DialogueAsset>> store;

    private JsonDialogues() {}

    @Nullable
    public static HytaleAssetStore<String, DialogueAsset, DefaultAssetMap<String, DialogueAsset>> store() {
        return store;
    }

    /** Call from the plugin's setup(). */
    public static void register(@Nonnull LowTalkPlugin plugin) {
        JsonCodecs.register();
        store = AssetRegistry.register(
                HytaleAssetStore.builder(DialogueAsset.class, new DefaultAssetMap<>())
                        .setPath(PATH)
                        .setCodec(JsonCodecs.DIALOGUE)
                        .setKeyFunction(DialogueAsset::getId)
                        .build());
        plugin.getEventRegistry().<Class<DialogueAsset>, LoadedAssetsEvent<String, DialogueAsset, DefaultAssetMap<String, DialogueAsset>>>register(
                LoadedAssetsEvent.class, DialogueAsset.class, e -> onLoaded(plugin, e));
        plugin.getEventRegistry().<Class<DialogueAsset>, RemovedAssetsEvent<String, DialogueAsset, DefaultAssetMap<String, DialogueAsset>>>register(
                RemovedAssetsEvent.class, DialogueAsset.class, e -> onRemoved(plugin, e));
        plugin.getEventRegistry().register(AssetEditorSelectAssetEvent.class, e -> onSelect(plugin, e));
        plugin.getEventRegistry().register(com.hypixel.hytale.builtin.asseteditor.event.AssetEditorFetchAutoCompleteDataEvent.class, DATASET_NPCS,
                e -> e.setResults(npcSuggestions(plugin, e.getQuery())));
        plugin.getEventRegistry().register(com.hypixel.hytale.builtin.asseteditor.event.AssetEditorFetchAutoCompleteDataEvent.class, DATASET_WEATHERS,
                e -> e.setResults(weatherSuggestions(e.getQuery())));
        plugin.getEventRegistry().register(com.hypixel.hytale.builtin.asseteditor.event.AssetEditorFetchAutoCompleteDataEvent.class, DATASET_COMMANDS,
                e -> e.setResults(commandSuggestions(plugin, e.getQuery())));
    }

    /** NPC role ids from the NPC plugin plus every LowTalk tag in use, as @tag. */
    static String[] npcSuggestions(LowTalkPlugin plugin, String query) {
        String q = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        java.util.List<String> out = new java.util.ArrayList<>();
        java.util.Set<String> tags = new java.util.TreeSet<>(plugin.getStore().allTags());
        for (Dialogue d : plugin.getRegistry().all()) {
            for (String b : d.bindings()) if (b.startsWith("@") && b.length() > 1) tags.add(b.substring(1));
        }
        for (String t : tags) {
            String tagged = "@" + t;
            if (q.isEmpty() || tagged.toLowerCase(java.util.Locale.ROOT).contains(q) || t.toLowerCase(java.util.Locale.ROOT).contains(q)) out.add(tagged);
        }
        if (!q.startsWith("@")) {
            try {
                java.util.List<String> roles = new java.util.ArrayList<>(com.hypixel.hytale.server.npc.NPCPlugin.get().getRoleTemplateNames(false));
                java.util.Collections.sort(roles);
                for (String r : roles) {
                    if (q.isEmpty() || r.toLowerCase(java.util.Locale.ROOT).contains(q)) out.add(r);
                    if (out.size() >= MAX_SUGGESTIONS) break;
                }
            } catch (RuntimeException ignored) {
                // NPC plugin not ready; tags alone
            }
        }
        return out.size() > MAX_SUGGESTIONS ? out.subList(0, MAX_SUGGESTIONS).toArray(new String[0]) : out.toArray(new String[0]);
    }

    static String[] weatherSuggestions(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        java.util.List<String> out = new java.util.ArrayList<>();
        if (q.isEmpty() || "clear".contains(q)) out.add("clear");
        try {
            java.util.List<String> ids = new java.util.ArrayList<>(
                    com.hypixel.hytale.server.core.asset.type.weather.config.Weather.getAssetMap().getAssetMap().keySet());
            java.util.Collections.sort(ids);
            for (String id : ids) {
                if (id.equals("Unknown")) continue;
                if (q.isEmpty() || id.toLowerCase(java.util.Locale.ROOT).contains(q)) out.add(id);
                if (out.size() >= MAX_SUGGESTIONS) break;
            }
        } catch (RuntimeException ignored) {
            // weather assets not available
        }
        return out.toArray(new String[0]);
    }

    static String[] commandSuggestions(LowTalkPlugin plugin, String query) {
        String q = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        java.util.Set<String> names = new java.util.TreeSet<>(com.chromecide.lowtalk.parser.Validator.BUILTIN_COMMANDS.keySet());
        names.addAll(plugin.getEffects().names());
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String n : names) if (q.isEmpty() || n.contains(q)) out.add(n);
        return out.toArray(new String[0]);
    }

    private static void onLoaded(LowTalkPlugin plugin, LoadedAssetsEvent<String, DialogueAsset, DefaultAssetMap<String, DialogueAsset>> event) {
        for (Map.Entry<String, DialogueAsset> entry : event.getLoadedAssets().entrySet()) {
            DialogueAsset asset = entry.getValue();
            String id = asset.getId();
            String display = id + ".json";
            DialogueRegistry.LoadReport report;
            try {
                Dialogue d = JsonConvert.toModel(asset, display);
                report = plugin.getRegistry().loadAsset(id, display, d, !event.isInitial());
            } catch (ParseException e) {
                plugin.getRegistry().removeAsset(id);
                report = new DialogueRegistry.LoadReport(1, 0, 1, 0, java.util.List.of("error " + e.getMessage()));
            }
            for (String m : report.messages()) {
                plugin.getLogger().at(m.startsWith("error") ? Level.WARNING : Level.INFO).log("[json dialogue] %s", m);
            }
            if (!event.isInitial()) notifyEditors(plugin, id, report);
        }
    }

    private static void onRemoved(LowTalkPlugin plugin, RemovedAssetsEvent<String, DialogueAsset, DefaultAssetMap<String, DialogueAsset>> event) {
        for (String key : event.getRemovedAssets()) {
            if (plugin.getRegistry().removeAsset(String.valueOf(key))) {
                plugin.getLogger().at(Level.INFO).log("[json dialogue] removed %s", key);
            }
        }
    }

    /** Tell whoever has this file open in the Asset Editor how the save went. */
    private static void notifyEditors(LowTalkPlugin plugin, String id, DialogueRegistry.LoadReport report) {
        AssetEditorPlugin editor;
        try {
            editor = AssetEditorPlugin.get();
        } catch (RuntimeException e) {
            return;
        }
        if (editor == null) return;
        String fileName = id + ".json";
        for (Map.Entry<EditorClient, AssetPath> e : editor.getClientOpenAssetPathMapping().entrySet()) {
            AssetPath path = e.getValue();
            if (path == null || path.path().getFileName() == null || !path.path().getFileName().toString().equals(fileName)) continue;
            EditorClient client = e.getKey();
            if (report.errors() > 0) {
                client.sendPopupNotification(AssetEditorPopupNotificationType.Error,
                        Message.raw("LowTalk: " + fileName + " has " + report.errors() + " error(s) and is not loaded.\n" + first(report, true)));
            } else if (report.warnings() > 0) {
                client.sendPopupNotification(AssetEditorPopupNotificationType.Warning,
                        Message.raw("LowTalk: " + fileName + " loaded with " + report.warnings() + " warning(s).\n" + first(report, false)));
            } else {
                client.sendPopupNotification(AssetEditorPopupNotificationType.Success,
                        Message.raw("LowTalk: " + fileName + " loaded. Talk to a bound NPC or use /lowtalk open " + id + " to try it."));
            }
        }
    }

    private static String first(DialogueRegistry.LoadReport report, boolean errorsOnly) {
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (String m : report.messages()) {
            if (errorsOnly && !m.startsWith("error")) continue;
            if (m.startsWith("skipped")) continue;
            if (shown == 4) {
                sb.append("...");
                break;
            }
            if (shown > 0) sb.append('\n');
            sb.append(m);
            shown++;
        }
        return sb.toString();
    }

    private static void onSelect(LowTalkPlugin plugin, AssetEditorSelectAssetEvent event) {
        if (!EDITOR_TYPE_ID.equals(event.getAssetType()) || event.getAssetFilePath() == null) return;
        String name = event.getAssetFilePath().path().getFileName().toString();
        String id = name.endsWith(".json") ? name.substring(0, name.length() - 5) : name;
        Dialogue d = plugin.getRegistry().byId(id);
        if (d == null) {
            event.getEditorClient().sendPopupNotification(AssetEditorPopupNotificationType.Warning,
                    Message.raw("LowTalk: " + name + " is not loaded (see the server log, or save it again to see why)."));
            return;
        }
        event.getEditorClient().sendPopupNotification(AssetEditorPopupNotificationType.Info,
                Message.raw("LowTalk: loaded.\n" + String.join("\n", Outline.of(d).lines())));
    }
}
