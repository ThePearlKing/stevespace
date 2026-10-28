package com.thepearlking.stevespace.registry;

import com.thepearlking.stevespace.StevesSpace;
import com.thepearlking.stevespace.block.PropellantTankBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, StevesSpace.MODID);

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<PropellantTankBlockEntity>> PROPELLANT_TANK = BLOCK_ENTITIES.register(
            "propellant_tank", () -> BlockEntityType.Builder.of(PropellantTankBlockEntity::new, ModBlocks.PROPELLANT_TANK.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.thepearlking.stevespace.block.GeyserBlockEntity>> GEYSER = BLOCK_ENTITIES.register(
            "geyser", () -> BlockEntityType.Builder.of(com.thepearlking.stevespace.block.GeyserBlockEntity::new,
                    WorldBlocks.get("geyser_vent"), WorldBlocks.get("nitrogen_geyser"), WorldBlocks.get("lava_vent")).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.thepearlking.stevespace.block.FlightDisplayBlockEntity>> FLIGHT_DISPLAY = BLOCK_ENTITIES.register(
            "flight_display", () -> BlockEntityType.Builder.of(com.thepearlking.stevespace.block.FlightDisplayBlockEntity::new,
                    ModBlocks.FLIGHT_DISPLAY.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.thepearlking.stevespace.remote.RemoteReceiverEntity>> REMOTE_RECEIVER = BLOCK_ENTITIES.register(
            "remote_receiver", () -> BlockEntityType.Builder.of(com.thepearlking.stevespace.remote.RemoteReceiverEntity::new,
                    ModBlocks.REMOTE_RECEIVER.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.thepearlking.stevespace.remote.RemoteConsoleEntity>> REMOTE_CONSOLE = BLOCK_ENTITIES.register(
            "remote_console", () -> BlockEntityType.Builder.of(com.thepearlking.stevespace.remote.RemoteConsoleEntity::new,
                    ModBlocks.REMOTE_CONSOLE.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.thepearlking.stevespace.remote.RcFunctionEntity>> RC_FUNCTION = BLOCK_ENTITIES.register(
            "rc_function", () -> BlockEntityType.Builder.of(com.thepearlking.stevespace.remote.RcFunctionEntity::new,
                    ModBlocks.RC_FUNCTION.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.thepearlking.stevespace.block.OxygenGeneratorBlockEntity>> OXYGEN_GENERATOR = BLOCK_ENTITIES.register(
            "oxygen_generator", () -> BlockEntityType.Builder.of(com.thepearlking.stevespace.block.OxygenGeneratorBlockEntity::new,
                    ModBlocks.OXYGEN_GENERATOR.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.thepearlking.stevespace.energy.PowerBlocks.SolarControllerEntity>> SOLAR_CONTROLLER =
            BLOCK_ENTITIES.register("solar_controller", () -> BlockEntityType.Builder.of(
                    com.thepearlking.stevespace.energy.PowerBlocks.SolarControllerEntity::new, ModBlocks.SOLAR_CONTROLLER.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.thepearlking.stevespace.energy.PowerBlocks.RtgEntity>> RTG =
            BLOCK_ENTITIES.register("rtg", () -> BlockEntityType.Builder.of(
                    com.thepearlking.stevespace.energy.PowerBlocks.RtgEntity::new, ModBlocks.RTG.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.thepearlking.stevespace.energy.PowerBlocks.BatteryEntity>> BATTERY =
            BLOCK_ENTITIES.register("battery", () -> BlockEntityType.Builder.of(
                    com.thepearlking.stevespace.energy.PowerBlocks.BatteryEntity::new, ModBlocks.SMALL_BATTERY.get(), ModBlocks.BATTERY.get(),
                    ModBlocks.LARGE_BATTERY.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.thepearlking.stevespace.energy.Machines.CoalGeneratorEntity>> COAL_GENERATOR =
            BLOCK_ENTITIES.register("coal_generator", () -> BlockEntityType.Builder.of(
                    com.thepearlking.stevespace.energy.Machines.CoalGeneratorEntity::new, ModBlocks.COAL_GENERATOR.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.thepearlking.stevespace.energy.Machines.ElectricFurnaceEntity>> ELECTRIC_FURNACE =
            BLOCK_ENTITIES.register("electric_furnace", () -> BlockEntityType.Builder.of(
                    com.thepearlking.stevespace.energy.Machines.ElectricFurnaceEntity::new, ModBlocks.ELECTRIC_FURNACE.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.thepearlking.stevespace.energy.Machines.CrusherEntity>> CRUSHER =
            BLOCK_ENTITIES.register("crusher", () -> BlockEntityType.Builder.of(
                    com.thepearlking.stevespace.energy.Machines.CrusherEntity::new, ModBlocks.CRUSHER.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.thepearlking.stevespace.energy.PowerBlocks.CreativeSourceEntity>> CREATIVE_POWER =
            BLOCK_ENTITIES.register("creative_power", () -> BlockEntityType.Builder.of(
                    com.thepearlking.stevespace.energy.PowerBlocks.CreativeSourceEntity::new, ModBlocks.CREATIVE_POWER.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.thepearlking.stevespace.block.GravityGeneratorBlock.Entity>> GRAVITY_GENERATOR =
            BLOCK_ENTITIES.register("gravity_generator", () -> BlockEntityType.Builder.of(
                    com.thepearlking.stevespace.block.GravityGeneratorBlock.Entity::new, ModBlocks.GRAVITY_GENERATOR.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.thepearlking.stevespace.block.ScienceBlocks.MagnetometerEntity>> MAGNETOMETER =
            BLOCK_ENTITIES.register("magnetometer", () -> BlockEntityType.Builder.of(
                    com.thepearlking.stevespace.block.ScienceBlocks.MagnetometerEntity::new, ModBlocks.MAGNETOMETER.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.thepearlking.stevespace.block.ScienceBlocks.PlasmaReceiverEntity>> PLASMA_RECEIVER =
            BLOCK_ENTITIES.register("plasma_receiver", () -> BlockEntityType.Builder.of(
                    com.thepearlking.stevespace.block.ScienceBlocks.PlasmaReceiverEntity::new, ModBlocks.PLASMA_RECEIVER.get()).build(null));

    private ModBlockEntities() {
    }
}
