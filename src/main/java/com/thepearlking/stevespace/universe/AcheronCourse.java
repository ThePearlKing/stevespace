package com.thepearlking.stevespace.universe;

/**
 * Where Acheron is. It keeps to no orbit. Now and then it comes in from far outside the solar
 * system, slowing as it nears, slips past the Moon close enough almost to touch it, and goes back
 * out the way nothing natural would, faster and faster, curling away until it's far past the
 * planets. Then nothing, for a long time.
 *
 * <p>Everything is relative to Earth. The pass is set by one number, the moment it is closest to
 * the Moon ({@link #pass}); the server decides it and tells every player. Its path lies in the
 * plane square to the Moon's direction at that moment, just outside the Moon's distance, so it can
 * never reach the Moon or Earth.
 */
public final class AcheronCourse implements CelestialBody.Course {

    public static final double RADIUS = 1_737;
    /** Its centre's closest distance from the Moon's centre: 900 km between the two surfaces. */
    public static final double GAP = RADIUS + 1_737 + 900;
    /** Speed at the closest point, km/s, against the Moon's own way round: slow, it lingers for days. */
    static final double SPEED = 0.3;
    /** How quickly it speeds up going out (and slows coming in), seconds. */
    static final double TAU = 86_400;
    /** Where it stops speeding up (in units of TAU): about 130 AU out. */
    static final double FAR = 14.2;
    /** How much its path curls as it goes, radians per TAU. */
    static final double CURL = 0.55;
    /** Seconds either side of the pass it keeps pace with the Moon (the encounter). */
    static final double HOLD = 43_200;
    /** Seconds over which it catches up with the Moon, and later lets go of it. */
    static final double FADE = 3_600;
    /** Seconds before its closest pass that it's still well out but on its way in, close enough to see coming. */
    public static final double LEAD = HOLD + 3 * FADE;
    /** Seconds after the pass when it's gone. */
    public static final double GONE = FAR * TAU;

    /** Universe time of the pass it's on (or the next one); NaN when there's none. */
    private static volatile double pass = Double.NaN;

    private final SolarSystem system;

    AcheronCourse(SolarSystem system) {
        this.system = system;
    }

    public static double pass() {
        return pass;
    }

    public static void setPass(double t) {
        pass = t;
    }

    @Override
    public StateVector stateAt(double t) {
        DVec3 here = positionAt(t);
        double h = 10;
        DVec3 velocity = positionAt(t + h).sub(positionAt(t - h)).scale(1 / (2 * h));
        return new StateVector(here, velocity);
    }

    private DVec3 positionAt(double t) {
        double p = pass;
        CelestialBody moon = system.body("moon");
        if (Double.isNaN(p) || moon == null) {
            // No pass coming: far out past the planets, off the plane they keep to.
            return new DVec3(0.3, 0.2, 1).normalize().scale(SPEED * TAU * Math.sinh(FAR));
        }
        double mu = system.earth().mu() + moon.mu();
        double x = Math.max(-FAR, Math.min(FAR, (t - p) / TAU));
        // Its path is laid out from the Moon, and for the encounter from where the Moon is right
        // then: it keeps pace with the Moon, hanging beside it the way nothing falling past ever
        // would. Before and after, from where the Moon was as it caught up or let go.
        return path(moon.orbit().stateAt(p + frameTime(t - p), mu), x);
    }

    /**
     * Time (from the pass) of the Moon it is laid out from: the same as the time for the
     * encounter, easing to a stop either side of it.
     */
    private static double frameTime(double dt) {
        double u = Math.abs(dt);
        double s = 0.5 * (u - FADE * (logCosh((u - HOLD) / FADE) - logCosh(-HOLD / FADE)));
        return Math.signum(dt) * s;
    }

    private static double logCosh(double z) {
        double a = Math.abs(z);
        return a + Math.log1p(Math.exp(-2 * a)) - Math.log(2);
    }

    /** True during an encounter: while it's anywhere round Earth and the Moon, not out on its long way. */
    public static boolean encounter(SolarSystem system, double t) {
        CelestialBody acheron = system.body("acheron");
        return !Double.isNaN(pass) && acheron != null
                && system.stateRelativeToParent(acheron, t).position().length() < system.sphereOfInfluence(system.earth());
    }

    /** Where it is {@code x} (in units of TAU) from its pass, the pass laid out from the Moon at {@code m}. */
    private static DVec3 path(StateVector m, double x) {
        DVec3 out = m.position().normalize();
        DVec3 along = m.velocity().sub(out.scale(m.velocity().dot(out))).normalize().scale(-1);
        DVec3 side = out.cross(along);
        DVec3 closest = m.position().add(out.scale(GAP));
        double distance = SPEED * TAU * Math.sinh(x);
        // Leaving square to the Moon's path (so it's never in the Moon's way round), curling
        // as it goes, a different way in than out.
        double angle = CURL * x * Math.abs(x);
        DVec3 dir = side.scale(Math.cos(angle)).add(along.scale(Math.sin(angle)));
        // And leaning outward the further it is, never toward Earth or the Moon.
        double lean = 0.35 * Math.abs(x) / FAR * Math.tanh(Math.abs(x) / 4);
        return closest.add(dir.scale(distance)).add(out.scale(Math.abs(distance) * lean));
    }
}
