package com.thepearlking.stevespace.universe;

/**
 * One star, planet or moon: its size, gravity, orbit, spin and looks.
 *
 * <p>Units: lengths in blocks (1 block = 1 km of the real body), time in game seconds, angles in
 * radians. Instances are built once by {@link SolarSystem} and never change.
 */
public final class CelestialBody {

    public enum Kind {
        STAR, TERRESTRIAL, GAS_GIANT, ICE_GIANT, MOON, DWARF_PLANET;

        /** Whether this kind has a solid surface. */
        public boolean isSolid() {
            return this == TERRESTRIAL || this == MOON || this == DWARF_PLANET;
        }
    }

    private final String id;
    private final String displayName;
    private final Kind kind;
    private final CelestialBody parent;
    private final double radius;
    private final double mu;
    private final OrbitalElements orbit;
    /** Sidereal rotation period relative to Earth's (negative = retrograde). Ignored if locked. */
    private final double siderealDays;
    private final boolean tidallyLocked;
    private final DVec3 spinAxis;
    private final int color;
    private final double albedo;
    private final Atmosphere atmosphere;
    private final Rings rings;
    /** Dimension id of the surface if you can land here, else null. */
    private final String surfaceDimension;
    /** The body's three half-axes over its mean radius (along its prime meridian, 90° east, and its pole); 1, 1, 1 for a round one. */
    private final double[] shape;
    /** A body that doesn't keep to an orbit: where it is (relative to its parent) comes from this instead. */
    private final Course course;
    /** Left off maps, target lists and route planning: only found by looking. */
    private final boolean hidden;
    /** Its sphere of influence, if set (a body with a course has no orbit to work it out from). */
    private final double soi;

    /** Where a body with no orbit of its own is, relative to its parent, at time t. */
    public interface Course {
        StateVector stateAt(double t);
    }

    /**
     * @param color   average colour seen from far away (0xRRGGBB)
     * @param height  how high the visible atmosphere reaches, in blocks above the surface
     * @param density 1 = Earth at the surface; drives sky colour strength and drag
     */
    public record Atmosphere(int color, double height, double density) {
    }

    public record Rings(double innerRadius, double outerRadius, int color, double opacity) {
    }

    private CelestialBody(Builder b) {
        this.id = b.id;
        this.displayName = b.displayName;
        this.kind = b.kind;
        this.parent = b.parent;
        this.radius = b.radius;
        this.mu = b.mu;
        this.orbit = b.orbit;
        this.siderealDays = b.siderealDays;
        this.tidallyLocked = b.tidallyLocked;
        double tilt = Math.toRadians(b.axialTiltDeg);
        double azimuth = Math.toRadians(b.axisAzimuthDeg);
        this.spinAxis = new DVec3(Math.sin(tilt) * Math.cos(azimuth), Math.sin(tilt) * Math.sin(azimuth), Math.cos(tilt));
        this.color = b.color;
        this.albedo = b.albedo;
        this.atmosphere = b.atmosphere;
        this.rings = b.rings;
        this.surfaceDimension = b.surfaceDimension;
        this.shape = b.shape;
        this.course = b.course;
        this.hidden = b.hidden;
        this.soi = b.soi;
    }

    public Course course() {
        return course;
    }

    public boolean hidden() {
        return hidden;
    }

    /** The sphere of influence given to it (0 if worked out from its orbit). */
    public double fixedSoi() {
        return soi;
    }

    /** Half-axes over the mean radius: (prime meridian, 90° east, pole). */
    public double[] shape() {
        return shape;
    }

    public boolean round() {
        return shape[0] == 1 && shape[1] == 1 && shape[2] == 1;
    }

    /**
     * The point on the surface (from the centre, in units of the mean radius) in the direction of
     * the round body's {@code up}, stretched to this body's real shape. {@code frame} is its
     * orientation now.
     */
    public DVec3 surface(SolarSystem.BodyFrame frame, DVec3 up) {
        if (round()) {
            return up;
        }
        return frame.meridian().scale(up.dot(frame.meridian()) * shape[0])
                .add(frame.east90().scale(up.dot(frame.east90()) * shape[1]))
                .add(frame.north().scale(up.dot(frame.north()) * shape[2]));
    }

    /** Which way the surface faces there (for the light on it). */
    public DVec3 normal(SolarSystem.BodyFrame frame, DVec3 up) {
        if (round()) {
            return up;
        }
        return frame.meridian().scale(up.dot(frame.meridian()) / shape[0])
                .add(frame.east90().scale(up.dot(frame.east90()) / shape[1]))
                .add(frame.north().scale(up.dot(frame.north()) / shape[2])).normalize();
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public Kind kind() {
        return kind;
    }

    /** The body this one orbits, or null for the Sun. */
    public CelestialBody parent() {
        return parent;
    }

    public double radius() {
        return radius;
    }

    /** Gravitational parameter GM in blocks³/s². */
    public double mu() {
        return mu;
    }

    public double surfaceGravity() {
        return mu / (radius * radius);
    }

    public OrbitalElements orbit() {
        return orbit;
    }

    public double siderealDays() {
        return siderealDays;
    }

    public boolean isTidallyLocked() {
        return tidallyLocked;
    }

    /** North pole direction in the ecliptic frame. */
    public DVec3 spinAxis() {
        return spinAxis;
    }

    public int color() {
        return color;
    }

    public double albedo() {
        return albedo;
    }

    public Atmosphere atmosphere() {
        return atmosphere;
    }

    public boolean hasAtmosphere() {
        return atmosphere != null;
    }

    public Rings rings() {
        return rings;
    }

    public String surfaceDimension() {
        return surfaceDimension;
    }

    public boolean isLandable() {
        return surfaceDimension != null;
    }

    @Override
    public String toString() {
        return displayName;
    }

    public static Builder builder(String id, String displayName, Kind kind) {
        return new Builder(id, displayName, kind);
    }

    /** Fluent builder so the solar system table in {@link SolarSystem} reads like a data sheet. */
    public static final class Builder {
        private final String id;
        private final String displayName;
        private final Kind kind;
        private CelestialBody parent;
        private double radius;
        private double mu;
        private OrbitalElements orbit;
        private double siderealDays = 1;
        private boolean tidallyLocked;
        private double axialTiltDeg;
        private double axisAzimuthDeg = -90;
        private int color = 0x808080;
        private double albedo = 0.3;
        private Atmosphere atmosphere;
        private Rings rings;
        private String surfaceDimension;
        private double[] shape = {1, 1, 1};
        private Course course;
        private boolean hidden;
        private double soi;

        private Builder(String id, String displayName, Kind kind) {
            this.id = id;
            this.displayName = displayName;
            this.kind = kind;
        }

        public Builder orbits(CelestialBody parent, OrbitalElements orbit) {
            this.parent = parent;
            this.orbit = orbit;
            return this;
        }

        public Builder size(double radius, double mu) {
            this.radius = radius;
            this.mu = mu;
            return this;
        }

        public Builder spin(double siderealDays, double axialTiltDeg) {
            this.siderealDays = siderealDays;
            this.axialTiltDeg = axialTiltDeg;
            return this;
        }

        public Builder axisAzimuth(double degrees) {
            this.axisAzimuthDeg = degrees;
            return this;
        }

        public Builder tidallyLocked(double axialTiltDeg) {
            this.tidallyLocked = true;
            this.axialTiltDeg = axialTiltDeg;
            return this;
        }

        public Builder looks(int color, double albedo) {
            this.color = color;
            this.albedo = albedo;
            return this;
        }

        public Builder atmosphere(int color, double height, double density) {
            this.atmosphere = new Atmosphere(color, height, density);
            return this;
        }

        public Builder rings(double innerRadius, double outerRadius, int color, double opacity) {
            this.rings = new Rings(innerRadius, outerRadius, color, opacity);
            return this;
        }

        /** A lumpy body: its three diameters (any unit), kept at the same mean size. */
        public Builder shape(double a, double b, double c) {
            double mean = Math.cbrt(a * b * c);
            this.shape = new double[]{a / mean, b / mean, c / mean};
            return this;
        }

        /** No orbit: it goes where {@code course} says (and has this sphere of influence). */
        public Builder course(Course course, double soi) {
            this.course = course;
            this.soi = soi;
            return this;
        }

        public Builder hidden() {
            this.hidden = true;
            return this;
        }

        public Builder landable(String dimensionId) {
            this.surfaceDimension = dimensionId;
            return this;
        }

        public CelestialBody build() {
            return new CelestialBody(this);
        }
    }
}
