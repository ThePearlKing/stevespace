package com.thepearlking.stevespace.world.planet.noise;

/**
 * Fractal noise sampled on the surface of the unit sphere.
 *
 * <p>Because it is evaluated at the true 3D point on the planet, it has no seam anywhere —
 * not at the date line and not at the poles. It is used for everything planet-sized:
 * continents, climate, mountain belts, crater fields.
 */
public final class SphereNoise {

    private final GradientNoise[] octaves;
    private final double baseFrequency;
    private final double lacunarity;
    private final double persistence;
    private final double normalisation;

    /**
     * @param seed          noise seed
     * @param baseFrequency cells per planet radius of the first octave (1 to 3 = continents)
     * @param octaveCount   octaves of detail
     * @param persistence   amplitude falloff per octave
     */
    public SphereNoise(long seed, double baseFrequency, int octaveCount, double persistence) {
        this(seed, baseFrequency, octaveCount, persistence, 2.0);
    }

    public SphereNoise(long seed, double baseFrequency, int octaveCount, double persistence, double lacunarity) {
        this.baseFrequency = baseFrequency;
        this.persistence = persistence;
        this.lacunarity = lacunarity;
        octaves = new GradientNoise[octaveCount];
        double amplitude = 1;
        double total = 0;
        for (int i = 0; i < octaveCount; i++) {
            octaves[i] = new GradientNoise(seed + i * 0x5851F42D4C957F2DL);
            total += amplitude;
            amplitude *= persistence;
        }
        normalisation = 1 / total;
    }

    /** Noise at a point on the unit sphere (nx, ny, nz must have length 1), roughly [-1, 1]. */
    public double sample(double nx, double ny, double nz) {
        double sum = 0;
        double amplitude = 1;
        double frequency = baseFrequency;
        for (GradientNoise octave : octaves) {
            // The offset keeps octaves from sharing a lattice point at the sphere's centre.
            sum += octave.sample(nx * frequency + 31.7, ny * frequency - 17.3, nz * frequency + 5.1) * amplitude;
            amplitude *= persistence;
            frequency *= lacunarity;
        }
        return sum * normalisation;
    }

    public double sample(SpherePoint p) {
        return sample(p.x, p.y, p.z);
    }
}
