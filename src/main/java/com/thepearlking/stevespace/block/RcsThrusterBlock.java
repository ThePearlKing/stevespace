package com.thepearlking.stevespace.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/**
 * A small cluster of thruster nozzles bolted to the hull ({@code FACING} points away from the
 * block it's on). Every one on a ship adds a little push in any direction, for nudging it about
 * without turning: docking, mostly.
 */
public class RcsThrusterBlock extends DirectionalBlock {

    public static final MapCodec<RcsThrusterBlock> CODEC = simpleCodec(RcsThrusterBlock::new);
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        // Pointing up: a plate on the hull below, the nozzle block on it.
        SHAPES.put(Direction.UP, Block.box(3, 0, 3, 13, 10, 13));
        SHAPES.put(Direction.DOWN, Block.box(3, 6, 3, 13, 16, 13));
        SHAPES.put(Direction.NORTH, Block.box(3, 3, 6, 13, 13, 16));
        SHAPES.put(Direction.SOUTH, Block.box(3, 3, 0, 13, 13, 10));
        SHAPES.put(Direction.EAST, Block.box(0, 3, 3, 10, 13, 13));
        SHAPES.put(Direction.WEST, Block.box(6, 3, 3, 16, 13, 13));
    }

    public RcsThrusterBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }
}
