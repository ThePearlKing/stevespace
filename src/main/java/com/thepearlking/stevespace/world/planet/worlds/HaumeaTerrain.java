package com.thepearlking.stevespace.world.planet.worlds;

import com.thepearlking.stevespace.world.planet.BodyTerrain;
import com.thepearlking.stevespace.world.planet.CraterField;
import com.thepearlking.stevespace.world.planet.TerrainSample;
import com.thepearlking.stevespace.world.planet.noise.SphereNoise;
import com.thepearlking.stevespace.world.planet.noise.SpherePoint;

import java.util.List;

/**
 * Haumea, the spinning egg: a dense ball of rock under a clean coat of crystalline water ice,
 * spinning so fast (under four hours a day) that it's pulled out long. Bright blue-white ice, old
 * craters, ridged where the coat has cracked, and one big dark red spot of mineral-stained ice on
 * one side. A thin ring circles it.
 *
 * <p>Sample fields: {@code humidity} = red spot (0..1), {@code river} = ice ridges (0..1).
 */
public final class HaumeaTerrain extends BodyTerrain {

    public static final double RADIUS = 780;
    private static final double BASE = 92;

    public static final Biome ICE = biome("haumea_ice", 0xDFE8F0);
    public static final Biome RED_SPOT = biome("haumea_red_spot", 0x7A3A30);

    private final SphereNoise swell = new SphereNoise(seed + 1, 4, 4, 0.5);
    private final SphereNoise rough = new SphereNoise(seed + 2, 80, 2, 0.5);
    private final SphereNoise ridges = new SphereNoise(seed + 3, 30, 2, 0.5);
    private final SphereNoise spotEdge = new SphereNoise(seed + 4, 8, 3, 0.5);
    private final CraterField craters;

    public HaumeaTerrain() {
        super(RADIUS, 0x4861756D65614567L);
        craters = new CraterField(RADIUS, seed, 0.55,
                new CraterField.Scale(160, 8, 30, 0.35, 0xC1L),
                new CraterField.Scale(50, 3, 9, 0.35, 0xC2L));
    }

    @Override
    public List<Biome> biomes() {
        return List.of(ICE, RED_SPOT);
    }

    @Override
    public void sample(SpherePoint p, double latitude, TerrainSample out) {
        double height = BASE + swell.sample(p) * 8 + rough.sample(p) * 0.6;
        // Cracked ridges in the ice coat.
        double r = ridges.sample(p);
        double ridge = Math.max(0, 1 - Math.abs(r) / 0.05);
        height += ridge * 4;
        // The red spot: a big blotch on one side (around longitude 0 on the equator).
        double spot = 1 - smoothstep(0.75, 0.95, distanceToSpot(p) + spotEdge.sample(p) * 0.08);
        CraterField.Sample c = craters(craters, p, 1);
        height += c.height;
        out.height = height;
        out.humidity = spot;
        out.river = ridge;
        out.biome = spot > 0.5 ? RED_SPOT : ICE;
    }

    /** Angular distance (radians) of a point from the red spot's middle. */
    private static double distanceToSpot(SpherePoint p) {
        // The spot sits on the equator facing +x.
        double cos = p.x;
        return Math.acos(Math.max(-1, Math.min(1, cos))) / 0.5;
    }

    @Override
    public int surfaceColor(TerrainSample s) {
        return mix(mix(ICE.mapColor(), 0xC8D4DE, s.river * 0.5), RED_SPOT.mapColor(), s.humidity);
    }
}
