package com.thepearlking.stevespace.client.vessel;

import com.thepearlking.stevespace.vessel.VesselEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Method;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** In third person, pulls the camera back far enough to see the whole vessel you are flying. */
public final class VesselCamera {

    private static final float VANILLA_DISTANCE = 4f;

    private VesselCamera() {
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.player.getVehicle() instanceof VesselEntity vessel) || vessel.stats() == null
                || com.thepearlking.stevespace.client.RemoteClient.active()) {
            return; // (flying by remote control: the remote camera does it)
        }
        if (mc.options.getCameraType().isFirstPerson()) {
            seatView(event, vessel);
            return;
        }
        float wanted = (float) (vessel.stats().radius() * 2.4 + 3);
        if (wanted > VANILLA_DISTANCE) {
            try {
                MOVE.invoke(event.getCamera(), -(wanted - VANILLA_DISTANCE), 0f, 0f);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Camera.move unavailable", e);
            }
        }
    }

    /**
     * First person in a seat: you look around relative to the seat, so when the ship pitches or
     * rolls your view turns with it and the cabin stays level in front of you.
     */
    private static void seatView(ViewportEvent.ComputeCameraAngles event, VesselEntity vessel) {
        org.joml.Quaternionf ship = vessel.renderOrientation((float) event.getPartialTick());
        // Vanilla's camera turn for this yaw/pitch (looking down -z), taken as a look within the
        // seat; the ship's own turn goes on top of it.
        org.joml.Quaternionf look = cameraTurn(event.getYaw(), event.getPitch(), 0);
        org.joml.Quaternionf world = new org.joml.Quaternionf(ship).mul(look);
        org.joml.Vector3f forward = world.transform(new org.joml.Vector3f(0, 0, -1));
        float yaw = (float) Math.toDegrees(Math.atan2(-forward.x, forward.z));
        float pitch = (float) Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, -forward.y))));
        // Whatever is left after yaw and pitch is a turn about the view axis: the roll.
        org.joml.Quaternionf rest = cameraTurn(yaw, pitch, 0).conjugate().mul(world);
        org.joml.Vector3f up = rest.transform(new org.joml.Vector3f(0, 1, 0));
        float roll = (float) Math.toDegrees(Math.atan2(-up.x, up.y));
        event.setYaw(yaw);
        event.setPitch(pitch);
        event.setRoll(roll);
    }

    /** The rotation vanilla's camera uses for these angles (degrees). */
    private static org.joml.Quaternionf cameraTurn(float yaw, float pitch, float roll) {
        return new org.joml.Quaternionf().rotationYXZ((float) Math.PI - (float) Math.toRadians(yaw),
                -(float) Math.toRadians(pitch), (float) Math.toRadians(roll));
    }

    /** Camera.move is protected; reached by its SRG name so it works in dev and release. */
    private static final Method MOVE = ObfuscationReflectionHelper.findMethod(Camera.class, "m_90568_",
            float.class, float.class, float.class);
}
