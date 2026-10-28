package com.thepearlking.stevespace.vessel;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Who is where aboard a flying vessel: each crew member either has a seat (by its index in the
 * vessel's seat list, 0 being the pilot's) or is up and walking about the cabin, at a position
 * in the ship's own block coordinates (feet, centre of the player).
 *
 * <p>Kept by player id rather than by passenger order, so it survives the vessel changing
 * dimension (which makes a new entity and boards the crew again in any order).
 */
public final class CabinCrew {

    private final Map<UUID, Integer> seats = new HashMap<>();
    private final Map<UUID, Vector3d> walking = new HashMap<>();

    public boolean knows(UUID id) {
        return seats.containsKey(id) || walking.containsKey(id);
    }

    /** Seat index, or -1 if walking or unknown. */
    public int seatOf(UUID id) {
        Integer s = seats.get(id);
        return s == null ? -1 : s;
    }

    public boolean isWalking(UUID id) {
        return walking.containsKey(id);
    }

    /** Where a walking crew member stands (ship block coordinates), or null. */
    public Vector3d walkPosition(UUID id) {
        return walking.get(id);
    }

    public UUID inSeat(int seat) {
        for (Map.Entry<UUID, Integer> e : seats.entrySet()) {
            if (e.getValue() == seat) {
                return e.getKey();
            }
        }
        return null;
    }

    public boolean seatFree(int seat) {
        return inSeat(seat) == null;
    }

    /** The lowest free seat, or -1. */
    public int freeSeat(int seatCount) {
        for (int i = 0; i < seatCount; i++) {
            if (seatFree(i)) {
                return i;
            }
        }
        return -1;
    }

    public int seated() {
        return seats.size();
    }

    public void sit(UUID id, int seat) {
        walking.remove(id);
        seats.put(id, seat);
    }

    public void walk(UUID id, Vector3d at) {
        seats.remove(id);
        walking.put(id, new Vector3d(at));
    }

    public void remove(UUID id) {
        seats.remove(id);
        walking.remove(id);
    }

    public Iterable<UUID> everyone() {
        java.util.Set<UUID> all = new java.util.HashSet<>(seats.keySet());
        all.addAll(walking.keySet());
        return all;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        CompoundTag s = new CompoundTag();
        seats.forEach((id, seat) -> s.putInt(id.toString(), seat));
        CompoundTag w = new CompoundTag();
        walking.forEach((id, p) -> {
            ListTag list = new ListTag();
            list.add(net.minecraft.nbt.DoubleTag.valueOf(p.x));
            list.add(net.minecraft.nbt.DoubleTag.valueOf(p.y));
            list.add(net.minecraft.nbt.DoubleTag.valueOf(p.z));
            w.put(id.toString(), list);
        });
        tag.put("seats", s);
        tag.put("walking", w);
        return tag;
    }

    public static CabinCrew load(CompoundTag tag) {
        CabinCrew crew = new CabinCrew();
        CompoundTag s = tag.getCompound("seats");
        for (String key : s.getAllKeys()) {
            crew.seats.put(UUID.fromString(key), s.getInt(key));
        }
        CompoundTag w = tag.getCompound("walking");
        for (String key : w.getAllKeys()) {
            ListTag list = w.getList(key, Tag.TAG_DOUBLE);
            crew.walking.put(UUID.fromString(key), new Vector3d(list.getDouble(0), list.getDouble(1), list.getDouble(2)));
        }
        return crew;
    }
}
