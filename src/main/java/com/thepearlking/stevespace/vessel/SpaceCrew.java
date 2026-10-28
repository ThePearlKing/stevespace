package com.thepearlking.stevespace.vessel;

import com.thepearlking.stevespace.network.CellOrbitS2C;
import com.thepearlking.stevespace.network.SpaceNetwork;
import com.thepearlking.stevespace.registry.ModBlocks;
import com.thepearlking.stevespace.world.BodyGravity;
import com.thepearlking.stevespace.world.ParkedShips;
import com.thepearlking.stevespace.world.SpaceDimension;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;

/**
 * People in space who aren't sitting in a flying ship: walking around a parked ship or floating
 * outside it. Space is weightless, except aboard a ship with a gravity generator, where you fall
 * toward the ship's floor. Each of them is told which orbit they are riding, for their sky.
 */
public final class SpaceCrew {

    /** Per parked ship: where its gravity generator is (or null), and when that was last checked. */
    private record Generator(BlockPos at, int checkedAt) {
    }

    /** Per parked ship: the last tick the generator was paid for (it costs once per ship, not per person). */
    private static final Map<Integer, Integer> PAID = new HashMap<>();
    /** Power the gravity generator draws per tick while it holds people down. */
    public static final int GRAVITY_COST = 30;

    private static final Map<Integer, Generator> GENERATORS = new HashMap<>();

    private SpaceCrew() {
    }

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 10 != 0) {
            return;
        }
        ServerLevel space = server.getLevel(SpaceDimension.KEY);
        if (space == null) {
            return;
        }
        ParkedShips parked = ParkedShips.get(server);
        for (ServerPlayer player : java.util.List.copyOf(space.players())) {
            if (player.getVehicle() instanceof VesselEntity) {
                continue; // aboard a flying ship: it looks after them
            }
            VesselEntity flying = VesselEntity.shipOf(player, 0);
            if (flying != null) {
                // Inside a ship that is blocks: what you feel is its thrust (and its gravity
                // generator) pressing you toward the floor; out on its hull you float.
                ParkedShips.Parked bounds = new ParkedShips.Parked(-1, "", null, 0, "",
                        flying.blockOrigin().offset(flying.boundsMin()), flying.blockOrigin().offset(flying.boundsMax()), null);
                double down = -flying.cabinPull().y;
                BodyGravity.set(player, enclosed(space, player.blockPosition(), bounds) ? Math.max(0, Math.min(3, down)) : 0);
                continue;
            }
            ParkedShips.Parked ship = parked.at(player.getX());
            // Gravity only inside the ship: step out of the hatch and you float.
            boolean inside = ship != null && ship.contains(player.getX(), player.getY(), player.getZ(), 0)
                    && enclosed(space, player.blockPosition(), ship);
            BodyGravity.set(player, inside && hasGenerator(space, ship, server.getTickCount()) ? 1 : 0);
            if (ship != null) {
                var s = ship.state();
                SpaceNetwork.sendTo(player, new CellOrbitS2C(ship.body(), ship.time(),
                        new double[]{s.position().x(), s.position().y(), s.position().z(), s.velocity().x(), s.velocity().y(), s.velocity().z()},
                        ship.anchor().x(), ship.anchor().y(), ship.anchor().z()));
                tether(player, new net.minecraft.world.phys.Vec3(ship.anchor().x(), ship.anchor().y(), ship.anchor().z()));
                continue;
            }
            // Out on a spacewalk beside a flying ship: its orbit.
            int cell = ParkedShips.cellAt(player.getX());
            boolean found = false;
            for (VesselEntity v : VesselEntity.serverVessels()) {
                if (v.isRemoved() || v.level() != space || ParkedShips.cellAt(v.getX()) != cell) {
                    continue;
                }
                if (v.orbital() != null) {
                    var s = v.orbital().state();
                    SpaceNetwork.sendTo(player, new CellOrbitS2C(v.orbital().body().id(), v.orbital().time(),
                            new double[]{s.position().x(), s.position().y(), s.position().z(), s.velocity().x(), s.velocity().y(), s.velocity().z()},
                            v.getX(), v.getY(), v.getZ()));
                }
                tether(player, v.position()); // (high over a world, in map mode, it's still right there)
                found = true;
                break;
            }
            if (!found && !player.isSpectator()) {
                // Out in space with no ship anywhere near (drifted off, or the ship left): never
                // left there. After a few seconds, back aboard their own ship, or home.
                int lost = player.getPersistentData().getInt(LOST) + 10;
                player.getPersistentData().putInt(LOST, lost);
                if (lost >= 100) {
                    player.getPersistentData().remove(LOST);
                    com.thepearlking.stevespace.StevesSpace.LOGGER.info("{} adrift in space at {} (cell {}), nothing near: bringing them back",
                            player.getName().getString(), player.blockPosition(), cell);
                    if (!com.thepearlking.stevespace.command.SpaceCommands.toShip(player)) {
                        com.thepearlking.stevespace.command.SpaceCommands.home(player);
                    }
                }
            } else {
                player.getPersistentData().remove(LOST);
            }
        }
    }

    private static final String LOST = "stevespace_lost_in_space";

    /** Floating too far from the ship: a safety line reels you back in (nobody drifts off for good). */
    private static void tether(ServerPlayer player, net.minecraft.world.phys.Vec3 anchor) {
        player.getPersistentData().remove(LOST);
        net.minecraft.world.phys.Vec3 away = player.position().subtract(anchor);
        double distance = away.length();
        if (distance < TETHER || player.isSpectator()) {
            return;
        }
        net.minecraft.world.phys.Vec3 pull = away.scale(-1 / distance);
        double speed = Math.min(1.2, 0.3 + (distance - TETHER) * 0.01);
        player.setDeltaMovement(pull.scale(speed));
        player.hurtMarked = true; // the client takes the new motion
        player.fallDistance = 0;
        if (distance > TETHER + 4 && player.tickCount % 100 < 10) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("stevespace.tether"), true);
        }
    }

    /** How far (blocks) you can float from your ship before the line pulls you back: past where you can still see it. */
    private static final double TETHER = 180;

    /** Something built onto a parked ship (touching it, or close): the ship now includes it. */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onPlace(net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !SpaceDimension.isSpace(level)) {
            return;
        }
        BlockPos pos = event.getPos();
        ParkedShips parked = ParkedShips.get(level.getServer());
        ParkedShips.Parked ship = parked.at(pos.getX() + 0.5);
        if (ship != null && ship.contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6)) {
            parked.grow(ship.cell(), pos);
            GENERATORS.remove(ship.cell());
        }
    }

    /** True when this player is aboard a parked ship that has a gravity generator. */
    public static boolean gravityOn(ServerPlayer player) {
        ServerLevel space = player.server.getLevel(SpaceDimension.KEY);
        if (space == null || player.level() != space) {
            return false;
        }
        ParkedShips.Parked ship = ParkedShips.get(player.server).at(player.getX());
        return ship != null && ship.contains(player.getX(), player.getY(), player.getZ(), 0)
                && enclosed(space, player.blockPosition(), ship) && hasGenerator(space, ship, player.server.getTickCount());
    }

    /**
     * Inside the hull rather than standing on its outside: there is ship above you (a ceiling)
     * and below you (a floor) within the ship's bounds.
     */
    private static boolean enclosed(ServerLevel space, BlockPos at, ParkedShips.Parked ship) {
        boolean floor = false;
        boolean ceiling = false;
        for (int y = at.getY() - 1; y >= ship.min().getY(); y--) {
            if (!space.getBlockState(new BlockPos(at.getX(), y, at.getZ())).isAir()) {
                floor = true;
                break;
            }
        }
        for (int y = at.getY() + 2; y <= ship.max().getY(); y++) {
            if (!space.getBlockState(new BlockPos(at.getX(), y, at.getZ())).isAir()) {
                ceiling = true;
                break;
            }
        }
        return floor && ceiling;
    }

    private static boolean hasGenerator(ServerLevel space, ParkedShips.Parked ship, int now) {
        Generator known = GENERATORS.get(ship.cell());
        if (known == null || now - known.checkedAt() >= 100) {
            BlockPos found = null;
            BlockPos min = ship.min().offset(-2, -2, -2);
            BlockPos max = ship.max().offset(2, 2, 2);
            for (BlockPos p : BlockPos.betweenClosed(min, max)) {
                if (space.isLoaded(p) && space.getBlockState(p).is(ModBlocks.GRAVITY_GENERATOR.get())) {
                    found = p.immutable();
                    break;
                }
            }
            known = new Generator(found, now);
            GENERATORS.put(ship.cell(), known);
        }
        if (known.at() == null || !(space.getBlockEntity(known.at()) instanceof com.thepearlking.stevespace.block.GravityGeneratorBlock.Entity generator)) {
            return false;
        }
        // Paid once per ten ticks for the whole ship.
        Integer paid = PAID.get(ship.cell());
        if (paid != null && now - paid < 10) {
            return true;
        }
        if (generator.buffer().use(GRAVITY_COST * 10)) {
            PAID.put(ship.cell(), now);
            generator.setChanged();
            return true;
        }
        return false;
    }
}
