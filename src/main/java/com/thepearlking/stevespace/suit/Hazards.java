package com.thepearlking.stevespace.suit;

import com.thepearlking.stevespace.block.SeatEntity;
import com.thepearlking.stevespace.universe.CelestialBody;
import com.thepearlking.stevespace.universe.ServerUniverse;
import com.thepearlking.stevespace.vessel.VesselEntity;
import net.minecraft.server.level.ServerPlayer;

/**
 * What a world does to you when you step outside, beyond having no air: the heat of Venus, the
 * cold of the outer moons, Jupiter's radiation. Sitting in a ship (flying or landed) shelters
 * you; otherwise the right suit does.
 */
public final class Hazards {

    public enum Hazard {
        HEAT, COLD, RADIATION;

        public String key() {
            return "hud.stevespace.hazard." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Dose rate (µSv/h) from nuclear bursts that starts doing harm without a radiation suit: a tenth of a sievert an hour. */
    private static final double NUCLEAR_HARM = 1e5;

    private Hazards() {
    }

    /** The hazard of where the player stands, or null. */
    public static Hazard at(ServerPlayer player) {
        // Near a nuclear burst or in its fallout (only when the config lets nukes do real harm).
        if (com.thepearlking.stevespace.config.SpaceConfig.NUKE_DESTRUCTION.get() && !underCover(player)) {
            var events = com.thepearlking.stevespace.nuke.NuclearAftermath.get(player.server).events();
            if (!events.isEmpty() && com.thepearlking.stevespace.nuke.NuclearAftermath.extra(events, player.level().dimension().location().toString(),
                    player.getX(), player.getY(), player.getZ(), null, null, ServerUniverse.time(player.server)) > NUCLEAR_HARM) {
                return Hazard.RADIATION;
            }
        }
        CelestialBody body = ServerUniverse.bodyOf(player.level());
        if (body == null) {
            // Out in space close to the Sun, outside the ship: its heat.
            return com.thepearlking.stevespace.world.SpaceDimension.isSpace(player.level()) && nearSun(player) ? Hazard.HEAT : null;
        }
        return switch (body.id()) {
            case "venus" -> Hazard.HEAT;
            // Mercury: scorching by day, freezing by night; bearable only near the terminator.
            case "mercury" -> {
                double sun = Math.toDegrees(ServerUniverse.sunElevation(player, body));
                yield sun > 4 ? Hazard.HEAT : sun < -4 ? Hazard.COLD : null;
            }
            // Inside Jupiter's radiation belts. Ganymede's own magnetic field shields it.
            case "io", "europa" -> underCover(player) ? null : Hazard.RADIATION;
            case "titan", "triton", "pluto", "charon", "makemake", "eris", "haumea" -> Hazard.COLD;
            default -> null;
        };
    }

    /** Within a few solar radii of the Sun (by the orbit of the ship or parked ship the player is beside). */
    private static boolean nearSun(ServerPlayer player) {
        double limit = 6;
        VesselEntity ship = VesselEntity.shipOf(player, 64);
        if (ship != null && ship.orbital() != null) {
            CelestialBody b = ship.orbital().body();
            return b.parent() == null && ship.orbital().state().position().length() < b.radius() * limit;
        }
        var parked = com.thepearlking.stevespace.world.ParkedShips.get(player.server).at(player.getX());
        if (parked != null) {
            CelestialBody b = ServerUniverse.system().body(parked.body());
            return b != null && b.parent() == null && parked.state().position().length() < b.radius() * limit;
        }
        return false;
    }

    /** Solid ground or a roof overhead stops the radiation. */
    private static boolean underCover(ServerPlayer player) {
        return !player.level().canSeeSky(player.blockPosition().above());
    }

    /** In a ship's seat or cabin, or a pressurised room: out of the weather. */
    public static boolean sheltered(ServerPlayer player) {
        return player.getVehicle() instanceof VesselEntity || player.getVehicle() instanceof SeatEntity
                || VesselEntity.shipOf(player, 0) != null
                || Habitats.pressurised(player.level(), net.minecraft.core.BlockPos.containing(player.getEyePosition()));
    }

    public static boolean protectedFrom(ServerPlayer player, Hazard hazard) {
        return switch (hazard) {
            case HEAT -> SpaceSuit.wearing(player, SpaceSuit.HEAT_SUIT);
            case COLD -> SpaceSuit.wearing(player, SpaceSuit.INSULATED_SUIT);
            case RADIATION -> SpaceSuit.wearing(player, SpaceSuit.RADIATION_SUIT);
        };
    }

    /** Called every tick; hurts once a second. Returns the hazard the player is exposed to, or null. */
    public static Hazard tick(ServerPlayer player, boolean creative) {
        Hazard hazard = at(player);
        if (hazard == null || sheltered(player) || protectedFrom(player, hazard)) {
            return null;
        }
        if (!creative && player.tickCount % 20 == 0) {
            switch (hazard) {
                case HEAT -> {
                    player.setRemainingFireTicks(Math.max(player.getRemainingFireTicks(), 30));
                    player.hurt(player.damageSources().onFire(), 3);
                }
                case COLD -> {
                    player.setTicksFrozen(Math.min(player.getTicksRequiredToFreeze() + 40, player.getTicksFrozen() + 40));
                    player.hurt(player.damageSources().freeze(), 2);
                }
                case RADIATION -> player.hurt(player.damageSources().magic(), 1);
            }
        }
        return hazard;
    }
}
