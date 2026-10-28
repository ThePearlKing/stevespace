package com.thepearlking.stevespace.remote;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** A block tuned to a remote control channel (1-99). The channel goes with it when a ship flies. */
public abstract class ChannelEntity extends BlockEntity {

    public static final int MIN = 1;
    public static final int MAX = 99;

    private int channel = MIN;

    protected ChannelEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public int channel() {
        return channel;
    }

    public void setChannel(int channel) {
        this.channel = Math.max(MIN, Math.min(MAX, channel));
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** The channel saved with a ship's blocks (or MIN if it never had one). */
    public static int channelOf(CompoundTag tag) {
        return tag != null && tag.contains("channel") ? Math.max(MIN, Math.min(MAX, tag.getInt("channel"))) : MIN;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        channel = channelOf(tag);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("channel", channel);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
