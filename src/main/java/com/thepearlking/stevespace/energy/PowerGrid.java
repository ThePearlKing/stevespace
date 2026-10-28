package com.thepearlking.stevespace.energy;

import com.thepearlking.stevespace.config.SpaceConfig;
import com.thepearlking.stevespace.vessel.VesselAssembler;

import com.thepearlking.stevespace.StevesSpace;
import com.thepearlking.stevespace.registry.WorldBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Power moves through a ship or base the way you'd expect a built vehicle to be wired: through
 * everything that's part of it (with the shipPower setting off, only this mod's blocks touching
 * face to face), and along power pipes to anything further away. Anything with Forge Energy
 * touching that structure, from any mod, is on the same grid.
 */
public final class PowerGrid {

    private static final int MAX_BLOCKS = 2048;
    private static final int REFRESH = 40;

    /** Who a source can reach: machines first, then batteries. */
    private record Reach(List<BlockPos> machines, List<BlockPos> stores, long found, boolean wholeShip) {
    }

    private static final Map<String, Reach> CACHE = new HashMap<>();

    private static final List<BlockPos> FACES = java.util.Arrays.stream(Direction.values()).map(Direction::getNormal)
            .map(BlockPos::new).toList();
    /** The 26 blocks around one, like the ship scan. */
    private static final List<BlockPos> AROUND = java.util.stream.IntStream.range(0, 27)
            .mapToObj(i -> new BlockPos(i % 3 - 1, i / 3 % 3 - 1, i / 9 - 1))
            .filter(p -> !p.equals(BlockPos.ZERO)).toList();

    private PowerGrid() {
    }

    /** Blocks power runs through: everything this mod makes, except the natural ground of other worlds. */
    static boolean conducts(BlockState state) {
        var id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (!id.getNamespace().equals(StevesSpace.MODID) || !state.getFluidState().isEmpty()) {
            return false;
        }
        return WorldBlocks.blocks().stream().noneMatch(b -> b.get() == state.getBlock());
    }

    /** Sends what {@code source} holds to the machines on its grid, then (unless it is one) to batteries. */
    public static void distribute(ServerLevel level, BlockPos origin, PowerBlockEntity source) {
        if (source.buffer().getEnergyStored() <= 0) {
            return;
        }
        Reach reach = reach(level, origin);
        int left = source.buffer().getEnergyStored();
        left = feed(level, reach.machines(), left);
        if (source.role() != PowerBlockEntity.Role.STORE) {
            left = feed(level, reach.stores(), left);
        }
        source.buffer().set(left);
    }

    /** Shares {@code energy} evenly among the targets that can take it; what one can't use goes to the rest. */
    private static int feed(ServerLevel level, List<BlockPos> targets, int energy) {
        List<IEnergyStorage> open = new ArrayList<>();
        for (BlockPos p : targets) {
            BlockEntity be = level.getBlockEntity(p);
            if (be == null) {
                continue;
            }
            IEnergyStorage storage = be.getCapability(ForgeCapabilities.ENERGY, null).orElse(null);
            if (storage != null && storage.canReceive()) {
                open.add(storage);
            }
        }
        while (energy > 0 && !open.isEmpty()) {
            int share = Math.max(1, energy / open.size());
            boolean any = false;
            for (var it = open.iterator(); it.hasNext() && energy > 0; ) {
                int took = it.next().receiveEnergy(Math.min(share, energy), false);
                energy -= took;
                if (took < share) {
                    it.remove(); // full, or at its intake limit this tick
                }
                any |= took > 0;
            }
            if (!any) {
                break;
            }
        }
        return energy;
    }

    private static Reach reach(ServerLevel level, BlockPos origin) {
        String key = level.dimension().location() + "|" + origin.asLong();
        Reach cached = CACHE.get(key);
        long now = level.getGameTime();
        boolean wholeShip = SpaceConfig.SHIP_POWER.get();
        if (cached != null && now - cached.found() < REFRESH && cached.wholeShip() == wholeShip) {
            return cached; // (switching whole-ship power in the settings takes effect at once)
        }
        List<BlockPos> machines = new ArrayList<>();
        List<BlockPos> stores = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        Set<BlockPos> users = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(origin);
        queue.add(origin);
        int limit = wholeShip ? Math.max(MAX_BLOCKS, SpaceConfig.MAX_VESSEL_BLOCKS.get()) : MAX_BLOCKS;
        List<BlockPos> around = wholeShip ? AROUND : FACES;
        while (!queue.isEmpty() && seen.size() < limit) {
            BlockPos p = queue.poll();
            for (BlockPos offset : around) {
                BlockPos n = p.offset(offset);
                if (seen.contains(n) || !level.isLoaded(n)) {
                    continue;
                }
                BlockState state = level.getBlockState(n);
                // Docking ports: power goes out through a collar only into another port that, like
                // this one, has sharing on (so docked ships keep their power to themselves unless both say).
                BlockState here = level.getBlockState(p);
                if ((com.thepearlking.stevespace.block.DockingPortBlock.throughCollar(here, p, n)
                        || com.thepearlking.stevespace.block.DockingPortBlock.throughCollar(state, n, p))
                        && !com.thepearlking.stevespace.block.DockingPortBlock.sharesWith(here, p, state, n)) {
                    continue;
                }
                BlockEntity be = level.getBlockEntity(n);
                if (be != null && !n.equals(origin) && users.add(n)) {
                    Direction side = Direction.fromDelta(-offset.getX(), -offset.getY(), -offset.getZ());
                    if (be instanceof PowerBlockEntity power) {
                        if (power.role() == PowerBlockEntity.Role.SINK) {
                            machines.add(n);
                        } else if (power.role() == PowerBlockEntity.Role.STORE) {
                            stores.add(n);
                        }
                    } else if (be.getCapability(ForgeCapabilities.ENERGY, side).isPresent()) {
                        machines.add(n); // another mod's machine or cable
                    }
                }
                // Whole ship: power reaches everything that would fly with it, corners included.
                if (conducts(state) || wholeShip && VesselAssembler.isVesselBlock(level, n, state)) {
                    seen.add(n);
                    queue.add(n);
                }
            }
        }
        Reach reach = new Reach(machines, stores, now, wholeShip);
        CACHE.put(key, reach);
        if (CACHE.size() > 4096) {
            CACHE.clear();
        }
        return reach;
    }
}
