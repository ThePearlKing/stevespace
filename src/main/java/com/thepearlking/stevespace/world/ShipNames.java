package com.thepearlking.stevespace.world;

import com.thepearlking.stevespace.block.PilotSeatBlock;
import com.thepearlking.stevespace.vessel.VesselStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * A ship's name while it stands in the world as blocks (landed, on the pad): kept by where its
 * controls are, so it gets the same name back when it lifts off again.
 */
public final class ShipNames extends SavedData {

    private static final String NAME = "stevespace_ship_names";

    private final Map<String, String> names = new HashMap<>();

    public static ShipNames get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ShipNames::new, ShipNames::load, null), NAME);
    }

    private static String key(ServerLevel level, BlockPos pos) {
        return level.dimension().location() + "|" + pos.asLong();
    }

    /** The ship has come down as blocks at {@code origin}: remember its name at its controls. */
    public static void remember(ServerLevel level, BlockPos origin, VesselStructure structure, Rotation rotation, Component name) {
        if (name == null) {
            return;
        }
        ShipNames data = get(level);
        String json = Component.Serializer.toJson(name, level.registryAccess());
        for (Map.Entry<BlockPos, BlockState> e : structure.blocks().entrySet()) {
            if (e.getValue().getBlock() instanceof PilotSeatBlock) {
                data.names.put(key(level, origin.offset(e.getKey().rotate(rotation))), json);
            }
        }
        data.setDirty();
    }

    /** Lifting off from {@code origin}: its name, if it had one when it last came down (and forget it there). */
    public static Component recall(ServerLevel level, BlockPos origin, VesselStructure structure) {
        ShipNames data = get(level);
        Component found = null;
        for (Map.Entry<BlockPos, BlockState> e : structure.blocks().entrySet()) {
            if (e.getValue().getBlock() instanceof PilotSeatBlock) {
                String json = data.names.remove(key(level, origin.offset(e.getKey())));
                if (json != null && found == null) {
                    try {
                        found = Component.Serializer.fromJson(json, level.registryAccess());
                    } catch (RuntimeException ignored) {
                        // unreadable: the default name
                    }
                }
            }
        }
        if (found != null) {
            data.setDirty();
        }
        return found;
    }

    private static ShipNames load(CompoundTag tag, HolderLookup.Provider registries) {
        ShipNames data = new ShipNames();
        for (String k : tag.getAllKeys()) {
            data.names.put(k, tag.getString(k));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        names.forEach(tag::putString);
        return tag;
    }
}
