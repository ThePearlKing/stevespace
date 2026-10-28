package com.thepearlking.stevespace.world.gen.worlds;

import com.thepearlking.stevespace.world.gen.BodyColumns;
import com.thepearlking.stevespace.world.planet.TerrainSample;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Acheron columns: the green crust (bloodstone, rim, pale or bare crater-wall rock where the map
 * says), dull green-grey rock all the way down, and at the very bottom of the world, always, a
 * cave system of its own: wide low chambers running on and on, their floors and ceilings thick
 * lumpy bloodstone like flesh, swellings hanging from the roof, and veins of bloodstone twisting
 * from the ceiling down to the floor. No other caves anywhere (its cave carving is off).
 */
public final class AcheronColumns extends BodyColumns {

    private final BlockState crust = block("acheron_crust");
    private final BlockState rim = block("acheron_rim");
    private final BlockState pale = block("acheron_pale");
    private final BlockState bloodstone = block("bloodstone");
    private final BlockState rock = block("acheron_rock");
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    @Override
    public int build(int x, int z, TerrainSample s, double slope, BlockState[] column, int minY) {
        int top = (int) Math.floor(s.height);
        BlockState surface = s.humidity > 0.5 ? bloodstone : s.temperature > 0.5 ? rim : s.seaIceDepth > 0.5 ? pale
                : s.river > 0.5 ? rock : crust;
        int skin = surface == bloodstone ? 6 : 3;
        double[] chamber = chamber(x, z);
        for (int y = minY; y <= top && y - minY < column.length; y++) {
            BlockState state;
            if (bedrock(x, z, y, minY)) {
                state = BEDROCK;
            } else if (y > top - skin) {
                state = surface;
            } else if (y - minY < DEEP) {
                state = deep(x, y - minY, z, chamber);
            } else {
                state = rock;
            }
            column[y - minY] = state;
        }
        return 4;
    }

    /** How far up from the bottom of the world the cave system and its bloodstone reach. */
    private static final int DEEP = 44;
    /** The chambers' floor (blocks above the bottom of the world) and the most they rise to. */
    private static final double FLOOR = 7;
    private static final double TALLEST = 24;
    /** Veins: one may stand in each cell this many blocks across. */
    private static final int VEIN_CELL = 11;

    /**
     * This column's chamber: {floor, ceiling} in blocks above the bottom of the world (equal where
     * there's solid rock between the chambers). Most of the ground has a chamber under it, and
     * they run into each other: one great system, everywhere.
     */
    private static double[] chamber(int x, int z) {
        double open = noise(x / 74.0, 0.5, z / 74.0, 11) * 0.7 + noise(x / 29.0, 3.5, z / 29.0, 12) * 0.3;
        double floor = FLOOR + noise(x / 19.0, 7.5, z / 19.0, 13) * 2.5;
        double height = Math.max(0, Math.min(TALLEST, (open + 0.25) * 42));
        double sag = noise(x / 13.0, 9.5, z / 13.0, 14) * 3; // the roof bulges down and up
        return new double[]{floor, floor + Math.max(0, height - Math.max(0, sag))};
    }

    private BlockState deep(int x, int y, int z, double[] chamber) {
        double floor = chamber[0];
        double ceiling = chamber[1];
        // Lumpy, fleshy surfaces: the floor and roof swell and dip from block to block.
        double lump = noise(x / 5.0, y / 4.0, z / 5.0, 15) * 1.6;
        boolean inside = ceiling - floor > 2 && y > floor + lump && y < ceiling + lump;
        // Swellings hanging from the roof.
        if (inside && y > ceiling - 6 && noise(x / 6.0, y / 5.0, z / 6.0, 16) > 0.35 + (ceiling - y) * 0.08) {
            inside = false;
        }
        if (inside && y > 3) {
            return vein(x, y, z, floor, ceiling) ? bloodstone : AIR;
        }
        // The flesh round the chambers: thick bloodstone, then the grey rock beyond it.
        double depth = Math.min(Math.abs(y - floor), Math.abs(y - ceiling));
        boolean near = ceiling - floor > 2 ? depth < 4 + noise(x / 7.0, y / 7.0, z / 7.0, 17) * 2
                : nearChamber(x, y, z);
        if (y < DEEP - 4 && near) {
            return bloodstone;
        }
        return rock;
    }

    /** Solid ground between chambers, close enough to one to be part of its flesh. */
    private static boolean nearChamber(int x, int y, int z) {
        for (int[] d : new int[][]{{3, 0}, {-3, 0}, {0, 3}, {0, -3}}) {
            double[] c = chamber(x + d[0], z + d[1]);
            if (c[1] - c[0] > 2 && y > c[0] - 3 && y < c[1] + 3) {
                return true;
            }
        }
        return false;
    }

    /**
     * Veins: in some cells a twisting column of bloodstone stands from the floor up into the roof,
     * thin in the middle and spreading thick where it grows into the floor and the roof.
     */
    private static boolean vein(int x, int y, int z, double floor, double ceiling) {
        int cx = Math.floorDiv(x, VEIN_CELL);
        int cz = Math.floorDiv(z, VEIN_CELL);
        double span = Math.max(1, ceiling - floor);
        double along = (y - floor) / span; // 0 at the floor, 1 at the roof
        double radius = 1.1 + 2.6 * Math.pow(Math.abs(along - 0.5) * 2, 3);
        for (int i = cx - 1; i <= cx + 1; i++) {
            for (int j = cz - 1; j <= cz + 1; j++) {
                if (hash(i, 0, j, 21) < -0.1) {
                    continue; // no vein in this cell
                }
                double phase = hash(i, 1, j, 22) * Math.PI;
                double vx = (i + 0.5 + hash(i, 2, j, 23) * 0.3) * VEIN_CELL + 2.4 * Math.sin(y / 6.0 + phase);
                double vz = (j + 0.5 + hash(i, 3, j, 24) * 0.3) * VEIN_CELL + 2.4 * Math.cos(y / 8.0 + phase * 1.7);
                double dx = x + 0.5 - vx;
                double dz = z + 0.5 - vz;
                if (dx * dx + dz * dz < radius * radius) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Smooth value noise in about -1..1. */
    private static double noise(double x, double y, double z, int seed) {
        int xi = (int) Math.floor(x);
        int yi = (int) Math.floor(y);
        int zi = (int) Math.floor(z);
        double fx = fade(x - xi);
        double fy = fade(y - yi);
        double fz = fade(z - zi);
        double v = 0;
        for (int i = 0; i < 8; i++) {
            int dx = i & 1;
            int dy = (i >> 1) & 1;
            int dz = (i >> 2) & 1;
            v += (dx == 1 ? fx : 1 - fx) * (dy == 1 ? fy : 1 - fy) * (dz == 1 ? fz : 1 - fz) * hash(xi + dx, yi + dy, zi + dz, seed);
        }
        return v;
    }

    private static double fade(double t) {
        return t * t * (3 - 2 * t);
    }

    private static double hash(int x, int y, int z, int seed) {
        long h = x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ z * 0x165667B19E3779F9L ^ seed * 0x27D4EB2F165667C5L;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 11) / (double) (1L << 53) * 2 - 1;
    }
}
