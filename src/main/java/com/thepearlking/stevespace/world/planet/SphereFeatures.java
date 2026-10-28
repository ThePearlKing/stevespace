package com.thepearlking.stevespace.world.planet;

import com.thepearlking.stevespace.world.planet.noise.SpherePoint;

import java.util.Random;

/**
 * Building blocks for landforms placed on a sphere: round features (volcanoes, domes, rings)
 * at random spots, and long straight features (rifts, ridges) along great circles. All sizes are
 * in blocks along the surface; everything is deterministic from the seed.
 */
public final class SphereFeatures {

    private SphereFeatures() {
    }

    /** A round feature: centre (unit vector), size (blocks) and a free parameter (height etc.). */
    public record Spot(double x, double y, double z, double radius, double value) {

        /** Distance from the centre to {@code p} along the surface, in units of this spot's radius. */
        public double distance(SpherePoint p, double planetRadius) {
            double dot = Math.max(-1, Math.min(1, p.x * x + p.y * y + p.z * z));
            return Math.acos(dot) * planetRadius / radius;
        }

        /** Cheap test before {@link #distance}: is {@code p} within {@code reach} radii? */
        public boolean near(SpherePoint p, double planetRadius, double reach) {
            double angle = radius * reach / planetRadius;
            return p.x * x + p.y * y + p.z * z > Math.cos(Math.min(Math.PI, angle));
        }
    }

    /**
     * A segment of a great circle: a pole (unit normal of its plane), the centre of the segment,
     * how far it runs either side of the centre (radians), and its half-width (blocks).
     */
    public record Arc(double nx, double ny, double nz, double cx, double cy, double cz, double halfLength, double halfWidth,
                      double value) {

        /** Distance from the arc's centre line in half-widths, or +infinity beyond its ends. */
        public double across(SpherePoint p, double planetRadius) {
            double off = p.x * nx + p.y * ny + p.z * nz;      // sine of the angle off the plane
            double along = Math.acos(Math.max(-1, Math.min(1, (p.x * cx + p.y * cy + p.z * cz) / Math.sqrt(Math.max(1e-12, 1 - off * off)))));
            if (along > halfLength) {
                return Double.POSITIVE_INFINITY;
            }
            return Math.abs(Math.asin(Math.max(-1, Math.min(1, off)))) * planetRadius / halfWidth;
        }

        /** 0 at the middle, 1 at the ends: arcs taper toward their ends. */
        public double alongFraction(SpherePoint p) {
            double off = p.x * nx + p.y * ny + p.z * nz;
            double along = Math.acos(Math.max(-1, Math.min(1, (p.x * cx + p.y * cy + p.z * cz) / Math.sqrt(Math.max(1e-12, 1 - off * off)))));
            return Math.min(1, along / halfLength);
        }
    }

    /** A uniformly random point on the sphere, optionally kept within |latitude| ≤ maxLat. */
    public static double[] randomPoint(Random random, double maxLatitude) {
        while (true) {
            double z = random.nextDouble() * 2 - 1;
            double a = random.nextDouble() * 2 * Math.PI;
            double r = Math.sqrt(1 - z * z);
            double[] p = {r * Math.cos(a), z, r * Math.sin(a)}; // y is north here, like SpherePoint
            if (Math.abs(Math.asin(p[1])) <= maxLatitude) {
                return p;
            }
        }
    }

    public static Spot[] spots(long seed, int count, double minRadius, double maxRadius, double minValue, double maxValue) {
        Random random = new Random(seed);
        Spot[] spots = new Spot[count];
        for (int i = 0; i < count; i++) {
            double[] p = randomPoint(random, Math.toRadians(80));
            // Small ones far more common than big ones.
            double t = Math.pow(random.nextDouble(), 2.2);
            spots[i] = new Spot(p[0], p[1], p[2], minRadius + (maxRadius - minRadius) * t,
                    minValue + (maxValue - minValue) * (0.3 + 0.7 * t) * (0.7 + 0.3 * random.nextDouble()));
        }
        return spots;
    }

    /** Random great-circle segments: {@code minLength}..{@code maxLength} radians long. */
    public static Arc[] arcs(long seed, int count, double minLength, double maxLength, double minWidth, double maxWidth,
                             double minValue, double maxValue) {
        Random random = new Random(seed);
        Arc[] arcs = new Arc[count];
        for (int i = 0; i < count; i++) {
            double[] c = randomPoint(random, Math.toRadians(75));
            double[] d = randomPoint(random, Math.PI);
            // Pole = centre × direction, normalised (a plane through the centre).
            double nx = c[1] * d[2] - c[2] * d[1];
            double ny = c[2] * d[0] - c[0] * d[2];
            double nz = c[0] * d[1] - c[1] * d[0];
            double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
            double t = random.nextDouble();
            arcs[i] = new Arc(nx / len, ny / len, nz / len, c[0], c[1], c[2],
                    (minLength + (maxLength - minLength) * t) / 2, minWidth + (maxWidth - minWidth) * random.nextDouble(),
                    minValue + (maxValue - minValue) * random.nextDouble());
        }
        return arcs;
    }
}
