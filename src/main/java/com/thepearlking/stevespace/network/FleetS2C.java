package com.thepearlking.stevespace.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Server → player with the map open: the other ships in orbit they can see on it (their own, and
 * other people's that have a radio rack), with each one's orbit (relative to its body) at {@code time}.
 */
public record FleetS2C(double time, List<Ship> ships) {

    public record Ship(java.util.UUID id, String name, String body, boolean owned, double[] state) {
    }

    public static void encode(FleetS2C msg, FriendlyByteBuf buf) {
        buf.writeDouble(msg.time);
        buf.writeVarInt(msg.ships.size());
        for (Ship s : msg.ships) {
            buf.writeUUID(s.id);
            buf.writeUtf(s.name);
            buf.writeUtf(s.body);
            buf.writeBoolean(s.owned);
            for (double d : s.state) {
                buf.writeDouble(d);
            }
        }
    }

    public static FleetS2C decode(FriendlyByteBuf buf) {
        double time = buf.readDouble();
        int n = buf.readVarInt();
        List<Ship> ships = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            java.util.UUID id = buf.readUUID();
            String name = buf.readUtf();
            String body = buf.readUtf();
            boolean owned = buf.readBoolean();
            double[] state = new double[6];
            for (int k = 0; k < 6; k++) {
                state[k] = buf.readDouble();
            }
            ships.add(new Ship(id, name, body, owned, state));
        }
        return new FleetS2C(time, ships);
    }

    public static void handle(FleetS2C msg, CustomPayloadEvent.Context ctx) {
        com.thepearlking.stevespace.client.space.ClientFleet.receive(msg);
        ctx.setPacketHandled(true);
    }
}
