package com.thepearlking.stevespace.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thepearlking.stevespace.block.ScienceBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The live screens of the science instruments. */
public final class ScienceRenderers {

    private ScienceRenderers() {
    }

    public static final class Magnetometer implements BlockEntityRenderer<ScienceBlocks.MagnetometerEntity> {
        private final Font font;

        public Magnetometer(BlockEntityRendererProvider.Context context) {
            this.font = context.getFont();
        }

        @Override
        public void render(ScienceBlocks.MagnetometerEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.player.distanceToSqr(be.getBlockPos().getCenter()) > 24 * 24) {
                return;
            }
            List<String[]> lines = new ArrayList<>();
            var place = Instruments.at(be.getLevel(), be.getBlockPos());
            if (place == null) {
                lines.add(new String[]{"NO DATA", ""});
            } else {
                var field = Instruments.field(place);
                lines.add(new String[]{"FIELD", String.format(Locale.ROOT, "%,.0f nT", field.nanotesla())});
                long dip = Math.round(field.inclination());
                lines.add(new String[]{"DIP", (dip > 0 ? "+" : "") + dip + "°"});
                lines.add(new String[]{"AT", place.body().displayName().toUpperCase(Locale.ROOT)});
            }
            FlightDisplayRenderer.drawScreen(font, lines, be.getBlockState(), pose, buffers);
        }
    }

    public static final class Plasma implements BlockEntityRenderer<ScienceBlocks.PlasmaReceiverEntity> {
        private final Font font;

        public Plasma(BlockEntityRendererProvider.Context context) {
            this.font = context.getFont();
        }

        @Override
        public void render(ScienceBlocks.PlasmaReceiverEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.player.distanceToSqr(be.getBlockPos().getCenter()) > 24 * 24) {
                return;
            }
            List<String[]> lines = new ArrayList<>();
            boolean on = be.getBlockState().getValue(ScienceBlocks.PlasmaReceiver.LISTENING);
            var place = Instruments.at(be.getLevel(), be.getBlockPos());
            String source = place == null ? "" : switch (Instruments.sound(place)) {
                case "plasma_earth" -> "CHORUS";
                case "plasma_jupiter" -> "JOVIAN";
                case "plasma_ganymede" -> "GANYMEDE";
                case "plasma_saturn" -> "KILOMETRIC";
                case "plasma_mercury" -> "SUBSTORMS";
                case "plasma_venus" -> "WHISTLERS";
                case "plasma_moon" -> "LUNAR WAKE";
                case "plasma_mars" -> "CYCLOTRON";
                case "plasma_io" -> "DECAMETRIC";
                case "plasma_europa" -> "EUROPA";
                case "plasma_callisto" -> "PLASMA SHEET";
                case "plasma_titan" -> "IONOSPHERE";
                case "plasma_enceladus" -> "PLUME DUST";
                case "plasma_uranus" -> "URANIAN";
                case "plasma_neptune" -> "NEPTUNIAN";
                case "plasma_triton" -> "AURORA";
                case "plasma_pluto", "plasma_charon" -> "PICKUP IONS";
                case "plasma_sun" -> "TYPE III";
                case "plasma_acheron" -> "ACHERON";
                default -> "SOLAR WIND";
            };
            lines.add(new String[]{"RX", on ? "ON" : "OFF"});
            lines.add(new String[]{"", on ? source : ""});
            FlightDisplayRenderer.drawScreen(font, lines, be.getBlockState(), pose, buffers);
        }
    }
}
