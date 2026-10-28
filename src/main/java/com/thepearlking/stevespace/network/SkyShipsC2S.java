package com.thepearlking.stevespace.network;

import com.thepearlking.stevespace.radio.SkyShips;
import com.thepearlking.stevespace.universe.ServerUniverse;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** Player at a telescope → server: which ships can be seen? */
public record SkyShipsC2S() {

    public static void encode(SkyShipsC2S msg, FriendlyByteBuf buf) {
    }

    public static SkyShipsC2S decode(FriendlyByteBuf buf) {
        return new SkyShipsC2S();
    }

    public static void handle(SkyShipsC2S msg, CustomPayloadEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player != null) {
            SpaceNetwork.sendTo(player, new SkyShipsS2C(ServerUniverse.time(player.server), SkyShips.list(player.server)));
        }
        ctx.setPacketHandled(true);
    }
}
