package com.thepearlking.stevespace.universe;

/**
 * Two-body orbital mechanics: converting between orbital elements and position/velocity, and
 * solving Kepler's equation. Works for ellipses (e &lt; 1) and hyperbolas (e &gt; 1).
 */
public final class Kepler {

    private static final double TWO_PI = 2 * Math.PI;

    private Kepler() {
    }

    // ---------------------------------------------------------------------------------------
    // Elements -> state
    // ---------------------------------------------------------------------------------------

    public static StateVector stateFromElements(OrbitalElements el, double t, double mu) {
        double a = el.semiMajorAxis();
        double e = el.eccentricity();
        double n = el.meanMotion(mu);
        double meanAnomaly = el.meanAnomalyAtEpoch() + n * (t - el.epoch());

        double trueAnomaly;
        double radius;
        if (e < 1) {
            meanAnomaly = wrapAngle(meanAnomaly);
            double eccentricAnomaly = solveElliptic(meanAnomaly, e);
            trueAnomaly = 2 * Math.atan2(Math.sqrt(1 + e) * Math.sin(eccentricAnomaly / 2),
                    Math.sqrt(1 - e) * Math.cos(eccentricAnomaly / 2));
            radius = a * (1 - e * Math.cos(eccentricAnomaly));
        } else {
            double hyperbolicAnomaly = solveHyperbolic(meanAnomaly, e);
            trueAnomaly = 2 * Math.atan(Math.sqrt((e + 1) / (e - 1)) * Math.tanh(hyperbolicAnomaly / 2));
            radius = a * (1 - e * Math.cosh(hyperbolicAnomaly));
        }
        return stateFromTrueAnomaly(el, trueAnomaly, radius, mu);
    }

    /** State at a given true anomaly (angle from periapsis). */
    public static StateVector stateFromTrueAnomaly(OrbitalElements el, double trueAnomaly, double radius, double mu) {
        double e = el.eccentricity();
        double p = el.semiMajorAxis() * (1 - e * e); // semi-latus rectum
        double cosNu = Math.cos(trueAnomaly);
        double sinNu = Math.sin(trueAnomaly);

        // Position and velocity in the orbit's own plane (perifocal frame).
        double px = radius * cosNu;
        double py = radius * sinNu;
        double speedFactor = Math.sqrt(mu / Math.abs(p));
        double vx = -speedFactor * sinNu;
        double vy = speedFactor * (e + cosNu);

        return new StateVector(perifocalToReference(el, px, py), perifocalToReference(el, vx, vy));
    }

    /** Rotates a vector from the orbit plane into the reference frame (by ω, then i, then Ω). */
    private static DVec3 perifocalToReference(OrbitalElements el, double x, double y) {
        double cosO = Math.cos(el.ascendingNode());
        double sinO = Math.sin(el.ascendingNode());
        double cosW = Math.cos(el.argumentOfPeriapsis());
        double sinW = Math.sin(el.argumentOfPeriapsis());
        double cosI = Math.cos(el.inclination());
        double sinI = Math.sin(el.inclination());

        double xx = cosO * cosW - sinO * sinW * cosI;
        double xy = -cosO * sinW - sinO * cosW * cosI;
        double yx = sinO * cosW + cosO * sinW * cosI;
        double yy = -sinO * sinW + cosO * cosW * cosI;
        double zx = sinW * sinI;
        double zy = cosW * sinI;
        return new DVec3(xx * x + xy * y, yx * x + yy * y, zx * x + zy * y);
    }

    // ---------------------------------------------------------------------------------------
    // State -> elements
    // ---------------------------------------------------------------------------------------

    /**
     * Orbital elements of a body at position/velocity {@code state} relative to a parent with
     * gravitational parameter {@code mu}, with the epoch set to {@code t}.
     */
    public static OrbitalElements elementsFromState(StateVector state, double mu, double t) {
        DVec3 r = state.position();
        DVec3 v = state.velocity();
        double rLen = r.length();
        double vLen2 = v.lengthSquared();

        DVec3 h = r.cross(v); // specific angular momentum
        double hLen = h.length();
        DVec3 nodeVector = DVec3.Z.cross(h); // points at the ascending node
        double nodeLen = nodeVector.length();

        DVec3 eVec = r.scale(vLen2 - mu / rLen).sub(v.scale(r.dot(v))).scale(1 / mu);
        double e = eVec.length();

        double energy = vLen2 / 2 - mu / rLen;
        double a = Math.abs(e - 1) < 1e-10 ? Double.POSITIVE_INFINITY : -mu / (2 * energy);

        double inclination = hLen < 1e-12 ? 0 : Math.acos(clamp(h.z() / hLen));

        boolean equatorial = nodeLen < 1e-9 * hLen;
        boolean circular = e < 1e-9;

        double ascendingNode = 0;
        if (!equatorial) {
            ascendingNode = Math.acos(clamp(nodeVector.x() / nodeLen));
            if (nodeVector.y() < 0) {
                ascendingNode = TWO_PI - ascendingNode;
            }
        }

        double argumentOfPeriapsis;
        double trueAnomaly;
        if (circular) {
            // No periapsis: measure position from the node (or the x axis when also equatorial).
            argumentOfPeriapsis = 0;
            DVec3 reference = equatorial ? DVec3.X : nodeVector.normalize();
            trueAnomaly = Math.acos(clamp(reference.dot(r) / rLen));
            DVec3 perpendicular = h.normalize().cross(reference);
            if (perpendicular.dot(r) < 0) {
                trueAnomaly = TWO_PI - trueAnomaly;
            }
        } else {
            if (equatorial) {
                argumentOfPeriapsis = Math.atan2(eVec.y(), eVec.x());
                if (h.z() < 0) {
                    argumentOfPeriapsis = -argumentOfPeriapsis;
                }
            } else {
                argumentOfPeriapsis = Math.acos(clamp(nodeVector.dot(eVec) / (nodeLen * e)));
                if (eVec.z() < 0) {
                    argumentOfPeriapsis = TWO_PI - argumentOfPeriapsis;
                }
            }
            trueAnomaly = Math.acos(clamp(eVec.dot(r) / (e * rLen)));
            if (r.dot(v) < 0) {
                trueAnomaly = TWO_PI - trueAnomaly;
            }
        }

        double meanAnomaly = meanAnomalyFromTrue(trueAnomaly, e);
        return new OrbitalElements(a, e, inclination, ascendingNode, argumentOfPeriapsis, meanAnomaly, t);
    }

    public static double meanAnomalyFromTrue(double trueAnomaly, double e) {
        if (e < 1) {
            double eccentricAnomaly = 2 * Math.atan2(Math.sqrt(1 - e) * Math.sin(trueAnomaly / 2),
                    Math.sqrt(1 + e) * Math.cos(trueAnomaly / 2));
            return wrapAngle(eccentricAnomaly - e * Math.sin(eccentricAnomaly));
        }
        double nu = trueAnomaly > Math.PI ? trueAnomaly - TWO_PI : trueAnomaly;
        double hyperbolicAnomaly = 2 * atanh(Math.sqrt((e - 1) / (e + 1)) * Math.tan(nu / 2));
        return e * Math.sinh(hyperbolicAnomaly) - hyperbolicAnomaly;
    }

    // ---------------------------------------------------------------------------------------
    // Kepler's equation
    // ---------------------------------------------------------------------------------------

    /** Solves M = E - e·sin(E) for the eccentric anomaly E (Newton's method). */
    public static double solveElliptic(double meanAnomaly, double e) {
        double eccentricAnomaly = e < 0.8 ? meanAnomaly : Math.PI;
        for (int i = 0; i < 50; i++) {
            double f = eccentricAnomaly - e * Math.sin(eccentricAnomaly) - meanAnomaly;
            double step = f / (1 - e * Math.cos(eccentricAnomaly));
            eccentricAnomaly -= step;
            if (Math.abs(step) < 1e-13) {
                break;
            }
        }
        return eccentricAnomaly;
    }

    /** Solves M = e·sinh(H) - H for the hyperbolic anomaly H. */
    public static double solveHyperbolic(double meanAnomaly, double e) {
        double h = Math.log(2 * Math.abs(meanAnomaly) / e + 1.8) * Math.signum(meanAnomaly);
        for (int i = 0; i < 80; i++) {
            double f = e * Math.sinh(h) - h - meanAnomaly;
            double step = f / (e * Math.cosh(h) - 1);
            h -= step;
            if (Math.abs(step) < 1e-13) {
                break;
            }
        }
        return h;
    }

    public static double wrapAngle(double angle) {
        angle %= TWO_PI;
        return angle < 0 ? angle + TWO_PI : angle;
    }

    private static double clamp(double v) {
        return Math.max(-1, Math.min(1, v));
    }

    private static double atanh(double x) {
        return 0.5 * Math.log((1 + x) / (1 - x));
    }
}
