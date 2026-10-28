package com.thepearlking.stevespace.client.sky;

/**
 * A cheap, good-looking approximation of atmospheric scattering, evaluated per sky vertex.
 *
 * <p>It is not a physical simulation; it reproduces what people expect to see: a deep blue
 * zenith fading to a pale horizon, a bright halo around the sun, warm sunsets that hug the
 * horizon on the sun's side, a blue twilight afterwards, and black space once the air thins out
 * high above the ground.
 */
public final class SkyColors {

    private static final float[] NIGHT = {0.006f, 0.009f, 0.02f};

    private SkyColors() {
    }

    /**
     * Colour of the sky in direction {@code (vx, vy, vz)} (unit, local axes, +Y up) with the sun
     * in direction {@code (sx, sy, sz)}.
     *
     * @param density    thickness of the air above the observer: 1 at the surface, 0 in space
     * @param horizonDip sine of how far the true horizon lies below level (grows with altitude)
     * @param out        receives r, g, b
     */
    public static void sky(SkyPalette palette, float vx, float vy, float vz, float sx, float sy, float sz,
                           float density, float horizonDip, float[] out) {
        float day = smoothstep(-0.14f, 0.2f, sy);
        // Elevation measured from the true horizon: 0 at the horizon, 1 at the zenith.
        float above = (vy + horizonDip) / (1 + horizonDip);
        // Below the horizon is the ground or the planet; keep the horizon haze colour there.
        float horizonness = (float) Math.pow(1 - Math.max(above, 0) * 0.98f, 3);

        float r = lerp(horizonness, palette.zenith()[0], palette.horizon()[0]) * day;
        float g = lerp(horizonness, palette.zenith()[1], palette.horizon()[1]) * day;
        float b = lerp(horizonness, palette.zenith()[2], palette.horizon()[2]) * day;

        // Sunset and sunrise: strongest along the horizon toward the sun, while the sun is low.
        float sunLow = 1 - smoothstep(0.0f, 0.32f, Math.abs(sy + 0.04f));
        float horizontalSun = (float) Math.sqrt(sx * sx + sz * sz);
        float horizontalView = (float) Math.sqrt(vx * vx + vz * vz);
        float towardSunAzimuth = horizontalSun < 1e-4f || horizontalView < 1e-4f ? 0
                : (vx * sx + vz * sz) / (horizontalSun * horizontalView);
        float azimuthWeight = (float) Math.pow((towardSunAzimuth + 1) / 2, 3);
        float sunset = sunLow * horizonness * (0.25f + 0.75f * azimuthWeight) * smoothstep(-0.22f, -0.02f, sy);
        sunset = Math.min(1, sunset * 1.2f);
        r = lerp(sunset * 0.85f, r, palette.sunset()[0]);
        g = lerp(sunset * 0.85f, g, palette.sunset()[1]);
        b = lerp(sunset * 0.85f, b, palette.sunset()[2]);

        // Wide soft halo around the sun (thicker air = wider). The tight glare is drawn as a
        // billboard by the renderer: the sky mesh is too coarse for a sharp highlight.
        float cosToSun = vx * sx + vy * sy + vz * sz;
        float wide = (float) Math.pow(Math.max(0, cosToSun), 7) * (0.22f + palette.haze() * 0.3f);
        float glow = wide * smoothstep(-0.1f, 0.05f, sy);
        r += palette.sunGlow()[0] * glow;
        g += palette.sunGlow()[1] * glow;
        b += palette.sunGlow()[2] * glow;

        // Blue hour: after sunset the sky overhead stays deep blue for a while.
        float twilight = smoothstep(-0.24f, -0.06f, sy) * (1 - day) * (1 - horizonness * 0.5f);
        r += 0.03f * twilight;
        g += 0.06f * twilight;
        b += 0.16f * twilight;

        // Thin air: the sky fades to black. (The glowing rim of the atmosphere seen from orbit is
        // drawn with the planet, where it belongs.)
        r *= density;
        g *= density;
        b *= density;

        float nightWeight = density;
        out[0] = Math.max(r, NIGHT[0] * nightWeight);
        out[1] = Math.max(g, NIGHT[1] * nightWeight);
        out[2] = Math.max(b, NIGHT[2] * nightWeight);
    }

    /** How bright the daytime sky is (0 night or space, 1 full day); dims stars and planets. */
    public static float skyBrightness(float sunY, float density) {
        return smoothstep(-0.14f, 0.2f, sunY) * density;
    }

    static float smoothstep(float edge0, float edge1, float v) {
        float t = Math.max(0, Math.min(1, (v - edge0) / (edge1 - edge0)));
        return t * t * (3 - 2 * t);
    }

    private static float lerp(float t, float a, float b) {
        return a + t * (b - a);
    }
}
