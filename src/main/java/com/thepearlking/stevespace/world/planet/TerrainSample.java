package com.thepearlking.stevespace.world.planet;

/**
 * Everything the terrain function knows about one surface column. Mutable and reused so the
 * chunk generator, LOD renderer and map tool can sample millions of columns without garbage.
 */
public final class TerrainSample {

    /** Height of the solid ground surface in blocks (the y of the top solid block, fractional). */
    public double height;
    /** Thickness in blocks of glacier ice on top of the ground (polar ice sheets). */
    public double iceThickness;
    /** True when water should freeze over at the surface (sea ice, frozen rivers and lakes). */
    public boolean frozenWater;
    /** How many blocks of the water surface are frozen when {@link #frozenWater} is set. */
    public double seaIceDepth;
    /** Climate, both roughly in [-1, 1] (temperature) and [0, 1] (humidity). */
    public double temperature;
    public double humidity;
    /** 0 outside rivers, rising to 1 in the middle of a river channel. */
    public double river;
    /** Steepness of the ground, 0 = flat; computed by the caller when needed (see TerrainSampler). */
    public double slope;
    public SurfaceBiome biome;
    /** A second surface some worlds have under the first (Europa's ocean top), or 0. */
    public double under;
    /** The floor beneath that (Europa's sea floor), or 0. */
    public double floor;

    /** Colour already worked out by a terrain that knows it better than a biome does (Earth), or 0. */
    public int color;

    public double topOfIce() {
        return height + iceThickness;
    }
}
