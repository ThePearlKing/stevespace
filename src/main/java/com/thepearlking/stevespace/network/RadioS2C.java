package com.thepearlking.stevespace.network;

import com.thepearlking.stevespace.radio.Radio;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

import java.util.ArrayList;
import java.util.List;

/** Server → player: open the radio, with the other radios and whether each can be reached. */
public record RadioS2C(List<Radio.Contact> contacts, double mhz) {

    public static void encode(RadioS2C msg, FriendlyByteBuf buf) {
        buf.writeDouble(msg.mhz);
        buf.writeVarInt(msg.contacts.size());
        for (Radio.Contact c : msg.contacts) {
            buf.writeUtf(c.name());
            buf.writeUtf(c.body());
            buf.writeDouble(c.distance());
            buf.writeUtf(c.blockedBy());
            buf.writeDouble(c.mhz());
        }
    }

    public static RadioS2C decode(FriendlyByteBuf buf) {
        double mhz = buf.readDouble();
        int n = buf.readVarInt();
        List<Radio.Contact> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(new Radio.Contact(buf.readUtf(), buf.readUtf(), buf.readDouble(), buf.readUtf(), buf.readDouble()));
        }
        return new RadioS2C(list, mhz);
    }

    public static void handle(RadioS2C msg, CustomPayloadEvent.Context ctx) {
        com.thepearlking.stevespace.client.gui.RadioScreen.open(msg.contacts, msg.mhz);
        ctx.setPacketHandled(true);
    }
}
