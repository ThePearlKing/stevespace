package com.thepearlking.stevespace.world.planet.moon;

import com.thepearlking.stevespace.world.planet.CraterField;
import com.thepearlking.stevespace.world.planet.PlanetGeometry;
import com.thepearlking.stevespace.world.planet.PlanetTerrain;
import com.thepearlking.stevespace.world.planet.SurfaceBiome;
import com.thepearlking.stevespace.world.planet.TerrainSample;
import com.thepearlking.stevespace.world.planet.noise.SphereNoise;
import com.thepearlking.stevespace.world.planet.noise.SpherePoint;

/**
 * The Moon's surface: bright, rugged highlands, dark smooth maria, and craters of every size
 * from basins a hundred blocks across to small pits.
 *
 * <p>Craters come from a cell grid laid over the sphere in 3D (each cell may hold one crater), so
 * they are round everywhere, poles included. Maria are younger, so they have fewer craters.
 * The Moon is the same in every world: its terrain uses a fixed seed, which also lets clients
 * draw it from afar without being sent anything.
 */
public final class MoonTerrain implements PlanetTerrain {

    public static final double RADIUS = 1737;
    /** The Moon's terrain doesn't depend on the world seed. */
    public static final long SEED = 0x4D6F6F6E4C756E61L;

    /** Average ground height (blocks). */
    private static final double BASE = 96;
    private static final double MARE_DEPTH = 22;

    public enum Biome implements SurfaceBiome {
        HIGHLANDS("stevespace:lunar_highlands", 0x9C9A95),
        MARE("stevespace:lunar_mare", 0x605F60);

        private final String id;
        private final int color;

        Biome(String id, int color) {
            this.id = id;
            this.color = color;
        }

        @Override
        public String biomeId() {
            return id;
        }

        @Override
        public int mapColor() {
            return color;
        }
    }

    private final PlanetGeometry geometry = new PlanetGeometry(RADIUS);
    private final SphereNoise mareShape;
    private final SphereNoise highlandRelief;
    private final SphereNoise roughness;
    private final CraterField craters;
    private final ThreadLocal<SpherePoint> point = ThreadLocal.withInitial(SpherePoint::new);
    private final ThreadLocal<CraterField.Sample> craterSample = ThreadLocal.withInitial(CraterField.Sample::new);

    public MoonTerrain() {
        this(SEED);
    }

    public MoonTerrain(long seed) {
        craters = new CraterField(RADIUS, seed, 1.0,
                new CraterField.Scale(420, 45, 120, 0.55, 0x51ED2701L),
                new CraterField.Scale(140, 14, 46, 0.6, 0x2545F491L),
                new CraterField.Scale(48, 5, 16, 0.65, 0x9E3779B9L),
                new CraterField.Scale(18, 2, 6, 0.5, 0x7F4A7C15L));
        mareShape = new SphereNoise(seed + 1, 1.6, 4, 0.5);
        highlandRelief = new SphereNoise(seed + 2, 9, 5, 0.5);
        roughness = new SphereNoise(seed + 3, 140, 3, 0.5);
    }

    @Override
    public PlanetGeometry geometry() {
        return geometry;
    }

    @Override
    public int seaLevel() {
        return Integer.MIN_VALUE;
    }

    /** How much of a mare this place is: 0 highlands, 1 deep in a mare. */
    private double mareness(SpherePoint p) {
        double n = mareShape.sample(p);
        // The near side has more maria, as on the real Moon (longitude 0 faces Earth).
        double nearSide = 0.12 * p.x;
        return smoothstep(0.08, 0.2, n + nearSide);
    }

    @Override
    public void sample(double x, double z, TerrainSample out) {
        SpherePoint p = point.get().set(geometry.latitude(z), geometry.longitude(x));
        double mare = mareness(p);
        double highland = 1 - mare;

        double height = BASE - mare * MARE_DEPTH;
        height += highlandRelief.sample(p) * 26 * highland + highlandRelief.sample(p) * 4 * mare;
        height += roughness.sample(p) * (1.6 * highland + 0.5 * mare);

        // Craters: fewer on the younger maria.
        CraterField.Sample c = craters.sample(p.x * RADIUS, p.y * RADIUS, p.z * RADIUS, 1 - 0.6 * mare, craterSample.get());
        height += c.height;
        double fresh = c.fresh;

        out.height = height;
        // Ice in the cold traps: crater floors near the poles that never see the Sun.
        double lat = Math.abs(geometry.latitude(z));
        out.iceThickness = lat > Math.toRadians(85) && c.height < -3 ? Math.min(3, (-c.height - 3) * 0.6) : 0;
        out.frozenWater = false;
        out.seaIceDepth = 0;
        out.temperature = mare; // reused: how much of a mare this is (for colour blending)
        out.humidity = fresh; // reused: how much fresh bright ejecta is here
        out.river = Math.max(0, Math.min(1, -c.height / 8)); // reused: depth in a crater floor
        out.biome = mare > 0.5 ? Biome.MARE : Biome.HIGHLANDS;
    }

    @Override
    public int surfaceColor(TerrainSample s) {
        double m = smoothstep(0.25, 0.75, s.temperature);
        int base = mix(Biome.HIGHLANDS.mapColor(), Biome.MARE.mapColor(), m);
        double light = 1 + s.humidity * 0.35;
        int r = (int) Math.min(255, ((base >> 16) & 255) * light);
        int g = (int) Math.min(255, ((base >> 8) & 255) * light);
        int b = (int) Math.min(255, (base & 255) * light);
        return (r << 16) | (g << 8) | b;
    }

    private static int mix(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static double smoothstep(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }
}
