package com.thepearlking.stevespace.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

import java.util.ArrayList;
import java.util.List;

/** Server → player: open a remote control console, with every craft listening on its channel. */
public record RemoteConsoleS2C(BlockPos pos, int channel, List<Entry> craft, int[] functions) {

    /**
     * One craft: its id (for the buttons), name, world, where (orbit, landed...), distance
     * (blocks), light delay (seconds, or -1 with no signal), what's in the way, and how many
     * relays it has on each function 1-5.
     */
    public record Entry(String id, String name, String body, String where, double distance, double delay, String blocker, int[] functions) {
    }

    public static void encode(RemoteConsoleS2C msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeVarInt(msg.channel);
        buf.writeVarInt(msg.craft.size());
        for (Entry e : msg.craft) {
            buf.writeUtf(e.id());
            buf.writeUtf(e.name());
            buf.writeUtf(e.body());
            buf.writeUtf(e.where());
            buf.writeDouble(e.distance());
            buf.writeDouble(e.delay());
            buf.writeUtf(e.blocker());
            buf.writeVarIntArray(e.functions());
        }
        buf.writeVarIntArray(msg.functions);
    }

    public static RemoteConsoleS2C decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int channel = buf.readVarInt();
        int n = buf.readVarInt();
        List<Entry> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(new Entry(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readDouble(), buf.readDouble(),
                    buf.readUtf(), buf.readVarIntArray(5)));
        }
        return new RemoteConsoleS2C(pos, channel, list, buf.readVarIntArray(5));
    }

    public static void handle(RemoteConsoleS2C msg, CustomPayloadEvent.Context ctx) {
        com.thepearlking.stevespace.client.gui.RemoteConsoleScreen.open(msg);
        ctx.setPacketHandled(true);
    }
}
