package com.thepearlking.stevespace.client.vessel;

import com.thepearlking.stevespace.network.CabinWalkC2S;
import com.thepearlking.stevespace.network.SpaceNetwork;
import com.thepearlking.stevespace.vessel.VesselEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Walking about inside a ship in flight. You move in the ship's own frame, bumping into its
 * blocks, and feel what the ship is doing: pressed toward the engines while they burn, weightless
 * while it coasts (the walk keys then nudge you along like the suit's thrusters), normal weight
 * with a gravity generator aboard. Worked out here for the local player and sent to the server.
 */
public final class CabinWalk {

    private static final double HALF_WIDTH = 0.3;
    private static final double HEIGHT = 1.8;
    /** One g, in blocks per tick² (Minecraft's own gravity). */
    private static final double G = 0.08;

    private static VesselEntity vessel;
    private static Vector3d local;
    private static final Vector3d velocity = new Vector3d();
    private static boolean onGround;

    private CabinWalk() {
    }

    public static void install() {
        VesselEntity.clientWalk = CabinWalk::positionFor;
        VesselEntity.clientVessels = () -> net.minecraft.client.Minecraft.getInstance().level == null ? java.util.List.of()
                : net.minecraft.client.Minecraft.getInstance().level.entitiesForRendering();
        VesselEntity.clientAxes = com.thepearlking.stevespace.client.space.ClientSpace::axesOf;
    }

    /** Where the local player stands in this vessel, if they're walking about in it. */
    public static Vector3d positionFor(VesselEntity v, Entity e) {
        return v == vessel && local != null && e == Minecraft.getInstance().player ? local : null;
    }

    public static boolean walking() {
        return local != null;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.player != mc.player || !event.player.level().isClientSide) {
            return;
        }
        LocalPlayer player = (LocalPlayer) event.player;
        if (!(player.getVehicle() instanceof VesselEntity v) || v.stats() == null || !v.isWalking(player)) {
            vessel = null;
            local = null;
            return;
        }
        if (v != vessel || local == null) {
            Vector3d synced = v.crew().walkPosition(player.getUUID());
            if (synced == null) {
                return;
            }
            vessel = v;
            local = new Vector3d(synced);
            velocity.zero();
            onGround = false;
        }
        step(mc, player, v);
        SpaceNetwork.sendToServer(new CabinWalkC2S(local.x, local.y, local.z));
    }

    private static void step(Minecraft mc, LocalPlayer player, VesselEntity v) {
        Vector3f pull = v.cabinPull();
        Vector3d g = new Vector3d(pull.x, pull.y, pull.z).mul(G);
        double gl = g.length();
        boolean keys = mc.screen == null;
        double forward = keys ? (mc.options.keyUp.isDown() ? 1 : 0) - (mc.options.keyDown.isDown() ? 1 : 0) : 0;
        double strafe = keys ? (mc.options.keyLeft.isDown() ? 1 : 0) - (mc.options.keyRight.isDown() ? 1 : 0) : 0;
        boolean jump = keys && mc.options.keyJump.isDown();
        boolean sneak = keys && mc.options.keyShift.isDown();
        // The look is within the ship (the camera turns with it), so it's already in ship axes.
        double yaw = Math.toRadians(player.getYRot());
        double pitch = Math.toRadians(player.getXRot());
        Vector3d look = new Vector3d(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
        Vector3d flatForward = new Vector3d(-Math.sin(yaw), 0, Math.cos(yaw));

        if (gl > G * 0.12) {
            Vector3d down = new Vector3d(g).div(gl);
            Vector3d fwd = new Vector3d(look).sub(new Vector3d(down).mul(look.dot(down)));
            if (fwd.lengthSquared() < 1e-4) {
                fwd.set(flatForward).sub(new Vector3d(down).mul(flatForward.dot(down)));
            }
            fwd.normalize();
            Vector3d left = new Vector3d(fwd).cross(down).normalize();
            Vector3d wish = new Vector3d(fwd).mul(forward).add(new Vector3d(left).mul(strafe));
            if (wish.lengthSquared() > 0) {
                wish.normalize().mul(player.isSprinting() || keys && mc.options.keySprint.isDown() ? 0.2 : 0.13);
            }
            Vector3d along = new Vector3d(down).mul(velocity.dot(down));
            Vector3d plane = new Vector3d(velocity).sub(along);
            double grip = onGround ? 0.55 : 0.06;
            plane.add(new Vector3d(wish).sub(plane).mul(grip));
            along.mul(0.98);
            velocity.set(plane).add(along).add(g);
            if (jump && onGround) {
                // A jump: a little weaker under a heavy pull, higher in light ones.
                velocity.sub(new Vector3d(down).mul(0.42 * Math.min(1.4, Math.sqrt(gl / G))));
                onGround = false;
            }
        } else {
            // Weightless: drift; the walk keys push you gently along your view, jump and sneak up and down.
            Vector3d left = new Vector3d(flatForward).cross(new Vector3d(0, -1, 0)).normalize();
            Vector3d push = new Vector3d(look).mul(forward).add(new Vector3d(left).mul(strafe));
            if (push.lengthSquared() > 0) {
                velocity.add(push.normalize().mul(0.006));
            }
            velocity.y += (jump ? 0.008 : 0) - (sneak ? 0.008 : 0);
            velocity.add(g);
            if (velocity.length() > 0.3) {
                velocity.normalize().mul(0.3);
            }
        }
        move(v);
        // Never out past the ship's blocks.
        BlockPos lo = v.boundsMin();
        BlockPos hi = v.boundsMax();
        local.x = Math.max(lo.getX() - 1.5, Math.min(hi.getX() + 2.5, local.x));
        local.y = Math.max(lo.getY() - 1.5, Math.min(hi.getY() + 2.5, local.y));
        local.z = Math.max(lo.getZ() - 1.5, Math.min(hi.getZ() + 2.5, local.z));
    }

    /** Moves by this tick's velocity, stopping at the ship's blocks (like vanilla: up/down first, then across). */
    private static void move(VesselEntity v) {
        AABB box = new AABB(local.x - HALF_WIDTH, local.y, local.z - HALF_WIDTH, local.x + HALF_WIDTH, local.y + HEIGHT, local.z + HALF_WIDTH);
        List<VoxelShape> shapes = shapesAround(v, box.expandTowards(velocity.x, velocity.y, velocity.z).inflate(1));
        double dy = Shapes.collide(Direction.Axis.Y, box, shapes, velocity.y);
        box = box.move(0, dy, 0);
        double dx = Shapes.collide(Direction.Axis.X, box, shapes, velocity.x);
        box = box.move(dx, 0, 0);
        double dz = Shapes.collide(Direction.Axis.Z, box, shapes, velocity.z);
        boolean hitX = Math.abs(dx - velocity.x) > 1e-7;
        boolean hitY = Math.abs(dy - velocity.y) > 1e-7;
        boolean hitZ = Math.abs(dz - velocity.z) > 1e-7;
        // On the ground: stopped by something in the direction the pull points.
        Vector3f pull = v.cabinPull();
        double ax = Math.abs(pull.x), ay = Math.abs(pull.y), az = Math.abs(pull.z);
        if (ay >= ax && ay >= az) {
            onGround = hitY && Math.signum(velocity.y) == Math.signum(pull.y);
        } else if (ax >= az) {
            onGround = hitX && Math.signum(velocity.x) == Math.signum(pull.x);
        } else {
            onGround = hitZ && Math.signum(velocity.z) == Math.signum(pull.z);
        }
        local.add(dx, dy, dz);
        if (hitX) {
            velocity.x = 0;
        }
        if (hitY) {
            velocity.y = 0;
        }
        if (hitZ) {
            velocity.z = 0;
        }
    }

    private static List<VoxelShape> shapesAround(VesselEntity v, AABB area) {
        List<VoxelShape> shapes = new ArrayList<>();
        var blocks = v.structure().blocks();
        for (int x = (int) Math.floor(area.minX); x <= (int) Math.floor(area.maxX); x++) {
            for (int y = (int) Math.floor(area.minY); y <= (int) Math.floor(area.maxY); y++) {
                for (int z = (int) Math.floor(area.minZ); z <= (int) Math.floor(area.maxZ); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = blocks.get(pos);
                    if (state != null) {
                        VoxelShape shape = state.getCollisionShape(EmptyBlockGetter.INSTANCE, pos);
                        if (!shape.isEmpty()) {
                            shapes.add(shape.move(x, y, z));
                        }
                    }
                }
            }
        }
        return shapes;
    }

    /** People walking about a cabin are drawn on their feet, not sitting. */
    @SubscribeEvent
    public static void onRenderPre(RenderLivingEvent.Pre<?, ?> event) {
        if (event.getEntity().getVehicle() instanceof VesselEntity v && v.isWalking(event.getEntity())) {
            VesselEntity.drawingWalker = event.getEntity();
        }
    }

    @SubscribeEvent
    public static void onRenderPost(RenderLivingEvent.Post<?, ?> event) {
        VesselEntity.drawingWalker = null;
    }
}
