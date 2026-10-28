package com.thepearlking.stevespace.world.planet.earth;

/**
 * World-creation options for Earth's planet-scale layout. They are saved with each world when it
 * is created (see EarthWorldgen), so changing the config later never tears existing terrain.
 *
 * @param continentSize  1 = Earth-like continents; 2 = continents twice as wide (and fewer)
 * @param landFraction   share of the surface that is land (real Earth: 0.29)
 * @param iceCapLatitude latitude in degrees where the polar ice and snow begins
 * @param real           the Real Earth world type: the real continents, mountains and sea floor
 */
public record EarthSettings(double continentSize, double landFraction, double iceCapLatitude, boolean real) {

    public static final EarthSettings DEFAULT = new EarthSettings(1.0, 0.34, 70.0);

    public EarthSettings(double continentSize, double landFraction, double iceCapLatitude) {
        this(continentSize, landFraction, iceCapLatitude, false);
    }

    public EarthSettings {
        continentSize = Math.max(0.25, Math.min(4.0, continentSize));
        landFraction = Math.max(0.05, Math.min(0.9, landFraction));
        iceCapLatitude = Math.max(45.0, Math.min(85.0, iceCapLatitude));
    }
}
