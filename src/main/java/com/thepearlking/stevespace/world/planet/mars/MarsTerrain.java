package com.thepearlking.stevespace.world.planet.mars;

import com.thepearlking.stevespace.world.planet.CraterField;
import com.thepearlking.stevespace.world.planet.PlanetGeometry;
import com.thepearlking.stevespace.world.planet.PlanetTerrain;
import com.thepearlking.stevespace.world.planet.SurfaceBiome;
import com.thepearlking.stevespace.world.planet.TerrainSample;
import com.thepearlking.stevespace.world.planet.noise.SphereNoise;
import com.thepearlking.stevespace.world.planet.noise.SpherePoint;

/**
 * Mars: old cratered southern highlands, smooth low northern plains, the Tharsis bulge with its
 * great shield volcanoes, a long canyon system east of it, and icy poles. Rusty dust everywhere,
 * darker where bare rock or basalt sand shows. Like the Moon, the same in every world.
 *
 * <p>Heights are the real ones (1 block = 1 km) stretched a little, so that Minecraft-scale
 * terrain reads as mountains and canyons rather than gentle swells.
 */
public final class MarsTerrain implements PlanetTerrain {

    public static final double RADIUS = 3390;
    public static final long SEED = 0x4D6172735265644CL;

    private static final double BASE = 92;
    /** Vertical stretch over real relief. */
    private static final double RELIEF = 1.8;

    public enum Biome implements SurfaceBiome {
        PLAINS("stevespace:mars_plains", 0xB06A42),
        HIGHLANDS("stevespace:mars_highlands", 0x9C5E3E),
        VOLCANIC("stevespace:mars_volcanic", 0x7A4A36),
        POLAR("stevespace:mars_polar", 0xE8E2DA);

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

    /** A great shield volcano: where (unit vector), how wide (blocks) and how tall (blocks). */
    private record Volcano(double x, double y, double z, double radius, double height) {
    }

    private final PlanetGeometry geometry = new PlanetGeometry(RADIUS);
    private final SphereNoise dichotomy;
    private final SphereNoise relief;
    private final SphereNoise roughness;
    private final SphereNoise dark;
    private final SphereNoise canyonWarp;
    private final SphereNoise capEdge;
    private final CraterField craters;
    private final Volcano[] volcanoes;
    /** Tharsis centre (unit vector) and the canyon's direction (a great circle through it). */
    private final double[] tharsis;
    private final double[] canyonPole;
    private final ThreadLocal<SpherePoint> point = ThreadLocal.withInitial(SpherePoint::new);
    private final ThreadLocal<CraterField.Sample> craterSample = ThreadLocal.withInitial(CraterField.Sample::new);

    public MarsTerrain() {
        this(SEED);
    }

    public MarsTerrain(long seed) {
        dichotomy = new SphereNoise(seed + 1, 1.2, 3, 0.45);
        relief = new SphereNoise(seed + 2, 7, 5, 0.5);
        roughness = new SphereNoise(seed + 3, 160, 3, 0.5);
        dark = new SphereNoise(seed + 4, 3, 4, 0.5);
        canyonWarp = new SphereNoise(seed + 5, 20, 2, 0.5);
        capEdge = new SphereNoise(seed + 6, 14, 5, 0.6);
        craters = new CraterField(RADIUS, seed, 0.55,
                new CraterField.Scale(700, 60, 180, 0.45, 0x13579BDFL),
                new CraterField.Scale(220, 18, 60, 0.5, 0x2468ACE0L),
                new CraterField.Scale(70, 5, 20, 0.45, 0x369CF258L),
                new CraterField.Scale(26, 2, 7, 0.35, 0x48D159E2L));
        tharsis = unit(Math.toRadians(2), Math.toRadians(-110));
        // The canyon runs east from Tharsis along the equator.
        canyonPole = new double[]{0, 1, 0};
        volcanoes = new Volcano[]{
                volcano(18, -134, 300, 22 * RELIEF),  // the great one
                volcano(12, -112, 170, 14 * RELIEF),
                volcano(-1, -120, 180, 15 * RELIEF),
                volcano(-9, -127, 180, 14 * RELIEF),
                volcano(40, -110, 380, 6 * RELIEF),   // broad and low
                volcano(25, 145, 150, 9 * RELIEF),
        };
    }

    private static Volcano volcano(double latDeg, double lonDeg, double radius, double height) {
        double[] u = unit(Math.toRadians(latDeg), Math.toRadians(lonDeg));
        return new Volcano(u[0], u[1], u[2], radius, height);
    }

    private static double[] unit(double lat, double lon) {
        return new double[]{Math.cos(lat) * Math.cos(lon), Math.sin(lat), Math.cos(lat) * Math.sin(lon)};
    }

    @Override
    public PlanetGeometry geometry() {
        return geometry;
    }

    @Override
    public int seaLevel() {
        return Integer.MIN_VALUE;
    }

    @Override
    public void sample(double x, double z, TerrainSample out) {
        double latitude = geometry.latitude(z);
        SpherePoint p = point.get().set(latitude, geometry.longitude(x));

        // North low and smooth, south high and old: a wavy boundary a little north of the equator.
        double southness = smoothstep(-0.25, 0.25, -p.y * 1.6 + 0.15 + dichotomy.sample(p) * 0.9);
        double height = BASE + (southness - 0.5) * 6 * RELIEF;
        height += relief.sample(p) * (2.0 + 3.0 * southness) * RELIEF;
        height += roughness.sample(p) * (0.4 + 0.8 * southness);

        // Tharsis: a broad rise, then the volcanoes on it.
        double distTharsis = arc(p, tharsis[0], tharsis[1], tharsis[2]) * RADIUS;
        double bulge = Math.exp(-Math.pow(distTharsis / 1400, 2));
        height += bulge * 5 * RELIEF;
        double volcanic = bulge * 0.6;
        for (Volcano v : volcanoes) {
            double d = arc(p, v.x, v.y, v.z) * RADIUS / v.radius;
            if (d < 1.6) {
                // Shield profile: gentle flanks, steeper near the top, a summit caldera.
                double flank = Math.max(0, 1 - d);
                double shape = flank * flank * (3 - 2 * flank);
                double caldera = d < 0.12 ? (0.12 - d) / 0.12 : 0;
                height += v.height * shape - v.height * 0.25 * caldera;
                volcanic = Math.max(volcanic, Math.min(1, (1.6 - d) / 0.8));
            }
        }

        // The canyon: a long, deep, winding trench east of Tharsis.
        double canyon = canyonDepth(p);
        height -= canyon;

        // Craters: many on the old highlands, few on the plains and volcanoes.
        double craterAmount = (0.35 + 0.65 * southness) * (1 - volcanic * 0.8) * (canyon > 1 ? 0.2 : 1);
        CraterField.Sample c = craters.sample(p.x * RADIUS, p.y * RADIUS, p.z * RADIUS, craterAmount, craterSample.get());
        height += c.height * RELIEF;

        double polar = polarCap(p, latitude);
        out.height = height;
        out.iceThickness = polar * 3;
        out.frozenWater = false;
        out.seaIceDepth = 0;
        out.temperature = volcanic;                                       // reused: volcanic darkness
        out.humidity = Math.max(0, dark.sample(p)) * (1 - polar) + c.fresh * 0.2; // reused: dark sand
        out.river = canyon > 2 ? 1 : 0;                                   // reused: inside the canyon
        out.seaIceDepth = Math.max(0, Math.min(1, -c.height / 6)) * southness; // reused: old crater-lake floor
        out.under = Math.toDegrees(latitude);                             // reused: latitude
        if (polar > 0.5) {
            out.biome = Biome.POLAR;
        } else if (volcanic > 0.55) {
            out.biome = Biome.VOLCANIC;
        } else if (southness > 0.5) {
            out.biome = Biome.HIGHLANDS;
        } else {
            out.biome = Biome.PLAINS;
        }
    }

    /**
     * The polar caps (0..1): the north one about 1000 km across, the south one smaller and set
     * off the pole. Ragged edges with frost patches beyond them, and the dark spiral troughs that
     * wind out from the middle, where the layered ground shows through the ice.
     */
    private double polarCap(SpherePoint p, double latitude) {
        boolean north = latitude > 0;
        // Distance from the cap's centre, in degrees (the south cap sits ~4 degrees off the pole).
        double cx = north ? 0 : Math.sin(Math.toRadians(4)) * Math.cos(Math.toRadians(-40));
        double cz = north ? 0 : Math.sin(Math.toRadians(4)) * Math.sin(Math.toRadians(-40));
        double cy = north ? 1 : -Math.cos(Math.toRadians(4));
        double d = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, p.x * cx + p.y * cy + p.z * cz))));
        double size = north ? 10.5 : 7;
        if (d > size + 8) {
            return 0;
        }
        double edge = capEdge.sample(p);
        double lobes = Math.sin(Math.atan2(p.z - cz, p.x - cx) * 3 + edge * 3) * 1.6;
        double ragged = d + edge * 5 + lobes + dichotomy.sample(p) * 1.5;
        double cap = 1 - smoothstep(size - 0.8, size + 0.4, ragged);
        // Frost left in hollows past the edge: patchy, thinning out.
        double outliers = (1 - smoothstep(size, size + 6, d)) * smoothstep(0.25, 0.45, edge + roughness.sample(p) * 0.2) * 0.8;
        // Spiral troughs: lanes curling out from the centre (anticlockwise in the north).
        double angle = Math.atan2(p.z - cz, p.x - cx) * (north ? 1 : -1);
        double phase = angle * 5 + Math.log(Math.max(0.5, d)) * 7 + edge * 2.5 + roughness.sample(p) * 0.3;
        double trough = smoothstep(0.72, 0.95, Math.sin(phase)) * smoothstep(2, 4, d) * (0.5 + 0.5 * smoothstep(-0.2, 0.3, edge));
        // Chasma Boreale: a great valley cut deep into the north cap from one side.
        if (north) {
            double notch = Math.abs(Math.sin((angle - Math.toRadians(-30)) / 2)) * 2 * d;
            double inCut = (1 - smoothstep(1.0, 2.2, notch + edge * 0.8)) * smoothstep(3, 6, d);
            cap *= 1 - inCut;
        }
        return Math.max(cap * (1 - trough * 0.55), outliers);
    }

    /** Depth of the canyon at this point (0 outside it). */
    private double canyonDepth(SpherePoint p) {
        // Along the equator from Tharsis' east edge, about 4000 km long, winding a little.
        double lon = Math.toDegrees(Math.atan2(p.z, p.x));
        double lat = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, p.y))));
        double start = -100;
        double end = -40;
        if (lon < start - 5 || lon > end + 5) {
            return 0;
        }
        double along = (lon - start) / (end - start);
        double centreLat = -8 + Math.sin(along * Math.PI * 2.2) * 2.5 + canyonWarp.sample(p) * 1.5;
        double halfWidth = 0.6 + 0.7 * Math.sin(Math.max(0, Math.min(1, along)) * Math.PI); // degrees
        double across = Math.abs(lat - centreLat) / halfWidth;
        double ends = smoothstep(-0.05, 0.08, along) * smoothstep(-0.05, 0.08, 1 - along);
        if (across > 1.3 || ends <= 0) {
            return 0;
        }
        // Flat floor, cliff walls. Deeper than the real one (7 km) so it reads as a canyon here.
        double wall = 1 - smoothstep(0.86, 1.02, across);
        return wall * 30 * ends;
    }

    /** Angle (radians) between the sample point and a unit vector. */
    private static double arc(SpherePoint p, double x, double y, double z) {
        double dot = p.x * x + p.y * y + p.z * z;
        return Math.acos(Math.max(-1, Math.min(1, dot)));
    }

    @Override
    public int surfaceColor(TerrainSample s) {
        int base = mix(Biome.PLAINS.mapColor(), Biome.HIGHLANDS.mapColor(), s.biome == Biome.HIGHLANDS ? 1 : 0);
        base = mix(base, Biome.VOLCANIC.mapColor(), Math.min(1, s.temperature));
        base = mix(base, 0x5C3A2C, Math.min(0.6, s.humidity * 1.4));
        return mix(base, Biome.POLAR.mapColor(), Math.min(1, s.iceThickness / 3));
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
