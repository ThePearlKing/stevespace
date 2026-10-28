package com.thepearlking.stevespace.registry;

import com.mojang.serialization.MapCodec;
import com.thepearlking.stevespace.StevesSpace;
import com.thepearlking.stevespace.world.gen.PlanetBiomeSource;
import com.thepearlking.stevespace.world.gen.PlanetChunkGenerator;
import com.thepearlking.stevespace.world.gen.earth.EarthDensityFunctions;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** Registers world-generation types (generators, biome sources, density functions) for world JSON. */
public final class ModWorldGen {

    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, StevesSpace.MODID);
    public static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES =
            DeferredRegister.create(Registries.BIOME_SOURCE, StevesSpace.MODID);

    public static final DeferredRegister<MapCodec<? extends DensityFunction>> DENSITY_FUNCTIONS =
            DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, StevesSpace.MODID);

    public static final RegistryObject<MapCodec<EarthDensityFunctions.Continents>> EARTH_CONTINENTS =
            DENSITY_FUNCTIONS.register("earth_continents", () -> EarthDensityFunctions.Continents.DATA_CODEC);
    public static final RegistryObject<MapCodec<EarthDensityFunctions.Climate>> EARTH_CLIMATE =
            DENSITY_FUNCTIONS.register("earth_climate", () -> EarthDensityFunctions.Climate.DATA_CODEC);
    public static final RegistryObject<MapCodec<EarthDensityFunctions.Erosion>> EARTH_EROSION =
            DENSITY_FUNCTIONS.register("earth_erosion", () -> EarthDensityFunctions.Erosion.DATA_CODEC);
    public static final RegistryObject<MapCodec<EarthDensityFunctions.RealOffset>> EARTH_REAL_OFFSET =
            DENSITY_FUNCTIONS.register("earth_real_offset", () -> EarthDensityFunctions.RealOffset.DATA_CODEC);

    public static final RegistryObject<MapCodec<PlanetChunkGenerator>> PLANET_GENERATOR =
            CHUNK_GENERATORS.register("planet", () -> PlanetChunkGenerator.CODEC);
    public static final RegistryObject<MapCodec<PlanetBiomeSource>> PLANET_BIOMES =
            BIOME_SOURCES.register("planet", () -> PlanetBiomeSource.CODEC);

    private ModWorldGen() {
    }
}
