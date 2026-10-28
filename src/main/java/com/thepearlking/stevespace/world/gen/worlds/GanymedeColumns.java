package com.thepearlking.stevespace.world.gen.worlds;

import com.thepearlking.stevespace.world.gen.BodyColumns;
import com.thepearlking.stevespace.world.planet.TerrainSample;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ganymede columns: regolith and dark crust (with magnetite) on the old ground, grooved and
 * bright ice on the lanes, palimpsest ice in the ghost craters, frost toward the poles; ice and
 * rock mixed deeper down.
 */
public final class GanymedeColumns extends BodyColumns {

    private final BlockState dark = block("dark_crust");
    private final BlockState grooved = block("grooved_ice");
    private final BlockState bright = block("bright_ice");
    private final BlockState regolith = block("ganymede_regolith");
    private final BlockState palimpsest = block("palimpsest_ice");
    private final BlockState frost = block("polar_frost");
    private final BlockState magnetite = block("magnetite_ore");

    @Override
    public int build(int x, int z, TerrainSample s, double slope, BlockState[] column, int minY) {
        int top = (int) Math.floor(s.height);
        double n = patch(x, z, 7, 51);
        boolean lane = s.temperature > 0.5;
        BlockState body = lane ? grooved : dark;
        BlockState surface;
        if (s.humidity > 0.5) {
            surface = frost;
        } else if (s.river > 0.5) {
            surface = palimpsest;
        } else if (lane) {
            surface = grooved;
        } else {
            surface = dark;
        }
        for (int y = minY; y <= top && y - minY < column.length; y++) {
            BlockState state;
            if (bedrock(x, z, y, minY)) {
                state = BEDROCK;
            } else if (y == top) {
                state = surface;
            } else if (y > top - 2 && surface != body) {
                state = surface == frost ? body : surface;
            } else if (y > top - 20) {
                state = body;
            } else {
                state = (y / 4 + x / 9 + z / 9) % 3 == 0 ? grooved : dark;
            }
            if (state == dark && y < top - 2 && chance(x, y, z, 52, 10)) {
                state = magnetite;
            }
            column[y - minY] = state;
        }
        return 4;
    }
}
