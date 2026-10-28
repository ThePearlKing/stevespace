package com.thepearlking.stevespace.vessel;

import com.thepearlking.stevespace.registry.ModBlocks;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Ground blocks (dirt, stone, the rocks of other worlds) are never part of a ship, so a launch
 * doesn't take the ground with it. Unless a player put them there: this remembers which ones
 * were placed by hand (or came down with a landed ship), and those count like any other part.
 */
public final class PlacedBlocks extends SavedData {

    private static final String NAME = "stevespace_placed_blocks";
    private final Map<String, LongOpenHashSet> placed = new HashMap<>();

    public static PlacedBlocks get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PlacedBlocks::new, PlacedBlocks::load, null), NAME);
    }

    /** True for ground-type blocks only (the ones scans would otherwise leave behind). */
    static boolean tracked(BlockState state) {
        return state.is(VesselAssembler.NOT_VESSEL) && !state.is(ModBlocks.LAUNCH_PAD.get()) && !state.is(ModBlocks.FUEL_LOADER.get());
    }

    public static void mark(ServerLevel level, BlockPos pos) {
        PlacedBlocks data = get(level);
        if (data.placed.computeIfAbsent(level.dimension().location().toString(), k -> new LongOpenHashSet()).add(pos.asLong())) {
            data.setDirty();
        }
    }

    public static void unmark(ServerLevel level, BlockPos pos) {
        PlacedBlocks data = get(level);
        LongOpenHashSet set = data.placed.get(level.dimension().location().toString());
        if (set != null && set.remove(pos.asLong())) {
            data.setDirty();
        }
    }

    public static boolean isPlaced(ServerLevel level, BlockPos pos) {
        LongOpenHashSet set = get(level).placed.get(level.dimension().location().toString());
        return set != null && set.contains(pos.asLong());
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof Player && tracked(event.getPlacedBlock())) {
            mark(level, event.getPos());
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            unmark(level, event.getPos());
        }
    }

    private static PlacedBlocks load(CompoundTag tag, HolderLookup.Provider registries) {
        PlacedBlocks data = new PlacedBlocks();
        for (String key : tag.getAllKeys()) {
            data.placed.put(key, new LongOpenHashSet(tag.getLongArray(key)));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        placed.forEach((dim, set) -> tag.put(dim, new LongArrayTag(set.toLongArray())));
        return tag;
    }
}
