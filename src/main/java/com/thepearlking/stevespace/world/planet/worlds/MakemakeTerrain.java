package com.thepearlking.stevespace.world.planet.worlds;

import com.thepearlking.stevespace.world.planet.BodyTerrain;
import com.thepearlking.stevespace.world.planet.CraterField;
import com.thepearlking.stevespace.world.planet.TerrainSample;
import com.thepearlking.stevespace.world.planet.noise.SphereNoise;
import com.thepearlking.stevespace.world.planet.noise.SpherePoint;

import java.util.List;

/**
 * Makemake, out past Pluto: no air to speak of, bright with coarse methane ice whose big grains
 * glint, streaked and blotched reddish-brown where sunlight has baked it into tholins. Low rolling
 * plains, old soft craters, and fields of cracked slabs where the ice has buckled.
 *
 * <p>Sample fields: {@code humidity} = tholin (0..1), {@code temperature} = buckled slabs (0..1),
 * {@code seaIceDepth} = frost in the hollows (0..1).
 */
public final class MakemakeTerrain extends BodyTerrain {

    public static final double RADIUS = 715;
    private static final double BASE = 90;

    public static final Biome PLAINS = biome("makemake_plains", 0xE6D8CC);
    public static final Biome STREAKS = biome("makemake_streaks", 0x8A5A40);
    public static final Biome SLABS = biome("makemake_slabs", 0xD8C8BA);

    private final SphereNoise swell = new SphereNoise(seed + 1, 3, 4, 0.5);
    private final SphereNoise rough = new SphereNoise(seed + 2, 70, 2, 0.5);
    private final SphereNoise streak = new SphereNoise(seed + 3, 9, 4, 0.55);
    private final SphereNoise slab = new SphereNoise(seed + 4, 40, 1, 0.5);
    private final SphereNoise slabField = new SphereNoise(seed + 5, 5, 3, 0.5);
    private final CraterField craters;

    public MakemakeTerrain() {
        super(RADIUS, 0x4D616B656D616B65L);
        craters = new CraterField(RADIUS, seed, 0.35,
                new CraterField.Scale(140, 6, 22, 0.3, 0xA1L),
                new CraterField.Scale(45, 2, 7, 0.3, 0xA2L));
    }

    @Override
    public List<Biome> biomes() {
        return List.of(PLAINS, STREAKS, SLABS);
    }

    @Override
    public void sample(SpherePoint p, double latitude, TerrainSample out) {
        double height = BASE + swell.sample(p) * 9 + rough.sample(p) * 0.5;
        // Long reddish streaks, stretched east-west like Pluto's dark belt but broken up.
        double red = smoothstep(0.15, 0.45, streak.sample(p) + 0.25 * Math.cos(latitude * 3));
        // Buckled slabs: a field of sharp-edged blocks of ice.
        double field = smoothstep(0.2, 0.45, slabField.sample(p));
        double s = slab.sample(p);
        double cracked = field * (Math.abs(s) < 0.06 ? -3 : (s > 0 ? 2 : 0));
        height += cracked;
        CraterField.Sample c = craters(craters, p, 1);
        height += c.height;
        double hollow = smoothstep(-2, -8, swell.sample(p) * 9 + c.height);
        out.height = height;
        out.humidity = red * (1 - field * 0.5);
        out.temperature = field;
        out.seaIceDepth = hollow;
        out.biome = field > 0.5 ? SLABS : (red > 0.5 ? STREAKS : PLAINS);
    }

    @Override
    public int surfaceColor(TerrainSample s) {
        int base = mix(PLAINS.mapColor(), SLABS.mapColor(), s.temperature);
        base = mix(base, STREAKS.mapColor(), s.humidity);
        return mix(base, 0xF2ECE4, s.seaIceDepth * 0.7);
    }
}
