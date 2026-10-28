package com.thepearlking.stevespace.world.gen.worlds;

import com.thepearlking.stevespace.world.gen.BodyColumns;
import com.thepearlking.stevespace.world.planet.TerrainSample;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Callisto columns: dark dust (deep in the crater floors) over dirty ice and dark rock; bright
 * ice where spires and fresh rims show; breccia in the Valhalla rings; carbon deposits in the
 * dust and rock.
 */
public final class CallistoColumns extends BodyColumns {

    private final BlockState dust = block("callistan_dust");
    private final BlockState ice = block("callistan_ice");
    private final BlockState dirty = block("dirty_ice");
    private final BlockState rock = block("callistan_rock");
    private final BlockState breccia = block("ring_breccia");
    private final BlockState carbon = block("carbon_deposit");

    @Override
    public int build(int x, int z, TerrainSample s, double slope, BlockState[] column, int minY) {
        int top = (int) Math.floor(s.height);
        double n = patch(x, z, 5, 61);
        boolean brightIce = s.temperature * 0.8 > 0.5;
        BlockState surface;
        int dustDepth;
        if (brightIce) {
            surface = ice;
            dustDepth = 0;
        } else if (s.river * 0.6 > 0.3) {
            surface = dust; // the dark dust in the lows
            dustDepth = 2 + (int) Math.round(s.river * 5);
        } else if (s.biome == com.thepearlking.stevespace.world.planet.worlds.CallistoTerrain.VALHALLA) {
            surface = dirty; // Valhalla's paler rings
            dustDepth = 0;
        } else {
            surface = breccia; // the cratered plains
            dustDepth = 0;
        }
        for (int y = minY; y <= top && y - minY < column.length; y++) {
            BlockState state;
            if (bedrock(x, z, y, minY)) {
                state = BEDROCK;
            } else if (y == top) {
                state = surface;
            } else if (y > top - dustDepth) {
                state = chance(x, y, z, 62, 14) ? carbon : dust;
            } else if (brightIce && y > top - 12) {
                state = ice;
            } else if (y > top - 25) {
                state = dirty;
            } else {
                state = chance(x, y, z, 63, 6) ? carbon : rock;
            }
            column[y - minY] = state;
        }
        return 4;
    }
}
