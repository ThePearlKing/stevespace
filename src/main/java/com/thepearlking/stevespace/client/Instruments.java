package com.thepearlking.stevespace.client;

import com.thepearlking.stevespace.client.space.ClientSpace;
import com.thepearlking.stevespace.universe.CelestialBody;
import com.thepearlking.stevespace.universe.DVec3;
import com.thepearlking.stevespace.universe.Kepler;
import com.thepearlking.stevespace.universe.Magnetosphere;
import com.thepearlking.stevespace.universe.SolarSystem;
import com.thepearlking.stevespace.universe.StateVector;
import com.thepearlking.stevespace.world.SpaceDimension;
import com.thepearlking.stevespace.world.planet.PlanetGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Where an instrument is, as the science models need it: which body, how far from its centre,
 * at what latitude and longitude. Works on a planet's surface and aboard a ship parked in orbit.
 */
public final class Instruments {

    /** A place: body, distance from its centre (blocks), latitude/longitude (radians), height above ground. */
    /** Where a reading is taken; {@code position} is relative to the body's centre in space (null on the ground). */
    public record Place(CelestialBody body, double r, double latitude, double longitude, double altitude, DVec3 position) {
    }

    private Instruments() {
    }

    public static Place at(Level level, BlockPos pos) {
        SolarSystem system = ClientUniverse.system();
        if (system == null) {
            return null;
        }
        if (SpaceDimension.isSpace(level)) {
            var cell = ClientSpace.cellOrbit();
            var flying = ClientSpace.orbitActive() ? ClientSpace.body() : null;
            CelestialBody body;
            StateVector now;
            if (cell != null) {
                body = system.body(cell.body());
                if (body == null) {
                    return null;
                }
                double mu = body.mu() * com.thepearlking.stevespace.config.SpaceConfig.GRAVITY_SCALE.get();
                double[] s = cell.state();
                now = Kepler.elementsFromState(new StateVector(new DVec3(s[0], s[1], s[2]), new DVec3(s[3], s[4], s[5])), mu, cell.time())
                        .stateAt(ClientUniverse.time(), mu);
            } else if (flying != null) {
                body = flying;
                now = ClientSpace.stateAt(ClientUniverse.time());
            } else if (ClientSpace.mapModeActive() && ClientSpace.body() != null) {
                // High over a world but not yet in orbit (climbing out, coming down): over the map.
                CelestialBody below = ClientSpace.body();
                var at = ClientSpace.mapPosition();
                PlanetGeometry geometry = new PlanetGeometry(below.radius());
                double altitude = at.y - 63;
                return new Place(below, below.radius() + Math.max(0, altitude), geometry.latitude(at.z), geometry.longitude(at.x), altitude, null);
            } else {
                return null;
            }
            DVec3 p = now.position();
            double r = p.length();
            double latitude = Math.asin(Math.max(-1, Math.min(1, p.normalize().dot(body.spinAxis()))));
            return new Place(body, r, latitude, Math.atan2(p.z(), p.x()), r - body.radius(), p);
        }
        CelestialBody body = ClientUniverse.currentBody();
        if (body == null) {
            return null;
        }
        PlanetGeometry geometry = new PlanetGeometry(body.radius());
        double altitude = pos.getY() - 63;
        return new Place(body, body.radius() + Math.max(0, altitude), geometry.latitude(pos.getZ()), geometry.longitude(pos.getX()), altitude, null);
    }

    /** Dose rate at a block (µSv/h, before shielding): the place's own, plus whatever nuclear bursts left there. */
    public static double doseAt(Level level, BlockPos pos, Place place) {
        double nuclear = 0;
        if (!com.thepearlking.stevespace.nuke.NuclearAftermath.CLIENT.isEmpty()) {
            // Where it is from the body's centre: enough for the magnetic shell (longitude doesn't matter to it).
            DVec3 axis = place.body().spinAxis();
            DVec3 across = axis.cross(Math.abs(axis.y()) < 0.9 ? new DVec3(0, 1, 0) : new DVec3(1, 0, 0)).normalize();
            DVec3 relative = axis.scale(Math.sin(place.latitude()) * place.r()).add(across.scale(Math.cos(place.latitude()) * place.r()));
            nuclear = com.thepearlking.stevespace.nuke.NuclearAftermath.extra(com.thepearlking.stevespace.nuke.NuclearAftermath.CLIENT,
                    level.dimension().location().toString(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, place.body(), relative,
                    ClientUniverse.time());
        }
        return dose(place) + nuclear;
    }

    /** The magnetic field reading at a place (shaken about while a solar storm is passing). */
    public static Magnetosphere.Field field(Place place) {
        SolarSystem system = ClientUniverse.system();
        double fromParent = place.body().parent() == null ? 0
                : system.stateRelativeToParent(place.body(), ClientUniverse.time()).position().length();
        Magnetosphere.Field calm = Magnetosphere.at(system, place.body(), place.r(), place.latitude(), place.longitude(), fromParent);
        double storm = storm(place);
        if (storm < 0.01) {
            return calm;
        }
        double t = ClientUniverse.time();
        double wobble = Math.sin(t * 0.7) * 0.6 + Math.sin(t * 2.3 + 1) * 0.3 + Math.sin(t * 5.1 + 2) * 0.1;
        return new Magnetosphere.Field(Math.max(0, calm.nanotesla() + storm * 60 * wobble), calm.inclination() + storm * 2 * wobble);
    }

    /** How hard a solar storm is hitting this place right now (0 calm .. 3 severe), before shielding. */
    public static double storm(Place place) {
        SolarSystem system = ClientUniverse.system();
        double t = ClientUniverse.time();
        return com.thepearlking.stevespace.universe.SolarActivity.storm(system.absolutePosition(place.body(), t), t);
    }

    /**
     * Dose rate at a place, microsieverts per hour, before any shielding. A solar storm adds its
     * particles: in full out in space or on an airless world, barely anything under a planet's
     * magnetic field and thick air.
     */
    public static double dose(Place place) {
        SolarSystem system = ClientUniverse.system();
        double fromParent = place.body().parent() == null ? 0
                : system.stateRelativeToParent(place.body(), ClientUniverse.time()).position().length();
        double calm = Magnetosphere.dose(system, place.body(), place.altitude(), fromParent);
        double storm = storm(place);
        if (storm < 0.01) {
            return calm;
        }
        double shield = 1;
        if (Magnetosphere.inside(system, place.body(), place.r(), ClientUniverse.time()) != null) {
            shield *= 0.05;
        }
        if (place.body().hasAtmosphere() && place.altitude() < place.body().atmosphere().height()) {
            shield *= Math.max(0.001, place.altitude() / place.body().atmosphere().height());
        }
        return calm + storm * 900 * shield;
    }

    /** The blend of sounds the plasma wave receiver hears at a place (name to loudness). */
    public static java.util.Map<String, Double> soundMix(Place place) {
        java.util.Map<String, Double> mix = Magnetosphere.soundMix(ClientUniverse.system(), place.body(), place.r(), ClientUniverse.time());
        // Acheron drowns out everything as you come near it, whatever you're counted as going round.
        double near = acheronNear(place);
        if (near > 0) {
            java.util.Map<String, Double> blended = new java.util.LinkedHashMap<>();
            mix.forEach((name, loudness) -> blended.put(name, loudness * (1 - near)));
            blended.merge("plasma_acheron", near, (a, b) -> Math.min(1, a + b));
            return blended;
        }
        return mix;
    }

    /** Which sound the plasma wave receiver hears at a place. */
    public static String sound(Place place) {
        if (acheronNear(place) > 0.5) {
            return "plasma_acheron";
        }
        CelestialBody in = Magnetosphere.inside(ClientUniverse.system(), place.body(), place.r(), ClientUniverse.time());
        return Magnetosphere.sound(place.body(), in);
    }

    /** How near Acheron is in space, 0 (beyond a dozen of its radii) to 1 (within three). */
    private static double acheronNear(Place place) {
        SolarSystem system = ClientUniverse.system();
        CelestialBody acheron = system.body("acheron");
        if (place.position() == null || acheron == null || place.body() == acheron
                || Double.isNaN(com.thepearlking.stevespace.universe.AcheronCourse.pass())) {
            return 0;
        }
        double t = ClientUniverse.time();
        double d = system.absolutePosition(place.body(), t).add(place.position()).sub(system.absolutePosition(acheron, t)).length();
        double x = Math.max(0, Math.min(1, (12 - d / acheron.radius()) / 9));
        return x * x * (3 - 2 * x);
    }

    /** True when the player is shielded: in a ship, or with solid blocks overhead. */
    public static boolean shielded() {
        var player = Minecraft.getInstance().player;
        return player != null && (player.isPassenger() || !player.level().canSeeSky(player.blockPosition().above()));
    }
}
