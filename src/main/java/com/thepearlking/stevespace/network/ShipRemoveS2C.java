package com.thepearlking.stevespace.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

import java.util.UUID;

/** Server → player: the ship they asked about removing (its picture, and whether it has a docking port). */
public record ShipRemoveS2C(UUID id, String name, DockListS2C.Shape shape, boolean hasPort) {

    public static void encode(ShipRemoveS2C msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.id);
        buf.writeUtf(msg.name);
        msg.shape.write(buf);
        buf.writeBoolean(msg.hasPort);
    }

    public static ShipRemoveS2C decode(FriendlyByteBuf buf) {
        return new ShipRemoveS2C(buf.readUUID(), buf.readUtf(), DockListS2C.Shape.read(buf), buf.readBoolean());
    }

    public static void handle(ShipRemoveS2C msg, CustomPayloadEvent.Context ctx) {
        com.thepearlking.stevespace.client.gui.RemoveShipScreen.receive(msg);
        ctx.setPacketHandled(true);
    }
}
