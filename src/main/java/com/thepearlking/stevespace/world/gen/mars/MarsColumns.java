package com.thepearlking.stevespace.world.gen.mars;

import com.thepearlking.stevespace.registry.ModBlocks;
import com.thepearlking.stevespace.world.gen.BodyColumns;
import com.thepearlking.stevespace.world.planet.TerrainSample;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Mars columns: rusty regolith over red-brown rock, with a skin of bright dust on the plains;
 * dark basalt sand where the map shows dark ground, the volcanoes' darker rock; layered
 * sediment in the canyon walls; clay and hematite concretions under old crater-lake floors;
 * water ice under the north cap and carbon dioxide frost on the south one.
 */
public final class MarsColumns extends BodyColumns {

    private static final BlockState DEEPSLATE = Blocks.DEEPSLATE.defaultBlockState();
    private final BlockState basalt = block("martian_basalt");
    private final BlockState brightDust = block("martian_dust");
    private final BlockState sediment = block("layered_sediment");
    private final BlockState clay = block("martian_clay");
    private final BlockState hematite = block("hematite_ore");
    private final BlockState waterIce = block("martian_ice");
    private final BlockState dryIce = block("dry_ice_frost");

    @Override
    public int build(int x, int z, TerrainSample sample, double slope, BlockState[] column, int minY) {
        long noise = hash(x, z);
        // Patchy (not speckled) edges between dust and dark sand: a smooth noise a few blocks across.
        double patch = patch(x, z, 7, 121);
        double lat = sample.under;
        // The same regions as from orbit: dusty plains, redder highlands, the volcanoes' darker
        // rock, and dark sand where the map shows it.
        boolean volcanic = sample.temperature > 0.5;
        boolean darkSand = sample.humidity * 1.4 > 0.5;
        boolean canyon = sample.river > 0.5;
        boolean lakeBed = sample.seaIceDepth > 0.45; // underneath only: the dust covers it
        BlockState rock = volcanic ? basalt : ModBlocks.MARS_ROCK.get().defaultBlockState();
        BlockState dust;
        if (darkSand) {
            dust = ModBlocks.BASALT_SAND.get().defaultBlockState();
        } else if (volcanic) {
            dust = ModBlocks.MARS_ROCK.get().defaultBlockState();
        } else if (sample.biome == com.thepearlking.stevespace.world.planet.mars.MarsTerrain.Biome.PLAINS) {
            dust = brightDust;
        } else {
            dust = ModBlocks.MARS_REGOLITH.get().defaultBlockState();
        }
        BlockState surface = dust;
        int top = (int) Math.floor(sample.height);
        int dustDepth = slope > 2.2 ? 0 : slope > 1.2 ? 1 : 2 + (int) (noise >>> 20) % 3;
        int iceTop = (int) Math.floor(sample.topOfIce());
        boolean south = lat < 0;
        for (int y = minY; y <= iceTop && y - minY < column.length; y++) {
            BlockState state;
            if (bedrock(x, z, y, minY)) {
                state = BEDROCK;
            } else if (y < 4 + (int) ((noise >>> 32) % 4)) {
                state = DEEPSLATE;
            } else if (y > top) {
                state = y == iceTop && south ? dryIce : waterIce; // polar caps
            } else if (y == top && dustDepth > 0) {
                state = surface;
            } else if (y > top - dustDepth) {
                state = lakeBed && chance(x, y, z, 124, 60) ? hematite : dust;
            } else if (canyon || (slope > 2.2 && !volcanic)) {
                state = y > top - 30 ? sediment : rock;
            } else if (lakeBed && y > top - dustDepth - 3) {
                state = chance(x, y, z, 125, 80) ? hematite : clay;
            } else {
                state = rock;
            }
            column[y - minY] = state;
        }
        return 4;
    }
}
