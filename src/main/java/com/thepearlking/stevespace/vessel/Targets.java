package com.thepearlking.stevespace.vessel;

import com.thepearlking.stevespace.network.MissionControlS2C;
import com.thepearlking.stevespace.network.SpaceNetwork;
import com.thepearlking.stevespace.network.TargetS2C;
import com.thepearlking.stevespace.universe.ServerUniverse;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Each player's chosen target vessel, kept up to date on their screen for meeting it in orbit. */
public final class Targets {

    private static final Map<UUID, UUID> TARGETS = new HashMap<>();

    private Targets() {
    }

    public static void set(ServerPlayer player, UUID target) {
        if (target == null) {
            TARGETS.remove(player.getUUID());
            SpaceNetwork.sendTo(player, new TargetS2C(false, "", "", 0, new double[6]));
        } else {
            TARGETS.put(player.getUUID(), target);
        }
    }

    /** The vessel a player has chosen as their target, or null. */
    public static VesselEntity targetVessel(net.minecraft.world.entity.player.Player player) {
        UUID target = player == null ? null : TARGETS.get(player.getUUID());
        if (target == null) {
            return null;
        }
        for (VesselEntity v : VesselEntity.serverVessels()) {
            if (v.getUUID().equals(target) && !v.isRemoved()) {
                return v;
            }
        }
        return null;
    }

    /** Twice a second: send every player with a target where it is. */
    public static void tick(MinecraftServer server) {
        if (TARGETS.isEmpty() || server.getTickCount() % 10 != 0) {
            return;
        }
        var vessels = VesselEntity.trackingList(server);
        double time = ServerUniverse.time(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID target = TARGETS.get(player.getUUID());
            if (target == null) {
                continue;
            }
            MissionControlS2C.Entry found = null;
            for (MissionControlS2C.Entry e : vessels) {
                if (e.id().equals(target)) {
                    found = e;
                }
            }
            if (found == null) {
                TARGETS.remove(player.getUUID());
                SpaceNetwork.sendTo(player, new TargetS2C(false, "", "", 0, new double[6]));
            } else {
                SpaceNetwork.sendTo(player, new TargetS2C(true, found.name(), found.body(), time, found.state()));
            }
        }
    }
}
