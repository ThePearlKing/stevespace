package com.thepearlking.stevespace.client.telescope;

import com.thepearlking.stevespace.client.TelescopeView;
import com.thepearlking.stevespace.universe.CelestialBody;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;

/**
 * The telescope's control panel: everything in the sky you can point it at, where each one is
 * right now, and a live view of the selected one. Pick a target and the telescope slews to it and
 * follows it across the sky.
 */
public class TelescopeScreen extends Screen {

    private static final int ROW = 20;
    private static final int SCREEN = 0xF00B0F0D;
    private static final int FRAME = 0xFF3B4247;
    private static final int FRAME_LIGHT = 0xFF5A6268;
    private static final int GREEN = 0xFF66D98F;
    private static final int GREEN_DIM = 0xFF2F6E47;
    private static final int TEXT = 0xFFD7E3DA;
    private static final int LABEL = 0xFF93A59A;
    private static final int DIM = 0xFF5C6862;
    private static final int SELECTED = 0xFF16261D;
    private static final int HOVER = 0xFF111A15;

    private final BlockPos telescope;
    private List<SkyCatalog.Entry> entries = List.of();
    private String selected;
    private int scroll;
    private int refresh;
    private Button observe;

    public TelescopeScreen(BlockPos telescope, String selected) {
        super(Component.translatable("screen.stevespace.telescope"));
        this.telescope = telescope;
        this.selected = selected;
    }

    private int panelW() {
        return Math.min(width - 20, 510);
    }

    private int panelH() {
        return Math.min(height - 20, 280);
    }

    private int left() {
        return (width - panelW()) / 2;
    }

    private int top() {
        return (height - panelH()) / 2;
    }

    private int listW() {
        return panelW() * 55 / 100;
    }

    private int visibleRows() {
        return (panelH() - 40) / ROW;
    }

    private Vec3 eye() {
        return Minecraft.getInstance().player.getEyePosition();
    }

    @Override
    protected void init() {
        entries = SkyCatalog.from(eye());
        if (selected == null && !entries.isEmpty()) {
            selected = entries.get(0).id();
        }
        int right = left() + panelW();
        int bottom = top() + panelH();
        observe = addRenderableWidget(Button.builder(Component.translatable("screen.stevespace.telescope.observe"), b -> observeSelected())
                .bounds(right - 96, bottom - 28, 86, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(right - 96 - 56, bottom - 28, 50, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.stevespace.telescope.manual"), b -> {
            TelescopeView.observe(telescope, TelescopeView.MANUAL);
            Minecraft.getInstance().setScreen(null);
        }).bounds(right - 96 - 56 - 66, bottom - 28, 60, 20).tooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("screen.stevespace.telescope.manual.tip"))).build());
    }

    private SkyCatalog.Entry selectedEntry() {
        for (SkyCatalog.Entry e : entries) {
            if (e.id().equals(selected)) {
                return e;
            }
        }
        return null;
    }

    private void observeSelected() {
        SkyCatalog.Entry e = selectedEntry();
        if (e != null && e.aboveHorizon()) {
            TelescopeView.observe(telescope, e.id());
            Minecraft.getInstance().setScreen(null);
        }
    }

    @Override
    public void tick() {
        // Positions change as the sky turns; keep the list current.
        if (++refresh % 20 == 0) {
            entries = SkyCatalog.from(eye());
        }
        SkyCatalog.Entry e = selectedEntry();
        observe.active = e != null && e.aboveHorizon();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = left() + 8;
        int y = top() + 30;
        if (mouseX >= x && mouseX < x + listW() - 8 && mouseY >= y && mouseY < y + visibleRows() * ROW) {
            int index = scroll + (int) ((mouseY - y) / ROW);
            if (index >= 0 && index < entries.size()) {
                String id = entries.get(index).id();
                if (id.equals(selected) && button == 0) {
                    observeSelected(); // a second click on the same row
                    return true;
                }
                selected = id;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        int max = Math.max(0, entries.size() - visibleRows());
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(dy)));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
            observeSelected();
            return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN || key == org.lwjgl.glfw.GLFW.GLFW_KEY_UP) {
            int i = entries.indexOf(selectedEntry()) + (key == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN ? 1 : -1);
            if (i >= 0 && i < entries.size()) {
                selected = entries.get(i).id();
                if (i < scroll) {
                    scroll = i;
                } else if (i >= scroll + visibleRows()) {
                    scroll = i - visibleRows() + 1;
                }
            }
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        int left = left();
        int top = top();
        int w = panelW();
        int h = panelH();
        g.fill(left - 4, top - 4, left + w + 4, top + h + 4, FRAME);
        g.renderOutline(left - 4, top - 4, w + 8, h + 8, FRAME_LIGHT);
        g.fill(left, top, left + w, top + h, SCREEN);
        g.drawString(font, title.copy().withStyle(s -> s.withBold(true)), left + 10, top + 8, GREEN, false);
        g.fill(left + 10, top + 20, left + w - 10, top + 21, GREEN_DIM);
        g.fill(left + listW(), top + 26, left + listW() + 1, top + h - 8, GREEN_DIM);

        // The list.
        int x = left + 8;
        int y = top + 30;
        int rows = visibleRows();
        for (int i = 0; i < rows && scroll + i < entries.size(); i++) {
            SkyCatalog.Entry e = entries.get(scroll + i);
            int ry = y + i * ROW;
            boolean isSelected = e.id().equals(selected);
            boolean hovered = mouseX >= x && mouseX < left + listW() - 4 && mouseY >= ry && mouseY < ry + ROW;
            if (isSelected || hovered) {
                g.fill(x - 2, ry, left + listW() - 4, ry + ROW - 1, isSelected ? SELECTED : HOVER);
            }
            dot(g, x + 5, ry + 9, e.color(), e.aboveHorizon());
            int nameColor = e.aboveHorizon() ? TEXT : DIM;
            g.drawString(font, e.name(), x + 14, ry + 2, nameColor, false);
            g.drawString(font, kind(e), x + 14, ry + 11, e.aboveHorizon() ? LABEL : DIM, false);
            String where = e.blockedBy() != null
                    ? Component.translatable("screen.stevespace.telescope.behind", e.blockedBy().displayName()).getString()
                    : e.inSpace() ? Component.translatable("screen.stevespace.telescope.in_view").getString()
                    : e.aboveHorizon()
                    ? String.format(Locale.ROOT, "%.0f° %s", Math.max(0, e.elevation()), SkyCatalog.compass(e.azimuth()))
                    : Component.translatable("screen.stevespace.telescope.below").getString();
            g.drawString(font, where, left + listW() - 8 - font.width(where), ry + 6, e.aboveHorizon() ? GREEN : DIM, false);
        }
        if (entries.size() > rows) {
            int trackH = rows * ROW;
            int barH = Math.max(12, trackH * rows / entries.size());
            int barY = y + (trackH - barH) * scroll / Math.max(1, entries.size() - rows);
            g.fill(left + listW() - 3, barY, left + listW() - 1, barY + barH, GREEN_DIM);
        }

        // The selected body.
        SkyCatalog.Entry e = selectedEntry();
        if (e == null) {
            return;
        }
        int px = left + listW() + 12;
        int pw = left + w - 10 - px;
        float r = Math.min(pw, h - 150) / 2f * 0.62f;
        float cx = px + pw / 2f;
        float cy = top + 34 + r * 1.35f;
        GlobePreview.draw(g, eye(), e, cx, cy, r);
        int ty = (int) (cy + r * 1.35f) + 4;
        g.drawString(font, e.name(), px, ty, TEXT, false);
        ty += 12;
        ty = line(g, px, pw, ty, "screen.stevespace.telescope.distance", SkyCatalog.distance(e.distance()));
        ty = line(g, px, pw, ty, "screen.stevespace.telescope.size", SkyCatalog.size(e.angularRadius()));
        ty = line(g, px, pw, ty, "screen.stevespace.telescope.phase", Math.round(e.phase() * 100) + "%");
        line(g, px, pw, ty, "screen.stevespace.telescope.brightness", String.format(Locale.ROOT, "%.1f", e.magnitude()));
    }

    private int line(GuiGraphics g, int x, int w, int y, String label, String value) {
        g.drawString(font, Component.translatable(label), x, y, LABEL, false);
        g.drawString(font, value, x + w - font.width(value), y, TEXT, false);
        return y + 10;
    }

    private Component kind(SkyCatalog.Entry e) {
        if (e.ship() != null) {
            return Component.translatable("screen.stevespace.telescope.spacecraft");
        }
        CelestialBody body = e.body();
        return switch (body.kind()) {
            case MOON -> Component.translatable("screen.stevespace.telescope.moon_of", body.parent().displayName());
            case DWARF_PLANET -> Component.translatable("screen.stevespace.telescope.dwarf");
            case STAR -> Component.translatable("screen.stevespace.telescope.star");
            default -> Component.translatable("screen.stevespace.telescope.planet");
        };
    }

    /** A small round swatch in the body's colour. */
    private static void dot(GuiGraphics g, int cx, int cy, int color, boolean bright) {
        int c = (bright ? 0xFF000000 : 0x70000000) | color;
        g.fill(cx - 2, cy - 4, cx + 3, cy + 5, c);
        g.fill(cx - 4, cy - 2, cx + 5, cy + 3, c);
        g.fill(cx - 3, cy - 3, cx + 4, cy + 4, c);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
