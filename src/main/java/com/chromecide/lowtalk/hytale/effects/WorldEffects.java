package com.chromecide.lowtalk.hytale.effects;

import com.chromecide.lowtalk.LowTalkPlugin;
import com.chromecide.lowtalk.hytale.EffectHost;
import com.chromecide.lowtalk.hytale.EffectRegistry;
import com.chromecide.lowtalk.hytale.functions.BuiltinFunctions;
import com.chromecide.lowtalk.runtime.RuntimeError;
import com.hypixel.hytale.builtin.weather.components.WeatherTracker;
import com.hypixel.hytale.builtin.weather.resources.WeatherResource;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.weather.config.Weather;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Locale;
import java.util.Map;

/**
 * Weather and time, done the way the game's own /weather and /time commands and its SetWeatherEffect /
 * TimeEffect trigger effects do it: WeatherResource + WorldConfig for the world, WeatherTracker for one player,
 * WorldTimeResource for the clock.
 */
public final class WorldEffects {
    /** Named times of day, matching the game's /time command: daylight is 60% of the day. */
    public static final Map<String, Double> TIMES_OF_DAY = Map.of(
            "dawn", 0.2, "day", 0.2, "morning", 0.2,
            "noon", 0.5, "midday", 0.5,
            "dusk", 0.8, "night", 0.8, "evening", 0.8,
            "midnight", 0.0
    );
    /** How long a per-player weather change takes to blend in; the game's trigger effect uses the same. */
    private static final float WEATHER_TRANSITION_SECONDS = 2.0f;

    private WorldEffects() {}

    public static void register(@Nonnull EffectRegistry effects, @Nonnull LowTalkPlugin plugin) {

        // <<weather Id>> world, <<weather Id player>> just this player, <<weather clear [player]>>
        effects.register("weather", (session, effect) -> {
            String id = effect.args().get(0).trim();
            boolean playerOnly = effect.args().size() > 1 && effect.args().get(1).trim().equalsIgnoreCase("player");
            boolean clear = id.equalsIgnoreCase("clear") || id.equalsIgnoreCase("reset") || id.equalsIgnoreCase("none");
            Ref<EntityStore> ref = BuiltinFunctions.playerEntity(session.getContext());
            Store<EntityStore> store = ref.getStore();
            World world = store.getExternalData().getWorld();
            WeatherResource resource = store.getResource(WeatherResource.getResourceType());
            if (resource == null) throw new RuntimeError(effect.pos(), "the weather system is not available in this world");
            int index = clear ? 0 : Weather.getAssetMap().getIndex(id);
            if (!clear && (index == Integer.MIN_VALUE || index == 0)) {
                throw new RuntimeError(effect.pos(), "no weather called '" + id + "' (see Server/Weathers in the assets)");
            }
            WeatherTracker tracker = store.getComponent(ref, WeatherTracker.getComponentType());
            if (playerOnly) {
                if (tracker == null) return null;
                if (clear) {
                    // Drop the override, then let the tracker's own routine pick what this player should see:
                    // the world's forced weather, else the area's natural weather.
                    tracker.clearOverrideWeatherIndex();
                    TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
                    if (transform != null) {
                        tracker.updateWeather(session.getPlayer(), resource, transform, WEATHER_TRANSITION_SECONDS, store);
                    }
                    if (naturalWeather(resource, tracker) <= 0) {
                        // Nothing natural to fall back to (flat/void worlds): the client ignores "no weather", so
                        // show the configured clear sky instead, as an override so the per-second tick keeps it.
                        int sky = clearSkyIndex(plugin, effect);
                        tracker.setOverrideWeatherIndex(sky);
                        tracker.sendWeatherIndex(session.getPlayer(), sky, WEATHER_TRANSITION_SECONDS);
                    }
                } else {
                    tracker.setOverrideWeatherIndex(index);
                    tracker.sendWeatherIndex(session.getPlayer(), index, WEATHER_TRANSITION_SECONDS);
                }
            } else {
                String forced = clear ? null : id;
                if (clear && tracker != null && naturalWeather(resource, tracker) <= 0) {
                    // No natural weather here, so "clear" means the configured clear sky for everyone.
                    forced = plugin.getSettings().getClearSkyWeather();
                    clearSkyIndex(plugin, effect); // validates the id
                }
                resource.setForcedWeather(forced);
                if (world != null) {
                    world.getWorldConfig().setForcedWeather(forced);
                    world.getWorldConfig().markChanged();
                }
            }
            return null;
        });

        // <<time noon>>, <<time 19.5>>, <<time dusk 5>> (fade over 5 s), <<time pause>>, <<time resume>>
        effects.register("time", (session, effect) -> {
            String what = effect.args().get(0).trim().toLowerCase(Locale.ROOT);
            Ref<EntityStore> ref = BuiltinFunctions.playerEntity(session.getContext());
            Store<EntityStore> store = ref.getStore();
            World world = store.getExternalData().getWorld();
            if (world == null) return null;
            if (what.equals("pause") || what.equals("stop")) {
                world.getWorldConfig().setGameTimePaused(true);
                world.getWorldConfig().markChanged();
                return null;
            }
            if (what.equals("resume") || what.equals("start") || what.equals("unpause")) {
                world.getWorldConfig().setGameTimePaused(false);
                world.getWorldConfig().markChanged();
                return null;
            }
            double target = dayFraction(what, effect);
            double fade = 0.0;
            if (effect.args().size() > 1) {
                try {
                    fade = Double.parseDouble(effect.args().get(1).trim());
                } catch (NumberFormatException e) {
                    throw new RuntimeError(effect.pos(), "<<time>> fade must be a number of seconds, got " + effect.args().get(1));
                }
            }
            WorldTimeResource time = store.getResource(WorldTimeResource.getResourceType());
            if (time == null) throw new RuntimeError(effect.pos(), "the time system is not available in this world");
            if (fade > 0.0) {
                time.startDayTimeInterpolation(target, fade, true, false, world, store);
            } else {
                time.setDayTime(target, world, store);
            }
            return null;
        });
    }

    /** The area's natural weather index for this player, 0 or less when the world has none. */
    private static int naturalWeather(WeatherResource resource, WeatherTracker tracker) {
        try {
            int idx = resource.getWeatherIndexForEnvironment(tracker.getEnvironmentId());
            return idx == Integer.MIN_VALUE ? 0 : idx;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static int clearSkyIndex(LowTalkPlugin plugin, com.chromecide.lowtalk.runtime.Effect effect) {
        String id = plugin.getSettings().getClearSkyWeather();
        int idx = Weather.getAssetMap().getIndex(id);
        if (idx == Integer.MIN_VALUE || idx == 0) {
            throw new RuntimeError(effect.pos(), "ClearSkyWeather '" + id + "' in lowtalk.json is not a weather id");
        }
        return idx;
    }

    /** Registers the media commands: music, vfx, camera. Same calls as the game's SetMusic, PlayVfx and camera-shake paths. */
    public static void registerMedia(@Nonnull EffectRegistry effects, @Nonnull LowTalkPlugin plugin) {
        // <<music Music_Container_Id>> forces a playlist for this player; <<music clear>> returns to the area's music
        effects.register("music", (session, effect) -> {
            String id = effect.args().get(0).trim();
            Ref<EntityStore> ref = BuiltinFunctions.playerEntity(session.getContext());
            Store<EntityStore> store = ref.getStore();
            com.hypixel.hytale.builtin.audio.components.ForcedMusicTracker tracker =
                    store.getComponent(ref, com.hypixel.hytale.builtin.audio.components.ForcedMusicTracker.getComponentType());
            if (tracker == null) throw new RuntimeError(effect.pos(), "the music system is not available for this player");
            if (id.equalsIgnoreCase("clear") || id.equalsIgnoreCase("none")) {
                tracker.setCurrentContainerIndex(0);
                return null;
            }
            int index = com.hypixel.hytale.server.core.asset.type.musiccontainer.config.MusicContainer.getAssetMap().getIndex(id);
            if (index <= 0) throw new RuntimeError(effect.pos(), "no music container called '" + id + "' (see Server/Audio/MusicContainers)");
            tracker.setCurrentContainerIndex(index);
            return null;
        });

        // <<vfx Particle_System_Id [scale] [seconds]>> at the NPC, or at the player when there is no NPC
        effects.register("vfx", (session, effect) -> {
            String id = effect.args().get(0).trim();
            if (com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSystem.getAssetMap().getAsset(id) == null) {
                throw new RuntimeError(effect.pos(), "no particle system called '" + id + "' (see Server/Particles)");
            }
            float scale = effect.args().size() > 1 ? parseFloat(effect, effect.args().get(1), "scale") : 1.0f;
            float seconds = effect.args().size() > 2 ? parseFloat(effect, effect.args().get(2), "duration") : 0.0f;
            Ref<EntityStore> playerRef = BuiltinFunctions.playerEntity(session.getContext());
            Store<EntityStore> store = playerRef.getStore();
            Ref<EntityStore> at = BuiltinEffects.npcRef(session, store);
            if (at == null) at = playerRef;
            TransformComponent transform = store.getComponent(at, TransformComponent.getComponentType());
            if (transform == null) return null;
            org.joml.Vector3d pos = new org.joml.Vector3d(transform.getPosition());
            pos.y += 1.0; // chest height rather than the feet
            com.hypixel.hytale.server.core.universe.world.ParticleUtil.spawnParticleEffect(id, pos, 0.0f, 0.0f, 0.0f, scale, seconds, store);
            return null;
        });

        // <<camera Camera_Effect_Id [intensity 0..1]>> shakes this player's camera the way damage does
        effects.register("camera", (session, effect) -> {
            String id = effect.args().get(0).trim();
            int index = com.hypixel.hytale.server.core.asset.type.camera.CameraEffect.getAssetMap().getIndex(id);
            com.hypixel.hytale.server.core.asset.type.camera.CameraEffect cameraEffect =
                    index == Integer.MIN_VALUE ? null : com.hypixel.hytale.server.core.asset.type.camera.CameraEffect.getAssetMap().getAsset(index);
            if (cameraEffect == null) throw new RuntimeError(effect.pos(), "no camera effect called '" + id + "' (see Server/Camera/CameraEffect)");
            float intensity = effect.args().size() > 1 ? Math.max(0.0f, Math.min(1.0f, parseFloat(effect, effect.args().get(1), "intensity"))) : 1.0f;
            session.getPlayer().getPacketHandler().writeNoCache(cameraEffect.createCameraShakePacket(intensity));
            return null;
        });
    }

    private static float parseFloat(com.chromecide.lowtalk.runtime.Effect effect, String raw, String what) {
        try {
            return Float.parseFloat(raw.trim());
        } catch (NumberFormatException e) {
            throw new RuntimeError(effect.pos(), "<<" + effect.name() + ">> " + what + " must be a number, got " + raw);
        }
    }

    /** "noon" -> 0.5, "19.5" (hours) -> 0.8125. */
    public static double dayFraction(String what, com.chromecide.lowtalk.runtime.Effect effect) {
        Double named = TIMES_OF_DAY.get(what);
        if (named != null) return named;
        try {
            double hours = Double.parseDouble(what);
            if (hours < 0 || hours > 24) throw new RuntimeError(effect.pos(), "<<time>> hour must be between 0 and 24, got " + what);
            return (hours % 24.0) / 24.0;
        } catch (NumberFormatException e) {
            throw new RuntimeError(effect.pos(), "<<time>> expects dawn, noon, dusk, midnight, an hour 0-24, pause or resume; got " + what);
        }
    }
}
