package com.thepearlking.stevespace.block;

import com.thepearlking.stevespace.universe.ServerUniverse;
import com.thepearlking.stevespace.world.ParkedShips;
import com.thepearlking.stevespace.world.SpaceDimension;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Gives a ship parked in orbit normal gravity toward its floor. It works by itself; clicking it
 * says whether it is running.
 */
public class GravityGeneratorBlock extends FacingBlock implements net.minecraft.world.level.block.EntityBlock {

    public GravityGeneratorBlock(Properties properties) {
        super(properties);
    }

    /** Its power store (it draws {@link com.thepearlking.stevespace.vessel.SpaceCrew#GRAVITY_COST} per tick in use). */
    public static class Entity extends com.thepearlking.stevespace.energy.PowerBlockEntity {
        public Entity(BlockPos pos, BlockState state) {
            super(com.thepearlking.stevespace.registry.ModBlockEntities.GRAVITY_GENERATOR.get(), pos, state, 12_000, 600, 0);
        }

        @Override
        public Role role() {
            return Role.SINK;
        }
    }

    @Override
    public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer p) {
            Component message;
            if (!SpaceDimension.isSpace(level)) {
                var body = ServerUniverse.bodyOf(level);
                message = Component.translatable("stevespace.gravity_generator.planet", body == null ? "" : body.displayName());
            } else {
                ParkedShips.Parked ship = ParkedShips.get(p.server).at(pos.getX() + 0.5);
                boolean aboard = ship != null && ship.contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 2);
                boolean power = level.getBlockEntity(pos) instanceof Entity e && e.buffer().getEnergyStored() > 0;
                message = Component.translatable(!aboard ? "stevespace.gravity_generator.no_ship"
                        : power ? "stevespace.gravity_generator.on" : "stevespace.gravity_generator.no_power");
            }
            p.displayClientMessage(message, true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

}
