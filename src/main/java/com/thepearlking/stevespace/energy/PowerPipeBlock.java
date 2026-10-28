package com.thepearlking.stevespace.energy;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

/**
 * Carries power across gaps: from a generator to a base, along the ground to a launch pad, through
 * walls that aren't part of the grid. Joins other pipes and anything that holds power, from any mod.
 */
public class PowerPipeBlock extends PipeBlock {

    public static final MapCodec<PowerPipeBlock> CODEC = simpleCodec(PowerPipeBlock::new);

    public PowerPipeBlock(Properties properties) {
        super(0.1875f, properties);
        BlockState state = stateDefinition.any();
        for (var p : PROPERTY_BY_DIRECTION.values()) {
            state = state.setValue(p, false);
        }
        registerDefaultState(state);
    }

    @Override
    protected MapCodec<? extends PipeBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        for (Direction d : Direction.values()) {
            BlockPos n = context.getClickedPos().relative(d);
            state = state.setValue(PROPERTY_BY_DIRECTION.get(d), joins(context.getLevel(), n, context.getLevel().getBlockState(n), d));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level,
                                     BlockPos pos, BlockPos neighbourPos) {
        return state.setValue(PROPERTY_BY_DIRECTION.get(direction), joins(level, neighbourPos, neighbour, direction));
    }

    private static boolean joins(BlockGetter level, BlockPos pos, BlockState state, Direction from) {
        if (state.getBlock() instanceof PowerPipeBlock || state.getBlock() instanceof PowerBlocks.PowerBlock) {
            return true;
        }
        BlockEntity be = level.getBlockEntity(pos);
        return be != null && be.getCapability(ForgeCapabilities.ENERGY, from.getOpposite()).isPresent();
    }

    /** Turning the block (a ship coming down facing a new way) turns which sides it joins on. */
    @Override
    protected BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rotation) {
        BlockState turned = state;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            turned = turned.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(rotation.rotate(d)), state.getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(d)));
        }
        return turned;
    }

    @Override
    protected BlockState mirror(BlockState state, net.minecraft.world.level.block.Mirror mirror) {
        BlockState flipped = state;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            flipped = flipped.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(mirror.mirror(d)), state.getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(d)));
        }
        return flipped;
    }
}
