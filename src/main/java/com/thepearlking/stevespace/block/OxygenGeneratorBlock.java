package com.thepearlking.stevespace.block;

import com.thepearlking.stevespace.suit.Habitats;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Fills a sealed room with air (see {@link Habitats}). Click it to see whether the room holds. */
public class OxygenGeneratorBlock extends FacingBlock implements net.minecraft.world.level.block.EntityBlock {

    public OxygenGeneratorBlock(Properties properties) {
        super(properties);
    }

    /** Its block entity only re-registers the generator when its chunk loads again. */
    @Override
    public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new OxygenGeneratorBlockEntity(pos, state);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (level instanceof ServerLevel server) {
            Habitats.addGenerator(server, pos.immutable());
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState now, boolean moving) {
        if (level instanceof ServerLevel server && !now.is(state.getBlock())) {
            Habitats.removeGenerator(server, pos);
        }
        super.onRemove(state, level, pos, now, moving);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server) {
            Habitats.addGenerator(server, pos.immutable()); // check now, not in two seconds
            int volume = Habitats.volume(level, pos);
            Component message;
            if (!Habitats.hasPower(level, pos) && volume == 0) {
                message = Component.translatable("stevespace.oxygen_generator.no_power");
            } else {
                message = volume > 0 ? Component.translatable("stevespace.oxygen_generator.sealed", volume,
                        com.thepearlking.stevespace.block.OxygenGeneratorBlockEntity.cost(volume))
                        : Component.translatable("stevespace.oxygen_generator.leaking");
            }
            player.displayClientMessage(message, true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
