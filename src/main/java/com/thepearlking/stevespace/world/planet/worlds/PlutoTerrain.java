package com.thepearlking.stevespace.world.planet.worlds;

import com.thepearlking.stevespace.world.planet.BodyTerrain;
import com.thepearlking.stevespace.world.planet.CraterField;
import com.thepearlking.stevespace.world.planet.TerrainSample;
import com.thepearlking.stevespace.world.planet.noise.SphereNoise;
import com.thepearlking.stevespace.world.planet.noise.SpherePoint;

import java.util.List;

/**
 * Pluto: the heart. Its western lobe is Sputnik Planitia, a vast basin filled with slowly
 * churning nitrogen ice, cut into convection cells with darker troughs between; along its edge
 * rise jumbled mountains of water ice, capped with methane snow. West of the heart lies the dark
 * red-brown tholin belt (Cthulhu), cratered and hilly; to the east, the bladed terrain - rows of
 * sharp ice fins like a forest of knives. Bright methane plains cover the north. Under it all:
 * core rock.
 *
 * <p>Sample fields: {@code temperature} = Sputnik ice (0..1), {@code humidity} = tholins
 * (0..1), {@code river} = blades (0..1), {@code seaIceDepth} = methane snow (0..1), {@code
 * under} = cell trough (0..1).
 */
public final class PlutoTerrain extends BodyTerrain {

    public static final double RADIUS = 1188;
    private static final double BASE = 92;

    public static final Biome SPUTNIK = biome("pluto_sputnik", 0xE8E2D8);
    public static final Biome MOUNTAINS = biome("pluto_mountains", 0xA49A90);
    public static final Biome CTHULHU = biome("pluto_cthulhu", 0x5C3A2C);
    public static final Biome BLADES = biome("pluto_blades", 0xB0A090);
    public static final Biome PLAINS = biome("pluto_plains", 0xC9A886);

    private final SphereNoise swell = new SphereNoise(seed + 1, 5, 4, 0.5);
    private final SphereNoise rough = new SphereNoise(seed + 2, 160, 2, 0.5);
    private final SphereNoise shape = new SphereNoise(seed + 3, 3, 3, 0.5);
    private final SphereNoise hills = new SphereNoise(seed + 4, 35, 4, 0.55);
    private final SphereNoise bladeWarp = new SphereNoise(seed + 5, 50, 2, 0.5);
    /** Fractal wobble for every coastline-like edge, so nothing ends in a clean curve. */
    private final SphereNoise edge = new SphereNoise(seed + 6, 9, 6, 0.6);
    private final SphereNoise patches = new SphereNoise(seed + 7, 22, 5, 0.6);
    private final CraterField craters;
    /** Centre of Sputnik Planitia (the heart's western lobe). */
    private static final double SPUTNIK_LAT = 24;
    private static final double SPUTNIK_LON = 175;
    private static final double[] TARTARUS = unit(18, -105);
    /** The smaller dark maculae strung along the equator east of the heart: lat, lon, size. */
    private static final double[][] MACULAE = {{-14, -150, 210}, {-8, -115, 150}, {-18, -70, 260}, {-6, -30, 140}};
    private final double[][] maculae = new double[MACULAE.length][];

    public PlutoTerrain() {
        super(RADIUS, 0x506C75746F486561L);
        craters = new CraterField(RADIUS, seed, 0.7,
                new CraterField.Scale(320, 15, 45, 0.35, 0x81L),
                new CraterField.Scale(90, 4, 12, 0.35, 0x82L));
        for (int i = 0; i < MACULAE.length; i++) {
            maculae[i] = unit(MACULAE[i][0], MACULAE[i][1]);
        }
    }

    @Override
    public List<Biome> biomes() {
        return List.of(SPUTNIK, MOUNTAINS, CTHULHU, BLADES, PLAINS);
    }

    private static double wrap(double degrees) {
        return degrees - 360 * Math.floor((degrees + 180) / 360);
    }

    /**
     * Cthulhu: a whale-shaped dark band along the equator, about 3000 blocks long, its blunt head
     * against the heart's south-west and its tail thinning out to the west. 0..1.
     */
    private double cthulhu(double lat, double lon, double wobble) {
        double t = wrap(lon - 90) / 150 + 0.5; // 0 at the tail, 1 at the head
        if (t < -0.3 || t > 1.3) {
            return 0;
        }
        double tc = Math.max(0, Math.min(1, t));
        // The belt sags and rises along its length, fattest just behind the head.
        double middle = -12 - 8 * Math.sin(Math.PI * tc) + tc * 4 + 4 * Math.sin(tc * 11 + 1) * (1 - tc);
        double half = 3 + 20 * Math.pow(Math.sin(Math.PI * Math.min(1, tc * 0.72 + 0.04)), 0.9);
        double across = Math.abs(lat - middle + wobble * 14) / half;
        double along = t < 0 ? -t / 0.15 : t > 1 ? (t - 1) / 0.12 : 0;
        // Rounded ends: the head is a blunt dome, not a cut.
        return 1 - smoothstep(0.8, 1.05, Math.sqrt(across * across + along * along) + wobble * 0.35);
    }

    @Override
    public void sample(SpherePoint p, double latitude, TerrainSample out) {
        double lat = Math.toDegrees(latitude);
        double lon = Math.toDegrees(Math.atan2(p.z, p.x));
        double wobble = edge.sample(p);
        double fine = patches.sample(p);
        double height = BASE + swell.sample(p) * 5 + rough.sample(p) * 0.5;
        Biome biome = PLAINS;
        double tholin = cthulhu(lat, lon, wobble + fine * 0.3);
        for (int i = 0; i < maculae.length; i++) {
            // Stretched east-west like the real ones, edges lobed.
            double e = wrap(lon - MACULAE[i][1]) * Math.cos(latitude) / 1.9;
            double n = lat - MACULAE[i][0] + wobble * 6;
            double d = Math.sqrt(e * e + n * n) * Math.PI / 180 * RADIUS / MACULAE[i][2] + wobble * 0.6 + fine * 0.3;
            tholin = Math.max(tholin, (1 - smoothstep(0.75, 1.0, d)) * 0.9);
        }
        double blades = region(p, TARTARUS, 420, bladeWarp, 0.35) * (1 - smoothstep(0.3, 0.8, -wobble));
        double snow = 0;
        double ice = 0;
        double trough = 0;

        // Sputnik Planitia: a teardrop basin ~1000 blocks north to south, pointed at its southern
        // end, its shore wandering in and out. Flat, in convection cells.
        double east = wrap(lon - SPUTNIK_LON) * Math.cos(latitude) * RADIUS * Math.PI / 180;
        double north = (lat - SPUTNIK_LAT) * RADIUS * Math.PI / 180;
        double taper = 0.45 + 0.55 * smoothstep(-520, 150, north);
        double ds = Math.sqrt(Math.pow(east / (390 * taper), 2) + Math.pow(north / 520, 2)) + wobble * 0.16 + fine * 0.05;
        if (ds < 1.45) {
            ice = 1 - smoothstep(0.94, 1.0, ds);
            if (ice > 0) {
                double[] c = cells(p, 28, 5);
                trough = 1 - smoothstep(0.5, 3.5, c[1] - c[0]);
                double dome = Math.max(0, 1 - c[0] / 16) * 1.2;
                double floor = BASE - 10 + dome - trough * 2.5;
                height = height * (1 - ice) + floor * ice;
                biome = ice > 0.5 ? SPUTNIK : biome;
            }
            // Water-ice mountains crowding the western and northern shore.
            double shore = Math.exp(-Math.pow((ds - 1.1) / 0.12, 2)) * smoothstep(-150, 150, north - east);
            if (shore > 0.05) {
                // Jumbled blocks of water ice: ragged peaks, not cones.
                double[] m = cells(p, 40, 6);
                double ragged = m[0] + hills.sample(p.x * 3, p.y * 3, p.z * 3) * 14;
                double peak = Math.pow(Math.max(0, 1 - ragged / 26), 1.4) * (20 + m[2] * 40);
                double crags = Math.abs(rough.sample(p.x * 0.6, p.y * 0.6, p.z * 0.6)) * 6;
                height += shore * (peak + crags + Math.abs(hills.sample(p)) * 10);
                if (shore > 0.4) {
                    biome = MOUNTAINS;
                }
            }
        }
        // The heart's eastern lobe: bright methane frost laid in patches over the uplands.
        double eastLobe = 0;
        {
            double e2 = wrap(lon - 207) * Math.cos(latitude) * RADIUS * Math.PI / 180;
            double n2 = (lat - 8) * RADIUS * Math.PI / 180;
            double d = Math.sqrt(Math.pow(e2 / 430, 2) + Math.pow(n2 / 560, 2)) + wobble * 0.3;
            eastLobe = (1 - smoothstep(0.7, 1.0, d)) * smoothstep(-0.25, 0.15, fine + 0.25 - d * 0.3);
        }
        tholin *= 1 - Math.max(ice, eastLobe);
        if (tholin > 0 && ice < 0.5) {
            height += tholin * (Math.abs(hills.sample(p)) * 22 - 3);
            if (tholin > 0.5) {
                biome = CTHULHU;
            }
        }
        if (blades > 0 && ice < 0.5) {
            // Rows of north-south ice fins ~7 blocks apart, up to ~25 tall.
            double phase = Math.toRadians(lon) * RADIUS * Math.cos(latitude) / 8 + bladeWarp.sample(p) * 0.6 + lat * 0.08;
            double f = Math.abs(Math.sin(phase * Math.PI));
            double tall = 14 + Math.abs(hills.sample(p)) * 22;
            height += blades * (Math.pow(f, 7) * tall + 6);
            if (blades > 0.5) {
                biome = BLADES;
            }
        }
        CraterField.Sample c = craters(craters, p, (1 - ice) * (blades > 0.5 ? 0.2 : 0.6 + tholin * 0.6));
        height += c.height;
        // Methane snow on everything high, the heart's eastern lobe, and the pale north (its
        // edge ragged, thinning into streaks).
        double polar = smoothstep(38, 62, lat + wobble * 14 + fine * 5) * (1 - tholin);
        snow = Math.max(Math.max(smoothstep(BASE + 18, BASE + 26, height), polar), eastLobe * 0.9);
        out.height = height;
        out.temperature = ice;
        out.humidity = clamp01(tholin * (1.05 + 0.25 * fine));
        out.river = blades;
        out.seaIceDepth = snow;
        out.under = trough;
        out.biome = biome;
    }

    @Override
    public int surfaceColor(TerrainSample s) {
        int base = PLAINS.mapColor();
        base = mix(base, CTHULHU.mapColor(), s.humidity);
        base = mix(base, BLADES.mapColor(), s.river);
        base = mix(base, SPUTNIK.mapColor(), s.temperature * (1 - s.under * 0.4));
        base = mix(base, 0xECE6DC, s.seaIceDepth * 0.8);
        if (s.biome == MOUNTAINS) {
            base = mix(base, MOUNTAINS.mapColor(), 0.5);
        }
        return base;
    }
}
