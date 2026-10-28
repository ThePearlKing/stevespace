package com.thepearlking.stevespace.vessel;

import com.thepearlking.stevespace.universe.CelestialBody;
import com.thepearlking.stevespace.universe.DVec3;
import com.thepearlking.stevespace.universe.Kepler;
import com.thepearlking.stevespace.universe.Lambert;
import com.thepearlking.stevespace.universe.OrbitalElements;
import com.thepearlking.stevespace.universe.SolarSystem;
import com.thepearlking.stevespace.universe.StateVector;

/**
 * Flights between planets (patched conics). First a porkchop search: Lambert transfers for many
 * departure dates and flight times, keeping the one needing the least Δv to leave the parking
 * orbit and to be captured at the far end. Then the departure burn itself: the point in the
 * parking orbit and the exact Δv vector whose escape hyperbola leaves along the planned
 * direction. On the way, {@link #aim} re-targets the arrival with a fresh Lambert solution.
 * Pure maths: safe off the server thread.
 */
public final class InterplanetaryPlanner {

    /**
     * A planned transfer: when to leave and arrive, the excess velocities (relative to each planet,
     * ecliptic frame) at either end, and the total Δv estimate.
     */
    public record Transfer(double departTime, double arriveTime, DVec3 vInfDepart, DVec3 vInfArrive, double deltaV) {
    }

    /** The departure burn: when (its middle) and the Δv vector (ecliptic frame). */
    public record Burn(double time, DVec3 deltaV) {
    }

    private InterplanetaryPlanner() {
    }

    /**
     * @param parkRadius     radius of the parking orbit at {@code from}
     * @param arriveRadius   wanted periapsis radius at {@code to}
     * @param earliest       the first moment the departure could be
     */
    public static Transfer porkchop(SolarSystem system, CelestialBody from, CelestialBody to, double earliest,
                                    StateVector ship, double arriveRadius, double gravityScale) {
        double parkRadius = ship.position().length();
        double muSun = system.sun().mu() * gravityScale;
        double muA = from.mu() * gravityScale;
        double muB = to.mu() * gravityScale;
        double rA = from.orbit().semiMajorAxis();
        double rB = to.orbit().semiMajorAxis();
        double periodA = from.orbit().period(system.sun().mu() + from.mu());
        double periodB = to.orbit().period(system.sun().mu() + to.mu());
        double synodic = 1 / Math.abs(1 / periodA - 1 / periodB);
        double window = Math.min(synodic, 3 * Math.max(periodA, periodB));
        double hohmann = Math.PI * Math.sqrt(Math.pow((rA + rB) / 2, 3) / muSun);
        DVec3 normal = system.stateRelativeToParent(from, earliest).position()
                .cross(system.stateRelativeToParent(from, earliest).velocity()).normalize();

        java.util.List<Transfer> cells = new java.util.ArrayList<>();
        int departures = 120;
        int flights = 30;
        for (int i = 0; i <= departures; i++) {
            double t1 = earliest + window * i / departures;
            for (int j = 0; j <= flights; j++) {
                double tof = hohmann * (0.45 + 1.1 * j / flights);
                Transfer t = evaluate(system, from, to, t1, tof, normal, muSun, muA, muB, parkRadius, arriveRadius);
                if (t != null) {
                    cells.add(t);
                }
            }
        }
        if (cells.isEmpty()) {
            return null;
        }
        // The estimate assumes the parking orbit lies in the right plane. Score the most promising
        // cells with the real departure burn from the ship's own orbit instead.
        cells.sort(java.util.Comparator.comparingDouble(Transfer::deltaV));
        Transfer best = null;
        double bestCost = Double.POSITIVE_INFINITY;
        for (Transfer t : cells.subList(0, Math.min(80, cells.size()))) {
            double cost = realCost(t, ship, earliest, muA, muB, arriveRadius);
            if (cost < bestCost) {
                bestCost = cost;
                best = t;
            }
        }
        if (best == null) {
            return null;
        }
        // Refine around the best cell.
        double dStep = window / departures;
        double fStep = hohmann * 1.1 / flights;
        Transfer coarse = best;
        for (int i = -10; i <= 10; i++) {
            for (int j = -10; j <= 10; j++) {
                double t1 = coarse.departTime() + dStep * i / 10;
                double tof = coarse.arriveTime() - coarse.departTime() + fStep * j / 10;
                if (t1 < earliest || tof <= 0) {
                    continue;
                }
                Transfer t = evaluate(system, from, to, t1, tof, normal, muSun, muA, muB, parkRadius, arriveRadius);
                if (t == null) {
                    continue;
                }
                double cost = realCost(t, ship, earliest, muA, muB, arriveRadius);
                if (cost < bestCost) {
                    bestCost = cost;
                    best = t;
                }
            }
        }
        return new Transfer(best.departTime(), best.arriveTime(), best.vInfDepart(), best.vInfArrive(), bestCost);
    }

    /** Δv of a transfer counting the real departure burn from the ship's orbit. */
    private static double realCost(Transfer t, StateVector ship, double t0, double muA, double muB, double arriveRadius) {
        Burn burn = departure(ship, t0, muA, t.vInfDepart(), t.departTime());
        if (burn == null) {
            return Double.POSITIVE_INFINITY;
        }
        double arrive = Math.sqrt(t.vInfArrive().lengthSquared() + 2 * muB / arriveRadius) - Math.sqrt(muB / arriveRadius);
        return burn.deltaV().length() + arrive;
    }

    private static Transfer evaluate(SolarSystem system, CelestialBody from, CelestialBody to, double t1, double tof, DVec3 normal,
                                     double muSun, double muA, double muB, double parkRadius, double arriveRadius) {
        StateVector a = system.stateRelativeToParent(from, t1);
        StateVector b = system.stateRelativeToParent(to, t1 + tof);
        Lambert.Solution s = Lambert.solve(a.position(), b.position(), tof, muSun, normal);
        if (s == null) {
            return null;
        }
        DVec3 vInf1 = s.v1().sub(a.velocity());
        DVec3 vInf2 = s.v2().sub(b.velocity());
        double leave = Math.sqrt(vInf1.lengthSquared() + 2 * muA / parkRadius) - Math.sqrt(muA / parkRadius);
        double arrive = Math.sqrt(vInf2.lengthSquared() + 2 * muB / arriveRadius) - Math.sqrt(muB / arriveRadius);
        return new Transfer(t1, t1 + tof, vInf1, vInf2, leave + arrive);
    }

    /**
     * The departure burn from the parking orbit ({@code state} relative to the planet at
     * {@code t0}) giving an escape whose excess velocity is {@code vInf}, near {@code around}.
     */
    public static Burn departure(StateVector state, double t0, double mu, DVec3 vInf, double around) {
        OrbitalElements orbit = Kepler.elementsFromState(state, mu, t0);
        double period = orbit.eccentricity() < 1 ? orbit.period(mu) : 3600;
        DVec3 u = vInf.normalize();
        double speed = vInf.length();
        Burn best = null;
        double bestCost = Double.POSITIVE_INFINITY;
        int steps = 180;
        for (int i = 0; i < steps; i++) {
            double tb = around - period / 2 + period * i / steps;
            if (tb < t0 + 60) {
                continue;
            }
            StateVector s = orbit.stateAt(tb, mu);
            DVec3 r = s.position();
            DVec3 rHat = r.normalize();
            double e = 1 + r.length() * speed * speed / mu;
            double limit = Math.acos(-1 / e);
            double angle = Math.acos(Math.max(-1, Math.min(1, rHat.dot(u))));
            if (Math.abs(angle - limit) > Math.toRadians(40)) {
                continue;
            }
            // First guess: the periapsis speed of the escape hyperbola, across the radius toward u.
            DVec3 across = u.sub(rHat.scale(rHat.dot(u)));
            if (across.length() < 1e-6) {
                continue;
            }
            double vp = Math.sqrt(speed * speed + 2 * mu / r.length());
            DVec3 dv = across.normalize().scale(vp).sub(s.velocity());
            dv = newton(r, s.velocity(), dv, vInf, mu);
            if (dv == null) {
                continue;
            }
            double cost = dv.length() + Math.abs(tb - around) * 1e-6;
            if (cost < bestCost) {
                bestCost = cost;
                best = new Burn(tb, dv);
            }
        }
        return best;
    }

    /** The burn still needed from (r, v) to leave with {@code target} excess velocity, or null. */
    public static DVec3 remaining(DVec3 r, DVec3 v, DVec3 guess, DVec3 target, double mu) {
        return newton(r, v, guess, target, mu);
    }

    /** Refines a burn so the escape leaves with exactly {@code target} excess velocity. */
    private static DVec3 newton(DVec3 r, DVec3 v, DVec3 dv, DVec3 target, double mu) {
        for (int iteration = 0; iteration < 12; iteration++) {
            DVec3 f = excess(r, v.add(dv), mu);
            if (f == null) {
                // Not escaping yet: push a little harder along the burn.
                dv = dv.scale(1.05);
                continue;
            }
            DVec3 error = f.sub(target);
            if (error.length() < target.length() * 1e-4) {
                return dv;
            }
            double h = Math.max(1e-3, dv.length() * 1e-4);
            double[][] jac = new double[3][3];
            DVec3[] axes = {DVec3.X, DVec3.Y, DVec3.Z};
            for (int k = 0; k < 3; k++) {
                DVec3 fk = excess(r, v.add(dv).add(axes[k].scale(h)), mu);
                if (fk == null) {
                    return null;
                }
                DVec3 d = fk.sub(f).scale(1 / h);
                jac[0][k] = d.x();
                jac[1][k] = d.y();
                jac[2][k] = d.z();
            }
            DVec3 step = solve3(jac, error);
            if (step == null) {
                return null;
            }
            dv = dv.sub(step);
        }
        DVec3 f = excess(r, v.add(dv), mu);
        return f != null && f.sub(target).length() < target.length() * 0.01 ? dv : null;
    }

    /** The excess velocity vector of the escape from (r, v), or null if it doesn't escape. */
    static DVec3 excess(DVec3 r, DVec3 v, double mu) {
        double energy = v.lengthSquared() / 2 - mu / r.length();
        if (energy <= 0) {
            return null;
        }
        DVec3 h = r.cross(v);
        DVec3 ev = r.scale(v.lengthSquared() - mu / r.length()).sub(v.scale(r.dot(v))).scale(1 / mu);
        double e = ev.length();
        if (e <= 1) {
            return null;
        }
        DVec3 eHat = ev.scale(1 / e);
        DVec3 pHat = h.normalize().cross(eHat);
        DVec3 u = eHat.scale(-1 / e).add(pHat.scale(Math.sqrt(1 - 1 / (e * e))));
        return u.scale(Math.sqrt(2 * energy));
    }

    /**
     * Out from a planet's orbit to one of its moons, directly: a Lambert arc from a point of the
     * ship's orbit to (beside) where the moon will be. Searches burn times over the next
     * ship/moon synodic period and flight times around the Hohmann one.
     *
     * @return {burn (middle time, Δv vector), arrival (time, unused)} or null
     */
    public static Burn[] toMoon(SolarSystem system, CelestialBody planet, CelestialBody moon, StateVector ship, double t0,
                                double arriveRadius, double gravityScale) {
        double mu = planet.mu() * gravityScale;
        double muM = moon.mu() * gravityScale;
        OrbitalElements orbit = Kepler.elementsFromState(ship, mu, t0);
        if (orbit.eccentricity() >= 1) {
            return null;
        }
        double shipPeriod = orbit.period(mu);
        double moonPeriod = moon.orbit().period(planet.mu() + moon.mu());
        double synodic = 1 / Math.abs(1 / shipPeriod - 1 / moonPeriod);
        double window = Math.min(synodic, 3 * moonPeriod) + shipPeriod;
        double r = ship.position().length();
        double moonR = moon.orbit().semiMajorAxis();
        double hohmann = Math.PI * Math.sqrt(Math.pow((r + moonR) / 2, 3) / mu);
        DVec3 normal = ship.position().cross(ship.velocity()).normalize();
        int burns = (int) Math.min(720, Math.max(72, window / shipPeriod * 36));
        double bestCost = Double.POSITIVE_INFINITY;
        double bestT1 = 0;
        double bestTof = 0;
        for (int pass = 0; pass < 2; pass++) {
            double centreT = bestT1;
            double centreTof = bestTof;
            for (int i = 0; i <= burns; i++) {
                double t1 = pass == 0 ? t0 + 60 + window * i / burns : centreT + (i - burns / 2.0) * (window / burns) * 2 / burns;
                if (t1 < t0 + 60) {
                    continue;
                }
                for (int j = 0; j <= 16; j++) {
                    double tof = pass == 0 ? hohmann * (0.55 + 0.9 * j / 16) : centreTof + (j - 8) * hohmann * 0.9 / 16 / 8;
                    if (tof <= 0) {
                        continue;
                    }
                    StateVector s = orbit.stateAt(t1, mu);
                    StateVector m = system.stateRelativeToParent(moon, t1 + tof);
                    Lambert.Solution sol = Lambert.solve(s.position(), m.position(), tof, mu, normal);
                    if (sol == null) {
                        continue;
                    }
                    double vInf2 = sol.v2().sub(m.velocity()).lengthSquared();
                    double cost = sol.v1().sub(s.velocity()).length()
                            + Math.sqrt(vInf2 + 2 * muM / arriveRadius) - Math.sqrt(muM / arriveRadius);
                    if (cost < bestCost) {
                        bestCost = cost;
                        bestT1 = t1;
                        bestTof = tof;
                    }
                }
            }
            if (bestCost == Double.POSITIVE_INFINITY) {
                return null;
            }
        }
        StateVector s = orbit.stateAt(bestT1, mu);
        // Aim beside the moon for the right closest approach (the same sum the mid-course aim uses).
        DVec3 dv = aim(system, moon, s, bestT1, bestT1 + bestTof, arriveRadius, gravityScale);
        if (dv == null) {
            return null;
        }
        // The second entry carries the arrival time, and the whole leg's Δv (with capture) in x.
        return new Burn[]{new Burn(bestT1, dv), new Burn(bestT1 + bestTof, new DVec3(bestCost, 0, 0))};
    }

    private static DVec3 solve3(double[][] m, DVec3 b) {
        double det = det(m[0][0], m[0][1], m[0][2], m[1][0], m[1][1], m[1][2], m[2][0], m[2][1], m[2][2]);
        if (Math.abs(det) < 1e-15) {
            return null;
        }
        double x = det(b.x(), m[0][1], m[0][2], b.y(), m[1][1], m[1][2], b.z(), m[2][1], m[2][2]) / det;
        double y = det(m[0][0], b.x(), m[0][2], m[1][0], b.y(), m[1][2], m[2][0], b.z(), m[2][2]) / det;
        double z = det(m[0][0], m[0][1], b.x(), m[1][0], m[1][1], b.y(), m[2][0], m[2][1], b.z()) / det;
        return new DVec3(x, y, z);
    }

    private static double det(double a, double b, double c, double d, double e, double f, double g, double h, double i) {
        return a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g);
    }

    /**
     * Mid-course: the Δv (ecliptic frame) that sends the ship from heliocentric {@code ship} at
     * {@code t} to pass {@code to} at {@code arriveTime}, offset to the side so the closest
     * approach is {@code arriveRadius}. Null if Lambert has no answer.
     */
    public static DVec3 aim(SolarSystem system, CelestialBody to, StateVector ship, double t, double arriveTime,
                            double arriveRadius, double gravityScale) {
        double muSun = to.parent().mu() * gravityScale;
        double muB = to.mu() * gravityScale;
        double tof = arriveTime - t;
        if (tof < 60) {
            return null;
        }
        StateVector b = system.stateRelativeToParent(to, arriveTime);
        DVec3 normal = b.position().cross(b.velocity()).normalize();
        Lambert.Solution centre = Lambert.solve(ship.position(), b.position(), tof, muSun, normal);
        if (centre == null) {
            return null;
        }
        DVec3 vInf = centre.v2().sub(b.velocity());
        double v2 = vInf.lengthSquared();
        // Impact parameter for that closest approach, to the side in the planet's orbit plane.
        double impact = arriveRadius * Math.sqrt(1 + 2 * muB / (arriveRadius * Math.max(1e-6, v2)));
        DVec3 side = vInf.cross(normal);
        if (side.length() < 1e-9) {
            return centre.v1().sub(ship.velocity());
        }
        DVec3 aimPoint = b.position().add(side.normalize().scale(impact));
        Lambert.Solution offset = Lambert.solve(ship.position(), aimPoint, tof, muSun, normal);
        return (offset == null ? centre : offset).v1().sub(ship.velocity());
    }
}
