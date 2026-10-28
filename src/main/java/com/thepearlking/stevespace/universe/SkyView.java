package com.thepearlking.stevespace.universe;

/**
 * Where the camera is in the solar system and how its axes (Minecraft's x, y, z) are oriented.
 * On a planet's surface the axes are east, up and south; in space they are the vessel cell's
 * fixed axes.
 */
public interface SkyView {

    /** Camera position relative to the Sun, in blocks. */
    DVec3 position();

    /** Converts an ecliptic-frame direction or offset to the camera's x, y, z axes. */
    DVec3 toLocal(DVec3 inertial);

    /** Converts camera axes back to the ecliptic frame. */
    DVec3 toInertial(DVec3 local);

    /** Offset from the camera to a body's centre, in camera axes (blocks). */
    default DVec3 offsetTo(SolarSystem system, CelestialBody body, double t) {
        return toLocal(system.absolutePosition(body, t).sub(position()));
    }
}
