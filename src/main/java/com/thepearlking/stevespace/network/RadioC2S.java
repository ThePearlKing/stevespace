package com.thepearlking.stevespace.network;

import com.thepearlking.stevespace.block.RadioBlock;
import com.thepearlking.stevespace.radio.Radio;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** Player → server: open the radio (empty text) or transmit a message. */
public record RadioC2S(String text) {

    public static void encode(RadioC2S msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.text, 256);
    }

    public static RadioC2S decode(FriendlyByteBuf buf) {
        return new RadioC2S(buf.readUtf(256));
    }

    public static void handle(RadioC2S msg, CustomPayloadEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player != null) {
            if (msg.text.isEmpty()) {
                RadioBlock.openRadio(player);
            } else {
                Radio.transmit(player, msg.text);
            }
        }
        ctx.setPacketHandled(true);
    }
}
