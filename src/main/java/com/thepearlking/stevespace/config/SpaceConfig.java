package com.thepearlking.stevespace.config;

import com.thepearlking.stevespace.world.planet.earth.EarthSettings;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Gameplay settings (common config: on a dedicated server the server's file applies).
 * The "new worlds" section only affects worlds created afterwards.
 */
public final class SpaceConfig {

    public enum WarpPermission { EVERYONE, OPERATORS, SINGLEPLAYER_ONLY }

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.DoubleValue EARTH_DAY_MINUTES;
    public static final ForgeConfigSpec.EnumValue<WarpPermission> WARP_PERMISSION;
    public static final ForgeConfigSpec.BooleanValue CREATIVE_INFINITE_FUEL;
    public static final ForgeConfigSpec.IntValue MAX_VESSEL_BLOCKS;
    public static final ForgeConfigSpec.BooleanValue SHIP_POWER;
    public static final ForgeConfigSpec.BooleanValue NUKES;
    public static final ForgeConfigSpec.BooleanValue NUKE_DESTRUCTION;
    public static final ForgeConfigSpec.BooleanValue NUKE_EMP;
    public static final ForgeConfigSpec.BooleanValue ACHERON;

    public static final ForgeConfigSpec.DoubleValue GRAVITY_SCALE;
    public static final ForgeConfigSpec.DoubleValue THRUST_SCALE;
    public static final ForgeConfigSpec.DoubleValue FUEL_EFFICIENCY;
    public static final ForgeConfigSpec.DoubleValue ATMOSPHERIC_DRAG;
    public static final ForgeConfigSpec.DoubleValue TURN_RATE;
    public static final ForgeConfigSpec.DoubleValue CRASH_SPEED;

    public static final ForgeConfigSpec.DoubleValue CONTINENT_SIZE;
    public static final ForgeConfigSpec.DoubleValue LAND_FRACTION;
    public static final ForgeConfigSpec.DoubleValue ICE_CAP_LATITUDE;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("time");
        EARTH_DAY_MINUTES = builder
                .comment("Length of a day on Earth in minutes. Other planets keep their real ratio to it.")
                .defineInRange("earthDayMinutes", 20.0, 1.0, 1440.0);
        WARP_PERMISSION = builder
                .comment("Who may use time warp. Warp affects everyone on the server.")
                .defineEnum("timeWarpPermission", WarpPermission.EVERYONE);
        builder.pop();

        builder.push("rockets");
        CREATIVE_INFINITE_FUEL = builder
                .comment("Vessels flown in creative mode never run out of fuel.")
                .define("creativeInfiniteFuel", true);
        MAX_VESSEL_BLOCKS = builder
                .comment("Largest number of blocks a vessel can have.")
                .defineInRange("maxVesselBlocks", 6000, 50, 20000);
        SHIP_POWER = builder
                .comment("Power reaches everything that's part of the same ship or build, any block, any side or corner.",
                        "Off: it only runs through this mod's blocks touching face to face.")
                .define("shipPower", true);
        builder.pop();

        builder.push("nukes");
        NUKES = builder
                .comment("Nuclear devices can be set off. Off: they do nothing.")
                .define("nukes", true);
        NUKE_DESTRUCTION = builder
                .comment("A nuclear blast wrecks the ground around it (crater, blast, fire, fallout).",
                        "Off: it destroys only the spacecraft it was part of; everything else is left standing.")
                .define("destruction", false);
        NUKE_EMP = builder
                .comment("A burst above the air sends out an electromagnetic pulse that drains machines and batteries",
                        "on the side of the planet below it.")
                .define("emp", true);
        builder.pop();

        builder.push("events");
        ACHERON = builder
                .comment("Enable Acheron.")
                .define("acheron", true);
        builder.pop();

        builder.push("physics");
        GRAVITY_SCALE = builder
                .comment("Gravity on vessels, relative to real. Orbits use the same value.")
                .defineInRange("gravityScale", 1.0, 0.1, 2.0);
        THRUST_SCALE = builder
                .comment("Thrust of all engines.")
                .defineInRange("thrustScale", 1.0, 0.25, 5.0);
        FUEL_EFFICIENCY = builder
                .comment("How far a tank of propellant gets you. 2 = twice the delta-v.")
                .defineInRange("fuelEfficiency", 1.0, 0.25, 5.0);
        ATMOSPHERIC_DRAG = builder
                .comment("Air resistance. 0 = none.")
                .defineInRange("atmosphericDrag", 1.0, 0.0, 3.0);
        TURN_RATE = builder
                .comment("How fast vessels turn, in degrees per second.")
                .defineInRange("turnRate", 30.0, 5.0, 120.0);
        CRASH_SPEED = builder
                .comment("Touching down faster than this (blocks per second) destroys the vessel.")
                .defineInRange("crashSpeed", 12.0, 3.0, 100.0);
        builder.pop();

        builder.comment("Earth layout for new worlds. Existing worlds keep their own values (<world>/stevespace_earth.json).")
                .push("newWorlds");
        CONTINENT_SIZE = builder
                .comment("Continent size. 1 = Earth-like, 2 = twice as large.")
                .defineInRange("continentSize", EarthSettings.DEFAULT.continentSize(), 0.25, 4.0);
        LAND_FRACTION = builder
                .comment("Fraction of the surface that is land.")
                .defineInRange("landFraction", EarthSettings.DEFAULT.landFraction(), 0.05, 0.9);
        ICE_CAP_LATITUDE = builder
                .comment("Latitude (degrees) where frozen biomes begin.")
                .defineInRange("iceCapLatitude", EarthSettings.DEFAULT.iceCapLatitude(), 45.0, 85.0);
        builder.pop();

        SPEC = builder.build();
    }

    private SpaceConfig() {
    }

    /** Earth day length in game seconds, safe to call before the config has loaded. */
    public static double earthDaySeconds() {
        try {
            return EARTH_DAY_MINUTES.get() * 60.0;
        } catch (IllegalStateException notLoadedYet) {
            return 1200.0;
        }
    }

    /** Earth layout for a world being created now. */
    public static EarthSettings newWorldEarthSettings() {
        return new EarthSettings(CONTINENT_SIZE.get(), LAND_FRACTION.get(), ICE_CAP_LATITUDE.get());
    }
}
