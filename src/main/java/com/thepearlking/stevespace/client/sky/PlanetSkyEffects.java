package com.thepearlking.stevespace.client.sky;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Sky and fog behaviour for every planet surface dimension. The real work is in
 * {@link SkyRenderer}; this class only plugs it into Minecraft.
 */
public final class PlanetSkyEffects extends DimensionSpecialEffects {

    public PlanetSkyEffects() {
        super(192.0f, true, SkyType.NORMAL, false, false);
    }

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, Camera camera, Matrix4f frameMatrix,
                             boolean isFoggy, Runnable setupFog) {
        return SkyRenderer.render(level, partialTick, camera, frameMatrix);
    }

    /**
     * Minecraft's flat cloud layer only belongs on Earth, seen from below or just above it.
     * Higher up, the planet's cloud shell (PlanetClouds) shows the clouds instead.
     */
    @Override
    public boolean renderClouds(ClientLevel level, int ticks, float partialTick, com.mojang.blaze3d.vertex.PoseStack poseStack,
                                double camX, double camY, double camZ, Matrix4f projectionMatrix) {
        var body = com.thepearlking.stevespace.client.ClientUniverse.currentBody();
        boolean earth = body != null && body.id().equals("earth") && !com.thepearlking.stevespace.world.SpaceDimension.isSpace(level)
                && !com.thepearlking.stevespace.client.space.ClientSpace.mapModeActive();
        return !earth || camY > com.thepearlking.stevespace.client.planet.PlanetClouds.HANDOVER_Y;
    }

    @Override
    public float[] getSunriseColor(float timeOfDay, float partialTicks) {
        return null; // sunsets are part of our own atmosphere colours
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
        return fogColor;
    }

    /** Daylight on worlds under thick air is dimmer and coloured (orange on Venus and Titan). */
    @Override
    public void adjustLightmapColors(net.minecraft.client.multiplayer.ClientLevel level, float partialTicks, float skyDarken,
                                     float blockLightRedFlicker, float skyLight, int pixelX, int pixelY, org.joml.Vector3f colors) {
        var body = com.thepearlking.stevespace.client.ClientUniverse.isSynced()
                ? com.thepearlking.stevespace.client.ClientUniverse.currentBody() : null;
        float[] tint = body == null ? null : BodyAir.daylight(body);
        if (tint == null) {
            return;
        }
        // Tint the part of the light that comes from the sky, not torches.
        float block = pixelX / 15f;
        float weight = skyLight / Math.max(0.001f, skyLight + block * 1.5f);
        colors.set(colors.x() * (1 + (tint[0] - 1) * weight), colors.y() * (1 + (tint[1] - 1) * weight),
                colors.z() * (1 + (tint[2] - 1) * weight));
    }

    @Override
    public boolean isFoggyAt(int x, int z) {
        return false;
    }
}
