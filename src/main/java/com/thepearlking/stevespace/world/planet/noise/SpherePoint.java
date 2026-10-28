package com.thepearlking.stevespace.world.planet.noise;

/**
 * A mutable point on the unit sphere, reused across samples to avoid garbage in hot loops.
 * Axes: +Y is the north pole, longitude 0 lies on +X, longitude +90° on +Z.
 */
public final class SpherePoint {
    public double x;
    public double y;
    public double z;

    public SpherePoint set(double latitude, double longitude) {
        double cosLat = Math.cos(latitude);
        x = cosLat * Math.cos(longitude);
        y = Math.sin(latitude);
        z = cosLat * Math.sin(longitude);
        return this;
    }
}
