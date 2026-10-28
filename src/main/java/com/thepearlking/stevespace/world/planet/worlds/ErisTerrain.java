package com.thepearlking.stevespace.world.planet.worlds;

import com.thepearlking.stevespace.world.planet.BodyTerrain;
import com.thepearlking.stevespace.world.planet.CraterField;
import com.thepearlking.stevespace.world.planet.TerrainSample;
import com.thepearlking.stevespace.world.planet.noise.SphereNoise;
import com.thepearlking.stevespace.world.planet.noise.SpherePoint;

import java.util.List;

/**
 * Eris, far out on its long tilted orbit: one of the brightest worlds there is, because its thin
 * air froze out and fell as fresh methane frost over everything. Smooth white plains, gentle
 * swells, fields of shallow pits where the frost boils off again, and here and there grey rock
 * showing through on steep ground.
 *
 * <p>Sample fields: {@code temperature} = sublimation pits (0..1).
 */
public final class ErisTerrain extends BodyTerrain {

    public static final double RADIUS = 1163;
    private static final double BASE = 92;

    public static final Biome FROST = biome("eris_frost_plains", 0xF6F4F0);
    public static final Biome PITS = biome("eris_pits", 0xE2DED8);

    private final SphereNoise swell = new SphereNoise(seed + 1, 4, 4, 0.5);
    private final SphereNoise rough = new SphereNoise(seed + 2, 90, 2, 0.45);
    private final SphereNoise pitField = new SphereNoise(seed + 3, 7, 3, 0.5);
    private final SphereNoise pits = new SphereNoise(seed + 4, 160, 1, 0.5);
    private final CraterField craters;

    public ErisTerrain() {
        super(RADIUS, 0x4572697345726973L);
        craters = new CraterField(RADIUS, seed, 0.2,
                new CraterField.Scale(200, 8, 26, 0.25, 0xB1L));
    }

    @Override
    public List<Biome> biomes() {
        return List.of(FROST, PITS);
    }

    @Override
    public void sample(SpherePoint p, double latitude, TerrainSample out) {
        double height = BASE + swell.sample(p) * 12 + rough.sample(p) * 0.4;
        double field = smoothstep(0.1, 0.4, pitField.sample(p));
        double pit = Math.max(0, pits.sample(p) - 0.15);
        height -= field * pit * 9;
        CraterField.Sample c = craters(craters, p, 1);
        height += c.height;
        out.height = height;
        out.temperature = field * smoothstep(0, 0.2, pit);
        out.biome = field > 0.5 ? PITS : FROST;
    }

    @Override
    public int surfaceColor(TerrainSample s) {
        return mix(FROST.mapColor(), PITS.mapColor(), s.temperature);
    }
}
