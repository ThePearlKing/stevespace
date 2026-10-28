package com.thepearlking.stevespace.world.planet.noise;

/**
 * Fractal noise in block coordinates that repeats exactly once around the planet along X.
 *
 * <p>Used for small and medium terrain detail (hills, ridges, river channels, caves). Each
 * octave's frequency is nudged so a whole number of noise cells fits the circumference, which
 * makes the value at {@code x} identical to the value at {@code x + circumference}.
 * Large features (continents, climate) use {@link SphereNoise} instead, which is seamless at
 * the poles too.
 */
public final class WrappingNoise {

    private final GradientNoise[] octaves;
    private final double[] frequencies;
    private final double[] amplitudes;
    private final double normalisation;

    /**
     * @param seed          noise seed
     * @param circumference planet circumference in blocks (the X repeat length)
     * @param wavelength    approximate size in blocks of the first (largest) octave
     * @param octaveCount   number of octaves, each half the wavelength of the previous
     * @param persistence   amplitude multiplier per octave (0.5 is classic fBm)
     */
    public WrappingNoise(long seed, int circumference, double wavelength, int octaveCount, double persistence) {
        octaves = new GradientNoise[octaveCount];
        frequencies = new double[octaveCount];
        amplitudes = new double[octaveCount];
        double amplitude = 1;
        double total = 0;
        for (int i = 0; i < octaveCount; i++) {
            double octaveWavelength = wavelength / (1 << i);
            int cellsAround = Math.max(1, (int) Math.round(circumference / octaveWavelength));
            frequencies[i] = cellsAround / (double) circumference;
            octaves[i] = new GradientNoise(seed + i * 0x632BE59BD9B4E019L, cellsAround);
            amplitudes[i] = amplitude;
            total += amplitude;
            amplitude *= persistence;
        }
        normalisation = 1 / total;
    }

    /** 2D fractal noise at a surface position, roughly in [-1, 1]. */
    public double sample(double x, double z) {
        return sample(x, 0.5, z);
    }

    /** 3D fractal noise; {@code y} is in blocks just like x and z. */
    public double sample(double x, double y, double z) {
        double sum = 0;
        for (int i = 0; i < octaves.length; i++) {
            double f = frequencies[i];
            sum += octaves[i].sample(x * f, y * f, z * f) * amplitudes[i];
        }
        return sum * normalisation;
    }

    /**
     * Ridged fractal noise: sharp crests where the underlying noise crosses zero. Gives mountain
     * ranges proper ridges and valleys. Result is in [0, 1], 1 on the ridge lines.
     */
    public double ridged(double x, double z) {
        double sum = 0;
        double weight = 1;
        for (int i = 0; i < octaves.length; i++) {
            double f = frequencies[i];
            double ridge = 1 - Math.abs(octaves[i].sample(x * f, 0.5, z * f) * 1.4);
            ridge = Math.max(0, ridge);
            ridge *= ridge;
            // Later octaves only add detail where the earlier ones already rose (eroded look).
            double contribution = ridge * weight;
            sum += contribution * amplitudes[i];
            weight = Math.min(1, contribution * 1.6);
        }
        return Math.min(1, sum * normalisation * 1.25);
    }
}
