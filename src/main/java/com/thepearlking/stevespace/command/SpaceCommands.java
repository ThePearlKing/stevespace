package com.thepearlking.stevespace.command;

import com.mojang.brigadier.CommandDispatcher;
import com.thepearlking.stevespace.block.SeatEntity;
import com.thepearlking.stevespace.vessel.VesselEntity;
import com.thepearlking.stevespace.world.ParkedShips;
import com.thepearlking.stevespace.world.SpaceDimension;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * {@code /stevespace}: the mod's own commands.
 * <ul>
 * <li>{@code ship}: back aboard your own ship, wherever it is (flying, parked in orbit).</li>
 * <li>{@code home}: safely down on the ground at the world's spawn.</li>
 * <li>{@code goto <world>}: (operators) onto any world you can land on.</li>
 * </ul>
 */
public final class SpaceCommands {

    /** Where a player's ship is remembered in their saved data: "v:" + vessel id, or "p:" + parked cell. */
    private static final String LAST_SHIP = "stevespace_last_ship";

    private SpaceCommands() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("stevespace")
                .then(Commands.literal("ship").executes(c -> toShip(c.getSource().getPlayerOrException()) ? 1 : 0))
                .then(Commands.literal("home").executes(c -> {
                    home(c.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("goto").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("world", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(landable().keySet(), b))
                                .executes(c -> goTo(c.getSource(), c.getSource().getPlayerOrException(),
                                        com.mojang.brigadier.arguments.StringArgumentType.getString(c, "world")) ? 1 : 0)))
                .then(Commands.literal("event").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("acheron").executes(c -> {
                            if (!com.thepearlking.stevespace.config.SpaceConfig.ACHERON.get()) {
                                c.getSource().sendFailure(Component.translatable("stevespace.command.event.off"));
                                return 0;
                            }
                            com.thepearlking.stevespace.world.AcheronEvent.summon(c.getSource().getServer());
                            c.getSource().sendSuccess(() -> Component.translatable("stevespace.command.event.acheron"), true);
                            return 1;
                        }))));
    }

    /** Remembers the ship each pilot flies (and where it parks), so they can always get back to it. */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 20 != 0) {
            return;
        }
        for (VesselEntity v : VesselEntity.allServerVessels()) {
            if (!v.isRemoved() && v.pilot() instanceof ServerPlayer p) {
                p.getPersistentData().putString(LAST_SHIP, "v:" + v.getUUID());
                if (v.orbital() != null) {
                    p.getPersistentData().putInt(LAST_SHIP + "_cell", v.orbital().cell());
                }
            }
        }
    }

    /** Puts the player back in their ship's pilot seat (or beside it). False (with a message) if it can't be found. */
    public static boolean toShip(ServerPlayer player) {
        var server = player.server;
        String last = player.getPersistentData().getString(LAST_SHIP);
        if (last.startsWith("v:")) {
            for (VesselEntity v : VesselEntity.allServerVessels()) {
                if (!v.isRemoved() && last.equals("v:" + v.getUUID()) && v.stats() != null) {
                    seat(player, v);
                    player.displayClientMessage(Component.translatable("stevespace.command.ship.back", v.getName()), true);
                    return true;
                }
            }
        }
        // Parked since (the flying craft is gone): its cell.
        int cell = player.getPersistentData().contains(LAST_SHIP + "_cell") ? player.getPersistentData().getInt(LAST_SHIP + "_cell") : -1;
        ParkedShips.Parked parked = cell < 0 ? null : ParkedShips.get(server).get(cell);
        ServerLevel space = server.getLevel(SpaceDimension.KEY);
        if (parked != null && space != null) {
            BlockPos min = parked.min();
            BlockPos max = parked.max();
            // Stand inside it, on its floor, in the middle.
            double x = (min.getX() + max.getX() + 1) / 2.0;
            double z = (min.getZ() + max.getZ() + 1) / 2.0;
            player.stopRiding();
            player.teleportTo(space, x, min.getY() + 1, z, java.util.Set.of(), player.getYRot(), player.getXRot());
            player.displayClientMessage(Component.translatable("stevespace.command.ship.back",
                    Component.literal(com.thepearlking.stevespace.radio.SkyShips.parkedName(server, parked))), true);
            return true;
        }
        player.displayClientMessage(Component.translatable("stevespace.command.ship.none"), false);
        return false;
    }

    private static void seat(ServerPlayer player, VesselEntity v) {
        player.stopRiding();
        ServerLevel level = (ServerLevel) v.level();
        if (v.materialized() && !v.stats().seats().isEmpty()) {
            BlockPos seatPos = v.blockOrigin().offset(v.stats().seats().get(0).pos());
            player.teleportTo(level, seatPos.getX() + 0.5, seatPos.getY() + 1, seatPos.getZ() + 0.5, java.util.Set.of(), player.getYRot(), 0);
            SeatEntity seat = SeatEntity.create(level, seatPos);
            level.addFreshEntity(seat);
            player.startRiding(seat, true);
        } else {
            player.teleportTo(level, v.getX(), v.getY() + 1, v.getZ(), java.util.Set.of(), player.getYRot(), 0);
            player.startRiding(v, true);
        }
    }

    /** Every world you can land on, by id, with its surface dimension. */
    private static java.util.Map<String, String> landable() {
        java.util.Map<String, String> worlds = new java.util.TreeMap<>();
        for (var body : com.thepearlking.stevespace.universe.ServerUniverse.system().bodies()) {
            if (body.isLandable() && body.surfaceDimension() != null) {
                worlds.put(body.id(), body.surfaceDimension());
            }
        }
        return worlds;
    }

    /** Straight down onto a world's ground (latitude 0, longitude 0: on Acheron, the face toward Earth). */
    private static boolean goTo(CommandSourceStack source, ServerPlayer player, String id) {
        String dimension = landable().get(id.toLowerCase(java.util.Locale.ROOT));
        ServerLevel level = dimension == null ? null : player.server.getLevel(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, net.minecraft.resources.ResourceLocation.parse(dimension)));
        if (level == null) {
            source.sendFailure(Component.translatable("stevespace.command.goto.unknown", id));
            return false;
        }
        level.getChunk(0, 0);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);
        player.stopRiding();
        player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        player.fallDistance = 0;
        player.teleportTo(level, 0.5, y + 0.1, 0.5, java.util.Set.of(), player.getYRot(), 0);
        var body = com.thepearlking.stevespace.universe.ServerUniverse.system().body(id.toLowerCase(java.util.Locale.ROOT));
        source.sendSuccess(() -> Component.translatable("stevespace.command.goto", body.displayName()), true);
        return true;
    }

    /** Down safely at the world's spawn, standing on the ground. */
    public static void home(ServerPlayer player) {
        ServerLevel overworld = player.server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        overworld.getChunk(spawn);
        int y = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ());
        player.stopRiding();
        player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        player.fallDistance = 0;
        player.teleportTo(overworld, spawn.getX() + 0.5, y + 0.1, spawn.getZ() + 0.5, java.util.Set.of(), player.getYRot(), 0);
        player.displayClientMessage(Component.translatable("stevespace.command.home"), true);
    }
}
