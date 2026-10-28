package com.thepearlking.stevespace.client.sky;

/**
 * The colours of a planet's sky. Each body with an atmosphere gets its own: Earth's blue sky
 * with orange sunsets, Mars's butterscotch sky with blue sunsets, Titan's orange haze...
 * Colours are linear-ish RGB in 0..1.
 */
public record SkyPalette(float[] zenith, float[] horizon, float[] sunset, float[] sunGlow, float haze) {

    public static final SkyPalette EARTH = new SkyPalette(
            rgb(0x3C6FC4), rgb(0xA9C4E2), rgb(0xF07A36), rgb(0xFFEBCB), 0.25f);
    public static final SkyPalette MARS = new SkyPalette(
            rgb(0xA77A55), rgb(0xD4AE84), rgb(0x6F8FC4), rgb(0xF2E6D2), 0.55f);
    public static final SkyPalette VENUS = new SkyPalette(
            rgb(0xB89A5E), rgb(0xD9C08A), rgb(0xC77A3A), rgb(0xFFF0C8), 0.9f);
    public static final SkyPalette TITAN = new SkyPalette(
            rgb(0x8A5A22), rgb(0xB7843E), rgb(0x7A4A1C), rgb(0xE8C890), 0.85f);

    /** Picks the palette for a body id, or null when the body has no visible atmosphere. */
    public static SkyPalette forBody(String bodyId) {
        return switch (bodyId) {
            case "earth" -> EARTH;
            case "mars" -> MARS;
            case "venus" -> VENUS;
            case "titan" -> TITAN;
            default -> null;
        };
    }

    private static float[] rgb(int color) {
        return new float[]{((color >> 16) & 255) / 255f, ((color >> 8) & 255) / 255f, (color & 255) / 255f};
    }
}
