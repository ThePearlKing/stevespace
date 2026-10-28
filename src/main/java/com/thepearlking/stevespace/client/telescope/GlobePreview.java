package com.thepearlking.stevespace.client.telescope;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.thepearlking.stevespace.client.ClientUniverse;
import com.thepearlking.stevespace.client.planet.PlanetMaps;
import com.thepearlking.stevespace.client.sky.BodyTextures;
import com.thepearlking.stevespace.universe.CelestialBody;
import com.thepearlking.stevespace.universe.DVec3;
import com.thepearlking.stevespace.universe.SkyView;
import com.thepearlking.stevespace.universe.SolarSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** A small lit globe of a body as it looks from here right now: its phase, its face, its rings. */
final class GlobePreview {

    private static final int LAT = 24;
    private static final int LON = 48;

    private GlobePreview() {
    }

    static void draw(GuiGraphics g, Vec3 eye, SkyCatalog.Entry e, float cx, float cy, float r) {
        SolarSystem system = ClientUniverse.system();
        double t = ClientUniverse.time();
        SkyView sky = ClientUniverse.telescopeSky(eye);
        if (sky == null) {
            return;
        }
        if (e.ship() != null) {
            ResourceLocation picture = TelescopeShips.texture(e.ship(), TelescopeShips.blur(e.angularRadius()));
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            float k = 0.25f + 0.75f * (float) e.phase();
            RenderSystem.setShaderColor(k, k, k, 1);
            int size = Math.round(r * 2);
            g.blit(picture, Math.round(cx - r), Math.round(cy - r), 0, 0, size, size, size, size);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.disableBlend();
            return;
        }
        CelestialBody body = e.body();
        SolarSystem.BodyFrame frame = system.frameAt(body, t);
        DVec3 sunFromBody = system.absolutePosition(body, t).scale(-1).normalize();
        // Screen axes, as the body sits in the sky: up is toward the zenith.
        DVec3 d = e.direction();
        DVec3 up = new DVec3(0, 1, 0).sub(d.scale(d.y()));
        up = up.length() < 1e-6 ? new DVec3(0, 0, -1) : up.normalize();
        DVec3 right = d.cross(up).normalize();
        Matrix4f matrix = g.pose().last().pose();
        float ringScale = body.rings() == null ? 0 : (float) (r / body.radius());

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        if (body.rings() != null) {
            rings(matrix, sky, frame, body, d, right, up, cx, cy, ringScale, r, false);
        }
        ResourceLocation texture = BodyTextures.forSky(body);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, texture);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        int quads = 0;
        for (int i = 0; i < LAT; i++) {
            double lat0 = Math.PI / 2 - i * Math.PI / LAT;
            double lat1 = Math.PI / 2 - (i + 1) * Math.PI / LAT;
            for (int j = 0; j < LON; j++) {
                double lon0 = -Math.PI + j * 2 * Math.PI / LON;
                double lon1 = -Math.PI + (j + 1) * 2 * Math.PI / LON;
                double[][] c = {{lat0, lon0}, {lat1, lon0}, {lat1, lon1}, {lat0, lon1}};
                boolean any = false;
                DVec3[] local = new DVec3[4];
                for (int k = 0; k < 4; k++) {
                    local[k] = sky.toLocal(frame.up(c[k][0], c[k][1]));
                    any |= local[k].dot(d) < 0;
                }
                if (!any) {
                    continue;
                }
                for (int k = 0; k < 4; k++) {
                    DVec3 n = frame.up(c[k][0], c[k][1]);
                    double sun = n.dot(sunFromBody);
                    float light = (float) Math.max(0.035, Math.min(1, (sun + 0.04) / 0.22));
                    // Squash the far side onto the rim so nothing behind shows.
                    double toward = -local[k].dot(d);
                    double x = local[k].dot(right);
                    double y = local[k].dot(up);
                    if (toward < 0) {
                        double l = Math.sqrt(x * x + y * y);
                        x /= l;
                        y /= l;
                    }
                    b.addVertex(matrix, cx + (float) (x * r), cy - (float) (y * r), 0)
                            .setUv((float) ((c[k][1] + Math.PI) / (2 * Math.PI)), (float) ((Math.PI / 2 - c[k][0]) / Math.PI))
                            .setColor(light, light, light, 1f);
                }
                quads++;
            }
        }
        MeshData mesh = b.build();
        if (mesh != null && quads > 0) {
            BufferUploader.drawWithShader(mesh);
        }
        if (body.rings() != null) {
            rings(matrix, sky, frame, body, d, right, up, cx, cy, ringScale, r, true);
        }
        RenderSystem.enableCull();
    }

    /** The rings as an ellipse: the half behind the planet first, the front half over it. */
    private static void rings(Matrix4f matrix, SkyView sky, SolarSystem.BodyFrame frame, CelestialBody body, DVec3 d,
                              DVec3 right, DVec3 up, float cx, float cy, float scale, float r, boolean front) {
        CelestialBody.Rings rings = body.rings();
        DVec3 a = sky.toLocal(frame.meridian());
        DVec3 bb = sky.toLocal(frame.east90());
        int c = rings.color();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int segments = 128;
        int quads = 0;
        double inner = rings.innerRadius() * scale;
        double outer = rings.outerRadius() * scale;
        for (int i = 0; i < segments; i++) {
            double t0 = i * 2 * Math.PI / segments;
            double t1 = (i + 1) * 2 * Math.PI / segments;
            DVec3 d0 = a.scale(Math.cos(t0)).add(bb.scale(Math.sin(t0)));
            DVec3 d1 = a.scale(Math.cos(t1)).add(bb.scale(Math.sin(t1)));
            boolean isFront = d0.add(d1).dot(d) < 0;
            if (isFront != front) {
                continue;
            }
            float alpha = (float) rings.opacity();
            float red = ((c >> 16) & 255) / 255f;
            float green = ((c >> 8) & 255) / 255f;
            float blue = (c & 255) / 255f;
            DVec3[] p = {d0.scale(inner), d0.scale(outer), d1.scale(outer), d1.scale(inner)};
            for (DVec3 q : p) {
                b.addVertex(matrix, cx + (float) q.dot(right), cy - (float) q.dot(up), 0).setColor(red, green, blue, alpha);
            }
            quads++;
        }
        MeshData mesh = b.build();
        if (mesh != null && quads > 0) {
            BufferUploader.drawWithShader(mesh);
        }
    }
}
