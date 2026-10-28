package com.thepearlking.stevespace.suit;

import com.thepearlking.stevespace.network.OxygenS2C;
import com.thepearlking.stevespace.network.SpaceNetwork;
import com.thepearlking.stevespace.universe.CelestialBody;
import com.thepearlking.stevespace.universe.ServerUniverse;
import com.thepearlking.stevespace.world.ParkedShips;
import com.thepearlking.stevespace.world.SpaceDimension;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Air. Only Earth has air you can breathe; everywhere else (space, the Moon, Mars, Titan...)
 * you need a sealed suit, whose tanks last {@link #CAPACITY} seconds and refill wherever there is
 * air: on Earth, aboard a ship, or in a parked ship's cabin. Without one, your breath runs out
 * like underwater. Creative players never run short.
 */
public final class Oxygen {

    /** Seconds of oxygen in a full suit. */
    public static final int CAPACITY = 600;
    private static final String KEY = "stevespace_oxygen";

    private Oxygen() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent.Post event) {
        if (!(event.player instanceof ServerPlayer player) || player.isSpectator()) {
            return;
        }
        var data = player.getPersistentData();
        double oxygen = data.contains(KEY) ? data.getDouble(KEY) : CAPACITY;
        boolean airless = airless(player);
        boolean sealed = SpaceSuit.sealed(player);
        boolean creative = player.getAbilities().instabuild;
        if (!airless) {
            oxygen = Math.min(CAPACITY, oxygen + 10.0 / 20); // topping up the tanks
        } else if (sealed) {
            if (!creative) {
                oxygen = Math.max(0, oxygen - 1.0 / 20);
            }
            if (oxygen > 0 || creative) {
                player.setAirSupply(player.getMaxAirSupply());
            } else {
                suffocate(player);
            }
        } else if (!creative) {
            suffocate(player);
        }
        data.putDouble(KEY, oxygen);
        Hazards.Hazard hazard = Hazards.tick(player, creative);
        if (player.tickCount % 10 == 0) {
            SpaceNetwork.sendTo(player, new OxygenS2C((float) (oxygen / CAPACITY), airless, sealed, creative,
                    hazard == null ? "" : hazard.key()));
        }
    }

    /** Running out of breath, as underwater: the bubbles go, then it hurts. */
    private static void suffocate(ServerPlayer player) {
        // Vanilla refills 4 a tick out of water; take 5, so a full breath lasts 15 seconds.
        int air = player.getAirSupply() - 5;
        if (air <= -20) {
            air = 0;
            player.hurt(player.damageSources().drown(), 2);
        }
        player.setAirSupply(air);
    }

    /** True where there is nothing to breathe. */
    public static boolean airless(ServerPlayer player) {
        if (Hazards.sheltered(player)) {
            return false; // a cabin or a pressurised room has air
        }
        if (SpaceDimension.isSpace(player.level())) {
            var ship = ParkedShips.get(player.server).at(player.getX());
            return ship == null || !ship.contains(player.getX(), player.getY(), player.getZ(), 0);
        }
        CelestialBody body = ServerUniverse.bodyOf(player.level());
        return body != null && !body.id().equals("earth");
    }
}
