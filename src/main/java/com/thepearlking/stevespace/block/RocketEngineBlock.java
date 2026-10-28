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

/**
 * A rocket engine. {@code FACING} is the direction the nozzle points; thrust pushes the other way.
 * Placed under a block or on the ground the nozzle points down; placed on a side it points out.
 */
public class RocketEngineBlock extends DirectionalBlock {

    public static final MapCodec<RocketEngineBlock> CODEC = simpleCodec(RocketEngineBlock::new);

    private static final VoxelShape VERTICAL = Block.box(2, 0, 2, 14, 16, 14);
    private static final VoxelShape NORTH_SOUTH = Block.box(2, 2, 0, 14, 14, 16);
    private static final VoxelShape EAST_WEST = Block.box(0, 2, 2, 16, 14, 14);

    public RocketEngineBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.DOWN));
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
        Direction clicked = context.getClickedFace();
        Direction nozzle = clicked.getAxis().isVertical() ? Direction.DOWN : clicked;
        return defaultBlockState().setValue(FACING, nozzle);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING).getAxis()) {
            case Y -> VERTICAL;
            case Z -> NORTH_SOUTH;
            case X -> EAST_WEST;
        };
    }
}
