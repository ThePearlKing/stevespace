package com.thepearlking.stevespace.client.sky;

import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;

/**
 * Walking bob, for things drawn shrunk into the sky. Vanilla's bob shifts the view by up to a
 * tenth of a block; on terrain drawn at 1/100 scale that same shift looks a hundred times bigger
 * and the horizon swings as you walk. Multiplying the view by this keeps the bob's tilt (so it
 * still lines up with the chunks) but takes its shift back out.
 */
public final class ViewBob {

    private ViewBob() {
    }

    /** Matrix to put before the camera rotation: cancels the bob's translation, keeps its rotation. */
    public static Matrix4f untranslate(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.options.bobView().get() || !(mc.getCameraEntity() instanceof Player player)) {
            return new Matrix4f();
        }
        // Same numbers as GameRenderer.bobView.
        float f = player.walkDist - player.walkDistO;
        float f1 = -(player.walkDist + f * partialTick);
        float f2 = Mth.lerp(partialTick, player.oBob, player.bob);
        Matrix4f rotation = new Matrix4f()
                .rotate(Axis.ZP.rotationDegrees(Mth.sin(f1 * (float) Math.PI) * f2 * 3.0F))
                .rotate(Axis.XP.rotationDegrees(Math.abs(Mth.cos(f1 * (float) Math.PI - 0.2F) * f2) * 5.0F));
        Matrix4f bob = new Matrix4f()
                .translation(Mth.sin(f1 * (float) Math.PI) * f2 * 0.5F, -Math.abs(Mth.cos(f1 * (float) Math.PI) * f2), 0.0F)
                .mul(rotation);
        // bob * result = rotation
        return bob.invert().mul(rotation);
    }
}
