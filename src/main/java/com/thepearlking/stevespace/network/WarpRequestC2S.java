package com.thepearlking.stevespace.network;

import com.thepearlking.stevespace.universe.TimeWarp;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** Player → server: faster (+1) or slower (-1) time warp. */
public record WarpRequestC2S(int step) {

    public static void encode(WarpRequestC2S msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.step);
    }

    public static WarpRequestC2S decode(FriendlyByteBuf buf) {
        return new WarpRequestC2S(buf.readByte());
    }

    public static void handle(WarpRequestC2S msg, CustomPayloadEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player != null) {
            TimeWarp.request(player, Integer.signum(msg.step));
        }
        ctx.setPacketHandled(true);
    }
}
