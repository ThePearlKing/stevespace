package com.thepearlking.stevespace.client.planet;

import com.thepearlking.stevespace.client.ClientUniverse;
import com.thepearlking.stevespace.config.ClientConfig;
import com.thepearlking.stevespace.universe.CelestialBody;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * On a small world (an asteroid like Hygiea) the ground falls away so fast that the flat chunks
 * round you reach past the real horizon. There the loaded chunks are drawn bent over the world's
 * sphere, the same way the distant ground is (far_terrain.vsh), so the ground beyond the horizon
 * drops out of sight behind it; mobs, players and items on it are bent the same way, so nothing
 * floats. Done in Minecraft's own terrain and entity shaders (overridden under
 * assets/minecraft/shaders/core; only in the 3D view, never on the screen), off everywhere else.
 */
public final class WorldCurve {

    /** Worlds smaller than this (blocks of radius) are drawn curved. */
    public static final double SMALL = 1200;

    private WorldCurve() {
    }

    /** The radius the chunks are bent over here, or 0 for flat. */
    public static double radius() {
        var mc = Minecraft.getInstance();
        if (mc.level == null || !ClientConfig.planetView()
                || com.thepearlking.stevespace.world.SpaceDimension.isSpace(mc.level)) {
            return 0;
        }
        CelestialBody body = ClientUniverse.currentBody();
        if (body == null || body.radius() >= SMALL || PlanetMaps.terrainOf(body) == null) {
            return 0;
        }
        return body.radius();
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) {
            return;
        }
        double radius = radius();
        float curvature = radius > 0 ? (float) (1 / radius) : 0f;
        // Height above the ground's datum (y 63), as the distant ground measures it.
        float altitude = (float) (event.getCamera().getPosition().y - 63);
        // The ground, and everything standing on it (mobs, players, items, block entities), bent alike.
        for (ShaderInstance shader : new ShaderInstance[]{GameRenderer.getRendertypeSolidShader(),
                GameRenderer.getRendertypeCutoutShader(), GameRenderer.getRendertypeCutoutMippedShader(),
                GameRenderer.getRendertypeTranslucentShader(),
                GameRenderer.getRendertypeEntitySolidShader(), GameRenderer.getRendertypeEntityCutoutShader(),
                GameRenderer.getRendertypeEntityCutoutNoCullShader(), GameRenderer.getRendertypeEntityCutoutNoCullZOffsetShader(),
                GameRenderer.getRendertypeItemEntityTranslucentCullShader(), GameRenderer.getRendertypeEntityTranslucentCullShader(),
                GameRenderer.getRendertypeEntityTranslucentShader(), GameRenderer.getRendertypeEntityTranslucentEmissiveShader(),
                GameRenderer.getRendertypeEntitySmoothCutoutShader(), GameRenderer.getRendertypeEntityDecalShader(),
                GameRenderer.getRendertypeEntityNoOutlineShader(), GameRenderer.getRendertypeEntityShadowShader(),
                GameRenderer.getRendertypeEntityAlphaShader(), GameRenderer.getRendertypeEyesShader(),
                GameRenderer.getRendertypeArmorCutoutNoCullShader(), GameRenderer.getRendertypeArmorEntityGlintShader(),
                GameRenderer.getRendertypeEntityGlintShader(), GameRenderer.getRendertypeEntityGlintDirectShader()}) {
            if (shader != null) {
                shader.safeGetUniform("PlanetCurvature").set(curvature);
                shader.safeGetUniform("PlanetAltitude").set(altitude);
            }
        }
    }
}
