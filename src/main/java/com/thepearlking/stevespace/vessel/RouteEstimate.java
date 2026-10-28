package com.thepearlking.stevespace.vessel;

import com.thepearlking.stevespace.universe.CelestialBody;
import com.thepearlking.stevespace.universe.DVec3;
import com.thepearlking.stevespace.universe.SolarSystem;
import com.thepearlking.stevespace.universe.StateVector;

/**
 * Roughly what a trip costs, worked out along the same legs the autopilot flies (out of a moon's
 * pull, across to another planet at the next good window, down to a moon, and the landing):
 * the Δv and the time until arrival. For the destination list; pure maths, run off-thread.
 */
public final class RouteEstimate {

    /** Δv in blocks per second, time in seconds from now; Δv is infinite when there's no way. */
    public record Estimate(double deltaV, double seconds) {
    }

    private RouteEstimate() {
    }

    /**
     * @param ship the ship's state around {@code here} at {@code t}, or null when on the ground
     *             (a climb to a low orbit is counted then)
     */
    public static Estimate of(SolarSystem system, CelestialBody here, StateVector ship, double t, CelestialBody end,
                              boolean land, double gravityScale) {
        double dv = 0;
        double now = t;
        CelestialBody body = here;
        StateVector state = ship;
        if (state == null) {
            double r = body.radius() + Autopilot.parkingAltitude(body);
            double circular = Math.sqrt(body.mu() * gravityScale / r);
            dv += circular * (body.hasAtmosphere() ? 1.35 : 1.1);
            state = circularOrbit(body, r, gravityScale);
        }
        if (end.parent() == null) {
            // A solar orbit: just climb out of the planet's pull (and a moon's, first).
            double v = state.velocity().length();
            double r = state.position().length();
            double mu0 = body.mu() * gravityScale;
            dv += Math.max(0, Math.sqrt(2 * mu0 / r) * 1.04 - v);
            if (body.parent() != null && !body.parent().id().equals(end.id())) {
                CelestialBody planet = body.parent();
                double pr = system.stateRelativeToParent(body, now).position().length();
                dv += Math.sqrt(2 * planet.mu() * gravityScale / pr) * 0.45;
            }
            double soi = body.parent() == null ? 0 : system.sphereOfInfluence(body);
            // Then down from the planet's distance to a close orbit of the Sun (half an ellipse).
            CelestialBody planet = body;
            while (planet.parent() != null && !planet.parent().id().equals(end.id())) {
                planet = planet.parent();
            }
            double muSun = end.mu() * gravityScale;
            double from = planet.parent() == null ? r : system.stateRelativeToParent(planet, now).position().length();
            double to = end.radius() * Autopilot.SUN_ORBIT;
            double a = (from + to) / 2;
            dv += Math.sqrt(muSun / from) - Math.sqrt(muSun * (2 / from - 1 / a));
            dv += Math.sqrt(muSun * (2 / to - 1 / a)) - Math.sqrt(muSun / to);
            double fall = Math.PI * Math.sqrt(a * a * a / muSun);
            return new Estimate(dv, soi / Math.max(1, v * 0.3) + fall);
        }
        for (int legs = 0; legs < 6; legs++) {
            double mu = body.mu() * gravityScale;
            if (body.id().equals(end.id())) {
                if (land) {
                    double v = Math.sqrt(mu / (body.radius() + Autopilot.parkingAltitude(body)));
                    dv += body.hasAtmosphere() ? v * 0.15 : v * 1.05;
                }
                return new Estimate(dv, now - t);
            }
            CelestialBody down = Autopilot.childToward(body, end);
            if (down != null) {
                double arrive = down.radius() + Autopilot.moonArrival(system, down);
                var leg = InterplanetaryPlanner.toMoon(system, body, down, state, now, arrive, gravityScale);
                if (leg == null) {
                    return new Estimate(Double.POSITIVE_INFINITY, 0);
                }
                dv += leg[1].deltaV().x();
                now = leg[1].time();
                body = down;
                state = circularOrbit(body, arrive, gravityScale);
                continue;
            }
            CelestialBody sun = system.sun();
            if (body.parent() != null && !body.parent().id().equals(sun.id())) {
                // Out of a moon's pull: a little past escape speed.
                double r = state.position().length();
                dv += Math.max(0, Math.sqrt(2 * mu / r) * 1.04 - state.velocity().length());
                CelestialBody parent = body.parent();
                StateVector moon = system.stateRelativeToParent(body, now);
                state = moon;
                now += Math.PI * Math.sqrt(Math.pow(system.sphereOfInfluence(body), 3) / mu) * 0.3;
                body = parent;
                if (parent.id().equals(end.id())) {
                    // Then down to a low orbit (or into the air) around the planet.
                    double rp = parent.radius() + Autopilot.parkingAltitude(parent);
                    double ra = moon.position().length();
                    double pmu = parent.mu() * gravityScale;
                    double a = (ra + rp) / 2;
                    double atApo = Math.sqrt(pmu * (2 / ra - 1 / a));
                    dv += Math.abs(moon.velocity().length() - atApo);
                    double atPeri = Math.sqrt(pmu * (2 / rp - 1 / a));
                    dv += land && parent.hasAtmosphere() ? 0 : atPeri - Math.sqrt(pmu / rp);
                    now += Math.PI * Math.sqrt(a * a * a / pmu);
                    state = circularOrbit(parent, rp, gravityScale);
                    if (land && parent.hasAtmosphere()) {
                        return new Estimate(dv + Math.sqrt(pmu / rp) * 0.15, now - t);
                    }
                }
                continue;
            }
            CelestialBody across = end;
            while (across.parent() != null && !across.parent().id().equals(sun.id())) {
                across = across.parent();
            }
            double arrive = across.radius() + Autopilot.planetArrival(across, end);
            var transfer = InterplanetaryPlanner.porkchop(system, body, across, now + 120, state, arrive, gravityScale);
            if (transfer == null) {
                return new Estimate(Double.POSITIVE_INFINITY, 0);
            }
            dv += transfer.deltaV();
            now = transfer.arriveTime();
            body = across;
            state = circularOrbit(body, arrive, gravityScale);
        }
        return new Estimate(Double.POSITIVE_INFINITY, 0);
    }

    /** A round orbit at radius r over the body's equator. */
    private static StateVector circularOrbit(CelestialBody body, double r, double gravityScale) {
        DVec3 axis = body.spinAxis();
        DVec3 out = axis.cross(DVec3.X).length() > 0.1 ? axis.cross(DVec3.X).normalize() : axis.cross(DVec3.Y).normalize();
        return new StateVector(out.scale(r), axis.cross(out).normalize().scale(Math.sqrt(body.mu() * gravityScale / r)));
    }
}
