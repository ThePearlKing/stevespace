package com.thepearlking.stevespace.world.planet;

/**
 * A biome as the pure terrain code sees it: which Minecraft biome to use and what colour the
 * ground looks from far away (orbit texture, LOD terrain, map tool).
 */
public interface SurfaceBiome {

    /** Namespaced biome id, e.g. {@code minecraft:plains} or {@code stevespace:lunar_highlands}. */
    String biomeId();

    /** Colour of this biome seen from altitude, 0xRRGGBB. */
    int mapColor();

    /** True for biomes whose surface is open water (oceans, rivers). */
    default boolean isWater() {
        return false;
    }
}
