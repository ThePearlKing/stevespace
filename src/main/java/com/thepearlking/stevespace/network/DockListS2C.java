package com.thepearlking.stevespace.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server → pilot: the ships they can dock with (theirs with a docking port; anyone else's with a
 * docking port and a radio rack), each one's free ports, and their own ship's free ports.
 */
public record DockListS2C(boolean rcs, List<Port> mine, List<Entry> ships) {

    /** A free docking port: its block (from its ship's origin), which side of the ship it's on, and a number if several are. */
    public record Port(BlockPos pos, String side, int number) {
    }

    /** A ship: its id (null for an old parked one, then its cell), name, the world it orbits, how far (-1 unknown), whether it's yours. */
    public record Entry(UUID id, int cell, String name, String body, double distance, boolean owned, List<Port> ports) {
    }

    public static void encode(DockListS2C msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.rcs);
        writePorts(buf, msg.mine);
        buf.writeVarInt(msg.ships.size());
        for (Entry e : msg.ships) {
            buf.writeBoolean(e.id != null);
            if (e.id != null) {
                buf.writeUUID(e.id);
            }
            buf.writeVarInt(e.cell + 1);
            buf.writeUtf(e.name);
            buf.writeUtf(e.body);
            buf.writeDouble(e.distance);
            buf.writeBoolean(e.owned);
            writePorts(buf, e.ports);
        }
    }

    public static DockListS2C decode(FriendlyByteBuf buf) {
        boolean rcs = buf.readBoolean();
        List<Port> mine = readPorts(buf);
        int count = buf.readVarInt();
        List<Entry> ships = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            UUID id = buf.readBoolean() ? buf.readUUID() : null;
            int cell = buf.readVarInt() - 1;
            ships.add(new Entry(id, cell, buf.readUtf(), buf.readUtf(), buf.readDouble(), buf.readBoolean(), readPorts(buf)));
        }
        return new DockListS2C(rcs, mine, ships);
    }

    private static void writePorts(FriendlyByteBuf buf, List<Port> ports) {
        buf.writeVarInt(ports.size());
        for (Port p : ports) {
            buf.writeBlockPos(p.pos);
            buf.writeUtf(p.side);
            buf.writeVarInt(p.number);
        }
    }

    private static List<Port> readPorts(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<Port> ports = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ports.add(new Port(buf.readBlockPos(), buf.readUtf(), buf.readVarInt()));
        }
        return ports;
    }

    public static void handle(DockListS2C msg, CustomPayloadEvent.Context ctx) {
        com.thepearlking.stevespace.client.gui.DockScreen.receive(msg);
        ctx.setPacketHandled(true);
    }
}
