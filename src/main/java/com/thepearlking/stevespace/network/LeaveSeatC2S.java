package com.thepearlking.stevespace.network;

import com.thepearlking.stevespace.vessel.VesselEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** Player → server: get out of the vessel seat (sneaking alone won't, it is the throttle key). */
public record LeaveSeatC2S() {

    public static void encode(LeaveSeatC2S msg, FriendlyByteBuf buf) {
    }

    public static LeaveSeatC2S decode(FriendlyByteBuf buf) {
        return new LeaveSeatC2S();
    }

    public static void handle(LeaveSeatC2S msg, CustomPayloadEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player != null && com.thepearlking.stevespace.remote.RemoteControl.isRemote(player)) {
            com.thepearlking.stevespace.remote.RemoteControl.disconnect(player, "stevespace.remote.disconnected");
        } else if (player != null && player.getVehicle() instanceof VesselEntity vessel) {
            vessel.requestDismount(player);
        } else if (player != null && player.getVehicle() instanceof com.thepearlking.stevespace.block.SeatEntity seat) {
            seat.requestDismount(player);
        }
        ctx.setPacketHandled(true);
    }
}
