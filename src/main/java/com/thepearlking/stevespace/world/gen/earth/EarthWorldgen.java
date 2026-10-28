package com.thepearlking.stevespace.world.gen.earth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.thepearlking.stevespace.StevesSpace;
import com.thepearlking.stevespace.config.SpaceConfig;
import com.thepearlking.stevespace.world.planet.earth.EarthClimate;
import com.thepearlking.stevespace.world.planet.earth.EarthSettings;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared state for Earth's world generation: the per-world settings (chosen when the world is
 * created) and one {@link EarthClimate} per seed, built once and shared by every worker thread.
 */
public final class EarthWorldgen {

    private static volatile EarthSettings settings = EarthSettings.DEFAULT;
    private static final Map<Long, EarthClimate> climates = new ConcurrentHashMap<>();

    private EarthWorldgen() {
    }

    /** Called when a world loads, before any chunk generates. */
    public static void setSettings(EarthSettings newSettings) {
        if (!newSettings.equals(settings)) {
            settings = newSettings;
            climates.clear();
        }
    }

    public static EarthSettings settings() {
        return settings;
    }

    /**
     * Before the world loads: use the settings saved in the world folder, or, for a new world,
     * save the current config defaults there.
     */
    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        Path file = event.getServer().getWorldPath(LevelResource.ROOT).resolve("stevespace_earth.json");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        EarthSettings loaded;
        try {
            if (Files.exists(file)) {
                loaded = gson.fromJson(Files.readString(file), EarthSettings.class);
            } else {
                loaded = SpaceConfig.newWorldEarthSettings();
                if (isRealEarth(event.getServer())) {
                    loaded = new EarthSettings(loaded.continentSize(), loaded.landFraction(), loaded.iceCapLatitude(), true);
                }
                Files.writeString(file, gson.toJson(loaded));
            }
        } catch (IOException | RuntimeException e) {
            StevesSpace.LOGGER.error("Could not read or write {}, using defaults", file, e);
            loaded = EarthSettings.DEFAULT;
        }
        setSettings(new EarthSettings(loaded.continentSize(), loaded.landFraction(), loaded.iceCapLatitude(), loaded.real()));
        StevesSpace.LOGGER.info("Earth layout for this world: {}", settings);
    }

    /** The world was made with the Real Earth world type (its overworld uses the stevespace:earth_real settings). */
    private static boolean isRealEarth(net.minecraft.server.MinecraftServer server) {
        var stems = server.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.LEVEL_STEM);
        var overworld = stems.get(net.minecraft.world.level.dimension.LevelStem.OVERWORLD);
        if (overworld != null && overworld.generator() instanceof net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator noise) {
            return noise.generatorSettings().unwrapKey()
                    .map(k -> k.location().toString().equals("stevespace:earth_real")).orElse(false);
        }
        return false;
    }

    public static EarthClimate climate(long seed) {
        return climates.computeIfAbsent(seed, s -> new EarthClimate(s, settings));
    }
}
