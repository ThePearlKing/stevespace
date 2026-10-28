package com.thepearlking.stevespace.block;

import com.thepearlking.stevespace.energy.PowerBlockEntity;
import com.thepearlking.stevespace.registry.ModBlockEntities;
import com.thepearlking.stevespace.suit.Habitats;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The oxygen generator's power store. Running costs a little for the machine plus a share for
 * every block of air it keeps up; with no power the room's air runs out.
 */
public class OxygenGeneratorBlockEntity extends PowerBlockEntity {

    public OxygenGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.OXYGEN_GENERATOR.get(), pos, state, 8_000, 500, 0);
    }

    @Override
    public Role role() {
        return Role.SINK;
    }

    /** Power per tick to keep a room of {@code volume} blocks in air. */
    public static int cost(int volume) {
        return 4 + volume / 100;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) {
            server.getServer().execute(() -> Habitats.addGenerator(server, worldPosition));
        }
    }
}
