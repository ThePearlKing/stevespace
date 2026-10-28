package com.thepearlking.stevespace.universe;

import com.thepearlking.stevespace.universe.CelestialBody.Kind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The real solar system at 1 block = 1 km: true sizes, true distances, true orbits (J2000
 * elements), true tilts and day lengths, true relative gravity.
 *
 * <p>Gravity: Earth's surface gravity is 9.81 blocks/s², which with 1 km blocks means physics
 * runs √1000 ≈ 31.6× faster than reality (low orbit ≈ 2.7 minutes, a year ≈ 11.6 real days).
 * Every other body's GM is its real ratio to Earth's.
 *
 * <p>Rotation is the one thing scaled separately: Earth's day is a config value (20 minutes by
 * default) and every other body's day keeps its real ratio to Earth's. Tidally locked moons
 * always show the same face to their planet.
 */
public final class SolarSystem {

    /** Earth's surface gravity in blocks/s²; every body's gravity is scaled from it. */
    public static final double EARTH_GRAVITY = 9.81;
    public static final double EARTH_RADIUS = 6371;
    public static final double EARTH_MU = EARTH_GRAVITY * EARTH_RADIUS * EARTH_RADIUS;
    /** One astronomical unit in blocks. */
    public static final double AU = 149_597_871;
    /** Earth's real sidereal day as a fraction of its solar day. */
    private static final double EARTH_SIDEREAL_RATIO = 0.99726968;

    private final Map<String, CelestialBody> bodies = new LinkedHashMap<>();
    private final Map<CelestialBody, List<CelestialBody>> children = new LinkedHashMap<>();
    private final double earthDaySeconds;
    /** Universe time and number of Earth rotations at the moment the day length was last set. */
    private final double rotationAnchorTime;
    private final double rotationAnchorTurns;
    private final CelestialBody sun;
    private final CelestialBody earth;

    /** @param earthDaySeconds length of a day on Earth in game seconds (default 1200 = 20 min) */
    public SolarSystem(double earthDaySeconds) {
        this(earthDaySeconds, 0, 0);
    }

    /**
     * @param rotationAnchorTime  universe time when this day length took effect
     * @param rotationAnchorTurns how many times Earth had turned by then; keeps every planet's
     *                            rotation continuous when the day length is changed mid-game
     */
    public SolarSystem(double earthDaySeconds, double rotationAnchorTime, double rotationAnchorTurns) {
        this.earthDaySeconds = earthDaySeconds;
        this.rotationAnchorTime = rotationAnchorTime;
        this.rotationAnchorTurns = rotationAnchorTurns;
        sun = add(CelestialBody.builder("sun", "Sun", Kind.STAR)
                .size(696_000, 332_946 * EARTH_MU)
                .spin(25.38, 7.25)
                .looks(0xFFF4E0, 1));

        add(planet("mercury", "Mercury", Kind.TERRESTRIAL, 0.387099, 0.205630, 7.005, 48.331, 77.456, 252.251)
                .size(2_440, 0.055274 * EARTH_MU).spin(58.646, 0.03)
                .looks(0x9C9690, 0.14).landable("stevespace:mercury"));
        add(planet("venus", "Venus", Kind.TERRESTRIAL, 0.723332, 0.006772, 3.395, 76.680, 131.533, 181.980)
                .size(6_052, 0.815 * EARTH_MU).spin(-243.02, 177.4)
                .looks(0xE6D8B4, 0.69).atmosphere(0xE8C88A, 2600, 60).landable("stevespace:venus"));
        earth = add(planet("earth", "Earth", Kind.TERRESTRIAL, 1.000000, 0.016710, 0.0, -11.26, 102.947, 100.464)
                .size(EARTH_RADIUS, EARTH_MU).spin(EARTH_SIDEREAL_RATIO, 23.44)
                .looks(0x4E6F9E, 0.37).atmosphere(0x7FA8E0, 2400, 1).landable("minecraft:overworld"));
        add(moon("moon", "Moon", earth, 384_400, 0.0549, 5.145, 125.08, 318.15, 135.27)
                .size(1_737, 0.0123 * EARTH_MU).tidallyLocked(1.54)
                .looks(0xA9A7A2, 0.12).landable("stevespace:moon"));
        // Not on any chart.
        add(moon("acheron", "???", earth, 384_400, 0, 0, 0, 0, 0)
                .course(new AcheronCourse(this), AcheronCourse.GAP / 2).hidden()
                .size(AcheronCourse.RADIUS, 0.0123 * EARTH_MU).tidallyLocked(0)
                .looks(0x35874F, 0.14).landable("stevespace:acheron"));

        CelestialBody mars = add(planet("mars", "Mars", Kind.TERRESTRIAL, 1.523679, 0.093400, 1.850, 49.558, 336.040, 355.453)
                .size(3_390, 0.107447 * EARTH_MU).spin(1.025957, 25.19)
                .looks(0xB8693F, 0.17).atmosphere(0xC9A07A, 1400, 0.03).landable("stevespace:mars"));
        add(moon("phobos", "Phobos", mars, 9_376, 0.0151, 1.08, 16.9, 150.0, 91.0)
                .size(11, 1.8e-9 * EARTH_MU).tidallyLocked(0).looks(0x6E655C, 0.07));
        add(moon("deimos", "Deimos", mars, 23_463, 0.0003, 1.79, 47.0, 290.0, 296.0)
                .size(6, 2.5e-10 * EARTH_MU).tidallyLocked(0).looks(0x8A7F72, 0.07));

        CelestialBody jupiter = add(planet("jupiter", "Jupiter", Kind.GAS_GIANT, 5.202603, 0.048498, 1.303, 100.464, 14.331, 34.351)
                .size(69_911, 317.83 * EARTH_MU).spin(0.41354, 3.13)
                .looks(0xD2B48C, 0.54).atmosphere(0xD9C3A0, 5_000, 1));
        add(moon("io", "Io", jupiter, 421_700, 0.0041, 2.2, 43.0, 84.0, 342.0)
                .size(1_822, 0.01496 * EARTH_MU).tidallyLocked(0)
                .looks(0xD8C25C, 0.63).landable("stevespace:io"));
        add(moon("europa", "Europa", jupiter, 671_034, 0.0094, 1.8, 219.0, 88.0, 171.0)
                .size(1_561, 0.00804 * EARTH_MU).tidallyLocked(0.1)
                .looks(0xCFC3AC, 0.67).landable("stevespace:europa"));
        add(moon("ganymede", "Ganymede", jupiter, 1_070_412, 0.0013, 2.2, 63.0, 192.0, 317.0)
                .size(2_634, 0.02480 * EARTH_MU).tidallyLocked(0.33)
                .looks(0x9C9286, 0.43).landable("stevespace:ganymede"));
        add(moon("callisto", "Callisto", jupiter, 1_882_709, 0.0074, 2.0, 298.0, 52.0, 181.0)
                .size(2_410, 0.01801 * EARTH_MU).tidallyLocked(0)
                .looks(0x6E6459, 0.22).landable("stevespace:callisto"));

        CelestialBody saturn = add(planet("saturn", "Saturn", Kind.GAS_GIANT, 9.554909, 0.055508, 2.489, 113.666, 93.057, 50.077)
                .size(58_232, 95.16 * EARTH_MU).spin(0.444, 26.73).axisAzimuth(79)
                .looks(0xD9C79C, 0.50).atmosphere(0xE0D2AE, 4_500, 1)
                .rings(74_500, 140_220, 0xCDBB95, 0.85));
        add(moon("enceladus", "Enceladus", saturn, 237_948, 0.0047, 26.73, 169.0, 115.0, 200.0)
                .size(252, 1.8e-5 * EARTH_MU).tidallyLocked(0)
                .looks(0xF2F4F6, 1.0).landable("stevespace:enceladus"));
        add(moon("titan", "Titan", saturn, 1_221_870, 0.0288, 27.0, 169.0, 186.0, 11.0)
                .size(2_575, 0.02253 * EARTH_MU).tidallyLocked(0.3)
                .looks(0xD39C48, 0.22).atmosphere(0xC98A3A, 2200, 1.5).landable("stevespace:titan"));

        add(planet("uranus", "Uranus", Kind.ICE_GIANT, 19.21845, 0.046295, 0.773, 74.006, 173.005, 314.055)
                .size(25_362, 14.54 * EARTH_MU).spin(-0.71833, 97.77).axisAzimuth(170)
                .looks(0xA7D3DA, 0.49).atmosphere(0xB5E0E6, 2_500, 1)
                .rings(41_800, 51_150, 0x5E5A57, 0.25));
        CelestialBody neptune = add(planet("neptune", "Neptune", Kind.ICE_GIANT, 30.11039, 0.008988, 1.770, 131.784, 48.124, 304.349)
                .size(24_622, 17.15 * EARTH_MU).spin(0.6713, 28.32)
                .looks(0x4B70C9, 0.44).atmosphere(0x5A82D6, 2_500, 1));
        add(moon("triton", "Triton", neptune, 354_759, 0.000016, 157.3, 177.0, 0.0, 264.0)
                .size(1_353, 0.00359 * EARTH_MU).tidallyLocked(0)
                .looks(0xCDBDB2, 0.76).landable("stevespace:triton"));

        // The main asteroid belt's four biggest: tiny worlds with barely any pull (1-3% of Earth's).
        add(planet("ceres", "Ceres", Kind.DWARF_PLANET, 2.7675, 0.0758, 10.59, 80.31, 153.9, 181.4)
                .size(470, 0.000157 * EARTH_MU).spin(0.378, 4.0).shape(964.4, 964.2, 891.8)
                .looks(0x6E6A66, 0.09).landable("stevespace:ceres"));
        add(planet("vesta", "Vesta", Kind.DWARF_PLANET, 2.3615, 0.0887, 7.14, 103.85, 255.05, 202.9)
                .size(263, 0.0000434 * EARTH_MU).spin(0.2226, 29.0).shape(572.6, 557.2, 446.4)
                .looks(0xA8A090, 0.42).landable("stevespace:vesta"));
        add(planet("pallas", "Pallas", Kind.DWARF_PLANET, 2.7725, 0.2299, 34.84, 173.1, 123.1, 285.0)
                .size(256, 0.0000342 * EARTH_MU).spin(0.3254, 84.0).shape(568, 532, 448)
                .looks(0x8C8880, 0.16).landable("stevespace:pallas"));
        add(planet("hygiea", "Hygiea", Kind.DWARF_PLANET, 3.1415, 0.1146, 3.83, 283.2, 235.5, 27.6)
                .size(217, 0.0000146 * EARTH_MU).spin(0.575, 10.0)
                .looks(0x5E5A56, 0.07).landable("stevespace:hygiea"));

        CelestialBody pluto = add(planet("pluto", "Pluto", Kind.DWARF_PLANET, 39.48211, 0.248808, 17.14, 110.30, 224.07, 238.93)
                .size(1_188, 0.00218 * EARTH_MU).spin(-6.387, 122.5)
                .looks(0xC9A886, 0.52).landable("stevespace:pluto"));
        add(moon("charon", "Charon", pluto, 19_591, 0.0002, 0.08, 223.0, 0.0, 147.0)
                .size(606, 0.000254 * EARTH_MU).tidallyLocked(0)
                .looks(0x928B84, 0.35).landable("stevespace:charon"));

        // Out past Neptune: three more dwarf planets (J2000 elements: a, e, i, node, perihelion, mean longitude).
        add(planet("haumea", "Haumea", Kind.DWARF_PLANET, 43.116, 0.1949, 28.21, 122.17, 1.21, 191.5)
                .size(780, 0.000671 * EARTH_MU).spin(0.1631, 0).shape(2322, 1704, 1026)
                .looks(0xDCE2E6, 0.80).rings(2252, 2322, 0x9C9488, 0.45).landable("stevespace:haumea"));
        add(planet("makemake", "Makemake", Kind.DWARF_PLANET, 45.430, 0.1610, 28.98, 79.62, 14.45, 154.05)
                .size(715, 0.000519 * EARTH_MU).spin(0.951, 0)
                .looks(0xD4B8A0, 0.82).landable("stevespace:makemake"));
        add(planet("eris", "Eris", Kind.DWARF_PLANET, 67.864, 0.4361, 44.04, 35.95, 186.93, 18.7)
                .size(1_163, 0.002757 * EARTH_MU).spin(15.786, 0)
                .looks(0xEEECE8, 0.96).landable("stevespace:eris"));
    }

    // ---------------------------------------------------------------------------------------
    // Building the table
    // ---------------------------------------------------------------------------------------

    private CelestialBody.Builder planet(String id, String name, Kind kind, double aAU, double e, double iDeg,
                                         double nodeDeg, double perihelionDeg, double meanLongitudeDeg) {
        return CelestialBody.builder(id, name, kind)
                .orbits(sun, OrbitalElements.fromPlanetTable(aAU * AU, e, iDeg, nodeDeg, perihelionDeg, meanLongitudeDeg));
    }

    private static CelestialBody.Builder moon(String id, String name, CelestialBody parent, double a, double e, double iDeg,
                                              double nodeDeg, double periapsisDeg, double meanAnomalyDeg) {
        OrbitalElements orbit = new OrbitalElements(a, e, Math.toRadians(iDeg), Math.toRadians(nodeDeg),
                Math.toRadians(periapsisDeg), Math.toRadians(meanAnomalyDeg), 0);
        return CelestialBody.builder(id, name, Kind.MOON).orbits(parent, orbit);
    }

    private CelestialBody add(CelestialBody.Builder builder) {
        CelestialBody body = builder.build();
        bodies.put(body.id(), body);
        children.put(body, new ArrayList<>());
        if (body.parent() != null) {
            children.get(body.parent()).add(body);
        }
        return body;
    }

    // ---------------------------------------------------------------------------------------
    // Lookup
    // ---------------------------------------------------------------------------------------

    public CelestialBody sun() {
        return sun;
    }

    public CelestialBody earth() {
        return earth;
    }

    public CelestialBody body(String id) {
        return bodies.get(id);
    }

    public Iterable<CelestialBody> bodies() {
        return Collections.unmodifiableCollection(bodies.values());
    }

    /** The bodies going round this one that are on the charts. */
    public List<CelestialBody> childrenOf(CelestialBody body) {
        List<CelestialBody> all = children.get(body);
        for (CelestialBody c : all) {
            if (c.hidden()) {
                return all.stream().filter(b -> !b.hidden()).toList();
            }
        }
        return Collections.unmodifiableList(all);
    }

    /** Every body within this one's reach, charted or not: what a craft actually meets. */
    public List<CelestialBody> allChildrenOf(CelestialBody body) {
        return Collections.unmodifiableList(children.get(body));
    }

    /**
     * The neighbours a craft near this body might rather belong to, sharing its parent: for an
     * ordinary body, the ones keeping no orbit that come right up to it (Acheron by the Moon); for
     * one of those, the ordinary bodies it passes. A craft belongs to whichever of the two it is
     * nearer to, within that one's sphere of influence (see {@link #handOver}).
     */
    public List<CelestialBody> visitorsOf(CelestialBody body) {
        if (body.parent() == null) {
            return List.of();
        }
        boolean wandering = body.course() != null;
        return children.get(body.parent()).stream()
                .filter(b -> b != body && (wandering ? b.course() == null : b.course() != null)).toList();
    }

    /**
     * Whether a craft at {@code fromParent} (relative to the shared parent) now belongs to
     * {@code other} instead of {@code current}: inside other's sphere of influence and nearer its
     * centre than current's. (Acheron's sphere reaches past the Moon's middle on its pass: without
     * the nearer-to rule a ship low over the Moon would be counted as Acheron's.)
     */
    public boolean handOver(CelestialBody current, CelestialBody other, DVec3 fromParent, double t) {
        double toOther = fromParent.sub(stateRelativeToParent(other, t).position()).length();
        if (toOther >= sphereOfInfluence(other)) {
            return false;
        }
        double toCurrent = fromParent.sub(stateRelativeToParent(current, t).position()).length();
        return toOther < toCurrent;
    }

    /** The body whose surface dimension has this id, or null. */
    public CelestialBody bodyForDimension(String dimensionId) {
        for (CelestialBody body : bodies.values()) {
            if (dimensionId.equals(body.surfaceDimension())) {
                return body;
            }
        }
        return null;
    }

    public double earthDaySeconds() {
        return earthDaySeconds;
    }

    /** How many times Earth has turned on its axis (sidereal rotations) by time {@code t}. */
    public double earthTurns(double t) {
        return rotationAnchorTurns + (t - rotationAnchorTime) / siderealPeriod(earth);
    }

    // ---------------------------------------------------------------------------------------
    // Positions
    // ---------------------------------------------------------------------------------------

    /** Position and velocity relative to the parent body at time {@code t}. */
    public StateVector stateRelativeToParent(CelestialBody body, double t) {
        if (body.parent() == null) {
            return new StateVector(DVec3.ZERO, DVec3.ZERO);
        }
        if (body.course() != null) {
            return body.course().stateAt(t);
        }
        return body.orbit().stateAt(t, body.parent().mu() + body.mu());
    }

    /** Position and velocity relative to the Sun. */
    public StateVector absoluteState(CelestialBody body, double t) {
        StateVector state = new StateVector(DVec3.ZERO, DVec3.ZERO);
        for (CelestialBody b = body; b.parent() != null; b = b.parent()) {
            state = state.add(stateRelativeToParent(b, t));
        }
        return state;
    }

    public DVec3 absolutePosition(CelestialBody body, double t) {
        return absoluteState(body, t).position();
    }

    /**
     * Radius of the body's sphere of influence: inside it, its gravity dominates and trajectories
     * are computed around it (patched conics).
     */
    public double sphereOfInfluence(CelestialBody body) {
        if (body.parent() == null) {
            return Double.POSITIVE_INFINITY;
        }
        if (body.fixedSoi() > 0) {
            return body.fixedSoi();
        }
        return body.orbit().semiMajorAxis() * Math.pow(body.mu() / body.parent().mu(), 0.4);
    }

    // ---------------------------------------------------------------------------------------
    // Rotation
    // ---------------------------------------------------------------------------------------

    /** Length of Earth's year in game seconds (from its orbit). */
    public double earthYearSeconds() {
        return earth.orbit().period(sun.mu() + earth.mu());
    }

    /**
     * Sidereal rotation period of a body in game seconds (negative = retrograde). Earth's is set
     * so that its solar day (sunrise to sunrise) is exactly the configured day length.
     */
    public double siderealPeriod(CelestialBody body) {
        double earthSidereal = earthDaySeconds / (1 + earthDaySeconds / earthYearSeconds());
        if (body == earth) {
            return earthSidereal;
        }
        return earthSidereal * body.siderealDays() / EARTH_SIDEREAL_RATIO;
    }

    /**
     * The body's orientation at time {@code t}: where its prime meridian (longitude 0) points, plus
     * its north pole. Tidally locked moons keep longitude 0 facing their planet.
     */
    public BodyFrame frameAt(CelestialBody body, double t) {
        DVec3 north = body.spinAxis();
        DVec3 reference = DVec3.Z.cross(north);
        if (reference.length() < 1e-6) {
            reference = DVec3.X;
        }
        reference = reference.normalize();
        DVec3 ninety = north.cross(reference);

        double angle;
        if (body.isTidallyLocked() && body.parent() != null) {
            DVec3 toParent = stateRelativeToParent(body, t).position().scale(-1);
            angle = Math.atan2(toParent.dot(ninety), toParent.dot(reference));
        } else {
            // Every body turns in step with Earth at its real ratio.
            angle = 2 * Math.PI * earthTurns(t) * siderealPeriod(earth) / siderealPeriod(body);
        }
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        DVec3 primeMeridian = reference.scale(cos).add(ninety.scale(sin));
        DVec3 east90 = north.cross(primeMeridian);
        return new BodyFrame(primeMeridian, east90, north);
    }

    /**
     * Orientation of a rotating body. A point at latitude φ, longitude λ on the surface lies in
     * direction {@code cosφ·cosλ·meridian + cosφ·sinλ·east90 + sinφ·north}.
     */
    public record BodyFrame(DVec3 meridian, DVec3 east90, DVec3 north) {

        /** Unit vector from the centre to the surface point at (latitude, longitude). */
        public DVec3 up(double latitude, double longitude) {
            double cosLat = Math.cos(latitude);
            return meridian.scale(cosLat * Math.cos(longitude))
                    .add(east90.scale(cosLat * Math.sin(longitude)))
                    .add(north.scale(Math.sin(latitude)));
        }

        /** Local "east" (direction of +X in Minecraft) at the surface point. */
        public DVec3 east(double longitude) {
            return meridian.scale(-Math.sin(longitude)).add(east90.scale(Math.cos(longitude)));
        }

        /** Local "north" (direction of -Z in Minecraft) at the surface point. */
        public DVec3 northAt(double latitude, double longitude) {
            double sinLat = Math.sin(latitude);
            return meridian.scale(-sinLat * Math.cos(longitude))
                    .add(east90.scale(-sinLat * Math.sin(longitude)))
                    .add(north.scale(Math.cos(latitude)));
        }
    }
}
