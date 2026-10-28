package com.thepearlking.stevespace.block;

import com.mojang.serialization.MapCodec;
import com.thepearlking.stevespace.network.FlightComputerS2C;
import com.thepearlking.stevespace.network.SpaceNetwork;
import com.thepearlking.stevespace.vessel.VesselReport;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** The vessel's computer: right-click for a pre-flight check of the vessel it is part of. */
public class FlightComputerBlock extends FacingBlock {

    public static final MapCodec<FlightComputerBlock> CODEC = simpleCodec(FlightComputerBlock::new);

    public FlightComputerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer serverPlayer) {
            SpaceNetwork.sendTo(serverPlayer, new FlightComputerS2C(VesselReport.analyze(server, pos)));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
