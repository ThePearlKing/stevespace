package com.thepearlking.stevespace.world.gen.worlds;

import com.thepearlking.stevespace.world.gen.BodyColumns;
import com.thepearlking.stevespace.world.planet.TerrainSample;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Eris columns: a thick skin of brilliant methane frost over everything except the steepest
 * ground, where the grey rock shows; frost-filled pits; water ice and rock below.
 */
public final class ErisColumns extends BodyColumns {

    private final BlockState frost = block("eris_frost");
    private final BlockState rock = block("eris_rock");
    private final BlockState ice = block("charon_ice");
    private final BlockState core = block("core_rock");
    private final BlockState uraninite = block("uraninite_ore");

    @Override
    public int build(int x, int z, TerrainSample s, double slope, BlockState[] column, int minY) {
        int top = (int) Math.floor(s.height);
        double n = patch(x, z, 6, 151);
        boolean bare = false; // white all over, as from above
        int coreTop = 22 + (int) (n * 10);
        for (int y = minY; y <= top && y - minY < column.length; y++) {
            BlockState state;
            if (bedrock(x, z, y, minY)) {
                state = BEDROCK;
            } else if (y > top - (bare ? 0 : 3)) {
                state = frost;
            } else if (y > top - 12) {
                state = rock;
            } else if (y > coreTop) {
                state = ice;
            } else {
                state = chance(x, y, z, 152, 6) ? uraninite : core;
            }
            if (bare && y == top) {
                state = rock;
            }
            column[y - minY] = state;
        }
        return 4;
    }
}
