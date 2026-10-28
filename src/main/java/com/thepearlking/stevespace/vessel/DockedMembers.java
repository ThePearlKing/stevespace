package com.thepearlking.stevespace.vessel;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * The ships docked together into one, each remembered by a block of its own: so undocking hands
 * each one back its own id, owner, name and share of the fuel, and takes nothing from the other.
 */
public final class DockedMembers {

    /** One of the ships: a block of its own (the joined ship's block coordinates), who it is, and its share of the fuel (0..1). */
    public record Member(BlockPos block, UUID id, UUID owner, String name, double share) {
        Member moved(Function<BlockPos, BlockPos> move) {
            return new Member(move.apply(block), id, owner, name, share);
        }

        Member scaled(double factor) {
            return new Member(block, id, owner, name, share * factor);
        }
    }

    private DockedMembers() {
    }

    public static List<Member> moved(List<Member> members, Function<BlockPos, BlockPos> move) {
        List<Member> out = new ArrayList<>(members.size());
        for (Member m : members) {
            out.add(m.moved(move));
        }
        return out;
    }

    /** The shares made to add up to 1 again; none at all when only one ship is left. */
    static List<Member> normalized(List<Member> members) {
        if (members.size() <= 1) {
            return new ArrayList<>();
        }
        double total = members.stream().mapToDouble(Member::share).sum();
        List<Member> out = new ArrayList<>(members.size());
        for (Member m : members) {
            out.add(m.scaled(total > 0 ? 1 / total : 1.0 / members.size()));
        }
        return out;
    }

    /** The one with the biggest share. */
    static Member primary(List<Member> members) {
        Member best = null;
        for (Member m : members) {
            if (best == null || m.share() > best.share()) {
                best = m;
            }
        }
        return best;
    }

    public static ListTag save(List<Member> members) {
        ListTag list = new ListTag();
        for (Member m : members) {
            CompoundTag t = new CompoundTag();
            t.putLong("block", m.block().asLong());
            t.putUUID("id", m.id());
            if (m.owner() != null) {
                t.putUUID("owner", m.owner());
            }
            t.putString("name", m.name());
            t.putDouble("share", m.share());
            list.add(t);
        }
        return list;
    }

    public static List<Member> load(ListTag list) {
        List<Member> out = new ArrayList<>();
        for (Tag tag : list) {
            CompoundTag t = (CompoundTag) tag;
            if (!t.hasUUID("id")) {
                continue;
            }
            out.add(new Member(BlockPos.of(t.getLong("block")), t.getUUID("id"), t.hasUUID("owner") ? t.getUUID("owner") : null,
                    t.getString("name"), t.getDouble("share")));
        }
        return out;
    }
}
