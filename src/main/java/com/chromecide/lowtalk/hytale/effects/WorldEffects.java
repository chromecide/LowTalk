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
            if (playerOnly) {
                WeatherTracker tracker = store.getComponent(ref, WeatherTracker.getComponentType());
                if (tracker == null) return null;
                if (clear) {
                    tracker.clearOverrideWeatherIndex();
                    int back = resource.getForcedWeatherIndex();
                    if (back == 0) {
                        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
                        if (transform != null) tracker.updateEnvironment(transform, store);
                        back = resource.getWeatherIndexForEnvironment(tracker.getEnvironmentId());
                    }
                    if (back != Integer.MIN_VALUE && back != 0) tracker.sendWeatherIndex(session.getPlayer(), back, WEATHER_TRANSITION_SECONDS);
                } else {
                    tracker.setOverrideWeatherIndex(index);
                    tracker.sendWeatherIndex(session.getPlayer(), index, WEATHER_TRANSITION_SECONDS);
                }
            } else {
                String forced = clear ? null : id;
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
