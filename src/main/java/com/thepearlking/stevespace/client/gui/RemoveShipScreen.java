package com.thepearlking.stevespace.client.gui;

import com.thepearlking.stevespace.network.ShipRemoveC2S;
import com.thepearlking.stevespace.network.ShipRemoveS2C;
import com.thepearlking.stevespace.network.SpaceNetwork;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/**
 * "Are you sure?" before removing one of your ships: its picture, whether it has a docking port,
 * and (if it does: it may be part of a station) a few seconds before Yes can be pressed.
 */
public class RemoveShipScreen extends Screen {

    private static final int W = 240;
    private static final int H = 220;
    private static final int SCREEN = 0xF00B0F0D;
    private static final int FRAME = 0xFF3B4247;
    private static final int GREEN = 0xFF66D98F;
    private static final int RED = 0xFFE04848;
    private static final long PORT_WAIT_MS = 5000;

    private final Screen parent;
    private final UUID id;
    private ShipRemoveS2C info;
    private long shownAt;
    private Button yes;

    public RemoveShipScreen(Screen parent, UUID id) {
        super(Component.translatable("screen.stevespace.remove_ship"));
        this.parent = parent;
        this.id = id;
        SpaceNetwork.sendToServer(new ShipRemoveC2S(id, false));
    }

    public static void receive(ShipRemoveS2C msg) {
        if (Minecraft.getInstance().screen instanceof RemoveShipScreen screen && screen.id.equals(msg.id())) {
            screen.info = msg;
            screen.shownAt = Util.getMillis();
            screen.rebuildWidgets();
        }
    }

    @Override
    protected void init() {
        int left = (width - W) / 2;
        int top = (height - H) / 2;
        yes = Button.builder(Component.translatable("gui.yes"), b -> {
            SpaceNetwork.sendToServer(new ShipRemoveC2S(id, true));
            Minecraft.getInstance().setScreen(parent);
        }).bounds(left + W - 12 - 90, top + H - 28, 90, 20).build();
        yes.active = false;
        addRenderableWidget(yes);
        addRenderableWidget(Button.builder(Component.translatable("gui.no"), b -> onClose())
                .bounds(left + 12, top + H - 28, 90, 20).build());
    }

    private long waitLeft() {
        return info == null ? PORT_WAIT_MS : info.hasPort() ? Math.max(0, PORT_WAIT_MS - (Util.getMillis() - shownAt)) : 0;
    }

    @Override
    public void tick() {
        if (yes != null) {
            long left = waitLeft();
            yes.active = info != null && left == 0;
            yes.setMessage(info != null && left > 0
                    ? Component.translatable("screen.stevespace.remove_ship.wait", (left + 999) / 1000)
                    : Component.translatable("gui.yes"));
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int left = (width - W) / 2;
        int top = (height - H) / 2;
        g.fill(left - 4, top - 4, left + W + 4, top + H + 4, FRAME);
        g.fill(left, top, left + W, top + H, SCREEN);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int left = (width - W) / 2;
        int top = (height - H) / 2;
        g.drawCenteredString(font, Component.translatable("screen.stevespace.remove_ship.sure").withStyle(s -> s.withBold(true)),
                left + W / 2, top + 8, RED);
        if (info == null) {
            return;
        }
        ShipPicture.draw(g, info.shape(), null, left + 12, top + 22, W - 24, 120, info.name());
        g.drawCenteredString(font, Component.translatable(info.hasPort() ? "screen.stevespace.remove_ship.port"
                : "screen.stevespace.remove_ship.no_port"), left + W / 2, top + 150, info.hasPort() ? RED : GREEN);
        g.drawCenteredString(font, Component.translatable("screen.stevespace.remove_ship.gone"), left + W / 2, top + 164, 0xFFB8D8C4);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
