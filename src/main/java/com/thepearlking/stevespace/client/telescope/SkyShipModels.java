package com.thepearlking.stevespace.client.telescope;

import com.mojang.blaze3d.platform.NativeImage;
import com.thepearlking.stevespace.client.vessel.VesselMesh;
import com.thepearlking.stevespace.network.SkyShipBlocksC2S;
import com.thepearlking.stevespace.network.SkyShipBlocksS2C;
import com.thepearlking.stevespace.network.SpaceNetwork;
import com.thepearlking.stevespace.radio.SkyShips;
import com.thepearlking.stevespace.vessel.VesselStructure;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;

import java.util.HashMap;
import java.util.Map;

/**
 * The real blocks of ships seen in the sky, baked into meshes so they can be drawn as they are,
 * from wherever you look at them. Fetched from the server once per version of each ship (a
 * station that is built onto gets fetched again).
 */
public final class SkyShipModels {

    private record Model(long version, VesselMesh mesh) {
    }

    private static final Map<String, Model> MODELS = new HashMap<>();
    private static final Map<String, Long> ASKED = new HashMap<>();
    private static DynamicTexture daylight;

    private SkyShipModels() {
    }

    /** The mesh to draw this ship with (the last version while a newer one is on its way), or null. */
    public static VesselMesh mesh(SkyShips.Ship ship) {
        Model m = MODELS.get(ship.id());
        if (m == null || m.version() != ship.version()) {
            long now = System.currentTimeMillis();
            Long asked = ASKED.get(ship.id());
            if (asked == null || now - asked > 3000) {
                ASKED.put(ship.id(), now);
                SpaceNetwork.sendToServer(new SkyShipBlocksC2S(ship.id()));
            }
        }
        return m == null ? null : m.mesh();
    }

    public static void receive(SkyShipBlocksS2C msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        VesselStructure structure = VesselStructure.load(msg.structure(), mc.level.registryAccess());
        var biome = mc.level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME)
                .getHolderOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS).value();
        Model old = MODELS.put(msg.id(), new Model(msg.version(), new VesselMesh(structure, biome)));
        if (old != null) {
            old.mesh().close();
        }
    }

    /** Drops the meshes of ships no longer listed. */
    public static void prune(java.util.Collection<SkyShips.Ship> listed) {
        java.util.Set<String> ids = new java.util.HashSet<>();
        listed.forEach(s -> ids.add(s.id()));
        MODELS.entrySet().removeIf(e -> {
            if (ids.contains(e.getKey())) {
                return false;
            }
            e.getValue().mesh().close();
            return true;
        });
    }

    /** A plain full-bright light map (GL id), for ships lit by the Sun rather than the local time of day. */
    public static int daylight() {
        if (daylight == null) {
            NativeImage image = new NativeImage(16, 16, false);
            image.fillRect(0, 0, 16, 16, 0xFFFFFFFF);
            daylight = new DynamicTexture(image);
        }
        return daylight.getId();
    }
}
