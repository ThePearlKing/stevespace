package com.thepearlking.stevespace.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** Server → player: open a receiver's or relay's channel tuner (function 1-5 for a relay, 0 for a receiver). */
public record RemoteTunerS2C(BlockPos pos, int channel, int function) {

    public static void encode(RemoteTunerS2C msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeVarInt(msg.channel);
        buf.writeVarInt(msg.function);
    }

    public static RemoteTunerS2C decode(FriendlyByteBuf buf) {
        return new RemoteTunerS2C(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(RemoteTunerS2C msg, CustomPayloadEvent.Context ctx) {
        com.thepearlking.stevespace.client.gui.RemoteTunerScreen.open(msg.pos, msg.channel, msg.function);
        ctx.setPacketHandled(true);
    }
}
