package com.chromecide.lowtalk.hytale.compat;

import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.AssetStore;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;

import java.util.logging.Level;

/**
 * Works around a load-order gap in Hytale 0.7.0-pre.2. The new {@code AttachBeam} interaction validates its
 * {@code BeamConfig} against the Beam asset store the moment the asset that embeds it is decoded. Stores that
 * embed interactions (ProjectileConfig for the vanilla Hookshot, RootInteraction, Item) declare that they load after
 * Interaction, but neither they nor Interaction declare anything about Beam, and the Beam store declares no
 * {@code loadsBefore}. Asset stores are kept in a hash map keyed by class, so whether Beam happens to load before
 * ProjectileConfig depends on identity hashes, which vary with the set of classes loaded before boot. When Beam
 * lands after ProjectileConfig the vanilla Hookshot fails to validate ("Asset 'Rope' of type Beam doesn't exist")
 * and the server refuses to boot. Verified both ways on pre.2: forcing Beam after ProjectileConfig breaks a layout
 * that otherwise boots, and the printed store order in a failing layout has Beam last.
 *
 * <p>The fix is one edge, Interaction loads after Beam, injected during plugin setup while stores can still take
 * dependencies; everything that loads after Interaction then loads after Beam too. It does nothing on servers
 * without a Beam asset type (release line 0.6.x), when the edge already exists, or if anything about the API has
 * moved; it can only add a constraint that is always correct, never remove one.
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
