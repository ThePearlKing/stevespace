package com.thepearlking.stevespace.remote;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Every remote control receiver (or, the other list, function relay) standing in the world: dimension and position. */
public final class RemoteSites extends SavedData {

    private static final String NAME = "stevespace_remote_sites";
    private static final String RELAYS = "stevespace_relay_sites";

    public record Site(String dimension, BlockPos pos) {
    }

    private final Set<String> keys = new LinkedHashSet<>();

    public static RemoteSites get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(RemoteSites::new, RemoteSites::load, null), NAME);
    }

    /** Every function relay standing in the world. */
    public static RemoteSites relays(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(RemoteSites::new, RemoteSites::load, null), RELAYS);
    }

    public List<Site> all() {
        List<Site> list = new ArrayList<>();
        for (String key : keys) {
            int bar = key.lastIndexOf('|');
            list.add(new Site(key.substring(0, bar), BlockPos.of(Long.parseLong(key.substring(bar + 1)))));
        }
        return list;
    }

    public void add(String dimension, BlockPos pos) {
        if (keys.add(dimension + "|" + pos.asLong())) {
            setDirty();
        }
    }

    public void remove(String dimension, BlockPos pos) {
        if (keys.remove(dimension + "|" + pos.asLong())) {
            setDirty();
        }
    }

    private static RemoteSites load(CompoundTag tag, HolderLookup.Provider registries) {
        RemoteSites data = new RemoteSites();
        for (Tag t : tag.getList("sites", Tag.TAG_STRING)) {
            data.keys.add(t.getAsString());
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (String key : keys) {
            list.add(net.minecraft.nbt.StringTag.valueOf(key));
        }
        tag.put("sites", list);
        return tag;
    }
}
