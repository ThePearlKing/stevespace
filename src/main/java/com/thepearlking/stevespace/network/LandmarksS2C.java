package com.thepearlking.stevespace.network;

import com.thepearlking.stevespace.world.Landmarks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Server → player: the buildings big enough to see from space, world by world. */
public record LandmarksS2C(List<World> worlds) {

    public record World(String dimension, Map<Long, Landmarks.Cell> cells) {
    }

    public static void encode(LandmarksS2C msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.worlds.size());
        for (World w : msg.worlds) {
            buf.writeUtf(w.dimension());
            buf.writeVarInt(w.cells().size());
            w.cells().forEach((k, c) -> {
                buf.writeLong(k);
                buf.writeVarInt(c.count());
                buf.writeVarInt(c.minY());
                buf.writeVarInt(c.maxY());
                buf.writeInt(c.color());
            });
        }
    }

    public static LandmarksS2C decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<World> worlds = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String dimension = buf.readUtf();
            int m = buf.readVarInt();
            Map<Long, Landmarks.Cell> cells = new HashMap<>();
            for (int j = 0; j < m; j++) {
                long k = buf.readLong();
                cells.put(k, new Landmarks.Cell(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readInt()));
            }
            worlds.add(new World(dimension, cells));
        }
        return new LandmarksS2C(worlds);
    }

    public static void handle(LandmarksS2C msg, CustomPayloadEvent.Context ctx) {
        com.thepearlking.stevespace.client.planet.LandmarkRenderer.receive(msg);
        ctx.setPacketHandled(true);
    }
}
