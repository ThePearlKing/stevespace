package com.thepearlking.stevespace.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** Holds rocket propellant. A full tank weighs 1 t, of which 0.9 t is propellant. */
public class PropellantTankBlock extends BaseEntityBlock {

    public static final MapCodec<PropellantTankBlock> CODEC = simpleCodec(PropellantTankBlock::new);
    /** Propellant one tank holds, in tonnes. */
    public static final double CAPACITY = 0.9;

    public PropellantTankBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PropellantTankBlockEntity(pos, state);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.stevespace.propellant_tank", CAPACITY)
                .withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
