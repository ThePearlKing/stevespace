package com.thepearlking.stevespace.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

/** A crew seat: you ride along, but the vessel is flown from the pilot seat. */
public class PassengerSeatBlock extends SeatBlock {

    public static final MapCodec<PassengerSeatBlock> CODEC = simpleCodec(PassengerSeatBlock::new);

    public PassengerSeatBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }
}
