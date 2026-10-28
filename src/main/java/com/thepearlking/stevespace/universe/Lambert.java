package com.thepearlking.stevespace.universe;

/**
 * Lambert's problem: the orbit that goes from one point to another in a given time. Solved with
 * universal variables (bisection on z, which is robust for every orbit shape short of multiple
 * revolutions). Used to plan and correct flights between planets.
 */
public final class Lambert {

    /** The velocities at departure and at arrival. */
    public record Solution(DVec3 v1, DVec3 v2) {
    }

    private Lambert() {
    }

    /**
     * @param normal the sense of motion: the transfer goes round the same way as orbits about this
     *               axis (pass the planets' orbit normal for a prograde transfer)
     * @return the solution, or null when there is none (e.g. points exactly opposite)
     */
    public static Solution solve(DVec3 r1, DVec3 r2, double dt, double mu, DVec3 normal) {
        double a1 = r1.length();
        double a2 = r2.length();
        double cos = Math.max(-1, Math.min(1, r1.dot(r2) / (a1 * a2)));
        double theta = Math.acos(cos);
        if (r1.cross(r2).dot(normal) < 0) {
            theta = 2 * Math.PI - theta;
        }
        double denominator = 1 - Math.cos(theta);
        if (denominator < 1e-12 || dt <= 0) {
            return null;
        }
        double a = Math.sin(theta) * Math.sqrt(a1 * a2 / denominator);
        if (Math.abs(a) < 1e-9) {
            return null;
        }
        double sqrtMu = Math.sqrt(mu);
        // F(z) rises with z; find its root between a hyperbolic lower bound and one revolution.
        double lo = -4 * Math.PI * Math.PI;
        while (f(lo, a1, a2, a, sqrtMu, dt) > 0 && lo > -1e6) {
            lo *= 2;
        }
        double hi = 4 * Math.PI * Math.PI - 1e-6;
        if (f(hi, a1, a2, a, sqrtMu, dt) < 0) {
            return null;
        }
        double z = 0;
        for (int i = 0; i < 200; i++) {
            z = (lo + hi) / 2;
            if (f(z, a1, a2, a, sqrtMu, dt) < 0) {
                lo = z;
            } else {
                hi = z;
            }
            if (hi - lo < 1e-12) {
                break;
            }
        }
        double y = y(z, a1, a2, a);
        if (y <= 0) {
            return null;
        }
        double fl = 1 - y / a1;
        double g = a * Math.sqrt(y / mu);
        double gdot = 1 - y / a2;
        DVec3 v1 = r2.sub(r1.scale(fl)).scale(1 / g);
        DVec3 v2 = r2.scale(gdot).sub(r1).scale(1 / g);
        return new Solution(v1, v2);
    }

    /** Time-of-flight mismatch for a given z (negative: too short; y < 0 counts as too short). */
    private static double f(double z, double r1, double r2, double a, double sqrtMu, double dt) {
        double y = y(z, r1, r2, a);
        if (y < 0) {
            return -1e30;
        }
        double c = c(z);
        double s = s(z);
        return Math.pow(y / c, 1.5) * s + a * Math.sqrt(y) - sqrtMu * dt;
    }

    private static double y(double z, double r1, double r2, double a) {
        return r1 + r2 + a * (z * s(z) - 1) / Math.sqrt(c(z));
    }

    /** Stumpff functions. */
    private static double c(double z) {
        if (z > 1e-6) {
            return (1 - Math.cos(Math.sqrt(z))) / z;
        }
        if (z < -1e-6) {
            return (Math.cosh(Math.sqrt(-z)) - 1) / -z;
        }
        return 0.5 - z / 24;
    }

    private static double s(double z) {
        if (z > 1e-6) {
            double q = Math.sqrt(z);
            return (q - Math.sin(q)) / (q * q * q);
        }
        if (z < -1e-6) {
            double q = Math.sqrt(-z);
            return (Math.sinh(q) - q) / (q * q * q);
        }
        return 1.0 / 6 - z / 120;
    }
}
