package com.thepearlking.stevespace.client.planet;

import com.thepearlking.stevespace.world.planet.PlanetGeometry;
import com.thepearlking.stevespace.world.planet.PlanetTerrain;
import com.thepearlking.stevespace.world.planet.Shaping;
import com.thepearlking.stevespace.world.planet.TerrainSample;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * Earth's ground beyond the loaded chunks. Earth is vanilla terrain laid out on our continents,
 * so its real heights only come from the world's own generator: in single player we ask the
 * integrated server for each column's height (pure noise, no chunks are made). Colours come from
 * the biome at each spot, as the real chunks will have it.
 */
final class EarthFarTerrain implements PlanetTerrain {

    private static final int SEA = 63;

    private final ServerLevel level;
    private final ChunkGenerator generator;
    private final RandomState random;
    private final EarthSurfaceColors colors;
    private final PlanetGeometry geometry;

    private EarthFarTerrain(ServerLevel level, EarthSurfaceColors colors) {
        this.level = level;
        this.generator = level.getChunkSource().getGenerator();
        this.random = level.getChunkSource().randomState();
        this.colors = colors;
        this.geometry = colors.geometry();
    }

    /** Earth's far terrain for this single-player world, or null (multiplayer, or not ready yet). */
    static EarthFarTerrain create() {
        var server = net.minecraft.client.Minecraft.getInstance().getSingleplayerServer();
        EarthSurfaceColors colors = PlanetMaps.earthColors();
        if (server == null || colors == null) {
            return null;
        }
        ServerLevel overworld = server.overworld();
        return overworld == null ? null : new EarthFarTerrain(overworld, colors);
    }

    boolean sameWorld(ServerLevel other) {
        return other == level;
    }

    @Override
    public PlanetGeometry geometry() {
        return geometry;
    }

    @Override
    public int seaLevel() {
        return SEA;
    }

    @Override
    public void sample(double x, double z, TerrainSample out) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int top = surface(bx, bz);
        out.height = top;
        out.iceThickness = 0;
        int color;
        if (top < SEA) {
            color = colors.isSea(x, z) ? colors.colorAt(x, z)
                    : Shaping.mixColor(0x245E86, 0x2E6A8E, Shaping.smoothstep(50, SEA, top)); // lakes, rivers, coast
        } else {
            var biome = generator.getBiomeSource().getNoiseBiome(bx >> 2, Math.max(SEA, top) >> 2, bz >> 2, random.sampler());
            color = biomeColor(biome, x, z);
        }
        out.color = color;
    }

    /**
     * The top solid block of a column, straight from the world's density functions: a coarse
     * walk down the smooth terrain shape finds roughly where the ground is, then the real density
     * (with its crags) pins it to the block. Far quicker than generating the column.
     */
    private int surface(int x, int z) {
        DensityFunction rough = random.router().initialDensityWithoutJaggedness();
        DensityFunction exact = random.router().finalDensity();
        int min = level.getMinBuildHeight();
        int max = level.getMaxBuildHeight() - 1;
        int y = max;
        while (y > min && rough.compute(new DensityFunction.SinglePointContext(x, y, z)) <= 0.390625) {
            y -= 16;
        }
        if (y <= min) {
            return min;
        }
        // From a little above that, step down to the first solid block, then narrow it down.
        int start = Math.min(max, y + 56);
        int step = 8;
        int hi = start;
        while (hi > min && exact.compute(new DensityFunction.SinglePointContext(x, hi, z)) <= 0) {
            hi -= step;
        }
        int lo = Math.min(start, hi + step); // air here (or the top of the world)
        while (lo - hi > 1) {
            int mid = (lo + hi) / 2;
            if (exact.compute(new DensityFunction.SinglePointContext(x, mid, z)) > 0) {
                hi = mid;
            } else {
                lo = mid;
            }
        }
        return hi;
    }

    /** What a biome looks like from high up. */
    private int biomeColor(net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome, double x, double z) {
        String id = biome.unwrapKey().map(k -> k.location().getPath()).orElse("");
        int c;
        if (id.contains("frozen") || id.contains("snowy") || id.equals("ice_spikes") || id.equals("jagged_peaks")) {
            c = id.contains("taiga") ? 0xC9D3CF : 0xE6ECEE;
        } else if (id.equals("grove")) {
            c = 0xBFCBC4;
        } else if (id.contains("stony") || id.contains("gravelly") || id.equals("windswept_hills")) {
            c = 0x86857F;
        } else if (id.contains("badlands")) {
            c = 0xB46A3E;
        } else if (id.equals("desert")) {
            c = 0xD8C58E;
        } else if (id.contains("beach")) {
            c = 0xD6CA98;
        } else if (id.contains("jungle") || id.equals("bamboo_jungle")) {
            c = 0x2F6E25;
        } else if (id.equals("dark_forest")) {
            c = 0x2C4A22;
        } else if (id.contains("taiga")) {
            c = 0x3B5A36;
        } else if (id.contains("savanna")) {
            c = 0x9E9A55;
        } else if (id.contains("swamp") || id.contains("mangrove")) {
            c = 0x4A5C35;
        } else if (id.contains("forest")) {
            c = 0x416E34;
        } else if (id.equals("cherry_grove")) {
            c = 0xB99AA6;
        } else if (id.contains("mushroom")) {
            c = 0x857581;
        } else if (id.contains("plains") || id.equals("meadow")) {
            c = 0x6E9A48;
        } else if (id.contains("ocean") || id.contains("river")) {
            c = 0x2B6488;
        } else {
            c = colors.colorAt(x, z);
        }
        return c;
    }

    @Override
    public int surfaceColor(TerrainSample s) {
        return s.color;
    }
}
