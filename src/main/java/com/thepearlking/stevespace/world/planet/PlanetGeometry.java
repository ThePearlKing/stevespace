package com.thepearlking.stevespace.world.planet;

import com.thepearlking.stevespace.world.planet.noise.SpherePoint;

/**
 * How a round planet is laid out on Minecraft's flat block grid.
 *
 * <p>The surface is an equirectangular map: X is longitude and Z is latitude.
 * <ul>
 *   <li>X runs from {@code -circumference/2} to {@code +circumference/2} and wraps around.</li>
 *   <li>Z = 0 is the equator, {@code z = -circumference/4} the north pole and
 *       {@code +circumference/4} the south pole (Minecraft's north is -Z).</li>
 *   <li>One block east-west equals one block north-south at the equator.</li>
 * </ul>
 * Walking over a pole puts you on the opposite meridian heading the other way, exactly like on
 * a real globe; {@link #wrap} computes that.
 */
public final class PlanetGeometry {

    private final double radius;
    private final int circumference;

    /** @param radius planet radius in blocks (1 block = 1 km of the real body) */
    public PlanetGeometry(double radius) {
        this.radius = radius;
        // Round to a multiple of 64 so the pole edges fall on chunk boundaries.
        this.circumference = Math.max(256, (int) Math.round(2 * Math.PI * radius / 64.0) * 64);
    }

    public double radius() {
        return radius;
    }

    public int circumference() {
        return circumference;
    }

    /** Distance in blocks from the equator to a pole. */
    public int poleDistance() {
        return circumference / 4;
    }

    /** Latitude in radians, +π/2 at the north pole. */
    public double latitude(double z) {
        return -z * (2 * Math.PI / circumference);
    }

    /** Longitude in radians, 0 at x = 0, increasing eastward (+X). */
    public double longitude(double x) {
        return x * (2 * Math.PI / circumference);
    }

    public double zForLatitude(double latitude) {
        return -latitude * circumference / (2 * Math.PI);
    }

    public double xForLongitude(double longitude) {
        return longitude * circumference / (2 * Math.PI);
    }

    public SpherePoint spherePoint(double x, double z, SpherePoint out) {
        return out.set(latitude(z), longitude(x));
    }

    /** Result of moving a position back onto the map after it walked off an edge. */
    public record Wrapped(double x, double z, boolean crossedPole) {
    }

    /**
     * Brings a position that left the map back onto it.
     * Crossing the east/west edge shifts by one circumference. Crossing a pole mirrors the
     * latitude back and moves half way round in longitude; the caller must then turn the
     * player around by 180° (they are now walking away from the pole they just crossed).
     */
    public Wrapped wrap(double x, double z) {
        boolean crossedPole = false;
        double half = circumference / 2.0;
        double pole = poleDistance();
        if (z < -pole) {
            z = -2 * pole - z;
            x += half;
            crossedPole = true;
        } else if (z > pole) {
            z = 2 * pole - z;
            x += half;
            crossedPole = true;
        }
        x = wrapX(x);
        return new Wrapped(x, z, crossedPole);
    }

    public double wrapX(double x) {
        double half = circumference / 2.0;
        double shifted = (x + half) % circumference;
        if (shifted < 0) {
            shifted += circumference;
        }
        return shifted - half;
    }

    public boolean isOnMap(double x, double z) {
        double half = circumference / 2.0;
        return x >= -half && x < half && Math.abs(z) <= poleDistance();
    }
}
