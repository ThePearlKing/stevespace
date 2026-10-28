package com.thepearlking.stevespace.world.planet.worlds;

import com.thepearlking.stevespace.world.planet.BodyTerrain;
import com.thepearlking.stevespace.world.planet.CraterField;
import com.thepearlking.stevespace.world.planet.SphereFeatures;
import com.thepearlking.stevespace.world.planet.TerrainSample;
import com.thepearlking.stevespace.world.planet.noise.SphereNoise;
import com.thepearlking.stevespace.world.planet.noise.SpherePoint;

import java.util.List;

/**
 * Mercury: craters on craters, cut by long lobate scarps (cliffs hundreds of kilometres long
 * where the shrinking planet's crust pushed over itself); smooth volcanic plains flooding the
 * lowlands; the giant Caloris basin ringed by mountains; very dark graphite-rich crust on the
 * old ground; bright bluish hollows in crater floors; and ice in polar craters that never see
 * the Sun.
 *
 * <p>Sample fields: {@code temperature} = smooth plains (0..1), {@code humidity} = dark crust
 * (0..1), {@code river} = hollows (0..1), {@code iceThickness} = polar crater ice.
 */
public final class MercuryTerrain extends BodyTerrain {

    public static final double RADIUS = 2440;
    private static final double BASE = 90;

    public static final Biome CRATERED = biome("mercury_cratered", 0x7A7470);
    public static final Biome PLAINS = biome("mercury_plains", 0x8C877F);
    public static final Biome CALORIS = biome("mercury_caloris", 0x9A9187);
    public static final Biome POLAR = biome("mercury_polar", 0x6A6866);

    private final SphereNoise lowlands = new SphereNoise(seed + 1, 2.2, 4, 0.5);
    private final SphereNoise relief = new SphereNoise(seed + 2, 9, 4, 0.5);
    private final SphereNoise rough = new SphereNoise(seed + 3, 220, 2, 0.5);
    private final SphereNoise dark = new SphereNoise(seed + 4, 4, 3, 0.5);
    private final CraterField craters;
    private final SphereFeatures.Arc[] scarps;
    private final double[] caloris = unit(31, 162);

    public MercuryTerrain() {
        super(RADIUS, 0x4D65726375727931L);
        craters = new CraterField(RADIUS, seed, 0.85,
                new CraterField.Scale(600, 50, 170, 0.5, 0x11L),
                new CraterField.Scale(200, 16, 55, 0.6, 0x12L),
                new CraterField.Scale(64, 5, 18, 0.6, 0x13L),
                new CraterField.Scale(22, 2, 6, 0.5, 0x14L));
        scarps = SphereFeatures.arcs(seed + 9, 40, 0.08, 0.3, 6, 10, 9, 18);
    }

    @Override
    public List<Biome> biomes() {
        return List.of(CRATERED, PLAINS, CALORIS, POLAR);
    }

    @Override
    protected void sample(SpherePoint p, double latitude, TerrainSample out) {
        double height = BASE + relief.sample(p) * 9 + rough.sample(p) * 0.7;
        // Smooth plains: lava filled the low ground and buried most of the older craters.
        double plains = smoothstep(0.12, 0.3, lowlands.sample(p));
        height -= plains * 6;
        Biome biome = plains > 0.5 ? PLAINS : CRATERED;

        // Caloris: a flat-floored basin, a ring of blocky mountains, plains flooding the floor.
        // Its rim wanders and is broken into separate massifs, not a drawn circle.
        double dc = arc(p, caloris) * RADIUS / 760
                + relief.sample(p.x * 3, p.y * 3, p.z * 3) * 0.22 + dark.sample(p.x * 8, p.y * 8, p.z * 8) * 0.09;
        if (dc < 1.6) {
            double floor = 1 - smoothstep(0.82, 1.02, dc);
            double broken = smoothstep(-0.15, 0.25, relief.sample(p.x * 9, p.y * 9, p.z * 9));
            double ring = Math.exp(-Math.pow((dc - 1.04) / 0.08, 2)) * (0.4 + 0.9 * Math.abs(relief.sample(p.x * 4, p.y * 4, p.z * 4))) * broken;
            double outer = Math.exp(-Math.pow((dc - 1.35) / 0.06, 2)) * 0.35 * broken;
            // Wrinkle ridges on the flooded floor, concentric and radial.
            double ridges = floor * Math.pow(Math.abs(Math.sin(dc * 38 + relief.sample(p) * 5)), 12) * 3;
            height += -floor * 8 + (ring + outer) * 26 + ridges;
            plains = Math.max(plains, floor * (0.6 + 0.3 * clamp01(dark.sample(p.x * 5, p.y * 5, p.z * 5) + 0.5)));
            if (dc < 1.12) {
                biome = CALORIS; // floor and ring mountains (the warped rim keeps its edge ragged)
            }
        }

        // Lobate scarps: one side stands higher, behind a steep face that winds across the land.
        for (SphereFeatures.Arc s : scarps) {
            double off = p.x * s.nx() + p.y * s.ny() + p.z * s.nz();
            double signed = Math.asin(Math.max(-1, Math.min(1, off))) * RADIUS / s.halfWidth();
            if (signed < -2 || signed > 14) {
                continue;
            }
            double taper = 1 - smoothstep(0.6, 1.0, s.alongFraction(p));
            if (taper <= 0) {
                continue;
            }
            double wave = Math.sin(s.alongFraction(p) * 40 + relief.sample(p) * 6) * 0.7;
            double x = signed - wave;
            double rise = smoothstep(-1, -0.2, x) * (1 - smoothstep(0, 12, x));
            height += rise * s.value() * taper;
        }

        CraterField.Sample c = craters(craters, p, 1 - plains * 0.8);
        height += c.height;
        // Hollows: bright, fresh-looking pits on crater floors and peaks.
        double hollows = c.height < -3 ? smoothstep(0.3, 0.55, dark.sample(p.x * 20, p.y * 20, p.z * 20)) : 0;

        double polar = Math.abs(latitude) > Math.toRadians(84) && c.height < -2 ? smoothstep(-2, -5, c.height) : 0;
        if (Math.abs(latitude) > Math.toRadians(84)) {
            biome = POLAR;
        }
        out.height = height;
        out.iceThickness = polar * 3;
        out.temperature = plains;
        out.humidity = clamp01(dark.sample(p) * 2.2 - 0.1) * (1 - plains);
        out.river = hollows;
        out.biome = biome;
    }

    @Override
    public int surfaceColor(TerrainSample s) {
        int base = s.biome.mapColor();
        base = mix(base, PLAINS.mapColor(), s.temperature);
        base = mix(base, 0x4A4846, s.humidity * 0.8);
        base = mix(base, 0xB4BCC4, s.river);
        if (s.iceThickness > 1) {
            base = mix(base, 0xA8ACAE, 0.6);
        }
        return base;
    }
}
