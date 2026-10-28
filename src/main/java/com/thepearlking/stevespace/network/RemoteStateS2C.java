package com.thepearlking.stevespace.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** Server → remote pilot: flying by remote control (or not any more), the link, and the craft's functions. */
public record RemoteStateS2C(boolean active, int channel, String name, boolean signal, String blocker, double delay, int[] functions) {

    public static RemoteStateS2C off() {
        return new RemoteStateS2C(false, 0, "", false, "", 0, new int[5]);
    }

    public static void encode(RemoteStateS2C msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.active);
        buf.writeVarInt(msg.channel);
        buf.writeUtf(msg.name);
        buf.writeBoolean(msg.signal);
        buf.writeUtf(msg.blocker);
        buf.writeDouble(msg.delay);
        buf.writeVarIntArray(msg.functions);
    }

    public static RemoteStateS2C decode(FriendlyByteBuf buf) {
        return new RemoteStateS2C(buf.readBoolean(), buf.readVarInt(), buf.readUtf(), buf.readBoolean(), buf.readUtf(), buf.readDouble(),
                buf.readVarIntArray(5));
    }

    public static void handle(RemoteStateS2C msg, CustomPayloadEvent.Context ctx) {
        com.thepearlking.stevespace.client.RemoteClient.update(msg);
        ctx.setPacketHandled(true);
    }
}
