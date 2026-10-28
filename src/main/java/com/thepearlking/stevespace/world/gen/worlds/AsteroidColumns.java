package com.thepearlking.stevespace.world.gen.worlds;

import com.thepearlking.stevespace.world.gen.BodyColumns;
import com.thepearlking.stevespace.world.planet.TerrainSample;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Asteroid columns: dark rubble (regolith) over solid rock, bright rock in fresh crater ejecta and
 * Ceres' salt, a little iron (magnetite) in the rock below.
 */
public final class AsteroidColumns extends BodyColumns {

    private final BlockState rubble = block("charon_regolith");
    private final BlockState rock = block("lunar_breccia");
    private final BlockState bright = block("silica_ice");
    private final BlockState core = block("core_rock");
    private final BlockState iron = block("magnetite_ore");
    private final BlockState ceresDark = block("mercury_rock");
    private final BlockState vestaDark = block("ice_bedrock");
    private final BlockState vestaFresh = block("tessera_rock");
    private final BlockState pallasDark = block("mercury_regolith");
    private final BlockState hygieaDark = block("haumea_rock");

    @Override
    public int build(int x, int z, TerrainSample s, double slope, BlockState[] column, int minY) {
        int top = (int) Math.floor(s.height);
        double n = patch(x, z, 5, 141);
        // Each asteroid its own greys, as seen from above: dark ground, fresher ejecta, bright salt.
        String kind = s.biome.biomeId().replace("stevespace:", "").split("_")[0];
        BlockState dark = switch (kind) {
            case "vesta" -> vestaDark;
            case "pallas" -> pallasDark;
            case "hygiea" -> hygieaDark;
            default -> ceresDark;
        };
        BlockState fresh = switch (kind) {
            case "vesta" -> vestaFresh;
            case "pallas" -> rock;
            case "hygiea" -> pallasDark;
            default -> rubble;
        };
        BlockState surface = s.humidity > 0.5 ? bright : s.seaIceDepth * 0.7 > 0.35 ? fresh : dark;
        BlockState rubble = dark;
        int coreTop = 30 + (int) (n * 10);
        for (int y = minY; y <= top && y - minY < column.length; y++) {
            BlockState state;
            if (bedrock(x, z, y, minY)) {
                state = BEDROCK;
            } else if (y == top) {
                state = surface;
            } else if (y > top - 3) {
                state = surface == bright ? bright : rubble;
            } else if (y > coreTop) {
                state = chance(x, y, z, 142, 60) ? iron : rock;
            } else {
                state = core;
            }
            column[y - minY] = state;
        }
        return 4;
    }
}
