package com.thepearlking.stevespace.world.planet;

/**
 * The shape and climate of a planet's surface as a pure function of position.
 *
 * <p>This is the single source of truth for a body's surface: the chunk generator builds blocks
 * from it, the client draws the far-away LOD terrain and the orbital view texture from it, and
 * the map tool renders it to PNG. Implementations must be deterministic and thread safe
 * (chunk generation runs on worker threads).
 */
public interface PlanetTerrain {

    PlanetGeometry geometry();

    /** Water surface height, or {@link Integer#MIN_VALUE} when the body has no liquid oceans. */
    int seaLevel();

    default boolean hasOceans() {
        return seaLevel() != Integer.MIN_VALUE;
    }

    /** Fills {@code out} with the surface at block position (x, z). Slope is left untouched. */
    void sample(double x, double z, TerrainSample out);

    /**
     * Colour of the surface at this column seen from altitude, including water, ice and a hint
     * of relief. Used for the orbit texture and the LOD terrain.
     */
    default int surfaceColor(TerrainSample s) {
        return s.biome.mapColor();
    }
}
