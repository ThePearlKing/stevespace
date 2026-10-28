package com.thepearlking.stevespace.vessel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The blocks of a vessel, stored relative to the vessel's origin (its command seat).
 * Immutable once built. Used by the flying {@link VesselEntity}, saved with it, and sent to
 * clients for rendering.
 */
public final class VesselStructure {

    private final Map<BlockPos, BlockState> blocks;
    private final Map<BlockPos, CompoundTag> blockEntities;
    private final BlockPos min;
    private final BlockPos max;

    public VesselStructure(Map<BlockPos, BlockState> blocks, Map<BlockPos, CompoundTag> blockEntities) {
        this.blocks = Collections.unmodifiableMap(new HashMap<>(blocks));
        this.blockEntities = Collections.unmodifiableMap(new HashMap<>(blockEntities));
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos p : blocks.keySet()) {
            minX = Math.min(minX, p.getX());
            minY = Math.min(minY, p.getY());
            minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX());
            maxY = Math.max(maxY, p.getY());
            maxZ = Math.max(maxZ, p.getZ());
        }
        if (blocks.isEmpty()) {
            minX = minY = minZ = maxX = maxY = maxZ = 0;
        }
        this.min = new BlockPos(minX, minY, minZ);
        this.max = new BlockPos(maxX, maxY, maxZ);
    }

    public Map<BlockPos, BlockState> blocks() {
        return blocks;
    }

    public Map<BlockPos, CompoundTag> blockEntities() {
        return blockEntities;
    }

    public BlockState get(BlockPos relative) {
        return blocks.get(relative);
    }

    public int size() {
        return blocks.size();
    }

    /** Smallest corner of the bounding box (relative to the origin). */
    public BlockPos min() {
        return min;
    }

    /** Largest corner of the bounding box (relative to the origin, inclusive). */
    public BlockPos max() {
        return max;
    }

    // ---------------------------------------------------------------------------------------
    // Saving: a palette of block states plus one palette index and position per block.
    // ---------------------------------------------------------------------------------------

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        List<BlockState> palette = new ArrayList<>();
        Map<BlockState, Integer> indices = new HashMap<>();
        int[] data = new int[blocks.size() * 4];
        int i = 0;
        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            Integer index = indices.get(entry.getValue());
            if (index == null) {
                index = palette.size();
                palette.add(entry.getValue());
                indices.put(entry.getValue(), index);
            }
            BlockPos p = entry.getKey();
            data[i++] = p.getX();
            data[i++] = p.getY();
            data[i++] = p.getZ();
            data[i++] = index;
        }
        ListTag paletteTag = new ListTag();
        for (BlockState state : palette) {
            paletteTag.add(NbtUtils.writeBlockState(state));
        }
        tag.put("palette", paletteTag);
        tag.putIntArray("blocks", data);

        ListTag entities = new ListTag();
        for (Map.Entry<BlockPos, CompoundTag> entry : blockEntities.entrySet()) {
            CompoundTag be = entry.getValue().copy();
            be.put("vessel_pos", NbtUtils.writeBlockPos(entry.getKey()));
            entities.add(be);
        }
        tag.put("block_entities", entities);
        return tag;
    }

    public static VesselStructure load(CompoundTag tag, HolderLookup.Provider registries) {
        HolderGetter<Block> blockLookup = registries.lookupOrThrow(Registries.BLOCK);
        ListTag paletteTag = tag.getList("palette", Tag.TAG_COMPOUND);
        List<BlockState> palette = new ArrayList<>(paletteTag.size());
        for (int i = 0; i < paletteTag.size(); i++) {
            palette.add(NbtUtils.readBlockState(blockLookup, paletteTag.getCompound(i)));
        }
        int[] data = tag.getIntArray("blocks");
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        for (int i = 0; i + 3 < data.length; i += 4) {
            int index = data[i + 3];
            if (index >= 0 && index < palette.size()) {
                blocks.put(new BlockPos(data[i], data[i + 1], data[i + 2]), palette.get(index));
            }
        }
        Map<BlockPos, CompoundTag> blockEntities = new HashMap<>();
        ListTag entities = tag.getList("block_entities", Tag.TAG_COMPOUND);
        for (int i = 0; i < entities.size(); i++) {
            CompoundTag be = entities.getCompound(i).copy();
            BlockPos pos = NbtUtils.readBlockPos(be, "vessel_pos").orElse(BlockPos.ZERO);
            be.remove("vessel_pos");
            blockEntities.put(pos, be);
        }
        return new VesselStructure(blocks, blockEntities);
    }
}
