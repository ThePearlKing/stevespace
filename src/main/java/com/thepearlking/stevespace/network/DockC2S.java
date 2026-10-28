package com.thepearlking.stevespace.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

import java.util.UUID;

/**
 * Pilot → server. {@code request} true: send the list of ships to dock with. Otherwise: dock
 * port {@code mine} of this ship with port {@code theirs} of that ship (id, or cell if parked
 * without one).
 */
public record DockC2S(boolean request, UUID id, int cell, BlockPos mine, BlockPos theirs) {

    public static DockC2S list() {
        return new DockC2S(true, null, -1, BlockPos.ZERO, BlockPos.ZERO);
    }

    public static void encode(DockC2S msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.request);
        buf.writeBoolean(msg.id != null);
        if (msg.id != null) {
            buf.writeUUID(msg.id);
        }
        buf.writeVarInt(msg.cell + 1);
        buf.writeBlockPos(msg.mine);
        buf.writeBlockPos(msg.theirs);
    }

    public static DockC2S decode(FriendlyByteBuf buf) {
        boolean request = buf.readBoolean();
        UUID id = buf.readBoolean() ? buf.readUUID() : null;
        return new DockC2S(request, id, buf.readVarInt() - 1, buf.readBlockPos(), buf.readBlockPos());
    }

    public static void handle(DockC2S msg, CustomPayloadEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player != null) {
            if (msg.request) {
                SpaceNetwork.sendTo(player, com.thepearlking.stevespace.vessel.DockList.build(player));
            } else {
                com.thepearlking.stevespace.vessel.VesselServer.onDock(player, msg.id, msg.cell, msg.mine, msg.theirs);
            }
        }
        ctx.setPacketHandled(true);
    }
}
