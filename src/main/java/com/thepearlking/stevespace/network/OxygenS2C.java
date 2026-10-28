package com.thepearlking.stevespace.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** Server → player: suit oxygen left (0..1), whether there is air here, and the hazard you are exposed to (key or ""). */
public record OxygenS2C(float oxygen, boolean airless, boolean sealed, boolean unlimited, String hazard) {

    public static void encode(OxygenS2C msg, FriendlyByteBuf buf) {
        buf.writeFloat(msg.oxygen);
        buf.writeBoolean(msg.airless);
        buf.writeBoolean(msg.sealed);
        buf.writeBoolean(msg.unlimited);
        buf.writeUtf(msg.hazard);
    }

    public static OxygenS2C decode(FriendlyByteBuf buf) {
        return new OxygenS2C(buf.readFloat(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readUtf());
    }

    public static void handle(OxygenS2C msg, CustomPayloadEvent.Context ctx) {
        com.thepearlking.stevespace.client.OxygenHud.update(msg);
        ctx.setPacketHandled(true);
    }
}
