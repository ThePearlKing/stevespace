package com.thepearlking.stevespace.vessel;

import com.thepearlking.stevespace.StevesSpace;
import com.thepearlking.stevespace.config.SpaceConfig;
import com.thepearlking.stevespace.universe.SurfaceFrame;
import com.thepearlking.stevespace.world.planet.PlanetGeometry;
import com.thepearlking.stevespace.universe.CelestialBody;
import com.thepearlking.stevespace.universe.DVec3;
import com.thepearlking.stevespace.universe.Kepler;
import com.thepearlking.stevespace.universe.OrbitalElements;
import com.thepearlking.stevespace.universe.ServerUniverse;
import com.thepearlking.stevespace.universe.StateVector;
import net.minecraft.world.level.levelgen.Heightmap;
import org.joml.Quaterniond;
import net.minecraft.core.Direction;

/**
 * Flies the vessel for the pilot. Each tick it decides where the engines should point and how
 * hard they should push; the vessel turns toward that at its normal turn rate.
 *
 * <p>Programs:
 * <ul>
 *   <li>{@link Program#ORBIT}: gravity turn to the east, coast to apoapsis, circularise.</li>
 *   <li>{@link Program#CIRCULARIZE}: raise periapsis to apoapsis.</li>
 *   <li>{@link Program#LAND}: de-orbit, point retrograde through entry, landing burn, touch down.</li>
 *   <li>{@link Program#MOON}: plan a transfer, wait for it, burn, cruise, capture into orbit.</li>
 *   <li>{@link Program#RETURN}: from a moon's orbit back down into its planet's air, then land.</li>
 * </ul>
 * Long waits and cruises are sped up with time warp, dropping back to normal before each burn.
 * Directions are in the vessel's world axes: east/up/south over a surface, the cell's axes in space.
 */
public final class Autopilot {

    public enum Program { OFF, ORBIT, CIRCULARIZE, LAND, MOON, RETURN, APPROACH, UNDOCK, TRAVEL, NODE, DOCK }

    /**
     * What the autopilot wants this tick: thrust direction (unit, world axes) and throttle; and
     * optionally a push from the RCS thrusters ({@code translate}, world axes, blocks/s²) and a whole
     * attitude to hold ({@code attitude}, vessel to world axes) instead of just pointing the engines.
     */
    public record Command(DVec3 direction, double throttle, DVec3 translate, org.joml.Quaterniond attitude) {

        public Command(DVec3 direction, double throttle) {
            this(direction, throttle, null, null);
        }
    }

    private enum Phase { CLIMB, COAST, CIRCULARIZE, DEORBIT, ENTRY, LANDING_BURN, TOUCHDOWN,
        PLAN, WAIT_BURN, TRANSFER_BURN, CRUISE, CORRECTION, CAPTURE, APPROACH, NO_TARGET, NO_TRANSFER, DONE,
        ROUTE, ESCAPE, LEAVE, INTER_PLAN, INTER_WAIT, HELIO, ARRIVE, SETTLE, NODE_WAIT, TOO_WEAK, SUN_DIVE, SUN_FALL, HOP,
        DOCK_PLAN, DOCK_WAIT, DOCK_COAST, DOCK_CLOSE, DOCK_ALIGN, DOCK_IN }

    /** Seconds after the transfer burn before checking the aim (far out, aim is easier to fix). */
    private static final double CORRECTION_DELAY = 600;
    private static final int MAX_CORRECTIONS = 4;

    /** Arrival height above a moon, and the height to aim for in a planet's air coming home. */
    private static final double MOON_ARRIVAL = 1500;
    private static final double HOME_ARRIVAL = 300;
    /** A close orbit of the Sun: this many solar radii from its centre. */
    static final double SUN_ORBIT = 2.5;

    private Program program = Program.OFF;
    private Phase phase = Phase.DONE;
    private double targetAltitude;
    // Transfer state (saved): when to burn, the speed to burn up to, where to.
    private double burnTime;
    private double burnSpeed;
    private double burnDeltaV;
    private String destination = "";
    private java.util.concurrent.CompletableFuture<TransferPlanner.Plan> planning;
    private java.util.concurrent.CompletableFuture<DVec3> correcting;
    private double burnEnd;
    private int corrections;
    /** The correction being flown: its direction (ecliptic frame) and the Δv still to give. */
    private DVec3 correctionDirection = DVec3.ZERO;
    private double correctionLeft;
    private int cruiseCheck;
    private double eventTime = Double.NaN;
    /** Where to set down, if picked on the map (radians, NaN for anywhere), and when to start down for it. */
    private double siteLat = Double.NaN;
    private double siteLon = Double.NaN;
    private double deorbitAt = Double.NaN;
    /** Mid-burn: turning the orbit, or lifting its low point out of the air. */
    private boolean siteBurning;
    private boolean siteLifting;
    private double siteRound;
    /** Why the landing pass was last planned again (logs). */
    private String siteWhy = "";
    /** How far off the picked spot the best pass goes (blocks), and the orbit's tilt wanted to fix that (its normal). */
    private double siteMiss = Double.NaN;
    private boolean loggedArrival;
    /** Where the planned fall was worked out to come down (map x, z), for checking against where it really does. */
    private double predictedX = Double.NaN;
    private double predictedZ = Double.NaN;
    private DVec3 siteNormal;
    private int siteTurns;
    /** When the planned turn is to be made (it's cheapest there). */
    private double siteTurnAt = Double.NaN;
    // Flying anywhere (saved): the final stop, whether to land there, and the current leg.
    private String finalDestination = "";
    private boolean landAtEnd;
    private boolean finished;
    /** This leg goes out to a moon (true) or home to the planet below (false). */
    private boolean outward = true;
    private String leaving = "";
    private Phase afterBurn = Phase.CRUISE;
    private double arriveTime;
    private double helioStart;
    private int helioCorrections;
    private java.util.concurrent.CompletableFuture<InterplanetaryPlanner.Burn[]> interPlanning;
    /** Excess velocity the departure burn is aiming for (ecliptic frame); zero for other burns. */
    private DVec3 departureExcess = DVec3.ZERO;
    private int steerCheck;
    // Docking (saved): the other ship (its id, or its cell while parked), which port of ours meets
    // which of theirs (blocks from each ship's origin), and the meeting being flown.
    private java.util.UUID dockId;
    private int dockCell = -1;
    private net.minecraft.core.BlockPos myPort = net.minecraft.core.BlockPos.ZERO;
    private net.minecraft.core.BlockPos theirPort = net.minecraft.core.BlockPos.ZERO;
    private double dockDepart;
    private double dockArrive;
    private int dockCorrections;
    /** Out in front of their port, on its axis (from here it's straight in). */
    private boolean dockOnAxis;
    private java.util.concurrent.CompletableFuture<RendezvousPlanner.Plan> dockPlanning;
    /** The ports picked can't meet without tipping a ship over. */
    private boolean dockTip;
    /** How the last docking step stood (logs). */
    private String dockDebug = "";
    /** Their port's face last tick (from their centre), and how fast it swings round (a station turning carries its port round). */
    /** The spot we're making for, against the other ship's centre, a tick ago (for how fast it moves). */
    private DVec3 lastArm;
    private double lastArmTime = Double.NaN;
    private DVec3 armVelocity;

    public Program program() {
        return program;
    }

    public boolean active() {
        return program != Program.OFF && phase != Phase.DONE;
    }

    /**
     * For the HUD: where the autopilot is taking you and, between worlds, when you get there
     * (or when it leaves). Empty for programs that stay put.
     */
    public String detail(VesselEntity vessel) {
        if (program == Program.NODE && vessel.orbital() != null) {
            double t = vessel.orbital().time();
            return phase == Phase.NODE_WAIT ? "T\u2212" + eta(Math.max(0, burnTime - t)) : String.format(java.util.Locale.ROOT, "%.0f m/s", Math.max(0, correctionLeft));
        }
        if (program != Program.TRAVEL && program != Program.MOON) {
            return "";
        }
        var system = ServerUniverse.system();
        CelestialBody end = system.body(travelling() ? finalDestination : destination);
        if (end == null) {
            return "";
        }
        String text = "\u2192 " + end.displayName();
        if (vessel.orbital() != null) {
            double t = vessel.orbital().time();
            if (phase == Phase.HELIO && arriveTime > t) {
                text += " \u00b7 " + eta(arriveTime - t);
            } else if (phase == Phase.INTER_WAIT && burnTime > t) {
                text += " \u00b7 T\u2212" + eta(burnTime - t);
            }
        }
        return text;
    }

    private static String eta(double seconds) {
        long s = (long) seconds;
        if (s >= 86400) {
            return String.format("%dd %02dh", s / 86400, (s % 86400) / 3600);
        }
        if (s >= 3600) {
            return String.format("%dh %02dm", s / 3600, (s % 3600) / 60);
        }
        return String.format("%dm", Math.max(1, s / 60));
    }

    /** One line for logs: where the autopilot is going and how it is doing. */
    public String debug(VesselEntity vessel) {
        StringBuilder b = new StringBuilder(program + "/" + phase + " to " + destination + " (final " + finalDestination + ")");
        if (phase == Phase.LANDING_BURN || phase == Phase.TOUCHDOWN) {
            b.append(" ").append(lastDescent);
        }
        if (phase == Phase.DEORBIT && hasSite() && vessel.orbital() != null) {
            var orbit = vessel.orbital().elements(SpaceConfig.GRAVITY_SCALE.get());
            CelestialBody body = vessel.orbital().body();
            b.append(String.format(java.util.Locale.ROOT, " pe %.0f ap %.0f down in %.0f miss %.0f turns %d warp %.0f %s",
                    orbit.periapsis() - body.radius(), orbit.apoapsis() - body.radius(), deorbitAt - vessel.orbital().time(),
                    siteMiss, siteTurns, com.thepearlking.stevespace.universe.UniverseClock.get(vessel.level().getServer()).warp(), siteWhy));
        }
        if (program == Program.DOCK) {
            b.append(' ').append(dockDebug);
        }
        if (phase == Phase.HELIO && vessel.orbital() != null) {
            var system = ServerUniverse.system();
            CelestialBody target = system.body(destination);
            double t = vessel.orbital().time();
            StateVector rel = vessel.orbital().state().sub(system.stateRelativeToParent(target, t));
            b.append(String.format(" dist %.0f rel %.1f arrive in %.0f corrections %d", rel.position().length(),
                    rel.velocity().length(), arriveTime - t, helioCorrections));
        }
        return b.toString();
    }

    /** A short status word for the HUD (translation key). */
    public String statusKey() {
        if (program == Program.NODE && phase == Phase.CORRECTION) {
            return "hud.stevespace.autopilot.node_burn";
        }
        return "hud.stevespace.autopilot." + phase.name().toLowerCase(java.util.Locale.ROOT);
    }

    public void start(Program program, VesselEntity vessel) {
        CelestialBody here = bodyOf(vessel);
        // "Fly to the Moon" and "Return home" are trips like any other: the route planner flies
        // them from whatever orbit (or the ground) the ship is in.
        if (program == Program.MOON && here != null) {
            var moons = ServerUniverse.system().childrenOf(here);
            if (!moons.isEmpty()) {
                startTravel(vessel, moons.get(0).id(), false);
                return;
            }
        } else if (program == Program.RETURN && here != null && here.parent() != null
                && !here.parent().id().equals(ServerUniverse.system().sun().id())) {
            startTravel(vessel, here.parent().id(), true);
            return;
        }
        this.program = program;
        CelestialBody body = here;
        double edge = body == null ? 2000 : VesselTransfer.spaceAltitude(body);
        this.targetAltitude = edge + 600;
        this.planning = null;
        this.eventTime = Double.NaN;
        this.finished = false;
        this.afterBurn = Phase.CRUISE;
        this.outward = program != Program.RETURN;
        this.phase = switch (program) {
            // Already up in space: no climbing, just make an orbit from wherever it's heading.
            case ORBIT -> vessel.orbital() != null ? Phase.COAST : Phase.CLIMB;
            case CIRCULARIZE -> Phase.CIRCULARIZE;
            // Somewhere to stand, or no landing at all (a gas giant has no ground to come down on).
            case LAND -> body != null && body.surfaceDimension() == null ? Phase.NO_TRANSFER
                    : vessel.orbital() != null ? Phase.DEORBIT : Phase.ENTRY;
            case MOON -> vessel.orbital() != null ? Phase.INTER_PLAN : Phase.NO_TRANSFER;
            case RETURN -> vessel.orbital() != null ? Phase.PLAN : Phase.NO_TRANSFER;
            case APPROACH -> vessel.orbital() != null ? Phase.APPROACH : Phase.NO_TARGET;
            case TRAVEL -> vessel.orbital() != null ? Phase.ROUTE : Phase.CLIMB;
            case NODE -> vessel.orbital() != null ? Phase.NODE_WAIT : Phase.NO_TRANSFER;
            case DOCK -> vessel.orbital() != null ? Phase.ROUTE : Phase.CLIMB;
            case OFF, UNDOCK -> Phase.DONE;
        };
        if (program == Program.MOON && body != null) {
            var moons = ServerUniverse.system().childrenOf(body);
            destination = moons.isEmpty() ? "" : moons.get(0).id();
        } else if (program == Program.RETURN && body != null && body.parent() != null) {
            destination = body.parent().id();
        }
        if ((program == Program.MOON || program == Program.RETURN) && destination.isEmpty()) {
            phase = Phase.NO_TRANSFER;
        }
    }

    /**
     * Flies a maneuver node the pilot planned on the map: at {@code time}, a change of speed of
     * {@code prograde}, {@code normal} and {@code radial} (along the orbit at that moment). Turns
     * to face it, warps to it, and burns half before and half after.
     */
    public void startNode(VesselEntity vessel, double time, double prograde, double normal, double radial) {
        start(Program.NODE, vessel);
        if (phase != Phase.NODE_WAIT) {
            return;
        }
        CelestialBody body = bodyOf(vessel);
        double mu = body.mu() * SpaceConfig.GRAVITY_SCALE.get();
        OrbitalFlight o = vessel.orbital();
        StateVector at = Kepler.elementsFromState(o.state(), mu, o.time()).stateAt(time, mu);
        DVec3 pro = at.velocity().normalize();
        DVec3 nor = at.position().cross(at.velocity()).normalize();
        DVec3 rad = nor.cross(pro).normalize();
        DVec3 dv = pro.scale(prograde).add(nor.scale(normal)).add(rad.scale(radial));
        if (dv.length() < 0.01) {
            phase = Phase.DONE;
            return;
        }
        burnTime = time;
        burnDeltaV = dv.length();
        correctionDirection = dv.normalize();
        correctionLeft = dv.length();
        afterBurn = Phase.DONE;
    }

    /** Waiting for a maneuver node: facing the burn, warping toward it. */
    private Command nodeWait(VesselEntity vessel) {
        Trajectory now = Trajectory.of(vessel, bodyOf(vessel), bodyOf(vessel).mu() * SpaceConfig.GRAVITY_SCALE.get());
        double duration = burnDeltaV / Math.max(0.01, vessel.maxAcceleration());
        double until = burnTime - duration / 2 - vessel.orbital().time();
        DVec3 direction = now.toWorld().apply(correctionDirection);
        // Give it time to turn round before the burn (warp drops back to normal 30 s before).
        if (until > 40 && vessel.alignment(direction) > 0.99) {
            warpToward(vessel, until - 30);
        } else {
            warp(vessel, 1);
        }
        if (until <= 0) {
            steerCheck = Integer.MAX_VALUE; // a node is flown as planned, not re-solved
            phase = Phase.CORRECTION;
        }
        return new Command(direction, 0);
    }

    /** Flies to any world: {@code target} (a body id), then stays in orbit or lands. */
    public void startTravel(VesselEntity vessel, String target, boolean land) {
        startTravel(vessel, target, land, Double.NaN, Double.NaN);
    }

    /** The same, landing at one spot (latitude, longitude in radians) picked on the map. */
    public void startTravel(VesselEntity vessel, String target, boolean land, double latitude, double longitude) {
        this.finalDestination = target;
        this.landAtEnd = land;
        this.siteLat = land ? latitude : Double.NaN;
        this.siteLon = land ? longitude : Double.NaN;
        this.deorbitAt = Double.NaN;
        this.siteMiss = Double.NaN;
        this.siteNormal = null;
        this.siteTurns = 0;
        start(Program.TRAVEL, vessel);
    }

    private boolean hasSite() {
        return !Double.isNaN(siteLat) && !Double.isNaN(siteLon);
    }

    public net.minecraft.nbt.CompoundTag save() {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("program", program.name());
        tag.putString("phase", phase.name());
        tag.putDouble("target", targetAltitude);
        tag.putDouble("burnTime", burnTime);
        tag.putDouble("burnSpeed", burnSpeed);
        tag.putDouble("burnDeltaV", burnDeltaV);
        tag.putString("destination", destination);
        tag.putDouble("burnEnd", burnEnd);
        tag.putInt("corrections", corrections);
        tag.putDouble("cdx", correctionDirection.x());
        tag.putDouble("cdy", correctionDirection.y());
        tag.putDouble("cdz", correctionDirection.z());
        tag.putDouble("correctionLeft", correctionLeft);
        tag.putString("final", finalDestination);
        tag.putBoolean("land", landAtEnd);
        tag.putDouble("siteLat", siteLat);
        tag.putDouble("siteLon", siteLon);
        tag.putDouble("deorbitAt", deorbitAt);
        tag.putDouble("siteMiss", siteMiss);
        tag.putBoolean("finished", finished);
        tag.putBoolean("outward", outward);
        tag.putString("leaving", leaving);
        tag.putString("afterBurn", afterBurn.name());
        tag.putDouble("arriveTime", arriveTime);
        tag.putDouble("helioStart", helioStart);
        tag.putInt("helioCorrections", helioCorrections);
        tag.putDouble("burnTimeSaved", burnTime);
        tag.putDouble("vix", departureExcess.x());
        tag.putDouble("viy", departureExcess.y());
        tag.putDouble("viz", departureExcess.z());
        if (dockId != null) {
            tag.putUUID("dockId", dockId);
        }
        tag.putInt("dockCell", dockCell);
        tag.putLong("myPort", myPort.asLong());
        tag.putLong("theirPort", theirPort.asLong());
        tag.putDouble("dockDepart", dockDepart);
        tag.putDouble("dockArrive", dockArrive);
        tag.putInt("dockCorrections", dockCorrections);
        tag.putBoolean("dockOnAxis", dockOnAxis);
        return tag;
    }

    public void load(net.minecraft.nbt.CompoundTag tag) {
        try {
            program = Program.valueOf(tag.getString("program"));
            phase = Phase.valueOf(tag.getString("phase"));
            targetAltitude = tag.getDouble("target");
            burnTime = tag.getDouble("burnTime");
            burnSpeed = tag.getDouble("burnSpeed");
            burnDeltaV = tag.getDouble("burnDeltaV");
            destination = tag.getString("destination");
            burnEnd = tag.getDouble("burnEnd");
            corrections = tag.getInt("corrections");
            correctionDirection = new DVec3(tag.getDouble("cdx"), tag.getDouble("cdy"), tag.getDouble("cdz"));
            correctionLeft = tag.getDouble("correctionLeft");
            finalDestination = tag.getString("final");
            landAtEnd = tag.getBoolean("land");
            siteLat = tag.contains("siteLat") ? tag.getDouble("siteLat") : Double.NaN;
            siteLon = tag.contains("siteLon") ? tag.getDouble("siteLon") : Double.NaN;
            deorbitAt = tag.contains("deorbitAt") ? tag.getDouble("deorbitAt") : Double.NaN;
            siteMiss = tag.contains("siteMiss") ? tag.getDouble("siteMiss") : Double.NaN;
            siteNormal = null; // worked out again if still needed
            finished = tag.getBoolean("finished");
            outward = !tag.contains("outward") || tag.getBoolean("outward");
            leaving = tag.getString("leaving");
            afterBurn = tag.contains("afterBurn") ? Phase.valueOf(tag.getString("afterBurn")) : Phase.CRUISE;
            arriveTime = tag.getDouble("arriveTime");
            helioStart = tag.getDouble("helioStart");
            helioCorrections = tag.getInt("helioCorrections");
            departureExcess = new DVec3(tag.getDouble("vix"), tag.getDouble("viy"), tag.getDouble("viz"));
            dockId = tag.hasUUID("dockId") ? tag.getUUID("dockId") : null;
            dockCell = tag.contains("dockCell") ? tag.getInt("dockCell") : -1;
            myPort = net.minecraft.core.BlockPos.of(tag.getLong("myPort"));
            theirPort = net.minecraft.core.BlockPos.of(tag.getLong("theirPort"));
            dockDepart = tag.getDouble("dockDepart");
            dockArrive = tag.getDouble("dockArrive");
            dockCorrections = tag.getInt("dockCorrections");
            dockOnAxis = tag.getBoolean("dockOnAxis");
            if (phase == Phase.DOCK_PLAN) {
                dockPlanning = null;
            }
            if (phase == Phase.INTER_PLAN || phase == Phase.PLAN) {
                // A plan being worked out when saved: work it out again.
                interPlanning = null;
                planning = null;
            }
        } catch (IllegalArgumentException e) {
            stop();
        }
    }

    public void stop() {
        program = Program.OFF;
        phase = Phase.DONE;
    }

    /** Programs that fly the route to another world (docking flies to the other ship's first). */
    private boolean travelling() {
        return program == Program.TRAVEL || program == Program.DOCK;
    }

    private static CelestialBody bodyOf(VesselEntity vessel) {
        return vessel.flightBody();
    }

    /** Decides this tick's command, or returns null when the program has finished. */
    public Command update(VesselEntity vessel) {
        if (!active()) {
            return null;
        }
        CelestialBody body = bodyOf(vessel);
        if (body == null) {
            stop();
            return null;
        }
        double gravityScale = SpaceConfig.GRAVITY_SCALE.get();
        double mu = body.mu() * gravityScale;
        if (vessel.orbital() == null && needsOrbit(phase)) {
            // Dropped into the air (or never left it): these steps only make sense in orbit.
            if (travelling()) {
                phase = Phase.ROUTE;
            } else {
                phase = Phase.NO_TRANSFER;
            }
        }
        Command command = switch (phase) {
            case CLIMB -> climb(vessel, body, mu);
            case COAST -> coast(vessel, body, mu);
            case CIRCULARIZE -> circularize(vessel, body, mu);
            case DEORBIT -> deorbit(vessel, body, mu);
            case ENTRY, LANDING_BURN, TOUCHDOWN -> descend(vessel, body, mu);
            case PLAN -> plan(vessel, body, mu);
            case WAIT_BURN -> waitForBurn(vessel, body, mu);
            case TRANSFER_BURN -> transferBurn(vessel, body, mu);
            case CRUISE -> cruise(vessel, body, mu);
            case CORRECTION -> correct(vessel, body, mu);
            case CAPTURE -> capture(vessel, body, mu);
            case APPROACH -> approach(vessel, body, mu);
            case ROUTE -> route(vessel, body, mu);
            case ESCAPE -> escape(vessel, body, mu);
            case LEAVE -> leave(vessel, body, mu);
            case INTER_PLAN -> interPlan(vessel, body, mu);
            case INTER_WAIT -> interWait(vessel, body, mu);
            case HELIO -> helio(vessel, body, mu);
            case ARRIVE -> arrive(vessel, body, mu);
            case SETTLE -> settle(vessel, body, mu);
            case NODE_WAIT -> nodeWait(vessel);
            case SUN_DIVE -> sunDive(vessel, body, mu);
            case SUN_FALL -> sunFall(vessel, body, mu);
            case HOP -> hop(vessel, body, mu);
            case DOCK_PLAN -> dockPlan(vessel, body, mu);
            case DOCK_WAIT -> dockWait(vessel, body, mu);
            case DOCK_COAST -> dockCoast(vessel, body, mu);
            case DOCK_CLOSE -> dockClose(vessel, body, mu);
            case DOCK_ALIGN -> dockFinal(vessel, body, mu, false);
            case DOCK_IN -> dockFinal(vessel, body, mu, true);
            case NO_TARGET, NO_TRANSFER, TOO_WEAK, DONE -> null;
        };
        // Flying anywhere: each step that finishes hands back to the route for the next one.
        if (travelling() && phase == Phase.DONE && !finished) {
            phase = Phase.ROUTE;
        }
        // Docking: once in orbit round the right body, go and meet the other ship.
        if (program == Program.DOCK && finished && phase == Phase.DONE) {
            finished = false;
            phase = Phase.DOCK_PLAN;
        }
        if (phase == Phase.DONE || phase == Phase.NO_TRANSFER || phase == Phase.NO_TARGET) {
            warp(vessel, 1);
        }
        if (phase == Phase.DONE) {
            program = Program.OFF;
        }
        return command;
    }

    private static boolean needsOrbit(Phase phase) {
        return switch (phase) {
            case ESCAPE, LEAVE, INTER_PLAN, INTER_WAIT, HELIO, ARRIVE, SETTLE, PLAN, WAIT_BURN, CRUISE, APPROACH, NODE_WAIT, SUN_DIVE, SUN_FALL,
                    DOCK_PLAN, DOCK_WAIT, DOCK_COAST, DOCK_CLOSE, DOCK_ALIGN, DOCK_IN -> true;
            default -> false;
        };
    }

    /** True when the program can't run (shown to the pilot, then the autopilot lets go). */
    public boolean failed() {
        return phase == Phase.NO_TRANSFER || phase == Phase.NO_TARGET || phase == Phase.TOO_WEAK;
    }

    /** The message for a program that couldn't run. */
    public String failureKey() {
        if (program == Program.DOCK && phase == Phase.NO_TARGET) {
            return "stevespace.autopilot.dock_lost";
        }
        if (program == Program.DOCK && dockTip) {
            return "stevespace.autopilot.dock_tip";
        }
        return phase == Phase.NO_TARGET ? "stevespace.autopilot.no_target"
                : phase == Phase.TOO_WEAK ? "stevespace.autopilot.too_weak" : "stevespace.autopilot.no_transfer";
    }

    // ---------------------------------------------------------------------------------------
    // Approaching a target in the same orbit area
    // ---------------------------------------------------------------------------------------

    /** Farthest a target can be for the approach program (blocks). */
    private static final double APPROACH_RANGE = 20_000;
    /** Where it stops: this far from the target, nearly still. */
    private static final double STATION_DISTANCE = 15;

    private Command approach(VesselEntity vessel, CelestialBody body, double mu) {
        Trajectory now = Trajectory.of(vessel, body, mu);
        VesselEntity target = Targets.targetVessel(vessel.pilot());
        if (target == null || target.orbital() == null || vessel.orbital() == null
                || !target.orbital().body().id().equals(body.id())) {
            phase = Phase.NO_TARGET;
            return new Command(now.prograde(), 0);
        }
        warp(vessel, 1);
        StateVector own = vessel.orbital().state();
        StateVector other = target.orbital().state();
        DVec3 toTarget = other.position().add(other.velocity().scale(vessel.orbital().time() - target.orbital().time()))
                .sub(own.position());
        DVec3 relative = own.velocity().sub(other.velocity()); // our motion as seen from the target
        double distance = toTarget.length();
        if (distance > APPROACH_RANGE) {
            phase = Phase.NO_TARGET;
            return new Command(now.prograde(), 0);
        }
        if (distance < STATION_DISTANCE + 5 && relative.length() < 0.3) {
            phase = Phase.DONE; // close and still: the last metres are the pilot's
            return new Command(now.prograde(), 0);
        }
        // Close in quickly from afar, slowly near; stop STATION_DISTANCE away.
        double speed = Math.max(0, Math.min(25, (distance - STATION_DISTANCE) / 25));
        DVec3 wanted = toTarget.normalize().scale(speed);
        DVec3 error = wanted.sub(relative);
        if (error.length() < 0.2) {
            return new Command(now.toWorld().apply(wanted.length() > 0 ? wanted.normalize() : now.prograde()), 0);
        }
        double throttle = Math.max(0.03, Math.min(1, error.length() / Math.max(0.01, vessel.maxAcceleration()) * 2));
        return new Command(now.toWorld().apply(error.normalize()), throttle);
    }

    // ---------------------------------------------------------------------------------------
    // Docking with another ship (or a station), from wherever this one is
    // ---------------------------------------------------------------------------------------

    /** Face to face distance (blocks) to line up at before closing in. */
    private static final double DOCK_HOLD = 8;
    /** Closing speed for the last metres (m/s). */
    private static final double DOCK_SPEED = 0.3;

    /**
     * Flies to the ship {@code id} (or the one parked in {@code cell}) and docks port {@code mine}
     * of this ship to its port {@code theirs} (blocks from each ship's origin). Gets to orbit, or
     * to the other ship's world, first if it has to.
     */
    public void startDock(VesselEntity vessel, java.util.UUID id, int cell, net.minecraft.core.BlockPos mine, net.minecraft.core.BlockPos theirs) {
        dockId = id;
        dockCell = cell;
        myPort = mine;
        theirPort = theirs;
        dockCorrections = 0;
        dockPlanning = null;
        dockOnAxis = false;
        DockTarget target = DockTarget.find(vessel, id, cell);
        if (target == null) {
            program = Program.DOCK;
            phase = Phase.NO_TARGET;
            return;
        }
        // Ports that would only meet with one ship tipped over against the other: not done.
        Direction[] facings = DockTarget.portFacings(vessel, target, mine, theirs);
        if (facings != null && !DockGeometry.canMeet(facings[0], facings[1])) {
            program = Program.DOCK;
            phase = Phase.TOO_WEAK;
            dockTip = true;
            return;
        }
        dockTip = false;
        finalDestination = target.body().id();
        landAtEnd = false;
        siteLat = Double.NaN;
        siteLon = Double.NaN;
        start(Program.DOCK, vessel);
    }

    /** Works out when and how to set off to meet the other ship (or goes straight to it when close). */
    private Command dockPlan(VesselEntity vessel, CelestialBody body, double mu) {
        Trajectory now = Trajectory.of(vessel, body, mu);
        DockTarget target = DockTarget.find(vessel, dockId, dockCell);
        if (target == null) {
            phase = Phase.NO_TARGET;
            return new Command(now.prograde(), 0);
        }
        if (!target.body().id().equals(body.id())) {
            finalDestination = target.body().id();
            phase = Phase.ROUTE; // it's round another world: go there first
            return new Command(now.prograde(), 0);
        }
        warp(vessel, 1);
        OrbitalFlight o = vessel.orbital();
        StateVector own = o.state();
        StateVector other = target.state(o.time());
        double distance = other.position().sub(own.position()).length();
        double closing = other.velocity().sub(own.velocity()).length();
        if (distance < 2500 && closing < 40) {
            dockOnAxis = false;
            phase = Phase.DOCK_CLOSE;
            return new Command(now.prograde(), 0);
        }
        if (dockPlanning == null) {
            double t = o.time();
            double floor = body.radius() + VesselTransfer.spaceAltitude(body) + 100;
            dockPlanning = java.util.concurrent.CompletableFuture.supplyAsync(() -> RendezvousPlanner.plan(own, t, other, t, mu, floor));
        } else if (dockPlanning.isDone()) {
            RendezvousPlanner.Plan plan = dockPlanning.getNow(null);
            dockPlanning = null;
            if (plan == null) {
                phase = Phase.NO_TRANSFER;
                return new Command(now.prograde(), 0);
            }
            StevesSpace.LOGGER.info("Docking: meet {} in {} s ({} m/s now, {} m/s on arrival)", target.name(),
                    String.format("%.0f", plan.arriveTime() - o.time()), String.format("%.1f", plan.deltaV().length()),
                    String.format("%.1f", plan.arrivalSpeed()));
            burnTime = plan.departTime();
            burnDeltaV = plan.deltaV().length();
            correctionDirection = plan.deltaV().normalize();
            correctionLeft = plan.deltaV().length();
            afterBurn = Phase.DOCK_COAST;
            dockDepart = plan.departTime();
            dockArrive = plan.arriveTime();
            dockCorrections = 0;
            phase = Phase.DOCK_WAIT;
        }
        return new Command(now.prograde(), 0);
    }

    /** Waiting for the moment to set off: facing the burn, warping toward it. */
    private Command dockWait(VesselEntity vessel, CelestialBody body, double mu) {
        Trajectory now = Trajectory.of(vessel, body, mu);
        double duration = burnDeltaV / Math.max(0.01, vessel.maxAcceleration());
        double until = burnTime - duration / 2 - vessel.orbital().time();
        DVec3 direction = now.toWorld().apply(correctionDirection);
        if (until > 40 && vessel.alignment(direction) > 0.99) {
            warpToward(vessel, until - 30);
        } else {
            warp(vessel, 1);
        }
        if (until <= 0) {
            steerCheck = Integer.MAX_VALUE;
            phase = Phase.CORRECTION;
        }
        return new Command(direction, 0);
    }

    /** On the way: a correction or two, warping between, until the other ship is near. */
    private Command dockCoast(VesselEntity vessel, CelestialBody body, double mu) {
        Trajectory now = Trajectory.of(vessel, body, mu);
        DockTarget target = DockTarget.find(vessel, dockId, dockCell);
        if (target == null) {
            phase = Phase.NO_TARGET;
            return new Command(now.prograde(), 0);
        }
        OrbitalFlight o = vessel.orbital();
        double t = o.time();
        double toGo = dockArrive - t;
        double distance = target.state(t).position().sub(o.state().position()).length();
        if (distance < 2000 || toGo < 20) {
            warp(vessel, 1);
            dockOnAxis = false;
            phase = toGo < -120 && distance > 4000 ? Phase.DOCK_PLAN : Phase.DOCK_CLOSE; // missed it: plan again
            return new Command(now.retrograde(), 0);
        }
        double[] checks = {(dockDepart + dockArrive) / 2, dockArrive - Math.min(240, (dockArrive - dockDepart) / 4)};
        if (dockCorrections < checks.length && t >= checks[dockCorrections]) {
            dockCorrections++;
            DVec3 aim = target.state(dockArrive).position();
            DVec3 dv = RendezvousPlanner.correction(o.state(), t, aim, dockArrive, mu);
            if (dv != null && dv.length() > 0.3 && dv.length() < 400) {
                correctionDirection = dv.normalize();
                correctionLeft = dv.length();
                afterBurn = Phase.DOCK_COAST;
                steerCheck = Integer.MAX_VALUE;
                phase = Phase.CORRECTION;
                return new Command(now.toWorld().apply(correctionDirection), 0);
            }
        }
        double next = dockCorrections < checks.length ? checks[dockCorrections] - t : toGo - 20;
        warpToward(vessel, Math.min(next, toGo - 20) - 10);
        return new Command(now.prograde(), 0);
    }

    /**
     * Near the other ship: match its speed and come round in front of its port (on its axis, a
     * few blocks out), then hold there. With RCS thrusters the last stretch is flown with those,
     * already turned to dock.
     */
    private Command dockClose(VesselEntity vessel, CelestialBody body, double mu) {
        Trajectory now = Trajectory.of(vessel, body, mu);
        warp(vessel, 1);
        DockTarget target = DockTarget.find(vessel, dockId, dockCell);
        if (target == null) {
            phase = Phase.NO_TARGET;
            return new Command(now.prograde(), 0);
        }
        OrbitalFlight o = vessel.orbital();
        StateVector own = o.state();
        StateVector other = target.state(o.time());
        DVec3 rel = own.velocity().sub(other.velocity());
        DVec3 toOther = other.position().sub(own.position());
        if (toOther.length() > 6000) {
            phase = Phase.DOCK_PLAN; // drifted far off: plan it again
            return new Command(now.prograde(), 0);
        }
        Ports ports = ports(vessel, target);
        DVec3 goal;
        Quaterniond attitude = null;

        if (ports == null) {
            // Still parked (it wakes up as we come near): close in on it, keeping a little off.
            goal = other.position().add(toOther.length() > 1 ? toOther.normalize().scale(-150) : DVec3.Y.scale(150));
        } else {
            attitude = ports.attitude();
            double clearance = target.vessel().stats().radius() + vessel.stats().radius() + 4;
            DVec3 hold = ports.comFor(DOCK_HOLD);
            DVec3 corridor = ports.comFor(DOCK_HOLD + clearance);
            DVec3 offAxis = own.position().sub(ports.face());
            double out = offAxis.dot(ports.direction());
            double side = offAxis.sub(ports.direction().scale(out)).length();
            if (!dockOnAxis && out > DOCK_HOLD && side < 3) {
                dockOnAxis = true;
            }
            goal = dockOnAxis ? hold : DockGeometry.around(own.position(), corridor, other.position(), clearance);
            // Motion against the spot we're making for, which moves round with the station as it turns.
            rel = own.velocity().sub(spotVelocity(goal, o.time(), other));
            if (dockOnAxis && own.position().sub(hold).length() < 1.5 && rel.length() < 0.15) {
                phase = Phase.DOCK_ALIGN;
                return new Command(now.prograde(), 0);
            }
        }
        DVec3 toGoal = goal.sub(own.position());
        double distance = toGoal.length();
        dockDebug = String.format(java.util.Locale.ROOT, "to goal %.1f rel %.2f on axis %b ports %b spin %.3f", distance, rel.length(),
                dockOnAxis, ports != null, armVelocity == null ? 0 : armVelocity.length());
        // Quick from afar, gently near (and never faster than it can stop).
        double stop = Math.sqrt(2 * Math.max(0.05, vessel.maxAcceleration() * 0.5) * distance);
        double speed = Math.min(Math.min(20, distance / 15), stop);
        DVec3 wanted = distance > 0.01 ? toGoal.scale(speed / distance) : DVec3.ZERO;
        DVec3 error = wanted.sub(rel);
        double rcs = vessel.dockAcceleration();
        // Close in on the small thrusters (or, without any, the weak vents: only once very near,
        // as the main engine is far too strong for those last few metres).
        boolean small = vessel.rcsAcceleration() > 0 ? error.length() < rcs * 6 : distance < 40 && error.length() < 3;
        if (toOther.length() < 120 && small) {
            // Close in: the RCS thrusters push, the ship turns to its docking attitude meanwhile.
            DVec3 push = clampLength(error.scale(1.5), rcs * 0.9);
            return new Command(now.prograde(), 0, now.toWorld().apply(push),
                    attitude == null ? null : vessel.worldOrientation(attitude));
        }
        double deadband = 0.08;
        if (error.length() < deadband) {
            return attitude != null
                    ? new Command(now.prograde(), 0, null, vessel.worldOrientation(attitude))
                    : new Command(now.toWorld().apply(error.length() > 1e-6 ? error.normalize() : now.prograde()), 0);
        }
        double throttle = Math.max(0.02, Math.min(1, error.length() / Math.max(0.01, vessel.maxAcceleration()) * 2));
        return new Command(now.toWorld().apply(error.normalize()), throttle);
    }

    /**
     * Lined up in front of the port: turn square to it and hold there ({@code in} false), then
     * close in slowly until the ports meet and join ({@code in} true).
     */
    private Command dockFinal(VesselEntity vessel, CelestialBody body, double mu, boolean in) {
        Trajectory now = Trajectory.of(vessel, body, mu);
        warp(vessel, 1);
        DockTarget target = DockTarget.find(vessel, dockId, dockCell);
        if (target == null || target.vessel() == null) {
            // It joined with us (we were the bigger ship and took it in), or it's gone.
            phase = in && target == null ? Phase.DONE : Phase.NO_TARGET;
            return new Command(now.prograde(), 0);
        }
        Ports ports = ports(vessel, target);
        if (ports == null) {
            phase = Phase.DONE; // our port is joined to theirs now
            return new Command(now.prograde(), 0);
        }
        OrbitalFlight o = vessel.orbital();
        StateVector own = o.state();
        StateVector other = target.state(o.time());
        DVec3 dir = ports.direction();
        // Where our port's face is now, against where it should be.
        DVec3 myFace = vessel.portFaceInertial(ports.mine());
        DVec3 spot = ports.face().add(dir.scale(in ? 0 : DOCK_HOLD));
        // Motion against that spot (it moves round with the station if the station turns).
        DVec3 rel = own.velocity().sub(spotVelocity(spot, o.time(), other));
        DVec3 error = spot.sub(myFace);
        double along = error.dot(dir);
        DVec3 lateral = error.sub(dir.scale(along));
        double turnedOff = DockGeometry.angle(vessel.inertialOrientation(), ports.attitude());
        // Wanted motion: square up sideways first, then along the axis.
        DVec3 wanted = clampLength(lateral.scale(1.0), 0.6);
        double gap = myFace.sub(ports.face()).dot(dir);
        if (in) {
            if (gap < -1.5) {
                phase = Phase.DOCK_ALIGN; // went past: back out and line up again
            }
            if (lateral.length() < 0.5 && turnedOff < Math.toRadians(6)) {
                wanted = wanted.add(dir.scale(-Math.min(DOCK_SPEED, Math.max(0.08, gap * 0.15))));
            }
        } else {
            wanted = wanted.add(dir.scale(Math.max(-0.8, Math.min(0.8, along * 0.3))));
            if (turnedOff < Math.toRadians(2.5) && lateral.length() < 0.25 && Math.abs(along) < 0.6 && rel.length() < 0.1) {
                phase = Phase.DOCK_IN;
            }
        }
        DVec3 change = wanted.sub(rel);
        dockDebug = String.format(java.util.Locale.ROOT, "gap %.2f along %.2f side %.2f turned %.1f rel %.3f",
                gap, along, lateral.length(), Math.toDegrees(turnedOff), rel.length());
        Quaterniond hold = vessel.worldOrientation(ports.attitude());
        double rcs = vessel.dockAcceleration();
        return new Command(now.prograde(), 0, now.toWorld().apply(clampLength(change.scale(3), rcs * 0.9)), hold);
    }

    /**
     * How fast a spot fixed to the other ship moves (fixed space): its orbit, plus the spot
     * swinging round as the ship turns (an idle station turns with its orbit, so a spot out in
     * front of its port moves faster than the port does). Measured from tick to tick, the same
     * way the spot itself is worked out; a jump (a new spot) starts the measuring again.
     */
    private DVec3 spotVelocity(DVec3 spot, double t, StateVector centre) {
        DVec3 arm = spot.sub(centre.position());
        double dt = t - lastArmTime;
        if (dt == 0 && armVelocity != null) {
            return centre.velocity().add(armVelocity);
        }
        if (lastArm != null && dt > 1e-4 && dt < 2 && arm.sub(lastArm).length() < 0.5) {
            DVec3 measured = arm.sub(lastArm).scale(1 / dt);
            armVelocity = armVelocity == null ? measured : armVelocity.scale(0.8).add(measured.scale(0.2));
        } else {
            armVelocity = null;
        }
        lastArm = arm;
        lastArmTime = t;
        return armVelocity != null ? centre.velocity().add(armVelocity) : centre.velocity();
    }

    private static DVec3 clampLength(DVec3 v, double most) {
        double length = v.length();
        return length > most && length > 0 ? v.scale(most / length) : v;
    }

    /** Our port and theirs, where theirs is and points, and how we must turn to meet it. */
    private record Ports(VesselStats.Port mine, VesselStats.Port theirs, DVec3 face, DVec3 direction, Quaterniond attitude,
                         DVec3 portOffset) {

        /** Where our centre of mass is when our port's face is {@code gap} blocks out from theirs. */
        DVec3 comFor(double gap) {
            return face.add(direction.scale(gap)).sub(portOffset);
        }
    }

    private Ports ports(VesselEntity vessel, DockTarget target) {
        VesselEntity other = target.vessel();
        if (other == null || other.orbital() == null) {
            return null;
        }
        VesselStats.Port mine = portAt(vessel, myPort);
        VesselStats.Port theirs = portAt(other, theirPort);
        if (mine == null || theirs == null) {
            return null;
        }
        double dt = vessel.orbital().time() - other.orbital().time();
        DVec3 drift = other.orbital().state().velocity().scale(dt);
        DVec3 face = other.portFaceInertial(theirs).add(drift);
        DVec3 direction = other.portDirectionInertial(theirs).normalize();
        if (!DockGeometry.canMeet(mine.facing(), theirs.facing())) {
            dockTip = true;
            phase = Phase.TOO_WEAK; // (a parked ship's ports are only known once it wakes)
            return null;
        }
        Quaterniond attitude = DockGeometry.dockAttitude(vessel.inertialOrientation(), other.inertialOrientation(), mine.facing(), theirs.facing());
        if (attitude == null) {
            return null;
        }
        // Our port's face from our centre of mass, once turned to dock.
        var com = vessel.stats().centerOfMass();
        DVec3 offset = DockGeometry.turn(attitude, mine.pos().getX() + 0.5 + mine.facing().getStepX() * 0.5 - com.x,
                mine.pos().getY() + 0.5 + mine.facing().getStepY() * 0.5 - com.y,
                mine.pos().getZ() + 0.5 + mine.facing().getStepZ() * 0.5 - com.z);
        return new Ports(mine, theirs, face, direction, attitude, offset);
    }

    /** The free docking port at {@code pos} on a ship (null if there's none, or it's joined already). */
    private static VesselStats.Port portAt(VesselEntity vessel, net.minecraft.core.BlockPos pos) {
        for (VesselStats.Port p : vessel.stats().ports()) {
            if (p.pos().equals(pos)) {
                var next = vessel.structure().get(pos.relative(p.facing()));
                return next == null ? p : null;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------
    // Transfers between a planet and its moon
    // ---------------------------------------------------------------------------------------

    private Command plan(VesselEntity vessel, CelestialBody body, double mu) {
        Trajectory now = Trajectory.of(vessel, body, mu);
        if (planning == null) {
            var system = ServerUniverse.system();
            CelestialBody target = system.body(destination);
            OrbitalFlight o = vessel.orbital();
            StateVector state = o.state();
            double t0 = o.time();
            double scale = SpaceConfig.GRAVITY_SCALE.get();
            boolean out = outward;
            double arrival = moonArrival(target);
            planning = java.util.concurrent.CompletableFuture.supplyAsync(() -> out
                    ? TransferPlanner.toMoon(system, body, target, state, t0, scale, arrival)
                    : TransferPlanner.toPlanet(system, body, state, t0, scale, HOME_ARRIVAL));
        } else if (planning.isDone()) {
            TransferPlanner.Plan plan = planning.getNow(null);
            planning = null;
            if (plan == null) {
                phase = Phase.NO_TRANSFER;
                return new Command(now.prograde(), 0);
            }
            burnTime = plan.burnTime();
            burnDeltaV = plan.deltaV();
            StateVector at = Kepler.elementsFromState(vessel.orbital().state(), mu, vessel.orbital().time()).stateAt(burnTime, mu);
            burnSpeed = at.velocity().length() + plan.deltaV();
            phase = Phase.WAIT_BURN;
        }
        return new Command(now.prograde(), 0);
    }

    private Command waitForBurn(VesselEntity vessel, CelestialBody body, double mu) {
        Trajectory now = Trajectory.of(vessel, body, mu);
        double duration = burnDeltaV / Math.max(0.01, vessel.maxAcceleration());
        double until = burnTime - duration / 2 - vessel.orbital().time();
        if (until > 20) {
            warpToward(vessel, until - 15);
        } else {
            warp(vessel, 1);
        }
        if (until <= 0) {
            phase = Phase.TRANSFER_BURN;
        }
        return new Command(now.prograde(), 0);
    }

    private Command transferBurn(VesselEntity vessel, CelestialBody body, double mu) {
        Trajectory now = Trajectory.of(vessel, body, mu);
        double speed = now.state().velocity().length();
        if (speed >= burnSpeed) {
            phase = Phase.CRUISE;
            eventTime = Double.NaN;
            burnEnd = vessel.orbital().time();
            corrections = 0;
            return new Command(now.prograde(), 0);
        }
        double throttle = Math.max(0.05, Math.min(1, (burnSpeed - speed) / Math.max(0.01, vessel.maxAcceleration()) * 2));
        return new Command(now.prograde(), throttle);
    }

    /** Coasting to the other body: warp toward the moment it takes over. */
    private Command cruise(VesselEntity vessel, CelestialBody body, double mu) {
        Trajectory now = Trajectory.of(vessel, body, mu);
        if (body.id().equals(destination)) {
            warp(vessel, 1);
            if (outward) {
                phase = Phase.CAPTURE;
            } else {
                // Home: bring the lowest point down into the air if it misses, then land.
                if (program == Program.RETURN) {
                    program = Program.LAND;
                }
                phase = Phase.DEORBIT;
            }
            return new Command(now.prograde(), 0);
        }
        OrbitalFlight o = vessel.orbital();
        var system = ServerUniverse.system();
        CelestialBody target = system.body(destination);
        double scale = SpaceConfig.GRAVITY_SCALE.get();
        double aim = outward ? moonArrival(target) : HOME_ARRIVAL;
        // A correction is being worked out.
        if (correcting != null) {
            warp(vessel, 1);
            if (correcting.isDone()) {
                DVec3 dv = correcting.getNow(null);
                correcting = null;
                corrections++;
                if (dv != null && dv.length() > 0.05) {
                    correctionDirection = dv.normalize();
                    correctionLeft = dv.length();
                    afterBurn = Phase.CRUISE;
                    phase = Phase.CORRECTION;
                }
            }
            return new Command(now.prograde(), 0);
        }
        double sinceBurn = o.time() - burnEnd;
        if (sinceBurn < CORRECTION_DELAY) {
            warpToward(vessel, CORRECTION_DELAY - sinceBurn);
            return new Command(now.prograde(), 0);
        }
        if (Double.isNaN(eventTime) || --cruiseCheck <= 0) {
            cruiseCheck = 20;
            var path = com.thepearlking.stevespace.universe.TrajectoryPredictor.predict(system, body, o.state(), o.time(), scale, 3 * 86400);
            Double arrival = TransferPlanner.arrivalWith(system, body, target, o.state(), o.time(), DVec3.ZERO, scale);
            boolean onTarget = arrival != null && arrival > aim * 0.4 && arrival < aim * 2.5;
            if (!onTarget && corrections < MAX_CORRECTIONS) {
                // Off target: work out a small correction from here.
                StateVector state = o.state();
                double t = o.time();
                CelestialBody from = body;
                correcting = java.util.concurrent.CompletableFuture.supplyAsync(
                        () -> TransferPlanner.correction(system, from, target, state, t, scale, aim));
                warp(vessel, 1);
                return new Command(now.prograde(), 0);
            }
            eventTime = path.isEmpty() || path.get(0).end() == com.thepearlking.stevespace.universe.TrajectoryPredictor.End.TIME_LIMIT
                    ? Double.NaN : path.get(0).endTime();
        }
        if (!Double.isNaN(eventTime)) {
            warpToward(vessel, eventTime - o.time());
        }
        return new Command(now.prograde(), 0);
    }

    /** Flies a planned correction: point along it, burn until it is all given. */
    private Command correct(VesselEntity vessel, CelestialBody body, double mu) {
        Trajectory now = Trajectory.of(vessel, body, mu);
        warp(vessel, 1);
        // What the engines actually gave last tick.
        correctionLeft -= vessel.maxAcceleration() * vessel.throttle() * 0.05;
        // Long burns: the ship moves on while burning, so work out what is still needed every
        // half second rather than holding the direction chosen at the start.
        if (--steerCheck <= 0 && vessel.orbital() != null && correctionLeft > 0.05) {
            steerCheck = 10;
            DVec3 still = steer(vessel, body, mu);
            if (still != null) {
                correctionDirection = still.normalize();
                correctionLeft = still.length();
            }
        }
        DVec3 direction = now.toWorld().apply(correctionDirection);
        if (correctionLeft <= 0.01) {
            phase = afterBurn;
            eventTime = Double.NaN;
            cruiseCheck = 0;
            return new Command(direction, 0);
        }
        double throttle = Math.max(0.02, Math.min(1, correctionLeft / Math.max(0.01, vessel.maxAcceleration()) * 2));
        return new Command(direction, throttle);
    }

    /** The rest of the burn being flown, re-solved from where the ship is now (null: keep going). */
    private DVec3 steer(VesselEntity vessel, CelestialBody body, double mu) {
        StateVector s = vessel.orbital().state();
        double t = vessel.orbital().time();
        var system = ServerUniverse.system();
        if (afterBurn == Phase.LEAVE && departureExcess.length() > 0) {
            DVec3 still = InterplanetaryPlanner.remaining(s.position(), s.velocity(), correctionDirection.scale(correctionLeft), departureExcess, mu);
            // Only small adjustments: a very different answer means the solve went astray.
            return still == null || still.length() > correctionLeft * 1.3 + 2
                    || still.normalize().dot(correctionDirection) < 0.8 ? null : still;
        }
        if (afterBurn == Phase.HELIO) {
            CelestialBody target = system.body(destination);
            if (target != null && target.parent() != null && target.parent().id().equals(body.id())) {
                DVec3 still = InterplanetaryPlanner.aim(system, target, s, t, arriveTime, target.radius() + arrivalAltitude(target),
                        SpaceConfig.GRAVITY_SCALE.get());
                return still == null || still.length() > correctionLeft * 1.5 + 5 ? null : still;
            }
        }
        return null;
    }

    /** Arriving at a moon: at the lowest point, burn backwards into a round orbit. */
    private Command capture(VesselEntity vessel, CelestialBody body, double mu) {
        Trajectory now = Trajectory.of(vessel, body, mu);
        OrbitalElements orbit = now.orbit();
        double circular = Math.sqrt(mu / now.radius());
        double speed = now.state().velocity().length();
        double excess = Math.max(0, speed - circular);
        double duration = excess / Math.max(0.01, vessel.maxAcceleration());
        double toPeriapsis = timeToPeriapsis(orbit, mu, now);
        if (orbit.eccentricity() < 0.02 || (now.verticalSpeed() >= 0 && speed <= circular * 1.01)) {
            phase = Phase.DONE;
            return new Command(now.retrograde(), 0);
        }
        if (toPeriapsis > duration / 2 + 20 && now.verticalSpeed() < 0) {
            warpToward(vessel, toPeriapsis - duration / 2 - 15);
            return new Command(now.retrograde(), 0);
        }
        warp(vessel, 1);
        if (toPeriapsis > duration / 2 && now.verticalSpeed() < 0) {
            return new Command(now.retrograde(), 0);
        }
        double throttle = Math.max(0.05, Math.min(1, excess / Math.max(0.01, vessel.maxAcceleration()) * 2));
        return new Command(now.retrograde(), throttle);
    }

    /** Seconds until the lowest point of the orbit (0 once past it, for an escape path). */
    private static double timeToPeriapsis(OrbitalElements orbit, double mu, Trajectory now) {
        double r = now.radius();
        double e = orbit.eccentricity();
        double a = orbit.semiMajorAxis();
        if (now.verticalSpeed() >= 0) {
            return 0;
        }
        if (e < 1) {
            double ecc = Math.acos(Math.max(-1, Math.min(1, (1 - r / a) / Math.max(1e-9, e))));
            double m = ecc - e * Math.sin(ecc);
            return m / orbit.meanMotion(mu);
        }
        double h = acosh(Math.max(1, (1 - r / a) / e));
        double m = e * Math.sinh(h) - h;
        return m / Math.sqrt(mu / Math.abs(a * a * a));
    }

    private static double acosh(double x) {
        return Math.log(x + Math.sqrt(x * x - 1));
    }

    // ---------------------------------------------------------------------------------------
    // Flying anywhere: a route of legs (up out of a moon's pull, across to another planet, down
    // to one of its moons), each ending in orbit, then orbit or land at the end
    // ---------------------------------------------------------------------------------------

    /** Height of a low orbit around a body: just above its air (or a little over the ground). */
    static double parkingAltitude(CelestialBody body) {
        double park = VesselTransfer.spaceAltitude(body) + 600;
        if (body.course() != null) {
            // Acheron: its own pull reaches only half way to the Moon on its pass; park well inside it.
            double room = ServerUniverse.system().sphereOfInfluence(body) - body.radius();
            park = Math.min(park, room * 0.8);
        }
        return park;
    }

    /** Arrival height over a moon: low, but well inside its (sometimes tiny) sphere of influence. */
    private static double moonArrival(CelestialBody moon) {
        return moonArrival(ServerUniverse.system(), moon);
    }

    static double moonArrival(com.thepearlking.stevespace.universe.SolarSystem system, CelestialBody moon) {
        if (moon.parent() == null) {
            return MOON_ARRIVAL;
        }
        double room = system.sphereOfInfluence(moon) - moon.radius();
        return Math.max(20, Math.min(MOON_ARRIVAL, room * 0.3));
    }

    /** Where to arrive at a planet: low orbit, or, when heading on to one of its moons, half way out to it. */
    private double planetArrival(CelestialBody planet) {
        var system = ServerUniverse.system();
        return planetArrival(planet, system.body(finalDestination));
    }

    static double planetArrival(CelestialBody planet, CelestialBody end) {
        CelestialBody next = end == null ? null : childToward(planet, end);
        double low = parkingAltitude(planet);
        if (next == null) {
            return low;
        }
        return Math.max(low, next.orbit().semiMajorAxis() * 0.5 - planet.radius());
    }

    /** Arrival height over the body a leg goes to: a moon, or a planet. */
    private double arrivalAltitude(CelestialBody body) {
        var system = ServerUniverse.system();
        boolean moon = body.parent() != null && !body.parent().id().equals(system.sun().id());
        return moon ? moonArrival(body) : planetArrival(body);
    }

    /** The child of {@code body} on the way down to {@code target}, or null if target isn't below it. */
    static CelestialBody childToward(CelestialBody body, CelestialBody target) {
        CelestialBody c = target;
        while (c != null && c.parent() != null) {
            if (c.parent().id().equals(body.id())) {
                return c;
            }
            c = c.parent();
        }
        return null;
    }

    /** Decides the next leg from wherever the ship is now. */
    private Command route(VesselEntity vessel, CelestialBody body, double mu) {
        var system = ServerUniverse.system();
        CelestialBody end = system.body(finalDestination);
        Trajectory now = Trajectory.of(vessel, body, mu);
        warp(vessel, 1);
        if (end == null) {
            phase = Phase.NO_TRANSFER;
            return new Command(now.prograde(), 0);
        }
        if (vessel.orbital() == null) {
            if (vessel.isGrounded() && body.id().equals(end.id())) {
                finished = true;
                phase = Phase.DONE;
            } else {
                phase = Phase.CLIMB; // on the ground or in the air somewhere else: get to orbit first
            }
            return new Command(DVec3.Y, 0);
        }
        // Going to Acheron and come to the Moon it is passing: straight across to it.
        if (end.course() != null && system.visitorsOf(body).contains(end)
                && com.thepearlking.stevespace.universe.AcheronCourse.encounter(system, vessel.orbital().time())) {
            phase = Phase.HOP;
            return new Command(now.prograde(), 0);
        }
        // Arriving fast, or on a path that leaves: first settle into an orbit here.
        if (now.eccentricity() >= 1) {
            phase = Phase.CAPTURE;
            return new Command(now.retrograde(), 0);
        }
        // An orbit that dips into the air would come down while waiting: round it off first.
        boolean landingHere = body.id().equals(end.id()) && landAtEnd;
        if (!landingHere && body.hasAtmosphere() && now.periapsisAltitude() < VesselTransfer.spaceAltitude(body) + 100) {
            phase = Phase.CIRCULARIZE;
            return new Command(now.prograde(), 0);
        }
        // Orbiting the Sun itself: out of the planets' pull, then down close to it, round.
        if (end.parent() == null) {
            if (body.id().equals(end.id())) {
                double target = body.radius() * SUN_ORBIT;
                if (now.radius() < target * 1.3 && now.eccentricity() < 0.05) {
                    finished = true;
                    phase = Phase.DONE;
                } else if (now.orbit().periapsis() > target * 1.1) {
                    phase = Phase.SUN_DIVE;
                } else if (now.radius() > target * 1.3) {
                    phase = Phase.SUN_FALL;
                } else {
                    phase = Phase.CIRCULARIZE;
                }
            } else {
                leaving = body.id();
                destination = body.parent() == null ? end.id() : body.parent().id();
                phase = Phase.ESCAPE;
            }
            return new Command(now.prograde(), 0);
        }
        if (body.id().equals(end.id())) {
            if (landAtEnd) {
                phase = Phase.DEORBIT;
            } else if (now.apoapsisAltitude() > Math.max(parkingAltitude(body) * 3, parkingAltitude(body) + 3000)) {
                phase = Phase.SETTLE;
            } else {
                finished = true;
                phase = Phase.DONE;
            }
            return new Command(now.prograde(), 0);
        }
        CelestialBody down = childToward(body, end);
        if (down != null) {
            destination = down.id();
            outward = true;
            phase = Phase.INTER_PLAN;
            interPlanning = null;
            return new Command(now.prograde(), 0);
        }
        if (body.parent() != null && body.parent() != system.sun() && !body.parent().id().equals(system.sun().id())) {
            // On a moon: climb out of its pull into the planet's.
            CelestialBody parent = body.parent();
            destination = parent.id();
            if (parent.id().equals(end.id()) && landAtEnd) {
                outward = false; // straight down into the planet's air, like coming home
                phase = Phase.PLAN;
            } else {
                leaving = body.id();
                phase = Phase.ESCAPE;
            }
            return new Command(now.prograde(), 0);
        }
        // Around a planet: across to the planet (or the planet of the moon) we're going to.
        CelestialBody across = end;
        while (across.parent() != null && !across.parent().id().equals(system.sun().id())) {
            across = across.parent();
        }
        destination = across.id();
        phase = Phase.INTER_PLAN;
        interPlanning = null;
        return new Command(now.prograde(), 0);
    }

    /** Leaving a moon: speed up past escape when moving along with the moon, so we end up near its orbit. */
    private Command escape(VesselEntity vessel, CelestialBody body, double mu) {
        Trajectory now = Trajectory.of(vessel, body, mu);
        if (!body.id().equals(leaving)) {
            phase = Phase.ROUTE;
            return new Command(now.prograde(), 0);
        }
        double speed = now.state().velocity().length();
        double escape = Math.sqrt(2 * mu / now.radius());
        if (speed >= escape * 1.04) {
            phase = Phase.LEAVE;
            eventTime = Double.NaN;
            return new Command(now.prograde(), 0);
        }
        var moonVelocity = ServerUniverse.system().stateRelativeToParent(body, vessel.orbital().time()).velocity();
        boolean along = now.state().velocity().normalize().dot(moonVelocity.normalize()) > 0.85;
        if (!along && speed < escape * 0.98) {
            // Wait for the right point of the orbit, warping: the direction of travel turns at
            // about the orbit's mean motion.
            double angle = Math.acos(Math.max(-1, Math.min(1, now.state().velocity().normalize().dot(moonVelocity.normalize()))));
            double n = now.eccentricity() < 1 ? now.orbit().meanMotion(mu) : 1e-3;
            warpToward(vessel, (angle - Math.toRadians(30)) / n - 5);
            return new Command(now.prograde(), 0);
        }
        double throttle = Math.max(0.05, Math.min(1, (escape * 1.04 - speed) / Math.max(0.01, vessel.maxAcceleration()) * 2));
        return new Command(now.prograde(), throttle);
    }

    /** Out among the planets, heading for the Sun: brakes until the path's lowest point is close to it. */
    private Command sunDive(VesselEntity vessel, CelestialBody sun, double mu) {
        Trajectory now = Trajectory.of(vessel, sun, mu);
        if (sun.parent() != null) {
            phase = Phase.ROUTE; // back inside a planet's pull: get out of it again first
            return new Command(now.prograde(), 0);
        }
        // Not right at the edge of the planet just left: braking there drops the ship back into
        // its pull. Coast well clear first.
        var system = ServerUniverse.system();
        CelestialBody left = leaving == null ? null : system.body(leaving);
        if (left != null && left.parent() != null) {
            double t = vessel.orbital().time();
            double away = now.state().position().add(system.absolutePosition(sun, t)).sub(system.absolutePosition(left, t)).length();
            double soi = system.sphereOfInfluence(left);
            if (away < soi * 4) {
                double speed = Math.max(1, now.state().velocity().sub(system.stateRelativeToParent(left, t).velocity()).length());
                warpToward(vessel, (soi * 4 - away) / speed);
                return new Command(now.retrograde(), 0);
            }
        }
        double target = sun.radius() * SUN_ORBIT;
        double gap = now.orbit().periapsis() - target;
        if (gap <= 0) {
            phase = Phase.SUN_FALL;
            return new Command(now.retrograde(), 0);
        }
        warp(vessel, 1);
        DVec3 lower = lowerPeriapsis(now.state(), mu);
        DVec3 direction = lower != null ? now.toWorld().apply(lower) : now.retrograde();
        double throttle = Math.max(0.05, Math.min(1, gap / Math.max(1, target * 0.5)));
        return new Command(direction, throttle);
    }

    /** Falling toward the Sun: warp to just before the closest point, then round the orbit off there. */
    private Command sunFall(VesselEntity vessel, CelestialBody sun, double mu) {
        Trajectory now = Trajectory.of(vessel, sun, mu);
        if (sun.parent() != null) {
            phase = Phase.ROUTE; // fell into a planet's pull on the way: sort that out first
            return new Command(now.prograde(), 0);
        }
        double peri = now.orbit().periapsis();
        double a = now.orbit().semiMajorAxis();
        double speedThere = Math.sqrt(mu * (2 / peri - 1 / a));
        double burnTime = (speedThere - Math.sqrt(mu / peri)) / Math.max(1e-3, vessel.maxAcceleration());
        double until = now.timeToPeriapsis();
        if (until <= burnTime / 2 + 2) {
            phase = Phase.CIRCULARIZE;
            return circularize(vessel, sun, mu);
        }
        warpToward(vessel, until - burnTime / 2);
        return new Command(now.retrograde(), 0);
    }

    /** Coasting out of a sphere of influence: warp toward the moment we leave it. */
    private Command leave(VesselEntity vessel, CelestialBody body, double mu) {
        Trajectory now = Trajectory.of(vessel, body, mu);
        if (!body.id().equals(leaving)) {
            phase = leaving.equals(destination) ? Phase.ROUTE : (body.id().equals(ServerUniverse.system().sun().id()) ? Phase.HELIO : Phase.ROUTE);
            if (phase == Phase.HELIO) {
                helioStart = vessel.orbital().time();
                helioCorrections = 0;
            }
            eventTime = Double.NaN;
            warp(vessel, 1);
            return new Command(now.prograde(), 0);
        }
        if (Double.isNaN(eventTime) || --cruiseCheck <= 0) {
            cruiseCheck = 40;
            var path = com.thepearlking.stevespace.universe.TrajectoryPredictor.predict(ServerUniverse.system(), body,
                    vessel.orbital().state(), vessel.orbital().time(), SpaceConfig.GRAVITY_SCALE.get(), 30 * 86400);
            eventTime = path.isEmpty() ? Double.NaN : path.get(0).endTime();
            if (!path.isEmpty() && path.get(0).end() != com.thepearlking.stevespace.universe.TrajectoryPredictor.End.ESCAPE) {
                // Not leaving after all (the burn fell short): try again.
                phase = Phase.ROUTE;
                return new Command(now.prograde(), 0);
            }
        }
        if (!Double.isNaN(eventTime)) {
            warpToward(vessel, eventTime - vessel.orbital().time());
        }
        return new Command(now.prograde(), 0);
    }

    /** Works out the flight to another planet: the best departure, then the exact burn. */
    private Command interPlan(VesselEntity vessel, CelestialBody body, double mu) {
        Trajectory now = Trajectory.of(vessel, body, mu);
        warp(vessel, 1);
        if (interPlanning == null) {
            var system = ServerUniverse.system();
            CelestialBody target = system.body(destination);
            StateVector state = vessel.orbital().state();
            double t0 = vessel.orbital().time();
            double scale = SpaceConfig.GRAVITY_SCALE.get();
            double arriveRadius = target.radius() + arrivalAltitude(target);
            double muFrom = body.mu() * scale;
            boolean toMoon = target.parent() != null && target.parent().id().equals(body.id());
            interPlanning = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                if (toMoon) {
                    return InterplanetaryPlanner.toMoon(system, body, target, state, t0, arriveRadius, scale);
                }
                var transfer = InterplanetaryPlanner.porkchop(system, body, target, t0 + 120, state, arriveRadius, scale);
                if (transfer == null) {
                    return null;
                }
                var burn = InterplanetaryPlanner.departure(state, t0, muFrom, transfer.vInfDepart(), transfer.departTime());
                if (burn == null) {
                    return null;
                }
                return new InterplanetaryPlanner.Burn[]{burn, new InterplanetaryPlanner.Burn(transfer.arriveTime(), transfer.vInfDepart())};
            });
        } else if (interPlanning.isDone()) {
            InterplanetaryPlanner.Burn[] plan = interPlanning.getNow(null);
            interPlanning = null;
            if (plan == null) {
                phase = Phase.NO_TRANSFER;
                return new Command(now.prograde(), 0);
            }
            burnTime = plan[0].time();
            correctionDirection = plan[0].deltaV().normalize();
            correctionLeft = plan[0].deltaV().length();
            arriveTime = plan[1].time();
            departureExcess = plan[1].deltaV();
            phase = Phase.INTER_WAIT;
        }
        return new Command(now.prograde(), 0);
    }

    /** Waiting (warping) for the departure; then the burn, centred on the planned moment. */
    private Command interWait(VesselEntity vessel, CelestialBody body, double mu) {
        Trajectory now = Trajectory.of(vessel, body, mu);
        DVec3 direction = now.toWorld().apply(correctionDirection);
        double duration = correctionLeft / Math.max(0.01, vessel.maxAcceleration());
        double until = burnTime - duration / 2 - vessel.orbital().time();
        if (until < -30) {
            phase = Phase.INTER_PLAN; // missed it (paused, or too slow to turn): plan again
            return new Command(direction, 0);
        }
        if (until > 20) {
            warpToward(vessel, until - 15);
        } else {
            warp(vessel, 1);
        }
        if (until <= 0) {
            var system = ServerUniverse.system();
            CelestialBody target = system.body(destination);
            boolean toMoon = target != null && target.parent() != null && target.parent().id().equals(body.id());
            leaving = body.id();
            afterBurn = toMoon ? Phase.HELIO : Phase.LEAVE;
            helioStart = burnTime;
            helioCorrections = 0;
            eventTime = Double.NaN;
            phase = Phase.CORRECTION;
        }
        return new Command(direction, 0);
    }

    /** Between the planets: a few Lambert corrections, otherwise warp toward the arrival. */
    private Command helio(VesselEntity vessel, CelestialBody body, double mu) {
        Trajectory now = Trajectory.of(vessel, body, mu);
        var system = ServerUniverse.system();
        CelestialBody goal = system.body(destination);
        CelestialBody central = goal == null || goal.parent() == null ? system.sun() : goal.parent();
        if (!body.id().equals(central.id())) {
            warp(vessel, 1);
            phase = body.id().equals(destination) ? Phase.ARRIVE : Phase.ROUTE;
            return new Command(now.prograde(), 0);
        }
        double t = vessel.orbital().time();
        double total = Math.max(1, arriveTime - helioStart);
        if (t > arriveTime + total * 0.3) {
            phase = travelling() ? Phase.ROUTE : Phase.NO_TRANSFER; // went past without meeting it
            return new Command(now.prograde(), 0);
        }
        double[] at = {0.01, 0.5, 0.88};
        CelestialBody target = system.body(destination);
        if (helioCorrections < at.length && t >= helioStart + at[helioCorrections] * total) {
            helioCorrections++;
            DVec3 dv = InterplanetaryPlanner.aim(system, target, vessel.orbital().state(), t, arriveTime,
                    target.radius() + arrivalAltitude(target), SpaceConfig.GRAVITY_SCALE.get());
            warp(vessel, 1);
            if (dv != null && dv.length() > 0.05) {
                correctionDirection = dv.normalize();
                correctionLeft = dv.length();
                afterBurn = Phase.HELIO;
                phase = Phase.CORRECTION;
                return new Command(now.toWorld().apply(correctionDirection), 0);
            }
            return new Command(now.prograde(), 0);
        }
        double next = helioCorrections < at.length ? helioStart + at[helioCorrections] * total : Double.POSITIVE_INFINITY;
        // Slow down before the planet's sphere of influence: the soonest we could reach it from
        // here, going straight at it at our speed relative to it.
        StateVector planet = system.stateRelativeToParent(target, t);
        StateVector relative = now.state().sub(planet);
        double soi = system.sphereOfInfluence(target);
        double distance = relative.position().length();
        double gap = distance - soi;
        // When we get there: by how fast the distance is actually closing (most of the speed on
        // the way in is sideways: dividing by all of it said "nearly there" for days and held
        // the warp down), and no later than the plan's arrival less the crossing of its pull.
        double closing = -relative.velocity().dot(relative.position()) / Math.max(1, distance);
        double planned = arriveTime - soi / Math.max(1, relative.velocity().length());
        // Going away from it, outside its pull, after we should have arrived: we missed it.
        // Plan a new way there now rather than drifting on at normal time hoping.
        if (closing <= 0.01 && gap > 0 && t > planned) {
            com.thepearlking.stevespace.StevesSpace.LOGGER.info("Autopilot missed {} by {} km: planning again", target.id(), (long) gap);
            warp(vessel, 1);
            phase = travelling() ? Phase.ROUTE : Phase.NO_TRANSFER;
            return new Command(now.prograde(), 0);
        }
        // Running a little late (small worlds pull too weakly to keep to the plan's minute) is no
        // reason to crawl: once the planned moment has gone by, go by how fast it's closing (with
        // some margin, as its pull speeds us up at the end).
        double byClosing = closing > 0.01 ? t + Math.max(0, gap) / closing : Double.POSITIVE_INFINITY;
        double entry = planned > t ? Math.min(byClosing, planned) : t + Math.max(0, gap) * 0.7 / Math.max(0.01, closing);
        if (gap <= 0) {
            entry = t;
        }
        warpToward(vessel, Math.min(next, entry) - t);
        return new Command(now.prograde(), 0);
    }

    /** Inside the target planet's pull: trim the closest approach, then capture into orbit. */
    private Command arrive(VesselEntity vessel, CelestialBody body, double mu) {
        Trajectory now = Trajectory.of(vessel, body, mu);
        double want = body.radius() + arrivalAltitude(body);
        double pe = periapsis(now.state().position(), now.state().velocity(), mu);
        double error = pe - want;
        double tolerance = Math.max(150, (want - body.radius()) * 0.15);
        if (now.verticalSpeed() < 0 && now.radius() > want * 3 && Math.abs(error) > tolerance) {
            warp(vessel, 1);
            DVec3 lower = lowerPeriapsis(now.state(), mu);
            if (lower == null) {
                phase = Phase.CAPTURE;
                return new Command(now.retrograde(), 0);
            }
            DVec3 direction = error > 0 ? lower : lower.scale(-1);
            // Full throttle while far off, easing in at the end (a long trim at 5% kept it at
            // normal time for ages).
            double throttle = Math.max(0.15, Math.min(1, Math.abs(error) / Math.max(1, tolerance * 4)));
            return new Command(now.toWorld().apply(direction), throttle);
        }
        phase = Phase.CAPTURE;
        return new Command(now.retrograde(), 0);
    }

    /**
     * Across from the Moon to Acheron hanging beside it: head straight for it, closing no faster
     * than the engines can stop from (at half strength) nor than a few times a slow orbit there,
     * to come to rest just over its surface. Its own pull takes over on the way (the craft is
     * handed to it once nearer it than the Moon), and the arrival and landing go on from there.
     */
    private Command hop(VesselEntity vessel, CelestialBody body, double mu) {
        Trajectory now = Trajectory.of(vessel, body, mu);
        var system = ServerUniverse.system();
        CelestialBody end = system.body(finalDestination);
        if (end == null || body.id().equals(end.id())) {
            phase = Phase.ARRIVE;
            return new Command(now.retrograde(), 0);
        }
        double t = vessel.orbital().time();
        if (!system.visitorsOf(body).contains(end) || !com.thepearlking.stevespace.universe.AcheronCourse.encounter(system, t)) {
            phase = Phase.ROUTE;
            return new Command(now.prograde(), 0);
        }
        StateVector own = vessel.orbital().state();
        StateVector target = system.stateRelativeToParent(end, t).sub(system.stateRelativeToParent(body, t));
        DVec3 to = target.position().sub(own.position());
        double distance = to.length();
        DVec3 relative = own.velocity().sub(target.velocity());
        double accel = Math.max(0.01, vessel.maxAcceleration());
        double stopAt = end.radius() + parkingAltitude(end) * 0.6;
        double range = Math.max(0, distance - stopAt);
        double cap = 2.5 * Math.sqrt(mu / Math.max(1, own.position().length())); // a few times a slow orbit's speed here
        double speed = Math.min(cap, Math.sqrt(2 * accel * 0.5 * range));
        DVec3 wanted = to.scale(1 / Math.max(1e-9, distance)).scale(speed);
        DVec3 error = wanted.sub(relative);
        if (error.length() < 0.1 * Math.max(1, cap)) {
            // On course, coasting: warp, looking again well before it's time to brake.
            warpToward(vessel, range / Math.max(0.01, speed) * 0.3 - 10);
            return new Command(now.toWorld().apply(wanted.length() > 0 ? wanted.normalize() : now.prograde()), 0);
        }
        warp(vessel, 1);
        double throttle = Math.max(0.03, Math.min(1, error.length() / accel));
        return new Command(now.toWorld().apply(error.normalize()), throttle);
    }

    /** In a high orbit over the final world: bring the low point down to a low orbit, then round it off. */
    private Command settle(VesselEntity vessel, CelestialBody body, double mu) {
        Trajectory now = Trajectory.of(vessel, body, mu);
        double want = body.radius() + parkingAltitude(body);
        double pe = periapsis(now.state().position(), now.state().velocity(), mu);
        if (pe <= want * 1.02) {
            phase = Phase.CAPTURE;
            return new Command(now.retrograde(), 0);
        }
        warp(vessel, 1);
        DVec3 lower = lowerPeriapsis(now.state(), mu);
        DVec3 direction = lower != null ? now.toWorld().apply(lower) : now.retrograde();
        double throttle = Math.max(0.05, Math.min(1, (pe - want) / Math.max(1, want)));
        return new Command(direction, throttle);
    }

    // ---------------------------------------------------------------------------------------
    // Time warp, run by the autopilot itself during long waits
    // ---------------------------------------------------------------------------------------

    /**
     * The fastest warp that still leaves a few seconds of normal time before the event, reached
     * gradually: one step faster per second at most (the way a player would turn it up), but
     * straight down whenever the event comes close.
     */
    private static void warpToward(VesselEntity vessel, double seconds) {
        int[] levels = com.thepearlking.stevespace.universe.TimeWarp.LEVELS;
        int wanted = 0;
        boolean cruising = vessel.orbital() != null && vessel.orbital().body().parent() == null;
        for (int i = 0; i < levels.length; i++) {
            if (levels[i] > com.thepearlking.stevespace.universe.TimeWarp.NEAR_PLANET_LIMIT && !cruising) {
                break; // the fastest warps only between the planets
            }
            // At most one twentieth of the remaining time per tick (every tick looks again).
            if (levels[i] * 0.05 * 20 <= seconds) {
                wanted = i;
            }
        }
        var server = vessel.level().getServer();
        if (server == null) {
            return;
        }
        double current = com.thepearlking.stevespace.universe.UniverseClock.get(server).warp();
        int now = 0;
        for (int i = 0; i < levels.length; i++) {
            if (levels[i] <= current + 1e-6) {
                now = i;
            }
        }
        if (wanted > now) {
            if (server.getTickCount() - lastStepUp < 20) {
                return; // turning it up one step a second
            }
            lastStepUp = server.getTickCount();
            wanted = now + 1;
        }
        warp(vessel, levels[wanted]);
    }

    private static int lastStepUp = -1000;

    private static void warp(VesselEntity vessel, int level) {
        var server = vessel.level().getServer();
        if (server == null) {
            return;
        }
        double current = com.thepearlking.stevespace.universe.UniverseClock.get(server).warp();
        if (Math.abs(current - level) > 1e-6) {
            ServerUniverse.setWarp(server, level);
        }
    }

    // ---------------------------------------------------------------------------------------
    // Getting to orbit
    // ---------------------------------------------------------------------------------------

    private Command climb(VesselEntity vessel, CelestialBody body, double mu) {
        Trajectory now = Trajectory.of(vessel, body, mu);
        if (now.apoapsisAltitude() >= targetAltitude) {
            phase = Phase.COAST;
            return new Command(now.prograde(), 0);
        }
        // Gravity turn: straight up at first, leaning further east as the air thins out.
        double edge = VesselTransfer.spaceAltitude(body);
        double progress = Math.max(0, Math.min(1, (now.altitude() - 60) / (edge * 0.75)));
        double lean = Math.toRadians(85) * Math.pow(progress, 0.6);
        DVec3 east = now.east();
        DVec3 up = now.up();
        DVec3 direction = up.scale(Math.cos(lean)).add(east.scale(Math.sin(lean))).normalize();
        return new Command(direction, 1);
    }

    private Command coast(VesselEntity vessel, CelestialBody body, double mu) {
        Trajectory now = Trajectory.of(vessel, body, mu);
        double floor = VesselTransfer.spaceAltitude(body) + 100;
        double reach = body.parent() == null ? Double.POSITIVE_INFINITY : ServerUniverse.system().sphereOfInfluence(body) * 0.5;
        // Heading somewhere silly (escaping, or a path far higher than the orbit we're after):
        // don't wait for a highest point that may never come.
        boolean tooFar = now.eccentricity() >= 1 || now.apoapsisRadius() > Math.min(reach, (body.radius() + targetAltitude) * 3);
        if (tooFar) {
            if (now.altitude() >= floor) {
                phase = Phase.CIRCULARIZE; // round it off right here
                return circularize(vessel, body, mu);
            }
            // Still in the air: take speed off until the path tops out at a sensible height.
            return new Command(now.retrograde(), 1);
        }
        if (now.verticalSpeed() < 0 && now.altitude() < floor) {
            // Past the top and sinking back into the air (a path that dips low on its way out):
            // push along the way it's going to lift the low point out again. Rounding the orbit
            // off down here can't be done, and handing back and forth would just let it fall.
            return new Command(now.prograde(), 1);
        }
        double burnTime = now.speedNeededForCircle(now.apoapsisRadius()) / Math.max(1e-3, vessel.maxAcceleration());
        if (now.timeToApoapsis() <= burnTime / 2 || now.verticalSpeed() < 0) {
            phase = Phase.CIRCULARIZE;
        } else if (now.altitude() >= floor && now.apoapsisAltitude() >= targetAltitude - 50 && now.timeToApoapsis() > burnTime + 30) {
            warpToward(vessel, now.timeToApoapsis() - burnTime);
        }
        // Keep the apoapsis up against drag while still in the air.
        double throttle = now.apoapsisAltitude() < targetAltitude - 50 ? 0.3 : 0;
        return new Command(now.prograde(), throttle);
    }

    /**
     * Makes the orbit round at the current height, from any path (falling, climbing or flying
     * off): burns toward exactly the velocity a circular orbit has here, sideways along the
     * horizon in the plane the ship is already moving in, until it matches.
     */
    private Command circularize(VesselEntity vessel, CelestialBody body, double mu) {
        Trajectory now = Trajectory.of(vessel, body, mu);
        double floor = VesselTransfer.spaceAltitude(body) + 100;
        if (now.altitude() < floor) {
            // Too low to stay up here: climb first, then round it off at the top (coasting there
            // lifts a path that's sinking back into the air).
            phase = now.apoapsisAltitude() >= floor ? Phase.COAST : Phase.CLIMB;
            if (phase == Phase.CLIMB && targetAltitude < floor + 500) {
                targetAltitude = floor + 500;
            }
            return new Command(now.prograde(), 0);
        }
        warp(vessel, 1);
        DVec3 r = now.state().position();
        DVec3 v = now.state().velocity();
        DVec3 rHat = r.normalize();
        DVec3 normal = r.cross(v);
        if (normal.length() < 1e-6 * r.length() * Math.max(1, v.length())) {
            normal = body.spinAxis(); // going straight up or down: pick the usual eastward orbit
        }
        DVec3 along = normal.normalize().cross(rHat).normalize();
        DVec3 wanted = along.scale(now.speedNeededForCircle(now.radius()));
        DVec3 dv = wanted.sub(v);
        double missing = dv.length();
        if (missing < 0.4) {
            phase = Phase.DONE;
            return new Command(now.prograde(), 0);
        }
        double throttle = Math.max(0.03, Math.min(1, missing / Math.max(1e-3, vessel.maxAcceleration())));
        return new Command(now.toWorld().apply(dv.scale(1 / missing)), throttle);
    }

    // ---------------------------------------------------------------------------------------
    // Coming back down
    // ---------------------------------------------------------------------------------------

    private Command deorbit(VesselEntity vessel, CelestialBody body, double mu) {
        Trajectory now = Trajectory.of(vessel, body, mu);
        // Never start down without the engines to stop at the bottom: stay in orbit and say so.
        double surfaceGravity = mu / (body.radius() * body.radius());
        if (vessel.maxAcceleration() < surfaceGravity * (body.hasAtmosphere() ? 1.1 : 1.5)) {
            phase = Phase.TOO_WEAK;
            return new Command(now.retrograde(), 0);
        }
        // Landing on a picked spot (first, while the orbit is still whole: halfway through turning
        // it, its lowest point dips for a while): tilt the orbit over it if need be, then wait (warping) for the pass that brings it down there.
        if (hasSite() && landingOn(body) && vessel.orbital() != null) {
            Command waiting = towardSite(vessel, body, mu, now);
            if (waiting != null) {
                return waiting;
            }
        }
        // Airless: come down steeply rather than skimming the ground at orbital speed.
        double target = body.hasAtmosphere() ? VesselTransfer.spaceAltitude(body) * 0.1 : -body.radius() * 0.25;
        // Flying away (on an escape path, or a fall that would climb much higher first): burn
        // against the motion until the ship can rise at most half again as high, then fall home.
        double energy = now.state().velocity().lengthSquared() / 2 - mu / now.radius();
        double highest = energy >= 0 ? Double.POSITIVE_INFINITY : -mu / (2 * energy) * (1 + now.eccentricity());
        double reach = body.parent() == null ? Double.POSITIVE_INFINITY : ServerUniverse.system().sphereOfInfluence(body) * 0.9;
        boolean climbing = now.verticalSpeed() > 0.5;
        if (energy >= 0 || climbing && (highest > reach || highest > now.radius() * 1.5)) {
            double speed = now.state().velocity().length();
            double throttle = Math.max(0.1, Math.min(1, speed / Math.max(0.01, vessel.maxAcceleration()) * 2));
            return new Command(now.retrograde(), throttle);
        }
        if (now.periapsisAltitude() <= target) {
            phase = Phase.ENTRY;
            return new Command(now.retrograde(), 0);
        }
        // Burn whichever way brings the lowest point down fastest from here: retrograde from a
        // round orbit, mostly toward the planet when arriving fast on a passing path.
        DVec3 lower = lowerPeriapsis(now.state(), mu);
        DVec3 direction = lower != null ? now.toWorld().apply(lower) : now.retrograde();
        double gap = now.periapsisAltitude() - target;
        double throttle = Math.max(0.1, Math.min(1, gap / Math.max(1, body.radius() * 0.05)));
        return new Command(direction, throttle);
    }

    /**
     * Before starting down to a picked spot: if no pass in the next few orbits goes near it (the
     * orbit isn't tilted enough to reach its latitude), turn the orbit's plane over it first (a
     * burn sideways); then wait for the best pass. Null once it's time to start down.
     */
    private Command towardSite(VesselEntity vessel, CelestialBody body, double mu, Trajectory now) {
        double t = vessel.orbital().time();
        StateVector state = vessel.orbital().state();
        // Waiting must never let it sink into the air: if the orbit dips into it (a turn can do
        // that), round it up first (and plan the pass again after).
        double safe = body.radius() + (body.hasAtmosphere() ? body.atmosphere().height() + 30 : 10);
        boolean turning = siteMiss > Math.max(60, body.radius() * 0.01); // (mid-turn its low point dips for a while: leave it)
        // Only the low point is lifted, to just clear the air (the cheapest burn), and only from up
        // where it can be: lower down it coasts up first.
        double lift = safe + 60;
        double r0 = state.position().length();
        boolean due = !Double.isNaN(deorbitAt) && t >= deorbitAt - 0.5;
        if (!turning && !due && now.orbit().eccentricity() < 1 && r0 > lift + 20
                && (siteLifting || now.orbit().periapsis() < safe)) {
            // (Once started, the burn goes on until the low point is all the way up, not just past the line.)
            DVec3 r = state.position().normalize();
            DVec3 along = state.velocity().sub(r.scale(state.velocity().dot(r)));
            DVec3 round = along.normalize().scale(Math.sqrt(2 * mu * lift / (r0 * (r0 + lift))));
            Command burn = burnToward(vessel, now, round.sub(state.velocity()));
            if (burn != null) {
                phase = Phase.DEORBIT;
                deorbitAt = Double.NaN;
                siteLifting = true;
                siteWhy = "lift pe " + (int) (now.orbit().periapsis() - body.radius());
                return burn;
            }
        }
        siteLifting = false;
        if (Double.isNaN(deorbitAt)) {
            deorbitAt = planDeorbit(vessel, body, state, t, mu);
            siteNormal = null; // a fresh turn is worked out from here if one's needed
            // Another, smaller turn if still well off (three at most); the rest is flown on the way down.
            if (siteTurns >= 3) {
                siteMiss = Math.min(siteMiss, Math.max(60, body.radius() * 0.01));
            }
        }
        double allowed = Math.max(60, body.radius() * 0.01);
        if (siteMiss > allowed) {
            if (siteNormal == null) {
                siteNormal = planePlan(body, state, t, mu);
                siteTurns++;
            }
            DVec3 normal = state.position().cross(state.velocity()).normalize();
            double off = Math.acos(Math.max(-1, Math.min(1, normal.dot(siteNormal))));
            if (off > Math.toRadians(0.5) && t < siteTurnAt - 1 && !siteBurning) {
                // Coast round to where the turn is cheapest.
                warpToward(vessel, siteTurnAt - t - 15);
                return new Command(now.retrograde(), 0);
            }
            if (off > Math.toRadians(0.5) || siteBurning) {
                // The velocity this orbit should have here: the same speed round, in the new plane
                // (and the burn goes on until it has it: partway through a turn it's slower).
                DVec3 r = state.position().normalize();
                double radial = state.velocity().dot(r);
                // (Its speed round as the turn began: partway through, it's lower.)
                if (!siteBurning) {
                    siteRound = state.velocity().sub(r.scale(radial)).length();
                }
                double round = siteRound;
                DVec3 wanted = siteNormal.cross(r).normalize().scale(round).add(r.scale(radial));
                Command burn = burnToward(vessel, now, wanted.sub(state.velocity()));
                if (burn != null) {
                    phase = Phase.DEORBIT;
                    siteBurning = true;
                    return burn;
                }
            }
            siteBurning = false;
            // Turned: look again for the best pass on the new orbit (next tick, once settled).
            deorbitAt = Double.NaN;
            siteWhy = "turned";
            return new Command(now.retrograde(), 0);
        }
        if (t < deorbitAt - 0.5) {
            warpToward(vessel, deorbitAt - t - 15);
            return new Command(now.retrograde(), 0);
        }
        return null;
    }

    /**
     * A burn for a velocity change (ecliptic frame, blocks per second), pointing first and firing
     * only once lined up (thrust while turning would push the wrong way), easing off as it's
     * nearly done; null once there's less than half a metre a second left to do.
     */
    private Command burnToward(VesselEntity vessel, Trajectory now, DVec3 change) {
        double left = change.length();
        if (left < 0.5) {
            return null;
        }
        warp(vessel, 1);
        DVec3 direction = now.toWorld().apply(change.scale(1 / left));
        double throttle = vessel.alignment(direction) < 0.995 ? 0 : Math.min(1, left / Math.max(0.01, vessel.maxAcceleration()));
        return new Command(direction, throttle);
    }

    /**
     * The cheapest turn that puts the orbit over the picked spot: tried from every point round the
     * next orbit (the turn needed depends a lot on where it's made), the one needing the smallest
     * turn wins. Sets {@link #siteTurnAt} (when to make it) and returns the new orbit's normal.
     */
    private DVec3 planePlan(CelestialBody body, StateVector state, double t, double mu) {
        var system = ServerUniverse.system();
        var orbit = Kepler.elementsFromState(state, mu, t);
        double period = orbit.period(mu);
        DVec3 oldNormal = state.position().cross(state.velocity()).normalize();
        double bestAngle = Double.POSITIVE_INFINITY;
        DVec3 best = oldNormal;
        double bestTime = t;
        int steps = 120;
        for (int k = 0; k < steps; k++) {
            double when = t + 10 + period * k / steps;
            StateVector at = orbit.stateAt(when, mu);
            DVec3 r = at.position().normalize();
            double rate = at.velocity().length() / at.position().length();
            double lead = 0;
            DVec3 normal = oldNormal;
            for (int i = 0; i < 6; i++) {
                DVec3 site = system.frameAt(body, when + lead).up(siteLat, siteLon);
                DVec3 n = r.cross(site);
                if (n.length() < 1e-6) {
                    break;
                }
                normal = n.normalize();
                if (normal.dot(oldNormal) < 0) {
                    normal = normal.scale(-1); // keep going round the same way
                }
                DVec3 ahead = normal.cross(r);
                double angle = Math.atan2(site.dot(ahead), site.dot(r));
                if (angle < 0) {
                    angle += 2 * Math.PI;
                }
                lead = angle / rate;
            }
            double turn = Math.acos(Math.max(-1, Math.min(1, normal.dot(oldNormal))));
            if (turn < bestAngle) {
                bestAngle = turn;
                best = normal;
                bestTime = when;
            }
        }
        siteTurnAt = bestTime;
        StevesSpace.LOGGER.info("Autopilot: turning the orbit {}° in {} s to pass over the landing site",
                String.format(java.util.Locale.ROOT, "%.1f", Math.toDegrees(bestAngle)), (int) (bestTime - t));
        return best;
    }

    /** True if this body is where the trip ends, landing (so a picked spot applies here). */
    private boolean landingOn(CelestialBody body) {
        return program == Program.LAND || body.id().equals(finalDestination) && landAtEnd;
    }

    /**
     * When to start down so the ship comes down on the picked spot: looks over the next several
     * orbits for the moment whose descent (a fixed part of an orbit later, the world having turned
     * meanwhile) ends closest to it.
     */
    private double planDeorbit(VesselEntity vessel, CelestialBody body, StateVector state, double t, double mu) {
        var system = ServerUniverse.system();
        var orbit = Kepler.elementsFromState(state, mu, t);
        double period = orbit.period(mu);
        if (!(period > 0) || Double.isInfinite(period)) {
            return t;
        }
        // Where there's no air: a short braking burn ends nearly under the ship.
        double lead = period * 0.04;
        PlanetGeometry geometry = new PlanetGeometry(body.radius());
        double sx = geometry.xForLongitude(siteLon);
        double sz = geometry.zForLatitude(siteLat);
        double width = 2 * Math.PI * body.radius();
        double best = t;
        double bestDistance = Double.POSITIVE_INFINITY;
        fallAngle = Double.NaN; // the fall through the air, worked out once for this orbit (below)
        fallVessel = vessel;
        int steps = 360 * 8;
        for (int i = 0; i < steps; i++) {
            double when = t + 20 + period * i / 360.0;
            SurfaceFrame.MapState map;
            if (body.hasAtmosphere()) {
                // Through air: where the fall that this deorbit burn starts really comes down.
                map = fallFrom(body, orbit.stateAt(when, mu), when, mu);
                if (map == null) {
                    continue;
                }
            } else {
                double down = when + lead;
                map = SurfaceFrame.toMap(system, body, orbit.stateAt(down, mu), down);
            }
            double dx = Math.abs(map.x() - sx) % width;
            dx = Math.min(dx, width - dx);
            double d = Math.hypot(dx, map.z() - sz);
            if (d < bestDistance) {
                bestDistance = d;
                best = when;
                predictedX = map.x();
                predictedZ = map.z();
            }
        }
        StevesSpace.LOGGER.info("Autopilot: landing site planned, starting down in {} s (passes within {} blocks)",
                (int) (best - t), (int) bestDistance);
        siteMiss = bestDistance;
        return best;
    }

    /** How far into the air (0 at the top, 1 at the ground) the ballistic path's end is taken as where the ship really comes down. */
    static double AIR_SHORTFALL = 1.0;

    /**
     * Where a ship starting down from {@code state} would land: the deorbit burn lowers the
     * orbit's low point to just inside the air; then it falls along that path to the top of the
     * air, and from there (the air slowing it) comes down part of the way it would have gone in
     * a vacuum. Null if the path doesn't come down.
     */
    private SurfaceFrame.MapState fallFrom(CelestialBody body, StateVector state, double when, double mu) {
        var system = ServerUniverse.system();
        DVec3 r = state.position();
        double r0 = r.length();
        DVec3 radialDir = r.scale(1 / r0);
        double radial = state.velocity().dot(radialDir);
        DVec3 along = state.velocity().sub(radialDir.scale(radial));
        if (along.length() < 1e-6) {
            return null;
        }
        double rp = body.radius() + VesselTransfer.spaceAltitude(body) * 0.1;
        double speed = Math.sqrt(2 * mu * rp / (r0 * (r0 + rp)));
        StateVector after = new StateVector(r, along.normalize().scale(speed).add(radialDir.scale(radial)));
        var fall = Kepler.elementsFromState(after, mu, when);
        double top = body.radius() + body.atmosphere().height();
        double fallPeriod = fall.period(mu);
        double step = Math.max(1, fallPeriod / 2000);
        double entry = Double.NaN;
        double ground = Double.NaN;
        for (double tt = when; tt < when + fallPeriod * 0.75; tt += step) {
            double rr = fall.stateAt(tt, mu).position().length();
            if (Double.isNaN(entry) && rr <= top) {
                entry = tt;
            }
            if (rr <= body.radius()) {
                ground = tt;
                break;
            }
        }
        if (Double.isNaN(entry)) {
            return null;
        }
        if (Double.isNaN(ground)) {
            ground = entry;
        }
        StateVector in = fall.stateAt(entry, mu);
        if (Double.isNaN(fallAngle)) {
            simulateFall(body, in, entry, mu);
        }
        if (Double.isNaN(fallAngle)) {
            double land = entry + (ground - entry) * AIR_SHORTFALL;
            return SurfaceFrame.toMap(system, body, fall.stateAt(land, mu), land);
        }
        // The same fall from this entry point: so far round, so long after.
        DVec3 normal = in.position().cross(in.velocity()).normalize();
        DVec3 u = in.position().normalize();
        DVec3 w = normal.cross(u);
        DVec3 down = u.scale(Math.cos(fallAngle)).add(w.scale(Math.sin(fallAngle))).scale(body.radius());
        return SurfaceFrame.toMap(system, body, new StateVector(down, in.velocity()), entry + fallTime);
    }

    /** How far round (radians, in the orbit's plane) and how long (s) the fall from the top of the air to the ground takes. */
    private double fallAngle = Double.NaN;
    private double fallTime;
    private VesselEntity fallVessel;

    /**
     * Flies the fall through the air the way the ship will (gravity, the air's drag on it, the air
     * turning with the planet), from where it reaches the top of the air to the ground.
     */
    private void simulateFall(CelestialBody body, StateVector entry, double t, double mu) {
        if (fallVessel == null || fallVessel.stats() == null || !body.hasAtmosphere()) {
            return;
        }
        var stats = fallVessel.stats();
        double mass = stats.dryMass() + fallVessel.propellant();
        double k = VesselEntity.DRAG_CONSTANT * SpaceConfig.ATMOSPHERIC_DRAG.get() * stats.crossSection() / mass;
        double scaleHeight = body.atmosphere().height() / 7.0;
        double thick = Math.min(3, body.atmosphere().density());
        DVec3 spin = ServerUniverse.system().frameAt(body, t).north().scale(2 * Math.PI / ServerUniverse.system().siderealPeriod(body));
        DVec3 r = entry.position();
        DVec3 v = entry.velocity();
        double dt = 0.1;
        double elapsed = 0;
        double thrust = Math.max(0.1, fallVessel.maxAcceleration());
        while (r.length() > body.radius() && elapsed < 3600) {
            double rr = r.length();
            DVec3 ground = v.sub(spin.cross(r));
            double speed = ground.length();
            // (As descend() reckons it low down: gravity less what the sideways speed carries.)
            DVec3 upNow = r.scale(1 / rr);
            double sidewaysNow = ground.sub(upNow.scale(ground.dot(upNow))).length();
            double felt = Math.max(0, mu / (rr * rr) - sidewaysNow * sidewaysNow / rr);
            double stopping = speed * speed / (2 * Math.max(0.1, thrust - felt));
            if (rr - body.radius() < stopping * 1.4 + 40 && ground.dot(r) < 0) {
                // The landing burn starts here (as descend() starts it) and brakes along the way it's
                // going: what's left of the sideways travel is a short stretch, not the rest of the fall.
                DVec3 up = r.normalize();
                DVec3 side = ground.sub(up.scale(ground.dot(up)));
                double sideways = side.length();
                double braking = sideways * speed / (2 * thrust * 0.6);
                if (sideways > 1e-6) {
                    r = r.add(side.scale(braking / sideways));
                }
                elapsed += speed / (thrust * 0.6);
                r = r.normalize().scale(body.radius());
                break;
            }
            double density = Math.exp(-Math.max(0, rr - body.radius()) / scaleHeight) * thick;
            DVec3 air = v.sub(spin.cross(r));
            DVec3 a = r.scale(-mu / (rr * rr * rr)).sub(air.scale(k * density * air.length()));
            v = v.add(a.scale(dt));
            r = r.add(v.scale(dt));
            elapsed += dt;
        }
        DVec3 u = entry.position().normalize();
        DVec3 normal = entry.position().cross(entry.velocity()).normalize();
        DVec3 w = normal.cross(u);
        fallAngle = Math.atan2(r.dot(w), r.dot(u));
        fallTime = elapsed;
        StevesSpace.LOGGER.info("Autopilot: the fall through the air goes {} blocks round in {} s",
                (int) (fallAngle * body.radius()), (int) elapsed);
    }

    /**
     * The sideways velocity (east, south; blocks per second) that would carry the ship to the
     * picked spot and stop it there, and how far away it is; null with no spot (or no fuel spare).
     */
    private double[] siteWant(VesselEntity vessel, CelestialBody body) {
        if (!hasSite() || !landingOn(body) || vessel.stats() == null) {
            return null;
        }
        if (vessel.propellant() < vessel.stats().propellantCapacity() * 0.08) {
            return null; // too little left to go looking: come down where it is
        }
        PlanetGeometry geometry = new PlanetGeometry(body.radius());
        double width = 2 * Math.PI * body.radius();
        double dx = geometry.xForLongitude(siteLon) - vessel.mapX();
        dx -= Math.round(dx / width) * width;
        double dz = geometry.zForLatitude(siteLat) - vessel.mapZ();
        double distance = Math.hypot(dx, dz);
        if (distance < 3) {
            return new double[]{0, 0, distance};
        }
        // Gently: the engines take a moment to turn and spool, so plan on only a little of their push.
        double accel = Math.max(0.1, vessel.maxAcceleration() * 0.1);
        double speed = Math.min(40, Math.sqrt(2 * accel * distance) * 0.8);
        return new double[]{dx / distance * speed, dz / distance * speed, distance};
    }

    /** Unit Δv direction (ecliptic frame) that lowers the periapsis fastest, or null. */
    private static DVec3 lowerPeriapsis(StateVector s, double mu) {
        double base = periapsis(s.position(), s.velocity(), mu);
        double h = 0.5;
        DVec3[] axes = {new DVec3(1, 0, 0), new DVec3(0, 1, 0), new DVec3(0, 0, 1)};
        double[] g = new double[3];
        for (int i = 0; i < 3; i++) {
            g[i] = (periapsis(s.position(), s.velocity().add(axes[i].scale(h)), mu) - base) / h;
        }
        DVec3 gradient = new DVec3(g[0], g[1], g[2]);
        return gradient.length() < 1e-9 ? null : gradient.scale(-1 / gradient.length());
    }

    private static double periapsis(DVec3 r, DVec3 v, double mu) {
        DVec3 hv = r.cross(v);
        double energy = v.dot(v) / 2 - mu / r.length();
        double e = Math.sqrt(Math.max(0, 1 + 2 * energy * hv.dot(hv) / (mu * mu)));
        return hv.dot(hv) / mu / (1 + e);
    }

    private Command descend(VesselEntity vessel, CelestialBody body, double mu) {
        Trajectory now = Trajectory.of(vessel, body, mu);
        // Powered descent where the air can't do the job: none at all, or so thin here that the
        // ship isn't actually coming down (skimming the top of Titan's haze); where it is falling
        // through thin upper air (Earth), drag does the work for free.
        boolean airless = !body.hasAtmosphere() || thinAir(body, now.altitude()) && now.verticalSpeed() > -3;
        if (vessel.orbital() != null && airless && now.altitude() < 2500 && hasSite() && landingOn(body)
                && phase != Phase.LANDING_BURN && phase != Phase.TOUCHDOWN) {
            // A picked spot: first get onto an orbit over it and wait for the pass that goes over it.
            Command waiting = towardSite(vessel, body, mu, now);
            if (waiting != null) {
                return waiting;
            }
        }
        if (vessel.orbital() != null && airless && now.altitude() < 2500) {
            // Low over an airless world, still in orbit mode: the same careful descent as below.
            DVec3 v = now.toWorld().apply(now.state().velocity());
            double ground = groundHeight(vessel) - 63;
            double height = Math.max(0, now.altitude() - vessel.stats().radius() - ground);
            // Moving sideways round the world holds you up a little: only the rest needs the engines.
            double sideways = v.x() * v.x() + v.z() * v.z();
            double gravity = Math.max(0, mu / (now.radius() * now.radius()) - sideways / now.radius());
            Command command = airlessDescent(vessel, height, v, gravity, null);
            if (command.throttle() < 1e-3 && v.y() < -1 && height > 200) {
                // Coasting down, engines off (on a small world that's minutes): warp, back to
                // normal well before the height where the braking will begin at this speed.
                double brake = Math.max(0.05, (Math.max(0.1, vessel.maxAcceleration()) - gravity) * 0.4);
                double brakeHeight = Math.pow(Math.max(0, -v.y() - 1.5), 2) / (2 * brake) + 40;
                warpToward(vessel, (height - brakeHeight) / -v.y() - 10);
            } else {
                warp(vessel, 1);
            }
            return command;
        }
        if (vessel.orbital() != null) {
            // Coming in fast with modest engines: start braking as early as the engines need. The
            // distance comes from the speed it will have reached at the ground (falling the rest of
            // the way) and the pull of gravity there, not from here, where both are still small.
            double speed = now.state().velocity().length();
            double r = now.radius();
            double surface = body.radius();
            double arrival = Math.sqrt(speed * speed + 2 * mu * Math.max(0, 1 / surface - 1 / r));
            double gravity = mu / (surface * surface);
            double available = Math.max(0.1, vessel.maxAcceleration() - gravity);
            double stoppingAtArrival = arrival * arrival / (2 * available);
            double stoppingNow = speed * speed / (2 * available);
            if (now.verticalSpeed() < 0 && now.altitude() < stoppingNow * 1.3 + 300) {
                phase = Phase.LANDING_BURN;
                warp(vessel, 1);
                double need = Math.min(1, (stoppingNow * 1.3 + 300) / Math.max(1, now.altitude()));
                return new Command(now.retrograde(), need);
            }
            // Time warp may run until a little before the point where braking will have to start.
            double brakeAltitude = stoppingAtArrival * 1.3 + 300;
            double toBraking = now.verticalSpeed() < 0 ? (now.altitude() - brakeAltitude) / -now.verticalSpeed() : Double.POSITIVE_INFINITY;
            // Still above the atmosphere: point retrograde and wait for entry (warping meanwhile).
            if (Double.isNaN(eventTime) || --cruiseCheck <= 0) {
                cruiseCheck = 100;
                var path = com.thepearlking.stevespace.universe.TrajectoryPredictor.predict(ServerUniverse.system(), body,
                        vessel.orbital().state(), vessel.orbital().time(), SpaceConfig.GRAVITY_SCALE.get(), 3 * 86400);
                eventTime = path.isEmpty() ? Double.NaN : path.get(0).endTime();
                if (!path.isEmpty() && path.get(0).end() == com.thepearlking.stevespace.universe.TrajectoryPredictor.End.ESCAPE) {
                    phase = Phase.DEORBIT; // this path leaves for good: stop and fall back first
                    eventTime = Double.NaN;
                    warp(vessel, 1);
                    return new Command(now.retrograde(), 0);
                }
            }
            double until = Double.isNaN(eventTime) ? toBraking : Math.min(toBraking, eventTime - vessel.orbital().time());
            if (!Double.isInfinite(until)) {
                warpToward(vessel, until - 10);
            }
            return new Command(now.retrograde(), 0);
        }
        eventTime = Double.NaN;
        double ground = groundHeight(vessel);
        double height = Math.max(0, vessel.mapY() - vessel.stats().radius() - ground);
        DVec3 v = vessel.surfaceVelocity();
        double speed = v.length();
        double gravity = mu / (now.radius() * now.radius());
        double available = Math.max(0.1, vessel.maxAcceleration() - gravity);
        double stopping = speed * speed / (2 * available);

        if (vessel.isGrounded()) {
            phase = Phase.DONE;
            return new Command(DVec3.Y, 0);
        }
        double[] want = siteWant(vessel, body);
        if (!body.hasAtmosphere() || body.atmosphere().density() < 0.1 || thinAir(body, height) && v.y() > -3
                || phase == Phase.LANDING_BURN && thinAir(body, height)
                || want != null && want[2] > 30 && height < 400 && speed < 150) {
            // (Also low down with a picked spot still off to one side: flown there under power.)
            if (want != null && !loggedArrival) {
                loggedArrival = true;
                PlanetGeometry geometry = new PlanetGeometry(body.radius());
                double width = 2 * Math.PI * body.radius();
                double px = predictedX - vessel.mapX();
                px -= Math.round(px / width) * width;
                double pz = predictedZ - vessel.mapZ();
                // Ahead of where it's going (+) or behind (-): along its ground track.
                double sx = geometry.xForLongitude(siteLon) - vessel.mapX();
                sx -= Math.round(sx / width) * width;
                double sz = geometry.zForLatitude(siteLat) - vessel.mapZ();
                double hv = Math.max(1e-6, Math.hypot(v.x(), v.z()));
                StevesSpace.LOGGER.info("Autopilot: down out of the fall {} blocks from the landing site ({} along the way it's going), {} from where it was predicted ({} along), height {}, speed {}",
                        (int) want[2], (int) ((sx * v.x() + sz * v.z()) / hv), (int) Math.hypot(px, pz), (int) ((px * v.x() + pz * v.z()) / hv),
                        (int) height, (int) speed);
            }
            double sideways = v.x() * v.x() + v.z() * v.z();
            return airlessDescent(vessel, height, v, Math.max(0, gravity - sideways / now.radius()), want);
        }
        if (speed > 8 && height > 20) {
            // Point the engines into the direction of travel to brake; light them when needed.
            DVec3 retro = v.scale(-1 / speed);
            // Near the ground (where the world has to load as we pass), shed sideways speed early.
            double sideways = Math.sqrt(v.x() * v.x() + v.z() * v.z());
            boolean skimming = sideways > 12 && !vessel.inMapMode();
            boolean burn = phase == Phase.LANDING_BURN || height < stopping * 1.4 + 40 || skimming;
            if (burn) {
                phase = Phase.LANDING_BURN;
            }
            double throttle = burn ? Math.min(1, (stopping * 1.4 + 40) / Math.max(1, height)) : 0;
            if (burn && want != null && sideways > 2) {
                Command guided = brakeOntoSite(vessel, body, v, height, gravity, sideways);
                if (guided != null) {
                    return guided;
                }
            }
            return new Command(retro, throttle);
        }
        // Final metres: upright, settle at a gentle sink rate.
        phase = Phase.TOUCHDOWN;
        double targetSink = -Math.max(2, Math.min(6, height * 0.2));
        double hover = gravity / Math.max(0.1, vessel.maxAcceleration());
        double throttle = Math.max(0, Math.min(1, hover * (1 + (targetSink - v.y()) * 0.5)));
        double wx = want == null ? 0 : want[0];
        double wz = want == null ? 0 : want[1];
        DVec3 lean = new DVec3((wx - v.x()) * 0.03, 1, (wz - v.z()) * 0.03).normalize();
        return new Command(lean, throttle);
    }

    /**
     * The braking burn shaped to end on the picked spot, in one go: as much sideways braking as
     * stops the ship over the spot (less if it would stop short: it keeps sliding, which is free),
     * the sideways drift off the line to the spot taken out as it goes, and enough upward push to
     * come down to a slow sink just above the ground. Null if the spot is behind (plain braking
     * then, and the last bit is flown).
     */
    private Command brakeOntoSite(VesselEntity vessel, CelestialBody body, DVec3 v, double height, double gravity, double sideways) {
        PlanetGeometry geometry = new PlanetGeometry(body.radius());
        double width = 2 * Math.PI * body.radius();
        double dx = geometry.xForLongitude(siteLon) - vessel.mapX();
        dx -= Math.round(dx / width) * width;
        double dz = geometry.zForLatitude(siteLat) - vessel.mapZ();
        double dist = Math.hypot(dx, dz);
        double ux = dx / Math.max(1e-6, dist);
        double uz = dz / Math.max(1e-6, dist);
        double toward = v.x() * ux + v.z() * uz; // speed toward the spot
        double across = -v.x() * uz + v.z() * ux; // speed off to the side of the line to it
        if (dist < 20 || toward < 2) {
            return null; // there, or past it: plain braking, and the last bit is flown
        }
        double max = Math.max(0.1, vessel.maxAcceleration());
        // Toward the spot: slow evenly to a stop on it (sliding on costs nothing).
        double alongBrake = toward * toward / (2 * dist);
        // Across: take the sideways drift out over a couple of seconds.
        double acrossBrake = across / 2.0;
        double ax = -ux * alongBrake + uz * acrossBrake;
        double az = -uz * alongBrake - ux * acrossBrake;
        // Down: keep falling, no faster than the engines can stop from (with some spare) by the
        // last 20 blocks. Never holding height: hovering burns fuel for nothing.
        double room = Math.max(0, height - 20);
        double sink = -Math.min(60, Math.sqrt(2 * 0.5 * Math.max(0.1, max - gravity) * room) + 3);
        double ay = Math.max(0, gravity + (sink - v.y()) * 0.8);
        // Stopping the fall always comes first: the sideways part only gets what's left.
        double side = Math.sqrt(ax * ax + az * az);
        double spare = Math.sqrt(Math.max(0, max * max - ay * ay));
        if (side > spare) {
            if (spare < 1e-6) {
                return null;
            }
            ax *= spare / side;
            az *= spare / side;
        }
        double total = Math.sqrt(ax * ax + ay * ay + az * az);
        double throttle = Math.min(1, total / max);
        return new Command(new DVec3(ax, ay, az).scale(1 / total), throttle);
    }

    /**
     * Too little air here to slow a ship down by itself (no atmosphere, a thin one like Mars', or
     * the top of a thick one, like high over Titan): land under power instead.
     */
    private static boolean thinAir(CelestialBody body, double altitude) {
        if (!body.hasAtmosphere()) {
            return true;
        }
        double scale = body.atmosphere().height() / 7.0;
        double density = Math.exp(-Math.max(0, altitude) / scale) * Math.min(3, body.atmosphere().density());
        return density < 0.05;
    }

    /**
     * Coming down where there's no air to help: the engines stay pointed up the whole way (no
     * diving under power, so never a last-moment flip), falling no faster than the engines could
     * stop from with half their strength spare, slowing to a walk for touchdown. Sideways speed is
     * taken off as it goes, but holding the descent always comes first.
     */
    private String lastDescent = "";
    /** How high (blocks) a ship flies across to a picked spot before coming straight down on it. */
    private static final double CRUISE_HEIGHT = 160;

    private Command airlessDescent(VesselEntity vessel, double height, DVec3 v, double gravity, double[] want) {
        double max = Math.max(0.1, vessel.maxAcceleration());
        double brake = Math.max(0.05, (max - gravity) * 0.4);
        // Looking a second and a half ahead: the engines take about a second to spool up.
        double ahead = Math.max(0, height + Math.min(0, v.y()) * 1.5 - 4);
        double target = -Math.min(50, Math.sqrt(2 * brake * ahead) + 1.5);
        if (height < 6) {
            target = -1.5;
        }
        // A picked spot still well off to one side: fly over to it at a safe height first (never
        // falling faster than a few metres a second meanwhile), and only then come straight down.
        boolean away = want != null && want[2] > 30;
        if (away) {
            target = height > CRUISE_HEIGHT ? Math.max(target, -4) : Math.min(6, (CRUISE_HEIGHT - height) * 0.2);
        }
        phase = height < 25 ? Phase.TOUCHDOWN : Phase.LANDING_BURN;
        double wantX = want == null ? 0 : want[0];
        double wantZ = want == null ? 0 : want[1];
        double sidewaysNow = Math.sqrt(v.x() * v.x() + v.z() * v.z());
        // On a world with hardly any pull (Charon, an asteroid) falling would take ages: far above
        // the ground, push gently downward to get going, at most 15 m/s. It turns back upright
        // while coasting, long before the braking starts.
        double dive = -Math.min(15, -target * 0.3);
        if (!away && height > 300 && sidewaysNow < 3 && v.y() > dive + 1 && gravity < 1.0) {
            DVec3 down = new DVec3(-v.x() * 0.05, -1, -v.z() * 0.05).normalize();
            double push = Math.min(max * 0.5, (v.y() - dive) * 1.0);
            lastDescent = String.format(java.util.Locale.ROOT, "h=%.0f diving to %.1f v.y=%.1f", height, dive, v.y());
            return new Command(down, Math.min(1, push / max));
        }
        // Vertical: hold gravity plus close the gap to the target speed quickly.
        double up = gravity + (target - v.y()) * 2.0;
        up = Math.max(0, Math.min(max, up));
        // Sideways: whatever strength is left, against the sideways motion (or toward the picked spot).
        double ex = v.x() - wantX;
        double ez = v.z() - wantZ;
        double sideways = Math.sqrt(ex * ex + ez * ez);
        double left = Math.sqrt(Math.max(0, max * max - up * up));
        double side = Math.min(left, sideways * (want != null ? 0.3 : 0.6));
        double ax = sideways > 1e-3 ? -ex / sideways * side : 0;
        double az = sideways > 1e-3 ? -ez / sideways * side : 0;
        double total = Math.sqrt(ax * ax + up * up + az * az);
        if (total < 1e-4) {
            return new Command(DVec3.Y, 0); // falling slower than safe: let it fall, engines ready, pointing up
        }
        lastDescent = String.format(java.util.Locale.ROOT, "h=%.0f target=%.1f v=(%.1f,%.1f,%.1f) g=%.3f max=%.2f up=%.2f side=%.2f",
                height, target, v.x(), v.y(), v.z(), gravity, max, up, side);
        DVec3 direction = new DVec3(ax, up, az).scale(1 / total);
        // Low down it stays nearly upright (no big turn near the ground); high up, with nothing to
        // hold back, it may lie over to take the sideways drift off without lifting itself up.
        double floor = height < 150 ? 0.75 : (up > 0.05 * max ? 0.3 : -1);
        if (direction.y() < floor) {
            double flat = Math.sqrt(Math.max(1e-9, direction.x() * direction.x() + direction.z() * direction.z()));
            double side2 = Math.sqrt(1 - floor * floor);
            direction = new DVec3(direction.x() / flat * side2, floor, direction.z() / flat * side2);
            // Only as much push as the upward part needs: never shove the ship up by tilting.
            total = Math.min(total, up / floor + 1e-3);
        }
        return new Command(direction, Math.min(1, total / max));
    }

    private static double groundHeight(VesselEntity vessel) {
        int x = (int) Math.floor(vessel.mapX());
        int z = (int) Math.floor(vessel.mapZ());
        if (vessel.orbital() != null) {
            // In orbit mode: the point on the map straight below.
            var o = vessel.orbital();
            var map = com.thepearlking.stevespace.universe.SurfaceFrame.toMap(ServerUniverse.system(), o.body(), o.state(), o.time());
            x = (int) Math.floor(map.x());
            z = (int) Math.floor(map.z());
        }
        if (!vessel.inMapMode() && vessel.level().hasChunk(x >> 4, z >> 4)) {
            return vessel.level().getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        }
        // The blocks below aren't there yet (high up, or still loading): ask the world's terrain
        // what height the ground will be. Never assume sea level: other worlds' ground is higher.
        CelestialBody body = vessel.flightBody();
        var terrain = body == null ? null : com.thepearlking.stevespace.world.gen.PlanetBodies.terrain(body.id());
        if (terrain == null) {
            return 63;
        }
        var sample = new com.thepearlking.stevespace.world.planet.TerrainSample();
        double highest = 63;
        // The highest point near where it will come down (it drifts a little on the way).
        for (int dx = -24; dx <= 24; dx += 12) {
            for (int dz = -24; dz <= 24; dz += 12) {
                terrain.sample(x + dx, z + dz, sample);
                highest = Math.max(highest, sample.height);
            }
        }
        return highest + 2;
    }

    // ---------------------------------------------------------------------------------------

    /**
     * The vessel's trajectory, worked out the same way over a surface and in space, expressed in
     * the vessel's world axes.
     */
    record Trajectory(StateVector state, OrbitalElements orbit, CelestialBody body, double mu,
                      DVec3 east, DVec3 up, java.util.function.Function<DVec3, DVec3> toWorld) {

        static Trajectory of(VesselEntity vessel, CelestialBody body, double mu) {
            var system = ServerUniverse.system();
            if (vessel.orbital() != null) {
                OrbitalFlight o = vessel.orbital();
                StateVector s = o.state();
                OrbitalElements el = com.thepearlking.stevespace.universe.Kepler.elementsFromState(s, mu, o.time());
                java.util.function.Function<DVec3, DVec3> toWorld =
                        d -> new DVec3(d.dot(o.axisX()), d.dot(o.axisY()), d.dot(o.axisZ()));
                DVec3 upInertial = s.position().normalize();
                DVec3 eastInertial = body.spinAxis().cross(upInertial).normalize();
                return new Trajectory(s, el, body, mu, toWorld.apply(eastInertial), toWorld.apply(upInertial), toWorld);
            }
            double t = ServerUniverse.time(vessel.level().getServer());
            DVec3 v = vessel.surfaceVelocity();
            StateVector s = com.thepearlking.stevespace.universe.SurfaceFrame.toOrbit(system, body, vessel.mapX(), vessel.mapY(),
                    vessel.mapZ(), v.x(), v.y(), v.z(), t);
            var local = com.thepearlking.stevespace.universe.SurfaceFrame.local(system, body, vessel.mapX(), vessel.mapY(), vessel.mapZ(), t);
            OrbitalElements el = com.thepearlking.stevespace.universe.Kepler.elementsFromState(s, mu, t);
            java.util.function.Function<DVec3, DVec3> toWorld =
                    d -> new DVec3(d.dot(local.east()), d.dot(local.up()), d.dot(local.south()));
            return new Trajectory(s, el, body, mu, new DVec3(1, 0, 0), new DVec3(0, 1, 0), toWorld);
        }

        double radius() {
            return state.position().length();
        }

        double altitude() {
            return radius() - body.radius();
        }

        double eccentricity() {
            return orbit.eccentricity();
        }

        double apoapsisRadius() {
            return orbit.eccentricity() < 1 ? orbit.apoapsis() : Double.POSITIVE_INFINITY;
        }

        double apoapsisAltitude() {
            return apoapsisRadius() - body.radius();
        }

        double periapsisAltitude() {
            return orbit.periapsis() - body.radius();
        }

        double verticalSpeed() {
            return state.velocity().dot(state.position()) / radius();
        }

        double horizontalSpeed() {
            DVec3 v = state.velocity();
            double vertical = verticalSpeed();
            return Math.sqrt(Math.max(0, v.lengthSquared() - vertical * vertical));
        }

        double speedNeededForCircle(double r) {
            return Math.sqrt(mu / r);
        }

        /** Seconds until apoapsis (0 if already past it). */
        double timeToApoapsis() {
            if (orbit.eccentricity() >= 1) {
                return Double.POSITIVE_INFINITY;
            }
            double n = orbit.meanMotion(mu);
            double m = com.thepearlking.stevespace.universe.Kepler.wrapAngle(orbit.meanAnomalyAtEpoch());
            double toApo = Math.PI - m;
            return toApo < 0 ? 0 : toApo / n;
        }

        /** Seconds until periapsis. */
        double timeToPeriapsis() {
            if (orbit.eccentricity() >= 1) {
                double m = orbit.meanAnomalyAtEpoch();
                return m < 0 ? -m / orbit.meanMotion(mu) : 0;
            }
            double n = orbit.meanMotion(mu);
            double m = com.thepearlking.stevespace.universe.Kepler.wrapAngle(orbit.meanAnomalyAtEpoch());
            return (2 * Math.PI - m) / n;
        }

        DVec3 prograde() {
            return toWorld.apply(state.velocity().normalize());
        }

        DVec3 retrograde() {
            return prograde().scale(-1);
        }

        DVec3 horizontalPrograde() {
            DVec3 r = state.position().normalize();
            DVec3 v = state.velocity();
            DVec3 horizontal = v.sub(r.scale(v.dot(r)));
            return toWorld.apply(horizontal.length() < 1e-6 ? state.velocity().normalize() : horizontal.normalize());
        }
    }
}
