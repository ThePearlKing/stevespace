package com.thepearlking.stevespace.world;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Arrays;
import java.util.BitSet;

/** Which cells of the space dimension are in use. Cell 0 is never used. */
public final class SpaceCells extends SavedData {

    private static final String NAME = "stevespace_space_cells";
    private final BitSet used = new BitSet();

    public static SpaceCells get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(SpaceCells::new, SpaceCells::load, null), NAME);
    }

    private static SpaceCells load(CompoundTag tag, HolderLookup.Provider registries) {
        SpaceCells cells = new SpaceCells();
        Arrays.stream(tag.getIntArray("used")).forEach(cells.used::set);
        return cells;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putIntArray("used", used.stream().toArray());
        return tag;
    }

    /** The cell {@link #allocate} would hand out next. */
    public int peek() {
        return used.nextClearBit(1);
    }

    public int allocate() {
        int index = used.nextClearBit(1);
        used.set(index);
        setDirty();
        return index;
    }

    public void free(int index) {
        if (index > 0) {
            used.clear(index);
            setDirty();
        }
    }
}
