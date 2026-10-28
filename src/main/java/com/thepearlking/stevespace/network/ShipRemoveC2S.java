package com.thepearlking.stevespace.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

import java.util.UUID;

/** Player → server: {@code confirm} false asks about one of their ships (for the "are you sure?"), true removes it. */
public record ShipRemoveC2S(UUID id, boolean confirm) {

    public static void encode(ShipRemoveC2S msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.id);
        buf.writeBoolean(msg.confirm);
    }

    public static ShipRemoveC2S decode(FriendlyByteBuf buf) {
        return new ShipRemoveC2S(buf.readUUID(), buf.readBoolean());
    }

    public static void handle(ShipRemoveC2S msg, CustomPayloadEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player != null) {
            com.thepearlking.stevespace.vessel.ShipRemoval.handle(player, msg.id, msg.confirm);
        }
        ctx.setPacketHandled(true);
    }
}
