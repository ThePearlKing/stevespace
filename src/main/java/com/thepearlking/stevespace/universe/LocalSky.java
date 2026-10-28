package com.thepearlking.stevespace.universe;

/**
 * Converts between the solar system's inertial frame and the sky of someone standing on a
 * planet. Minecraft axes for the local sky: +X east, +Y up, +Z south.
 */
public final class LocalSky implements SkyView {

    private final DVec3 east;
    private final DVec3 up;
    private final DVec3 north;
    /** The planet's spin axis (north pole direction). */
    private final DVec3 axis;
    /** Observer position relative to the Sun, in blocks. */
    private final DVec3 position;

    private LocalSky(DVec3 east, DVec3 up, DVec3 north, DVec3 axis, DVec3 position) {
        this.axis = axis;
        this.east = east;
        this.up = up;
        this.north = north;
        this.position = position;
    }

    /**
     * The sky seen from latitude/longitude (radians) at {@code altitude} blocks above the surface
     * of {@code planet} at universe time {@code t}.
     */
    public static LocalSky at(SolarSystem system, CelestialBody planet, double latitude, double longitude,
                              double altitude, double t) {
        SolarSystem.BodyFrame frame = system.frameAt(planet, t);
        DVec3 up = frame.up(latitude, longitude);
        DVec3 east = frame.east(longitude);
        DVec3 north = frame.northAt(latitude, longitude);
        DVec3 position = system.absolutePosition(planet, t).add(up.scale(planet.radius() + altitude));
        return new LocalSky(east, up, north, frame.north(), position);
    }

    /** Converts an inertial direction or offset to local Minecraft axes. */
    @Override
    public DVec3 toLocal(DVec3 inertial) {
        return new DVec3(inertial.dot(east), inertial.dot(up), -inertial.dot(north));
    }

    /** Converts local Minecraft axes back to the inertial frame. */
    @Override
    public DVec3 toInertial(DVec3 local) {
        return east.scale(local.x()).add(up.scale(local.y())).add(north.scale(-local.z()));
    }

    /** Elevation of a body's centre above the horizon, in radians. */
    public double elevationOf(SolarSystem system, CelestialBody body, double t) {
        DVec3 local = offsetTo(system, body, t).normalize();
        return Math.asin(Math.max(-1, Math.min(1, local.y())));
    }

    /**
     * Local hour angle of a body in radians: 0 when it crosses the meridian (local noon for
     * the Sun), positive after, ±π at local midnight. Works at any latitude.
     */
    public double hourAngle(SolarSystem system, CelestialBody body, double t) {
        DVec3 toBody = system.absolutePosition(body, t).sub(position).normalize();
        DVec3 bodyInPlane = toBody.sub(axis.scale(toBody.dot(axis)));
        DVec3 upInPlane = up.sub(axis.scale(up.dot(axis)));
        // Sign: the sun moves westward, i.e. away from local east, as the hour angle grows.
        double x = bodyInPlane.dot(upInPlane.normalize());
        double y = -bodyInPlane.dot(axis.cross(upInPlane).normalize());
        return Math.atan2(y, x);
    }

    @Override
    public DVec3 position() {
        return position;
    }

    public DVec3 up() {
        return up;
    }
}
