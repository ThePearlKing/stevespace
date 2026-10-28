package com.thepearlking.stevespace.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

/** The seat a vessel is flown from. Sitting here on the ground, throttling up launches. */
public class PilotSeatBlock extends SeatBlock {

    public static final MapCodec<PilotSeatBlock> CODEC = simpleCodec(PilotSeatBlock::new);

    public PilotSeatBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }
}
