package com.thepearlking.stevespace.world.gen.worlds;

import com.thepearlking.stevespace.world.gen.BodyColumns;
import com.thepearlking.stevespace.world.planet.TerrainSample;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Charon columns: grey regolith over water ice, the red tholin cap in the north, bare ice on the
 * canyon walls, bright ammonia ice around fresh craters, core rock with uraninite deep down.
 */
public final class CharonColumns extends BodyColumns {

    private final BlockState ice = block("charon_ice");
    private final BlockState regolith = block("charon_regolith");
    private final BlockState tholin = block("mordor_tholin");
    private final BlockState ammonia = block("ammonia_ice");
    private final BlockState core = block("core_rock");
    private final BlockState uraninite = block("uraninite_ore");

    @Override
    public int build(int x, int z, TerrainSample s, double slope, BlockState[] column, int minY) {
        int top = (int) Math.floor(s.height);
        double n = patch(x, z, 5, 131);
        BlockState surface;
        int skin;
        if (slope > 2.0 || s.river > 0.6) {
            surface = ice;
            skin = 0;
        } else if (s.seaIceDepth * 0.6 > 0.3) {
            surface = ammonia;
            skin = 1;
        } else if (s.humidity > 0.5) {
            surface = tholin;
            skin = 2;
        } else if (s.temperature > 0.5) {
            surface = ice; // Vulcan's smooth plain
            skin = 1;
        } else {
            surface = regolith;
            skin = 2;
        }
        int coreTop = 20 + (int) (n * 8);
        for (int y = minY; y <= top && y - minY < column.length; y++) {
            BlockState state;
            if (bedrock(x, z, y, minY)) {
                state = BEDROCK;
            } else if (y == top) {
                state = surface;
            } else if (y > top - skin) {
                state = surface == ammonia ? ice : surface;
            } else if (y > coreTop) {
                state = ice;
            } else {
                state = chance(x, y, z, 132, 7) ? uraninite : core;
            }
            column[y - minY] = state;
        }
        return 4;
    }
}
