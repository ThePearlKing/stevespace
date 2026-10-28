package com.thepearlking.stevespace.world.planet;

import com.thepearlking.stevespace.world.planet.noise.WrappingNoise;

/**
 * Underground caves for a whole chunk at once: big "cheese" caverns plus long winding
 * "spaghetti" tunnels, in the spirit of vanilla 1.18+ caves.
 *
 * <p>Noise is evaluated on a coarse 4×8×4 grid and trilinearly interpolated, which is ~100×
 * cheaper than per-block evaluation and still smooth. All noise wraps around the planet, so
 * caves continue seamlessly across the date line.
 */
public final class CaveNoise {

    private static final int CELL_XZ = 4;
    private static final int CELL_Y = 8;

    private final WrappingNoise caverns;
    private final WrappingNoise cavernSize;
    private final WrappingNoise tunnelA;
    private final WrappingNoise tunnelB;
    private final WrappingNoise tunnelWidth;
    private final double cavernThreshold;

    /**
     * @param caveAmount 1 = Earth-like caves; lower values make caves rarer (0 disables)
     */
    public CaveNoise(long seed, int circumference, double caveAmount) {
        caverns = new WrappingNoise(seed + 101, circumference, 96, 3, 0.5);
        cavernSize = new WrappingNoise(seed + 102, circumference, 400, 2, 0.5);
        tunnelA = new WrappingNoise(seed + 103, circumference, 150, 2, 0.4);
        tunnelB = new WrappingNoise(seed + 104, circumference, 150, 2, 0.4);
        tunnelWidth = new WrappingNoise(seed + 105, circumference, 300, 1, 0.5);
        cavernThreshold = caveAmount <= 0 ? 99 : 0.42 / Math.min(1.5, caveAmount);
    }

    /**
     * Computes the cave openness for every block of a chunk column range.
     *
     * @param minBlockX chunk's first block x
     * @param minBlockZ chunk's first block z
     * @param minY      lowest y to evaluate
     * @param maxY      highest y to evaluate (inclusive)
     * @return a field where {@code field.isCave(lx, y, lz)} tells if the block is hollow
     */
    public Field compute(int minBlockX, int minBlockZ, int minY, int maxY) {
        int cellsY = (maxY - minY) / CELL_Y + 2;
        int cellsXZ = 16 / CELL_XZ + 1;
        double[][][] grid = new double[cellsXZ][cellsY][cellsXZ];
        for (int cx = 0; cx < cellsXZ; cx++) {
            for (int cz = 0; cz < cellsXZ; cz++) {
                double x = minBlockX + cx * CELL_XZ;
                double z = minBlockZ + cz * CELL_XZ;
                for (int cy = 0; cy < cellsY; cy++) {
                    grid[cx][cy][cz] = openness(x, minY + cy * CELL_Y, z);
                }
            }
        }
        return new Field(grid, minY);
    }

    /** Positive = hollow. Combines caverns and tunnels. */
    private double openness(double x, double y, double z) {
        if (cavernThreshold > 50) {
            return -1; // caves switched off: no caverns, and no tunnels either (Acheron makes its own)
        }
        // Caverns: squashed vertically so they are wider than tall, rarer near the surface.
        double sizeBias = cavernSize.sample(x, z) * 0.12;
        double cavern = caverns.sample(x, y * 1.6, z) - (cavernThreshold - sizeBias);
        if (y > 30) {
            cavern -= (y - 30) * 0.006;
        }
        // Tunnels: where two independent noises are both near zero, a winding tube forms.
        double a = tunnelA.sample(x, y * 1.2, z);
        double b = tunnelB.sample(x, y * 1.2, z);
        double width = 0.055 + tunnelWidth.sample(x, y, z) * 0.025;
        double tunnel = width * width - (a * a + b * b);
        return Math.max(cavern, tunnel * 12);
    }

    /** Interpolated cave field for one chunk. */
    public static final class Field {
        private final double[][][] grid;
        private final int minY;

        private Field(double[][][] grid, int minY) {
            this.grid = grid;
            this.minY = minY;
        }

        public boolean isCave(int localX, int y, int localZ) {
            double gx = localX / (double) CELL_XZ;
            double gy = (y - minY) / (double) CELL_Y;
            double gz = localZ / (double) CELL_XZ;
            int x0 = (int) gx;
            int y0 = (int) gy;
            int z0 = (int) gz;
            if (y0 < 0 || y0 + 1 >= grid[0].length) {
                return false;
            }
            double tx = gx - x0;
            double ty = gy - y0;
            double tz = gz - z0;
            int x1 = Math.min(x0 + 1, grid.length - 1);
            int z1 = Math.min(z0 + 1, grid.length - 1);
            double c00 = lerp(tx, grid[x0][y0][z0], grid[x1][y0][z0]);
            double c10 = lerp(tx, grid[x0][y0 + 1][z0], grid[x1][y0 + 1][z0]);
            double c01 = lerp(tx, grid[x0][y0][z1], grid[x1][y0][z1]);
            double c11 = lerp(tx, grid[x0][y0 + 1][z1], grid[x1][y0 + 1][z1]);
            double c0 = lerp(ty, c00, c10);
            double c1 = lerp(ty, c01, c11);
            return lerp(tz, c0, c1) > 0;
        }

        private static double lerp(double t, double a, double b) {
            return a + t * (b - a);
        }
    }
}
