package com.thepearlking.stevespace.vessel;

import com.thepearlking.stevespace.network.LaunchTrackS2C;
import com.thepearlking.stevespace.network.SpaceNetwork;
import com.thepearlking.stevespace.universe.CelestialBody;
import com.thepearlking.stevespace.universe.ServerUniverse;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Tells everyone on a planet where the rockets in its air are while they burn (or streak down
 * fast), a few times a second, so a launch or a fall leaves a trail that can be seen from a long
 * way off, the way a real launch can be watched from a hundred kilometres away.
 */
public final class LaunchBeacon {

    /** Faster than this (blocks per second) through the air, a falling craft leaves a trail of its own. */
    public static final float STREAK_SPEED = 30;
    private static final double RANGE = 12000;

    private LaunchBeacon() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 2 != 0) {
            return;
        }
        for (VesselEntity v : VesselEntity.allServerVessels()) {
            if (v.isRemoved() || v.materialized() || v.orbital() != null) {
                continue;
            }
            CelestialBody body = v.flightBody();
            if (body == null || !body.hasAtmosphere()) {
                continue;
            }
            float speed = v.velocity().length();
            float throttle = v.throttle();
            if (throttle < 0.01f && speed < STREAK_SPEED) {
                continue;
            }
            double y = v.mapY();
            if (y - 63 > body.atmosphere().height() * 1.05) {
                continue; // above the air: nothing to leave a trail in
            }
            LaunchTrackS2C msg = new LaunchTrackS2C(v.getId(), v.mapX(), y, v.mapZ(), throttle, speed);
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (ServerUniverse.bodyOf(p.serverLevel()) == body
                        && Math.hypot(p.getX() - v.mapX(), p.getZ() - v.mapZ()) < RANGE) {
                    SpaceNetwork.sendTo(p, msg);
                }
            }
        }
    }
}
