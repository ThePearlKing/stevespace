package com.thepearlking.stevespace.block;

import com.thepearlking.stevespace.network.MissionControlS2C;
import com.thepearlking.stevespace.network.SpaceNetwork;
import com.thepearlking.stevespace.vessel.VesselEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** A tracking console: lists every vessel on the server and shows its orbit on the map. */
public class MissionControlBlock extends RadioBlock {

    public MissionControlBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected String defaultName() {
        return "Mission Control";
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            var server = serverPlayer.server;
            SpaceNetwork.sendTo(serverPlayer, new MissionControlS2C(
                    com.thepearlking.stevespace.universe.ServerUniverse.time(server), VesselEntity.trackingList(server)));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
