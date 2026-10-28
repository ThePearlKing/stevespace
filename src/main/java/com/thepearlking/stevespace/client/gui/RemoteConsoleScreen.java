package com.thepearlking.stevespace.client.gui;

import com.thepearlking.stevespace.network.RemoteActionC2S;
import com.thepearlking.stevespace.network.RemoteConsoleS2C;
import com.thepearlking.stevespace.network.SpaceNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The remote control console: the channel, every craft listening on it (where it is, how far,
 * the light delay or what's blocking the signal), and for the one picked: Connect to fly it, and
 * a button for each function its relays use.
 */
public class RemoteConsoleScreen extends Screen {

    private static final int W = 360;
    private static final int ROW = 22;
    private static final int ROWS = 6;
    private static final int GREEN = 0xFF66D98F;
    private static final int AMBER = 0xFFE0A43C;
    private static final int RED = 0xFFE05A4E;
    private static final int TEXT = 0xFFB8C4BC;
    private static final int GREY = 0xFF5E6862;

    private final RemoteConsoleS2C data;
    private static String selectedId = "";
    private int scroll;
    private int left;
    private int top;
    private int height2;
    private Button connect;
    private final List<Button> functionButtons = new ArrayList<>();

    public RemoteConsoleScreen(RemoteConsoleS2C data) {
        super(Component.translatable("screen.stevespace.remote_console"));
        this.data = data;
    }

    public static void open(RemoteConsoleS2C data) {
        int keepScroll = Minecraft.getInstance().screen instanceof RemoteConsoleScreen old ? old.scroll : 0;
        RemoteConsoleScreen screen = new RemoteConsoleScreen(data);
        screen.scroll = keepScroll;
        Minecraft.getInstance().setScreen(screen);
    }

    @Override
    protected void init() {
        if (selected() == null && !data.craft().isEmpty()) {
            selectedId = data.craft().get(0).id(); // the first one ready to go
        }
        height2 = 30 + ROWS * ROW + 54;
        left = (width - W) / 2;
        top = (height - height2) / 2;
        int[] steps = {-10, -1, 1, 10};
        String[] labels = {"-10", "-1", "+1", "+10"};
        for (int i = 0; i < 4; i++) {
            int step = steps[i];
            int bx = left + W - 8 - (4 - i) * 30 - (i >= 2 ? 0 : 76);
            addRenderableWidget(Button.builder(Component.literal(labels[i]), b -> send("tune", data.channel() + step))
                    .bounds(bx, top + 6, 28, 16).build());
        }
        int by = top + 30 + ROWS * ROW + 8;
        connect = addRenderableWidget(Button.builder(Component.translatable("screen.stevespace.remote.connect"), b -> send("connect", 0))
                .bounds(left + 8, by, 80, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.stevespace.remote.refresh"), b -> send("refresh", 0))
                .bounds(left + 92, by, 60, 18).build());
        functionButtons.clear();
        for (int i = 0; i < 5; i++) {
            int n = i + 1;
            Button fb = addRenderableWidget(Button.builder(Component.literal("F" + n), b -> send("function", n))
                    .bounds(left + 160 + i * 38, by, 36, 18).build());
            functionButtons.add(fb);
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + W - 68, by + 22, 60, 16).build());
        updateButtons();
    }

    private RemoteConsoleS2C.Entry selected() {
        for (RemoteConsoleS2C.Entry e : data.craft()) {
            if (e.id().equals(selectedId)) {
                return e;
            }
        }
        return null;
    }

    private void updateButtons() {
        RemoteConsoleS2C.Entry e = selected();
        connect.active = e != null && e.delay() >= 0;
        for (int i = 0; i < 5; i++) {
            Button b = functionButtons.get(i);
            int count = data.functions()[i]; // every relay on the channel, crafted in or standing alone
            b.visible = count > 0;
            b.setMessage(Component.literal("F" + (i + 1) + (count > 1 ? " ×" + count : "")));
        }
    }

    private void send(String action, int value) {
        SpaceNetwork.sendToServer(new RemoteActionC2S(data.pos(), action, value, selectedId));
        if (action.equals("connect")) {
            onClose();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int listTop = top + 30;
        if (mouseX >= left + 6 && mouseX < left + W - 6 && mouseY >= listTop && mouseY < listTop + ROWS * ROW) {
            int i = (int) ((mouseY - listTop) / ROW) + scroll;
            if (i < data.craft().size()) {
                selectedId = data.craft().get(i).id();
                updateButtons();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Math.max(0, Math.min(Math.max(0, data.craft().size() - ROWS), scroll - (int) Math.signum(scrollY)));
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.fill(left, top, left + W, top + height2, 0xF00B0F0D);
        g.renderOutline(left, top, W, height2, 0xFF3B4247);
        g.drawString(font, title, left + 8, top + 10, GREEN, false);
        String ch = Component.translatable("screen.stevespace.remote.channel", String.format("%02d", data.channel())).getString();
        g.drawCenteredString(font, ch, left + W - 8 - 2 * 30 - 38, top + 10, AMBER);
        int listTop = top + 30;
        g.fill(left + 6, listTop - 2, left + W - 6, listTop + ROWS * ROW, 0xFF070A09);
        if (data.craft().isEmpty()) {
            g.drawString(font, Component.translatable("screen.stevespace.remote.none", data.channel()), left + 12, listTop + 8, GREY, false);
        }
        for (int row = 0; row < ROWS && row + scroll < data.craft().size(); row++) {
            RemoteConsoleS2C.Entry e = data.craft().get(row + scroll);
            int y = listTop + row * ROW;
            if (e.id().equals(selectedId)) {
                g.fill(left + 6, y, left + W - 6, y + ROW - 1, 0xFF1A2A20);
            }
            g.drawString(font, e.name(), left + 12, y + 3, TEXT, false);
            String place = Component.translatable("screen.stevespace.remote.where." + e.where(), worldName(e.body())).getString();
            g.drawString(font, place + " · " + distance(e.distance()), left + 12, y + 12, GREY, false);
            String link;
            int color;
            if (e.delay() < 0) {
                link = Component.translatable("screen.stevespace.remote.behind", e.blocker()).getString();
                color = RED;
            } else if (e.delay() < 0.05) {
                link = Component.translatable("screen.stevespace.remote.in_range").getString();
                color = GREEN;
            } else {
                link = Component.translatable("screen.stevespace.remote.delay", String.format(java.util.Locale.ROOT, "%.1f", e.delay())).getString();
                color = AMBER;
            }
            g.drawString(font, link, left + W - 12 - font.width(link), y + 7, color, false);
        }
        if (data.craft().size() > ROWS) {
            String more = (scroll + 1) + "-" + Math.min(data.craft().size(), scroll + ROWS) + " / " + data.craft().size();
            g.drawString(font, more, left + W - 12 - font.width(more), listTop + ROWS * ROW + 1, GREY, false);
        }
        for (var child : renderables) {
            child.render(g, mouseX, mouseY, partialTick);
        }
    }

    private static String worldName(String id) {
        var system = com.thepearlking.stevespace.client.ClientUniverse.system();
        var body = system == null || id.isEmpty() ? null : system.body(id);
        return body == null ? "?" : body.displayName();
    }

    /** Blocks are kilometres: km close by, then millions of km, then AU. */
    private static String distance(double km) {
        if (km < 10_000) {
            return String.format(java.util.Locale.ROOT, "%,.0f km", km);
        }
        if (km < 15_000_000) {
            return String.format(java.util.Locale.ROOT, "%,.2f million km", km / 1e6);
        }
        return String.format(java.util.Locale.ROOT, "%.2f AU", km / com.thepearlking.stevespace.universe.SolarSystem.AU);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
