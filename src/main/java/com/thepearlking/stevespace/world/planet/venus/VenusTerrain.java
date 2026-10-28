package com.thepearlking.stevespace.world.planet.venus;

import com.thepearlking.stevespace.world.planet.CraterField;
import com.thepearlking.stevespace.world.planet.PlanetGeometry;
import com.thepearlking.stevespace.world.planet.PlanetTerrain;
import com.thepearlking.stevespace.world.planet.SphereFeatures;
import com.thepearlking.stevespace.world.planet.SurfaceBiome;
import com.thepearlking.stevespace.world.planet.TerrainSample;
import com.thepearlking.stevespace.world.planet.noise.SphereNoise;
import com.thepearlking.stevespace.world.planet.noise.SpherePoint;

/**
 * Venus under the clouds: wide basalt plains of overlapping lava flows, rusted by the hot air;
 * raised tesserae crumpled into crossing ridges; one great highland with mountains frosted in
 * metal; shield volcanoes with glowing calderas; coronae (giant rings); clusters of flat-topped
 * pancake domes; long straight rift valleys; and only a few big craters, because the thick air
 * burns up small meteors. Same in every world.
 *
 * <p>Sample fields reused for the block builder: {@code temperature} = how fresh the lava is
 * (0 old and weathered .. 1 newly cooled), {@code humidity} = metal frost (0..1),
 * {@code river} = volcanic heat (0..1, 1 in an active vent).
 */
public final class VenusTerrain implements PlanetTerrain {

    public static final double RADIUS = 6052;
    public static final long SEED = 0x56656E7573576F72L;

    private static final double BASE = 88;
    /** Above this height the highlands are cold enough for metal frost. */
    public static final double FROST_LINE = 150;

    public enum Biome implements SurfaceBiome {
        PLAINS("stevespace:venus_plains", 0x6E5848),
        FLOWS("stevespace:venus_flows", 0x4C4541),
        TESSERA("stevespace:venus_tessera", 0xA49680),
        VOLCANIC("stevespace:venus_volcanic", 0x5C3B33),
        HIGHLANDS("stevespace:venus_highlands", 0x968A78);

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
    private final SphereNoise provinces = new SphereNoise(SEED + 1, 1.7, 4, 0.5);
    private final SphereNoise swell = new SphereNoise(SEED + 2, 6, 4, 0.5);
    private final SphereNoise flows = new SphereNoise(SEED + 3, 40, 4, 0.55);
    private final SphereNoise roughness = new SphereNoise(SEED + 4, 300, 2, 0.5);
    private final SphereNoise foldWarp = new SphereNoise(SEED + 5, 30, 2, 0.5);
    private final CraterField craters;
    private final SphereFeatures.Spot[] volcanoes;
    private final SphereFeatures.Spot[] coronae;
    private final SphereFeatures.Spot[] domeFields;
    private final SphereFeatures.Arc[] rifts;
    /** The great highland (like Ishtar Terra) and its tallest range (like Maxwell Montes). */
    private final SphereFeatures.Spot highland;
    private final SphereFeatures.Spot range;
    private final ThreadLocal<SpherePoint> point = ThreadLocal.withInitial(SpherePoint::new);
    private final ThreadLocal<CraterField.Sample> craterSample = ThreadLocal.withInitial(CraterField.Sample::new);

    public VenusTerrain() {
        // Only big craters: small impactors never reach the ground through this much air.
        craters = new CraterField(RADIUS, SEED, 0.7,
                new CraterField.Scale(900, 40, 140, 0.35, SEED ^ 0x1F2E3D4CL),
                new CraterField.Scale(320, 22, 50, 0.18, SEED ^ 0x5A6B7C8DL));
        volcanoes = SphereFeatures.spots(SEED + 10, 90, 120, 700, 10, 42);
        coronae = SphereFeatures.spots(SEED + 11, 60, 110, 420, 6, 12);
        domeFields = SphereFeatures.spots(SEED + 12, 70, 60, 160, 5, 10);
        rifts = SphereFeatures.arcs(SEED + 13, 26, 0.05, 0.4, 12, 34, 14, 26);
        highland = new SphereFeatures.Spot(Math.cos(Math.toRadians(68)), Math.sin(Math.toRadians(68)), 0, 1600, 0);
        range = new SphereFeatures.Spot(Math.cos(Math.toRadians(65)) * Math.cos(Math.toRadians(5)), Math.sin(Math.toRadians(65)),
                Math.cos(Math.toRadians(65)) * Math.sin(Math.toRadians(5)), 420, 0);
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
        SpherePoint p = point.get().set(geometry.latitude(z), geometry.longitude(x));
        double height = BASE + swell.sample(p) * 5 + roughness.sample(p) * 0.6;
        double fresh = Math.max(0, Math.min(1, flows.sample(p) * 1.6 + 0.35));
        double heat = 0;
        double frost = 0;
        Biome biome = fresh > 0.7 ? Biome.FLOWS : Biome.PLAINS;

        // Tesserae: raised blocks of crust folded twice over, so the ridges cross in a grid.
        double province = provinces.sample(p);
        double tessera = smoothstep(0.22, 0.34, province);
        if (tessera > 0) {
            double warp = foldWarp.sample(p) * 2.5;
            double u = (p.x * 0.8 + p.z * 0.6) * RADIUS / 38 + warp;
            double v = (p.y * 0.9 - p.x * 0.3) * RADIUS / 23 + warp * 0.6;
            double folds = Math.abs(Math.sin(u)) * 4.5 + Math.abs(Math.sin(v)) * 2.5;
            height += tessera * (14 + folds);
            if (tessera > 0.5) {
                biome = Biome.TESSERA;
            }
        }

        // The great highland plateau and its mountain range.
        if (highland.near(p, RADIUS, 1.4)) {
            double d = highland.distance(p, RADIUS);
            double plateau = 1 - smoothstep(0.75, 1.3, d);
            height += plateau * 26;
            if (plateau > 0.5) {
                biome = Biome.HIGHLANDS;
            }
            if (range.near(p, RADIUS, 1.5)) {
                double r = range.distance(p, RADIUS);
                double peaks = Math.max(0, 1 - r) * (0.75 + 0.45 * Math.abs(foldWarp.sample(p.x * 3, p.y * 3, p.z * 3)));
                height += peaks * peaks * 95;
            }
        }

        // Shield volcanoes: wide gentle flanks, a summit caldera with hot vents.
        for (SphereFeatures.Spot v : volcanoes) {
            if (!v.near(p, RADIUS, 1.3)) {
                continue;
            }
            double d = v.distance(p, RADIUS);
            if (d < 1.3) {
                double flank = Math.max(0, 1 - d);
                height += v.value() * (flank * flank * (3 - 2 * flank)) * 0.9;
                double caldera = 0.11;
                if (d < caldera) {
                    height -= v.value() * 0.25 * (1 - d / caldera);
                    heat = Math.max(heat, 1 - d / caldera);
                }
                biome = d < 0.75 ? Biome.VOLCANIC : biome;
                // Fresh flows streaming down the flanks.
                fresh = Math.max(fresh, (1 - d) * smoothstep(0.1, 0.5, flows.sample(p.x * 2, p.y * 2, p.z * 2) + 0.2));
            }
        }

        // Coronae: a raised rim around a sunken middle, ringed by fractures.
        for (SphereFeatures.Spot c : coronae) {
            if (!c.near(p, RADIUS, 1.5)) {
                continue;
            }
            double d = c.distance(p, RADIUS);
            double rim = Math.exp(-Math.pow((d - 1) / 0.13, 2));
            double inner = d < 1 ? (1 - smoothstep(0.6, 1, d)) : 0;
            double rings = d > 1.05 && d < 1.45 ? Math.max(0, Math.sin(d * 70)) * 0.8 : 0;
            height += rim * c.value() - inner * c.value() * 0.35 - rings;
        }

        // Pancake domes: steep-sided, flat-topped lava domes in clusters.
        for (SphereFeatures.Spot field : domeFields) {
            if (!field.near(p, RADIUS, 1.0)) {
                continue;
            }
            height += pancakes(p, field);
        }

        // Rift valleys: long, straight, steep-walled.
        for (SphereFeatures.Arc rift : rifts) {
            double across = rift.across(p, RADIUS);
            if (across < 1.4) {
                double taper = 1 - smoothstep(0.7, 1, rift.alongFraction(p));
                double wall = 1 - smoothstep(0.75, 1.2, across);
                height -= wall * rift.value() * taper;
            }
        }

        CraterField.Sample c = craters.sample(p.x * RADIUS, p.y * RADIUS, p.z * RADIUS, 1, craterSample.get());
        height += c.height * 0.8;
        fresh = Math.max(fresh, c.fresh * 0.6); // dark ejecta around young craters

        if (height > FROST_LINE) {
            frost = smoothstep(FROST_LINE, FROST_LINE + 12, height);
        }

        out.height = height;
        out.iceThickness = 0;
        out.frozenWater = false;
        out.seaIceDepth = 0;
        out.temperature = fresh;
        out.humidity = frost;
        out.river = heat;
        out.biome = biome;
    }

    /** Height added by the domes of one dome field at this point. */
    private double pancakes(SpherePoint p, SphereFeatures.Spot field) {
        // Domes sit on a jittered grid inside the field; each grid cell may hold one dome.
        double cell = 46;
        double px = p.x * RADIUS / cell;
        double py = p.y * RADIUS / cell;
        double pz = p.z * RADIUS / cell;
        long ix = (long) Math.floor(px);
        long iy = (long) Math.floor(py);
        long iz = (long) Math.floor(pz);
        double best = 0;
        for (long a = ix - 1; a <= ix + 1; a++) {
            for (long b = iy - 1; b <= iy + 1; b++) {
                for (long c = iz - 1; c <= iz + 1; c++) {
                    long h = hash(a, b, c);
                    if ((h & 7) > 2) {
                        continue;
                    }
                    double cx = a + ((h >>> 8) & 255) / 255.0;
                    double cy = b + ((h >>> 16) & 255) / 255.0;
                    double cz = c + ((h >>> 24) & 255) / 255.0;
                    double r = 0.3 + ((h >>> 32) & 255) / 255.0 * 0.35;
                    double d = Math.sqrt((px - cx) * (px - cx) + (py - cy) * (py - cy) + (pz - cz) * (pz - cz)) / r;
                    if (d < 1) {
                        // Flat top, very steep edge, a little sag in the middle.
                        double top = 1 - smoothstep(0.82, 1.0, d);
                        best = Math.max(best, top * (field.value() - (d < 0.3 ? 0.8 * (1 - d / 0.3) : 0)));
                    }
                }
            }
        }
        double inField = 1 - smoothstep(0.7, 1.0, field.distance(p, RADIUS));
        return best * inField;
    }

    private static long hash(long a, long b, long c) {
        long h = a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL ^ c * 0x165667B19E3779F9L ^ SEED;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return h;
    }

    @Override
    public int surfaceColor(TerrainSample s) {
        int base = ((Biome) s.biome).mapColor();
        base = mix(base, Biome.FLOWS.mapColor(), Math.min(1, s.temperature) * 0.6);
        base = mix(base, 0xBDBAB2, s.humidity);
        if (s.river > 0.6) {
            base = mix(base, 0xC0602A, (s.river - 0.6) * 1.5);
        }
        return base;
    }

    private static int mix(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
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
