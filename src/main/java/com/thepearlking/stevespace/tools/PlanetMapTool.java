package com.thepearlking.stevespace.tools;

import com.thepearlking.stevespace.world.planet.Shaping;
import com.thepearlking.stevespace.world.planet.earth.EarthClimate;
import com.thepearlking.stevespace.world.planet.earth.EarthSettings;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.stream.IntStream;

/**
 * Developer tool (not part of the game): renders Earth's planet-scale layout (continents and
 * climate zones) to PNG, roughly coloured the way vanilla will fill them with biomes.
 * Usage: PlanetMapTool outputDir [seed...]
 */
public final class PlanetMapTool {

    public static void main(String[] args) throws IOException {
        File outDir = new File(args.length > 0 ? args[0] : "build/planet-maps");
        outDir.mkdirs();
        for (int i = 1; i < Math.max(2, args.length); i++) {
            long seed = args.length > i ? Long.parseLong(args[i]) : 1234567L;
            render(new EarthClimate(seed, EarthSettings.DEFAULT), 1600, new File(outDir, "earth_" + seed + ".png"));
        }
    }

    static void render(EarthClimate climate, int width, File file) throws IOException {
        int height = width / 2;
        double c = climate.geometry().circumference();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        IntStream.range(0, height).parallel().forEach(py -> {
            double z = -c / 4 + (py + 0.5) * c / 2 / height;
            for (int px = 0; px < width; px++) {
                double x = -c / 2 + (px + 0.5) * c / width;
                image.setRGB(px, py, color(climate.continentalness(x, z), climate.temperature(x, z), climate.humidity(x, z)));
            }
        });
        ImageIO.write(image, "png", file);
        System.out.println("Wrote " + file);
    }

    /** Approximates which vanilla biome family would appear, by its colour from orbit. */
    static int color(double continentalness, double temperature, double humidity) {
        if (continentalness < -0.19) {
            int sea = Shaping.mixColor(0x0f2b52, 0x1d4f80, Shaping.ramp(-1.0, -0.2, continentalness));
            return temperature < -0.45 ? 0xc9d6de : sea;
        }
        if (continentalness < -0.11) {
            return temperature < -0.45 ? 0xdde3e6 : 0xd8c99a;
        }
        if (temperature < -0.45) {
            return 0xeef2f4;
        }
        if (temperature > 0.55) {
            return 0xd3be87;
        }
        if (temperature > 0.2) {
            return humidity > 0.3 ? 0x2e6627 : (humidity > -0.1 ? 0x5a7f3a : 0xa39a5a);
        }
        if (temperature < -0.15) {
            return humidity > 0 ? 0x3e5a3c : 0x7c8f62;
        }
        return humidity > 0.1 ? 0x3f6a33 : 0x7c9550;
    }
}
