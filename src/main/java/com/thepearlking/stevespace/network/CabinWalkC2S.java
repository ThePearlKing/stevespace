package com.thepearlking.stevespace.network;

import com.thepearlking.stevespace.vessel.VesselEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** Player walking about a flying ship → server: where they are now (ship block coordinates). */
public record CabinWalkC2S(double x, double y, double z) {

    public static void encode(CabinWalkC2S msg, FriendlyByteBuf buf) {
        buf.writeDouble(msg.x);
        buf.writeDouble(msg.y);
        buf.writeDouble(msg.z);
    }

    public static CabinWalkC2S decode(FriendlyByteBuf buf) {
        return new CabinWalkC2S(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public static void handle(CabinWalkC2S msg, CustomPayloadEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player != null && player.getVehicle() instanceof VesselEntity vessel) {
            vessel.setWalkPosition(player, msg.x, msg.y, msg.z);
        }
        ctx.setPacketHandled(true);
    }
}
