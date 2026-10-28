package com.thepearlking.stevespace.world;

import com.thepearlking.stevespace.world.planet.PlanetGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Keeps the two sides of a planet's seams the same. The ground just past the east edge of the map
 * is a copy of the ground at the west edge (and the other way round), and the ground past a pole is
 * the far side of that pole (half way round, north and south swapped), there so you can see across
 * before you walk over; whatever changes on one side (building, digging, a blast, flowing water)
 * is copied onto its twin on the other, so the view across shows your builds.
 */
public final class WorldSeam {

    /** How far either side of the seam blocks are kept in step (blocks): further than you can see. */
    private static final int BAND = 160;
    private static boolean copying;

    private WorldSeam() {
    }

    @SubscribeEvent
    public static void onBlockChanged(BlockEvent.NeighborNotifyEvent event) {
        if (copying || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = event.getPos();
        // Cheap test first: nowhere near any seam (the smallest world's are 330 blocks out or more).
        if (Math.abs(pos.getX()) < 160 && Math.abs(pos.getZ()) < 160) {
            return;
        }
        PlanetGeometry geometry = WorldWrap.geometryOf(level);
        if (geometry == null) {
            return;
        }
        int c = geometry.circumference();
        int half = c / 2;
        int pole = geometry.poleDistance();
        BlockState state = level.getBlockState(pos);
        int x = pos.getX();
        int z = pos.getZ();
        if (x >= half - BAND && x < half + BAND) {
            copy(level, new BlockPos(x - c, pos.getY(), z), state);
        } else if (x >= -half - BAND && x < -half + BAND) {
            copy(level, new BlockPos(x + c, pos.getY(), z), state);
        }
        if (z >= pole - BAND && z < pole + BAND) {
            copy(level, new BlockPos(canonicalX(x + half, c), pos.getY(), 2 * pole - 1 - z), state.mirror(Mirror.LEFT_RIGHT));
        } else if (z >= -pole - BAND && z < -pole + BAND) {
            copy(level, new BlockPos(canonicalX(x + half, c), pos.getY(), -2 * pole - 1 - z), state.mirror(Mirror.LEFT_RIGHT));
        }
    }

    private static void copy(ServerLevel level, BlockPos other, BlockState state) {
        if (level.getBlockState(other) == state) {
            return;
        }
        copying = true;
        try {
            // Quietly: no neighbour updates there (the twin follows this side, it doesn't act on its own).
            level.setBlock(other, state, 2 | 16);
        } finally {
            copying = false;
        }
    }

    /** x kept inside the map. */
    public static int canonicalX(int x, int circumference) {
        return Math.floorMod(x + circumference / 2, circumference) - circumference / 2;
    }

    /**
     * The column on the map that one past a seam is a copy of, for the world generator: {x, z},
     * moved back across the date line, or over the pole (half way round, mirrored north-south).
     */
    public static int[] canonical(int x, int z, int circumference) {
        int pole = circumference / 4;
        if (z >= pole) {
            z = 2 * pole - 1 - z;
            x += circumference / 2;
        } else if (z < -pole) {
            z = -2 * pole - 1 - z;
            x += circumference / 2;
        }
        return new int[]{canonicalX(x, circumference), z};
    }
}
