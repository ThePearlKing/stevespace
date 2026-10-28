package com.thepearlking.stevespace.client.cloud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.thepearlking.stevespace.client.SpaceShaders;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Soft, lit clouds made of puffs: mushroom clouds, rocket exhaust columns, dust. Each puff is a
 * camera-facing ball with a billowing edge (the puff shader lights it), drawn far to near with
 * the world's depth so hills and buildings stand in front of them.
 */
public final class PuffRenderer {

    /** One puff: centre (world), radius (blocks), colour and opacity, glow colour (0 = none), and a seed for its shape. */
    public record Puff(double x, double y, double z, float radius, float r, float g, float b, float a,
                       float glowR, float glowG, float glowB, int seed) {
    }

    /** How the sky lights the clouds this frame. */
    public record Light(Vector3f sun, Vector3f sunColour, Vector3f ambient, Vector3f haze, float hazeDistance) {
    }

    private PuffRenderer() {
    }

    public static void draw(List<Puff> puffs, Matrix4f view, Camera camera, Light light, float time) {
        if (puffs.isEmpty() || SpaceShaders.puff() == null) {
            return;
        }
        Vec3 eye = camera.getPosition();
        // Past the far clip plane a puff is drawn nearer and smaller, the same size on screen (it
        // still keeps its real distance for the haze).
        double reach = net.minecraft.client.Minecraft.getInstance().gameRenderer.getDepthFar() * 0.8;
        List<Puff> sorted = new ArrayList<>(puffs);
        sorted.sort((p, q) -> Double.compare(dist2(q, eye), dist2(p, eye)));
        Vector3f left = camera.getLeftVector();
        Vector3f up = camera.getUpVector();
        // Lighting directions in view space (the puffs' shading is worked out there).
        Vector3f sunView = view.transformDirection(new Vector3f(light.sun())).normalize();
        Vector3f upView = view.transformDirection(new Vector3f(0, 1, 0)).normalize();
        var shader = SpaceShaders.puff();
        RenderSystem.setShader(SpaceShaders::puff);
        shader.safeGetUniform("SunView").set(sunView.x, sunView.y, sunView.z);
        shader.safeGetUniform("UpView").set(upView.x, upView.y, upView.z);
        shader.safeGetUniform("SunLight").set(light.sunColour().x, light.sunColour().y, light.sunColour().z);
        shader.safeGetUniform("Ambient").set(light.ambient().x, light.ambient().y, light.ambient().z);
        shader.safeGetUniform("HazeColor").set(light.haze().x, light.haze().y, light.haze().z);
        shader.safeGetUniform("HazeDistance").set(light.hazeDistance());
        shader.safeGetUniform("AnimTime").set(time);
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
        for (Puff p : sorted) {
            double dx = p.x() - eye.x;
            double dy = p.y() - eye.y;
            double dz = p.z() - eye.z;
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double pull = d > reach ? reach / d : 1;
            float cx = (float) (dx * pull);
            float cy = (float) (dy * pull);
            float cz = (float) (dz * pull);
            float radius = (float) (p.radius() * pull);
            // Camera-facing: "left" points left, so right = -left.
            float rx = -left.x * radius;
            float ry = -left.y * radius;
            float rz = -left.z * radius;
            float ux = up.x * radius;
            float uy = up.y * radius;
            float uz = up.z * radius;
            float s = (p.seed() % 997) * 10f;
            float f = Math.min(990, Math.round(4 / pull)) * 10f;
            float gx = p.glowR() * 2 - 1;
            float gy = p.glowG() * 2 - 1;
            float gz = p.glowB() * 2 - 1;
            vertex(builder, view, cx - rx - ux, cy - ry - uy, cz - rz - uz, -1 + s, -1 + f, p, gx, gy, gz);
            vertex(builder, view, cx + rx - ux, cy + ry - uy, cz + rz - uz, 1 + s, -1 + f, p, gx, gy, gz);
            vertex(builder, view, cx + rx + ux, cy + ry + uy, cz + rz + uz, 1 + s, 1 + f, p, gx, gy, gz);
            vertex(builder, view, cx - rx + ux, cy - ry + uy, cz - rz + uz, -1 + s, 1 + f, p, gx, gy, gz);
        }
        BufferUploader.drawWithShader(builder.buildOrThrow());
        stack.popMatrix();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
    }

    /**
     * Puffs out in the sky, seen from far off (a cloud on a planet seen from orbit): positions are
     * already relative to the eye in the sky's axes and scaled into the sky, {@code toView} turns
     * those axes to the view, {@code upDir} is "up" at the cloud (for its shading).
     */
    public static void drawSky(List<Puff> puffs, Matrix4f toView, Vector3f upDir, Light light, float time) {
        if (puffs.isEmpty() || SpaceShaders.puff() == null) {
            return;
        }
        List<Puff> sorted = new ArrayList<>(puffs);
        sorted.sort((p, q) -> Double.compare(q.x() * q.x() + q.y() * q.y() + q.z() * q.z(), p.x() * p.x() + p.y() * p.y() + p.z() * p.z()));
        // The screen's right and up, in the sky's axes (for facing the puffs to the camera).
        Matrix4f back = new Matrix4f(toView).invert();
        Vector3f right = back.transformDirection(new Vector3f(1, 0, 0)).normalize();
        Vector3f up = back.transformDirection(new Vector3f(0, 1, 0)).normalize();
        Vector3f sunView = toView.transformDirection(new Vector3f(light.sun())).normalize();
        Vector3f upView = toView.transformDirection(new Vector3f(upDir)).normalize();
        var shader = SpaceShaders.puff();
        RenderSystem.setShader(SpaceShaders::puff);
        shader.safeGetUniform("SunView").set(sunView.x, sunView.y, sunView.z);
        shader.safeGetUniform("UpView").set(upView.x, upView.y, upView.z);
        shader.safeGetUniform("SunLight").set(light.sunColour().x, light.sunColour().y, light.sunColour().z);
        shader.safeGetUniform("Ambient").set(light.ambient().x, light.ambient().y, light.ambient().z);
        shader.safeGetUniform("HazeColor").set(light.haze().x, light.haze().y, light.haze().z);
        shader.safeGetUniform("HazeDistance").set(1e9f);
        shader.safeGetUniform("AnimTime").set(time);
        Matrix4fStack stack = RenderSystem.getModelViewStack();
        stack.pushMatrix();
        stack.identity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
        for (Puff p : sorted) {
            float cx = (float) p.x();
            float cy = (float) p.y();
            float cz = (float) p.z();
            float rx = right.x * p.radius();
            float ry = right.y * p.radius();
            float rz = right.z * p.radius();
            float ux = up.x * p.radius();
            float uy = up.y * p.radius();
            float uz = up.z * p.radius();
            float s = (p.seed() % 997) * 10f;
            float f = 40f;
            float gx = p.glowR() * 2 - 1;
            float gy = p.glowG() * 2 - 1;
            float gz = p.glowB() * 2 - 1;
            vertex(builder, toView, cx - rx - ux, cy - ry - uy, cz - rz - uz, -1 + s, -1 + f, p, gx, gy, gz);
            vertex(builder, toView, cx + rx - ux, cy + ry - uy, cz + rz - uz, 1 + s, -1 + f, p, gx, gy, gz);
            vertex(builder, toView, cx + rx + ux, cy + ry + uy, cz + rz + uz, 1 + s, 1 + f, p, gx, gy, gz);
            vertex(builder, toView, cx - rx + ux, cy - ry + uy, cz - rz + uz, -1 + s, 1 + f, p, gx, gy, gz);
        }
        BufferUploader.drawWithShader(builder.buildOrThrow());
        stack.popMatrix();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
    }

    private static void vertex(BufferBuilder b, Matrix4f view, float x, float y, float z, float u, float v, Puff p,
                               float gx, float gy, float gz) {
        b.addVertex(view, x, y, z).setUv(u, v).setColor(p.r(), p.g(), p.b(), p.a()).setNormal(gx, gy, gz);
    }

    private static double dist2(Puff p, Vec3 eye) {
        double dx = p.x() - eye.x;
        double dy = p.y() - eye.y;
        double dz = p.z() - eye.z;
        return dx * dx + dy * dy + dz * dz;
    }
}
