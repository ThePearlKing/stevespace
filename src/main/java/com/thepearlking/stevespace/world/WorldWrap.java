package com.thepearlking.stevespace.world;

import com.thepearlking.stevespace.universe.CelestialBody;
import com.thepearlking.stevespace.universe.ServerUniverse;
import com.thepearlking.stevespace.world.planet.PlanetGeometry;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Makes planets round to walk on: anything that walks off the east or west edge of the map
 * comes back on the other side, and anything that walks over a pole comes out on the far side
 * of the pole, heading away from it.
 *
 * <p>On Earth the date line and the poles are always ocean, so the sea surface matches exactly
 * where the world wraps. The wrap happens a few blocks past the edge so that standing on the
 * line doesn't bounce you back and forth.
 */
public final class WorldWrap {

    /** How far past the edge something must be before it is moved, in blocks. */
    private static final double MARGIN = 2.0;
    /** Non-player entities are checked this often (ticks); players every tick. */
    private static final int ENTITY_CHECK_INTERVAL = 20;

    private WorldWrap() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        PlanetGeometry geometry = geometryOf(player.serverLevel());
        if (geometry == null) {
            return;
        }
        Entity root = player.getRootVehicle();
        if (root != player) {
            wrapEntity(root, geometry);
        } else {
            wrapPlayer(player, geometry);
        }
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)
                || level.getGameTime() % ENTITY_CHECK_INTERVAL != 0) {
            return;
        }
        PlanetGeometry geometry = geometryOf(level);
        if (geometry == null) {
            return;
        }
        List<Entity> outside = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof ServerPlayer) && !entity.isPassenger() && !entity.hasExactlyOnePlayerPassenger()
                    && isPastEdge(entity.getX(), entity.getZ(), geometry)) {
                outside.add(entity);
            }
        }
        // Moving entities while iterating the level's entity list is not allowed.
        for (Entity entity : outside) {
            wrapEntity(entity, geometry);
        }
    }

    /** The geometry of the planet this level is the surface of, or null if it is not a planet. */
    public static PlanetGeometry geometryOf(ServerLevel level) {
        CelestialBody body = ServerUniverse.bodyOf(level);
        return body == null ? null : new PlanetGeometry(body.radius());
    }

    private static boolean isPastEdge(double x, double z, PlanetGeometry geometry) {
        double half = geometry.circumference() / 2.0;
        return x < -half - MARGIN || x >= half + MARGIN || Math.abs(z) > geometry.poleDistance() + MARGIN;
    }

    private static void wrapPlayer(ServerPlayer player, PlanetGeometry geometry) {
        if (!isPastEdge(player.getX(), player.getZ(), geometry)) {
            return;
        }
        PlanetGeometry.Wrapped target = geometry.wrap(player.getX(), player.getZ());
        Vec3 motion = player.getDeltaMovement();
        float yaw = player.getYRot();
        if (target.crossedPole()) {
            yaw += 180;
            motion = new Vec3(-motion.x, motion.y, -motion.z);
        }
        player.teleportTo(player.serverLevel(), target.x(), player.getY(), target.z(),
                Set.<RelativeMovement>of(), yaw, player.getXRot());
        // Teleporting resets the client's momentum; give it back (matters when flying).
        player.setDeltaMovement(motion);
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }

    /** Wraps a non-player entity if it is past an edge. Public for the self-test. */
    public static void wrapEntity(Entity entity, PlanetGeometry geometry) {
        if (!isPastEdge(entity.getX(), entity.getZ(), geometry) || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        PlanetGeometry.Wrapped target = geometry.wrap(entity.getX(), entity.getZ());
        float yaw = entity.getYRot() + (target.crossedPole() ? 180 : 0);
        Vec3 motion = entity.getDeltaMovement();
        if (target.crossedPole()) {
            motion = new Vec3(-motion.x, motion.y, -motion.z);
        }
        entity.teleportTo(level, target.x(), entity.getY(), target.z(), Set.of(), yaw, entity.getXRot());
        entity.setDeltaMovement(motion);
    }
}
