package com.thepearlking.stevespace.world.planet.earth;

import com.thepearlking.stevespace.world.planet.PlanetGeometry;
import com.thepearlking.stevespace.world.planet.noise.GradientNoise;
import com.thepearlking.stevespace.world.planet.noise.SphereNoise;
import com.thepearlking.stevespace.world.planet.noise.SpherePoint;

import java.util.Arrays;
import java.util.Random;

import static com.thepearlking.stevespace.world.planet.Shaping.*;

/**
 * Earth at planet scale: where the continents and oceans are, and how warm and wet each
 * latitude is. Everything at the scale you walk around in (hills, cliffs, rivers, caves) is
 * Minecraft's own terrain generator; this class only replaces vanilla's continentalness,
 * temperature and vegetation inputs so the world is a real globe.
 *
 * <p><b>Continents</b> are built like real ones: a handful of large tectonic plates (seeded
 * points on the sphere) decide where the big landmasses sit, then strongly warped fractal noise
 * gives them irregular coastlines with peninsulas, bays and a few island chains, while keeping
 * the open ocean mostly free of speckle islands.
 *
 * <p><b>Seams:</b> the date line (the world's east/west edge) and both poles are always ocean,
 * so the sea surface matches perfectly where the world wraps around.
 */
public final class EarthClimate {

    public static final double RADIUS = 6371;

    private final PlanetGeometry geometry = new PlanetGeometry(RADIUS);
    private final EarthSettings settings;

    private final DVec[] plateCenters;
    private final boolean[] plateIsContinental;
    private final double[] plateStrength;
    private final SphereNoise coastNoise;
    private final SphereNoise warpA;
    private final SphereNoise warpB;
    private final SphereNoise warpC;
    private final SphereNoise fineWarp;
    private final SphereNoise temperatureNoise;
    private final SphereNoise humidityNoise;
    private final GradientNoise speckle;

    private final double coastThreshold;
    private final double spread;

    private record DVec(double x, double y, double z) {
    }

    public EarthClimate(long seed, EarthSettings settings) {
        this.settings = settings;
        double size = settings.continentSize();

        // Tectonic plates: ~14 on a real-sized Earth; bigger continents = fewer plates.
        int plateCount = (int) Math.max(5, Math.round(14 / size));
        Random random = new Random(seed ^ 0x51A7E5L);
        plateCenters = new DVec[plateCount];
        plateIsContinental = new boolean[plateCount];
        plateStrength = new double[plateCount];
        for (int i = 0; i < plateCount; i++) {
            double z = random.nextDouble() * 2 - 1;
            double a = random.nextDouble() * 2 * Math.PI;
            double r = Math.sqrt(1 - z * z);
            plateCenters[i] = new DVec(r * Math.cos(a), z, r * Math.sin(a));
            plateIsContinental[i] = random.nextDouble() < 0.45;
            plateStrength[i] = 0.6 + random.nextDouble() * 0.6;
        }

        coastNoise = new SphereNoise(seed + 101, 2.2 / size, 10, 0.56);
        warpA = new SphereNoise(seed + 102, 1.3 / size, 4, 0.5);
        warpB = new SphereNoise(seed + 103, 1.3 / size, 4, 0.5);
        warpC = new SphereNoise(seed + 104, 1.3 / size, 4, 0.5);
        fineWarp = new SphereNoise(seed + 105, 7.0 / size, 3, 0.5);
        temperatureNoise = new SphereNoise(seed + 106, 4.0, 3, 0.5);
        humidityNoise = new SphereNoise(seed + 107, 3.5, 4, 0.55);
        speckle = new GradientNoise(seed + 108);

        double[] q = measureDistribution();
        coastThreshold = q[0];
        spread = q[1];
    }

    public PlanetGeometry geometry() {
        return geometry;
    }

    // ---------------------------------------------------------------------------------------
    // Continents
    // ---------------------------------------------------------------------------------------

    /**
     * How far inland a point is, in "standard deviations": 0 on the coast, about +1.5 deep
     * inside a continent, about -1.5 in the deep ocean. Includes the ocean seams.
     */
    public double landness(double x, double z) {
        double latitude = geometry.latitude(z);
        if (settings.real()) {
            return realLandness(latitude, geometry.longitude(x));
        }
        SpherePoint p = new SpherePoint().set(latitude, geometry.longitude(x));
        double value = (raw(p.x, p.y, p.z) - coastThreshold) / spread;

        // Date-line ocean: within ~2000 blocks of the world's east/west edge it is always deep sea.
        double fromEdge = geometry.circumference() / 2.0 - Math.abs(geometry.wrapX(x));
        double edgeOcean = smoothstep(2600, 700, fromEdge);
        // Polar oceans (frozen): land fades out from ~78° and is gone by ~85°.
        double absLat = Math.toDegrees(Math.abs(latitude));
        double polarOcean = smoothstep(settings.iceCapLatitude() + 8, settings.iceCapLatitude() + 15, absLat);
        double forced = Math.max(edgeOcean, polarOcean);
        return lerp(forced, value, Math.min(value, -1.4));
    }

    /**
     * The Real Earth: how far inland from the real height (or depth) here. Coasts sit at sea level;
     * the continental shelf is shallow sea; high plateaus and mountain ranges are far inland. The
     * data's points are 10 km apart, so the line of the coast is roughened a little between them.
     */
    private double realLandness(double latitude, double longitude) {
        return curve(realMetres(latitude, longitude), REAL_METRES, REAL_LANDNESS);
    }

    /** The real height here (metres), looked up a little off the true spot so coasts aren't the data's straight steps. */
    private double realMetres(double latitude, double longitude) {
        SpherePoint p = new SpherePoint().set(latitude, longitude);
        double nudge = 0.0003; // ~2 km, in radians (the coastline map is that fine)
        double lat = latitude + fineWarp.sample(p) * nudge;
        double lon = longitude + fineWarp.sample(p.y, p.z, p.x) * nudge / Math.max(0.2, Math.cos(latitude));
        return EarthElevation.metres(lat, lon) + speckle.sample(p.x * 900, p.y * 900, p.z * 900) * 25;
    }

    /**
     * Real Earth: how many blocks above (or below) sea level the ground stands here, straight
     * from the real heights. Everest comes out about 80 up and everything lower in proportion,
     * gently (a 4 km volcano is a broad dome, not a spire); the deep ocean floor about 40 down.
     */
    public double realHeight(double x, double z) {
        double metres = realMetres(geometry.latitude(z), geometry.longitude(x));
        if (metres >= 0) {
            return 81 * Math.pow(metres / 8848, 0.6);
        }
        return -60 * Math.sqrt(Math.min(1, -metres / 11000));
    }

    /**
     * How deep inside a real continent a point is, on the same scale as landness (0 at the coast,
     * ~2 in the middle of the Sahara or Siberia): the share of land within about 1000 km.
     */
    private double realInland(double latitude, double longitude) {
        if (inland == null) {
            buildInland();
        }
        double fx = (longitude + Math.PI) / (2 * Math.PI) * IW - 0.5;
        double fy = (Math.PI / 2 - latitude) / Math.PI * IH - 0.5;
        int x0 = (int) Math.floor(fx);
        int y0 = (int) Math.floor(fy);
        double tx = fx - x0;
        double ty = fy - y0;
        double a = inlandAt(x0, y0) * (1 - tx) + inlandAt(x0 + 1, y0) * tx;
        double b = inlandAt(x0, y0 + 1) * (1 - tx) + inlandAt(x0 + 1, y0 + 1) * tx;
        double share = a * (1 - ty) + b * ty;
        double here = EarthElevation.metres(latitude, longitude) > 0 ? 1 : 0;
        return here > 0 ? (share - 0.5) * 4.4 : -1;
    }

    private static final int IW = 512;
    private static final int IH = 256;
    private float[] inland;

    private float inlandAt(int x, int y) {
        return inland[Math.max(0, Math.min(IH - 1, y)) * IW + Math.floorMod(x, IW)];
    }

    /** Land share round each point of a coarse grid, averaged over ~1000 km (wider in longitude near the poles). */
    private synchronized void buildInland() {
        if (inland != null) {
            return;
        }
        float[] land = new float[IW * IH];
        for (int y = 0; y < IH; y++) {
            double lat = Math.PI / 2 - (y + 0.5) / IH * Math.PI;
            for (int x = 0; x < IW; x++) {
                double lon = (x + 0.5) / IW * 2 * Math.PI - Math.PI;
                land[y * IW + x] = EarthElevation.metres(lat, lon) > 0 ? 1 : 0;
            }
        }
        // Rain comes in on the wind, so what matters is the land upwind: to the east in the trade
        // winds (deserts on the west sides of continents: Sahara, Atacama, Namib, Western
        // Australia), to the west in the westerlies further out (the Gobi, the Great Basin).
        double cell = 2 * Math.PI * RADIUS / IW; // km per cell along the equator
        int ry = (int) Math.round(700 / cell);
        float[] out = new float[IW * IH];
        for (int y = 0; y < IH; y++) {
            double lat = Math.PI / 2 - (y + 0.5) / IH * Math.PI;
            double absLat = Math.toDegrees(Math.abs(lat));
            int reach = (int) Math.min(IW / 2, Math.round(2000 / cell / Math.max(0.1, Math.cos(lat))));
            double westerly = smoothstep(26, 36, absLat);
            for (int x = 0; x < IW; x++) {
                double east = 0;
                double west = 0;
                int count = 0;
                for (int dy = -ry; dy <= ry; dy += 2) {
                    int yy = Math.max(0, Math.min(IH - 1, y + dy));
                    for (int d = 0; d <= reach; d += Math.max(1, reach / 10)) {
                        east += land[yy * IW + Math.floorMod(x + d, IW)];
                        west += land[yy * IW + Math.floorMod(x - d, IW)];
                        count++;
                    }
                }
                out[y * IW + x] = (float) (lerp(westerly, east, west) / count);
            }
        }
        inland = out;
    }

    /** The real heights matched to landness (toVanillaContinentalness turns that into vanilla's bands). */
    private static final double[] REAL_METRES = {-6000, -3000, -200, -40, 0, 30, 300, 1500, 5000};
    // Never as low as vanilla's mushroom fields band (below -1.05): the real Earth has none.
    private static final double[] REAL_LANDNESS = {-1.0, -0.8, -0.5, -0.12, 0.0, 0.06, 0.35, 0.9, 2.2};

    /**
     * Vanilla's erosion for the Real Earth: low (rugged, mountainous) where the real ground is
     * high, high (flat) on lowland plains; elsewhere the vanilla noise as it is.
     */
    public double erosion(double x, double z, double vanilla) {
        if (!settings.real()) {
            return vanilla;
        }
        double metres = EarthElevation.metres(geometry.latitude(z), geometry.longitude(x));
        if (metres <= 0) {
            return vanilla;
        }
        double target = metres > 3000 ? -0.95 : metres > 1200 ? lerp((metres - 1200) / 1800, -0.5, -0.95)
                : metres > 400 ? lerp((metres - 400) / 800, 0.0, -0.5) : lerp(metres / 400, 0.45, 0.0);
        // High ground follows the data closely (vanilla's tallest peaks need the lowest erosion); low ground keeps more of vanilla's variety.
        double weight = metres > 1200 ? 0.92 : 0.7;
        return lerp(weight, vanilla, target);
    }

    /** {@link #landness} mapped onto Minecraft's continentalness scale (-1.05 deep sea .. 1 inland). */
    public double continentalness(double x, double z) {
        return toVanillaContinentalness(landness(x, z));
    }

    static double toVanillaContinentalness(double landness) {
        // Breakpoints chosen so vanilla's own bands land in the right places:
        // deep ocean < -0.455 < ocean < -0.19 < coast < -0.11 < near inland < 0.03 < mid < 0.3 < far.
        double[] in = {-3.0, -1.2, -0.5, -0.12, 0.0, 0.06, 0.35, 0.9, 2.2};
        double[] out = {-1.1, -0.75, -0.42, -0.21, -0.15, -0.105, 0.04, 0.32, 0.85};
        if (landness <= in[0]) {
            return out[0];
        }
        for (int i = 1; i < in.length; i++) {
            if (landness <= in[i]) {
                double t = (landness - in[i - 1]) / (in[i] - in[i - 1]);
                return lerp(t, out[i - 1], out[i]);
            }
        }
        return out[out.length - 1];
    }

    private double raw(double px, double py, double pz) {
        // Two levels of domain warping turn round blobs into ragged, believable coastlines.
        double w = 0.7;
        double ax = px + warpA.sample(px, py, pz) * w;
        double ay = py + warpB.sample(px, py, pz) * w;
        double az = pz + warpC.sample(px, py, pz) * w;
        double len = Math.sqrt(ax * ax + ay * ay + az * az);
        ax /= len;
        ay /= len;
        az /= len;
        double f = 0.16;
        double bx = ax + fineWarp.sample(ax, ay, az) * f;
        double by = ay + fineWarp.sample(ay, az, ax) * f;
        double bz = az + fineWarp.sample(az, ax, ay) * f;
        len = Math.sqrt(bx * bx + by * by + bz * bz);
        bx /= len;
        by /= len;
        bz /= len;

        // Plates: the nearest plate centre decides continental or oceanic crust; the edge between
        // two plates blends smoothly so continents aren't Voronoi-shaped.
        double best = -2;
        double second = -2;
        int bestIndex = 0;
        for (int i = 0; i < plateCenters.length; i++) {
            DVec c = plateCenters[i];
            double d = c.x * ax + c.y * ay + c.z * az;
            if (d > best) {
                second = best;
                best = d;
                bestIndex = i;
            } else if (d > second) {
                second = d;
            }
        }
        double plate = plateIsContinental[bestIndex] ? plateStrength[bestIndex] : -plateStrength[bestIndex] * 0.8;
        double edge = smoothstep(0, 0.12, best - second); // 0 on the boundary, 1 inside
        double crust = plate * edge;

        // Fractal detail is strongest near coasts and fades in open ocean, so the deep sea stays
        // mostly clear of speckle islands while coastlines stay intricate.
        double detail = coastNoise.sample(bx, by, bz);
        return crust * 0.85 + detail * 0.85;
    }

    // ---------------------------------------------------------------------------------------
    // Climate (in Minecraft's parameter ranges)
    // ---------------------------------------------------------------------------------------

    /**
     * Temperature parameter. Vanilla bands: frozen < -0.45 < cold < -0.15 < temperate < 0.2 < warm
     * < 0.55 < hot. Equator warm (jungles), subtropics hot (deserts), mid-latitudes temperate, high
     * latitudes cold, polar frozen.
     */
    public double temperature(double x, double z) {
        double latitude = geometry.latitude(z);
        double absLat = Math.toDegrees(Math.abs(latitude));
        SpherePoint p = new SpherePoint().set(latitude, geometry.longitude(x));
        double iceShift = settings.iceCapLatitude() - EarthSettings.DEFAULT.iceCapLatitude();
        double[] lat = {0, 12, 19, 31, 38, 50, 57, 64 + iceShift, 72 + iceShift, 90};
        double[] t = {0.40, 0.42, 0.45, 0.45, 0.02, -0.02, -0.28, -0.34, -0.62, -0.9};
        // Latitude sets the climate zone, but the boundaries wander a lot (±8° or so), like real
        // climate zones bent by ocean currents and mountains.
        double wander = temperatureNoise.sample(p) * 9;
        double base = curve(Math.max(0, absLat + wander), lat, t);
        // Minecraft turns everything "hot" into desert, so only the dry parts of the subtropics
        // get hot: deserts become patches inside continents (Sahara, Australia), not stripes.
        double subtropics = smoothstep(10, 18, absLat + wander) * (1 - smoothstep(30, 38, absLat + wander));
        double dryness = smoothstep(0.0, -0.45, humidity(x, z));
        base += subtropics * dryness * 0.32;
        return clamp(base + temperatureNoise.sample(p.z, p.x, p.y) * 0.08, -1, 1);
    }

    /**
     * Humidity (vanilla "vegetation") parameter, bands at -0.35, -0.1, 0.1, 0.3. Wet tropics, dry
     * subtropical belts, damp mid-latitudes; continental interiors are drier than coasts.
     */
    public double humidity(double x, double z) {
        double latitude = geometry.latitude(z);
        double absLat = Math.toDegrees(Math.abs(latitude));
        SpherePoint p = new SpherePoint().set(latitude, geometry.longitude(x));
        double[] lat = {0, 10, 19, 26, 34, 45, 60, 75, 90};
        double[] h = {0.55, 0.45, -0.05, -0.45, -0.05, 0.15, 0.1, -0.05, -0.1};
        double wander = humidityNoise.sample(p.y, p.z, p.x) * 8;
        double base = curve(Math.max(0, absLat + wander), lat, h) * 0.75;
        // Coasts are wet and interiors dry: that is what makes real deserts sit inside continents.
        double land = settings.real() ? realInland(latitude, geometry.longitude(x)) : landness(x, z);
        double coastal = (1 - smoothstep(0.0, 0.7, land)) * 0.3;
        double interior = smoothstep(0.7, 1.9, land) * 0.35;
        if (settings.real()) {
            // Real rainforests sit deep inland too (the Amazon, the Congo): the tropics make their own rain.
            interior *= smoothstep(9, 17, absLat);
        }
        return clamp(base + humidityNoise.sample(p) * 0.45 + coastal - interior, -1, 1);
    }

    private static double curve(double v, double[] xs, double[] ys) {
        for (int i = 1; i < xs.length; i++) {
            if (v <= xs[i]) {
                double t = smoothstep(xs[i - 1], xs[i], v);
                return lerp(t, ys[i - 1], ys[i]);
            }
        }
        return ys[ys.length - 1];
    }

    // ---------------------------------------------------------------------------------------
    // Setup
    // ---------------------------------------------------------------------------------------

    private double[] measureDistribution() {
        int count = 30000;
        double[] values = new double[count];
        double golden = Math.PI * (3 - Math.sqrt(5));
        for (int i = 0; i < count; i++) {
            double y = 1 - 2 * (i + 0.5) / count;
            double r = Math.sqrt(1 - y * y);
            values[i] = raw(Math.cos(golden * i) * r, y, Math.sin(golden * i) * r);
        }
        Arrays.sort(values);
        // Land fraction is measured before the forced polar/date-line oceans, which remove a bit.
        double threshold = values[(int) ((1 - settings.landFraction() * 1.08) * (count - 1))];
        double sigma = (values[(int) (0.84 * (count - 1))] - values[(int) (0.16 * (count - 1))]) / 2;
        return new double[]{threshold, Math.max(1e-6, sigma)};
    }
}
