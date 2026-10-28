package com.thepearlking.stevespace.network;

import com.thepearlking.stevespace.block.RadioBlock;
import com.thepearlking.stevespace.radio.Radio;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** Player → server: tune my radio to this frequency (MHz); the radio screen reopens with it. */
public record RadioTuneC2S(double mhz) {

    public static void encode(RadioTuneC2S msg, FriendlyByteBuf buf) {
        buf.writeDouble(msg.mhz);
    }

    public static RadioTuneC2S decode(FriendlyByteBuf buf) {
        return new RadioTuneC2S(buf.readDouble());
    }

    public static void handle(RadioTuneC2S msg, CustomPayloadEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player != null && Double.isFinite(msg.mhz)) {
            Radio.tune(player, msg.mhz);
            RadioBlock.openRadio(player);
        }
        ctx.setPacketHandled(true);
    }
}
