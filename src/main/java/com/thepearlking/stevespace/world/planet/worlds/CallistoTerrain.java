package com.thepearlking.stevespace.world.planet.worlds;

import com.thepearlking.stevespace.world.planet.BodyTerrain;
import com.thepearlking.stevespace.world.planet.CraterField;
import com.thepearlking.stevespace.world.planet.TerrainSample;
import com.thepearlking.stevespace.world.planet.noise.SphereNoise;
import com.thepearlking.stevespace.world.planet.noise.SpherePoint;

import java.util.List;

/**
 * Callisto: the most cratered surface known, every crater softened under a blanket of dark,
 * carbon-rich dust that slumps off the high ground and fills the lows. What sticks out of the
 * dust are bright spires and knobs of ice, the eroded remains of crater rims. Valhalla, a
 * gigantic bull's-eye of concentric ridges and troughs, sits on the side facing Jupiter. Far out
 * past Jupiter's radiation belts: no radiation to worry about.
 *
 * <p>Sample fields: {@code temperature} = bright ice showing (0..1), {@code humidity} = Valhalla
 * rings (0..1), {@code river} = dust fill in the lows (0..1).
 */
public final class CallistoTerrain extends BodyTerrain {

    public static final double RADIUS = 2410;
    private static final double BASE = 92;

    public static final Biome CRATERED = biome("callisto_cratered", 0x5E554C);
    public static final Biome KNOBS = biome("callisto_knobs", 0x8A8278);
    public static final Biome VALHALLA = biome("callisto_valhalla", 0x7C7268);

    private final SphereNoise swell = new SphereNoise(seed + 1, 5, 4, 0.5);
    private final SphereNoise rough = new SphereNoise(seed + 2, 260, 2, 0.5);
    private final SphereNoise knobField = new SphereNoise(seed + 3, 9, 3, 0.5);
    private final CraterField craters;
    private final double[] valhalla = unit(14, 56);

    public CallistoTerrain() {
        super(RADIUS, 0x43616C6C6973746FL);
        craters = new CraterField(RADIUS, seed, 0.6,
                new CraterField.Scale(500, 40, 130, 0.75, 0x41L),
                new CraterField.Scale(150, 10, 40, 0.85, 0x42L),
                new CraterField.Scale(48, 3, 13, 0.85, 0x43L),
                new CraterField.Scale(18, 1.5, 4.5, 0.6, 0x44L));
    }

    @Override
    public List<Biome> biomes() {
        return List.of(CRATERED, KNOBS, VALHALLA);
    }

    @Override
    public void sample(SpherePoint p, double latitude, TerrainSample out) {
        double height = BASE + swell.sample(p) * 5 + rough.sample(p) * 0.4;
        Biome biome = CRATERED;
        double rings = 0;
        double dv = arc(p, valhalla) * RADIUS;
        if (dv < 1500) {
            // A bright central plain, then rings every ~45 blocks fading outward.
            double ringPhase = dv / 45;
            double ring = Math.pow(Math.abs(Math.sin(ringPhase * Math.PI)), 8) * (1 - smoothstep(300, 1500, dv));
            height += ring * 6 - (dv < 300 ? (1 - dv / 300) * 4 : 0);
            rings = Math.max(ring, dv < 300 ? 1 - dv / 300 : 0);
            if (dv < 900) {
                biome = VALHALLA;
            }
        }
        CraterField.Sample c = craters(craters, p, 1 - rings * 0.6);
        // Dust softens everything: rims rounded down, floors filled in.
        double h = c.height;
        double dustFill = h < 0 ? clamp01(-h / 6) : 0;
        h = h < 0 ? h * 0.7 : h;
        height += h;

        // Ice spires: where old rims and high ground poke through, sharp bright knobs.
        double knobs = smoothstep(0.1, 0.35, knobField.sample(p)) * smoothstep(0.5, 2.5, c.height + swell.sample(p) * 3);
        double bright = c.fresh * 0.8;
        if (knobs > 0) {
            double[] k = cells(p, 9, 4);
            double spire = Math.max(0, 1 - k[0] / (2.2 + k[2] * 2.5));
            double tall = 4 + k[2] * 10;
            height += spire * spire * tall * knobs;
            bright = Math.max(bright, spire > 0.15 ? knobs : 0);
            if (knobs > 0.5) {
                biome = KNOBS;
            }
        }
        out.height = height;
        out.temperature = bright;
        out.humidity = rings;
        out.river = dustFill;
        out.biome = biome;
    }

    @Override
    public int surfaceColor(TerrainSample s) {
        int base = s.biome == VALHALLA ? VALHALLA.mapColor() : CRATERED.mapColor();
        base = mix(base, 0xCFCBC4, s.temperature * 0.8);
        base = mix(base, 0x4A423A, s.river * 0.6);
        return base;
    }
}
