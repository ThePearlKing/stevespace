package com.thepearlking.stevespace.network;

import com.thepearlking.stevespace.vessel.VesselEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** Pilot → server: the maneuver node planned on the map (and whether to fly it now). */
public record ManeuverC2S(double time, double prograde, double normal, double radial, boolean execute) {

    public static void encode(ManeuverC2S msg, FriendlyByteBuf buf) {
        buf.writeDouble(msg.time);
        buf.writeDouble(msg.prograde);
        buf.writeDouble(msg.normal);
        buf.writeDouble(msg.radial);
        buf.writeBoolean(msg.execute);
    }

    public static ManeuverC2S decode(FriendlyByteBuf buf) {
        return new ManeuverC2S(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readBoolean());
    }

    public static void handle(ManeuverC2S msg, CustomPayloadEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player != null && VesselEntity.shipOf(player) instanceof VesselEntity vessel && vessel.pilot() == player
                && Double.isFinite(msg.time + msg.prograde + msg.normal + msg.radial)) {
            vessel.planNode(msg.time, msg.prograde, msg.normal, msg.radial, msg.execute);
        }
        ctx.setPacketHandled(true);
    }
}
