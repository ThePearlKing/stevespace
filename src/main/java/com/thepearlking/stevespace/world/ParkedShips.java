package com.thepearlking.stevespace.world;

import com.thepearlking.stevespace.universe.DVec3;
import com.thepearlking.stevespace.universe.StateVector;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Ships parked in orbit: real blocks standing in their own cell of the space dimension, whose
 * cell keeps following the ship's orbit (on rails) while people walk around inside, build on it,
 * or step out for a spacewalk. Sitting down at the pilot seat and throttling up turns one back
 * into a flying vessel.
 */
public final class ParkedShips extends SavedData {

    private static final String NAME = "stevespace_parked_ships";

    /**
     * One parked ship.
     *
     * @param cell   its cell of the space dimension
     * @param body   the body it orbits
     * @param state  its orbit (relative to the body) at {@code time}
     * @param name   the ship's name
     * @param min    lowest corner of its blocks (world coordinates)
     * @param max    highest corner of its blocks
     * @param anchor the point in the cell that moves along the orbit (blocks are placed around it)
     */
    public record Parked(int cell, String body, StateVector state, double time, String name, BlockPos min, BlockPos max,
                         DVec3 anchor) {

        public boolean contains(double x, double y, double z, double margin) {
            return x >= min.getX() - margin && x <= max.getX() + 1 + margin
                    && y >= min.getY() - margin && y <= max.getY() + 1 + margin
                    && z >= min.getZ() - margin && z <= max.getZ() + 1 + margin;
        }
    }

    private final Map<Integer, Parked> ships = new HashMap<>();

    public static ParkedShips get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ParkedShips::new, ParkedShips::load, null), NAME);
    }

    /** The cell a position in the space dimension belongs to. */
    public static int cellAt(double x) {
        return (int) Math.round((x - 0.5) / SpaceDimension.CELL_SPACING);
    }

    public Parked at(double x) {
        return ships.get(cellAt(x));
    }

    public Parked get(int cell) {
        return ships.get(cell);
    }

    public Collection<Parked> all() {
        return ships.values();
    }

    public void put(Parked parked) {
        ships.put(parked.cell(), parked);
        setDirty();
    }

    /** Building onto a parked ship makes it bigger: its bounds grow to take in {@code pos}. */
    public void grow(int cell, BlockPos pos) {
        Parked p = ships.get(cell);
        if (p == null || p.contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 0)) {
            return;
        }
        BlockPos min = new BlockPos(Math.min(p.min().getX(), pos.getX()), Math.min(p.min().getY(), pos.getY()), Math.min(p.min().getZ(), pos.getZ()));
        BlockPos max = new BlockPos(Math.max(p.max().getX(), pos.getX()), Math.max(p.max().getY(), pos.getY()), Math.max(p.max().getZ(), pos.getZ()));
        ships.put(cell, new Parked(p.cell(), p.body(), p.state(), p.time(), p.name(), min, max, p.anchor()));
        setDirty();
    }

    public Parked remove(int cell) {
        Parked removed = ships.remove(cell);
        if (removed != null) {
            setDirty();
        }
        return removed;
    }

    private static ParkedShips load(CompoundTag tag, HolderLookup.Provider registries) {
        ParkedShips data = new ParkedShips();
        for (Tag t : tag.getList("ships", Tag.TAG_COMPOUND)) {
            CompoundTag s = (CompoundTag) t;
            data.ships.put(s.getInt("cell"), new Parked(s.getInt("cell"), s.getString("body"),
                    new StateVector(vec(s, "r"), vec(s, "v")), s.getDouble("time"), s.getString("name"),
                    BlockPos.of(s.getLong("min")), BlockPos.of(s.getLong("max")), vec(s, "anchor")));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Parked p : ships.values()) {
            CompoundTag s = new CompoundTag();
            s.putInt("cell", p.cell());
            s.putString("body", p.body());
            putVec(s, "r", p.state().position());
            putVec(s, "v", p.state().velocity());
            s.putDouble("time", p.time());
            s.putString("name", p.name());
            s.putLong("min", p.min().asLong());
            s.putLong("max", p.max().asLong());
            putVec(s, "anchor", p.anchor());
            list.add(s);
        }
        tag.put("ships", list);
        return tag;
    }

    private static void putVec(CompoundTag tag, String key, DVec3 v) {
        tag.putDouble(key + "x", v.x());
        tag.putDouble(key + "y", v.y());
        tag.putDouble(key + "z", v.z());
    }

    private static DVec3 vec(CompoundTag tag, String key) {
        return new DVec3(tag.getDouble(key + "x"), tag.getDouble(key + "y"), tag.getDouble(key + "z"));
    }
}
