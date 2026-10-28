package com.thepearlking.stevespace.client.vessel;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.thepearlking.stevespace.vessel.VesselStructure;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Matrix4f;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A vessel's blocks baked into vertex buffers once (one per render layer), drawn every frame the
 * same way Minecraft draws chunk sections.
 */
public final class VesselMesh implements AutoCloseable {

    private final Map<RenderType, VertexBuffer> layers = new LinkedHashMap<>();

    public VesselMesh(VesselStructure structure, Biome biome) {
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        VesselBlockGetter getter = new VesselBlockGetter(structure, biome);
        RandomSource random = RandomSource.create();
        for (RenderType layer : RenderType.chunkBufferLayers()) {
            try (ByteBufferBuilder bytes = new ByteBufferBuilder(layer.bufferSize())) {
                BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
                PoseStack pose = new PoseStack();
                for (Map.Entry<BlockPos, BlockState> entry : structure.blocks().entrySet()) {
                    BlockState state = entry.getValue();
                    if (state.getRenderShape() != RenderShape.MODEL) {
                        continue;
                    }
                    BakedModel model = dispatcher.getBlockModel(state);
                    random.setSeed(42);
                    if (!model.getRenderTypes(state, random, ModelData.EMPTY).contains(layer)) {
                        continue;
                    }
                    BlockPos pos = entry.getKey();
                    pose.pushPose();
                    pose.translate(pos.getX(), pos.getY(), pos.getZ());
                    dispatcher.renderBatched(state, pos, getter, pose, builder, true, random, ModelData.EMPTY, layer);
                    pose.popPose();
                }
                MeshData mesh = builder.build();
                if (mesh != null) {
                    if (layer == RenderType.translucent()) {
                        mesh.sortQuads(bytes, com.mojang.blaze3d.vertex.VertexSorting.byDistance(0, 0, 0));
                    }
                    VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
                    buffer.bind();
                    buffer.upload(mesh);
                    VertexBuffer.unbind();
                    layers.put(layer, buffer);
                }
            }
        }
    }

    /** Draws all layers with the given model-view matrix (camera rotation × vessel placement). */
    public void draw(Matrix4f modelView, Matrix4f projection) {
        draw(modelView, projection, -1);
    }

    /**
     * The same, but with the light map swapped for one plain texture (a ship far off in the sky
     * is lit by the Sun, not by the time of day where you stand); -1 keeps the world's.
     */
    public void draw(Matrix4f modelView, Matrix4f projection, int lightTexture) {
        for (Map.Entry<RenderType, VertexBuffer> entry : layers.entrySet()) {
            RenderType layer = entry.getKey();
            layer.setupRenderState();
            if (lightTexture >= 0) {
                RenderSystem.setShaderTexture(2, lightTexture);
            }
            ShaderInstance shader = RenderSystem.getShader();
            if (shader != null) {
                shader.setDefaultUniforms(VertexFormat.Mode.QUADS, modelView, projection, Minecraft.getInstance().getWindow());
                if (shader.CHUNK_OFFSET != null) {
                    shader.CHUNK_OFFSET.set(0f, 0f, 0f);
                }
                shader.apply();
                VertexBuffer buffer = entry.getValue();
                buffer.bind();
                buffer.draw();
                shader.clear();
            }
            VertexBuffer.unbind();
            layer.clearRenderState();
        }
    }

    @Override
    public void close() {
        layers.values().forEach(VertexBuffer::close);
        layers.clear();
    }
}
