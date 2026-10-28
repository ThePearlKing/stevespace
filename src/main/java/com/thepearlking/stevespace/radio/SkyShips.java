package com.thepearlking.stevespace.radio;

import com.thepearlking.stevespace.config.SpaceConfig;
import com.thepearlking.stevespace.universe.CelestialBody;
import com.thepearlking.stevespace.universe.DVec3;
import com.thepearlking.stevespace.universe.Kepler;
import com.thepearlking.stevespace.universe.ServerUniverse;
import com.thepearlking.stevespace.universe.StateVector;
import com.thepearlking.stevespace.world.ParkedShips;
import com.thepearlking.stevespace.world.SpaceDimension;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * What a telescope can pick out among the stars: ships and stations in space that carry a radio
 * (anything without one stays undetected). Each comes with a rough silhouette, its side view in
 * block colours, for the telescope to show blurred at its real size.
 */
public final class SkyShips {

    /** Side-view silhouette size in pixels. */
    public static final int SIZE = 24;

    /**
     * One ship: its orbit around {@code body} (state at {@code epoch}, with that body's scaled mu),
     * how big it is, and its silhouette (ARGB, row-major). Its blocks (fetched separately, by
     * {@code version}) sit around {@code centre}, turned by {@code turn} (x, y, z, w) from the
     * axes of its orbit cell (east, up and south under it).
     */
    public record Ship(String id, String name, boolean radio, String body, StateVector state, double epoch, double mu, double radius, int[] pixels,
                       long version, float[] turn, double[] centre) {

        /** Where it is at time t, relative to its body. */
        public DVec3 relative(double t) {
            return Kepler.elementsFromState(state, mu, epoch).stateAt(t, mu).position();
        }
    }

    private SkyShips() {
    }

    /**
     * Every ship out in space. The ones with a radio can be found from the target list; the rest
     * only show up if someone points a telescope at them by hand.
     */
    public static List<Ship> list(MinecraftServer server) {
        List<Ship> ships = new ArrayList<>();
        double t = ServerUniverse.time(server);
        double scale = SpaceConfig.GRAVITY_SCALE.get();
        for (com.thepearlking.stevespace.vessel.VesselEntity v : com.thepearlking.stevespace.vessel.VesselEntity.allServerVessels()) {
            if (v.isRemoved() || v.orbital() == null || v.structure() == null) {
                continue;
            }
            CelestialBody body = v.flightBody();
            if (body == null) {
                continue;
            }
            Map<BlockPos, BlockState> blocks = v.structure().blocks();
            boolean radio = blocks.values().stream().anyMatch(Radio::isRadio);
            org.joml.Quaternionf q = v.syncedOrientation();
            org.joml.Vector3d com = v.stats().centerOfMass();
            ships.add(new Ship("v:" + v.getUUID(), v.getName().getString(), radio, body.id(), v.orbital().state(), t,
                    body.mu() * scale, v.stats().radius(), silhouette(blocks, null), blocks.hashCode(),
                    new float[]{q.x, q.y, q.z, q.w}, new double[]{com.x, com.y, com.z}));
        }
        ServerLevel space = server.getLevel(SpaceDimension.KEY);
        if (space != null) {
            for (ParkedShips.Parked parked : ParkedShips.get(server).all()) {
                CelestialBody body = ServerUniverse.system().body(parked.body());
                if (body == null) {
                    continue;
                }
                Cached c = parkedLook(space, parked, server.getTickCount());
                ships.add(new Ship("p:" + parked.cell(), Radio.shipName(server, parked.name(), "Station"), c.radio, body.id(),
                        parked.state(), parked.time(), body.mu() * scale, c.radius, c.pixels, c.blocks.hashCode(),
                        new float[]{0, 0, 0, 1}, new double[]{parked.anchor().x(), parked.anchor().y(), parked.anchor().z()}));
            }
        }
        return ships;
    }

    private record Cached(int tick, boolean radio, double radius, int[] pixels, Map<BlockPos, BlockState> blocks) {
    }

    private static final Map<Integer, Cached> PARKED = new java.util.HashMap<>();

    /** A parked ship's look, rescanned at most every 10 seconds while its chunks are loaded. */
    private static Cached parkedLook(ServerLevel space, ParkedShips.Parked parked, int tick) {
        Cached c = PARKED.get(parked.cell());
        if (c != null && tick - c.tick() < 200) {
            return c;
        }
        // The saved bounds can lag behind what's really there: look a little round them (the cell
        // holds nothing else).
        BlockPos min = parked.min().offset(-12, -12, -12);
        BlockPos max = parked.max().offset(12, 12, 12);
        if (c != null && (!space.isLoaded(parked.min()) || !space.isLoaded(parked.max()))) {
            return c; // out of everyone's reach: it can't have changed
        }
        Map<BlockPos, BlockState> blocks = new java.util.HashMap<>();
        boolean radio = false;
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE;
        int x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(min, max)) {
            BlockState state = space.getBlockState(p);
            if (!state.isAir()) {
                blocks.put(p.immutable(), state);
                radio |= Radio.isRadio(state);
                x0 = Math.min(x0, p.getX());
                y0 = Math.min(y0, p.getY());
                z0 = Math.min(z0, p.getZ());
                x1 = Math.max(x1, p.getX());
                y1 = Math.max(y1, p.getY());
                z1 = Math.max(z1, p.getZ());
            }
        }
        double radius = blocks.isEmpty() ? 1 : Math.max(1, Math.max(x1 - x0 + 1, Math.max(y1 - y0 + 1, z1 - z0 + 1)) / 2.0);
        c = new Cached(tick, radio, radius, silhouette(blocks, space), blocks);
        PARKED.put(parked.cell(), c);
        return c;
    }

    /** A parked ship's name as shown to players. */
    public static String parkedName(MinecraftServer server, ParkedShips.Parked parked) {
        return Radio.shipName(server, parked.name(), "Station");
    }

    /** A ship's blocks by its id (as listed), for drawing it in the sky; null if it's gone. */
    public static Map<BlockPos, BlockState> blocks(MinecraftServer server, String id) {
        if (id.startsWith("v:")) {
            for (com.thepearlking.stevespace.vessel.VesselEntity v : com.thepearlking.stevespace.vessel.VesselEntity.allServerVessels()) {
                if (!v.isRemoved() && v.structure() != null && id.equals("v:" + v.getUUID())) {
                    return v.structure().blocks();
                }
            }
            return null;
        }
        if (id.startsWith("p:")) {
            try {
                Cached c = PARKED.get(Integer.parseInt(id.substring(2)));
                return c == null ? null : c.blocks;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /** The ship seen from the side (looking along z), scaled into SIZE×SIZE, in map colours. */
    static int[] silhouette(Map<BlockPos, BlockState> blocks, ServerLevel level) {
        int[] out = new int[SIZE * SIZE];
        if (blocks.isEmpty()) {
            return out;
        }
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (BlockPos p : blocks.keySet()) {
            minX = Math.min(minX, p.getX());
            maxX = Math.max(maxX, p.getX());
            minY = Math.min(minY, p.getY());
            maxY = Math.max(maxY, p.getY());
        }
        int span = Math.max(maxX - minX, maxY - minY) + 1;
        double scale = (double) SIZE / span;
        int offX = (int) ((SIZE - (maxX - minX + 1) * scale) / 2);
        int offY = (int) ((SIZE - (maxY - minY + 1) * scale) / 2);
        // Nearest block along z wins (the one facing the viewer).
        int[] depth = new int[SIZE * SIZE];
        java.util.Arrays.fill(depth, Integer.MAX_VALUE);
        for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet()) {
            BlockPos p = e.getKey();
            int color = e.getValue().getMapColor(level, p).col;
            int x0 = offX + (int) Math.floor((p.getX() - minX) * scale);
            int x1 = offX + (int) Math.ceil((p.getX() - minX + 1) * scale);
            int y0 = offY + (int) Math.floor((maxY - p.getY()) * scale);
            int y1 = offY + (int) Math.ceil((maxY - p.getY() + 1) * scale);
            for (int y = Math.max(0, y0); y < Math.min(SIZE, y1); y++) {
                for (int x = Math.max(0, x0); x < Math.min(SIZE, x1); x++) {
                    int i = y * SIZE + x;
                    if (-p.getZ() < depth[i]) {
                        depth[i] = -p.getZ();
                        out[i] = 0xFF000000 | (color == 0 ? 0x9A9A9A : color);
                    }
                }
            }
        }
        return out;
    }
}
