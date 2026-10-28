package com.thepearlking.stevespace.network;

import com.thepearlking.stevespace.vessel.VesselEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** Pilot → server: which way SAS should hold the ship. */
public record SasC2S(int mode) {

    public static void encode(SasC2S msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.mode);
    }

    public static SasC2S decode(FriendlyByteBuf buf) {
        return new SasC2S(buf.readVarInt());
    }

    public static void handle(SasC2S msg, CustomPayloadEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        VesselEntity.Sas[] modes = VesselEntity.Sas.values();
        if (player != null && VesselEntity.shipOf(player) instanceof VesselEntity vessel && vessel.pilot() == player
                && msg.mode >= 0 && msg.mode < modes.length) {
            vessel.setSas(modes[msg.mode], null);
        }
        ctx.setPacketHandled(true);
    }
}
