package com.thepearlking.stevespace.world.gen.worlds;

import com.thepearlking.stevespace.world.gen.BodyColumns;
import com.thepearlking.stevespace.world.planet.TerrainSample;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Europa columns, top to bottom: frost and ice (stained along the lineae, broken plates in the
 * chaos, salt crusts), the crust getting clearer and bluer with depth, then the ocean, then a
 * rocky sea floor with glowing vent chimneys and sulfide ore around them.
 */
public final class EuropaColumns extends BodyColumns {

    private final BlockState ice = block("europan_ice");
    private final BlockState frost = block("europan_frost");
    private final BlockState lineae = block("lineae_ice");
    private final BlockState chaos = block("chaos_ice");
    private final BlockState salt = block("salt_crust");
    private final BlockState deep = block("deep_ice");
    private final BlockState rock = block("seafloor_rock");
    private final BlockState chimney = block("vent_chimney");
    private final BlockState sulfide = block("sulfide_ore");
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();

    @Override
    public int build(int x, int z, TerrainSample s, double slope, BlockState[] column, int minY) {
        int top = (int) Math.floor(s.height);
        int ocean = (int) Math.floor(s.under);
        int floor = (int) Math.floor(s.floor);
        double n = patch(x, z, 5, 41);
        BlockState surface;
        if (s.temperature > 0.5) {
            surface = chaos;
        } else if (s.humidity > 0.5) {
            surface = lineae;
        } else {
            surface = ice;
        }
        // A chimney: a column of vent rock rising from the sea floor at a hot spot.
        boolean vent = s.river > 0.5 && hash(x, z) % 97 == 0;
        int chimneyTop = vent ? floor + 3 + (int) (hash(z, x) % 6) : floor;
        for (int y = minY; y <= top && y - minY < column.length; y++) {
            BlockState state;
            if (bedrock(x, z, y, minY)) {
                state = BEDROCK;
            } else if (y <= floor) {
                state = s.river > 0.3 && y > floor - 4 && chance(x, y, z, 43, 60) ? sulfide : rock;
                if (y == floor && s.river > 0.7 && patch(x, z, 3, 44) > 0.6) {
                    state = chimney;
                }
            } else if (y <= chimneyTop) {
                state = chimney;
            } else if (y <= ocean) {
                state = WATER;
            } else if (y == top) {
                state = surface;
            } else if (y > top - 3) {
                state = surface == frost || surface == salt ? ice : surface;
            } else if (y < ocean + 8) {
                state = deep;
            } else {
                state = ice;
            }
            column[y - minY] = state;
        }
        // Caves stay under the sea floor: the crust must hold the ocean in.
        return top - floor + 3;
    }
}
