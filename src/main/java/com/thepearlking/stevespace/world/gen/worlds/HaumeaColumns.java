package com.thepearlking.stevespace.world.gen.worlds;

import com.thepearlking.stevespace.world.gen.BodyColumns;
import com.thepearlking.stevespace.world.planet.TerrainSample;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Haumea columns: a thin coat of crystalline water ice (dark red in the spot) over a great deal of
 * dense rock: it's mostly rock inside, close under the ice.
 */
public final class HaumeaColumns extends BodyColumns {

    private final BlockState ice = block("crystalline_ice");
    private final BlockState red = block("red_spot_ice");
    private final BlockState rock = block("haumea_rock");
    private final BlockState uraninite = block("uraninite_ore");

    @Override
    public int build(int x, int z, TerrainSample s, double slope, BlockState[] column, int minY) {
        int top = (int) Math.floor(s.height);
        double n = patch(x, z, 5, 161);
        BlockState coat = s.humidity > 0.5 ? red : ice;
        int iceDepth = 5 + (int) (n * 4);
        for (int y = minY; y <= top && y - minY < column.length; y++) {
            BlockState state;
            if (bedrock(x, z, y, minY)) {
                state = BEDROCK;
            } else if (y > top - iceDepth) {
                state = coat;
            } else {
                state = chance(x, y, z, 162, 8) ? uraninite : rock;
            }
            column[y - minY] = state;
        }
        return 4;
    }
}
