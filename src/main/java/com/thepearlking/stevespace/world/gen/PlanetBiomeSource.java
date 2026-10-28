package com.thepearlking.stevespace.world.gen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.thepearlking.stevespace.world.planet.PlanetTerrain;
import com.thepearlking.stevespace.world.planet.SurfaceBiome;
import com.thepearlking.stevespace.world.planet.TerrainSample;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Picks biomes from the planet's terrain function, so biomes always agree with the terrain
 * shape (and with what the planet looks like from orbit). Underground it adds cave biomes.
 *
 * <p>The terrain needs the world seed, which Minecraft only hands to the chunk generator; the
 * generator therefore {@link #attach attaches} its terrain once the seed is known.
 */
public final class PlanetBiomeSource extends BiomeSource {

    public static final MapCodec<PlanetBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("body").forGetter(PlanetBiomeSource::body),
            RegistryOps.retrieveGetter(Registries.BIOME)
    ).apply(instance, instance.stable(PlanetBiomeSource::new)));

    private final String body;
    private final Map<String, Holder<Biome>> biomesById = new HashMap<>();
    private final Holder<Biome> fallback;
    private volatile PlanetTerrain terrain;
    private volatile CaveBiomes caveBiomes;
    /** Each worker thread remembers the last column it sampled; biome lookups come in runs. */
    private final ThreadLocal<ColumnMemo> memo = ThreadLocal.withInitial(ColumnMemo::new);

    public PlanetBiomeSource(String body, HolderGetter<Biome> biomes) {
        this.body = body;
        for (SurfaceBiome biome : PlanetBodies.surfaceBiomes(body)) {
            biomesById.computeIfAbsent(biome.biomeId(), id -> lookup(biomes, id));
        }
        for (String id : PlanetBodies.caveBiomeIds(body)) {
            biomesById.computeIfAbsent(id, key -> lookup(biomes, key));
        }
        fallback = biomesById.get(PlanetBodies.surfaceBiomes(body).get(0).biomeId());
    }

    private static Holder<Biome> lookup(HolderGetter<Biome> biomes, String id) {
        return biomes.getOrThrow(ResourceKey.create(Registries.BIOME, ResourceLocation.parse(id)));
    }

    public String body() {
        return body;
    }

    /** Called by the chunk generator once the world seed (and so the terrain) is known. */
    public void attach(PlanetTerrain terrain, CaveBiomes caveBiomes) {
        this.caveBiomes = caveBiomes;
        this.terrain = terrain;
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        Set<Holder<Biome>> unique = new LinkedHashSet<>(biomesById.values());
        return unique.stream();
    }

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
        PlanetTerrain t = terrain;
        if (t == null) {
            return fallback;
        }
        int x = QuartPos.toBlock(quartX) + 2;
        int y = QuartPos.toBlock(quartY) + 2;
        int z = QuartPos.toBlock(quartZ) + 2;

        TerrainSample column = memo.get().sample(t, x, z);
        CaveBiomes caves = caveBiomes;
        if (caves != null) {
            String cave = caves.caveBiomeAt(x, y, z, column);
            if (cave != null) {
                return biomesById.getOrDefault(cave, fallback);
            }
        }
        return biomesById.getOrDefault(column.biome.biomeId(), fallback);
    }

    /** Decides underground biomes for a body; null result means "use the surface biome". */
    public interface CaveBiomes {
        String caveBiomeAt(int x, int y, int z, TerrainSample column);
    }

    /**
     * Small direct-mapped cache of recently sampled columns. A chunk asks for 4x4 columns at
     * every height, so remembering 64 columns per thread removes almost all repeat work.
     */
    private static final class ColumnMemo {
        private static final int SIZE = 64;
        private final int[] xs = new int[SIZE];
        private final int[] zs = new int[SIZE];
        private final PlanetTerrain[] owners = new PlanetTerrain[SIZE];
        private final TerrainSample[] samples = new TerrainSample[SIZE];

        ColumnMemo() {
            for (int i = 0; i < SIZE; i++) {
                samples[i] = new TerrainSample();
            }
        }

        TerrainSample sample(PlanetTerrain terrain, int x, int z) {
            int slot = ((x >> 2) * 31 + (z >> 2) * 17) & (SIZE - 1);
            if (owners[slot] != terrain || xs[slot] != x || zs[slot] != z) {
                terrain.sample(x, z, samples[slot]);
                owners[slot] = terrain;
                xs[slot] = x;
                zs[slot] = z;
            }
            return samples[slot];
        }
    }
}
