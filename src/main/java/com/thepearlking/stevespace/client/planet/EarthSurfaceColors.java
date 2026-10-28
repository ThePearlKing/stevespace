package com.thepearlking.stevespace.client.planet;

import com.thepearlking.stevespace.world.planet.PlanetGeometry;
import com.thepearlking.stevespace.world.planet.Shaping;
import com.thepearlking.stevespace.world.planet.earth.EarthClimate;
import com.thepearlking.stevespace.world.planet.noise.SphereNoise;
import com.thepearlking.stevespace.world.planet.noise.SpherePoint;

/**
 * Colour of Earth's surface seen from altitude, from the same continent and climate layout the
 * server generates with. Land colours follow vanilla's biome bands (Minecraft picks biomes from
 * these same temperature and humidity values) so the far planet matches the chunks near you.
 */
final class EarthSurfaceColors {

    // Rows: temperature anchors. Columns: humidity anchors. Bilinear blend between them.
    private static final double[] T = {-1.0, -0.5, -0.4, -0.15, 0.2, 0.5, 0.6, 1.0};
    private static final double[] H = {-1.0, -0.35, -0.1, 0.1, 0.3, 1.0};
    private static final int[][] LAND = {
            // dry ............................................. wet
            {0xE9EEF0, 0xE9EEF0, 0xE6ECEE, 0xE3EAEC, 0xDFE7EA, 0xDDE5E8},   // frozen
            {0xE2E8E9, 0xDDE4E3, 0xD5DDDA, 0xCDD6D2, 0xC4CFC9, 0xBDC9C3},   // frozen edge
            {0x9AAA86, 0x8C9F75, 0x6E8A62, 0x557458, 0x48664B, 0x415E45},   // cold: plains .. taiga
            {0x8BA265, 0x7C9A57, 0x67904C, 0x4E7A3E, 0x456F38, 0x3E6533},   // cold/temperate edge
            {0x94A65B, 0x86A052, 0x6C9548, 0x4D7A39, 0x3B6530, 0x30552A},   // temperate: plains .. dark forest
            {0xA49F56, 0x9E9D52, 0x80954A, 0x5A8738, 0x437C2E, 0x3A752B},   // warm: savanna .. jungle
            {0xC8B77A, 0xC2B272, 0xB4AD6A, 0xA9A862, 0x9FA35E, 0x98A05A},   // warm/hot edge
            {0xD9C690, 0xD8C58E, 0xD6C48C, 0xD4C28A, 0xD2C088, 0xD0BE86},   // hot: desert
    };

    private final EarthClimate climate;
    private final SphereNoise variation;

    EarthSurfaceColors(EarthClimate climate, long seed) {
        this.climate = climate;
        this.variation = new SphereNoise(seed + 77, 40, 3, 0.5);
    }

    PlanetGeometry geometry() {
        return climate.geometry();
    }

    /** Whether the map shows sea here. */
    boolean isSea(double x, double z) {
        return climate.continentalness(x, z) < -0.19;
    }

    int colorAt(double x, double z) {
        double continentalness = climate.continentalness(x, z);
        double temperature = climate.temperature(x, z);
        double latitude = climate.geometry().latitude(z);
        double longitude = climate.geometry().longitude(x);
        double detail = variation.sample(new SpherePoint().set(latitude, longitude));

        if (continentalness < -0.19) {
            // Sea: deep blue offshore, lighter over shelves, frozen toward the poles.
            int deep = 0x0E2A52;
            int shelf = 0x245E86;
            int water = Shaping.mixColor(deep, shelf, Shaping.smoothstep(-0.6, -0.2, continentalness));
            if (temperature < -0.45) {
                water = Shaping.mixColor(water, 0xD3DEE4, Shaping.smoothstep(-0.45, -0.6, temperature) * 0.85);
            }
            return Shaping.shade(water, 1 + detail * 0.02);
        }
        double humidity = climate.humidity(x, z);
        int land = vivid(landColor(temperature, humidity));
        if (continentalness < -0.11 && temperature > -0.45) {
            // Beaches and coastal sand.
            land = Shaping.mixColor(land, 0xD3C796, 0.6);
        }
        return Shaping.shade(land, 1 + detail * 0.06);
    }

    /**
     * Minecraft land seen from above is more saturated and darker than satellite imagery
     * (bright grass tints, dense leaf canopies with shadow); push the map toward that so the far
     * planet blends into the real chunks at render distance.
     */
    private static int vivid(int rgb) {
        double r = (rgb >> 16) & 255;
        double g = (rgb >> 8) & 255;
        double b = rgb & 255;
        double grey = r * 0.3 + g * 0.59 + b * 0.11;
        double saturation = grey > 200 ? 1.0 : 1.35; // leave snow and ice alone
        double darken = grey > 200 ? 1.0 : 0.9;
        r = (grey + (r - grey) * saturation) * darken;
        g = (grey + (g - grey) * saturation) * darken;
        b = (grey + (b - grey) * saturation) * darken;
        return (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    private static int clamp(double v) {
        return (int) Math.max(0, Math.min(255, Math.round(v)));
    }

    private static int landColor(double t, double h) {
        int ti = segment(T, t);
        int hi = segment(H, h);
        double ft = Shaping.clamp01((t - T[ti]) / (T[ti + 1] - T[ti]));
        double fh = Shaping.clamp01((h - H[hi]) / (H[hi + 1] - H[hi]));
        int top = Shaping.mixColor(LAND[ti][hi], LAND[ti][hi + 1], fh);
        int bottom = Shaping.mixColor(LAND[ti + 1][hi], LAND[ti + 1][hi + 1], fh);
        return Shaping.mixColor(top, bottom, ft);
    }

    private static int segment(double[] anchors, double v) {
        for (int i = 0; i < anchors.length - 2; i++) {
            if (v < anchors[i + 1]) {
                return i;
            }
        }
        return anchors.length - 2;
    }
}
