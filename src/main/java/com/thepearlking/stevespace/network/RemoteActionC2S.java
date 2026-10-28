package com.thepearlking.stevespace.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** Player → server: a button on a console or receiver screen (tune, connect, function, refresh). */
public record RemoteActionC2S(BlockPos pos, String action, int value, String probe) {

    public static void encode(RemoteActionC2S msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeUtf(msg.action, 16);
        buf.writeVarInt(msg.value);
        buf.writeUtf(msg.probe, 256);
    }

    public static RemoteActionC2S decode(FriendlyByteBuf buf) {
        return new RemoteActionC2S(buf.readBlockPos(), buf.readUtf(16), buf.readVarInt(), buf.readUtf(256));
    }

    public static void handle(RemoteActionC2S msg, CustomPayloadEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player != null) {
            com.thepearlking.stevespace.remote.RemoteControl.action(player, msg.pos, msg.action, msg.value, msg.probe);
        }
        ctx.setPacketHandled(true);
    }
}
