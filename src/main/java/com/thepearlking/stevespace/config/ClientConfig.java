package com.thepearlking.stevespace.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Visual settings, per player. */
public final class ClientConfig {

    public enum TextureQuality {
        LOW(1024), MEDIUM(2048), HIGH(4096);

        public final int width;

        TextureQuality(int width) {
            this.width = width;
        }
    }

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue SUN_MOON_SCALE;
    public static final ForgeConfigSpec.DoubleValue STAR_BRIGHTNESS;
    public static final ForgeConfigSpec.BooleanValue PLANET_VIEW;
    public static final ForgeConfigSpec.EnumValue<TextureQuality> PLANET_TEXTURE_QUALITY;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("sky");
        SUN_MOON_SCALE = builder
                .comment("Apparent size of the Sun and Moon. 1 = true size.")
                .defineInRange("sunMoonScale", 3.0, 1.0, 10.0);
        STAR_BRIGHTNESS = builder
                .comment("Brightness of the stars at night.")
                .defineInRange("starBrightness", 1.0, 0.0, 2.0);
        builder.pop();

        builder.push("planet");
        PLANET_VIEW = builder
                .comment("Draw the planet beyond render distance when high up.")
                .define("planetView", true);
        PLANET_TEXTURE_QUALITY = builder
                .comment("Detail of planet surface maps seen from altitude and from space.")
                .defineEnum("textureQuality", TextureQuality.MEDIUM);
        builder.pop();
        SPEC = builder.build();
    }

    private ClientConfig() {
    }

    /** Value or default; safe before the config file has loaded. */
    public static double sunMoonScale() {
        try {
            return SUN_MOON_SCALE.get();
        } catch (IllegalStateException e) {
            return 3.0;
        }
    }

    public static double starBrightness() {
        try {
            return STAR_BRIGHTNESS.get();
        } catch (IllegalStateException e) {
            return 1.0;
        }
    }

    public static boolean planetView() {
        try {
            return PLANET_VIEW.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    public static TextureQuality textureQuality() {
        try {
            return PLANET_TEXTURE_QUALITY.get();
        } catch (IllegalStateException e) {
            return TextureQuality.MEDIUM;
        }
    }
}
