package com.thepearlking.stevespace.network;

import com.thepearlking.stevespace.vessel.Autopilot;
import com.thepearlking.stevespace.vessel.VesselServer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** Pilot → server: engage an autopilot program (or turn it off); for TRAVEL, where to and whether to land. */
public record AutopilotC2S(Autopilot.Program program, String destination, boolean land, double latitude, double longitude) {

    public AutopilotC2S(Autopilot.Program program) {
        this(program, "", false);
    }

    public AutopilotC2S(Autopilot.Program program, String destination, boolean land) {
        this(program, destination, land, Double.NaN, Double.NaN);
    }

    public static void encode(AutopilotC2S msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.program);
        buf.writeUtf(msg.destination);
        buf.writeBoolean(msg.land);
        buf.writeDouble(msg.latitude);
        buf.writeDouble(msg.longitude);
    }

    public static AutopilotC2S decode(FriendlyByteBuf buf) {
        return new AutopilotC2S(buf.readEnum(Autopilot.Program.class), buf.readUtf(), buf.readBoolean(), buf.readDouble(), buf.readDouble());
    }

    public static void handle(AutopilotC2S msg, CustomPayloadEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player != null) {
            VesselServer.onAutopilot(player, msg.program, msg.destination, msg.land, msg.latitude, msg.longitude);
        }
        ctx.setPacketHandled(true);
    }
}
