package com.thepearlking.stevespace.vessel;

import com.thepearlking.stevespace.universe.CelestialBody;
import com.thepearlking.stevespace.universe.DVec3;
import com.thepearlking.stevespace.universe.Kepler;
import com.thepearlking.stevespace.universe.OrbitalElements;
import com.thepearlking.stevespace.universe.SolarSystem;
import com.thepearlking.stevespace.universe.StateVector;
import com.thepearlking.stevespace.universe.TrajectoryPredictor;

import java.util.List;

/**
 * Plans a single prograde burn that takes a vessel from its orbit to another body: out to a moon
 * (arriving a set height above it), or from a moon back down to its planet (arriving low enough
 * to enter the air). It tries departure times over the next while and burn sizes around the
 * Hohmann estimate, predicting each with {@link TrajectoryPredictor}, and keeps the earliest
 * departure that arrives where it should. Pure maths: safe to run off the server thread.
 */
public final class TransferPlanner {

    /** A planned burn: start time, prograde Δv, and the predicted arrival height (blocks). */
    public record Plan(double burnTime, double deltaV, double arrivalAltitude) {
    }

    private TransferPlanner() {
    }

    /**
     * Out from a planet's orbit to one of its moons.
     *
     * @param state  vessel state relative to {@code from} at {@code t0}
     * @param target arrival periapsis height above the moon's surface
     */
    public static Plan toMoon(SolarSystem system, CelestialBody from, CelestialBody moon, StateVector state, double t0,
                              double gravityScale, double target) {
        double mu = from.mu() * gravityScale;
        OrbitalElements orbit = Kepler.elementsFromState(state, mu, t0);
        double period = orbit.eccentricity() < 1 ? orbit.period(mu) : 600;
        double moonDistance = system.stateRelativeToParent(moon, t0).position().length();
        double moonPeriod = moon.orbit().period(from.mu() + moon.mu());
        double step = Math.max(5, period / 24);
        Plan best = null;
        for (double t = t0 + 45; t < t0 + moonPeriod && best == null; t += step) {
            StateVector s = orbit.stateAt(t, mu);
            double r = s.position().length();
            double a = (r + moonDistance) / 2;
            double needed = Math.sqrt(mu * (2 / r - 1 / a));
            double dv0 = needed - s.velocity().length();
            // A first quick check at the Hohmann size; only promising times are refined.
            Double first = arrival(system, from, moon, s, t, dv0, gravityScale);
            if (first == null) {
                continue;
            }
            best = refine(system, from, moon, s, t, dv0, gravityScale, target, 8);
        }
        return best;
    }

    /** Down from a moon's orbit to its planet, arriving at {@code target} height (inside the air). */
    public static Plan toPlanet(SolarSystem system, CelestialBody moon, StateVector state, double t0, double gravityScale,
                                double target) {
        CelestialBody planet = moon.parent();
        double mu = moon.mu() * gravityScale;
        OrbitalElements orbit = Kepler.elementsFromState(state, mu, t0);
        double period = orbit.eccentricity() < 1 ? orbit.period(mu) : 600;
        double step = Math.max(2, period / 72);
        Plan best = null;
        double bestError = Double.POSITIVE_INFINITY;
        for (double t = t0 + 30; t < t0 + period + 30; t += step) {
            StateVector s = orbit.stateAt(t, mu);
            double r = s.position().length();
            double escape = Math.sqrt(2 * mu / r);
            double v = s.velocity().length();
            for (double dv = escape - v + 1; dv < escape - v + 60; dv += 1) {
                Double height = arrival(system, moon, planet, s, t, dv, gravityScale);
                if (height == null) {
                    continue;
                }
                double error = Math.abs(height - target);
                if (error < bestError) {
                    bestError = error;
                    best = new Plan(t, dv, height);
                }
            }
        }
        if (best == null) {
            return null;
        }
        StateVector s = orbit.stateAt(best.burnTime(), mu);
        return refine(system, moon, planet, s, best.burnTime(), best.deltaV(), gravityScale, target, 2);
    }

    /** Fine search over the burn size around {@code dv0}, aiming at {@code target}. */
    private static Plan refine(SolarSystem system, CelestialBody from, CelestialBody to, StateVector s, double t,
                               double dv0, double gravityScale, double target, double range) {
        Plan best = null;
        double bestError = Double.POSITIVE_INFINITY;
        for (double dv = dv0 - range; dv <= dv0 + range; dv += range / 40) {
            Double height = arrival(system, from, to, s, t, dv, gravityScale);
            if (height == null) {
                continue;
            }
            double error = Math.abs(height - target);
            if (error < bestError) {
                bestError = error;
                best = new Plan(t, dv, height);
            }
        }
        // Good enough when it doesn't hit the ground and isn't absurdly high.
        if (best == null || best.arrivalAltitude() < Math.min(target * 0.25, 300) - 1e-9 && to.parent() == from) {
            return null;
        }
        return best;
    }

    /**
     * A small correction on the way: the burn (ecliptic-frame Δv vector) that best brings the
     * arrival height over {@code to} to {@code target}, searching along the direction of travel
     * and across the orbit plane. Null if nothing reaches it.
     */
    public static DVec3 correction(SolarSystem system, CelestialBody from, CelestialBody to, StateVector s, double t,
                                   double gravityScale, double target) {
        DVec3 prograde = s.velocity().normalize();
        DVec3 normal = s.position().cross(s.velocity()).normalize();
        DVec3 best = null;
        double bestError = Double.POSITIVE_INFINITY;
        double centreA = 0;
        double centreB = 0;
        double[][] passes = {{25, 1}, {1.5, 0.1}};
        for (double[] pass : passes) {
            double range = pass[0];
            double step = pass[1];
            double ca = centreA;
            double cb = centreB;
            for (double a = ca - range; a <= ca + range + 1e-9; a += step) {
                for (double b = cb - range; b <= cb + range + 1e-9; b += step) {
                    DVec3 dv = prograde.scale(a).add(normal.scale(b));
                    Double height = arrivalWith(system, from, to, s, t, dv, gravityScale);
                    if (height == null) {
                        continue;
                    }
                    // Prefer the right height, then the smallest burn.
                    double error = Math.abs(height - target) + dv.length() * 2;
                    if (error < bestError) {
                        bestError = error;
                        best = dv;
                        centreA = a;
                        centreB = b;
                    }
                }
            }
            if (best == null) {
                return null;
            }
        }
        return best;
    }

    /**
     * Where a prograde burn of {@code dv} at time {@code t} ends up: the periapsis height over
     * {@code to}, or null if the path never gets there.
     */
    static Double arrival(SolarSystem system, CelestialBody from, CelestialBody to, StateVector s, double t, double dv,
                          double gravityScale) {
        return arrivalWith(system, from, to, s, t, s.velocity().normalize().scale(dv), gravityScale);
    }

    /** Like {@link #arrival} for any burn vector (ecliptic frame); a zero vector checks the current path. */
    public static Double arrivalWith(SolarSystem system, CelestialBody from, CelestialBody to, StateVector s, double t,
                                     DVec3 dv, double gravityScale) {
        StateVector after = new StateVector(s.position(), s.velocity().add(dv));
        List<TrajectoryPredictor.Segment> path = TrajectoryPredictor.predict(system, from, after, t, gravityScale, 3 * 86400);
        for (int i = 1; i < path.size(); i++) {
            TrajectoryPredictor.Segment segment = path.get(i);
            if (segment.body().id().equals(to.id())) {
                // Only an arrival if it comes in toward the body: a path already heading away has
                // its closest point behind it and would sail on past.
                var at = segment.orbit().stateAt(segment.startTime(), to.mu() * gravityScale);
                if (at.position().dot(at.velocity()) >= 0) {
                    return null;
                }
                return segment.orbit().periapsis() - to.radius();
            }
        }
        return null;
    }
}
