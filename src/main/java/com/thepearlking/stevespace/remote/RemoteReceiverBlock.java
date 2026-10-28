package com.thepearlking.stevespace.remote;

import com.mojang.serialization.MapCodec;
import com.thepearlking.stevespace.block.PilotSeatBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The remote control receiver: a ship's flight computer for flying without anyone aboard. To the
 * ship it is its pilot's place (a ship can be built round one instead of a pilot seat); a
 * remote control console tuned to the same channel flies it from anywhere it can be heard.
 * Right-click to set its channel.
 */
public class RemoteReceiverBlock extends PilotSeatBlock implements EntityBlock {

    public static final MapCodec<RemoteReceiverBlock> CODEC = simpleCodec(RemoteReceiverBlock::new);
    private static final VoxelShape SHAPE = net.minecraft.world.phys.shapes.Shapes.block();

    public RemoteReceiverBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RemoteReceiverEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        // Nobody sits in it: right-click tunes it.
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof RemoteReceiverEntity receiver) {
            RemoteControl.openTuner(serverPlayer, pos, receiver.channel(), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (level instanceof ServerLevel server && !old.is(state.getBlock())) {
            RemoteSites.get(server.getServer()).add(server.dimension().location().toString(), pos);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState now, boolean moving) {
        if (level instanceof ServerLevel server && !now.is(state.getBlock())) {
            RemoteSites.get(server.getServer()).remove(server.dimension().location().toString(), pos);
        }
        super.onRemove(state, level, pos, now, moving);
    }
}
