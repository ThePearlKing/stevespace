package com.thepearlking.stevespace.client.vessel;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.thepearlking.stevespace.vessel.VesselEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Draws flying vessels from their cached meshes, rotated about their centre of mass. */
public class VesselRenderer extends EntityRenderer<VesselEntity> {

    private static final Map<Integer, VesselMesh> MESHES = new HashMap<>();

    public VesselRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0;
    }

    @Override
    public void render(VesselEntity vessel, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        if (vessel.structure() == null || vessel.materialized()) {
            return; // a ship that is blocks is drawn by the world itself
        }
        VesselMesh mesh = MESHES.computeIfAbsent(vessel.getId(), id -> new VesselMesh(vessel.structure(),
                vessel.level().getBiome(BlockPos.containing(vessel.position())).value()));
        Vector3d com = vessel.stats().centerOfMass();
        pose.pushPose();
        pose.mulPose(vessel.renderOrientation(partialTick));
        pose.translate(-com.x, -com.y, -com.z);
        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(pose.last().pose());
        mesh.draw(modelView, RenderSystem.getProjectionMatrix());
        pose.popPose();
    }

    /** Frees meshes of vessels that no longer exist (landed, crashed, out of range). */
    public static void pruneMeshes() {
        Minecraft mc = Minecraft.getInstance();
        Iterator<Map.Entry<Integer, VesselMesh>> it = MESHES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, VesselMesh> entry = it.next();
            if (mc.level == null || !(mc.level.getEntity(entry.getKey()) instanceof VesselEntity)) {
                entry.getValue().close();
                it.remove();
            }
        }
    }

    @Override
    public ResourceLocation getTextureLocation(VesselEntity vessel) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
