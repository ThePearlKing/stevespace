package com.thepearlking.stevespace.world.gen;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.thepearlking.stevespace.world.planet.CaveNoise;
import com.thepearlking.stevespace.world.planet.PlanetTerrain;
import com.thepearlking.stevespace.world.planet.TerrainSample;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Chunk generator for walkable bodies other than Earth (Moon, Mars, ...). The body's pure terrain
 * function decides the shape; its {@link ColumnBuilder} decides the blocks; caves are carved
 * from {@link CaveNoise}. Trees, ores and structures come from the normal biome features.
 */
public final class PlanetChunkGenerator extends ChunkGenerator {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final MapCodec<PlanetChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("body").forGetter(PlanetChunkGenerator::body),
            RegistryOps.retrieveGetter(Registries.BIOME)
    ).apply(instance, instance.stable(PlanetChunkGenerator::new)));

    /** Cave air below this height turns into lava, like vanilla's deep lava lakes. */
    private static final int LAVA_LEVEL = -55;
    private static final BlockState LAVA = Blocks.LAVA.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private final String body;
    private volatile PlanetBodies.BodyWorldGen worldGen;

    public PlanetChunkGenerator(String body, HolderGetter<Biome> biomes) {
        super(new PlanetBiomeSource(body, biomes));
        this.body = body;
    }

    public String body() {
        return body;
    }

    public PlanetTerrain terrain() {
        return worldGen().terrain();
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    // ---------------------------------------------------------------------------------------
    // Seed: Minecraft passes the world seed only here, before any chunk is generated.
    // ---------------------------------------------------------------------------------------

    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structureSets, RandomState randomState, long seed) {
        initialise(seed);
        return super.createState(structureSets, randomState, seed);
    }

    private synchronized void initialise(long seed) {
        if (worldGen == null) {
            PlanetBodies.BodyWorldGen created = PlanetBodies.create(body, seed);
            ((PlanetBiomeSource) biomeSource).attach(created.terrain(), created.caveBiomes());
            worldGen = created;
        }
    }

    private PlanetBodies.BodyWorldGen worldGen() {
        PlanetBodies.BodyWorldGen gen = worldGen;
        if (gen == null) {
            LOGGER.warn("Planet generator for '{}' used before the world seed was known; using seed 0", body);
            initialise(0);
            gen = worldGen;
        }
        return gen;
    }

    // ---------------------------------------------------------------------------------------
    // Terrain
    // ---------------------------------------------------------------------------------------

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState,
                                                        StructureManager structures, ChunkAccess chunk) {
        PlanetBodies.BodyWorldGen gen = worldGen();
        ChunkPos pos = chunk.getPos();
        int minX = pos.getMinBlockX();
        int minZ = pos.getMinBlockZ();
        int minY = chunk.getMinBuildHeight();
        int height = chunk.getHeight();
        int circumference = gen.terrain().geometry().circumference();

        // Terrain for the chunk plus a one-block border, so slopes can be measured at the edge.
        TerrainSample[][] samples = new TerrainSample[18][18];
        double highest = minY;
        for (int i = 0; i < 18; i++) {
            for (int j = 0; j < 18; j++) {
                TerrainSample s = new TerrainSample();
                // Past a seam, exactly the column it is a copy of.
                int[] at = com.thepearlking.stevespace.world.WorldSeam.canonical(minX + i - 1, minZ + j - 1, circumference);
                gen.terrain().sample(at[0], at[1], s);
                samples[i][j] = s;
                highest = Math.max(highest, s.height);
            }
        }
        int caveTop = Math.min((int) highest, minY + height - 1);
        // Past a pole the caves are the far side's, mirrored north-south (chunks line up: the pole
        // is a whole number of chunks out).
        int pole = circumference / 4;
        boolean overPole = minZ >= pole || minZ < -pole;
        int caveX = overPole ? com.thepearlking.stevespace.world.WorldSeam.canonicalX(minX + circumference / 2, circumference) : minX;
        int caveZ = minZ >= pole ? 2 * pole - 16 - minZ : minZ < -pole ? -2 * pole - 16 - minZ : minZ;
        CaveNoise.Field caves = gen.caves().compute(caveX, caveZ, minY, caveTop);

        BlockState[] column = new BlockState[height];
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                Arrays.fill(column, null);
                TerrainSample s = samples[lx + 1][lz + 1];
                double slope = slopeAt(samples, lx + 1, lz + 1);
                int x = minX + lx;
                int z = minZ + lz;
                // Past the date line, the same column as on the other side (its scattered details too).
                int[] at = com.thepearlking.stevespace.world.WorldSeam.canonical(x, z, circumference);
                int caveGuard = gen.columns().build(at[0], at[1], s, slope, column, minY);
                carveCaves(caves, column, minY, lx, overPole ? 15 - lz : lz, (int) Math.floor(s.height) - caveGuard);
                writeColumn(chunk, column, minY, lx, lz, mutable.set(x, 0, z));
            }
        }

        Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.WORLD_SURFACE_WG));
        return CompletableFuture.completedFuture(chunk);
    }

    private static double slopeAt(TerrainSample[][] samples, int i, int j) {
        double h = samples[i][j].topOfIce();
        double slope = Math.abs(samples[i + 1][j].topOfIce() - h);
        slope = Math.max(slope, Math.abs(samples[i - 1][j].topOfIce() - h));
        slope = Math.max(slope, Math.abs(samples[i][j + 1].topOfIce() - h));
        slope = Math.max(slope, Math.abs(samples[i][j - 1].topOfIce() - h));
        return slope;
    }

    private static void carveCaves(CaveNoise.Field caves, BlockState[] column, int minY, int lx, int lz, int carveTop) {
        for (int y = minY + 5; y <= carveTop; y++) {
            int i = y - minY;
            BlockState state = column[i];
            if (state == null || !state.getFluidState().isEmpty() || state.is(Blocks.BEDROCK)) {
                continue;
            }
            // Never undercut liquid: the block above must not be water or lava.
            BlockState above = i + 1 < column.length ? column[i + 1] : null;
            if (above != null && !above.getFluidState().isEmpty()) {
                continue;
            }
            if (caves.isCave(lx, y, lz)) {
                column[i] = y <= LAVA_LEVEL ? LAVA : AIR;
            }
        }
    }

    private static void writeColumn(ChunkAccess chunk, BlockState[] column, int minY, int lx, int lz, BlockPos.MutableBlockPos pos) {
        LevelChunkSection section = null;
        int sectionIndex = -1;
        for (int i = 0; i < column.length; i++) {
            BlockState state = column[i];
            if (state == null || state.isAir()) {
                continue;
            }
            int y = minY + i;
            int index = chunk.getSectionIndex(y);
            if (index != sectionIndex) {
                sectionIndex = index;
                section = chunk.getSection(index);
            }
            section.setBlockState(lx, y & 15, lz, state, false);
            if (state.is(Blocks.LAVA) || !state.getFluidState().isEmpty() && isNextToAir(column, i)) {
                // Lava needs its light registered; liquids beside caves should start flowing.
                chunk.markPosForPostprocessing(pos.setY(y));
            }
        }
    }

    private static boolean isNextToAir(BlockState[] column, int i) {
        return i > 0 && (column[i - 1] == null || column[i - 1].isAir());
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structures, RandomState randomState, ChunkAccess chunk) {
        // Surfaces are part of each column builder, done in fillFromNoise.
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState randomState, BiomeManager biomes,
                             StructureManager structures, ChunkAccess chunk, GenerationStep.Carving step) {
        // Caves come from CaveNoise in fillFromNoise.
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        ChunkPos center = region.getCenter();
        var biome = region.getBiome(center.getWorldPosition().atY(region.getMaxBuildHeight() - 1));
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
        random.setDecorationSeed(region.getSeed(), center.getMinBlockX(), center.getMinBlockZ());
        NaturalSpawner.spawnMobsForChunkGeneration(region, biome, center, random);
    }

    // ---------------------------------------------------------------------------------------
    // Fast height queries (structure placement, spawn search)
    // ---------------------------------------------------------------------------------------

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState randomState) {
        TerrainSample s = new TerrainSample();
        terrain().sample(x, z, s);
        int top = (int) Math.floor(s.topOfIce());
        if (type != Heightmap.Types.OCEAN_FLOOR_WG && type != Heightmap.Types.OCEAN_FLOOR) {
            PlanetTerrain t = terrain();
            if (t.hasOceans()) {
                top = Math.max(top, t.seaLevel() - 1);
            }
        }
        return Mth.clamp(top + 1, level.getMinBuildHeight(), level.getMaxBuildHeight());
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
        PlanetBodies.BodyWorldGen gen = worldGen();
        TerrainSample s = new TerrainSample();
        gen.terrain().sample(x, z, s);
        BlockState[] column = new BlockState[level.getHeight()];
        gen.columns().build(x, z, s, 0, column, level.getMinBuildHeight());
        for (int i = 0; i < column.length; i++) {
            if (column[i] == null) {
                column[i] = AIR;
            }
        }
        return new NoiseColumn(level.getMinBuildHeight(), column);
    }

    @Override
    public int getSeaLevel() {
        int sea = PlanetBodies.seaLevel(body);
        return sea == Integer.MIN_VALUE ? -63 : sea;
    }

    @Override
    public int getMinY() {
        return -64;
    }

    @Override
    public int getGenDepth() {
        return 384;
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState randomState, BlockPos pos) {
        PlanetTerrain t = terrain();
        double lat = Math.toDegrees(t.geometry().latitude(pos.getZ()));
        double lon = Math.toDegrees(t.geometry().longitude(pos.getX()));
        info.add(String.format("Planet %s  lat %.2f%s  lon %.2f%s", body,
                Math.abs(lat), lat >= 0 ? "N" : "S", Math.abs(lon), lon >= 0 ? "E" : "W"));
    }
}
