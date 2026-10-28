package com.thepearlking.stevespace.world.gen;

import com.thepearlking.stevespace.registry.WorldBlocks;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Helpers shared by the column builders of the hand-made worlds. */
public abstract class BodyColumns implements ColumnBuilder {

    protected static final BlockState BEDROCK = Blocks.BEDROCK.defaultBlockState();
    protected static final BlockState AIR = Blocks.AIR.defaultBlockState();

    protected static BlockState block(String id) {
        return WorldBlocks.state(id);
    }

    /** Bedrock floor, 1-3 blocks thick. */
    protected static boolean bedrock(int x, int z, int y, int minY) {
        long h = hash(x, z);
        return y < minY + 1 + (h & 1) + ((h >> 1) & 1);
    }

    /** Smooth 0..1 patches about {@code cell} blocks across (bilinear hash noise). */
    protected static double patch(int x, int z, int cell, int salt) {
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

    protected static double unit(int x, int z, int salt) {
        return (hash(x * 7 + salt, z * 13 - salt) % 10000) / 10000.0;
    }

    /** True for about {@code perMille}/1000 of all blocks: scattered ore. */
    protected static boolean chance(int x, int y, int z, int salt, int perMille) {
        return hash(x * 31 + y * 7 + salt, z * 17 - y * 3 + salt) % 1000 < perMille;
    }

    protected static long hash(int x, int z) {
        long h = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return h & Long.MAX_VALUE;
    }
}
