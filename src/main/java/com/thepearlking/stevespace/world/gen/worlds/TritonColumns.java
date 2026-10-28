package com.thepearlking.stevespace.world.gen.worlds;

import com.thepearlking.stevespace.world.gen.BodyColumns;
import com.thepearlking.stevespace.world.planet.TerrainSample;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Triton columns: pink frost and nitrogen ice in the cap with dark plume dust in the streaks and
 * nitrogen geysers; dimpled cantaloupe crust in the west; methane ice and nitrogen ice on the
 * plains; core rock (with uraninite) deep down.
 */
public final class TritonColumns extends BodyColumns {

    private final BlockState frost = block("pink_frost");
    private final BlockState nitrogen = block("nitrogen_ice");
    private final BlockState cantaloupe = block("cantaloupe_crust");
    private final BlockState dust = block("plume_dust");
    private final BlockState methane = block("methane_ice");
    private final BlockState geyser = block("nitrogen_geyser");
    private final BlockState core = block("core_rock");
    private final BlockState uraninite = block("uraninite_ore");

    @Override
    public int build(int x, int z, TerrainSample s, double slope, BlockState[] column, int minY) {
        int top = (int) Math.floor(s.height);
        double n = patch(x, z, 5, 91);
        BlockState surface;
        BlockState body;
        if (s.river > 0.5) {
            surface = geyser;
            body = nitrogen;
        } else if (s.humidity * 0.8 > 0.5) {
            surface = dust; // the dark plume streaks, over cap or plain
            body = s.temperature > 0.5 ? nitrogen : methane;
        } else if (s.temperature > 0.5) {
            surface = frost;
            body = nitrogen;
        } else if (s.seaIceDepth > 0.5) {
            surface = cantaloupe;
            body = cantaloupe;
        } else {
            surface = methane;
            body = methane;
        }
        int coreTop = 22 + (int) (n * 8);
        for (int y = minY; y <= top && y - minY < column.length; y++) {
            BlockState state;
            if (bedrock(x, z, y, minY)) {
                state = BEDROCK;
            } else if (y == top) {
                state = surface;
            } else if (y > top - 2 && surface == dust) {
                state = frost;
            } else if (y > coreTop) {
                state = y > top - 12 ? body : nitrogen;
            } else {
                state = chance(x, y, z, 92, 7) ? uraninite : core;
            }
            column[y - minY] = state;
        }
        return 4;
    }
}
