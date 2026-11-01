package com.thepearlking.stevespace.world;

import com.thepearlking.stevespace.registry.ModBlocks;
import com.thepearlking.stevespace.universe.CelestialBody;
import com.thepearlking.stevespace.universe.SolarSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Gravity for everything else that moves (dropped items, arrows, falling sand, thrown things):
 * living things have it set through their gravity attribute ({@link BodyGravity}); these fall
 * with a fixed pull, so before each tick the part of it this world doesn't have is given back.
 * Weightless in space, unless a gravity generator holds them down inside a ship. The same on
 * the server and on each player's game, so nothing jitters.
 */
public final class LooseGravity {

    /** Weightless entities near a gravity generator: checked once a second, not every tick. */
    private static final Map<Entity, long[]> HELD = new WeakHashMap<>();

    private LooseGravity() {
    }

    /** This world's pull as a fraction of Earth's: 0 in space. */
    public static java.util.function.Function<Level, Double> ratioOf = level -> level.isClientSide ? LooseGravity.clientRatio.apply(level)
            : ratio(com.thepearlking.stevespace.universe.ServerUniverse.bodyOf(level));
    /** The same on the player's game (set up by the client). */
    public static java.util.function.Function<Level, Double> clientRatio = level -> 1.0;
    /** The entities of the player's own world (set up by the client; nothing elsewhere). */
    public static java.util.function.Function<Level, Iterable<Entity>> clientEntities = level -> java.util.List.of();

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent.Pre event) {
        Level level = event.level;
        boolean space = SpaceDimension.isSpace(level);
        double ratio = space ? 0 : ratioOf.apply(level);
        if (Math.abs(ratio - 1) < 1e-6) {
            return;
        }
        Iterable<Entity> entities = level instanceof net.minecraft.server.level.ServerLevel server ? server.getAllEntities()
                : clientEntities.apply(level);
        for (Entity e : entities) {
            if (e instanceof LivingEntity || e instanceof AbstractMinecart || e.isPassenger() || e.isRemoved()
                    || e instanceof com.thepearlking.stevespace.vessel.VesselEntity
                    || e instanceof com.thepearlking.stevespace.block.SeatEntity) {
                continue;
            }
            double g = e.getGravity();
            if (g == 0) {
                continue;
            }
            double here = space && heldDown(level, e) ? 1 : ratio;
            if (here < 1) {
                e.setDeltaMovement(e.getDeltaMovement().add(0, g * (1 - here), 0));
            }
        }
    }

    /** Inside a ship with a gravity generator close by (a floor under it and a ceiling over it). */
    private static boolean heldDown(Level level, Entity e) {
        long now = level.getGameTime();
        long[] known = HELD.get(e);
        if (known != null && now - known[0] < 20) {
            return known[1] != 0;
        }
        BlockPos at = e.blockPosition();
        boolean held = false;
        if (level.isLoaded(at)) {
            boolean floor = false;
            boolean ceiling = false;
            for (int dy = 1; dy <= 8 && !(floor && ceiling); dy++) {
                floor |= !level.getBlockState(at.below(dy)).isAir();
                ceiling |= !level.getBlockState(at.above(dy)).isAir();
            }
            if (floor && ceiling) {
                for (BlockPos p : BlockPos.betweenClosed(at.offset(-8, -8, -8), at.offset(8, 8, 8))) {
                    if (level.getBlockState(p).is(ModBlocks.GRAVITY_GENERATOR.get())) {
                        held = true;
                        break;
                    }
                }
            }
        }
        HELD.put(e, new long[]{now, held ? 1 : 0});
        return held;
    }

    /** A body's surface pull as a fraction of Earth's (1 for a world that isn't one of ours). */
    public static double ratio(CelestialBody body) {
        return body != null && body.isLandable() ? body.surfaceGravity() / SolarSystem.EARTH_GRAVITY : 1;
    }
}
