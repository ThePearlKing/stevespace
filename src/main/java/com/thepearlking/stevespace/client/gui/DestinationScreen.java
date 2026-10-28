package com.thepearlking.stevespace.client.gui;

import com.thepearlking.stevespace.client.ClientUniverse;
import com.thepearlking.stevespace.client.FlightInput;
import com.thepearlking.stevespace.network.AutopilotC2S;
import com.thepearlking.stevespace.network.SpaceNetwork;
import com.thepearlking.stevespace.universe.CelestialBody;
import com.thepearlking.stevespace.vessel.Autopilot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Where to fly: every world you can stand on, each with "Orbit" and "Land". */
public class DestinationScreen extends Screen {

    private static final int W = 360;
    private static final int ROW = 22;
    private static final int SCREEN = 0xF00B0F0D;
    private static final int FRAME = 0xFF3B4247;
    private static final int FRAME_LIGHT = 0xFF5A6268;
    private static final int GREEN = 0xFF66D98F;
    private static final int GREEN_DIM = 0xFF2F6E47;

    private final Screen parent;
    private final List<CelestialBody> worlds = new ArrayList<>();
    /** Estimated cost of flying to each world and landing, worked out in the background. */
    private final java.util.Map<String, java.util.concurrent.CompletableFuture<com.thepearlking.stevespace.vessel.RouteEstimate.Estimate>> estimates =
            new java.util.HashMap<>();
    private double shipDeltaV = Double.POSITIVE_INFINITY;
    private int top;
    private int left;
    /** First row shown, and how many fit on the screen (the list scrolls when they don't all fit). */
    private int scroll;
    private int visibleRows;

    public DestinationScreen(Screen parent) {
        super(Component.translatable("screen.stevespace.destination"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        worlds.clear();
        var system = ClientUniverse.system();
        worlds.add(system.sun()); // a solar orbit: orbit only
        for (CelestialBody body : system.bodies()) {
            if (body.isLandable() && !body.hidden()) {
                worlds.add(body);
            }
        }
        startEstimates();
        visibleRows = Math.max(3, Math.min(worlds.size(), (height - 8 - 42 - 30) / ROW));
        scroll = Math.max(0, Math.min(scroll, worlds.size() - visibleRows));
        int h = 42 + visibleRows * ROW + 30;
        left = (width - W) / 2;
        top = Math.max(4, (height - h) / 2);
        int y = top + 38;
        for (CelestialBody body : worlds.subList(scroll, scroll + visibleRows)) {
            addRenderableWidget(Button.builder(Component.translatable("screen.stevespace.destination.orbit"), b -> go(body, false))
                    .bounds(left + W - 112, y, 50, 18).build());
            Button land = Button.builder(Component.translatable("screen.stevespace.destination.land"), b -> go(body, true))
                    .bounds(left + W - 58, y, 50, 18).build();
            land.active = body.isLandable();
            addRenderableWidget(land);
            y += ROW;
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> minecraft.setScreen(parent))
                .bounds(left + 10, y + 4, W - 20, 18).build());
    }

    private void startEstimates() {
        if (!estimates.isEmpty()) {
            return;
        }
        var system = ClientUniverse.system();
        double t = ClientUniverse.time();
        CelestialBody here;
        com.thepearlking.stevespace.universe.StateVector ship = null;
        if (com.thepearlking.stevespace.client.space.ClientSpace.orbitActive()) {
            here = com.thepearlking.stevespace.client.space.ClientSpace.body();
            ship = com.thepearlking.stevespace.client.space.ClientSpace.stateAt(t);
        } else {
            here = ClientUniverse.currentBody();
        }
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null && com.thepearlking.stevespace.vessel.VesselEntity.shipOf(mc.player) instanceof com.thepearlking.stevespace.vessel.VesselEntity vessel
                && !com.thepearlking.stevespace.client.FlightHud.unlimitedPropellant(mc)) {
            shipDeltaV = vessel.stats().deltaV(vessel.propellant(), com.thepearlking.stevespace.config.SpaceConfig.FUEL_EFFICIENCY.get());
        }
        if (here == null) {
            return;
        }
        double scale = com.thepearlking.stevespace.config.SpaceConfig.GRAVITY_SCALE.get();
        final CelestialBody from = here;
        final com.thepearlking.stevespace.universe.StateVector state = ship;
        for (CelestialBody body : worlds) {
            estimates.put(body.id(), java.util.concurrent.CompletableFuture.supplyAsync(
                    () -> com.thepearlking.stevespace.vessel.RouteEstimate.of(system, from, state, t, body, true, scale),
                    net.minecraft.Util.backgroundExecutor()));
        }
    }

    private void go(CelestialBody body, boolean land) {
        FlightInput.setThrottle(0);
        SpaceNetwork.sendToServer(new AutopilotC2S(Autopilot.Program.TRAVEL, body.id(), land));
        onClose();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int h = 42 + visibleRows * ROW + 30;
        g.fill(left - 4, top - 4, left + W + 4, top + h + 4, FRAME);
        if (worlds.size() > visibleRows) {
            // Scroll bar along the right edge of the list.
            int listTop = top + 38;
            int listHeight = visibleRows * ROW;
            int barHeight = Math.max(12, listHeight * visibleRows / worlds.size());
            int barTop = listTop + (listHeight - barHeight) * scroll / Math.max(1, worlds.size() - visibleRows);
            g.fill(left + W - 5, listTop, left + W - 3, listTop + listHeight, 0xFF1B2220);
            g.fill(left + W - 5, barTop, left + W - 3, barTop + barHeight, GREEN_DIM);
        }
        g.renderOutline(left - 4, top - 4, W + 8, h + 8, FRAME_LIGHT);
        g.fill(left, top, left + W, top + h, SCREEN);
        g.drawString(font, title.copy().withStyle(s -> s.withBold(true)), left + 10, top + 8, GREEN, false);
        g.fill(left + 10, top + 20, left + W - 10, top + 21, GREEN_DIM);
        g.drawString(font, Component.translatable("screen.stevespace.destination.cost"), left + 140, top + 26, 0xFF5E6862, false);
        g.drawString(font, Component.translatable("screen.stevespace.destination.time"), left + 190, top + 26, 0xFF5E6862, false);
        int y = top + 38;
        CelestialBody here = com.thepearlking.stevespace.client.ClientUniverse.currentBody();
        for (CelestialBody body : worlds.subList(scroll, Math.min(worlds.size(), scroll + visibleRows))) {
            boolean current = here != null && here.id().equals(body.id());
            String name = body.displayName() + (body.parent() != null && body.parent() != ClientUniverse.system().sun()
                    && !body.parent().id().equals("sun") ? "  (" + body.parent().displayName() + ")" : "");
            g.drawString(font, font.plainSubstrByWidth(name, 124), left + 12, y + 5, current ? GREEN : 0xFFB8C4BC, false);
            var future = estimates.get(body.id());
            var estimate = future != null && future.isDone() ? future.getNow(null) : null;
            if (current) {
                // here already
            } else if (future == null || estimate == null) {
                g.drawString(font, future == null ? "" : "\u2026", left + 140, y + 5, 0xFF5E6862, false);
            } else if (Double.isInfinite(estimate.deltaV())) {
                g.drawString(font, "\u2014", left + 140, y + 5, 0xFF5E6862, false);
            } else {
                boolean enough = estimate.deltaV() <= shipDeltaV;
                g.drawString(font, String.format("%,.0f", estimate.deltaV()), left + 140, y + 5, enough ? 0xFFB8C4BC : 0xFFE6AD45, false);
                g.drawString(font, duration(estimate.seconds()), left + 190, y + 5, 0xFFB8C4BC, false);
            }
            y += ROW;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int next = Math.max(0, Math.min(worlds.size() - visibleRows, scroll - (int) Math.signum(scrollY)));
        if (next != scroll) {
            scroll = next;
            rebuildWidgets();
        }
        return true;
    }

    private static String duration(double seconds) {
        long s = (long) seconds;
        if (s >= 86400) {
            return String.format("%dd %02dh", s / 86400, (s % 86400) / 3600);
        }
        if (s >= 3600) {
            return String.format("%dh %02dm", s / 3600, (s % 3600) / 60);
        }
        return String.format("%dm", Math.max(1, s / 60));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
