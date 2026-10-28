package com.thepearlking.stevespace.vessel;

import com.thepearlking.stevespace.StevesSpace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * Finds the blocks that make up a vessel and lifts them out of the world (or puts them back).
 *
 * <p>A vessel is everything connected to its command seat through touching blocks, except
 * natural ground (dirt, stone, sand, water, ...) and launch pads, which the vessel stands on
 * but does not take with it. If the search runs into more than the size limit the vessel is
 * probably welded to a building or the landscape, and assembly is refused.
 */
public final class VesselAssembler {

    /** Blocks that never become part of a vessel (the ground and launch infrastructure). */
    public static final TagKey<Block> NOT_VESSEL =
            TagKey.create(net.minecraft.core.registries.Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(StevesSpace.MODID, "not_vessel"));

    /** Block-update flags for moving blocks silently: notify clients, skip shape updates and drops. */
    private static final int SILENT = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    private VesselAssembler() {
    }

    /** Result of trying to assemble: a structure, or a reason it could not be built. */
    public record Result(VesselStructure structure, String error) {
        public boolean ok() {
            return structure != null;
        }
    }

    /** Collects the vessel connected to {@code origin} without changing the world. */
    public static Result scan(ServerLevel level, BlockPos origin, int maxBlocks) {
        return scan(level, origin, maxBlocks, true);
    }

    /**
     * The same; {@code leavePad} false keeps everything joined (a ship in space has no launch pad
     * under its engines, only more of itself).
     */
    public static Result scan(ServerLevel level, BlockPos origin, int maxBlocks, boolean leavePad) {
        Map<BlockPos, BlockState> found = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(origin);
        found.put(origin, level.getBlockState(origin));
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            // Joined by a face, an edge or a corner: diagonal builds hold together too.
            for (BlockPos offset : NEIGHBOURS) {
                BlockPos next = pos.offset(offset);
                if (found.containsKey(next)) {
                    continue;
                }
                BlockState state = level.getBlockState(next);
                if (!isVesselBlock(level, next, state)) {
                    continue;
                }
                if (found.size() >= maxBlocks) {
                    return new Result(null, "stevespace.vessel.error.too_big");
                }
                found.put(next.immutable(), state);
                queue.add(next.immutable());
            }
        }

        if (leavePad) {
            leavePadBehind(found, origin);
        }

        Map<BlockPos, BlockState> relative = new HashMap<>();
        Map<BlockPos, CompoundTag> blockEntities = new HashMap<>();
        for (Map.Entry<BlockPos, BlockState> entry : found.entrySet()) {
            BlockPos rel = entry.getKey().subtract(origin);
            relative.put(rel, entry.getValue());
            BlockEntity be = level.getBlockEntity(entry.getKey());
            if (be != null) {
                blockEntities.put(rel, be.saveWithoutMetadata(level.registryAccess()));
            }
        }
        return new Result(new VesselStructure(relative, blockEntities), null);
    }

    /**
     * Whatever sits right under the engine nozzles is the pad the rocket stands on, not part of
     * it: those blocks, and everything joined to them at their level or lower, stay behind. (Then
     * only what is still connected to the seat counts.) Legs beside the engines are kept.
     */
    private static void leavePadBehind(Map<BlockPos, BlockState> found, BlockPos origin) {
        java.util.Set<BlockPos> pad = new java.util.HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (Map.Entry<BlockPos, BlockState> e : found.entrySet()) {
            BlockState state = e.getValue();
            if (state.getBlock() instanceof com.thepearlking.stevespace.block.RocketEngineBlock
                    && state.getValue(com.thepearlking.stevespace.block.RocketEngineBlock.FACING) == Direction.DOWN) {
                BlockPos under = e.getKey().below();
                if (found.containsKey(under) && !(found.get(under).getBlock() instanceof com.thepearlking.stevespace.block.RocketEngineBlock)
                        && pad.add(under)) {
                    queue.add(under);
                }
            }
        }
        int top = pad.stream().mapToInt(BlockPos::getY).max().orElse(Integer.MIN_VALUE);
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            for (Direction d : Direction.values()) {
                BlockPos next = p.relative(d);
                if (next.getY() <= top && found.containsKey(next) && !next.equals(origin)
                        && !(found.get(next).getBlock() instanceof com.thepearlking.stevespace.block.RocketEngineBlock) && pad.add(next)) {
                    queue.add(next);
                }
            }
        }
        if (pad.isEmpty()) {
            return;
        }
        pad.forEach(found::remove);
        // Keep only what is still joined to the seat.
        java.util.Set<BlockPos> kept = new java.util.HashSet<>();
        ArrayDeque<BlockPos> walk = new ArrayDeque<>();
        walk.add(origin);
        kept.add(origin);
        while (!walk.isEmpty()) {
            BlockPos p = walk.poll();
            for (BlockPos offset : NEIGHBOURS) {
                BlockPos next = p.offset(offset);
                if (found.containsKey(next) && kept.add(next)) {
                    walk.add(next);
                }
            }
        }
        found.keySet().retainAll(kept);
    }

    /** The 26 blocks around one: faces, edges and corners. */
    private static final java.util.List<BlockPos> NEIGHBOURS = java.util.stream.IntStream.range(0, 27)
            .mapToObj(i -> new BlockPos(i % 3 - 1, i / 3 % 3 - 1, i / 9 - 1))
            .filter(p -> !p.equals(BlockPos.ZERO)).toList();

    /** Like {@link #isVesselBlock(BlockState)}, but ground blocks a player placed count too. */
    public static boolean isVesselBlock(ServerLevel level, BlockPos pos, BlockState state) {
        if (isVesselBlock(state)) {
            return true;
        }
        return !state.isAir() && PlacedBlocks.tracked(state) && PlacedBlocks.isPlaced(level, pos);
    }

    public static boolean isVesselBlock(BlockState state) {
        if (state.isAir() || state.is(NOT_VESSEL)) {
            return false;
        }
        // Loose liquids are never part of a ship (waterlogged blocks are fine).
        return !(state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock)
                && !state.is(BlockTags.FIRE);
    }

    /**
     * Removes the vessel's blocks from the world without dropping items or popping off attached
     * blocks such as torches. Block entities are removed first so containers don't spill.
     */
    public static void removeFromWorld(ServerLevel level, BlockPos origin, VesselStructure structure) {
        for (BlockPos rel : structure.blockEntities().keySet()) {
            BlockPos pos = origin.offset(rel);
            level.removeBlockEntity(pos);
        }
        for (BlockPos rel : structure.blocks().keySet()) {
            BlockPos pos = origin.offset(rel);
            BlockState state = level.getBlockState(pos);
            BlockState replacement = state.getFluidState().isSource() ? state.getFluidState().createLegacyBlock() : Blocks.AIR.defaultBlockState();
            level.setBlock(pos, replacement, SILENT);
        }
        // Now tell the surroundings, so sand falls and redstone notices.
        for (BlockPos rel : structure.blocks().keySet()) {
            BlockPos pos = origin.offset(rel);
            level.blockUpdated(pos, Blocks.AIR);
        }
    }

    /**
     * Puts a vessel back into the world at {@code origin}, rotated by {@code rotation} quarter
     * turns about the vertical axis (vessels always land upright).
     */
    public static void placeInWorld(ServerLevel level, BlockPos origin, VesselStructure structure,
                                    net.minecraft.world.level.block.Rotation rotation) {
        for (Map.Entry<BlockPos, BlockState> entry : structure.blocks().entrySet()) {
            BlockPos pos = origin.offset(entry.getKey().rotate(rotation));
            level.setBlock(pos, entry.getValue().rotate(level, pos, rotation), SILENT);
            if (PlacedBlocks.tracked(entry.getValue())) {
                PlacedBlocks.mark(level, pos); // still the ship's, wherever it came down
            }
        }
        for (Map.Entry<BlockPos, CompoundTag> entry : structure.blockEntities().entrySet()) {
            BlockPos pos = origin.offset(entry.getKey().rotate(rotation));
            BlockEntity be = level.getBlockEntity(pos);
            if (be != null) {
                be.loadWithComponents(entry.getValue(), level.registryAccess());
                be.setChanged();
            }
        }
        for (BlockPos rel : structure.blocks().keySet()) {
            BlockPos pos = origin.offset(rel.rotate(rotation));
            // Blocks that join their neighbours (windows, pipes, panes, fences) look again now
            // that the whole ship is down, so their joins match exactly what's around them.
            BlockState state = level.getBlockState(pos);
            BlockState joined = Block.updateFromNeighbourShapes(state, level, pos);
            if (joined != state && joined.is(state.getBlock())) {
                level.setBlock(pos, joined, SILENT);
            }
            level.blockUpdated(pos, level.getBlockState(pos).getBlock());
        }
    }
}
