package com.thepearlking.stevespace.client.planet;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.thepearlking.stevespace.network.LandmarksS2C;
import com.thepearlking.stevespace.universe.CelestialBody;
import com.thepearlking.stevespace.universe.DVec3;
import com.thepearlking.stevespace.world.Landmarks;
import com.thepearlking.stevespace.world.planet.PlanetGeometry;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/**
 * Big buildings seen from far above a world: each built-up cell as a plain box of its own
 * colour and true size standing on the globe. Blocky on purpose: they're made of blocks.
 */
public final class LandmarkRenderer {

    private static final Map<String, Map<Long, Landmarks.Cell>> WORLDS = new HashMap<>();

    private LandmarkRenderer() {
    }

    public static void receive(LandmarksS2C msg) {
        WORLDS.clear();
        for (LandmarksS2C.World w : msg.worlds()) {
            WORLDS.put(w.dimension(), w.cells());
        }
    }

    /**
     * Draws a world's buildings in its own frame, in units of its radius ({@code modelView} as
     * for its surface). {@code camera} and {@code sun} are in that frame too.
     */
    static void draw(CelestialBody body, Matrix4f modelView, Matrix4f projection, DVec3 camera, DVec3 sun) {
        draw(body, modelView, projection, camera, sun, Integer.MIN_VALUE, 0);
    }

    /** The same, leaving out what's within {@code skipWithin} blocks along the ground (the real blocks are in view there). */
    static void draw(CelestialBody body, Matrix4f modelView, Matrix4f projection, DVec3 camera, DVec3 sun, double skipWithin) {
        draw(body, modelView, projection, camera, sun, Integer.MIN_VALUE, skipWithin);
    }

    /** The same, only what stands above {@code fromY} (the parts of tall buildings sticking up out of the clouds). */
    static void draw(CelestialBody body, Matrix4f modelView, Matrix4f projection, DVec3 camera, DVec3 sun, int fromY) {
        draw(body, modelView, projection, camera, sun, fromY, 0);
    }

    private static void draw(CelestialBody body, Matrix4f modelView, Matrix4f projection, DVec3 camera, DVec3 sun, int fromY,
                             double skipWithin) {
        if (body.surfaceDimension() == null) {
            return;
        }
        Map<Long, Landmarks.Cell> cells = WORLDS.get(body.surfaceDimension());
        if (cells == null || cells.isEmpty()) {
            return;
        }
        double radius = body.radius();
        PlanetGeometry geometry = new PlanetGeometry(radius);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.disableBlend();
        // Far to near (each one covers what's behind it), seen faces only.
        java.util.List<Object[]> boxes = new java.util.ArrayList<>();
        for (Map.Entry<Long, Landmarks.Cell> e : cells.entrySet()) {
            Landmarks.Cell c = e.getValue();
            if (c.maxY() < fromY) {
                continue;
            }
            double x0 = Landmarks.cellX(e.getKey()) * (double) Landmarks.CELL;
            double z0 = Landmarks.cellZ(e.getKey()) * (double) Landmarks.CELL;
            DVec3 up = up(geometry.latitude(z0 + Landmarks.CELL / 2.0), geometry.longitude(x0 + Landmarks.CELL / 2.0));
            if (up.dot(camera.sub(up)) <= 0) {
                continue; // round the far side
            }
            if (skipWithin > 0 && Math.acos(Math.max(-1, Math.min(1, up.dot(camera.normalize())))) * radius < skipWithin) {
                continue; // close enough that its real blocks are loaded and drawn
            }
            boxes.add(new Object[]{c, x0, z0, up, up.sub(camera).length(), e.getKey()});
        }
        if (boxes.isEmpty()) {
            return;
        }
        boxes.sort((p, q) -> Double.compare((double) q[4], (double) p[4]));
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (Object[] box : boxes) {
            Landmarks.Cell c = (Landmarks.Cell) box[0];
            double x0 = (double) box[1];
            double z0 = (double) box[2];
            DVec3 up = (DVec3) box[3];
            double bottom = (Math.max(fromY, c.minY() - 1) - 63) / radius;
            double top = (c.maxY() + 1 - 63) / radius;
            DVec3[][] corner = new DVec3[4][2];
            double[][] xz = {{x0, z0}, {x0 + Landmarks.CELL, z0}, {x0 + Landmarks.CELL, z0 + Landmarks.CELL}, {x0, z0 + Landmarks.CELL}};
            for (int i = 0; i < 4; i++) {
                DVec3 u = up(geometry.latitude(xz[i][1]), geometry.longitude(xz[i][0]));
                corner[i][0] = u.scale(1 + bottom);
                corner[i][1] = u.scale(1 + top);
            }
            float r = ((c.color() >> 16) & 255) / 255f;
            float g = ((c.color() >> 8) & 255) / 255f;
            float b = (c.color() & 255) / 255f;
            DVec3 centre = up.scale(1 + (bottom + top) / 2);
            // Outside walls first (the ones facing this way; against a neighbour, only what stands
            // above it), then the roof over them.
            long key = (long) box[5];
            int cx = Landmarks.cellX(key);
            int cz = Landmarks.cellZ(key);
            int[][] side = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}}; // the neighbour beyond each wall (north, east, south, west)
            for (int i = 0; i < 4; i++) {
                int j = (i + 1) % 4;
                Landmarks.Cell next = cells.get(((long) (cx + side[i][0]) << 32) ^ ((cz + side[i][1]) & 0xffffffffL));
                double from = bottom;
                if (next != null) {
                    from = Math.max(from, (next.maxY() + 1 - 63) / radius);
                }
                if (from >= top) {
                    continue; // hidden against a taller neighbour
                }
                DVec3 a0 = corner[i][1].scale((1 + from) / (1 + top));
                DVec3 b0 = corner[j][1].scale((1 + from) / (1 + top));
                DVec3 mid = a0.add(b0).add(corner[i][1]).add(corner[j][1]).scale(0.25);
                DVec3 out = mid.sub(centre);
                out = out.sub(up.scale(out.dot(up))).normalize();
                face(builder, modelView, camera, sun, out, mid, r, g, b, a0, b0, corner[j][1], corner[i][1]);
            }
            DVec3 roof = up.scale(1 + top);
            face(builder, modelView, camera, sun, up, roof, r, g, b, corner[0][1], corner[1][1], corner[2][1], corner[3][1]);
        }
        RenderSystem.setShaderColor(1, 1, 1, 1); // whatever tint the sky drew with before
        BufferUploader.drawWithShader(builder.buildOrThrow());
    }

    /** One face if it's turned toward the camera, lit by the Sun (a little light even on the dark side). */
    private static void face(BufferBuilder builder, Matrix4f m, DVec3 camera, DVec3 sun, DVec3 normal, DVec3 at,
                             float r, float g, float b, DVec3 p0, DVec3 p1, DVec3 p2, DVec3 p3) {
        if (normal.dot(camera.sub(at)) <= 0) {
            return;
        }
        // The sky lights the sides a little too; the Sun does the rest.
        float light = (float) (0.42 + 0.58 * Math.max(0, normal.dot(sun)));
        for (DVec3 p : new DVec3[]{p0, p1, p2, p3}) {
            builder.addVertex(m, (float) p.x(), (float) p.y(), (float) p.z()).setColor(r * light, g * light, b * light, 1);
        }
    }

    /** Up at a latitude and longitude, in the planet's frame (meridian, east90, north). */
    private static DVec3 up(double lat, double lon) {
        double c = Math.cos(lat);
        return new DVec3(c * Math.cos(lon), c * Math.sin(lon), Math.sin(lat));
    }
}
