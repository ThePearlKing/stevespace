package com.thepearlking.stevespace.block;

import com.thepearlking.stevespace.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/** Science instruments: the magnetometer and the plasma wave receiver. */
public final class ScienceBlocks {

    private ScienceBlocks() {
    }

    /** Shows the magnetic field where it stands, live, on its screen. */
    public static class Magnetometer extends FacingBlock implements EntityBlock {
        public Magnetometer(Properties p) {
            super(p);
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new MagnetometerEntity(pos, state);
        }
    }

    public static class MagnetometerEntity extends BlockEntity {
        public MagnetometerEntity(BlockPos pos, BlockState state) {
            super(ModBlockEntities.MAGNETOMETER.get(), pos, state);
        }
    }

    /**
     * Turns the waves in the plasma around a planet into sound, as real spacecraft do: switch it
     * on and listen. What you hear depends on whose magnetic field you are in.
     */
    public static class PlasmaReceiver extends FacingBlock implements EntityBlock {
        public static final BooleanProperty LISTENING = BlockStateProperties.POWERED;

        public PlasmaReceiver(Properties p) {
            super(p);
            registerDefaultState(defaultBlockState().setValue(LISTENING, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            super.createBlockStateDefinition(builder);
            builder.add(LISTENING);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (!level.isClientSide) {
                boolean on = !state.getValue(LISTENING);
                level.setBlockAndUpdate(pos, state.setValue(LISTENING, on));
                player.displayClientMessage(Component.translatable(on ? "stevespace.plasma.on" : "stevespace.plasma.off"), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new PlasmaReceiverEntity(pos, state);
        }

        @Override
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            if (!level.isClientSide || type != ModBlockEntities.PLASMA_RECEIVER.get()) {
                return null;
            }
            return (lvl, pos, st, be) -> PlasmaReceiverEntity.clientTick((PlasmaReceiverEntity) be);
        }
    }

    public static class PlasmaReceiverEntity extends BlockEntity {
        /** Client side: the sound playing now (an opaque handle for the client code). */
        public Object playing;

        public PlasmaReceiverEntity(BlockPos pos, BlockState state) {
            super(ModBlockEntities.PLASMA_RECEIVER.get(), pos, state);
        }

        static void clientTick(PlasmaReceiverEntity be) {
            com.thepearlking.stevespace.client.PlasmaSound.tick(be);
        }
    }
}
