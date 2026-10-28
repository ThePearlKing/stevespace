package com.thepearlking.stevespace.client.planet;

import com.mojang.blaze3d.platform.NativeImage;
import com.thepearlking.stevespace.StevesSpace;
import com.thepearlking.stevespace.universe.CelestialBody;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Earth's clouds seen from above: Minecraft's own cloud pattern wrapped round the globe at the
 * height the clouds float (y = 192), each texel one 12-block cloud cell, drifting the way the
 * clouds overhead drift. Mipmapped so they stay smooth from orbit.
 */
public final class PlanetClouds {

    /** Vanilla cloud height (bottom of the layer). */
    public static final double CLOUD_Y = 192;
    /** Above this the flat vanilla layer gives way to the shell. */
    public static final double HANDOVER_Y = 200;
    /** Blocks per cloud cell (one pixel of vanilla's clouds texture). */
    public static final double CELL = 12;

    private static ResourceLocation texture;
    private static int width;
    private static boolean failed;

    private PlanetClouds() {
    }

    /** True for the worlds that have Minecraft's water clouds. */
    public static boolean has(CelestialBody body) {
        return body.id().equals("earth");
    }

    /** Fraction of the texture width the clouds have drifted (vanilla: 0.03 blocks per tick east). */
    public static float drift(double ticks) {
        double blocks = ticks * 0.03;
        return width == 0 ? 0 : (float) ((blocks / CELL / width) % 1.0);
    }

    /** The cloud map for {@code body}, built on first use (null if it can't be). */
    public static ResourceLocation texture(CelestialBody body) {
        if (texture != null || failed) {
            return texture;
        }
        try {
            var resource = Minecraft.getInstance().getResourceManager()
                    .getResourceOrThrow(ResourceLocation.withDefaultNamespace("textures/environment/clouds.png"));
            NativeImage tile;
            try (var in = resource.open()) {
                tile = NativeImage.read(in);
            }
            int t = tile.getWidth();
            // The whole planet's width in cloud cells, rounded to whole tiles so it wraps seamlessly.
            double circumference = 2 * Math.PI * body.radius();
            int tiles = Math.max(1, (int) Math.round(circumference / CELL / t));
            width = Math.min(8192, tiles * t);
            int height = width / 2;
            NativeImage map = new NativeImage(width, height, true);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    map.setPixelRGBA(x, y, tile.getPixelRGBA(x % t, y % t));
                }
            }
            tile.close();
            DynamicTexture dynamic = new DynamicTexture(map);
            texture = ResourceLocation.fromNamespaceAndPath(StevesSpace.MODID, "planet_clouds/" + body.id());
            Minecraft.getInstance().getTextureManager().register(texture, dynamic);
            dynamic.bind();
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        } catch (Exception e) {
            StevesSpace.LOGGER.warn("No planet clouds: {}", e.toString());
            failed = true;
        }
        return texture;
    }
}
