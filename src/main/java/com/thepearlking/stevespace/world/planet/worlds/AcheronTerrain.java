package com.thepearlking.stevespace.world.planet.worlds;

import com.thepearlking.stevespace.world.planet.BodyTerrain;
import com.thepearlking.stevespace.world.planet.TerrainSample;
import com.thepearlking.stevespace.world.planet.noise.SphereNoise;
import com.thepearlking.stevespace.world.planet.noise.SpherePoint;

import java.io.InputStream;
import java.util.List;

/**
 * Acheron. Its ground comes from maps made from the old drawings (tools/acheron/make_map.py):
 * green crust with dark red bloodstone in a few big shapes and darker stained ground round them,
 * craters of every size, and on the far side a great impact basin, rings of cliffs round a sunken
 * floor with a pale middle. The two splotches face Earth. The maps are a few blocks to a pixel;
 * the last few blocks of lumps and knobs are added here.
 *
 * <p>Sample fields: {@code humidity} = bloodstone (0..1), {@code temperature} = the darker green
 * (0..1), {@code seaIceDepth} = the pale middle (0..1), {@code river} = bare rock (0..1).
 */
public final class AcheronTerrain extends BodyTerrain {

    public static final double RADIUS = 1737;
    private static final double BASE = 90;

    public static final Biome CRUST = biome("acheron_crust", 0x35874F);
    public static final Biome BLOODSTONE = biome("acheron_bloodstone", 0x873636);

    private static final int[] COLORS = {0x35874F, 0x317747, 0x873636, 0x4A9863, 0x4E6C57};
    private static final Map KIND = Map.load("surface", false);
    private static final Map COLOR = Map.load("color", false);
    private static final Map HEIGHT = Map.load("height", true);

    /** The fine lumps of the ground, a few dozen blocks across. */
    private final SphereNoise lumps = new SphereNoise(seed + 5, 45, 4, 0.5);
    /** The bloodstone's knobbly grain. */
    private final SphereNoise knobs = new SphereNoise(seed + 6, 140, 3, 0.55);
    /** Jitters the edges between kinds of ground by a few blocks, so they aren't the maps' pixel steps. */
    private final SphereNoise jitterA = new SphereNoise(seed + 3, 120, 3, 0.5);
    private final SphereNoise jitterB = new SphereNoise(seed + 4, 120, 3, 0.5);

    public AcheronTerrain() {
        super(RADIUS, 0x416368657230AL);
    }

    @Override
    public List<Biome> biomes() {
        return List.of(CRUST, BLOODSTONE);
    }

    /** The map's kind of ground at a latitude and longitude (0 crust, 1 darker green, 2 bloodstone, 3 pale, 4 rock). */
    public static int kind(double latitude, double longitude) {
        return KIND.nearest(latitude, longitude);
    }

    @Override
    public void sample(SpherePoint p, double latitude, TerrainSample out) {
        double longitude = Math.atan2(p.z, p.x);
        double jitter = 0.003; // about five blocks
        int k = kind(latitude + jitterA.sample(p) * jitter, longitude + jitterB.sample(p) * jitter / Math.max(0.2, Math.cos(latitude)));
        double grain = knobs.sample(p);
        double lump = lumps.sample(p);
        double height = BASE + HEIGHT.smooth(latitude, longitude) / 100.0 + lump * 2.5;
        if (k == 2) {
            height += grain * 1.5;
        }
        out.height = height;
        out.humidity = k == 2 ? 1 : 0;
        out.temperature = k == 1 ? 1 : 0;
        out.seaIceDepth = k == 3 ? 1 : 0;
        out.river = k == 4 ? 1 : 0;
        out.biome = k == 2 ? BLOODSTONE : CRUST;
        int c = COLOR.available() ? COLOR.smoothRgb(latitude, longitude) : COLORS[k];
        out.color = shade(c, 1 + lump * 0.06 + (k == 2 ? grain * 0.06 : 0));
    }

    private static int shade(int c, double k) {
        int r = (int) Math.max(0, Math.min(255, ((c >> 16) & 255) * k));
        int g = (int) Math.max(0, Math.min(255, ((c >> 8) & 255) * k));
        int b = (int) Math.max(0, Math.min(255, (c & 255) * k));
        return Math.max(1, (r << 16) | (g << 8) | b);
    }

    @Override
    public int surfaceColor(TerrainSample s) {
        if (s.color != 0) {
            return s.color;
        }
        int k = s.humidity > 0.5 ? 2 : s.temperature > 0.5 ? 1 : s.seaIceDepth > 0.5 ? 3 : s.river > 0.5 ? 4 : 0;
        return COLORS[k];
    }

    /** One of the maps, equirectangular, longitude -180..180 left to right. */
    private record Map(int w, int h, int[] data) {

        static Map load(String name, boolean sixteen) {
            try (InputStream in = AcheronTerrain.class.getResourceAsStream("/data/stevespace/acheron/" + name + ".png")) {
                if (in != null) {
                    var image = javax.imageio.ImageIO.read(in);
                    int w = image.getWidth();
                    int h = image.getHeight();
                    int[] data = new int[w * h];
                    var raster = image.getRaster();
                    boolean rgb = raster.getNumBands() >= 3;
                    for (int y = 0; y < h; y++) {
                        for (int x = 0; x < w; x++) {
                            data[y * w + x] = rgb
                                    ? (raster.getSample(x, y, 0) << 16) | (raster.getSample(x, y, 1) << 8) | raster.getSample(x, y, 2)
                                    : sixteen ? raster.getSample(x, y, 0) - 32768 : raster.getSample(x, y, 0);
                        }
                    }
                    return new Map(w, h, data);
                }
            } catch (java.io.IOException e) {
                // fall through: an empty map
            }
            return new Map(1, 1, new int[1]);
        }

        boolean available() {
            return data.length > 1;
        }

        int nearest(double latitude, double longitude) {
            int x = Math.floorMod((int) Math.floor((longitude + Math.PI) / (2 * Math.PI) * w), w);
            int y = Math.max(0, Math.min(h - 1, (int) Math.floor((Math.PI / 2 - latitude) / Math.PI * h)));
            return data[y * w + x];
        }

        private int at(int x, int y) {
            return data[Math.max(0, Math.min(h - 1, y)) * w + Math.floorMod(x, w)];
        }

        double smooth(double latitude, double longitude) {
            double fx = (longitude + Math.PI) / (2 * Math.PI) * w - 0.5;
            double fy = (Math.PI / 2 - latitude) / Math.PI * h - 0.5;
            int x0 = (int) Math.floor(fx);
            int y0 = (int) Math.floor(fy);
            double tx = fx - x0;
            double ty = fy - y0;
            double a = at(x0, y0) * (1 - tx) + at(x0 + 1, y0) * tx;
            double b = at(x0, y0 + 1) * (1 - tx) + at(x0 + 1, y0 + 1) * tx;
            return a * (1 - ty) + b * ty;
        }

        int smoothRgb(double latitude, double longitude) {
            double fx = (longitude + Math.PI) / (2 * Math.PI) * w - 0.5;
            double fy = (Math.PI / 2 - latitude) / Math.PI * h - 0.5;
            int x0 = (int) Math.floor(fx);
            int y0 = (int) Math.floor(fy);
            double tx = fx - x0;
            double ty = fy - y0;
            int result = 0;
            for (int shift = 16; shift >= 0; shift -= 8) {
                double a = (at(x0, y0) >> shift & 255) * (1 - tx) + (at(x0 + 1, y0) >> shift & 255) * tx;
                double b = (at(x0, y0 + 1) >> shift & 255) * (1 - tx) + (at(x0 + 1, y0 + 1) >> shift & 255) * tx;
                result |= ((int) Math.round(a * (1 - ty) + b * ty)) << shift;
            }
            return result;
        }
    }
}
