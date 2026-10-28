package com.thepearlking.stevespace.suit;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * Pressurised rooms. An oxygen generator fills the space it opens onto; if that space is closed
 * all round (walls, floor, roof, shut doors, windows) the room holds air: you can breathe in it
 * without a helmet, and it shuts out the heat, cold and radiation outside. A gap anywhere and
 * the air leaks away. Rooms are re-checked every couple of seconds, so opening a door or
 * breaking a wall is felt at once.
 */
public final class Habitats {

    /** Largest room one generator can fill. */
    public static final int MAX_VOLUME = 4096;
    private static final int CHECK_EVERY = 40;

    /** Every generator, per dimension, with the room it holds (empty when leaking). */
    private static final Map<ResourceKey<Level>, Map<Long, LongSet>> ROOMS = new HashMap<>();

    private Habitats() {
    }

    public static void addGenerator(ServerLevel level, BlockPos pos) {
        ROOMS.computeIfAbsent(level.dimension(), k -> new HashMap<>()).put(pos.asLong(), powered(level, pos, 1));
    }

    /** The room the generator holds, if it can pay for {@code ticks} ticks of it (else nothing). */
    private static LongSet powered(ServerLevel level, BlockPos pos, int ticks) {
        LongSet room = fill(level, pos);
        if (room.isEmpty()) {
            return room;
        }
        if (level.getBlockEntity(pos) instanceof com.thepearlking.stevespace.block.OxygenGeneratorBlockEntity generator
                && generator.buffer().use(com.thepearlking.stevespace.block.OxygenGeneratorBlockEntity.cost(room.size()) * ticks)) {
            generator.setChanged();
            return room;
        }
        return new LongOpenHashSet(); // no power: no air
    }

    /** True when the generator here has power to run. */
    public static boolean hasPower(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof com.thepearlking.stevespace.block.OxygenGeneratorBlockEntity generator
                && generator.buffer().getEnergyStored() > 0;
    }

    public static void removeGenerator(ServerLevel level, BlockPos pos) {
        Map<Long, LongSet> rooms = ROOMS.get(level.dimension());
        if (rooms != null) {
            rooms.remove(pos.asLong());
        }
    }

    /** True when this block is inside a pressurised room. */
    public static boolean pressurised(Level level, BlockPos pos) {
        Map<Long, LongSet> rooms = ROOMS.get(level.dimension());
        if (rooms == null) {
            return false;
        }
        long key = pos.asLong();
        for (LongSet room : rooms.values()) {
            if (room.contains(key)) {
                return true;
            }
        }
        return false;
    }

    /** The size of the room a generator holds (0 when it leaks), for the generator's readout. */
    public static int volume(Level level, BlockPos generator) {
        Map<Long, LongSet> rooms = ROOMS.get(level.dimension());
        LongSet room = rooms == null ? null : rooms.get(generator.asLong());
        return room == null ? 0 : room.size();
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent.Post event) {
        if (!(event.level instanceof ServerLevel level) || level.getGameTime() % CHECK_EVERY != 0) {
            return;
        }
        Map<Long, LongSet> rooms = ROOMS.get(level.dimension());
        if (rooms == null || rooms.isEmpty()) {
            return;
        }
        for (Map.Entry<Long, LongSet> e : rooms.entrySet()) {
            BlockPos pos = BlockPos.of(e.getKey());
            if (!level.isLoaded(pos)) {
                continue;
            }
            if (!(level.getBlockState(pos).getBlock() instanceof com.thepearlking.stevespace.block.OxygenGeneratorBlock)) {
                e.setValue(new LongOpenHashSet());
                continue;
            }
            e.setValue(powered(level, pos, CHECK_EVERY));
        }
        rooms.entrySet().removeIf(e -> level.isLoaded(BlockPos.of(e.getKey()))
                && !(level.getBlockState(BlockPos.of(e.getKey())).getBlock() instanceof com.thepearlking.stevespace.block.OxygenGeneratorBlock));
    }

    /** The sealed space next to the generator, or an empty set if it leaks or is too big. */
    static LongSet fill(ServerLevel level, BlockPos generator) {
        LongSet seen = new LongOpenHashSet();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (Direction d : Direction.values()) {
            BlockPos start = generator.relative(d);
            if (open(level, start) && seen.add(start.asLong())) {
                queue.add(start);
            }
        }
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            if (seen.size() > MAX_VOLUME || !level.isLoaded(p) || p.getY() >= level.getMaxBuildHeight() - 1 || p.getY() <= level.getMinBuildHeight()) {
                return new LongOpenHashSet(); // leaks into the open
            }
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (open(level, n) && seen.add(n.asLong())) {
                    queue.add(n);
                }
            }
        }
        return seen;
    }

    /** Air can pass: empty space, or things you walk through (torches, signs, open doors). */
    private static boolean open(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || (state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty());
    }
}
