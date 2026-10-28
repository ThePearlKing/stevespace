package com.thepearlking.stevespace.block;

import com.thepearlking.stevespace.network.RadioS2C;
import com.thepearlking.stevespace.network.SpaceNetwork;
import com.thepearlking.stevespace.radio.Radio;
import com.thepearlking.stevespace.radio.RadioSites;
import com.thepearlking.stevespace.universe.ServerUniverse;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** A radio rack: put one in a ship, a station or a base, and use it to talk to the others. */
public class RadioBlock extends FacingBlock {

    public RadioBlock(Properties properties) {
        super(properties);
    }

    /** The name other radios see when nobody named it (placed by a ship landing, for one). */
    protected String defaultName() {
        return "Radio";
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (level instanceof ServerLevel server && !old.is(state.getBlock())) {
            var body = ServerUniverse.bodyOf(server);
            String name = defaultName() + (body == null || defaultName().equals("Mission Control") ? "" : " (" + body.displayName() + ")");
            RadioSites.get(server.getServer()).add(new RadioSites.Site(server.dimension().location().toString(), pos.immutable(), name));
        }
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel server && placer instanceof Player player && !defaultName().equals("Mission Control")) {
            RadioSites.get(server.getServer()).add(new RadioSites.Site(server.dimension().location().toString(), pos.immutable(),
                    player.getName().getString() + "'s radio"));
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState now, boolean moving) {
        if (level instanceof ServerLevel server && !now.is(state.getBlock())) {
            RadioSites.get(server.getServer()).remove(server.dimension().location().toString(), pos);
        }
        super.onRemove(state, level, pos, now, moving);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            openRadio(serverPlayer);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Sends the player the radio screen with who can be heard from here. */
    public static void openRadio(ServerPlayer player) {
        var contacts = Radio.contacts(player);
        if (contacts == null) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("stevespace.radio.no_radio"), true);
            return;
        }
        SpaceNetwork.sendTo(player, new RadioS2C(contacts, com.thepearlking.stevespace.radio.Radio.frequencyOf(player)));
    }
}
