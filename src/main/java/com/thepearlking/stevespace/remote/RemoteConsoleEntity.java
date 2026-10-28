package com.thepearlking.stevespace.remote;

import com.thepearlking.stevespace.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** The console's channel. */
public class RemoteConsoleEntity extends ChannelEntity {

    public RemoteConsoleEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.REMOTE_CONSOLE.get(), pos, state);
    }
}
