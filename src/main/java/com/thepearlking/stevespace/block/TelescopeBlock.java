package com.thepearlking.stevespace.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.fml.loading.FMLEnvironment;

/** A telescope on a tripod. Look through it to see the planets as discs, Saturn's rings and the moons of Jupiter. */
public class TelescopeBlock extends FacingBlock {

    private static final VoxelShape SHAPE = Shapes.box(0.2, 0, 0.2, 0.8, 0.95, 0.8);

    public TelescopeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide && FMLEnvironment.dist.isClient()) {
            com.thepearlking.stevespace.client.TelescopeView.toggle(pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
