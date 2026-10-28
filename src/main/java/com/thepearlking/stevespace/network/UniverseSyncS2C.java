package com.thepearlking.stevespace.network;

import com.thepearlking.stevespace.client.ClientUniverse;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** Server → client: the universe clock, warp factor and day length (with its rotation anchor). */
public record UniverseSyncS2C(double time, double warp, double earthDaySeconds,
                              double rotationAnchorTime, double rotationAnchorTurns) {

    public static void encode(UniverseSyncS2C msg, FriendlyByteBuf buf) {
        buf.writeDouble(msg.time);
        buf.writeDouble(msg.warp);
        buf.writeDouble(msg.earthDaySeconds);
        buf.writeDouble(msg.rotationAnchorTime);
        buf.writeDouble(msg.rotationAnchorTurns);
    }

    public static UniverseSyncS2C decode(FriendlyByteBuf buf) {
        return new UniverseSyncS2C(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public static void handle(UniverseSyncS2C msg, CustomPayloadEvent.Context ctx) {
        ClientUniverse.receive(msg.time, msg.warp, msg.earthDaySeconds, msg.rotationAnchorTime, msg.rotationAnchorTurns);
        ctx.setPacketHandled(true);
    }
}
