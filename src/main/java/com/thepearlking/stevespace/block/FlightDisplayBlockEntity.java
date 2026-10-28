package com.thepearlking.stevespace.block;

import com.thepearlking.stevespace.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Holds nothing: it only gives the display a renderer for its live readout. */
public class FlightDisplayBlockEntity extends BlockEntity {

    public FlightDisplayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FLIGHT_DISPLAY.get(), pos, state);
    }
}
