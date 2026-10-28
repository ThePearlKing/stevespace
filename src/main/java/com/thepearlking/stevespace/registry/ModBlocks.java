package com.thepearlking.stevespace.registry;

import com.thepearlking.stevespace.StevesSpace;
import com.thepearlking.stevespace.block.FacingBlock;
import com.thepearlking.stevespace.block.FlightComputerBlock;
import com.thepearlking.stevespace.block.FuelLoaderBlock;
import com.thepearlking.stevespace.block.PilotSeatBlock;
import com.thepearlking.stevespace.block.PortholeBlock;
import com.thepearlking.stevespace.block.PropellantTankBlock;
import com.thepearlking.stevespace.block.RocketEngineBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

public final class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, StevesSpace.MODID);

    public static final RegistryObject<Block> HULL_WHITE = register("hull_white", () -> new Block(hull(MapColor.SNOW)));
    public static final RegistryObject<Block> HULL_GREY = register("hull_grey", () -> new Block(hull(MapColor.COLOR_LIGHT_GRAY)));
    public static final RegistryObject<Block> HULL_DARK = register("hull_dark", () -> new Block(hull(MapColor.COLOR_GRAY)));
    public static final RegistryObject<Block> HULL_WHITE_BAND = register("hull_white_band", () -> new Block(hull(MapColor.SNOW)));
    public static final RegistryObject<Block> HEAT_SHIELD = register("heat_shield", () -> new Block(hull(MapColor.COLOR_BLACK).strength(4f, 9f)));
    public static final RegistryObject<Block> INSULATION_FOIL = register("insulation_foil",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).strength(0.8f).sound(SoundType.WOOL)));
    public static final RegistryObject<Block> ALUMINIUM_FRAME = register("aluminium_frame", () -> new Block(hull(MapColor.METAL)));
    public static final RegistryObject<Block> PORTHOLE = register("porthole",
            () -> new PortholeBlock(hull(MapColor.SNOW).noOcclusion().isViewBlocking((s, l, p) -> false)));
    public static final RegistryObject<Block> SPACECRAFT_WINDOW = register("spacecraft_window",
            () -> new com.thepearlking.stevespace.block.ConnectedWindowBlock(hull(MapColor.COLOR_LIGHT_GRAY).noOcclusion()
                    .isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false).sound(SoundType.GLASS)));
    public static final RegistryObject<Block> WINDOW_PANE = register("window_pane",
            () -> new net.minecraft.world.level.block.IronBarsBlock(hull(MapColor.COLOR_LIGHT_GRAY).noOcclusion().sound(SoundType.GLASS)));
    public static final RegistryObject<Block> PROPELLANT_TANK = register("propellant_tank", () -> new PropellantTankBlock(hull(MapColor.SNOW)));
    public static final RegistryObject<Block> ROCKET_ENGINE = register("rocket_engine",
            () -> new RocketEngineBlock(hull(MapColor.COLOR_GRAY).strength(5f, 8f).noOcclusion()));
    /** Lighter, more efficient, less thrust: for flying in space. */
    public static final RegistryObject<Block> VACUUM_ENGINE = register("vacuum_engine",
            () -> new RocketEngineBlock(hull(MapColor.COLOR_LIGHT_GRAY).strength(5f, 8f).noOcclusion()));
    /** Heavy and strong, and very efficient. */
    public static final RegistryObject<Block> NUCLEAR_ENGINE = register("nuclear_engine",
            () -> new RocketEngineBlock(hull(MapColor.COLOR_GRAY).strength(6f, 10f).noOcclusion()));
    public static final RegistryObject<Block> CARBON_HULL = register("carbon_hull", () -> new Block(hull(MapColor.COLOR_BLACK)));
    public static final RegistryObject<Block> PILOT_SEAT = register("pilot_seat",
            () -> new PilotSeatBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).strength(2f).sound(SoundType.WOOL).noOcclusion()));
    public static final RegistryObject<Block> PASSENGER_SEAT = register("passenger_seat",
            () -> new com.thepearlking.stevespace.block.PassengerSeatBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY)
                    .strength(2f).sound(SoundType.WOOL).noOcclusion()));
    public static final RegistryObject<Block> FLIGHT_DISPLAY = register("flight_display",
            () -> new com.thepearlking.stevespace.block.FlightDisplayBlock(hull(MapColor.COLOR_GRAY).lightLevel(s -> 3)));
    public static final RegistryObject<Block> OXYGEN_GENERATOR = register("oxygen_generator",
            () -> new com.thepearlking.stevespace.block.OxygenGeneratorBlock(hull(MapColor.COLOR_LIGHT_GRAY)));
    public static final RegistryObject<Block> POWER_PIPE = register("power_pipe",
            () -> new com.thepearlking.stevespace.energy.PowerPipeBlock(hull(MapColor.COLOR_ORANGE).noOcclusion().strength(1f).sound(SoundType.COPPER)));
    public static final RegistryObject<Block> COAL_GENERATOR = register("coal_generator",
            () -> new com.thepearlking.stevespace.energy.Machines.MachineBlock(hull(MapColor.COLOR_GRAY)
                    .lightLevel(s -> s.getValue(com.thepearlking.stevespace.energy.Machines.LIT) ? 13 : 0), com.thepearlking.stevespace.energy.Machines.Kind.COAL_GENERATOR));
    public static final RegistryObject<Block> SMALL_BATTERY = register("small_battery",
            () -> new com.thepearlking.stevespace.energy.PowerBlocks.Battery(hull(MapColor.COLOR_ORANGE), 50_000, 250));
    public static final RegistryObject<Block> SOLAR_PANEL = register("solar_panel",
            () -> new com.thepearlking.stevespace.energy.PowerBlocks.SolarPanel(hull(MapColor.COLOR_BLUE).noOcclusion().strength(1.5f)));
    public static final RegistryObject<Block> SOLAR_CONTROLLER = register("solar_controller",
            () -> new com.thepearlking.stevespace.energy.PowerBlocks.SolarController(hull(MapColor.COLOR_GRAY)));
    public static final RegistryObject<Block> RTG = register("rtg",
            () -> new com.thepearlking.stevespace.energy.PowerBlocks.Rtg(hull(MapColor.COLOR_GRAY).lightLevel(s -> 2)));
    public static final RegistryObject<Block> BATTERY = register("battery",
            () -> new com.thepearlking.stevespace.energy.PowerBlocks.Battery(hull(MapColor.COLOR_GRAY), 200_000, 1_000));
    public static final RegistryObject<Block> LARGE_BATTERY = register("large_battery",
            () -> new com.thepearlking.stevespace.energy.PowerBlocks.Battery(hull(MapColor.COLOR_LIGHT_GRAY), 2_000_000, 8_000));
    public static final RegistryObject<Block> ELECTRIC_FURNACE = register("electric_furnace",
            () -> new com.thepearlking.stevespace.energy.Machines.MachineBlock(hull(MapColor.COLOR_GRAY)
                    .lightLevel(s -> s.getValue(com.thepearlking.stevespace.energy.Machines.LIT) ? 13 : 0), com.thepearlking.stevespace.energy.Machines.Kind.ELECTRIC_FURNACE));
    public static final RegistryObject<Block> CRUSHER = register("crusher",
            () -> new com.thepearlking.stevespace.energy.Machines.MachineBlock(hull(MapColor.COLOR_GRAY), com.thepearlking.stevespace.energy.Machines.Kind.CRUSHER));
    public static final RegistryObject<Block> CREATIVE_POWER = register("creative_power",
            () -> new com.thepearlking.stevespace.energy.PowerBlocks.CreativeSource(hull(MapColor.COLOR_PURPLE).strength(-1f, 3_600_000f)));
    public static final RegistryObject<Block> MAGNETOMETER = register("magnetometer",
            () -> new com.thepearlking.stevespace.block.ScienceBlocks.Magnetometer(hull(MapColor.COLOR_GRAY).lightLevel(s -> 3)));
    public static final RegistryObject<Block> PLASMA_RECEIVER = register("plasma_receiver",
            () -> new com.thepearlking.stevespace.block.ScienceBlocks.PlasmaReceiver(hull(MapColor.COLOR_GRAY).lightLevel(s -> 3)));
    public static final RegistryObject<Block> FLAG = register("flag",
            () -> new com.thepearlking.stevespace.block.FlagBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(1f)
                    .noOcclusion().sound(SoundType.WOOL)));
    public static final RegistryObject<Block> FLIGHT_COMPUTER = register("flight_computer", () -> new FlightComputerBlock(hull(MapColor.COLOR_GRAY)));
    public static final RegistryObject<Block> GRAVITY_GENERATOR = register("gravity_generator",
            () -> new com.thepearlking.stevespace.block.GravityGeneratorBlock(hull(MapColor.COLOR_GRAY).lightLevel(s -> 4)));
    public static final RegistryObject<Block> RADIO_RACK = register("radio_rack", () -> new com.thepearlking.stevespace.block.RadioBlock(hull(MapColor.COLOR_GRAY)));
    // Remote control: a receiver to fly a craft by, a console to fly it from, relays for its functions.
    public static final RegistryObject<Block> REMOTE_RECEIVER = register("remote_receiver",
            () -> new com.thepearlking.stevespace.remote.RemoteReceiverBlock(hull(MapColor.COLOR_GRAY)
                    .isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false)));
    public static final RegistryObject<Block> REMOTE_CONSOLE = register("remote_console",
            () -> new com.thepearlking.stevespace.remote.RemoteConsoleBlock(hull(MapColor.COLOR_GRAY)));
    public static final RegistryObject<Block> NUCLEAR_DEVICE = register("nuclear_device",
            () -> new com.thepearlking.stevespace.nuke.NuclearDeviceBlock(hull(MapColor.COLOR_GREEN)));
    public static final RegistryObject<Block> RC_FUNCTION = register("rc_function",
            () -> new com.thepearlking.stevespace.remote.RcFunctionBlock(hull(MapColor.COLOR_RED)));
    public static final RegistryObject<Block> LAUNCH_PAD = register("launch_pad",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(3f, 12f).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> FUEL_LOADER = register("fuel_loader", () -> new FuelLoaderBlock(hull(MapColor.COLOR_GRAY)));

    public static final RegistryObject<Block> DOCKING_PORT = register("docking_port",
            () -> new com.thepearlking.stevespace.block.DockingPortBlock(hull(MapColor.COLOR_LIGHT_GRAY).noOcclusion()));
    public static final RegistryObject<Block> TELESCOPE = register("telescope",
            () -> new com.thepearlking.stevespace.block.TelescopeBlock(hull(MapColor.SNOW).noOcclusion()));

    public static final RegistryObject<Block> MISSION_CONTROL = register("mission_control",
            () -> new com.thepearlking.stevespace.block.MissionControlBlock(hull(MapColor.COLOR_GRAY)));

    // Moon ground
    public static final RegistryObject<Block> REGOLITH = register("regolith",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GRAY).strength(0.6f).sound(SoundType.SAND)));
    public static final RegistryObject<Block> MARE_REGOLITH = register("mare_regolith",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(0.6f).sound(SoundType.SAND)));
    public static final RegistryObject<Block> ANORTHOSITE = register("anorthosite",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).strength(1.5f, 6f).requiresCorrectToolForDrops()));

    // Mars ground
    public static final RegistryObject<Block> MARS_REGOLITH = register("mars_regolith",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE).strength(0.6f).sound(SoundType.SAND)));
    public static final RegistryObject<Block> MARS_ROCK = register("mars_rock",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_BROWN).strength(1.5f, 6f).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> BASALT_SAND = register("basalt_sand",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(0.6f).sound(SoundType.SAND)));

    private ModBlocks() {
    }

    /** Metal spacecraft parts: like iron, but lighter to mine. */
    private static BlockBehaviour.Properties hull(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(3f, 6f).sound(SoundType.METAL).requiresCorrectToolForDrops();
    }

    /** The other worlds' ground blocks (see {@link WorldBlocks}). Declared after the rest. */
    static {
        WorldBlocks.init();
    }

    static RegistryObject<Block> registerWorld(String id, Supplier<Block> block) {
        return register(id, block);
    }

    private static <T extends Block> RegistryObject<T> register(String id, Supplier<T> block) {
        RegistryObject<T> registered = BLOCKS.register(id, block);
        ModItems.ITEMS.register(id, () -> new net.minecraft.world.item.BlockItem(registered.get(), new net.minecraft.world.item.Item.Properties()));
        return registered;
    }
}
