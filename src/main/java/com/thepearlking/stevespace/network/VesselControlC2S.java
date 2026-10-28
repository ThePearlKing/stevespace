package com.thepearlking.stevespace.network;

import com.thepearlking.stevespace.vessel.VesselControls;
import com.thepearlking.stevespace.vessel.VesselServer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** Pilot → server: throttle, attitude and RCS inputs, sent every tick while piloting. */
public record VesselControlC2S(float throttle, float pitch, float roll, float yaw, float forward, float right, float up) {

    public VesselControlC2S(float throttle, float pitch, float roll, float yaw) {
        this(throttle, pitch, roll, yaw, 0, 0, 0);
    }

    public static void encode(VesselControlC2S msg, FriendlyByteBuf buf) {
        buf.writeFloat(msg.throttle);
        buf.writeFloat(msg.pitch);
        buf.writeFloat(msg.roll);
        buf.writeFloat(msg.yaw);
        buf.writeFloat(msg.forward);
        buf.writeFloat(msg.right);
        buf.writeFloat(msg.up);
    }

    public static VesselControlC2S decode(FriendlyByteBuf buf) {
        return new VesselControlC2S(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    public static void handle(VesselControlC2S msg, CustomPayloadEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player != null) {
            VesselServer.onControls(player, new VesselControls(msg.throttle, msg.pitch, msg.roll, msg.yaw,
                    msg.forward, msg.right, msg.up).sanitized());
        }
        ctx.setPacketHandled(true);
    }
}
