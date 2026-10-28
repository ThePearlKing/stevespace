package com.thepearlking.stevespace.block;

import com.mojang.serialization.MapCodec;
import com.thepearlking.stevespace.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A geyser vent on an icy moon. Every so often it erupts: a tall plume of vapour and ice, and
 * anything standing over the vent goes up with it (in Enceladus' gravity, a very long way).
 * Triton's nitrogen geysers carry dark dust; Io's lava vents throw up molten rock and set
 * whatever they lift alight.
 */
public class GeyserBlock extends BaseEntityBlock {

    public static final int ICE = 0;
    public static final int DARK = 1;
    public static final int LAVA = 2;
    public static final MapCodec<GeyserBlock> CODEC = simpleCodec(p -> new GeyserBlock(p, ICE));
    private final int kind;

    public GeyserBlock(Properties properties, int kind) {
        super(properties);
        this.kind = kind;
    }

    public int kind() {
        return kind;
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
        return new GeyserBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.GEYSER.get(), GeyserBlockEntity::tick);
    }
}
