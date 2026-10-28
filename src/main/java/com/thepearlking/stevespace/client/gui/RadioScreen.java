package com.thepearlking.stevespace.client.gui;

import com.thepearlking.stevespace.network.RadioC2S;
import com.thepearlking.stevespace.network.SpaceNetwork;
import com.thepearlking.stevespace.radio.Radio;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The radio: who else is on the air, how far, how long a message takes to get there, and
 * whether a planet is in the way. Type and send; replies come in on the chat.
 */
public class RadioScreen extends Screen {

    private static final int W = 340;
    private static final int ROW = 13;
    private static final int MAX_ROWS = 12;
    private static final int SCREEN = 0xF00B0F0D;
    private static final int FRAME = 0xFF3B4247;
    private static final int FRAME_LIGHT = 0xFF5A6268;
    private static final int GREEN = 0xFF66D98F;
    private static final int GREEN_DIM = 0xFF2F6E47;
    private static final int TEXT = 0xFFB8C4BC;
    private static final int GREY = 0xFF5E6862;

    private final List<Radio.Contact> contacts;
    private static final int TUNER = 34;
    private EditBox message;
    private int left;
    private int top;
    private int height2;

    /** What this radio is tuned to (MHz). */
    private final double mhz;

    public RadioScreen(List<Radio.Contact> contacts, double mhz) {
        super(Component.translatable("screen.stevespace.radio"));
        this.contacts = contacts;
        this.mhz = mhz;
    }

    public static void open(List<Radio.Contact> contacts, double mhz) {
        // Keep what was being typed when the screen comes back after tuning.
        String typing = Minecraft.getInstance().screen instanceof RadioScreen old && old.message != null ? old.message.getValue() : "";
        RadioScreen screen = new RadioScreen(contacts, mhz);
        Minecraft.getInstance().setScreen(screen);
        if (!typing.isEmpty() && screen.message != null) {
            screen.message.setValue(typing);
        }
    }

    private void tune(double step) {
        SpaceNetwork.sendToServer(new com.thepearlking.stevespace.network.RadioTuneC2S(mhz + step));
    }

    @Override
    protected void init() {
        int rows = Math.max(1, Math.min(MAX_ROWS, contacts.size()));
        height2 = 30 + 12 + rows * ROW + 40 + TUNER;
        left = (width - W) / 2;
        top = (height - height2) / 2;
        message = new EditBox(font, left + 10, top + height2 - 28, W - 76, 18, Component.translatable("screen.stevespace.radio.message"));
        message.setMaxLength(200);
        addRenderableWidget(message);
        setInitialFocus(message);
        addRenderableWidget(Button.builder(Component.translatable("screen.stevespace.radio.send"), b -> send())
                .bounds(left + W - 62, top + height2 - 28, 52, 18).build());
        // The tuner: down and up in big and small steps either side of the frequency readout.
        double[] steps = {-10, -1, -0.1, 0.1, 1, 10};
        String[] labels = {"-10", "-1", "-.1", "+.1", "+1", "+10"};
        int ty = top + height2 - 28 - TUNER + 2;
        for (int i = 0; i < steps.length; i++) {
            double step = steps[i];
            int bx = i < 3 ? left + 10 + i * 30 : left + W - 10 - (6 - i) * 30;
            addRenderableWidget(Button.builder(Component.literal(labels[i]), b -> tune(step)).bounds(bx, ty, 28, 16).build());
        }
    }

    private void send() {
        if (!message.getValue().isBlank()) {
            SpaceNetwork.sendToServer(new RadioC2S(message.getValue()));
            message.setValue("");
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
            send();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(left - 4, top - 4, left + W + 4, top + height2 + 4, FRAME);
        g.renderOutline(left - 4, top - 4, W + 8, height2 + 8, FRAME_LIGHT);
        g.fill(left, top, left + W, top + height2, SCREEN);
        g.drawString(font, title.copy().withStyle(s -> s.withBold(true)), left + 10, top + 8, GREEN, false);
        g.fill(left + 10, top + 20, left + W - 10, top + 21, GREEN_DIM);
        int y = top + 26;
        g.drawString(font, Component.translatable("screen.stevespace.radio.station"), left + 10, y, GREY, false);
        g.drawString(font, Component.translatable("screen.stevespace.radio.distance"), left + 168, y, GREY, false);
        g.drawString(font, Component.translatable("screen.stevespace.radio.delay"), left + 228, y, GREY, false);
        y += 12;
        if (contacts.isEmpty()) {
            g.drawString(font, Component.translatable("screen.stevespace.radio.empty"), left + 10, y, GREY, false);
        }
        for (Radio.Contact c : contacts.subList(0, Math.min(MAX_ROWS, contacts.size()))) {
            boolean sameChannel = Math.abs(c.mhz() - mhz) < 0.05;
            int color = c.visible() && sameChannel ? TEXT : GREY;
            String name = font.plainSubstrByWidth(c.name(), 150);
            g.drawString(font, name, left + 10, y, color, false);
            g.drawString(font, distance(c.distance()), left + 168, y, color, false);
            Component delay = c.visible() ? Component.literal(delay(c.distance() / Radio.LIGHT))
                    : Component.translatable("screen.stevespace.radio.blocked", c.blockedBy());
            g.drawString(font, delay, left + 228, y, c.visible() ? GREEN : GREY, false);
            String channel = String.format(java.util.Locale.ROOT, "%.1f", c.mhz());
            g.drawString(font, channel, left + W - 10 - font.width(channel), y, sameChannel ? GREEN : GREY, false);
            y += ROW;
        }
        // The frequency readout between the tuning buttons.
        String tuned = String.format(java.util.Locale.ROOT, "%.1f MHz", mhz);
        int ty = top + height2 - 28 - TUNER + 6;
        g.drawString(font, tuned, left + W / 2 - font.width(tuned) / 2, ty, GREEN, false);
        Component hint = Component.translatable("screen.stevespace.radio.channel_hint");
        g.drawString(font, hint, left + W / 2 - font.width(hint) / 2, ty - 11, GREY, false);
    }

    private static String distance(double blocks) {
        if (blocks < 10_000) {
            return String.format("%.0f km", blocks);
        }
        if (blocks < 10_000_000) {
            return String.format("%.0fk km", blocks / 1000);
        }
        return String.format("%.2f AU", blocks / com.thepearlking.stevespace.universe.SolarSystem.AU);
    }

    private static String delay(double seconds) {
        if (seconds < 1) {
            return String.format("%.0f ms", seconds * 1000);
        }
        if (seconds < 120) {
            return String.format("%.1f s", seconds);
        }
        return String.format("%.0f min", seconds / 60);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
