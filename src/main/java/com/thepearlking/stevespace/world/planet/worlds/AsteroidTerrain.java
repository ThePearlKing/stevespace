package com.thepearlking.stevespace.world.planet.worlds;

import com.thepearlking.stevespace.world.planet.BodyTerrain;
import com.thepearlking.stevespace.world.planet.CraterField;
import com.thepearlking.stevespace.world.planet.noise.SphereNoise;
import com.thepearlking.stevespace.world.planet.noise.SpherePoint;
import com.thepearlking.stevespace.world.planet.TerrainSample;

import java.util.List;

/**
 * The big asteroids of the main belt: small, lumpy, battered worlds with almost no pull. Ceres is
 * dark with the bright salt of Occator crater; Vesta is grey-tan, its south pole blasted into the
 * huge Rheasilvia basin with grooves running round its equator; Pallas and Hygiea are dark and
 * cratered all over.
 *
 * <p>Sample fields: {@code humidity} = bright deposit (0..1), {@code seaIceDepth} = fresh ejecta.
 */
public final class AsteroidTerrain extends BodyTerrain {

    private static final double BASE = 92;

    public enum Kind { CERES, VESTA, PALLAS, HYGIEA }

    private final Kind kind;
    private final Biome surface;
    private final SphereNoise lumps;
    private final SphereNoise rough;
    private final CraterField craters;
    private final int dark;
    private final int light;

    public AsteroidTerrain(Kind kind, double radius, long seed) {
        super(radius, seed);
        this.kind = kind;
        String id = kind.name().toLowerCase(java.util.Locale.ROOT);
        this.dark = switch (kind) {
            case CERES -> 0x5C5955;
            case VESTA -> 0x8E8676;
            case PALLAS -> 0x6E6A64;
            case HYGIEA -> 0x4E4B48;
        };
        this.light = switch (kind) {
            case CERES -> 0x7A7672;
            case VESTA -> 0xB4AA96;
            case PALLAS -> 0x8E8A82;
            case HYGIEA -> 0x66625E;
        };
        this.surface = biome(id + "_surface", dark);
        // Not round: big slow swells (a small body never pulled itself fully spherical).
        this.lumps = new SphereNoise(seed + 1, 3, 4, 0.55);
        this.rough = new SphereNoise(seed + 2, 60, 3, 0.5);
        this.craters = new CraterField(radius, seed, 0.9,
                new CraterField.Scale(90, 8, 26, 0.5, seed + 3),
                new CraterField.Scale(30, 2.5, 8, 0.5, seed + 4),
                new CraterField.Scale(10, 1, 3, 0.45, seed + 5));
    }

    @Override
    public List<Biome> biomes() {
        return List.of(surface);
    }

    @Override
    public void sample(SpherePoint p, double latitude, TerrainSample out) {
        double lat = Math.toDegrees(latitude);
        double lump = kind == Kind.CERES ? 6 : 14; // Ceres is the roundest
        double height = BASE + lumps.sample(p) * lump + rough.sample(p) * 1.2;
        double bright = 0;
        if (kind == Kind.VESTA) {
            // Rheasilvia: a basin covering most of the southern hemisphere, with a central peak.
            double basin = smoothstep(-25, -60, lat);
            height -= basin * 22;
            height += Math.pow(smoothstep(-80, -90, lat), 2) * 30;
            // The Divalia grooves: troughs ringing the equator.
            double band = Math.exp(-Math.pow(lat / 14, 2));
            height -= band * Math.max(0, Math.sin(lat * 0.9 + rough.sample(p) * 0.4) * 5);
        }
        if (kind == Kind.CERES) {
            // Occator: a bright salt deposit on a crater floor, north of the equator.
            double dLat = lat - 19.8;
            double dLon = Math.toDegrees(Math.atan2(p.z, p.x)) - 120;
            double d = Math.sqrt(dLat * dLat + dLon * dLon * Math.cos(latitude) * Math.cos(latitude));
            bright = Math.max(0, 1 - d / 3.0);
            height -= Math.max(0, 1 - d / 6.0) * 8;
        }
        if (kind == Kind.HYGIEA) {
            // Its two great craters, as in the telescope pictures: so big their rims bulge the
            // outline and their bowls notch it.
            height += bigCrater(p, BIG_A, 0.42, 26, 11) + bigCrater(p, BIG_B, 0.24, 15, 7);
        }
        CraterField.Sample c = craters(craters, p, 1.0);
        height += c.height;
        out.height = height;
        out.humidity = bright;
        out.seaIceDepth = c.fresh;
        out.biome = surface;
    }

    private static final double[] BIG_A = unit(28, 35);
    private static final double[] BIG_B = unit(-22, 150);

    /** A crater {@code size} radians across the middle: a bowl {@code depth} blocks deep, its rim raised {@code rim} blocks. */
    private static double bigCrater(SpherePoint p, double[] centre, double size, double depth, double rim) {
        double d = arc(p, centre) / size;
        if (d < 1) {
            return depth * (d * d - 1) + rim;
        }
        double t = (d - 1) / 0.5;
        return rim * Math.exp(-t * t * 2);
    }

    @Override
    public int surfaceColor(TerrainSample s) {
        int base = mix(dark, light, s.seaIceDepth * 0.7);
        return mix(base, 0xEDEFF0, s.humidity);
    }
}
