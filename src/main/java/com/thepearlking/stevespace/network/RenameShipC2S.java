package com.thepearlking.stevespace.network;

import com.thepearlking.stevespace.vessel.VesselEntity;
import com.thepearlking.stevespace.world.ParkedShips;
import com.thepearlking.stevespace.world.SpaceDimension;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** Player → server: a new name for the ship they're in or flying by remote control (from the map). */
public record RenameShipC2S(String name) {

    public static final int MAX = 40;

    public static void encode(RenameShipC2S msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.name, MAX * 4);
    }

    public static RenameShipC2S decode(FriendlyByteBuf buf) {
        return new RenameShipC2S(buf.readUtf(MAX * 4));
    }

    public static void handle(RenameShipC2S msg, CustomPayloadEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player != null) {
            rename(player, msg.name);
        }
        ctx.setPacketHandled(true);
    }

    static void rename(ServerPlayer player, String raw) {
        String name = ChatFormatting.stripFormatting(raw).trim();
        if (name.isEmpty()) {
            return;
        }
        if (name.length() > MAX) {
            name = name.substring(0, MAX);
        }
        VesselEntity ship = VesselEntity.shipOf(player);
        if (ship != null) {
            ship.setCustomName(Component.literal(name));
        } else if (SpaceDimension.isSpace(player.level())) {
            ParkedShips parkedShips = ParkedShips.get(player.server);
            var parked = parkedShips.at(player.getX());
            if (parked == null || !parked.contains(player.getX(), player.getY(), player.getZ(), 48)) {
                return;
            }
            parkedShips.put(parked.withName(name));
        } else {
            return;
        }
        player.displayClientMessage(Component.translatable("stevespace.ship.renamed", name), true);
    }
}
