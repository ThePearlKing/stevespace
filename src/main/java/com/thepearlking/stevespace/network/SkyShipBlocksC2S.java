package com.thepearlking.stevespace.network;

import com.thepearlking.stevespace.radio.SkyShips;
import com.thepearlking.stevespace.vessel.VesselStructure;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

import java.util.Map;

/** Player → server: the blocks of a ship seen in the sky, to draw it as it really is. */
public record SkyShipBlocksC2S(String id) {

    /** Bigger than this is drawn from its silhouette instead. */
    public static final int MAX_BLOCKS = 40000;

    public static void encode(SkyShipBlocksC2S msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.id);
    }

    public static SkyShipBlocksC2S decode(FriendlyByteBuf buf) {
        return new SkyShipBlocksC2S(buf.readUtf());
    }

    public static void handle(SkyShipBlocksC2S msg, CustomPayloadEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player != null) {
            var blocks = SkyShips.blocks(player.server, msg.id);
            if (blocks != null && blocks.size() <= MAX_BLOCKS) {
                SpaceNetwork.sendTo(player, new SkyShipBlocksS2C(msg.id, blocks.hashCode(), new VesselStructure(blocks, Map.of()).save()));
            }
        }
        ctx.setPacketHandled(true);
    }
}
