package com.thepearlking.stevespace.client.gui;

import com.thepearlking.stevespace.client.ClientUniverse;
import com.thepearlking.stevespace.network.MissionControlS2C;
import com.thepearlking.stevespace.universe.DVec3;
import com.thepearlking.stevespace.universe.StateVector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Mission Control: every vessel on the server, where it is, and a button to follow it on the map. */
public class MissionControlScreen extends Screen {

    private static final int W = 300;
    private static final int ROW = 22;
    private static final int SCREEN = 0xF00B0F0D;
    private static final int FRAME = 0xFF3B4247;
    private static final int FRAME_LIGHT = 0xFF5A6268;
    private static final int GREEN = 0xFF66D98F;
    private static final int GREEN_DIM = 0xFF2F6E47;
    private static final int TEXT = 0xFFD7E3DA;
    private static final int LABEL = 0xFF93A59A;

    private final MissionControlS2C data;

    private MissionControlScreen(MissionControlS2C data) {
        super(Component.translatable("screen.stevespace.mission_control"));
        this.data = data;
    }

    public static void open(MissionControlS2C data) {
        Minecraft.getInstance().setScreen(new MissionControlScreen(data));
    }

    private int height() {
        return 40 + Math.max(1, Math.min(8, data.vessels().size())) * ROW;
    }

    @Override
    protected void init() {
        int left = (width - W) / 2;
        int top = (height - height()) / 2;
        List<MissionControlS2C.Entry> vessels = data.vessels();
        for (int i = 0; i < Math.min(8, vessels.size()); i++) {
            MissionControlS2C.Entry e = vessels.get(i);
            int y = top + 30 + i * ROW;
            addRenderableWidget(Button.builder(Component.translatable("screen.stevespace.mission_control.track"), b -> track(e))
                    .bounds(left + W - 58, y, 50, 18).build());
            addRenderableWidget(Button.builder(Component.translatable("screen.stevespace.mission_control.target"), b -> {
                        com.thepearlking.stevespace.network.SpaceNetwork.sendToServer(new com.thepearlking.stevespace.network.SetTargetC2S(e.id()));
                        onClose();
                    }).bounds(left + W - 112, y, 50, 18).build());
        }
    }

    private void track(MissionControlS2C.Entry e) {
        var state = new StateVector(new DVec3(e.state()[0], e.state()[1], e.state()[2]),
                new DVec3(e.state()[3], e.state()[4], e.state()[5]));
        var body = ClientUniverse.system().body(e.body());
        if (body != null) {
            Minecraft.getInstance().setScreen(new MapScreen(new MapScreen.Tracked(e.name(), body, state, data.time(), e.mode() == 0)));
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        int left = (width - W) / 2;
        int top = (height - height()) / 2;
        int h = height();
        g.fill(left - 4, top - 4, left + W + 4, top + h + 4, FRAME);
        g.renderOutline(left - 4, top - 4, W + 8, h + 8, FRAME_LIGHT);
        g.fill(left, top, left + W, top + h, SCREEN);
        g.drawString(font, title.copy().withStyle(s -> s.withBold(true)), left + 10, top + 8, GREEN, false);
        g.fill(left + 10, top + 20, left + W - 10, top + 21, GREEN_DIM);
        List<MissionControlS2C.Entry> vessels = data.vessels();
        if (vessels.isEmpty()) {
            g.drawString(font, Component.translatable("screen.stevespace.mission_control.none"), left + 10, top + 32, LABEL, false);
        }
        for (int i = 0; i < Math.min(8, vessels.size()); i++) {
            MissionControlS2C.Entry e = vessels.get(i);
            int y = top + 30 + i * ROW;
            var body = ClientUniverse.system().body(e.body());
            String where = body == null ? e.body() : body.displayName();
            String key = switch (e.mode()) {
                case 0 -> "screen.stevespace.mission_control.landed";
                case 1 -> "screen.stevespace.mission_control.flying";
                default -> "screen.stevespace.mission_control.orbit";
            };
            g.drawString(font, e.name(), left + 10, y, TEXT, false);
            String status = Component.translatable(key, where).getString();
            if (e.mode() != 0) {
                status += "  " + FlightText.distance(e.altitude()) + "  " + FlightText.speed(e.speed());
            }
            g.drawString(font, status, left + 10, y + 10, e.mode() == 2 ? GREEN : LABEL, false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
