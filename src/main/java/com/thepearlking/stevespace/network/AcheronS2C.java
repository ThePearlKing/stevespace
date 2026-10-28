package com.thepearlking.stevespace.network;

import com.thepearlking.stevespace.universe.AcheronCourse;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** Server → player: when Acheron passes (NaN: it doesn't). */
public record AcheronS2C(double pass) {

    public static void encode(AcheronS2C msg, FriendlyByteBuf buf) {
        buf.writeDouble(msg.pass);
    }

    public static AcheronS2C decode(FriendlyByteBuf buf) {
        return new AcheronS2C(buf.readDouble());
    }

    public static void handle(AcheronS2C msg, CustomPayloadEvent.Context ctx) {
        AcheronCourse.setPass(msg.pass);
        ctx.setPacketHandled(true);
    }
}
