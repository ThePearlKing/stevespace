package com.thepearlking.stevespace.network;

import com.thepearlking.stevespace.nuke.NuclearAftermath;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

import java.util.ArrayList;
import java.util.List;

/** Server → player on joining: past detonations still leaving fallout or a radiation belt. */
public record NukeHistoryS2C(List<NuclearAftermath.Event> events) {

    public static void encode(NukeHistoryS2C msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.events.size());
        for (NuclearAftermath.Event e : msg.events) {
            buf.writeUtf(e.dimension());
            buf.writeDouble(e.x());
            buf.writeDouble(e.y());
            buf.writeDouble(e.z());
            buf.writeDouble(e.kt());
            buf.writeVarInt(e.regime());
            buf.writeUtf(e.body());
            buf.writeDouble(e.shell());
            buf.writeDouble(e.time());
        }
    }

    public static NukeHistoryS2C decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<NuclearAftermath.Event> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(new NuclearAftermath.Event(buf.readUtf(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readVarInt(), buf.readUtf(), buf.readDouble(), buf.readDouble()));
        }
        return new NukeHistoryS2C(list);
    }

    public static void handle(NukeHistoryS2C msg, CustomPayloadEvent.Context ctx) {
        NuclearAftermath.CLIENT.clear();
        NuclearAftermath.CLIENT.addAll(msg.events);
        ctx.setPacketHandled(true);
    }
}
