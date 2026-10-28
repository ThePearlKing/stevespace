package com.thepearlking.stevespace.world.gen.earth;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.thepearlking.stevespace.world.planet.earth.EarthClimate;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * The three inputs of Minecraft's terrain generator that Earth replaces, so vanilla terrain is
 * laid out as a real planet:
 * <ul>
 *   <li>{@code stevespace:earth_continents} — continentalness: real continents and oceans</li>
 *   <li>{@code stevespace:earth_climate} with kind {@code temperature} or {@code humidity} —
 *       climate by latitude: icy poles, deserts in dry subtropics, jungles at the equator</li>
 * </ul>
 * Everything else (erosion, ridges, caves, aquifers, surface rules) is vanilla.
 *
 * <p>The world seed reaches a density function only through the noises it holds, so each
 * function carries a vanilla noise purely to read the seed from it (see {@link #seedOf}).
 */
public final class EarthDensityFunctions {

    private EarthDensityFunctions() {
    }

    /** Derives a stable seed from a seeded vanilla noise: same world seed, same value. */
    static long seedOf(DensityFunction.NoiseHolder noise) {
        return seedOf(noise.noise());
    }

    /**
     * The planet-layout seed of a world, read from its continentalness noise. Must be the same
     * noise the density functions use ({@code seed_noise} in the noise settings).
     */
    public static long seedOf(NormalNoise noise) {
        long seed = 0x5EED5EEDL;
        double[][] points = {{0.123, 4.56, 7.89}, {-31.7, 2.25, 101.5}, {77.7, -8.8, -15.3}};
        for (double[] p : points) {
            seed = seed * 31 + Double.doubleToLongBits(noise.getValue(p[0], p[1], p[2]));
        }
        return seed;
    }

    /** Continentalness from {@link EarthClimate}. */
    public record Continents(DensityFunction.NoiseHolder seedNoise) implements DensityFunction.SimpleFunction {

        public static final MapCodec<Continents> DATA_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                DensityFunction.NoiseHolder.CODEC.fieldOf("seed_noise").forGetter(Continents::seedNoise)
        ).apply(i, Continents::new));
        public static final KeyDispatchDataCodec<Continents> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

        @Override
        public double compute(FunctionContext context) {
            if (seedNoise.noise() == null) {
                return 0;
            }
            return EarthWorldgen.climate(seedOf(seedNoise)).continentalness(context.blockX(), context.blockZ());
        }

        @Override
        public DensityFunction mapAll(Visitor visitor) {
            return visitor.apply(new Continents(visitor.visitNoise(seedNoise)));
        }

        @Override
        public double minValue() {
            return -1.2;
        }

        @Override
        public double maxValue() {
            return 1.0;
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }
    }

    /**
     * Erosion: vanilla's noise ({@code argument}), steered by the real heights in a Real Earth
     * world so its mountain ranges come out rugged and its plains flat (see EarthClimate.erosion).
     */
    public record Erosion(DensityFunction argument, DensityFunction.NoiseHolder seedNoise) implements DensityFunction {

        public static final MapCodec<Erosion> DATA_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                DensityFunction.HOLDER_HELPER_CODEC.fieldOf("argument").forGetter(Erosion::argument),
                DensityFunction.NoiseHolder.CODEC.fieldOf("seed_noise").forGetter(Erosion::seedNoise)
        ).apply(i, Erosion::new));
        public static final KeyDispatchDataCodec<Erosion> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

        @Override
        public double compute(FunctionContext context) {
            double vanilla = argument.compute(context);
            if (seedNoise.noise() == null || !EarthWorldgen.settings().real()) {
                return vanilla;
            }
            return EarthWorldgen.climate(seedOf(seedNoise)).erosion(context.blockX(), context.blockZ(), vanilla);
        }

        @Override
        public void fillArray(double[] values, ContextProvider provider) {
            provider.fillAllDirectly(values, this);
        }

        @Override
        public DensityFunction mapAll(Visitor visitor) {
            return visitor.apply(new Erosion(argument.mapAll(visitor), visitor.visitNoise(seedNoise)));
        }

        @Override
        public double minValue() {
            return -1.5;
        }

        @Override
        public double maxValue() {
            return 1.5;
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }
    }

    /**
     * Real Earth's ground height, as the "offset" the terrain is shaped round: the real height
     * map's (EarthClimate.realHeight), so mountains, islands and coasts stand where and as high as
     * they really do, in proportion (no spires where the vanilla shaping would put them).
     */
    public record RealOffset(DensityFunction.NoiseHolder seedNoise) implements DensityFunction {

        public static final MapCodec<RealOffset> DATA_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                DensityFunction.NoiseHolder.CODEC.fieldOf("seed_noise").forGetter(RealOffset::seedNoise)
        ).apply(i, RealOffset::new));
        public static final KeyDispatchDataCodec<RealOffset> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

        @Override
        public double compute(FunctionContext context) {
            if (seedNoise.noise() == null) {
                return -0.5;
            }
            double height = EarthWorldgen.climate(seedOf(seedNoise)).realHeight(context.blockX(), context.blockZ());
            // The depth gradient falls 1/128 per block from 1.5 at y -64: the ground is where it meets this.
            return (63 + height + 64) / 128.0 - 1.5;
        }

        @Override
        public void fillArray(double[] values, ContextProvider provider) {
            provider.fillAllDirectly(values, this);
        }

        @Override
        public DensityFunction mapAll(Visitor visitor) {
            return visitor.apply(new RealOffset(visitor.visitNoise(seedNoise)));
        }

        @Override
        public double minValue() {
            return -1.5;
        }

        @Override
        public double maxValue() {
            return 1.5;
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }
    }

    public enum ClimateKind implements StringRepresentable {
        TEMPERATURE("temperature"), HUMIDITY("humidity");

        public static final Codec<ClimateKind> CODEC = StringRepresentable.fromEnum(ClimateKind::values);
        private final String name;

        ClimateKind(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    /** Temperature or humidity from {@link EarthClimate}. */
    public record Climate(ClimateKind kind, DensityFunction.NoiseHolder seedNoise) implements DensityFunction.SimpleFunction {

        public static final MapCodec<Climate> DATA_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                ClimateKind.CODEC.fieldOf("kind").forGetter(Climate::kind),
                DensityFunction.NoiseHolder.CODEC.fieldOf("seed_noise").forGetter(Climate::seedNoise)
        ).apply(i, Climate::new));
        public static final KeyDispatchDataCodec<Climate> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

        @Override
        public double compute(FunctionContext context) {
            if (seedNoise.noise() == null) {
                return 0;
            }
            EarthClimate climate = EarthWorldgen.climate(seedOf(seedNoise));
            return kind == ClimateKind.TEMPERATURE
                    ? climate.temperature(context.blockX(), context.blockZ())
                    : climate.humidity(context.blockX(), context.blockZ());
        }

        @Override
        public DensityFunction mapAll(Visitor visitor) {
            return visitor.apply(new Climate(kind, visitor.visitNoise(seedNoise)));
        }

        @Override
        public double minValue() {
            return -1;
        }

        @Override
        public double maxValue() {
            return 1;
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }
    }
}
