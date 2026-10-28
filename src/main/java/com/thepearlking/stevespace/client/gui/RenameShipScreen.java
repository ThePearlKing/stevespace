package com.thepearlking.stevespace.client.gui;

import com.thepearlking.stevespace.network.RenameShipC2S;
import com.thepearlking.stevespace.network.SpaceNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** A new name for your ship, from the map. */
public class RenameShipScreen extends Screen {

    private static final int W = 220;
    private static final int H = 70;

    private final Screen back;
    private final String current;
    private EditBox box;

    public RenameShipScreen(Screen back, String current) {
        super(Component.translatable("screen.stevespace.rename_ship"));
        this.back = back;
        this.current = current;
    }

    @Override
    protected void init() {
        int left = (width - W) / 2;
        int top = (height - H) / 2;
        box = new EditBox(font, left + 8, top + 22, W - 16, 18, title);
        box.setMaxLength(RenameShipC2S.MAX);
        box.setValue(current);
        addRenderableWidget(box);
        setInitialFocus(box);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> done()).bounds(left + W - 128, top + H - 24, 60, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(left + W - 66, top + H - 24, 58, 18).build());
    }

    private void done() {
        String name = box.getValue().trim();
        if (!name.isEmpty() && !name.equals(current)) {
            SpaceNetwork.sendToServer(new RenameShipC2S(name));
        }
        onClose();
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
            done();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(back);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int left = (width - W) / 2;
        int top = (height - H) / 2;
        g.fill(left, top, left + W, top + H, 0xF00B0F0D);
        g.renderOutline(left, top, W, H, 0xFF3B4247);
        g.drawString(font, title, left + 8, top + 8, 0xFF66D98F, false);
        for (var child : renderables) {
            child.render(g, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
