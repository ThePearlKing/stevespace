package com.thepearlking.stevespace.world.gen.venus;

import com.thepearlking.stevespace.registry.WorldBlocks;
import com.thepearlking.stevespace.world.gen.ColumnBuilder;
import com.thepearlking.stevespace.world.planet.TerrainSample;
import com.thepearlking.stevespace.world.planet.venus.VenusTerrain;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Venus columns. Old plains: a rusty weathered crust over dark basalt, with drifts of fine dust
 * in the hollows. Fresh flows: bare basalt and glassy crust. Tesserae and highlands: pale folded
 * rock with veins of telluride ore, and metal frost on the high peaks. Volcanoes: scoria flanks,
 * glowing vent rock in the calderas, and pools of lava.
 */
public final class VenusColumns implements ColumnBuilder {

    private final BlockState basalt = WorldBlocks.state("venus_basalt");
    private final BlockState weathered = WorldBlocks.state("weathered_basalt");
    private final BlockState dust = WorldBlocks.state("venus_dust");
    private final BlockState tessera = WorldBlocks.state("tessera_rock");
    private final BlockState scoria = WorldBlocks.state("venus_scoria");
    private final BlockState glass = WorldBlocks.state("lava_glass");
    private final BlockState frost = WorldBlocks.state("metal_frost");
    private final BlockState vent = WorldBlocks.state("vent_rock");
    private final BlockState ore = WorldBlocks.state("telluride_ore");
    private static final BlockState BEDROCK = Blocks.BEDROCK.defaultBlockState();
    private static final BlockState LAVA = Blocks.LAVA.defaultBlockState();

    @Override
    public int build(int x, int z, TerrainSample sample, double slope, BlockState[] column, int minY) {
        VenusTerrain.Biome biome = (VenusTerrain.Biome) sample.biome;
        int top = (int) Math.floor(sample.height);
        boolean steep = slope > 1.8;
        boolean pale = biome == VenusTerrain.Biome.TESSERA || biome == VenusTerrain.Biome.HIGHLANDS;
        double fresh = sample.temperature;
        double heat = sample.river;
        long h = hash(x, z);

        BlockState surface;
        BlockState under;
        int surfaceDepth;
        if (heat > 0.92 && !steep) {
            surface = LAVA;
            under = vent;
            surfaceDepth = 1;
        } else if (heat > 0.55) {
            surface = vent;
            under = scoria;
            surfaceDepth = 2;
        } else if (biome == VenusTerrain.Biome.VOLCANIC) {
            surface = scoria;
            under = scoria;
            surfaceDepth = 3;
        } else if (pale) {
            surface = sample.humidity > 0.5 && !steep ? frost : tessera;
            under = tessera;
            surfaceDepth = 10;
        } else if (fresh > 0.7) {
            surface = basalt;
            under = basalt;
            surfaceDepth = 2;
        } else {
            // Old plains: weathered crust, dust gathering on flat ground.
            surface = weathered;
            under = weathered;
            surfaceDepth = 2;
        }
        if (steep && surface != LAVA) {
            surface = pale ? tessera : basalt; // cliffs show bare rock
        }

        for (int y = minY; y <= top && y - minY < column.length; y++) {
            int i = y - minY;
            BlockState state;
            if (y < minY + 1 + (h & 1) + ((h >> 1) & 1)) {
                state = BEDROCK;
            } else if (y == top) {
                state = surface;
            } else if (y > top - surfaceDepth) {
                state = under;
            } else {
                state = pale && y > top - 26 ? tessera : basalt;
            }
            // Telluride veins in the folded rock of the tesserae and highlands.
            if (pale && y < top - 1 && y > top - 26 && state == tessera && hash(x * 31 + y, z * 17 - y) % 1000 < 9) {
                state = ore;
            }
            column[i] = state;
        }
        return 5;
    }

    /** Smooth 0..1 patches about {@code cell} blocks across (bilinear hash noise). */
    private static double patch(int x, int z, int cell, int salt) {
        int cx = Math.floorDiv(x, cell);
        int cz = Math.floorDiv(z, cell);
        double fx = (x - cx * cell) / (double) cell;
        double fz = (z - cz * cell) / (double) cell;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        double a = unit(cx, cz, salt);
        double b = unit(cx + 1, cz, salt);
        double c = unit(cx, cz + 1, salt);
        double d = unit(cx + 1, cz + 1, salt);
        return a * (1 - fx) * (1 - fz) + b * fx * (1 - fz) + c * (1 - fx) * fz + d * fx * fz;
    }

    private static double unit(int x, int z, int salt) {
        return (hash(x * 7 + salt, z * 13 - salt) % 10000) / 10000.0;
    }

    private static long hash(int x, int z) {
        long h = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return h & Long.MAX_VALUE;
    }
}
