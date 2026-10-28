package com.thepearlking.stevespace.world.gen.worlds;

import com.thepearlking.stevespace.registry.ModFluids;
import com.thepearlking.stevespace.world.gen.BodyColumns;
import com.thepearlking.stevespace.world.planet.TerrainSample;
import com.thepearlking.stevespace.world.planet.worlds.TitanTerrain;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Titan columns: dune sand piled on the equator, ice pebbles on the plains and in the river beds,
 * bright organic crust on the uplands, tholin mud along the shores, evaporite on dry lake beds,
 * ice bedrock beneath it all, and liquid methane filling everything below sea level.
 */
public final class TitanColumns extends BodyColumns {

    private final BlockState sand = block("tholin_sand");
    private final BlockState bedrockIce = block("ice_bedrock");
    private final BlockState pebbles = block("ice_pebbles");
    private final BlockState mud = block("tholin_mud");
    private final BlockState crust = block("organic_crust");
    private final BlockState evaporite = block("evaporite");

    @Override
    public int build(int x, int z, TerrainSample s, double slope, BlockState[] column, int minY) {
        int top = (int) Math.floor(s.height);
        int sea = TitanTerrain.SEA_LEVEL - 1;
        BlockState methane = ModFluids.METHANE_BLOCK.get().defaultBlockState();
        double n = patch(x, z, 6, 71);
        BlockState surface;
        int skin;
        if (top < sea - 1) {
            surface = mud;
            skin = 2;
        } else if (top <= sea + 1) {
            surface = mud;   // the shore (dark from above too)
            skin = 2;
        } else if (s.seaIceDepth * 0.8 > 0.5) {
            surface = evaporite;
            skin = 2;
        } else if (s.temperature > 0.5) {
            surface = sand;
            skin = 3 + (int) Math.round(s.temperature * 6);
        } else if (s.humidity > 0.5) {
            surface = slope > 1.6 ? bedrockIce : crust;
            skin = 2;
        } else {
            surface = pebbles; // the plains
            skin = 2;
        }
        if (slope > 2.2 && surface != sand) {
            surface = bedrockIce;
        }
        int last = Math.max(top, sea);
        for (int y = minY; y <= last && y - minY < column.length; y++) {
            BlockState state;
            if (bedrock(x, z, y, minY)) {
                state = BEDROCK;
            } else if (y > top) {
                state = methane;
            } else if (y == top) {
                state = surface;
            } else if (y > top - skin) {
                state = surface == evaporite ? mud : surface;
            } else {
                state = bedrockIce;
            }
            column[y - minY] = state;
        }
        return top < sea ? sea - top + 4 : 3;
    }
}
