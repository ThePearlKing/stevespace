package com.thepearlking.stevespace.world.planet;

import com.thepearlking.stevespace.world.planet.noise.SphereNoise;

/**
 * Cloud-top maps of the giant planets (equirectangular, north up, longitude across), drawn from
 * what they really look like: Jupiter's brown belts and cream zones at their real latitudes,
 * streaked by its jets, with the Great Red Spot, white ovals and blue-grey festoons along the
 * equator; Saturn's soft butterscotch bands and polar hexagon; Uranus nearly bare cyan; Neptune
 * deep blue with a dark spot and bright streaks of cloud. Pure Java (no game classes), so the
 * map tools can render them too.
 */
public final class GiantPlanets {

    private GiantPlanets() {
    }

    /** Whether there's a hand-made map for this body. */
    public static boolean has(String id) {
        return switch (id) {
            case "jupiter", "saturn", "uranus", "neptune" -> true;
            default -> false;
        };
    }

    /** The map as 0xRRGGBB pixels, row by row from the north pole; null for other bodies. */
    public static int[] render(String id, int width, int height) {
        if (!has(id)) {
            return null;
        }
        Painter painter = switch (id) {
            case "jupiter" -> new Jupiter();
            case "saturn" -> new Saturn();
            case "uranus" -> new Uranus();
            default -> new Neptune();
        };
        int[] pixels = new int[width * height];
        java.util.stream.IntStream.range(0, height).parallel().forEach(y -> {
            double lat = Math.PI / 2 - (y + 0.5) / height * Math.PI;
            for (int x = 0; x < width; x++) {
                double lon = (x + 0.5) / width * 2 * Math.PI - Math.PI;
                pixels[y * width + x] = painter.color(lat, lon);
            }
        });
        return pixels;
    }

    private interface Painter {
        int color(double lat, double lon);
    }

    // -------------------------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------------------------

    /** Noise stretched along the longitude: long streaks, as zonal winds pull clouds out. */
    private static double streak(SphereNoise n, double lat, double lon, double across) {
        double c = Math.cos(lat);
        return n.sample(c * Math.cos(lon), lat * across, c * Math.sin(lon));
    }

    private static double plain(SphereNoise n, double lat, double lon) {
        double c = Math.cos(lat);
        return n.sample(c * Math.cos(lon), Math.sin(lat), c * Math.sin(lon));
    }

    /** A colour from a list of (latitude in degrees, colour) stops, blended smoothly between them. */
    private static int bands(double latDeg, double[] lats, int[] colors) {
        if (latDeg >= lats[0]) {
            return colors[0];
        }
        for (int i = 1; i < lats.length; i++) {
            if (latDeg >= lats[i]) {
                double t = (lats[i - 1] - latDeg) / (lats[i - 1] - lats[i]);
                return Shaping.mixColor(colors[i - 1], colors[i], Shaping.smoothstep(0, 1, t));
            }
        }
        return colors[colors.length - 1];
    }

    /** Distance from the centre of an oval storm, 1 at its edge (degrees of latitude and longitude). */
    private static double oval(double lat, double lon, double lat0, double lon0, double halfLonDeg, double halfLatDeg) {
        double dlon = Math.toDegrees(Math.atan2(Math.sin(lon - lon0), Math.cos(lon - lon0))) * Math.cos(lat);
        double dlat = Math.toDegrees(lat - lat0);
        return Math.sqrt((dlon / halfLonDeg) * (dlon / halfLonDeg) + (dlat / halfLatDeg) * (dlat / halfLatDeg));
    }

    // -------------------------------------------------------------------------------------------
    // Jupiter
    // -------------------------------------------------------------------------------------------

    private static final class Jupiter implements Painter {
        // North to south: polar haze, temperate belts and zones, tropical zone, the belts either
        // side of the bright equatorial zone, and on south. (Latitudes in degrees.)
        private static final double[] LATS = {
                72, 60, 52, 47, 43, 38, 34, 30, 26, 22, 18, 16, 10, 7.5, 5, 0, -5, -7.5, -10, -14, -17, -20,
                -23, -27, -30, -33, -37, -42, -47, -55, -64, -74};
        private static final int[] COLORS = {
                0x8E8A86, 0x9E968A, 0xB5A891, 0xC9BA9D, 0xB29C80, 0xD7C9AD, 0xE1D5BC, 0xB08A68, 0xA8825F,
                0xE9DFC9, 0xE6DAC1, 0x9C6646, 0x8E5A3E, 0xB89679, 0xE3D3B3, 0xE8DABF, 0xE2D1AF, 0xC7A684,
                0xA06F4E, 0xB89577, 0xA57351, 0xD8C6A8, 0xEADFCB, 0xE6DAC4, 0xAD8866, 0xC7AF92, 0xDCCDB3,
                0xB59F84, 0xCDBEA4, 0xAA9C8A, 0x948C82, 0x86827E};

        private static final double GRS_LAT = Math.toRadians(-22.3);
        private static final double GRS_LON = 0.6;
        private static final double BA_LAT = Math.toRadians(-33.5);
        private static final double BA_LON = 1.9;

        private final SphereNoise warp = new SphereNoise(11, 3.5, 4, 0.55);
        private final SphereNoise jets = new SphereNoise(12, 2.5, 5, 0.6);
        private final SphereNoise fine = new SphereNoise(13, 22, 3, 0.5);
        private final SphereNoise swirl = new SphereNoise(14, 7, 3, 0.5);
        private final SphereNoise polar = new SphereNoise(15, 9, 4, 0.55);

        @Override
        public int color(double lat, double lon) {
            double latDeg = Math.toDegrees(lat);
            // Band edges wander with longitude, more toward the poles.
            double wobble = streak(warp, lat, lon, 3.0) * (1.8 + Math.abs(latDeg) / 20) + plain(swirl, lat, lon) * 0.9;
            double grs = oval(lat, lon, GRS_LAT, GRS_LON, 11.0, 5.6);
            // The South Equatorial Belt bows around the Red Spot (the "hollow").
            if (grs < 2.2) {
                wobble += Math.max(0, 2.2 - grs) * 1.6;
            }
            double l = latDeg + wobble;
            int c = bands(l, LATS, COLORS);
            // Jet streaks: strong in the belts, softer in the zones.
            double belt = belt(c);
            double s = streak(jets, lat, lon, 16 + belt * 10);
            c = Shaping.shade(c, 1 + s * (0.07 + belt * 0.1) + plain(fine, lat, lon) * 0.035);
            // Festoons: blue-grey plumes hanging off the north edge of the equatorial zone.
            if (latDeg > 0.5 && latDeg < 9) {
                double phase = lon * 9 + (8 - latDeg) * 0.3 + streak(warp, lat, lon, 6) * 2.6;
                double hook = Shaping.smoothstep(0.82, 0.97, Math.cos(phase))
                        * Shaping.smoothstep(0.5, 3.5, latDeg) * Shaping.smoothstep(9, 6.2, latDeg);
                c = Shaping.mixColor(c, 0x7D8794, hook * 0.38);
                double plume = Shaping.smoothstep(0.85, 0.98, Math.cos(phase - 0.35)) * Shaping.smoothstep(1, 4, latDeg)
                        * Shaping.smoothstep(8, 5.5, latDeg);
                c = Shaping.mixColor(c, 0xF4EEE2, plume * 0.25);
            }
            // Dark "barges" in the North Equatorial Belt.
            for (int i = 0; i < 4; i++) {
                double barge = oval(lat, lon, Math.toRadians(15.5), -2.6 + i * 1.7, 3.2, 1.1);
                c = Shaping.mixColor(c, 0x5E3A2A, Shaping.smoothstep(1.0, 0.4, barge) * 0.7);
            }
            // The Great Red Spot: a brick-red oval with a swirl, a paler core and a bright collar.
            if (grs < 1.6) {
                double angle = Math.atan2(Math.toDegrees(lat - GRS_LAT) / 6.0,
                        Math.toDegrees(Math.atan2(Math.sin(lon - GRS_LON), Math.cos(lon - GRS_LON))) * Math.cos(lat) / 11.0);
                double spiral = Math.sin(angle * 2 + grs * 6 + plain(swirl, lat, lon) * 4);
                int red = Shaping.mixColor(0xC07656, 0xD7A07C, Shaping.smoothstep(0.8, 0.05, grs));
                red = Shaping.shade(red, 1 + spiral * 0.05);
                double inside = Shaping.smoothstep(1.0, 0.85, grs);
                double collar = Shaping.smoothstep(0.9, 1.05, grs) * Shaping.smoothstep(1.5, 1.12, grs);
                c = Shaping.mixColor(c, 0xF2E7D6, collar * 0.5);
                c = Shaping.mixColor(c, red, inside);
            }
            // Oval BA and a string of small white ovals further south.
            double ba = oval(lat, lon, BA_LAT, BA_LON, 3.6, 2.6);
            c = Shaping.mixColor(c, 0xF0E6D8, Shaping.smoothstep(1.0, 0.55, ba) * 0.9);
            c = Shaping.mixColor(c, 0xD9A385, Shaping.smoothstep(0.55, 0.0, ba) * 0.5);
            for (int i = 0; i < 6; i++) {
                double o = oval(lat, lon, Math.toRadians(-41.5 + (i % 3) * 1.2), -2.9 + i * 1.05 + (i * 0.37) % 0.5, 2.6, 1.7);
                c = Shaping.mixColor(c, 0xF3EDE3, Shaping.smoothstep(1.0, 0.2, o) * 0.7);
            }
            // The poles: mottled grey-blue haze, many small storms.
            double pole = Shaping.smoothstep(58, 80, Math.abs(latDeg));
            if (pole > 0) {
                double p = plain(polar, lat, lon);
                c = Shaping.mixColor(c, Shaping.shade(0x9A958F, 1 + p * 0.22), pole * 0.55);
            }
            return c;
        }

        /** How much a colour looks like a belt (dark) rather than a zone (light): 0..1. */
        private static double belt(int c) {
            double grey = (((c >> 16) & 255) * 0.3 + ((c >> 8) & 255) * 0.59 + (c & 255) * 0.11) / 255;
            return Shaping.smoothstep(0.85, 0.55, grey);
        }
    }

    // -------------------------------------------------------------------------------------------
    // Saturn
    // -------------------------------------------------------------------------------------------

    private static final class Saturn implements Painter {
        private static final double[] LATS = {
                88, 80, 76, 70, 62, 55, 47, 40, 33, 26, 20, 14, 8, 0, -8, -14, -20, -27, -35, -45, -55, -65, -78, -88};
        private static final int[] COLORS = {
                0x7D8A93, 0x8F9AA0, 0xA9A08A, 0xBFAE8A, 0xCDB98F, 0xC9B07F, 0xD8C49A, 0xCDB386, 0xDCC9A0,
                0xD2B788, 0xE0CDA5, 0xD7BC8C, 0xE9D9B4, 0xEEE0BE, 0xE6D4AE, 0xD3B88A, 0xDFCB9F, 0xCFB485,
                0xDAC59A, 0xCCB386, 0xC8B186, 0xB9A47E, 0xA29473, 0x8F8670};

        private final SphereNoise warp = new SphereNoise(21, 3, 3, 0.5);
        private final SphereNoise jets = new SphereNoise(22, 2.5, 4, 0.55);
        private final SphereNoise fine = new SphereNoise(23, 20, 3, 0.5);

        @Override
        public int color(double lat, double lon) {
            double latDeg = Math.toDegrees(lat);
            double l = latDeg + streak(warp, lat, lon, 2.5) * 0.8;
            int c = bands(l, LATS, COLORS);
            c = Shaping.shade(c, 1 + streak(jets, lat, lon, 14) * 0.035 + plain(fine, lat, lon) * 0.015);
            // The north polar hexagon: a six-sided jet around a bluish cap.
            if (latDeg > 70) {
                double r = 90 - latDeg;
                double edge = hexEdge(lon);
                c = Shaping.mixColor(c, 0x8FA0AB, Shaping.smoothstep(edge + 0.5, edge - 2, r) * 0.6);
                c = Shaping.mixColor(c, 0xB9A982, Shaping.smoothstep(1.4, 0.0, Math.abs(r - edge)) * 0.5);
            }
            return c;
        }

        /** Distance from the pole (degrees) of the hexagon's edge at this longitude. */
        private static double hexEdge(double lon) {
            double sector = Math.PI / 3;
            double a = ((lon % sector) + sector) % sector - sector / 2;
            return 13.0 * Math.cos(sector / 2) / Math.cos(a);
        }
    }

    // -------------------------------------------------------------------------------------------
    // Uranus and Neptune
    // -------------------------------------------------------------------------------------------

    private static final class Uranus implements Painter {
        private final SphereNoise soft = new SphereNoise(31, 2.5, 3, 0.5);

        @Override
        public int color(double lat, double lon) {
            double latDeg = Math.toDegrees(lat);
            int c = 0x9CCFD6;
            // Almost featureless: faint bands, a brighter polar cap facing the Sun.
            c = Shaping.shade(c, 1 + Math.sin(lat * 9) * 0.012 + streak(soft, lat, lon, 8) * 0.012);
            c = Shaping.mixColor(c, 0xC4E4E6, Shaping.smoothstep(45, 75, latDeg) * 0.55);
            return c;
        }
    }

    private static final class Neptune implements Painter {
        private final SphereNoise jets = new SphereNoise(41, 2.5, 4, 0.55);
        private final SphereNoise wisps = new SphereNoise(42, 6, 4, 0.55);

        @Override
        public int color(double lat, double lon) {
            double latDeg = Math.toDegrees(lat);
            int c = Shaping.mixColor(0x3456B8, 0x4C72CF, Shaping.smoothstep(-60, 10, latDeg) * 0.5 + 0.25);
            c = Shaping.shade(c, 1 + streak(jets, lat, lon, 10) * 0.05 + Math.sin(lat * 7) * 0.025);
            // The dark spot and its bright companion clouds.
            double spot = oval(lat, lon, Math.toRadians(-21), 1.2, 15.0, 7.0);
            c = Shaping.mixColor(c, 0x1E2E6E, Shaping.smoothstep(1.0, 0.45, spot) * 0.85);
            double companion = oval(lat, lon, Math.toRadians(-29.5), 1.05, 13.0, 2.2);
            c = Shaping.mixColor(c, 0xEEF3FA, Shaping.smoothstep(1.0, 0.2, companion) * 0.75);
            // Thin white streaks of high cloud.
            double w = streak(wisps, lat, lon, 30);
            double band = Shaping.smoothstep(0.25, 0.55, Math.abs(Math.sin(lat * 3.2)));
            c = Shaping.mixColor(c, 0xE8EFFA, Shaping.smoothstep(0.2, 0.5, w) * band * 0.7);
            c = Shaping.mixColor(c, 0x5E80D2, Shaping.smoothstep(55, 80, -latDeg) * 0.4);
            return c;
        }
    }
}
