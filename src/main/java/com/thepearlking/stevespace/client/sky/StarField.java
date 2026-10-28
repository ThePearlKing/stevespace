package com.thepearlking.stevespace.client.sky;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.thepearlking.stevespace.universe.DVec3;

import java.util.Random;

/**
 * About 9000 stars fixed on the celestial sphere (the ecliptic frame), so they wheel overhead
 * as the planet turns and look the same from every planet — exactly like the real sky.
 * Stars cluster toward the Milky Way's plane. Built once into a vertex buffer.
 */
public final class StarField {

    private static final int STAR_COUNT = 9000;
    private static final float DISTANCE = 100;
    /** Galactic north pole in ecliptic coordinates (longitude 180°, latitude 29.8°). */
    private static final DVec3 GALACTIC_POLE = fromEcliptic(Math.toRadians(180), Math.toRadians(29.8));

    private VertexBuffer buffer;

    public VertexBuffer buffer() {
        if (buffer == null) {
            buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            buffer.bind();
            buffer.upload(build());
            VertexBuffer.unbind();
        }
        return buffer;
    }

    private com.mojang.blaze3d.vertex.MeshData build() {
        Random random = new Random(0x5737L);
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        DVec3 planeA = GALACTIC_POLE.cross(DVec3.Z).normalize();
        DVec3 planeB = GALACTIC_POLE.cross(planeA).normalize();
        for (int i = 0; i < STAR_COUNT; i++) {
            DVec3 dir;
            if (random.nextFloat() < 0.45f) {
                // Milky Way: concentrated near the galactic plane.
                double longitude = random.nextDouble() * 2 * Math.PI;
                double latitude = random.nextGaussian() * Math.toRadians(7);
                dir = planeA.scale(Math.cos(latitude) * Math.cos(longitude))
                        .add(planeB.scale(Math.cos(latitude) * Math.sin(longitude)))
                        .add(GALACTIC_POLE.scale(Math.sin(latitude)));
            } else {
                dir = randomDirection(random);
            }
            // Many faint stars, few bright ones (roughly N ∝ 10^(0.45·m)).
            double magnitude = 6.6 - Math.log10(1 + random.nextDouble() * 1e3) * 2.6;
            addStar(builder, dir.normalize(), magnitude, random);
        }
        return builder.buildOrThrow();
    }

    private static void addStar(BufferBuilder builder, DVec3 dir, double magnitude, Random random) {
        float size = (float) Math.min(0.75, 0.14 + 0.085 * Math.max(0, 4.2 - magnitude));
        float brightness = (float) Math.max(0.12, Math.min(1, (6.8 - magnitude) / 5.5));
        float[] color = starColor(random.nextDouble());

        DVec3 helper = Math.abs(dir.z()) < 0.9 ? DVec3.Z : DVec3.X;
        DVec3 u = dir.cross(helper).normalize().scale(size);
        DVec3 v = dir.cross(u).normalize().scale(size);
        DVec3 c = dir.scale(DISTANCE);
        vertex(builder, c.sub(u).sub(v), 0, 0, color, brightness);
        vertex(builder, c.add(u).sub(v), 1, 0, color, brightness);
        vertex(builder, c.add(u).add(v), 1, 1, color, brightness);
        vertex(builder, c.sub(u).add(v), 0, 1, color, brightness);
    }

    private static void vertex(BufferBuilder builder, DVec3 p, float u, float v, float[] color, float alpha) {
        builder.addVertex((float) p.x(), (float) p.y(), (float) p.z()).setUv(u, v).setColor(color[0], color[1], color[2], alpha);
    }

    /** Star colours from hot blue-white to cool orange, weighted like the real night sky. */
    private static float[] starColor(double r) {
        if (r < 0.10) {
            return new float[]{0.72f, 0.80f, 1.0f};
        }
        if (r < 0.55) {
            return new float[]{0.95f, 0.96f, 1.0f};
        }
        if (r < 0.80) {
            return new float[]{1.0f, 0.96f, 0.86f};
        }
        if (r < 0.95) {
            return new float[]{1.0f, 0.86f, 0.68f};
        }
        return new float[]{1.0f, 0.74f, 0.55f};
    }

    private static DVec3 randomDirection(Random random) {
        double z = random.nextDouble() * 2 - 1;
        double angle = random.nextDouble() * 2 * Math.PI;
        double r = Math.sqrt(1 - z * z);
        return new DVec3(r * Math.cos(angle), r * Math.sin(angle), z);
    }

    private static DVec3 fromEcliptic(double longitude, double latitude) {
        return new DVec3(Math.cos(latitude) * Math.cos(longitude), Math.cos(latitude) * Math.sin(longitude), Math.sin(latitude));
    }

    public void close() {
        if (buffer != null) {
            buffer.close();
            buffer = null;
        }
    }
}
