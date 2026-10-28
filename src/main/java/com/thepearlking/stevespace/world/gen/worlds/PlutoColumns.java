package com.thepearlking.stevespace.world.gen.worlds;

import com.thepearlking.stevespace.world.gen.BodyColumns;
import com.thepearlking.stevespace.world.planet.TerrainSample;
import com.thepearlking.stevespace.world.planet.worlds.PlutoTerrain;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Pluto columns: Sputnik ice with trough ice between the cells and carbon monoxide ice near the
 * shore; water-ice mountains under methane snow; red-brown tholins; bladed ice; core rock with
 * uraninite deep down.
 */
public final class PlutoColumns extends BodyColumns {

    private final BlockState sputnik = block("sputnik_ice");
    private final BlockState trough = block("trough_ice");
    private final BlockState tholins = block("tholins");
    private final BlockState mountain = block("mountain_ice");
    private final BlockState snow = block("methane_snow");
    private final BlockState blades = block("bladed_ice");
    private final BlockState co = block("carbon_monoxide_ice");
    private final BlockState frost = block("tholin_frost");
    private final BlockState core = block("core_rock");
    private final BlockState uraninite = block("uraninite_ore");

    /**
     * The surface follows exactly what the terrain says is there, the same as its colour seen
     * from space (PlutoTerrain.surfaceColor): plains of tan tholin frost, the dark tholin regions,
     * snow where the map shows snow, the borders exactly where the globe has them (like Earth's
     * biomes).
     */
    @Override
    public int build(int x, int z, TerrainSample s, double slope, BlockState[] column, int minY) {
        int top = (int) Math.floor(s.height);
        double n = patch(x, z, 6, 101);
        double edge = 0; // borders exactly where the globe shows them
        BlockState surface;
        BlockState body;
        if (s.temperature > 0.5 + edge) {
            surface = s.under > 0.5 ? trough : (s.temperature < 0.8 && n > 0.6 ? co : sputnik);
            body = sputnik;
        } else if (s.biome == PlutoTerrain.MOUNTAINS || slope > 2.5) {
            surface = s.seaIceDepth > 0.55 + edge && slope < 2.5 ? snow : mountain;
            body = mountain;
        } else if (s.river > 0.5 + edge) {
            surface = s.seaIceDepth > 0.6 + edge ? snow : blades;
            body = blades;
        } else if (s.seaIceDepth > 0.55 + edge) {
            surface = snow;
            body = mountain;
        } else if (s.humidity > 0.5 + edge) {
            surface = tholins;
            body = mountain;
        } else {
            surface = frost;
            body = mountain;
        }
        int coreTop = 24 + (int) (n * 8);
        int skin = surface == tholins || surface == snow || surface == frost ? 2 : 1;
        for (int y = minY; y <= top && y - minY < column.length; y++) {
            BlockState state;
            if (bedrock(x, z, y, minY)) {
                state = BEDROCK;
            } else if (y == top) {
                state = surface;
            } else if (y > top - skin) {
                state = surface;
            } else if (y > coreTop) {
                state = body;
            } else {
                state = chance(x, y, z, 102, 7) ? uraninite : core;
            }
            column[y - minY] = state;
        }
        return 4;
    }
}
