package com.thepearlking.stevespace.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.TransparentBlock;

/** A hull panel with a window you can see through. */
public class PortholeBlock extends TransparentBlock {

    public static final MapCodec<PortholeBlock> CODEC = simpleCodec(PortholeBlock::new);

    public PortholeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends TransparentBlock> codec() {
        return CODEC;
    }
}
