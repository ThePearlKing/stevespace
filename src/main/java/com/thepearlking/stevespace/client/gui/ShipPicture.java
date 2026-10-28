package com.thepearlking.stevespace.client.gui;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.thepearlking.stevespace.network.DockListS2C;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** A ship drawn small and turning in a menu, one of its blocks (a docking port) outlined. */
final class ShipPicture {

    private static final int FRAME = 0xFF2F6E47;
    private static final int BACK = 0xFF060908;
    private static final int LABEL = 0xFF66D98F;

    private ShipPicture() {
    }

    static void draw(GuiGraphics g, DockListS2C.Shape shape, BlockPos marked, int x, int y, int w, int h, String label) {
        g.fill(x, y, x + w, y + h, BACK);
        g.renderOutline(x, y, w, h, FRAME);
        var font = Minecraft.getInstance().font;
        g.drawString(font, font.plainSubstrByWidth(label, w - 8), x + 4, y + 3, LABEL, false);
        if (shape.positions().length == 0) {
            return;
        }
        // Its size and middle.
        int[] lo = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
        int[] hi = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (long p : shape.positions()) {
            BlockPos b = BlockPos.of(p);
            lo[0] = Math.min(lo[0], b.getX());
            lo[1] = Math.min(lo[1], b.getY());
            lo[2] = Math.min(lo[2], b.getZ());
            hi[0] = Math.max(hi[0], b.getX());
            hi[1] = Math.max(hi[1], b.getY());
            hi[2] = Math.max(hi[2], b.getZ());
        }
        double cx = (lo[0] + hi[0] + 1) / 2.0;
        double cy = (lo[1] + hi[1] + 1) / 2.0;
        double cz = (lo[2] + hi[2] + 1) / 2.0;
        double span = Math.sqrt(Math.pow(hi[0] - lo[0] + 1, 2) + Math.pow(hi[1] - lo[1] + 1, 2) + Math.pow(hi[2] - lo[2] + 1, 2));
        float scale = (float) (Math.min(w, h - 12) * 0.85 / Math.max(1, span));

        g.flush();
        g.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(x + w / 2f, y + 6 + (h - 6) / 2f, 200);
        pose.scale(scale, -scale, scale);
        pose.mulPose(Axis.XP.rotationDegrees(-22));
        pose.mulPose(Axis.YP.rotationDegrees((Util.getMillis() % 14400L) / 40f));
        pose.translate(-cx, -cy, -cz);
        Lighting.setupFor3DItems();
        var buffers = g.bufferSource();
        var blocks = Minecraft.getInstance().getBlockRenderer();
        for (int i = 0; i < shape.positions().length; i++) {
            BlockState state = Block.stateById(shape.states()[i]);
            if (state.isAir()) {
                continue;
            }
            BlockPos b = BlockPos.of(shape.positions()[i]);
            pose.pushPose();
            pose.translate(b.getX(), b.getY(), b.getZ());
            blocks.renderSingleBlock(state, pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                    net.minecraftforge.client.model.data.ModelData.EMPTY, null);
            pose.popPose();
        }
        g.flush();
        if (marked != null) {
            // The picked port: a bright box round it, pulsing.
            float pulse = 0.6f + 0.4f * (float) Math.sin(Util.getMillis() / 180.0);
            var lines = buffers.getBuffer(RenderType.lines());
            double e = 0.06;
            LevelRenderer.renderLineBox(pose, lines, marked.getX() - e, marked.getY() - e, marked.getZ() - e,
                    marked.getX() + 1 + e, marked.getY() + 1 + e, marked.getZ() + 1 + e, 0.4f * pulse, 1f * pulse, 0.55f * pulse, 1f);
            g.flush();
        }
        pose.popPose();
        Lighting.setupForFlatItems();
        g.disableScissor();
    }
}
