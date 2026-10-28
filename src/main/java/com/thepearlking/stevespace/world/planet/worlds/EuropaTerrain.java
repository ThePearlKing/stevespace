package com.thepearlking.stevespace.world.planet.worlds;

import com.thepearlking.stevespace.world.planet.BodyTerrain;
import com.thepearlking.stevespace.world.planet.CraterField;
import com.thepearlking.stevespace.world.planet.SphereFeatures;
import com.thepearlking.stevespace.world.planet.TerrainSample;
import com.thepearlking.stevespace.world.planet.noise.SphereNoise;
import com.thepearlking.stevespace.world.planet.noise.SpherePoint;

import java.util.List;

/**
 * Europa: a shell of ice over a global ocean. The surface is smooth and young, criss-crossed by
 * lineae (long double ridges stained reddish-brown by salts welling up from below), wide dark
 * bands where the crust pulled apart, and chaos regions where it broke into rafts and froze
 * again; only a handful of craters. Under the crust is the ocean, dark and deep, and on its
 * rocky floor hot vents build mineral chimneys. The crust is thinnest under the chaos.
 *
 * <p>Sample fields: {@code temperature} = chaos (0..1), {@code humidity} = salt stain (0..1),
 * {@code river} = vent heat on the sea floor (0..1), {@code under} = ocean top,
 * {@code floor} = sea floor.
 */
public final class EuropaTerrain extends BodyTerrain {

    public static final double RADIUS = 1561;
    private static final double BASE = 92;
    /** The ocean's surface under normal crust, and how high it comes under the chaos. */
    private static final double OCEAN = 50;
    private static final double OCEAN_UNDER_CHAOS = 76;

    public static final Biome PLAINS = biome("europa_plains", 0xD6D2C8);
    public static final Biome LINEAE = biome("europa_lineae", 0xA88A6C);
    public static final Biome CHAOS = biome("europa_chaos", 0xB8A48C);
    public static final Biome BANDS = biome("europa_bands", 0x9C8C7C);

    private final SphereNoise swell = new SphereNoise(seed + 1, 8, 3, 0.5);
    private final SphereNoise rough = new SphereNoise(seed + 2, 200, 2, 0.5);
    private final SphereNoise chaosField = new SphereNoise(seed + 3, 4.5, 4, 0.55);
    private final SphereNoise floorNoise = new SphereNoise(seed + 4, 25, 4, 0.5);
    private final SphereNoise ventNoise = new SphereNoise(seed + 5, 60, 2, 0.5);
    private final SphereFeatures.Arc[] lineae;
    private final SphereFeatures.Arc[] bands;
    private final CraterField craters;

    public EuropaTerrain() {
        super(RADIUS, 0x4575726F70614963L);
        lineae = SphereFeatures.arcs(seed + 10, 140, 0.3, 1.6, 4, 7, 2.5, 4.5);
        bands = SphereFeatures.arcs(seed + 11, 14, 0.2, 0.6, 14, 30, 1, 2);
        craters = new CraterField(RADIUS, seed, 0.6,
                new CraterField.Scale(500, 12, 28, 0.08, 0x21L));
    }

    @Override
    public List<Biome> biomes() {
        return List.of(PLAINS, LINEAE, CHAOS, BANDS);
    }

    @Override
    public void sample(SpherePoint p, double latitude, TerrainSample out) {
        double height = BASE + swell.sample(p) * 2.5 + rough.sample(p) * 0.4;
        double stain = 0;
        Biome biome = PLAINS;

        // Bands: wide, flat, darker strips where the crust spread apart.
        for (SphereFeatures.Arc b : bands) {
            double a = b.across(p, RADIUS);
            if (a < 1) {
                double in = 1 - smoothstep(0.8, 1, a);
                height += -in * b.value() + Math.sin(a * 20) * 0.3 * in;
                stain = Math.max(stain, in * 0.55);
                biome = BANDS;
            }
        }
        // Lineae: two parallel ridges with a trough between, stained along their length.
        for (SphereFeatures.Arc l : lineae) {
            double a = l.across(p, RADIUS);
            if (a < 1.4) {
                double taper = 1 - smoothstep(0.75, 1, l.alongFraction(p));
                double ridges = Math.exp(-Math.pow((a - 0.55) / 0.22, 2)) * l.value() - Math.exp(-Math.pow(a / 0.18, 2)) * 1.2;
                height += ridges * taper;
                double s = (1 - smoothstep(0.9, 1.4, a)) * taper;
                if (s > stain) {
                    stain = s;
                    if (s > 0.5) {
                        biome = LINEAE;
                    }
                }
            }
        }
        // Chaos: the crust broke into rafts that tilted and froze in a jumble of darker ice.
        double chaos = smoothstep(0.28, 0.42, chaosField.sample(p));
        if (chaos > 0) {
            double[] c = cells(p, 14, 3);
            double raft = c[2] < 0.35 ? -3 : (c[2] - 0.35) * 6;
            double gap = 1 - smoothstep(0.8, 2.5, c[1] - c[0]);
            height = height * (1 - chaos * 0.6) + (BASE - 1 + raft - gap * 2.5) * chaos * 0.6;
            stain = Math.max(stain * (1 - chaos), chaos * 0.4);
            if (chaos > 0.5) {
                biome = CHAOS;
            }
        }
        CraterField.Sample cr = craters(craters, p, 1 - chaos);
        height += cr.height * 0.7;

        // Below: the ocean top (higher under the chaos) and the sea floor with its vents.
        double ocean = OCEAN + (OCEAN_UNDER_CHAOS - OCEAN) * smoothstep(0.15, 0.5, chaosField.sample(p));
        double floor = -30 + floorNoise.sample(p) * 22;
        double vents = smoothstep(0.42, 0.6, ventNoise.sample(p));
        floor += vents * 6;

        out.height = height;
        out.temperature = chaos;
        out.humidity = stain;
        out.river = vents;
        out.under = Math.min(ocean, height - 12);
        out.floor = floor;
        out.biome = biome;
    }

    @Override
    public int surfaceColor(TerrainSample s) {
        int base = PLAINS.mapColor();
        base = mix(base, CHAOS.mapColor(), s.temperature);
        base = mix(base, 0x9A7458, s.humidity * 0.85);
        return base;
    }
}
