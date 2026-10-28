package com.thepearlking.stevespace.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** Server → player: a ship's blocks (this version of them), for drawing it in the sky. */
public record SkyShipBlocksS2C(String id, long version, CompoundTag structure) {

    public static void encode(SkyShipBlocksS2C msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.id);
        buf.writeLong(msg.version);
        buf.writeNbt(msg.structure);
    }

    public static SkyShipBlocksS2C decode(FriendlyByteBuf buf) {
        return new SkyShipBlocksS2C(buf.readUtf(), buf.readLong(), buf.readNbt());
    }

    public static void handle(SkyShipBlocksS2C msg, CustomPayloadEvent.Context ctx) {
        com.thepearlking.stevespace.client.telescope.SkyShipModels.receive(msg);
        ctx.setPacketHandled(true);
    }
}
