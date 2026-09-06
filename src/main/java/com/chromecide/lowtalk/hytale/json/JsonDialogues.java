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
    public static final String EDITOR_TYPE_ID = LowTalkJson.class.getSimpleName();

    /** Autocomplete data sets the form fields ask the server for. */
    public static final String DATASET_NPCS = "LowTalkNpcs";
    public static final String DATASET_WEATHERS = "LowTalkWeathers";
    public static final String DATASET_COMMANDS = "LowTalkCommands";
    public static final String DATASET_ROLES = "LowTalkRoles";
    public static final String DATASET_ATTITUDES = "LowTalkAttitudes";
    public static final String DATASET_ANIMATIONS = "LowTalkAnimations";
    public static final String DATASET_ANIMATION_SLOTS = "LowTalkAnimationSlots";
    public static final String DATASET_NOTIFY_STYLES = "LowTalkNotifyStyles";
    public static final String DATASET_STATS = "LowTalkStats";
    public static final String DATASET_RECIPES = "LowTalkRecipes";
    public static final String DATASET_WARPS = "LowTalkWarps";
    public static final String DATASET_TIMES = "LowTalkTimes";
    public static final String DATASET_REPUTATION_GROUPS = "LowTalkReputationGroups";
    public static final String DATASET_SHOPS = "LowTalkShops";
    /** Loaded dialogue ids: for the trigger tool's picker and for Dialogue fields in interaction JSON. */
    public static final String DATASET_DIALOGUES = "LowTalkDialogues";
    /** Every node name in every loaded dialogue, for objective task fields. */
    public static final String DATASET_NODES = "LowTalkNodes";
    public static final String DATASET_MUSIC = "LowTalkMusic";
    public static final String DATASET_PARTICLES = "LowTalkParticles";
    public static final String DATASET_CAMERA_EFFECTS = "LowTalkCameraEffects";
    private static final int MAX_SUGGESTIONS = 40;

    private static HytaleAssetStore<String, LowTalkJson, DefaultAssetMap<String, LowTalkJson>> store;

    private JsonDialogues() {}

    @Nullable
    public static HytaleAssetStore<String, LowTalkJson, DefaultAssetMap<String, LowTalkJson>> store() {
        return store;
    }

    /** Call from the plugin's setup(). */
    public static void register(@Nonnull LowTalkPlugin plugin) {
        JsonCodecs.register();
        store = AssetRegistry.register(
                HytaleAssetStore.builder(LowTalkJson.class, new DefaultAssetMap<>())
                        .setPath(PATH)
                        .setCodec(JsonCodecs.DIALOGUE)
                        .setKeyFunction(LowTalkJson::getId)
                        .build());
        plugin.getEventRegistry().<Class<LowTalkJson>, LoadedAssetsEvent<String, LowTalkJson, DefaultAssetMap<String, LowTalkJson>>>register(
                LoadedAssetsEvent.class, LowTalkJson.class, e -> onLoaded(plugin, e));
        plugin.getEventRegistry().<Class<LowTalkJson>, RemovedAssetsEvent<String, LowTalkJson, DefaultAssetMap<String, LowTalkJson>>>register(
                RemovedAssetsEvent.class, LowTalkJson.class, e -> onRemoved(plugin, e));
        plugin.getEventRegistry().register(AssetEditorSelectAssetEvent.class, e -> onSelect(plugin, e));
        plugin.getEventRegistry().register(com.hypixel.hytale.builtin.asseteditor.event.AssetEditorFetchAutoCompleteDataEvent.class, DATASET_NPCS,
                e -> e.setResults(npcSuggestions(plugin, e.getQuery())));
        plugin.getEventRegistry().register(com.hypixel.hytale.builtin.asseteditor.event.AssetEditorFetchAutoCompleteDataEvent.class, DATASET_WEATHERS,
                e -> e.setResults(weatherSuggestions(e.getQuery())));
        plugin.getEventRegistry().register(com.hypixel.hytale.builtin.asseteditor.event.AssetEditorFetchAutoCompleteDataEvent.class, DATASET_COMMANDS,
                e -> e.setResults(commandSuggestions(plugin, e.getQuery())));
        DATASETS.put(DATASET_NPCS, () -> java.util.Arrays.asList(npcSuggestions(plugin, "")));
        DATASETS.put(DATASET_WEATHERS, () -> java.util.Arrays.asList(weatherSuggestions("")));
        DATASETS.put(DATASET_COMMANDS, () -> java.util.Arrays.asList(commandSuggestions(plugin, "")));
        dataset(plugin, DATASET_ROLES, () -> new java.util.ArrayList<>(com.hypixel.hytale.server.npc.NPCPlugin.get().getRoleTemplateNames(false)));
        dataset(plugin, DATASET_DIALOGUES, () -> plugin.getRegistry().ids());
        dataset(plugin, DATASET_NODES, () -> {
            java.util.Set<String> names = new java.util.TreeSet<>();
            for (Dialogue d : plugin.getRegistry().all()) names.addAll(d.nodes().keySet());
            return new java.util.ArrayList<>(names);
        });
        dataset(plugin, DATASET_ATTITUDES, () -> java.util.List.of("ignore", "hostile", "neutral", "friendly", "revered"));
        dataset(plugin, DATASET_ANIMATION_SLOTS, () -> java.util.List.of("Emote", "Status", "Action", "Movement", "Face", "ServerAction"));
        dataset(plugin, DATASET_NOTIFY_STYLES, () -> java.util.List.of("success", "warning", "danger"));
        dataset(plugin, DATASET_TIMES, () -> java.util.List.of("dawn", "noon", "dusk", "midnight", "pause", "resume", "6", "12", "18", "0"));
        dataset(plugin, DATASET_ANIMATIONS, () -> {
            java.util.Set<String> names = new java.util.TreeSet<>();
            for (com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset m
                    : com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset.getAssetMap().getAssetMap().values()) {
                if (m.getAnimationSetMap() != null) names.addAll(m.getAnimationSetMap().keySet());
            }
            return new java.util.ArrayList<>(names);
        });
        dataset(plugin, DATASET_STATS, () -> new java.util.ArrayList<>(
                com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType.getAssetMap().getAssetMap().keySet()));
        dataset(plugin, DATASET_RECIPES, () -> new java.util.ArrayList<>(
                com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe.getAssetMap().getAssetMap().keySet()));
        dataset(plugin, DATASET_WARPS, () -> {
            com.hypixel.hytale.builtin.teleport.TeleportPlugin tp = com.hypixel.hytale.builtin.teleport.TeleportPlugin.get();
            return tp == null || !tp.isWarpsLoaded() ? java.util.List.<String>of() : new java.util.ArrayList<>(tp.getWarps().keySet());
        });
        dataset(plugin, DATASET_REPUTATION_GROUPS, () -> new java.util.ArrayList<>(
                com.hypixel.hytale.builtin.adventure.reputation.assets.ReputationGroup.getAssetMap().getAssetMap().keySet()));
        dataset(plugin, DATASET_MUSIC, () -> {
            java.util.List<String> out = new java.util.ArrayList<>(
                    com.hypixel.hytale.server.core.asset.type.musiccontainer.config.MusicContainer.getAssetMap().getAssetMap().keySet());
            out.add("clear");
            return out;
        });
        dataset(plugin, DATASET_PARTICLES, () -> new java.util.ArrayList<>(
                com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSystem.getAssetMap().getAssetMap().keySet()));
        dataset(plugin, DATASET_CAMERA_EFFECTS, () -> new java.util.ArrayList<>(
                com.hypixel.hytale.server.core.asset.type.camera.CameraEffect.getAssetMap().getAssetMap().keySet()));
        dataset(plugin, DATASET_SHOPS, () -> new java.util.ArrayList<>(
                com.hypixel.hytale.builtin.adventure.shop.barter.BarterShopAsset.getAssetMap().getAssetMap().keySet()));
    }

    /** Register an autocomplete data set: the supplier's names, filtered by the typed prefix or fragment, sorted. */
    private static final java.util.Map<String, java.util.function.Supplier<java.util.List<String>>> DATASETS = new java.util.concurrent.ConcurrentHashMap<>();

    /** The full, sorted contents of a data set (for in-game pickers); empty when unknown or unavailable. */
    public static java.util.List<String> names(String id) {
        java.util.function.Supplier<java.util.List<String>> s = DATASETS.get(id);
        if (s == null) return java.util.List.of();
        try {
            java.util.List<String> all = new java.util.ArrayList<>(s.get());
            all.removeIf(java.util.Objects::isNull);
            java.util.Collections.sort(all);
            return all;
        } catch (RuntimeException e) {
            return java.util.List.of();
        }
    }

    /** Register a named list for pickers and Asset Editor autocomplete (other plugins use LowTalkApi.registerDataSet). */
    public static void registerDataSet(LowTalkPlugin plugin, String id, java.util.function.Supplier<java.util.List<String>> names) {
        dataset(plugin, id, names);
    }

    private static void dataset(LowTalkPlugin plugin, String id, java.util.function.Supplier<java.util.List<String>> names) {
        DATASETS.put(id, names);
        plugin.getEventRegistry().register(com.hypixel.hytale.builtin.asseteditor.event.AssetEditorFetchAutoCompleteDataEvent.class, id, e -> {
            String q = e.getQuery() == null ? "" : e.getQuery().trim().toLowerCase(java.util.Locale.ROOT);
            java.util.List<String> out = new java.util.ArrayList<>();
            try {
                java.util.List<String> all = new java.util.ArrayList<>(names.get());
                java.util.Collections.sort(all);
                for (String n : all) {
                    if (n == null) continue;
                    if (q.isEmpty() || n.toLowerCase(java.util.Locale.ROOT).contains(q)) out.add(n);
                    if (out.size() >= MAX_SUGGESTIONS) break;
                }
            } catch (RuntimeException ex) {
                plugin.getLogger().at(Level.FINE).log("data set %s unavailable: %s", id, ex.toString());
            }
            e.setResults(out.toArray(new String[0]));
        });
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

    /** Only commands without a typed statement: every built-in has one, so this is what other plugins add. */
    static String[] commandSuggestions(LowTalkPlugin plugin, String query) {
        String q = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        java.util.Set<String> names = new java.util.TreeSet<>(plugin.getEffects().names());
        names.removeAll(com.chromecide.lowtalk.parser.Validator.BUILTIN_COMMANDS.keySet());
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String n : names) if (q.isEmpty() || n.contains(q)) out.add(n);
        return out.toArray(new String[0]);
    }

    private static void onLoaded(LowTalkPlugin plugin, LoadedAssetsEvent<String, LowTalkJson, DefaultAssetMap<String, LowTalkJson>> event) {
        for (Map.Entry<String, LowTalkJson> entry : event.getLoadedAssets().entrySet()) {
            LowTalkJson asset = entry.getValue();
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

    private static void onRemoved(LowTalkPlugin plugin, RemovedAssetsEvent<String, LowTalkJson, DefaultAssetMap<String, LowTalkJson>> event) {
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
