package com.thepearlking.stevespace.world.planet.earth;

import java.io.InputStream;

/**
 * The real Earth's land heights and sea depths (NOAA ETOPO5, resampled to 4096 x 2048; see
 * tools/earth/make_dem.py), for the Real Earth world type. Metres above (or below) sea level at
 * any latitude and longitude, smoothly between the points.
 */
public final class EarthElevation {

    private static final int W;
    private static final int H;
    private static final byte[] MAP;

    static {
        byte[] map = new byte[0];
        int w = 1;
        int h = 1;
        try (InputStream in = EarthElevation.class.getResourceAsStream("/data/stevespace/earth/elevation.png")) {
            if (in != null) {
                var image = javax.imageio.ImageIO.read(in);
                w = image.getWidth();
                h = image.getHeight();
                map = new byte[w * h];
                var raster = image.getRaster();
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        map[y * w + x] = (byte) raster.getSample(x, y, 0);
                    }
                }
            }
        } catch (Exception e) {
            map = new byte[0];
        }
        W = w;
        H = h;
        MAP = map;
    }

    /** The coastlines, finer than the heights (tools/earth/make_land_mask.py): 1 bit a point, rows packed. */
    private static final byte[] LAND;
    private static final int LW;
    private static final int LH;
    private static final int STRIDE;

    static {
        byte[] land = new byte[0];
        int w = 1;
        int h = 1;
        try (InputStream in = EarthElevation.class.getResourceAsStream("/data/stevespace/earth/land.png")) {
            if (in != null) {
                var image = javax.imageio.ImageIO.read(in);
                w = image.getWidth();
                h = image.getHeight();
                var packed = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_BYTE_BINARY);
                packed.getGraphics().drawImage(image, 0, 0, null);
                land = ((java.awt.image.DataBufferByte) packed.getRaster().getDataBuffer()).getData();
            }
        } catch (Exception e) {
            land = new byte[0];
        }
        LAND = land;
        LW = w;
        LH = h;
        STRIDE = (w + 7) / 8;
    }

    private EarthElevation() {
    }

    /** Whether the coastline map has land here (null-safe: true when there is no map, so the heights decide). */
    private static Boolean land(double latitude, double longitude) {
        if (LAND.length == 0) {
            return null;
        }
        int x = Math.floorMod((int) Math.floor((longitude + Math.PI) / (2 * Math.PI) * LW), LW);
        int y = Math.max(0, Math.min(LH - 1, (int) Math.floor((Math.PI / 2 - latitude) / Math.PI * LH)));
        return (LAND[y * STRIDE + (x >> 3)] & (0x80 >> (x & 7))) != 0;
    }

    public static boolean available() {
        return MAP.length > 0;
    }

    /** Metres above sea level (negative: the sea's depth) at a latitude and longitude in radians. */
    public static double metres(double latitude, double longitude) {
        if (MAP.length == 0) {
            return -4000;
        }
        double fx = (longitude + Math.PI) / (2 * Math.PI) * W - 0.5;
        double fy = (Math.PI / 2 - latitude) / Math.PI * H - 0.5;
        int x0 = (int) Math.floor(fx);
        int y0 = (int) Math.floor(fy);
        double tx = fx - x0;
        double ty = fy - y0;
        double a = at(x0, y0) * (1 - tx) + at(x0 + 1, y0) * tx;
        double b = at(x0, y0 + 1) * (1 - tx) + at(x0 + 1, y0 + 1) * tx;
        double metres = a * (1 - ty) + b * ty;
        // The finer coastline has the last word on land or sea (the heights smear small islands away).
        Boolean land = land(latitude, longitude);
        if (land != null) {
            metres = land ? Math.max(metres, 4) : Math.min(metres, -6);
        }
        return metres;
    }

    private static double at(int x, int y) {
        x = Math.floorMod(x, W);
        y = Math.max(0, Math.min(H - 1, y));
        int v = MAP[y * W + x] & 0xFF;
        if (v >= 128) {
            double s = (v - 128) / 127.0;
            return s * s * 8800;
        }
        double s = (127 - v) / 127.0;
        return -s * s * 11000;
    }
}
