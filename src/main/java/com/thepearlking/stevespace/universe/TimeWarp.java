package com.thepearlking.stevespace.universe;

import com.thepearlking.stevespace.config.SpaceConfig;
import com.thepearlking.stevespace.vessel.VesselEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Time warp: speeds up the whole solar system. Allowed only while nothing is flying through an
 * atmosphere and no engine is burning, and only for players the config permits.
 */
public final class TimeWarp {

    /** The warp rates, slowest to fastest. */
    public static final int[] LEVELS = {1, 5, 10, 50, 100, 1_000, 10_000, 100_000, 1_000_000, 10_000_000};
    /** Fastest warp near the planets; the two above it are for cruising between them. */
    public static final int NEAR_PLANET_LIMIT = 100_000;

    private TimeWarp() {
    }

    public static void request(ServerPlayer player, int step) {
        MinecraftServer server = player.server;
        if (!permitted(player)) {
            player.displayClientMessage(Component.translatable("stevespace.warp.not_permitted"), true);
            return;
        }
        double current = UniverseClock.get(server).warp();
        int index = 0;
        for (int i = 0; i < LEVELS.length; i++) {
            if (LEVELS[i] <= current + 1e-6) {
                index = i;
            }
        }
        int next = Math.max(0, Math.min(LEVELS.length - 1, index + step));
        if (next > index && LEVELS[next] > NEAR_PLANET_LIMIT && !cruising(server)) {
            player.displayClientMessage(Component.translatable("stevespace.warp.near_planet"), true);
            return;
        }
        if (next > index) {
            VesselEntity holding = holdingBack(server);
            if (holding != null) {
                player.displayClientMessage(Component.translatable(reason(holding), holding.getName()), true);
                return;
            }
        }
        ServerUniverse.setWarp(server, LEVELS[next]);
    }

    private static boolean permitted(ServerPlayer player) {
        return switch (SpaceConfig.WARP_PERMISSION.get()) {
            case EVERYONE -> true;
            case OPERATORS -> player.hasPermissions(2);
            case SINGLEPLAYER_ONLY -> player.server.isSingleplayer() && player.server.getPlayerCount() <= 1;
        };
    }

    /** Why warp can't speed up right now (a translation key), or null if it can. */
    public static String blocker(MinecraftServer server) {
        VesselEntity v = holdingBack(server);
        return v == null ? null : reason(v);
    }

    /**
     * The ship holding warp back, or null: one with someone aboard (or flying it by remote) that
     * is flying through air, firing its engines, or under autopilot. An empty ship drifting along
     * somewhere doesn't (warp doesn't change how it flies).
     */
    public static VesselEntity holdingBack(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof VesselEntity vessel && !vessel.isRemoved() && crewed(vessel) && reason(vessel) != null) {
                    return vessel;
                }
            }
        }
        return null;
    }

    private static boolean crewed(VesselEntity vessel) {
        return vessel.pilot() != null || vessel.getPassengers().stream().anyMatch(e -> e instanceof net.minecraft.world.entity.player.Player);
    }

    private static String reason(VesselEntity vessel) {
        if (vessel.orbital() == null && !vessel.isGrounded()) {
            return "stevespace.warp.atmosphere";
        }
        if (vessel.commandedThrottle() > 0) {
            return "stevespace.warp.burning";
        }
        if (vessel.autopilotEngaged()) {
            return "stevespace.warp.autopilot";
        }
        return null;
    }

    /**
     * True when every ship in flight is out between the planets (orbiting the Sun itself) and at
     * least one is: the only time the two fastest warps make sense. A trip to Pluto takes years.
     */
    public static boolean cruising(MinecraftServer server) {
        boolean any = false;
        for (VesselEntity v : VesselEntity.allServerVessels()) {
            if (v.isRemoved()) {
                continue;
            }
            if (v.orbital() == null || v.orbital().body().parent() != null) {
                if (!v.isGrounded() || v.orbital() != null) {
                    return false;
                }
                continue;
            }
            any = true;
        }
        return any;
    }

    /** Drops back to normal speed (an engine fired, or something entered an atmosphere). */
    public static void stop(MinecraftServer server) {
        if (UniverseClock.get(server).warp() > 1) {
            ServerUniverse.setWarp(server, 1);
        }
    }
}
