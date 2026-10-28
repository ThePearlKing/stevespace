package com.thepearlking.stevespace.client.gui;

import com.thepearlking.stevespace.vessel.VesselReport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Pre-flight check: the vessel's mass, propulsion and propellant, and whether it is ready.
 * Drawn as a dark instrument display with green/amber readouts.
 */
public class FlightComputerScreen extends Screen {

    private static final int W = 248;
    private static final int H = 188;

    private static final int SCREEN = 0xF00B0F0D;
    private static final int FRAME = 0xFF3B4247;
    private static final int FRAME_LIGHT = 0xFF5A6268;
    private static final int GREEN = 0xFF66D98F;
    private static final int GREEN_DIM = 0xFF2F6E47;
    private static final int AMBER = 0xFFE6AD45;
    private static final int RED = 0xFFE36450;
    private static final int TEXT = 0xFFD7E3DA;

    private final VesselReport report;

    private FlightComputerScreen(VesselReport report) {
        super(Component.translatable("screen.stevespace.flight_computer"));
        this.report = report;
    }

    public static void open(VesselReport report) {
        Minecraft.getInstance().setScreen(new FlightComputerScreen(report));
    }

    @Override
    protected void init() {
        int left = (width - W) / 2;
        int top = (height - H) / 2;
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(left + W / 2 - 50, top + H + 6, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int left = (width - W) / 2;
        int top = (height - H) / 2;
        Font font = this.font;

        // Bezel and screen
        g.fill(left - 4, top - 4, left + W + 4, top + H + 4, FRAME);
        g.renderOutline(left - 4, top - 4, W + 8, H + 8, FRAME_LIGHT);
        g.fill(left, top, left + W, top + H, SCREEN);

        int x = left + 10;
        int y = top + 8;
        g.drawString(font, title.copy().withStyle(s -> s.withBold(true)), x, y, GREEN, false);
        String parts = Component.translatable("screen.stevespace.fc.parts", report.blocks()).getString();
        g.drawString(font, parts, left + W - 10 - font.width(parts), y, GREEN_DIM, false);
        y += 12;
        g.fill(x, y, left + W - 10, y + 1, GREEN_DIM);
        y += 6;

        int col2 = left + W / 2 + 4;
        section(g, font, x, y, "screen.stevespace.fc.mass");
        section(g, font, col2, y, "screen.stevespace.fc.propulsion");
        y += 11;
        value(g, font, x, y, "screen.stevespace.fc.dry_mass", String.format(Locale.ROOT, "%.1f t", report.dryMass()));
        value(g, font, col2, y, "screen.stevespace.fc.engines", String.valueOf(report.engines()));
        y += 10;
        value(g, font, x, y, "screen.stevespace.fc.total_mass", String.format(Locale.ROOT, "%.1f t", report.totalMass()));
        value(g, font, col2, y, "screen.stevespace.fc.thrust", String.format(Locale.ROOT, "%,.0f kN", report.thrust()));
        y += 10;
        value(g, font, x, y, "screen.stevespace.fc.seats", String.valueOf(report.seats()));
        double twr = report.thrustToWeight();
        valueColored(g, font, col2, y, "screen.stevespace.fc.twr", String.format(Locale.ROOT, "%.2f", twr),
                report.engines() == 0 ? TEXT : (twr < 1 ? RED : (twr < 1.3 ? AMBER : GREEN)));
        y += 16;

        section(g, font, x, y, "screen.stevespace.fc.propellant");
        y += 11;
        double fraction = report.capacity() > 0 ? report.propellant() / report.capacity() : 0;
        int barW = W - 20;
        g.fill(x, y, x + barW, y + 8, 0xFF16201A);
        g.fill(x, y, x + (int) Math.round(barW * fraction), y + 8, fraction < 0.1 ? AMBER : GREEN);
        y += 11;
        String prop = String.format(Locale.ROOT, "%.1f / %.1f t", report.propellant(), report.capacity());
        g.drawString(font, prop, x, y, TEXT, false);
        String dv = Component.translatable("screen.stevespace.fc.delta_v", String.format(Locale.ROOT, "%,.0f", report.deltaV())).getString();
        g.drawString(font, dv, left + W - 10 - font.width(dv), y, TEXT, false);
        y += 16;

        g.fill(x, y, left + W - 10, y + 1, GREEN_DIM);
        y += 6;
        if (report.ready()) {
            g.drawString(font, Component.translatable("screen.stevespace.fc.ready"), x, y, GREEN, false);
            y += 11;
            g.drawString(font, Component.translatable("screen.stevespace.fc.ready_hint", com.thepearlking.stevespace.client.FlightKeys.THROTTLE_UP.getTranslatedKeyMessage()), x, y, GREEN_DIM, false);
        } else {
            for (String problem : report.problems()) {
                g.drawString(font, Component.literal("▲ ").append(com.thepearlking.stevespace.vessel.VesselReport.describe(problem)), x, y, AMBER, false);
                y += 11;
            }
        }
    }

    private static void section(GuiGraphics g, Font font, int x, int y, String key) {
        g.drawString(font, Component.translatable(key), x, y, GREEN_DIM, false);
    }

    private static void value(GuiGraphics g, Font font, int x, int y, String key, String value) {
        valueColored(g, font, x, y, key, value, TEXT);
    }

    private static void valueColored(GuiGraphics g, Font font, int x, int y, String key, String value, int color) {
        g.drawString(font, Component.translatable(key), x, y, 0xFF93A59A, false);
        g.drawString(font, value, x + 108 - font.width(value), y, color, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
