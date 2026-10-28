package com.thepearlking.stevespace.radio;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Every radio standing in the world (not flying): a dimension, a block position and a name. */
public final class RadioSites extends SavedData {

    private static final String NAME = "stevespace_radio_sites";

    public record Site(String dimension, BlockPos pos, String name) {
        String key() {
            return dimension + "|" + pos.asLong();
        }
    }

    private final Map<String, Site> sites = new LinkedHashMap<>();
    /** What each radio (by station id) is tuned to, in MHz; untuned radios sit on {@link #DEFAULT_MHZ}. */
    private final Map<String, Double> tuned = new LinkedHashMap<>();
    /** S band, where spacecraft really talk. */
    public static final double DEFAULT_MHZ = 2250.0;

    public double frequency(String station) {
        return tuned.getOrDefault(station, DEFAULT_MHZ);
    }

    public void tune(String station, double mhz) {
        tuned.put(station, Math.round(Math.max(1, Math.min(10_000, mhz)) * 10) / 10.0);
        setDirty();
    }

    public static RadioSites get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(RadioSites::new, RadioSites::load, null), NAME);
    }

    public Collection<Site> all() {
        return sites.values();
    }

    public void add(Site site) {
        sites.put(site.key(), site);
        setDirty();
    }

    public void remove(String dimension, BlockPos pos) {
        if (sites.remove(dimension + "|" + pos.asLong()) != null) {
            setDirty();
        }
    }

    private static RadioSites load(CompoundTag tag, HolderLookup.Provider registries) {
        RadioSites data = new RadioSites();
        for (Tag t : tag.getList("sites", Tag.TAG_COMPOUND)) {
            CompoundTag s = (CompoundTag) t;
            Site site = new Site(s.getString("dim"), BlockPos.of(s.getLong("pos")), s.getString("name"));
            data.sites.put(site.key(), site);
        }
        for (Tag t : tag.getList("tuned", Tag.TAG_COMPOUND)) {
            CompoundTag f = (CompoundTag) t;
            data.tuned.put(f.getString("id"), f.getDouble("mhz"));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Site site : sites.values()) {
            CompoundTag s = new CompoundTag();
            s.putString("dim", site.dimension());
            s.putLong("pos", site.pos().asLong());
            s.putString("name", site.name());
            list.add(s);
        }
        tag.put("sites", list);
        ListTag freqs = new ListTag();
        for (Map.Entry<String, Double> e : tuned.entrySet()) {
            CompoundTag f = new CompoundTag();
            f.putString("id", e.getKey());
            f.putDouble("mhz", e.getValue());
            freqs.add(f);
        }
        tag.put("tuned", freqs);
        return tag;
    }
}
