package com.thepearlking.stevespace.client;

import com.thepearlking.stevespace.block.PilotSeatBlock;
import com.thepearlking.stevespace.block.SeatEntity;
import com.thepearlking.stevespace.config.SpaceConfig;
import com.thepearlking.stevespace.universe.CelestialBody;
import com.thepearlking.stevespace.vessel.VesselEntity;
import com.thepearlking.stevespace.world.planet.PlanetGeometry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.joml.Vector3f;

import java.util.Locale;

/**
 * Flight instruments drawn in the corner of the screen while piloting: altitude, speeds, the
 * orbit you are on, propellant, throttle. Styled like a modern glass cockpit display.
 */
public final class FlightHud {

    private static final int WIDTH = 150;
    private static final int PADDING = 6;
    private static final int LINE = 10;

    private static final int BACKGROUND = 0xC80D1114;
    private static final int BORDER = 0xFF2E353B;
    private static final int LABEL = 0xFF8E989F;
    private static final int VALUE = 0xFFE8ECEE;
    private static final int GOOD = 0xFF62D08A;
    private static final int WARN = 0xFFE5A83B;
    private static final int BAD = 0xFFE0614F;
    private static final int BAR_BACK = 0xFF1B2126;

    private FlightHud() {
    }

    /** A layer of the vanilla HUD. */
    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        double warp = ClientUniverse.warp();
        if (warp > 1) {
            String text = Component.translatable("hud.stevespace.warp", String.format(Locale.ROOT, "%,d", (long) warp)).getString();
            int w = mc.font.width(text) + 12;
            int x = (graphics.guiWidth() - w) / 2;
            graphics.fill(x, 4, x + w, 18, BACKGROUND);
            graphics.renderOutline(x, 4, w, 14, BORDER);
            graphics.drawString(mc.font, text, x + 6, 7, WARN, false);
        }
        boolean passenger = VesselEntity.shipOf(mc.player) != null && !FlightInput.isPiloting();
        if (!FlightInput.isPiloting() && !passenger) {
            return;
        }
        if (VesselEntity.shipOf(mc.player) instanceof VesselEntity vessel && vessel.stats() != null) {
            renderFlight(graphics, mc, vessel);
        } else if (mc.player.getVehicle() instanceof SeatEntity seat
                && mc.level.getBlockState(seat.blockPosition()).getBlock() instanceof PilotSeatBlock) {
            renderOnPad(graphics, mc);
        }
    }

    private static com.thepearlking.stevespace.vessel.VesselReport padReport;
    private static boolean padNeedsPropellant;
    private static long padReportTime;

    /** The server's latest check of the vessel around the pilot's seat. */
    public static void setPadStatus(com.thepearlking.stevespace.vessel.VesselReport report, boolean needsPropellant) {
        padReport = report;
        padNeedsPropellant = needsPropellant;
        padReportTime = net.minecraft.Util.getMillis();
    }

    private static void renderOnPad(GuiGraphics g, Minecraft mc) {
        Font font = mc.font;
        var report = net.minecraft.Util.getMillis() - padReportTime < 2000 ? padReport : null;
        Component status;
        int statusColor;
        if (report == null) {
            status = Component.translatable("hud.stevespace.checking");
            statusColor = LABEL;
        } else if (!report.ready()) {
            status = com.thepearlking.stevespace.vessel.VesselReport.describe(report.problems().get(0));
            statusColor = BAD;
        } else if (padNeedsPropellant && report.propellant() <= 0) {
            status = Component.translatable("stevespace.vessel.problem.no_propellant");
            statusColor = BAD;
        } else {
            status = Component.translatable("hud.stevespace.ready");
            statusColor = GOOD;
        }
        int rows = report != null ? 5 : 3;
        int x = g.guiWidth() - WIDTH - PADDING;
        int height = PADDING * 2 + LINE * rows + 6;
        int y = g.guiHeight() - height - PADDING;
        panel(g, x, y, WIDTH, height);
        int cy = y + PADDING;
        g.drawString(font, Component.translatable("hud.stevespace.pre_launch"), x + PADDING, cy, LABEL, false);
        cy += LINE;
        g.drawString(font, status, x + PADDING, cy, statusColor, false);
        cy += LINE;
        if (report != null) {
            row(g, font, x, cy, "hud.stevespace.twr", String.format(Locale.ROOT, "%.2f", report.thrustToWeight()),
                    report.thrustToWeight() < 1 ? BAD : VALUE);
            cy += LINE;
            String dv = padNeedsPropellant ? speed(report.deltaV()) : Component.translatable("hud.stevespace.unlimited").getString();
            row(g, font, x, cy, "hud.stevespace.delta_v", dv, VALUE);
            cy += LINE;
        }
        cy += 2;
        bar(g, font, x + PADDING, cy, Component.translatable("hud.stevespace.throttle"), FlightInput.throttle(), VALUE);
        hint(g, font, x, y - 11);
    }

    /** Creative pilots fly on unlimited propellant (when the server allows it). */
    public static boolean unlimitedPropellant(Minecraft mc) {
        return mc.player.getAbilities().instabuild && SpaceConfig.CREATIVE_INFINITE_FUEL.get();
    }

    private static void renderFlight(GuiGraphics g, Minecraft mc, VesselEntity vessel) {
        Font font = mc.font;
        Vector3f v = vessel.velocity();
        double altitude = vessel.getY() - 63;
        double speed = Math.sqrt(v.x * v.x + v.y * v.y + v.z * v.z);
        double verticalSpeed = v.y;
        CelestialBody space = null;
        double[] spaceApsides = null;
        double eccentricity = 0;
        if (com.thepearlking.stevespace.client.space.ClientSpace.mapModeActive()) {
            altitude = com.thepearlking.stevespace.client.space.ClientSpace.mapPosition().y - 63;
        }
        if (com.thepearlking.stevespace.client.space.ClientSpace.orbitActive()) {
            // In space: everything from the orbit itself.
            space = com.thepearlking.stevespace.client.space.ClientSpace.body();
            var state = com.thepearlking.stevespace.client.space.ClientSpace.stateAt(ClientUniverse.time());
            var orbit = com.thepearlking.stevespace.client.space.ClientSpace.orbit();
            if (space != null && state != null && orbit != null) {
                double r = state.position().length();
                altitude = r - space.radius();
                speed = state.velocity().length();
                verticalSpeed = state.velocity().dot(state.position()) / r;
                eccentricity = orbit.eccentricity();
                spaceApsides = new double[]{orbit.periapsis() - space.radius(),
                        eccentricity < 1 ? orbit.apoapsis() - space.radius() : Double.POSITIVE_INFINITY};
            }
        }
        double propellantFraction = vessel.stats().propellantCapacity() > 0
                ? vessel.propellant() / vessel.stats().propellantCapacity() : 0;
        double mass = vessel.stats().dryMass() + vessel.propellant();
        CelestialBody body = space != null ? space : ClientUniverse.currentBody();
        double gravity = body != null ? body.surfaceGravity() * SpaceConfig.GRAVITY_SCALE.get() : 9.81;
        double twr = vessel.stats().thrustVector().length() * SpaceConfig.THRUST_SCALE.get() / (mass * gravity);
        double deltaV = vessel.stats().deltaV(vessel.propellant(), SpaceConfig.FUEL_EFFICIENCY.get());

        boolean showTarget = com.thepearlking.stevespace.client.space.ClientTarget.active() && space != null
                && com.thepearlking.stevespace.client.space.ClientTarget.body() != null
                && com.thepearlking.stevespace.client.space.ClientTarget.body().id().equals(space.id());
        // Inside a giant: the pressure outside, and how close the hull is to giving way.
        boolean inGiant = space == null && com.thepearlking.stevespace.client.space.ClientSpace.mapModeActive()
                && com.thepearlking.stevespace.universe.GiantAir.has(body);
        double stress = inGiant ? com.thepearlking.stevespace.universe.GiantAir.stress(altitude) : 0;
        int rows = (showTarget ? 10 : 8) + (inGiant ? 1 : 0);
        String detail = vessel.autopilotStatus().isEmpty() ? "" : vessel.autopilotDetail();
        int height = PADDING * 2 + LINE * rows + 14 + (detail.isEmpty() ? 0 : LINE);
        int x = g.guiWidth() - WIDTH - PADDING;
        int y = g.guiHeight() - height - PADDING;
        panel(g, x, y, WIDTH, height);
        int cy = y + PADDING;

        row(g, font, x, cy, "hud.stevespace.altitude", distance(altitude), VALUE);
        cy += LINE;
        row(g, font, x, cy, "hud.stevespace.vertical_speed", signedSpeed(verticalSpeed), verticalSpeed < -15 && space == null ? WARN : VALUE);
        cy += LINE;
        row(g, font, x, cy, "hud.stevespace.speed", speed(speed), VALUE);
        cy += LINE;

        double[] apsides = spaceApsides != null ? spaceApsides : (body != null && space == null ? apsides(body, vessel, v) : null);
        String apoapsis = apsides == null || Double.isInfinite(apsides[1]) ? "—" : distance(apsides[1]);
        String periapsis = apsides == null ? "—" : (apsides[0] < -body.radius() * 0.5 ? "—" : distance(apsides[0]));
        boolean orbiting = apsides != null && body.hasAtmosphere() && apsides[0] > body.atmosphere().height()
                || apsides != null && !body.hasAtmosphere() && apsides[0] > 10;
        row(g, font, x, cy, "hud.stevespace.apoapsis", apoapsis, VALUE);
        cy += LINE;
        row(g, font, x, cy, "hud.stevespace.periapsis", periapsis, orbiting ? GOOD : VALUE);
        cy += LINE;
        boolean unlimited = unlimitedPropellant(mc);
        row(g, font, x, cy, "hud.stevespace.delta_v", unlimited ? Component.translatable("hud.stevespace.unlimited").getString() : speed(deltaV),
                deltaV < 30 && !unlimited ? WARN : VALUE);
        cy += LINE;
        row(g, font, x, cy, "hud.stevespace.twr", String.format(Locale.ROOT, "%.2f", twr), twr < 1 && !vessel.isGrounded() ? WARN : VALUE);
        cy += LINE;
        if (inGiant) {
            double bar = com.thepearlking.stevespace.universe.GiantAir.pressure(altitude);
            row(g, font, x, cy, "hud.stevespace.pressure", String.format(Locale.ROOT, bar < 10 ? "%.2f bar" : "%.0f bar", bar),
                    stress > 0.6 ? BAD : stress > 0 ? WARN : VALUE);
            cy += LINE;
        }
        cy += 2;
        bar(g, font, x + PADDING, cy, Component.translatable("hud.stevespace.throttle"), vessel.throttle(), VALUE);
        cy += LINE;
        if (unlimited) {
            row(g, font, x, cy, "hud.stevespace.propellant", Component.translatable("hud.stevespace.unlimited").getString(), VALUE);
        } else {
            bar(g, font, x + PADDING, cy, Component.translatable("hud.stevespace.propellant"), (float) propellantFraction,
                    propellantFraction < 0.1 ? BAD : (propellantFraction < 0.25 ? WARN : VALUE));
        }
        cy += LINE + 2;

        // The target: how far, and how fast it's coming or going.
        if (showTarget) {
            var own = com.thepearlking.stevespace.client.space.ClientSpace.stateAt(ClientUniverse.time());
            var target = com.thepearlking.stevespace.client.space.ClientTarget.stateAt(ClientUniverse.time());
            if (own != null && target != null) {
                double distance = target.position().sub(own.position()).length();
                double closing = target.velocity().sub(own.velocity()).length();
                row(g, font, x, cy, "hud.stevespace.target_distance", distance(distance), VALUE);
                cy += LINE;
                row(g, font, x, cy, "hud.stevespace.target_speed", speed(closing).replace(" m/s", String.format(Locale.ROOT, ".%01d m/s",
                        (int) (closing * 10) % 10)), closing > 5 && distance < 500 ? WARN : VALUE);
                cy += LINE;
            }
        }
        Component status;
        int statusColor;
        if (!vessel.autopilotStatus().isEmpty()) {
            status = Component.translatable("hud.stevespace.autopilot").append(": ").append(Component.translatable(vessel.autopilotStatus()));

            statusColor = GOOD;
        } else if (stress > 0) {
            status = Component.translatable("hud.stevespace.status.crushing");
            statusColor = stress > 0.6 ? BAD : WARN;
        } else if (vessel.isGrounded()) {
            status = Component.translatable("hud.stevespace.status.landed");
            statusColor = LABEL;
        } else if (vessel.propellant() <= 0 && vessel.throttle() > 0 && !unlimited) {
            status = Component.translatable("hud.stevespace.status.no_propellant");
            statusColor = BAD;
        } else if (space != null && eccentricity >= 1) {
            status = Component.translatable("hud.stevespace.status.escape", space.displayName());
            statusColor = WARN;
        } else if (orbiting) {
            status = space != null ? Component.translatable("hud.stevespace.status.orbiting", space.displayName())
                    : Component.translatable("hud.stevespace.status.orbit");
            statusColor = GOOD;
        } else if (space != null) {
            status = Component.translatable("hud.stevespace.status.suborbital");
            statusColor = WARN;
        } else {
            status = Component.translatable("hud.stevespace.status.flight");
            statusColor = VALUE;
        }
        g.drawString(font, status, x + PADDING, cy, statusColor, false);
        if (!detail.isEmpty()) {
            g.drawString(font, detail, x + PADDING, cy + LINE, VALUE, false);
        }
        hint(g, font, x, y - 11);
    }

    /**
     * Periapsis and apoapsis altitudes of the current trajectory around the body (blocks above sea
     * level), from position and velocity including the planet's rotation.
     */
    private static double[] apsides(CelestialBody body, VesselEntity vessel, Vector3f v) {
        double mu = body.mu() * SpaceConfig.GRAVITY_SCALE.get();
        net.minecraft.world.phys.Vec3 at = com.thepearlking.stevespace.client.space.ClientSpace.flightPosition(vessel, vessel.position(), 1);
        double r = body.radius() + at.y - 63;
        PlanetGeometry geometry = new PlanetGeometry(body.radius());
        double latitude = geometry.latitude(at.z);
        double spin = 2 * Math.PI / ClientUniverse.system().siderealPeriod(body);
        double east = v.x + spin * r * Math.cos(latitude);
        double horizontal = Math.sqrt(east * east + v.z * v.z);
        double speed2 = horizontal * horizontal + v.y * v.y;
        double energy = speed2 / 2 - mu / r;
        double h = r * horizontal;
        double e = Math.sqrt(Math.max(0, 1 + 2 * energy * h * h / (mu * mu)));
        double p = h * h / mu;
        double rp = p / (1 + e);
        double ra = e < 1 ? p / (1 - e) : Double.POSITIVE_INFINITY;
        return new double[]{rp - body.radius(), ra - body.radius()};
    }

    private static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, BACKGROUND);
        g.renderOutline(x, y, w, h, BORDER);
    }

    private static void row(GuiGraphics g, Font font, int x, int y, String label, String value, int color) {
        g.drawString(font, Component.translatable(label), x + PADDING, y, LABEL, false);
        g.drawString(font, value, x + WIDTH - PADDING - font.width(value), y, color, false);
    }

    private static void bar(GuiGraphics g, Font font, int x, int y, Component label, float fraction, int color) {
        g.drawString(font, label, x, y, LABEL, false);
        int barX = x + 52;
        int barW = WIDTH - PADDING * 2 - 52 - 26;
        g.fill(barX, y + 1, barX + barW, y + 7, BAR_BACK);
        g.fill(barX, y + 1, barX + Math.round(barW * Math.max(0, Math.min(1, fraction))), y + 7, color);
        String pct = Math.round(fraction * 100) + "%";
        g.drawString(font, pct, x + WIDTH - PADDING * 2 - font.width(pct), y, VALUE, false);
    }

    private static void hint(GuiGraphics g, Font font, int x, int y) {
        if (com.thepearlking.stevespace.client.vessel.CabinWalk.walking()) {
            Component text = Component.translatable("hud.stevespace.keys_walking", FlightKeys.MAP.getTranslatedKeyMessage(),
                    FlightKeys.LEAVE_SEAT.getTranslatedKeyMessage());
            g.drawString(font, text, x + WIDTH - font.width(text), y, 0xB0FFFFFF, true);
            return;
        }
        if (!FlightInput.isPiloting()) {
            Component text = Component.translatable("hud.stevespace.keys_passenger", FlightKeys.MAP.getTranslatedKeyMessage(),
                    FlightKeys.LEAVE_SEAT.getTranslatedKeyMessage());
            g.drawString(font, text, x + WIDTH - font.width(text), y, 0xB0FFFFFF, true);
            return;
        }
        if (FlightInput.rcsMode()) {
            Component rcs = Component.translatable("hud.stevespace.keys_rcs", FlightKeys.THROTTLE_UP.getTranslatedKeyMessage(),
                    FlightKeys.THROTTLE_DOWN.getTranslatedKeyMessage(), FlightKeys.RCS_MODE.getTranslatedKeyMessage());
            g.drawString(font, rcs, x + WIDTH - font.width(rcs), y, 0xE0FFD966, true);
            return;
        }
        Component text = Component.translatable("hud.stevespace.keys",
                FlightKeys.THROTTLE_UP.getTranslatedKeyMessage(), FlightKeys.THROTTLE_DOWN.getTranslatedKeyMessage(),
                FlightKeys.AUTOPILOT.getTranslatedKeyMessage(), FlightKeys.MAP.getTranslatedKeyMessage(),
                FlightKeys.LEAVE_SEAT.getTranslatedKeyMessage());
        g.drawString(font, text, x + WIDTH - font.width(text), y, 0xB0FFFFFF, true);
    }

    private static String distance(double blocks) {
        return com.thepearlking.stevespace.client.gui.FlightText.distance(blocks);
    }

    private static String speed(double value) {
        return com.thepearlking.stevespace.client.gui.FlightText.speed(value);
    }

    private static String signedSpeed(double value) {
        return com.thepearlking.stevespace.client.gui.FlightText.signedSpeed(value);
    }
}
