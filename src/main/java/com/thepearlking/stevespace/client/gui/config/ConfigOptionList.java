package com.thepearlking.stevespace.client.gui.config;

import com.google.common.collect.ImmutableList;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.resources.ResourceLocation;
import com.thepearlking.stevespace.StevesSpace;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Scrolling list of section headings and option rows (label on the left, control on the right). */
public final class ConfigOptionList extends ContainerObjectSelectionList<ConfigOptionList.Row> {

    private static final int ROW_WIDTH = 340;
    private static final int CONTROL_WIDTH = 150;
    private static final int RESET_WIDTH = 20;
    private static final ResourceLocation RESET_ICON = ResourceLocation.fromNamespaceAndPath(StevesSpace.MODID, "icon/reset");

    public ConfigOptionList(Minecraft minecraft, int width, int height, int top) {
        super(minecraft, width, height, top, 24);
    }

    public void addSection(Component title) {
        addEntry(new Section(title));
    }

    public void addOption(ConfigOption<?> option) {
        addEntry(new OptionRow(option));
    }

    @Override
    public int getRowWidth() {
        return ROW_WIDTH;
    }

    @Override
    protected int getScrollbarPosition() {
        return this.width / 2 + ROW_WIDTH / 2 + 10;
    }

    public abstract static class Row extends ContainerObjectSelectionList.Entry<Row> {
    }

    /** A heading in the list, e.g. "Time". */
    private final class Section extends Row {
        private final Component title;

        Section(Component title) {
            this.title = title.copy().withStyle(ChatFormatting.BOLD);
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            Font font = minecraft.font;
            int textY = top + height - font.lineHeight - 3;
            graphics.drawString(font, title, left, textY, 0xFFE0E0E0);
            graphics.fill(left, top + height - 2, left + width, top + height - 1, 0x40FFFFFF);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }

    /** One option: label, control, and a reset button that is active when the value is changed. */
    private final class OptionRow extends Row {
        private final ConfigOption<?> option;
        private AbstractWidget control;
        private final Button reset;

        OptionRow(ConfigOption<?> option) {
            this.option = option;
            this.reset = Button.builder(Component.literal("↺"), b -> resetToDefault())
                    .size(RESET_WIDTH, 20)
                    .tooltip(Tooltip.create(Component.translatable("config.stevespace.reset")))
                    .build();
            rebuildControl();
        }

        private void rebuildControl() {
            control = option.createWidget(CONTROL_WIDTH, this::refreshReset);
            control.setTooltip(Tooltip.create(option.tooltip()));
            refreshReset();
        }

        private void refreshReset() {
            reset.active = !option.isDefault();
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private void resetToDefault() {
            ((ConfigOption) option).set(option.defaultValue());
            rebuildControl();
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            Font font = minecraft.font;
            graphics.drawString(font, option.label(), left, top + (height - font.lineHeight) / 2 - 1, 0xFFFFFFFF);
            int controlX = left + width - CONTROL_WIDTH - RESET_WIDTH - 4;
            control.setPosition(controlX, top);
            control.render(graphics, mouseX, mouseY, partialTick);
            reset.setPosition(left + width - RESET_WIDTH, top);
            reset.render(graphics, mouseX, mouseY, partialTick);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return ImmutableList.of(control, reset);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return ImmutableList.of(control, reset);
        }
    }
}
