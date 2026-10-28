package com.thepearlking.stevespace.client.vessel;

import com.thepearlking.stevespace.world.SpaceDimension;
import com.thepearlking.stevespace.debug.ClientTest;
import com.thepearlking.stevespace.registry.ModParticles;
import com.thepearlking.stevespace.vessel.VesselEntity;
import com.thepearlking.stevespace.vessel.VesselStats;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

/** Engine exhaust for every vessel nearby: flame at the nozzles, smoke trail, ground billow. */
public final class VesselEffects {

    private static final double EFFECT_RANGE = 384;
    /** Exhaust leaves the nozzle at about this speed (blocks per tick) relative to the vessel. */
    private static final double EXHAUST_SPEED = 1.1;
    /** Engine sounds playing now, by vessel id. */
    private static final java.util.Map<Integer, VesselEngineSound> ENGINE_SOUNDS = new java.util.HashMap<>();

    private VesselEffects() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused() || mc.player == null) {
            return;
        }
        VesselRenderer.pruneMeshes();
        ENGINE_SOUNDS.values().removeIf(sound -> sound.isStopped());
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof VesselEntity vessel && vessel.throttle() > 0.01f && !ENGINE_SOUNDS.containsKey(vessel.getId())) {
                VesselEngineSound sound = new VesselEngineSound(vessel);
                ENGINE_SOUNDS.put(vessel.getId(), sound);
                mc.getSoundManager().play(sound);
            }
        }
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof VesselEntity vessel && vessel.stats() != null && vessel.throttle() > 0.01f
                    && vessel.distanceToSqr(mc.player) < EFFECT_RANGE * EFFECT_RANGE) {
                exhaust(level, vessel);
            }
        }
    }

    private static void exhaust(ClientLevel level, VesselEntity vessel) {
        RandomSource random = level.random;
        float throttle = vessel.throttle();
        // A ship that is blocks stands upright in its cell: the exhaust leaves its engine blocks
        // as they stand, and the motion it streams back from is turned into the ship's own axes.
        boolean blocks = vessel.materialized();
        Quaternionf q = blocks ? new Quaternionf() : vessel.renderOrientation(1f);
        Vector3d com = vessel.stats().centerOfMass();
        Vector3f velocity = blocks ? vessel.renderOrientation(1f).conjugate(new Quaternionf()).transform(new Vector3f(vessel.velocity()))
                : vessel.velocity();
        double vx = velocity.x / 20.0;
        double vy = velocity.y / 20.0;
        double vz = velocity.z / 20.0;
        // High up and in orbit the vessel stays put in its space cell while really moving, so the
        // exhaust is given the opposite of that motion instead: it still streams away behind.
        boolean parked = SpaceDimension.isSpace(level);
        boolean vacuum = parked && velocity.lengthSquared() == 0; // orbit: velocity over the ground isn't tracked
        double fx = parked ? 0 : vx;
        double fy = parked ? 0 : vy;
        double fz = parked ? 0 : vz;
        double sx = parked ? -0.7 * vx : 0.3 * vx;
        double sy = parked ? -0.7 * vy : 0.3 * vy;
        double sz = parked ? -0.7 * vz : 0.3 * vz;
        for (VesselStats.Engine engine : vessel.stats().engines()) {
            Vector3f exit = q.transform(new Vector3f((float) (engine.nozzleExit().x - com.x),
                    (float) (engine.nozzleExit().y - com.y), (float) (engine.nozzleExit().z - com.z)));
            Vector3f out = q.transform(new Vector3f((float) -engine.thrustDirection().x,
                    (float) -engine.thrustDirection().y, (float) -engine.thrustDirection().z));
            Vec3 nozzle = blocks
                    ? Vec3.atLowerCornerOf(vessel.blockOrigin()).add(engine.nozzleExit().x, engine.nozzleExit().y, engine.nozzleExit().z)
                    : vessel.position().add(exit.x, exit.y, exit.z);

            int flames = 2 + Math.round(5 * throttle);
            for (int i = 0; i < flames; i++) {
                double spread = 0.06;
                double speed = EXHAUST_SPEED * (0.7 + 0.5 * throttle);
                double start = 0.25 + random.nextDouble() * 0.3;
                level.addParticle(ModParticles.EXHAUST_FLAME.get(),
                        nozzle.x + out.x * start + (random.nextDouble() - 0.5) * 0.35,
                        nozzle.y + out.y * start + (random.nextDouble() - 0.5) * 0.35,
                        nozzle.z + out.z * start + (random.nextDouble() - 0.5) * 0.35,
                        fx + out.x * speed + random.nextGaussian() * spread,
                        fy + out.y * speed + random.nextGaussian() * spread,
                        fz + out.z * speed + random.nextGaussian() * spread);
            }
            if (!vacuum && random.nextFloat() < 0.4f + 0.6f * throttle) {
                Vec3 trail = nozzle.add(out.x * 2.5, out.y * 2.5, out.z * 2.5);
                level.addParticle(ModParticles.EXHAUST_SMOKE.get(), trail.x, trail.y, trail.z,
                        sx + out.x * 0.25 + random.nextGaussian() * 0.03,
                        sy + out.y * 0.25 + random.nextGaussian() * 0.03,
                        sz + out.z * 0.25 + random.nextGaussian() * 0.03);
            }
            if (!parked) {
                groundBillow(level, random, nozzle, out, throttle);
            }
        }
    }

    /**
     * Where the exhaust hits the ground (launch, landing) it splashes out sideways: fire right
     * under the engine, then smoke rolling outward along the ground.
     */
    private static void groundBillow(ClientLevel level, RandomSource random, Vec3 nozzle, Vector3f out, float throttle) {
        if (out.y > -0.5f) {
            return; // only engines pointing downward
        }
        BlockPos.MutableBlockPos pos = BlockPos.containing(nozzle.x, nozzle.y - 0.01, nozzle.z).mutable();
        for (int depth = 0; depth < 16; depth++) {
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                double surface = pos.getY() + 1.05;
                double strength = throttle * (1 - depth / 16.0);
                if (depth < 4) {
                    int fire = Math.round((float) strength * 4);
                    for (int i = 0; i < fire; i++) {
                        double angle = random.nextDouble() * Math.PI * 2;
                        double speed = 0.25 + random.nextDouble() * 0.3;
                        level.addParticle(ModParticles.EXHAUST_FLAME.get(), nozzle.x, surface + 0.2, nozzle.z,
                                Math.cos(angle) * speed, 0.02, Math.sin(angle) * speed);
                    }
                }
                int puffs = Math.max(random.nextFloat() < strength ? 1 : 0, Math.round((float) strength * 2));
                for (int i = 0; i < puffs; i++) {
                    double angle = random.nextDouble() * Math.PI * 2;
                    double speed = 0.3 + random.nextDouble() * 0.35;
                    level.addParticle(ModParticles.EXHAUST_SMOKE.get(), nozzle.x, surface + 0.4, nozzle.z,
                            Math.cos(angle) * speed, 0.02 + random.nextDouble() * 0.04, Math.sin(angle) * speed);
                }
                return;
            }
            pos.move(0, -1, 0);
        }
    }
}
