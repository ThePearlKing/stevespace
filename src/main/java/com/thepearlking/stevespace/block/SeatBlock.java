package com.thepearlking.stevespace.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A seat in a vessel. Right-click to sit. */
public abstract class SeatBlock extends FacingBlock {

    private static final VoxelShape BASE = Block.box(2, 0, 2, 14, 7, 14);
    private static final VoxelShape NORTH = Shapes.or(BASE, Block.box(2, 7, 12, 14, 16, 15));
    private static final VoxelShape SOUTH = Shapes.or(BASE, Block.box(2, 7, 1, 14, 16, 4));
    private static final VoxelShape EAST = Shapes.or(BASE, Block.box(1, 7, 2, 4, 16, 14));
    private static final VoxelShape WEST = Shapes.or(BASE, Block.box(12, 7, 2, 15, 16, 14));

    protected SeatBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH;
            case EAST -> EAST;
            case WEST -> WEST;
            default -> NORTH;
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isPassenger() || player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            SeatEntity seat = SeatEntity.create(level, pos);
            level.addFreshEntity(seat);
            player.startRiding(seat);
            player.setYRot(state.getValue(FACING).toYRot());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
