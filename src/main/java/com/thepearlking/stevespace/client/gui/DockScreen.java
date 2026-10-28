package com.thepearlking.stevespace.client.gui;

import com.thepearlking.stevespace.client.FlightInput;
import com.thepearlking.stevespace.network.DockC2S;
import com.thepearlking.stevespace.network.DockListS2C;
import com.thepearlking.stevespace.network.SpaceNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Picking a ship to dock with, which of its ports, and which of ours: then the autopilot flies
 * there (to orbit first, or to the other world, if need be) and docks.
 */
public class DockScreen extends Screen {

    private static final int W = 440;
    /** Width of the left part (list and ports); the ship pictures are to its right. */
    private static final int LIST_W = 300;
    private static final int H = 236;
    private static final int SCREEN = 0xF00B0F0D;
    private static final int FRAME = 0xFF3B4247;
    private static final int FRAME_LIGHT = 0xFF5A6268;
    private static final int GREEN = 0xFF66D98F;
    private static final int GREEN_DIM = 0xFF2F6E47;
    private static final int SELECTED = 0xFF1C3A28;
    private static final int RED = 0xFFE04848;
    private static final int RED_BACK = 0xFF4A1010;
    private static final int ROW = 13;
    private static final int ROWS = 6;

    private final Screen parent;
    private DockListS2C list;
    private int selected = -1;
    private int theirPort = -1;
    private int myPort = -1;
    private int scroll;
    private final List<Button> portButtons = new ArrayList<>();
    /** The ports picked (outlined). */
    private final List<Button> chosen = new ArrayList<>();
    private Button dock;

    public DockScreen(Screen parent) {
        super(Component.translatable("screen.stevespace.dock"));
        this.parent = parent;
    }

    /** Client tests: picks their port {@code index} on the open dock menu. */
    public static void pickForTest(int index) {
        if (Minecraft.getInstance().screen instanceof DockScreen screen) {
            screen.theirPort = index;
            screen.rebuildWidgets();
        }
    }

    /** The server's answer to our request. */
    public static void receive(DockListS2C msg) {
        if (Minecraft.getInstance().screen instanceof DockScreen screen) {
            screen.list = msg;
            screen.selected = msg.ships().isEmpty() ? -1 : 0;
            screen.theirPort = !msg.ships().isEmpty() && msg.ships().get(0).ports().size() == 1 ? 0 : -1;
            screen.myPort = msg.mine().size() == 1 ? 0 : -1;
            screen.rebuildWidgets();
        }
    }

    @Override
    protected void init() {
        if (list == null) {
            SpaceNetwork.sendToServer(DockC2S.list());
        }
        portButtons.clear();
        chosen.clear();
        int left = (width - W) / 2;
        int top = (height - H) / 2;
        if (list != null && selected >= 0 && selected < list.ships().size()) {
            DockListS2C.Entry ship = list.ships().get(selected);
            int y = top + listTop() + ROWS * ROW + 18;
            int x = left + 70;
            for (int i = 0; i < ship.ports().size(); i++) {
                int index = i;
                Button b = Button.builder(Component.literal(portName(ship.ports(), i)), button -> {
                    theirPort = index;
                    rebuildWidgets();
                }).bounds(x, y, 54, 16).build();
                if (theirPort == i) {
                    chosen.add(b);
                }
                b.active = myPort < 0 || meet(list.mine().get(myPort), ship.ports().get(i));
                addRenderableWidget(b);
                portButtons.add(b);
                x += 57;
                if (x + 54 > left + LIST_W - 8) {
                    break; // more ports than fit: the first few
                }
            }
            y += 20;
            x = left + 70;
            for (int i = 0; i < list.mine().size(); i++) {
                int index = i;
                Button b = Button.builder(Component.literal(portName(list.mine(), i)), button -> {
                    myPort = index;
                    rebuildWidgets();
                }).bounds(x, y, 54, 16).build();
                if (myPort == i) {
                    chosen.add(b);
                }
                b.active = theirPort < 0 || meet(list.mine().get(i), ship.ports().get(theirPort));
                addRenderableWidget(b);
                portButtons.add(b);
                x += 57;
                if (x + 54 > left + LIST_W - 8) {
                    break;
                }
            }
        }
        dock = Button.builder(Component.translatable("screen.stevespace.dock.go"), b -> go())
                .bounds(left + LIST_W - 12 - 90, top + H - 26, 90, 20).build();
        dock.active = list != null && selected >= 0 && theirPort >= 0 && myPort >= 0
                && meet(list.mine().get(myPort), list.ships().get(selected).ports().get(theirPort));
        addRenderableWidget(dock);
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(left + 12, top + H - 26, 70, 20).build());
    }

    private int listTop() {
        return list != null && !list.rcs() && !list.mine().isEmpty() ? 58 : 26;
    }

    /** Whether two ports can meet with both ships upright: top to bottom, or side to side (never tipping one over). */
    private static boolean meet(DockListS2C.Port mine, DockListS2C.Port theirs) {
        boolean upDownMine = mine.side().equals("top") || mine.side().equals("bottom");
        boolean upDownTheirs = theirs.side().equals("top") || theirs.side().equals("bottom");
        if (upDownMine || upDownTheirs) {
            return upDownMine && upDownTheirs && !mine.side().equals(theirs.side());
        }
        return true;
    }

    private static String portName(List<DockListS2C.Port> ports, int i) {
        DockListS2C.Port p = ports.get(i);
        String side = Component.translatable("screen.stevespace.dock.side." + p.side()).getString();
        long same = ports.stream().filter(q -> q.side().equals(p.side())).count();
        return same > 1 ? side + " " + p.number() : side;
    }

    private void go() {
        DockListS2C.Entry ship = list.ships().get(selected);
        FlightInput.setThrottle(0);
        SpaceNetwork.sendToServer(new DockC2S(false, ship.id(), ship.cell(), list.mine().get(myPort).pos(), ship.ports().get(theirPort).pos()));
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (list != null) {
            int left = (width - W) / 2;
            int top = (height - H) / 2 + listTop();
            if (mouseX >= left + 10 && mouseX <= left + LIST_W - 10 && mouseY >= top && mouseY < top + ROWS * ROW) {
                int row = (int) ((mouseY - top) / ROW) + scroll;
                if (row < list.ships().size() && row != selected) {
                    selected = row;
                    theirPort = list.ships().get(row).ports().size() == 1 ? 0 : -1;
                    rebuildWidgets();
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        if (list != null) {
            int most = Math.max(0, list.ships().size() - ROWS);
            scroll = Math.max(0, Math.min(most, scroll - (int) Math.signum(dy)));
        }
        return true;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int left = (width - W) / 2;
        int top = (height - H) / 2;
        g.fill(left - 4, top - 4, left + W + 4, top + H + 4, FRAME);
        g.renderOutline(left - 4, top - 4, W + 8, H + 8, FRAME_LIGHT);
        g.fill(left, top, left + W, top + H, SCREEN);
        g.drawString(font, title.copy().withStyle(s -> s.withBold(true)), left + 10, top + 8, GREEN, false);
        g.fill(left + 10, top + 20, left + W - 10, top + 21, GREEN_DIM);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        for (Button b : chosen) {
            g.renderOutline(b.getX() - 1, b.getY() - 1, b.getWidth() + 2, b.getHeight() + 2, GREEN);
        }
        int left = (width - W) / 2;
        int top = (height - H) / 2;
        if (list == null) {
            g.drawString(font, Component.translatable("screen.stevespace.dock.loading"), left + 12, top + 30, GREEN_DIM, false);
            return;
        }
        if (list.mine().isEmpty()) {
            g.drawWordWrap(font, Component.translatable("screen.stevespace.dock.no_port"), left + 12, top + 30, W - 24, GREEN);
            return;
        }
        if (!list.rcs()) {
            // Big and red: docking without RCS works, but it's slow and rough.
            g.fill(left + 8, top + 24, left + LIST_W - 8, top + 54, RED_BACK);
            g.renderOutline(left + 8, top + 24, LIST_W - 16, 30, RED);
            g.drawCenteredString(font, Component.translatable("screen.stevespace.dock.no_rcs").withStyle(s -> s.withBold(true)),
                    left + LIST_W / 2, top + 28, RED);
            g.drawCenteredString(font, Component.translatable("screen.stevespace.dock.no_rcs_detail"), left + LIST_W / 2, top + 41, 0xFFFFB0B0);
        }
        int y0 = top + listTop();
        if (list.ships().isEmpty()) {
            g.drawWordWrap(font, Component.translatable("screen.stevespace.dock.none"), left + 12, y0 + 2, W - 24, GREEN_DIM);
            return;
        }
        for (int row = 0; row < ROWS && row + scroll < list.ships().size(); row++) {
            int i = row + scroll;
            DockListS2C.Entry e = list.ships().get(i);
            int y = y0 + row * ROW;
            if (i == selected) {
                g.fill(left + 10, y - 1, left + LIST_W - 10, y + ROW - 2, SELECTED);
            }
            g.drawString(font, font.plainSubstrByWidth(e.name(), 150), left + 14, y + 1, i == selected ? GREEN : 0xFFB8D8C4, false);
            String where = e.distance() >= 0 ? FlightText.distance(e.distance())
                    : Component.translatable("screen.stevespace.dock.orbiting", body(e.body())).getString();
            String whose = Component.translatable(e.owned() ? "screen.stevespace.dock.yours" : "screen.stevespace.dock.radio").getString();
            String right = where + "  " + whose;
            g.drawString(font, right, left + LIST_W - 14 - font.width(right), y + 1, GREEN_DIM, false);
        }
        if (list.ships().size() > ROWS) {
            int barH = ROWS * ROW;
            int knob = Math.max(8, barH * ROWS / list.ships().size());
            int knobY = y0 + (barH - knob) * scroll / Math.max(1, list.ships().size() - ROWS);
            g.fill(left + LIST_W - 9, knobY, left + LIST_W - 7, knobY + knob, GREEN_DIM);
        }
        if (selected >= 0) {
            int y = y0 + ROWS * ROW + 22;
            g.drawString(font, Component.translatable("screen.stevespace.dock.their_port"), left + 12, y, GREEN, false);
            g.drawString(font, Component.translatable("screen.stevespace.dock.my_port"), left + 12, y + 20, GREEN, false);
            g.drawString(font, font.plainSubstrByWidth(Component.translatable("screen.stevespace.dock.upright").getString(), LIST_W - 24),
                    left + 12, y + 38, GREEN_DIM, false);
            // Pictures of both ships, turning, the picked ports lit up.
            DockListS2C.Entry ship = list.ships().get(selected);
            int px = left + LIST_W;
            int pw = W - LIST_W - 10;
            int ph = (H - 34) / 2;
            ShipPicture.draw(g, ship.shape(), theirPort >= 0 ? ship.ports().get(theirPort).pos() : null,
                    px, top + 24, pw, ph, ship.name());
            ShipPicture.draw(g, list.myShape(), myPort >= 0 ? list.mine().get(myPort).pos() : null,
                    px, top + 28 + ph, pw, ph, Component.translatable("screen.stevespace.dock.your_ship").getString());
        }
    }

    private static String body(String id) {
        var system = com.thepearlking.stevespace.client.ClientUniverse.system();
        var body = system == null ? null : system.body(id);
        return body == null ? id : body.displayName();
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
