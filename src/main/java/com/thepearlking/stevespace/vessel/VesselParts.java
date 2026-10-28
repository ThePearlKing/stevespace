package com.thepearlking.stevespace.vessel;

import com.thepearlking.stevespace.block.PropellantTankBlock;
import com.thepearlking.stevespace.registry.ModBlocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/**
 * What each kind of block contributes to a vessel: its mass, and for engines and tanks their
 * thrust and capacity. Any block can be part of a vessel; blocks not listed weigh 0.1 t.
 *
 * <p>Units: tonnes, kilonewtons, blocks per second. With 1 block = 1 m, a tonne pushed by one
 * kilonewton accelerates at 1 block/s².
 */
public final class VesselParts {

    /** Mass of a block that isn't a known part. */
    public static final double DEFAULT_MASS = 0.1;

    /** An engine's performance. */
    public record EngineSpec(double thrust, double exhaustVelocity) {
    }

    private static final Map<Block, Double> MASS = new HashMap<>();
    private static final Map<Block, EngineSpec> ENGINES = new HashMap<>();

    private VesselParts() {
    }

    private static void init() {
        if (!MASS.isEmpty()) {
            return;
        }
        mass(ModBlocks.HULL_WHITE.get(), 0.08);
        mass(ModBlocks.HULL_GREY.get(), 0.08);
        mass(ModBlocks.HULL_DARK.get(), 0.08);
        mass(ModBlocks.HULL_WHITE_BAND.get(), 0.08);
        mass(ModBlocks.HEAT_SHIELD.get(), 0.12);
        mass(ModBlocks.INSULATION_FOIL.get(), 0.02);
        mass(ModBlocks.ALUMINIUM_FRAME.get(), 0.05);
        mass(ModBlocks.PORTHOLE.get(), 0.08);
        mass(ModBlocks.PROPELLANT_TANK.get(), 0.1);
        mass(ModBlocks.ROCKET_ENGINE.get(), 0.6);
        mass(ModBlocks.PILOT_SEAT.get(), 0.06);
        mass(ModBlocks.FLIGHT_COMPUTER.get(), 0.12);
        mass(ModBlocks.RADIO_RACK.get(), 0.1);
        mass(ModBlocks.FLIGHT_DISPLAY.get(), 0.05);
        mass(ModBlocks.OXYGEN_GENERATOR.get(), 0.15);
        mass(ModBlocks.SOLAR_PANEL.get(), 0.02);
        mass(ModBlocks.MAGNETOMETER.get(), 0.03);
        mass(ModBlocks.PLASMA_RECEIVER.get(), 0.05);
        mass(ModBlocks.SOLAR_CONTROLLER.get(), 0.05);
        mass(ModBlocks.RTG.get(), 0.3);
        mass(ModBlocks.BATTERY.get(), 0.25);
        mass(ModBlocks.CREATIVE_POWER.get(), 0.05);
        // 100 kN: one engine lifts a capsule on a column of six tanks at about 1.4 g.
        ENGINES.put(ModBlocks.ROCKET_ENGINE.get(), new EngineSpec(100, 560));
        mass(ModBlocks.VACUUM_ENGINE.get(), 0.4);
        ENGINES.put(ModBlocks.VACUUM_ENGINE.get(), new EngineSpec(60, 950));
        mass(ModBlocks.NUCLEAR_ENGINE.get(), 2.0);
        ENGINES.put(ModBlocks.NUCLEAR_ENGINE.get(), new EngineSpec(150, 1700));
        mass(ModBlocks.CARBON_HULL.get(), 0.03);
    }

    private static void mass(Block block, double tonnes) {
        MASS.put(block, tonnes);
    }

    /** Dry mass of one block in tonnes (propellant not included). */
    public static double massOf(BlockState state) {
        init();
        Double known = MASS.get(state.getBlock());
        if (known != null) {
            return known;
        }
        // Light decorative blocks (torches, buttons, carpets) shouldn't weigh as much as stone.
        try {
            return state.isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO)
                    ? DEFAULT_MASS : 0.02;
        } catch (RuntimeException shapeNeedsAWorld) {
            return DEFAULT_MASS;
        }
    }

    public static EngineSpec engine(BlockState state) {
        init();
        return ENGINES.get(state.getBlock());
    }

    public static double propellantCapacity(BlockState state) {
        return state.getBlock() instanceof PropellantTankBlock ? PropellantTankBlock.CAPACITY : 0;
    }
}
