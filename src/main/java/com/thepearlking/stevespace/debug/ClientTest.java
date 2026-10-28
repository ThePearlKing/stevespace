package com.thepearlking.stevespace.debug;

import com.thepearlking.stevespace.StevesSpace;
import com.thepearlking.stevespace.client.ClientUniverse;
import com.thepearlking.stevespace.universe.DVec3;
import com.thepearlking.stevespace.universe.LocalSky;
import com.thepearlking.stevespace.universe.ServerUniverse;
import com.thepearlking.stevespace.universe.SolarSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.server.IntegratedServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Automated visual check (developer only): with STEVESPACE_CLIENTTEST=1 the client joins the
 * quick-play world, stages scenes (time of day + camera direction), saves screenshots to
 * run-clienttest/screenshots and quits. Runs on a private virtual display, never the user's.
 */
public final class ClientTest {

    private static net.minecraft.server.level.ServerPlayer trailPilot;

    private record Scene(String name, Consumer<Minecraft> setup, int settleTicks) {
    }

    private static final List<Scene> scenes = new ArrayList<>();
    private static int sceneIndex = -1;
    private static int waitTicks;
    private static int ticksInWorld;
    private static boolean worldRequested;
    private static boolean holdScreen;
    private static net.minecraft.core.BlockPos rocketSeat;
    private static com.thepearlking.stevespace.vessel.VesselEntity testFire;
    /** A scene can wait for something to happen before its settle time starts. */
    private static java.util.function.BooleanSupplier waitCondition;
    private static int conditionTicks;

    private ClientTest() {
    }

    public static boolean enabled() {
        return "1".equals(System.getenv("STEVESPACE_CLIENTTEST"));
    }

    /** The standard regression shots; add scenes here for new visual features. */
    static {
        scenes.add(new Scene("config_gameplay", mc -> {
            var mod = net.minecraftforge.fml.ModList.get().getModContainerById(StevesSpace.MODID).orElseThrow();
            boolean hasScreen = net.minecraftforge.client.ConfigScreenHandler.getScreenFactoryFor(mod.getModInfo()).isPresent();
            StevesSpace.LOGGER.info("[ClientTest] mods-menu config button enabled: {}", hasScreen);
            mc.setScreen(new com.thepearlking.stevespace.client.gui.config.SpaceConfigScreen(null));
            holdScreen = true;
        }, 20));
        scenes.add(new Scene("config_visuals", mc -> {
            for (var child : java.util.List.copyOf(mc.screen.children())) {
                if (child instanceof net.minecraft.client.gui.components.Button b && b.getMessage().getString().equals("Visuals")) {
                    b.onPress();
                    break;
                }
            }
        }, 20));
        scenes.add(new Scene("close_config", mc -> {
            holdScreen = false;
            mc.setScreen(null);
        }, 5));
        scenes.add(new Scene("ground_view", mc -> {
            command(mc, "gamemode spectator @a");
            time(mc, 4500);
            look(mc, 30, 5);
        }, 80));
        scenes.add(new Scene("aerial_300", mc -> {
            command(mc, "tp @a ~ 300 ~");
            look(mc, 30, 35);
        }, 160));
        scenes.add(new Scene("aerial_1200", mc -> {
            command(mc, "tp @a ~ 1200 ~");
            look(mc, 30, 30);
        }, 160));
        scenes.add(new Scene("aerial_1200_up", mc -> look(mc, 30, -20), 30));
        scenes.add(new Scene("aerial_2600", mc -> {
            command(mc, "tp @a ~ 2600 ~");
            look(mc, 30, 40);
        }, 100));
        scenes.add(new Scene("aerial_6000", mc -> {
            command(mc, "tp @a ~ 6000 ~");
            look(mc, 30, 60);
        }, 100));
        scenes.add(new Scene("noon_south", mc -> {
            command(mc, "tp @a ~ 80 ~");
            time(mc, 6000);
            look(mc, 0, -25);
        }, 60));
        scenes.add(new Scene("sunset", mc -> {
            when(mc, t -> elevation(t, "sun") < 3 && elevation(t, "sun") > 1 && !rising(t, "sun"));
            lookAt(mc, "sun", 8);
        }, 60));
        scenes.add(new Scene("dusk", mc -> {
            when(mc, t -> elevation(t, "sun") < -5 && elevation(t, "sun") > -7 && !rising(t, "sun"));
            lookAt(mc, "sun", 15);
        }, 60));
        scenes.add(new Scene("night_stars", mc -> {
            when(mc, t -> elevation(t, "sun") < -30);
            look(mc, 180, -60);
        }, 60));
        scenes.add(new Scene("moon_zoom", mc -> {
            when(mc, t -> elevation(t, "moon") > 25 && elevation(t, "sun") < -15);
            zoom(mc, 30);
            lookAt(mc, "moon", 0);
        }, 60));
        scenes.add(new Scene("moon_ground", mc -> {
            command(mc, "gamemode spectator @a");
            command(mc, "execute in stevespace:moon run tp @a 0 140 0 0 20");
            time(mc, 4500);
        }, 200));
        scenes.add(new Scene("moon_ground_low", mc -> {
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                var moon = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                        net.minecraft.resources.ResourceLocation.parse("stevespace:moon")));
                int y = moon.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, 0, 0);
                var player = server.getPlayerList().getPlayers().get(0);
                player.teleportTo(moon, 0.5, y + 1.7, 0.5, java.util.Set.of(), 30, 8);
                StevesSpace.LOGGER.info("[ClientTest] moon ground at y={} gravity={}", y,
                        player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.GRAVITY));
            });
        }, 120));
        scenes.add(new Scene("moon_earthrise", mc -> lookAt(mc, "earth", 0), 60));
        scenes.add(new Scene("moon_aerial", mc -> {
            command(mc, "execute in stevespace:moon run tp @a 0 500 0 0 45");
        }, 160));
        scenes.add(new Scene("mars_ground", mc -> {
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                var mars = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                        net.minecraft.resources.ResourceLocation.parse("stevespace:mars")));
                mars.getChunk(0, 0);
                int y = mars.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, 0, 0);
                var player = server.getPlayerList().getPlayers().get(0);
                player.teleportTo(mars, 0.5, y + 1.7, 0.5, java.util.Set.of(), 60, 6);
                StevesSpace.LOGGER.info("[ClientTest] mars ground at y={} gravity={}", y,
                        player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.GRAVITY));
            });
            time(mc, 4500);
        }, 200));
        scenes.add(new Scene("mars_canyon", mc -> command(mc, "execute in stevespace:mars run tp @a -4400 160 300 0 55"), 200));
        scenes.add(new Scene("mars_olympus", mc -> command(mc, "execute in stevespace:mars run tp @a -7700 450 -800 -120 30"), 200));
        scenes.add(new Scene("mars_high", mc -> command(mc, "execute in stevespace:mars run tp @a 0 1300 0 0 50"), 120));
        for (String body : new String[]{"io", "europa", "titan", "venus", "pluto", "enceladus"}) {
            scenes.add(new Scene(body + "_ground", mc -> {
                IntegratedServer server = mc.getSingleplayerServer();
                server.execute(() -> {
                    var level = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(StevesSpace.MODID, body)));
                    // Somewhere it's mid-morning: west of the point under the Sun.
                    int x = (int) daysideX(server, body);
                    level.getChunk(x >> 4, 5);
                    int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, 80);
                    var player = server.getPlayerList().getPlayers().get(0);
                    player.teleportTo(level, x + 0.5, y + 6, 80.5, java.util.Set.of(), 40, 15);
                    ClientTest.dayside[0] = x;
                });
            }, 160));
            scenes.add(new Scene(body + "_aerial", mc -> command(mc, "execute in stevespace:" + body + " run tp @a " + ClientTest.dayside[0] + " 700 80 40 40"), 120));
        }
        // The radio by a base on the Moon: Mission Control on Earth in the list, a message sent.
        scenes.add(new Scene("radio_screen", mc -> {
            command(mc, "gamemode creative @a");
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                server.overworld().getChunk(0, 0);
                server.overworld().setBlockAndUpdate(new net.minecraft.core.BlockPos(0, 200, 0),
                        com.thepearlking.stevespace.registry.ModBlocks.MISSION_CONTROL.get().defaultBlockState());
                var level = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(StevesSpace.MODID, "moon")));
                level.getChunk(0, 0);
                int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, 0, 0);
                ClientTest.dayside[0] = y;
                level.setBlockAndUpdate(new net.minecraft.core.BlockPos(2, y, 2), com.thepearlking.stevespace.registry.ModBlocks.RADIO_RACK.get().defaultBlockState());
                server.getPlayerList().getPlayers().get(0).teleportTo(level, 0.5, y, 0.5, java.util.Set.of(), 0, 10);
            });
        }, 60));
        scenes.add(new Scene("radio_screen_open", mc -> {
            ClientTest.holdScreen = true;
            com.thepearlking.stevespace.network.SpaceNetwork.sendToServer(new com.thepearlking.stevespace.network.RadioC2S(""));
        }, 30));
        scenes.add(new Scene("radio_screen_sent", mc -> {
            com.thepearlking.stevespace.network.SpaceNetwork.sendToServer(new com.thepearlking.stevespace.network.RadioC2S("Base One to Mission Control, radio check."));
        }, 60));
        // A flight display on the wall of a base.
        scenes.add(new Scene("flight_display", mc -> {
            mc.setScreen(null);
            ClientTest.holdScreen = false;
            int y = ClientTest.dayside[0];
            command(mc, "execute in stevespace:moon run setblock 0 " + (y + 1) + " -2 stevespace:flight_display[facing=south]");
            command(mc, "execute in stevespace:moon run setblock 0 " + y + " -2 stevespace:hull_grey");
            command(mc, "execute in stevespace:moon run tp @a 0.5 " + y + " 0.5 180 10");
            time(mc, 6000);
        }, 60));
        // A small base on the Moon: sealed hull room, oxygen generator, radio, display; no helmet needed.
        scenes.add(new Scene("moon_base", mc -> {
            ClientTest.holdScreen = false;
            mc.setScreen(null);
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                var level = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(StevesSpace.MODID, "moon")));
                level.getChunk(1, 1);
                int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, 24, 24) + 1;
                var o = new net.minecraft.core.BlockPos(20, y, 20);
                var hull = com.thepearlking.stevespace.registry.ModBlocks.HULL_WHITE.get().defaultBlockState();
                var window = com.thepearlking.stevespace.registry.ModBlocks.SPACECRAFT_WINDOW.get().defaultBlockState();
                for (int x = 0; x <= 8; x++) {
                    for (int yy = 0; yy <= 4; yy++) {
                        for (int z = 0; z <= 8; z++) {
                            boolean shell = x == 0 || x == 8 || yy == 0 || yy == 4 || z == 0 || z == 8;
                            boolean glass = shell && z == 0 && yy == 2 && x >= 2 && x <= 6;
                            level.setBlockAndUpdate(o.offset(x, yy, z), glass ? window : shell ? hull
                                    : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                        }
                    }
                }
                level.setBlockAndUpdate(o.offset(1, 1, 7), com.thepearlking.stevespace.registry.ModBlocks.OXYGEN_GENERATOR.get().defaultBlockState()
                        .setValue(com.thepearlking.stevespace.block.FacingBlock.FACING, net.minecraft.core.Direction.NORTH));
                level.setBlockAndUpdate(o.offset(3, 1, 7), com.thepearlking.stevespace.registry.ModBlocks.RADIO_RACK.get().defaultBlockState()
                        .setValue(com.thepearlking.stevespace.block.FacingBlock.FACING, net.minecraft.core.Direction.NORTH));
                level.setBlockAndUpdate(o.offset(4, 2, 7), com.thepearlking.stevespace.registry.ModBlocks.FLIGHT_DISPLAY.get().defaultBlockState()
                        .setValue(com.thepearlking.stevespace.block.FacingBlock.FACING, net.minecraft.core.Direction.NORTH));
                level.setBlockAndUpdate(o.offset(5, 1, 7), com.thepearlking.stevespace.registry.ModBlocks.MISSION_CONTROL.get().defaultBlockState()
                        .setValue(com.thepearlking.stevespace.block.FacingBlock.FACING, net.minecraft.core.Direction.NORTH));
                // Power: a solar array on the roof, its controller, a battery bank and an RTG.
                for (int x = 0; x <= 8; x++) {
                    for (int z = 0; z <= 8; z++) {
                        level.setBlockAndUpdate(o.offset(x, 5, z), com.thepearlking.stevespace.registry.ModBlocks.SOLAR_PANEL.get().defaultBlockState());
                    }
                }
                level.setBlockAndUpdate(o.offset(7, 1, 7), com.thepearlking.stevespace.registry.ModBlocks.SOLAR_CONTROLLER.get().defaultBlockState()
                        .setValue(com.thepearlking.stevespace.block.FacingBlock.FACING, net.minecraft.core.Direction.NORTH));
                level.setBlockAndUpdate(o.offset(7, 2, 7), com.thepearlking.stevespace.registry.ModBlocks.BATTERY.get().defaultBlockState()
                        .setValue(com.thepearlking.stevespace.block.FacingBlock.FACING, net.minecraft.core.Direction.NORTH));
                level.setBlockAndUpdate(o.offset(7, 1, 5), com.thepearlking.stevespace.registry.ModBlocks.RTG.get().defaultBlockState());
                level.setBlockAndUpdate(o.offset(2, 1, 7), com.thepearlking.stevespace.registry.ModBlocks.MAGNETOMETER.get().defaultBlockState()
                        .setValue(com.thepearlking.stevespace.block.FacingBlock.FACING, net.minecraft.core.Direction.NORTH));
                level.setBlockAndUpdate(o.offset(2, 2, 7), com.thepearlking.stevespace.registry.ModBlocks.PLASMA_RECEIVER.get().defaultBlockState()
                        .setValue(com.thepearlking.stevespace.block.FacingBlock.FACING, net.minecraft.core.Direction.NORTH)
                        .setValue(com.thepearlking.stevespace.block.ScienceBlocks.PlasmaReceiver.LISTENING, true));
                level.setBlockAndUpdate(o.offset(6, 1, 7), com.thepearlking.stevespace.registry.ModBlocks.CREATIVE_POWER.get().defaultBlockState()
                        .setValue(com.thepearlking.stevespace.block.FacingBlock.FACING, net.minecraft.core.Direction.NORTH));
                var player = server.getPlayerList().getPlayers().get(0);
                player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                player.getInventory().armor.clear();
                player.teleportTo(level, o.getX() + 4.5, o.getY() + 1, o.getZ() + 2.5, java.util.Set.of(), 0, 15);
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                        new net.minecraft.world.item.ItemStack(com.thepearlking.stevespace.registry.ModItems.RADIATION_COUNTER.get()));
            });
            time(mc, 6000);
        }, 100));
        // A flag planted outside the base, dyed blue.
        scenes.add(new Scene("moon_flag", mc -> {
            ClientTest.holdScreen = false;
            mc.setScreen(null);
            command(mc, "gamemode spectator @a");
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                var level = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(StevesSpace.MODID, "moon")));
                int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, 14, 14);
                level.setBlockAndUpdate(new net.minecraft.core.BlockPos(14, y, 14), com.thepearlking.stevespace.registry.ModBlocks.FLAG.get()
                        .defaultBlockState().setValue(com.thepearlking.stevespace.block.FlagBlock.COLOR, net.minecraft.world.item.DyeColor.BLUE));
                server.getPlayerList().getPlayers().get(0).teleportTo(level, 14.5, y + 1, 18.5, java.util.Set.of(), 180, 10);
            });
            time(mc, 6000);
        }, 60));
        scenes.add(new Scene("moon_base_window", mc -> look(mc, 180, 5), 40));
        scenes.add(new Scene("moon_base_power", mc -> look(mc, -30, 20), 40));
        scenes.add(new Scene("moon_base_roof", mc -> {
            command(mc, "gamemode spectator @a");
            command(mc, "execute in stevespace:moon run tp @a ~-6 ~9 ~-6 -45 35");
        }, 60));
        // A dust storm on Mars.
        scenes.add(new Scene("mars_storm", mc -> {
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                double t = ServerUniverse.time(server);
                double phase = ((t % 2400) + 2400) % 2400;
                ServerUniverse.advance(server, (2400 - phase) + 300); // the middle of the next storm
                toDaylight(server, "mars", 0, 0);
                double t2 = ServerUniverse.time(server);
                double p2 = ((t2 % 2400) + 2400) % 2400;
                ServerUniverse.advance(server, p2 < 300 ? 300 - p2 : (2400 - p2) + 300);
            });
            command(mc, "execute in stevespace:mars run tp @a 0.5 150 0.5 90 5");
            command(mc, "gamemode creative @a");
        }, 160));
        // A tour of every world's signature places, found from the terrain itself, by day.
        Object[][] tour = {
                {"mercury_caloris", "mercury", (java.util.function.Predicate<com.thepearlking.stevespace.world.planet.TerrainSample>)
                        t -> t.biome == com.thepearlking.stevespace.world.planet.worlds.MercuryTerrain.CALORIS && t.height > 108, 14},
                {"mercury_hollows", "mercury", (java.util.function.Predicate<com.thepearlking.stevespace.world.planet.TerrainSample>) t -> t.river > 0.8, 8},
                {"io_lava", "io", (java.util.function.Predicate<com.thepearlking.stevespace.world.planet.TerrainSample>) t -> t.river > 0.97, 14},
                {"io_vent", "io", (java.util.function.Predicate<com.thepearlking.stevespace.world.planet.TerrainSample>) t -> t.under > 0.5 && t.river < 0.5, 12},
                {"io_fissure", "io", (java.util.function.Predicate<com.thepearlking.stevespace.world.planet.TerrainSample>) t -> t.under > 0.5 && t.river > 0.9, 14},
                {"io_river", "io", (java.util.function.Predicate<com.thepearlking.stevespace.world.planet.TerrainSample>)
                        t -> t.river > 0.9 && t.biome != com.thepearlking.stevespace.world.planet.worlds.IoTerrain.PATERA, 12},
                {"io_mountain", "io", (java.util.function.Predicate<com.thepearlking.stevespace.world.planet.TerrainSample>) t -> t.height > 150, 30},
                {"europa_lineae", "europa", (java.util.function.Predicate<com.thepearlking.stevespace.world.planet.TerrainSample>) t -> t.humidity > 0.9, 8},
                {"europa_chaos", "europa", (java.util.function.Predicate<com.thepearlking.stevespace.world.planet.TerrainSample>) t -> t.temperature > 0.9, 10},
                {"ganymede_grooves", "ganymede", (java.util.function.Predicate<com.thepearlking.stevespace.world.planet.TerrainSample>)
                        t -> t.temperature > 0.95 && t.humidity < 0.1, 10},
                {"callisto_knobs", "callisto", (java.util.function.Predicate<com.thepearlking.stevespace.world.planet.TerrainSample>)
                        t -> t.biome == com.thepearlking.stevespace.world.planet.worlds.CallistoTerrain.KNOBS && t.temperature > 0.6, 10},
                {"titan_shore", "titan", (java.util.function.Predicate<com.thepearlking.stevespace.world.planet.TerrainSample>)
                        t -> t.height < 80, 8},
                {"titan_dunes", "titan", (java.util.function.Predicate<com.thepearlking.stevespace.world.planet.TerrainSample>) t -> t.temperature > 0.9, 10},
                {"enceladus_stripes", "enceladus", (java.util.function.Predicate<com.thepearlking.stevespace.world.planet.TerrainSample>) t -> t.river > 0.95, 12},
                {"triton_streaks", "triton", (java.util.function.Predicate<com.thepearlking.stevespace.world.planet.TerrainSample>) t -> t.humidity > 0.7, 10},
                {"triton_cantaloupe", "triton", (java.util.function.Predicate<com.thepearlking.stevespace.world.planet.TerrainSample>) t -> t.seaIceDepth > 0.9, 12},
                {"pluto_sputnik", "pluto", (java.util.function.Predicate<com.thepearlking.stevespace.world.planet.TerrainSample>)
                        t -> t.temperature > 0.95 && t.under > 0.8, 10},
                {"pluto_mountains", "pluto", (java.util.function.Predicate<com.thepearlking.stevespace.world.planet.TerrainSample>)
                        t -> t.biome == com.thepearlking.stevespace.world.planet.worlds.PlutoTerrain.MOUNTAINS && t.height > 120, 20},
                {"pluto_blades", "pluto", (java.util.function.Predicate<com.thepearlking.stevespace.world.planet.TerrainSample>) t -> t.river > 0.9, 40},
                {"pluto_cthulhu", "pluto", (java.util.function.Predicate<com.thepearlking.stevespace.world.planet.TerrainSample>) t -> t.humidity > 0.95, 10},
                {"charon_chasm", "charon", (java.util.function.Predicate<com.thepearlking.stevespace.world.planet.TerrainSample>) t -> t.river > 0.95, 30},
                {"charon_mordor", "charon", (java.util.function.Predicate<com.thepearlking.stevespace.world.planet.TerrainSample>) t -> t.humidity > 0.9, 10},
                {"mars_lakebed", "mars", (java.util.function.Predicate<com.thepearlking.stevespace.world.planet.TerrainSample>) t -> t.seaIceDepth > 0.7, 10},
                {"moon_floor", "moon", (java.util.function.Predicate<com.thepearlking.stevespace.world.planet.TerrainSample>) t -> t.river > 0.8 && t.humidity > 0.6, 12},
        };
        for (Object[] stop : tour) {
            String name = (String) stop[0];
            String body = (String) stop[1];
            @SuppressWarnings("unchecked")
            var wanted = (java.util.function.Predicate<com.thepearlking.stevespace.world.planet.TerrainSample>) stop[2];
            int rise = (Integer) stop[3];
            scenes.add(new Scene("tour_" + name, mc -> {
                command(mc, "gamemode spectator @a");
                IntegratedServer server = mc.getSingleplayerServer();
                server.execute(() -> {
                    ServerUniverse.setWarp(server, 1);
                    double[] spot = findSpot(body, wanted);
                    if (spot == null) {
                        StevesSpace.LOGGER.warn("Tour: no {} found", name);
                        return;
                    }
                    toDaylight(server, body, spot[0], spot[1]);
                    var level = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(StevesSpace.MODID, body)));
                    int x = (int) spot[0] - 22;
                    int z = (int) spot[1];
                    level.getChunk(x >> 4, z >> 4);
                    int y = Math.max(level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z),
                            level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, (int) spot[0], z));
                    var player = server.getPlayerList().getPlayers().get(0);
                    player.teleportTo(level, x + 0.5, y + rise, z + 0.5, java.util.Set.of(), -90, 28);
                    StevesSpace.LOGGER.info("Tour {} at {} {} {}", name, x, y, z);
                });
            }, 200));
        }
        // Inside Jupiter: a ship riding down through the layers, at noon, the pilot aboard.
        Object[][] dive = {{"giant_haze", 4300.0, 1.0, 25f}, {"giant_top", 3000.0, 1.0, 10f}, {"giant_between", 2000.0, 0.4, 12f},
                {"giant_between_up", 2000.0, 0.4, -25f}, {"giant_middle", 1100.0, 0.2, 10f}, {"giant_gap", 400.0, 0.1, 15f},
                {"giant_water", -400.0, 0.05, 5f}, {"giant_below_water", -1000.0, 0.05, 12f}, {"giant_flash", 300.0, 0.05, 25f}, {"giant_deep", -2100.0, 0.05, 10f}};
        for (Object[] d : dive) {
            double height = (Double) d[1];
            double fraction = (Double) d[2];
            float pitch = (Float) d[3];
            scenes.add(new Scene((String) d[0], mc -> {
                giantDive(mc, System.getenv().getOrDefault("STEVESPACE_GIANT", "jupiter"), height, fraction);
                look(mc, 0, pitch);
                if (((String) d[0]).equals("giant_flash")) {
                    com.thepearlking.stevespace.client.sky.GiantClouds.flashIn(2.5);
                }
            }, 90));
        }
        // The map, opened while flying inside Jupiter.
        scenes.add(new Scene("giantmap_open", mc -> {
            ClientTest.holdScreen = true;
            mc.setScreen(new com.thepearlking.stevespace.client.gui.MapScreen());
        }, 60));
        scenes.add(new Scene("giantmap_done", mc -> {
            ClientTest.holdScreen = false;
            mc.setScreen(null);
        }, 10));
        // An Io lava vent mid-eruption: placed where its own rhythm has it erupting a few seconds on.
        scenes.add(new Scene("io_vent_erupt", mc -> {
            command(mc, "gamemode spectator @a");
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                ServerUniverse.setWarp(server, 1);
                double[] spot = findSpot("io", t -> t.river < 0.5 && t.height > 90 && t.height < 100);
                toDaylight(server, "io", spot[0], spot[1]);
                var level = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(StevesSpace.MODID, "io")));
                int x0 = (int) spot[0];
                int z0 = (int) spot[1];
                level.getChunk(x0 >> 4, z0 >> 4);
                int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x0, z0);
                long soon = level.getGameTime() + 165;
                for (int dx = 0; dx < 400; dx++) {
                    var pos = new net.minecraft.core.BlockPos(x0 + dx, y - 1, z0);
                    long h = pos.asLong() * 0x9E3779B97F4A7C15L;
                    h ^= h >>> 29;
                    int period = 360 + (int) ((h >>> 8) & 255);
                    long phase = Math.floorMod(soon + (h >>> 20), period);
                    if (phase < 10) {
                        level.setBlockAndUpdate(pos, com.thepearlking.stevespace.registry.WorldBlocks.state("lava_vent"));
                        for (int up = 0; up < 20; up++) {
                            level.setBlockAndUpdate(pos.above(1 + up), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                        }
                        var player = server.getPlayerList().getPlayers().get(0);
                        player.teleportTo(level, pos.getX() - 9.5, pos.getY() + 4, pos.getZ() + 0.5, java.util.Set.of(), -90, 5);
                        StevesSpace.LOGGER.info("Vent at {}", pos);
                        return;
                    }
                }
            });
        }, 200));
        // Europa's ocean, under the ice: down among the vents.
        scenes.add(new Scene("tour_europa_ocean", mc -> {
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                double[] spot = findSpot("europa", t -> t.river > 0.8);
                var level = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(StevesSpace.MODID, "europa")));
                var s = new com.thepearlking.stevespace.world.planet.TerrainSample();
                com.thepearlking.stevespace.world.gen.PlanetBodies.terrain("europa").sample(spot[0], spot[1], s);
                var player = server.getPlayerList().getPlayers().get(0);
                player.teleportTo(level, spot[0] - 12, s.floor + 8, spot[1], java.util.Set.of(), -90, 20);
            });
            command(mc, "effect give @a minecraft:night_vision 30 0 true");
        }, 200));
        // The ground past the loaded chunks, in daylight: at eye height and a little way up.
        for (String body : new String[]{"moon", "mars", "europa", "titan", "callisto", "venus"}) {
            scenes.add(new Scene("far_" + body, mc -> {
                command(mc, "gamemode spectator @a");
                IntegratedServer server = mc.getSingleplayerServer();
                server.execute(() -> {
                    ServerUniverse.setWarp(server, 1);
                    var level = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(StevesSpace.MODID, body)));
                    int x = (int) daysideX(server, body);
                    level.getChunk(x >> 4, 0);
                    int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, 0);
                    var player = server.getPlayerList().getPlayers().get(0);
                    // Facing away from the Sun (west of the subsolar point it's east... look north-east).
                    player.teleportTo(level, x + 0.5, y + 4, 0.5, java.util.Set.of(), -135, 3);
                    ClientTest.dayside[0] = x;
                });
            }, 220));
            scenes.add(new Scene("far_" + body + "_high", mc -> command(mc, "execute in stevespace:" + body + " run tp @a "
                    + ClientTest.dayside[0] + " 420 0 -135 12"), 160));
        }
        // A rocket left standing on the Moon stays in sight once you've walked out of its chunks.
        scenes.add(new Scene("farship_build", mc -> {
            command(mc, "gamemode creative @a");
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                ServerUniverse.setWarp(server, 1);
                var level = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(StevesSpace.MODID, "moon")));
                int x = (int) daysideX(server, "moon");
                level.getChunk(x >> 4, 0);
                int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, 0);
                for (int dy = 0; dy < 16; dy++) {
                    for (int dx = -2; dx <= 2; dx++) {
                        for (int dz = -2; dz <= 2; dz++) {
                            boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                            if (dy < 12 ? edge : Math.abs(dx) + Math.abs(dz) <= 14 - dy) {
                                level.setBlockAndUpdate(new net.minecraft.core.BlockPos(x + dx, y + dy, dz), dy % 4 == 3
                                        ? com.thepearlking.stevespace.registry.ModBlocks.HULL_DARK.get().defaultBlockState()
                                        : com.thepearlking.stevespace.registry.ModBlocks.HULL_WHITE.get().defaultBlockState());
                            }
                        }
                    }
                }
                ClientTest.dayside[0] = x;
                ClientTest.dayside[1] = y;
                var player = server.getPlayerList().getPlayers().get(0);
                player.teleportTo(level, x + 8.5, y + 2, 0.5, java.util.Set.of(), 90, 0);
            });
        }, 160));
        scenes.add(new Scene("farship_away", mc -> {
            command(mc, "gamemode spectator @a");
            command(mc, "execute in stevespace:moon run tp @a " + (ClientTest.dayside[0] + 450) + " " + (ClientTest.dayside[1] + 80) + " 0.5 90 9");
        }, 260));
        scenes.add(new Scene("farship_zoom", mc -> zoom(mc, 30), 40));
        scenes.add(new Scene("farship_unzoom", mc -> zoom(mc, 70), 5));
        // Earth's ground past the chunks, from its real heights: hills and mountains in 3D.
        scenes.add(new Scene("far_earth", mc -> {
            command(mc, "gamemode spectator @a");
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                ServerUniverse.setWarp(server, 1);
                var level = server.overworld();
                var gen = level.getChunkSource().getGenerator();
                var rs = level.getChunkSource().randomState();
                int x = (int) daysideX(server, "earth");
                // Somewhere hilly on the day side.
                int bestZ = 0;
                int best = -1;
                for (int z = -5000; z <= 5000; z += 125) {
                    for (int dx = -2000; dx <= 2000; dx += 500) {
                        int h = gen.getBaseHeight(x + dx, z, net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG, level, rs);
                        if (h > best && h < 200) {
                            best = h;
                            bestZ = z;
                            ClientTest.dayside[0] = x + dx;
                        }
                    }
                }
                ClientTest.dayside[1] = bestZ;
                StevesSpace.LOGGER.info("[ClientTest] earth far spot {} {} height {}", ClientTest.dayside[0], bestZ, best);
                var player = server.getPlayerList().getPlayers().get(0);
                player.teleportTo(level, ClientTest.dayside[0] + 0.5, best + 30, bestZ + 0.5, java.util.Set.of(), -135, 5);
            });
        }, 300));
        scenes.add(new Scene("far_earth_high", mc -> command(mc, "execute in minecraft:overworld run tp @a "
                + ClientTest.dayside[0] + " 600 " + ClientTest.dayside[1] + " -135 15"), 300));
        scenes.add(new Scene("far_earth_higher", mc -> command(mc, "execute in minecraft:overworld run tp @a "
                + ClientTest.dayside[0] + " 1800 " + ClientTest.dayside[1] + " -135 25"), 300));
        // The space suit on the Moon: survival, oxygen gauge above the hotbar; then without it.
        scenes.add(new Scene("suit_moon", mc -> {
            command(mc, "gamemode survival @a");
            command(mc, "execute in stevespace:moon run tp @a 0.5 140 0.5 30 10");
            command(mc, "item replace entity @a armor.head with stevespace:space_suit_helmet");
            command(mc, "item replace entity @a armor.chest with stevespace:space_suit");
            command(mc, "item replace entity @a armor.legs with stevespace:space_suit_trousers");
            command(mc, "item replace entity @a armor.feet with stevespace:space_suit_boots");
            command(mc, "effect give @a minecraft:slow_falling 20 0 true");
            time(mc, 4500);
        }, 160));
        scenes.add(new Scene("suit_moon_front", mc -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT), 30));
        scenes.add(new Scene("suit_moon_none", mc -> {
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            command(mc, "item replace entity @a armor.head with minecraft:air");
            command(mc, "item replace entity @a armor.chest with minecraft:air");
        }, 100));
        scenes.add(new Scene("suit_moon_done", mc -> command(mc, "gamemode creative @a"), 5));
        // Windows: a framed window wall and panes, on a terrace in daylight.
        scenes.add(new Scene("windows", mc -> {
            command(mc, "gamemode spectator @a");
            command(mc, "execute in minecraft:overworld run tp @a 0.5 200 -4566.5 180 5");
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                var level = server.overworld();
                var player = server.getPlayerList().getPlayers().get(0);
                var at = player.blockPosition();
                level.getChunk(at.getX() >> 4, (at.getZ() - 6) >> 4);
                for (int dx = -3; dx <= 3; dx++) {
                    for (int dy = 0; dy < 4; dy++) {
                        var pos = new net.minecraft.core.BlockPos(at.getX() + dx, at.getY() + dy, at.getZ() - 6);
                        boolean edge = Math.abs(dx) == 3 || dy == 0 || dy == 3;
                        level.setBlockAndUpdate(pos, edge ? com.thepearlking.stevespace.registry.ModBlocks.HULL_WHITE.get().defaultBlockState()
                                : dx < 2 ? com.thepearlking.stevespace.registry.ModBlocks.SPACECRAFT_WINDOW.get().defaultBlockState()
                                : com.thepearlking.stevespace.registry.ModBlocks.WINDOW_PANE.get().defaultBlockState());
                    }
                }
                // A lone window beside the wall, and every window joined up with its neighbours.
                level.setBlockAndUpdate(new net.minecraft.core.BlockPos(at.getX() - 5, at.getY() + 1, at.getZ() - 6),
                        com.thepearlking.stevespace.registry.ModBlocks.SPACECRAFT_WINDOW.get().defaultBlockState());
                for (int dx = -5; dx <= 3; dx++) {
                    for (int dy = 0; dy < 4; dy++) {
                        var pos = new net.minecraft.core.BlockPos(at.getX() + dx, at.getY() + dy, at.getZ() - 6);
                        var state = level.getBlockState(pos);
                        level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Block.updateFromNeighbourShapes(state, level, pos));
                    }
                }
            });
            time(mc, 4500);
        }, 60));
        // Power machines: a coal generator piped to a furnace and crusher, batteries, a double-sided solar panel.
        scenes.add(new Scene("power_blocks", mc -> {
            command(mc, "gamemode spectator @a");
            command(mc, "execute in minecraft:overworld run tp @a 20.5 201 -4566.5 180 25");
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                var level = server.overworld();
                var at = new net.minecraft.core.BlockPos(20, 200, -4571);
                level.getChunk(at.getX() >> 4, at.getZ() >> 4);
                for (int dx = -4; dx <= 4; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        level.setBlockAndUpdate(at.offset(dx, -1, dz), net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState());
                        for (int dy = 0; dy < 3; dy++) {
                            level.setBlockAndUpdate(at.offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                        }
                    }
                }
                var south = net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING;
                java.util.function.BiConsumer<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState> put = (p, st) ->
                        level.setBlockAndUpdate(p, st.hasProperty(south) ? st.setValue(south, net.minecraft.core.Direction.SOUTH) : st);
                put.accept(at.offset(-4, 0, 0), com.thepearlking.stevespace.registry.ModBlocks.COAL_GENERATOR.get().defaultBlockState()
                        .setValue(com.thepearlking.stevespace.energy.Machines.LIT, true));
                for (int i = -3; i <= 1; i++) {
                    put.accept(at.offset(i, 0, 0), com.thepearlking.stevespace.registry.ModBlocks.POWER_PIPE.get().defaultBlockState());
                }
                put.accept(at.offset(2, 0, 0), com.thepearlking.stevespace.registry.ModBlocks.ELECTRIC_FURNACE.get().defaultBlockState()
                        .setValue(com.thepearlking.stevespace.energy.Machines.LIT, true));
                put.accept(at.offset(3, 0, 0), com.thepearlking.stevespace.registry.ModBlocks.CRUSHER.get().defaultBlockState());
                put.accept(at.offset(-1, 0, 1), com.thepearlking.stevespace.registry.ModBlocks.POWER_PIPE.get().defaultBlockState());
                put.accept(at.offset(-1, 0, 2), com.thepearlking.stevespace.registry.ModBlocks.SMALL_BATTERY.get().defaultBlockState());
                put.accept(at.offset(-2, 0, 2), com.thepearlking.stevespace.registry.ModBlocks.BATTERY.get().defaultBlockState());
                put.accept(at.offset(-3, 0, 2), com.thepearlking.stevespace.registry.ModBlocks.LARGE_BATTERY.get().defaultBlockState());
                put.accept(at.offset(-1, 1, 0), com.thepearlking.stevespace.registry.ModBlocks.POWER_PIPE.get().defaultBlockState());
                put.accept(at.offset(1, 2, 1), com.thepearlking.stevespace.registry.ModBlocks.SOLAR_PANEL.get().defaultBlockState());
                put.accept(at.offset(2, 2, 1), com.thepearlking.stevespace.registry.ModBlocks.SOLAR_PANEL.get().defaultBlockState());
                for (int dx = -4; dx <= 4; dx++) {
                    for (int dy = 0; dy < 3; dy++) {
                        for (int dz = -2; dz <= 2; dz++) {
                            var pos = at.offset(dx, dy, dz);
                            level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Block.updateFromNeighbourShapes(level.getBlockState(pos), level, pos));
                        }
                    }
                }
            });
            time(mc, 4500);
        }, 60));
        // The solar panel's underside has cells too.
        scenes.add(new Scene("power_blocks_below", mc -> command(mc, "tp @a 21.5 200.2 -4568.5 200 -60"), 30));
        // The coal generator's screen.
        scenes.add(new Scene("power_screen", mc -> {
            holdScreen = true;
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                var player = server.getPlayerList().getPlayers().get(0);
                var pos = new net.minecraft.core.BlockPos(16, 200, -4571);
                if (server.overworld().getBlockEntity(pos) instanceof com.thepearlking.stevespace.energy.Machines.MachineEntity m) {
                    m.items().insertItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COAL, 12), false);
                    player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                    player.openMenu(m, buf -> {
                        buf.writeBlockPos(pos);
                        buf.writeEnum(m.kind);
                    });
                }
            });
        }, 60));
        scenes.add(new Scene("power_screen_done", mc -> {
            holdScreen = false;
            if (mc.player != null) {
                mc.player.closeContainer();
            }
        }, 5));
        // Inside a parked ship in orbit: get up from the seat, look around, see Earth below.
        scenes.add(new Scene("parked_ship", mc -> {
            command(mc, "gamemode creative @a");
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                var space = server.getLevel(com.thepearlking.stevespace.world.SpaceDimension.KEY);
                var earth = com.thepearlking.stevespace.universe.ServerUniverse.system().earth();
                double r = earth.radius() + 3100;
                var state = new com.thepearlking.stevespace.universe.StateVector(
                        new com.thepearlking.stevespace.universe.DVec3(0, 0, r),
                        new com.thepearlking.stevespace.universe.DVec3(Math.sqrt(earth.mu() / r), 0, 0));
                var blocks = new java.util.HashMap<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState>();
                var hull = com.thepearlking.stevespace.registry.ModBlocks.HULL_WHITE.get().defaultBlockState();
                var window = com.thepearlking.stevespace.registry.ModBlocks.SPACECRAFT_WINDOW.get().defaultBlockState();
                // A 5x5 cabin: floor, walls with windows, roof; seat in the middle, engine underneath.
                for (int x = -2; x <= 2; x++) {
                    for (int z = -2; z <= 2; z++) {
                        blocks.put(new net.minecraft.core.BlockPos(x, 0, z), hull);
                        blocks.put(new net.minecraft.core.BlockPos(x, 4, z), hull);
                        for (int y = 1; y <= 3; y++) {
                            if (Math.abs(x) == 2 || Math.abs(z) == 2) {
                                blocks.put(new net.minecraft.core.BlockPos(x, y, z), y == 2 && (x == 0 || z == 0) ? window : hull);
                            }
                        }
                    }
                }
                blocks.put(new net.minecraft.core.BlockPos(0, 1, 0), com.thepearlking.stevespace.registry.ModBlocks.PILOT_SEAT.get().defaultBlockState());
                blocks.put(new net.minecraft.core.BlockPos(1, 1, -1), com.thepearlking.stevespace.registry.ModBlocks.FLIGHT_COMPUTER.get().defaultBlockState());
                blocks.put(new net.minecraft.core.BlockPos(-1, 1, -1), com.thepearlking.stevespace.registry.ModBlocks.RADIO_RACK.get().defaultBlockState());
                blocks.put(new net.minecraft.core.BlockPos(0, -1, 0), com.thepearlking.stevespace.registry.ModBlocks.ROCKET_ENGINE.get().defaultBlockState());
                var ship = com.thepearlking.stevespace.vessel.VesselEntity.spawnInOrbit(space,
                        new com.thepearlking.stevespace.vessel.VesselStructure(blocks, new java.util.HashMap<>()), earth, state, new org.joml.Quaterniond());
                ship.setCustomName(net.minecraft.network.chat.Component.literal("Test capsule"));
                var player = server.getPlayerList().getPlayers().get(0);
                player.teleportTo(space, ship.getX(), ship.getY() + 1, ship.getZ(), java.util.Set.of(), 0, 0);
                player.startRiding(ship, true);
                ClientTest.parkedShip = ship;
            });
        }, 120));
        scenes.add(new Scene("parked_ship_inside", mc -> {
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                var player = server.getPlayerList().getPlayers().get(0);
                // The ship is blocks now: getting up is just getting out of the seat block.
                if (player.getVehicle() instanceof com.thepearlking.stevespace.block.SeatEntity seat) {
                    seat.requestDismount(player);
                } else if (ClientTest.parkedShip != null) {
                    ClientTest.parkedShip.requestDismount(player);
                }
            });
            look(mc, 90, 10);
        }, 80));
        scenes.add(new Scene("parked_ship_window", mc -> look(mc, 0, 25), 40));
        // Back in the pilot seat, turning the ship: the cabin stays still and the sky swings round.
        scenes.add(new Scene("blockship_turn", mc -> {
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                var player = server.getPlayerList().getPlayers().get(0);
                var ship = ClientTest.parkedShip;
                if (ship == null || ship.isRemoved() || !ship.materialized()) {
                    StevesSpace.LOGGER.info("[ClientTest] blockship: no materialized ship ({})", ship);
                    return;
                }
                var seatPos = ship.blockOrigin().offset(ship.stats().seats().get(0).pos());
                var seat = com.thepearlking.stevespace.block.SeatEntity.create((net.minecraft.server.level.ServerLevel) ship.level(), seatPos);
                ship.level().addFreshEntity(seat);
                player.startRiding(seat, true);
                com.thepearlking.stevespace.vessel.VesselServer.onControls(player, new com.thepearlking.stevespace.vessel.VesselControls(0, 0, 0, 1));
            });
            look(mc, 0, 25);
        }, 50));
        // Sitting still in orbit: the sky must hold perfectly still (logged every frame).
        // Turning steadily at normal time speed (the yaw key held): the sky must turn evenly.
        scenes.add(new Scene("blockship_still", mc -> {
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> ServerUniverse.setWarp(server, 1));
            net.minecraft.client.KeyMapping.set(com.thepearlking.stevespace.client.FlightKeys.YAW_LEFT.getKey(), true);
            sampleSky = true;
            look(mc, 0, 25);
        }, 160));
        scenes.add(new Scene("blockship_still_done", mc -> {
            sampleSky = false;
            net.minecraft.client.KeyMapping.set(com.thepearlking.stevespace.client.FlightKeys.YAW_LEFT.getKey(), false);
        }, 60));
        // Then hands off: the ship holds still and so must the sky (it only drifts with the orbit).
        scenes.add(new Scene("blockship_quiet", mc -> {
            sampleSky = true;
            StevesSpace.LOGGER.info("[ClientTest] sky quiet");
        }, 120));
        scenes.add(new Scene("blockship_quiet_done", mc -> sampleSky = false, 5));
        scenes.add(new Scene("blockship_turned", mc -> {
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> com.thepearlking.stevespace.vessel.VesselServer.onControls(server.getPlayerList().getPlayers().get(0),
                    com.thepearlking.stevespace.vessel.VesselControls.IDLE));
            look(mc, 0, 25);
        }, 20));
        // A telescope on the roof, in orbit: everything not behind Earth is in the list.
        scenes.add(new Scene("blockship_telescope", mc -> {
            IntegratedServer server = mc.getSingleplayerServer();
            var ship = ClientTest.parkedShip;
            if (ship == null || !ship.materialized()) {
                return;
            }
            var scope = ship.blockOrigin().offset(0, 5, 2);
            ClientTest.roofScope = scope; // (the ship's origin moves once the telescope becomes part of it)
            server.execute(() -> {
                var player = server.getPlayerList().getPlayers().get(0);
                if (player.getVehicle() != null) {
                    player.stopRiding();
                }
                ship.level().setBlockAndUpdate(scope, com.thepearlking.stevespace.registry.ModBlocks.TELESCOPE.get().defaultBlockState());
                player.teleportTo(scope.getX() + 0.5, scope.getY(), scope.getZ() - 0.5);
            });
            ClientTest.holdScreen = true;
            mc.setScreen(new com.thepearlking.stevespace.client.telescope.TelescopeScreen(scope, null));
        }, 60));
        scenes.add(new Scene("blockship_telescope_done", mc -> {
            ClientTest.holdScreen = false;
            mc.setScreen(null);
        }, 5));
        // From orbit, the Sun up close: granulation, spots, the corona around it.
        scenes.add(new Scene("blockship_sun_walk", mc -> {
            // Out of the seat and up beside the roof telescope (getting up first, then the move).
            var ship = ClientTest.parkedShip;
            if (ship == null || !ship.materialized()) {
                return;
            }
            var scope = ClientTest.roofScope;
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                var player = server.getPlayerList().getPlayers().get(0);
                if (player.getVehicle() instanceof com.thepearlking.stevespace.block.SeatEntity seat) {
                    seat.requestDismount(player); // standing up, as a player does
                }
                player.teleportTo(scope.getX() + 0.5, scope.getY(), scope.getZ() - 0.5);
            });
        }, 20));
        scenes.add(new Scene("blockship_sun", mc -> {
            var ship = ClientTest.parkedShip;
            if (ship != null && ship.materialized()) {
                // The Sun if it's in view from this side of the orbit, else whatever is.
                String target = "sun";
                for (String id : new String[]{"sun", "moon", "jupiter", "venus", "saturn", "mars"}) {
                    var e = com.thepearlking.stevespace.client.telescope.SkyCatalog.of(mc.player.getEyePosition(), id);
                    if (e != null && e.blockedBy() == null) {
                        target = id;
                        break;
                    }
                }
                StevesSpace.LOGGER.info("[ClientTest] roof telescope on {}", target);
                com.thepearlking.stevespace.client.TelescopeView.observe(ClientTest.roofScope, target);
            }
        }, 80));
        scenes.add(new Scene("blockship_sun_done", mc -> com.thepearlking.stevespace.client.TelescopeView.stop(), 10));
        // The map: warp bar at the top, a picked world's card, and the asteroid belt from afar.
        scenes.add(new Scene("map_pick", mc -> {
            ClientTest.holdScreen = true;
            mc.setScreen(com.thepearlking.stevespace.client.gui.MapScreen.forDestination());
        }, 40));
        scenes.add(new Scene("map_pick_out", mc -> {
            if (mc.screen instanceof com.thepearlking.stevespace.client.gui.MapScreen map) {
                for (int i = 0; i < 60; i++) {
                    map.mouseScrolled(map.width / 2.0, map.height / 2.0, 0, -1);
                }
            }
        }, 40));
        scenes.add(new Scene("map_pick_done", mc -> {
            ClientTest.holdScreen = false;
            mc.setScreen(null);
        }, 5));
        // Flying from the map: navball, SAS, a planned maneuver.
        scenes.add(new Scene("navmap_orbit", mc -> {
            command(mc, "gamemode creative @a");
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                ServerUniverse.setWarp(server, 1);
                var space = server.getLevel(com.thepearlking.stevespace.world.SpaceDimension.KEY);
                var earth = ServerUniverse.system().body("earth");
                double r = earth.radius() + com.thepearlking.stevespace.vessel.VesselTransfer.spaceAltitude(earth) + 700;
                var axis = earth.spinAxis();
                var out = axis.cross(com.thepearlking.stevespace.universe.DVec3.X).normalize();
                double mu = earth.mu() * com.thepearlking.stevespace.config.SpaceConfig.GRAVITY_SCALE.get();
                var state = new com.thepearlking.stevespace.universe.StateVector(out.scale(r), axis.cross(out).normalize().scale(Math.sqrt(mu / r)));
                var blocks = new java.util.HashMap<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState>();
                blocks.put(new net.minecraft.core.BlockPos(0, 0, 0), com.thepearlking.stevespace.registry.ModBlocks.ROCKET_ENGINE.get().defaultBlockState());
                for (int i = 1; i <= 3; i++) {
                    blocks.put(new net.minecraft.core.BlockPos(0, i, 0), com.thepearlking.stevespace.registry.ModBlocks.PROPELLANT_TANK.get().defaultBlockState());
                }
                blocks.put(new net.minecraft.core.BlockPos(0, 4, 0), com.thepearlking.stevespace.registry.ModBlocks.PILOT_SEAT.get().defaultBlockState());
                blocks.put(new net.minecraft.core.BlockPos(0, 5, 0), com.thepearlking.stevespace.registry.ModBlocks.HULL_WHITE.get().defaultBlockState());
                var ship = com.thepearlking.stevespace.vessel.VesselEntity.spawnInOrbit(space,
                        new com.thepearlking.stevespace.vessel.VesselStructure(blocks, new java.util.HashMap<>()), earth, state,
                        new org.joml.Quaterniond().rotateX(1.0));
                ship.fillTanks();
                var player = server.getPlayerList().getPlayers().get(0);
                player.teleportTo(space, ship.getX(), ship.getY() + 1, ship.getZ(), java.util.Set.of(), 0, 0);
                player.startRiding(ship, true);
            });
        }, 120));
        scenes.add(new Scene("navmap_open", mc -> {
            ClientTest.holdScreen = true;
            mc.setScreen(new com.thepearlking.stevespace.client.gui.MapScreen());
        }, 40));
        scenes.add(new Scene("navmap_sas", mc -> com.thepearlking.stevespace.network.SpaceNetwork.sendToServer(
                new com.thepearlking.stevespace.network.SasC2S(com.thepearlking.stevespace.vessel.VesselEntity.Sas.PROGRADE.ordinal())), 100));
        scenes.add(new Scene("navmap_node", mc -> {
            com.thepearlking.stevespace.client.gui.MapScreen.testNode(900, 120, 0, 30);
            mc.setScreen(new com.thepearlking.stevespace.client.gui.MapScreen());
        }, 40));
        scenes.add(new Scene("navmap_done", mc -> {
            ClientTest.holdScreen = false;
            mc.setScreen(null);
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                var player = server.getPlayerList().getPlayers().get(0);
                if (player.getVehicle() instanceof com.thepearlking.stevespace.vessel.VesselEntity v) {
                    v.discard();
                }
            });
        }, 10));
        // First person in a rolled ship: the cabin stays level in view, the world tilts.
        scenes.add(new Scene("seat_view", mc -> {
            command(mc, "gamemode creative @a");
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                ServerUniverse.setWarp(server, 1);
                var space = server.getLevel(com.thepearlking.stevespace.world.SpaceDimension.KEY);
                var earth = ServerUniverse.system().earth();
                double r = earth.radius() + 3200;
                var state = new com.thepearlking.stevespace.universe.StateVector(
                        new com.thepearlking.stevespace.universe.DVec3(0, 0, r), new com.thepearlking.stevespace.universe.DVec3(Math.sqrt(earth.mu() / r), 0, 0));
                var blocks = new java.util.HashMap<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState>();
                var hull = com.thepearlking.stevespace.registry.ModBlocks.HULL_GREY.get().defaultBlockState();
                blocks.put(new net.minecraft.core.BlockPos(0, 0, 0), com.thepearlking.stevespace.registry.ModBlocks.ROCKET_ENGINE.get().defaultBlockState());
                blocks.put(new net.minecraft.core.BlockPos(0, 1, 0), com.thepearlking.stevespace.registry.ModBlocks.PROPELLANT_TANK.get().defaultBlockState());
                blocks.put(new net.minecraft.core.BlockPos(0, 2, 0), com.thepearlking.stevespace.registry.ModBlocks.PILOT_SEAT.get().defaultBlockState());
                // A cabin wall in front of the seat with a window, so "level" can be seen.
                for (int x = -2; x <= 2; x++) {
                    for (int y = 1; y <= 5; y++) {
                        boolean glass = Math.abs(x) <= 1 && y >= 3 && y <= 4;
                        blocks.put(new net.minecraft.core.BlockPos(x, y, -3), glass
                                ? com.thepearlking.stevespace.registry.ModBlocks.SPACECRAFT_WINDOW.get().defaultBlockState() : hull);
                    }
                    blocks.put(new net.minecraft.core.BlockPos(x, 1, -2), hull);
                    blocks.put(new net.minecraft.core.BlockPos(x, 1, -1), hull);
                }
                var ship = com.thepearlking.stevespace.vessel.VesselEntity.spawnInOrbit(space,
                        new com.thepearlking.stevespace.vessel.VesselStructure(blocks, new java.util.HashMap<>()), earth, state,
                        new org.joml.Quaterniond().rotateZ(Math.toRadians(30)));
                ship.fillTanks();
                var player = server.getPlayerList().getPlayers().get(0);
                player.teleportTo(space, ship.getX(), ship.getY() + 1, ship.getZ(), java.util.Set.of(), 180, 0);
                player.startRiding(ship, true);
            });
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
        }, 100));
        scenes.add(new Scene("seat_view_look", mc -> look(mc, 180, 10), 30));
        // Landing on Venus from orbit with the autopilot: above, inside and below the cloud deck.
        scenes.add(new Scene("venus_flight", mc -> {
            command(mc, "gamemode creative @a");
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                ServerUniverse.setWarp(server, 1);
                var space = server.getLevel(com.thepearlking.stevespace.world.SpaceDimension.KEY);
                var venus = ServerUniverse.system().body("venus");
                double r = venus.radius() + 3200;
                var state = new com.thepearlking.stevespace.universe.StateVector(
                        new com.thepearlking.stevespace.universe.DVec3(r, 0, 0), new com.thepearlking.stevespace.universe.DVec3(0, Math.sqrt(venus.mu() / r), 0));
                var blocks = new java.util.HashMap<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState>();
                blocks.put(new net.minecraft.core.BlockPos(0, 0, 0), com.thepearlking.stevespace.registry.ModBlocks.ROCKET_ENGINE.get().defaultBlockState());
                for (int i = 1; i <= 4; i++) {
                    blocks.put(new net.minecraft.core.BlockPos(0, i, 0), com.thepearlking.stevespace.registry.ModBlocks.PROPELLANT_TANK.get().defaultBlockState());
                }
                blocks.put(new net.minecraft.core.BlockPos(0, 5, 0), com.thepearlking.stevespace.registry.ModBlocks.PILOT_SEAT.get().defaultBlockState());
                var ship = com.thepearlking.stevespace.vessel.VesselEntity.spawnInOrbit(space,
                        new com.thepearlking.stevespace.vessel.VesselStructure(blocks, new java.util.HashMap<>()), venus, state, new org.joml.Quaterniond());
                ship.fillTanks();
                var player = server.getPlayerList().getPlayers().get(0);
                player.stopRiding(); // out of the seat-view ship first
                player.teleportTo(space, ship.getX(), ship.getY() + 1, ship.getZ(), java.util.Set.of(), 0, 0);
                player.startRiding(ship, true);
            });
        }, 100));
        // The destination list of the fly-anywhere autopilot, opened from the autopilot panel.
        scenes.add(new Scene("venus_flight_destinations", mc -> {
            ClientTest.holdScreen = true;
            mc.setScreen(new com.thepearlking.stevespace.client.gui.DestinationScreen(new com.thepearlking.stevespace.client.gui.AutopilotScreen()));
        }, 30));
        scenes.add(new Scene("venus_flight_land", mc -> {
            mc.setScreen(null);
            ClientTest.holdScreen = false;
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
            look(mc, 0, 40);
            com.thepearlking.stevespace.network.SpaceNetwork.sendToServer(
                    new com.thepearlking.stevespace.network.AutopilotC2S(com.thepearlking.stevespace.vessel.Autopilot.Program.LAND));
            waitFor(() -> ClientTest.flightAltitude(mc) < 1900, 20 * 400);
        }, 5));
        scenes.add(new Scene("venus_above_deck", mc -> waitFor(() -> ClientTest.flightAltitude(mc) < 1200, 20 * 300), 5));
        scenes.add(new Scene("venus_in_deck", mc -> waitFor(() -> ClientTest.flightAltitude(mc) < 650, 20 * 300), 5));
        scenes.add(new Scene("venus_below_deck", mc -> waitFor(() -> ClientTest.flightAltitude(mc) < 120, 20 * 300), 5));
        scenes.add(new Scene("venus_near_ground", mc -> waitFor(() -> !(mc.player.getVehicle() instanceof com.thepearlking.stevespace.vessel.VesselEntity), 20 * 300), 40));
        scenes.add(new Scene("venus_landed", mc -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON), 60));
        // Stepping out onto Venus: without the heat-shielded suit it burns; with it you're fine.
        scenes.add(new Scene("venus_heat", mc -> {
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                var player = server.getPlayerList().getPlayers().get(0);
                if (player.getVehicle() instanceof com.thepearlking.stevespace.block.SeatEntity seat) {
                    seat.requestDismount(player);
                }
                player.teleportTo(player.getX() + 3, player.getY() + 1, player.getZ());
                player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                player.setHealth(player.getMaxHealth());
            });
            command(mc, "item replace entity @a armor.head with stevespace:space_suit_helmet");
            command(mc, "item replace entity @a armor.chest with stevespace:space_suit");
            look(mc, 30, 10);
        }, 60));
        scenes.add(new Scene("venus_heatsuit", mc -> {
            command(mc, "item replace entity @a armor.chest with stevespace:heat_shielded_space_suit");
            command(mc, "effect give @a minecraft:instant_health 1 5");
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
        }, 80));
        // The same spot by day (Venus turns slowly: half a solar day on).
        scenes.add(new Scene("venus_day", mc -> {
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> ServerUniverse.advance(server, ServerUniverse.solarDaySeconds(ServerUniverse.system().body("venus")) / 2));
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            look(mc, 30, 5);
        }, 80));
        // The map opened on foot: centred on the player, wherever they are.
        scenes.add(new Scene("mapself_earth", mc -> {
            command(mc, "gamemode spectator @a");
            command(mc, "execute in minecraft:overworld run tp @a 1200 140 -2600");
        }, 80));
        scenes.add(new Scene("mapself_earth_open", mc -> {
            ClientTest.holdScreen = true;
            mc.setScreen(new com.thepearlking.stevespace.client.gui.MapScreen());
        }, 30));
        scenes.add(new Scene("mapself_moon", mc -> {
            ClientTest.holdScreen = false;
            mc.setScreen(null);
            command(mc, "execute in stevespace:moon run tp @a -900 140 700");
        }, 100));
        scenes.add(new Scene("mapself_moon_open", mc -> {
            ClientTest.holdScreen = true;
            mc.setScreen(new com.thepearlking.stevespace.client.gui.MapScreen());
        }, 30));
        scenes.add(new Scene("mapself_close", mc -> {
            ClientTest.holdScreen = false;
            mc.setScreen(null);
        }, 5));
        // The parent planet seen from its moons (which keep one face to it: longitude 0).
        for (String[] pair : new String[][]{{"moon", "earth"}, {"europa", "jupiter"}, {"titan", "saturn"}, {"enceladus", "saturn"}}) {
            scenes.add(new Scene("skyfrom_" + pair[0], mc -> {
                command(mc, "gamemode spectator @a");
                IntegratedServer server = mc.getSingleplayerServer();
                server.execute(() -> {
                    var level = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(StevesSpace.MODID, pair[0])));
                    var geometry = new com.thepearlking.stevespace.world.planet.PlanetGeometry(ServerUniverse.system().body(pair[0]).radius());
                    int x = (int) geometry.xForLongitude(Math.toRadians(55));
                    level.getChunk(x >> 4, 0);
                    int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, 0);
                    var player = server.getPlayerList().getPlayers().get(0);
                    player.teleportTo(level, x + 0.5, y + 12, 0.5, java.util.Set.of(), 90, 0);
                });
                lookAt(mc, pair[1], 0);
            }, 160));
            scenes.add(new Scene("skyfrom_" + pair[0] + "_zoom", mc -> {
                zoom(mc, 30);
                lookAt(mc, pair[1], 0);
            }, 60));
            scenes.add(new Scene("skyfrom_" + pair[0] + "_reset", mc -> zoom(mc, 70), 5));
            scenes.add(new Scene("skyfrom_" + pair[0] + "_back", mc -> look(mc, mc.player.getYRot() + 180, -25), 30));
            scenes.add(new Scene("skyfrom_" + pair[0] + "_up", mc -> look(mc, mc.player.getYRot(), -80), 30));
        }
        // The telescope never shrinks what it looks at: the Moon at zoom 1, 1.5, 2, 3 and 6.
        scenes.add(new Scene("tzoom_setup", mc -> {
            command(mc, "gamemode creative @a");
            command(mc, "execute in minecraft:overworld run tp @a 0.5 200 0.5");
            playerCommand(mc, "weather clear");
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> server.overworld().setBlockAndUpdate(new net.minecraft.core.BlockPos(0, 199, 0),
                    com.thepearlking.stevespace.registry.ModBlocks.TELESCOPE.get().defaultBlockState()));
            when(mc, t -> elevation(t, "moon") > 30 && elevation(t, "sun") < -12);
            lookAt(mc, "moon", 0);
        }, 100));
        scenes.add(new Scene("tzoom_eye", mc -> lookAt(mc, "moon", 0), 30));
        for (double z : new double[]{1, 1.5, 2, 3, 6}) {
            scenes.add(new Scene("tzoom_" + z, mc -> {
                lookAt(mc, "moon", 0);
                com.thepearlking.stevespace.client.TelescopeView.observe(new net.minecraft.core.BlockPos(0, 199, 0),
                        com.thepearlking.stevespace.client.TelescopeView.MANUAL);
                com.thepearlking.stevespace.client.TelescopeView.setZoom(z);
            }, 80));
        }
        scenes.add(new Scene("tzoom_end", mc -> com.thepearlking.stevespace.client.TelescopeView.stop(), 10));
        scenes.add(new Scene("moon_back_home", mc -> command(mc, "execute in minecraft:overworld run tp @a 0 120 -4572"), 40));
        scenes.add(new Scene("telescope_setup", mc -> {
            command(mc, "gamemode creative @a");
            command(mc, "execute in minecraft:overworld run tp @a 0.5 120 -4571.5");
            IntegratedServer server = mc.getSingleplayerServer();
            command(mc, "weather clear");
            server.execute(() -> {
                var level = server.overworld();
                // A clear stone terrace for the telescope, above whatever grows there.
                for (int dx = -6; dx <= 6; dx++) {
                    for (int dz = -6; dz <= 6; dz++) {
                        level.setBlockAndUpdate(new net.minecraft.core.BlockPos(1 + dx, 119, -4572 + dz),
                                net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState());
                        for (int dy = 0; dy < 30; dy++) {
                            level.setBlockAndUpdate(new net.minecraft.core.BlockPos(1 + dx, 120 + dy, -4572 + dz),
                                    net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                        }
                    }
                }
                level.setBlockAndUpdate(new net.minecraft.core.BlockPos(1, 120, -4572),
                        com.thepearlking.stevespace.registry.ModBlocks.TELESCOPE.get().defaultBlockState());
            });
            // Night, with a bright planet well up: the first of these that is.
            ClientTest.telescopeTarget = null;
            for (String planet : new String[]{System.getenv().getOrDefault("STEVESPACE_TELESCOPE", "saturn"), "jupiter", "mars", "venus"}) {
                IntegratedServer s2 = server;
                double found = findTime(s2, t -> elevation(t, planet) > 15 && elevation(t, "sun") < -12, 400);
                if (!Double.isNaN(found)) {
                    ClientTest.telescopeTarget = planet;
                    double time = found;
                    server.execute(() -> {
                        com.thepearlking.stevespace.universe.UniverseClock.get(server).setTime(time);
                        ServerUniverse.setWarp(server, 1);
                    });
                    StevesSpace.LOGGER.info("[ClientTest] telescope target {}", planet);
                    break;
                }
            }
        }, 60));
        for (int[] view : new int[][]{{0, 45, 20}, {1, 150, 15}, {2, 270, 35}}) {
            scenes.add(new Scene("telescope_model_" + view[0], mc -> {
                mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                double yaw = Math.toRadians(view[1]);
                double px = 1.5 + Math.sin(yaw) * 1.9;
                double pz = -4571.5 - Math.cos(yaw) * 1.9;
                command(mc, String.format(java.util.Locale.ROOT, "execute in minecraft:overworld run tp @a %.2f 120.0 %.2f %d %d",
                        px, pz, view[1], view[2]));
                command(mc, "effect give @a minecraft:night_vision 30 0 true");
            }, 30));
        }
        scenes.add(new Scene("telescope_list", mc -> {
            command(mc, "effect clear @a");
            command(mc, "execute in minecraft:overworld run tp @a 0.5 120 -4571.5");
            ClientTest.holdScreen = true;
            look(mc, 0, 0);
            com.thepearlking.stevespace.client.TelescopeView.toggle(new net.minecraft.core.BlockPos(1, 120, -4572));
        }, 40));
        scenes.add(new Scene("telescope_planet", mc -> {
            ClientTest.holdScreen = false;
            mc.setScreen(null);
            com.thepearlking.stevespace.client.TelescopeView.observe(new net.minecraft.core.BlockPos(1, 120, -4572),
                    ClientTest.telescopeTarget == null ? "moon" : ClientTest.telescopeTarget);
        }, 160));
        scenes.add(new Scene("telescope_moon", mc -> com.thepearlking.stevespace.client.TelescopeView.observe(
                new net.minecraft.core.BlockPos(1, 120, -4572), "moon"), 160));
        scenes.add(new Scene("telescope_wide", mc -> com.thepearlking.stevespace.client.TelescopeView.setZoom(0.15), 80));
        // The Sun up close through the telescope (daytime): its face, spots and any flare.
        scenes.add(new Scene("telescope_sun", mc -> {
            time(mc, 6000);
            com.thepearlking.stevespace.client.TelescopeView.observe(new net.minecraft.core.BlockPos(1, 120, -4572), "sun");
        }, 120));
        // A station with a radio in orbit: overhead and close, then far out (blurred).
        scenes.add(new Scene("telescope_ship_near", mc -> {
            parkTestStation(mc, 600, true);
            com.thepearlking.stevespace.client.TelescopeView.observe(new net.minecraft.core.BlockPos(1, 120, -4572), "ship:p:900");
        }, 60));
        scenes.add(new Scene("telescope_ship_far", mc -> {
            parkTestStation(mc, 30000, true);
            com.thepearlking.stevespace.client.TelescopeView.observe(new net.minecraft.core.BlockPos(1, 120, -4572), "ship:p:900");
        }, 160));
        scenes.add(new Scene("telescope_ship_list", mc -> {
            ClientTest.holdScreen = true;
            com.thepearlking.stevespace.client.TelescopeView.toggle(new net.minecraft.core.BlockPos(1, 120, -4572));
            mc.setScreen(new com.thepearlking.stevespace.client.telescope.TelescopeScreen(new net.minecraft.core.BlockPos(1, 120, -4572), "ship:p:900"));
        }, 40));
        // Without its radio it's off the list, but you can still find it by hand.
        scenes.add(new Scene("telescope_ship_manual", mc -> {
            ClientTest.holdScreen = false;
            mc.setScreen(null);
            parkTestStation(mc, 30000, false);
            com.thepearlking.stevespace.client.TelescopeView.observe(new net.minecraft.core.BlockPos(1, 120, -4572),
                    com.thepearlking.stevespace.client.TelescopeView.MANUAL);
            com.thepearlking.stevespace.client.TelescopeView.setZoom(400);
        }, 260));
        scenes.add(new Scene("telescope_ship_manual_aim", mc -> {
            var sky = com.thepearlking.stevespace.client.ClientUniverse.skyAt(mc.player.getEyePosition(),
                    com.thepearlking.stevespace.client.ClientUniverse.time());
            var ship = com.thepearlking.stevespace.client.telescope.TelescopeShips.find("p:900");
            StevesSpace.LOGGER.info("[ClientTest] manual: station {} radio {}", ship != null, ship != null && ship.radio());
            if (sky != null && ship != null) {
                var d = com.thepearlking.stevespace.client.telescope.SkyCatalog.ship(sky, ship).direction();
                com.thepearlking.stevespace.client.TelescopeView.setAim((float) Math.toDegrees(Math.atan2(-d.x(), d.z())),
                        (float) -Math.toDegrees(Math.asin(d.y())));
            }
        }, 100));
        scenes.add(new Scene("telescope_ship_manual_list", mc -> {
            ClientTest.holdScreen = true;
            mc.setScreen(new com.thepearlking.stevespace.client.telescope.TelescopeScreen(new net.minecraft.core.BlockPos(1, 120, -4572), null));
        }, 40));
        scenes.add(new Scene("telescope_ship_done", mc -> {
            ClientTest.holdScreen = false;
            mc.setScreen(null);
        }, 5));
        scenes.add(new Scene("telescope_off", mc -> {
            com.thepearlking.stevespace.client.TelescopeView.stop();
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
            look(mc, 200, 25);
        }, 40));
        scenes.add(new Scene("test_fire", mc -> {
            command(mc, "gamemode spectator @a");
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                var level = server.overworld();
                var spawn = level.getSharedSpawnPos();
                int gx = spawn.getX() - 20;
                int gz = spawn.getZ() - 20;
                int gy = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, gx, gz);
                var ground = new net.minecraft.core.BlockPos(gx, gy, gz);
                for (int dx = -14; dx <= 14; dx++) {
                    for (int dz = -14; dz <= 14; dz++) {
                        for (int dy = 0; dy < 30; dy++) {
                            level.setBlockAndUpdate(ground.offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                        }
                        level.setBlockAndUpdate(ground.offset(dx, -1, dz), net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState());
                    }
                }
                var seat = SelfTest.VesselTests.buildRocket(level, ground, 10);
                var pilot = SelfTest.VesselTests.mockPlayer(level, "TestFirePilot");
                var scan = com.thepearlking.stevespace.vessel.VesselAssembler.scan(level, seat, 1000);
                var vessel = com.thepearlking.stevespace.vessel.VesselEntity.launch(level, seat, scan.structure(), pilot);
                vessel.setControls(new com.thepearlking.stevespace.vessel.VesselControls(0.35, 0, 0, 0));
                testFire = vessel;
                var player = server.getPlayerList().getPlayers().get(0);
                player.teleportTo(level, gx + 9.5, gy + 4, gz + 0.5, java.util.Set.of(), 90, 5);
            });
            time(mc, 4500);
        }, 30));
        scenes.add(new Scene("test_fire_burn", mc -> {
            IntegratedServer server = mc.getSingleplayerServer();
            look(mc, 90, 25);
        }, 40));
        // A player's launch, done the way a player does it: click the seat, hold the throttle key.
        // Getting up and walking about the cabin of a ship in flight.
        scenes.add(new Scene("cabin_build", mc -> {
            command(mc, "gamemode creative @a");
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                var level = server.overworld();
                var spawn = level.getSharedSpawnPos();
                int gx = spawn.getX() + 60;
                int gz = spawn.getZ() + 60;
                level.getChunk(gx >> 4, gz >> 4);
                int gy = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, gx, gz);
                var g = new net.minecraft.core.BlockPos(gx, gy, gz);
                for (int dx = -7; dx <= 7; dx++) {
                    for (int dz = -7; dz <= 7; dz++) {
                        for (int dy = 0; dy < 30; dy++) {
                            level.setBlockAndUpdate(g.offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                        }
                    }
                }
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        boolean core = Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
                        boolean wall = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                        if (core) {
                            level.setBlockAndUpdate(g.offset(dx, 0, dz), com.thepearlking.stevespace.registry.ModBlocks.LAUNCH_PAD.get().defaultBlockState());
                            level.setBlockAndUpdate(g.offset(dx, 1, dz), com.thepearlking.stevespace.registry.ModBlocks.ROCKET_ENGINE.get().defaultBlockState());
                            var tankPos = g.offset(dx, 2, dz);
                            level.setBlockAndUpdate(tankPos, com.thepearlking.stevespace.registry.ModBlocks.PROPELLANT_TANK.get().defaultBlockState());
                            if (level.getBlockEntity(tankPos) instanceof com.thepearlking.stevespace.block.PropellantTankBlockEntity tank) {
                                tank.setPropellant(com.thepearlking.stevespace.block.PropellantTankBlock.CAPACITY);
                            }
                        }
                        level.setBlockAndUpdate(g.offset(dx, 3, dz), com.thepearlking.stevespace.registry.ModBlocks.HULL_WHITE.get().defaultBlockState());
                        level.setBlockAndUpdate(g.offset(dx, 7, dz), com.thepearlking.stevespace.registry.ModBlocks.HULL_WHITE.get().defaultBlockState());
                        if (wall) {
                            for (int dy = 4; dy <= 6; dy++) {
                                boolean window = dy == 5 && (dx == 0 || dz == 0);
                                level.setBlockAndUpdate(g.offset(dx, dy, dz), window
                                        ? com.thepearlking.stevespace.registry.ModBlocks.SPACECRAFT_WINDOW.get().defaultBlockState()
                                        : com.thepearlking.stevespace.registry.ModBlocks.HULL_GREY.get().defaultBlockState());
                            }
                        }
                    }
                }
                level.setBlockAndUpdate(g.offset(0, 4, -1), com.thepearlking.stevespace.registry.ModBlocks.PILOT_SEAT.get().defaultBlockState());
                level.setBlockAndUpdate(g.offset(0, 4, -2), com.thepearlking.stevespace.registry.ModBlocks.FLIGHT_COMPUTER.get().defaultBlockState());
                playerSeat = g.offset(0, 4, -1);
                var report = com.thepearlking.stevespace.vessel.VesselReport.analyze(level, ClientTest.playerSeat);
                StevesSpace.LOGGER.info("[ClientTest] cabin ship: ready={} blocks={} twr={} problems={}", report.ready(), report.blocks(),
                        report.thrustToWeight(), report.problems());
                var player = server.getPlayerList().getPlayers().get(0);
                player.teleportTo(level, gx + 0.5, gy + 4, gz + 1.5, java.util.Set.of(), 180, 10);
            });
            time(mc, 4500);
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
        }, 40));
        scenes.add(new Scene("cabin_sit", mc -> {
            var seat = ClientTest.playerSeat;
            mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.phys.BlockHitResult(
                    net.minecraft.world.phys.Vec3.atCenterOf(seat), net.minecraft.core.Direction.SOUTH, seat, false));
        }, 20));
        scenes.add(new Scene("cabin_throttle", mc -> net.minecraft.client.KeyMapping.set(
                com.thepearlking.stevespace.client.FlightKeys.THROTTLE_UP.getKey(), true), 40));
        scenes.add(new Scene("cabin_climb", mc -> {
            net.minecraft.client.KeyMapping.set(com.thepearlking.stevespace.client.FlightKeys.THROTTLE_UP.getKey(), false);
            StevesSpace.LOGGER.info("[ClientTest] cabin: vehicle={} throttle={}", mc.player.getVehicle(),
                    com.thepearlking.stevespace.client.FlightInput.throttle());
        }, 60));
        scenes.add(new Scene("cabin_standup", mc -> {
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                var player = server.getPlayerList().getPlayers().get(0);
                if (player.getVehicle() instanceof com.thepearlking.stevespace.vessel.VesselEntity v) {
                    v.requestDismount(player);
                }
            });
            look(mc, 0, 15);
        }, 30));
        scenes.add(new Scene("cabin_walk", mc -> {
            StevesSpace.LOGGER.info("[ClientTest] cabin standing: walking={} vehicle={} pull={}",
                    com.thepearlking.stevespace.client.vessel.CabinWalk.walking(), mc.player.getVehicle(),
                    mc.player.getVehicle() instanceof com.thepearlking.stevespace.vessel.VesselEntity v ? v.cabinPull() : null);
            net.minecraft.client.KeyMapping.set(mc.options.keyUp.getKey(), true);
        }, 20));
        scenes.add(new Scene("cabin_walk_stop", mc -> {
            net.minecraft.client.KeyMapping.set(mc.options.keyUp.getKey(), false);
            var v = (com.thepearlking.stevespace.vessel.VesselEntity) mc.player.getVehicle();
            StevesSpace.LOGGER.info("[ClientTest] cabin walked to {} (synced {})",
                    v == null ? null : com.thepearlking.stevespace.client.vessel.CabinWalk.positionFor(v, mc.player),
                    v == null ? null : v.crew().walkPosition(mc.player.getUUID()));
            look(mc, 180, 10);
        }, 30));
        for (int[] dir : new int[][]{{0, 10}, {90, 10}, {180, 10}, {270, 10}, {0, 85}}) {
            scenes.add(new Scene("cabin_look_" + dir[0] + "_" + dir[1], mc -> {
                look(mc, dir[0], dir[1]);
                var v = (com.thepearlking.stevespace.vessel.VesselEntity) mc.player.getVehicle();
                if (v != null) {
                    StevesSpace.LOGGER.info("[ClientTest] cabin look {}: q={} eye-vessel={} pos-vessel={} eyeH={} cam={} com={} walk={} yaw={}", dir[0],
                            v.renderOrientation(1), mc.player.getEyePosition().subtract(v.position()), mc.player.position().subtract(v.position()),
                            mc.player.getEyeHeight(), mc.gameRenderer.getMainCamera().getPosition().subtract(v.position()),
                            v.stats().centerOfMass(), com.thepearlking.stevespace.client.vessel.CabinWalk.positionFor(v, mc.player), mc.player.getYRot());
                }
            }, 15));
        }
        scenes.add(new Scene("cabin_third", mc -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK), 20));
        scenes.add(new Scene("cabin_coast", mc -> {
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                var player = server.getPlayerList().getPlayers().get(0);
                if (player.getVehicle() instanceof com.thepearlking.stevespace.vessel.VesselEntity v) {
                    v.setControls(com.thepearlking.stevespace.vessel.VesselControls.IDLE);
                }
            });
            net.minecraft.client.KeyMapping.set(mc.options.keyJump.getKey(), true);
        }, 30));
        scenes.add(new Scene("cabin_float", mc -> {
            net.minecraft.client.KeyMapping.set(mc.options.keyJump.getKey(), false);
            var v = (com.thepearlking.stevespace.vessel.VesselEntity) mc.player.getVehicle();
            StevesSpace.LOGGER.info("[ClientTest] cabin coasting: pull={} at {}", v == null ? null : v.cabinPull(),
                    v == null ? null : com.thepearlking.stevespace.client.vessel.CabinWalk.positionFor(v, mc.player));
            look(mc, 0, 40);
        }, 20));
        scenes.add(new Scene("cabin_sitback", mc -> {
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                var player = server.getPlayerList().getPlayers().get(0);
                if (player.getVehicle() instanceof com.thepearlking.stevespace.vessel.VesselEntity v) {
                    v.requestDismount(player); // walking: back to the seat (if near enough)
                }
            });
        }, 20));
        scenes.add(new Scene("cabin_done", mc -> {
            StevesSpace.LOGGER.info("[ClientTest] cabin after sitting: walking={} piloting={}",
                    com.thepearlking.stevespace.client.vessel.CabinWalk.walking(), com.thepearlking.stevespace.client.FlightInput.isPiloting());
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                var player = server.getPlayerList().getPlayers().get(0);
                if (player.getVehicle() instanceof com.thepearlking.stevespace.vessel.VesselEntity v) {
                    v.discard();
                }
                player.stopRiding();
            });
        }, 10));
        scenes.add(new Scene("player_launch_build", mc -> {
            command(mc, "gamemode creative @a");
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                var level = server.overworld();
                var spawn = level.getSharedSpawnPos();
                int gx = spawn.getX() - 30;
                int gz = spawn.getZ() + 25;
                int gy = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, gx, gz);
                var ground = new net.minecraft.core.BlockPos(gx, gy, gz);
                for (int dx = -6; dx <= 6; dx++) {
                    for (int dz = -6; dz <= 6; dz++) {
                        for (int dy = 0; dy < 20; dy++) {
                            level.setBlockAndUpdate(ground.offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                        }
                    }
                }
                // Placed the way a player would: pad, engine, empty tanks, seat, nose cone.
                level.setBlockAndUpdate(ground, com.thepearlking.stevespace.registry.ModBlocks.LAUNCH_PAD.get().defaultBlockState());
                level.setBlockAndUpdate(ground.above(1), com.thepearlking.stevespace.registry.ModBlocks.ROCKET_ENGINE.get().defaultBlockState());
                level.setBlockAndUpdate(ground.above(2), com.thepearlking.stevespace.registry.ModBlocks.PROPELLANT_TANK.get().defaultBlockState());
                level.setBlockAndUpdate(ground.above(3), com.thepearlking.stevespace.registry.ModBlocks.PROPELLANT_TANK.get().defaultBlockState());
                level.setBlockAndUpdate(ground.above(4), com.thepearlking.stevespace.registry.ModBlocks.PILOT_SEAT.get().defaultBlockState());
                level.setBlockAndUpdate(ground.above(5), com.thepearlking.stevespace.registry.ModBlocks.HULL_WHITE.get().defaultBlockState());
                playerSeat = ground.above(4);
                var player = server.getPlayerList().getPlayers().get(0);
                player.teleportTo(level, gx + 0.5, gy + 4, gz + 2.5, java.util.Set.of(), 180, 25);
            });
            time(mc, 4500);
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
        }, 40));
        scenes.add(new Scene("player_launch_sit", mc -> {
            var seat = ClientTest.playerSeat;
            var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(seat),
                    net.minecraft.core.Direction.SOUTH, seat, false);
            var result = mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
            StevesSpace.LOGGER.info("[ClientTest] clicked seat at {}: {}", seat, result);
        }, 20));
        scenes.add(new Scene("player_launch_throttle", mc -> {
            StevesSpace.LOGGER.info("[ClientTest] seated: vehicle={} piloting={}", mc.player.getVehicle(),
                    com.thepearlking.stevespace.client.FlightInput.isPiloting());
            net.minecraft.client.KeyMapping.set(com.thepearlking.stevespace.client.FlightKeys.THROTTLE_UP.getKey(), true);
            sampleCamera = true;
        }, 40));
        scenes.add(new Scene("player_launch_result", mc -> {
            net.minecraft.client.KeyMapping.set(com.thepearlking.stevespace.client.FlightKeys.THROTTLE_UP.getKey(), false);
            StevesSpace.LOGGER.info("[ClientTest] after holding throttle: vehicle={} throttle={}", mc.player.getVehicle(),
                    com.thepearlking.stevespace.client.FlightInput.throttle());
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
        }, 40));
        scenes.add(new Scene("player_launch_off", mc -> {
            sampleCamera = false;
            com.thepearlking.stevespace.client.FlightInput.setThrottle(0);
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                var player = server.getPlayerList().getPlayers().get(0);
                if (player.getVehicle() instanceof com.thepearlking.stevespace.vessel.VesselEntity v) {
                    v.discard(); // removed vessels let their crew go
                }
                player.stopRiding();
            });
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
        }, 10));
        // The same, with the engine put on sideways: it still launches (and goes sideways).
        scenes.add(new Scene("player_launch_bad", mc -> {
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                var level = server.overworld();
                var seat = ClientTest.playerSeat;
                level.setBlockAndUpdate(seat.below(3), com.thepearlking.stevespace.registry.ModBlocks.ROCKET_ENGINE.get().defaultBlockState()
                        .setValue(com.thepearlking.stevespace.block.RocketEngineBlock.FACING, net.minecraft.core.Direction.NORTH));
                for (int dy = 0; dy < 2; dy++) {
                    level.setBlockAndUpdate(seat.below(2 - dy), com.thepearlking.stevespace.registry.ModBlocks.PROPELLANT_TANK.get().defaultBlockState());
                }
                level.setBlockAndUpdate(seat, com.thepearlking.stevespace.registry.ModBlocks.PILOT_SEAT.get().defaultBlockState());
                level.setBlockAndUpdate(seat.above(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                var player = server.getPlayerList().getPlayers().get(0);
                player.teleportTo(level, seat.getX() + 0.5, seat.getY() + 0.2, seat.getZ() + 2.5, java.util.Set.of(), 180, 25);
            });
        }, 20));
        scenes.add(new Scene("player_launch_bad_sit", mc -> {
            var seat = ClientTest.playerSeat;
            mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.phys.BlockHitResult(
                    net.minecraft.world.phys.Vec3.atCenterOf(seat), net.minecraft.core.Direction.SOUTH, seat, false));
            net.minecraft.client.KeyMapping.set(com.thepearlking.stevespace.client.FlightKeys.THROTTLE_UP.getKey(), true);
        }, 40));
        scenes.add(new Scene("player_launch_bad_off", mc -> {
            net.minecraft.client.KeyMapping.set(com.thepearlking.stevespace.client.FlightKeys.THROTTLE_UP.getKey(), false);
            StevesSpace.LOGGER.info("[ClientTest] sideways engine: vehicle={}", mc.player.getVehicle());
            com.thepearlking.stevespace.client.FlightInput.setThrottle(0);
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> server.getPlayerList().getPlayers().get(0).stopRiding());
        }, 10));
        // A launch watched from a long way off: the trail it leaves, as it climbs and as it hangs in the sky.
        scenes.add(new Scene("trail_setup", mc -> {
            command(mc, "gamemode spectator @a");
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                var level = server.overworld();
                var player = server.getPlayerList().getPlayers().get(0);
                var spawn = level.getSharedSpawnPos();
                int gx = spawn.getX() + 40;
                int gz = spawn.getZ() - 260;
                level.getChunk(gx >> 4, gz >> 4);
                var ground = SelfTest.VesselTests.clearArea(level, gx, gz);
                var seat = SelfTest.VesselTests.buildRocket(level, ground, 6);
                var pilot = SelfTest.VesselTests.mockPlayer(level, "Trail" + (System.nanoTime() % 100000)); // fresh each run: no saved ride
                pilot.stopRiding();
                pilot.teleportTo(level, seat.getX() + 0.5, seat.getY() + 1, seat.getZ() + 0.5, java.util.Set.of(), 0, 0);
                var seatEntity = com.thepearlking.stevespace.block.SeatEntity.create(level, seat);
                level.addFreshEntity(seatEntity);
                pilot.startRiding(seatEntity, true);
                trailPilot = pilot;
                int py = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX() + 40, spawn.getZ());
                player.stopRiding();
                player.teleportTo(level, spawn.getX() + 40.5, py + 45, spawn.getZ() + 0.5, java.util.Set.of(), 180, -4);
            });
            time(mc, 3000);
            look(mc, 180, -4);
        }, 60));
        scenes.add(new Scene("trail_liftoff", mc -> {
            look(mc, 180, -6);
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> com.thepearlking.stevespace.vessel.VesselServer.onControls(trailPilot,
                    new com.thepearlking.stevespace.vessel.VesselControls(1, 0, 0, 0)));
        }, 100));
        scenes.add(new Scene("trail_climb", mc -> look(mc, 180, -12), 200));
        scenes.add(new Scene("trail_high", mc -> look(mc, 180, -30), 400));
        scenes.add(new Scene("trail_hanging", mc -> look(mc, 180, -25), 1200));
        scenes.add(new Scene("trail_dusk", mc -> {
            time(mc, 12900);
            look(mc, 180, -25);
        }, 60));
        scenes.add(new Scene("trail_done", mc -> time(mc, 6000), 5));
        scenes.add(new Scene("rocket_pad", mc -> {
            command(mc, "gamemode creative @a");
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                var level = server.overworld();
                var player = server.getPlayerList().getPlayers().get(0);
                var spawn = level.getSharedSpawnPos();
                int gx = spawn.getX() + 6;
                int gz = spawn.getZ() + 6;
                int gy = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, gx, gz);
                var ground = new net.minecraft.core.BlockPos(gx, gy, gz);
                for (int dx = -12; dx <= 12; dx++) {
                    for (int dz = -12; dz <= 12; dz++) {
                        for (int dy = 0; dy < 30; dy++) {
                            level.setBlockAndUpdate(ground.offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                        }
                    }
                }
                var seat = SelfTest.VesselTests.buildRocket(level, ground, 6);
                player.stopRiding();
                player.teleportTo(level, seat.getX() + 0.5, seat.getY() + 1, seat.getZ() + 0.5, java.util.Set.of(), 0, 0);
                var seatEntity = com.thepearlking.stevespace.block.SeatEntity.create(level, seat);
                level.addFreshEntity(seatEntity);
                player.startRiding(seatEntity, true);
                rocketSeat = seat;
            });
            time(mc, 4500);
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
            look(mc, 135, 12);
        }, 60));
        scenes.add(new Scene("flight_computer", mc -> {
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                var report = com.thepearlking.stevespace.vessel.VesselReport.analyze(server.overworld(), rocketSeat);
                mc.execute(() -> com.thepearlking.stevespace.client.gui.FlightComputerScreen.open(report));
            });
            holdScreen = true;
        }, 20));
        scenes.add(new Scene("liftoff", mc -> {
            holdScreen = false;
            mc.setScreen(null);
            com.thepearlking.stevespace.client.FlightInput.setThrottle(1f);
        }, 14));
        scenes.add(new Scene("liftoff_2s", mc -> look(mc, 135, 20), 26));
        scenes.add(new Scene("ascent", mc -> {
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
            look(mc, 135, 55);
        }, 30));
        scenes.add(new Scene("ascent_high", mc -> look(mc, 135, 45), 90));
        scenes.add(new Scene("autopilot_menu", mc -> {
            mc.setScreen(new com.thepearlking.stevespace.client.gui.AutopilotScreen());
            holdScreen = true;
        }, 10));
        scenes.add(new Scene("autopilot_engaged", mc -> {
            for (var child : java.util.List.copyOf(mc.screen.children())) {
                if (child instanceof net.minecraft.client.gui.components.Button b && b.getMessage().getString().equals("Fly to orbit")) {
                    b.onPress();
                    break;
                }
            }
            holdScreen = false;
        }, 40));
        scenes.add(new Scene("space_arrival", mc -> {
            waitFor(() -> com.thepearlking.stevespace.world.SpaceDimension.isSpace(mc.level), 20 * 240);
            look(mc, 135, 40);
        }, 40));
        scenes.add(new Scene("orbit_down", mc -> {
            waitFor(() -> com.thepearlking.stevespace.vessel.VesselEntity.shipOf(mc.player) instanceof com.thepearlking.stevespace.vessel.VesselEntity v
                    && v.autopilotStatus().isEmpty(), 20 * 240);
            look(mc, 135, 70);
        }, 40));
        scenes.add(new Scene("orbit_horizon", mc -> look(mc, 135, 15), 30));
        scenes.add(new Scene("map_orbit", mc -> {
            mc.setScreen(new com.thepearlking.stevespace.client.gui.MapScreen());
            holdScreen = true;
        }, 30));
        scenes.add(new Scene("map_moon", mc -> mc.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_TAB, 0, 0), 20));
        scenes.add(new Scene("mission_control", mc -> {
            holdScreen = true;
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> com.thepearlking.stevespace.network.SpaceNetwork.sendTo(server.getPlayerList().getPlayers().get(0),
                    new com.thepearlking.stevespace.network.MissionControlS2C(ServerUniverse.time(server),
                            com.thepearlking.stevespace.vessel.VesselEntity.trackingList(server))));
        }, 20));
        scenes.add(new Scene("mission_track", mc -> {
            for (var child : java.util.List.copyOf(mc.screen.children())) {
                if (child instanceof net.minecraft.client.gui.components.Button b && b.getMessage().getString().equals("Track")) {
                    b.onPress();
                    break;
                }
            }
        }, 20));
        scenes.add(new Scene("map_close", mc -> {
            mc.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_TAB, 0, 0);
            holdScreen = false;
            mc.setScreen(null);
        }, 5));
        scenes.add(new Scene("orbit_first_person", mc -> {
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            look(mc, 90, 30);
        }, 30));
        scenes.add(new Scene("orbit_warp", mc -> {
            if (testFire != null) {
                // The test-fire rocket is still burning in the air, which (rightly) blocks warp.
                var fire = testFire;
                mc.getSingleplayerServer().execute(fire::discard);
            }
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
            look(mc, 135, 50);
            new Thread(() -> {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {
                }
                for (int i = 0; i < 4; i++) {
                    com.thepearlking.stevespace.network.SpaceNetwork.sendToServer(new com.thepearlking.stevespace.network.WarpRequestC2S(1));
                }
            }, "stevespace-clienttest-warp").start();
        }, 80));
        scenes.add(new Scene("deorbit", mc -> {
            for (int i = 0; i < 8; i++) {
                com.thepearlking.stevespace.network.SpaceNetwork.sendToServer(new com.thepearlking.stevespace.network.WarpRequestC2S(-1));
            }
            com.thepearlking.stevespace.network.SpaceNetwork.sendToServer(
                    new com.thepearlking.stevespace.network.AutopilotC2S(com.thepearlking.stevespace.vessel.Autopilot.Program.LAND));
        }, 60));
        scenes.add(new Scene("map_deorbit", mc -> {
            mc.setScreen(new com.thepearlking.stevespace.client.gui.MapScreen());
            holdScreen = true;
        }, 20));
        scenes.add(new Scene("reentry", mc -> {
            holdScreen = false;
            mc.setScreen(null);
            waitFor(() -> !com.thepearlking.stevespace.world.SpaceDimension.isSpace(mc.level), 20 * 400);
            look(mc, 135, 50);
        }, 20));
        scenes.add(new Scene("landing_burn", mc -> {
            final int[] n = new int[1];
            waitFor(() -> {
                if (n[0]++ % 40 == 0) {
                    StevesSpace.LOGGER.info("[ClientTest] descending: vehicle={} y={}", mc.player.getVehicle() == null ? "none"
                            : mc.player.getVehicle().getClass().getSimpleName(), (int) mc.player.getY());
                }
                return mc.player.getY() < 400 || mc.player.getVehicle() instanceof com.thepearlking.stevespace.block.SeatEntity;
            }, 20 * 300);
            look(mc, 135, 30);
        }, 5));
        scenes.add(new Scene("landed", mc -> {
            waitFor(() -> mc.player.getVehicle() instanceof com.thepearlking.stevespace.block.SeatEntity
                    || mc.player.getVehicle() == null, 20 * 300);
            StevesSpace.LOGGER.info("[ClientTest] after landing: vehicle={} pos={}", mc.player.getVehicle(), mc.player.blockPosition());
            look(mc, 135, 20);
        }, 60));
        // Auroras: from the ground under Earth's oval at midnight in a storm, then from orbit
        // over the night side of Earth, Jupiter and Saturn.
        // A quiet night (no storm) under the oval, as a player would usually find it.
        scenes.add(new Scene("auroraquiet_ground", mc -> {
            com.thepearlking.stevespace.client.sky.AuroraRenderer.testStorm = 0;
            command(mc, "gamemode spectator @a");
            var geometry = new com.thepearlking.stevespace.world.planet.PlanetGeometry(ServerUniverse.system().earth().radius());
            // Southern oval (dark season there now), on the night side of the world.
            int z = (int) Math.round(geometry.zForLatitude(Math.toRadians(-66)));
            command(mc, "execute in minecraft:overworld run tp @a 0 220 " + z + " 0 -25");
            time(mc, 18000);
        }, 200));
        scenes.add(new Scene("auroraquiet_up", mc -> look(mc, 0, -80), 30));
        scenes.add(new Scene("auroraquiet_north", mc -> look(mc, 180, -15), 30));
        scenes.add(new Scene("auroraquiet_south", mc -> look(mc, 0, -15), 30));
        scenes.add(new Scene("auroraquiet_done", mc -> com.thepearlking.stevespace.client.sky.AuroraRenderer.testStorm = Double.NaN, 5));
        scenes.add(new Scene("aurora_earth_ground", mc -> {
            com.thepearlking.stevespace.client.sky.AuroraRenderer.testStorm = 1.5;
            command(mc, "gamemode spectator @a");
            var geometry = new com.thepearlking.stevespace.world.planet.PlanetGeometry(ServerUniverse.system().earth().radius());
            int z = (int) Math.round(geometry.zForLatitude(Math.toRadians(62)));
            command(mc, "execute in minecraft:overworld run tp @a 0 220 " + z + " 180 -25");
            time(mc, 18000);
        }, 200));
        scenes.add(new Scene("aurora_earth_ground_up", mc -> look(mc, 180, -80), 30));
        scenes.add(new Scene("aurora_earth_south", mc -> {
            var geometry = new com.thepearlking.stevespace.world.planet.PlanetGeometry(ServerUniverse.system().earth().radius());
            int z = (int) Math.round(geometry.zForLatitude(Math.toRadians(-62)));
            command(mc, "execute in minecraft:overworld run tp @a 0 220 " + z + " 0 -25");
            time(mc, 18000);
        }, 200));
        scenes.add(new Scene("aurora_earth_south_up", mc -> look(mc, 0, -80), 30));
        // The map from a ship in orbit: opens on the ship; pick a landing spot on the Moon.
        // The map opened standing on Earth: you, on the world.
        scenes.add(new Scene("mapself_open", mc -> {
            command(mc, "gamemode creative @a");
            command(mc, "execute in minecraft:overworld run tp @a 0 120 0");
        }, 80));
        scenes.add(new Scene("mapself_map", mc -> {
            holdScreen = true;
            mc.setScreen(new com.thepearlking.stevespace.client.gui.MapScreen());
        }, 40));
        scenes.add(new Scene("mapself_out1", mc -> {
            for (int i = 0; i < 25; i++) {
                mc.screen.mouseScrolled(100, 100, 0, -1);
            }
        }, 30));
        scenes.add(new Scene("mapself_out2", mc -> {
            for (int i = 0; i < 40; i++) {
                mc.screen.mouseScrolled(100, 100, 0, -1);
            }
        }, 30));
        scenes.add(new Scene("mapself_vesta", mc -> ((com.thepearlking.stevespace.client.gui.MapScreen) mc.screen).testFocus("vesta"), 40));
        scenes.add(new Scene("mapself_pallas", mc -> ((com.thepearlking.stevespace.client.gui.MapScreen) mc.screen).testFocus("pallas"), 40));
        scenes.add(new Scene("mapself_ceres", mc -> ((com.thepearlking.stevespace.client.gui.MapScreen) mc.screen).testFocus("ceres"), 40));
        scenes.add(new Scene("mapself_done", mc -> {
            holdScreen = false;
            mc.setScreen(null);
        }, 5));
        scenes.add(new Scene("mapui_setup", mc -> {
            orbitNight(mc, "earth", 1.3, 20, 0);
        }, 120));
        scenes.add(new Scene("mapui_board", mc -> {
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                var player = server.getPlayerList().getPlayers().get(0);
                var space = server.getLevel(com.thepearlking.stevespace.world.SpaceDimension.KEY);
                for (var v : space.getEntitiesOfClass(com.thepearlking.stevespace.vessel.VesselEntity.class, player.getBoundingBox().inflate(40))) {
                    if (v.materialized() && v.stats() != null && !v.stats().seats().isEmpty()) {
                        // A ship that is blocks: sit in its pilot seat.
                        var seatPos = v.blockOrigin().offset(v.stats().seats().get(0).pos());
                        var seat = com.thepearlking.stevespace.block.SeatEntity.create(space, seatPos);
                        space.addFreshEntity(seat);
                        player.startRiding(seat, true);
                    } else {
                        player.startRiding(v, true);
                    }
                    StevesSpace.LOGGER.info("[ClientTest] mapui ship materialized={} pilot={} vehicle={}", v.materialized(), v.pilot(), player.getVehicle());
                    break;
                }
            });
        }, 40));
        scenes.add(new Scene("mapui_open", mc -> {
            holdScreen = true;
            mc.setScreen(new com.thepearlking.stevespace.client.gui.MapScreen());
        }, 40));
        // A planet filling the view behind the panel (the buttons must keep their colours).
        scenes.add(new Scene("mapui_planet", mc -> {
            if (mc.screen instanceof com.thepearlking.stevespace.client.gui.MapScreen map) {
                map.testFocus("earth");
                for (int i = 0; i < 6; i++) {
                    map.mouseScrolled(100, 100, 0, 1);
                }
            }
        }, 60));
        scenes.add(new Scene("mapui_night", mc -> {
            time(mc, 18000);
        }, 60));
        scenes.add(new Scene("mapui_day", mc -> time(mc, 6000), 40));
        scenes.add(new Scene("mapui_pick", mc -> {
            mc.setScreen(com.thepearlking.stevespace.client.gui.MapScreen.forDestination());
        }, 40));
        scenes.add(new Scene("mapui_moon", mc -> {
            if (mc.screen instanceof com.thepearlking.stevespace.client.gui.MapScreen map) {
                map.testSelect("moon");
            }
        }, 30));
        scenes.add(new Scene("mapui_spotmode", mc -> {
            if (mc.screen instanceof com.thepearlking.stevespace.client.gui.MapScreen map) {
                map.testPressSpot();
            }
        }, 40));
        scenes.add(new Scene("mapui_spotpicked", mc -> {
            if (mc.screen instanceof com.thepearlking.stevespace.client.gui.MapScreen map) {
                map.testClick(mc.getWindow().getGuiScaledWidth() / 2.0 + 20, mc.getWindow().getGuiScaledHeight() / 2.0 - 10);
            }
        }, 30));
        scenes.add(new Scene("mapui_zoomout", mc -> {
            mc.setScreen(new com.thepearlking.stevespace.client.gui.MapScreen());
        }, 30));
        scenes.add(new Scene("mapui_zoomout_far", mc -> {
            if (mc.screen != null) {
                for (int i = 0; i < 60; i++) {
                    mc.screen.mouseScrolled(100, 100, 0, -1);
                }
            }
        }, 30));
        scenes.add(new Scene("mapui_zoomout_system", mc -> {
            if (mc.screen != null) {
                for (int i = 0; i < 40; i++) {
                    mc.screen.mouseScrolled(100, 100, 0, -1);
                }
            }
        }, 30));
        scenes.add(new Scene("mapui_done", mc -> {
            holdScreen = false;
            mc.setScreen(null);
        }, 5));
        // Weather that's somewhere: a storm brewed here, rain only under it, cleared again, and seen from orbit.
        scenes.add(new Scene("weather_setup", mc -> {
            command(mc, "gamemode creative @a");
            command(mc, "execute in minecraft:overworld run tp @a 0 140 0 0 10");
            time(mc, 6000);
        }, 80));
        // Every world's ground from a little above: the same regions and colours as from orbit.
        for (String world : new String[]{"pluto", "charon", "makemake", "eris", "haumea", "ceres", "vesta", "mercury", "venus", "moon",
                "mars", "io", "europa", "ganymede", "callisto", "titan", "enceladus", "triton"}) {
            scenes.add(new Scene("surface_" + world, mc -> {
                command(mc, "gamemode spectator @a");
                command(mc, "execute in stevespace:" + world + " run tp @a 300 230 300 35 40");
            }, 160));
        }
        // Acheron: from Earth beside the Moon on its pass, from the Moon, close up, on the ground, and deep down.
        scenes.add(new Scene("acheron_earth", mc -> {
            command(mc, "gamemode spectator @a");
            command(mc, "execute in minecraft:overworld run tp @a 0 140 0 0 -30");
            playerCommand(mc, "weather clear");
            when(mc, t -> elevation(t, "moon") > 30 && elevation(t, "sun") < -12);
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> com.thepearlking.stevespace.world.AcheronEvent.testPass(server, 1800));
            lookAt(mc, "moon", 0);
        }, 100));
        scenes.add(new Scene("acheron_earth_zoom", mc -> com.thepearlking.stevespace.client.TelescopeView.observe(
                new net.minecraft.core.BlockPos(0, 139, 0), "acheron"), 200));
        scenes.add(new Scene("acheron_earth_zoom_end", mc -> com.thepearlking.stevespace.client.TelescopeView.stop(), 20));
        // Drifting from the Moon's pull into Acheron's during an encounter: the switch must look seamless.
        scenes.add(new Scene("acheron_cross", mc -> {
            command(mc, "gamemode creative @a");
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                com.thepearlking.stevespace.world.AcheronEvent.testPass(server, 600);
                var space = server.getLevel(com.thepearlking.stevespace.world.SpaceDimension.KEY);
                var player = server.getPlayerList().getPlayers().get(0);
                if (player.getVehicle() != null) {
                    player.stopRiding();
                }
                var system = ServerUniverse.system();
                double t = ServerUniverse.time(server);
                var moon = system.body("moon");
                var acheron = system.body("acheron");
                var a = system.stateRelativeToParent(acheron, t).sub(system.stateRelativeToParent(moon, t)); // Acheron as seen from the Moon
                DVec3 away = a.position().normalize();
                DVec3 at = a.position().add(away.scale(9_150));
                DVec3 v = a.velocity().sub(away.scale(0.05));
                var blocks = new java.util.HashMap<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState>();
                blocks.put(new net.minecraft.core.BlockPos(0, 0, 0), com.thepearlking.stevespace.registry.ModBlocks.ROCKET_ENGINE.get().defaultBlockState());
                blocks.put(new net.minecraft.core.BlockPos(0, 1, 0), com.thepearlking.stevespace.registry.ModBlocks.PILOT_SEAT.get().defaultBlockState());
                var ship = com.thepearlking.stevespace.vessel.VesselEntity.spawnInOrbit(space,
                        new com.thepearlking.stevespace.vessel.VesselStructure(blocks, new java.util.HashMap<>()), moon,
                        new com.thepearlking.stevespace.universe.StateVector(at, v), new org.joml.Quaterniond());
                player.teleportTo(space, ship.getX() + 6, ship.getY() + 2, ship.getZ(), java.util.Set.of(), 0, 0);
            });
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
        }, 120));
        scenes.add(new Scene("acheron_cross_look", mc -> lookAt(mc, "acheron", 0), 60));
        scenes.add(new Scene("acheron_cross_after", mc -> lookAt(mc, "acheron", 0), 100));
        scenes.add(new Scene("acheron_cross_moon", mc -> lookAt(mc, "moon", 0), 60));
        scenes.add(new Scene("acheron_cross_later", mc -> lookAt(mc, "acheron", 0), 200));
        // On the map during the encounter: an unnamed dark grey thing beside the Moon.
        scenes.add(new Scene("acheron_map", mc -> {
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> com.thepearlking.stevespace.world.AcheronEvent.testPass(server, 600));
            holdScreen = true;
            var map = new com.thepearlking.stevespace.client.gui.MapScreen();
            mc.setScreen(map);
            map.testFocus("moon");
        }, 60));
        scenes.add(new Scene("acheron_map_out", mc -> {
            if (mc.screen != null) {
                for (int i = 0; i < 6; i++) {
                    mc.screen.mouseScrolled(100, 100, 0, 1);
                }
            }
        }, 30));
        scenes.add(new Scene("acheron_map_pick", mc -> {
            if (mc.screen instanceof com.thepearlking.stevespace.client.gui.MapScreen map) {
                map.testSelect("acheron");
            }
        }, 30));
        scenes.add(new Scene("acheron_map_done", mc -> {
            holdScreen = false;
            mc.setScreen(null);
        }, 5));
        scenes.add(new Scene("acheron_moon", mc -> {
            zoom(mc, 70);
            command(mc, "execute in stevespace:moon run tp @a 0 140 0 0 -20"); // the near side, under Earth
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> com.thepearlking.stevespace.world.AcheronEvent.testPass(server, 1200));
            lookAt(mc, "acheron", 0);
        }, 140));
        scenes.add(new Scene("acheron_orbit", mc -> {
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> com.thepearlking.stevespace.world.AcheronEvent.testPass(server, 0));
            orbitFacing(mc, "acheron", 2.6);
            lookAt(mc, "acheron", 0);
        }, 400)); // (time for its map to be drawn the first time)
        // Underground and below the world: no distant ground or horizon showing through.
        scenes.add(new Scene("under_moon_bedrock", mc -> {
            command(mc, "gamemode spectator @a");
            command(mc, "execute in stevespace:moon run tp @a 0.5 -75 0.5 0 5");
        }, 120));
        scenes.add(new Scene("under_moon_rock", mc -> command(mc, "execute in stevespace:moon run tp @a 0.5 20 0.5 0 5"), 80));
        scenes.add(new Scene("under_moon_surface", mc -> command(mc, "execute in stevespace:moon run tp @a 0.5 140 0.5 0 5"), 80));
        // A small world's ground curving away: the horizon close, nothing flat sticking out past it.
        scenes.add(new Scene("curve_hygiea", mc -> {
            command(mc, "gamemode spectator @a");
            command(mc, "execute in stevespace:hygiea run tp @a 0 104 0 0 8");
            time(mc, 4000);
        }, 300));
        // Things standing out on the curve: they must sit on the ground, not float above it.
        scenes.add(new Scene("curve_hygiea_stands", mc -> {
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                var level = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                        net.minecraft.resources.ResourceLocation.parse("stevespace:hygiea")));
                for (int d = 20; d <= 80; d += 15) {
                    int z = d;
                    level.getChunk(0, z >> 4);
                    int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, 0, z);
                    var stand = net.minecraft.world.entity.EntityType.ARMOR_STAND.create(level);
                    stand.moveTo(0.5, y, z + 0.5, 0, 0);
                    stand.setNoGravity(true);
                    level.addFreshEntity(stand);
                }
            });
            command(mc, "execute in stevespace:hygiea run tp @a 0.5 104 0.5 0 12");
        }, 120));
        // From y 403 over Hygiea: the ground below in blocks, and a box built 350 blocks off (past
        // the loaded ground) drawn from space as a landmark.
        scenes.add(new Scene("curve_hygiea_403", mc -> {
            command(mc, "gamemode creative @a");
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                var level = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                        net.minecraft.resources.ResourceLocation.parse("stevespace:hygiea")));
                for (int x = 0; x < 8; x++) {
                    for (int z = 0; z < 8; z++) {
                        for (int y = 100; y < 108; y++) {
                            if (x == 0 || z == 0 || x == 7 || z == 7 || y == 107) {
                                com.thepearlking.stevespace.world.Landmarks.recordBuilt(level, new net.minecraft.core.BlockPos(x, y, 208 + z), 0xD0D0D0);
                            }
                        }
                    }
                }
            });
            command(mc, "execute in stevespace:hygiea run tp @a 0.5 403 0.5 0 55");
            time(mc, 6000);
        }, 200));
        scenes.add(new Scene("curve_hygiea_403_down", mc -> look(mc, 0, 80), 40));
        // High above the small world: something right beside you must keep its shape (not squashed).
        scenes.add(new Scene("curve_hygiea_float", mc -> {
            command(mc, "gamemode creative @a");
            command(mc, "execute in stevespace:hygiea run tp @a 0.5 600 0.5 0 10");
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                var level = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                        net.minecraft.resources.ResourceLocation.parse("stevespace:hygiea")));
                var stand = net.minecraft.world.entity.EntityType.ARMOR_STAND.create(level);
                stand.moveTo(0.5, 599.5, 4.5, 180, 0);
                stand.setNoGravity(true);
                level.addFreshEntity(stand);
                level.setBlockAndUpdate(new net.minecraft.core.BlockPos(-1, 599, 4), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(new net.minecraft.core.BlockPos(2, 599, 4), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            });
        }, 100));
        scenes.add(new Scene("curve_hygiea_inventory", mc -> {
            command(mc, "gamemode survival @a");
            command(mc, "give @a minecraft:diamond_sword");
            command(mc, "give @a minecraft:oak_log 16");
            command(mc, "give @a minecraft:chest");
            holdScreen = true;
            mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
        }, 40));
        scenes.add(new Scene("curve_hygiea_inventory_close", mc -> {
            holdScreen = false;
            mc.setScreen(null);
            command(mc, "gamemode creative @a");
        }, 5));
        scenes.add(new Scene("curve_hygiea_high", mc -> command(mc, "execute in stevespace:hygiea run tp @a 0 160 0 0 20"), 200));
        scenes.add(new Scene("curve_earth", mc -> command(mc, "execute in minecraft:overworld run tp @a 0 140 0 0 5"), 200));
        // (A moment for a fresh world to finish loading before the next ones.)
        scenes.add(new Scene("acheron_orbit_hold", mc -> command(mc, "gamemode creative @a"), 200));
        // Hygiea's lumpy outline (its real ground, from orbit).
        scenes.add(new Scene("acheron_orbit_hygiea", mc -> {
            orbitFacing(mc, "hygiea", 9);
            lookAt(mc, "hygiea", 0);
        }, 300));
        // Low over Haumea: round close in (no stretched shell cutting through the view).
        scenes.add(new Scene("haumea_low", mc -> {
            command(mc, "gamemode spectator @a");
            command(mc, "execute in stevespace:haumea run tp @a 0.5 420 0.5 0 20");
        }, 160));
        scenes.add(new Scene("haumea_low_side", mc -> look(mc, 90, 10), 40));
        // Haumea's egg shape, from a high orbit.
        scenes.add(new Scene("acheron_orbit_haumea", mc -> {
            orbitFacing(mc, "haumea", 4);
            lookAt(mc, "haumea", 0);
        }, 300));
        // The face it keeps toward Earth (acheron1.svg): the two splotches.
        scenes.add(new Scene("acheron_orbit_face", mc -> {
            orbitFacing(mc, "acheron", 2.6, 0, 0);
            lookAt(mc, "acheron", 0);
        }, 140));
        // Which way round: over the big splotch's longitude, and the same distance the other way.
        scenes.add(new Scene("acheron_probe_east", mc -> {
            orbitFacing(mc, "acheron", 2.6, 35, 28);
            lookAt(mc, "acheron", 0);
        }, 140));
        scenes.add(new Scene("acheron_probe_west", mc -> {
            orbitFacing(mc, "acheron", 2.6, 35, -28);
            lookAt(mc, "acheron", 0);
        }, 140));
        // The far side's basin of rings, with the pale spot in the middle.
        scenes.add(new Scene("acheron_orbit_basin", mc -> {
            orbitFacing(mc, "acheron", 2.6, 5, 105);
            lookAt(mc, "acheron", 0);
        }, 140));
        scenes.add(new Scene("acheron_ground", mc -> {
            command(mc, "gamemode spectator @a");
            var geo = new com.thepearlking.stevespace.world.planet.PlanetGeometry(com.thepearlking.stevespace.universe.AcheronCourse.RADIUS);
            int x = (int) geo.xForLongitude(Math.toRadians(20));
            int z = (int) geo.zForLatitude(Math.toRadians(22));
            command(mc, "execute in stevespace:acheron run tp @a " + x + " 160 " + z + " 90 25");
            time(mc, 4000);
        }, 200));
        // Where the big splotch meets the crust, and one of the basin's rings, from a little way up.
        scenes.add(new Scene("acheron_edge", mc -> {
            var geo = new com.thepearlking.stevespace.world.planet.PlanetGeometry(com.thepearlking.stevespace.universe.AcheronCourse.RADIUS);
            int x = (int) geo.xForLongitude(Math.toRadians(8));
            int z = (int) geo.zForLatitude(Math.toRadians(10));
            command(mc, "execute in stevespace:acheron run tp @a " + x + " 150 " + z + " -90 30");
        }, 200));
        scenes.add(new Scene("acheron_ring", mc -> {
            var geo = new com.thepearlking.stevespace.world.planet.PlanetGeometry(com.thepearlking.stevespace.universe.AcheronCourse.RADIUS);
            int x = (int) geo.xForLongitude(Math.toRadians(105));
            int z = (int) geo.zForLatitude(Math.toRadians(5.5 + 11.4 + 4));
            command(mc, "execute in stevespace:acheron run tp @a " + x + " 200 " + z + " 0 30");
        }, 200));
        scenes.add(new Scene("acheron_deep", mc -> {
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                var level = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                        net.minecraft.resources.ResourceLocation.parse("stevespace:acheron")));
                var player = server.getPlayerList().getPlayers().get(0);
                var blood = com.thepearlking.stevespace.registry.ModBlocks.BLOCKS.getEntries().stream()
                        .filter(e -> e.getId().getPath().equals("bloodstone")).findFirst().get().get();
                // Find a vein: open space with bloodstone round it, near the bottom.
                for (int r = 0; r < 200; r += 4) {
                    for (int a = 0; a < 16; a++) {
                        int x = (int) (r * Math.cos(a * Math.PI / 8)), z = (int) (r * Math.sin(a * Math.PI / 8));
                        level.getChunk(x >> 4, z >> 4);
                        for (int y = -62; y < -40; y++) {
                            var at = new net.minecraft.core.BlockPos(x, y, z);
                            if (level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir()
                                    && level.getBlockState(at.below()).is(blood)) {
                                player.teleportTo(level, x + 0.5, y + 0.2, z + 0.5, java.util.Set.of(), 30, 10);
                                StevesSpace.LOGGER.info("[ClientTest] acheron vein at {} {} {}", x, y, z);
                                return;
                            }
                        }
                    }
                }
                StevesSpace.LOGGER.warn("[ClientTest] no acheron vein found");
            });
            command(mc, "gamemode spectator @a");
            command(mc, "effect give @a minecraft:night_vision 60 0 true");
        }, 160));
        scenes.add(new Scene("acheron_deep_across", mc -> look(mc, mc.player.getYRot() + 120, 5), 40));
        scenes.add(new Scene("acheron_deep_roof", mc -> look(mc, mc.player.getYRot() + 100, -35), 40));
        scenes.add(new Scene("weather_storm", mc -> playerCommand(mc, "weather thunder"), 260));
        scenes.add(new Scene("weather_far", mc -> command(mc, "tp @a 3000 140 0"), 200));
        scenes.add(new Scene("weather_far_look", mc -> look(mc, 90, -6), 40));
        scenes.add(new Scene("weather_far_low", mc -> {
            command(mc, "gamemode spectator @a");
            command(mc, "tp @a 3000 95 0 90 -3");
        }, 120));
        scenes.add(new Scene("weather_far_side", mc -> look(mc, 60, -3), 40));
        scenes.add(new Scene("weather_far_done", mc -> command(mc, "gamemode creative @a"), 5));
        // Docking: the dock menu (a ship without RCS: the red warning), the new blocks, RCS firing.
        scenes.add(new Scene("dock_setup", mc -> {
            command(mc, "gamemode creative @a");
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                var space = server.getLevel(com.thepearlking.stevespace.world.SpaceDimension.KEY);
                var earth = com.thepearlking.stevespace.universe.ServerUniverse.system().earth();
                double r = earth.radius() + 3100;
                double mu = earth.mu() * com.thepearlking.stevespace.config.SpaceConfig.GRAVITY_SCALE.get();
                java.util.function.DoubleFunction<com.thepearlking.stevespace.universe.StateVector> at = a -> new com.thepearlking.stevespace.universe.StateVector(
                        new com.thepearlking.stevespace.universe.DVec3(r * Math.cos(a), r * Math.sin(a), 0),
                        new com.thepearlking.stevespace.universe.DVec3(-Math.sin(a), Math.cos(a), 0).scale(Math.sqrt(mu / r)));
                var hull = com.thepearlking.stevespace.registry.ModBlocks.HULL_WHITE.get().defaultBlockState();
                var portEast = com.thepearlking.stevespace.registry.ModBlocks.DOCKING_PORT.get().defaultBlockState()
                        .setValue(com.thepearlking.stevespace.block.DockingPortBlock.FACING, net.minecraft.core.Direction.EAST);
                var station = new java.util.HashMap<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState>();
                for (int y = 0; y < 4; y++) {
                    station.put(new net.minecraft.core.BlockPos(0, y, 0), hull);
                }
                station.put(new net.minecraft.core.BlockPos(0, 4, 0), com.thepearlking.stevespace.registry.ModBlocks.RADIO_RACK.get().defaultBlockState());
                station.put(new net.minecraft.core.BlockPos(1, 2, 0), portEast);
                station.put(new net.minecraft.core.BlockPos(-1, 2, 0), portEast.setValue(com.thepearlking.stevespace.block.DockingPortBlock.FACING,
                        net.minecraft.core.Direction.WEST));
                var s = com.thepearlking.stevespace.vessel.VesselEntity.spawnInOrbit(space,
                        new com.thepearlking.stevespace.vessel.VesselStructure(station, new java.util.HashMap<>()), earth, at.apply(1.2), new org.joml.Quaterniond());
                s.setCustomName(net.minecraft.network.chat.Component.literal("Skylab"));
                s.park(null);
                var ship = new java.util.HashMap<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState>();
                ship.put(new net.minecraft.core.BlockPos(0, 0, 0), com.thepearlking.stevespace.registry.ModBlocks.ROCKET_ENGINE.get().defaultBlockState());
                ship.put(new net.minecraft.core.BlockPos(0, 1, 0), com.thepearlking.stevespace.registry.ModBlocks.PROPELLANT_TANK.get().defaultBlockState());
                ship.put(new net.minecraft.core.BlockPos(0, 2, 0), com.thepearlking.stevespace.registry.ModBlocks.PILOT_SEAT.get().defaultBlockState());
                ship.put(new net.minecraft.core.BlockPos(0, 3, 0), hull);
                ship.put(new net.minecraft.core.BlockPos(0, 4, 0), portEast.setValue(com.thepearlking.stevespace.block.DockingPortBlock.FACING,
                        net.minecraft.core.Direction.UP));
                var mine = com.thepearlking.stevespace.vessel.VesselEntity.spawnInOrbit(space,
                        new com.thepearlking.stevespace.vessel.VesselStructure(ship, new java.util.HashMap<>()), earth, at.apply(1.2 - 1500 / r),
                        new org.joml.Quaterniond());
                mine.fillTanks();
                var player = server.getPlayerList().getPlayers().get(0);
                player.teleportTo(space, mine.getX(), mine.getY() + 1, mine.getZ(), java.util.Set.of(), 0, 0);
                player.startRiding(mine, true);
            });
        }, 140));
        scenes.add(new Scene("dock_menu", mc -> {
            ClientTest.holdScreen = true;
            mc.setScreen(new com.thepearlking.stevespace.client.gui.DockScreen(null));
        }, 60));
        scenes.add(new Scene("dock_autopilot", mc -> {
            ClientTest.holdScreen = true;
            mc.setScreen(new com.thepearlking.stevespace.client.gui.AutopilotScreen());
        }, 30));
        scenes.add(new Scene("dock_done", mc -> {
            ClientTest.holdScreen = false;
            mc.setScreen(null);
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                var player = server.getPlayerList().getPlayers().get(0);
                var own = com.thepearlking.stevespace.vessel.VesselEntity.shipOf(player, 2);
                player.stopRiding();
                if (own != null) {
                    own.discard();
                }
            });
        }, 20));
        // The new blocks on a little hull: RCS clusters, a docking port sharing power (red) and one not.
        scenes.add(new Scene("rcs_blocks_go", mc -> {
            command(mc, "gamemode spectator @a");
            command(mc, "execute in minecraft:overworld run tp @a 43.5 153.5 44.5 135 25");
        }, 60));
        scenes.add(new Scene("rcs_blocks", mc -> {
            command(mc, "execute in minecraft:overworld run fill 40 150 40 40 153 40 stevespace:hull_white");
            command(mc, "execute in minecraft:overworld run setblock 41 151 40 stevespace:rcs_thruster[facing=east]");
            command(mc, "execute in minecraft:overworld run setblock 40 151 41 stevespace:rcs_thruster[facing=south]");
            command(mc, "execute in minecraft:overworld run setblock 39 151 40 stevespace:rcs_thruster[facing=west]");
            command(mc, "execute in minecraft:overworld run setblock 40 154 40 stevespace:docking_port[facing=up,sharing=true]");
            command(mc, "execute in minecraft:overworld run setblock 40 153 41 stevespace:docking_port[facing=south,sharing=false]");
            command(mc, "execute in minecraft:overworld run tp @a 43.5 153.5 44.5 135 25");
        }, 80));
        // RCS firing on a ship in orbit, seen from beside it.
        scenes.add(new Scene("rcs_puffs", mc -> {
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                var space = server.getLevel(com.thepearlking.stevespace.world.SpaceDimension.KEY);
                var earth = com.thepearlking.stevespace.universe.ServerUniverse.system().earth();
                double r = earth.radius() + 3300;
                double mu = earth.mu() * com.thepearlking.stevespace.config.SpaceConfig.GRAVITY_SCALE.get();
                var state = new com.thepearlking.stevespace.universe.StateVector(new com.thepearlking.stevespace.universe.DVec3(0, r, 0),
                        new com.thepearlking.stevespace.universe.DVec3(-Math.sqrt(mu / r), 0, 0));
                var ship = new java.util.HashMap<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState>();
                ship.put(new net.minecraft.core.BlockPos(0, 0, 0), com.thepearlking.stevespace.registry.ModBlocks.ROCKET_ENGINE.get().defaultBlockState());
                for (int y = 1; y <= 3; y++) {
                    ship.put(new net.minecraft.core.BlockPos(0, y, 0), com.thepearlking.stevespace.registry.ModBlocks.PROPELLANT_TANK.get().defaultBlockState());
                }
                ship.put(new net.minecraft.core.BlockPos(0, 4, 0), com.thepearlking.stevespace.registry.ModBlocks.HULL_WHITE.get().defaultBlockState());
                for (var d : new net.minecraft.core.Direction[]{net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.SOUTH,
                        net.minecraft.core.Direction.EAST, net.minecraft.core.Direction.WEST}) {
                    for (int y : new int[]{1, 3}) {
                        ship.put(new net.minecraft.core.BlockPos(0, y, 0).relative(d), com.thepearlking.stevespace.registry.ModBlocks.RCS_THRUSTER.get()
                                .defaultBlockState().setValue(com.thepearlking.stevespace.block.RcsThrusterBlock.FACING, d));
                    }
                }
                var v = com.thepearlking.stevespace.vessel.VesselEntity.spawnInOrbit(space,
                        new com.thepearlking.stevespace.vessel.VesselStructure(ship, new java.util.HashMap<>()), earth, state, new org.joml.Quaterniond());
                v.fillTanks();
                com.thepearlking.stevespace.vessel.VesselEntity.testRcsPush = new org.joml.Vector3d(0.7, 0, 0.7);
                var player = server.getPlayerList().getPlayers().get(0);
                player.teleportTo(space, v.getX() - 7, v.getY() + 1, v.getZ() + 3, java.util.Set.of(), -110, 5);
            });
        }, 60));
        scenes.add(new Scene("rcs_puffs_done", mc -> com.thepearlking.stevespace.vessel.VesselEntity.testRcsPush = null, 5));
        scenes.add(new Scene("weather_back", mc -> command(mc, "tp @a 0 140 0"), 260));
        scenes.add(new Scene("weather_clear", mc -> playerCommand(mc, "weather clear"), 400));
        scenes.add(new Scene("weather_orbit", mc -> {
            playerCommand(mc, "weather thunder");
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> orbitOverServer(server, 0, 0, 1.45));
        }, 200));
        scenes.add(new Scene("weather_orbit_down", mc -> look(mc, 0, 89), 60));
        // A skyscraper seen from orbit.
        scenes.add(new Scene("landmark_build", mc -> {
            command(mc, "gamemode creative @a");
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                var level = server.overworld();
                var spawn = level.getSharedSpawnPos();
                int x0 = spawn.getX() + 200;
                int z0 = spawn.getZ();
                for (int x = 0; x < 64; x++) {
                    for (int z = 0; z < 64; z++) {
                        int top = 63 + 230 - ((x / 16 + z / 16) % 3) * 40;
                        for (int y = 64; y <= top; y += 1) {
                            if (x == 0 || z == 0 || x == 63 || z == 63 || y == top) {
                                com.thepearlking.stevespace.world.Landmarks.recordBuilt(level, new net.minecraft.core.BlockPos(x0 + x, y, z0 + z), 0xC8C8C8);
                            }
                        }
                    }
                }
            });
        }, 20));
        scenes.add(new Scene("landmark_orbit", mc -> {
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                var spawn = server.overworld().getSharedSpawnPos();
                orbitOverServer(server, spawn.getX() + 232, spawn.getZ() + 32, 1.45);
            });
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
        }, 160));
        scenes.add(new Scene("landmark_down", mc -> look(mc, 0, 89), 40));
        scenes.add(new Scene("landmark_slant", mc -> look(mc, 0, 70), 40));
        // Seen from orbit on the night side: lightning in the storms below, and a burst on the ground.
        scenes.add(new Scene("orbitview_lightning", mc -> orbitNight(mc, "earth", 1.5, 5, 75), 200));
        scenes.add(new Scene("orbitview_nuke_flash", mc -> {
            var state = com.thepearlking.stevespace.client.sky.SkyState.compute(mc.gameRenderer.getMainCamera(), mc.level, 1);
            var system = com.thepearlking.stevespace.client.ClientUniverse.system();
            var earth = system.body("earth");
            double t = com.thepearlking.stevespace.client.ClientUniverse.time();
            var frame = system.frameAt(earth, t);
            DVec3 up = state.sky().position().sub(system.absolutePosition(earth, t)).normalize();
            // A bit ahead of the point straight below, so it's in view looking forward and down.
            double lat = Math.asin(up.dot(frame.north()));
            double lon = Math.atan2(up.dot(frame.east90()), up.dot(frame.meridian()));
            var geometry = new com.thepearlking.stevespace.world.planet.PlanetGeometry(earth.radius());
            double x = geometry.xForLongitude(lon);
            double z = geometry.zForLatitude(lat + Math.toRadians(2));
            com.thepearlking.stevespace.client.nuke.NukeClient.detonation(new com.thepearlking.stevespace.network.NukeS2C("minecraft:overworld",
                    x, 70, z, 64, 50000, com.thepearlking.stevespace.nuke.NuclearEffects.Regime.SURFACE.ordinal(), "earth", new double[3], 0, t));
        }, 2));
        scenes.add(new Scene("orbitview_nuke_fireball", mc -> { }, 60));
        scenes.add(new Scene("orbitview_nuke_rising", mc -> { }, 600));
        scenes.add(new Scene("orbitview_nuke_cloud", mc -> { }, 1200));
        scenes.add(new Scene("aurora_earth_orbit", mc -> orbitNight(mc, "earth", 1.5, 55, 70), 160));
        scenes.add(new Scene("aurora_earth_orbit_limb", mc -> look(mc, 180, 35), 30));
        scenes.add(new Scene("aurora_jupiter_orbit", mc -> orbitNight(mc, "jupiter", 1.6, 60, 60), 160));
        scenes.add(new Scene("aurora_jupiter_north", mc -> look(mc, 180, 40), 30));
        scenes.add(new Scene("aurora_saturn_orbit", mc -> orbitNight(mc, "saturn", 1.6, 60, 60), 160));
        scenes.add(new Scene("aurora_saturn_north", mc -> look(mc, 180, 40), 30));
        scenes.add(new Scene("aurora_done", mc -> com.thepearlking.stevespace.client.sky.AuroraRenderer.testStorm = Double.NaN, 5));
        // Remote control: a probe on its pad, a console down the road; the console screen, the
        // view from the probe, and launching it from the console.
        scenes.add(new Scene("rc_setup", mc -> {
            command(mc, "gamemode creative @a");
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                var level = server.overworld();
                var spawn = level.getSharedSpawnPos();
                level.getChunk((spawn.getX() + 40) >> 4, (spawn.getZ() - 30) >> 4);
                var ground = SelfTest.VesselTests.clearArea(level, spawn.getX() + 40, spawn.getZ() - 30);
                var seat = SelfTest.VesselTests.buildRocket(level, ground, 4);
                level.setBlockAndUpdate(seat, com.thepearlking.stevespace.registry.ModBlocks.REMOTE_RECEIVER.get().defaultBlockState());
                if (level.getBlockEntity(seat) instanceof com.thepearlking.stevespace.remote.ChannelEntity rx) {
                    rx.setChannel(12);
                }
                for (int f = 1; f <= 2; f++) {
                    level.setBlockAndUpdate(seat.below(f).east(), com.thepearlking.stevespace.registry.ModBlocks.RC_FUNCTION.get().defaultBlockState()
                            .setValue(com.thepearlking.stevespace.remote.RcFunctionBlock.FUNCTION, f));
                }
                var console = ground.offset(-4, 1, 3);
                level.setBlockAndUpdate(console.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(console, com.thepearlking.stevespace.registry.ModBlocks.REMOTE_CONSOLE.get().defaultBlockState()
                        .setValue(com.thepearlking.stevespace.block.FacingBlock.FACING, net.minecraft.core.Direction.SOUTH));
                if (level.getBlockEntity(console) instanceof com.thepearlking.stevespace.remote.ChannelEntity cx) {
                    cx.setChannel(12);
                }
                ClientTest.rcConsole = console;
                var player = server.getPlayerList().getPlayers().get(0);
                if (player.getVehicle() != null) {
                    player.stopRiding();
                }
                player.teleportTo(level, console.getX() + 0.5, console.getY(), console.getZ() + 2.5, java.util.Set.of(), 180, 20);
            });
            time(mc, 4500);
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
        }, 60));
        scenes.add(new Scene("rc_console", mc -> {
            IntegratedServer server = mc.getSingleplayerServer();
            holdScreen = true;
            server.execute(() -> com.thepearlking.stevespace.remote.RemoteControl.openConsole(server.getPlayerList().getPlayers().get(0), ClientTest.rcConsole));
        }, 30));
        scenes.add(new Scene("rc_view", mc -> {
            if (mc.screen instanceof com.thepearlking.stevespace.client.gui.RemoteConsoleScreen screen) {
                for (var child : java.util.List.copyOf(screen.children())) {
                    if (child instanceof net.minecraft.client.gui.components.Button b && b.getMessage().getString().equals("Connect")) {
                        b.onPress();
                        break;
                    }
                }
            }
            holdScreen = false;
            look(mc, 200, 15);
        }, 60));
        scenes.add(new Scene("rc_launch", mc -> net.minecraft.client.KeyMapping.set(com.thepearlking.stevespace.client.FlightKeys.THROTTLE_UP.getKey(), true), 50));
        scenes.add(new Scene("rc_flight", mc -> net.minecraft.client.KeyMapping.set(com.thepearlking.stevespace.client.FlightKeys.THROTTLE_UP.getKey(), false), 30));
        scenes.add(new Scene("rc_done", mc -> {
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                var player = server.getPlayerList().getPlayers().get(0);
                var ship = com.thepearlking.stevespace.vessel.VesselEntity.shipOf(player);
                com.thepearlking.stevespace.remote.RemoteControl.disconnect(player, "stevespace.remote.disconnected");
                if (ship != null) {
                    ship.discard();
                }
            });
        }, 30));
        // A nuclear surface burst (1.45 Mt), watched from 220 blocks at noon: flash, fireball,
        // shock and dust, the cap climbing, the full mushroom cloud.
        scenes.add(new Scene("nuke_setup", mc -> {
            command(mc, "gamemode spectator @a");
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                var level = server.overworld();
                var spawn = level.getSharedSpawnPos();
                int gx = spawn.getX() + 400;
                int gz = spawn.getZ();
                level.getChunk(gx >> 4, gz >> 4);
                int gy = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, gx, gz);
                ClientTest.nukeAt = new net.minecraft.core.BlockPos(gx, gy, gz);
                var player = server.getPlayerList().getPlayers().get(0);
                player.teleportTo(level, gx - 220, gy + 40, gz + 0.5, java.util.Set.of(), -90, -8);
            });
            time(mc, 6000);
        }, 200));
        scenes.add(new Scene("nuke_detonate", mc -> {
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                var level = server.overworld();
                var at = ClientTest.nukeAt;
                level.setBlockAndUpdate(at, com.thepearlking.stevespace.registry.ModBlocks.NUCLEAR_DEVICE.get().defaultBlockState()
                        .setValue(com.thepearlking.stevespace.nuke.NuclearDeviceBlock.YIELD, Integer.getInteger("stevespace.nukeYield", System.getenv("STEVESPACE_NUKE_YIELD") == null ? 1 : Integer.parseInt(System.getenv("STEVESPACE_NUKE_YIELD"))))
                        .setValue(com.thepearlking.stevespace.nuke.NuclearDeviceBlock.ARMED, true));
                level.setBlockAndUpdate(at.east(), net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK.defaultBlockState());
            });
            look(mc, -90, -8);
        }, 3));
        scenes.add(new Scene("nuke_t1s", mc -> { }, 17));
        scenes.add(new Scene("nuke_t3s", mc -> { }, 40));
        scenes.add(new Scene("nuke_t10s", mc -> { }, 140));
        scenes.add(new Scene("nuke_t30s", mc -> { }, 400));
        scenes.add(new Scene("nuke_t90s", mc -> { }, 1200));
        scenes.add(new Scene("nuke_t240s", mc -> look(mc, -90, -20), 3000));
        scenes.add(new Scene("nuke_done", mc -> { }, 5));
        // The same up close (90 blocks), for detail.
        scenes.add(new Scene("nukeclose_setup", mc -> {
            command(mc, "gamemode spectator @a");
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                var level = server.overworld();
                var spawn = level.getSharedSpawnPos();
                int gx = spawn.getX() + 400;
                int gz = spawn.getZ() + 40;
                level.getChunk(gx >> 4, gz >> 4);
                int gy = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, gx, gz);
                ClientTest.nukeAt = new net.minecraft.core.BlockPos(gx, gy, gz);
                var player = server.getPlayerList().getPlayers().get(0);
                player.teleportTo(level, gx - 120, gy + 55, gz + 0.5, java.util.Set.of(), -90, 10);
            });
            time(mc, 6000);
        }, 200));
        scenes.add(new Scene("nukeclose_detonate", mc -> {
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                var level = server.overworld();
                var at = ClientTest.nukeAt;
                level.setBlockAndUpdate(at, com.thepearlking.stevespace.registry.ModBlocks.NUCLEAR_DEVICE.get().defaultBlockState()
                        .setValue(com.thepearlking.stevespace.nuke.NuclearDeviceBlock.YIELD, 1)
                        .setValue(com.thepearlking.stevespace.nuke.NuclearDeviceBlock.ARMED, true));
                level.setBlockAndUpdate(at.east(), net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK.defaultBlockState());
            });
            look(mc, -90, 10);
        }, 10));
        scenes.add(new Scene("nukeclose_t1s", mc -> { }, 10));
        scenes.add(new Scene("nukeclose_t2s", mc -> { }, 20));
        scenes.add(new Scene("nukeclose_t4s", mc -> { }, 40));
        scenes.add(new Scene("nukeclose_t8s", mc -> { }, 80));
        scenes.add(new Scene("nukeclose_t15s", mc -> look(mc, -90, -5), 140));
        scenes.add(new Scene("nukeclose_t30s", mc -> look(mc, -90, -15), 300));
        scenes.add(new Scene("nukeclose_t60s", mc -> look(mc, -90, -22), 600));
        scenes.add(new Scene("nukeclose_t120s", mc -> look(mc, -90, -25), 1200));
        scenes.add(new Scene("nukeclose_done", mc -> { }, 5));
        // A Starfish Prime-style burst high above Earth at night, seen from the ground beneath:
        // the flash, the swelling debris ball changing colour, the red glow, the aurora.
        scenes.add(new Scene("starfish_setup", mc -> {
            command(mc, "gamemode spectator @a");
            IntegratedServer server = mc.getSingleplayerServer();
            time(mc, 18000);
            server.execute(() -> {
                var level = server.overworld();
                var player = server.getPlayerList().getPlayers().get(0);
                var spawn = level.getSharedSpawnPos();
                player.teleportTo(level, spawn.getX() + 0.5, 140, spawn.getZ() + 0.5, java.util.Set.of(), 0, -60);
                var system = ServerUniverse.system();
                var earth = system.earth();
                double t = ServerUniverse.time(server);
                var below = com.thepearlking.stevespace.universe.SurfaceFrame.toOrbit(system, earth, spawn.getX(), 140, spawn.getZ(), 0, 0, 0, t).position();
                // A little to the south of straight up, as Johnston Island was from Hawaii.
                DVec3 north = system.frameAt(earth, t).north();
                DVec3 dir = below.normalize().sub(north.scale(0.12)).normalize();
                double r = earth.radius() + 3500; // above the air
                DVec3 along = north.cross(dir).normalize();
                double mu = earth.mu() * com.thepearlking.stevespace.config.SpaceConfig.GRAVITY_SCALE.get();
                var space = server.getLevel(com.thepearlking.stevespace.world.SpaceDimension.KEY);
                var blocks = new java.util.HashMap<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState>();
                blocks.put(new net.minecraft.core.BlockPos(0, 0, 0), com.thepearlking.stevespace.registry.ModBlocks.ROCKET_ENGINE.get().defaultBlockState());
                blocks.put(new net.minecraft.core.BlockPos(0, 1, 0), com.thepearlking.stevespace.registry.ModBlocks.REMOTE_RECEIVER.get().defaultBlockState());
                blocks.put(new net.minecraft.core.BlockPos(0, 2, 0), com.thepearlking.stevespace.registry.ModBlocks.NUCLEAR_DEVICE.get().defaultBlockState()
                        .setValue(com.thepearlking.stevespace.nuke.NuclearDeviceBlock.ARMED, true));
                ClientTest.starfish = com.thepearlking.stevespace.vessel.VesselEntity.spawnInOrbit(space,
                        new com.thepearlking.stevespace.vessel.VesselStructure(blocks, new java.util.HashMap<>()), earth,
                        new com.thepearlking.stevespace.universe.StateVector(dir.scale(r), along.scale(Math.sqrt(mu / r))), new org.joml.Quaterniond());
            });
        }, 120));
        scenes.add(new Scene("starfish_detonate", mc -> {
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                var ship = ClientTest.starfish;
                if (ship != null && !ship.isRemoved()) {
                    // Fired in flight, as a remote function relay would.
                    com.thepearlking.stevespace.nuke.Detonation.inFlight(ship, new net.minecraft.core.BlockPos(0, 2, 0),
                            ship.structure().blocks().get(new net.minecraft.core.BlockPos(0, 2, 0)));
                } else {
                    StevesSpace.LOGGER.info("[ClientTest] starfish: ship missing ({})", ship);
                }
            });
            lookAtNuke(mc);
        }, 4));
        scenes.add(new Scene("starfish_t1s", mc -> lookAtNuke(mc), 16));
        scenes.add(new Scene("starfish_t5s", mc -> lookAtNuke(mc), 80));
        scenes.add(new Scene("starfish_t20s", mc -> lookAtNuke(mc), 300));
        scenes.add(new Scene("starfish_t60s", mc -> lookAtNuke(mc), 800));
        scenes.add(new Scene("starfish_t180s", mc -> lookAtNuke(mc), 2400));
        scenes.add(new Scene("starfish_aurora", mc -> look(mc, 0, -25), 30));
        // On the Moon: no air, so a flash, a plasma ball, and debris thrown out in silence.
        scenes.add(new Scene("moonnuke_setup", mc -> {
            command(mc, "gamemode spectator @a");
            command(mc, "execute in stevespace:moon run tp @a 0 150 0 -90 15");
        }, 160));
        scenes.add(new Scene("moonnuke_detonate", mc -> {
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                var moon = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(StevesSpace.MODID, "moon")));
                int gy = moon.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 80, 0);
                var at = new net.minecraft.core.BlockPos(80, gy, 0);
                moon.setBlockAndUpdate(at, com.thepearlking.stevespace.registry.ModBlocks.NUCLEAR_DEVICE.get().defaultBlockState()
                        .setValue(com.thepearlking.stevespace.nuke.NuclearDeviceBlock.ARMED, true));
                moon.setBlockAndUpdate(at.east(), net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK.defaultBlockState());
            });
        }, 4));
        scenes.add(new Scene("moonnuke_t1s", mc -> { }, 16));
        scenes.add(new Scene("moonnuke_t10s", mc -> { }, 180));
        scenes.add(new Scene("moonnuke_t40s", mc -> { }, 600));
        // Last, since they leave the player out by the Sun:
        // Orbiting the Sun itself: far, close, and skimming its surface (looking at the limb).
        scenes.add(new Scene("sun_orbit_mercury", mc -> sunOrbit(mc, 83, 90), 140));
        scenes.add(new Scene("sun_orbit_far", mc -> sunOrbit(mc, 3.0, 90), 140));
        scenes.add(new Scene("sun_orbit_close", mc -> sunOrbit(mc, 1.25, 30), 140));
        scenes.add(new Scene("sun_orbit_close_down", mc -> look(mc, 0, 90), 30));
        scenes.add(new Scene("sun_orbit_skim", mc -> sunOrbit(mc, 1.04, 12), 140));
        scenes.add(new Scene("sun_orbit_skim_down", mc -> look(mc, 0, 90), 30));
    }

    private static void waitFor(java.util.function.BooleanSupplier condition, int maxTicks) {
        waitCondition = condition;
        conditionTicks = maxTicks;
    }

    private static final int[] dayside = new int[2];
    private static net.minecraft.core.BlockPos roofScope = net.minecraft.core.BlockPos.ZERO;
    private static net.minecraft.core.BlockPos rcConsole = net.minecraft.core.BlockPos.ZERO;
    private static net.minecraft.core.BlockPos nukeAt = net.minecraft.core.BlockPos.ZERO;
    private static com.thepearlking.stevespace.vessel.VesselEntity starfish;
    private static String telescopeTarget;

    /**
     * Builds a small station (module, truss, solar wings, radio rack) in cell 900 of the space
     * dimension and parks it {@code altitude} km above the telescope, tipped a little toward the
     * Sun so it is lit.
     */
    private static void parkTestStation(Minecraft mc, double altitude, boolean radio) {
        IntegratedServer server = mc.getSingleplayerServer();
        server.execute(() -> {
            var space = server.getLevel(com.thepearlking.stevespace.world.SpaceDimension.KEY);
            var c = com.thepearlking.stevespace.world.SpaceDimension.cellCenter(900);
            int cx = (int) Math.floor(c.x);
            int cy = (int) c.y;
            int cz = (int) Math.floor(c.z);
            space.getChunk(new net.minecraft.core.BlockPos(cx, cy, cz));
            for (int dx = -14; dx <= 14; dx += 16) {
                space.getChunk(new net.minecraft.core.BlockPos(cx + dx, cy, cz));
            }
            for (int x = -12; x <= 12; x++) {
                for (int y = -4; y <= 4; y++) {
                    for (int z = -2; z <= 2; z++) {
                        net.minecraft.world.level.block.state.BlockState st = null;
                        if (Math.abs(x) <= 3 && Math.abs(y) <= 2) {
                            st = com.thepearlking.stevespace.registry.ModBlocks.HULL_WHITE.get().defaultBlockState();
                        } else if (y == 0 && z == 0) {
                            st = com.thepearlking.stevespace.registry.ModBlocks.ALUMINIUM_FRAME.get().defaultBlockState();
                        } else if (Math.abs(x) >= 6 && z == 0 && y != 0) {
                            st = com.thepearlking.stevespace.registry.ModBlocks.SOLAR_PANEL.get().defaultBlockState();
                        }
                        if (st != null) {
                            space.setBlockAndUpdate(new net.minecraft.core.BlockPos(cx + x, cy + y, cz + z), st);
                        }
                    }
                }
            }
            space.setBlockAndUpdate(new net.minecraft.core.BlockPos(cx, cy + 3, cz), radio
                    ? com.thepearlking.stevespace.registry.ModBlocks.RADIO_RACK.get().defaultBlockState()
                    : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            var system = ServerUniverse.system();
            var earth = system.body("earth");
            double t = ServerUniverse.time(server);
            var up = com.thepearlking.stevespace.universe.SurfaceFrame.toOrbit(system, earth, 1.5, 121, -4571.5, 0, 0, 0, t)
                    .position().normalize();
            var sun = system.absolutePosition(earth, t).scale(-1).normalize();
            double r = earth.radius() + altitude;
            // About 20° from the zenith, on the Sun's side.
            var dir = up.add(sun.sub(up.scale(sun.dot(up))).normalize().scale(0.36 * altitude / r)).normalize();
            double mu = earth.mu() * com.thepearlking.stevespace.config.SpaceConfig.GRAVITY_SCALE.get();
            var along = dir.cross(com.thepearlking.stevespace.universe.DVec3.Y).normalize();
            var state = new com.thepearlking.stevespace.universe.StateVector(dir.scale(r), along.scale(Math.sqrt(mu / r)));
            com.thepearlking.stevespace.world.ParkedShips.get(server).put(new com.thepearlking.stevespace.world.ParkedShips.Parked(
                    900, "earth", state, t, "\"Test Station\"", new net.minecraft.core.BlockPos(cx - 12, cy - 4, cz - 2),
                    new net.minecraft.core.BlockPos(cx + 12, cy + 4, cz + 2), new com.thepearlking.stevespace.universe.DVec3(c.x, c.y, c.z)));
            StevesSpace.LOGGER.info("[ClientTest] test station parked {} km up, elevation {} at t={}", altitude,
                    Math.toDegrees(Math.asin(dir.dot(up))), t);
        });
    }
    private static net.minecraft.core.BlockPos playerSeat;
    private static com.thepearlking.stevespace.vessel.VesselEntity parkedShip;

    /** The first universe time within {@code days} Earth days when the sky condition holds, or NaN. */
    private static double findTime(IntegratedServer server, SkyCondition condition, int days) {
        double start = com.thepearlking.stevespace.universe.UniverseClock.get(server).time();
        for (double t = start + 5; t < start + days * 1200.0; t += 5) {
            if (condition.test(t)) {
                return t;
            }
        }
        return Double.NaN;
    }

    /** Map x on a body where the Sun stands about 40° up in the morning sky. */
    private static double daysideX(IntegratedServer server, String bodyId) {
        var system = ServerUniverse.system();
        var body = system.body(bodyId);
        double t = com.thepearlking.stevespace.universe.UniverseClock.get(server).time();
        var frame = system.frameAt(body, t);
        var sun = system.absolutePosition(system.sun(), t).sub(system.absolutePosition(body, t)).normalize();
        double subsolar = Math.atan2(sun.dot(frame.east90()), sun.dot(frame.meridian()));
        var geometry = new com.thepearlking.stevespace.world.planet.PlanetGeometry(body.radius());
        double longitude = subsolar - Math.toRadians(50);
        return geometry.xForLongitude(Math.atan2(Math.sin(longitude), Math.cos(longitude)));
    }

    /** A place on a world matching {@code wanted} (searched in a fixed pseudo-random order). */
    private static double[] findSpot(String body, java.util.function.Predicate<com.thepearlking.stevespace.world.planet.TerrainSample> wanted) {
        var terrain = com.thepearlking.stevespace.world.gen.PlanetBodies.terrain(body);
        var geometry = terrain.geometry();
        var s = new com.thepearlking.stevespace.world.planet.TerrainSample();
        java.util.Random random = new java.util.Random(body.hashCode());
        double c = geometry.circumference();
        for (int i = 0; i < 400_000; i++) {
            double x = (random.nextDouble() - 0.5) * c;
            double z = (random.nextDouble() - 0.5) * c * 0.5 * 0.85;
            terrain.sample(x, z, s);
            if (wanted.test(s)) {
                return new double[]{x, z};
            }
        }
        return null;
    }

    /** Moves the clock on until the Sun stands well up over this spot (within one solar day). */
    private static void toDaylight(IntegratedServer server, String bodyId, double x, double z) {
        var sys = ServerUniverse.system();
        var body = sys.body(bodyId);
        var geometry = new com.thepearlking.stevespace.world.planet.PlanetGeometry(body.radius());
        double t = ServerUniverse.time(server);
        double day = ServerUniverse.solarDaySeconds(body);
        double best = 0;
        double bestElevation = -10;
        for (int i = 0; i < 192; i++) {
            double dt = day * i / 192;
            double e = com.thepearlking.stevespace.universe.LocalSky.at(sys, body, geometry.latitude(z), geometry.longitude(x), 0, t + dt)
                    .elevationOf(sys, sys.sun(), t + dt);
            if (e > Math.toRadians(35)) {
                best = dt;
                bestElevation = e;
                break;
            }
            if (e > bestElevation) {
                bestElevation = e;
                best = dt;
            }
        }
        if (best > 0) {
            ServerUniverse.advance(server, best);
        }
    }

    private static void command(Minecraft mc, String command) {
        IntegratedServer server = mc.getSingleplayerServer();
        server.execute(() -> server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withSuppressedOutput(), command));
    }

    /** A command as the player types it (where they stand, with their permissions). */
    private static void playerCommand(Minecraft mc, String command) {
        IntegratedServer server = mc.getSingleplayerServer();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayers().get(0);
            server.getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(4), command);
        });
    }

    private static void zoom(Minecraft mc, int fov) {
        mc.options.fov().set(fov);
    }

    private static double angleBetween(double t, String a, String b) {
        var sys = ServerUniverse.system();
        var sky = spawnSky(t);
        var da = sky.offsetTo(sys, sys.body(a), t).normalize();
        var db = sky.offsetTo(sys, sys.body(b), t).normalize();
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, da.dot(db)))));
    }

    /** Sky test at the spawn point at universe time t. */
    private interface SkyCondition {
        boolean test(double t);
    }

    private static double elevation(double t, String body) {
        var sys = ServerUniverse.system();
        return Math.toDegrees(spawnSky(t).elevationOf(sys, sys.body(body), t));
    }

    private static boolean rising(double t, String body) {
        return elevation(t + 5, body) > elevation(t, body);
    }

    private static LocalSky spawnSky(double t) {
        var sys = ServerUniverse.system();
        var server = Minecraft.getInstance().getSingleplayerServer();
        var spawn = server.overworld().getSharedSpawnPos();
        var geo = new com.thepearlking.stevespace.world.planet.PlanetGeometry(sys.earth().radius());
        return LocalSky.at(sys, sys.earth(), geo.latitude(spawn.getZ()), geo.longitude(spawn.getX()), 0, t);
    }

    /** Jumps the universe clock forward to the first moment (within ~90 days) the condition holds. */
    private static void when(Minecraft mc, SkyCondition condition) {
        IntegratedServer server = mc.getSingleplayerServer();
        server.execute(() -> {
            var clock = com.thepearlking.stevespace.universe.UniverseClock.get(server);
            double start = clock.time();
            for (double t = start + 5; t < start + 90 * 1200; t += 5) {
                if (condition.test(t)) {
                    clock.setTime(t);
                    ServerUniverse.setWarp(server, 1); // re-syncs clients
                    return;
                }
            }
            StevesSpace.LOGGER.warn("[ClientTest] condition never met");
        });
    }

    /** While set, every frame's camera height goes to the log (to measure shaking). */
    static boolean sampleCamera;
    /** While set, every frame's sky direction (the Moon's) goes to the log. */
    public static boolean sampleSky;

    @SubscribeEvent
    public static void onRenderStage(net.minecraftforge.client.event.RenderLevelStageEvent event) {
        if (!sampleCamera || event.getStage() != net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_SKY) {
            return;
        }
        var cam = event.getCamera().getPosition();
        Minecraft mc = Minecraft.getInstance();
        var v = mc.player == null ? null : mc.player.getVehicle();
        StevesSpace.LOGGER.info("[ClientTest] camera {} {} {} {} vehicle {}", System.nanoTime(), cam.x, cam.y, cam.z,
                v == null ? "none" : v.getClass().getSimpleName());
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof net.minecraft.client.gui.screens.BackupConfirmScreen screen) {
            // The normal "experimental settings" warning for worlds with custom world gen: skip it.
            for (var child : screen.children()) {
                if (child instanceof net.minecraft.client.gui.components.Button button
                        && button.getMessage().getString().contains("know what")) {
                    button.onPress();
                    break;
                }
            }
            return;
        }
        if (mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen && !worldRequested) {
            // Open the test world the way a player would (quick-play starts too early for Forge).
            worldRequested = true;
            mc.createWorldOpenFlows().openWorld("test", () -> StevesSpace.LOGGER.info("[ClientTest] world open cancelled"));
            return;
        }
        if (mc.level == null || mc.player == null) {
            return;
        }
        if (mc.screen != null && !holdScreen) {
            StevesSpace.LOGGER.info("[ClientTest] closing unexpected screen {}", mc.screen.getClass().getSimpleName());
            mc.setScreen(null);
        }
        ticksInWorld++;
        if (ticksInWorld < 200) {
            return; // let terrain load (software rendering is slow)
        }
        if (waitCondition != null) {
            if (!waitCondition.getAsBoolean() && --conditionTicks > 0) {
                return;
            }
            StevesSpace.LOGGER.info("[ClientTest] wait {} ", conditionTicks > 0 ? "done" : "TIMED OUT");
            waitCondition = null;
        }
        if (waitTicks > 0) {
            waitTicks--;
            return;
        }
        if (sceneIndex >= scenes.size()) {
            return; // finished, the game is closing
        }
        if (sceneIndex >= 0) {
            String name = scenes.get(sceneIndex).name();
            Screenshot.grab(mc.gameDirectory, "test_" + name + ".png", mc.getMainRenderTarget(),
                    message -> StevesSpace.LOGGER.info("[ClientTest] {}", message.getString()));
        }
        sceneIndex++;
        String only = System.getenv("STEVESPACE_CLIENTTEST_ONLY");
        while (only != null && sceneIndex < scenes.size()
                && java.util.Arrays.stream(only.split(",")).noneMatch(scenes.get(sceneIndex).name()::startsWith)) {
            sceneIndex++;
        }
        if (sceneIndex >= scenes.size()) {
            StevesSpace.LOGGER.info("[ClientTest] DONE");
            mc.stop();
            return;
        }
        Scene scene = scenes.get(sceneIndex);
        StevesSpace.LOGGER.info("[ClientTest] scene {}", scene.name());
        scene.setup().accept(mc);
        waitTicks = scene.settleTicks();
    }

    /** Height above sea level of whatever the player is flying in (or of the player). */
    static double flightAltitude(Minecraft mc) {
        if (mc.player == null) {
            return Double.MAX_VALUE;
        }
        if (com.thepearlking.stevespace.client.space.ClientSpace.orbitActive()) {
            var state = com.thepearlking.stevespace.client.space.ClientSpace.stateAt(ClientUniverse.time());
            var body = com.thepearlking.stevespace.client.space.ClientSpace.body();
            return state == null || body == null ? Double.MAX_VALUE : state.position().length() - body.radius();
        }
        if (com.thepearlking.stevespace.client.space.ClientSpace.mapModeActive()) {
            return com.thepearlking.stevespace.client.space.ClientSpace.mapPosition().y - 63;
        }
        return mc.player.getY() - 63;
    }

    private static void time(Minecraft mc, long dayTime) {
        IntegratedServer server = mc.getSingleplayerServer();
        if (server != null) {
            server.execute(() -> ServerUniverse.setLocalTime(server, dayTime));
        }
    }

    private static void look(Minecraft mc, float yaw, float pitch) {
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
        mc.player.yRotO = yaw;
        mc.player.xRotO = pitch;
    }

    /** A probe on the side of a body that faces Earth and the Sun, the player beside it. */
    private static void orbitFacing(Minecraft mc, String bodyId, double radii) {
        orbitFacing(mc, bodyId, radii, Double.NaN, Double.NaN);
    }

    /** The same, over a given latitude and longitude (degrees) instead. */
    private static void orbitFacing(Minecraft mc, String bodyId, double radii, double latDeg, double lonDeg) {
        command(mc, "gamemode creative @a");
        IntegratedServer server = mc.getSingleplayerServer();
        server.execute(() -> {
            var space = server.getLevel(com.thepearlking.stevespace.world.SpaceDimension.KEY);
            var player = server.getPlayerList().getPlayers().get(0);
            if (player.getVehicle() != null) {
                player.stopRiding();
            }
            var system = ServerUniverse.system();
            var body = system.body(bodyId);
            double t = ServerUniverse.time(server);
            DVec3 here = system.absolutePosition(body, t);
            DVec3 toEarth = system.absolutePosition(system.earth(), t).sub(here).normalize();
            DVec3 toSun = here.scale(-1).normalize();
            var f = system.frameAt(body, t);
            DVec3 at = Double.isNaN(latDeg) ? toEarth.add(toSun.scale(0.3)).normalize() : f.up(Math.toRadians(latDeg), Math.toRadians(lonDeg));
            DVec3 north = f.north();
            StevesSpace.LOGGER.info("[ClientTest] over {} at lat {} lon {} (Earth at lon {})", bodyId,
                    Math.toDegrees(Math.asin(at.dot(f.north()))), Math.toDegrees(Math.atan2(at.dot(f.east90()), at.dot(f.meridian()))),
                    Math.toDegrees(Math.atan2(toEarth.dot(f.east90()), toEarth.dot(f.meridian()))));
            double r = body.radius() * radii;
            DVec3 along = north.cross(at).normalize();
            double mu = body.mu() * com.thepearlking.stevespace.config.SpaceConfig.GRAVITY_SCALE.get();
            var state = new com.thepearlking.stevespace.universe.StateVector(at.scale(r), along.scale(Math.sqrt(mu / r)));
            var blocks = new java.util.HashMap<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState>();
            blocks.put(new net.minecraft.core.BlockPos(0, 0, 0), com.thepearlking.stevespace.registry.ModBlocks.ROCKET_ENGINE.get().defaultBlockState());
            blocks.put(new net.minecraft.core.BlockPos(0, 1, 0), com.thepearlking.stevespace.registry.ModBlocks.PILOT_SEAT.get().defaultBlockState());
            var ship = com.thepearlking.stevespace.vessel.VesselEntity.spawnInOrbit(space,
                    new com.thepearlking.stevespace.vessel.VesselStructure(blocks, new java.util.HashMap<>()), body, state, new org.joml.Quaterniond());
            player.teleportTo(space, ship.getX() + 6, ship.getY() + 2, ship.getZ(), java.util.Set.of(), 0, 0);
        });
        mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
    }

    /** A ship at {@code height} blocks above a giant's one-bar level under the noon Sun, going at {@code fraction} of orbital speed, the pilot aboard. */
    private static void giantDive(Minecraft mc, String bodyId, double height, double fraction) {
        command(mc, "gamemode creative @a");
        IntegratedServer server = mc.getSingleplayerServer();
        server.execute(() -> {
            ServerUniverse.setWarp(server, 1);
            var space = server.getLevel(com.thepearlking.stevespace.world.SpaceDimension.KEY);
            var player = server.getPlayerList().getPlayers().get(0);
            if (player.getVehicle() != null) {
                var old = player.getVehicle();
                player.stopRiding();
                old.discard();
            }
            var system = ServerUniverse.system();
            var body = system.body(bodyId);
            double t = ServerUniverse.time(server);
            DVec3 at = system.absolutePosition(system.sun(), t).sub(system.absolutePosition(body, t)).normalize();
            DVec3 north = system.frameAt(body, t).north();
            double r = body.radius() + height;
            DVec3 along = north.cross(at).normalize();
            double mu = body.mu() * com.thepearlking.stevespace.config.SpaceConfig.GRAVITY_SCALE.get();
            var state = new com.thepearlking.stevespace.universe.StateVector(at.scale(r), along.scale(Math.sqrt(mu / r) * fraction));
            var blocks = new java.util.HashMap<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState>();
            blocks.put(new net.minecraft.core.BlockPos(0, 0, 0), com.thepearlking.stevespace.registry.ModBlocks.ROCKET_ENGINE.get().defaultBlockState());
            blocks.put(new net.minecraft.core.BlockPos(0, 1, 0), com.thepearlking.stevespace.registry.ModBlocks.PROPELLANT_TANK.get().defaultBlockState());
            blocks.put(new net.minecraft.core.BlockPos(0, 2, 0), com.thepearlking.stevespace.registry.ModBlocks.PILOT_SEAT.get().defaultBlockState());
            var ship = com.thepearlking.stevespace.vessel.VesselEntity.spawnInOrbit(space,
                    new com.thepearlking.stevespace.vessel.VesselStructure(blocks, new java.util.HashMap<>()), body, state, new org.joml.Quaterniond());
            ship.setCustomName(net.minecraft.network.chat.Component.literal("Giant probe"));
            player.teleportTo(space, ship.getX(), ship.getY() + 3, ship.getZ(), java.util.Set.of(), 0, 0);
            player.startRiding(ship, true);
        });
        mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
    }

    /** A probe over the night side of a planet ({@code radii} from its centre, at {@code latDeg} toward its north), the player beside it. */
    private static void orbitNight(Minecraft mc, String bodyId, double radii, double latDeg, float pitch) {
        command(mc, "gamemode creative @a");
        IntegratedServer server = mc.getSingleplayerServer();
        server.execute(() -> {
            var space = server.getLevel(com.thepearlking.stevespace.world.SpaceDimension.KEY);
            var player = server.getPlayerList().getPlayers().get(0);
            if (player.getVehicle() != null) {
                player.stopRiding();
            }
            var system = ServerUniverse.system();
            var body = system.body(bodyId);
            double t = ServerUniverse.time(server);
            DVec3 night = system.absolutePosition(body, t).sub(system.absolutePosition(system.sun(), t)).normalize();
            DVec3 north = system.frameAt(body, t).north();
            double lat = Math.toRadians(latDeg);
            DVec3 at = night.scale(Math.cos(lat)).add(north.scale(Math.sin(lat))).normalize();
            double r = body.radius() * radii;
            DVec3 along = north.cross(at).normalize();
            double mu = body.mu() * com.thepearlking.stevespace.config.SpaceConfig.GRAVITY_SCALE.get();
            var state = new com.thepearlking.stevespace.universe.StateVector(at.scale(r), along.scale(Math.sqrt(mu / r)));
            var blocks = new java.util.HashMap<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState>();
            blocks.put(new net.minecraft.core.BlockPos(0, 0, 0), com.thepearlking.stevespace.registry.ModBlocks.ROCKET_ENGINE.get().defaultBlockState());
            blocks.put(new net.minecraft.core.BlockPos(0, 1, 0), com.thepearlking.stevespace.registry.ModBlocks.PROPELLANT_TANK.get().defaultBlockState());
            blocks.put(new net.minecraft.core.BlockPos(0, 2, 0), com.thepearlking.stevespace.registry.ModBlocks.PILOT_SEAT.get().defaultBlockState());
            var ship = com.thepearlking.stevespace.vessel.VesselEntity.spawnInOrbit(space,
                    new com.thepearlking.stevespace.vessel.VesselStructure(blocks, new java.util.HashMap<>()), body, state, new org.joml.Quaterniond());
            ship.setCustomName(net.minecraft.network.chat.Component.literal("Aurora probe"));
            player.teleportTo(space, ship.getX() + 6, ship.getY() + 2, ship.getZ(), java.util.Set.of(), 0, pitch);
        });
        mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
        look(mc, 0, pitch);
    }

    /** A probe in a circular orbit right over a spot on Earth now, the player floating beside it (server thread). */
    private static void orbitOverServer(IntegratedServer server, double x, double z, double radii) {
        var space = server.getLevel(com.thepearlking.stevespace.world.SpaceDimension.KEY);
        var player = server.getPlayerList().getPlayers().get(0);
        if (player.getVehicle() != null) {
            player.stopRiding();
        }
        var system = ServerUniverse.system();
        var body = system.earth();
        double t = ServerUniverse.time(server);
        DVec3 at = com.thepearlking.stevespace.universe.SurfaceFrame.toOrbit(system, body, x, 100, z, 0, 0, 0, t).position().normalize();
        DVec3 north = system.frameAt(body, t).north();
        double r = body.radius() * radii;
        DVec3 along = north.cross(at).normalize();
        double mu = body.mu() * com.thepearlking.stevespace.config.SpaceConfig.GRAVITY_SCALE.get();
        var state = new com.thepearlking.stevespace.universe.StateVector(at.scale(r), along.scale(Math.sqrt(mu / r)));
        var blocks = new java.util.HashMap<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState>();
        blocks.put(new net.minecraft.core.BlockPos(0, 0, 0), com.thepearlking.stevespace.registry.ModBlocks.ROCKET_ENGINE.get().defaultBlockState());
        blocks.put(new net.minecraft.core.BlockPos(0, 1, 0), com.thepearlking.stevespace.registry.ModBlocks.PILOT_SEAT.get().defaultBlockState());
        var ship = com.thepearlking.stevespace.vessel.VesselEntity.spawnInOrbit(space,
                new com.thepearlking.stevespace.vessel.VesselStructure(blocks, new java.util.HashMap<>()), body, state, new org.joml.Quaterniond());
        player.teleportTo(space, ship.getX() + 6, ship.getY() + 2, ship.getZ(), java.util.Set.of(), 0, 89);
    }

    /** A small probe in a circular orbit {@code radii} solar radii from the Sun's centre, the player floating beside it. */
    private static void sunOrbit(Minecraft mc, double radii, float pitch) {
        command(mc, "gamemode creative @a");
        IntegratedServer server = mc.getSingleplayerServer();
        server.execute(() -> {
            var space = server.getLevel(com.thepearlking.stevespace.world.SpaceDimension.KEY);
            var player = server.getPlayerList().getPlayers().get(0);
            if (player.getVehicle() != null) {
                player.stopRiding();
            }
            var sun = com.thepearlking.stevespace.universe.ServerUniverse.system().sun();
            double r = sun.radius() * radii;
            // Hang over the biggest sunspot group, in its plane of rotation.
            double t = com.thepearlking.stevespace.universe.ServerUniverse.time(server);
            DVec3 over = new DVec3(1, 0, 0);
            double biggest = 0;
            for (var spot : com.thepearlking.stevespace.universe.SolarActivity.spots(t)) {
                if (spot.size() > biggest) {
                    biggest = spot.size();
                    over = new DVec3(Math.cos(spot.latitude()) * Math.cos(spot.longitude()), Math.cos(spot.latitude()) * Math.sin(spot.longitude()), Math.sin(spot.latitude()));
                }
            }
            StevesSpace.LOGGER.info("[ClientTest] sun probe over a spot of size {}", biggest);
            DVec3 along = new DVec3(0, 0, 1).cross(over).normalize();
            var state = new com.thepearlking.stevespace.universe.StateVector(over.scale(r), along.scale(Math.sqrt(sun.mu() / r)));
            var blocks = new java.util.HashMap<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState>();
            blocks.put(new net.minecraft.core.BlockPos(0, 0, 0), com.thepearlking.stevespace.registry.ModBlocks.ROCKET_ENGINE.get().defaultBlockState());
            blocks.put(new net.minecraft.core.BlockPos(0, 1, 0), com.thepearlking.stevespace.registry.ModBlocks.PROPELLANT_TANK.get().defaultBlockState());
            blocks.put(new net.minecraft.core.BlockPos(0, 2, 0), com.thepearlking.stevespace.registry.ModBlocks.PILOT_SEAT.get().defaultBlockState());
            var ship = com.thepearlking.stevespace.vessel.VesselEntity.spawnInOrbit(space,
                    new com.thepearlking.stevespace.vessel.VesselStructure(blocks, new java.util.HashMap<>()), sun, state, new org.joml.Quaterniond());
            ship.setCustomName(net.minecraft.network.chat.Component.literal("Sun probe"));
            player.teleportTo(space, ship.getX() + 6, ship.getY() + 2, ship.getZ(), java.util.Set.of(), 0, pitch);
        });
        mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
        look(mc, 0, pitch);
    }

    /** Points the camera at the latest detonation's place in the sky. */
    private static void lookAtNuke(Minecraft mc) {
        var events = com.thepearlking.stevespace.client.nuke.NukeClient.events();
        if (events.isEmpty() || mc.level == null) {
            return;
        }
        var e = events.get(events.size() - 1);
        var state = com.thepearlking.stevespace.client.sky.SkyState.compute(mc.gameRenderer.getMainCamera(), mc.level, 1);
        if (state == null) {
            return;
        }
        DVec3 d = state.sky().toLocal(e.absolute().sub(state.sky().position())).normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x(), d.z()));
        float pitch = (float) -Math.toDegrees(Math.asin(d.y()));
        look(mc, yaw, Math.max(-85, pitch + 15));
    }

    /** Points the camera at a body, a few degrees above it if asked; resolved a few ticks later. */
    private static void lookAt(Minecraft mc, String bodyId, float raiseDegrees) {
        new Thread(() -> {
            try {
                Thread.sleep(1500); // wait for the time sync to arrive
            } catch (InterruptedException ignored) {
            }
            mc.execute(() -> {
                SolarSystem sys = ClientUniverse.system();
                double t = ClientUniverse.time();
                LocalSky sky = ClientUniverse.skyAt(mc.player.getEyePosition(), t);
                DVec3 d;
                if (sky != null) {
                    d = sky.offsetTo(sys, sys.body(bodyId), t).normalize();
                } else {
                    // Out in space: from the sky as drawn there.
                    var state = com.thepearlking.stevespace.client.sky.SkyState.compute(mc.gameRenderer.getMainCamera(), mc.level, 1);
                    if (state == null) {
                        return;
                    }
                    d = state.sky().toLocal(sys.absolutePosition(sys.body(bodyId), t).sub(state.sky().position())).normalize();
                }
                float yaw = (float) Math.toDegrees(Math.atan2(-d.x(), d.z()));
                float pitch = (float) -Math.toDegrees(Math.asin(d.y())) - raiseDegrees;
                StevesSpace.LOGGER.info("[ClientTest] {} at elevation {}°", bodyId, Math.toDegrees(Math.asin(d.y())));
                look(mc, yaw, pitch);
            });
        }, "stevespace-clienttest").start();
    }

}
