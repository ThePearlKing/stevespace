package com.thepearlking.stevespace.world.planet.worlds;

import com.thepearlking.stevespace.world.planet.BodyTerrain;
import com.thepearlking.stevespace.world.planet.CraterField;
import com.thepearlking.stevespace.world.planet.TerrainSample;
import com.thepearlking.stevespace.world.planet.noise.SphereNoise;
import com.thepearlking.stevespace.world.planet.noise.SpherePoint;

import java.util.List;

/**
 * Enceladus, small and dazzling white: old soft craters in the north, belts of ridged plains, and
 * around the south pole a young fractured region crossed by four parallel "tiger stripes" -
 * trenches flanked by ridges, lined with blue ice, from which geysers shoot water vapour and ice
 * into space. Everything is dusted with snow that fell back from the plumes.
 *
 * <p>Sample fields: {@code temperature} = south polar terrain (0..1), {@code humidity} = ridged
 * plains (0..1), {@code river} = tiger stripe (1 on the trench floor).
 */
public final class EnceladusTerrain extends BodyTerrain {

    public static final double RADIUS = 252;
    private static final double BASE = 92;

    public static final Biome CRATERED = biome("enceladus_cratered", 0xEEF0F2);
    public static final Biome RIDGED = biome("enceladus_ridged", 0xE4EAEE);
    public static final Biome SOUTH_POLAR = biome("enceladus_tiger_stripes", 0xD4E2EA);

    private final SphereNoise swell = new SphereNoise(seed + 1, 3, 4, 0.5);
    private final SphereNoise rough = new SphereNoise(seed + 2, 40, 2, 0.5);
    private final SphereNoise fracture = new SphereNoise(seed + 3, 14, 3, 0.55);
    private final SphereNoise wobble = new SphereNoise(seed + 4, 4, 2, 0.5);
    private final CraterField craters;

    public EnceladusTerrain() {
        super(RADIUS, 0x456E63656C616475L);
        craters = new CraterField(RADIUS, seed, 0.55,
                new CraterField.Scale(60, 6, 18, 0.55, 0x61L),
                new CraterField.Scale(20, 2, 6, 0.5, 0x62L));
    }

    @Override
    public List<Biome> biomes() {
        return List.of(CRATERED, RIDGED, SOUTH_POLAR);
    }

    @Override
    public void sample(SpherePoint p, double latitude, TerrainSample out) {
        double lat = Math.toDegrees(latitude);
        double height = BASE + swell.sample(p) * 5 + rough.sample(p) * 0.6;
        // The south polar terrain's edge: a scalloped chain of curving lobes, not a circle.
        double lon = Math.atan2(p.z, p.x);
        double scallop = Math.abs(Math.sin(lon * 2.5 + wobble.sample(p) * 1.5)) * 6 + fracture.sample(p) * 3;
        double south = smoothstep(-50, -57, lat - scallop + 3);
        double ridged = (1 - south) * (1 - smoothstep(-5, 15, lat)) * smoothstep(-60, -45, lat);
        Biome biome = lat > 0 ? CRATERED : RIDGED;

        if (ridged > 0) {
            // Belts of close parallel ridges wrapping around the moon.
            double phase = latitude * RADIUS / 7 + wobble.sample(p) * 2;
            height += ridged * Math.pow(Math.abs(Math.sin(phase)), 3) * 3;
            if (ridged > 0.5) {
                biome = RIDGED;
            }
        }

        double stripe = 0;
        if (south > 0) {
            biome = south > 0.5 ? SOUTH_POLAR : biome;
            height += south * Math.abs(fracture.sample(p)) * 5;
            // Near the pole the surface is almost flat: walk it in local coordinates.
            double u = p.x * RADIUS;
            double v = p.z * RADIUS + wobble.sample(p) * 6 + u * u * 0.004;
            if (Math.abs(u) < 75) {
                double ends = 1 - smoothstep(55, 75, Math.abs(u));
                for (int k = 0; k < 4; k++) {
                    double centre = (k - 1.5) * 34;
                    double d = Math.abs(v - centre);
                    double trench = Math.exp(-Math.pow(d / 2.6, 2)) * 9;
                    double flanks = Math.exp(-Math.pow((d - 5.5) / 2.2, 2)) * 4;
                    height += (flanks - trench) * ends;
                    stripe = Math.max(stripe, Math.exp(-Math.pow(d / 2.2, 2)) * ends);
                }
            }
        }
        CraterField.Sample c = craters(craters, p, (1 - south) * (lat > 0 ? 1 : 0.4));
        height += c.height * 0.8;

        out.height = height;
        out.temperature = south;
        out.humidity = ridged;
        out.river = stripe;
        out.biome = biome;
    }

    @Override
    public int surfaceColor(TerrainSample s) {
        // Old cratered north into the ridged south and the faintly bluish polar terrain, blended.
        int base = mix(CRATERED.mapColor(), RIDGED.mapColor(), clamp01(s.humidity + (s.biome == CRATERED ? 0 : 0.5)));
        base = mix(base, SOUTH_POLAR.mapColor(), s.temperature * 0.7);
        return mix(base, 0x8CB4D2, s.river);
    }
}
