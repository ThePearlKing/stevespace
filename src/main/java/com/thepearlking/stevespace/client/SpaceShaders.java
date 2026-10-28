package com.thepearlking.stevespace.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.thepearlking.stevespace.StevesSpace;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RegisterShadersEvent;

import java.io.IOException;

/** The mod's own GLSL shaders, loaded with the resource packs. */
public final class SpaceShaders {

    private static ShaderInstance skyGlow;
    private static ShaderInstance planetSurface;
    private static ShaderInstance planetAtmosphere;
    private static ShaderInstance farTerrain;
    private static ShaderInstance planetClouds;
    private static ShaderInstance sun;
    private static ShaderInstance aurora;
    private static ShaderInstance puff;
    private static ShaderInstance cloud;

    private SpaceShaders() {
    }

    /** Textured + coloured, with no alpha cutoff: for glows, stars and planet points. */
    public static ShaderInstance skyGlow() {
        return skyGlow;
    }

    /** Lit planet surface with haze, for planets seen from altitude or space. */
    public static ShaderInstance planetSurface() {
        return planetSurface;
    }

    /** Additive glowing rim of a planet's atmosphere. */
    public static ShaderInstance planetAtmosphere() {
        return planetAtmosphere;
    }

    /** Earth's cloud layer seen from above. */
    public static ShaderInstance planetClouds() {
        return planetClouds;
    }

    /** The Sun up close: surface, spots, flares, prominences and corona, ray traced. */
    public static ShaderInstance sun() {
        return sun;
    }

    /** Lit cloud puffs (mushroom clouds, exhaust columns, dust). */
    public static ShaderInstance puff() {
        return puff;
    }

    /** Storm clouds heaped up on a flat base. */
    public static ShaderInstance cloud() {
        return cloud;
    }

    /** Auroral curtains: rays, a sharp lower edge, colours by height. */
    public static ShaderInstance aurora() {
        return aurora;
    }

    /** The lit, curving ground beyond the loaded chunks. */
    public static ShaderInstance farTerrain() {
        return farTerrain;
    }

    /** Mod bus. */
    public static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                            ResourceLocation.fromNamespaceAndPath(StevesSpace.MODID, "sky_glow"), DefaultVertexFormat.POSITION_TEX_COLOR),
                    shader -> skyGlow = shader);
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                            ResourceLocation.fromNamespaceAndPath(StevesSpace.MODID, "planet_surface"), DefaultVertexFormat.POSITION_TEX),
                    shader -> planetSurface = shader);
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                            ResourceLocation.fromNamespaceAndPath(StevesSpace.MODID, "planet_atmosphere"), DefaultVertexFormat.POSITION_TEX),
                    shader -> planetAtmosphere = shader);
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                            ResourceLocation.fromNamespaceAndPath(StevesSpace.MODID, "far_terrain"), DefaultVertexFormat.POSITION_COLOR_NORMAL),
                    shader -> farTerrain = shader);
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                            ResourceLocation.fromNamespaceAndPath(StevesSpace.MODID, "planet_clouds"), DefaultVertexFormat.POSITION_TEX),
                    shader -> planetClouds = shader);
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                            ResourceLocation.fromNamespaceAndPath(StevesSpace.MODID, "sun"), DefaultVertexFormat.POSITION),
                    shader -> sun = shader);
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                            ResourceLocation.fromNamespaceAndPath(StevesSpace.MODID, "aurora"), DefaultVertexFormat.POSITION_TEX_COLOR),
                    shader -> aurora = shader);
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                            ResourceLocation.fromNamespaceAndPath(StevesSpace.MODID, "puff"), DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL),
                    shader -> puff = shader);
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                            ResourceLocation.fromNamespaceAndPath(StevesSpace.MODID, "cloud"), DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL),
                    shader -> cloud = shader);
        } catch (IOException e) {
            throw new IllegalStateException("Could not load Steve's Space shaders", e);
        }
    }
}
