package com.thepearlking.stevespace.world.gen.worlds;

import com.thepearlking.stevespace.world.gen.BodyColumns;
import com.thepearlking.stevespace.world.planet.TerrainSample;
import com.thepearlking.stevespace.world.planet.worlds.IoTerrain;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Io columns: a skin of sulphur (yellow, white frost or red) over silicate crust; dark basalt and
 * ash on patera floors, flows and cinder cones; lava in the lakes, rivers and fissures, on a bed
 * of magma, with glowing crust round it; lava vents that erupt at the tops of cones and along the
 * fissures; sulphur deposits and veins of magma in the crust; mountains of bare layered crust.
 */
public final class IoColumns extends BodyColumns {

    private final BlockState sulphur = block("sulphur");
    private final BlockState frost = block("sulphur_frost");
    private final BlockState red = block("red_sulphur");
    private final BlockState deposit = block("sulphur_deposit");
    private final BlockState basalt = block("ionian_basalt");
    private final BlockState crust = block("silicate_crust");
    private final BlockState lavaCrust = block("lava_crust");
    private final BlockState ash = block("volcanic_ash");
    private final BlockState vent = block("lava_vent");
    private static final BlockState LAVA = Blocks.LAVA.defaultBlockState();
    private static final BlockState MAGMA = Blocks.MAGMA_BLOCK.defaultBlockState();

    @Override
    public int build(int x, int z, TerrainSample s, double slope, BlockState[] column, int minY) {
        int top = (int) Math.floor(s.height);
        boolean steep = slope > 1.7;
        boolean mountain = s.biome == IoTerrain.MOUNTAINS;
        double n = patch(x, z, 6, 11);
        BlockState surface;
        int skin;
        if (s.under > 0.5 && hash(x * 3 + 5, z * 5 - 7) % (s.river > 0.9 ? 40 : 3) == 0) {
            surface = vent; // a vent: at the top of a cone, or here and there along a fissure
            skin = 1;
        } else if (s.river > 0.9 && !steep) {
            surface = LAVA;
            skin = 1;
        } else if (s.river > 0.84) {
            surface = lavaCrust;
            skin = 2;
        } else if (s.seaIceDepth > 0.5) {
            surface = s.humidity > 0.3 && s.seaIceDepth < 0.85 ? red : n > 0.4 ? basalt : ash;
            skin = 2;
        } else if (mountain || steep) {
            surface = crust;
            skin = 0;
        } else if (s.humidity > 0.5) {
            surface = red;
            skin = 2;
        } else if (s.temperature > 0.5) {
            surface = frost;
            skin = 1;
        } else {
            surface = sulphur;
            skin = 3;
        }
        boolean hot = surface == LAVA || surface == lavaCrust || surface == vent;
        for (int y = minY; y <= top && y - minY < column.length; y++) {
            BlockState state;
            if (bedrock(x, z, y, minY)) {
                state = BEDROCK;
            } else if (y == top) {
                state = surface;
            } else if (y > top - skin) {
                state = hot ? (y == top - 1 && surface == LAVA ? MAGMA : basalt) : surface == frost ? sulphur : surface;
            } else if (hot && y > top - 4) {
                state = chance(x, y, z, 33, 300) ? MAGMA : basalt; // the heat under a lava lake
            } else if (y > top - 14 && !mountain) {
                state = chance(x, y, z, 31, 25) ? deposit : (y > top - 6 ? sulphur : crust);
            } else {
                state = y < 20 ? basalt : crust;
                if (chance(x, y, z, 32, 6)) {
                    state = deposit;
                } else if (y < 30 && patch(x + y * 3, z - y * 2, 5, 13) > 0.82) {
                    state = MAGMA; // veins of magma deep down
                }
            }
            column[y - minY] = state;
        }
        return 3;
    }
}
