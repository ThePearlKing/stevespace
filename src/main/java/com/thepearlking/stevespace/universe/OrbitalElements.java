package com.thepearlking.stevespace.universe;

/**
 * A Keplerian orbit: the six classical elements, measured in the ecliptic frame
 * (x toward the vernal equinox, z toward ecliptic north). Angles in radians, lengths in blocks.
 *
 * @param semiMajorAxis       size of the orbit (negative for hyperbolic escape trajectories)
 * @param eccentricity        0 = circle, 0..1 = ellipse, &gt; 1 = hyperbola
 * @param inclination         tilt of the orbit plane against the reference plane
 * @param ascendingNode       longitude where the orbit crosses the plane going north (Ω)
 * @param argumentOfPeriapsis angle from the ascending node to the closest point (ω)
 * @param meanAnomalyAtEpoch  where along the orbit the body is at time {@code epoch} (M₀)
 * @param epoch               universe time (seconds) at which {@code meanAnomalyAtEpoch} holds
 */
public record OrbitalElements(double semiMajorAxis, double eccentricity, double inclination,
                              double ascendingNode, double argumentOfPeriapsis,
                              double meanAnomalyAtEpoch, double epoch) {

    /** Builds elements from degrees and the usual "longitude of perihelion / mean longitude" pair. */
    public static OrbitalElements fromPlanetTable(double semiMajorAxis, double eccentricity, double inclinationDeg,
                                                  double ascendingNodeDeg, double longitudeOfPerihelionDeg,
                                                  double meanLongitudeDeg) {
        double node = Math.toRadians(ascendingNodeDeg);
        double perihelion = Math.toRadians(longitudeOfPerihelionDeg);
        double meanLongitude = Math.toRadians(meanLongitudeDeg);
        return new OrbitalElements(semiMajorAxis, eccentricity, Math.toRadians(inclinationDeg), node,
                perihelion - node, meanLongitude - perihelion, 0);
    }

    /** Orbital period in seconds around a parent with gravitational parameter {@code mu}. */
    public double period(double mu) {
        return 2 * Math.PI * Math.sqrt(Math.abs(semiMajorAxis * semiMajorAxis * semiMajorAxis) / mu);
    }

    public double meanMotion(double mu) {
        return Math.sqrt(mu / Math.abs(semiMajorAxis * semiMajorAxis * semiMajorAxis));
    }

    public double periapsis() {
        return semiMajorAxis * (1 - eccentricity);
    }

    public double apoapsis() {
        return eccentricity < 1 ? semiMajorAxis * (1 + eccentricity) : Double.POSITIVE_INFINITY;
    }

    /** Position and velocity relative to the parent at universe time {@code t}. */
    public StateVector stateAt(double t, double mu) {
        return Kepler.stateFromElements(this, t, mu);
    }
}
