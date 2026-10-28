package com.thepearlking.stevespace.client.gui;

import com.thepearlking.stevespace.client.FlightInput;
import com.thepearlking.stevespace.network.AutopilotC2S;
import com.thepearlking.stevespace.network.SpaceNetwork;
import com.thepearlking.stevespace.vessel.Autopilot;
import com.thepearlking.stevespace.vessel.VesselEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** The flight computer's autopilot panel, opened while piloting. */
public class AutopilotScreen extends Screen {

    private static final int W = 196;
    private static final int H = 247;
    private static final int SCREEN = 0xF00B0F0D;
    private static final int FRAME = 0xFF3B4247;
    private static final int FRAME_LIGHT = 0xFF5A6268;
    private static final int GREEN = 0xFF66D98F;
    private static final int GREEN_DIM = 0xFF2F6E47;

    public AutopilotScreen() {
        super(Component.translatable("screen.stevespace.autopilot"));
    }

    @Override
    protected void init() {
        int left = (width - W) / 2;
        int top = (height - H) / 2;
        boolean flying = Minecraft.getInstance().player != null && VesselEntity.shipOf(Minecraft.getInstance().player) != null;
        int y = top + 30;
        // Transfers start from orbit: out to a moon from a planet, home from a moon.
        var body = com.thepearlking.stevespace.client.space.ClientSpace.orbitActive()
                ? com.thepearlking.stevespace.client.space.ClientSpace.body() : null;
        var system = com.thepearlking.stevespace.client.ClientUniverse.system();
        boolean planetOrbit = body != null && !system.childrenOf(body).isEmpty();
        boolean moonOrbit = body != null && body.parent() != null && body.parent() != system.sun();
        y = program(left, y, Autopilot.Program.ORBIT, true);
        y = program(left, y, Autopilot.Program.CIRCULARIZE, flying);
        y = program(left, y, Autopilot.Program.MOON, planetOrbit);
        y = program(left, y, Autopilot.Program.RETURN, moonOrbit);
        y = program(left, y, Autopilot.Program.LAND, flying);
        Button travel = Button.builder(Component.translatable("screen.stevespace.autopilot.travel"),
                b -> Minecraft.getInstance().setScreen(MapScreen.forDestination())).bounds(left + 12, y, W - 24, 20).build();
        addRenderableWidget(travel);
        y += 23;
        y = program(left, y, Autopilot.Program.APPROACH, body != null && com.thepearlking.stevespace.client.space.ClientTarget.active());
        y = program(left, y, Autopilot.Program.UNDOCK, body != null);
        program(left, y, Autopilot.Program.OFF, flying);
    }

    private int program(int left, int y, Autopilot.Program program, boolean enabled) {
        String key = "screen.stevespace.autopilot." + program.name().toLowerCase(java.util.Locale.ROOT);
        Button button = Button.builder(Component.translatable(key), b -> engage(program))
                .bounds(left + 12, y, W - 24, 20).build();
        button.active = enabled;
        addRenderableWidget(button);
        return y + 23;
    }

    private void engage(Autopilot.Program program) {
        FlightInput.setThrottle(0); // the autopilot takes the throttle; touching it again takes over
        SpaceNetwork.sendToServer(new AutopilotC2S(program));
        onClose();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int left = (width - W) / 2;
        int top = (height - H) / 2;
        g.fill(left - 4, top - 4, left + W + 4, top + H + 4, FRAME);
        g.renderOutline(left - 4, top - 4, W + 8, H + 8, FRAME_LIGHT);
        g.fill(left, top, left + W, top + H, SCREEN);
        g.drawString(font, title.copy().withStyle(s -> s.withBold(true)), left + 10, top + 8, GREEN, false);
        String status = "";
        if (Minecraft.getInstance().player != null && VesselEntity.shipOf(Minecraft.getInstance().player) instanceof VesselEntity vessel
                && !vessel.autopilotStatus().isEmpty()) {
            status = Component.translatable(vessel.autopilotStatus()).getString();
        }
        g.drawString(font, status, left + W - 10 - font.width(status), top + 8, GREEN_DIM, false);
        g.fill(left + 10, top + 20, left + W - 10, top + 21, GREEN_DIM);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
