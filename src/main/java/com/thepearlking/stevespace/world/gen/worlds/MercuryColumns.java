package com.thepearlking.stevespace.world.gen.worlds;

import com.thepearlking.stevespace.world.gen.BodyColumns;
import com.thepearlking.stevespace.world.planet.TerrainSample;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Mercury columns: grey-brown regolith over rock; plains basalt under the smooth plains; the dark
 * graphite crust on the old ground; bright hollow deposits; layered rock on the scarp faces;
 * ilmenite in the rock; ice in the polar craters.
 */
public final class MercuryColumns extends BodyColumns {

    private final BlockState regolith = block("mercury_regolith");
    private final BlockState rock = block("mercury_rock");
    private final BlockState graphite = block("graphite_crust");
    private final BlockState hollow = block("hollow_deposit");
    private final BlockState basalt = block("plains_basalt");
    private final BlockState smooth = block("lunar_breccia");
    private final BlockState caloris_ = block("tessera_rock");
    private final BlockState scarp = block("scarp_rock");
    private final BlockState ilmenite = block("ilmenite_ore");
    private final BlockState ice = block("mercury_ice");

    @Override
    public int build(int x, int z, TerrainSample s, double slope, BlockState[] column, int minY) {
        int top = (int) Math.floor(s.height);
        int iceTop = (int) Math.floor(s.topOfIce());
        boolean steep = slope > 1.6;
        boolean plains = s.temperature > 0.5;
        boolean caloris = s.biome == com.thepearlking.stevespace.world.planet.worlds.MercuryTerrain.CALORIS;
        BlockState host = plains ? basalt : rock;
        BlockState surface;
        if (steep) {
            surface = slope > 2.4 ? scarp : host;
        } else if (s.river > 0.5) {
            surface = hollow;
        } else if (s.humidity * 0.8 > 0.5) {
            surface = graphite;
        } else if (caloris) {
            surface = caloris_;
        } else if (plains) {
            surface = smooth;
        } else {
            surface = scarp; // the cratered ground: the same grey as from above
        }
        int soil = steep ? 0 : 2 + (int) (hash(x, z) % 2);
        for (int y = minY; y <= iceTop && y - minY < column.length; y++) {
            BlockState state;
            if (bedrock(x, z, y, minY)) {
                state = BEDROCK;
            } else if (y > top) {
                state = ice;
            } else if (y == top) {
                state = surface;
            } else if (y > top - soil && surface != host && surface != scarp) {
                state = surface == graphite ? graphite : regolith;
            } else if (steep && y > top - 8) {
                state = scarp;
            } else {
                state = y > top - 10 ? host : rock;
                if (state == rock && chance(x, y, z, 7, 9)) {
                    state = ilmenite;
                }
            }
            column[y - minY] = state;
        }
        return 4;
    }
}
