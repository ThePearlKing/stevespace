package com.thepearlking.stevespace.client.sky;

import com.thepearlking.stevespace.StevesSpace;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.FogType;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Vector3f;

/** Hooks the planet sky into Minecraft: sky effects, fog colour and fog distance. */
public final class SkyEvents {

    /** Height above sea level where fog starts stretching out so the ground stays visible. */
    private static final double FOG_STRETCH_START = 60;

    private SkyEvents() {
    }

    /** Mod bus. */
    public static void onRegisterEffects(RegisterDimensionSpecialEffectsEvent event) {
        PlanetSkyEffects effects = new PlanetSkyEffects();
        event.register(ResourceLocation.fromNamespaceAndPath(StevesSpace.MODID, "earth"), effects);
        event.register(ResourceLocation.fromNamespaceAndPath(StevesSpace.MODID, "planet"), effects);
        event.register(ResourceLocation.fromNamespaceAndPath(StevesSpace.MODID, "space"), effects);
    }

    /** Distant terrain fades into the real horizon colour. */
    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || event.getCamera().getFluidInCamera() != FogType.NONE) {
            return;
        }
        SkyState state = SkyState.compute(event.getCamera(), mc.level, (float) event.getPartialTick());
        if (state == null) {
            return;
        }
        Vector3f look = event.getCamera().getLookVector();
        float[] color = state.horizonColor(look.x(), look.z());
        BodyAir.View air = BodyAir.at(state.home(), state.altitude());
        if (air != null && air.cloud() > 0) {
            float[] cloud = BodyAir.lit(air.cloudColor(), state.sunDir().y());
            for (int i = 0; i < 3; i++) {
                color[i] += (cloud[i] - color[i]) * air.cloud();
            }
        }
        event.setRed(color[0]);
        event.setGreen(color[1]);
        event.setBlue(color[2]);
    }

    /**
     * High above the ground, vanilla fog (sized for standing on it) would hide the terrain below
     * you; stretch it with altitude so the chunks stay clear and blend into the planet view.
     */
    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || event.getType() != FogType.NONE || event.getMode() != net.minecraft.client.renderer.FogRenderer.FogMode.FOG_TERRAIN) {
            return;
        }
        SkyState state = SkyState.compute(event.getCamera(), mc.level, (float) event.getPartialTick());
        if (state == null) {
            return;
        }
        // Thick air (Venus's haze and cloud deck, Titan's smog) sets its own distance, wherever you are.
        BodyAir.View air = state.palette() != null || com.thepearlking.stevespace.client.space.ClientSpace.mapModeActive()
                ? BodyAir.at(state.home(), state.altitude()) : null;
        if (air != null) {
            event.setNearPlaneDistance(air.near());
            event.setFarPlaneDistance(Math.min(event.getFarPlaneDistance() * 6, air.far()));
            event.setCanceled(true);
            return;
        }
        if (com.thepearlking.stevespace.world.SpaceDimension.isSpace(mc.level)) {
            // No air, no fog.
            event.setNearPlaneDistance(event.getFarPlaneDistance() * 4);
            event.setFarPlaneDistance(event.getFarPlaneDistance() * 5);
            event.setCanceled(true);
            return;
        }
        double height = event.getCamera().getPosition().y - SkyState.SEA_LEVEL;
        if (height <= FOG_STRETCH_START) {
            return;
        }
        float stretch = (float) (1 + (height - FOG_STRETCH_START) / 80.0);
        event.setNearPlaneDistance(event.getNearPlaneDistance() * stretch);
        event.setFarPlaneDistance(event.getFarPlaneDistance() * stretch);
        event.setCanceled(true);
    }
}
