package com.thepearlking.stevespace.universe;

import com.thepearlking.stevespace.world.planet.noise.SphereNoise;

/**
 * Magnetic fields and radiation, from real measurements (scaled to the mod's distances only
 * through each body's radius). A planet with a dynamo has a dipole field aligned with its spin
 * axis; Mars and the Moon have only patches of magnetised crust; the rest only see the field of
 * the planet they orbit, or the faint field of the solar wind.
 */
public final class Magnetosphere {

    /** A field reading: strength (nanotesla) and dip below the horizon (degrees, + = pointing down). */
    public record Field(double nanotesla, double inclination) {
    }

    private static final SphereNoise MARS_CRUST = new SphereNoise(0x4D61726D61676EL, 9, 4, 0.55);
    private static final SphereNoise MOON_CRUST = new SphereNoise(0x4D6F6F6E6D61676EL, 12, 3, 0.5);

    private Magnetosphere() {
    }

    /** Equatorial surface field of each body's own dipole, nanotesla (0 = none). */
    public static double dipole(CelestialBody body) {
        return switch (body.id()) {
            case "earth" -> 30_000;
            case "mercury" -> 300;
            case "jupiter" -> 428_000;
            case "saturn" -> 21_000;
            case "uranus" -> 23_000;
            case "neptune" -> 14_000;
            case "ganymede" -> 719;
            default -> 0;
        };
    }

    /**
     * The field at {@code r} blocks from the centre of {@code body}, at {@code latitude}
     * (radians, relative to its spin axis), including the field of the planet it orbits.
     *
     * @param distanceFromParent where the body is from its parent (for the parent's field), blocks
     */
    public static Field at(SolarSystem system, CelestialBody body, double r, double latitude, double longitude,
                           double distanceFromParent) {
        double radial = 0;   // outward component
        double north = 0;    // horizontal, toward the north
        double b0 = dipole(body);
        if (b0 > 0) {
            double k = b0 * Math.pow(body.radius() / Math.max(r, body.radius()), 3);
            radial += -2 * k * Math.sin(latitude);
            north += k * Math.cos(latitude);
        }
        // Magnetised crust: strong right at the ground, gone a little way up.
        if (body.id().equals("mars") || body.id().equals("moon")) {
            double lat = latitude;
            double x = Math.cos(lat) * Math.cos(longitude);
            double y = Math.sin(lat);
            double z = Math.cos(lat) * Math.sin(longitude);
            boolean mars = body.id().equals("mars");
            double n = (mars ? MARS_CRUST : MOON_CRUST).sample(x, y, z);
            // Mars' crustal fields are in the old southern highlands.
            double where = mars ? Math.max(0, Math.min(1, -y * 2 + 0.4)) : 1;
            double peak = mars ? 1500 : 300;
            double fall = Math.pow(body.radius() / Math.max(r, body.radius()), mars ? 6 : 8);
            double crust = Math.max(0, n) * 2.2 * peak * where * fall;
            radial += crust * Math.signum(n == 0 ? 1 : Math.sin(n * 40));
            north += crust * 0.4;
        }
        // The parent planet's field reaching out to a moon.
        CelestialBody parent = body.parent();
        if (parent != null && dipole(parent) > 0 && distanceFromParent > 0) {
            double k = dipole(parent) * Math.pow(parent.radius() / distanceFromParent, 3);
            north += k; // near the planet's equator, roughly along its axis
        }
        // The solar wind's own field, about 5 nT at Earth's distance.
        double sunDistance = Math.max(1, sunDistance(system, body));
        north += 5 * SolarSystem.AU / sunDistance;
        double total = Math.sqrt(radial * radial + north * north);
        double inclination = Math.toDegrees(Math.atan2(-radial, Math.abs(north)));
        return new Field(total, inclination);
    }

    private static double sunDistance(SolarSystem system, CelestialBody body) {
        CelestialBody planet = body;
        while (planet.parent() != null && !planet.parent().id().equals(system.sun().id())) {
            planet = planet.parent();
        }
        return planet.parent() == null ? SolarSystem.AU : planet.orbit().semiMajorAxis();
    }

    /**
     * Radiation dose rate (microsieverts per hour) at {@code altitude} blocks above the surface of
     * {@code body} (or out in its orbit), unshielded.
     */
    public static double dose(SolarSystem system, CelestialBody body, double altitude, double distanceFromParent) {
        double surface = switch (body.id()) {
            case "earth" -> 0.1;
            case "venus" -> 0.1;
            case "titan" -> 0.4;
            case "moon" -> 57;
            case "mars" -> 28;
            case "mercury" -> 60;
            case "io" -> 1_500_000;
            case "europa" -> 225_000;
            case "ganymede" -> 3_300;
            case "callisto" -> 4;
            case "enceladus" -> 120;
            case "triton", "pluto", "charon", "makemake", "eris", "haumea" -> 25;
            default -> 75;
        };
        if (altitude > 0 && body.hasAtmosphere() && (body.id().equals("earth") || body.id().equals("venus") || body.id().equals("titan"))) {
            // Thick air shields the ground; high up the shield is gone.
            double air = Math.min(1, altitude / VesselTransferAltitude.of(body));
            air *= air; // most of the shielding is in the low, dense air
            surface = surface * (1 - air) + 20 * air;
        }
        double dose = surface;
        // Radiation belts round planets with strong fields.
        double r = body.radius() + Math.max(0, altitude);
        if (body.id().equals("earth")) {
            double x = r / body.radius();
            // The inner belt starts about 1,000 km up (1.15 radii); nothing of it reaches the ground.
            dose += (12_000 * Math.exp(-Math.pow((x - 1.6) / 0.35, 2)) + 4_000 * Math.exp(-Math.pow((x - 4.5) / 1.2, 2)))
                    * beltEdge(x, 1.12, 1.25);
        } else if (body.id().equals("jupiter")) {
            double x = r / body.radius();
            dose += 3_000_000 * Math.exp(-Math.pow((x - 6) / 3, 2)) * beltEdge(x, 1.0, 1.5);
        } else if (body.id().equals("saturn")) {
            double x = r / body.radius();
            dose += 2_000 * Math.exp(-Math.pow((x - 4) / 2, 2)) * beltEdge(x, 1.0, 2.3);
        }
        return dose;
    }

    /** 0 below {@code from} radii, rising smoothly to 1 at {@code to}: belts have a floor. */
    private static double beltEdge(double x, double from, double to) {
        double t = Math.max(0, Math.min(1, (x - from) / (to - from)));
        return t * t * (3 - 2 * t);
    }

    /** Height above which a body's air no longer shields (its atmosphere's height). */
    private static final class VesselTransferAltitude {
        static double of(CelestialBody body) {
            return body.hasAtmosphere() ? body.atmosphere().height() : 1;
        }
    }

    /** How far a planet's field holds off the solar wind, in its own radii (0 = no magnetosphere). */
    public static double magnetopause(CelestialBody body) {
        return switch (body.id()) {
            case "earth" -> 10;
            case "mercury" -> 1.5;
            case "jupiter" -> 75;
            case "saturn" -> 22;
            case "uranus" -> 18;
            case "neptune" -> 25;
            case "ganymede" -> 2;
            default -> 0;
        };
    }

    /**
     * Whose magnetosphere a point is in: the body's own (if it has one and the point is inside),
     * else the planet it orbits (if the body sits inside that one), else null (the solar wind).
     */
    public static CelestialBody inside(SolarSystem system, CelestialBody body, double r, double t) {
        if (magnetopause(body) > 0 && r < magnetopause(body) * body.radius()) {
            return body;
        }
        CelestialBody parent = body.parent();
        if (parent != null && magnetopause(parent) > 0) {
            double d = system.stateRelativeToParent(body, t).position().length();
            if (d < magnetopause(parent) * parent.radius()) {
                return parent;
            }
        }
        return null;
    }

    private static final java.util.Set<String> OWN_SOUND = java.util.Set.of("sun", "mercury", "venus", "moon", "mars", "io",
            "europa", "ganymede", "callisto", "titan", "enceladus", "triton", "pluto", "charon", "acheron");

    private static double fade(double from, double to, double x) {
        double t = Math.max(0, Math.min(1, (x - from) / (to - from)));
        return t * t * (3 - 2 * t);
    }

    /**
     * What a plasma wave receiver hears here, as a blend (sound name to loudness, 0..1): the solar
     * wind underneath everything; a planet's magnetosphere fading in across its boundary (from a
     * quarter outside the magnetopause to a tenth inside); a world's own sound fading in from a
     * dozen radii out (the Sun's from 60). Nothing switches abruptly.
     */
    public static java.util.Map<String, Double> soundMix(SolarSystem system, CelestialBody body, double r, double t) {
        java.util.Map<String, Double> mix = new java.util.LinkedHashMap<>();
        double own = 0;
        if (OWN_SOUND.contains(body.id())) {
            double radii = r / body.radius();
            own = body.id().equals("sun") ? fade(60, 15, radii) : fade(12, 3, radii);
        }
        // The planet whose field this is (the body's own, or the planet it circles).
        double field = 0;
        String planet = null;
        if (magnetopause(body) > 0 && !OWN_SOUND.contains(body.id())) {
            planet = body.id();
            field = fade(1.25, 0.9, r / (magnetopause(body) * body.radius()));
        } else if (body.parent() != null && magnetopause(body.parent()) > 0) {
            planet = body.parent().id();
            double d = system.stateRelativeToParent(body, t).position().length();
            field = fade(1.25, 0.9, d / (magnetopause(body.parent()) * body.parent().radius()));
        }
        if (own > 0) {
            mix.put("plasma_" + body.id(), own);
        }
        if (planet != null && field > 0) {
            mix.put("plasma_" + planet, field * (1 - own));
        }
        double wind = (1 - own) * (1 - field);
        if (wind > 0.001) {
            mix.merge("plasma_solar_wind", wind, Double::sum);
        }
        // A solar storm passing: its own roar of plasma waves rises over the rest.
        double storm = SolarActivity.storm(system.absolutePosition(body, t), t) / 2;
        if (storm > 0.02) {
            double shielded = field > 0.5 ? 0.3 : 1;
            mix.put("plasma_sun", Math.min(1, mix.getOrDefault("plasma_sun", 0.0) + storm * shielded));
        }
        return mix;
    }

    /** The sound of this place on a plasma wave receiver: a sound name, or "" for silence. */
    public static String sound(CelestialBody body, CelestialBody planetInside) {
        // Each world has its own; moons and small worlds by where you are, planets by whose field you're in.
        switch (body.id()) {
            case "sun", "mercury", "venus", "moon", "mars", "io", "europa", "ganymede", "callisto", "titan", "enceladus",
                 "triton", "pluto", "charon", "acheron" -> {
                return "plasma_" + body.id();
            }
            default -> {
            }
        }
        String id = planetInside == null ? "" : planetInside.id();
        return switch (id) {
            case "earth", "jupiter", "saturn", "uranus", "neptune" -> "plasma_" + id;
            default -> "plasma_solar_wind";
        };
    }
}
