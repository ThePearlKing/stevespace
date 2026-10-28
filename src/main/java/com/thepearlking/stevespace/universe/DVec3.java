package com.thepearlking.stevespace.universe;

/**
 * Immutable double-precision 3D vector for orbital mechanics. Solar-system distances (billions of
 * blocks) need doubles; Minecraft's float vectors would lose whole blocks of precision.
 */
public record DVec3(double x, double y, double z) {

    public static final DVec3 ZERO = new DVec3(0, 0, 0);
    public static final DVec3 X = new DVec3(1, 0, 0);
    public static final DVec3 Y = new DVec3(0, 1, 0);
    public static final DVec3 Z = new DVec3(0, 0, 1);

    public DVec3 add(DVec3 o) {
        return new DVec3(x + o.x, y + o.y, z + o.z);
    }

    public DVec3 sub(DVec3 o) {
        return new DVec3(x - o.x, y - o.y, z - o.z);
    }

    public DVec3 scale(double s) {
        return new DVec3(x * s, y * s, z * s);
    }

    public double dot(DVec3 o) {
        return x * o.x + y * o.y + z * o.z;
    }

    public DVec3 cross(DVec3 o) {
        return new DVec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x);
    }

    public double length() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    public double lengthSquared() {
        return x * x + y * y + z * z;
    }

    public DVec3 normalize() {
        double len = length();
        return len < 1e-300 ? ZERO : scale(1 / len);
    }

    /** Rotates this vector about a unit axis by {@code angle} radians (right-hand rule). */
    public DVec3 rotate(DVec3 axis, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        // Rodrigues' rotation formula
        return scale(cos).add(axis.cross(this).scale(sin)).add(axis.scale(axis.dot(this) * (1 - cos)));
    }

    public static DVec3 lerp(DVec3 a, DVec3 b, double t) {
        return a.add(b.sub(a).scale(t));
    }
}
