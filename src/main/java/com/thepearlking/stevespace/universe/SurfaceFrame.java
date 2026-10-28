package com.thepearlking.stevespace.universe;

import com.thepearlking.stevespace.world.planet.PlanetGeometry;

/**
 * Converts between flying over a planet's map (block position + velocity along east, up, south)
 * and orbiting it (position and velocity relative to its centre, in the ecliptic frame).
 *
 * <p>The surface map turns with the planet, so velocities over it are relative to the ground:
 * going to orbit adds the ground's own eastward speed, coming back subtracts it again.
 */
public final class SurfaceFrame {

    /** Sea level of every planet map: altitude 0 is y = 63. */
    public static final double SEA_LEVEL = 63;

    private SurfaceFrame() {
    }

    /** A place over the map, with its local axes in the ecliptic frame. */
    public record Local(double latitude, double longitude, double altitude, DVec3 east, DVec3 up, DVec3 north) {
        public DVec3 south() {
            return north.scale(-1);
        }
    }

    /** Local axes above map position (x, y, z) of {@code body} at time {@code t}. */
    public static Local local(SolarSystem system, CelestialBody body, double x, double y, double z, double t) {
        PlanetGeometry geometry = new PlanetGeometry(body.radius());
        double latitude = geometry.latitude(z);
        double longitude = geometry.longitude(x);
        SolarSystem.BodyFrame frame = system.frameAt(body, t);
        return new Local(latitude, longitude, y - SEA_LEVEL, frame.east(longitude), frame.up(latitude, longitude),
                frame.northAt(latitude, longitude));
    }

    /** How fast the body turns, in radians per second (tidally locked: once per orbit). */
    public static double spinRate(SolarSystem system, CelestialBody body) {
        if (body.isTidallyLocked() && body.parent() != null) {
            return 2 * Math.PI / body.orbit().period(body.parent().mu() + body.mu());
        }
        return 2 * Math.PI / system.siderealPeriod(body);
    }

    /** Velocity of the ground itself at a point (the planet spinning), in the ecliptic frame. */
    public static DVec3 groundVelocity(SolarSystem system, CelestialBody body, DVec3 position, double t) {
        return body.spinAxis().scale(spinRate(system, body)).cross(position);
    }

    /**
     * Orbital state (relative to the body's centre) of something at map position (x, y, z) moving
     * at {@code east}, {@code up}, {@code south} blocks per second over the ground.
     */
    public static StateVector toOrbit(SolarSystem system, CelestialBody body, double x, double y, double z,
                                      double east, double up, double south, double t) {
        Local local = local(system, body, x, y, z, t);
        DVec3 position = local.up().scale(body.radius() + local.altitude());
        DVec3 velocity = local.east().scale(east).add(local.up().scale(up)).add(local.north().scale(-south))
                .add(groundVelocity(system, body, position, t));
        return new StateVector(position, velocity);
    }

    /** The reverse: where over the map an orbital state is, and its velocity over the ground. */
    public record MapState(double x, double y, double z, double east, double up, double south, Local local) {
    }

    public static MapState toMap(SolarSystem system, CelestialBody body, StateVector state, double t) {
        SolarSystem.BodyFrame frame = system.frameAt(body, t);
        DVec3 r = state.position();
        double radius = r.length();
        DVec3 dir = r.scale(1 / radius);
        double latitude = Math.asin(Math.max(-1, Math.min(1, dir.dot(frame.north()))));
        double longitude = Math.atan2(dir.dot(frame.east90()), dir.dot(frame.meridian()));
        PlanetGeometry geometry = new PlanetGeometry(body.radius());
        double x = geometry.xForLongitude(longitude);
        double z = geometry.zForLatitude(latitude);
        double y = SEA_LEVEL + radius - body.radius();
        Local local = new Local(latitude, longitude, radius - body.radius(), frame.east(longitude),
                frame.up(latitude, longitude), frame.northAt(latitude, longitude));
        DVec3 overGround = state.velocity().sub(groundVelocity(system, body, r, t));
        return new MapState(x, y, z, overGround.dot(local.east()), overGround.dot(local.up()),
                -overGround.dot(local.north()), local);
    }
}
