package com.thepearlking.stevespace.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** A wall display for capsules, stations and bases: shows where you are and how you're moving. */
public class FlightDisplayBlock extends FacingBlock implements EntityBlock {

    public FlightDisplayBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FlightDisplayBlockEntity(pos, state);
    }
}
