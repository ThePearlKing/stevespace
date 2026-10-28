package com.thepearlking.stevespace.client.sky;

import com.thepearlking.stevespace.universe.CelestialBody;

/**
 * What the air of a world does to the view at a given height: how far you can see, the colour of
 * the murk, whether you are inside a cloud deck, how much of the Sun gets through, and the tint of
 * daylight on the ground. Worlds without anything special return null (normal sky and fog).
 *
 * <p>Venus: a thick yellow haze at the surface, a solid cloud deck high above it that you fly
 * through blind, and clear sky with bright cloud tops above. Titan: an orange smog that thins out
 * slowly with height.
 */
public final class BodyAir {

    /**
     * @param near         fog start (blocks)
     * @param far          fog end (blocks)
     * @param cloud        0 outside a cloud deck, 1 deep inside it (the sky is all cloud)
     * @param cloudColor   the colour of the cloud deck (RGB 0..1)
     * @param sun          how much of the Sun's disc and the stars show (0 hidden .. 1 clear)
     * @param groundHidden true when the ground can't be seen from here at all (in or above the deck)
     */
    public record View(float near, float far, float cloud, float[] cloudColor, float sun, boolean groundHidden) {
    }

    /** A world's cloud deck: base and top heights above sea level (blocks). */
    public record Deck(double base, double top, int color) {
    }

    private BodyAir() {
    }

    /** The cloud deck of a world, or null. */
    public static Deck deck(CelestialBody body) {
        var giant = com.thepearlking.stevespace.universe.GiantAir.of(body);
        if (giant != null) {
            return new Deck(giant.top().base(), giant.top().top(), giant.top().color());
        }
        return switch (body.id()) {
            case "venus" -> new Deck(900, 1500, 0xE6D49C);
            default -> null;
        };
    }

    /** The view at {@code altitude} blocks above sea level on {@code body}, or null for a normal sky. */
    public static View at(CelestialBody body, double altitude) {
        var giant = com.thepearlking.stevespace.universe.GiantAir.of(body);
        if (giant != null) {
            return giant(giant, altitude);
        }
        switch (body.id()) {
            case "venus" -> {
                Deck d = deck(body);
                float[] color = rgb(d.color());
                if (altitude < d.base()) {
                    // Under the deck: dim yellow murk, thicker toward the ground.
                    float far = (float) (130 + 170 * Math.max(0, altitude - 60) / (d.base() - 60));
                    float glow = (float) smoothstep(d.base() - 250, d.base(), altitude);
                    // Near the ground the murk is a warm orange haze; it pales toward the clouds.
                    float[] murk = {0.8f + 0.1f * glow, 0.6f + 0.2f * glow, 0.36f + 0.24f * glow};
                    return new View(0, far, 0.85f, murk, 0, false);
                }
                if (altitude < d.top()) {
                    // Inside the deck: blind. Brighter toward the top, where the Sun shows through.
                    double depth = (altitude - d.base()) / (d.top() - d.base());
                    float shade = (float) (0.62 + 0.38 * depth);
                    float[] inside = {color[0] * shade, color[1] * shade, color[2] * shade};
                    return new View(0, 9, 1, inside, (float) smoothstep(0.75, 1.0, depth) * 0.5f, true);
                }
                // Above the deck: clear, with the cloud tops spread out below.
                return new View(2000, 6000, 0, color, 1, true);
            }
            case "mars" -> {
                // A dust storm: the far distance goes first, then the Sun dims to a smudge.
                double storm = com.thepearlking.stevespace.universe.Weather.marsStorm(com.thepearlking.stevespace.client.ClientUniverse.time());
                if (storm <= 0.01 || altitude > 1400) {
                    return null;
                }
                float far = (float) (600 - storm * 560);
                float[] dust = {0.72f, 0.48f, 0.3f};
                return new View(0, far, (float) (storm * 0.9), dust, (float) Math.max(0, 1 - storm * 1.2), false);
            }
            case "titan" -> {
                float far = (float) (240 + Math.max(0, altitude - 60) * 0.5);
                float sun = (float) (0.15 + 0.85 * smoothstep(900, 2000, altitude));
                return new View(far * 0.08f, far, 0, rgb(0xB7843E), sun, false);
            }
            default -> {
                return null;
            }
        }
    }

    /**
     * Inside a giant: haze thickening above the top deck; blind inside each deck; between decks,
     * clear murk with a ceiling and a floor of cloud (GiantClouds draws those); below the last,
     * the dark, thick, hot depths, glowing red toward the crush depth.
     */
    private static View giant(com.thepearlking.stevespace.universe.GiantAir.Layers layers, double altitude) {
        var decks = layers.decks();
        float[] air = giantAir(layers, altitude);
        if (altitude >= decks[0].top()) {
            float k = (float) smoothstep(5000, decks[0].top(), altitude);
            return new View(2000, 9000, 0.6f * k, air, 1 - 0.35f * k, true);
        }
        for (int i = 0; i < decks.length; i++) {
            var d = decks[i];
            if (altitude >= d.base() && altitude <= d.top()) {
                double depth = (altitude - d.base()) / (d.top() - d.base());
                float sun = i == 0 ? (float) smoothstep(0.7, 1.0, depth) * 0.4f : 0;
                return new View(0, (float) (10 + 30 * Math.min(depth, 1 - depth) * 2), 1, air, sun, true);
            }
        }
        if (altitude < decks[decks.length - 1].base()) {
            double deep = com.thepearlking.stevespace.universe.GiantAir.stress(altitude);
            return new View(0, (float) (700 - 560 * deep), 1, air, 0, true);
        }
        return new View(200, 4000, 1, air, 0, true);
    }

    /** The colour of the air at this height inside a giant (before day and night). */
    public static float[] giantAir(com.thepearlking.stevespace.universe.GiantAir.Layers layers, double altitude) {
        var decks = layers.decks();
        double light = giantLight(layers, altitude);
        int color;
        if (altitude >= decks[0].top()) {
            color = layers.haze();
        } else {
            color = decks[decks.length - 1].below();
            for (int i = 0; i < decks.length; i++) {
                if (altitude >= decks[i].base()) {
                    color = altitude <= decks[i].top() ? decks[i].color() : decks[i - 1 < 0 ? 0 : i - 1].below();
                    break;
                }
            }
            if (altitude < decks[decks.length - 1].base()) {
                // Down in the deep: darker, then the heat coming up from below glows red.
                double deep = com.thepearlking.stevespace.universe.GiantAir.stress(altitude);
                float[] a = rgb(mixColor(decks[decks.length - 1].below(), layers.deep(), smoothstep(decks[decks.length - 1].base(), -1500, altitude)));
                float[] glow = {0.75f, 0.2f, 0.06f};
                for (int i = 0; i < 3; i++) {
                    a[i] = (float) (a[i] + (glow[i] - a[i]) * deep * 0.8);
                }
                return a;
            }
        }
        float[] c = rgb(color);
        for (int i = 0; i < 3; i++) {
            c[i] *= (float) light;
        }
        return c;
    }

    /** How much daylight gets down to this height inside a giant (1 above the clouds). */
    public static double giantLight(com.thepearlking.stevespace.universe.GiantAir.Layers layers, double altitude) {
        var decks = layers.decks();
        double light = 1;
        for (var d : decks) {
            // Each deck takes most of what reaches it.
            light *= 1 - 0.28 * smoothstep(d.top(), d.base(), altitude);
        }
        return light;
    }

    private static int mixColor(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    /**
     * Daylight colour on the ground (multiplies the sky part of the light map), or null for white.
     * Venus: dim and orange through the clouds. Titan: dimmer and browner still.
     */
    public static float[] daylight(CelestialBody body) {
        return switch (body.id()) {
            case "venus" -> new float[]{0.95f, 0.8f, 0.58f};
            case "titan" -> new float[]{0.66f, 0.5f, 0.31f};
            case "mars" -> new float[]{1.0f, 0.93f, 0.84f};
            default -> null;
        };
    }

    /** Cloud and murk are lit by the Sun above them: bright by day, dark at night. */
    public static float[] lit(float[] color, double sunHeight) {
        double t = smoothstep(-0.15, 0.25, sunHeight);
        float day = (float) (0.25 + 0.75 * t);
        return new float[]{color[0] * day, color[1] * day, color[2] * day};
    }

    private static float[] rgb(int c) {
        return new float[]{((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f};
    }

    private static double smoothstep(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }
}
