package com.thepearlking.stevespace.client.sky;

import com.mojang.blaze3d.platform.NativeImage;
import com.thepearlking.stevespace.StevesSpace;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Small textures the sky draws with, generated in code so they are perfectly smooth:
 * a soft round glow (stars, planet dots, the sun's halo) and a hard-edged disc (the sun).
 */
public final class SkyTextures {

    public static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath(StevesSpace.MODID, "sky/glow");
    public static final ResourceLocation DISC = ResourceLocation.fromNamespaceAndPath(StevesSpace.MODID, "sky/disc");
    /** A glowing shell of gas seen from outside: filled, brighter towards its rim, with a soft edge. */
    public static final ResourceLocation SHELL = ResourceLocation.fromNamespaceAndPath(StevesSpace.MODID, "sky/shell");

    private static boolean created;

    private SkyTextures() {
    }

    public static void ensureCreated() {
        if (created) {
            return;
        }
        created = true;
        register(GLOW, radial(64, r -> {
            // Bright centre with a long gentle falloff, reaching zero at the edge.
            double core = Math.exp(-r * r * 30);
            double halo = Math.pow(Math.max(0, 1 - r), 3) * 0.55;
            return Math.min(1, core + halo);
        }));
        register(DISC, radial(64, r -> {
            // Solid disc with one pixel of anti-aliasing and slight limb darkening.
            double edge = Math.max(0, Math.min(1, (0.97 - r) * 32));
            return edge * (0.82 + 0.18 * Math.sqrt(Math.max(0, 1 - r * r)));
        }));
        register(SHELL, shell(512));
    }

    /**
     * A ball of glowing debris gas, as photographed from Hawaii: brighter towards its rim, with
     * a soft, slightly uneven edge, and faintly mottled glow inside (no hard outline, nothing flat).
     */
    private static NativeImage shell(int size) {
        NativeImage image = new NativeImage(size, size, false);
        java.util.Random random = new java.util.Random(1962);
        double[] edgeWobble = new double[12];
        double[] edgePhase = new double[12];
        for (int k = 0; k < edgeWobble.length; k++) {
            edgeWobble[k] = random.nextGaussian() * 0.012 / (k + 1);
            edgePhase[k] = random.nextDouble() * Math.PI * 2;
        }
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double dx = (x + 0.5) / size * 2 - 1;
                double dy = (y + 0.5) / size * 2 - 1;
                double r = Math.sqrt(dx * dx + dy * dy);
                double angle = Math.atan2(dy, dx);
                double rim = 0.8;
                for (int k = 0; k < edgeWobble.length; k++) {
                    rim += edgeWobble[k] * Math.sin((k + 2) * angle + edgePhase[k]);
                }
                double u = r / rim;
                // Path length through a shell of thickness 0.15: longest just inside the rim.
                double outer = Math.sqrt(Math.max(0, 1 - u * u));
                double inner = Math.sqrt(Math.max(0, 0.72 - u * u));
                double path = (outer - inner) * 2.2;
                // Beyond the rim it fades softly (no outline).
                double fade = u <= 1 ? 1 : Math.exp(-Math.pow((u - 1) / 0.09, 2));
                double glow = u <= 1 ? 0.22 + path : 0.22 * fade;
                // Faint unevenness in the glow (soft, nothing regular).
                double streak = 0.9 + 0.12 * fbm(dx * 3 + 0.3 * Math.cos(angle), dy * 3 + 0.3 * Math.sin(angle), 7);
                double clump = 0.9 + 0.18 * fbm(dx * 5, dy * 5, 11);
                double a = Math.max(0, Math.min(1, glow * streak * clump * fade));
                int alpha = (int) Math.round(a * 255);
                image.setPixelRGBA(x, y, (alpha << 24) | 0x00FFFFFF);
            }
        }
        return image;
    }

    /** Smooth noise in about -1..1 (a few octaves), seeded. */
    private static double fbm(double x, double y, int seed) {
        double total = 0;
        double amp = 0.6;
        for (int o = 0; o < 4; o++) {
            total += amp * valueNoise(x, y, seed + o * 31);
            x *= 2.03;
            y *= 2.03;
            amp *= 0.5;
        }
        return total;
    }

    private static double valueNoise(double x, double y, int seed) {
        int xi = (int) Math.floor(x);
        int yi = (int) Math.floor(y);
        double fx = x - xi;
        double fy = y - yi;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        double a = hash(xi, yi, seed);
        double b = hash(xi + 1, yi, seed);
        double c = hash(xi, yi + 1, seed);
        double d = hash(xi + 1, yi + 1, seed);
        return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fy;
    }

    private static double hash(int x, int y, int seed) {
        long h = x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ seed * 0x165667B19E3779F9L;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 11) / (double) (1L << 53) * 2 - 1;
    }

    private interface Profile {
        double alpha(double radius);
    }

    /** A white image whose alpha follows {@code profile} of the distance from the centre (0..1). */
    private static NativeImage radial(int size, Profile profile) {
        NativeImage image = new NativeImage(size, size, false);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double dx = (x + 0.5) / size * 2 - 1;
                double dy = (y + 0.5) / size * 2 - 1;
                double r = Math.sqrt(dx * dx + dy * dy);
                int a = (int) Math.round(Math.max(0, Math.min(1, profile.alpha(r))) * 255);
                image.setPixelRGBA(x, y, (a << 24) | 0x00FFFFFF);
            }
        }
        return image;
    }

    private static void register(ResourceLocation location, NativeImage image) {
        if (com.thepearlking.stevespace.debug.ClientTest.enabled()) {
            try {
                image.writeToFile(new java.io.File(Minecraft.getInstance().gameDirectory,
                        "debug_" + location.getPath().replace('/', '_') + ".png"));
            } catch (java.io.IOException e) {
                StevesSpace.LOGGER.warn("Could not dump {}", location, e);
            }
        }
        DynamicTexture texture = new DynamicTexture(image);
        texture.setFilter(true, false);
        Minecraft.getInstance().getTextureManager().register(location, texture);
    }
}
