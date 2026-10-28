package com.thepearlking.stevespace.remote;

import com.thepearlking.stevespace.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** A function relay's channel: consoles on the same channel set it off. */
public class RcFunctionEntity extends ChannelEntity {

    public RcFunctionEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RC_FUNCTION.get(), pos, state);
    }
}
