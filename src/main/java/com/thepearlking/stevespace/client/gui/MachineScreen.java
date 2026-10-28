package com.thepearlking.stevespace.client.gui;

import com.thepearlking.stevespace.energy.MachineMenu;
import com.thepearlking.stevespace.energy.Machines;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** Coal generator / electric furnace / crusher screen, drawn in the vanilla container style. */
public class MachineScreen extends AbstractContainerScreen<MachineMenu> {

    private static final ResourceLocation FLAME = ResourceLocation.withDefaultNamespace("container/furnace/lit_progress");
    private static final ResourceLocation ARROW = ResourceLocation.withDefaultNamespace("container/furnace/burn_progress");
    /** The furnace screen, for its empty arrow and flame outlines. */
    private static final ResourceLocation FURNACE = ResourceLocation.withDefaultNamespace("textures/gui/container/furnace.png");

    private static final int BAR_X = 10;
    private static final int BAR_Y = 17;
    private static final int BAR_W = 12;
    private static final int BAR_H = 52;

    public MachineScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        titleLabelX = (imageWidth - font.width(title)) / 2;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        int bx = leftPos + BAR_X;
        int by = topPos + BAR_Y;
        if (mouseX >= bx && mouseX < bx + BAR_W && mouseY >= by && mouseY < by + BAR_H) {
            g.renderTooltip(font, Component.translatable("stevespace.power.stored",
                    String.format("%,d", menu.energy()), String.format("%,d", menu.capacity())), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        // Panel with the usual bevel.
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xFF000000);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, 0xFFFFFFFF);
        g.fill(x + 2, y + 2, x + imageWidth - 1, y + imageHeight - 1, 0xFF555555);
        g.fill(x + 2, y + 2, x + imageWidth - 2, y + imageHeight - 2, 0xFFC6C6C6);
        for (Slot slot : menu.slots) {
            boolean big = slot.index == 1 && menu.kind != Machines.Kind.COAL_GENERATOR;
            int pad = big ? 5 : 1;
            sunken(g, x + slot.x - pad, y + slot.y - pad, 16 + pad * 2, 16 + pad * 2);
        }
        // Power bar.
        sunken(g, x + BAR_X - 1, y + BAR_Y - 1, BAR_W + 2, BAR_H + 2);
        g.fill(x + BAR_X, y + BAR_Y, x + BAR_X + BAR_W, y + BAR_Y + BAR_H, 0xFF2A1A1A);
        int capacity = Math.max(1, menu.capacity());
        int filled = (int) Math.round(BAR_H * Math.min(1.0, menu.energy() / (double) capacity));
        if (filled > 0) {
            g.fillGradient(x + BAR_X, y + BAR_Y + BAR_H - filled, x + BAR_X + BAR_W, y + BAR_Y + BAR_H, 0xFFE04030, 0xFF8A1A10);
        }
        int total = menu.total();
        int progress = menu.progress();
        if (menu.kind == Machines.Kind.COAL_GENERATOR) {
            // Flame above the fuel, shrinking as the piece burns down.
            g.blit(FURNACE, x + 81, y + 26, 56, 36, 14, 14);
            if (progress > 0 && total > 0) {
                int h = Math.max(1, (int) Math.ceil(13.0 * progress / total));
                g.blitSprite(FLAME, 14, 14, 0, 14 - h, x + 81, y + 26 + 14 - h, 14, h);
            }
        } else {
            int w = total > 0 ? (int) Math.ceil(24.0 * progress / total) : 0;
            g.blit(FURNACE, x + 79, y + 34, 79, 34, 24, 16);
            g.blitSprite(ARROW, 24, 16, 0, 0, x + 79, y + 34, w, 16);
        }
    }

    /** A recessed box: dark top-left edge, light bottom-right, like a slot. */
    private static void sunken(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF8B8B8B);
        g.fill(x, y, x + w - 1, y + 1, 0xFF373737);
        g.fill(x, y, x + 1, y + h - 1, 0xFF373737);
        g.fill(x + 1, y + h - 1, x + w, y + h, 0xFFFFFFFF);
        g.fill(x + w - 1, y + 1, x + w, y + h, 0xFFFFFFFF);
    }
}
