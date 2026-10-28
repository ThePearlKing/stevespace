package com.thepearlking.stevespace.world.planet.worlds;

import com.thepearlking.stevespace.world.planet.BodyTerrain;
import com.thepearlking.stevespace.world.planet.CraterField;
import com.thepearlking.stevespace.world.planet.SphereFeatures;
import com.thepearlking.stevespace.world.planet.TerrainSample;
import com.thepearlking.stevespace.world.planet.noise.SphereNoise;
import com.thepearlking.stevespace.world.planet.noise.SpherePoint;

import java.util.List;

/**
 * Triton, Neptune's captured moon and one of the coldest places there is: a pinkish polar cap of
 * nitrogen frost over most of the south, streaked with dark dust blown downwind from nitrogen
 * geysers; "cantaloupe" terrain in the west, a crust dimpled all over with round pits; smooth
 * icy plains in the east with walled, terraced ponds where ice lava welled up and froze; almost
 * no craters. Deep under the ice: dark core rock.
 *
 * <p>Sample fields: {@code temperature} = polar cap (0..1), {@code humidity} = dark streak
 * (0..1), {@code river} = geyser (1 at a vent), {@code seaIceDepth} = cantaloupe (0..1).
 */
public final class TritonTerrain extends BodyTerrain {

    public static final double RADIUS = 1353;
    private static final double BASE = 92;

    public static final Biome CAP = biome("triton_cap", 0xD8C0B0);
    public static final Biome CANTALOUPE = biome("triton_cantaloupe", 0xA89A90);
    public static final Biome PLAINS = biome("triton_plains", 0xC4B8AE);

    private final SphereNoise swell = new SphereNoise(seed + 1, 5, 4, 0.5);
    private final SphereNoise rough = new SphereNoise(seed + 2, 180, 2, 0.5);
    private final SphereNoise capEdge = new SphereNoise(seed + 3, 9, 3, 0.5);
    private final SphereFeatures.Spot[] geysers;
    private static final int BANDS = 90;
    private final SphereFeatures.Spot[][] geyserBands;
    private final SphereFeatures.Spot[] ponds;
    private final CraterField craters;
    private static final double[] CANTALOUPE_CENTRE = unit(15, -95);

    public TritonTerrain() {
        super(RADIUS, 0x547269746F6E4963L);
        geysers = southernSpots(seed + 10, 1400);
        // Index the geysers by 2° latitude bands so each sample only checks its neighbours.
        java.util.List<java.util.List<SphereFeatures.Spot>> lists = new java.util.ArrayList<>();
        for (int i = 0; i < BANDS; i++) {
            lists.add(new java.util.ArrayList<>());
        }
        for (SphereFeatures.Spot g : geysers) {
            lists.get(band(g.y())).add(g);
        }
        geyserBands = new SphereFeatures.Spot[BANDS][];
        for (int i = 0; i < BANDS; i++) {
            geyserBands[i] = lists.get(i).toArray(new SphereFeatures.Spot[0]);
        }
        ponds = SphereFeatures.spots(seed + 11, 40, 40, 110, 4, 8);
        craters = new CraterField(RADIUS, seed, 0.6, new CraterField.Scale(400, 8, 20, 0.1, 0x71L));
    }

    private static int band(double y) {
        double lat = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, y))));
        return Math.max(0, Math.min(BANDS - 1, (int) Math.floor((lat + 90) / 2)));
    }

    /** Geysers, all in the polar cap. */
    private static SphereFeatures.Spot[] southernSpots(long seed, int count) {
        SphereFeatures.Spot[] all = SphereFeatures.spots(seed, count * 3, 1, 1, 0, 1);
        java.util.List<SphereFeatures.Spot> south = new java.util.ArrayList<>();
        for (SphereFeatures.Spot s : all) {
            if (s.y() < -0.2 && south.size() < count) {
                south.add(s);
            }
        }
        return south.toArray(new SphereFeatures.Spot[0]);
    }

    @Override
    public List<Biome> biomes() {
        return List.of(CAP, CANTALOUPE, PLAINS);
    }

    @Override
    public void sample(SpherePoint p, double latitude, TerrainSample out) {
        double lat = Math.toDegrees(latitude);
        double height = BASE + swell.sample(p) * 4 + rough.sample(p) * 0.5;
        double cap = smoothstep(-5, -18, lat + capEdge.sample(p) * 12);
        double cantaloupe = (1 - cap) * region(p, CANTALOUPE_CENTRE, 900, capEdge, 0.5);
        Biome biome = cap > 0.5 ? CAP : (cantaloupe > 0.5 ? CANTALOUPE : PLAINS);

        if (cantaloupe > 0) {
            // Round pits about 30 blocks across packed edge to edge, ridges between.
            double[] c = cells(p, 30, 2);
            double pit = Math.max(0, 1 - c[0] / 16);
            double ridge = 1 - smoothstep(0, 4, c[1] - c[0]);
            height += cantaloupe * (-pit * pit * 7 + ridge * 3);
        }
        // Walled ponds: flat terraced floors where ice lava welled up.
        if (cap < 0.5) {
            for (SphereFeatures.Spot s : ponds) {
                if (!s.near(p, RADIUS, 1.2)) {
                    continue;
                }
                double d = s.distance(p, RADIUS);
                if (d < 1.15) {
                    double floor = d < 0.6 ? -s.value() : (d < 0.95 ? -s.value() * 0.5 : 0);
                    double wall = Math.exp(-Math.pow((d - 1.0) / 0.06, 2)) * 3;
                    height = Math.min(height, BASE + 1) + floor + wall;
                }
            }
        }
        // Geysers in the cap, each with a long dark streak blown off to the north-east.
        double streak = 0;
        double vent = 0;
        int b = band(p.y);
        for (int bi = Math.max(0, b - 4); bi <= Math.min(BANDS - 1, b + 4); bi++)
        for (SphereFeatures.Spot g : geyserBands[bi]) {
            if (!g.near(p, RADIUS, 150)) {
                continue;
            }
            double dx = (p.x - g.x()) * RADIUS;
            double dy = (p.y - g.y()) * RADIUS;
            double dz = (p.z - g.z()) * RADIUS;
            double r = Math.sqrt(dx * dx + dy * dy + dz * dz);
            vent = Math.max(vent, Math.max(0, 1 - r / 1.2));
            // Downwind: northward and a little east (along the ground); the streak widens as it goes.
            double h = Math.sqrt(g.x() * g.x() + g.z() * g.z());
            double north = (-g.y() * g.x() * dx + h * h * dy - g.y() * g.z() * dz) / h;
            double east = (-g.z() * dx + g.x() * dz) / h;
            double along = north * 0.85 + east * 0.53;
            if (along > 0 && along < 140) {
                double across = Math.sqrt(Math.max(0, r * r - along * along));
                double width = 4 + along * 0.14;
                streak = Math.max(streak, Math.exp(-Math.pow(across / width, 2)) * (1 - along / 140));
            }
        }
        CraterField.Sample c = craters(craters, p, 1);
        height += c.height * 0.7;
        out.height = height;
        out.temperature = cap;
        out.humidity = streak * cap;
        out.river = vent * cap;
        out.seaIceDepth = cantaloupe;
        out.biome = biome;
    }

    @Override
    public int surfaceColor(TerrainSample s) {
        int base = mix(PLAINS.mapColor(), CANTALOUPE.mapColor(), s.seaIceDepth);
        base = mix(base, CAP.mapColor(), s.temperature);
        return mix(base, 0x5E5450, s.humidity * 0.8);
    }
}
