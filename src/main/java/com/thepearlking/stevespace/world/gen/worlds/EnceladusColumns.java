package com.thepearlking.stevespace.world.gen.worlds;

import com.thepearlking.stevespace.world.gen.BodyColumns;
import com.thepearlking.stevespace.world.planet.TerrainSample;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Enceladus columns: plume snow over clean ice everywhere; ridged ice in the ridge belts; blue
 * tiger-stripe ice in the trenches, with geyser vents along their floors and silica-rich ice
 * around them.
 */
public final class EnceladusColumns extends BodyColumns {

    private final BlockState snow = block("plume_snow");
    private final BlockState ice = block("enceladus_ice");
    private final BlockState stripe = block("stripe_ice");
    private final BlockState ridged = block("ridged_ice");
    private final BlockState silica = block("silica_ice");
    private final BlockState geyser = block("geyser_vent");

    @Override
    public int build(int x, int z, TerrainSample s, double slope, BlockState[] column, int minY) {
        int top = (int) Math.floor(s.height);
        double n = patch(x, z, 4, 81);
        boolean inStripe = s.river > 0.35;
        BlockState body = s.humidity > 0.5 ? ridged : ice;
        BlockState surface;
        if (inStripe) {
            surface = s.river > 0.92 && hash(x, z) % 29 == 0 ? geyser : stripe;
        } else if (slope > 2.0) {
            surface = body;
        } else {
            surface = snow;
        }
        int snowDepth = surface == snow ? 1 + (int) Math.round(n * 2 + s.temperature * 2) : 0;
        for (int y = minY; y <= top && y - minY < column.length; y++) {
            BlockState state;
            if (bedrock(x, z, y, minY)) {
                state = BEDROCK;
            } else if (y == top) {
                state = surface;
            } else if (y > top - snowDepth) {
                state = snow;
            } else if (inStripe && y > top - 6) {
                state = chance(x, y, z, 82, 120) ? silica : stripe;
            } else {
                state = s.temperature > 0.5 && chance(x, y, z, 83, 8) ? silica : body;
            }
            column[y - minY] = state;
        }
        return 4;
    }
}
