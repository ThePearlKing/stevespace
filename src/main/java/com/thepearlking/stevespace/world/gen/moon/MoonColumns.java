package com.thepearlking.stevespace.world.gen.moon;

import com.thepearlking.stevespace.registry.ModBlocks;
import com.thepearlking.stevespace.world.gen.BodyColumns;
import com.thepearlking.stevespace.world.planet.TerrainSample;
import com.thepearlking.stevespace.world.planet.moon.MoonTerrain;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Moon columns: a few blocks of regolith over rock - pale anorthosite in the highlands, dark
 * mare basalt under the maria, bright fresh anorthosite where the ground is bright from orbit,
 * helium-3 rich regolith in the mare dust, breccia in crater floors, ice in the polar
 * cold traps. Steep crater walls show bare rock.
 */
public final class MoonColumns extends BodyColumns {

    private final BlockState basalt = block("mare_basalt");
    private final BlockState breccia = block("lunar_breccia");
    private final BlockState helium = block("helium_regolith");
    private final BlockState ice = block("lunar_ice");

    @Override
    public int build(int x, int z, TerrainSample sample, double slope, BlockState[] column, int minY) {
        boolean mare = sample.biome == MoonTerrain.Biome.MARE;
        BlockState rock = mare ? basalt : ModBlocks.ANORTHOSITE.get().defaultBlockState();
        BlockState dust = mare ? ModBlocks.MARE_REGOLITH.get().defaultBlockState() : ModBlocks.REGOLITH.get().defaultBlockState();
        boolean floor = sample.river > 0.45;
        BlockState surface = dust;
        if (!mare && sample.humidity > 0.5) {
            surface = ModBlocks.ANORTHOSITE.get().defaultBlockState(); // fresh bright ground, bright from orbit too
        }
        int top = (int) Math.floor(sample.height);
        int iceTop = (int) Math.floor(sample.topOfIce());
        // Dust settles on flat ground and slides off steep walls.
        int dustDepth = slope > 2.2 ? 0 : slope > 1.2 ? 1 : 2 + (int) (hash(x, z) % 3);
        for (int y = minY; y <= iceTop && y - minY < column.length; y++) {
            BlockState state;
            if (bedrock(x, z, y, minY)) {
                state = BEDROCK;
            } else if (y > top) {
                state = ice;
            } else if (y == top && dustDepth > 0) {
                state = surface;
            } else if (y > top - dustDepth) {
                state = mare && chance(x, y, z, 113, 35) ? helium : dust;
            } else if (floor && y > top - dustDepth - 4) {
                state = breccia;
            } else {
                state = rock;
            }
            column[y - minY] = state;
        }
        return 4; // caves (lava tubes) stay well below the surface
    }
}
