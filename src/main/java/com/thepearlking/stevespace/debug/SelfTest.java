package com.thepearlking.stevespace.debug;

import com.thepearlking.stevespace.StevesSpace;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Headless checks run by {@code ./gradlew runServerTest} (environment STEVESPACE_SELFTEST=1).
 * Each check tests a real outcome in a real world, prints PASS/FAIL, and the server stops.
 * Runs in run-test/, never in the player's run/ folder.
 */
public final class SelfTest {

    private static final List<String> failures = new ArrayList<>();
    private static int passed;

    private SelfTest() {
    }

    public static boolean enabled() {
        return "1".equals(System.getenv("STEVESPACE_SELFTEST"));
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        StevesSpace.LOGGER.info("[SelfTest] starting");
        try {
            if ("rescue".equals(System.getenv("STEVESPACE_SELFTEST_ONLY"))) {
                rescueTest(server);
                return;
            }
            if ("inspect".equals(System.getenv("STEVESPACE_SELFTEST_ONLY"))) {
                inspectRockets(server);
                return;
            }
            if ("vessels".equals(System.getenv("STEVESPACE_SELFTEST_ONLY"))) {
                VesselTests.schedule(server);
                return;
            }
            if ("return".equals(System.getenv("STEVESPACE_SELFTEST_ONLY"))) {
                ReturnTests.schedule(server);
                return;
            }
            if ("parking".equals(System.getenv("STEVESPACE_SELFTEST_ONLY"))) {
                ParkingTests.schedule(server);
                return;
            }
            if ("scan".equals(System.getenv("STEVESPACE_SELFTEST_ONLY"))) {
                ScanTests.run(server);
                return;
            }
            if ("dock".equals(System.getenv("STEVESPACE_SELFTEST_ONLY"))) {
                DockTests.schedule(server);
                return;
            }
            if ("dockpower".equals(System.getenv("STEVESPACE_SELFTEST_ONLY"))) {
                DockTests.listAndPower(server);
                return;
            }
            if ("dockquick".equals(System.getenv("STEVESPACE_SELFTEST_ONLY"))) {
                DockTests.dock(server, !"0".equals(System.getenv("STEVESPACE_RCS")), 0.3, 300, 0);
                return;
            }
            if ("power".equals(System.getenv("STEVESPACE_SELFTEST_ONLY"))) {
                PowerTests.schedule(server);
                return;
            }
            if ("habitat".equals(System.getenv("STEVESPACE_SELFTEST_ONLY"))) {
                HabitatTests.schedule(server);
                return;
            }
            if ("names".equals(System.getenv("STEVESPACE_SELFTEST_ONLY"))) {
                NameTests.schedule(server);
                return;
            }
            if ("realearth".equals(System.getenv("STEVESPACE_SELFTEST_ONLY"))) {
                RealEarthTests.run(server);
                finish(server);
                return;
            }
            if ("commands".equals(System.getenv("STEVESPACE_SELFTEST_ONLY"))) {
                CommandTests.schedule(server);
                return;
            }
            if ("nuke".equals(System.getenv("STEVESPACE_SELFTEST_ONLY"))) {
                NukeTests.schedule(server);
                return;
            }
            if ("remote".equals(System.getenv("STEVESPACE_SELFTEST_ONLY"))) {
                RemoteTests.schedule(server);
                return;
            }
            if ("radio".equals(System.getenv("STEVESPACE_SELFTEST_ONLY"))) {
                RadioTests.schedule(server);
                return;
            }
            if ("userorbit".equals(System.getenv("STEVESPACE_SELFTEST_ONLY"))) {
                userOrbitTest(server);
                return;
            }
            if ("blockship".equals(System.getenv("STEVESPACE_SELFTEST_ONLY"))) {
                BlockShipTests.schedule(server);
                return;
            }
            if ("seam".equals(System.getenv("STEVESPACE_SELFTEST_ONLY"))) {
                later(server, 2, () -> {
                    var level = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(StevesSpace.MODID, "hygiea")));
                    var geometry = com.thepearlking.stevespace.world.WorldWrap.geometryOf(level);
                    int c = geometry.circumference();
                    int half = c / 2;
                    // Generated ground: every block of a strip past the east edge equals its twin inside the west edge.
                    int same = 0;
                    int differ = 0;
                    for (int x = half; x < half + 24; x++) {
                        for (int z = -40; z < 40; z += 7) {
                            for (int y = level.getMinBuildHeight(); y < 200; y++) {
                                if (level.getBlockState(new BlockPos(x, y, z)) == level.getBlockState(new BlockPos(x - c, y, z))) {
                                    same++;
                                } else {
                                    differ++;
                                }
                            }
                        }
                    }
                    check("seam generated ground matches", differ == 0, same + " same, " + differ + " differ");
                    // A block placed by the west edge shows up past the east edge, and breaking it there clears it here.
                    BlockPos here = new BlockPos(-half + 3, 150, 5);
                    level.setBlockAndUpdate(here, net.minecraft.world.level.block.Blocks.GOLD_BLOCK.defaultBlockState());
                    boolean copied = level.getBlockState(here.offset(c, 0, 0)).is(net.minecraft.world.level.block.Blocks.GOLD_BLOCK);
                    level.setBlockAndUpdate(here.offset(c, 0, 0), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                    boolean cleared = level.getBlockState(here).isAir();
                    check("seam build copied across", copied && cleared, "copied " + copied + ", cleared back " + cleared);
                    // Over the south pole: past it is the far side of the pole, half way round, mirrored north-south.
                    int pole = geometry.poleDistance();
                    int poleSame = 0;
                    int poleDiffer = 0;
                    for (int k = 0; k < 20; k++) {
                        for (int x = -60; x < 60; x += 9) {
                            for (int y = level.getMinBuildHeight(); y < 200; y++) {
                                var a = level.getBlockState(new BlockPos(x, y, pole + k));
                                var b = level.getBlockState(new BlockPos(com.thepearlking.stevespace.world.WorldSeam.canonicalX(x + half, c), y, pole - 1 - k));
                                if (a == b) {
                                    poleSame++;
                                } else {
                                    if (poleDiffer++ < 6) {
                                        StevesSpace.LOGGER.info("[SelfTest] pole differs at {} {} {}: {} vs {}", x, y, pole + k, a, b);
                                    }
                                }
                            }
                        }
                    }
                    check("seam ground past the pole matches", poleDiffer == 0, poleSame + " same, " + poleDiffer + " differ");
                    BlockPos near = new BlockPos(10, 150, pole - 4);
                    level.setBlockAndUpdate(near, net.minecraft.world.level.block.Blocks.OAK_STAIRS.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.StairBlock.FACING, net.minecraft.core.Direction.NORTH));
                    var twin = level.getBlockState(new BlockPos(com.thepearlking.stevespace.world.WorldSeam.canonicalX(10 + half, c), 150, pole + 3));
                    check("seam build copied over the pole", twin.is(net.minecraft.world.level.block.Blocks.OAK_STAIRS)
                            && twin.getValue(net.minecraft.world.level.block.StairBlock.FACING) == net.minecraft.core.Direction.SOUTH, String.valueOf(twin));
                    finish(server);
                });
                return;
            }
            if ("dive".equals(System.getenv("STEVESPACE_SELFTEST_ONLY"))) {
                DiveTests.run(server);
                return;
            }
            if ("landall".equals(System.getenv("STEVESPACE_SELFTEST_ONLY"))) {
                if (System.getenv("STEVESPACE_ACHERON_IN") != null) {
                    // Landing with Acheron passing (it must not take ships that belong to the Moon).
                    com.thepearlking.stevespace.world.AcheronEvent.testPass(server, Double.parseDouble(System.getenv("STEVESPACE_ACHERON_IN")));
                }
                LandAllTests.run(server, 0);
                return;
            }
            if ("orbitfix".equals(System.getenv("STEVESPACE_SELFTEST_ONLY"))) {
                OrbitFixTests.schedule(server);
                return;
            }
            if ("travel".equals(System.getenv("STEVESPACE_SELFTEST_ONLY"))) {
                TravelTests.schedule(server);
                return;
            }
            if ("maneuver".equals(System.getenv("STEVESPACE_SELFTEST_ONLY"))) {
                ManeuverTests.schedule(server);
                return;
            }
            if ("docking".equals(System.getenv("STEVESPACE_SELFTEST_ONLY"))) {
                DockingTests.schedule(server);
                return;
            }
            WorldGenTests.run(server);
            UniverseTests.schedule(server);
            BodyWorldTests.run(server);
            VesselTests.schedule(server);
            OrbitTests.schedule(server);
            DockingTests.schedule(server);
            ParkingTests.schedule(server);
            RadioTests.schedule(server);
            HabitatTests.schedule(server);
            ScanTests.run(server);
            PowerTests.schedule(server);
            ManeuverTests.schedule(server);
        } catch (Throwable t) {
            fail("exception: " + t);
            StevesSpace.LOGGER.error("[SelfTest] crashed", t);
        }
        later(server, 0, () -> {
        });
    }

    // ---------------------------------------------------------------------------------------
    // Tick scheduling: some checks need the server to run for a while.
    // ---------------------------------------------------------------------------------------

    /** A step runs {@code delay} ticks after the step before it has finished. */
    private record Step(int delay, Runnable action) {
    }

    private static final java.util.ArrayDeque<Step> steps = new java.util.ArrayDeque<>();
    private static int previousDone = -1;

    /** Runs {@code action} {@code delay} ticks after the previously scheduled step. */
    /**
     * For a copy of a player's world (STEVESPACE_INSPECT=x,z): finds the pilot seats near that
     * spot, prints the launch check for each, and tries a launch the way a creative player does.
     */
    private static void inspectRockets(MinecraftServer server) {
        String[] at = System.getenv().getOrDefault("STEVESPACE_INSPECT", "0,0").split(",");
        int cx = Integer.parseInt(at[0].trim());
        int cz = Integer.parseInt(at[1].trim());
        ServerLevel level = server.overworld();
        java.util.List<BlockPos> seats = new java.util.ArrayList<>();
        for (int x = cx - 64; x <= cx + 64; x++) {
            for (int z = cz - 64; z <= cz + 64; z++) {
                level.getChunk(x >> 4, z >> 4);
                for (int y = -60; y < 300; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (level.getBlockState(pos).getBlock() instanceof com.thepearlking.stevespace.block.PilotSeatBlock) {
                        seats.add(pos);
                    }
                }
            }
        }
        StevesSpace.LOGGER.info("[Inspect] {} pilot seats near {},{}", seats.size(), cx, cz);
        // What our own blocks are standing around there (pads, loaders, consoles...).
        java.util.Map<String, Integer> ours = new java.util.TreeMap<>();
        for (int x = cx - 24; x <= cx + 24; x++) {
            for (int z = cz - 24; z <= cz + 24; z++) {
                for (int y = 40; y < 140; y++) {
                    var st = level.getBlockState(new BlockPos(x, y, z));
                    String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(st.getBlock()).toString();
                    if (id.startsWith("stevespace:")) {
                        ours.merge(id + "@y" + y, 1, Integer::sum);
                    }
                }
            }
        }
        ours.forEach((k, n) -> StevesSpace.LOGGER.info("[Inspect] around: {} x{}", k, n));
        // Load the first space cells so ships parked or orbiting there show up.
        ServerLevel space = server.getLevel(com.thepearlking.stevespace.world.SpaceDimension.KEY);
        for (int c = 1; c <= 12; c++) {
            var centre = com.thepearlking.stevespace.world.SpaceDimension.cellCenter(c);
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    space.getChunk(((int) centre.x >> 4) + dx, ((int) centre.z >> 4) + dz);
                }
            }
        }
        for (int[] rc : new int[][]{{2, -3}, {2, -4}, {3, -3}, {3, -4}, {7, 0}, {7, -1}, {8, 0}, {8, -1}}) {
            for (int dx = 0; dx < 32; dx += 4) {
                for (int dz = 0; dz < 32; dz += 4) {
                    space.getChunk(rc[0] * 32 + dx, rc[1] * 32 + dz);
                }
            }
        }
        waitUntil(server, 100, () -> false, () -> {
            for (var v : VesselEntity_all(server)) {
                StevesSpace.LOGGER.info("[Inspect] vessel {} in {} at {} orbit={}", v.getName().getString(), v.level().dimension().location(),
                        v.blockPosition(), v.orbital() == null ? null : v.orbital().body().id() + " alt " + (int) v.orbital().altitude());
            }
        });
        for (var v : VesselEntity_all(server)) {
            StevesSpace.LOGGER.info("[Inspect] vessel {} in {} at {} orbit={}", v.getName().getString(), v.level().dimension().location(),
                    v.blockPosition(), v.orbital() == null ? null : v.orbital().body().id() + " alt " + (int) v.orbital().altitude());
        }
        for (BlockPos seat : seats) {
            var report = com.thepearlking.stevespace.vessel.VesselReport.analyze(level, seat);
            StevesSpace.LOGGER.info("[Inspect] seat {}: blocks={} engines={} seats={} thrust={} mass={} twr={} prop={} problems={}",
                    seat, report.blocks(), report.engines(), report.seats(), report.thrust(), report.totalMass(),
                    String.format("%.2f", report.thrustToWeight()), report.propellant(), report.problems());
            var scan = com.thepearlking.stevespace.vessel.VesselAssembler.scan(level, seat, 6000);
            if (scan.ok()) {
                java.util.Map<String, Integer> counts = new java.util.TreeMap<>();
                scan.structure().blocks().forEach((rel, state) -> counts.merge(
                        net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString()
                                + state.getValues().toString(), 1, Integer::sum));
                counts.forEach((k, n) -> StevesSpace.LOGGER.info("[Inspect]   {} x{}", k, n));
            } else {
                StevesSpace.LOGGER.info("[Inspect]   scan failed: {}", scan.error());
            }
        }
        if (seats.isEmpty()) {
            return;
        }
        BlockPos seat = seats.get(0);
        var pilot = VesselTests.mockPlayer(level, "Inspector");
        pilot.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        var seatEntity = com.thepearlking.stevespace.block.SeatEntity.create(level, seat);
        level.addFreshEntity(seatEntity);
        pilot.startRiding(seatEntity, true);
        final int[] tick = new int[1];
        later(server, 2, () -> { });
        waitUntil(server, 60, () -> {
            // Like the client: the throttle every tick.
            com.thepearlking.stevespace.vessel.VesselServer.onControls(pilot, new com.thepearlking.stevespace.vessel.VesselControls(1, 0, 0, 0));
            if (com.thepearlking.stevespace.vessel.VesselEntity.shipOf(pilot) instanceof com.thepearlking.stevespace.vessel.VesselEntity v && tick[0]++ % 5 == 0) {
                StevesSpace.LOGGER.info("[Inspect] t={} y={} v={} throttle={} grounded={} prop={}", tick[0], String.format("%.2f", v.getY()),
                        v.velocity(), v.throttle(), v.isGrounded(), v.propellant());
            }
            return false;
        }, () -> { });
        later(server, 1, () -> {
            var vehicle = pilot.getVehicle();
            StevesSpace.LOGGER.info("[Inspect] after throttle: vehicle={} y={}", vehicle, vehicle == null ? 0 : vehicle.getY());
            check("inspected-rocket-launches", vehicle instanceof com.thepearlking.stevespace.vessel.VesselEntity, String.valueOf(vehicle));
        });
    }

    /** On a copy of the player's world: sit in their rocket near where they stand and press "Fly to orbit". */
    private static void userOrbitTest(MinecraftServer server) {
        ServerLevel level = server.overworld();
        final net.minecraft.server.level.ServerPlayer[] p = new net.minecraft.server.level.ServerPlayer[1];
        BlockPos stand = new BlockPos(17, 89, -4763);
        later(server, 2, () -> {
            for (int cx = (stand.getX() >> 4) - 3; cx <= (stand.getX() >> 4) + 3; cx++) {
                for (int cz = (stand.getZ() >> 4) - 3; cz <= (stand.getZ() >> 4) + 3; cz++) {
                    level.getChunk(cx, cz);
                }
            }
            BlockPos seat = null;
            for (BlockPos q : BlockPos.betweenClosed(stand.offset(-40, -30, -40), stand.offset(40, 60, 40))) {
                if (level.getBlockState(q).getBlock() instanceof com.thepearlking.stevespace.block.PilotSeatBlock) {
                    seat = q.immutable();
                    break;
                }
            }
            StevesSpace.LOGGER.info("[UserOrbit] pilot seat {}", seat);
            if (seat == null) {
                check("user-rocket-found", false, "");
                return;
            }
            var report = com.thepearlking.stevespace.vessel.VesselReport.analyze(level, seat);
            StevesSpace.LOGGER.info("[UserOrbit] blocks={} engines={} twr={} dv={} problems={}", report.blocks(), report.engines(),
                    report.thrustToWeight(), report.deltaV(), report.problems());
            p[0] = VesselTests.mockPlayer(level, "Dev");
            p[0].setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            p[0].teleportTo(level, seat.getX() + 0.5, seat.getY() + 1, seat.getZ() + 0.5, java.util.Set.of(), 0, 0);
            var seatEntity = com.thepearlking.stevespace.block.SeatEntity.create(level, seat);
            level.addFreshEntity(seatEntity);
            p[0].startRiding(seatEntity, true);
            com.thepearlking.stevespace.vessel.VesselServer.onAutopilot(p[0], com.thepearlking.stevespace.vessel.Autopilot.Program.ORBIT);
        });
        // What the player did: get up and walk about during the climb, sit back down, and the
        // seat sends whatever throttle it last had (full). None of it may stop the autopilot.
        later(server, 60, () -> {
            if (com.thepearlking.stevespace.vessel.VesselEntity.shipOf(p[0]) instanceof com.thepearlking.stevespace.vessel.VesselEntity v) {
                v.requestDismount(p[0]);
                com.thepearlking.stevespace.vessel.VesselServer.onControls(p[0], new com.thepearlking.stevespace.vessel.VesselControls(1, 0.5, 0, 0));
                StevesSpace.LOGGER.info("[UserOrbit] stood up: {}", v.autopilotStatus());
            }
        });
        later(server, 80, () -> {
            if (com.thepearlking.stevespace.vessel.VesselEntity.shipOf(p[0]) instanceof com.thepearlking.stevespace.vessel.VesselEntity v) {
                v.requestDismount(p[0]);
                com.thepearlking.stevespace.vessel.VesselServer.onControls(p[0], new com.thepearlking.stevespace.vessel.VesselControls(1, 0, 0, 0));
                StevesSpace.LOGGER.info("[UserOrbit] sat back down, seat sent full throttle: {}", v.autopilotStatus());
            }
        });
        final int[] t = new int[1];
        waitUntil(server, 20 * 300, () -> {
            if (++t[0] % 40 == 0 && p[0] != null && com.thepearlking.stevespace.vessel.VesselEntity.shipOf(p[0]) instanceof com.thepearlking.stevespace.vessel.VesselEntity v) {
                var body = com.thepearlking.stevespace.universe.ServerUniverse.system().earth();
                double mu = body.mu() * SpaceConfigValue.gravity();
                String orbit = v.orbital() != null ? OrbitTests.describe(v) : "y=" + (int) v.mapY() + " v=" + String.format("%.1f", v.velocity().length());
                StevesSpace.LOGGER.info("[UserOrbit] t={}s {} {} throttle={} status={} debug={}", t[0] / 20, v.level().dimension().location(), orbit,
                        String.format("%.2f", v.throttle()), v.autopilotStatus(), v.autopilotDebug());
            }
            return t[0] > 100 && p[0] != null && com.thepearlking.stevespace.vessel.VesselEntity.shipOf(p[0]) instanceof com.thepearlking.stevespace.vessel.VesselEntity v && !v.autopilotEngaged();
        }, () -> {
            if (com.thepearlking.stevespace.vessel.VesselEntity.shipOf(p[0]) instanceof com.thepearlking.stevespace.vessel.VesselEntity v && v.orbital() != null) {
                var o = v.orbital();
                var el = com.thepearlking.stevespace.universe.Kepler.elementsFromState(o.state(), o.body().mu() * SpaceConfigValue.gravity(), o.time());
                check("user-rocket-reaches-orbit", el.eccentricity() < 0.05, OrbitTests.describe(v));
                check("engines-quiet-after-autopilot", v.throttle() < 0.01, "throttle " + v.throttle());
            } else {
                check("user-rocket-reaches-orbit", false, "vehicle " + p[0].getVehicle());
            }
        });
    }

    /** On a copy of the player's world: stand where they were saved in space and get their ship back. */
    private static void rescueTest(MinecraftServer server) {
        ServerLevel space = server.getLevel(com.thepearlking.stevespace.world.SpaceDimension.KEY);
        final net.minecraft.server.level.ServerPlayer[] p = new net.minecraft.server.level.ServerPlayer[1];
        later(server, 2, () -> {
            p[0] = VesselTests.mockPlayer(server.overworld(), "Rescuer");
            p[0].setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            p[0].teleportTo(space, 4100.2, 127.5, 2.5, java.util.Set.of(), 0, 0);
        });
        waitUntil(server, 400, () -> com.thepearlking.stevespace.world.ParkedShips.get(server).get(1) != null, () -> {
            var parked = com.thepearlking.stevespace.world.ParkedShips.get(server).get(1);
            check("rescue-ship-parked", parked != null, String.valueOf(parked));
            if (parked == null) {
                return;
            }
            BlockPos seat = null;
            int count = 0;
            for (BlockPos q : BlockPos.betweenClosed(parked.min(), parked.max())) {
                var st = space.getBlockState(q);
                if (!st.isAir()) {
                    count++;
                }
                if (st.getBlock() instanceof com.thepearlking.stevespace.block.PilotSeatBlock) {
                    seat = q.immutable();
                }
            }
            StevesSpace.LOGGER.info("[Rescue] {} blocks between {} and {}, seat {}, player now {} (in a block: {})", count, parked.min(), parked.max(), seat,
                    p[0].position(), !space.noCollision(p[0]));
            if (seat != null) {
                var report = com.thepearlking.stevespace.vessel.VesselReport.analyze(space, seat);
                StevesSpace.LOGGER.info("[Rescue] launch check: blocks={} problems={} parkedAt={}", report.blocks(), report.problems(),
                        com.thepearlking.stevespace.world.ParkedShips.get(server).at(seat.getX()));
                var seatEntity = com.thepearlking.stevespace.block.SeatEntity.create(space, seat);
                space.addFreshEntity(seatEntity);
                p[0].startRiding(seatEntity, true);
                com.thepearlking.stevespace.vessel.VesselServer.onControls(p[0], new com.thepearlking.stevespace.vessel.VesselControls(0.3, 0, 0, 0));
            }
        });
        later(server, 40, () -> {
            check("rescue-ship-flies", com.thepearlking.stevespace.vessel.VesselEntity.shipOf(p[0]) instanceof com.thepearlking.stevespace.vessel.VesselEntity v
                    && v.orbital() != null, "vehicle=" + p[0].getVehicle() + " name=" + (p[0].getVehicle() == null ? "" : p[0].getVehicle().getName().getString()));
            com.thepearlking.stevespace.vessel.VesselServer.onControls(p[0], com.thepearlking.stevespace.vessel.VesselControls.IDLE);
            com.thepearlking.stevespace.vessel.VesselServer.onAutopilot(p[0], com.thepearlking.stevespace.vessel.Autopilot.Program.LAND);
        });
        final int[] t = new int[1];
        waitUntil(server, 20 * 900, () -> {
            if (++t[0] % 200 == 0 && com.thepearlking.stevespace.vessel.VesselEntity.shipOf(p[0]) instanceof com.thepearlking.stevespace.vessel.VesselEntity v) {
                StevesSpace.LOGGER.info("[Rescue] t={}s {} {} warp={} {}", t[0] / 20, v.level().dimension().location(),
                        v.orbital() == null ? "y=" + (int) v.mapY() : "alt=" + (int) v.orbital().altitude() + " e=" + String.format("%.2f", v.orbital().elements(1).eccentricity()),
                        com.thepearlking.stevespace.universe.UniverseClock.get(server).warp(), v.autopilotStatus());
            }
            return !(com.thepearlking.stevespace.vessel.VesselEntity.shipOf(p[0]) instanceof com.thepearlking.stevespace.vessel.VesselEntity) && p[0].level() == server.overworld();
        }, () -> check("rescue-lands-on-earth", p[0].level() == server.overworld() && !(com.thepearlking.stevespace.vessel.VesselEntity.shipOf(p[0]) instanceof com.thepearlking.stevespace.vessel.VesselEntity),
                "player in " + p[0].level().dimension().location() + " at " + p[0].blockPosition() + " vehicle=" + p[0].getVehicle()));
    }

    private static java.util.List<com.thepearlking.stevespace.vessel.VesselEntity> VesselEntity_all(MinecraftServer server) {
        java.util.List<com.thepearlking.stevespace.vessel.VesselEntity> all = new java.util.ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (var e : level.getAllEntities()) {
                if (e instanceof com.thepearlking.stevespace.vessel.VesselEntity v) {
                    all.add(v);
                }
            }
        }
        return all;
    }

    static void later(MinecraftServer server, int delay, Runnable action) {
        steps.add(new Step(delay, action));
    }

    /**
     * Polls {@code condition} once per tick (up to {@code maxTicks}), then runs {@code then}.
     * Later steps scheduled with {@link #later} wait until this finishes.
     */
    static void waitUntil(MinecraftServer server, int maxTicks, java.util.function.BooleanSupplier condition, Runnable then) {
        final int[] waited = new int[1];
        Runnable[] poll = new Runnable[1];
        poll[0] = () -> {
            if (condition.getAsBoolean() || ++waited[0] >= maxTicks) {
                then.run();
            } else {
                steps.addFirst(new Step(1, poll[0]));
            }
        };
        later(server, 1, poll[0]);
    }

    @SubscribeEvent
    public static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        int now = server.getTickCount();
        if (previousDone < 0) {
            previousDone = now;
        }
        while (!steps.isEmpty() && previousDone + steps.peek().delay() <= now) {
            Step step = steps.poll();
            try {
                step.action().run();
            } catch (Throwable t) {
                fail("exception: " + t);
                StevesSpace.LOGGER.error("[SelfTest] step crashed", t);
            }
            previousDone = now;
            if (steps.isEmpty()) {
                finish(server);
            }
        }
    }

    private static void finish(MinecraftServer server) {
        StevesSpace.LOGGER.info("[SelfTest] RESULT {} passed, {} failed", passed, failures.size());
        for (String f : failures) {
            StevesSpace.LOGGER.info("[SelfTest] FAILED: {}", f);
        }
        server.halt(false);
    }

    public static void check(String name, boolean ok, String detail) {
        if (ok) {
            passed++;
            StevesSpace.LOGGER.info("[SelfTest] PASS {} {}", name, detail);
        } else {
            fail(name + " " + detail);
        }
    }

    private static void fail(String what) {
        failures.add(what);
        StevesSpace.LOGGER.info("[SelfTest] FAIL {}", what);
    }

    /** The real sun drives vanilla day time; /time set moves the real sun; warp speeds the clock. */
    static final class UniverseTests {
        static void schedule(MinecraftServer server) {
            ServerLevel level = server.overworld();
            var clock = com.thepearlking.stevespace.universe.UniverseClock.get(server);
            later(server, 5, () -> {
                long within = level.getDayTime() % 24000;
                // New world starts in the morning: vanilla 23000..24000 or 0..3000. (Only a new
                // world: the test world is reused, and earlier runs move its clock on.)
                if (level.getGameTime() < 1200) {
                    check("world-starts-morning", within > 22500 || within < 3500, "dayTime%24000=" + within);
                } else {
                    StevesSpace.LOGGER.info("[SelfTest] skip world-starts-morning (not a new world: {} ticks old)", level.getGameTime());
                }
                check("clock-started", clock.isStarted(), "t=" + clock.time());
            });
            final double[] mark = new double[1];
            later(server, 1, () -> {
                // The test world is reused: a run that ended mid-warp leaves its clock warping.
                com.thepearlking.stevespace.universe.ServerUniverse.setWarp(server, 1);
                mark[0] = clock.time();
            });
            later(server, 40, () -> check("clock-advances", Math.abs(clock.time() - mark[0] - 2.0) < 0.3,
                    String.format("advanced %.2f s in 40 ticks", clock.time() - mark[0])));
            later(server, 1, () -> {
                level.setDayTime(level.getDayTime() - level.getDayTime() % 24000 + 18000); // like /time set midnight
            });
            later(server, 3, () -> {
                var sys = com.thepearlking.stevespace.universe.ServerUniverse.system();
                var geo = new com.thepearlking.stevespace.world.planet.PlanetGeometry(sys.earth().radius());
                BlockPos spawn = level.getSharedSpawnPos();
                double el = com.thepearlking.stevespace.universe.LocalSky.at(sys, sys.earth(), geo.latitude(spawn.getZ()),
                        geo.longitude(spawn.getX()), 0, clock.time()).elevationOf(sys, sys.sun(), clock.time());
                double hour = com.thepearlking.stevespace.universe.LocalSky.at(sys, sys.earth(), geo.latitude(spawn.getZ()),
                        geo.longitude(spawn.getX()), 0, clock.time()).hourAngle(sys, sys.sun(), clock.time());
                check("time-set-moves-real-sun", Math.toDegrees(el) < -10 && Math.abs(Math.abs(Math.toDegrees(hour)) - 180) < 3,
                        String.format("sun %.1f°, hour angle %.1f°", Math.toDegrees(el), Math.toDegrees(hour)));
            });
            later(server, 1, () -> {
                mark[0] = clock.time();
                com.thepearlking.stevespace.universe.ServerUniverse.setWarp(server, 1000);
            });
            later(server, 20, () -> {
                double advanced = clock.time() - mark[0];
                com.thepearlking.stevespace.universe.ServerUniverse.setWarp(server, 1);
                check("warp-1000x", Math.abs(advanced - 1000) < 60, String.format("advanced %.0f s in 1 s", advanced));
            });
            // Changing the day length mid-game must not make the sun jump.
            final double[] hourBefore = new double[1];
            later(server, 2, () -> {
                hourBefore[0] = sunHour(server, clock.time());
                com.thepearlking.stevespace.config.SpaceConfig.EARTH_DAY_MINUTES.set(40.0);
            });
            later(server, 2, () -> {
                double after = sunHour(server, clock.time());
                double jump = Math.abs(Math.toDegrees(after - hourBefore[0]));
                jump = Math.min(jump, 360 - jump);
                double day = com.thepearlking.stevespace.universe.ServerUniverse.system().earthDaySeconds();
                check("day-length-change-smooth", jump < 2 && Math.abs(day - 2400) < 1e-6,
                        String.format("sun moved %.2f° over 2 ticks, day now %.0f s", jump, day));
                com.thepearlking.stevespace.config.SpaceConfig.EARTH_DAY_MINUTES.set(20.0);
            });
        }
    }

    /** Rockets: build from blocks, launch, climb, crash, land softly. */
    public static final class VesselTests {

        /** A server player with a dummy connection, like vanilla game tests use. */
        static net.minecraft.server.level.ServerPlayer mockPlayer(ServerLevel level, String name) {
            var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
                    new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes(name.getBytes()), name), false);
            var player = new net.minecraft.server.level.ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
            var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
            new io.netty.channel.embedded.EmbeddedChannel(connection);
            level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            return player;
        }

        /** Builds a small rocket standing on a launch pad; returns the pilot seat position. */
        public static BlockPos buildRocket(ServerLevel level, BlockPos ground, int tanks) {
            BlockPos pad = ground;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    level.setBlockAndUpdate(pad.offset(dx, 0, dz), com.thepearlking.stevespace.registry.ModBlocks.LAUNCH_PAD.get().defaultBlockState());
                }
            }
            BlockPos engine = pad.above();
            level.setBlockAndUpdate(engine, com.thepearlking.stevespace.registry.ModBlocks.ROCKET_ENGINE.get().defaultBlockState());
            for (int i = 1; i <= tanks; i++) {
                level.setBlockAndUpdate(engine.above(i), com.thepearlking.stevespace.registry.ModBlocks.PROPELLANT_TANK.get().defaultBlockState());
                if (level.getBlockEntity(engine.above(i)) instanceof com.thepearlking.stevespace.block.PropellantTankBlockEntity tank) {
                    tank.setPropellant(com.thepearlking.stevespace.block.PropellantTankBlock.CAPACITY);
                }
            }
            BlockPos seat = engine.above(tanks + 1);
            level.setBlockAndUpdate(seat, com.thepearlking.stevespace.registry.ModBlocks.PILOT_SEAT.get().defaultBlockState());
            level.setBlockAndUpdate(seat.north(), com.thepearlking.stevespace.registry.ModBlocks.FLIGHT_COMPUTER.get().defaultBlockState());
            level.setBlockAndUpdate(seat.above(), com.thepearlking.stevespace.registry.ModBlocks.HULL_WHITE.get().defaultBlockState());
            return seat;
        }

        public static BlockPos clearArea(ServerLevel level, int x, int z) {
            // A flat, empty test site high above the terrain, built on a stone floor.
            int y = 200;
            for (int dx = -6; dx <= 6; dx++) {
                for (int dz = -6; dz <= 6; dz++) {
                    level.setBlockAndUpdate(new BlockPos(x + dx, y - 1, z + dz), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                    for (int dy = 0; dy < 40; dy++) {
                        level.setBlockAndUpdate(new BlockPos(x + dx, y + dy, z + dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                    }
                }
            }
            return new BlockPos(x, y, z);
        }

        static void schedule(MinecraftServer server) {
            ServerLevel level = server.overworld();
            BlockPos spawn = level.getSharedSpawnPos();
            int x = spawn.getX() + 40;
            int z = spawn.getZ() + 40;
            level.getChunk(x >> 4, z >> 4);
            BlockPos ground = clearArea(level, x, z);
            BlockPos seat = buildRocket(level, ground, 4);

            var report = com.thepearlking.stevespace.vessel.VesselReport.analyze(level, seat);
            check("vessel-report", report.ready() && report.blocks() == 8 && report.engines() == 1
                            && Math.abs(report.propellant() - 3.6) < 1e-6 && report.thrustToWeight() > 1.15 && report.thrustToWeight() < 2.5,
                    String.format("blocks=%d engines=%d prop=%.2f twr=%.2f dv=%.0f problems=%s",
                            report.blocks(), report.engines(), report.propellant(), report.thrustToWeight(), report.deltaV(), report.problems()));

            // A fuel loader set down beside the pad (touching no tank) still reaches the rocket's tanks.
            BlockPos loader = ground.offset(2, 0, 0);
            level.setBlockAndUpdate(loader, com.thepearlking.stevespace.registry.ModBlocks.FUEL_LOADER.get().defaultBlockState());
            int reached = com.thepearlking.stevespace.block.FuelLoaderBlock.connectedTanks(level, loader).size();
            check("fuel-loader-beside-pad", reached == 4, "tanks reached=" + reached);
            level.setBlockAndUpdate(loader, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());

            final net.minecraft.server.level.ServerPlayer[] crewTest = new net.minecraft.server.level.ServerPlayer[2];
            final boolean[] crewLaunchBlocked = new boolean[1];
            // Crew: a passenger seat beside the pilot's. Both ride up; only the pilot flies.
            later(server, 1, () -> {
                BlockPos crewGround = clearArea(level, x - 24, z);
                BlockPos crewSeat = buildRocket(level, crewGround, 2);
                BlockPos extra = crewSeat.south();
                level.setBlockAndUpdate(extra, com.thepearlking.stevespace.registry.ModBlocks.PASSENGER_SEAT.get().defaultBlockState());
                var captain = mockPlayer(level, "SeatPilot");
                var guest = mockPlayer(level, "SeatGuest");
                for (var pair : java.util.List.of(java.util.Map.entry(captain, crewSeat), java.util.Map.entry(guest, extra))) {
                    var seatEntity = com.thepearlking.stevespace.block.SeatEntity.create(level, pair.getValue());
                    level.addFreshEntity(seatEntity);
                    pair.getKey().startRiding(seatEntity, true);
                }
                var crewReport = com.thepearlking.stevespace.vessel.VesselReport.analyze(level, crewSeat);
                check("passenger-seat-counts", crewReport.ready() && crewReport.seats() == 2, "seats=" + crewReport.seats() + " " + crewReport.problems());
                com.thepearlking.stevespace.vessel.VesselServer.onControls(guest, new com.thepearlking.stevespace.vessel.VesselControls(1, 0, 0, 0));
                boolean guestCantLaunch = !(com.thepearlking.stevespace.vessel.VesselEntity.shipOf(guest) instanceof com.thepearlking.stevespace.vessel.VesselEntity);
                com.thepearlking.stevespace.vessel.VesselServer.onControls(captain, new com.thepearlking.stevespace.vessel.VesselControls(1, 0, 0, 0));
                crewTest[0] = captain;
                crewTest[1] = guest;
                crewLaunchBlocked[0] = guestCantLaunch;
            });
            later(server, 20, () -> {
                var captain = crewTest[0];
                var guest = crewTest[1];
                boolean together = com.thepearlking.stevespace.vessel.VesselEntity.shipOf(captain) instanceof com.thepearlking.stevespace.vessel.VesselEntity v
                        && guest.getVehicle() == v && v.pilot() == captain;
                check("passenger-rides-along", crewLaunchBlocked[0] && together,
                        "guestCantLaunch=" + crewLaunchBlocked[0] + " captain=" + captain.getVehicle() + " guest=" + guest.getVehicle());
                if (com.thepearlking.stevespace.vessel.VesselEntity.shipOf(captain) instanceof com.thepearlking.stevespace.vessel.VesselEntity v) {
                    v.discard();
                }
                captain.discard();
                guest.discard();
            });

            var pilot = mockPlayer(level, "TestPilot1");
            final com.thepearlking.stevespace.vessel.VesselEntity[] vessel = new com.thepearlking.stevespace.vessel.VesselEntity[1];
            later(server, 2, () -> {
                var scan = com.thepearlking.stevespace.vessel.VesselAssembler.scan(level, seat, 1000);
                vessel[0] = com.thepearlking.stevespace.vessel.VesselEntity.launch(level, seat, scan.structure(), pilot);
                vessel[0].setControls(new com.thepearlking.stevespace.vessel.VesselControls(1, 0, 0, 0));
                check("launch-lifts-blocks", level.getBlockState(seat).isAir() && level.getBlockState(ground.above()).isAir()
                                && !level.getBlockState(ground).isAir() && pilot.getVehicle() == vessel[0],
                        "seat=" + level.getBlockState(seat) + " pad=" + level.getBlockState(ground));
            });
            final double[] start = new double[2];
            later(server, 1, () -> {
                start[0] = vessel[0].getY();
                start[1] = vessel[0].propellant();
            });
            later(server, 60, () -> {
                double climbed = vessel[0].getY() - start[0];
                check("vessel-climbs", climbed > 3 && climbed < 40 && !vessel[0].isGrounded(), String.format("climbed %.1f blocks in 3 s", climbed));
                check("burns-propellant", vessel[0].propellant() < start[1] - 0.2,
                        String.format("propellant %.2f -> %.2f t", start[1], vessel[0].propellant()));
                vessel[0].setControls(com.thepearlking.stevespace.vessel.VesselControls.IDLE);
            });
            // Engines off: it coasts up, falls back and hits the ground far too fast.
            waitUntil(server, 900, () -> vessel[0].isRemoved(), () -> check("hard-impact-crashes",
                    vessel[0].isRemoved() && level.getBlockState(ground.above(3)).isAir(),
                    "removed=" + vessel[0].isRemoved() + " y=" + String.format("%.0f", vessel[0].getY())));

            // A second rocket lands gently: a simple controller hovers it down.
            BlockPos ground2 = clearArea(level, x + 20, z);
            BlockPos seat2 = buildRocket(level, ground2, 3);
            var pilot2 = mockPlayer(level, "TestPilot2");
            final com.thepearlking.stevespace.vessel.VesselEntity[] lander = new com.thepearlking.stevespace.vessel.VesselEntity[1];
            later(server, 1, () -> {
                var scan = com.thepearlking.stevespace.vessel.VesselAssembler.scan(level, seat2, 1000);
                lander[0] = com.thepearlking.stevespace.vessel.VesselEntity.launch(level, seat2, scan.structure(), pilot2);
            });
            final int[] tick = new int[1];
            waitUntil(server, 1200, () -> {
                var v = lander[0];
                if (v == null || v.isRemoved()) {
                    return true;
                }
                // Climb at 6 m/s for 2.5 s, then come down at 3 m/s until touchdown, then cut.
                double hover = 9.81 * (v.stats().dryMass() + v.propellant()) / v.stats().thrustVector().length();
                double targetVy = tick[0]++ < 50 ? 6 : -3;
                double throttle = Math.max(0, Math.min(1, hover * (1 + (targetVy - v.velocity().y) * 0.4)));
                if (tick[0] > 50 && v.isGrounded()) {
                    throttle = 0;
                }
                v.setControls(new com.thepearlking.stevespace.vessel.VesselControls(throttle, 0, 0, 0));
                return false;
            }, () -> {
                boolean landed = lander[0].isRemoved();
                BlockPos found = null;
                for (int dy = -2; dy <= 4 && found == null; dy++) {
                    for (int dx = -2; dx <= 2 && found == null; dx++) {
                        for (int dz = -2; dz <= 2; dz++) {
                            BlockPos p = seat2.offset(dx, dy, dz);
                            if (level.getBlockState(p).getBlock() instanceof com.thepearlking.stevespace.block.PilotSeatBlock) {
                                found = p;
                                break;
                            }
                        }
                    }
                }
                double fuel = 0;
                if (found != null) {
                    for (int i = 1; i <= 3; i++) {
                        if (level.getBlockEntity(found.below(i)) instanceof com.thepearlking.stevespace.block.PropellantTankBlockEntity tank) {
                            fuel += tank.propellant();
                        }
                    }
                }
                check("gentle-landing-places-blocks", landed && found != null && fuel > 0.1 && fuel < 2.7,
                        "landed=" + landed + " seat=" + found + " fuel=" + String.format("%.2f", fuel)
                                + " pilotSeated=" + (pilot2.getVehicle() != null));
                check("pilot-reseated-after-landing", pilot2.getVehicle() instanceof com.thepearlking.stevespace.block.SeatEntity,
                        "vehicle=" + pilot2.getVehicle());
            });
        }
    }

    /** The autopilot flies a rocket to orbit, time warp keeps it there, then it lands again. */
    static final class OrbitTests {

        private static com.thepearlking.stevespace.vessel.VesselEntity vesselOf(net.minecraft.world.entity.Entity pilot) {
            if (pilot instanceof net.minecraft.world.entity.player.Player player) {
                return com.thepearlking.stevespace.vessel.VesselEntity.shipOf(player, 2);
            }
            return pilot.getVehicle() instanceof com.thepearlking.stevespace.vessel.VesselEntity v ? v : null;
        }

        static String describe(com.thepearlking.stevespace.vessel.VesselEntity v) {
            if (v == null) {
                return "no vessel";
            }
            var o = v.orbital();
            if (o != null) {
                var el = o.elements(1);
                return String.format("space alt=%.0f pe=%.0f ap=%.0f e=%.3f v=%.1f prop=%.2f ap=%s", o.altitude(),
                        el.periapsis() - o.body().radius(), el.apoapsis() - o.body().radius(), el.eccentricity(),
                        o.state().velocity().length(), v.propellant(), v.autopilotStatus());
            }
            return String.format("%s y=%.0f v=(%.1f,%.1f,%.1f) prop=%.2f ap=%s grounded=%s",
                    v.inMapMode() ? "map" : v.level().dimension().location().toString(), v.mapY(),
                    v.velocity().x, v.velocity().y, v.velocity().z, v.propellant(), v.autopilotStatus(), v.isGrounded());
        }

        static void schedule(MinecraftServer server) {
            ServerLevel level = server.overworld();
            BlockPos spawn = level.getSharedSpawnPos();
            int x = spawn.getX() - 60;
            int z = spawn.getZ() + 40;
            level.getChunk(x >> 4, z >> 4);
            BlockPos ground = VesselTests.clearArea(level, x, z);
            BlockPos seat = VesselTests.buildRocket(level, ground, 6);
            final net.minecraft.server.level.ServerPlayer[] pilot = new net.minecraft.server.level.ServerPlayer[1];
            final int[] ticks = new int[1];
            later(server, 2, () -> {
                pilot[0] = VesselTests.mockPlayer(level, "TestAstronaut");
                pilot[0].setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                var scan = com.thepearlking.stevespace.vessel.VesselAssembler.scan(level, seat, 1000);
                var v = com.thepearlking.stevespace.vessel.VesselEntity.launch(level, seat, scan.structure(), pilot[0]);
                v.startAutopilot(com.thepearlking.stevespace.vessel.Autopilot.Program.ORBIT);
            });
            waitUntil(server, 20 * 60 * 8, () -> {
                var v = vesselOf(pilot[0]);
                if (++ticks[0] % 200 == 0) {
                    StevesSpace.LOGGER.info("[SelfTest] ascent t={}s {}", ticks[0] / 20, describe(v));
                }
                return v == null || (v.orbital() != null && !v.autopilotEngaged());
            }, () -> {
                var v = vesselOf(pilot[0]);
                boolean inSpace = v != null && com.thepearlking.stevespace.world.SpaceDimension.isSpace(v.level()) && v.orbital() != null;
                double pe = inSpace ? v.orbital().elements(1).periapsis() - v.orbital().body().radius() : -1;
                check("autopilot-reaches-orbit", inSpace && pe > 2400, describe(v) + " after " + ticks[0] / 20 + "s");
                check("crew-goes-to-space", pilot[0].level().dimension() == com.thepearlking.stevespace.world.SpaceDimension.KEY,
                        "pilot in " + pilot[0].level().dimension().location());
            });
            final double[] before = new double[3];
            later(server, 1, () -> {
                var v = vesselOf(pilot[0]);
                if (v == null || v.orbital() == null) {
                    return;
                }
                var el = v.orbital().elements(1);
                before[0] = el.periapsis();
                before[1] = el.apoapsis();
                before[2] = com.thepearlking.stevespace.universe.UniverseClock.get(server).time();
                for (int i = 0; i < 5; i++) {
                    com.thepearlking.stevespace.universe.TimeWarp.request(pilot[0], 1);
                }
            });
            later(server, 100, () -> {
                var v = vesselOf(pilot[0]);
                var clock = com.thepearlking.stevespace.universe.UniverseClock.get(server);
                double elapsed = clock.time() - before[2];
                boolean ok = v != null && v.orbital() != null;
                double drift = ok ? Math.abs(v.orbital().elements(1).periapsis() - before[0]) + Math.abs(v.orbital().elements(1).apoapsis() - before[1]) : -1;
                check("warp-in-orbit", ok && clock.warp() >= 100 && elapsed > 400 && drift < 1,
                        String.format("warp=%.0f elapsed=%.0f s drift=%.3f %s", clock.warp(), elapsed, drift, describe(v)));
                for (int i = 0; i < 8; i++) {
                    com.thepearlking.stevespace.universe.TimeWarp.request(pilot[0], -1);
                }
            });
            // A whole Moon mission: out, land, take off again, come home and land.
            moonMission(server, level, pilot, ticks);
        }

        private static void program(net.minecraft.server.level.ServerPlayer pilot, com.thepearlking.stevespace.vessel.Autopilot.Program p) {
            var v = vesselOf(pilot);
            if (v != null) {
                v.startAutopilot(p);
            }
        }

        private static net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> moonKey() {
            return net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                    net.minecraft.resources.ResourceLocation.parse("stevespace:moon"));
        }

        static void moonMission(MinecraftServer server, ServerLevel earth, net.minecraft.server.level.ServerPlayer[] pilot, int[] ticks) {
            var system = com.thepearlking.stevespace.universe.ServerUniverse.system();
            var moon = system.body("moon");
            later(server, 2, () -> {
                program(pilot[0], com.thepearlking.stevespace.vessel.Autopilot.Program.MOON);
                ticks[0] = 0;
            });
            waitUntil(server, 20 * 60 * 8, () -> {
                var v = vesselOf(pilot[0]);
                if (++ticks[0] % 200 == 0) {
                    StevesSpace.LOGGER.info("[SelfTest] to the Moon t={}s warp={} {}", ticks[0] / 20,
                            com.thepearlking.stevespace.universe.UniverseClock.get(server).warp(), describe(v));
                }
                return v == null || (v.orbital() != null && v.orbital().body().id().equals("moon") && !v.autopilotEngaged())
                        || (v != null && !v.autopilotEngaged());
            }, () -> {
                var v = vesselOf(pilot[0]);
                boolean ok = v != null && v.orbital() != null && v.orbital().body().id().equals("moon");
                double pe = ok ? v.orbital().elements(1).periapsis() - moon.radius() : -1;
                double e = ok ? v.orbital().elements(1).eccentricity() : -1;
                check("autopilot-reaches-moon-orbit", ok && pe > 200 && e < 0.3,
                        describe(v) + " pe=" + (int) pe + " e=" + String.format("%.3f", e) + " after " + ticks[0] / 20 + "s");
            });
            // In lunar orbit: get up (the ship parks), float about weightless, sit back down and fly on.
            final BlockPos[] lunarSeat = new BlockPos[1];
            later(server, 2, () -> {
                var v = vesselOf(pilot[0]);
                // The ship is blocks: get out of the seat block and step outside onto the hull.
                if (v != null && v.materialized()) {
                    lunarSeat[0] = v.blockOrigin().offset(v.stats().seats().get(0).pos());
                    if (pilot[0].getVehicle() instanceof com.thepearlking.stevespace.block.SeatEntity seat) {
                        seat.requestDismount(pilot[0]);
                    }
                    BlockPos top = v.blockOrigin().offset(v.boundsMax()).above(2);
                    pilot[0].teleportTo(top.getX() + 0.5, top.getY(), top.getZ() + 0.5);
                } else if (v != null) {
                    v.requestDismount(pilot[0]);
                }
            });
            later(server, 20, () -> {
                double g = pilot[0].getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.GRAVITY);
                boolean out = pilot[0].getVehicle() == null && pilot[0].level().dimension() == com.thepearlking.stevespace.world.SpaceDimension.KEY;
                check("spacewalk-weightless", out && g == 0 && lunarSeat[0] != null,
                        "out=" + out + " gravity=" + g + " seat=" + lunarSeat[0]);
                if (lunarSeat[0] != null) {
                    ServerLevel space = (ServerLevel) pilot[0].level();
                    var seatEntity = com.thepearlking.stevespace.block.SeatEntity.create(space, lunarSeat[0]);
                    space.addFreshEntity(seatEntity);
                    pilot[0].startRiding(seatEntity, true);
                    com.thepearlking.stevespace.vessel.VesselServer.onControls(pilot[0], new com.thepearlking.stevespace.vessel.VesselControls(0.01, 0, 0, 0));
                }
            });
            later(server, 5, () -> {
                com.thepearlking.stevespace.vessel.VesselServer.onControls(pilot[0], com.thepearlking.stevespace.vessel.VesselControls.IDLE);
                var v = vesselOf(pilot[0]);
                check("spacewalk-back-aboard", v != null && v.orbital() != null && v.orbital().body().id().equals("moon"),
                        "vehicle=" + pilot[0].getVehicle());
            });
            later(server, 2, () -> {
                program(pilot[0], com.thepearlking.stevespace.vessel.Autopilot.Program.LAND);
                ticks[0] = 0;
            });
            waitUntil(server, 20 * 60 * 6, () -> {
                var v = vesselOf(pilot[0]);
                if (++ticks[0] % 200 == 0) {
                    StevesSpace.LOGGER.info("[SelfTest] Moon landing t={}s {}", ticks[0] / 20, describe(v));
                }
                return v == null;
            }, () -> {
                boolean seated = pilot[0].getVehicle() instanceof com.thepearlking.stevespace.block.SeatEntity;
                check("lands-on-moon", seated && pilot[0].level().dimension() == moonKey(),
                        "vehicle=" + pilot[0].getVehicle() + " in " + pilot[0].level().dimension().location() + " at " + pilot[0].blockPosition());
                check("moon-gravity", Math.abs(pilot[0].getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.GRAVITY)
                        - 0.08 * moon.surfaceGravity() / 9.81) < 1e-4, "gravity attribute "
                        + pilot[0].getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.GRAVITY));
            });
            later(server, 40, () -> {
                // Take off again from where it landed.
                if (pilot[0].getVehicle() instanceof com.thepearlking.stevespace.block.SeatEntity seat) {
                    ServerLevel moonLevel = (ServerLevel) seat.level();
                    var scan = com.thepearlking.stevespace.vessel.VesselAssembler.scan(moonLevel, seat.blockPosition(), 1000);
                    if (scan.ok()) {
                        VesselEntityAccess.leave(pilot[0]);
                        var v = com.thepearlking.stevespace.vessel.VesselEntity.launch(moonLevel, seat.blockPosition(), scan.structure(), pilot[0]);
                        v.startAutopilot(com.thepearlking.stevespace.vessel.Autopilot.Program.ORBIT);
                    }
                }
                ticks[0] = 0;
            });
            waitUntil(server, 20 * 60 * 5, () -> {
                var v = vesselOf(pilot[0]);
                if (++ticks[0] % 200 == 0) {
                    StevesSpace.LOGGER.info("[SelfTest] Moon ascent t={}s {}", ticks[0] / 20, describe(v));
                }
                return v == null || (v.orbital() != null && !v.autopilotEngaged());
            }, () -> {
                var v = vesselOf(pilot[0]);
                boolean ok = v != null && v.orbital() != null && v.orbital().body().id().equals("moon");
                double pe = ok ? v.orbital().elements(1).periapsis() - moon.radius() : -1;
                check("orbit-from-moon", ok && pe > 200, describe(v) + " pe=" + (int) pe);
            });
            later(server, 2, () -> {
                program(pilot[0], com.thepearlking.stevespace.vessel.Autopilot.Program.RETURN);
                ticks[0] = 0;
            });
            waitUntil(server, 20 * 60 * 10, () -> {
                var v = vesselOf(pilot[0]);
                if (++ticks[0] % 200 == 0) {
                    StevesSpace.LOGGER.info("[SelfTest] coming home t={}s warp={} {}", ticks[0] / 20,
                            com.thepearlking.stevespace.universe.UniverseClock.get(server).warp(), describe(v));
                }
                // Done once it has settled back into blocks (or if the autopilot gave up in flight).
                return v == null || (!v.autopilotEngaged() && !v.isGrounded());
            }, () -> {
                boolean seated = pilot[0].getVehicle() instanceof com.thepearlking.stevespace.block.SeatEntity;
                check("vessel-never-hidden", com.thepearlking.stevespace.vessel.VesselEntity.timesHidden == 0,
                        "hidden " + com.thepearlking.stevespace.vessel.VesselEntity.timesHidden + " times");
                check("returns-and-lands-on-earth", seated && pilot[0].level() == earth && pilot[0].getY() > 62,
                        "pilot vehicle=" + pilot[0].getVehicle() + " in " + pilot[0].level().dimension().location()
                                + " at " + pilot[0].blockPosition() + " after " + ticks[0] / 20 + "s " + describe(vesselOf(pilot[0])));
                check("earth-gravity-restored", Math.abs(pilot[0].getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.GRAVITY) - 0.08) < 1e-6,
                        "gravity " + pilot[0].getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.GRAVITY));
            });
        }

        /** Gets the pilot out of the landed seat so the rocket can be launched again. */
        private static final class VesselEntityAccess {
            static void leave(net.minecraft.server.level.ServerPlayer player) {
                com.thepearlking.stevespace.vessel.VesselEntity.leaveGroundSeat(player);
            }
        }
    }

    /** Every landable world has a surface dimension that generates the right ground. */
    static final class BodyWorldTests {
        static void run(MinecraftServer server) {
            var system = com.thepearlking.stevespace.universe.ServerUniverse.system();
            StringBuilder missing = new StringBuilder();
            for (var body : system.bodies()) {
                if (!body.isLandable()) {
                    continue;
                }
                var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                        net.minecraft.resources.ResourceLocation.parse(body.surfaceDimension()));
                if (server.getLevel(key) == null) {
                    missing.append(body.id()).append(' ');
                }
            }
            check("every-landable-body-has-a-world", missing.isEmpty(), "missing: " + missing);
            // Every world: the ground must be its own blocks.
            for (String id : com.thepearlking.stevespace.world.gen.PlanetBodies.bodies()) {
                ServerLevel level = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(StevesSpace.MODID, id)));
                long start = System.nanoTime();
                level.getChunk(2, 2);
                double ms = (System.nanoTime() - start) / 1e6;
                int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, 40, 40);
                String topId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(
                        level.getBlockState(new BlockPos(40, y - 1, 40)).getBlock()).toString();
                check("world-" + id, y > 40 && y < 260 && (topId.startsWith("stevespace:") || topId.equals("minecraft:lava")),
                        String.format("ground y=%d top=%s chunk %.0f ms", y, topId, ms));
            }
        }
    }

    /** Two vessels meeting in orbit dock into one, then undock into two again. */
    /** Coming home fast (as from the Moon) with modest engines: the autopilot must brake in time. */
    static final class ReturnTests {

        static void schedule(MinecraftServer server) {
            final net.minecraft.server.level.ServerPlayer[] pilot = new net.minecraft.server.level.ServerPlayer[1];
            final int[] t = new int[1];
            later(server, 2, () -> {
                com.thepearlking.stevespace.universe.ServerUniverse.setWarp(server, 1);
                ServerLevel space = server.getLevel(com.thepearlking.stevespace.world.SpaceDimension.KEY);
                // Pods left over from earlier runs of this test world would answer too.
                for (var old : com.thepearlking.stevespace.vessel.VesselEntity.allServerVessels()) {
                    String n = old.getName().getString();
                    if (n.equals("Pod One") || n.equals("Pod Two")) {
                        old.discard();
                    }
                }
                var parkedShips = com.thepearlking.stevespace.world.ParkedShips.get(server);
                for (var parked : java.util.List.copyOf(parkedShips.all())) {
                    if (parked.name().contains("Pod One") || parked.name().contains("Pod Two")) {
                        parkedShips.remove(parked.cell());
                    }
                }
                var earth = com.thepearlking.stevespace.universe.ServerUniverse.system().earth();
                double r = 300_000;
                // Falling in from far away on a path that grazes the air: like a return from the Moon.
                double vt = 0.6;
                var state = new com.thepearlking.stevespace.universe.StateVector(
                        new com.thepearlking.stevespace.universe.DVec3(r, 0, 0), new com.thepearlking.stevespace.universe.DVec3(-2, vt, 0));
                var blocks = new java.util.HashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
                blocks.put(new BlockPos(0, 0, 0), com.thepearlking.stevespace.registry.ModBlocks.ROCKET_ENGINE.get().defaultBlockState());
                for (int i = 1; i <= 6; i++) {
                    blocks.put(new BlockPos(0, i, 0), com.thepearlking.stevespace.registry.ModBlocks.PROPELLANT_TANK.get().defaultBlockState());
                }
                blocks.put(new BlockPos(0, 7, 0), com.thepearlking.stevespace.registry.ModBlocks.PILOT_SEAT.get().defaultBlockState());
                blocks.put(new BlockPos(0, 8, 0), com.thepearlking.stevespace.registry.ModBlocks.HULL_WHITE.get().defaultBlockState());
                var ship = com.thepearlking.stevespace.vessel.VesselEntity.spawnInOrbit(space,
                        new com.thepearlking.stevespace.vessel.VesselStructure(blocks, new java.util.HashMap<>()), earth, state, new org.joml.Quaterniond());
                ship.fillTanks();
                pilot[0] = VesselTests.mockPlayer(server.overworld(), "Returner");
                pilot[0].setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                pilot[0].teleportTo(space, ship.getX(), ship.getY() + 3, ship.getZ(), java.util.Set.of(), 0, 0);
                pilot[0].startRiding(ship, true);
            });
            later(server, 5, () -> com.thepearlking.stevespace.vessel.VesselServer.onAutopilot(pilot[0],
                    com.thepearlking.stevespace.vessel.Autopilot.Program.LAND));
            waitUntil(server, 20 * 600, () -> {
                if (++t[0] % 100 == 0 && com.thepearlking.stevespace.vessel.VesselEntity.shipOf(pilot[0]) instanceof com.thepearlking.stevespace.vessel.VesselEntity v) {
                    StevesSpace.LOGGER.info("[Return] t={}s {} {} speed {} warp={} {}", t[0] / 20, v.level().dimension().location(),
                            v.orbital() == null ? "y=" + (int) v.mapY() : "alt=" + (int) v.orbital().altitude(),
                            String.format("%.1f", v.orbital() != null ? v.orbital().state().velocity().length() : v.velocity().length()),
                            com.thepearlking.stevespace.universe.UniverseClock.get(server).warp(), v.autopilotStatus());
                }
                return !(com.thepearlking.stevespace.vessel.VesselEntity.shipOf(pilot[0]) instanceof com.thepearlking.stevespace.vessel.VesselEntity);
            }, () -> check("fast-return-lands", pilot[0].getVehicle() instanceof com.thepearlking.stevespace.block.SeatEntity
                    && pilot[0].level() == server.overworld(), "vehicle=" + pilot[0].getVehicle() + " in " + pilot[0].level().dimension().location()));
        }
    }

    /** What counts as the ship: a player-placed ground block and a diagonal part do; the ground doesn't. */
    static final class ScanTests {

        static void run(MinecraftServer server) {
            ServerLevel level = server.overworld();
            BlockPos spawn = level.getSharedSpawnPos();
            int x = spawn.getX() + 80;
            int z = spawn.getZ() - 60;
            level.getChunk(x >> 4, z >> 4);
            BlockPos ground = VesselTests.clearArea(level, x, z);
            BlockPos seat = VesselTests.buildRocket(level, ground, 2);
            var player = VesselTests.mockPlayer(level, "Builder");
            // An orange roof of layered sediment, placed by hand, on top of the hull.
            BlockPos roof = seat.above(2);
            placeOnShip(level, roof, com.thepearlking.stevespace.registry.WorldBlocks.state("layered_sediment"), player);
            // A hull block touching the roof only by its corner.
            BlockPos diagonal = roof.offset(1, 1, 1);
            level.setBlockAndUpdate(diagonal, com.thepearlking.stevespace.registry.ModBlocks.HULL_GREY.get().defaultBlockState());
            // Natural ground (never placed by a player) right beside the engine.
            BlockPos natural = seat.below(3).east();
            level.setBlockAndUpdate(natural, net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState());
            var scan = com.thepearlking.stevespace.vessel.VesselAssembler.scan(level, seat, 1000);
            var blocks = scan.structure().blocks().keySet();
            check("ship-takes-placed-ground-block", blocks.contains(roof.subtract(seat)), "blocks " + blocks.size());
            check("ship-takes-diagonal-block", blocks.contains(diagonal.subtract(seat)), "blocks " + blocks.size());
            check("ship-leaves-natural-ground", !blocks.contains(natural.subtract(seat)), "blocks " + blocks.size());
            player.discard();
        }
    }

    /** Power: sunlight on Earth makes FE in a solar array; a generator with no power gives no air. */
    static final class PowerTests {

        static void schedule(MinecraftServer server) {
            ServerLevel level = server.overworld();
            BlockPos spawn = level.getSharedSpawnPos();
            final BlockPos[] controller = new BlockPos[1];
            final BlockPos[] room = new BlockPos[1];
            later(server, 2, () -> {
                com.thepearlking.stevespace.universe.ServerUniverse.setLocalTime(server, 6000); // noon
                int x = spawn.getX() + 140;
                int z = spawn.getZ() + 20;
                // Block entities only tick in chunks that are kept ticking (a player is usually near).
                for (int cx = (x >> 4) - 1; cx <= (x >> 4) + 1; cx++) {
                    for (int cz = (z >> 4) - 1; cz <= (z >> 4) + 1; cz++) {
                        level.setChunkForced(cx, cz, true);
                    }
                }
                BlockPos ground = VesselTests.clearArea(level, x, z);
                controller[0] = ground.above();
                level.setBlockAndUpdate(controller[0], com.thepearlking.stevespace.registry.ModBlocks.SOLAR_CONTROLLER.get().defaultBlockState());
                for (int i = 1; i <= 6; i++) {
                    level.setBlockAndUpdate(controller[0].east(i), com.thepearlking.stevespace.registry.ModBlocks.SOLAR_PANEL.get().defaultBlockState());
                }
                // A sealed hull box with an oxygen generator and nothing to power it.
                room[0] = ground.above().south(4);
                var hull = com.thepearlking.stevespace.registry.ModBlocks.HULL_GREY.get().defaultBlockState();
                for (int dx = 0; dx <= 3; dx++) {
                    for (int dy = 0; dy <= 3; dy++) {
                        for (int dz = 0; dz <= 3; dz++) {
                            boolean shell = dx == 0 || dx == 3 || dy == 0 || dy == 3 || dz == 0 || dz == 3;
                            level.setBlockAndUpdate(room[0].offset(dx, dy, dz), shell ? hull : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                        }
                    }
                }
                level.setBlockAndUpdate(room[0].offset(1, 1, 1), com.thepearlking.stevespace.registry.ModBlocks.OXYGEN_GENERATOR.get().defaultBlockState());
            });
            later(server, 60, () -> {
                var be = level.getBlockEntity(controller[0]);
                int stored = be instanceof com.thepearlking.stevespace.energy.PowerBlockEntity p ? p.buffer().getEnergyStored() : -1;
                check("solar-makes-power-at-noon", stored > 0, "stored " + stored + " light "
                        + com.thepearlking.stevespace.energy.Sunlight.at(level, controller[0].east()));
                check("unpowered-generator-no-air", !com.thepearlking.stevespace.suit.Habitats.pressurised(level, room[0].offset(2, 1, 2)), "");
                // Now power it through the hull: a creative source on the outside of the box.
                level.setBlockAndUpdate(room[0].offset(-1, 1, 1), com.thepearlking.stevespace.registry.ModBlocks.CREATIVE_POWER.get().defaultBlockState());
            });
            later(server, 80, () -> check("power-through-hull-gives-air",
                    com.thepearlking.stevespace.suit.Habitats.pressurised(level, room[0].offset(2, 1, 2)), ""));
            // Basic machines: coal generator → pipe over bare ground → electric furnace and crusher.
            final BlockPos[] line = new BlockPos[1];
            later(server, 2, () -> {
                line[0] = room[0].south(8);
                // Fresh site: the test world keeps what earlier runs left here.
                for (int dx = -1; dx <= 7; dx++) {
                    for (int dy = 0; dy <= 3; dy++) {
                        for (int dz = -1; dz <= 5; dz++) {
                            level.setBlockAndUpdate(line[0].offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                        }
                    }
                }
                level.setBlockAndUpdate(line[0], com.thepearlking.stevespace.registry.ModBlocks.COAL_GENERATOR.get().defaultBlockState());
                for (int i = 1; i <= 5; i++) {
                    level.setBlockAndUpdate(line[0].east(i), com.thepearlking.stevespace.registry.ModBlocks.POWER_PIPE.get().defaultBlockState());
                }
                level.setBlockAndUpdate(line[0].east(6), com.thepearlking.stevespace.registry.ModBlocks.ELECTRIC_FURNACE.get().defaultBlockState());
                level.setBlockAndUpdate(line[0].east(5).south(), com.thepearlking.stevespace.registry.ModBlocks.CRUSHER.get().defaultBlockState());
                level.setBlockAndUpdate(line[0].east(3).north(), com.thepearlking.stevespace.registry.ModBlocks.SMALL_BATTERY.get().defaultBlockState());
                machine(level, line[0]).items().insertItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COAL, 4), false);
                machine(level, line[0].east(6)).items().insertItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.RAW_IRON, 2), false);
                machine(level, line[0].east(5).south()).items().insertItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_ORE, 1), false);
                for (int i = 1; i <= 5; i++) { // as if placed by hand, after their neighbours
                    BlockPos pipe = line[0].east(i);
                    level.setBlockAndUpdate(pipe, net.minecraft.world.level.block.Block.updateFromNeighbourShapes(level.getBlockState(pipe), level, pipe));
                }
                check("pipe-joins-generator", level.getBlockState(line[0].east()).getValue(net.minecraft.world.level.block.PipeBlock.WEST), "");
                // Whole-ship power: a creative source reaching a furnace through a corner and a vanilla block.
                BlockPos ship = line[0].south(4);
                level.setBlockAndUpdate(ship, com.thepearlking.stevespace.registry.ModBlocks.CREATIVE_POWER.get().defaultBlockState());
                level.setBlockAndUpdate(ship.east().above(), net.minecraft.world.level.block.Blocks.IRON_BLOCK.defaultBlockState());
                level.setBlockAndUpdate(ship.east(2).above(2), com.thepearlking.stevespace.registry.ModBlocks.CRUSHER.get().defaultBlockState());
            });
            later(server, 300, () -> {
                var furnace = machine(level, line[0].east(6));
                var crusher = machine(level, line[0].east(5).south());
                var battery = level.getBlockEntity(line[0].east(3).north());
                check("coal-generator-makes-power", machine(level, line[0]).buffer().getEnergyStored() > 0
                        || furnace.buffer().getEnergyStored() > 0, "gen " + machine(level, line[0]).buffer().getEnergyStored());
                check("electric-furnace-smelts", furnace.items().getStackInSlot(1).is(net.minecraft.world.item.Items.IRON_INGOT),
                        furnace.items().getStackInSlot(1).toString());
                check("crusher-doubles-ore", crusher.items().getStackInSlot(1).getCount() == 2
                        && crusher.items().getStackInSlot(1).is(com.thepearlking.stevespace.registry.ModItems.IRON_DUST.get()),
                        crusher.items().getStackInSlot(1).toString());
                var shipCrusher = machine(level, line[0].south(4).east(2).above(2));
                check("ship-power-through-corner-and-vanilla-block", shipCrusher.buffer().getEnergyStored() > 0,
                        "stored " + shipCrusher.buffer().getEnergyStored());
            });
            // With whole-ship power switched off, the same crusher gets nothing.
            later(server, 305, () -> {
                com.thepearlking.stevespace.config.SpaceConfig.SHIP_POWER.set(false);
                machine(level, line[0].south(4).east(2).above(2)).buffer().set(0);
            });
            later(server, 345, () -> {
                int stored = machine(level, line[0].south(4).east(2).above(2)).buffer().getEnergyStored();
                com.thepearlking.stevespace.config.SpaceConfig.SHIP_POWER.set(true);
                check("ship-power-off-needs-pipes", stored == 0, "stored " + stored);
            });
            // Once the machines are done, what the generator makes goes into the battery.
            later(server, 200, () -> {
                var battery = level.getBlockEntity(line[0].east(3).north());
                check("small-battery-charges", battery instanceof com.thepearlking.stevespace.energy.PowerBlockEntity b
                        && b.buffer().getEnergyStored() > 0 && b.buffer().getMaxEnergyStored() == 50_000,
                        battery instanceof com.thepearlking.stevespace.energy.PowerBlockEntity b ? "stored " + b.buffer().getEnergyStored() : "none");
            });
        }

        static com.thepearlking.stevespace.energy.Machines.MachineEntity machine(ServerLevel level, BlockPos pos) {
            return (com.thepearlking.stevespace.energy.Machines.MachineEntity) level.getBlockEntity(pos);
        }
    }

    /** A sealed room on the Moon with an oxygen generator holds air; a hole in the wall lets it out. */
    static final class HabitatTests {

        static void schedule(MinecraftServer server) {
            final net.minecraft.server.level.ServerPlayer[] p = new net.minecraft.server.level.ServerPlayer[1];
            final BlockPos[] origin = new BlockPos[1];
            later(server, 2, () -> {
                ServerLevel moon = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(StevesSpace.MODID, "moon")));
                moon.getChunk(0, 0);
                int y = moon.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, 3, 3) + 2;
                origin[0] = new BlockPos(0, y, 0);
                var hull = com.thepearlking.stevespace.registry.ModBlocks.HULL_GREY.get().defaultBlockState();
                for (int x = 0; x <= 6; x++) {
                    for (int yy = 0; yy <= 4; yy++) {
                        for (int z = 0; z <= 6; z++) {
                            boolean shell = x == 0 || x == 6 || yy == 0 || yy == 4 || z == 0 || z == 6;
                            moon.setBlockAndUpdate(origin[0].offset(x, yy, z), shell ? hull : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                        }
                    }
                }
                moon.setBlockAndUpdate(origin[0].offset(1, 1, 1), com.thepearlking.stevespace.registry.ModBlocks.OXYGEN_GENERATOR.get().defaultBlockState());
                // Power: a solar controller can't run at night, so the creative source keeps the test steady.
                moon.setBlockAndUpdate(origin[0].offset(1, 1, 2), com.thepearlking.stevespace.registry.ModBlocks.CREATIVE_POWER.get().defaultBlockState());
                p[0] = VesselTests.mockPlayer(server.overworld(), "Settler");
                p[0].setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                p[0].teleportTo(moon, origin[0].getX() + 3.5, origin[0].getY() + 1, origin[0].getZ() + 3.5, java.util.Set.of(), 0, 0);
            });
            later(server, 50, () -> check("sealed-room-has-air", !com.thepearlking.stevespace.suit.Oxygen.airless(p[0]),
                    "pressurised=" + com.thepearlking.stevespace.suit.Habitats.pressurised(p[0].level(), p[0].blockPosition())));
            later(server, 2, () -> p[0].level().setBlockAndUpdate(origin[0].offset(3, 4, 3), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()));
            later(server, 50, () -> check("hole-lets-air-out", com.thepearlking.stevespace.suit.Oxygen.airless(p[0]),
                    "pressurised=" + com.thepearlking.stevespace.suit.Habitats.pressurised(p[0].level(), p[0].blockPosition())));
        }
    }

    /** Radio: a pod near the Moon hears a pod in Earth orbit a moment later; Earth in the way blocks it. */
    static final class RadioTests {

        static com.thepearlking.stevespace.vessel.VesselEntity pod(MinecraftServer server, String bodyId, com.thepearlking.stevespace.universe.DVec3 at,
                                                                  String name) {
            ServerLevel space = server.getLevel(com.thepearlking.stevespace.world.SpaceDimension.KEY);
            var body = com.thepearlking.stevespace.universe.ServerUniverse.system().body(bodyId);
            double r = at.length();
            var v = at.cross(new com.thepearlking.stevespace.universe.DVec3(0, 1, 0)).normalize().scale(Math.sqrt(body.mu() / r));
            var blocks = new java.util.HashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
            blocks.put(new BlockPos(0, 0, 0), com.thepearlking.stevespace.registry.ModBlocks.ROCKET_ENGINE.get().defaultBlockState());
            blocks.put(new BlockPos(0, 1, 0), com.thepearlking.stevespace.registry.ModBlocks.PROPELLANT_TANK.get().defaultBlockState());
            blocks.put(new BlockPos(0, 2, 0), com.thepearlking.stevespace.registry.ModBlocks.PILOT_SEAT.get().defaultBlockState());
            blocks.put(new BlockPos(1, 2, 0), com.thepearlking.stevespace.registry.ModBlocks.RADIO_RACK.get().defaultBlockState());
            var ship = com.thepearlking.stevespace.vessel.VesselEntity.spawnInOrbit(space,
                    new com.thepearlking.stevespace.vessel.VesselStructure(blocks, new java.util.HashMap<>()), body,
                    new com.thepearlking.stevespace.universe.StateVector(at, v), new org.joml.Quaterniond());
            ship.setCustomName(net.minecraft.network.chat.Component.literal(name));
            return ship;
        }

        static void schedule(MinecraftServer server) {
            final com.thepearlking.stevespace.vessel.VesselEntity[] ships = new com.thepearlking.stevespace.vessel.VesselEntity[2];
            final net.minecraft.server.level.ServerPlayer[] crew = new net.minecraft.server.level.ServerPlayer[2];
            final java.util.List<String> heard = new java.util.ArrayList<>();
            later(server, 2, () -> {
                com.thepearlking.stevespace.universe.ServerUniverse.setWarp(server, 1);
                ServerLevel space = server.getLevel(com.thepearlking.stevespace.world.SpaceDimension.KEY);
                var earth = com.thepearlking.stevespace.universe.ServerUniverse.system().earth();
                ships[0] = pod(server, "earth", new com.thepearlking.stevespace.universe.DVec3(0, 0, earth.radius() + 3100), "Pod One");
                ships[1] = pod(server, "moon", new com.thepearlking.stevespace.universe.DVec3(0, 0, 1737 + 1500), "Pod Two");
                for (int i = 0; i < 2; i++) {
                    crew[i] = VesselTests.mockPlayer(server.overworld(), i == 0 ? "Alice" : "Bob");
                    crew[i].setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                    crew[i].teleportTo(space, ships[i].getX(), ships[i].getY() + 3, ships[i].getZ(), java.util.Set.of(), 0, 0);
                    crew[i].startRiding(ships[i], true);
                }
            });
            later(server, 5, () -> {
                var contacts = com.thepearlking.stevespace.radio.Radio.contacts(crew[0]);
                check("radio-sees-other-pod", contacts != null && contacts.stream().anyMatch(c -> c.name().equals("Pod Two")),
                        "contacts " + contacts);
                com.thepearlking.stevespace.radio.Radio.transmit(crew[0], "Pod Two, do you read?");
                check("radio-message-on-its-way", com.thepearlking.stevespace.radio.Radio.pending() >= 1,
                        "pending " + com.thepearlking.stevespace.radio.Radio.pending());
            });
            later(server, 60, () -> check("radio-message-arrives-after-light-delay", com.thepearlking.stevespace.radio.Radio.pending() == 0,
                    "pending " + com.thepearlking.stevespace.radio.Radio.pending()));
            later(server, 70, () -> {
                // Bob tunes away: Alice's next call must not reach him.
                String bob = com.thepearlking.stevespace.radio.Radio.stationIdOf(crew[1]);
                com.thepearlking.stevespace.radio.Radio.tune(crew[1], 145.0);
                com.thepearlking.stevespace.radio.Radio.transmit(crew[0], "Anyone on 2250?");
                check("radio-other-channel-not-heard", bob != null && com.thepearlking.stevespace.radio.Radio.pendingTo(bob) == 0
                                && com.thepearlking.stevespace.radio.Radio.frequencyOf(crew[1]) == 145.0,
                        "bob " + bob + " pending " + (bob == null ? -1 : com.thepearlking.stevespace.radio.Radio.pendingTo(bob)));
                com.thepearlking.stevespace.radio.Radio.tune(crew[0], 145.0);
                com.thepearlking.stevespace.radio.Radio.transmit(crew[0], "Bob, on 145 now");
                check("radio-same-channel-heard", bob != null && com.thepearlking.stevespace.radio.Radio.pendingTo(bob) == 1,
                        "pending " + (bob == null ? -1 : com.thepearlking.stevespace.radio.Radio.pendingTo(bob)));
            });
        }
    }

    /**
     * Fly anywhere: from low Earth orbit to another world (STEVESPACE_TRAVEL, default Mars) and
     * land there, all on the autopilot.
     */
    /** SAS holding prograde, and a maneuver node planned on the map flown by the autopilot. */
    static final class ManeuverTests {

        static void schedule(MinecraftServer server) {
            final com.thepearlking.stevespace.vessel.VesselEntity[] ship = new com.thepearlking.stevespace.vessel.VesselEntity[1];
            final double[] expected = new double[3];
            later(server, 2, () -> {
                com.thepearlking.stevespace.universe.ServerUniverse.setWarp(server, 1);
                ServerLevel space = server.getLevel(com.thepearlking.stevespace.world.SpaceDimension.KEY);
                var body = com.thepearlking.stevespace.universe.ServerUniverse.system().body("earth");
                double r = body.radius() + com.thepearlking.stevespace.vessel.VesselTransfer.spaceAltitude(body) + 700;
                var axis = body.spinAxis();
                var out = axis.cross(com.thepearlking.stevespace.universe.DVec3.X).normalize();
                double mu = body.mu() * com.thepearlking.stevespace.config.SpaceConfig.GRAVITY_SCALE.get();
                var state = new com.thepearlking.stevespace.universe.StateVector(out.scale(r), axis.cross(out).normalize().scale(Math.sqrt(mu / r)));
                var blocks = new java.util.HashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
                blocks.put(new BlockPos(0, 0, 0), com.thepearlking.stevespace.registry.ModBlocks.NUCLEAR_ENGINE.get().defaultBlockState());
                for (int i = 1; i <= 4; i++) {
                    blocks.put(new BlockPos(0, i, 0), com.thepearlking.stevespace.registry.ModBlocks.PROPELLANT_TANK.get().defaultBlockState());
                }
                blocks.put(new BlockPos(0, 5, 0), com.thepearlking.stevespace.registry.ModBlocks.PILOT_SEAT.get().defaultBlockState());
                // Turned sideways to start with, so SAS has something to do.
                ship[0] = com.thepearlking.stevespace.vessel.VesselEntity.spawnInOrbit(space,
                        new com.thepearlking.stevespace.vessel.VesselStructure(blocks, new java.util.HashMap<>()), body, state,
                        new org.joml.Quaterniond().rotateX(Math.PI / 2));
                ship[0].fillTanks();
                var pilot = VesselTests.mockPlayer(server.overworld(), "Navigator");
                pilot.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                pilot.teleportTo(space, ship[0].getX(), ship[0].getY() + 3, ship[0].getZ(), java.util.Set.of(), 0, 0);
                pilot.startRiding(ship[0], true);
                ship[0].setSas(com.thepearlking.stevespace.vessel.VesselEntity.Sas.PROGRADE, null);
                // A prograde burn of 60 m/s, 150 s from now (other tests may keep time warp off):
                // where its far side should end up.
                double v = Math.sqrt(mu / r) + 60;
                double a = 1 / (2 / r - v * v / mu);
                expected[0] = 2 * a - r;
                expected[1] = r;
                expected[2] = ship[0].orbital().time() + 150;
            });
            waitUntil(server, 20 * 40, () -> ship[0] != null && ship[0].sasTarget() != null && ship[0].alignment(ship[0].sasTarget()) > 0.995, () ->
                    check("sas-prograde", ship[0].alignment(ship[0].sasTarget()) > 0.995,
                            String.format("alignment %.4f", ship[0].alignment(ship[0].sasTarget()))));
            later(server, 5, () -> ship[0].planNode(expected[2], 60, 0, 0, true));
            waitUntil(server, 20 * 60 * 8, () -> ship[0] != null && !ship[0].autopilotEngaged(), () -> {
                var body = com.thepearlking.stevespace.universe.ServerUniverse.system().body("earth");
                double mu = body.mu() * com.thepearlking.stevespace.config.SpaceConfig.GRAVITY_SCALE.get();
                var o = ship[0].orbital();
                var el = com.thepearlking.stevespace.universe.Kepler.elementsFromState(o.state(), mu, o.time());
                double ap = el.apoapsis();
                double pe = el.periapsis();
                check("maneuver-node", Math.abs(ap - expected[0]) < expected[0] * 0.02 && Math.abs(pe - expected[1]) < expected[1] * 0.01,
                        String.format("ap %.0f (want %.0f) pe %.0f (want %.0f) done at t=%.0f node t=%.0f", ap, expected[0], pe, expected[1],
                                o.time(), expected[2]));
                ship[0].discard();
            });
        }
    }

    /**
     * Remote control, end to end: a probe on its pad (a receiver, no pilot seat) and a console
     * down the road on the same channel. Connect, fire a function (a lamp lights), launch it by
     * throttling up from the console, come back, connect again to it in flight, and lose it.
     */
    /** A ship's name stays with it through landing and lifting off again. */
    static final class NameTests {

        static void schedule(MinecraftServer server) {
            final net.minecraft.server.level.ServerPlayer[] p = new net.minecraft.server.level.ServerPlayer[1];
            final BlockPos[] seat = new BlockPos[1];
            later(server, 2, () -> {
                ServerLevel level = server.overworld();
                BlockPos spawn = level.getSharedSpawnPos();
                level.getChunk((spawn.getX() + 60) >> 4, (spawn.getZ() - 90) >> 4);
                BlockPos ground = VesselTests.clearArea(level, spawn.getX() + 60, spawn.getZ() - 90);
                seat[0] = VesselTests.buildRocket(level, ground, 3);
                p[0] = VesselTests.mockPlayer(level, "Namer");
                p[0].teleportTo(level, seat[0].getX() + 0.5, seat[0].getY() + 1, seat[0].getZ() + 0.5, java.util.Set.of(), 0, 0);
                var seatEntity = com.thepearlking.stevespace.block.SeatEntity.create(level, seat[0]);
                level.addFreshEntity(seatEntity);
                p[0].startRiding(seatEntity, true);
            });
            later(server, 10, () -> com.thepearlking.stevespace.vessel.VesselServer.onControls(p[0], new com.thepearlking.stevespace.vessel.VesselControls(1, 0, 0, 0)));
            later(server, 10, () -> {
                if (p[0].getVehicle() instanceof com.thepearlking.stevespace.vessel.VesselEntity v) {
                    v.setCustomName(net.minecraft.network.chat.Component.literal("Hi"));
                }
                com.thepearlking.stevespace.vessel.VesselServer.onControls(p[0], com.thepearlking.stevespace.vessel.VesselControls.IDLE);
            });
            // Falls back and lands on its pad as blocks.
            waitUntil(server, 20 * 30, () -> !(p[0].getVehicle() instanceof com.thepearlking.stevespace.vessel.VesselEntity), () -> {
                check("names-landed", !(p[0].getVehicle() instanceof com.thepearlking.stevespace.vessel.VesselEntity), "vehicle " + p[0].getVehicle());
                com.thepearlking.stevespace.vessel.VesselServer.onControls(p[0], new com.thepearlking.stevespace.vessel.VesselControls(1, 0, 0, 0));
            });
            later(server, 10, () -> {
                var v = p[0].getVehicle();
                check("name-kept-through-landing", v instanceof com.thepearlking.stevespace.vessel.VesselEntity ship && ship.getName().getString().equals("Hi"),
                        "vehicle " + v + (v == null ? "" : " named " + v.getName().getString()));
                com.thepearlking.stevespace.vessel.VesselServer.onControls(p[0], com.thepearlking.stevespace.vessel.VesselControls.IDLE);
            });
        }
    }

    /** /stevespace home and ship, and nobody left floating in empty space. */
    /** A world made with the Real Earth type (level-type=stevespace:real_earth): the real map, really generated. */
    static final class RealEarthTests {
        static void run(MinecraftServer server) {
            var settings = com.thepearlking.stevespace.world.gen.earth.EarthWorldgen.settings();
            check("real-earth-settings", settings.real(), settings.toString());
            var level = server.overworld();
            var geo = new com.thepearlking.stevespace.world.planet.PlanetGeometry(com.thepearlking.stevespace.world.planet.earth.EarthClimate.RADIUS);
            String[] names = {"London", "mid-Atlantic", "Everest", "Kansas", "Tibet", "Sahara"};
            double[][] places = {{51.5, -0.1}, {30, -40}, {27.99, 86.93}, {38.5, -98}, {32, 88}, {23, 10}};
            int[] tops = new int[places.length];
            for (int i = 0; i < places.length; i++) {
                int x = (int) geo.xForLongitude(Math.toRadians(places[i][1]));
                int z = (int) geo.zForLatitude(Math.toRadians(places[i][0]));
                level.getChunk(x >> 4, z >> 4);
                tops[i] = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR, x, z);
                var biome = level.getBiome(new BlockPos(x, Math.max(63, tops[i]), z)).unwrapKey().map(k -> k.location().getPath()).orElse("?");
                StevesSpace.LOGGER.info("[RealEarth] {} ({}, {}) at x={} z={}: ground y={} biome {}", names[i], places[i][0], places[i][1], x, z, tops[i], biome);
            }
            check("real-earth-london-is-land", tops[0] >= 60, "y=" + tops[0]);
            check("real-earth-atlantic-is-sea", tops[1] < 50, "y=" + tops[1]);
            check("real-earth-himalaya-high", tops[2] > 120 && tops[4] > 110, "Everest y=" + tops[2] + ", Tibet y=" + tops[4]);
            // Across Hawaii's Big Island, west to east through Mauna Kea.
            for (double lon = -156.3; lon <= -154.6; lon += 0.1) {
                int x = (int) geo.xForLongitude(Math.toRadians(lon));
                int z = (int) geo.zForLatitude(Math.toRadians(19.8));
                level.getChunk(x >> 4, z >> 4);
                int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR, x, z);
                var biome = level.getBiome(new BlockPos(x, Math.max(63, y), z)).unwrapKey().map(k -> k.location().getPath()).orElse("?");
                StevesSpace.LOGGER.info("[RealEarth] Hawaii lon {} real {} m: ground y={} biome {}", String.format("%.1f", lon),
                        (int) com.thepearlking.stevespace.world.planet.earth.EarthElevation.metres(Math.toRadians(19.8), Math.toRadians(lon)), y, biome);
            }
            var spawn = level.getSharedSpawnPos();
            StevesSpace.LOGGER.info("[RealEarth] spawn {} = {}°, {}°", spawn, Math.toDegrees(geo.latitude(spawn.getZ())), Math.toDegrees(geo.longitude(spawn.getX())));
        }
    }

    static final class CommandTests {

        static void schedule(MinecraftServer server) {
            final net.minecraft.server.level.ServerPlayer[] p = new net.minecraft.server.level.ServerPlayer[1];
            final com.thepearlking.stevespace.vessel.VesselEntity[] ship = new com.thepearlking.stevespace.vessel.VesselEntity[1];
            // /stevespace goto: onto a world's ground, for several kinds of world.
            final net.minecraft.server.level.ServerPlayer[] g = new net.minecraft.server.level.ServerPlayer[1];
            later(server, 4, () -> {
                g[0] = VesselTests.mockPlayer(server.overworld(), "Goer");
                g[0].setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            });
            String[] worlds = {"mars", "hygiea", "acheron"};
            for (int i = 0; i < worlds.length; i++) {
                String w = worlds[i];
                later(server, 10 + i * 40, () -> server.getCommands().performPrefixedCommand(
                        g[0].createCommandSourceStack().withPermission(4), "stevespace goto " + w));
                later(server, 40 + i * 40, () -> {
                    var below = g[0].blockPosition().below();
                    boolean onGround = !g[0].level().getBlockState(below).isAir() || !g[0].level().getBlockState(below.below()).isAir();
                    check("goto-" + w, g[0].level().dimension().location().getPath().equals(w) && onGround,
                            "in " + g[0].level().dimension().location() + " at " + g[0].blockPosition() + " standing on " + g[0].level().getBlockState(below));
                });
            }
            later(server, 2, () -> {
                p[0] = VesselTests.mockPlayer(server.overworld(), "Lost");
                p[0].setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                ServerLevel space = server.getLevel(com.thepearlking.stevespace.world.SpaceDimension.KEY);
                // Far out in an empty cell: nothing there at all.
                p[0].teleportTo(space, 4096 * 900 + 0.5, 128, 0.5, java.util.Set.of(), 0, 0);
            });
            later(server, 140, () -> check("lost-in-empty-space-brought-back",
                    p[0].isAlive() && (p[0].level() == server.overworld() || com.thepearlking.stevespace.world.ParkedShips.cellAt(p[0].getX()) != 900),
                    "in " + p[0].level().dimension().location() + " at " + p[0].blockPosition()));
            // A ship in orbit; the player floats 150 blocks off it: the line pulls them back.
            later(server, 2, () -> {
                ServerLevel space = server.getLevel(com.thepearlking.stevespace.world.SpaceDimension.KEY);
                var body = com.thepearlking.stevespace.universe.ServerUniverse.system().earth();
                double r = body.radius() + 3000;
                var axis = body.spinAxis();
                var out = axis.cross(com.thepearlking.stevespace.universe.DVec3.X).normalize();
                var state = new com.thepearlking.stevespace.universe.StateVector(out.scale(r),
                        axis.cross(out).normalize().scale(Math.sqrt(body.mu() * SpaceConfigValue.gravity() / r)));
                var blocks = new java.util.HashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
                blocks.put(new BlockPos(0, 0, 0), com.thepearlking.stevespace.registry.ModBlocks.ROCKET_ENGINE.get().defaultBlockState());
                blocks.put(new BlockPos(0, 1, 0), com.thepearlking.stevespace.registry.ModBlocks.PILOT_SEAT.get().defaultBlockState());
                ship[0] = com.thepearlking.stevespace.vessel.VesselEntity.spawnInOrbit(space,
                        new com.thepearlking.stevespace.vessel.VesselStructure(blocks, new java.util.HashMap<>()), body, state, new org.joml.Quaterniond());
                p[0].teleportTo(space, ship[0].getX(), ship[0].getY() + 3, ship[0].getZ(), java.util.Set.of(), 0, 0);
                p[0].startRiding(ship[0], true);
            });
            later(server, 40, () -> {
                com.thepearlking.stevespace.vessel.VesselEntity.leaveGroundSeat(p[0]); // (it's blocks by now: a pilot seat holds on)
                p[0].teleportTo(p[0].serverLevel(), ship[0].getX() + 250, ship[0].getY(), ship[0].getZ(), java.util.Set.of(), 0, 0);
            });
            // (A player's motion is their own client's: here, check the line pulls them toward the ship.)
            later(server, 30, () -> check("tether-pulls-drifter-back", p[0].getDeltaMovement().x < -0.1 && p[0].level() != server.overworld(),
                    "motion " + p[0].getDeltaMovement() + " in " + p[0].level().dimension().location()));
            // Floating a little way off the ship (a spacewalk): left alone, nobody "rescues" them.
            later(server, 2, () -> {
                p[0].stopRiding();
                p[0].teleportTo(p[0].serverLevel(), ship[0].getX() + 10, ship[0].getY() + 2, ship[0].getZ(), java.util.Set.of(), 0, 0);
            });
            later(server, 300, () -> check("spacewalk-left-alone", p[0].level().dimension().equals(com.thepearlking.stevespace.world.SpaceDimension.KEY)
                            && p[0].position().distanceTo(ship[0].position()) < 30,
                    "in " + p[0].level().dimension().location() + String.format(" %.0f blocks from ship", p[0].position().distanceTo(ship[0].position()))));
            later(server, 2, () -> server.getCommands().performPrefixedCommand(p[0].createCommandSourceStack(), "stevespace home"));
            later(server, 5, () -> check("command-home", p[0].level() == server.overworld(), "in " + p[0].level().dimension().location()));
            later(server, 2, () -> server.getCommands().performPrefixedCommand(p[0].createCommandSourceStack(), "stevespace ship"));
            later(server, 10, () -> check("command-ship-back-aboard", com.thepearlking.stevespace.vessel.VesselEntity.shipOf(p[0], 4) == ship[0]
                            || p[0].getVehicle() == ship[0],
                    "vehicle " + p[0].getVehicle() + " in " + p[0].level().dimension().location()));
        }
    }

    /** Setting off nuclear devices the ways a player would: a button on it, and a remote relay. */
    static final class NukeTests {

        static void schedule(MinecraftServer server) {
            final BlockPos[] at = new BlockPos[4]; // device with button, its button, device by a relay, the relay
            later(server, 2, () -> {
                ServerLevel level = server.overworld();
                BlockPos spawn = level.getSharedSpawnPos();
                level.getChunk((spawn.getX() - 140) >> 4, (spawn.getZ() + 40) >> 4);
                BlockPos ground = VesselTests.clearArea(level, spawn.getX() - 140, spawn.getZ() + 40);
                var device = com.thepearlking.stevespace.registry.ModBlocks.NUCLEAR_DEVICE.get().defaultBlockState()
                        .setValue(com.thepearlking.stevespace.nuke.NuclearDeviceBlock.YIELD, 0);
                at[0] = ground.above();
                level.setBlockAndUpdate(at[0], device);
                // A stone button on its east face, the way a player sticks one on.
                at[1] = at[0].east();
                level.setBlockAndUpdate(at[1], net.minecraft.world.level.block.Blocks.STONE_BUTTON.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.ButtonBlock.FACING, net.minecraft.core.Direction.EAST)
                        .setValue(net.minecraft.world.level.block.ButtonBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.WALL));
                at[2] = ground.offset(0, 1, 12);
                level.setBlockAndUpdate(at[2], device.setValue(com.thepearlking.stevespace.nuke.NuclearDeviceBlock.ARMED, true));
                at[3] = at[2].east();
                level.setBlockAndUpdate(at[3], com.thepearlking.stevespace.registry.ModBlocks.RC_FUNCTION.get().defaultBlockState());
                press(level, at[1]);
            });
            later(server, 5, () -> check("nuke-button-unarmed-does-nothing",
                    server.overworld().getBlockState(at[0]).getBlock() instanceof com.thepearlking.stevespace.nuke.NuclearDeviceBlock,
                    String.valueOf(server.overworld().getBlockState(at[0]))));
            later(server, 40, () -> {
                ServerLevel level = server.overworld();
                // Armed quietly (no neighbour update), as sneak-clicking it with nothing powered does.
                level.setBlock(at[0], level.getBlockState(at[0]).setValue(com.thepearlking.stevespace.nuke.NuclearDeviceBlock.ARMED, true), 2);
                press(level, at[1]);
            });
            later(server, 3, () -> check("nuke-button-sets-off-armed-device", server.overworld().getBlockState(at[0]).isAir(),
                    String.valueOf(server.overworld().getBlockState(at[0]))));
            later(server, 2, () -> com.thepearlking.stevespace.remote.RcFunctionBlock.pulse(server.overworld(), at[3]));
            later(server, 3, () -> check("nuke-relay-sets-off-armed-device", server.overworld().getBlockState(at[2]).isAir(),
                    String.valueOf(server.overworld().getBlockState(at[2]))));
        }

        private static void press(ServerLevel level, BlockPos button) {
            var state = level.getBlockState(button);
            if (state.getBlock() instanceof net.minecraft.world.level.block.ButtonBlock b) {
                b.press(state, level, button, null);
            } else {
                check("nuke-button-placed", false, String.valueOf(state));
            }
        }
    }

    static final class RemoteTests {

        static void schedule(MinecraftServer server) {
            final net.minecraft.server.level.ServerPlayer[] p = new net.minecraft.server.level.ServerPlayer[1];
            final BlockPos[] at = new BlockPos[4]; // receiver, console, lamp, where the player stood
            final double[] y0 = new double[1];
            later(server, 2, () -> {
                ServerLevel level = server.overworld();
                BlockPos spawn = level.getSharedSpawnPos();
                level.getChunk((spawn.getX() + 120) >> 4, (spawn.getZ() + 60) >> 4);
                BlockPos ground = VesselTests.clearArea(level, spawn.getX() + 120, spawn.getZ() + 60);
                BlockPos seat = VesselTests.buildRocket(level, ground, 4);
                // The pilot seat becomes a receiver on channel 7, with a relay on function 2 and a lamp.
                level.setBlockAndUpdate(seat, com.thepearlking.stevespace.registry.ModBlocks.REMOTE_RECEIVER.get().defaultBlockState());
                if (level.getBlockEntity(seat) instanceof com.thepearlking.stevespace.remote.ChannelEntity rx) {
                    rx.setChannel(7);
                }
                BlockPos relay = seat.below().east();
                level.setBlockAndUpdate(relay, com.thepearlking.stevespace.registry.ModBlocks.RC_FUNCTION.get().defaultBlockState()
                        .setValue(com.thepearlking.stevespace.remote.RcFunctionBlock.FUNCTION, 2));
                if (level.getBlockEntity(relay) instanceof com.thepearlking.stevespace.remote.ChannelEntity rc) {
                    rc.setChannel(7);
                }
                // A relay on function 3 against an armed nuclear device, for the end.
                BlockPos relay3 = seat.below().west();
                level.setBlockAndUpdate(relay3, com.thepearlking.stevespace.registry.ModBlocks.RC_FUNCTION.get().defaultBlockState()
                        .setValue(com.thepearlking.stevespace.remote.RcFunctionBlock.FUNCTION, 3));
                if (level.getBlockEntity(relay3) instanceof com.thepearlking.stevespace.remote.ChannelEntity rc3) {
                    rc3.setChannel(7);
                }
                level.setBlockAndUpdate(relay3.west(), com.thepearlking.stevespace.registry.ModBlocks.NUCLEAR_DEVICE.get().defaultBlockState()
                        .setValue(com.thepearlking.stevespace.nuke.NuclearDeviceBlock.YIELD, 0)
                        .setValue(com.thepearlking.stevespace.nuke.NuclearDeviceBlock.ARMED, true));
                BlockPos lamp = relay.east();
                level.setBlockAndUpdate(lamp, net.minecraft.world.level.block.Blocks.REDSTONE_LAMP.defaultBlockState());
                BlockPos console = ground.offset(-5, 1, -5);
                level.setBlockAndUpdate(console.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(console, com.thepearlking.stevespace.registry.ModBlocks.REMOTE_CONSOLE.get().defaultBlockState());
                if (level.getBlockEntity(console) instanceof com.thepearlking.stevespace.remote.ChannelEntity cx) {
                    cx.setChannel(7);
                }
                at[0] = seat;
                at[1] = console;
                at[2] = lamp;
                p[0] = VesselTests.mockPlayer(level, "Remoteop");
                p[0].setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                p[0].teleportTo(level, console.getX() + 0.5, console.getY(), console.getZ() + 2.5, java.util.Set.of(), 0, 0);
                at[3] = p[0].blockPosition();
            });
            later(server, 5, () -> {
                var probes = com.thepearlking.stevespace.remote.RemoteControl.probes(server, 7);
                check("remote-console-lists-probe", probes.size() == 1 && probes.get(0).functions()[1] == 1,
                        probes.size() + " probes " + (probes.isEmpty() ? "" : java.util.Arrays.toString(probes.get(0).functions())));
                if (!probes.isEmpty()) {
                    com.thepearlking.stevespace.remote.RemoteControl.action(p[0], at[1], "connect", 0, probes.get(0).id());
                }
            });
            later(server, 5, () -> {
                check("remote-connect-puts-pilot-in-receiver", com.thepearlking.stevespace.remote.RemoteControl.isRemote(p[0])
                                && p[0].isInvisible() && p[0].getVehicle() instanceof com.thepearlking.stevespace.block.SeatEntity seat
                                && seat.blockPosition().equals(at[0]),
                        "remote " + com.thepearlking.stevespace.remote.RemoteControl.isRemote(p[0]) + " vehicle " + p[0].getVehicle());
                com.thepearlking.stevespace.remote.RemoteControl.function(p[0], 2);
            });
            later(server, 4, () -> check("remote-function-lights-lamp",
                    server.overworld().getBlockState(at[2]).getValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT),
                    String.valueOf(server.overworld().getBlockState(at[2]))));
            later(server, 30, () -> {
                y0[0] = p[0].getY();
                com.thepearlking.stevespace.vessel.VesselServer.onControls(p[0], new com.thepearlking.stevespace.vessel.VesselControls(1, 0, 0, 0));
            });
            later(server, 60, () -> {
                check("remote-throttle-launches-probe", p[0].getVehicle() instanceof com.thepearlking.stevespace.vessel.VesselEntity
                                && p[0].getY() > y0[0] + 5,
                        "vehicle " + p[0].getVehicle() + " y " + (int) y0[0] + " -> " + (int) p[0].getY());
                com.thepearlking.stevespace.vessel.VesselServer.onControls(p[0], com.thepearlking.stevespace.vessel.VesselControls.IDLE);
                com.thepearlking.stevespace.remote.RemoteControl.disconnect(p[0], "stevespace.remote.disconnected");
            });
            later(server, 3, () -> {
                check("remote-disconnect-back-at-console", !com.thepearlking.stevespace.remote.RemoteControl.isRemote(p[0])
                                && p[0].getVehicle() == null && !p[0].isInvisible() && p[0].blockPosition().distManhattan(at[3]) <= 1,
                        "at " + p[0].blockPosition() + " want " + at[3] + " vehicle " + p[0].getVehicle() + " invisible " + p[0].isInvisible());
                var probes = com.thepearlking.stevespace.remote.RemoteControl.probes(server, 7);
                check("remote-flying-probe-still-listed", probes.size() == 1 && probes.get(0).vessel() != null, probes.size() + " probes");
                if (!probes.isEmpty()) {
                    com.thepearlking.stevespace.remote.RemoteControl.action(p[0], at[1], "connect", 0, probes.get(0).id());
                }
            });
            later(server, 5, () -> {
                var v = p[0].getVehicle();
                check("remote-reconnect-in-flight", v instanceof com.thepearlking.stevespace.vessel.VesselEntity ship && ship.pilot() == p[0],
                        "vehicle " + v);
                // Function 3 sets off the nuclear device aboard: the craft is gone, the pilot home.
                com.thepearlking.stevespace.remote.RemoteControl.function(p[0], 3);
            });
            later(server, 3, () -> check("remote-function-nukes-craft-in-flight",
                    com.thepearlking.stevespace.vessel.VesselEntity.allServerVessels().stream().noneMatch(v -> !v.isRemoved()
                            && v.structure() != null && v.structure().blocks().values().stream()
                            .anyMatch(b -> b.getBlock() instanceof com.thepearlking.stevespace.nuke.NuclearDeviceBlock)),
                    "vessels " + com.thepearlking.stevespace.vessel.VesselEntity.allServerVessels().size()));
            later(server, 3, () -> check("remote-craft-lost-pilot-home-safe", !com.thepearlking.stevespace.remote.RemoteControl.isRemote(p[0])
                            && p[0].isAlive() && p[0].getVehicle() == null && p[0].blockPosition().distManhattan(at[3]) <= 1 && !p[0].isInvulnerable(),
                    "at " + p[0].blockPosition() + " alive " + p[0].isAlive() + " vehicle " + p[0].getVehicle()));
        }
    }

    static final class TravelTests {

        static void schedule(MinecraftServer server) {
            String to = System.getenv().getOrDefault("STEVESPACE_TRAVEL", "mars");
            String from = System.getenv().getOrDefault("STEVESPACE_TRAVEL_FROM", "earth");
            if (to.equals("acheron")) {
                // An encounter on its way: it reaches the Moon in a few hours.
                com.thepearlking.stevespace.world.AcheronEvent.testPass(server,
                        Double.parseDouble(System.getenv().getOrDefault("STEVESPACE_ACHERON_IN", "14400")));
            }
            final net.minecraft.server.level.ServerPlayer[] pilot = new net.minecraft.server.level.ServerPlayer[1];
            final int[] t = new int[1];
            if (from.equals("pad") || from.equals("padorbit")) {
                // As a player does it: a rocket on the ground, then "Fly to" (or first "Orbit").
                ServerLevel level = server.overworld();
                BlockPos spawn = level.getSharedSpawnPos();
                level.getChunk((spawn.getX() - 60) >> 4, (spawn.getZ() + 40) >> 4);
                BlockPos ground = VesselTests.clearArea(level, spawn.getX() - 60, spawn.getZ() + 40);
                BlockPos seat = VesselTests.buildRocket(level, ground, 6);
                later(server, 2, () -> {
                    pilot[0] = VesselTests.mockPlayer(level, "Traveller");
                    pilot[0].setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                    var scan = com.thepearlking.stevespace.vessel.VesselAssembler.scan(level, seat, 1000);
                    var v = com.thepearlking.stevespace.vessel.VesselEntity.launch(level, seat, scan.structure(), pilot[0]);
                    if (from.equals("pad")) {
                        v.startAutopilot(com.thepearlking.stevespace.vessel.Autopilot.Program.TRAVEL, to, true);
                    } else {
                        v.startAutopilot(com.thepearlking.stevespace.vessel.Autopilot.Program.ORBIT);
                    }
                });
                if (from.equals("padorbit")) {
                    final boolean[] started = new boolean[1];
                    waitUntil(server, 20 * 60 * 6, () -> pilot[0] != null && com.thepearlking.stevespace.vessel.VesselEntity.shipOf(pilot[0]) instanceof com.thepearlking.stevespace.vessel.VesselEntity v
                            && v.orbital() != null && !v.autopilotEngaged(), () -> {
                        if (com.thepearlking.stevespace.vessel.VesselEntity.shipOf(pilot[0]) instanceof com.thepearlking.stevespace.vessel.VesselEntity v) {
                            StevesSpace.LOGGER.info("[Travel] in orbit, now Fly to {}: {}", to, OrbitTests.describe(v));
                            com.thepearlking.stevespace.vessel.VesselServer.onAutopilot(pilot[0],
                                    com.thepearlking.stevespace.vessel.Autopilot.Program.TRAVEL, to, true);
                            started[0] = true;
                        }
                    });
                }
                watch(server, pilot, t, "pad", to);
                return;
            }
            later(server, 2, () -> {
                com.thepearlking.stevespace.universe.ServerUniverse.setWarp(server, 1);
                ServerLevel space = server.getLevel(com.thepearlking.stevespace.world.SpaceDimension.KEY);
                var body = com.thepearlking.stevespace.universe.ServerUniverse.system().body(from);
                double r = body.radius() + com.thepearlking.stevespace.vessel.VesselTransfer.spaceAltitude(body) + 700;
                // An equatorial orbit, eastward, as a launch from the equator gives.
                var axis = body.spinAxis();
                var out = axis.cross(com.thepearlking.stevespace.universe.DVec3.X).length() > 0.1
                        ? axis.cross(com.thepearlking.stevespace.universe.DVec3.X).normalize() : axis.cross(com.thepearlking.stevespace.universe.DVec3.Y).normalize();
                var state = new com.thepearlking.stevespace.universe.StateVector(out.scale(r),
                        axis.cross(out).normalize().scale(Math.sqrt(body.mu() / r)));
                var blocks = new java.util.HashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
                blocks.put(new BlockPos(0, 0, 0), com.thepearlking.stevespace.registry.ModBlocks.NUCLEAR_ENGINE.get().defaultBlockState());
                for (int i = 1; i <= 4; i++) {
                    blocks.put(new BlockPos(0, i, 0), com.thepearlking.stevespace.registry.ModBlocks.PROPELLANT_TANK.get().defaultBlockState());
                }
                blocks.put(new BlockPos(0, 5, 0), com.thepearlking.stevespace.registry.ModBlocks.PILOT_SEAT.get().defaultBlockState());
                var ship = com.thepearlking.stevespace.vessel.VesselEntity.spawnInOrbit(space,
                        new com.thepearlking.stevespace.vessel.VesselStructure(blocks, new java.util.HashMap<>()), body, state, new org.joml.Quaterniond());
                ship.fillTanks();
                pilot[0] = VesselTests.mockPlayer(server.overworld(), "Traveller");
                pilot[0].setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                pilot[0].teleportTo(space, ship.getX(), ship.getY() + 3, ship.getZ(), java.util.Set.of(), 0, 0);
                pilot[0].startRiding(ship, true);
            });
            later(server, 5, () -> com.thepearlking.stevespace.vessel.VesselServer.onAutopilot(pilot[0],
                    com.thepearlking.stevespace.vessel.Autopilot.Program.TRAVEL, to, !to.equals("sun")));
            if (to.equals("sun")) {
                watchSun(server, pilot, t, from);
                return;
            }
            watch(server, pilot, t, from, to);
        }

        /** Logs the trip to the Sun and checks it ends in a round orbit close to it. */
        static void watchSun(MinecraftServer server, net.minecraft.server.level.ServerPlayer[] pilot, int[] t, String from) {
            waitUntil(server, 20 * 60 * 30, () -> {
                var v = pilot[0] == null ? null : com.thepearlking.stevespace.vessel.VesselEntity.shipOf(pilot[0]);
                if (v == null) {
                    return false;
                }
                if (++t[0] % 200 == 0) {
                    var o = v.orbital();
                    StevesSpace.LOGGER.info("[Travel] t={}s {} {} warp={} {} {}", t[0] / 20, o == null ? "-" : o.body().id(),
                            o == null ? "-" : String.format("r=%.0f", o.state().position().length()),
                            com.thepearlking.stevespace.universe.UniverseClock.get(server).warp(), v.autopilotStatus(), v.autopilotDebug());
                }
                return t[0] > 200 && !v.autopilotEngaged();
            }, () -> {
                var v = com.thepearlking.stevespace.vessel.VesselEntity.shipOf(pilot[0]);
                var o = v == null ? null : v.orbital();
                boolean ok = false;
                String detail = "no ship";
                if (o != null) {
                    var sun = com.thepearlking.stevespace.universe.ServerUniverse.system().sun();
                    double mu = sun.mu() * SpaceConfigValue.gravity();
                    var el = com.thepearlking.stevespace.universe.Kepler.elementsFromState(o.state(), mu, o.time());
                    double r = o.state().position().length() / sun.radius();
                    ok = o.body().id().equals("sun") && r < 3.3 && el.eccentricity() < 0.06;
                    detail = String.format("body %s r=%.2f R e=%.3f prop=%.2f", o.body().id(), r, el.eccentricity(), v.propellant());
                }
                check("travel-" + from + "-to-close-sun-orbit", ok, detail);
                if (v != null && v.materialized()) {
                    // A spacewalker floating beside the ship this close to the Sun feels its heat;
                    // the pilot inside does not.
                    var walker = VesselTests.mockPlayer(server.overworld(), "Spacewalker");
                    walker.teleportTo((ServerLevel) v.level(), v.getX() + 20, v.getY(), v.getZ(), java.util.Set.of(), 0, 0);
                    var outside = com.thepearlking.stevespace.suit.Hazards.tick(walker, true);
                    var inside = com.thepearlking.stevespace.suit.Hazards.tick(pilot[0], true);
                    check("sun-heat-outside-not-inside", outside == com.thepearlking.stevespace.suit.Hazards.Hazard.HEAT && inside == null,
                            "outside " + outside + " inside " + inside);
                }
            });
        }

        /** Logs the trip and checks it ends seated on the destination's ground. */
        static void watch(MinecraftServer server, net.minecraft.server.level.ServerPlayer[] pilot, int[] t, String from, String to) {
            final String[] lastPhase = {""};
            final int[] flips = new int[1];
            waitUntil(server, 20 * 60 * 40, () -> {
                if (pilot[0] != null && com.thepearlking.stevespace.vessel.VesselEntity.shipOf(pilot[0]) instanceof com.thepearlking.stevespace.vessel.VesselEntity vv) {
                    String ph = vv.autopilotStatus();
                    if (!ph.equals(lastPhase[0])) {
                        flips[0]++;
                        var o = vv.orbital();
                        StevesSpace.LOGGER.info("[Phase] t={} {} -> {} ({} {})", t[0], lastPhase[0], ph,
                                o == null ? "-" : o.body().id(), o == null ? "y=" + (int) vv.mapY() : "alt=" + (int) o.altitude());
                        lastPhase[0] = ph;
                    }
                }
                if (++t[0] % 200 == 0 && pilot[0] != null && com.thepearlking.stevespace.vessel.VesselEntity.shipOf(pilot[0]) instanceof com.thepearlking.stevespace.vessel.VesselEntity v) {
                    var o = v.orbital();
                    StevesSpace.LOGGER.info("[Travel] t={}s {} {} {} speed {} warp={} {} time={}", t[0] / 20, v.level().dimension().location(),
                            o == null ? "-" : o.body().id(),
                            o == null ? "y=" + (int) v.mapY() : "alt=" + (int) o.altitude(),
                            String.format("%.1f", o != null ? o.state().velocity().length() : v.velocity().length()),
                            com.thepearlking.stevespace.universe.UniverseClock.get(server).warp(), v.autopilotStatus(),
                            (long) com.thepearlking.stevespace.universe.ServerUniverse.time(server) + " " + v.autopilotDebug());
                }
                return pilot[0] != null && t[0] > 200 && !(com.thepearlking.stevespace.vessel.VesselEntity.shipOf(pilot[0]) instanceof com.thepearlking.stevespace.vessel.VesselEntity);
            }, () -> check("travel-" + from + "-to-" + to + "-lands" + " (phase changes " + flips[0] + ")", pilot[0].getVehicle() instanceof com.thepearlking.stevespace.block.SeatEntity
                    && pilot[0].level().dimension().location().getPath().equals(to),
                    "vehicle=" + pilot[0].getVehicle() + " in " + pilot[0].level().dimension().location()));
        }
    }

    /**
     * "Fly to orbit" and "Circularize" from wherever the ship is: one flying off on an escape path
     * high above Earth, one on a long stretched orbit. Both should end in a round orbit.
     */
    static final class OrbitFixTests {

        static void schedule(MinecraftServer server) {
            run(server, "orbit-from-escape-path", com.thepearlking.stevespace.vessel.Autopilot.Program.ORBIT, 1.25, 0.6);
        }

        static void run(MinecraftServer server, String name, com.thepearlking.stevespace.vessel.Autopilot.Program program,
                        double speedOverEscape, double outward) {
            final net.minecraft.server.level.ServerPlayer[] pilot = new net.minecraft.server.level.ServerPlayer[1];
            later(server, 2, () -> {
                com.thepearlking.stevespace.universe.ServerUniverse.setWarp(server, 1);
                ServerLevel space = server.getLevel(com.thepearlking.stevespace.world.SpaceDimension.KEY);
                var body = com.thepearlking.stevespace.universe.ServerUniverse.system().earth();
                double r = body.radius() + com.thepearlking.stevespace.vessel.VesselTransfer.spaceAltitude(body) + 8000;
                var axis = body.spinAxis();
                var out = axis.cross(com.thepearlking.stevespace.universe.DVec3.X).normalize();
                var east = axis.cross(out).normalize();
                double v = Math.sqrt(2 * body.mu() * SpaceConfigValue.gravity() / r) * speedOverEscape;
                var velocity = east.scale(v * Math.sqrt(1 - outward * outward)).add(out.scale(v * outward));
                var state = new com.thepearlking.stevespace.universe.StateVector(out.scale(r), velocity);
                var blocks = new java.util.HashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
                blocks.put(new BlockPos(0, 0, 0), com.thepearlking.stevespace.registry.ModBlocks.NUCLEAR_ENGINE.get().defaultBlockState());
                for (int i = 1; i <= 4; i++) {
                    blocks.put(new BlockPos(0, i, 0), com.thepearlking.stevespace.registry.ModBlocks.PROPELLANT_TANK.get().defaultBlockState());
                }
                blocks.put(new BlockPos(0, 5, 0), com.thepearlking.stevespace.registry.ModBlocks.PILOT_SEAT.get().defaultBlockState());
                var ship = com.thepearlking.stevespace.vessel.VesselEntity.spawnInOrbit(space,
                        new com.thepearlking.stevespace.vessel.VesselStructure(blocks, new java.util.HashMap<>()), body, state, new org.joml.Quaterniond());
                ship.fillTanks();
                pilot[0] = VesselTests.mockPlayer(server.overworld(), "Orbiter");
                pilot[0].setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                pilot[0].teleportTo(space, ship.getX(), ship.getY() + 3, ship.getZ(), java.util.Set.of(), 0, 0);
                pilot[0].startRiding(ship, true);
            });
            later(server, 5, () -> com.thepearlking.stevespace.vessel.VesselServer.onAutopilot(pilot[0], program, "", false));
            final int[] t = new int[1];
            waitUntil(server, 20 * 60 * 5, () -> {
                if (++t[0] % 100 == 0 && com.thepearlking.stevespace.vessel.VesselEntity.shipOf(pilot[0]) instanceof com.thepearlking.stevespace.vessel.VesselEntity v) {
                    StevesSpace.LOGGER.info("[OrbitFix] t={}s {} {}", t[0] / 20, OrbitTests.describe(v), v.autopilotStatus());
                }
                return t[0] > 40 && com.thepearlking.stevespace.vessel.VesselEntity.shipOf(pilot[0]) instanceof com.thepearlking.stevespace.vessel.VesselEntity v && !v.autopilotEngaged();
            }, () -> {
                if (com.thepearlking.stevespace.vessel.VesselEntity.shipOf(pilot[0]) instanceof com.thepearlking.stevespace.vessel.VesselEntity v && v.orbital() != null) {
                    var o = v.orbital();
                    double mu = o.body().mu() * SpaceConfigValue.gravity();
                    var el = com.thepearlking.stevespace.universe.Kepler.elementsFromState(o.state(), mu, o.time());
                    check(name, el.eccentricity() < 0.02 && el.periapsis() - o.body().radius()
                                    > com.thepearlking.stevespace.vessel.VesselTransfer.spaceAltitude(o.body()),
                            "e=" + String.format("%.4f", el.eccentricity()) + " " + OrbitTests.describe(v));
                } else {
                    check(name, false, "not in orbit: " + pilot[0].getVehicle());
                }
            });
        }
    }

    /**
     * Ships are blocks in space: in orbit a ship becomes real blocks in its cell with the pilot in
     * the seat block; flying it from that seat moves the orbit and turns the ship without moving
     * a block; building onto it adds to it; coming down it lifts back out as a vessel and lands.
     */
    static final class BlockShipTests {

        static void schedule(MinecraftServer server) {
            final net.minecraft.server.level.ServerPlayer[] pilot = new net.minecraft.server.level.ServerPlayer[1];
            final com.thepearlking.stevespace.vessel.VesselEntity[] ship = new com.thepearlking.stevespace.vessel.VesselEntity[1];
            final double[] before = new double[2];
            final org.joml.Quaternionf[] startTurn = new org.joml.Quaternionf[1];
            final BlockPos[] seatBefore = new BlockPos[1];
            final net.minecraft.server.level.ServerPlayer[] rider = new net.minecraft.server.level.ServerPlayer[2];
            final double[] riderStart = new double[1];
            later(server, 1, () -> {
                // Lift-off with one person seated and one standing on the floor beside them.
                ServerLevel level = server.overworld();
                BlockPos spawn = level.getSharedSpawnPos();
                level.getChunk((spawn.getX() + 90) >> 4, (spawn.getZ() + 90) >> 4);
                BlockPos ground = VesselTests.clearArea(level, spawn.getX() + 90, spawn.getZ() + 90);
                BlockPos seat = VesselTests.buildRocket(level, ground, 4);
                level.setBlockAndUpdate(seat.below().east(), com.thepearlking.stevespace.registry.ModBlocks.HULL_WHITE.get().defaultBlockState());
                level.setBlockAndUpdate(seat.below().east(2), com.thepearlking.stevespace.registry.ModBlocks.HULL_WHITE.get().defaultBlockState());
                rider[0] = VesselTests.mockPlayer(level, "Liftpilot");
                rider[1] = VesselTests.mockPlayer(level, "Liftstander");
                rider[0].setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                rider[1].setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                rider[0].teleportTo(level, seat.getX() + 0.5, seat.getY(), seat.getZ() + 0.5, java.util.Set.of(), 0, 0);
                var seatEntity = com.thepearlking.stevespace.block.SeatEntity.create(level, seat);
                level.addFreshEntity(seatEntity);
                rider[0].startRiding(seatEntity, true);
                rider[1].teleportTo(level, seat.getX() + 2.5, seat.getY(), seat.getZ() + 0.5, java.util.Set.of(), 0, 0);
                riderStart[0] = rider[1].getY();
                com.thepearlking.stevespace.vessel.VesselServer.onControls(rider[0], new com.thepearlking.stevespace.vessel.VesselControls(1, 0, 0, 0));
            });
            later(server, 60, () -> {
                var v = rider[1].getVehicle();
                check("standing-crew-lifts-off-with-ship", v instanceof com.thepearlking.stevespace.vessel.VesselEntity
                                && rider[0].getVehicle() == v && rider[1].getY() > riderStart[0] + 3,
                        "stander in " + v + " y " + (int) riderStart[0] + " -> " + (int) rider[1].getY() + " pilot in " + rider[0].getVehicle());
                if (v instanceof com.thepearlking.stevespace.vessel.VesselEntity lifted) {
                    lifted.discard();
                }
            });
            later(server, 1, () -> {
                // A ship coming down turned a quarter: a window pane two blocks wide keeps its glass joined.
                ServerLevel level = server.overworld();
                var window = com.thepearlking.stevespace.registry.ModBlocks.SPACECRAFT_WINDOW.get();
                var blocks = new java.util.HashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
                var east = window.defaultBlockState().setValue(net.minecraft.world.level.block.PipeBlock.EAST, true);
                var west = window.defaultBlockState().setValue(net.minecraft.world.level.block.PipeBlock.WEST, true);
                blocks.put(new BlockPos(0, 0, 0), east);
                blocks.put(new BlockPos(1, 0, 0), west);
                BlockPos at = level.getSharedSpawnPos().offset(40, 60, 40);
                com.thepearlking.stevespace.vessel.VesselAssembler.placeInWorld(level, at,
                        new com.thepearlking.stevespace.vessel.VesselStructure(blocks, new java.util.HashMap<>()),
                        net.minecraft.world.level.block.Rotation.CLOCKWISE_90);
                var a0 = level.getBlockState(at);
                var a1 = level.getBlockState(at.offset(new BlockPos(1, 0, 0).rotate(net.minecraft.world.level.block.Rotation.CLOCKWISE_90)));
                check("window-joins-survive-turned-landing", a0.getValue(net.minecraft.world.level.block.PipeBlock.SOUTH)
                                && a1.getValue(net.minecraft.world.level.block.PipeBlock.NORTH)
                                && !a0.getValue(net.minecraft.world.level.block.PipeBlock.EAST), a0 + " / " + a1);
                level.setBlockAndUpdate(at, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(at.south(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            });
            later(server, 2, () -> {
                com.thepearlking.stevespace.universe.ServerUniverse.setWarp(server, 1);
                ServerLevel space = server.getLevel(com.thepearlking.stevespace.world.SpaceDimension.KEY);
                var body = com.thepearlking.stevespace.universe.ServerUniverse.system().earth();
                double r = body.radius() + com.thepearlking.stevespace.vessel.VesselTransfer.spaceAltitude(body) + 900;
                var axis = body.spinAxis();
                var out = axis.cross(com.thepearlking.stevespace.universe.DVec3.X).normalize();
                var east = axis.cross(out).normalize();
                var state = new com.thepearlking.stevespace.universe.StateVector(out.scale(r),
                        east.scale(Math.sqrt(body.mu() * SpaceConfigValue.gravity() / r)));
                var blocks = new java.util.HashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
                blocks.put(new BlockPos(0, 0, 0), com.thepearlking.stevespace.registry.ModBlocks.ROCKET_ENGINE.get().defaultBlockState()
                        .setValue(com.thepearlking.stevespace.block.RocketEngineBlock.FACING, net.minecraft.core.Direction.DOWN));
                for (int i = 1; i <= 3; i++) {
                    blocks.put(new BlockPos(0, i, 0), com.thepearlking.stevespace.registry.ModBlocks.PROPELLANT_TANK.get().defaultBlockState());
                }
                blocks.put(new BlockPos(0, 4, 0), com.thepearlking.stevespace.registry.ModBlocks.PILOT_SEAT.get().defaultBlockState());
                blocks.put(new BlockPos(0, 6, 0), com.thepearlking.stevespace.registry.ModBlocks.HEAT_SHIELD.get().defaultBlockState());
                blocks.put(new BlockPos(1, 5, 0), com.thepearlking.stevespace.registry.ModBlocks.HULL_WHITE.get().defaultBlockState());
                ship[0] = com.thepearlking.stevespace.vessel.VesselEntity.spawnInOrbit(space,
                        new com.thepearlking.stevespace.vessel.VesselStructure(blocks, new java.util.HashMap<>()), body, state, new org.joml.Quaterniond());
                ship[0].setCustomName(net.minecraft.network.chat.Component.literal("Blockship"));
                ship[0].fillTanks();
                pilot[0] = VesselTests.mockPlayer(server.overworld(), "Blockpilot");
                pilot[0].setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                pilot[0].teleportTo(space, ship[0].getX(), ship[0].getY() + 3, ship[0].getZ(), java.util.Set.of(), 0, 0);
                pilot[0].startRiding(ship[0], true);
            });
            later(server, 40, () -> {
                var v = ship[0];
                ServerLevel space = (ServerLevel) v.level();
                BlockPos seat = v.blockOrigin().offset(v.stats().seats().get(0).pos());
                check("blockship-is-blocks", v.materialized() && space.getBlockState(seat).getBlock() instanceof com.thepearlking.stevespace.block.PilotSeatBlock,
                        "materialized=" + v.materialized() + " seat " + seat + " " + space.getBlockState(seat));
                check("blockship-pilot-in-seat-block", pilot[0].getVehicle() instanceof com.thepearlking.stevespace.block.SeatEntity && v.pilot() == pilot[0],
                        "vehicle " + pilot[0].getVehicle());
                // Someone standing on the hull, not in a seat: they come down with it on their feet.
                rider[1].stopRiding();
                BlockPos deck = v.blockOrigin().offset(1, 6, 0);
                rider[1].teleportTo(space, deck.getX() + 0.5, deck.getY(), deck.getZ() + 0.5, java.util.Set.of(), 0, 0);
                before[0] = v.orbital().altitude();
                startTurn[0] = v.syncedOrientation();
                seatBefore[0] = seat;
                // Throttle up and turn from the seat.
                com.thepearlking.stevespace.vessel.VesselServer.onControls(pilot[0], new com.thepearlking.stevespace.vessel.VesselControls(1, 1, 0, 0));
            });
            final BlockPos[] seatAt = new BlockPos[1];
            later(server, 60, () -> {
                var v = ship[0];
                com.thepearlking.stevespace.vessel.VesselServer.onControls(pilot[0], com.thepearlking.stevespace.vessel.VesselControls.IDLE);
                ServerLevel space = (ServerLevel) v.level();
                seatAt[0] = v.blockOrigin().offset(v.stats().seats().get(0).pos());
                var el = v.orbital().elements(SpaceConfigValue.gravity());
                check("blockship-flies-from-seat", Math.abs(el.apoapsis() - el.periapsis()) > 20, "pe " + (int) el.periapsis() + " ap " + (int) el.apoapsis());
                float turned = new org.joml.Quaternionf(startTurn[0]).difference(v.syncedOrientation(), new org.joml.Quaternionf()).angle();
                check("blockship-turns-blocks-stay", turned > 0.05 && seatAt[0].equals(seatBefore[0])
                                && space.getBlockState(seatAt[0]).getBlock() instanceof com.thepearlking.stevespace.block.PilotSeatBlock,
                        "turned " + turned + " rad, seat " + seatBefore[0] + " -> " + seatAt[0]);
                // Build onto it like a player would.
                BlockPos onto = v.blockOrigin().offset(2, 5, 0);
                space.setBlockAndUpdate(onto, com.thepearlking.stevespace.registry.ModBlocks.HULL_GREY.get().defaultBlockState());
                before[1] = v.stats().dryMass();
            });
            later(server, 30, () -> {
                var v = ship[0];
                check("blockship-grows-when-built-on", v.stats().dryMass() > before[1], "mass " + before[1] + " -> " + v.stats().dryMass());
                com.thepearlking.stevespace.vessel.VesselServer.onAutopilot(pilot[0], com.thepearlking.stevespace.vessel.Autopilot.Program.LAND);
                StevesSpace.LOGGER.info("[BlockShip] landing: {}", v.autopilotStatus());
            });
            final int[] t = new int[1];
            waitUntil(server, 20 * 600, () -> {
                if (++t[0] % 200 == 0) {
                    var vehicle = pilot[0].getVehicle();
                    StevesSpace.LOGGER.info("[BlockShip] t={}s player in {} vehicle={} {}", t[0] / 20, pilot[0].level().dimension().location(),
                            vehicle == null ? "-" : vehicle.getClass().getSimpleName(),
                            ship[0] == null || ship[0].isRemoved() ? "" : (ship[0].materialized() ? "blocks " : "vessel ") + ship[0].autopilotStatus());
                }
                return t[0] > 100 && pilot[0].level() == server.overworld()
                        && !(com.thepearlking.stevespace.vessel.VesselEntity.shipOf(pilot[0]) instanceof com.thepearlking.stevespace.vessel.VesselEntity);
            }, () -> {
                check("blockship-lands-on-earth", pilot[0].level() == server.overworld()
                        && pilot[0].getVehicle() instanceof com.thepearlking.stevespace.block.SeatEntity, "vehicle " + pilot[0].getVehicle());
                check("standing-crew-lands-on-feet", rider[1].isAlive() && rider[1].level() == server.overworld()
                                && !(rider[1].getVehicle() instanceof com.thepearlking.stevespace.block.SeatEntity)
                                && rider[1].distanceTo(pilot[0]) < 8,
                        "alive " + rider[1].isAlive() + " in " + rider[1].level().dimension().location() + " vehicle " + rider[1].getVehicle()
                                + " " + (int) rider[1].distanceTo(pilot[0]) + " blocks from pilot");
                ServerLevel space = server.getLevel(com.thepearlking.stevespace.world.SpaceDimension.KEY);
                check("blockship-left-no-blocks-in-space", seatAt[0] != null && space.getBlockState(seatAt[0]).isAir(),
                        String.valueOf(seatAt[0] == null ? "" : space.getBlockState(seatAt[0])));
            });
        }
    }

    /**
     * Diving straight at a world with no autopilot: the ship must keep its speed through every
     * hand-off (orbit, map mode, the world) all the way down, never pausing or slowing.
     */
    static final class DiveTests {

        static void run(MinecraftServer server) {
            String id = System.getenv().getOrDefault("STEVESPACE_LAND", "moon");
            final com.thepearlking.stevespace.vessel.VesselEntity[] ship = new com.thepearlking.stevespace.vessel.VesselEntity[1];
            later(server, 2, () -> {
                com.thepearlking.stevespace.universe.ServerUniverse.setWarp(server, 1);
                ServerLevel space = server.getLevel(com.thepearlking.stevespace.world.SpaceDimension.KEY);
                var body = com.thepearlking.stevespace.universe.ServerUniverse.system().body(id);
                double r = body.radius() + com.thepearlking.stevespace.vessel.VesselTransfer.spaceAltitude(body) + 300;
                var axis = body.spinAxis();
                var out = axis.cross(com.thepearlking.stevespace.universe.DVec3.X).normalize();
                var east = axis.cross(out).normalize();
                var state = new com.thepearlking.stevespace.universe.StateVector(out.scale(r), out.scale(-250).add(east.scale(40)));
                var blocks = new java.util.HashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
                blocks.put(new BlockPos(0, 0, 0), com.thepearlking.stevespace.registry.ModBlocks.PILOT_SEAT.get().defaultBlockState());
                ship[0] = com.thepearlking.stevespace.vessel.VesselEntity.spawnInOrbit(space,
                        new com.thepearlking.stevespace.vessel.VesselStructure(blocks, new java.util.HashMap<>()), body, state, new org.joml.Quaterniond());
            });
            final double[] last = {-1};
            final String[] mode = {""};
            final int[] slow = {0};
            final int[] ticks = {0};
            later(server, 4, new Runnable() {
                @Override
                public void run() {
                    var v = ship[0];
                    ticks[0]++;
                    boolean giant = com.thepearlking.stevespace.universe.GiantAir.has(com.thepearlking.stevespace.universe.ServerUniverse.system().body(id));
                    int limit = giant ? 12000 : 2400;
                    if (giant && ticks[0] % 200 == 0 && v != null && v.inMapMode()) {
                        StevesSpace.LOGGER.info("[SelfTest] dive {}: t={}s altitude {} speed {}", id, ticks[0] / 20, String.format("%.0f", v.mapY() - 63),
                                String.format("%.1f", v.surfaceVelocity().length()));
                    }
                    if (v == null || v.isRemoved() && v.getRemovalReason() != net.minecraft.world.entity.Entity.RemovalReason.CHANGED_DIMENSION
                            || ticks[0] > limit) {
                        // A giant slows you smoothly (drag), so only sudden stops count; and it must end crushed.
                        boolean ok = (giant || slow[0] == 0) && ticks[0] <= limit;
                        StevesSpace.LOGGER.info("[SelfTest] dive {}: {} (slow ticks {}, last speed {})", id, ok ? "PASS" : "FAIL", slow[0], last[0]);
                        finish(server);
                        return;
                    }
                    if (v.isRemoved()) {
                        // Changing dimension: carry on with the copy in the new level.
                        for (ServerLevel level : server.getAllLevels()) {
                            for (var e : level.getAllEntities()) {
                                if (e instanceof com.thepearlking.stevespace.vessel.VesselEntity copy && !copy.isRemoved() && copy.getUUID().equals(v.getUUID())) {
                                    ship[0] = copy;
                                }
                            }
                        }
                        later(server, 1, this);
                        return;
                    }
                    String now = v.orbital() != null ? "orbit" : v.inMapMode() ? "map" : "world";
                    double speed = v.orbital() != null ? v.orbital().state().velocity().length() : v.surfaceVelocity().length();
                    if (!now.equals(mode[0])) {
                        StevesSpace.LOGGER.info("[SelfTest] dive {}: {} at speed {} (y {})", id, now, String.format("%.1f", speed),
                                String.format("%.0f", v.inMapMode() ? v.mapY() : v.getY()));
                        mode[0] = now;
                    } else if (last[0] > 0 && speed < last[0] * (giant ? 0.5 : 0.9)) {
                        slow[0]++;
                        StevesSpace.LOGGER.warn("[SelfTest] dive {}: slowed {} -> {} in {}", id, String.format("%.1f", last[0]), String.format("%.1f", speed), now);
                    }
                    last[0] = speed;
                    later(server, 1, this);
                }
            });
        }
    }

    /**
     * The autopilot lands safely on every world you can land on: from a low orbit, "Land", and
     * the pilot must end up sitting on the ground there, ship in one piece.
     */
    static final class LandAllTests {

        static final String[] WORLDS = {"moon", "pluto", "charon", "makemake", "eris", "haumea", "acheron", "ceres", "vesta", "pallas", "hygiea", "mercury", "mars", "io", "europa", "ganymede", "callisto",
                "enceladus", "triton", "titan", "venus", "earth"};

        static void run(MinecraftServer server, int index) {
            String only = System.getenv("STEVESPACE_LAND");
            if (index >= WORLDS.length) {
                if (only == null || java.util.Arrays.asList(only.split(",")).contains("saturn")) {
                    refusesGasGiant(server);
                }
                return;
            }
            String id = WORLDS[index];
            if (only != null && !java.util.Arrays.asList(only.split(",")).contains(id)) {
                run(server, index + 1);
                return;
            }
            final net.minecraft.server.level.ServerPlayer[] pilot = new net.minecraft.server.level.ServerPlayer[1];
            final int[] t = new int[1];
            later(server, 2, () -> {
                com.thepearlking.stevespace.universe.ServerUniverse.setWarp(server, 1);
                ServerLevel space = server.getLevel(com.thepearlking.stevespace.world.SpaceDimension.KEY);
                var body = com.thepearlking.stevespace.universe.ServerUniverse.system().body(id);
                // A low orbit, inside the body's own pull (tiny moons hold things only close in).
                double soi = com.thepearlking.stevespace.universe.ServerUniverse.system().sphereOfInfluence(body);
                // A normal parking orbit (well clear of any air), inside the body's own pull.
                double above = body.hasAtmosphere() ? body.atmosphere().height() + 1500 : com.thepearlking.stevespace.vessel.VesselTransfer.spaceAltitude(body) + 600;
                double r = body.radius() + Math.min(Math.max(above, com.thepearlking.stevespace.vessel.VesselTransfer.spaceAltitude(body) + 600),
                        (soi - body.radius()) * 0.8);
                var axis = body.spinAxis();
                var out = axis.cross(com.thepearlking.stevespace.universe.DVec3.X).normalize();
                var east = axis.cross(out).normalize();
                var state = new com.thepearlking.stevespace.universe.StateVector(out.scale(r),
                        east.scale(Math.sqrt(body.mu() * SpaceConfigValue.gravity() / r)));
                var blocks = new java.util.HashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
                blocks.put(new BlockPos(0, 0, 0), com.thepearlking.stevespace.registry.ModBlocks.ROCKET_ENGINE.get().defaultBlockState()
                        .setValue(com.thepearlking.stevespace.block.RocketEngineBlock.FACING, net.minecraft.core.Direction.DOWN));
                for (int i = 1; i <= 3; i++) {
                    blocks.put(new BlockPos(0, i, 0), com.thepearlking.stevespace.registry.ModBlocks.PROPELLANT_TANK.get().defaultBlockState());
                }
                blocks.put(new BlockPos(0, 4, 0), com.thepearlking.stevespace.registry.ModBlocks.PILOT_SEAT.get().defaultBlockState());
                blocks.put(new BlockPos(0, 5, 0), com.thepearlking.stevespace.registry.ModBlocks.HEAT_SHIELD.get().defaultBlockState());
                var ship = com.thepearlking.stevespace.vessel.VesselEntity.spawnInOrbit(space,
                        new com.thepearlking.stevespace.vessel.VesselStructure(blocks, new java.util.HashMap<>()), body, state, new org.joml.Quaterniond());
                ship.setCustomName(net.minecraft.network.chat.Component.literal("Lander " + id));
                ship.fillTanks();
                pilot[0] = VesselTests.mockPlayer(server.overworld(), "Lander" + index);
                pilot[0].setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                pilot[0].teleportTo(space, ship.getX(), ship.getY() + 3, ship.getZ(), java.util.Set.of(), 0, 0);
                pilot[0].startRiding(ship, true);
            });
            // STEVESPACE_SPOT=lat,lon (degrees): land on that spot, picked as on the map.
            String spot = System.getenv("STEVESPACE_SPOT");
            final double[] site = spot == null ? null : java.util.Arrays.stream(spot.split(",")).mapToDouble(s -> Math.toRadians(Double.parseDouble(s))).toArray();
            later(server, 30, () -> {
                if (site != null) {
                    com.thepearlking.stevespace.vessel.VesselServer.onAutopilot(pilot[0], com.thepearlking.stevespace.vessel.Autopilot.Program.TRAVEL,
                            id, true, site[0], site[1]);
                } else {
                    com.thepearlking.stevespace.vessel.VesselServer.onAutopilot(pilot[0], com.thepearlking.stevespace.vessel.Autopilot.Program.LAND);
                }
            });
            final int[] gone = {0};
            waitUntil(server, 20 * 60 * (site != null ? 20 : 8), () -> {
                var v = com.thepearlking.stevespace.vessel.VesselEntity.shipOf(pilot[0], 2);
                if (++t[0] % 100 == 0) {
                    StevesSpace.LOGGER.info("[LandAll] {} t={}s {} {}", id, t[0] / 20, pilot[0].level().dimension().location(),
                            v == null ? "vehicle=" + (pilot[0].getVehicle() == null ? "-" : pilot[0].getVehicle().getClass().getSimpleName())
                                    : (v.orbital() != null ? "alt=" + (int) v.orbital().altitude() : "y=" + (int) v.mapY() + " v=" + String.format("%.1f", v.velocity().length())
                                    + String.format(" (%.1f, %.1f, %.1f) thr=%.2f", v.velocity().x, v.velocity().y, v.velocity().z, v.throttle()))
                                    + " thr=" + String.format("%.2f", v.throttle()) + " " + v.autopilotDebug()
                                    + " " + v.autopilotStatus());
                }
                // Out of the ship for a whole second (not just the tick it turns into blocks or back).
                gone[0] = v == null ? gone[0] + 1 : 0;
                return t[0] > 60 && gone[0] > 20;
            }, () -> {
                boolean seated = pilot[0].getVehicle() instanceof com.thepearlking.stevespace.block.SeatEntity;
                String dim = pilot[0].level().dimension().location().toString();
                var body = com.thepearlking.stevespace.universe.ServerUniverse.system().body(id);
                boolean there = dim.equals(body.surfaceDimension());
                check("lands-safely-on-" + id, seated && there && pilot[0].isAlive(),
                        "in " + dim + " vehicle=" + pilot[0].getVehicle() + " alive=" + pilot[0].isAlive() + " after " + t[0] / 20 + "s");
                if (site != null) {
                    var geometry = new com.thepearlking.stevespace.world.planet.PlanetGeometry(body.radius());
                    double width = 2 * Math.PI * body.radius();
                    double dx = geometry.xForLongitude(site[1]) - pilot[0].getX();
                    dx -= Math.round(dx / width) * width;
                    double miss = Math.hypot(dx, geometry.zForLatitude(site[0]) - pilot[0].getZ());
                    check("lands-on-picked-spot-" + id, there && miss < 40, String.format("missed by %.0f blocks", miss));
                }
                run(server, index + 1);
            });
        }
    }

    /** "Land" around a world with no ground (Saturn) is refused: the ship stays safely in orbit. */
    static void refusesGasGiant(MinecraftServer server) {
        final net.minecraft.server.level.ServerPlayer[] pilot = new net.minecraft.server.level.ServerPlayer[1];
        final com.thepearlking.stevespace.vessel.VesselEntity[] ship = new com.thepearlking.stevespace.vessel.VesselEntity[1];
        later(server, 2, () -> {
            ServerLevel space = server.getLevel(com.thepearlking.stevespace.world.SpaceDimension.KEY);
            var body = com.thepearlking.stevespace.universe.ServerUniverse.system().body("saturn");
            double r = body.radius() * 3;
            var axis = body.spinAxis();
            var out = axis.cross(com.thepearlking.stevespace.universe.DVec3.X).normalize();
            var state = new com.thepearlking.stevespace.universe.StateVector(out.scale(r),
                    axis.cross(out).normalize().scale(Math.sqrt(body.mu() * SpaceConfigValue.gravity() / r)));
            var blocks = new java.util.HashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
            blocks.put(new BlockPos(0, 0, 0), com.thepearlking.stevespace.registry.ModBlocks.ROCKET_ENGINE.get().defaultBlockState());
            blocks.put(new BlockPos(0, 1, 0), com.thepearlking.stevespace.registry.ModBlocks.PILOT_SEAT.get().defaultBlockState());
            ship[0] = com.thepearlking.stevespace.vessel.VesselEntity.spawnInOrbit(space,
                    new com.thepearlking.stevespace.vessel.VesselStructure(blocks, new java.util.HashMap<>()), body, state, new org.joml.Quaterniond());
            pilot[0] = VesselTests.mockPlayer(server.overworld(), "GasGiant");
            pilot[0].setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            pilot[0].teleportTo(space, ship[0].getX(), ship[0].getY() + 3, ship[0].getZ(), java.util.Set.of(), 0, 0);
            pilot[0].startRiding(ship[0], true);
        });
        later(server, 30, () -> com.thepearlking.stevespace.vessel.VesselServer.onAutopilot(pilot[0],
                com.thepearlking.stevespace.vessel.Autopilot.Program.LAND));
        later(server, 100, () -> {
            var v = ship[0];
            check("land-refused-on-saturn", !v.isRemoved() && !v.autopilotEngaged() && v.orbital() != null
                            && v.orbital().altitude() > v.orbital().body().radius(),
                    "removed=" + v.isRemoved() + " autopilot=" + v.autopilotStatus() + " alt=" + (v.orbital() == null ? -1 : (int) v.orbital().altitude()));
        });
    }

    /** The physics gravity scale, readable before the config loads. */
    static final class SpaceConfigValue {
        static double gravity() {
            try {
                return com.thepearlking.stevespace.config.SpaceConfig.GRAVITY_SCALE.get();
            } catch (IllegalStateException e) {
                return 1;
            }
        }
    }

    /** Places a block on a parked ship the way a player would (so the ship grows to include it). */
    static void placeOnShip(ServerLevel space, BlockPos pos, net.minecraft.world.level.block.state.BlockState state,
                            net.minecraft.world.entity.Entity by) {
        space.setBlockAndUpdate(pos, state);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent(
                net.minecraftforge.common.util.BlockSnapshot.create(space.dimension(), space, pos), state, by));
    }

    /** Getting up in orbit parks the ship as blocks; sitting back down and throttling flies it again. */
    static final class ParkingTests {

        static void schedule(MinecraftServer server) {
            final com.thepearlking.stevespace.vessel.VesselEntity[] ship = new com.thepearlking.stevespace.vessel.VesselEntity[2];
            final net.minecraft.server.level.ServerPlayer[] crew = new net.minecraft.server.level.ServerPlayer[1];
            final BlockPos[] seat = new BlockPos[1];
            final int[] cell = new int[1];
            later(server, 2, () -> {
                com.thepearlking.stevespace.universe.ServerUniverse.setWarp(server, 1);
                ServerLevel space = server.getLevel(com.thepearlking.stevespace.world.SpaceDimension.KEY);
                var earth = com.thepearlking.stevespace.universe.ServerUniverse.system().earth();
                double r = earth.radius() + 3100;
                var state = new com.thepearlking.stevespace.universe.StateVector(
                        new com.thepearlking.stevespace.universe.DVec3(0, 0, r),
                        new com.thepearlking.stevespace.universe.DVec3(Math.sqrt(earth.mu() / r), 0, 0));
                var blocks = new java.util.HashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
                var hull = com.thepearlking.stevespace.registry.ModBlocks.HULL_GREY.get().defaultBlockState();
                blocks.put(new BlockPos(0, 0, 0), com.thepearlking.stevespace.registry.ModBlocks.ROCKET_ENGINE.get().defaultBlockState());
                blocks.put(new BlockPos(0, 1, 0), com.thepearlking.stevespace.registry.ModBlocks.PROPELLANT_TANK.get().defaultBlockState());
                blocks.put(new BlockPos(0, 2, 0), com.thepearlking.stevespace.registry.ModBlocks.PILOT_SEAT.get().defaultBlockState());
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx != 0 || dz != 0) {
                            blocks.put(new BlockPos(dx, 1, dz), hull); // a floor round the seat
                        }
                    }
                }
                ship[0] = com.thepearlking.stevespace.vessel.VesselEntity.spawnInOrbit(space,
                        new com.thepearlking.stevespace.vessel.VesselStructure(blocks, new java.util.HashMap<>()), earth, state, new org.joml.Quaterniond());
                ship[0].setCustomName(net.minecraft.network.chat.Component.literal("Parking test"));
                cell[0] = ship[0].orbital().cell();
                crew[0] = VesselTests.mockPlayer(server.overworld(), "Parker");
                crew[0].setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                crew[0].teleportTo(space, ship[0].getX(), ship[0].getY() + 4, ship[0].getZ(), java.util.Set.of(), 0, 0);
                crew[0].startRiding(ship[0], true);
            });
            later(server, 5, () -> {
                ServerLevel space = server.getLevel(com.thepearlking.stevespace.world.SpaceDimension.KEY);
                ship[0].requestDismount(crew[0]);
                var parked = com.thepearlking.stevespace.world.ParkedShips.get(server).get(cell[0]);
                seat[0] = null;
                if (parked != null) {
                    for (BlockPos p : BlockPos.betweenClosed(parked.min(), parked.max())) {
                        if (space.getBlockState(p).getBlock() instanceof com.thepearlking.stevespace.block.PilotSeatBlock) {
                            seat[0] = p.immutable();
                        }
                    }
                }
                check("parks-as-blocks", ship[0].isRemoved() && parked != null && seat[0] != null && !crew[0].isPassenger()
                                && crew[0].distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(seat[0])) < 4,
                        "removed=" + ship[0].isRemoved() + " parked=" + parked + " seat=" + seat[0] + " player=" + crew[0].position());
            });
            later(server, 25, () -> {
                double g = crew[0].getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.GRAVITY);
                check("parked-ship-weightless", g < 1e-4, "gravity " + g);
                ServerLevel space = server.getLevel(com.thepearlking.stevespace.world.SpaceDimension.KEY);
                // Machines only run in ticking chunks: a real player keeps theirs ticking, the test one doesn't.
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        space.setChunkForced((seat[0].getX() >> 4) + dx, (seat[0].getZ() >> 4) + dz, true);
                    }
                }
                space.setBlockAndUpdate(seat[0].below().east(2), com.thepearlking.stevespace.registry.ModBlocks.GRAVITY_GENERATOR.get().defaultBlockState());
                placeOnShip(space, seat[0].below().east(3), com.thepearlking.stevespace.registry.ModBlocks.CREATIVE_POWER.get().defaultBlockState(), crew[0]);
                // A roof over the crew, so they are inside the ship.
                BlockPos at = crew[0].blockPosition();
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        placeOnShip(space, at.offset(dx, 3, dz), com.thepearlking.stevespace.registry.ModBlocks.HULL_GREY.get().defaultBlockState(), crew[0]);
                    }
                }
            });
            later(server, 130, () -> {
                double g = crew[0].getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.GRAVITY);
                check("gravity-generator-pulls", g > 0.05, "gravity " + g);
                // Build a room onto the ship, well past where it ended, and move the generator there.
                ServerLevel space = server.getLevel(com.thepearlking.stevespace.world.SpaceDimension.KEY);
                var parked = com.thepearlking.stevespace.world.ParkedShips.get(server).get(cell[0]);
                space.setBlockAndUpdate(seat[0].below().east(2), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                var hull = com.thepearlking.stevespace.registry.ModBlocks.HULL_GREY.get().defaultBlockState();
                for (int i = 1; i <= 6; i++) {
                    BlockPos p = new BlockPos(parked.max().getX() + i, parked.min().getY(), seat[0].getZ());
                    space.setBlockAndUpdate(p, hull);
                    net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent(
                            net.minecraftforge.common.util.BlockSnapshot.create(space.dimension(), space, p), hull, crew[0]));
                }
                BlockPos far = new BlockPos(parked.max().getX() + 6, parked.min().getY() + 1, seat[0].getZ());
                var gen = com.thepearlking.stevespace.registry.ModBlocks.GRAVITY_GENERATOR.get().defaultBlockState();
                space.setBlockAndUpdate(far, gen);
                placeOnShip(space, far.above(), com.thepearlking.stevespace.registry.ModBlocks.CREATIVE_POWER.get().defaultBlockState(), crew[0]);
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent(
                        net.minecraftforge.common.util.BlockSnapshot.create(space.dimension(), space, far), hull, crew[0]));
                for (int dx = -3; dx <= 0; dx++) {
                    placeOnShip(space, far.offset(dx, 2, 0), hull, crew[0]);
                    placeOnShip(space, far.offset(dx, -1, 0), hull, crew[0]);
                }
                crew[0].teleportTo(far.getX() - 1.5, far.getY(), far.getZ() + 0.5);
            });
            later(server, 130, () -> {
                double g = crew[0].getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.GRAVITY);
                check("gravity-generator-in-added-room", g > 0.05, "gravity " + g);
                // Out on the hull, not inside: weightless.
                crew[0].teleportTo(crew[0].getX(), crew[0].getY() + 3.2, crew[0].getZ());
            });
            later(server, 30, () -> {
                double g = crew[0].getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.GRAVITY);
                check("outside-the-hull-floats", g < 1e-4, "gravity " + g + " at " + crew[0].position());
                ServerLevel space = server.getLevel(com.thepearlking.stevespace.world.SpaceDimension.KEY);
                space.setBlockAndUpdate(seat[0].below().east(2), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                var seatEntity = com.thepearlking.stevespace.block.SeatEntity.create(space, seat[0]);
                space.addFreshEntity(seatEntity);
                crew[0].startRiding(seatEntity, true);
                com.thepearlking.stevespace.vessel.VesselServer.onControls(crew[0], new com.thepearlking.stevespace.vessel.VesselControls(1, 0, 0, 0));
            });
            later(server, 5, () -> {
                ServerLevel space = server.getLevel(com.thepearlking.stevespace.world.SpaceDimension.KEY);
                boolean flying = com.thepearlking.stevespace.vessel.VesselEntity.shipOf(crew[0]) instanceof com.thepearlking.stevespace.vessel.VesselEntity v && v.orbital() != null
                        && v.orbital().cell() == cell[0];
                if (com.thepearlking.stevespace.vessel.VesselEntity.shipOf(crew[0]) instanceof com.thepearlking.stevespace.vessel.VesselEntity v && v.orbital() != null) {
                    double alt = v.orbital().altitude();
                    double speed = v.orbital().state().velocity().length();
                    check("same-orbit-after-parking", Math.abs(alt - 3100) < 50 && speed > 150,
                            String.format("alt %.0f speed %.1f", alt, speed));
                }
                // Flying again, and still blocks: the seat block stays where it was.
                check("flies-again-from-seat", flying && com.thepearlking.stevespace.world.ParkedShips.get(server).get(cell[0]) == null
                                && space.getBlockState(seat[0]).getBlock() instanceof com.thepearlking.stevespace.block.PilotSeatBlock,
                        "vehicle=" + crew[0].getVehicle() + " parked=" + com.thepearlking.stevespace.world.ParkedShips.get(server).get(cell[0]));
                if (com.thepearlking.stevespace.vessel.VesselEntity.shipOf(crew[0]) instanceof com.thepearlking.stevespace.vessel.VesselEntity v) {
                    v.discard();
                }
                crew[0].discard();
                // Like a ship whose pilot drifted off: in orbit, named, nobody aboard. It parks itself.
                var earth = com.thepearlking.stevespace.universe.ServerUniverse.system().earth();
                double r = earth.radius() + 3300;
                var state = new com.thepearlking.stevespace.universe.StateVector(
                        new com.thepearlking.stevespace.universe.DVec3(r, 0, 0),
                        new com.thepearlking.stevespace.universe.DVec3(0, 0, Math.sqrt(earth.mu() / r)));
                var blocks = new java.util.HashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
                blocks.put(new BlockPos(0, 0, 0), com.thepearlking.stevespace.registry.ModBlocks.HULL_WHITE.get().defaultBlockState());
                blocks.put(new BlockPos(0, 1, 0), com.thepearlking.stevespace.registry.ModBlocks.PILOT_SEAT.get().defaultBlockState());
                ship[1] = com.thepearlking.stevespace.vessel.VesselEntity.spawnInOrbit(space,
                        new com.thepearlking.stevespace.vessel.VesselStructure(blocks, new java.util.HashMap<>()), earth, state, new org.joml.Quaterniond());
                ship[1].setCustomName(net.minecraft.network.chat.Component.literal("Drifting ship"));
                cell[0] = ship[1].orbital().cell();
            });
            // The same in lunar orbit: park, sit back down, and it must still be orbiting the Moon.
            final net.minecraft.server.level.ServerPlayer[] lunar = new net.minecraft.server.level.ServerPlayer[1];
            final com.thepearlking.stevespace.vessel.VesselEntity[] moonShip = new com.thepearlking.stevespace.vessel.VesselEntity[1];
            later(server, 5, () -> {
                ServerLevel space = server.getLevel(com.thepearlking.stevespace.world.SpaceDimension.KEY);
                var moon = com.thepearlking.stevespace.universe.ServerUniverse.system().body("moon");
                double r = moon.radius() + 1500;
                var state = new com.thepearlking.stevespace.universe.StateVector(
                        new com.thepearlking.stevespace.universe.DVec3(r, 0, 0),
                        new com.thepearlking.stevespace.universe.DVec3(0, Math.sqrt(moon.mu() / r), 0));
                var blocks = new java.util.HashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
                blocks.put(new BlockPos(0, 0, 0), com.thepearlking.stevespace.registry.ModBlocks.ROCKET_ENGINE.get().defaultBlockState());
                blocks.put(new BlockPos(0, 1, 0), com.thepearlking.stevespace.registry.ModBlocks.PILOT_SEAT.get().defaultBlockState());
                blocks.put(new BlockPos(1, 0, 0), com.thepearlking.stevespace.registry.ModBlocks.HULL_GREY.get().defaultBlockState());
                moonShip[0] = com.thepearlking.stevespace.vessel.VesselEntity.spawnInOrbit(space,
                        new com.thepearlking.stevespace.vessel.VesselStructure(blocks, new java.util.HashMap<>()), moon, state, new org.joml.Quaterniond());
                moonShip[0].setCustomName(net.minecraft.network.chat.Component.literal("Lunar test"));
                lunar[0] = VesselTests.mockPlayer(server.overworld(), "Lunar");
                lunar[0].setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                lunar[0].teleportTo(space, moonShip[0].getX(), moonShip[0].getY() + 3, moonShip[0].getZ(), java.util.Set.of(), 0, 0);
                lunar[0].startRiding(moonShip[0], true);
            });
            later(server, 5, () -> moonShip[0].requestDismount(lunar[0]));
            later(server, 10, () -> {
                ServerLevel space = server.getLevel(com.thepearlking.stevespace.world.SpaceDimension.KEY);
                var parked = com.thepearlking.stevespace.world.ParkedShips.get(server).at(lunar[0].getX());
                BlockPos s2 = null;
                if (parked != null) {
                    for (BlockPos q : BlockPos.betweenClosed(parked.min(), parked.max())) {
                        if (space.getBlockState(q).getBlock() instanceof com.thepearlking.stevespace.block.PilotSeatBlock) {
                            s2 = q.immutable();
                        }
                    }
                }
                StevesSpace.LOGGER.info("[Parking] lunar parked={} seat={}", parked, s2);
                if (s2 != null) {
                    var seatEntity = com.thepearlking.stevespace.block.SeatEntity.create(space, s2);
                    space.addFreshEntity(seatEntity);
                    lunar[0].startRiding(seatEntity, true);
                    com.thepearlking.stevespace.vessel.VesselServer.onControls(lunar[0], new com.thepearlking.stevespace.vessel.VesselControls(0.01, 0, 0, 0));
                }
            });
            later(server, 20, () -> {
                boolean ok = com.thepearlking.stevespace.vessel.VesselEntity.shipOf(lunar[0]) instanceof com.thepearlking.stevespace.vessel.VesselEntity v && v.orbital() != null
                        && v.orbital().body().id().equals("moon") && Math.abs(v.orbital().altitude() - 1500) < 60;
                var v = com.thepearlking.stevespace.vessel.VesselEntity.shipOf(lunar[0]) instanceof com.thepearlking.stevespace.vessel.VesselEntity ve ? ve : null;
                check("lunar-orbit-after-parking", ok, v == null || v.orbital() == null ? "vehicle=" + lunar[0].getVehicle()
                        : v.orbital().body().id() + " alt " + (int) v.orbital().altitude() + " speed " + String.format("%.1f", v.orbital().state().velocity().length()));
            });
            later(server, 140, () -> check("uncrewed-ship-parks-itself",
                    ship[1] != null && ship[1].isRemoved() && com.thepearlking.stevespace.world.ParkedShips.get(server).get(cell[0]) != null,
                    "removed=" + (ship[1] == null ? null : ship[1].isRemoved())));
        }
    }

    static final class DockingTests {

        private static com.thepearlking.stevespace.vessel.VesselStructure module() {
            var blocks = new java.util.HashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
            blocks.put(new BlockPos(0, 0, 0), com.thepearlking.stevespace.registry.ModBlocks.HULL_GREY.get().defaultBlockState());
            blocks.put(new BlockPos(0, 1, 0), com.thepearlking.stevespace.registry.ModBlocks.DOCKING_PORT.get().defaultBlockState()
                    .setValue(com.thepearlking.stevespace.block.DockingPortBlock.FACING, net.minecraft.core.Direction.UP));
            return new com.thepearlking.stevespace.vessel.VesselStructure(blocks, new java.util.HashMap<>());
        }

        static void schedule(MinecraftServer server) {
            final com.thepearlking.stevespace.vessel.VesselEntity[] pair = new com.thepearlking.stevespace.vessel.VesselEntity[2];
            later(server, 2, () -> {
                com.thepearlking.stevespace.universe.ServerUniverse.setWarp(server, 1); // earlier tests may have left it warping
                ServerLevel space = server.getLevel(com.thepearlking.stevespace.world.SpaceDimension.KEY);
                var system = com.thepearlking.stevespace.universe.ServerUniverse.system();
                var earth = system.earth();
                double r = earth.radius() + 3000;
                double v = Math.sqrt(earth.mu() / r);
                var state = new com.thepearlking.stevespace.universe.StateVector(
                        new com.thepearlking.stevespace.universe.DVec3(r, 0, 0), new com.thepearlking.stevespace.universe.DVec3(0, v, 0));
                pair[0] = com.thepearlking.stevespace.vessel.VesselEntity.spawnInOrbit(space, module(), earth, state, new org.joml.Quaterniond());
                // The second, upside down, its port 0.3 blocks above the first one's.
                var flipped = new org.joml.Quaterniond().rotateX(Math.PI);
                var probe = com.thepearlking.stevespace.vessel.VesselEntity.spawnInOrbit(space, module(), earth, state, flipped);
                var a = pair[0];
                double faceA = 2.0 - a.stats().centerOfMass().y;          // top of the first port above its centre
                double faceB = 2.0 - probe.stats().centerOfMass().y;      // the same on the second (pointing down)
                double up = faceA + 0.3 + faceB;
                var axisY = a.orbital().axisY();
                probe.orbital().state();
                com.thepearlking.stevespace.vessel.VesselEntityAccess.setState(probe,
                        new com.thepearlking.stevespace.universe.StateVector(state.position().add(axisY.scale(up)), state.velocity()));
                pair[1] = probe;
            });
            final int[] n = new int[1];
            waitUntil(server, 100, () -> {
                if (pair[1] != null && n[0]++ % 10 == 0 && !pair[1].isRemoved()) {
                    StevesSpace.LOGGER.info("[SelfTest] docking: a at {} b at {} anchor={} sep={}", pair[0].position(), pair[1].position(),
                            pair[1].orbital().anchor(), pair[1].orbital().state().position().sub(pair[0].orbital().state().position()).length());
                }
                return pair[1] != null && pair[1].isRemoved();
            }, () -> {
                var a = pair[0];
                check("vessels-dock", pair[1] != null && pair[1].isRemoved() && a.structure().size() == 4 && a.stats().ports().size() == 2,
                        "visitor removed=" + (pair[1] != null && pair[1].isRemoved()) + " blocks=" + a.structure().size());
            });
            later(server, 5, () -> {
                var a = pair[0];
                boolean split = a.undock();
                check("vessels-undock", split && a.structure().size() == 2, "split=" + split + " blocks=" + a.structure().size());
            });
            later(server, 60, () -> {
                var a = pair[0];
                long modules = com.thepearlking.stevespace.vessel.VesselEntity.trackingList(server).stream()
                        .filter(e -> e.mode() == 2).count();
                check("undocked-module-flies-off", modules >= 2, "vessels in orbit: " + modules);
            });
            // The approach autopilot: a crewed chaser 400 blocks behind closes in and stops nearby.
            final com.thepearlking.stevespace.vessel.VesselEntity[] chase = new com.thepearlking.stevespace.vessel.VesselEntity[2];
            final int[] ticks = new int[1];
            later(server, 2, () -> {
                ServerLevel space = server.getLevel(com.thepearlking.stevespace.world.SpaceDimension.KEY);
                var system = com.thepearlking.stevespace.universe.ServerUniverse.system();
                var earth = system.earth();
                double r = earth.radius() + 3200;
                double v = Math.sqrt(earth.mu() / r);
                var station = new com.thepearlking.stevespace.universe.StateVector(
                        new com.thepearlking.stevespace.universe.DVec3(0, r, 0), new com.thepearlking.stevespace.universe.DVec3(-v, 0, 0));
                chase[0] = com.thepearlking.stevespace.vessel.VesselEntity.spawnInOrbit(space, module(), earth, station, new org.joml.Quaterniond());
                var blocks = new java.util.HashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
                blocks.put(new BlockPos(0, 0, 0), com.thepearlking.stevespace.registry.ModBlocks.ROCKET_ENGINE.get().defaultBlockState());
                blocks.put(new BlockPos(0, 1, 0), com.thepearlking.stevespace.registry.ModBlocks.PROPELLANT_TANK.get().defaultBlockState());
                blocks.put(new BlockPos(0, 2, 0), com.thepearlking.stevespace.registry.ModBlocks.PILOT_SEAT.get().defaultBlockState());
                double angle = 400.0 / r;
                var behind = new com.thepearlking.stevespace.universe.StateVector(
                        new com.thepearlking.stevespace.universe.DVec3(r * Math.sin(angle), r * Math.cos(angle), 0),
                        new com.thepearlking.stevespace.universe.DVec3(-v * Math.cos(angle), v * Math.sin(angle), 0));
                chase[1] = com.thepearlking.stevespace.vessel.VesselEntity.spawnInOrbit(space,
                        new com.thepearlking.stevespace.vessel.VesselStructure(blocks, new java.util.HashMap<>()), earth, behind, new org.joml.Quaterniond());
                var pilot = VesselTests.mockPlayer(server.overworld(), "TestChaser");
                pilot.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                pilot.teleportTo(space, chase[1].getX(), chase[1].getY() + 3, chase[1].getZ(), java.util.Set.of(), 0, 0);
                pilot.startRiding(chase[1], true);
                com.thepearlking.stevespace.vessel.Targets.set(pilot, chase[0].getUUID());
                chase[1].startAutopilot(com.thepearlking.stevespace.vessel.Autopilot.Program.APPROACH);
            });
            waitUntil(server, 20 * 240, () -> {
                if (++ticks[0] % 200 == 0) {
                    var d = chase[1].orbital().state().position().sub(chase[0].orbital().state().position());
                    var u = chase[1].orbital().state().velocity().sub(chase[0].orbital().state().velocity());
                    StevesSpace.LOGGER.info("[SelfTest] approach t={}s distance {} closing {} {}", ticks[0] / 20,
                            String.format("%.1f", d.length()), String.format("%.2f", u.length()), chase[1].autopilotStatus());
                }
                return chase[1].isRemoved() || !chase[1].autopilotEngaged();
            }, () -> {
                var d = chase[1].orbital().state().position().sub(chase[0].orbital().state().position()).length();
                var u = chase[1].orbital().state().velocity().sub(chase[0].orbital().state().velocity()).length();
                check("approach-target", d < 35 && u < 0.6, String.format("distance %.1f, relative speed %.2f after %ds", d, u, ticks[0] / 20));
            });
        }
    }

    /**
     * Docking with the autopilot: a station parked in orbit, a crewed ship a few kilometres off in
     * a different orbit picks it from the dock list, flies there and docks (with RCS thrusters,
     * then a ship without any). Plus who's on the list, and power across docked ports.
     */
    static final class DockTests {

        private static net.minecraft.world.level.block.state.BlockState port(net.minecraft.core.Direction facing) {
            return com.thepearlking.stevespace.registry.ModBlocks.DOCKING_PORT.get().defaultBlockState()
                    .setValue(com.thepearlking.stevespace.block.DockingPortBlock.FACING, facing);
        }

        /** A small station: a hull column with a radio rack and a port sticking out east. */
        private static com.thepearlking.stevespace.vessel.VesselStructure station() {
            var blocks = new java.util.HashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
            var hull = com.thepearlking.stevespace.registry.ModBlocks.HULL_WHITE.get().defaultBlockState();
            for (int y = 0; y < 4; y++) {
                blocks.put(new BlockPos(0, y, 0), hull);
                blocks.put(new BlockPos(1, y, 0), hull);
            }
            blocks.put(new BlockPos(0, 4, 0), com.thepearlking.stevespace.registry.ModBlocks.RADIO_RACK.get().defaultBlockState());
            blocks.put(new BlockPos(1, -1, 0), port(net.minecraft.core.Direction.DOWN)); // underneath: the ship's nose port meets it upright
            return new com.thepearlking.stevespace.vessel.VesselStructure(blocks, new java.util.HashMap<>());
        }

        /** The visiting ship: engine, tank, pilot seat, a port on the nose, and RCS round the tank. */
        private static com.thepearlking.stevespace.vessel.VesselStructure chaser(boolean rcs) {
            var blocks = new java.util.HashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
            blocks.put(new BlockPos(0, 0, 0), com.thepearlking.stevespace.registry.ModBlocks.ROCKET_ENGINE.get().defaultBlockState());
            blocks.put(new BlockPos(0, 1, 0), com.thepearlking.stevespace.registry.ModBlocks.PROPELLANT_TANK.get().defaultBlockState());
            blocks.put(new BlockPos(0, 2, 0), com.thepearlking.stevespace.registry.ModBlocks.PILOT_SEAT.get().defaultBlockState());
            blocks.put(new BlockPos(0, 3, 0), com.thepearlking.stevespace.registry.ModBlocks.HULL_GREY.get().defaultBlockState());
            blocks.put(new BlockPos(0, 4, 0), port(net.minecraft.core.Direction.UP));
            if (rcs) {
                for (var d : new net.minecraft.core.Direction[]{net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.SOUTH,
                        net.minecraft.core.Direction.EAST, net.minecraft.core.Direction.WEST}) {
                    blocks.put(new BlockPos(0, 1, 0).relative(d), com.thepearlking.stevespace.registry.ModBlocks.RCS_THRUSTER.get()
                            .defaultBlockState().setValue(com.thepearlking.stevespace.block.RcsThrusterBlock.FACING, d));
                }
            }
            return new com.thepearlking.stevespace.vessel.VesselStructure(blocks, new java.util.HashMap<>());
        }

        private static com.thepearlking.stevespace.universe.StateVector circular(double radius, double angle) {
            double v = Math.sqrt(com.thepearlking.stevespace.universe.ServerUniverse.system().earth().mu()
                    * com.thepearlking.stevespace.config.SpaceConfig.GRAVITY_SCALE.get() / radius);
            return new com.thepearlking.stevespace.universe.StateVector(
                    new com.thepearlking.stevespace.universe.DVec3(radius * Math.cos(angle), radius * Math.sin(angle), 0),
                    new com.thepearlking.stevespace.universe.DVec3(-v * Math.sin(angle), v * Math.cos(angle), 0));
        }

        static void schedule(MinecraftServer server) {
            listAndPower(server);
            dock(server, true, 0.3, 4000, 200);
            dock(server, false, 2.5, 4000, 200);
        }

        /** One docking run: the station at {@code angle} round the Earth, the ship {@code behind} blocks behind and {@code lower} lower. */
        static void dock(MinecraftServer server, boolean rcs, double angle, double behind, double lower) {
            String what = rcs ? "rcs" : "no-rcs";
            final com.thepearlking.stevespace.vessel.VesselEntity[] ship = new com.thepearlking.stevespace.vessel.VesselEntity[1];
            final java.util.UUID[] stationId = new java.util.UUID[1];
            final net.minecraft.server.level.ServerPlayer[] pilot = new net.minecraft.server.level.ServerPlayer[1];
            final int[] ticks = new int[1];
            later(server, 2, () -> {
                com.thepearlking.stevespace.universe.ServerUniverse.setWarp(server, 1);
                ServerLevel space = server.getLevel(com.thepearlking.stevespace.world.SpaceDimension.KEY);
                var earth = com.thepearlking.stevespace.universe.ServerUniverse.system().earth();
                double r = earth.radius() + 3000;
                var station = com.thepearlking.stevespace.vessel.VesselEntity.spawnInOrbit(space, station(), earth, circular(r, angle),
                        new org.joml.Quaterniond());
                station.setCustomName(net.minecraft.network.chat.Component.literal("Test Station " + what));
                stationId[0] = station.getUUID();
                station.park(null);
                ship[0] = com.thepearlking.stevespace.vessel.VesselEntity.spawnInOrbit(space, chaser(rcs), earth,
                        circular(r - lower, angle - behind / r), new org.joml.Quaterniond());
                ship[0].setCustomName(net.minecraft.network.chat.Component.literal("Chaser " + what));
                // A new pilot each run (the test world keeps the ships and players earlier runs left).
                pilot[0] = VesselTests.mockPlayer(server.overworld(), "Dk" + (rcs ? "A" : "B") + Integer.toHexString(new java.util.Random().nextInt(0xFFFFFF)));
                pilot[0].setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                pilot[0].teleportTo(space, ship[0].getX(), ship[0].getY() + 3, ship[0].getZ(), java.util.Set.of(), 0, 0);
                pilot[0].startRiding(ship[0], true);
            });
            later(server, 40, () -> {
                var list = com.thepearlking.stevespace.vessel.DockList.build(pilot[0]);
                var entry = list.ships().stream().filter(e -> stationId[0].equals(e.id())).findFirst().orElse(null);
                check("dock-list-has-parked-station-" + what, entry != null && !entry.ports().isEmpty() && !list.mine().isEmpty()
                        && list.rcs() == rcs, "ships " + list.ships().size() + " mine " + list.mine().size() + " rcs " + list.rcs());
                if (entry != null && !list.mine().isEmpty()) {
                    com.thepearlking.stevespace.vessel.VesselServer.onDock(pilot[0], entry.id(), entry.cell(), list.mine().get(0).pos(),
                            entry.ports().get(0).pos());
                }
            });
            waitUntil(server, 20 * 900, () -> {
                var own = com.thepearlking.stevespace.vessel.VesselEntity.shipOf(pilot[0], 2);
                if (++ticks[0] % 100 == 0) {
                    StevesSpace.LOGGER.info("[SelfTest] dock {} t={}s {} {}", what, ticks[0] / 20,
                            own == null ? "no ship" : own.autopilotStatus(), own == null ? "" : own.autopilotDebug());
                }
                return own != null && own.structure().blocks().values().stream()
                        .anyMatch(b -> b.is(com.thepearlking.stevespace.registry.ModBlocks.RADIO_RACK.get()));
            }, () -> {
                var own = com.thepearlking.stevespace.vessel.VesselEntity.shipOf(pilot[0], 2);
                boolean joined = own != null && own.structure().blocks().values().stream()
                        .anyMatch(b -> b.is(com.thepearlking.stevespace.registry.ModBlocks.RADIO_RACK.get()));
                check("autopilot-docks-with-station-" + what, joined, String.format("after %d s%s", ticks[0] / 20,
                        own == null ? ", no ship" : ", " + own.structure().size() + " blocks"));
                pilot[0].stopRiding();
                com.thepearlking.stevespace.universe.ServerUniverse.setWarp(server, 1);
            });
        }

        /** Who's on the dock list, and power crossing a pair of docked ports only when both share. */
        static void listAndPower(MinecraftServer server) {
            final net.minecraft.server.level.ServerPlayer[] pilot = new net.minecraft.server.level.ServerPlayer[1];
            final java.util.UUID[] ids = new java.util.UUID[3];
            later(server, 2, () -> {
                ServerLevel space = server.getLevel(com.thepearlking.stevespace.world.SpaceDimension.KEY);
                var earth = com.thepearlking.stevespace.universe.ServerUniverse.system().earth();
                double r = earth.radius() + 3500;
                var mine = com.thepearlking.stevespace.vessel.VesselEntity.spawnInOrbit(space, chaser(true), earth, circular(r, 4.0),
                        new org.joml.Quaterniond());
                java.util.UUID stranger = java.util.UUID.randomUUID();
                // Someone else's, no radio: not listed. Someone else's with a radio: listed. Mine: listed.
                var silent = com.thepearlking.stevespace.vessel.VesselEntity.spawnInOrbit(space, chaser(false), earth, circular(r, 4.01),
                        new org.joml.Quaterniond());
                com.thepearlking.stevespace.vessel.VesselEntityAccess.setOwner(silent, stranger);
                var talking = com.thepearlking.stevespace.vessel.VesselEntity.spawnInOrbit(space, station(), earth, circular(r, 4.02),
                        new org.joml.Quaterniond());
                com.thepearlking.stevespace.vessel.VesselEntityAccess.setOwner(talking, stranger);
                pilot[0] = VesselTests.mockPlayer(server.overworld(), "Ls" + Integer.toHexString(new java.util.Random().nextInt(0xFFFFFF)));
                var own = com.thepearlking.stevespace.vessel.VesselEntity.spawnInOrbit(space, chaser(true), earth, circular(r, 3.99),
                        new org.joml.Quaterniond());
                com.thepearlking.stevespace.vessel.VesselEntityAccess.setOwner(mine, pilot[0].getUUID());
                pilot[0].teleportTo(space, own.getX(), own.getY() + 3, own.getZ(), java.util.Set.of(), 0, 0);
                pilot[0].startRiding(own, true);
                ids[0] = mine.getUUID();
                ids[1] = silent.getUUID();
                ids[2] = talking.getUUID();
            });
            later(server, 20, () -> {
                var list = com.thepearlking.stevespace.vessel.DockList.build(pilot[0]);
                java.util.Set<java.util.UUID> listed = new java.util.HashSet<>();
                list.ships().forEach(e -> listed.add(e.id()));
                check("dock-list-who", listed.contains(ids[0]) && !listed.contains(ids[1]) && listed.contains(ids[2]),
                        "mine " + listed.contains(ids[0]) + ", stranger's silent " + listed.contains(ids[1]) + ", stranger's with radio "
                                + listed.contains(ids[2]));
                pilot[0].stopRiding();
            });
            // Power: creative source, port facing east, port facing west, battery. Only both sharing lets it through.
            ServerLevel level = server.overworld();
            BlockPos spawn = level.getSharedSpawnPos();
            final BlockPos[] at = new BlockPos[1];
            later(server, 2, () -> {
                int x = spawn.getX() + 170;
                int z = spawn.getZ() - 30;
                for (int cx = (x >> 4) - 1; cx <= (x >> 4) + 1; cx++) {
                    for (int cz = (z >> 4) - 1; cz <= (z >> 4) + 1; cz++) {
                        level.setChunkForced(cx, cz, true);
                    }
                }
                at[0] = VesselTests.clearArea(level, x, z).above(4);
                for (int i = 0; i < 4; i++) {
                    level.setBlockAndUpdate(at[0].east(i), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()); // what earlier runs left
                }
                level.setBlockAndUpdate(at[0], com.thepearlking.stevespace.registry.ModBlocks.CREATIVE_POWER.get().defaultBlockState());
                level.setBlockAndUpdate(at[0].east(), port(net.minecraft.core.Direction.EAST));
                level.setBlockAndUpdate(at[0].east(2), port(net.minecraft.core.Direction.WEST));
                level.setBlockAndUpdate(at[0].east(3), com.thepearlking.stevespace.registry.ModBlocks.SMALL_BATTERY.get().defaultBlockState());
            });
            java.util.function.IntSupplier stored = () -> level.getBlockEntity(at[0].east(3))
                    instanceof com.thepearlking.stevespace.energy.PowerBlockEntity p ? p.buffer().getEnergyStored() : -1;
            later(server, 1, () -> StevesSpace.LOGGER.info("[SelfTest] power: battery {} at start, around {}", stored.getAsInt(),
                    java.util.stream.IntStream.range(-1, 5).mapToObj(i -> level.getBlockState(at[0].east(i)).getBlock().getName().getString())
                            .toList()));
            later(server, 60, () -> {
                check("docked-ports-keep-power-apart", stored.getAsInt() == 0, "battery " + stored.getAsInt());
                var a = level.getBlockState(at[0].east());
                level.setBlockAndUpdate(at[0].east(), a.setValue(com.thepearlking.stevespace.block.DockingPortBlock.SHARING, true));
            });
            later(server, 60, () -> {
                check("one-port-sharing-not-enough", stored.getAsInt() == 0, "battery " + stored.getAsInt());
                var b = level.getBlockState(at[0].east(2));
                level.setBlockAndUpdate(at[0].east(2), b.setValue(com.thepearlking.stevespace.block.DockingPortBlock.SHARING, true));
            });
            later(server, 60, () -> check("both-ports-sharing-pass-power", stored.getAsInt() > 0, "battery " + stored.getAsInt()));
        }
    }

    private static double sunHour(MinecraftServer server, double t) {
        var sys = com.thepearlking.stevespace.universe.ServerUniverse.system();
        var geo = new com.thepearlking.stevespace.world.planet.PlanetGeometry(sys.earth().radius());
        BlockPos spawn = server.overworld().getSharedSpawnPos();
        return com.thepearlking.stevespace.universe.LocalSky.at(sys, sys.earth(), geo.latitude(spawn.getZ()),
                geo.longitude(spawn.getX()), 0, t).hourAngle(sys, sys.sun(), t);
    }

    /** World generation: Earth is vanilla terrain laid out as a planet. */
    static final class WorldGenTests {
        static void run(MinecraftServer server) {
            ServerLevel level = server.overworld();
            var router = level.getChunkSource().randomState().router();
            var geometry = com.thepearlking.stevespace.world.WorldWrap.geometryOf(level);
            check("overworld-is-earth", geometry != null && level.getChunkSource().getGenerator()
                    instanceof net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator, String.valueOf(geometry));
            if (geometry == null) {
                return;
            }

            BlockPos spawn = level.getSharedSpawnPos();
            double lat = Math.toDegrees(geometry.latitude(spawn.getZ()));
            check("spawn-temperate", lat > 25 && lat < 60, "lat=" + lat + " spawn=" + spawn);
            BlockState below = level.getBlockState(spawn.below());
            check("spawn-on-solid-ground", below.isFaceSturdy(level, spawn.below(), net.minecraft.core.Direction.UP)
                    && below.getFluidState().isEmpty(), below.toString());

            // Planet-scale inputs really reach the generator.
            java.util.function.BiFunction<Integer, Integer, Double> cont = (x, z) ->
                    router.continents().compute(new net.minecraft.world.level.levelgen.DensityFunction.SinglePointContext(x, 64, z));
            java.util.function.BiFunction<Integer, Integer, Double> temp = (x, z) ->
                    router.temperature().compute(new net.minecraft.world.level.levelgen.DensityFunction.SinglePointContext(x, 64, z));
            int pole = geometry.poleDistance();
            int half = geometry.circumference() / 2;
            check("poles-frozen", temp.apply(0, -pole + 300) < -0.45 && temp.apply(5000, pole - 300) < -0.45,
                    "N " + temp.apply(0, -pole + 300) + " S " + temp.apply(5000, pole - 300));
            check("equator-warm", temp.apply(spawn.getX(), 0) > 0.2, "T=" + temp.apply(spawn.getX(), 0));
            double edge = Math.max(cont.apply(half - 20, 0), cont.apply(-half + 20, 3000));
            check("dateline-is-ocean", edge < -0.4, "continentalness " + edge);
            check("pole-is-ocean", cont.apply(0, -pole + 50) < -0.4, "continentalness " + cont.apply(0, -pole + 50));

            // Land fraction over the globe roughly matches the setting (dateline + poles remove some).
            int landCount = 0;
            int samples = 0;
            for (int i = 0; i < 4000; i++) {
                double y = 1 - 2 * (i + 0.5) / 4000;
                double longitude = Math.PI * (3 - Math.sqrt(5)) * i;
                int x = (int) geometry.wrapX(geometry.xForLongitude(longitude));
                int z = (int) geometry.zForLatitude(Math.asin(y));
                samples++;
                if (cont.apply(x, z) > -0.11) {
                    landCount++;
                }
            }
            double fraction = landCount / (double) samples;
            check("land-fraction", fraction > 0.22 && fraction < 0.42, String.format("%.2f", fraction));

            // Generation speed for real chunks near spawn.
            long start = System.nanoTime();
            int count = 0;
            for (int i = 0; i < 8; i++) {
                for (int j = 0; j < 8; j++) {
                    level.getChunk((spawn.getX() >> 4) + 30 + i, (spawn.getZ() >> 4) + j);
                    count++;
                }
            }
            double msPerChunk = (System.nanoTime() - start) / 1e6 / count;
            check("chunk-speed", msPerChunk < 80, String.format("%.1f ms/chunk (full status)", msPerChunk));

            // Sea surface at the date line exists on both sides (seamless wrap).
            int z0 = 1000;
            boolean east = level.getBlockState(new BlockPos(half - 8, 62, z0)).getFluidState().isSource();
            boolean west = level.getBlockState(new BlockPos(-half + 8, 62, z0)).getFluidState().isSource();
            check("dateline-water-both-sides", east && west, "east=" + east + " west=" + west);

            // Entities that wander off an edge come back on the other side.
            var pig = net.minecraft.world.entity.EntityType.PIG.create(level);
            pig.moveTo(half + 10.5, 120, spawn.getZ() + 0.5);
            level.addFreshEntity(pig);
            com.thepearlking.stevespace.world.WorldWrap.wrapEntity(pig, geometry);
            check("wrap-east-edge", Math.abs(pig.getX() - (-half + 10.5)) < 1e-6, "x=" + pig.getX());
            pig.moveTo(100.5, 200, -pole - 5.5);
            com.thepearlking.stevespace.world.WorldWrap.wrapEntity(pig, geometry);
            check("wrap-over-pole", Math.abs(pig.getZ() - (-pole + 5.5)) < 1e-6 && Math.abs(pig.getX() - (100.5 + half - 2 * half)) < 1e-6,
                    "x=" + pig.getX() + " z=" + pig.getZ());
            pig.discard();
        }
    }
}
