package com.thepearlking.stevespace.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * A window block that joins its neighbours: put several side by side and they make one sheet of
 * glass, framed only round the outside.
 */
public class ConnectedWindowBlock extends TransparentBlock {

    public static final MapCodec<ConnectedWindowBlock> CODEC = simpleCodec(ConnectedWindowBlock::new);

    public ConnectedWindowBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any();
        for (BooleanProperty p : PipeBlock.PROPERTY_BY_DIRECTION.values()) {
            state = state.setValue(p, false);
        }
        registerDefaultState(state);
    }

    @Override
    protected MapCodec<? extends TransparentBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PipeBlock.NORTH, PipeBlock.EAST, PipeBlock.SOUTH, PipeBlock.WEST, PipeBlock.UP, PipeBlock.DOWN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return connect(defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level,
                                     BlockPos pos, BlockPos neighbourPos) {
        return state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(direction), neighbour.is(this));
    }

    private BlockState connect(BlockState state, BlockGetter level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            state = state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(d), level.getBlockState(pos.relative(d)).is(this));
        }
        return state;
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
