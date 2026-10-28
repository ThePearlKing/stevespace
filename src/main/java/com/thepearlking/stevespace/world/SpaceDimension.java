package com.thepearlking.stevespace.world;

import com.thepearlking.stevespace.StevesSpace;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * The space dimension: where vessels are while above the atmosphere. Nothing is generated in it.
 * Each vessel or station in space occupies its own "cell" (a region far from the others), whose
 * axes stay fixed relative to the stars.
 */
public final class SpaceDimension {

    public static final ResourceKey<Level> KEY = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(StevesSpace.MODID, "space"));

    /** Spacing between cells, far enough that one cell never loads another's chunks. */
    public static final int CELL_SPACING = 4096;
    /** Height of a cell's centre. */
    public static final double CELL_Y = 128;

    private SpaceDimension() {
    }

    public static boolean isSpace(Level level) {
        return level.dimension() == KEY;
    }

    /** Centre of cell number {@code index}. */
    public static net.minecraft.world.phys.Vec3 cellCenter(int index) {
        return new net.minecraft.world.phys.Vec3(index * (double) CELL_SPACING + 0.5, CELL_Y, 0.5);
    }
}
