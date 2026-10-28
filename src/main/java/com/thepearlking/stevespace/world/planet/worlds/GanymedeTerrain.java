package com.thepearlking.stevespace.world.planet.worlds;

import com.thepearlking.stevespace.world.planet.BodyTerrain;
import com.thepearlking.stevespace.world.planet.CraterField;
import com.thepearlking.stevespace.world.planet.SphereFeatures;
import com.thepearlking.stevespace.world.planet.TerrainSample;
import com.thepearlking.stevespace.world.planet.noise.SphereNoise;
import com.thepearlking.stevespace.world.planet.noise.SpherePoint;

import java.util.List;

/**
 * Ganymede, the biggest moon: a patchwork of old dark crust, full of craters and curving
 * furrows, and younger bright lanes of grooved ice where the crust was pulled apart into sets of
 * parallel ridges, each lane running its own way. Palimpsests (pale flat ghosts of giant
 * craters), bright-rayed young craters, and frost on the high latitudes. Its own magnetic field
 * keeps most of Jupiter's radiation off: the safest place to live out here.
 *
 * <p>Sample fields: {@code temperature} = bright grooved terrain (0..1), {@code humidity} =
 * frost (0..1), {@code river} = palimpsest (0..1).
 */
public final class GanymedeTerrain extends BodyTerrain {

    public static final double RADIUS = 2634;
    private static final double BASE = 92;

    public static final Biome DARK = biome("ganymede_dark", 0x6E665C);
    public static final Biome GROOVED = biome("ganymede_grooved", 0xB8B0A4);
    public static final Biome PALIMPSEST = biome("ganymede_palimpsest", 0xA0968A);
    public static final Biome POLAR = biome("ganymede_polar", 0xDCDAD4);

    private final SphereNoise swell = new SphereNoise(seed + 1, 6, 4, 0.5);
    private final SphereNoise rough = new SphereNoise(seed + 2, 240, 2, 0.5);
    private final SphereNoise frost = new SphereNoise(seed + 3, 12, 3, 0.5);
    private final SphereNoise warp = new SphereNoise(seed + 4, 30, 2, 0.5);
    private final SphereNoise domains = new SphereNoise(seed + 5, 2.6, 4, 0.55);
    private final CraterField craters;
    private final SphereFeatures.Spot[] palimpsests;

    public GanymedeTerrain() {
        super(RADIUS, 0x47616E796D656465L);
        craters = new CraterField(RADIUS, seed, 0.7,
                new CraterField.Scale(600, 30, 110, 0.35, 0x31L),
                new CraterField.Scale(160, 8, 30, 0.45, 0x32L),
                new CraterField.Scale(45, 3, 9, 0.4, 0x33L));
        palimpsests = SphereFeatures.spots(seed + 9, 12, 140, 320, 0, 0);
    }

    @Override
    public List<Biome> biomes() {
        return List.of(DARK, GROOVED, PALIMPSEST, POLAR);
    }

    @Override
    public void sample(SpherePoint p, double latitude, TerrainSample out) {
        double height = BASE + swell.sample(p) * 6 + rough.sample(p) * 0.5;
        // Wide swaths of old dark crust and of younger bright terrain; the bright terrain is made
        // of lanes (polygons a couple of hundred blocks across), each grooved its own way.
        double brightness = smoothstep(-0.06, 0.1, domains.sample(p));
        double[] c = cells(p, 190, 1);
        double edge = c[1] - c[0];
        boolean bright = brightness > 0.5;
        Biome biome = bright ? GROOVED : DARK;
        double cellId = c[2];
        if (brightness > 0) {
            // Grooves: parallel ridges along a direction chosen per lane.
            double a = cellId * 40.0;
            double dx = Math.cos(a);
            double dz = Math.sin(a);
            double along = (p.x * dx + p.z * dz + p.y * 0.3 * Math.sin(a * 3)) * RADIUS;
            double spacing = 9 + (cellId * 977 % 1) * 6;
            double phase = along / spacing + warp.sample(p) * 1.5;
            double ridge = 1 - Math.abs(2 * (phase - Math.floor(phase)) - 1); // triangle wave
            height += ((ridge * ridge * 5 - 1.5) * smoothstep(0, 18, edge) - 2) * brightness;
        }
        if (brightness < 1) {
            // Furrows: long curving troughs in the old crust.
            double f = Math.sin((p.x * 0.7 + p.y * 0.2 + warp.sample(p) * 0.05) * RADIUS / 70);
            height += -Math.max(0, f - 0.92) * 40 * (1 - brightness);
        }

        double palimpsest = 0;
        for (SphereFeatures.Spot s : palimpsests) {
            if (s.near(p, RADIUS, 1.1)) {
                palimpsest = Math.max(palimpsest, 1 - smoothstep(0.75, 1.05, s.distance(p, RADIUS)));
            }
        }
        if (palimpsest > 0) {
            height = height * (1 - palimpsest) + (BASE - 1 + rough.sample(p) * 0.4) * palimpsest;
            if (palimpsest > 0.5) {
                biome = PALIMPSEST;
            }
        }
        CraterField.Sample cr = craters(craters, p, (bright ? 0.5 : 1) * (1 - palimpsest));
        height += cr.height;

        double polar = smoothstep(Math.toRadians(42), Math.toRadians(58), Math.abs(latitude) + frost.sample(p) * 0.3);
        if (polar > 0.6) {
            biome = POLAR;
        }
        out.height = height;
        out.temperature = Math.max(brightness, cr.fresh * 0.7);
        out.humidity = polar;
        out.river = palimpsest;
        out.biome = biome;
    }

    @Override
    public int surfaceColor(TerrainSample s) {
        int base = mix(DARK.mapColor(), GROOVED.mapColor(), s.temperature);
        base = mix(base, PALIMPSEST.mapColor(), s.river);
        base = mix(base, POLAR.mapColor(), s.humidity);
        return base;
    }
}
