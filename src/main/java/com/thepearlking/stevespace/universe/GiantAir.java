package com.thepearlking.stevespace.universe;

/**
 * The inside of a giant planet's atmosphere, for flying down into it (there is no ground: it just
 * gets thicker, darker and hotter until it crushes you). Heights are blocks above the planet's
 * radius (the one-bar level). Three cloud decks, as in the real giants: ammonia ice on top, then
 * ammonium hydrosulphide, then water with lightning in it; then the hot dark depths.
 *
 * <p>The air above the top deck is very thin (an orbit through the haze lasts a long while), and
 * thickens fast below it.
 */
public final class GiantAir {

    /** Where hull warnings start, and where the hull gives way (blocks above the one-bar level). */
    public static final double WARN = -1500;
    public static final double CRUSH = -2500;

    /**
     * One cloud deck: its base and top (blocks above the one-bar level), its colour, and the
     * colour of the clear air just under it.
     */
    public record Deck(double base, double top, int color, int below) {
    }

    /** A giant's three decks (top first) and the colour of its haze above them. */
    public record Layers(Deck[] decks, int haze, int deep) {

        public Deck top() {
            return decks[0];
        }
    }

    private GiantAir() {
    }

    /** The layers of a giant planet, or null for any other body. */
    public static Layers of(CelestialBody body) {
        if (body == null) {
            return null;
        }
        return switch (body.id()) {
            case "jupiter" -> new Layers(new Deck[]{
                    new Deck(2600, 3400, 0xE6DAC0, 0xC4A47A),
                    new Deck(800, 1400, 0xAE7444, 0x6E5040),
                    new Deck(-800, 0, 0x5C5A60, 0x3A2A24)}, 0x9C8C78, 0x2E1A12);
            case "saturn" -> new Layers(new Deck[]{
                    new Deck(2700, 3500, 0xE4D2A0, 0xCCB080),
                    new Deck(900, 1500, 0xBE9658, 0x7C6446),
                    new Deck(-800, 0, 0x625F58, 0x3C3228)}, 0xA89C80, 0x30221A);
            case "uranus" -> new Layers(new Deck[]{
                    new Deck(2600, 3400, 0xBEE4E6, 0x8CC0C6),
                    new Deck(800, 1400, 0x86ACB0, 0x52767C),
                    new Deck(-800, 0, 0x3E5058, 0x22323A)}, 0x7AA6AE, 0x14202A);
            case "neptune" -> new Layers(new Deck[]{
                    new Deck(2600, 3400, 0x8CA8E6, 0x5A7ACC),
                    new Deck(800, 1400, 0x4A64AA, 0x2E4282),
                    new Deck(-800, 0, 0x2C3462, 0x182040)}, 0x4E66B4, 0x0E1430);
            default -> null;
        };
    }

    public static boolean has(CelestialBody body) {
        return of(body) != null;
    }

    /** Air density at {@code altitude} (1 = the one-bar level): thin up high, thicker and thicker down deep. */
    public static double density(double altitude) {
        return altitude >= 0 ? Math.exp(-altitude / 300) : 1 - altitude / 400;
    }

    /** Pressure in bars at {@code altitude}, for the readout (rises steeply into the deep). */
    public static double pressure(double altitude) {
        return altitude >= 0 ? Math.exp(-altitude / 1300) : Math.exp(-altitude / 600);
    }

    /** 0 above the deep, rising to 1 at the crush depth: hull stress, heat, the red glow. */
    public static double stress(double altitude) {
        return Math.max(0, Math.min(1, (WARN - altitude) / (WARN - CRUSH)));
    }
}
