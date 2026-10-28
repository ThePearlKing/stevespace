package com.thepearlking.stevespace.world.planet.worlds;

import com.thepearlking.stevespace.world.planet.BodyTerrain;
import com.thepearlking.stevespace.world.planet.CraterField;
import com.thepearlking.stevespace.world.planet.SphereFeatures;
import com.thepearlking.stevespace.world.planet.TerrainSample;
import com.thepearlking.stevespace.world.planet.noise.SphereNoise;
import com.thepearlking.stevespace.world.planet.noise.SpherePoint;

import java.util.List;

/**
 * Charon, Pluto's big grey moon: rough cratered highlands in the north under a dark red cap of
 * tholins (Mordor Macula), a belt of enormous canyons across the middle where the crust split as
 * its ocean froze, and the smooth southern plain of Vulcan Planitia, dotted with mountains sitting
 * in moats. Fresh craters throw out bright ammonia ice.
 *
 * <p>Sample fields: {@code temperature} = southern plain (0..1), {@code humidity} = red cap
 * (0..1), {@code river} = canyon (0..1), {@code seaIceDepth} = fresh ejecta (0..1).
 */
public final class CharonTerrain extends BodyTerrain {

    public static final double RADIUS = 606;
    private static final double BASE = 92;

    public static final Biome HIGHLANDS = biome("charon_oz", 0x8C8680);
    public static final Biome PLAIN = biome("charon_vulcan", 0xA09A94);
    public static final Biome MORDOR = biome("charon_mordor", 0x5A3428);

    private final SphereNoise swell = new SphereNoise(seed + 1, 4, 4, 0.5);
    private final SphereNoise rough = new SphereNoise(seed + 2, 90, 2, 0.5);
    private final SphereNoise edge = new SphereNoise(seed + 3, 6, 3, 0.5);
    private final CraterField craters;
    private final SphereFeatures.Arc[] canyons;
    private final SphereFeatures.Spot[] moatMountains;

    public CharonTerrain() {
        super(RADIUS, 0x436861726F6E4D6FL);
        craters = new CraterField(RADIUS, seed, 0.7,
                new CraterField.Scale(160, 10, 32, 0.35, 0x91L),
                new CraterField.Scale(50, 3, 10, 0.35, 0x92L));
        canyons = chasms(seed + 9);
        moatMountains = SphereFeatures.spots(seed + 10, 20, 14, 30, 18, 30);
    }

    /**
     * The chasm belt: long canyons running roughly east-west around the equator, each a great
     * circle tilted a little from it, some overlapping into wider troughs.
     */
    private static SphereFeatures.Arc[] chasms(long seed) {
        java.util.Random random = new java.util.Random(seed);
        SphereFeatures.Arc[] arcs = new SphereFeatures.Arc[9];
        for (int i = 0; i < arcs.length; i++) {
            double tilt = Math.toRadians(4 + random.nextDouble() * 14);
            double node = random.nextDouble() * 2 * Math.PI;
            // Pole: the north axis tipped by the tilt toward a random direction.
            double nx = Math.sin(tilt) * Math.cos(node);
            double ny = Math.cos(tilt);
            double nz = Math.sin(tilt) * Math.sin(node);
            // Centre: a point on that circle at a random longitude.
            double lon = random.nextDouble() * 2 * Math.PI;
            double cx = Math.cos(lon);
            double cz = Math.sin(lon);
            double cy = -(nx * cx + nz * cz) / ny; // on the plane
            double len = Math.sqrt(cx * cx + cy * cy + cz * cz);
            arcs[i] = new SphereFeatures.Arc(nx, ny, nz, cx / len, cy / len, cz / len,
                    0.5 + random.nextDouble() * 0.6, 12 + random.nextDouble() * 12, 20 + random.nextDouble() * 14);
        }
        return arcs;
    }

    @Override
    public List<Biome> biomes() {
        return List.of(HIGHLANDS, PLAIN, MORDOR);
    }

    @Override
    public void sample(SpherePoint p, double latitude, TerrainSample out) {
        double lat = Math.toDegrees(latitude);
        double height = BASE + swell.sample(p) * 7 + rough.sample(p) * 0.6;
        double plain = smoothstep(-2, -14, lat + edge.sample(p) * 10);
        double cap = smoothstep(62, 72, lat + edge.sample(p) * 6);
        Biome biome = plain > 0.5 ? PLAIN : (cap > 0.5 ? MORDOR : HIGHLANDS);
        // The plain flooded the low south: smooth, a little lower.
        height = height * (1 - plain) + (BASE - 6 + rough.sample(p) * 0.3) * plain;

        // The canyon belt along the plain's edge.
        double canyon = 0;
        for (SphereFeatures.Arc c : canyons) {
            double across = c.across(p, RADIUS);
            if (across < 1.3) {
                double taper = 1 - smoothstep(0.7, 1, c.alongFraction(p));
                double wall = 1 - smoothstep(0.7, 1.05, across);
                height -= wall * c.value() * taper;
                canyon = Math.max(canyon, wall * taper);
            }
        }
        // Mountains standing in moats on the plain.
        if (plain > 0.3) {
            for (SphereFeatures.Spot m : moatMountains) {
                if (!m.near(p, RADIUS, 2)) {
                    continue;
                }
                double d = m.distance(p, RADIUS);
                double peak = Math.max(0, 1 - d);
                double moat = Math.exp(-Math.pow((d - 1.25) / 0.2, 2));
                height += (peak * peak * m.value() - moat * 5) * plain;
            }
        }
        CraterField.Sample c = craters(craters, p, 1 - plain * 0.7);
        height += c.height;
        out.height = height;
        out.temperature = plain;
        out.humidity = cap;
        out.river = canyon;
        out.seaIceDepth = c.fresh;
        out.biome = biome;
    }

    @Override
    public int surfaceColor(TerrainSample s) {
        int base = mix(HIGHLANDS.mapColor(), PLAIN.mapColor(), s.temperature);
        base = mix(base, MORDOR.mapColor(), s.humidity);
        return mix(base, 0xC8D0D4, s.seaIceDepth * 0.6);
    }
}
