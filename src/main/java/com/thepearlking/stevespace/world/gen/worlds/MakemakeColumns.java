package com.thepearlking.stevespace.world.gen.worlds;

import com.thepearlking.stevespace.world.gen.BodyColumns;
import com.thepearlking.stevespace.world.planet.TerrainSample;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Makemake columns: coarse methane ice, reddish-brown tholin in the streaks, ethane frost settled
 * in the hollows, water ice under the methane, and the rocky core with uraninite deep down.
 */
public final class MakemakeColumns extends BodyColumns {

    private final BlockState methane = block("makemake_methane_ice");
    private final BlockState tholin = block("makemake_tholin");
    private final BlockState frost = block("ethane_frost");
    private final BlockState ice = block("charon_ice");
    private final BlockState core = block("core_rock");
    private final BlockState uraninite = block("uraninite_ore");

    @Override
    public int build(int x, int z, TerrainSample s, double slope, BlockState[] column, int minY) {
        int top = (int) Math.floor(s.height);
        double n = patch(x, z, 5, 141);
        BlockState surface;
        if (s.humidity > 0.5) {
            surface = tholin;
        } else if (s.seaIceDepth * 0.7 > 0.4) {
            surface = frost;
        } else {
            surface = methane;
        }
        int coreTop = 24 + (int) (n * 8);
        for (int y = minY; y <= top && y - minY < column.length; y++) {
            BlockState state;
            if (bedrock(x, z, y, minY)) {
                state = BEDROCK;
            } else if (y == top) {
                state = surface;
            } else if (y > top - 4) {
                state = surface == tholin ? tholin : methane;
            } else if (y > coreTop) {
                state = ice;
            } else {
                state = chance(x, y, z, 142, 7) ? uraninite : core;
            }
            column[y - minY] = state;
        }
        return 4;
    }
}
