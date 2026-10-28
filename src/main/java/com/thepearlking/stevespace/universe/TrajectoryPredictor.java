package com.thepearlking.stevespace.universe;

import java.util.ArrayList;
import java.util.List;

/**
 * Predicts where a coasting vessel will go: its Kepler orbit around the current body, and, if it
 * leaves that body's sphere of influence or meets a moon, the next orbit after that (patched
 * conics, like the flight model itself).
 */
public final class TrajectoryPredictor {

    /** One piece of the trajectory, around one body. Points are relative to that body. */
    public record Segment(CelestialBody body, OrbitalElements orbit, List<DVec3> points, List<Double> times,
                          double startTime, double endTime, End end) {
    }

    /** How a segment ends. */
    public enum End { CONTINUES, IMPACT, ATMOSPHERE, ESCAPE, ENCOUNTER, TIME_LIMIT }

    private static final int POINTS_PER_SEGMENT = 360;
    private static final int MAX_SEGMENTS = 3;

    private TrajectoryPredictor() {
    }

    /**
     * @param gravityScale  the vessel gravity setting (bodies' own orbits are not scaled)
     * @param horizon       how far ahead to look, in seconds, for open or very long trajectories
     */
    public static List<Segment> predict(SolarSystem system, CelestialBody body, StateVector state, double t0,
                                        double gravityScale, double horizon) {
        List<Segment> segments = new ArrayList<>();
        double t = t0;
        for (int n = 0; n < MAX_SEGMENTS; n++) {
            double mu = body.mu() * gravityScale;
            OrbitalElements orbit = Kepler.elementsFromState(state, mu, t);
            double span;
            if (orbit.eccentricity() < 1) {
                span = Math.min(orbit.period(mu), horizon);
            } else {
                span = horizon;
            }
            // A giant has no ground: the end of the line is where the hull is crushed, deep down.
            double floor = body.radius() + (GiantAir.has(body) ? GiantAir.CRUSH : 0);
            double airTop = body.hasAtmosphere() ? body.radius() + body.atmosphere().height() : body.radius();
            double soi = body.parent() == null ? Double.POSITIVE_INFINITY : system.sphereOfInfluence(body);

            List<DVec3> points = new ArrayList<>();
            List<Double> times = new ArrayList<>();
            End end = orbit.eccentricity() < 1 && span < horizon ? End.CONTINUES : End.TIME_LIMIT;
            double dt = span / POINTS_PER_SEGMENT;
            CelestialBody next = null;
            StateVector nextState = null;
            boolean wasAboveAir = state.position().length() >= airTop;
            // Coming down onto the planet: the exact moment the path reaches the air (or the
            // ground), which samples can step right past on a steep fall.
            double crossTime = Double.NaN;
            double crossRadius = body.hasAtmosphere() && wasAboveAir ? airTop : floor;
            if (orbit.periapsis() < crossRadius && state.position().dot(state.velocity()) < 0) {
                double m = orbit.meanAnomalyAtEpoch();
                if (orbit.eccentricity() < 1) {
                    m = Kepler.wrapAngle(m);
                    if (m > Math.PI) {
                        m -= 2 * Math.PI; // before periapsis: negative
                    }
                }
                double toPeriapsis = m < 0 ? -m / orbit.meanMotion(mu) : 0;
                double lo = t;
                double hi = t + toPeriapsis;
                if (toPeriapsis > 0 && orbit.stateAt(hi, mu).position().length() < crossRadius) {
                    for (int it = 0; it < 60; it++) {
                        double mid = (lo + hi) / 2;
                        if (orbit.stateAt(mid, mu).position().length() < crossRadius) {
                            hi = mid;
                        } else {
                            lo = mid;
                        }
                    }
                    crossTime = hi;
                }
            }
            for (int i = 0; i <= POINTS_PER_SEGMENT; i++) {
                double ti = t + dt * i;
                if (!Double.isNaN(crossTime) && ti >= crossTime) {
                    StateVector at = orbit.stateAt(crossTime, mu);
                    points.add(at.position());
                    times.add(crossTime);
                    end = crossRadius == floor ? End.IMPACT : End.ATMOSPHERE;
                    break;
                }
                StateVector s = orbit.stateAt(ti, mu);
                double r = s.position().length();
                points.add(s.position());
                times.add(ti);
                if (i == 0) {
                    continue;
                }
                if (r <= floor) {
                    end = End.IMPACT;
                    break;
                }
                // A fast fall can pass the whole planet between two samples: check the chord.
                double surface = body.hasAtmosphere() && wasAboveAir ? airTop : floor;
                DVec3 prev = points.get(points.size() - 2);
                DVec3 chord = s.position().sub(prev);
                double near = chord.lengthSquared() < 1e-12 ? 0 : Math.max(0, Math.min(1, -prev.dot(chord) / chord.lengthSquared()));
                if (near > 0 && near < 1 && prev.add(chord.scale(near)).length() < surface) {
                    double lo = ti - dt;
                    double hi = ti - dt + dt * near;
                    for (int it = 0; it < 40; it++) {
                        double mid = (lo + hi) / 2;
                        if (orbit.stateAt(mid, mu).position().length() < surface) {
                            hi = mid;
                        } else {
                            lo = mid;
                        }
                    }
                    StateVector at = orbit.stateAt(hi, mu);
                    if (at.position().length() < surface * 1.001) {
                        points.set(points.size() - 1, at.position());
                        times.set(times.size() - 1, hi);
                        end = surface == floor ? End.IMPACT : End.ATMOSPHERE;
                        break;
                    }
                }
                if (wasAboveAir && r < airTop && body.hasAtmosphere()) {
                    end = End.ATMOSPHERE;
                    break;
                }
                wasAboveAir = wasAboveAir || r >= airTop;
                if (r > soi) {
                    end = End.ESCAPE;
                    next = body.parent();
                    nextState = s.add(system.stateRelativeToParent(body, ti));
                    break;
                }
                CelestialBody hit = null;
                for (CelestialBody moon : system.allChildrenOf(body)) {
                    double soiMoon = system.sphereOfInfluence(moon);
                    StateVector m = system.stateRelativeToParent(moon, ti);
                    if (s.position().sub(m.position()).length() < soiMoon) {
                        hit = moon;
                        nextState = s.sub(m);
                        break;
                    }
                    // Between two samples the path may have crossed a small sphere of influence
                    // entirely: look for the moment it came within reach.
                    double tp = ti - dt;
                    DVec3 a = orbit.stateAt(tp, mu).position().sub(system.stateRelativeToParent(moon, tp).position());
                    DVec3 b = s.position().sub(m.position());
                    DVec3 ab = b.sub(a);
                    double k = ab.lengthSquared() < 1e-12 ? 0 : Math.max(0, Math.min(1, -a.dot(ab) / ab.lengthSquared()));
                    if (a.add(ab.scale(k)).length() < soiMoon) {
                        double lo = tp;
                        double hi = tp + dt * k;
                        for (int it = 0; it < 30; it++) {
                            double mid = (lo + hi) / 2;
                            double d = orbit.stateAt(mid, mu).position().sub(system.stateRelativeToParent(moon, mid).position()).length();
                            if (d < soiMoon) {
                                hi = mid;
                            } else {
                                lo = mid;
                            }
                        }
                        StateVector at = orbit.stateAt(hi, mu);
                        StateVector mAt = system.stateRelativeToParent(moon, hi);
                        if (at.position().sub(mAt.position()).length() < soiMoon * 1.001) {
                            hit = moon;
                            nextState = at.sub(mAt);
                            points.set(points.size() - 1, at.position());
                            times.set(times.size() - 1, hi);
                            break;
                        }
                    }
                }
                if (hit == null) {
                    for (CelestialBody visitor : system.visitorsOf(body)) {
                        StateVector fromParent = s.add(system.stateRelativeToParent(body, ti));
                        if (system.handOver(body, visitor, fromParent.position(), ti)) {
                            hit = visitor;
                            nextState = fromParent.sub(system.stateRelativeToParent(visitor, ti));
                            break;
                        }
                    }
                }
                if (hit != null) {
                    end = End.ENCOUNTER;
                    next = hit;
                    break;
                }
            }
            double endTime = times.get(times.size() - 1);
            segments.add(new Segment(body, orbit, points, times, t, endTime, end));
            if (next == null) {
                break;
            }
            body = next;
            state = nextState;
            t = endTime;
        }
        return segments;
    }
}
