package com.thepearlking.stevespace.vessel;

import com.thepearlking.stevespace.universe.DVec3;
import com.thepearlking.stevespace.universe.Kepler;
import com.thepearlking.stevespace.universe.Lambert;
import com.thepearlking.stevespace.universe.OrbitalElements;
import com.thepearlking.stevespace.universe.StateVector;

/**
 * Meeting another ship in orbit round the same body: when to burn, and how, to arrive where it
 * will be. Tries every departure over the next few orbits with transfers shorter than an orbit
 * (Lambert) and ones that go round once or twice more first (phasing: the usual way to catch a
 * ship just ahead in the same orbit), and keeps the cheapest that stays clear of the air.
 */
public final class RendezvousPlanner {

    /**
     * A plan: burn {@code deltaV} (inertial frame) at {@code departTime}, arriving next to the
     * target at {@code arriveTime} with {@code arrivalSpeed} still to cancel.
     */
    public record Plan(double departTime, DVec3 deltaV, double arriveTime, double arrivalSpeed) {
        public double total() {
            return deltaV.length() + arrivalSpeed;
        }
    }

    private RendezvousPlanner() {
    }

    /**
     * @param own    the ship's orbit at {@code t0}
     * @param target the target's orbit at {@code tTarget}
     * @param floor  lowest safe distance from the body's centre (top of the air)
     */
    public static Plan plan(StateVector own, double t0, StateVector target, double tTarget, double mu, double floor) {
        OrbitalElements ownOrbit = Kepler.elementsFromState(own, mu, t0);
        OrbitalElements targetOrbit = Kepler.elementsFromState(target, mu, tTarget);
        double period = ownOrbit.eccentricity() < 1 ? ownOrbit.period(mu) : 3600;
        double targetPeriod = targetOrbit.eccentricity() < 1 ? targetOrbit.period(mu) : period;
        DVec3 normal = own.position().cross(own.velocity()).normalize();
        Plan best = null;
        int departures = 48;
        double reach = 2.5 * period; // wait up to a couple of orbits for a good moment
        for (int i = 0; i < departures; i++) {
            double depart = t0 + 20 + reach * i / departures;
            StateVector from = ownOrbit.stateAt(depart, mu);
            // Straight there, in less than one orbit of the target.
            for (int j = 1; j <= 24; j++) {
                double tof = targetPeriod * (0.05 + 0.9 * j / 24.0);
                best = better(best, attempt(from, depart, tof, targetOrbit, mu, floor, normal, null));
            }
            // Round once or twice more first: aim for an orbit with the right period, then refine.
            for (int laps = 1; laps <= 2; laps++) {
                for (int j = -4; j <= 4; j++) {
                    double tof = targetPeriod * (laps + 0.12 * j);
                    if (tof <= 0) {
                        continue;
                    }
                    double wanted = tof / laps;
                    // A tangential burn giving that period (vis-viva).
                    double r = from.position().length();
                    double a = Math.cbrt(mu * Math.pow(wanted / (2 * Math.PI), 2));
                    double v2 = mu * (2 / r - 1 / a);
                    if (v2 <= 0) {
                        continue;
                    }
                    DVec3 guess = from.velocity().normalize().scale(Math.sqrt(v2));
                    best = better(best, attempt(from, depart, tof, targetOrbit, mu, floor, normal, guess));
                }
            }
        }
        return best;
    }

    private static Plan better(Plan best, Plan candidate) {
        if (candidate == null) {
            return best;
        }
        // A little extra Δv is worth it for a much earlier arrival.
        double score = candidate.total() + (candidate.arriveTime() - candidate.departTime()) * 0.002;
        double bestScore = best == null ? Double.MAX_VALUE : best.total() + (best.arriveTime() - best.departTime()) * 0.002;
        return score < bestScore ? candidate : best;
    }

    /** One transfer leaving {@code from} at {@code depart}, reaching the target after {@code tof}. */
    private static Plan attempt(StateVector from, double depart, double tof, OrbitalElements targetOrbit, double mu, double floor,
                                DVec3 normal, DVec3 guess) {
        double arrive = depart + tof;
        StateVector there = targetOrbit.stateAt(arrive, mu);
        DVec3 v1;
        if (guess == null) {
            Lambert.Solution s = Lambert.solve(from.position(), there.position(), tof, mu, normal);
            if (s == null) {
                return null;
            }
            v1 = s.v1();
        } else {
            v1 = shoot(from.position(), guess, tof, there.position(), mu);
            if (v1 == null) {
                return null;
            }
        }
        StateVector start = new StateVector(from.position(), v1);
        OrbitalElements transfer = Kepler.elementsFromState(start, mu, depart);
        if (transfer.eccentricity() >= 1 || transfer.periapsis() < floor) {
            return null; // would leave, or dip into the air
        }
        StateVector end = transfer.stateAt(arrive, mu);
        if (end.position().sub(there.position()).length() > 5) {
            return null;
        }
        return new Plan(depart, v1.sub(from.velocity()), arrive, end.velocity().sub(there.velocity()).length());
    }

    /**
     * Finds the departure velocity from {@code r1} that reaches {@code r2} after {@code tof},
     * starting from a guess (Newton's method on the miss, any number of laps). Null if it won't settle.
     */
    static DVec3 shoot(DVec3 r1, DVec3 guess, double tof, DVec3 r2, double mu) {
        DVec3 v = guess;
        for (int iteration = 0; iteration < 12; iteration++) {
            DVec3 miss = land(r1, v, tof, mu);
            if (miss == null) {
                return null;
            }
            miss = miss.sub(r2);
            if (miss.length() < 0.5) {
                return v;
            }
            // How the arrival point moves with each component of the departure velocity.
            double h = 0.01;
            DVec3[] columns = new DVec3[3];
            DVec3 base = miss.add(r2);
            DVec3[] axes = {new DVec3(h, 0, 0), new DVec3(0, h, 0), new DVec3(0, 0, h)};
            for (int k = 0; k < 3; k++) {
                DVec3 moved = land(r1, v.add(axes[k]), tof, mu);
                if (moved == null) {
                    return null;
                }
                columns[k] = moved.sub(base).scale(1 / h);
            }
            DVec3 step = solve3(columns, miss.scale(-1));
            if (step == null) {
                return null;
            }
            // Don't jump wildly far from the guess.
            double most = Math.max(5, v.length() * 0.2);
            if (step.length() > most) {
                step = step.scale(most / step.length());
            }
            v = v.add(step);
        }
        return null;
    }

    /** Where a ship leaving {@code r} at {@code v} is after {@code dt} (null if it escapes). */
    private static DVec3 land(DVec3 r, DVec3 v, double dt, double mu) {
        OrbitalElements e = Kepler.elementsFromState(new StateVector(r, v), mu, 0);
        if (e.eccentricity() >= 1) {
            return null;
        }
        return e.stateAt(dt, mu).position();
    }

    /** Solves [c0 c1 c2] x = b (Cramer's rule), or null if it's singular. */
    private static DVec3 solve3(DVec3[] c, DVec3 b) {
        double det = c[0].dot(c[1].cross(c[2]));
        if (Math.abs(det) < 1e-12) {
            return null;
        }
        return new DVec3(b.dot(c[1].cross(c[2])) / det, c[0].dot(b.cross(c[2])) / det, c[0].dot(c[1].cross(b)) / det);
    }

    /**
     * Mid-way: the change of velocity that puts the ship (at {@code own}, time {@code t}) on
     * course for {@code aim} at {@code arrive}. Null if there's no sensible answer.
     */
    public static DVec3 correction(StateVector own, double t, DVec3 aim, double arrive, double mu) {
        double tof = arrive - t;
        if (tof < 5) {
            return null;
        }
        DVec3 v = shoot(own.position(), own.velocity(), tof, aim, mu);
        if (v == null) {
            DVec3 normal = own.position().cross(own.velocity()).normalize();
            Lambert.Solution s = Lambert.solve(own.position(), aim, tof, mu, normal);
            v = s == null ? null : s.v1();
        }
        return v == null ? null : v.sub(own.velocity());
    }
}
