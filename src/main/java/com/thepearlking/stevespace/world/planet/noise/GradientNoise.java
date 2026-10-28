package com.thepearlking.stevespace.world.planet.noise;

/**
 * Seeded 3D gradient ("Perlin") noise with an optional repeat period along X.
 *
 * <p>The X period is what lets planet terrain wrap seamlessly around the world: a noise with
 * period {@code p} lattice cells along X returns the same value at {@code x} and {@code x + p}.
 * Callers choose the frequency so that the planet's circumference is a whole number of
 * lattice cells (see {@link WrappingNoise}).
 *
 * <p>Output is roughly in [-1, 1]. Instead of a permutation table this uses an integer hash
 * of the lattice coordinates, which keeps any period possible and needs no setup memory.
 */
public final class GradientNoise {

    /** The 12 edge-midpoint gradients of a cube, as recommended for improved Perlin noise. */
    private static final double[][] GRADIENTS = {
            {1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0},
            {1, 0, 1}, {-1, 0, 1}, {1, 0, -1}, {-1, 0, -1},
            {0, 1, 1}, {0, -1, 1}, {0, 1, -1}, {0, -1, -1},
            // repeated so the table length is a power of two (cheap masking)
            {1, 1, 0}, {-1, 1, 0}, {0, -1, 1}, {0, -1, -1}
    };

    private final long seed;
    /** Repeat period along X in lattice cells; 0 means "does not repeat". */
    private final int periodX;

    public GradientNoise(long seed) {
        this(seed, 0);
    }

    public GradientNoise(long seed, int periodX) {
        this.seed = seed;
        this.periodX = periodX;
    }

    public double sample(double x, double y, double z) {
        int x0 = floor(x);
        int y0 = floor(y);
        int z0 = floor(z);
        double fx = x - x0;
        double fy = y - y0;
        double fz = z - z0;

        int x1 = x0 + 1;
        if (periodX > 0) {
            x0 = Math.floorMod(x0, periodX);
            x1 = Math.floorMod(x1, periodX);
        }
        int y1 = y0 + 1;
        int z1 = z0 + 1;

        double n000 = corner(x0, y0, z0, fx, fy, fz);
        double n100 = corner(x1, y0, z0, fx - 1, fy, fz);
        double n010 = corner(x0, y1, z0, fx, fy - 1, fz);
        double n110 = corner(x1, y1, z0, fx - 1, fy - 1, fz);
        double n001 = corner(x0, y0, z1, fx, fy, fz - 1);
        double n101 = corner(x1, y0, z1, fx - 1, fy, fz - 1);
        double n011 = corner(x0, y1, z1, fx, fy - 1, fz - 1);
        double n111 = corner(x1, y1, z1, fx - 1, fy - 1, fz - 1);

        double u = fade(fx);
        double v = fade(fy);
        double w = fade(fz);

        double nx00 = lerp(u, n000, n100);
        double nx10 = lerp(u, n010, n110);
        double nx01 = lerp(u, n001, n101);
        double nx11 = lerp(u, n011, n111);
        double nxy0 = lerp(v, nx00, nx10);
        double nxy1 = lerp(v, nx01, nx11);
        return lerp(w, nxy0, nxy1);
    }

    /** Dot product of the hashed corner gradient with the offset from that corner. */
    private double corner(int ix, int iy, int iz, double dx, double dy, double dz) {
        double[] g = GRADIENTS[hash(ix, iy, iz) & 15];
        return g[0] * dx + g[1] * dy + g[2] * dz;
    }

    private int hash(int x, int y, int z) {
        long h = seed;
        h ^= x * 0x9E3779B97F4A7C15L;
        h = Long.rotateLeft(h, 27) * 0xC2B2AE3D27D4EB4FL;
        h ^= y * 0x165667B19E3779F9L;
        h = Long.rotateLeft(h, 31) * 0x94D049BB133111EBL;
        h ^= z * 0xD6E8FEB86659FD93L;
        h ^= h >>> 32;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (int) h;
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    private static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }
}
