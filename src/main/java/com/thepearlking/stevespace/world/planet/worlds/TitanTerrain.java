package com.thepearlking.stevespace.world.planet.worlds;

import com.thepearlking.stevespace.world.planet.BodyTerrain;
import com.thepearlking.stevespace.world.planet.CraterField;
import com.thepearlking.stevespace.world.planet.TerrainSample;
import com.thepearlking.stevespace.world.planet.noise.SphereNoise;
import com.thepearlking.stevespace.world.planet.noise.SpherePoint;

import java.util.List;

/**
 * Titan, the only moon with weather: a belt of long parallel dunes of dark organic sand around
 * the equator, all running east-west; bright rugged uplands (Xanadu); plains strewn with rounded
 * ice pebbles where rivers once ran; and in the far north, seas and lakes of liquid methane fed
 * by branching river channels, with pale evaporite rings around dried-up lake beds. Hardly any
 * craters survive the weather.
 *
 * <p>Sample fields: {@code temperature} = dune sand (0..1), {@code humidity} = bright upland
 * (0..1), {@code river} = river channel (0..1), {@code seaIceDepth} = dry lake bed (0..1).
 */
public final class TitanTerrain extends BodyTerrain {

    public static final double RADIUS = 2575;
    public static final int SEA_LEVEL = 84;
    private static final double BASE = 92;

    public static final Biome DUNES = biome("titan_dunes", 0x4E3C2C);
    public static final Biome PLAINS = biome("titan_plains", 0x7A6248);
    public static final Biome UPLANDS = biome("titan_uplands", 0x9C8664);
    public static final Biome LAKES = biome("titan_lakes", 0x2A2418);

    private final SphereNoise swell = new SphereNoise(seed + 1, 5, 4, 0.5);
    private final SphereNoise rough = new SphereNoise(seed + 2, 200, 2, 0.5);
    private final SphereNoise duneWarp = new SphereNoise(seed + 3, 60, 2, 0.5);
    private final SphereNoise duneCover = new SphereNoise(seed + 4, 4, 5, 0.6);
    private final SphereNoise hills = new SphereNoise(seed + 5, 45, 4, 0.55);
    private final SphereNoise rivers = new SphereNoise(seed + 6, 40, 3, 0.5);
    private final SphereNoise basins = new SphereNoise(seed + 7, 12, 4, 0.5);
    /** Fractal wobble for region edges (Xanadu's coast, the dune seas' fingers). */
    private final SphereNoise edge = new SphereNoise(seed + 8, 10, 6, 0.6);
    private final SphereNoise seas = new SphereNoise(seed + 9, 4, 4, 0.55);
    private final CraterField craters;
    private final double[] xanadu = unit(-10, 100);

    public TitanTerrain() {
        super(RADIUS, 0x546974616E4C616BL);
        craters = new CraterField(RADIUS, seed, 0.5, new CraterField.Scale(700, 20, 90, 0.05, 0x51L));
    }

    @Override
    public List<Biome> biomes() {
        return List.of(DUNES, PLAINS, UPLANDS, LAKES);
    }

    @Override
    public int seaLevel() {
        return SEA_LEVEL;
    }

    @Override
    public void sample(SpherePoint p, double latitude, TerrainSample out) {
        double lat = Math.toDegrees(latitude);
        double height = BASE + swell.sample(p) * 5 + rough.sample(p) * 0.4;
        Biome biome = PLAINS;

        // The north is low and wet: a few great seas and many small lakes, with some in the far
        // south too. The wet country's edge wanders.
        double wobble = edge.sample(p);
        double wet = smoothstep(50, 66, lat + wobble * 10) + smoothstep(-66, -76, lat - wobble * 6) * 0.6;
        double basin = basins.sample(p);
        height -= wet * (6 + basin * 12 + Math.max(0, seas.sample(p)) * 22);
        double dryBed = 0;
        if (wet < 0.3 && Math.abs(lat) > 25 && basin < -0.3) {
            // Dry lake beds at mid latitudes: flat floors with pale crusts.
            dryBed = smoothstep(-0.3, -0.45, basin);
            height -= dryBed * 3;
        }

        // Xanadu: bright, rugged uplands.
        // Xanadu: a long bright land, about 4000 by 2500 blocks, its coast ragged.
        double east = (lon(p) - 100) * Math.cos(latitude);
        east -= 360 * Math.floor((east + 180) / 360);
        double north = lat + 10;
        double dx = Math.sqrt(Math.pow(east / 46, 2) + Math.pow(north / 27, 2)) + wobble * 0.4 + hills.sample(p) * 0.08;
        double up = (1 - smoothstep(0.8, 1.0, dx)) * (0.8 + 0.2 * smoothstep(-0.3, 0.3, hills.sample(p)));
        if (up > 0) {
            height += up * (8 + Math.abs(hills.sample(p)) * 26);
            if (up > 0.5) {
                biome = UPLANDS;
            }
        }

        // Dune seas: long east-west ridges, ~30 blocks apart, 6-9 tall, in a few great dark seas
        // around the equator, their edges running out in fingers, with bright islands inside.
        double sand = (1 - smoothstep(20, 34, Math.abs(lat) + wobble * 9)) * smoothstep(-0.3, 0.05, duneCover.sample(p) + wobble * 0.25) * (1 - up);
        if (sand > 0) {
            double across = latitude * RADIUS / 30 + duneWarp.sample(p) * 1.2;
            double f = across - Math.floor(across);
            double crest = Math.pow(1 - Math.abs(2 * f - 1), 1.6);
            height += sand * (crest * 8 + 1);
            if (sand > 0.4) {
                biome = DUNES;
            }
        }

        // River channels: narrow branching valleys draining toward the lowlands.
        double r = 1 - Math.abs(rivers.sample(p)) * 18;
        double channel = clamp01(r) * (1 - up * 0.5) * (1 - sand);
        height -= channel * 3;

        CraterField.Sample c = craters(craters, p, 1 - sand);
        height += c.height * 0.6;
        if (height < SEA_LEVEL) {
            biome = LAKES;
        }
        out.height = height;
        out.temperature = sand;
        out.humidity = up;
        out.river = channel;
        out.seaIceDepth = dryBed;
        out.biome = biome;
    }

    private static double lon(SpherePoint p) {
        return Math.toDegrees(Math.atan2(p.z, p.x));
    }

    @Override
    public int surfaceColor(TerrainSample s) {
        if (s.height < SEA_LEVEL) {
            return mix(0x2A2418, 0x3A3022, clamp01((s.height - SEA_LEVEL + 10) / 10));
        }
        int base = mix(PLAINS.mapColor(), DUNES.mapColor(), s.temperature);
        base = mix(base, UPLANDS.mapColor(), s.humidity);
        base = mix(base, 0xC2B49A, s.seaIceDepth * 0.8);
        return base;
    }
}
