package com.thepearlking.stevespace.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** Remote pilot → server: a function key (1-5) pressed while flying the craft. */
public record RemoteFunctionC2S(int function) {

    public static void encode(RemoteFunctionC2S msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.function);
    }

    public static RemoteFunctionC2S decode(FriendlyByteBuf buf) {
        return new RemoteFunctionC2S(buf.readVarInt());
    }

    public static void handle(RemoteFunctionC2S msg, CustomPayloadEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player != null) {
            com.thepearlking.stevespace.remote.RemoteControl.function(player, msg.function);
        }
        ctx.setPacketHandled(true);
    }
}
