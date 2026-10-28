package com.thepearlking.stevespace.tools;

import com.thepearlking.stevespace.universe.CelestialBody;
import com.thepearlking.stevespace.universe.DVec3;
import com.thepearlking.stevespace.universe.Kepler;
import com.thepearlking.stevespace.universe.LocalSky;
import com.thepearlking.stevespace.universe.OrbitalElements;
import com.thepearlking.stevespace.universe.SolarSystem;
import com.thepearlking.stevespace.universe.StateVector;

/**
 * Developer tool: numeric sanity checks of the solar system model (no Minecraft needed).
 * Prints PASS/FAIL lines; exits with status 1 if anything fails.
 */
public final class UniverseCheck {

    private static int failures;

    public static void main(String[] args) {
        SolarSystem sys = new SolarSystem(1200);
        CelestialBody earth = sys.earth();
        CelestialBody sun = sys.sun();
        CelestialBody moon = sys.body("moon");

        double yearDays = sys.earthYearSeconds() / 86400;
        check("earth year ~11.6 real days", Math.abs(yearDays - 11.56) < 0.1, String.format("%.2f days", yearDays));
        double leoSpeed = Math.sqrt(earth.mu() / (earth.radius() + 700));
        double leoPeriod = 2 * Math.PI * (earth.radius() + 700) / leoSpeed;
        check("LEO speed/period", leoSpeed > 200 && leoSpeed < 260, String.format("%.0f b/s, %.0f s", leoSpeed, leoPeriod));
        check("moon gravity", Math.abs(moon.surfaceGravity() - 1.6) < 0.1, String.format("%.2f", moon.surfaceGravity()));

        // Kepler round trip on several orbits, including a hyperbola.
        double worst = 0;
        double[][] orbits = {{7000, 0.001, 0.5}, {12000, 0.6, 1.2}, {-9000, 1.8, 0.3}, {384400, 0.05, 0.09}};
        for (double[] o : orbits) {
            OrbitalElements el = new OrbitalElements(o[0], o[1], o[2], 0.7, 1.1, 0.4, 0);
            StateVector s = el.stateAt(37, earth.mu());
            OrbitalElements back = Kepler.elementsFromState(s, earth.mu(), 37);
            StateVector s2 = back.stateAt(80, earth.mu());
            StateVector s1 = el.stateAt(80, earth.mu());
            worst = Math.max(worst, s1.position().sub(s2.position()).length() / s1.position().length());
        }
        check("kepler round trip", worst < 1e-8, "rel err " + worst);

        // Sun over a temperate observer: rises in the east, highest around noon, ~day length.
        double lat = Math.toRadians(43);
        double lon = 0;
        double bestElevation = -10;
        double bestT = 0;
        for (double t = 0; t < 1200; t += 1) {
            double el = LocalSky.at(sys, earth, lat, lon, 0, t).elevationOf(sys, sun, t);
            if (el > bestElevation) {
                bestElevation = el;
                bestT = t;
            }
        }
        double tRise = -1;
        for (double t = bestT - 600; t < bestT; t += 0.5) {
            if (LocalSky.at(sys, earth, lat, lon, 0, t).elevationOf(sys, sun, t) > 0) {
                tRise = t;
                break;
            }
        }
        DVec3 riseDir = LocalSky.at(sys, earth, lat, lon, 0, tRise).offsetTo(sys, sun, tRise).normalize();
        check("sun rises in the east", riseDir.x() > 0.3, "dir " + fmt(riseDir));
        double noonHour = Math.toDegrees(LocalSky.at(sys, earth, lat, lon, 0, bestT).hourAngle(sys, sun, bestT));
        double laterHour = Math.toDegrees(LocalSky.at(sys, earth, lat, lon, 0, bestT + 100).hourAngle(sys, sun, bestT + 100));
        check("hour angle 0 at noon, grows after", Math.abs(noonHour) < 1 && laterHour > 20 && laterHour < 40,
                String.format("%.2f° then %.1f°", noonHour, laterHour));
        DVec3 noonDir = LocalSky.at(sys, earth, lat, lon, 0, bestT).offsetTo(sys, sun, bestT).normalize();
        check("noon sun is due south (northern hemisphere)", noonDir.z() > 0.2 && Math.abs(noonDir.x()) < 0.05, "dir " + fmt(noonDir));
        double noonDeg = Math.toDegrees(bestElevation);
        check("noon elevation plausible (90-43±23.4)", noonDeg > 23 && noonDeg < 71, String.format("%.1f°", noonDeg));

        // Solar day exactly 1200 s: next noon one day later.
        double next = 0;
        double nextBest = -10;
        for (double t = bestT + 1100; t < bestT + 1300; t += 0.1) {
            double el = LocalSky.at(sys, earth, lat, lon, 0, t).elevationOf(sys, sun, t);
            if (el > nextBest) {
                nextBest = el;
                next = t;
            }
        }
        check("solar day = 1200 s", Math.abs(next - bestT - 1200) < 1, String.format("%.1f s", next - bestT));

        // Seasons: over half a year the noon elevation at 43°N changes by about 47°.
        double t2 = bestT + sys.earthYearSeconds() / 2;
        double noon2 = -10;
        for (double t = t2 - 700; t < t2 + 700; t += 1) {
            noon2 = Math.max(noon2, LocalSky.at(sys, earth, lat, lon, 0, t).elevationOf(sys, sun, t));
        }
        double swing = Math.abs(Math.toDegrees(noon2) - noonDeg);
        check("seasons swing", swing > 25, String.format("%.1f° vs %.1f° (swing %.1f°)", noonDeg, Math.toDegrees(noon2), swing));

        // Moon from Earth: real angular size ~0.5°.
        DVec3 toMoon = LocalSky.at(sys, earth, lat, lon, 0, 0).offsetTo(sys, moon, 0);
        double moonDeg = Math.toDegrees(2 * Math.atan(moon.radius() / toMoon.length()));
        check("moon angular size", moonDeg > 0.45 && moonDeg < 0.6, String.format("%.3f°", moonDeg));

        // Tidal lock: from the Moon's (0,0), Earth stays near the zenith all month.
        double minEl = 90;
        double month = moon.orbit().period(earth.mu() + moon.mu());
        for (double t = 0; t < month; t += month / 50) {
            minEl = Math.min(minEl, Math.toDegrees(LocalSky.at(sys, moon, 0, 0, 0, t).elevationOf(sys, earth, t)));
        }
        check("moon tidally locked", minEl > 75, String.format("Earth min elevation %.1f°, month %.1f h", minEl, month / 3600));

        // Map <-> orbit conversion round trip, and the ground really moves with the planet.
        double worstPos = 0;
        double worstVel = 0;
        for (double[] c : new double[][]{{120, 500, -4500, 30, 12, -7}, {-15000, 2463, 3000, 0, 200, 0}, {19000, 80, 9000, -50, -3, 20}}) {
            StateVector orbit = com.thepearlking.stevespace.universe.SurfaceFrame.toOrbit(sys, earth, c[0], c[1], c[2], c[3], c[4], c[5], 777);
            var back = com.thepearlking.stevespace.universe.SurfaceFrame.toMap(sys, earth, orbit, 777);
            worstPos = Math.max(worstPos, Math.abs(back.x() - c[0]) + Math.abs(back.y() - c[1]) + Math.abs(back.z() - c[2]));
            worstVel = Math.max(worstVel, Math.abs(back.east() - c[3]) + Math.abs(back.up() - c[4]) + Math.abs(back.south() - c[5]));
        }
        check("map<->orbit round trip", worstPos < 1e-6 && worstVel < 1e-6, String.format("pos %.2e vel %.2e", worstPos, worstVel));
        StateVector a = com.thepearlking.stevespace.universe.SurfaceFrame.toOrbit(sys, earth, 300, 70, -4000, 0, 0, 0, 1000);
        StateVector b = com.thepearlking.stevespace.universe.SurfaceFrame.toOrbit(sys, earth, 300, 70, -4000, 0, 0, 0, 1000.5);
        DVec3 moved = b.position().sub(a.position()).scale(1 / 0.5);
        check("ground velocity matches rotation", moved.sub(a.velocity()).length() < 0.05 * a.velocity().length(),
                String.format("ground %.2f b/s, measured %.2f b/s", a.velocity().length(), moved.length()));

        System.out.println(failures == 0 ? "ALL PASS" : failures + " FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void check(String name, boolean ok, String detail) {
        System.out.println((ok ? "PASS " : "FAIL ") + name + "  " + detail);
        if (!ok) {
            failures++;
        }
    }

    private static String fmt(DVec3 v) {
        return String.format("(%.2f, %.2f, %.2f)", v.x(), v.y(), v.z());
    }
}
