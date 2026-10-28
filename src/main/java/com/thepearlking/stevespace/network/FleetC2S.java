package com.thepearlking.stevespace.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** Player → server, every couple of seconds while the map is open: send the ships to show on it. */
public record FleetC2S() {

    public static void encode(FleetC2S msg, FriendlyByteBuf buf) {
    }

    public static FleetC2S decode(FriendlyByteBuf buf) {
        return new FleetC2S();
    }

    public static void handle(FleetC2S msg, CustomPayloadEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player != null) {
            SpaceNetwork.sendTo(player, com.thepearlking.stevespace.vessel.DockList.fleet(player));
        }
        ctx.setPacketHandled(true);
    }
}
