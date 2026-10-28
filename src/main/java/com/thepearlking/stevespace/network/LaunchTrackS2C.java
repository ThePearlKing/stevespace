package com.thepearlking.stevespace.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

/**
 * Server → players on a planet: a rocket in its air, burning or falling fast, and where (map
 * coordinates of that planet), so its trail can be seen from far beyond the entity's range.
 */
public record LaunchTrackS2C(int id, double x, double y, double z, float throttle, float speed) {

    public static void encode(LaunchTrackS2C msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.id);
        buf.writeDouble(msg.x);
        buf.writeDouble(msg.y);
        buf.writeDouble(msg.z);
        buf.writeFloat(msg.throttle);
        buf.writeFloat(msg.speed);
    }

    public static LaunchTrackS2C decode(FriendlyByteBuf buf) {
        return new LaunchTrackS2C(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat());
    }

    public static void handle(LaunchTrackS2C msg, CustomPayloadEvent.Context ctx) {
        com.thepearlking.stevespace.client.vessel.LaunchTrails.track(msg);
        ctx.setPacketHandled(true);
    }
}
