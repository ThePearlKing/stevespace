package com.thepearlking.stevespace.network;

import com.thepearlking.stevespace.vessel.VesselControls;
import com.thepearlking.stevespace.vessel.VesselServer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** Pilot → server: throttle and attitude inputs, sent every tick while piloting. */
public record VesselControlC2S(float throttle, float pitch, float roll, float yaw) {

    public static void encode(VesselControlC2S msg, FriendlyByteBuf buf) {
        buf.writeFloat(msg.throttle);
        buf.writeFloat(msg.pitch);
        buf.writeFloat(msg.roll);
        buf.writeFloat(msg.yaw);
    }

    public static VesselControlC2S decode(FriendlyByteBuf buf) {
        return new VesselControlC2S(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    public static void handle(VesselControlC2S msg, CustomPayloadEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player != null) {
            VesselServer.onControls(player, new VesselControls(msg.throttle, msg.pitch, msg.roll, msg.yaw).sanitized());
        }
        ctx.setPacketHandled(true);
    }
}
