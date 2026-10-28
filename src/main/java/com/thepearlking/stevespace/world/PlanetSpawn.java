package com.thepearlking.stevespace.world;

import com.thepearlking.stevespace.StevesSpace;
import com.thepearlking.stevespace.universe.CelestialBody;
import com.thepearlking.stevespace.universe.ServerUniverse;
import com.thepearlking.stevespace.world.planet.PlanetGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Chooses the world spawn on Earth: inland, temperate, around 40°N, instead of vanilla's search
 * around (0, 0), which on a real globe is in the middle of the date-line ocean.
 */
public final class PlanetSpawn {

    private static final double SPAWN_LATITUDE = 40;

    private PlanetSpawn() {
    }

    @SubscribeEvent
    public static void onCreateSpawn(LevelEvent.CreateSpawnPosition event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        CelestialBody body = ServerUniverse.bodyOf(level);
        if (body == null || !body.id().equals("earth")) {
            return;
        }
        PlanetGeometry geometry = new PlanetGeometry(body.radius());
        NoiseRouter router = level.getChunkSource().randomState().router();
        BlockPos column = findSpot(geometry, router, candidate -> standsOnDryLand(level, candidate));
        int y = level.getChunk(column.getX() >> 4, column.getZ() >> 4)
                .getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX() & 15, column.getZ() & 15) + 1;
        BlockPos spot = new BlockPos(column.getX(), y, column.getZ());
        event.getSettings().setSpawn(spot, 0);
        event.setCanceled(true);
        StevesSpace.LOGGER.info("Planet spawn set to {}", spot);
    }

    /** Generates the candidate's chunk and checks the real surface block: no lakes or rivers. */
    private static boolean standsOnDryLand(ServerLevel level, BlockPos candidate) {
        int y = level.getChunk(candidate.getX() >> 4, candidate.getZ() >> 4)
                .getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, candidate.getX() & 15, candidate.getZ() & 15);
        BlockPos ground = new BlockPos(candidate.getX(), y - 1, candidate.getZ());
        return level.getBlockState(ground).getFluidState().isEmpty()
                && level.getBlockState(ground).isFaceSturdy(level, ground, net.minecraft.core.Direction.UP);
    }

    /**
     * Spirals out from 40°N, 0° in 64-block steps for a column that is solidly inland, temperate
     * and not mountainous (judged from the world's own seeded terrain inputs, which is cheap), and
     * that {@code accept} confirms (generating the chunk to check for lakes and rivers).
     */
    static BlockPos findSpot(PlanetGeometry geometry, NoiseRouter router, java.util.function.Predicate<BlockPos> accept) {
        int startX = 0;
        int startZ = (int) geometry.zForLatitude(Math.toRadians(SPAWN_LATITUDE));
        int step = 64;
        int maxRing = geometry.circumference() / 2 / step;
        for (int ring = 0; ring <= maxRing; ring++) {
            for (int i = -ring; i <= ring; i++) {
                for (int j = -ring; j <= ring; j++) {
                    if (Math.max(Math.abs(i), Math.abs(j)) != ring) {
                        continue;
                    }
                    int x = (int) geometry.wrapX(startX + i * step);
                    int z = startZ + j * step;
                    if (Math.abs(z) > geometry.poleDistance() * 0.7) {
                        continue;
                    }
                    if (isGoodSpawn(router, x, z) && accept.test(new BlockPos(x, 0, z))) {
                        return new BlockPos(x, 0, z);
                    }
                }
            }
        }
        return new BlockPos(startX, 0, startZ);
    }

    private static boolean isGoodSpawn(NoiseRouter router, int x, int z) {
        DensityFunction.SinglePointContext at = new DensityFunction.SinglePointContext(x, 64, z);
        double continentalness = router.continents().compute(at);
        double temperature = router.temperature().compute(at);
        double erosion = router.erosion().compute(at);
        // Mid-inland, temperate or warm, low-relief ground (high erosion = flatter).
        return continentalness > 0.05 && continentalness < 0.5
                && temperature > -0.15 && temperature < 0.45
                && erosion > -0.2;
    }
}
