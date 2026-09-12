package com.chromecide.lowtalk.hytale.presentation;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.codec.util.RawJsonReader;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.AssetModule;
import com.hypixel.hytale.assetstore.AssetPack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * A pack's presentation defaults: {@code Server/LowTalk/Settings.json} next to its dialogues. A mod author sets the
 * look of every dialogue in their pack once, without touching the server config.
 *
 * <pre>
 * { "Layout": "bottom", "HideHud": ["Reticle", "Hotbar"] }
 * </pre>
 * Both keys are optional; a missing key leaves that decision to the next level down.
 */
public final class PackSettings {

    public static final String FILE = "Settings.json";
    public static final String PACK_DIR = "Server/LowTalk";

    public static final BuilderCodec<PackSettings> CODEC = BuilderCodec.builder(PackSettings.class, PackSettings::new)
            .append(new KeyedCodec<>("Layout", Codec.STRING), (s, v) -> s.layout = v, s -> s.layout)
            .documentation("Where dialogues from this pack appear: window, bottom or top.").add()
            .<String[]>append(new KeyedCodec<>("HideHud", new ArrayCodec<>(Codec.STRING, String[]::new)), (s, v) -> s.hideHud = v, s -> s.hideHud)
            .documentation("HUD parts hidden while a dialogue from this pack is open, e.g. Reticle, Hotbar, Compass.").add()
            .append(new KeyedCodec<>("History", Codec.STRING), (s, v) -> s.history = v, s -> s.history)
            .documentation("How much stays on screen: full (the whole transcript) or latest (only the NPC's current line).").add()
            .build();

    private String layout;
    private String[] hideHud;
    private String history;

    private PackSettings() {}

    @Nonnull
    public Presentation.Defaults toDefaults(@Nonnull String pack, @Nonnull HytaleLogger logger) {
        DialogueLayout l = null;
        if (layout != null && !layout.isBlank()) {
            l = DialogueLayout.parse(layout);
            if (l == null) logger.at(Level.WARNING).log("%s/%s of pack %s: unknown Layout '%s' (use %s)", PACK_DIR, FILE, pack, layout, DialogueLayout.keys());
        }
        List<String> hide = hideHud == null ? null : Arrays.asList(hideHud);
        History h = null;
        if (history != null && !history.isBlank()) {
            h = History.parse(history);
            if (h == null) logger.at(Level.WARNING).log("%s/%s of pack %s: unknown History '%s' (use %s)", PACK_DIR, FILE, pack, history, History.keys());
        }
        return new Presentation.Defaults(l, hide, h);
    }

    /** Parse one settings file; null on a malformed file (logged). */
    @Nullable
    public static Presentation.Defaults read(@Nonnull Path file, @Nonnull String pack, @Nonnull HytaleLogger logger) {
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            PackSettings s = CODEC.decodeJson(RawJsonReader.fromJsonString(text), new ExtraInfo());
            return s == null ? null : s.toDefaults(pack, logger);
        } catch (Exception e) {
            logger.at(Level.WARNING).log("Could not read %s of pack %s: %s", file.getFileName(), pack, e.toString());
            return null;
        }
    }

    /** Scan every loaded asset pack for a settings file. Keys are pack names as the asset module reports them. */
    @Nonnull
    public static Map<String, Presentation.Defaults> scan(@Nonnull HytaleLogger logger) {
        Map<String, Presentation.Defaults> out = new LinkedHashMap<>();
        AssetModule assets;
        try {
            assets = AssetModule.get();
        } catch (RuntimeException e) {
            return out;
        }
        if (assets == null) return out;
        for (AssetPack pack : assets.getAssetPacks()) {
            try {
                Path file = pack.getRoot().resolve(PACK_DIR).resolve(FILE);
                if (!Files.isRegularFile(file)) continue;
                Presentation.Defaults d = read(file, pack.getName(), logger);
                if (d != null && !d.isEmpty()) {
                    out.put(pack.getName(), d);
                    logger.at(Level.INFO).log("Pack %s sets dialogue defaults: layout=%s hideHud=%s history=%s", pack.getName(),
                            d.layout() == null ? "(inherit)" : d.layout().key(), d.hideHud() == null ? "(inherit)" : d.hideHud(),
                            d.history() == null ? "(inherit)" : d.history().key());
                }
            } catch (RuntimeException ignored) {
                // packs inside archives may not resolve
            }
        }
        return out;
    }
}
