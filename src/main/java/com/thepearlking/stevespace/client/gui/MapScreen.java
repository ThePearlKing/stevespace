package com.thepearlking.stevespace.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.thepearlking.stevespace.client.ClientUniverse;
import com.thepearlking.stevespace.client.FlightKeys;
import com.thepearlking.stevespace.client.planet.PlanetMaps;
import com.thepearlking.stevespace.client.space.ClientSpace;
import com.thepearlking.stevespace.config.SpaceConfig;
import com.thepearlking.stevespace.universe.CelestialBody;
import com.thepearlking.stevespace.universe.DVec3;
import com.thepearlking.stevespace.universe.Kepler;
import com.thepearlking.stevespace.universe.OrbitalElements;
import com.thepearlking.stevespace.universe.SolarSystem;
import com.thepearlking.stevespace.universe.StateVector;
import com.thepearlking.stevespace.universe.SurfaceFrame;
import com.thepearlking.stevespace.universe.TrajectoryPredictor;
import com.thepearlking.stevespace.vessel.VesselEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * The map: the planet you're at, its moons, and where your vessel is going. Drag to turn it,
 * scroll to zoom, Tab to look at another body.
 */
public class MapScreen extends Screen {

    private static final int BACKGROUND = 0xFF04070A;
    private static final int PANEL = 0xD00B0F0D;
    private static final int PANEL_EDGE = 0xFF2B3236;
    private static final int GREEN = 0xFF66D98F;
    private static final int GREEN_DIM = 0xFF2F6E47;
    private static final int LABEL = 0xFF93A59A;
    private static final int TEXT = 0xFFD7E3DA;
    private static final int AMBER = 0xFFE6AD45;
    private static final int[] SEGMENT_COLORS = {0xFF66D98F, 0xFFE6AD45, 0xFF6FB7E8};

    /** Remembered between openings, like a real map you left turned a certain way. */
    private static double yaw = Math.toRadians(20);
    private static double pitch = Math.toRadians(55);
    /** Where a drag is taking the view; the view eases toward it each frame. */
    private static double targetYaw = yaw;
    private static double targetPitch = pitch;
    /** Centred on the ship itself rather than the body it is at. */
    private boolean shipFocus;
    private static String focusId;
    private static double zoom = -1;
    /** The view as it was when the map was last closed (it opens the same way next time). */
    private static boolean remembered;
    private static boolean rememberedShip;

    private CelestialBody focus;
    private List<TrajectoryPredictor.Segment> trajectory = List.of();
    private CelestialBody vesselBody;
    private StateVector vesselState;
    /** Where the player stands, when not flying: the body and the spot (relative to its centre). */
    private CelestialBody selfBody;
    private DVec3 selfPosition;
    /** Keep the view over the player as the planet turns, until they drag it themselves. */
    private boolean followSelf;
    /** Looking at yourself on a world (the view centres on you, not the world's middle). */
    private boolean selfFocus;
    private int recomputeIn;

    // View axes for this frame (ecliptic frame), and the screen centre.
    private DVec3 right;
    private DVec3 up;
    /** Perspective camera: field of view, focal length in pixels, distance from the focus, position. */
    private static final double FOV = 50;
    private double focal = 1;
    private double distance = 1;
    private DVec3 camera = DVec3.ZERO;
    private DVec3 forward;
    private float cx;
    private float cy;

    /** A vessel being followed from Mission Control: its state relative to its body at a time. */
    public record Tracked(String name, CelestialBody body, StateVector state, double time, boolean landed) {
    }

    private final Tracked tracked;

    // ---- Maneuver node (kept while the map is closed) ----
    private static double nodeTime = Double.NaN;
    private static double nodePrograde;
    private static double nodeNormal;
    private static double nodeRadial;
    private static int stepIndex = 1;
    private static final double[] STEPS = {1, 10, 100};
    private static final int NODE_COLOR = 0xFF5AA0FF;
    private List<TrajectoryPredictor.Segment> nodePath = List.of();
    /** Where the node is on the orbit (relative to the vessel's body), and the burn direction (inertial). */
    private DVec3 nodePoint;
    private DVec3 nodeDirection;
    private double pressX;
    private double pressY;
    private boolean pressHandled;
    /** A world picked by clicking it: its name, the trip's cost, and Orbit / Land. */
    private CelestialBody selected;
    private java.util.concurrent.CompletableFuture<com.thepearlking.stevespace.vessel.RouteEstimate.Estimate> selectedEstimate;
    private static final int PICK_W = 170;
    private int[] orbitButton;
    private int[] landButton;
    private int[] spotButton;
    /** Picking a landing spot: the next click on the world marks it. */
    private boolean spotPicking;
    private CelestialBody spotBody;
    private double spotLat = Double.NaN;
    private double spotLon = Double.NaN;

    /** The map, ready to pick where to fly: click a world, then Orbit or Land. */
    public static MapScreen forDestination() {
        MapScreen screen = new MapScreen();
        screen.picking = true;
        return screen;
    }

    /** For tests: pick a world as if clicked. */
    public void testSelect(String bodyId) {
        CelestialBody body = ClientUniverse.system().body(bodyId);
        if (body != null) {
            select(body);
        }
    }

    /** For tests: look at a world, as a right-click on it would. */
    public void testFocus(String bodyId) {
        CelestialBody body = ClientUniverse.system().body(bodyId);
        if (body != null) {
            focus = body;
            focusId = body.id();
            shipFocus = false;
            followSelf = false;
            selfFocus = false;
            zoom = -1;
            fitZoom();
        }
    }

    /** For tests: press the landing spot button. */
    public void testPressSpot() {
        if (spotButton != null) {
            testClick(spotButton[0] + 2, spotButton[1] + 2);
        }
    }

    /** For tests: a left click (press and release) at a point. */
    public void testClick(double x, double y) {
        mouseClicked(x, y, 0);
        mouseReleased(x, y, 0);
    }

    private boolean picking;
    /** The normal map's view, put back when the destination picker closes. */
    private Object[] savedView;
    private final List<net.minecraft.client.gui.components.Button> nodeButtons = new ArrayList<>();
    private final List<net.minecraft.client.gui.components.Button> sasButtons = new ArrayList<>();
    private net.minecraft.client.gui.components.Button stepButton;

    public MapScreen() {
        this(null);
    }

    public MapScreen(Tracked tracked) {
        super(Component.translatable("screen.stevespace.map"));
        this.tracked = tracked;
        if (tracked != null) {
            focus = tracked.body();
        }
    }

    @Override
    protected void init() {
        SolarSystem system = ClientUniverse.system();
        if (focusId != null && system.body(focusId) != null && focus == null) {
            focus = system.body(focusId);
        }
        updateVessel();
        if (picking) {
            // Picking where to fly: always the whole solar system round the Sun, whatever the
            // normal map was last showing (and that view comes back untouched afterwards).
            if (savedView == null) {
                savedView = new Object[]{yaw, pitch, targetYaw, targetPitch, focusId, zoom};
                focus = system.sun();
                focusId = focus.id();
                shipFocus = false;
                followSelf = false;
                selfFocus = false;
                pitch = targetPitch = Math.toRadians(55);
                zoom = Math.min(width, height) * 0.42 / (1.8 * SolarSystem.AU);
            }
        } else if (tracked == null && vesselBody != null && vesselState != null) {
            // In a rocket out there: always open on it (the angle as you left it).
            focus = vesselBody;
            focusId = focus.id();
            shipFocus = true;
            selfFocus = false;
        selfFocus = false;
            followSelf = false;
            if (!rememberedShip || zoom <= 0) {
                zoom = Math.min(width, height) * 0.42 / 2000;
            }
        } else if (tracked == null && selfPosition != null) {
            // On a world (on foot, landed, or flying low): open on you, close in.
            focusSelf();
        }
        if (focus == null) {
            focus = vesselBody != null ? vesselBody : ClientUniverse.currentBody();
        }
        if (focus == null) {
            focus = system.earth();
        }
        if (zoom <= 0) {
            fitZoom();
        }
        addFlightWidgets();
        addWarpWidgets();
    }

    // ---------------------------------------------------------------------------------------
    // Time warp, top centre like KSP: slower / faster, the rate, and who set it
    // ---------------------------------------------------------------------------------------

    private static final int WARP_W = 196;

    private void addWarpWidgets() {
        int x = width / 2 - WARP_W / 2;
        addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.literal("\u25C0\u25C0"),
                b -> requestWarp(-1)).bounds(x + 4, 6, 22, 18).build());
        addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.literal("\u25B6\u25B6"),
                b -> requestWarp(1)).bounds(x + WARP_W - 26, 6, 22, 18).build());
    }

    private static void requestWarp(int step) {
        com.thepearlking.stevespace.network.SpaceNetwork.sendToServer(new com.thepearlking.stevespace.network.WarpRequestC2S(step));
    }

    private void drawWarp(GuiGraphics g) {
        int x = width / 2 - WARP_W / 2;
        int y = 4;
        g.fill(x, y, x + WARP_W, y + 34, PANEL);
        g.renderOutline(x, y, WARP_W, 34, PANEL_EDGE);
        double warp = ClientUniverse.warp();
        int[] levels = com.thepearlking.stevespace.universe.TimeWarp.LEVELS;
        // One pip per warp step, lit up to the current one.
        int pips = levels.length;
        int pipW = 12;
        int px = width / 2 - pips * (pipW + 2) / 2;
        for (int i = 0; i < pips; i++) {
            boolean on = levels[i] <= warp + 1e-6;
            int left = px + i * (pipW + 2);
            for (int k = 0; k < 5; k++) { // a small right-pointing triangle
                g.fill(left + k, y + 6 + k, left + k + 1, y + 16 - k, on ? (i == 0 ? GREEN : AMBER) : 0x40FFFFFF);
            }
        }
        VesselEntity ship = minecraft.player == null ? null : VesselEntity.shipOf(minecraft.player);
        boolean auto = ship != null && !ship.autopilotStatus().isEmpty() && warp > 1;
        String rate = warp > 1 ? String.format(Locale.ROOT, "%,d\u00D7", (long) warp) : tr("screen.stevespace.map.warp.normal");
        String who = auto ? " \u00B7 " + tr("screen.stevespace.map.warp.auto") : "";
        String text = rate + who;
        g.drawString(font, text, width / 2 - font.width(text) / 2, y + 22, auto ? 0xFF6FB7E8 : warp > 1 ? AMBER : TEXT, false);
    }

    /** The vessel the player pilots, or null. */
    /** True if you can send a ship somewhere: flying one, or sitting in the pilot seat of one standing on the ground. */
    private boolean canFly() {
        if (piloted() != null) {
            return true;
        }
        Minecraft mc = Minecraft.getInstance();
        return tracked == null && mc.player != null && mc.player.getVehicle() instanceof com.thepearlking.stevespace.block.SeatEntity seat
                && mc.level != null && mc.level.getBlockState(seat.blockPosition()).getBlock() instanceof com.thepearlking.stevespace.block.PilotSeatBlock;
    }

    private VesselEntity piloted() {
        Minecraft mc = Minecraft.getInstance();
        return tracked == null && mc.player != null && VesselEntity.shipOf(mc.player) instanceof VesselEntity v && v.pilot() == mc.player
                && v.stats() != null ? v : null;
    }

    private int nodeX() {
        return width - 178;
    }

    private int navX() {
        return width / 2;
    }

    private int navY() {
        return height - 66;
    }

    private void addFlightWidgets() {
        nodeButtons.clear();
        sasButtons.clear();
        int x0 = nodeX();
        int y0 = 8;
        String[] axes = {"prograde", "normal", "radial"};
        for (int i = 0; i < 3; i++) {
            final int axis = i;
            int y = y0 + 62 + i * 16;
            nodeButtons.add(addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.literal("\u2212"),
                    b -> nudge(axis, -1)).bounds(x0 + 72, y - 3, 16, 14).build()));
            nodeButtons.add(addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.literal("+"),
                    b -> nudge(axis, 1)).bounds(x0 + 148, y - 3, 16, 14).build()));
        }
        int y = y0 + 112;
        nodeButtons.add(addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.literal("\u25C0"),
                b -> moveNode(-1)).bounds(x0 + 6, y, 20, 14).build()));
        nodeButtons.add(addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.literal("\u25B6"),
                b -> moveNode(1)).bounds(x0 + 28, y, 20, 14).build()));
        stepButton = addRenderableWidget(net.minecraft.client.gui.components.Button.builder(stepLabel(), b -> {
            stepIndex = (stepIndex + 1) % STEPS.length;
            b.setMessage(stepLabel());
        }).bounds(x0 + 52, y, 112, 14).build());
        nodeButtons.add(stepButton);
        nodeButtons.add(addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                Component.translatable("screen.stevespace.map.node.delete"), b -> {
                    nodeTime = Double.NaN;
                    nodePath = List.of();
                    sendNode(false);
                }).bounds(x0 + 6, y + 18, 78, 16).build()));
        nodeButtons.add(addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                Component.translatable("screen.stevespace.map.node.execute"), b -> sendNode(true)).bounds(x0 + 86, y + 18, 78, 16).build()));
        // SAS, beside the navball.
        VesselEntity.Sas[] modes = {VesselEntity.Sas.HOLD, VesselEntity.Sas.OFF, VesselEntity.Sas.PROGRADE, VesselEntity.Sas.RETROGRADE,
                VesselEntity.Sas.NORMAL, VesselEntity.Sas.ANTINORMAL, VesselEntity.Sas.RADIAL_OUT, VesselEntity.Sas.RADIAL_IN,
                VesselEntity.Sas.MANEUVER};
        int sx = navX() - 56 - 104;
        int sy = navY() - 46;
        for (int i = 0; i < modes.length; i++) {
            VesselEntity.Sas mode = modes[i];
            sasButtons.add(addRenderableWidget(net.minecraft.client.gui.components.Button.builder(sasLabel(mode, false),
                    b -> com.thepearlking.stevespace.network.SpaceNetwork.sendToServer(new com.thepearlking.stevespace.network.SasC2S(mode.ordinal())))
                    .bounds(sx + (i % 2) * 52, sy + (i / 2) * 18, 50, 16).build()));
        }
        updateFlightWidgets();
    }

    private static Component stepLabel() {
        return Component.translatable("screen.stevespace.map.node.step", FlightText.speed(STEPS[stepIndex]));
    }

    private static Component sasLabel(VesselEntity.Sas mode, boolean on) {
        Component text = Component.translatable("screen.stevespace.map.sas." + mode.name().toLowerCase(Locale.ROOT));
        return on ? Component.literal("\u25B8").append(text).withStyle(net.minecraft.ChatFormatting.GREEN) : text;
    }

    private static final VesselEntity.Sas[] SAS_ORDER = {VesselEntity.Sas.HOLD, VesselEntity.Sas.OFF, VesselEntity.Sas.PROGRADE,
            VesselEntity.Sas.RETROGRADE, VesselEntity.Sas.NORMAL, VesselEntity.Sas.ANTINORMAL, VesselEntity.Sas.RADIAL_OUT,
            VesselEntity.Sas.RADIAL_IN, VesselEntity.Sas.MANEUVER};

    private void updateFlightWidgets() {
        VesselEntity vessel = piloted();
        boolean flying = vessel != null && !vessel.isGrounded();
        boolean orbit = flying && ClientSpace.orbitActive();
        boolean node = orbit && hasNode();
        for (var b : nodeButtons) {
            b.visible = node;
        }
        for (int i = 0; i < sasButtons.size(); i++) {
            VesselEntity.Sas mode = SAS_ORDER[i];
            var b = sasButtons.get(i);
            boolean needsOrbit = mode != VesselEntity.Sas.HOLD && mode != VesselEntity.Sas.OFF && mode != VesselEntity.Sas.PROGRADE
                    && mode != VesselEntity.Sas.RETROGRADE;
            b.visible = flying;
            b.active = !needsOrbit || orbit && (mode != VesselEntity.Sas.MANEUVER || hasNode());
            b.setMessage(sasLabel(mode, vessel != null && vessel.sas() == mode));
        }
    }

    /** For the automated client test: a node this far ahead with this burn. */
    public static void testNode(double inSeconds, double prograde, double normal, double radial) {
        nodeTime = ClientUniverse.time() + inSeconds;
        nodePrograde = prograde;
        nodeNormal = normal;
        nodeRadial = radial;
    }

    private static boolean hasNode() {
        return !Double.isNaN(nodeTime);
    }

    private void nudge(int axis, int sign) {
        double step = STEPS[stepIndex] * sign * (hasShiftDown() ? 0.1 : 1);
        switch (axis) {
            case 0 -> nodePrograde += step;
            case 1 -> nodeNormal += step;
            default -> nodeRadial += step;
        }
        sendNode(false);
        recomputeIn = 0;
    }

    /** Slides the node along the orbit (a 72nd of the way round). */
    private void moveNode(int sign) {
        if (!hasNode() || trajectory.isEmpty()) {
            return;
        }
        OrbitalElements orbit = trajectory.get(0).orbit();
        double mu = trajectory.get(0).body().mu() * gravityScale();
        double step = orbit.eccentricity() < 1 ? orbit.period(mu) / 72 : 60;
        nodeTime = Math.max(ClientUniverse.time() + 5, nodeTime + sign * step);
        sendNode(false);
        recomputeIn = 0;
    }

    private void sendNode(boolean execute) {
        if (piloted() == null) {
            return;
        }
        com.thepearlking.stevespace.network.SpaceNetwork.sendToServer(new com.thepearlking.stevespace.network.ManeuverC2S(
                hasNode() ? nodeTime : 0, hasNode() ? nodePrograde : 0, hasNode() ? nodeNormal : 0, hasNode() ? nodeRadial : 0, execute && hasNode()));
    }

    private void fitZoom() {
        double extent = focus.radius() * 2.5;
        if (vesselBody == focus && !trajectory.isEmpty()) {
            OrbitalElements orbit = trajectory.get(0).orbit();
            if (orbit.eccentricity() < 1) {
                extent = Math.max(extent, orbit.apoapsis() * 1.15);
            }
        }
        zoom = Math.min(width, height) * 0.42 / extent;
    }

    /** Where the vessel the player is in is, and where it's going. */
    private void updateVessel() {
        Minecraft mc = Minecraft.getInstance();
        SolarSystem system = ClientUniverse.system();
        double t = ClientUniverse.time();
        vesselBody = null;
        vesselState = null;
        selfBody = null;
        selfPosition = null;
        trajectory = List.of();
        if (mc.player != null && tracked == null && !com.thepearlking.stevespace.world.SpaceDimension.isSpace(mc.player.level())) {
            CelestialBody here = ClientUniverse.currentBody();
            if (here != null) {
                selfBody = here;
                selfPosition = SurfaceFrame.toOrbit(system, here, mc.player.getX(), mc.player.getY(), mc.player.getZ(), 0, 0, 0, t).position();
            }
        }
        if (tracked != null) {
            // Followed from Mission Control: carried along its orbit from the last report.
            double mu = tracked.body().mu() * gravityScale();
            vesselBody = tracked.body();
            if (tracked.landed()) {
                // On the ground: just where it stands (no orbit to draw).
                vesselState = tracked.state();
                return;
            }
            vesselState = Kepler.elementsFromState(tracked.state(), mu, tracked.time()).stateAt(t, mu);
            trajectory = TrajectoryPredictor.predict(system, vesselBody, vesselState, t, gravityScale(), horizon(vesselBody, vesselState));
            return;
        }
        if (mc.player == null || !(VesselEntity.shipOf(mc.player) instanceof VesselEntity vessel)) {
            // In space with no flying ship to hand (aboard or beside a parked one, or out on a
            // spacewalk): where you are is that cell's orbit.
            var cell = ClientSpace.cellOrbit();
            if (mc.player != null && cell != null && com.thepearlking.stevespace.world.SpaceDimension.isSpace(mc.player.level())) {
                CelestialBody body = system.body(cell.body());
                if (body != null) {
                    double mu = body.mu() * gravityScale();
                    double[] st = cell.state();
                    vesselBody = body;
                    vesselState = Kepler.elementsFromState(new com.thepearlking.stevespace.universe.StateVector(
                            new DVec3(st[0], st[1], st[2]), new DVec3(st[3], st[4], st[5])), mu, cell.time()).stateAt(t, mu);
                    trajectory = TrajectoryPredictor.predict(system, vesselBody, vesselState, t, gravityScale(), horizon(vesselBody, vesselState));
                }
            }
            return;
        }
        if (ClientSpace.active()) {
            // In orbit or high in the air: the server's own record of where it is.
            vesselBody = ClientSpace.body();
            vesselState = ClientSpace.stateAt(t);
        } else {
            CelestialBody body = ClientUniverse.currentBody();
            if (body != null && !vessel.isGrounded()) {
                Vector3f v = vessel.velocity();
                vesselBody = body;
                vesselState = SurfaceFrame.toOrbit(system, body, vessel.getX(), vessel.getY(), vessel.getZ(), v.x, v.y, v.z, t);
            }
        }
        if (vesselBody != null && vesselState != null) {
            trajectory = TrajectoryPredictor.predict(system, vesselBody, vesselState, t, gravityScale(), horizon(vesselBody, vesselState));
        }
        updateNode(system, t);
    }

    /** Where the planned node is and where the ship goes after it. */
    private void updateNode(SolarSystem system, double t) {
        nodePath = List.of();
        nodePoint = null;
        nodeDirection = null;
        if (!hasNode()) {
            return;
        }
        if (t > nodeTime + 120 || vesselBody == null || vesselState == null || !ClientSpace.orbitActive()) {
            if (t > nodeTime + 120) {
                nodeTime = Double.NaN; // long past
            }
            return;
        }
        double mu = vesselBody.mu() * gravityScale();
        StateVector at = Kepler.elementsFromState(vesselState, mu, t).stateAt(Math.max(t, nodeTime), mu);
        DVec3 pro = at.velocity().normalize();
        DVec3 nor = at.position().cross(at.velocity()).normalize();
        DVec3 rad = nor.cross(pro).normalize();
        DVec3 dv = pro.scale(nodePrograde).add(nor.scale(nodeNormal)).add(rad.scale(nodeRadial));
        nodePoint = at.position();
        nodeDirection = dv.length() > 1e-6 ? dv.normalize() : null;
        StateVector after = new StateVector(at.position(), at.velocity().add(dv));
        nodePath = TrajectoryPredictor.predict(system, vesselBody, after, Math.max(t, nodeTime), gravityScale(), horizon(vesselBody, after));
    }

    @Override
    public void tick() {
        if (--recomputeIn <= 0) {
            updateVessel();
            recomputeIn = 5;
        }
        updateFlightWidgets();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (renameBox != null && button == 0 && mouseX >= renameBox[0] && mouseX < renameBox[0] + renameBox[2]
                && mouseY >= renameBox[1] && mouseY < renameBox[1] + renameBox[3]) {
            minecraft.setScreen(new RenameShipScreen(this, ownShipName()));
            return true;
        }
        pressX = mouseX;
        pressY = mouseY;
        pressHandled = super.mouseClicked(mouseX, mouseY, button);
        return true;
    }

    /** A click (not a drag) on your own orbit puts a maneuver node there. */
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean handled = super.mouseReleased(mouseX, mouseY, button);
        boolean click = button == 0 && !pressHandled && Math.hypot(mouseX - pressX, mouseY - pressY) < 4;
        if (click && selected != null && inside(orbitButton, mouseX, mouseY)) {
            fly(selected, false);
            return true;
        }
        if (click && selected != null && inside(landButton, mouseX, mouseY) && selected.isLandable()) {
            fly(selected, true);
            return true;
        }
        if (click && selected != null && inside(spotButton, mouseX, mouseY) && selected.isLandable()) {
            if (spotBody == selected && !Double.isNaN(spotLat)) {
                flyToSpot(selected, spotLat, spotLon);
            } else {
                // Look at the world close up, then click where to come down.
                spotPicking = true;
                selfFocus = false;
                focus = selected;
                focusId = selected.id();
                shipFocus = false;
                followSelf = false;
                zoom = -1;
                fitZoom();
            }
            return true;
        }
        if (click && spotPicking && selected != null) {
            double[] latLon = surfaceAt(selected, mouseX, mouseY);
            if (latLon != null) {
                spotBody = selected;
                spotLat = latLon[0];
                spotLon = latLon[1];
                return true;
            }
        }
        if (button == 1 && Math.hypot(mouseX - pressX, mouseY - pressY) < 4) {
            if (spotPicking) {
                spotPicking = false; // right-click: stop picking a spot
                return true;
            }
            // Right-click on something: look at it.
            CelestialBody hit = bodyAt(mouseX, mouseY);
            if (hit != null) {
                focus = hit;
                focusId = hit.id();
                selfFocus = false;
                shipFocus = false;
                followSelf = false;
                zoom = -1;
                fitZoom();
                return true;
            }
        }
        if (click) {
            CelestialBody hit = bodyAt(mouseX, mouseY);
            if (hit != null) {
                if (hit == selected && hit != focus) {
                    // Clicked twice: look at it.
                    focus = hit;
                    focusId = hit.id();
                    selfFocus = false;
                selfFocus = false;
                    shipFocus = false;
                    zoom = -1;
                    fitZoom();
                } else {
                    select(hit);
                }
                return true;
            }
        }
        if (button == 0 && !pressHandled && Math.hypot(mouseX - pressX, mouseY - pressY) < 4 && piloted() != null
                && ClientSpace.orbitActive() && !trajectory.isEmpty()) {
            SolarSystem system = ClientUniverse.system();
            TrajectoryPredictor.Segment first = trajectory.get(0);
            double best = 10;
            double bestTime = Double.NaN;
            double now = ClientUniverse.time();
            for (int k = 0; k < first.points().size(); k++) {
                double time = first.times().get(k);
                if (time < now + 5) {
                    continue;
                }
                float[] p = project(toFocus(system, first.body(), first.points().get(k), time));
                double d = Math.hypot(p[0] - mouseX, p[1] - mouseY);
                if (d < best) {
                    best = d;
                    bestTime = time;
                }
            }
            if (!Double.isNaN(bestTime)) {
                nodeTime = bestTime;
                nodePrograde = 0;
                nodeNormal = 0;
                nodeRadial = 0;
                sendNode(false);
                recomputeIn = 0;
                return true;
            }
        }
        return handled;
    }

    /** The main belt: its asteroids, each on its own orbit (faster closer in), with the Kirkwood gaps. */
    private static final double[][] BELT = makeBelt();

    private static double[][] makeBelt() {
        Random random = new Random(1801);
        double[][] belt = new double[1800][];
        double[] gaps = {2.50, 2.82, 2.95, 3.27}; // where Jupiter's pull clears rings
        for (int i = 0; i < belt.length; i++) {
            double a;
            do {
                a = 2.1 + 1.2 * (0.5 + 0.22 * random.nextGaussian());
            } while (a < 2.1 || a > 3.3 || nearGap(a, gaps));
            double e = Math.abs(random.nextGaussian()) * 0.08;
            double inc = Math.toRadians(Math.abs(random.nextGaussian()) * 7);
            belt[i] = new double[]{a * SolarSystem.AU, e, inc, random.nextDouble() * 2 * Math.PI, random.nextDouble() * 2 * Math.PI,
                    random.nextDouble() * 2 * Math.PI, 0.4 + 0.6 * random.nextDouble()};
        }
        return belt;
    }

    private static boolean nearGap(double a, double[] gaps) {
        for (double g : gaps) {
            if (Math.abs(a - g) < 0.025) {
                return true;
            }
        }
        return false;
    }

    private void drawAsteroidBelt(GuiGraphics g, SolarSystem system, double t) {
        double mu = system.sun().mu();
        for (double[] rock : BELT) {
            double a = rock[0];
            double n = Math.sqrt(mu / (a * a * a));
            double m = rock[5] + n * t;
            double nu = m + 2 * rock[1] * Math.sin(m); // near enough for small eccentricities
            double r = a * (1 - rock[1] * Math.cos(m));
            double node = rock[3];
            double argument = rock[4] + nu;
            double cosNode = Math.cos(node);
            double sinNode = Math.sin(node);
            double cosArg = Math.cos(argument);
            double sinArg = Math.sin(argument);
            double cosInc = Math.cos(rock[2]);
            DVec3 p = new DVec3(r * (cosNode * cosArg - sinNode * sinArg * cosInc),
                    r * (sinNode * cosArg + cosNode * sinArg * cosInc), r * sinArg * Math.sin(rock[2]));
            float[] at = project(p);
            if (at[2] <= 0 || at[0] < 0 || at[1] < 0 || at[0] >= width || at[1] >= height) {
                continue;
            }
            int alpha = (int) (70 + 110 * rock[6]);
            g.fill((int) at[0], (int) at[1], (int) at[0] + 1, (int) at[1] + 1, (alpha << 24) | 0xB8AE9C);
        }
    }

    private static boolean inside(int[] box, double x, double y) {
        return box != null && x >= box[0] && x < box[0] + box[2] && y >= box[1] && y < box[1] + box[3];
    }

    /** The world drawn under the cursor (the focus or one circling it), or null. */
    private CelestialBody bodyAt(double mouseX, double mouseY) {
        SolarSystem system = ClientUniverse.system();
        double t = ClientUniverse.time();
        CelestialBody best = null;
        double bestDistance = Double.MAX_VALUE;
        List<CelestialBody> candidates = new ArrayList<>(shownChildren(system, focus));
        candidates.add(focus);
        candidates.addAll(contextBodies(system, contextAncestors(system, t)));
        for (CelestialBody b : candidates) {
            DVec3 at = toFocus(system, b, DVec3.ZERO, t);
            float[] p = project(at);
            if (p[2] <= 0) {
                continue;
            }
            double radius = Math.max(9, b.radius() * focal / p[2]);
            double d = Math.hypot(p[0] - mouseX, p[1] - mouseY);
            if (d < radius && d < bestDistance) {
                best = b;
                bestDistance = d;
            }
        }
        return best;
    }

    private void select(CelestialBody body) {
        selected = body;
        selectedEstimate = null;
        var system = ClientUniverse.system();
        double t = ClientUniverse.time();
        CelestialBody from = vesselBody != null ? vesselBody : ClientUniverse.currentBody();
        if (from == null || body == from && vesselBody == null) {
            return;
        }
        var state = vesselState;
        double scale = gravityScale();
        selectedEstimate = java.util.concurrent.CompletableFuture.supplyAsync(
                () -> com.thepearlking.stevespace.vessel.RouteEstimate.of(system, from, state, t, body, body.isLandable(), scale),
                net.minecraft.Util.backgroundExecutor());
    }

    /** Fly there and land on the picked spot. */
    private void flyToSpot(CelestialBody body, double latitude, double longitude) {
        if (!canFly()) {
            return;
        }
        com.thepearlking.stevespace.client.FlightInput.setThrottle(0);
        com.thepearlking.stevespace.network.SpaceNetwork.sendToServer(new com.thepearlking.stevespace.network.AutopilotC2S(
                com.thepearlking.stevespace.vessel.Autopilot.Program.TRAVEL, body.id(), true, latitude, longitude));
        onClose();
    }

    /** The latitude and longitude (radians) on this world under the mouse, or null if the mouse is off it. */
    private double[] surfaceAt(CelestialBody body, double mouseX, double mouseY) {
        SolarSystem system = ClientUniverse.system();
        double t = ClientUniverse.time();
        DVec3 centre = toFocus(system, body, DVec3.ZERO, t);
        DVec3 dir = forward.add(right.scale((mouseX - cx) / focal)).sub(up.scale((mouseY - cy) / focal)).normalize();
        DVec3 oc = camera.sub(centre);
        double b = oc.dot(dir);
        double c = oc.dot(oc) - body.radius() * body.radius();
        double disc = b * b - c;
        if (disc < 0) {
            return null;
        }
        double along = -b - Math.sqrt(disc);
        if (along <= 0) {
            return null;
        }
        DVec3 n = camera.add(dir.scale(along)).sub(centre).normalize();
        var frame = system.frameAt(body, t);
        return new double[]{Math.asin(Math.max(-1, Math.min(1, n.dot(frame.north())))),
                Math.atan2(n.dot(frame.east90()), n.dot(frame.meridian()))};
    }

    private void fly(CelestialBody body, boolean land) {
        if (!canFly()) {
            return;
        }
        com.thepearlking.stevespace.client.FlightInput.setThrottle(0);
        com.thepearlking.stevespace.network.SpaceNetwork.sendToServer(new com.thepearlking.stevespace.network.AutopilotC2S(
                com.thepearlking.stevespace.vessel.Autopilot.Program.TRAVEL, body.id(), land));
        onClose();
    }

    /** The picked world's card: name, cost and time, Orbit and Land (when flying a ship). */
    private void drawPick(GuiGraphics g) {
        orbitButton = null;
        landButton = null;
        spotButton = null;
        if (selected == null) {
            if (picking) {
                String hint = tr("screen.stevespace.map.pick_hint");
                g.drawString(font, hint, width / 2 - font.width(hint) / 2, 44, AMBER, false);
            }
            return;
        }
        int x = width - PICK_W - 8;
        int y = 44;
        int h = 110;
        g.fill(x, y, x + PICK_W, y + h, PANEL);
        g.renderOutline(x, y, PICK_W, h, PANEL_EDGE);
        g.drawString(font, name(selected), x + 6, y + 6, GREEN, false);
        g.fill(x + 6, y + 17, x + PICK_W - 6, y + 18, GREEN_DIM);
        String cost = "";
        String time = "";
        if (selected.id().equals("acheron")) {
            // No figures for it: just the warning.
            int line = y + 23;
            for (var part : font.split(Component.translatable("screen.stevespace.map.unidentified"), PICK_W - 12)) {
                g.drawString(font, part, x + 6, line, AMBER, false);
                line += 10;
            }
        } else if (selectedEstimate != null && selectedEstimate.isDone()) {
            var e = selectedEstimate.getNow(null);
            if (e != null) {
                cost = String.format(Locale.ROOT, "%,.0f m/s", e.deltaV());
                time = FlightText.duration(e.seconds());
            }
        } else if (selectedEstimate != null) {
            cost = "\u2026";
            time = "\u2026";
        }
        if (!selected.id().equals("acheron")) {
            g.drawString(font, tr("screen.stevespace.destination.cost"), x + 6, y + 24, LABEL, false);
            g.drawString(font, cost, x + PICK_W - 6 - font.width(cost), y + 24, TEXT, false);
            g.drawString(font, tr("screen.stevespace.destination.time"), x + 6, y + 36, LABEL, false);
            g.drawString(font, time, x + PICK_W - 6 - font.width(time), y + 36, TEXT, false);
        }
        if (canFly()) {
            orbitButton = new int[]{x + 6, y + 56, (PICK_W - 18) / 2, 20};
            landButton = new int[]{x + 12 + (PICK_W - 18) / 2, y + 56, (PICK_W - 18) / 2, 20};
            button(g, orbitButton, tr("screen.stevespace.destination.orbit"), true);
            button(g, landButton, tr("screen.stevespace.destination.land"), selected.isLandable());
            spotButton = new int[]{x + 6, y + 80, PICK_W - 12, 20};
            boolean picked = spotBody == selected && !Double.isNaN(spotLat);
            String label = picked ? Component.translatable("screen.stevespace.destination.land_spot",
                    String.format(Locale.ROOT, "%.1f°%s %.1f°%s", Math.abs(Math.toDegrees(spotLat)), spotLat >= 0 ? "N" : "S",
                            Math.abs(Math.toDegrees(spotLon)), spotLon >= 0 ? "E" : "W")).getString()
                    : tr("screen.stevespace.destination.pick_spot");
            button(g, spotButton, label, selected.isLandable());
            if (spotPicking) {
                String hint = tr("screen.stevespace.map.spot_hint");
                g.drawString(font, hint, width / 2 - font.width(hint) / 2, 44, AMBER, false);
            }
        } else {
            g.drawString(font, tr("screen.stevespace.map.pick_need_ship"), x + 6, y + 60, LABEL, false);
        }
    }

    private void button(GuiGraphics g, int[] box, String text, boolean active) {
        g.fill(box[0], box[1], box[0] + box[2], box[1] + box[3], active ? 0xFF2F6E47 : 0xFF2A2F2C);
        g.renderOutline(box[0], box[1], box[2], box[3], active ? GREEN : 0xFF3B4247);
        g.drawString(font, text, box[0] + box[2] / 2 - font.width(text) / 2, box[1] + 6, active ? TEXT : LABEL, false);
    }

    // ---------------------------------------------------------------------------------------
    // Input
    // ---------------------------------------------------------------------------------------

    private static void lookAt(DVec3 position) {
        DVec3 n = position.normalize();
        pitch = Math.asin(Math.max(-1, Math.min(1, n.z())));
        yaw = Math.atan2(-n.x(), -n.y());
        targetPitch = pitch;
        targetYaw = yaw;
    }

    /**
     * Dragging turns the view like grabbing a globe: drag right and the side facing you moves
     * right. The same speed whatever the window size, and it eases instead of jumping.
     */
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        followSelf = false;
        double perPixel = Math.PI / Math.max(200, Math.min(width, height));
        // Dragging moves the camera round the focus (KSP style) on both axes: drag right and you
        // orbit right, so the planet turns the other way; drag up and you rise over it.
        targetYaw -= dx * perPixel;
        targetPitch = Math.max(Math.toRadians(-89), Math.min(Math.toRadians(89), targetPitch - dy * perPixel));
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Zooming keeps whatever you're looking at (your ship, or you) in the middle, however far out.
        zoom *= Math.pow(1.18, scrollY);
        // Close in to just above the surface (or the ship), never inside the planet.
        double nearest = shipFocus || selfFocus ? 40 : focus.radius() * 1.08;
        zoom = Math.max(1e-12, Math.min(focal / nearest, zoom));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (FlightKeys.MAP.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB) {
            cycleFocus();
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE) {
            focusShip();
            return true;
        }
        if (FlightKeys.WARP_FASTER.matches(keyCode, scanCode)) {
            requestWarp(1);
            return true;
        }
        if (FlightKeys.WARP_SLOWER.matches(keyCode, scanCode)) {
            requestWarp(-1);
            return true;
        }
        // Flying from the map: full and cut throttle work here too (the rest are read as held keys).
        if (piloted() != null) {
            if (FlightKeys.THROTTLE_FULL.matches(keyCode, scanCode)) {
                com.thepearlking.stevespace.client.FlightInput.fullThrottle();
                return true;
            }
            if (FlightKeys.THROTTLE_CUT.matches(keyCode, scanCode)) {
                com.thepearlking.stevespace.client.FlightInput.cutThrottle();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Next body to look at: the vessel's body, then its parent and moons, then back. */
    /** Looking at yourself on the world you're on, close in, following you. */
    private void focusSelf() {
        if (selfPosition == null || selfBody == null) {
            return;
        }
        focus = selfBody;
        focusId = focus.id();
        shipFocus = false;
        followSelf = true;
        selfFocus = true;
        lookAt(selfPosition);
        // Close enough to pick out where you are, far enough to see the world round you.
        zoom = Math.min(width, height) * 0.42 / (selfBody.radius() * 0.35);
    }

    /** Straight back to your own rocket (Backspace, as in KSP). */
    private void focusShip() {
        if (vesselBody == null || vesselState == null) {
            focusSelf(); // on a world: that's where you (and your rocket) are
            return;
        }
        followSelf = false;
        shipFocus = true;
        selfFocus = false;
        focus = vesselBody;
        focusId = focus.id();
        zoom = Math.min(width, height) * 0.42 / 2000;
    }

    private void cycleFocus() {
        SolarSystem system = ClientUniverse.system();
        CelestialBody home = vesselBody != null ? vesselBody : ClientUniverse.currentBody();
        if (home == null) {
            home = system.earth();
        }
        CelestialBody planet = home.parent() != null && home.parent() != system.sun() ? home.parent() : home;
        List<CelestialBody> choices = new ArrayList<>();
        choices.add(planet);
        choices.addAll(system.childrenOf(planet));
        followSelf = false;
        selfFocus = false;
        // After the bodies comes your own ship (when you are in one).
        if (shipFocus) {
            shipFocus = false;
            focus = choices.get(0);
        } else {
            int index = choices.indexOf(focus);
            if (index == choices.size() - 1 && vesselBody != null && vesselState != null) {
                shipFocus = true;
            selfFocus = false;
                selfFocus = false;
        selfFocus = false;
                focus = vesselBody;
                focusId = focus.id();
                zoom = Math.min(width, height) * 0.42 / 300;
                return;
            }
            focus = choices.get((index + 1) % choices.size());
        }
        focusId = focus.id();
        zoom = -1;
        fitZoom();
    }

    @Override
    public void removed() {
        super.removed();
        if (picking && savedView != null) {
            yaw = (double) savedView[0];
            pitch = (double) savedView[1];
            targetYaw = (double) savedView[2];
            targetPitch = (double) savedView[3];
            focusId = (String) savedView[4];
            zoom = (double) savedView[5];
            return;
        }
        if (tracked == null && focus != null) {
            remembered = true;
            rememberedShip = shipFocus;
            focusId = focus.id();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------------------------------------------------------------------------------
    // Drawing
    // ---------------------------------------------------------------------------------------

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, BACKGROUND);
        if (forward == null) {
            return;
        }
        // Stars fixed in space: they swing past as the camera turns, like looking out of a window.
        for (float[] star : STARS) {
            double dx = star[0];
            double dy = star[1];
            double dz = star[2];
            double depth = dx * forward.x() + dy * forward.y() + dz * forward.z();
            if (depth <= 0.05) {
                continue;
            }
            double sx = cx + (dx * right.x() + dy * right.y() + dz * right.z()) * focal / depth;
            double sy = cy - (dx * up.x() + dy * up.y() + dz * up.z()) * focal / depth;
            if (sx < 0 || sy < 0 || sx >= width || sy >= height) {
                continue;
            }
            int a = (int) star[3];
            int x = (int) sx;
            int y = (int) sy;
            int size = star[4] > 0.92f ? 2 : 1;
            g.fill(x, y, x + size, y + size, (a << 24) | (int) star[5]);
        }
    }

    /** Directions (unit x, y, z), brightness (alpha), size roll and colour of the background stars. */
    private static final float[][] STARS = makeStars();

    private static float[][] makeStars() {
        Random random = new Random(7);
        float[][] stars = new float[2600][];
        int[] tints = {0xDDE6FF, 0xFFF4E0, 0xFFE2C4, 0xCFDCFF, 0xFFFFFF};
        for (int i = 0; i < stars.length; i++) {
            double z = random.nextDouble() * 2 - 1;
            // A faint band of extra stars, like the Milky Way, tilted across the sky.
            if (i % 3 == 0) {
                z = random.nextGaussian() * 0.12;
            }
            double a = random.nextDouble() * 2 * Math.PI;
            double r = Math.sqrt(Math.max(0, 1 - z * z));
            double x = r * Math.cos(a);
            double y = r * Math.sin(a);
            // Tilt the band by 60 degrees about the x axis.
            double ty = y * 0.5 - z * 0.866;
            double tz = y * 0.866 + z * 0.5;
            float brightness = (float) Math.pow(random.nextDouble(), 3);
            stars[i] = new float[]{(float) x, (float) ty, (float) tz, 30 + brightness * 200, random.nextFloat(), tints[random.nextInt(tints.length)]};
        }
        return stars;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        SolarSystem system = ClientUniverse.system();
        double t = ClientUniverse.time();
        cx = width / 2f;
        cy = height / 2f;
        yaw += (targetYaw - yaw) * 0.35;
        pitch += (targetPitch - pitch) * 0.35;
        forward = new DVec3(Math.sin(yaw) * Math.cos(pitch), Math.cos(yaw) * Math.cos(pitch), -Math.sin(pitch));
        right = new DVec3(Math.cos(yaw), -Math.sin(yaw), 0);
        up = right.cross(forward);
        // A real perspective camera: 'zoom' is the scale (pixels per metre) at the focus, which
        // sets how far back the camera stands.
        focal = Math.min(width, height) * 0.5 / Math.tan(Math.toRadians(FOV) / 2);
        distance = focal / zoom;
        camera = focusPoint().sub(forward.scale(distance));
        renderBackground(g, mouseX, mouseY, partialTick); // stars (now facing the right way)

        Matrix4f matrix = g.pose().last().pose();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull(); // lines are thin quads that may face either way
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // Moons' orbits, behind everything else.
        for (CelestialBody moon : system.childrenOf(focus)) {
            List<DVec3> ring = new ArrayList<>();
            double period = moon.orbit().period(focus.mu() + moon.mu());
            for (int i = 0; i <= 180; i++) {
                ring.add(system.stateRelativeToParent(moon, t + period * i / 180).position());
            }
            polyline(matrix, ring, 0x5593A59A, 1f);
        }
        // Zoomed out far enough, what's round the focus comes into view too (it stays the centre):
        // its planet and that planet's other moons, then the Sun and the planets, with their orbits.
        List<CelestialBody> context = contextAncestors(system, t);
        for (CelestialBody ancestor : context) {
            for (CelestialBody child : system.childrenOf(ancestor)) {
                int alpha = ringAlpha(child.orbit().semiMajorAxis(), ancestor.parent() == null ? 0x40 : 0x55);
                if (alpha == 0) {
                    continue;
                }
                List<DVec3> ring = new ArrayList<>();
                double period = child.orbit().period(ancestor.mu() + child.mu());
                for (int i = 0; i <= 180; i++) {
                    ring.add(toFocus(system, ancestor, system.stateRelativeToParent(child, t + period * i / 180).position(), t));
                }
                polyline(matrix, ring, alpha | (ancestor.parent() == null ? 0xA0A8B0 : 0x93A59A), 1f);
            }
        }
        if (focus.hasAtmosphere()) {
            circle(matrix, focus.radius() + focus.atmosphere().height(), (0x60 << 24) | (focus.atmosphere().color() & 0xFFFFFF), 1f);
        }
        if (focus.parent() == null) {
            drawAsteroidBelt(g, system, t);
        }
        // Far to near, so a moon passing in front of its planet covers it and one behind is hidden.
        List<Object[]> bodies = new ArrayList<>();
        bodies.add(new Object[]{focus, DVec3.ZERO});
        for (CelestialBody moon : shownChildren(system, focus)) {
            bodies.add(new Object[]{moon, system.stateRelativeToParent(moon, t).position()});
        }
        for (CelestialBody other : contextBodies(system, context)) {
            bodies.add(new Object[]{other, toFocus(system, other, DVec3.ZERO, t)});
        }
        bodies.sort((a, b) -> Double.compare(((DVec3) b[1]).sub(camera).length(), ((DVec3) a[1]).sub(camera).length()));
        for (Object[] body : bodies) {
            DVec3 at = (DVec3) body[1];
            if (at.sub(camera).dot(forward) > 0) {
                drawBody(matrix, system, (CelestialBody) body[0], at, t);
            }
        }

        // The target's path, if there is one around this body.
        var targetBody = com.thepearlking.stevespace.client.space.ClientTarget.body();
        StateVector targetState = null;
        if (targetBody != null && targetBody.id().equals(focus.id())) {
            targetState = com.thepearlking.stevespace.client.space.ClientTarget.stateAt(t);
            if (targetState != null) {
                var path = TrajectoryPredictor.predict(system, targetBody, targetState, t, gravityScale(), 6 * 3600);
                if (!path.isEmpty()) {
                    TrajectoryPredictor.Segment first = path.get(0);
                    List<DVec3> points = new ArrayList<>();
                    for (int k = 0; k < first.points().size(); k++) {
                        points.add(first.points().get(k));
                    }
                    polyline(matrix, points, 0xC06FB7E8, 1.2f);
                }
            }
        }

        // The vessel's path, piece by piece.
        for (int i = 0; i < trajectory.size(); i++) {
            TrajectoryPredictor.Segment segment = trajectory.get(i);
            List<DVec3> points = new ArrayList<>();
            for (int k = 0; k < segment.points().size(); k++) {
                points.add(toFocus(system, segment.body(), segment.points().get(k), segment.times().get(k)));
            }
            polyline(matrix, points, SEGMENT_COLORS[i % SEGMENT_COLORS.length], 1.6f);
        }
        // After the planned maneuver.
        for (TrajectoryPredictor.Segment segment : nodePath) {
            List<DVec3> points = new ArrayList<>();
            for (int k = 0; k < segment.points().size(); k++) {
                points.add(toFocus(system, segment.body(), segment.points().get(k), segment.times().get(k)));
            }
            polyline(matrix, points, NODE_COLOR, 1.2f);
        }
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();

        // Labels and markers on top.
        for (CelestialBody moon : shownChildren(system, focus)) {
            DVec3 at = system.stateRelativeToParent(moon, t).position();
            float[] p = project(at);
            if (p[2] <= 0) {
                continue;
            }
            float r = (float) Math.max(3, moon.radius() * focal / p[2]);
            g.drawString(font, name(moon), (int) (p[0] + r + 3), (int) p[1] - 4, LABEL, false);
        }
        for (CelestialBody other : contextBodies(system, context)) {
            // Named once zoomed out far enough to take in where it is.
            double reach = other.parent() == null ? system.absolutePosition(focus, t).sub(system.absolutePosition(other, t)).length()
                    : other.orbit().semiMajorAxis();
            if (ringAlpha(reach, 255) == 0) {
                continue;
            }
            float[] p = project(toFocus(system, other, DVec3.ZERO, t));
            if (p[2] <= 0) {
                continue;
            }
            float r = (float) Math.max(3, other.radius() * focal / p[2]);
            g.drawString(font, name(other), (int) (p[0] + r + 3), (int) p[1] - 4, LABEL, false);
        }
        drawTrajectoryMarkers(g, system);
        if (vesselBody != null && vesselState != null) {
            float[] p = project(toFocus(system, vesselBody, vesselState.position(), t));
            int x = (int) p[0];
            int y = (int) p[1];
            g.fill(x - 2, y - 2, x + 3, y + 3, 0xFFFFFFFF);
            g.renderOutline(x - 4, y - 4, 9, 9, GREEN);
        }
        if (spotBody != null && !Double.isNaN(spotLat)) {
            // The picked landing spot.
            var frame = system.frameAt(spotBody, t);
            DVec3 n = frame.up(spotLat, spotLon);
            DVec3 at = toFocus(system, spotBody, n.scale(spotBody.radius()), t);
            float[] p = project(at);
            if (p[2] > 0 && !(spotBody == focus && hidden(at))) {
                int x = (int) p[0];
                int y = (int) p[1];
                for (int k = -4; k <= 4; k++) {
                    g.fill(x + k, y + k, x + k + 1, y + k + 1, AMBER);
                    g.fill(x + k, y - k, x + k + 1, y - k + 1, AMBER);
                }
            }
        }
        if (vesselBody == null && selfBody != null && selfBody.id().equals(focus.id())) {
            // You are here.
            DVec3 me = SurfaceFrame.toOrbit(system, selfBody, Minecraft.getInstance().player.getX(), Minecraft.getInstance().player.getY(),
                    Minecraft.getInstance().player.getZ(), 0, 0, 0, t).position();
            if (followSelf) {
                lookAt(me);
            }
            float[] p = project(me);
            int x = (int) p[0];
            int y = (int) p[1];
            boolean behind = hidden(me);
            int ring = behind ? 0x80FFFFFF : 0xFFFFFFFF;
            g.fill(x - 1, y - 1, x + 2, y + 2, ring);
            g.renderOutline(x - 4, y - 4, 9, 9, behind ? (0x80 << 24) | (GREEN & 0xFFFFFF) : GREEN);
            g.drawString(font, Minecraft.getInstance().player.getName(), x + 7, y - 4, behind ? LABEL : TEXT, false);
        }
        if (targetState != null) {
            float[] p = project(targetState.position());
            int x = (int) p[0];
            int y = (int) p[1];
            g.renderOutline(x - 3, y - 3, 7, 7, 0xFF6FB7E8);
            g.drawString(font, com.thepearlking.stevespace.client.space.ClientTarget.name(), x + 6, y - 4, 0xFF6FB7E8, false);
        }
        if (nodePoint != null && vesselBody != null) {
            float[] p = project(toFocus(system, vesselBody, nodePoint, nodeTime));
            int x = (int) p[0];
            int y = (int) p[1];
            for (int k = 0; k <= 4; k++) {
                g.fill(x - k, y - 4 + k, x + k + 1, y - 3 + k, NODE_COLOR);
                g.fill(x - k, y + 4 - k, x + k + 1, y + 5 - k, NODE_COLOR);
            }
        }
        // The buttons after the worlds (drawn before them, a planet behind covered them and only
        // their text showed through the panel), then the panels they sit in, as always.
        RenderSystem.disableDepthTest();
        for (var widget : renderables) {
            widget.render(g, mouseX, mouseY, partialTick);
        }
        drawInfo(g, system, t);
        drawFlight(g, t, partialTick);
        drawWarp(g);
        drawPick(g);
    }

    /** The navball, throttle and node panel, when you are flying the ship. */
    private void drawFlight(GuiGraphics g, double t, float partialTick) {
        VesselEntity vessel = piloted();
        if (vessel == null || vessel.isGrounded()) {
            return;
        }
        int nx = navX();
        int ny = navY();
        g.fill(nx - 168, ny - 58, nx + 168, height - 4, PANEL);
        g.renderOutline(nx - 168, ny - 58, 336, height - 4 - (ny - 58), PANEL_EDGE);
        Navball.draw(g, font, vessel, Navball.frame(vessel, nodeDirection), nx, ny, 46, partialTick);
        // Throttle and SAS state to the right.
        int rx = nx + 58;
        int ry = ny - 46;
        float throttle = com.thepearlking.stevespace.client.FlightInput.throttle();
        g.drawString(font, tr("hud.stevespace.throttle"), rx, ry, LABEL, false);
        g.fill(rx, ry + 11, rx + 76, ry + 17, 0xFF1A2420);
        g.fill(rx, ry + 11, rx + Math.round(76 * throttle), ry + 17, GREEN);
        String pct = Math.round(throttle * 100) + "%";
        g.drawString(font, pct, rx + 80, ry + 10, TEXT, false);
        ry += 24;
        g.drawString(font, Component.translatable("screen.stevespace.map.sas", Component.translatable(
                "screen.stevespace.map.sas." + vessel.sas().name().toLowerCase(Locale.ROOT))), rx, ry, LABEL, false);
        ry += 12;
        String status = vessel.autopilotStatus();
        if (!status.isEmpty()) {
            g.drawString(font, Component.translatable(status), rx, ry, AMBER, false);
            ry += 10;
            String detail = vessel.autopilotDetail();
            if (!detail.isEmpty()) {
                g.drawString(font, detail, rx, ry, AMBER, false);
            }
        }
        var keyLines = font.split(Component.translatable("screen.stevespace.map.fly_keys", FlightKeys.THROTTLE_UP.getTranslatedKeyMessage(),
                FlightKeys.THROTTLE_DOWN.getTranslatedKeyMessage()), nx + 164 - rx);
        int ky = height - 8 - keyLines.size() * 10;
        for (var line : keyLines) {
            g.drawString(font, line, rx, ky, GREEN_DIM, false);
            ky += 10;
        }

        // The node panel.
        if (!hasNode() || !ClientSpace.orbitActive()) {
            if (ClientSpace.orbitActive()) {
                String tip = tr("screen.stevespace.map.node.tip");
                g.drawString(font, tip, width - 8 - font.width(tip), 10, GREEN_DIM, false);
            }
            return;
        }
        int x0 = nodeX();
        int y0 = 8;
        int w = 170;
        int h = 150;
        g.fill(x0, y0, x0 + w, y0 + h, PANEL);
        g.renderOutline(x0, y0, w, h, PANEL_EDGE);
        g.drawString(font, tr("screen.stevespace.map.node"), x0 + 6, y0 + 6, NODE_COLOR, false);
        g.fill(x0 + 6, y0 + 17, x0 + w - 6, y0 + 18, GREEN_DIM);
        double dv = Math.sqrt(nodePrograde * nodePrograde + nodeNormal * nodeNormal + nodeRadial * nodeRadial);
        double burn = dv / Math.max(1e-3, vessel.maxAcceleration());
        String[][] rows = {
                {tr("screen.stevespace.map.node.in"), "T\u2212" + FlightText.duration(nodeTime - t)},
                {tr("screen.stevespace.map.node.dv"), FlightText.speed(dv)},
                {tr("screen.stevespace.map.node.burn"), FlightText.duration(burn)}};
        int ry2 = y0 + 22;
        for (String[] row : rows) {
            g.drawString(font, row[0], x0 + 6, ry2, LABEL, false);
            g.drawString(font, row[1], x0 + w - 6 - font.width(row[1]), ry2, TEXT, false);
            ry2 += 11;
        }
        String[] names = {"screen.stevespace.map.node.prograde", "screen.stevespace.map.node.normal", "screen.stevespace.map.node.radial"};
        double[] values = {nodePrograde, nodeNormal, nodeRadial};
        int[] colors = {0xFFE8D24A, 0xFFC56BE8, 0xFF5FD8E0};
        for (int i = 0; i < 3; i++) {
            int y = y0 + 62 + i * 16;
            g.drawString(font, tr(names[i]), x0 + 6, y, colors[i], false);
            String v = FlightText.signedSpeed(values[i]);
            g.drawString(font, v, x0 + 118 - font.width(v) / 2, y, TEXT, false);
        }
    }

    /** Ap/Pe ticks on the first segment and a note at the end of each one. */
    private void drawTrajectoryMarkers(GuiGraphics g, SolarSystem system) {
        for (int i = 0; i < trajectory.size(); i++) {
            TrajectoryPredictor.Segment segment = trajectory.get(i);
            OrbitalElements orbit = segment.orbit();
            CelestialBody body = segment.body();
            double mu = body.mu() * gravityScale();
            if (i == 0) {
                marker(g, system, segment, Math.PI, orbit, mu, "Ap", orbit.eccentricity() < 1);
                marker(g, system, segment, 0, orbit, mu, "Pe", orbit.periapsis() > 0);
            }
            if (segment.end() != TrajectoryPredictor.End.CONTINUES && segment.end() != TrajectoryPredictor.End.TIME_LIMIT) {
                int last = segment.points().size() - 1;
                float[] p = project(toFocus(system, body, segment.points().get(last), segment.times().get(last)));
                String key = "screen.stevespace.map.end." + segment.end().name().toLowerCase(Locale.ROOT);
                String target = segment.end() == TrajectoryPredictor.End.ENCOUNTER && i + 1 < trajectory.size()
                        ? name(trajectory.get(i + 1).body()) : name(body);
                String text = Component.translatable(key, target).getString();
                int color = SEGMENT_COLORS[i % SEGMENT_COLORS.length];
                g.fill((int) p[0] - 1, (int) p[1] - 1, (int) p[0] + 2, (int) p[1] + 2, color);
                g.drawString(font, text, (int) p[0] + 5, (int) p[1] + 3, color, false);
            }
        }
    }

    private void marker(GuiGraphics g, SolarSystem system, TrajectoryPredictor.Segment segment, double trueAnomaly,
                        OrbitalElements orbit, double mu, String name, boolean show) {
        if (!show) {
            return;
        }
        double r = orbit.semiMajorAxis() * (1 - orbit.eccentricity() * orbit.eccentricity())
                / (1 + orbit.eccentricity() * Math.cos(trueAnomaly));
        if (r < segment.body().radius() * 0.999) {
            return; // under the ground: no such point on this flight
        }
        StateVector s = Kepler.stateFromTrueAnomaly(orbit, trueAnomaly, r, mu);
        float[] p = project(toFocus(system, segment.body(), s.position(), segment.startTime()));
        String text = name + " " + FlightText.distance(r - segment.body().radius());
        g.fill((int) p[0] - 1, (int) p[1] - 1, (int) p[0] + 2, (int) p[1] + 2, AMBER);
        g.drawString(font, text, (int) p[0] + 4, (int) p[1] - 10, AMBER, false);
    }

    private void drawInfo(GuiGraphics g, SolarSystem system, double t) {
        int x = 8;
        int y = 8;
        int w = 168;
        List<String[]> rows = new ArrayList<>();
        if (!trajectory.isEmpty() && vesselState != null) {
            TrajectoryPredictor.Segment first = trajectory.get(0);
            OrbitalElements orbit = first.orbit();
            CelestialBody body = first.body();
            double mu = body.mu() * gravityScale();
            DVec3 h = vesselState.position().cross(vesselState.velocity());
            double inclination = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, h.normalize().dot(body.spinAxis())))));
            if (body != focus) {
                rows.add(new String[]{tr("screen.stevespace.map.orbiting"), name(body)});
            }
            rows.add(new String[]{tr("screen.stevespace.map.apoapsis"), orbit.eccentricity() < 1
                    ? FlightText.distance(orbit.apoapsis() - body.radius()) : "—"});
            rows.add(new String[]{tr("screen.stevespace.map.periapsis"), FlightText.distance(orbit.periapsis() - body.radius())});
            rows.add(new String[]{tr("screen.stevespace.map.inclination"), String.format(Locale.ROOT, "%.1f°", inclination)});
            if (orbit.eccentricity() < 1) {
                rows.add(new String[]{tr("screen.stevespace.map.period"), FlightText.duration(orbit.period(mu))});
                double n = orbit.meanMotion(mu);
                double m = Kepler.wrapAngle(orbit.meanAnomalyAtEpoch() + n * (t - orbit.epoch()));
                double toPe = (m <= 0 ? -m : 2 * Math.PI - m) / n;
                double toAp = (m <= Math.PI ? Math.PI - m : 3 * Math.PI - m) / n;
                rows.add(new String[]{tr("screen.stevespace.map.time_to_ap"), FlightText.duration(toAp)});
                rows.add(new String[]{tr("screen.stevespace.map.time_to_pe"), FlightText.duration(toPe)});
            }
            for (TrajectoryPredictor.Segment segment : trajectory) {
                if (segment.end() == TrajectoryPredictor.End.ATMOSPHERE || segment.end() == TrajectoryPredictor.End.IMPACT
                        || segment.end() == TrajectoryPredictor.End.ENCOUNTER || segment.end() == TrajectoryPredictor.End.ESCAPE) {
                    String key = "screen.stevespace.map.event." + segment.end().name().toLowerCase(Locale.ROOT);
                    rows.add(new String[]{tr(key), FlightText.duration(segment.endTime() - t)});
                }
            }
        }
        if (rows.isEmpty() && tracked == null && selfBody != null) {
            Minecraft mc = Minecraft.getInstance();
            var geometry = new com.thepearlking.stevespace.world.planet.PlanetGeometry(selfBody.radius());
            double lat = Math.toDegrees(geometry.latitude(mc.player.getZ()));
            double lon = Math.toDegrees(geometry.longitude(geometry.wrapX(mc.player.getX())));
            rows.add(new String[]{tr("screen.stevespace.map.you"), name(selfBody)});
            rows.add(new String[]{tr("screen.stevespace.map.latitude"),
                    String.format(Locale.ROOT, "%.2f° %s", Math.abs(lat), lat >= 0 ? "N" : "S")});
            rows.add(new String[]{tr("screen.stevespace.map.longitude"),
                    String.format(Locale.ROOT, "%.2f° %s", Math.abs(lon), lon >= 0 ? "E" : "W")});
            rows.add(new String[]{tr("screen.stevespace.map.altitude"), FlightText.distance(mc.player.getY() - 63)});
        }
        if (tracked != null && tracked.landed()) {
            rows.add(new String[]{Component.translatable("screen.stevespace.mission_control.landed", name(tracked.body())).getString(), ""});
        }
        int h = 22 + rows.size() * 11 + (rows.isEmpty() ? 11 : 0);
        g.fill(x, y, x + w, y + h, PANEL);
        g.renderOutline(x, y, w, h, PANEL_EDGE);
        String title = tracked != null ? Component.translatable("screen.stevespace.map.tracking", tracked.name()).getString()
                : shipFocus && minecraft.player != null && VesselEntity.shipOf(minecraft.player) != null ? VesselEntity.shipOf(minecraft.player).getName().getString()
                : name(focus);
        g.drawString(font, title, x + 6, y + 6, GREEN, false);
        // Your own ship's name: click it to rename the ship.
        String own = title.equals(ownShipName()) || tracked == null && shipFocus ? ownShipName() : null;
        renameBox = own == null ? null : new int[]{x + 4, y + 3, w - 8, 13};
        if (own != null) {
            String pencil = "\u270E";
            g.drawString(font, pencil, x + w - 6 - font.width(pencil), y + 6, GREEN_DIM, false);
        }
        g.fill(x + 6, y + 17, x + w - 6, y + 18, GREEN_DIM);
        int ry = y + 22;
        if (rows.isEmpty()) {
            g.drawString(font, tr("screen.stevespace.map.no_vessel"), x + 6, ry, LABEL, false);
        }
        for (String[] row : rows) {
            g.drawString(font, row[0], x + 6, ry, LABEL, false);
            g.drawString(font, row[1], x + w - 6 - font.width(row[1]), ry, TEXT, false);
            ry += 11;
        }
        // Bottom left, wrapped to leave room for the flight panel when it's there.
        Component help = Component.translatable("screen.stevespace.map.help", FlightKeys.MAP.getTranslatedKeyMessage());
        VesselEntity flying = piloted();
        int room = flying != null && !flying.isGrounded() ? navX() - 168 - 16 : width - 16;
        var lines = font.split(help, Math.max(80, room));
        int hy = height - 6 - lines.size() * 10;
        for (var line : lines) {
            g.drawString(font, line, 8, hy, GREEN_DIM, false);
            hy += 10;
        }
    }

    /** Where the ship name can be clicked to rename it (x, y, w, h), or null. */
    private int[] renameBox;

    /** The name of the ship you're in or flying by remote control, or null if you aren't in one. */
    private String ownShipName() {
        if (minecraft.player == null) {
            return null;
        }
        VesselEntity ship = VesselEntity.shipOf(minecraft.player);
        if (ship != null) {
            return ship.getName().getString();
        }
        var cell = com.thepearlking.stevespace.client.space.ClientSpace.cellOrbit();
        if (cell == null) {
            return null;
        }
        var listed = com.thepearlking.stevespace.client.telescope.TelescopeShips.find("p:" + com.thepearlking.stevespace.world.ParkedShips.cellAt(minecraft.player.getX()));
        return listed != null ? listed.name() : Component.translatable("screen.stevespace.rename_parked").getString();
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }

    /** A point relative to {@code body} at time {@code t}, relative to the focus body instead. */
    /**
     * The bodies the focus goes round (its planet, the Sun): what's round them is always there
     * on the map too, in its real place (zoom out and there it is).
     */
    private List<CelestialBody> contextAncestors(SolarSystem system, double t) {
        List<CelestialBody> list = new ArrayList<>();
        for (CelestialBody ancestor = focus.parent(); ancestor != null; ancestor = ancestor.parent()) {
            list.add(ancestor);
        }
        return list;
    }

    /** How strongly to draw orbit rings this big: faint (then gone) when zoomed right in, so they don't slice through the view. */
    private int ringAlpha(double ringRadius, int alpha) {
        double view = distance * Math.tan(Math.toRadians(FOV) / 2);
        double k = Math.max(0, Math.min(1, (view / ringRadius - 0.01) / 0.05));
        return (int) (alpha * k) << 24;
    }

    /** Everything those bring into view (themselves and their other moons or planets), not the focus or its moons. */
    /**
     * The bodies round {@code body} shown on the map: its moons, and during an encounter the thing
     * that comes to the Moon (unnamed, unlit by any map; never on its way in or out).
     */
    private static List<CelestialBody> shownChildren(SolarSystem system, CelestialBody body) {
        List<CelestialBody> list = system.childrenOf(body);
        CelestialBody acheron = system.body("acheron");
        if (acheron != null && acheron.parent() == body
                && com.thepearlking.stevespace.universe.AcheronCourse.encounter(system, com.thepearlking.stevespace.client.ClientUniverse.time())) {
            list = new ArrayList<>(list);
            list.add(acheron);
        }
        return list;
    }

    /** A body's name on the map: Acheron has none. */
    private static String name(CelestialBody body) {
        return body.id().equals("acheron") ? "???" : body.displayName();
    }

    private List<CelestialBody> contextBodies(SolarSystem system, List<CelestialBody> ancestors) {
        List<CelestialBody> list = new ArrayList<>();
        for (CelestialBody ancestor : ancestors) {
            list.add(ancestor);
            for (CelestialBody child : shownChildren(system, ancestor)) {
                if (child != focus && !ancestors.contains(child) && !list.contains(child)) {
                    list.add(child);
                }
            }
        }
        return list;
    }

    private DVec3 toFocus(SolarSystem system, CelestialBody body, DVec3 point, double t) {
        if (body == focus) {
            return point;
        }
        if (body.parent() == focus) {
            return point.add(system.stateRelativeToParent(body, t).position());
        }
        if (focus.parent() == body) {
            return point.sub(system.stateRelativeToParent(focus, t).position());
        }
        return system.absolutePosition(body, t).add(point).sub(system.absolutePosition(focus, t));
    }

    /** Where the camera looks at: the focus body's centre, or your ship when following it. */
    private DVec3 focusPoint() {
        if (shipFocus && vesselState != null && vesselBody != null && vesselBody.id().equals(focus.id())) {
            return vesselState.position();
        }
        if (selfFocus && selfPosition != null && selfBody != null && selfBody.id().equals(focus.id())) {
            return selfPosition; // you, on the world: the view turns round where you stand
        }
        return DVec3.ZERO;
    }

    /** Screen x, y and distance in front of the camera (0 or less: behind it, not drawn). */
    private float[] project(DVec3 p) {
        DVec3 v = p.sub(camera);
        double depth = v.dot(forward);
        if (depth <= distance * 1e-4) {
            return new float[]{cx, cy, 0};
        }
        double k = focal / depth;
        return new float[]{(float) (cx + v.dot(right) * k), (float) (cy - v.dot(up) * k), (float) depth};
    }

    /** True if the focus body stands between the camera and this point. */
    private boolean hidden(DVec3 p) {
        DVec3 ray = p.sub(camera);
        double length = ray.length();
        if (length < 1e-9) {
            return false;
        }
        DVec3 dir = ray.scale(1 / length);
        double along = -camera.dot(dir); // closest approach to the body's centre
        if (along <= 0 || along >= length) {
            return false;
        }
        return camera.add(dir.scale(along)).length() < focus.radius();
    }

    private void polyline(Matrix4f matrix, List<DVec3> points, int color, float width) {
        if (points.size() < 2) {
            return;
        }
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float[] previous = project(points.get(0));
        boolean previousHidden = hidden(points.get(0));
        for (int i = 1; i < points.size(); i++) {
            float[] p = project(points.get(i));
            boolean isHidden = hidden(points.get(i));
            int c = previousHidden && isHidden ? dim(color) : color;
            if (previous[2] > 0 && p[2] > 0) { // both in front of the camera
                segment(builder, matrix, previous, p, c, width);
            }
            previous = p;
            previousHidden = isHidden;
        }
        com.mojang.blaze3d.vertex.MeshData mesh = builder.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
    }

    private void circle(Matrix4f matrix, double radius, int color, float width) {
        // The outline a sphere of this radius makes as seen from the camera: the circle where the
        // sight lines just graze it, a little nearer than its centre.
        DVec3 toCamera = camera.scale(-1);
        double d = toCamera.length();
        if (d <= radius) {
            return;
        }
        DVec3 axis = toCamera.scale(-1 / d);
        DVec3 centre = axis.scale(-radius * radius / d);
        double r = radius * Math.sqrt(1 - radius * radius / (d * d));
        DVec3 a1 = axis.cross(Math.abs(axis.z()) < 0.9 ? new DVec3(0, 0, 1) : new DVec3(1, 0, 0)).normalize();
        DVec3 a2 = axis.cross(a1);
        List<DVec3> ring = new ArrayList<>();
        for (int i = 0; i <= 120; i++) {
            double a = i * 2 * Math.PI / 120;
            ring.add(centre.add(a1.scale(Math.cos(a) * r)).add(a2.scale(Math.sin(a) * r)));
        }
        polyline(matrix, ring, color, width);
    }

    private static int dim(int color) {
        int a = (color >>> 24) / 4;
        return (a << 24) | (color & 0xFFFFFF);
    }

    private static void segment(BufferBuilder builder, Matrix4f matrix, float[] a, float[] b, int color, float width) {
        float dx = b[0] - a[0];
        float dy = b[1] - a[1];
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 1e-4f) {
            return;
        }
        float nx = -dy / length * width / 2;
        float ny = dx / length * width / 2;
        float alpha = (color >>> 24) / 255f;
        float r = ((color >> 16) & 255) / 255f;
        float g = ((color >> 8) & 255) / 255f;
        float bl = (color & 255) / 255f;
        builder.addVertex(matrix, a[0] + nx, a[1] + ny, 0).setColor(r, g, bl, alpha);
        builder.addVertex(matrix, a[0] - nx, a[1] - ny, 0).setColor(r, g, bl, alpha);
        builder.addVertex(matrix, b[0] - nx, b[1] - ny, 0).setColor(r, g, bl, alpha);
        builder.addVertex(matrix, b[0] + nx, b[1] + ny, 0).setColor(r, g, bl, alpha);
    }

    /** A body as a lit sphere: its surface map if it has one, its colour if not. */
    private void drawBody(Matrix4f matrix, SolarSystem system, CelestialBody body, DVec3 centre, double t) {
        SolarSystem.BodyFrame frame = system.frameAt(body, t);
        DVec3 sun = system.absolutePosition(system.sun(), t).sub(system.absolutePosition(body, t)).normalize();
        if (body.parent() == null) {
            // The Sun shines: a bright disc with a glow, not a lit globe.
            float[] c = project(centre);
            if (c[2] > 0) {
                float r = (float) Math.max(4, body.radius() * focal / c[2]);
                BufferBuilder glow = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
                RenderSystem.setShader(GameRenderer::getPositionColorShader);
                glow.addVertex(matrix, c[0], c[1], 0).setColor(1f, 0.95f, 0.75f, 1f);
                for (int i = 0; i <= 48; i++) {
                    double a = i * 2 * Math.PI / 48;
                    glow.addVertex(matrix, c[0] + (float) Math.cos(a) * r * 2.2f, c[1] + (float) Math.sin(a) * r * 2.2f, 0).setColor(1f, 0.8f, 0.4f, 0f);
                }
                BufferUploader.drawWithShader(glow.buildOrThrow());
                BufferBuilder disc = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
                disc.addVertex(matrix, c[0], c[1], 0).setColor(1f, 0.98f, 0.9f, 1f);
                for (int i = 0; i <= 48; i++) {
                    double a = i * 2 * Math.PI / 48;
                    disc.addVertex(matrix, c[0] + (float) Math.cos(a) * r, c[1] + (float) Math.sin(a) * r, 0).setColor(1f, 0.88f, 0.55f, 1f);
                }
                BufferUploader.drawWithShader(disc.buildOrThrow());
            }
            return;
        }
        double depth = Math.max(1e-9, centre.sub(camera).dot(forward));
        double radius = Math.max(body.radius(), 2.5 * depth / focal); // never smaller than a few pixels
        // Acheron: no map of it, just a dark grey shape.
        boolean unknown = body.id().equals("acheron");
        ResourceLocation map = unknown ? null : PlanetMaps.surface(body);
        int base = unknown ? 0x3C3C3C : body.color();
        if (map != null) {
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.setShaderTexture(0, map);
        } else {
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
        }
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,
                map != null ? DefaultVertexFormat.POSITION_TEX_COLOR : DefaultVertexFormat.POSITION_COLOR);
        int latSteps = 48;
        int lonSteps = 96;
        // A small world's outline bumps out where its ground does.
        float[] relief = com.thepearlking.stevespace.client.planet.PlanetMaps.relief(body);
        for (int i = 0; i < latSteps; i++) {
            double lat0 = Math.PI / 2 - i * Math.PI / latSteps;
            double lat1 = Math.PI / 2 - (i + 1) * Math.PI / latSteps;
            for (int j = 0; j < lonSteps; j++) {
                double lon0 = -Math.PI + j * 2 * Math.PI / lonSteps;
                double lon1 = -Math.PI + (j + 1) * 2 * Math.PI / lonSteps;
                DVec3 middle = surface(frame, (lat0 + lat1) / 2, (lon0 + lon1) / 2);
                if (centre.add(middle.scale(radius)).sub(camera).dot(middle) > 0) {
                    continue; // faces away from the camera
                }
                double[][] corners = {{lat0, lon0}, {lat1, lon0}, {lat1, lon1}, {lat0, lon1}};
                // A patch with a corner behind the camera is left out whole (a quad needs all four).
                float[][] projected = new float[4][];
                boolean behind = false;
                for (int k = 0; k < 4; k++) {
                    double lift = relief == null ? 1 : 1 + com.thepearlking.stevespace.client.planet.PlanetMaps.reliefAt(relief, corners[k][0], corners[k][1]);
                    projected[k] = project(centre.add(body.surface(frame, surface(frame, corners[k][0], corners[k][1])).scale(radius * lift)));
                    behind |= projected[k][2] <= 0;
                }
                if (behind) {
                    continue;
                }
                for (int k = 0; k < 4; k++) {
                    double[] c = corners[k];
                    DVec3 n = body.normal(frame, surface(frame, c[0], c[1]));
                    float[] p = projected[k];
                    float light = (float) Math.max(0.08, Math.min(1, n.dot(sun) * 1.1 + 0.1));
                    if (map != null) {
                        builder.addVertex(matrix, p[0], p[1], 0)
                                .setUv((float) ((c[1] + Math.PI) / (2 * Math.PI)), (float) ((Math.PI / 2 - c[0]) / Math.PI))
                                .setColor(light, light, light, 1);
                    } else {
                        builder.addVertex(matrix, p[0], p[1], 0).setColor(
                                ((base >> 16) & 255) / 255f * light, ((base >> 8) & 255) / 255f * light, (base & 255) / 255f * light, 1);
                    }
                }
            }
        }
        com.mojang.blaze3d.vertex.MeshData mesh = builder.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
    }

    private static DVec3 surface(SolarSystem.BodyFrame frame, double lat, double lon) {
        double x = Math.cos(lat) * Math.cos(lon);
        double y = Math.cos(lat) * Math.sin(lon);
        double z = Math.sin(lat);
        return frame.meridian().scale(x).add(frame.east90().scale(y)).add(frame.north().scale(z));
    }

    private static double gravityScale() {
        try {
            return SpaceConfig.GRAVITY_SCALE.get();
        } catch (IllegalStateException e) {
            return 1;
        }
    }

    /**
     * How far ahead to draw a path: a few hours around a planet, but long enough between the
     * planets (or leaving one) to show the whole way to the next world.
     */
    private double horizon(CelestialBody body, StateVector state) {
        double mu = body.mu() * gravityScale();
        double energy = state.velocity().lengthSquared() / 2 - mu / state.position().length();
        if (body.parent() == null || energy >= 0) {
            return 3_000_000;
        }
        return 6 * 3600;
    }
}
