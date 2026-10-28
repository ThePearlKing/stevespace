package com.thepearlking.stevespace.remote;

import com.thepearlking.stevespace.vessel.VesselEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;

/**
 * A function pulse in a craft flying as one piece (its blocks aren't in the world, so real
 * redstone can't run): each relay on that function powers whatever touches it, as a redstone
 * pulse would. Blocks that react to that (a nuclear device) say so with {@link Triggered}.
 */
public final class RemoteFunctions {

    /** A block that does something when a relay touching it pulses in flight. */
    public interface Triggered {
        void triggerInFlight(VesselEntity vessel, BlockPos relative);
    }

    private RemoteFunctions() {
    }

    /** Pulses this craft's relays on a function: those tuned to {@code channel}, or all of them (-1). */
    public static void fireInFlight(VesselEntity vessel, int function, int channel) {
        Map<BlockPos, BlockState> blocks = vessel.structure().blocks();
        java.util.Set<BlockPos> powered = new java.util.LinkedHashSet<>();
        for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet()) {
            BlockState s = e.getValue();
            if (s.getBlock() instanceof RcFunctionBlock && s.getValue(RcFunctionBlock.FUNCTION) == function
                    && (channel < 0 || ChannelEntity.channelOf(vessel.structure().blockEntities().get(e.getKey())) == channel)) {
                for (Direction d : Direction.values()) {
                    powered.add(e.getKey().relative(d));
                }
            }
        }
        for (BlockPos rel : powered) {
            BlockState s = blocks.get(rel);
            if (s != null && s.getBlock() instanceof Triggered t) {
                t.triggerInFlight(vessel, rel);
                if (vessel.isRemoved()) {
                    return; // it took the craft with it
                }
            }
        }
    }
}
