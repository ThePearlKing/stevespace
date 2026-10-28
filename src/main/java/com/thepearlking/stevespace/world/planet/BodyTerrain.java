package com.thepearlking.stevespace.world.planet;

import com.thepearlking.stevespace.world.planet.noise.SphereNoise;
import com.thepearlking.stevespace.world.planet.noise.SpherePoint;

import java.util.List;

/**
 * Common ground for the hand-made worlds: geometry, a per-thread sample point, simple biomes,
 * and the little maths every terrain uses (smoothstep, colour mixing, cell patterns on the
 * sphere). Subclasses describe their world in {@link #sample(SpherePoint, double, TerrainSample)}.
 */
public abstract class BodyTerrain implements PlanetTerrain {

    /** A biome of one of these worlds: its Minecraft biome and its colour seen from orbit. */
    public record Biome(String biomeId, int mapColor) implements SurfaceBiome {
    }

    protected final double radius;
    protected final long seed;
    private final PlanetGeometry geometry;
    private final ThreadLocal<SpherePoint> point = ThreadLocal.withInitial(SpherePoint::new);
    private final ThreadLocal<CraterField.Sample> craterSample = ThreadLocal.withInitial(CraterField.Sample::new);
    private final ThreadLocal<double[]> cellOut = ThreadLocal.withInitial(() -> new double[4]);

    protected BodyTerrain(double radius, long seed) {
        this.radius = radius;
        this.seed = seed;
        this.geometry = new PlanetGeometry(radius);
    }

    protected static Biome biome(String id, int color) {
        return new Biome("stevespace:" + id, color);
    }

    /** Every biome this world can produce. */
    public abstract List<Biome> biomes();

    @Override
    public PlanetGeometry geometry() {
        return geometry;
    }

    @Override
    public int seaLevel() {
        return Integer.MIN_VALUE;
    }

    @Override
    public final void sample(double x, double z, TerrainSample out) {
        double latitude = geometry.latitude(z);
        SpherePoint p = point.get().set(latitude, geometry.longitude(x));
        out.iceThickness = 0;
        out.frozenWater = false;
        out.seaIceDepth = 0;
        out.temperature = 0;
        out.humidity = 0;
        out.river = 0;
        out.under = 0;
        out.floor = 0;
        sample(p, latitude, out);
    }

    /** Fill in height, biome and the free fields (temperature, humidity, river) for this point. */
    protected abstract void sample(SpherePoint p, double latitude, TerrainSample out);

    protected CraterField.Sample craters(CraterField field, SpherePoint p, double amount) {
        return field.sample(p.x * radius, p.y * radius, p.z * radius, amount, craterSample.get());
    }

    /**
     * Cells of a jittered 3D grid, {@code size} blocks across, cut by the sphere: a pattern of
     * irregular polygons. Returns {nearest distance, second distance, cell id (0..1), unused},
     * distances in blocks. The edge between two cells is where the two distances are equal.
     */
    protected double[] cells(SpherePoint p, double size, long salt) {
        double px = p.x * radius / size;
        double py = p.y * radius / size;
        double pz = p.z * radius / size;
        long ix = (long) Math.floor(px);
        long iy = (long) Math.floor(py);
        long iz = (long) Math.floor(pz);
        double f1 = Double.MAX_VALUE;
        double f2 = Double.MAX_VALUE;
        long id = 0;
        for (long a = ix - 1; a <= ix + 1; a++) {
            for (long b = iy - 1; b <= iy + 1; b++) {
                for (long c = iz - 1; c <= iz + 1; c++) {
                    long h = hash(a, b, c, salt);
                    double cx = a + ((h >>> 8) & 1023) / 1023.0;
                    double cy = b + ((h >>> 18) & 1023) / 1023.0;
                    double cz = c + ((h >>> 28) & 1023) / 1023.0;
                    double d = (px - cx) * (px - cx) + (py - cy) * (py - cy) + (pz - cz) * (pz - cz);
                    if (d < f1) {
                        f2 = f1;
                        f1 = d;
                        id = h;
                    } else if (d < f2) {
                        f2 = d;
                    }
                }
            }
        }
        double[] out = cellOut.get();
        out[0] = Math.sqrt(f1) * size;
        out[1] = Math.sqrt(f2) * size;
        out[2] = ((id >>> 40) & 0xFFFF) / 65535.0;
        return out;
    }

    protected long hash(long a, long b, long c, long salt) {
        long h = a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL ^ c * 0x165667B19E3779F9L ^ seed ^ salt * 0x27D4EB2F165667C5L;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return h;
    }

    /** Angle (radians) between the point and a unit vector. */
    protected static double arc(SpherePoint p, double[] u) {
        return Math.acos(Math.max(-1, Math.min(1, p.x * u[0] + p.y * u[1] + p.z * u[2])));
    }

    protected static double[] unit(double latDeg, double lonDeg) {
        double lat = Math.toRadians(latDeg);
        double lon = Math.toRadians(lonDeg);
        return new double[]{Math.cos(lat) * Math.cos(lon), Math.sin(lat), Math.cos(lat) * Math.sin(lon)};
    }

    /**
     * A region with a ragged natural edge: 1 inside, 0 outside, centred on {@code centre}, about
     * {@code size} blocks in radius, its edge pushed in and out by {@code edge} (a noise) by up
     * to {@code ragged} of the radius.
     */
    protected double region(SpherePoint p, double[] centre, double size, SphereNoise edge, double ragged) {
        double d = arc(p, centre) * radius / size + edge.sample(p) * ragged;
        return 1 - smoothstep(0.85, 1.05, d);
    }

    protected static int mix(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    protected static double smoothstep(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    protected static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }
}
