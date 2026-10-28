package com.thepearlking.stevespace.client.gui;

import com.thepearlking.stevespace.network.RemoteActionC2S;
import com.thepearlking.stevespace.network.SpaceNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** A receiver's or relay's channel (which console it answers to), and a relay's function. */
public class RemoteTunerScreen extends Screen {

    private static final int W = 200;
    private static final int H = 74;
    private static final int RELAY_H = 100;

    private final BlockPos pos;
    private final int channel;
    private final int function;
    private int left;
    private int top;

    public RemoteTunerScreen(BlockPos pos, int channel, int function) {
        super(Component.translatable(function > 0 ? "screen.stevespace.rc_function" : "screen.stevespace.remote_receiver"));
        this.pos = pos;
        this.channel = channel;
        this.function = function;
    }

    public static void open(BlockPos pos, int channel, int function) {
        Minecraft.getInstance().setScreen(new RemoteTunerScreen(pos, channel, function));
    }

    private int h() {
        return function > 0 ? RELAY_H : H;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - h()) / 2;
        int[] steps = {-10, -1, 1, 10};
        String[] labels = {"-10", "-1", "+1", "+10"};
        for (int i = 0; i < 4; i++) {
            int step = steps[i];
            int bx = i < 2 ? left + 8 + i * 30 : left + W - 8 - (4 - i) * 30 + 2;
            addRenderableWidget(Button.builder(Component.literal(labels[i]), b -> tune(step)).bounds(bx, top + 26, 28, 16).build());
        }
        if (function > 0) {
            for (int i = 0; i < 5; i++) {
                int n = i + 1;
                Button b = addRenderableWidget(Button.builder(Component.literal("F" + n),
                        x -> SpaceNetwork.sendToServer(new RemoteActionC2S(pos, "relay_function", n, ""))).bounds(left + 16 + i * 34, top + 52, 32, 16).build());
                b.active = n != function;
            }
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(left + W / 2 - 30, top + h() - 24, 60, 16).build());
    }

    private void tune(int step) {
        SpaceNetwork.sendToServer(new RemoteActionC2S(pos, "tune", channel + step, ""));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.fill(left, top, left + W, top + h(), 0xF00B0F0D);
        g.renderOutline(left, top, W, h(), 0xFF3B4247);
        g.drawString(font, title, left + 8, top + 7, 0xFF66D98F, false);
        String ch = Component.translatable("screen.stevespace.remote.channel", String.format("%02d", channel)).getString();
        g.drawCenteredString(font, ch, left + W / 2, top + 30, 0xFFE0A43C);
        for (var child : renderables) {
            child.render(g, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
