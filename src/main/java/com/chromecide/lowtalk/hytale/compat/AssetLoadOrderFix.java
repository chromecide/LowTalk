package com.chromecide.lowtalk.hytale.compat;

import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.AssetStore;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;

import java.util.logging.Level;

/**
 * Works around a load-order gap in Hytale 0.7.0-pre.2: the Interaction asset store validates {@code BeamConfig}
 * references against the Beam asset store, but does not declare that it loads after it. Asset stores are kept in a
 * hash map keyed by class, so whether Beam happens to load first depends on identity hashes, which change with the
 * set of classes loaded before boot. When the order comes out wrong the vanilla Hookshot interaction fails to
 * validate ("Asset 'Rope' of type Beam doesn't exist") and the server refuses to boot.
 *
 * <p>The fix is one missing edge, injected during plugin setup while stores can still take dependencies. It does
 * nothing on servers without a Beam asset type (release line 0.6.x), when the edge already exists, or if anything
 * about the API has moved; it can only add a constraint that is always correct, never remove one.
 */
public final class AssetLoadOrderFix {
    private static final String BEAM_CLASS = "com.hypixel.hytale.builtin.beam.asset.Beam";

    private AssetLoadOrderFix() {
    }

    @SuppressWarnings({"unchecked", "rawtypes", "deprecation"})
    public static void apply(HytaleLogger logger) {
        try {
            Class<?> beam = Class.forName(BEAM_CLASS);
            AssetStore interactions = AssetRegistry.getAssetStore((Class) Interaction.class);
            if (interactions == null || AssetRegistry.getAssetStore((Class) beam) == null) {
                return;
            }
            if (interactions.getLoadsAfter().contains(beam)) {
                return;
            }
            interactions.injectLoadsAfter((Class) beam);
            logger.at(Level.INFO).log("Interaction assets now load after Beam assets (missing load-order edge in this server version)");
        } catch (ClassNotFoundException ignored) {
            // No Beam asset type on this server line; nothing to fix.
        } catch (RuntimeException | LinkageError e) {
            logger.at(Level.FINE).log("Asset load-order fix not applied: %s", e.toString());
        }
    }
}
