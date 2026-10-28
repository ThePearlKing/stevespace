package com.thepearlking.stevespace.client.cloud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.thepearlking.stevespace.client.SpaceShaders;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Storm clouds: each one a heaped-up cloud standing on its flat base, drawn as an upright card
 * turned round to face the camera (the cloud shader gives it its lumpy shape), far to near.
 */
public final class CloudRenderer {

    /**
     * One cloud: the middle of its base (relative to the eye, world axes), half its width, its
     * height, colour and opacity, whether it spreads into an anvil, and a seed for its shape.
     */
    public record Cloud(double x, double y, double z, float halfWidth, float height, float r, float g, float b, float a,
                        boolean anvil, int seed) {
    }

    private CloudRenderer() {
    }

    /**
     * Draws clouds shrunk about the eye by {@code scale} (1 in the world; the far terrain's scale
     * out past it), against whatever depth is there. {@code projection} null keeps the current one.
     */
    public static void draw(List<Cloud> clouds, Matrix4f toView, Matrix4f projection, float scale, PuffRenderer.Light light, float time) {
        if (clouds.isEmpty() || SpaceShaders.cloud() == null) {
            return;
        }
        List<Cloud> sorted = new ArrayList<>(clouds);
        sorted.sort((p, q) -> Double.compare(q.x() * q.x() + q.z() * q.z(), p.x() * p.x() + p.z() * p.z()));
        Vector3f sunView = toView.transformDirection(new Vector3f(light.sun())).normalize();
        Vector3f upView = toView.transformDirection(new Vector3f(0, 1, 0)).normalize();
        var shader = SpaceShaders.cloud();
        RenderSystem.setShader(SpaceShaders::cloud);
        shader.safeGetUniform("SunView").set(sunView.x, sunView.y, sunView.z);
        shader.safeGetUniform("UpView").set(upView.x, upView.y, upView.z);
        shader.safeGetUniform("SunLight").set(light.sunColour().x, light.sunColour().y, light.sunColour().z);
        shader.safeGetUniform("Ambient").set(light.ambient().x, light.ambient().y, light.ambient().z);
        shader.safeGetUniform("HazeColor").set(light.haze().x, light.haze().y, light.haze().z);
        shader.safeGetUniform("HazeDistance").set(light.hazeDistance());
        shader.safeGetUniform("AnimTime").set(time);
        if (projection != null) {
            RenderSystem.backupProjectionMatrix();
            RenderSystem.setProjectionMatrix(projection, com.mojang.blaze3d.vertex.VertexSorting.DISTANCE_TO_ORIGIN);
        }
        // Past the far clip plane a cloud is drawn nearer and smaller, the same size on screen.
        double reach = net.minecraft.client.Minecraft.getInstance().gameRenderer.getDepthFar() * 0.8;
        Matrix4fStack stack = RenderSystem.getModelViewStack();
        stack.pushMatrix();
        stack.identity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
        for (Cloud c : sorted) {
            double horizontal = Math.hypot(c.x(), c.z());
            if (horizontal < 1e-3) {
                continue;
            }
            double d = Math.sqrt(c.x() * c.x() + c.y() * c.y() + c.z() * c.z()) * scale;
            double pull = scale * (projection == null && d > reach ? reach / d : 1);
            // Square on to the line of sight, upright.
            float rx = (float) (-c.z() / horizontal * c.halfWidth() * pull);
            float rz = (float) (c.x() / horizontal * c.halfWidth() * pull);
            float h = (float) (c.height() * pull);
            float cx = (float) (c.x() * pull);
            float cy = (float) (c.y() * pull);
            float cz = (float) (c.z() * pull);
            float s = Math.floorMod(c.seed(), 997) * 10f;
            float f = Math.min(990, Math.round(4 / pull)) * 10f;
            float aspect = Math.min(1, c.height() / c.halfWidth() / 4);
            float anvil = c.anvil() ? 1 : 0;
            vertex(builder, toView, cx - rx, cy, cz - rz, -1 + s, f, c, aspect, anvil);
            vertex(builder, toView, cx + rx, cy, cz + rz, 1 + s, f, c, aspect, anvil);
            vertex(builder, toView, cx + rx, cy + h, cz + rz, 1 + s, 1 + f, c, aspect, anvil);
            vertex(builder, toView, cx - rx, cy + h, cz - rz, -1 + s, 1 + f, c, aspect, anvil);
        }
        BufferUploader.drawWithShader(builder.buildOrThrow());
        stack.popMatrix();
        RenderSystem.applyModelViewMatrix();
        if (projection != null) {
            RenderSystem.restoreProjectionMatrix();
        }
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
    }

    private static void vertex(BufferBuilder b, Matrix4f view, float x, float y, float z, float u, float v, Cloud c,
                               float aspect, float anvil) {
        b.addVertex(view, x, y, z).setUv(u, v).setColor(c.r(), c.g(), c.b(), c.a()).setNormal(aspect, anvil, 0);
    }
}
