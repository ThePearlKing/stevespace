package com.thepearlking.stevespace.world.planet;

/** Small shaping functions used all over the terrain code. */
public final class Shaping {

    private Shaping() {
    }

    public static double clamp(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }

    public static double clamp01(double v) {
        return clamp(v, 0, 1);
    }

    public static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    /** 0 below {@code edge0}, 1 above {@code edge1}, smooth S-curve in between. */
    public static double smoothstep(double edge0, double edge1, double v) {
        double t = clamp01((v - edge0) / (edge1 - edge0));
        return t * t * (3 - 2 * t);
    }

    /** Linear 0..1 ramp between two edges. */
    public static double ramp(double edge0, double edge1, double v) {
        return clamp01((v - edge0) / (edge1 - edge0));
    }

    /** Mixes two 0xRRGGBB colours. */
    public static int mixColor(int a, int b, double t) {
        t = clamp01(t);
        int r = (int) Math.round(lerp(t, (a >> 16) & 255, (b >> 16) & 255));
        int g = (int) Math.round(lerp(t, (a >> 8) & 255, (b >> 8) & 255));
        int bl = (int) Math.round(lerp(t, a & 255, b & 255));
        return (r << 16) | (g << 8) | bl;
    }

    /** Multiplies a colour's brightness. */
    public static int shade(int color, double factor) {
        int r = (int) clamp(((color >> 16) & 255) * factor, 0, 255);
        int g = (int) clamp(((color >> 8) & 255) * factor, 0, 255);
        int b = (int) clamp((color & 255) * factor, 0, 255);
        return (r << 16) | (g << 8) | b;
    }
}
