package com.thepearlking.stevespace.remote;

import com.thepearlking.stevespace.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** The receiver's channel. */
public class RemoteReceiverEntity extends ChannelEntity {

    public RemoteReceiverEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.REMOTE_RECEIVER.get(), pos, state);
    }
}
