package com.thepearlking.stevespace.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.thepearlking.stevespace.block.FacingBlock;
import com.thepearlking.stevespace.block.FlightDisplayBlockEntity;
import com.thepearlking.stevespace.client.space.ClientSpace;
import com.thepearlking.stevespace.universe.CelestialBody;
import com.thepearlking.stevespace.universe.DVec3;
import com.thepearlking.stevespace.universe.Kepler;
import com.thepearlking.stevespace.universe.OrbitalElements;
import com.thepearlking.stevespace.universe.SolarSystem;
import com.thepearlking.stevespace.universe.StateVector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * The flight display's readout: in orbit (aboard a parked ship or station) the body, height,
 * speed and the orbit's high and low points; on the ground the world, its gravity and the Sun's
 * height. Worked out on the client from what it already knows, every frame.
 */
public class FlightDisplayRenderer implements BlockEntityRenderer<FlightDisplayBlockEntity> {

    private static final int GREEN = 0xFF66D98F;
    private static final int DIM = 0xFF2F7A48;
    private final Font font;

    public FlightDisplayRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(FlightDisplayBlockEntity display, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.distanceToSqr(display.getBlockPos().getCenter()) > 24 * 24) {
            return;
        }
        drawScreen(font, readout(mc), display.getBlockState(), pose, buffers);
    }

    /** Draws label/value rows on the front face of a facing block's screen. */
    public static void drawScreen(Font font, List<String[]> lines, net.minecraft.world.level.block.state.BlockState state,
                                  PoseStack pose, MultiBufferSource buffers) {
        Direction facing = state.getValue(FacingBlock.FACING);
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        // Front face, just in front of it; text runs left to right, top to bottom.
        pose.translate(0, 0, 0.5005);
        pose.scale(1 / 16f, -1 / 16f, 1 / 16f);
        pose.translate(-6, -5.6, 0);
        float s = 0.16f;
        pose.scale(s, s, s);
        int y = 0;
        for (String[] line : lines) {
            font.drawInBatch(line[0], 0, y, DIM, false, pose.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET, 0,
                    LightTexture.FULL_BRIGHT);
            int w = font.width(line[1]);
            font.drawInBatch(line[1], 75 - w, y, GREEN, false, pose.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET, 0,
                    LightTexture.FULL_BRIGHT);
            y += 13;
        }
        pose.popPose();
    }

    private static List<String[]> readout(Minecraft mc) {
        List<String[]> lines = new ArrayList<>();
        SolarSystem system = ClientUniverse.system();
        var cell = ClientSpace.cellOrbit();
        if (cell != null && system != null) {
            CelestialBody body = system.body(cell.body());
            if (body != null) {
                double mu = body.mu() * com.thepearlking.stevespace.config.SpaceConfig.GRAVITY_SCALE.get();
                double[] st = cell.state();
                StateVector at = new StateVector(new DVec3(st[0], st[1], st[2]), new DVec3(st[3], st[4], st[5]));
                OrbitalElements orbit = Kepler.elementsFromState(at, mu, cell.time());
                StateVector now = orbit.stateAt(ClientUniverse.time(), mu);
                lines.add(new String[]{"ORBIT", body.displayName().toUpperCase(java.util.Locale.ROOT)});
                lines.add(new String[]{"ALT", length(now.position().length() - body.radius())});
                lines.add(new String[]{"SPD", String.format("%.0f m/s", now.velocity().length())});
                lines.add(new String[]{"AP", orbit.eccentricity() < 1 ? length(orbit.apoapsis() - body.radius()) : "ESC"});
                lines.add(new String[]{"PE", length(orbit.periapsis() - body.radius())});
                if (orbit.eccentricity() < 1) {
                    lines.add(new String[]{"PERIOD", duration(orbit.period(mu))});
                }
                return lines;
            }
        }
        CelestialBody body = ClientUniverse.currentBody();
        if (body == null) {
            lines.add(new String[]{"NO DATA", ""});
            return lines;
        }
        lines.add(new String[]{"BODY", body.displayName().toUpperCase(java.util.Locale.ROOT)});
        lines.add(new String[]{"GRAV", String.format("%.2f g", body.surfaceGravity() / SolarSystem.EARTH_GRAVITY)});
        lines.add(new String[]{"ALT", length(mc.player.getY() - 63)});
        lines.add(new String[]{"DAY", duration(solarDay(system, body))});
        lines.add(new String[]{"AIR", body.hasAtmosphere() && body.id().equals("earth") ? "BREATHABLE" : (body.hasAtmosphere() ? "TOXIC" : "NONE")});
        return lines;
    }

    /** Sunrise to sunrise, in seconds. */
    private static double solarDay(SolarSystem sys, CelestialBody body) {
        CelestialBody planet = body;
        while (planet.parent() != null && !planet.parent().id().equals(sys.sun().id())) {
            planet = planet.parent();
        }
        double year = planet.orbit().period(sys.sun().mu());
        double rotation = body.isTidallyLocked() ? body.orbit().period(body.parent().mu()) : sys.siderealPeriod(body);
        return Math.abs(1 / (1 / rotation - 1 / year));
    }

    private static String length(double blocks) {
        if (Math.abs(blocks) >= 10_000) {
            return String.format("%.0f km", blocks / 1000);
        }
        if (Math.abs(blocks) >= 1000) {
            return String.format("%.1f km", blocks / 1000);
        }
        return String.format("%.0f m", blocks);
    }

    private static String duration(double seconds) {
        long s = Math.round(seconds);
        if (s >= 86400) {
            return String.format("%dd %02dh", s / 86400, (s % 86400) / 3600);
        }
        return String.format("%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }
}
