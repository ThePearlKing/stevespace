package com.thepearlking.stevespace.vessel;

import com.thepearlking.stevespace.block.DockingPortBlock;
import com.thepearlking.stevespace.block.PilotSeatBlock;
import com.thepearlking.stevespace.block.SeatEntity;
import com.thepearlking.stevespace.network.DockListS2C;
import com.thepearlking.stevespace.registry.ModBlocks;
import com.thepearlking.stevespace.universe.ServerUniverse;
import com.thepearlking.stevespace.world.ParkedShips;
import com.thepearlking.stevespace.world.SpaceDimension;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Which ships a pilot can dock with: their own that have a free docking port, and anyone
 * else's that have one and a radio rack to talk them in. Ships parked in orbit count too.
 */
public final class DockList {

    private DockList() {
    }

    public static DockListS2C build(ServerPlayer player) {
        // The pilot's own ship: flying, blocks in space, or still standing on its pad.
        VesselEntity own = VesselEntity.shipOf(player, 2);
        VesselStructure mine = own != null ? own.structure() : null;
        if (mine == null && player.getVehicle() instanceof SeatEntity seat
                && player.serverLevel().getBlockState(seat.blockPosition()).getBlock() instanceof PilotSeatBlock) {
            var scan = VesselAssembler.scan(player.serverLevel(), seat.blockPosition(),
                    com.thepearlking.stevespace.config.SpaceConfig.MAX_VESSEL_BLOCKS.get());
            mine = scan.ok() ? scan.structure() : null;
        }
        if (mine == null) {
            return new DockListS2C(false, List.of(), List.of(), DockListS2C.Shape.EMPTY);
        }
        VesselStats myStats = new VesselStats(mine);
        boolean rcs = !myStats.rcs().isEmpty();
        List<DockListS2C.Entry> ships = new ArrayList<>();
        double t = ServerUniverse.time(player.server);
        for (VesselEntity v : VesselEntity.serverVessels()) {
            if (v == own || v.isRemoved() || v.orbital() == null || v.stats() == null) {
                continue;
            }
            boolean owned = v.ownedBy(player);
            if (!owned && !hasRadio(v.structure())) {
                continue;
            }
            List<DockListS2C.Port> ports = freePorts(v.structure());
            if (ports.isEmpty()) {
                continue;
            }
            double distance = -1;
            if (own != null && own.orbital() != null && own.orbital().body().id().equals(v.orbital().body().id())) {
                distance = v.orbital().state().position().sub(own.orbital().state().position()).length();
            }
            ships.add(new DockListS2C.Entry(v.getUUID(), v.orbital().cell(), v.getName().getString(), v.orbital().body().id(),
                    distance, owned, ports, DockListS2C.Shape.of(v.structure().blocks())));
        }
        ServerLevel space = player.server.getLevel(SpaceDimension.KEY);
        if (space != null) {
            for (ParkedShips.Parked p : List.copyOf(ParkedShips.get(player.server).all())) {
                boolean owned = p.owner() == null || p.owner().equals(player.getUUID());
                VesselStructure blocks = parkedBlocks(space, p);
                if (blocks == null || !owned && !hasRadio(blocks)) {
                    continue;
                }
                List<DockListS2C.Port> ports = freePorts(blocks);
                if (ports.isEmpty()) {
                    continue;
                }
                double distance = -1;
                if (own != null && own.orbital() != null && own.orbital().body().id().equals(p.body())) {
                    distance = Rendezvous.parkedPosition(p, own.orbital().time()).sub(own.orbital().state().position()).length();
                }
                ships.add(new DockListS2C.Entry(p.id(), p.cell(), nameOf(p.name(), space), p.body(), distance, owned, ports,
                        DockListS2C.Shape.of(blocks.blocks())));
            }
        }
        // Nearest first; those round other worlds after.
        ships.sort((a, b) -> Double.compare(a.distance() < 0 ? Double.MAX_VALUE : a.distance(), b.distance() < 0 ? Double.MAX_VALUE : b.distance()));
        return new DockListS2C(rcs, freePorts(mine), ships, DockListS2C.Shape.of(mine.blocks()));
    }

    /**
     * The other ships in orbit a player sees on the map: their own (or anyone's), and other
     * people's with a radio rack. Ships parked in orbit too.
     */
    public static com.thepearlking.stevespace.network.FleetS2C fleet(ServerPlayer player) {
        double t = ServerUniverse.time(player.server);
        VesselEntity own = VesselEntity.shipOf(player, 2);
        List<com.thepearlking.stevespace.network.FleetS2C.Ship> ships = new ArrayList<>();
        for (VesselEntity v : VesselEntity.serverVessels()) {
            if (v == own || v.isRemoved() || v.orbital() == null || v.stats() == null) {
                continue;
            }
            boolean owned = v.ownedBy(player);
            if (!owned && !hasRadio(v.structure())) {
                continue;
            }
            var s = v.orbital().state();
            var at = s.position().add(s.velocity().scale(t - v.orbital().time())); // a tick or two on, to now
            ships.add(new com.thepearlking.stevespace.network.FleetS2C.Ship(v.getUUID(), v.getName().getString(), v.orbital().body().id(),
                    owned, new double[]{at.x(), at.y(), at.z(), s.velocity().x(), s.velocity().y(), s.velocity().z()}));
        }
        ServerLevel space = player.server.getLevel(SpaceDimension.KEY);
        for (ParkedShips.Parked p : List.copyOf(ParkedShips.get(player.server).all())) {
            boolean owned = p.owner() == null || p.owner().equals(player.getUUID());
            if (!owned && (space == null || !parkedHasRadio(space, p))) {
                continue;
            }
            var body = ServerUniverse.system().body(p.body());
            if (body == null) {
                continue;
            }
            double mu = body.mu() * com.thepearlking.stevespace.config.SpaceConfig.GRAVITY_SCALE.get();
            var s = com.thepearlking.stevespace.universe.Kepler.elementsFromState(p.state(), mu, p.time()).stateAt(t, mu);
            ships.add(new com.thepearlking.stevespace.network.FleetS2C.Ship(p.id() != null ? p.id() : new java.util.UUID(0, p.cell()),
                    space == null ? p.name() : nameOf(p.name(), space), p.body(), owned,
                    new double[]{s.position().x(), s.position().y(), s.position().z(), s.velocity().x(), s.velocity().y(), s.velocity().z()}));
        }
        return new com.thepearlking.stevespace.network.FleetS2C(t, ships);
    }

    /** Whether a parked ship has a radio rack (only looked for if its blocks are loaded). */
    private static boolean parkedHasRadio(ServerLevel space, ParkedShips.Parked p) {
        for (BlockPos pos : BlockPos.betweenClosed(p.min(), p.max())) {
            if (!space.isLoaded(pos)) {
                return false;
            }
            if (space.getBlockState(pos).is(ModBlocks.RADIO_RACK.get())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasRadio(VesselStructure structure) {
        return structure.blocks().values().stream().anyMatch(b -> b.is(ModBlocks.RADIO_RACK.get()));
    }

    /**
     * A parked ship's blocks, counted from the same block it would fly from (so its ports are
     * named the same once it wakes). Loads its chunks if they aren't.
     */
    private static VesselStructure parkedBlocks(ServerLevel space, ParkedShips.Parked p) {
        for (int cx = p.min().getX() >> 4; cx <= p.max().getX() >> 4; cx++) {
            for (int cz = p.min().getZ() >> 4; cz <= p.max().getZ() >> 4; cz++) {
                space.getChunk(cx, cz);
            }
        }
        BlockPos origin = Rendezvous.parkedOrigin(space, p);
        if (origin == null) {
            return null;
        }
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        for (BlockPos pos : BlockPos.betweenClosed(p.min(), p.max())) {
            BlockState state = space.getBlockState(pos);
            if (!state.isAir()) {
                blocks.put(pos.subtract(origin), state);
            }
        }
        return new VesselStructure(blocks, new HashMap<>());
    }

    /** Ports with nothing in front of them, each named by the side of the ship it's on (from the pilot's seat). */
    static List<DockListS2C.Port> freePorts(VesselStructure structure) {
        Direction front = Direction.NORTH;
        for (var e : structure.blocks().entrySet()) {
            if (e.getValue().getBlock() instanceof PilotSeatBlock) {
                front = e.getValue().getValue(PilotSeatBlock.FACING);
                break;
            }
        }
        List<DockListS2C.Port> ports = new ArrayList<>();
        Map<String, Integer> count = new HashMap<>();
        List<Map.Entry<BlockPos, BlockState>> sorted = new ArrayList<>(structure.blocks().entrySet());
        sorted.sort((a, b) -> Long.compare(a.getKey().asLong(), b.getKey().asLong()));
        for (var e : sorted) {
            if (!(e.getValue().getBlock() instanceof DockingPortBlock)) {
                continue;
            }
            Direction facing = e.getValue().getValue(DockingPortBlock.FACING);
            if (structure.get(e.getKey().relative(facing)) != null) {
                continue; // something's in front of it (another port, docked)
            }
            String side = facing == Direction.UP ? "top" : facing == Direction.DOWN ? "bottom" : facing == front ? "front"
                    : facing == front.getOpposite() ? "back" : facing == front.getClockWise() ? "right" : "left";
            int n = count.merge(side, 1, Integer::sum);
            ports.add(new DockListS2C.Port(e.getKey(), side, n));
        }
        return ports;
    }

    private static String nameOf(String saved, ServerLevel level) {
        if (saved.startsWith("{") || saved.startsWith("\"")) {
            try {
                var name = net.minecraft.network.chat.Component.Serializer.fromJson(saved, level.registryAccess());
                if (name != null) {
                    return name.getString();
                }
            } catch (RuntimeException ignored) {
                // plain text
            }
        }
        return saved;
    }
}
