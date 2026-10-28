package com.thepearlking.stevespace.world.gen;

import com.thepearlking.stevespace.world.planet.TerrainSample;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Turns one terrain sample into a column of blocks: rock, soil, sand, water, ice.
 * Each body has its own builder (Earth's grass and oceans, the Moon's regolith, ...).
 */
public interface ColumnBuilder {

    /**
     * Fills {@code column} (index 0 = {@code minY}) for the column at (x, z).
     * Entries left null become air.
     *
     * @param sample the terrain at this column
     * @param slope  the steepest height change to a neighbouring column, in blocks per block
     * @return how far below the ground surface caves must stop at this column (keeps caves
     *         from breaking into the sea floor; 0 allows cave entrances)
     */
    int build(int x, int z, TerrainSample sample, double slope, BlockState[] column, int minY);
}
