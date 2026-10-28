package com.thepearlking.stevespace.vessel;

import com.thepearlking.stevespace.block.PropellantTankBlockEntity;
import com.thepearlking.stevespace.block.SeatEntity;
import com.thepearlking.stevespace.config.SpaceConfig;
import com.thepearlking.stevespace.registry.ModEntities;
import com.thepearlking.stevespace.universe.CelestialBody;
import com.thepearlking.stevespace.universe.ServerUniverse;
import com.thepearlking.stevespace.world.planet.PlanetGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import com.thepearlking.stevespace.universe.DVec3;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A vessel in flight: a structure of blocks lifted out of the world, flying as one body.
 *
 * <p>The entity's position is the vessel's centre of mass. A block at vessel coordinates
 * {@code p} is at {@code position + orientation · (p + 0.5 − centreOfMass)} in the world.
 * Velocity is in true blocks per second along east (+x), up (+y) and south (+z).
 *
 * <p>Life cycle: assembled from blocks ({@link #launch}) → GROUNDED until its engines lift it →
 * FLYING → touches down gently and goes GROUNDED → turned back into blocks once the engines are
 * off ({@link #land}); or touches down too fast and is destroyed ({@link #crash}).
 */
public class VesselEntity extends Entity implements IEntityAdditionalSpawnData {

    private static final EntityDataAccessor<Quaternionf> ORIENTATION =
            SynchedEntityData.defineId(VesselEntity.class, EntityDataSerializers.QUATERNION);
    private static final EntityDataAccessor<Float> THROTTLE =
            SynchedEntityData.defineId(VesselEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> PROPELLANT =
            SynchedEntityData.defineId(VesselEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Vector3f> VELOCITY =
            SynchedEntityData.defineId(VesselEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Boolean> GROUNDED =
            SynchedEntityData.defineId(VesselEntity.class, EntityDataSerializers.BOOLEAN);
    /** Autopilot status for the HUD: a translation key, or empty when it's off. */
    private static final EntityDataAccessor<String> AUTOPILOT =
            SynchedEntityData.defineId(VesselEntity.class, EntityDataSerializers.STRING);
    /** Which way SAS is holding the ship (an ordinal of {@link Sas}). */
    private static final EntityDataAccessor<Integer> SAS =
            SynchedEntityData.defineId(VesselEntity.class, EntityDataSerializers.INT);
    /** Who sits where and who is up walking about (see {@link CabinCrew}). */
    private static final EntityDataAccessor<CompoundTag> CREW =
            SynchedEntityData.defineId(VesselEntity.class, EntityDataSerializers.COMPOUND_TAG);
    /** The pull felt inside, in the ship's own axes, in g (thrust and drag push you; gravity alone doesn't). */
    private static final EntityDataAccessor<Vector3f> CABIN_PULL =
            SynchedEntityData.defineId(VesselEntity.class, EntityDataSerializers.VECTOR3);
    /** How fast it turns (radians a second, about its own axes): lets clients turn it smoothly. */
    private static final EntityDataAccessor<Vector3f> SPIN = SynchedEntityData.defineId(VesselEntity.class, EntityDataSerializers.VECTOR3);
    /**
     * In orbit: which way it points in fixed space (the cell's axes turn as it goes round; this
     * doesn't, unless it spins). All zero when not in orbit.
     */
    private static final EntityDataAccessor<Quaternionf> ATTITUDE = SynchedEntityData.defineId(VesselEntity.class, EntityDataSerializers.QUATERNION);
    /** The server tick the orientation above belongs to (updates don't arrive evenly). */
    private static final EntityDataAccessor<Integer> STAMP = SynchedEntityData.defineId(VesselEntity.class, EntityDataSerializers.INT);
    /**
     * In space the ship is real blocks standing still in its cell (see {@link #materialize}); this
     * entity is then only its flight: orbit, engines, autopilot, orientation. Turning and thrust
     * move the sky around the blocks, never the blocks.
     */
    private static final EntityDataAccessor<Boolean> MATERIALIZED =
            SynchedEntityData.defineId(VesselEntity.class, EntityDataSerializers.BOOLEAN);
    /** Where the ship's blocks stand while materialized (their structure's origin). */
    private static final EntityDataAccessor<BlockPos> BLOCK_ORIGIN =
            SynchedEntityData.defineId(VesselEntity.class, EntityDataSerializers.BLOCK_POS);
    /** How hard the RCS thrusters are pushing, in the vessel's own axes (each -1..1), for the puffs. */
    private static final EntityDataAccessor<Vector3f> RCS =
            SynchedEntityData.defineId(VesselEntity.class, EntityDataSerializers.VECTOR3);

    /** Keeps the chunks a flying vessel is in, and is heading into, loaded and ticking. */
    private static final net.minecraft.server.level.TicketType<net.minecraft.world.level.ChunkPos> FLIGHT_TICKET =
            net.minecraft.server.level.TicketType.create("stevespace_vessel",
                    java.util.Comparator.comparingLong(net.minecraft.world.level.ChunkPos::toLong), 60);
    /**
     * Vessels on this server. They keep ticking even where the world around them isn't (high up,
     * outrunning the chunks loaded around their crew), the way players do.
     */
    private static final java.util.Set<VesselEntity> SERVER_VESSELS =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    /** How far ahead (seconds of flight) chunks are loaded. */
    private static final int LOOK_AHEAD_SECONDS = 4;

    /** Drag constant: a 12-block capsule falls at about 55 m/s at sea level. */
    public static final double DRAG_CONSTANT = 0.00094;
    /** Ticks a vessel must rest with engines off before it turns back into blocks. */
    private static final int SETTLE_TICKS = 30;
    private static final double TICK = 0.05;

    private VesselStructure structure;
    private VesselStats stats;

    // Server-side flight state (double precision).
    private final Quaterniond orientation = new Quaterniond();
    private final Vector3d velocity = new Vector3d();
    private final Vector3d angularVelocity = new Vector3d();
    private double propellant;
    /** What the engines and thrusters are doing this tick (the pilot's input, or the autopilot's). */
    private VesselControls controls = VesselControls.IDLE;
    /** The pilot's latest input. */
    private VesselControls pilotControls = VesselControls.IDLE;
    private final CabinCrew crew = new CabinCrew();
    private boolean crewDirty;
    /** Ticks each crew record has had nobody aboard to match it (dropped after a while). */
    private final Map<java.util.UUID, Integer> crewAbsent = new java.util.HashMap<>();
    private CompoundTag clientCrewTag;
    private CabinCrew clientCrew = new CabinCrew();
    /** Acceleration from everything but gravity this tick (world axes, blocks/s²): what the crew feels. */
    private final Vector3d felt = new Vector3d();
    private boolean gravityGenerator;
    /** SAS "hold": the direction to keep pointing (inertial in orbit, world axes in the air). */
    private DVec3 sasHold;

    /** What SAS keeps the ship pointed at. */
    public enum Sas { OFF, HOLD, PROGRADE, RETROGRADE, NORMAL, ANTINORMAL, RADIAL_OUT, RADIAL_IN, MANEUVER }

    /** Where SAS's maneuver mode points (inertial), set when the pilot plans a node. */
    private DVec3 sasManeuver;
    private BlockPos boundsMin = BlockPos.ZERO;
    private BlockPos boundsMax = BlockPos.ZERO;
    private final Autopilot autopilot = new Autopilot();
    /** Where the autopilot wants the engines to push (world axes), or null to fly by hand. */
    private DVec3 steerTarget;
    /**
     * Visiting a ship that is blocks: its blocks stand upright in the cell whichever way it really
     * points, so this visitor is drawn turned into that ship's own frame (null otherwise).
     */
    private Quaterniond shownFrame;
    /** A whole attitude the autopilot wants held (vessel to world axes), or null. */
    private Quaterniond steerAttitude;
    /** The push the autopilot wants from the RCS thrusters (world axes, blocks/s²), or null. */
    private DVec3 rcsWanted;
    private int restingTicks;
    private boolean allowDismount;
    /** Who launched it (they may dock anything of theirs to it), or null: anyone's. */
    private java.util.UUID owner;
    /** Ticks left before this vessel may dock again (just undocked: let the two drift apart). */
    int dockCooldown;
    /** Vertical speed paused while waiting in the air for the ground below to load (0: none). */
    private double heldFall;
    /** Ticks spent holding still for the world ahead to load. */
    private int waitingTicks;
    /** Server tick on which this vessel last ran its own tick (to catch it when it doesn't). */
    private int lastTickedOn = -1;
    /** Set while the vessel is in space (null while flying over a surface). */
    private OrbitalFlight orbital;
    /**
     * "Map mode": high in the air, above where the world's chunks matter, the vessel waits in its
     * space-dimension cell and flies this position over its body's map instead (same physics as
     * low down). Null when not in map mode.
     */
    private Vector3d mapPosition;
    private CelestialBody mapBody;
    /** Its cell in the space dimension while in map mode (orbit mode keeps it in {@link #orbital}). */
    private int mapCell;

    // Client-side: previous orientation for smooth rendering between ticks.
    private final Quaternionf currentOrientation = new Quaternionf();

    public VesselEntity(EntityType<? extends VesselEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    // ---------------------------------------------------------------------------------------
    // Creating a vessel from blocks
    // ---------------------------------------------------------------------------------------

    /**
     * Lifts the structure at {@code origin} out of the world as a vessel and seats {@code pilot}.
     * The caller has already checked the structure (size, engines, seat).
     */
    public static VesselEntity launch(ServerLevel level, BlockPos origin, VesselStructure structure, Player pilot) {
        VesselEntity vessel = new VesselEntity(ModEntities.VESSEL.get(), level);
        vessel.setStructure(structure);
        var known = com.thepearlking.stevespace.world.ShipNames.recall(level, origin, structure);
        if (known != null) {
            vessel.setCustomName(known); // the name it had when it came down here
        } else if (pilot != null) {
            vessel.setCustomName(net.minecraft.network.chat.Component.translatable("entity.stevespace.vessel.named", pilot.getName()));
        }
        vessel.propellant = totalPropellant(structure);
        vessel.owner = pilot == null ? null : pilot.getUUID();
        Vector3d com = vessel.stats.centerOfMass();
        vessel.setPos(origin.getX() + com.x, origin.getY() + com.y, origin.getZ() + com.z);
        vessel.setGrounded(true);
        vessel.syncState();
        VesselAssembler.removeFromWorld(level, origin, structure);
        level.addFreshEntity(vessel);
        if (pilot != null) {
            leaveGroundSeat(pilot);
            pilot.startRiding(vessel, true);
        }
        return vessel;
    }

    /** Takes a player out of a ground seat (pilot seats otherwise hold on to their rider). */
    public static void leaveGroundSeat(Entity rider) {
        if (rider.getVehicle() instanceof SeatEntity seat) {
            seat.requestDismount(rider);
        } else {
            rider.stopRiding();
        }
    }

    private static double totalPropellant(VesselStructure structure) {
        double total = 0;
        for (CompoundTag tag : structure.blockEntities().values()) {
            total += tag.getDouble("propellant");
        }
        return total;
    }

    private void setStructure(VesselStructure structure) {
        this.structure = structure;
        this.stats = new VesselStats(structure);
        int[] lo = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
        int[] hi = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (BlockPos p : structure.blocks().keySet()) {
            lo[0] = Math.min(lo[0], p.getX());
            lo[1] = Math.min(lo[1], p.getY());
            lo[2] = Math.min(lo[2], p.getZ());
            hi[0] = Math.max(hi[0], p.getX());
            hi[1] = Math.max(hi[1], p.getY());
            hi[2] = Math.max(hi[2], p.getZ());
        }
        this.boundsMin = structure.blocks().isEmpty() ? BlockPos.ZERO : new BlockPos(lo[0], lo[1], lo[2]);
        this.boundsMax = structure.blocks().isEmpty() ? BlockPos.ZERO : new BlockPos(hi[0], hi[1], hi[2]);
        this.gravityGenerator = structure.blocks().values().stream()
                .anyMatch(b -> b.is(com.thepearlking.stevespace.registry.ModBlocks.GRAVITY_GENERATOR.get()));
        refreshDimensions();
    }

    public VesselStructure structure() {
        return structure;
    }

    public VesselStats stats() {
        return stats;
    }

    public java.util.UUID owner() {
        return owner;
    }

    /** Which way the ship points (vessel axes to its world axes). */
    public Quaterniond orientationCopy() {
        return new Quaterniond(orientation);
    }

    void setShownFrame(Quaterniond frame) {
        shownFrame = frame;
    }

    void setOwner(java.util.UUID owner) {
        this.owner = owner;
    }


    /** The cell's axes as rows (for {@link #cellTurn}). */
    private double[] axisRows() {
        return new double[]{orbital.axisX().x(), orbital.axisX().y(), orbital.axisX().z(),
                orbital.axisY().x(), orbital.axisY().y(), orbital.axisY().z(),
                orbital.axisZ().x(), orbital.axisZ().y(), orbital.axisZ().z()};
    }

    /** Which way the ship points in fixed space (vessel axes to the inertial frame); orbit only. */
    public Quaterniond inertialOrientation() {
        return cellTurn(axisRows()).conjugate().mul(orientation).normalize();
    }

    /** An attitude in fixed space turned into this ship's world axes; orbit only. */
    public Quaterniond worldOrientation(Quaterniond inertial) {
        return cellTurn(axisRows()).mul(inertial).normalize();
    }

    /** Centre of a docking port's outer face, relative to the body (inertial); orbit only. */
    public DVec3 portFaceInertial(VesselStats.Port port) {
        Vector3d local = new Vector3d(port.pos().getX() + 0.5 + port.facing().getStepX() * 0.5,
                port.pos().getY() + 0.5 + port.facing().getStepY() * 0.5,
                port.pos().getZ() + 0.5 + port.facing().getStepZ() * 0.5).sub(stats.centerOfMass());
        orientation.transform(local);
        return orbital.state().position().add(orbital.toInertial(local.x, local.y, local.z));
    }

    /** The way a docking port points (inertial); orbit only. */
    public DVec3 portDirectionInertial(VesselStats.Port port) {
        Vector3d d = orientation.transform(new Vector3d(port.facing().getStepX(), port.facing().getStepY(), port.facing().getStepZ()));
        return orbital.toInertial(d.x, d.y, d.z);
    }

    /** Whether this is {@code player}'s ship (one with no owner is everyone's). */
    public boolean ownedBy(Player player) {
        return owner == null || player != null && owner.equals(player.getUUID());
    }

    // ---------------------------------------------------------------------------------------
    // Synced data and saving
    // ---------------------------------------------------------------------------------------

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(ORIENTATION, new Quaternionf());
        builder.define(THROTTLE, 0f);
        builder.define(PROPELLANT, 0f);
        builder.define(VELOCITY, new Vector3f());
        builder.define(GROUNDED, true);
        builder.define(AUTOPILOT, "");
        builder.define(CREW, new CompoundTag());
        builder.define(SAS, 0);
        builder.define(CABIN_PULL, new Vector3f());
        builder.define(SPIN, new Vector3f());
        builder.define(ATTITUDE, new Quaternionf(0, 0, 0, 0));
        builder.define(STAMP, 0);
        builder.define(MATERIALIZED, false);
        builder.define(BLOCK_ORIGIN, BlockPos.ZERO);
        builder.define(RCS, new Vector3f());
    }

    private void syncState() {
        Quaterniond drawn = shownFrame != null ? new Quaterniond(shownFrame).mul(orientation) : orientation;
        entityData.set(ORIENTATION, new Quaternionf((float) drawn.x, (float) drawn.y, (float) drawn.z, (float) drawn.w));
        entityData.set(SPIN, new Vector3f((float) angularVelocity.x, (float) angularVelocity.y, (float) angularVelocity.z));
        entityData.set(STAMP, tickCount);
        if (orbital != null) {
            Quaterniond cell = cellTurn(new double[]{orbital.axisX().x(), orbital.axisX().y(), orbital.axisX().z(),
                    orbital.axisY().x(), orbital.axisY().y(), orbital.axisY().z(),
                    orbital.axisZ().x(), orbital.axisZ().y(), orbital.axisZ().z()});
            Quaterniond fixed = cell.conjugate(new Quaterniond()).mul(orientation).normalize();
            entityData.set(ATTITUDE, new Quaternionf((float) fixed.x, (float) fixed.y, (float) fixed.z, (float) fixed.w));
        } else {
            entityData.set(ATTITUDE, new Quaternionf(0, 0, 0, 0));
        }
        if (orbital == null) {
            entityData.set(THROTTLE, (float) controls.throttle());
        }
        entityData.set(PROPELLANT, (float) propellant);
        entityData.set(VELOCITY, new Vector3f((float) velocity.x, (float) velocity.y, (float) velocity.z));
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setStructure(VesselStructure.load(tag.getCompound("structure"), registryAccess()));
        propellant = tag.getDouble("propellant");
        orientation.set(tag.getDouble("qx"), tag.getDouble("qy"), tag.getDouble("qz"), tag.getDouble("qw"));
        if (orientation.lengthSquared() < 0.5) {
            orientation.identity();
        }
        velocity.set(tag.getDouble("vx"), tag.getDouble("vy"), tag.getDouble("vz"));
        setGrounded(tag.getBoolean("grounded"));
        orbital = tag.contains("orbit") && !level().isClientSide
                ? OrbitalFlight.load(tag.getCompound("orbit"), com.thepearlking.stevespace.universe.ServerUniverse.system()) : null;
        if (tag.contains("autopilot")) {
            autopilot.load(tag.getCompound("autopilot"));
        }
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        if (tag.contains("lever")) {
            pilotControls = new VesselControls(tag.getDouble("lever"), 0, 0, 0);
        }
        if (tag.contains("crew")) {
            CabinCrew loaded = CabinCrew.load(tag.getCompound("crew"));
            for (java.util.UUID id : loaded.everyone()) {
                if (loaded.isWalking(id)) {
                    crew.walk(id, loaded.walkPosition(id));
                } else {
                    crew.sit(id, loaded.seatOf(id));
                }
            }
            crewDirty = true;
        }
        if (tag.getBoolean("materialized")) {
            entityData.set(MATERIALIZED, true);
            entityData.set(BLOCK_ORIGIN, new BlockPos(tag.getInt("ox"), tag.getInt("oy"), tag.getInt("oz")));
        }
        if (tag.contains("map") && !level().isClientSide) {
            CompoundTag map = tag.getCompound("map");
            mapBody = ServerUniverse.system().body(map.getString("body"));
            mapPosition = mapBody == null ? null : new Vector3d(map.getDouble("x"), map.getDouble("y"), map.getDouble("z"));
            mapCell = map.getInt("cell");
        }
        syncState();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.put("structure", structure.save());
        tag.putDouble("propellant", propellant);
        tag.putDouble("qx", orientation.x);
        tag.putDouble("qy", orientation.y);
        tag.putDouble("qz", orientation.z);
        tag.putDouble("qw", orientation.w);
        tag.putDouble("vx", velocity.x);
        tag.putDouble("vy", velocity.y);
        tag.putDouble("vz", velocity.z);
        tag.putBoolean("grounded", isGrounded());
        if (orbital != null) {
            tag.put("orbit", orbital.save());
        }
        if (autopilot.active()) {
            tag.put("autopilot", autopilot.save());
        }
        tag.put("crew", crew.save());
        tag.putDouble("lever", pilotControls.throttle()); // the throttle as the pilot left it
        if (owner != null) {
            tag.putUUID("owner", owner);
        }
        if (materialized()) {
            tag.putBoolean("materialized", true);
            tag.putInt("ox", blockOrigin().getX());
            tag.putInt("oy", blockOrigin().getY());
            tag.putInt("oz", blockOrigin().getZ());
        }
        if (mapPosition != null && mapBody != null) {
            CompoundTag map = new CompoundTag();
            map.putString("body", mapBody.id());
            map.putDouble("x", mapPosition.x);
            map.putDouble("y", mapPosition.y);
            map.putDouble("z", mapPosition.z);
            map.putInt("cell", mapCell);
            tag.put("map", map);
        }
    }

    @Override
    public void writeSpawnData(FriendlyByteBuf buffer) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes)) {
            NbtIo.writeCompressed(structure.save(), out);
            buffer.writeByteArray(bytes.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("Could not encode vessel", e);
        }
    }

    @Override
    public void readSpawnData(FriendlyByteBuf buffer) {
        byte[] data = buffer.readByteArray();
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            CompoundTag tag = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
            setStructure(VesselStructure.load(tag, registryAccess()));
        } catch (IOException e) {
            throw new IllegalStateException("Could not decode vessel", e);
        }
    }

    // ---------------------------------------------------------------------------------------
    // State accessors (used by the HUD and renderer on the client)
    // ---------------------------------------------------------------------------------------

    public boolean isGrounded() {
        return entityData.get(GROUNDED);
    }

    private void setGrounded(boolean grounded) {
        entityData.set(GROUNDED, grounded);
    }

    public float throttle() {
        return entityData.get(THROTTLE);
    }

    /** The throttle the pilot (or autopilot) is asking for; the engines follow it within a second. */
    public double commandedThrottle() {
        return controls.throttle();
    }

    public double propellant() {
        return entityData.get(PROPELLANT);
    }

    /** Velocity in blocks per second (east, up, south). */
    public Vector3f velocity() {
        return entityData.get(VELOCITY);
    }

    /** The orientation as last synced (either side). */
    public Quaternionf syncedOrientation() {
        return new Quaternionf(entityData.get(ORIENTATION));
    }

    /** Orientation for rendering, smoothly between the last two ticks. */
    /**
     * The orientation to draw with this frame (client). Updates come from the server every tick,
     * but its ticks and the client's frames don't line up (some frames see none, some two), and
     * stepping between them made the sky surge and stall as the ship turned: thousands of times
     * worse through a telescope. Instead it turns smoothly at the spin the server reports, and
     * drifts gently onto each update. Everything drawn in one frame gets the same answer.
     */
    public Quaternionf renderOrientation(float partialTick) {
        if (!level().isClientSide) {
            return new Quaternionf(currentOrientation);
        }
        long now = System.nanoTime();
        if (shownAt != 0 && now - shownAt < 500_000L) {
            return new Quaternionf(shown);
        }
        refreshSample();
        // Turned per real second: the spin (per game second) at the speed the server really runs.
        Vector3f spin = new Vector3f(entityData.get(SPIN)).mul((float) (TICK / tickSeconds));
        double age = Math.max(0, Math.min(0.15, (now - sampleAt) / 1e9));
        // In orbit: smooth which way it points in fixed space, then turn that into the cell's
        // axes as they are right now (worked out continuously, not stepped).
        Quaternionf attitude = entityData.get(ATTITUDE);
        double[] axes = attitude.lengthSquared() > 0.5f && materialized() ? clientAxes.apply(this) : null;
        if (axes != null) {
            Quaternionf fixedTarget = new Quaternionf(attitude).mul(turnBy(spin, age));
            if (fixedAt == 0 || Math.abs(fixedShown.dot(fixedTarget)) < 0.99f) {
                fixedShown.set(fixedTarget);
            } else {
                double dt = Math.min(0.1, (now - fixedAt) / 1e9);
                fixedShown.mul(turnBy(spin, dt)).normalize();
                fixedShown.slerp(fixedTarget, (float) (1 - Math.exp(-dt / 0.2))).normalize();
            }
            fixedAt = now;
            Quaterniond cell = cellTurn(axes);
            shown.set(new Quaternionf((float) cell.x, (float) cell.y, (float) cell.z, (float) cell.w).mul(fixedShown).normalize());
            shownAt = now;
            return new Quaternionf(shown);
        }
        fixedAt = 0;
        Quaternionf target = new Quaternionf(currentOrientation).mul(turnBy(spin, age));
        if (shownAt == 0 || Math.abs(shown.dot(target)) < 0.99f) {
            shown.set(target); // first sight, or something big happened: straight there
        } else {
            double dt = Math.min(0.1, (now - shownAt) / 1e9);
            shown.mul(turnBy(spin, dt)).normalize();
            shown.slerp(target, (float) (1 - Math.exp(-dt / 0.2))).normalize();
        }
        shownAt = now;
        return new Quaternionf(shown);
    }

    /**
     * Client: takes in the latest orientation from the server, noting which tick it belongs to.
     * Updates are applied as packets come in, any frame, so this is checked every frame too.
     */
    private void refreshSample() {
        Quaternionf latest = entityData.get(ORIENTATION);
        int stamp = entityData.get(STAMP);
        if (!latest.equals(currentOrientation) || sampleAt == 0 || stamp != sampleStamp) {
            currentOrientation.set(latest);
            // When this update really belongs: its server tick, mapped onto our clock by the
            // quickest any update has come through (late ones don't count as fresh).
            long now = System.nanoTime();
            if (stamp < sampleStamp || firstStampAt == 0) {
                firstStamp = stamp;
                firstStampAt = now;
                stampOffset = Double.NaN;
            }
            // How long the server's ticks really take (it may run a little slow).
            if (stamp - firstStamp > 40) {
                tickSeconds = Math.max(0.04, Math.min(0.2, (now - firstStampAt) / 1e9 / (stamp - firstStamp)));
            }
            double seen = now / 1e9 - stamp * tickSeconds;
            if (Double.isNaN(stampOffset) || seen < stampOffset) {
                stampOffset = seen;
            } else {
                stampOffset += (seen - stampOffset) * 0.02;
            }
            sampleStamp = stamp;
            sampleAt = (long) ((stamp * tickSeconds + stampOffset) * 1e9);
        }
    }

    /** The turn made in {@code seconds} at {@code spin} (about the ship's own axes). */
    private static Quaternionf turnBy(Vector3f spin, double seconds) {
        float rate = spin.length();
        if (rate < 1e-7f) {
            return new Quaternionf();
        }
        return new Quaternionf().rotationAxis((float) (rate * seconds), spin.x / rate, spin.y / rate, spin.z / rate);
    }

    /** The turn taking fixed-space directions into a cell's axes (given as rows: east, up, south). */
    static Quaterniond cellTurn(double[] rows) {
        org.joml.Matrix3d m = new org.joml.Matrix3d(
                rows[0], rows[3], rows[6],
                rows[1], rows[4], rows[7],
                rows[2], rows[5], rows[8]);
        return new Quaterniond().setFromNormalized(m);
    }

    private double stampOffset = Double.NaN;
    private int firstStamp;
    private long firstStampAt;
    /** Seconds per server tick, as measured (20 a second when it keeps up). */
    private double tickSeconds = TICK;
    private int sampleStamp;
    private final Quaternionf fixedShown = new Quaternionf();
    private long fixedAt;
    private final Quaternionf shown = new Quaternionf();
    private long shownAt;
    private long sampleAt;

    public OrbitalFlight orbital() {
        return orbital;
    }

    /** True while flying high over a body's map from the space dimension. */
    public boolean inMapMode() {
        return mapPosition != null;
    }

    /** Position over the body's map: the real position, or the map position in map mode. */
    public double mapX() {
        return mapPosition != null ? mapPosition.x : getX();
    }

    public double mapY() {
        return mapPosition != null ? mapPosition.y : getY();
    }

    public double mapZ() {
        return mapPosition != null ? mapPosition.z : getZ();
    }

    int mapCell() {
        return mapCell;
    }

    /** The body this vessel is flying over or orbiting, or null. */
    public CelestialBody flightBody() {
        if (orbital != null) {
            return orbital.body();
        }
        if (mapBody != null) {
            if (!level().isClientSide) {
                CelestialBody current = ServerUniverse.system().body(mapBody.id());
                if (current != null) {
                    mapBody = current; // the solar system is rebuilt when the day length changes
                }
            }
            return mapBody;
        }
        return level().isClientSide ? null : ServerUniverse.bodyOf(level());
    }

    /** Going up into map mode (the dimension change follows). */
    void enterMapMode(CelestialBody body, Vector3d position, int cell) {
        this.mapBody = body;
        this.mapPosition = new Vector3d(position);
        this.mapCell = cell;
        this.orbital = null;
    }

    /** Leaving map mode for the surface below (the dimension change follows). */
    void leaveMapMode() {
        this.mapBody = null;
        this.mapPosition = null;
    }

    /** Velocity over the ground (east, up, south), for leaving the atmosphere. */
    public DVec3 surfaceVelocity() {
        return new DVec3(velocity.x, velocity.y, velocity.z);
    }

    void enterSpace(OrbitalFlight flight) {
        this.orbital = flight;
        this.mapPosition = null;
        this.mapBody = null;
        velocity.zero();
    }

    /**
     * Coming down out of orbit into the air (map mode, same cell): position and velocity over the
     * map, and the turn from the cell's fixed axes to the local axes there.
     */
    void enterAtmosphere(CelestialBody body, Vector3d position, DVec3 groundVelocity, Quaterniond cellToLocal) {
        int cell = orbital != null ? orbital.cell() : mapCell;
        this.orbital = null;
        enterMapMode(body, position, cell);
        com.thepearlking.stevespace.universe.TimeWarp.stop(((ServerLevel) level()).getServer());
        velocity.set(groundVelocity.x(), groundVelocity.y(), groundVelocity.z());
        Quaterniond turned = new Quaterniond(cellToLocal).mul(orientation);
        orientation.set(turned).normalize();
        setGrounded(false);
    }

    // ---------------------------------------------------------------------------------------
    // Client: smooth motion between the server's updates
    // ---------------------------------------------------------------------------------------

    /** The last position the server sent, and how many ticks ago (client side). */
    private Vec3 serverPosition;
    private int serverAge;

    /**
     * The server's updates don't land on every client tick evenly; snapping to each one, a rocket
     * doing several blocks a tick jumps about (worst right after launch). Instead the client
     * carries the vessel along at its own velocity and eases toward where the server says it is.
     */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        if (!level().isClientSide) {
            super.lerpTo(x, y, z, yRot, xRot, steps);
            return;
        }
        Vec3 target = new Vec3(x, y, z);
        if (serverPosition == null || isGrounded() || target.distanceToSqr(position()) > 64 * 64) {
            setPos(x, y, z); // first sight, resting, or a jump (dimension change): straight there
        }
        serverPosition = target;
        serverAge = 0;
    }

    private void glide() {
        if (serverPosition == null || isGrounded() || com.thepearlking.stevespace.world.SpaceDimension.isSpace(level())) {
            return; // in space it stays in its cell; only its flight moves
        }
        serverAge++;
        Vector3f v = velocity();
        double dt = TICK;
        Vec3 predicted = position().add(v.x * dt, v.y * dt, v.z * dt);
        Vec3 expected = serverPosition.add(v.x * dt * serverAge, v.y * dt * serverAge, v.z * dt * serverAge);
        Vec3 next = predicted.add(expected.subtract(predicted).scale(0.35));
        setPos(next.x, next.y, next.z);
    }

    /** Lets the crew ride along through a dimension change. */
    void beginTransfer() {
        allowDismount = true;
    }

    public void setControls(VesselControls controls) {
        this.pilotControls = controls;
    }

    public void startAutopilot(Autopilot.Program program) {
        startAutopilot(program, "", false);
    }

    public void startAutopilot(Autopilot.Program program, String destination, boolean land) {
        startAutopilot(program, destination, land, Double.NaN, Double.NaN);
    }

    public void startAutopilot(Autopilot.Program program, String destination, boolean land, double latitude, double longitude) {
        if (program == Autopilot.Program.OFF) {
            autopilot.stop();
        } else if (program == Autopilot.Program.TRAVEL) {
            autopilot.startTravel(this, destination, land, latitude, longitude);
        } else {
            autopilot.start(program, this);
        }
    }

    /** Docks with another ship: see {@link Autopilot#startDock}. */
    public void startDock(java.util.UUID id, int cell, BlockPos mine, BlockPos theirs) {
        autopilot.startDock(this, id, cell, mine, theirs);
    }

    public String autopilotDebug() {
        return autopilot.debug(this);
    }

    /** True while an autopilot program is running (server side). */
    public boolean autopilotEngaged() {
        return autopilot.active();
    }

    /** The autopilot's status key for the HUD, or an empty string when it's off. */
    public String autopilotStatus() {
        String s = entityData.get(AUTOPILOT);
        int bar = s.indexOf('|');
        return bar < 0 ? s : s.substring(0, bar);
    }

    /** Where the autopilot is heading and when it gets there, as plain text (may be empty). */
    public String autopilotDetail() {
        String s = entityData.get(AUTOPILOT);
        int bar = s.indexOf('|');
        return bar < 0 ? "" : s.substring(bar + 1);
    }

    /** Acceleration with every engine at full throttle, at the current mass (blocks/s²). */
    public double maxAcceleration() {
        return stats.thrustVector().length() * SpaceConfig.THRUST_SCALE.get() / (stats.dryMass() + propellant);
    }

    public void requestDismount(Player player) {
        java.util.UUID id = player.getUUID();
        if (crew.isWalking(id)) {
            sitDown(player);
            return;
        }
        // In orbit with nothing running, getting up parks the ship: it becomes blocks you can walk around in.
        if (orbital != null && orbital.anchor() == null && !autopilot.active()) {
            park(player);
            return;
        }
        // In flight (or in orbit with the autopilot busy): get up and walk about the cabin.
        if (!isGrounded()) {
            standUp(player);
            return;
        }
        allowDismount = true;
        player.stopRiding();
        allowDismount = false;
        crew.remove(id);
        crewDirty = true;
    }

    /** Out of the seat and onto your feet beside it, inside the flying ship. */
    private void standUp(Player player) {
        int seat = crew.seatOf(player.getUUID());
        BlockPos at = seat >= 0 && seat < stats.seats().size() ? stats.seats().get(seat).pos() : BlockPos.ZERO;
        BlockPos spot = null;
        for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            BlockPos feet = at.relative(d);
            if (open(feet) && open(feet.above())) {
                spot = feet;
                break;
            }
        }
        if (spot == null) {
            spot = open(at.above()) && open(at.above(2)) ? at.above() : at;
        }
        crew.walk(player.getUUID(), new Vector3d(spot.getX() + 0.5, spot.getY() + 0.01, spot.getZ() + 0.5));
        crewDirty = true;
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("stevespace.vessel.walking",
                net.minecraft.network.chat.Component.keybind("key.stevespace.leave_seat")), true);
    }

    /** Back into the nearest free seat (anywhere in the ship). */
    private void sitDown(Player player) {
        Vector3d at = crew.walkPosition(player.getUUID());
        int best = -1;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < stats.seats().size(); i++) {
            BlockPos p = stats.seats().get(i).pos();
            double d = at.distanceSquared(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
            if (crew.seatFree(i) && d < bestDistance) {
                bestDistance = d;
                best = i;
            }
        }
        if (best < 0) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("stevespace.vessel.no_seat_near"), true);
            return;
        }
        crew.sit(player.getUUID(), best);
        crewDirty = true;
        player.displayClientMessage(net.minecraft.network.chat.Component.empty(), true);
    }

    /** No block in the ship's way at this spot (ship block coordinates). */
    private boolean open(BlockPos pos) {
        BlockState state = structure.blocks().get(pos);
        return state == null || state.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, pos).isEmpty();
    }

    /** A walking crew member's client says where they are now (ship block coordinates). */
    public void setWalkPosition(Player player, double x, double y, double z) {
        if (!crew.isWalking(player.getUUID()) || !Double.isFinite(x + y + z)) {
            return;
        }
        // Kept within the ship's own bounds (a little beyond, for open decks).
        x = Math.max(boundsMin.getX() - 1.5, Math.min(boundsMax.getX() + 2.5, x));
        y = Math.max(boundsMin.getY() - 1.5, Math.min(boundsMax.getY() + 2.5, y));
        z = Math.max(boundsMin.getZ() - 1.5, Math.min(boundsMax.getZ() + 2.5, z));
        crew.walk(player.getUUID(), new Vector3d(x, y, z));
        crewDirty = true;
    }

    /** Climbing back aboard (after a spacewalk, or into a free seat of a parked vessel). */
    @Override
    public net.minecraft.world.InteractionResult interact(Player player, net.minecraft.world.InteractionHand hand) {
        // Walking about inside: right-clicking the ship (its seat) sits back down.
        if (player.getVehicle() == this && crew.isWalking(player.getUUID())) {
            if (!level().isClientSide) {
                sitDown(player);
            }
            return net.minecraft.world.InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (player.isSecondaryUseActive() || player.getVehicle() == this || !canAddPassenger(player)) {
            return net.minecraft.world.InteractionResult.PASS;
        }
        if (!level().isClientSide) {
            player.startRiding(this);
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(level().isClientSide);
    }

    /** Whether a passenger may get out right now (only on request, never by accident). */
    public boolean allowsDismount() {
        return allowDismount || isRemoved();
    }

    // ---------------------------------------------------------------------------------------
    // Ticking
    // ---------------------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (structure == null) {
            return;
        }
        if (!level().isClientSide) {
            lastTickedOn = ((ServerLevel) level()).getServer().getTickCount();
        }
        if (level().isClientSide) {
            refreshSample();
            glide();
            return;
        }
        if (materialized()) {
            shipBlocksTick((ServerLevel) level());
        } else {
            tidyCrew();
            if (readyToMaterialize()) {
                materialize();
            }
        }
        Player pilot = pilot();
        // Anyone aboard, seated or walking about: enough for the autopilot to keep flying.
        Player aboard = pilot != null ? pilot : commander();
        // The throttle is a lever: it stays where the pilot left it when they get up (no steering
        // though). With nobody aboard at all, the engines stop.
        controls = pilot != null ? pilotControls
                : aboard != null ? new VesselControls(pilotControls.throttle(), 0, 0, 0) : VesselControls.IDLE;
        // Nothing stops the autopilot by accident (boarding, standing up, crossing into space):
        // only the pilot taking over (a second touch of the controls) or Cancel in its menu.
        // A player's ship left in orbit with nobody aboard parks itself, so it can be walked into.
        boolean nobodyAboard = materialized() ? aboard == null && !autopilot.active() : getPassengers().isEmpty();
        if (nobodyAboard && orbital != null && orbital.anchor() == null && hasCustomName() && !Rendezvous.busy(this)) {
            if (++uncrewedTicks > 100) {
                park(null);
                return;
            }
        } else {
            uncrewedTicks = 0;
        }
        steerTarget = null;
        steerAttitude = null;
        rcsWanted = null;
        // With nobody aboard (or the crew still catching up after a dimension change) it just waits.
        if (autopilot.active() && aboard != null) {
            controls = VesselControls.IDLE; // the autopilot has the controls; the pilot's are ignored
            {
                Autopilot.Command command = autopilot.update(this);
                if (autopilot.failed()) {
                    aboard.displayClientMessage(net.minecraft.network.chat.Component.translatable(autopilot.failureKey()), true);
                    autopilot.stop();
                }
                if (command != null) {
                    steerTarget = command.direction();
                    steerAttitude = command.attitude();
                    rcsWanted = command.translate();
                    double aligned = alignment(command.direction());
                    // Only fire once roughly pointing the right way.
                    double throttle = aligned > 0.97 ? command.throttle() : aligned > 0.9 ? command.throttle() * (aligned - 0.9) / 0.07 : 0;
                    controls = new VesselControls(throttle, 0, 0, 0);
                }
            }
        }
        // Whenever the autopilot lets go (finished, failed, cancelled), the engines go quiet: the
        // throttle lever is set to zero rather than left wherever it was.
        if (autopilotWasActive && !autopilot.active()) {
            pilotControls = VesselControls.IDLE;
            controls = VesselControls.IDLE;
        }
        autopilotWasActive = autopilot.active();
        entityData.set(AUTOPILOT, autopilot.active() ? autopilot.statusKey() + "|" + autopilot.detail(this) : "");
        if (steerTarget == null && !isGrounded()) {
            steerTarget = sasDirection(controls);
        }
        felt.zero();
        fly(aboard);
        if (!isRemoved()) {
            updateCabinPull();
            if (crewDirty) {
                entityData.set(CREW, crew.save());
                crewDirty = false;
            }
        }
        syncState();
    }

    /**
     * The pilot planned (or changed) a maneuver node: SAS's maneuver mode points along it, and
     * with {@code execute} the autopilot flies it.
     */
    public void planNode(double time, double prograde, double normal, double radial, boolean execute) {
        CelestialBody body = flightBody();
        if (orbital == null || body == null) {
            sasManeuver = null;
            return;
        }
        double mu = body.mu() * SpaceConfig.GRAVITY_SCALE.get();
        var at = com.thepearlking.stevespace.universe.Kepler.elementsFromState(orbital.state(), mu, orbital.time()).stateAt(time, mu);
        DVec3 pro = at.velocity().normalize();
        DVec3 nor = at.position().cross(at.velocity()).normalize();
        DVec3 rad = nor.cross(pro).normalize();
        DVec3 dv = pro.scale(prograde).add(nor.scale(normal)).add(rad.scale(radial));
        sasManeuver = dv.length() > 1e-6 ? dv.normalize() : null;
        if (execute && sasManeuver != null) {
            pilotControls = VesselControls.IDLE;
            autopilot.startNode(this, time, prograde, normal, radial);
        }
    }

    /** Where SAS would point now (world axes), or null: for tests. */
    public DVec3 sasTarget() {
        return sasDirection(VesselControls.IDLE);
    }

    public Sas sas() {
        int i = entityData.get(SAS);
        return i >= 0 && i < Sas.values().length ? Sas.values()[i] : Sas.OFF;
    }

    public void setSas(Sas mode, DVec3 maneuver) {
        entityData.set(SAS, mode.ordinal());
        sasHold = null;
        sasManeuver = maneuver;
    }

    /** Where SAS wants the engines pointed this tick (world axes), or null. Turning by hand lets go of "hold" until you stop. */
    private DVec3 sasDirection(VesselControls input) {
        Sas mode = sas();
        CelestialBody body = flightBody();
        if (mode == Sas.OFF || body == null) {
            return null;
        }
        boolean turning = input.pitch() != 0 || input.roll() != 0 || input.yaw() != 0;
        Autopilot.Trajectory now = Autopilot.Trajectory.of(this, body, body.mu() * SpaceConfig.GRAVITY_SCALE.get());
        if (mode == Sas.HOLD) {
            if (turning) {
                sasHold = null;
                return null;
            }
            if (sasHold == null) {
                Vector3d t = thrustDirection();
                DVec3 world = new DVec3(t.x, t.y, t.z);
                sasHold = orbital != null ? orbital.toInertial(world.x(), world.y(), world.z()) : world;
            }
            return orbital != null ? now.toWorld().apply(sasHold) : sasHold;
        }
        if (turning) {
            return null; // the pilot is steering
        }
        com.thepearlking.stevespace.universe.StateVector state = now.state();
        DVec3 pro = state.velocity().normalize();
        DVec3 nor = state.position().cross(state.velocity()).normalize();
        DVec3 rad = nor.cross(pro).normalize();
        DVec3 inertial = switch (mode) {
            case PROGRADE -> pro;
            case RETROGRADE -> pro.scale(-1);
            case NORMAL -> nor;
            case ANTINORMAL -> nor.scale(-1);
            case RADIAL_OUT -> rad;
            case RADIAL_IN -> rad.scale(-1);
            case MANEUVER -> sasManeuver;
            default -> null;
        };
        return inertial == null ? null : now.toWorld().apply(inertial);
    }

    /** What the crew feels, in the ship's axes and in g: pressed toward the engines while they burn, floating while coasting. */
    private void updateCabinPull() {
        Vector3d inShip = orientation.transformInverse(new Vector3d(felt)).div(-9.81);
        if (gravityGenerator) {
            inShip.y -= 1; // the generator's own pull toward the floor
        }
        double g = inShip.length();
        if (g > 3) {
            inShip.mul(3 / g);
        }
        entityData.set(CABIN_PULL, new Vector3f((float) inShip.x, (float) inShip.y, (float) inShip.z));
    }

    /** The ship's blocks span these (ship block coordinates). */
    public BlockPos boundsMin() {
        return boundsMin;
    }

    public BlockPos boundsMax() {
        return boundsMax;
    }

    /** The pull felt in the cabin (ship axes, in g). */
    public Vector3f cabinPull() {
        return entityData.get(CABIN_PULL);
    }

    /** Drops records of crew who have been gone a while (left, or never came back after a transfer). */
    private void tidyCrew() {
        java.util.Set<java.util.UUID> present = new java.util.HashSet<>();
        for (Entity p : getPassengers()) {
            present.add(p.getUUID());
        }
        for (java.util.UUID id : crew.everyone()) {
            if (present.contains(id)) {
                crewAbsent.remove(id);
            } else if (crewAbsent.merge(id, 1, Integer::sum) > 200) {
                crew.remove(id);
                crewAbsent.remove(id);
                crewDirty = true;
            }
        }
    }

    /** The crew record (on the client, as last synced). */
    public CabinCrew crew() {
        if (!level().isClientSide) {
            return crew;
        }
        CompoundTag tag = entityData.get(CREW);
        if (tag != clientCrewTag) {
            clientCrewTag = tag;
            clientCrew = CabinCrew.load(tag);
        }
        return clientCrew;
    }

    /** Whether this passenger is up and walking about rather than in a seat. */
    public boolean isWalking(Entity passenger) {
        return crew().isWalking(passenger.getUUID());
    }

    /** Anyone aboard: the pilot if seated, otherwise the first player aboard. */
    public Player commander() {
        Player pilot = pilot();
        if (pilot != null) {
            return pilot;
        }
        if (materialized()) {
            List<Player> aboard = playersAboard(2);
            return aboard.isEmpty() ? null : aboard.get(0);
        }
        for (Entity passenger : getPassengers()) {
            if (passenger instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    /** The player in the pilot's seat (the first one), or null if nobody is sitting there. */
    public Player pilot() {
        if (materialized()) {
            // Sitting in the pilot seat block.
            if (stats == null || stats.seats().isEmpty()) {
                return null;
            }
            BlockPos seat = blockOrigin().offset(stats.seats().get(0).pos());
            for (Player player : level().players()) {
                if (player.getVehicle() instanceof SeatEntity s && s.blockPosition().equals(seat)) {
                    return player;
                }
            }
            return null;
        }
        CabinCrew c = crew();
        for (Entity passenger : getPassengers()) {
            if (passenger instanceof Player player && c.seatOf(player.getUUID()) == 0) {
                return player;
            }
        }
        return null;
    }

    private void fly(Player pilot) {
        if (orbital != null) {
            flyInSpace(pilot);
            return;
        }
        ServerLevel level = (ServerLevel) level();
        CelestialBody body = flightBody();
        boolean infiniteFuel = pilot != null && pilot.getAbilities().instabuild && SpaceConfig.CREATIVE_INFINITE_FUEL.get();
        double throttle = spool((propellant > 0 || infiniteFuel) ? controls.throttle() : 0);

        // --- Forces ---------------------------------------------------------------------------
        double mass = stats.dryMass() + propellant;
        Vector3d thrust = orientation.transform(stats.thrustVector()).mul(throttle * SpaceConfig.THRUST_SCALE.get());
        Vector3d acceleration = new Vector3d(thrust).div(mass);
        Vector3d rcs = (propellant > 0 || infiniteFuel) && !isGrounded() ? rcsPush() : new Vector3d();
        acceleration.add(rcsAcceleration(rcs, infiniteFuel));

        double altitude = mapY() - 63;
        double gravityY;
        if (body != null) {
            double mu = body.mu() * SpaceConfig.GRAVITY_SCALE.get();
            boolean giant = com.thepearlking.stevespace.universe.GiantAir.has(body);
            double radius = body.radius() + Math.max(giant ? com.thepearlking.stevespace.universe.GiantAir.CRUSH : -60, altitude);
            PlanetGeometry geometry = new PlanetGeometry(body.radius());
            double latitude = geometry.latitude(mapZ());
            // Moving sideways around a round planet: the faster you go, the less you weigh
            // (planet rotation included, since the ground itself is already moving east).
            double spin = 2 * Math.PI / ServerUniverse.system().siderealPeriod(body);
            double ground = spin * radius * Math.cos(latitude);
            // Days here are much shorter than real ones, which would make a fast spinner (Haumea,
            // Vesta) throw things off its equator; the ground's own swing never takes more than
            // half of gravity.
            double most = Math.sqrt(0.5 * mu / (radius * radius) * radius);
            ground = Math.max(-most, Math.min(most, ground));
            double eastInertial = velocity.x + ground;
            gravityY = -mu / (radius * radius) + (eastInertial * eastInertial + velocity.z * velocity.z) / radius;
            acceleration.y += gravityY;
            if (body.hasAtmosphere()) {
                double scaleHeight = body.atmosphere().height() / 7.0;
                double density = (giant ? com.thepearlking.stevespace.universe.GiantAir.density(altitude)
                        : Math.exp(-Math.max(0, altitude) / scaleHeight)) * Math.min(3, body.atmosphere().density());
                double speed = velocity.length();
                double drag = DRAG_CONSTANT * SpaceConfig.ATMOSPHERIC_DRAG.get() * density * stats.crossSection() / mass * speed;
                acceleration.sub(new Vector3d(velocity).mul(drag));
                if (body.id().equals("mars")) {
                    // Dust storm winds shove a lander sideways near the ground.
                    double wind = com.thepearlking.stevespace.universe.Weather.marsWind(ServerUniverse.time(level().getServer()));
                    acceleration.x += wind * 0.06 * Math.exp(-Math.max(0, altitude) / 400);
                }
            }
        } else {
            gravityY = -9.81 * SpaceConfig.GRAVITY_SCALE.get();
            acceleration.y += gravityY;
        }
        // Everything but gravity is felt aboard; standing on the ground, the ground holds you up.
        felt.set(acceleration).sub(0, gravityY, 0);
        if (isGrounded()) {
            felt.set(0, -gravityY, 0);
        }

        // --- Propellant ------------------------------------------------------------------------
        if (!infiniteFuel && throttle > 0) {
            double flow = 0;
            for (VesselStats.Engine engine : stats.engines()) {
                flow += engine.thrust() * SpaceConfig.THRUST_SCALE.get()
                        / (engine.exhaustVelocity() * SpaceConfig.FUEL_EFFICIENCY.get());
            }
            propellant = Math.max(0, propellant - flow * throttle * TICK);
        }

        // --- Attitude --------------------------------------------------------------------------
        turn(pilot);

        // --- Motion and contact ----------------------------------------------------------------
        if (isGrounded()) {
            if (acceleration.y > 0.05) {
                setGrounded(false);
                restingTicks = 0;
            } else {
                velocity.zero();
                restingTicks = throttle > 0 ? 0 : restingTicks + 1;
                if (restingTicks >= SETTLE_TICKS) {
                    land();
                }
                return;
            }
        }
        if (mapPosition != null) {
            velocity.add(new Vector3d(acceleration).mul(TICK));
            moveOnMap(body);
            sendFlightToCrew();
            double height = mapPosition.y - 63;
            if (com.thepearlking.stevespace.universe.GiantAir.has(body)) {
                // Inside a giant: no ground, only deeper and deeper air, until the hull gives way.
                if (height < com.thepearlking.stevespace.universe.GiantAir.CRUSH) {
                    crash(velocity.length());
                } else if (height > VesselTransfer.spaceAltitude(body)) {
                    VesselTransfer.mapToOrbit(this);
                }
                return;
            }
            if (height < -40) {
                crash(velocity.length()); // went below where the ground can be
                return;
            }
            if (height > VesselTransfer.spaceAltitude(body)) {
                VesselTransfer.mapToOrbit(this);
            } else if (height < VesselTransfer.surfaceEntry(body)) {
                // The fall held while waiting carries on as it was once the ground is there.
                if (heldFall < 0) {
                    velocity.y = heldFall;
                }
                VesselTransfer.mapToSurface(this); // waits (stays in map mode) until the ground is loaded
                // Still waiting for the ground below to be made (a world's first visit can take a
                // while): keep falling while there's plenty of room above where the ground can be,
                // and only pause (remembering how fast it was coming down) when it gets close.
                double room = mapPosition == null ? 0
                        : mapPosition.y - VesselTransfer.groundBelow(body, mapPosition.x, mapPosition.z);
                if (!isRemoved() && mapPosition != null && velocity.y < 0 && room < 24 - velocity.y * TICK * 4) {
                    heldFall = velocity.y;
                    velocity.y = 0;
                } else {
                    heldFall = 0;
                }
            } else if (velocity.y < 0) {
                VesselTransfer.prepareSurface(this, body, mapPosition, velocity);
            }
            return;
        }
        keepChunksLoaded(level, body);
        if (!nextChunkReady(level, body, acceleration)) {
            if (++waitingTicks < 40) {
                return; // the world ahead is still loading: hold still for a tick rather than fly into nothing
            }
            // Still not there after two seconds: load it now (a short hitch beats hanging in the air).
            double latitude = body == null ? 0 : new PlanetGeometry(body.radius()).latitude(getZ());
            double eastScale = 1 / Math.max(0.05, Math.cos(latitude));
            int cx = net.minecraft.util.Mth.floor(getX() + velocity.x * eastScale * TICK) >> 4;
            int cz = net.minecraft.util.Mth.floor(getZ() + velocity.z * TICK) >> 4;
            com.thepearlking.stevespace.StevesSpace.LOGGER.warn("Vessel {} waited {} ticks for chunk {},{} (generated={} entities={}); loading it now",
                    getId(), waitingTicks, cx, cz, level.hasChunk(cx, cz), level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(cx, cz)));
            level.getChunk(cx, cz);
        }
        waitingTicks = 0;
        velocity.add(new Vector3d(acceleration).mul(TICK));
        move(velocity, body);
        if (body != null && !isRemoved() && getY() - 63 > VesselTransfer.surfaceCeiling(body)) {
            VesselTransfer.surfaceToMap(this, body);
        }
    }

    /** Map mode: moves over the map, wrapping around the date line and over the poles. */
    private void moveOnMap(CelestialBody body) {
        PlanetGeometry geometry = new PlanetGeometry(body.radius());
        double eastScale = 1 / Math.max(0.05, Math.cos(geometry.latitude(mapPosition.z)));
        mapPosition.add(velocity.x * eastScale * TICK, velocity.y * TICK, velocity.z * TICK);
        PlanetGeometry.Wrapped wrapped = geometry.wrap(mapPosition.x, mapPosition.z);
        if (wrapped.crossedPole()) {
            // Over the pole you are heading the other way on the map: east and south flip.
            velocity.x = -velocity.x;
            velocity.z = -velocity.z;
            orientation.premul(new Quaterniond().rotateY(Math.PI)).normalize();
        }
        mapPosition.x = wrapped.x();
        mapPosition.z = wrapped.z();
    }

    /** Above the atmosphere: an orbit around the body (or whatever body it has drifted to). */
    private void flyInSpace(Player pilot) {
        ServerLevel level = (ServerLevel) level();
        var server = level.getServer();
        var clock = com.thepearlking.stevespace.universe.UniverseClock.get(server);
        double gravityScale = SpaceConfig.GRAVITY_SCALE.get();
        boolean infiniteFuel = pilot != null && pilot.getAbilities().instabuild && SpaceConfig.CREATIVE_INFINITE_FUEL.get();
        // Burning only happens at normal time speed; time warp is coasting only.
        if (clock.warp() > 1 && (controls.throttle() > 0 || controls.translating())) {
            com.thepearlking.stevespace.universe.TimeWarp.stop(server); // firing the engines ends warp
        }
        boolean warping = clock.warp() > 1;
        if (warping) {
            engineLevel = 0; // time warp is coasting only: the engines are off, not winding down
        }
        double throttle = warping ? 0 : spool((propellant > 0 || infiniteFuel) ? controls.throttle() : 0);
        DVec3 thrust = null;
        if (throttle > 0) {
            double mass = stats.dryMass() + propellant;
            Vector3d local = orientation.transform(stats.thrustVector()).mul(throttle * SpaceConfig.THRUST_SCALE.get() / mass);
            felt.set(local);
            thrust = orbital.toInertial(local.x, local.y, local.z);
            if (!infiniteFuel) {
                double flow = 0;
                for (VesselStats.Engine engine : stats.engines()) {
                    flow += engine.thrust() * SpaceConfig.THRUST_SCALE.get()
                            / (engine.exhaustVelocity() * SpaceConfig.FUEL_EFFICIENCY.get());
                }
                propellant = Math.max(0, propellant - flow * throttle * TICK);
            }
        }
        entityData.set(THROTTLE, (float) throttle);
        Vector3d rcs = !warping && (propellant > 0 || infiniteFuel) ? rcsPush() : new Vector3d();
        Vector3d nudge = rcsAcceleration(rcs, infiniteFuel);
        if (nudge.lengthSquared() > 0) {
            felt.add(nudge);
            DVec3 push = orbital.toInertial(nudge.x, nudge.y, nudge.z);
            thrust = thrust == null ? push : thrust.add(push);
        }
        turn(pilot);
        org.joml.Matrix3d turn = orbital.advance(ServerUniverse.system(), clock.time(), thrust, gravityScale,
                orbital.body().radius() + VesselTransfer.entryAltitude(orbital.body()));
        // The cell turns with the horizon as the vessel goes round; keep the vessel still in space.
        // A ship that is blocks with nobody flying it (a station) turns with its cell instead, as
        // a parked ship does: its blocks stand upright in it, and that's where it really points.
        if (!(materialized() && pilot() == null && !autopilot.active() && controls.equals(VesselControls.IDLE))) {
            orientation.premul(new Quaterniond().setFromNormalized(turn)).normalize();
        }
        sendFlightToCrew();

        CelestialBody body = orbital.body();
        if (orbital.altitude() < VesselTransfer.entryAltitude(body)) {
            boolean giant = com.thepearlking.stevespace.universe.GiantAir.has(body);
            if (giant ? orbital.altitude() < com.thepearlking.stevespace.universe.GiantAir.CRUSH : orbital.altitude() < 0) {
                crash(orbital.state().velocity().length());
            } else {
                VesselTransfer.orbitToMap(this, orbital); // stays in orbit mode if there's no ground here
            }
        }
    }

    /** Tells the crew where in the solar system they are: the orbit, or the place over the map. */
    private void sendFlightToCrew() {
        com.thepearlking.stevespace.network.VesselOrbitS2C message;
        if (orbital != null) {
            var state = orbital.state();
            double[] values = {state.position().x(), state.position().y(), state.position().z(),
                    state.velocity().x(), state.velocity().y(), state.velocity().z()};
            double[] axes = axisRows();
            message = new com.thepearlking.stevespace.network.VesselOrbitS2C(getId(), orbital.body().id(), orbital.time(),
                    values, axes, controls.throttle() <= 0, false);
        } else if (mapPosition != null) {
            double[] values = {mapPosition.x, mapPosition.y, mapPosition.z, velocity.x, velocity.y, velocity.z};
            message = new com.thepearlking.stevespace.network.VesselOrbitS2C(getId(), mapBody.id(),
                    ServerUniverse.time(((ServerLevel) level()).getServer()), values, new double[9], false, true);
        } else {
            return;
        }
        for (Entity passenger : getPassengers()) {
            if (passenger instanceof net.minecraft.server.level.ServerPlayer player) {
                com.thepearlking.stevespace.network.SpaceNetwork.sendTo(player, message);
            }
        }
        if (materialized()) {
            for (Player player : playersAboard(48)) {
                // (Not to someone in another ship close by: theirs tells them where they are.)
                if (player instanceof net.minecraft.server.level.ServerPlayer sp
                        && !(sp.getVehicle() instanceof VesselEntity other && other != this)) {
                    com.thepearlking.stevespace.network.SpaceNetwork.sendTo(sp, message);
                }
            }
        }
    }

    /** Current thrust direction in world axes (unit). */
    private Vector3d thrustDirection() {
        Vector3d t = new Vector3d(stats.thrustVector());
        if (t.lengthSquared() < 1e-12) {
            t.set(0, 1, 0);
        }
        return orientation.transform(t.normalize());
    }

    /** Cosine of the angle between the thrust direction and {@code direction}. */
    public double alignment(DVec3 direction) {
        Vector3d t = thrustDirection();
        return t.x * direction.x() + t.y * direction.y() + t.z * direction.z();
    }

    /**
     * The RCS push this tick, in the vessel's own axes (each -1..1): what the autopilot wants, or
     * the pilot's RCS keys (forward is where the pilot's seat faces).
     */
    private Vector3d rcsPush() {
        if (stats.rcs().isEmpty() && rcsWanted == null) {
            return new Vector3d(); // without RCS thrusters only the docking autopilot can puff (weakly)
        }
        Vector3d local;
        if (testRcsPush != null) {
            local = new Vector3d(testRcsPush);
        } else if (rcsWanted != null) {
            local = new Quaterniond(orientation).conjugate().transform(new Vector3d(rcsWanted.x(), rcsWanted.y(), rcsWanted.z()))
                    .div(dockAcceleration());
        } else if (controls.translating()) {
            Direction facing = stats.seats().isEmpty() ? Direction.NORTH : stats.seats().get(0).facing();
            Vector3d forward = new Vector3d(facing.getStepX(), 0, facing.getStepZ());
            Vector3d up = new Vector3d(0, 1, 0);
            Vector3d right = new Vector3d(forward).cross(up);
            local = forward.mul(controls.forward()).add(right.mul(controls.right())).add(up.mul(controls.up()));
        } else {
            return new Vector3d();
        }
        if (local.length() > 1) {
            local.normalize();
        }
        return local;
    }

    /** The acceleration (world axes, blocks/s²) from an RCS push, using up propellant for it. */
    private Vector3d rcsAcceleration(Vector3d push, boolean infiniteFuel) {
        entityData.set(RCS, new Vector3f((float) push.x, (float) push.y, (float) push.z));
        if (push.lengthSquared() < 1e-8) {
            return new Vector3d();
        }
        double force = dockAcceleration() * (stats.dryMass() + propellant);
        if (!infiniteFuel) {
            propellant = Math.max(0, propellant - push.length() * force / (VesselParts.RCS_EXHAUST * SpaceConfig.FUEL_EFFICIENCY.get()) * TICK);
        }
        return orientation.transform(new Vector3d(push)).mul(force / (stats.dryMass() + propellant));
    }

    /** Client tests: every RCS-fitted ship pushes this way (vessel axes), or null. */
    public static Vector3d testRcsPush;

    /** How hard the RCS thrusters push (vessel axes, each -1..1), for the puffs on screen. */
    public Vector3f rcsPushSynced() {
        return entityData.get(RCS);
    }

    /** Greatest acceleration the RCS thrusters can give (blocks/s²), 0 without any. */
    public double rcsAcceleration() {
        return stats.rcsThrust() * SpaceConfig.THRUST_SCALE.get() / (stats.dryMass() + propellant);
    }

    /** Puffing propellant out through the hull's vents: all a ship without RCS thrusters has for docking. */
    private static final double VENT_ACCELERATION = 0.06;

    /** The push the docking autopilot has for small moves (blocks/s²): RCS thrusters, or weak vents without. */
    public double dockAcceleration() {
        double rcs = rcsAcceleration();
        return rcs > 0 ? rcs : VENT_ACCELERATION;
    }

    /** Rotates toward the pilot's input at the configured rate; holds attitude without input. */
    private void turn(Player pilot) {
        double rate = Math.toRadians(SpaceConfig.TURN_RATE.get());
        Vector3d target = new Vector3d();
        if (steerAttitude != null && !isGrounded()) {
            // Autopilot holding a whole attitude (lining a docking port up, square to the other's):
            // turning gently, so nothing seems to swing round all at once.
            rate = Math.min(rate, Math.toRadians(8));
            Quaterniond error = new Quaterniond(orientation).conjugate().mul(steerAttitude).normalize();
            if (error.w < 0) {
                error.set(-error.x, -error.y, -error.z, -error.w); // the short way round
            }
            double angle = 2 * Math.acos(Math.min(1, error.w));
            double s = Math.sqrt(Math.max(0, 1 - error.w * error.w));
            if (s > 1e-6) {
                target.set(error.x / s, error.y / s, error.z / s).mul(Math.min(rate, angle * 2.5));
            }
        } else if (steerTarget != null && !isGrounded()) {
            // Autopilot: turn the thrust direction toward the target, slowing as it lines up.
            Vector3d current = thrustDirection();
            Vector3d wanted = new Vector3d(steerTarget.x(), steerTarget.y(), steerTarget.z());
            Vector3d axis = new Vector3d(current).cross(wanted);
            double angle = Math.atan2(axis.length(), current.dot(wanted));
            if (axis.lengthSquared() < 1e-12 && angle > 1) {
                axis = new Vector3d(current).orthogonalize(new Vector3d(1, 0, 0)); // exactly opposite
                if (axis.lengthSquared() < 1e-12) {
                    axis = new Vector3d(0, 0, 1);
                }
            }
            if (axis.lengthSquared() > 1e-12) {
                axis.normalize();
                new Quaterniond(orientation).conjugate().transform(axis); // into the vessel's own frame
                target.set(axis).mul(Math.min(rate, angle * 2.5));
            }
        } else if (pilot != null && !isGrounded()) {
            // Input axes are relative to the pilot's seat: forward = where the seat faces.
            Direction facing = stats.seats().isEmpty() ? Direction.NORTH : stats.seats().get(0).facing();
            Vector3d forward = new Vector3d(facing.getStepX(), 0, facing.getStepZ());
            Vector3d up = new Vector3d(0, 1, 0);
            Vector3d right = new Vector3d(forward).cross(up);
            // W tips the nose toward where you face, A/D tilt left/right, Q/E spin.
            target.add(new Vector3d(right).mul(-controls.pitch() * rate));
            target.add(new Vector3d(forward).mul(controls.roll() * rate));
            target.add(new Vector3d(up).mul(-controls.yaw() * rate));
        }
        double response = Math.min(1, TICK * 4);
        angularVelocity.lerp(target, response);
        if (angularVelocity.lengthSquared() > 1e-10) {
            Vector3d axis = new Vector3d(angularVelocity).normalize();
            double angle = angularVelocity.length() * TICK;
            orientation.mul(new Quaterniond().fromAxisAngleRad(axis.x, axis.y, axis.z, angle)).normalize();
        }
    }

    /**
     * Fast vessels outrun the chunks loaded around their crew, and an entity in a chunk that
     * isn't ticking just stops. Load the path ahead, a few seconds of flight at a time.
     */
    private void keepChunksLoaded(ServerLevel level, CelestialBody body) {
        if (tickCount % 10 != 0 && velocity.lengthSquared() < 400) {
            return;
        }
        loadPathAhead(level, body, getX(), getZ(), velocity.x, velocity.z);
    }

    @Override
    public void setLevelCallback(net.minecraft.world.level.entity.EntityInLevelCallback callback) {
        super.setLevelCallback(callback);
        // Called when the vessel enters a level's entity storage, even where chunks aren't loaded yet.
        if (!level().isClientSide && callback != net.minecraft.world.level.entity.EntityInLevelCallback.NULL) {
            SERVER_VESSELS.add(this);
        }
    }

    /** Puts a vessel straight into orbit in a cell of its own (for tests, and stations placed by commands). */
    /**
     * Lifts a parked ship's blocks back into a flying vessel, on the orbit its cell has been
     * following, in the same place among the stars.
     */
    public static VesselEntity unpark(ServerLevel space, BlockPos origin, VesselStructure structure,
                                      com.thepearlking.stevespace.world.ParkedShips.Parked parked, Player pilot) {
        var server = space.getServer();
        var system = ServerUniverse.system();
        CelestialBody body = system.body(parked.body());
        double now = ServerUniverse.time(server);
        double mu = body.mu() * SpaceConfig.GRAVITY_SCALE.get();
        var state = com.thepearlking.stevespace.universe.Kepler.elementsFromState(parked.state(), mu, parked.time()).stateAt(now, mu);
        VesselEntity vessel = new VesselEntity(ModEntities.VESSEL.get(), space);
        if (parked.id() != null && space.getEntity(parked.id()) == null) {
            vessel.setUUID(parked.id()); // the same ship as before it parked (docking targets follow it)
        }
        vessel.owner = parked.owner();
        vessel.setStructure(structure);
        vessel.setCustomName(shipName(parked.name(), space));
        vessel.propellant = totalPropellant(structure);
        vessel.orbital = new OrbitalFlight(system, body, state, now, parked.cell());
        Vector3d centre = vessel.stats.centerOfMass();
        vessel.setPos(origin.getX() + centre.x, origin.getY() + centre.y, origin.getZ() + centre.z);
        vessel.setGrounded(false);
        // The blocks stay where they are: the ship just starts flying around them.
        vessel.entityData.set(MATERIALIZED, true);
        vessel.entityData.set(BLOCK_ORIGIN, origin);
        vessel.syncState();
        com.thepearlking.stevespace.world.ParkedShips.get(server).remove(parked.cell());
        space.addFreshEntity(vessel);
        return vessel;
    }

    /** A parked ship's saved name (JSON text, or plain text from older saves). */
    private static net.minecraft.network.chat.Component shipName(String saved, ServerLevel level) {
        if (saved.startsWith("{") || saved.startsWith("\"")) {
            try {
                var name = net.minecraft.network.chat.Component.Serializer.fromJson(saved, level.registryAccess());
                if (name != null) {
                    return name;
                }
            } catch (RuntimeException ignored) {
                // fall through to plain text
            }
        }
        return net.minecraft.network.chat.Component.literal(saved);
    }

    public static VesselEntity spawnInOrbit(ServerLevel space, VesselStructure structure, CelestialBody body,
                                            com.thepearlking.stevespace.universe.StateVector state, Quaterniond orientation) {
        var server = space.getServer();
        int cell = com.thepearlking.stevespace.world.SpaceCells.get(server).allocate();
        VesselEntity vessel = new VesselEntity(ModEntities.VESSEL.get(), space);
        vessel.setStructure(structure);
        vessel.propellant = totalPropellant(structure);
        vessel.orientation.set(orientation);
        vessel.orbital = new OrbitalFlight(ServerUniverse.system(), body, state, ServerUniverse.time(server), cell);
        Vec3 centre = com.thepearlking.stevespace.world.SpaceDimension.cellCenter(cell);
        vessel.setPos(centre.x, centre.y, centre.z);
        vessel.setGrounded(false);
        vessel.syncState();
        space.getChunk((int) Math.floor(centre.x) >> 4, (int) Math.floor(centre.z) >> 4);
        space.addFreshEntity(vessel);
        return vessel;
    }

    public static List<VesselEntity> allServerVessels() {
        return List.copyOf(SERVER_VESSELS);
    }

    /** Every vessel on this server (for rendezvous and tracking). */
    static List<VesselEntity> serverVessels() {
        return List.copyOf(SERVER_VESSELS);
    }

    /** Set while turning into a parked ship: its cell stays in use, now by the blocks. */
    private boolean parking;
    private boolean autopilotWasActive;
    private int uncrewedTicks;
    /** How hard the engines are actually pushing (0..1): they take a moment to spool up and down. */
    private double engineLevel;

    /** Moves the engines toward the commanded throttle at a real engine's pace (about a second). */
    private double spool(double commanded) {
        double rate = 1.2 * TICK;
        engineLevel += Math.max(-rate * 2, Math.min(rate, commanded - engineLevel));
        if (commanded <= 0 && engineLevel < 0.02) {
            engineLevel = 0;
        }
        return engineLevel;
    }

    /** Creative pilots fly with full tanks (they never run dry), so the ship weighs what it really would. */
    public void fillTanks() {
        propellant = stats.propellantCapacity();
    }

    /**
     * Turns the ship into blocks in its own cell, still following its orbit: a place to walk
     * around in (zero g, unless it has a gravity generator), build on, and fly again from the
     * pilot seat. {@code leaving} (if any) stands up beside their seat; everyone else stays seated.
     */
    public void park(Player leaving) {
        if (materialized()) {
            parkInPlace();
            return;
        }
        autopilot.stop(); // a parked ship is blocks: whatever it was doing is over
        ServerLevel level = (ServerLevel) level();
        var server = level.getServer();
        Rotation rotation = nearestUprightRotation();
        Vector3d half = new Vector3d(0.5, 0.5, 0.5);
        Vector3d originExact = new Vector3d(getX(), getY(), getZ())
                .sub(rotatedOffset(stats.centerOfMass(), rotation))
                .add(rotatedOffset(half, rotation))
                .sub(half);
        BlockPos origin = new BlockPos((int) Math.round(originExact.x), (int) Math.round(originExact.y), (int) Math.round(originExact.z));
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos rel : structure.blocks().keySet()) {
            BlockPos p = origin.offset(rel.rotate(rotation));
            minX = Math.min(minX, p.getX());
            minY = Math.min(minY, p.getY());
            minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX());
            maxY = Math.max(maxY, p.getY());
            maxZ = Math.max(maxZ, p.getZ());
        }
        double now = ServerUniverse.time(server);
        com.thepearlking.stevespace.world.ParkedShips.get(server).put(new com.thepearlking.stevespace.world.ParkedShips.Parked(
                orbital.cell(), orbital.body().id(), orbital.state(), orbital.time(),
                net.minecraft.network.chat.Component.Serializer.toJson(getName(), level.registryAccess()),
                new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ),
                new com.thepearlking.stevespace.universe.DVec3(getX(), getY(), getZ()), getUUID(), owner));
        List<Entity> riders = new ArrayList<>(getPassengers());
        allowDismount = true;
        ejectPassengers();
        allowDismount = false;
        VesselAssembler.placeInWorld(level, origin, structure, rotation);
        com.thepearlking.stevespace.world.ShipNames.remember(level, origin, structure, rotation, getCustomName());
        distributePropellant(level, origin, rotation);
        if (leaving != null) {
            riders.remove(leaving);
        }
        // Anyone floating where the blocks just appeared is moved clear (straight up).
        net.minecraft.world.phys.AABB shipBox = new net.minecraft.world.phys.AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
        for (Player nearby : level.getEntitiesOfClass(Player.class, shipBox.inflate(1))) {
            if (riders.contains(nearby) || nearby == leaving) {
                continue;
            }
            for (int up = 0; up < 64 && !level.noCollision(nearby); up++) {
                nearby.teleportTo(nearby.getX(), nearby.getY() + 1, nearby.getZ());
            }
        }
        seatRiders(level, origin, rotation, riders);
        if (leaving != null) {
            standUpBeside(level, leaving, stats.seats().isEmpty() ? origin : origin.offset(stats.seats().get(0).pos().rotate(rotation)));
        }
        parking = true;
        discard();
        com.thepearlking.stevespace.StevesSpace.LOGGER.info("{} parked in orbit of {} (cell {}, t={})", getName().getString(),
                orbital.body().id(), orbital.cell(), String.format("%.0f", now));
    }

    /** Puts someone on their feet next to a seat: the nearest free spot beside it, else on top. */
    private static void standUpBeside(ServerLevel level, Player player, BlockPos seat) {
        BlockPos best = null;
        for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            BlockPos feet = seat.relative(d);
            if (level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                    && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) {
                best = feet;
                break;
            }
        }
        if (best == null) {
            best = seat.above();
        }
        player.teleportTo(best.getX() + 0.5, best.getY() + 0.05, best.getZ() + 0.5);
        player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
    }

    @Override
    public void remove(RemovalReason reason) {
        // A vessel destroyed or packed away in space gives its cell back (not when parking).
        if (!level().isClientSide && !parking && reason != RemovalReason.CHANGED_DIMENSION && reason != RemovalReason.UNLOADED_TO_CHUNK
                && reason != RemovalReason.UNLOADED_WITH_PLAYER) {
            var server = ((ServerLevel) level()).getServer();
            com.thepearlking.stevespace.remote.RemoteControl.craftLost(this);
            if (orbital != null && orbital.anchor() == null) {
                com.thepearlking.stevespace.world.SpaceCells.get(server).free(orbital.cell());
            } else if (mapPosition != null) {
                com.thepearlking.stevespace.world.SpaceCells.get(server).free(mapCell);
            }
        }
        super.remove(reason);
    }

    // ---------------------------------------------------------------------------------------
    // Docking
    // ---------------------------------------------------------------------------------------

    /**
     * Docks {@code other} onto this vessel: its blocks join this structure, its propellant and crew
     * come aboard, and the two orbits become one (by momentum). Returns false, changing nothing, if
     * the two aren't lined up square or their blocks would overlap.
     */
    boolean absorb(VesselEntity other, VesselStats.Port mine, VesselStats.Port theirs) {
        dematerialize(); // joined as one vessel, then back to blocks next tick
        other.dematerialize();
        // The other vessel's axes in this vessel's block axes, snapped to a right-angle turn.
        Quaterniond relative = new Quaterniond(orientation).conjugate().mul(other.orientation);
        org.joml.Matrix3d m = relative.get(new org.joml.Matrix3d());
        int[][] snap = new int[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                double v = m.get(j, i); // row i, column j
                snap[i][j] = (int) Math.round(v);
                if (Math.abs(v - snap[i][j]) > 0.3) {
                    return false; // more than ~17° off square
                }
            }
        }
        if (snap[1][1] != 1) {
            return false; // never join with one ship tipped over against the other
        }
        Direction theirFacing = turn(snap, theirs.facing());
        if (theirFacing != mine.facing().getOpposite()) {
            return false;
        }
        BlockPos anchor = mine.pos().relative(mine.facing());
        Map<BlockPos, BlockState> blocks = new java.util.HashMap<>(structure.blocks());
        Map<BlockPos, CompoundTag> entities = new java.util.HashMap<>(structure.blockEntities());
        for (Map.Entry<BlockPos, BlockState> e : other.structure.blocks().entrySet()) {
            BlockPos d = e.getKey().subtract(theirs.pos());
            BlockPos at = anchor.offset(turn(snap, d));
            if (blocks.containsKey(at)) {
                return false; // the two would overlap
            }
            blocks.put(at, turnState(snap, e.getValue()));
            CompoundTag tag = other.structure.blockEntities().get(e.getKey());
            if (tag != null) {
                entities.put(at, tag.copy());
            }
        }
        // Mass-weighted orbit of the pair (momentum is conserved).
        double myMass = stats.dryMass() + propellant;
        double theirMass = other.stats.dryMass() + other.propellant;
        double total = myMass + theirMass;
        var a = orbital.state();
        var b = other.orbital.state();
        orbital.setState(new com.thepearlking.stevespace.universe.StateVector(
                a.position().scale(myMass / total).add(b.position().scale(theirMass / total)),
                a.velocity().scale(myMass / total).add(b.velocity().scale(theirMass / total))));
        Vector3d oldCom = stats.centerOfMass();
        propellant += other.propellant;
        if (owner == null) {
            owner = other.owner;
        }
        setStructure(new VesselStructure(blocks, entities));
        Vector3d shift = orientation.transform(stats.centerOfMass().sub(oldCom));
        setPos(getX() + shift.x, getY() + shift.y, getZ() + shift.z);
        // The other crew comes aboard.
        List<Entity> crew = new ArrayList<>(other.getPassengers());
        other.allowDismount = true;
        other.ejectPassengers();
        for (Entity member : crew) {
            member.startRiding(this, true);
        }
        other.discard();
        syncState();
        com.thepearlking.stevespace.StevesSpace.LOGGER.info("Vessel {} docked with {} (both upright: {})", getId(), other.getId(), snap[1][1] == 1);
        return true;
    }

    /** Parts at the first pair of joined docking ports: the side without the pilot flies off on its own. */
    public boolean undock() {
        dematerialize();
        if (orbital == null) {
            return false;
        }
        for (VesselStats.Port port : stats.ports()) {
            BlockPos next = port.pos().relative(port.facing());
            BlockState there = structure.get(next);
            if (!(there != null && there.getBlock() instanceof com.thepearlking.stevespace.block.DockingPortBlock
                    && there.getValue(com.thepearlking.stevespace.block.DockingPortBlock.FACING) == port.facing().getOpposite())) {
                continue;
            }
            // Everything reachable from the pilot's side without crossing this joint.
            BlockPos start = stats.seats().isEmpty() ? port.pos() : stats.seats().get(0).pos();
            java.util.Set<BlockPos> kept = new java.util.HashSet<>();
            java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>(List.of(start));
            kept.add(start);
            while (!queue.isEmpty()) {
                BlockPos p = queue.poll();
                for (Direction d : Direction.values()) {
                    BlockPos n = p.relative(d);
                    boolean joint = (p.equals(port.pos()) && n.equals(next)) || (p.equals(next) && n.equals(port.pos()));
                    if (!joint && structure.get(n) != null && kept.add(n)) {
                        queue.add(n);
                    }
                }
            }
            if (kept.size() == structure.size()) {
                continue; // still joined some other way
            }
            Map<BlockPos, BlockState> keptBlocks = new java.util.HashMap<>();
            Map<BlockPos, BlockState> partBlocks = new java.util.HashMap<>();
            Map<BlockPos, CompoundTag> keptTags = new java.util.HashMap<>();
            Map<BlockPos, CompoundTag> partTags = new java.util.HashMap<>();
            for (Map.Entry<BlockPos, BlockState> e : structure.blocks().entrySet()) {
                boolean keep = kept.contains(e.getKey());
                (keep ? keptBlocks : partBlocks).put(e.getKey(), e.getValue());
                CompoundTag tag = structure.blockEntities().get(e.getKey());
                if (tag != null) {
                    (keep ? keptTags : partTags).put(e.getKey(), tag);
                }
            }
            VesselStructure keptStructure = new VesselStructure(keptBlocks, keptTags);
            VesselStructure partStructure = new VesselStructure(partBlocks, partTags);
            VesselStats keptStats = new VesselStats(keptStructure);
            VesselStats partStats = new VesselStats(partStructure);
            double capacity = keptStats.propellantCapacity() + partStats.propellantCapacity();
            double partPropellant = capacity > 0 ? propellant * partStats.propellantCapacity() / capacity : 0;
            Vector3d oldCom = stats.centerOfMass();

            VesselEntity part = new VesselEntity(ModEntities.VESSEL.get(), level());
            part.setStructure(partStructure);
            part.propellant = partPropellant;
            part.owner = owner;
            part.orientation.set(orientation);
            Vector3d partShift = orientation.transform(partStats.centerOfMass().sub(oldCom));
            part.setPos(getX() + partShift.x, getY() + partShift.y, getZ() + partShift.z);
            part.setGrounded(false);
            part.setCustomName(net.minecraft.network.chat.Component.translatable("entity.stevespace.vessel.module", getName()));

            propellant -= partPropellant;
            setStructure(keptStructure);
            Vector3d keptShift = orientation.transform(keptStats.centerOfMass().sub(oldCom));
            setPos(getX() + keptShift.x, getY() + keptShift.y, getZ() + keptShift.z);

            // A gentle push apart along the ports, sharing the momentum.
            Vector3d away = orientation.transform(new Vector3d(port.facing().getStepX(), port.facing().getStepY(), port.facing().getStepZ()));
            DVec3 push = orbital.toInertial(away.x, away.y, away.z);
            double keptMass = keptStats.dryMass() + propellant;
            double partMass = partStats.dryMass() + part.propellant;
            double speed = 0.4;
            var s = orbital.state();
            DVec3 offsetKept = orbital.toInertial(keptShift.x, keptShift.y, keptShift.z);
            DVec3 offsetPart = orbital.toInertial(partShift.x, partShift.y, partShift.z);
            orbital.setState(new com.thepearlking.stevespace.universe.StateVector(s.position().add(offsetKept),
                    s.velocity().sub(push.scale(speed * partMass / (keptMass + partMass)))));
            part.orbital = new OrbitalFlight(ServerUniverse.system(), orbital.body(),
                    new com.thepearlking.stevespace.universe.StateVector(s.position().add(offsetPart),
                            s.velocity().add(push.scale(speed * keptMass / (keptMass + partMass)))),
                    orbital.time(), orbital.cell());
            part.orbital.setCell(orbital.cell(), getUUID());
            part.dockCooldown = 200;
            dockCooldown = 200;
            part.syncState();
            level().addFreshEntity(part);
            // Anyone beyond the seats this side still has moves across.
            List<Entity> crew = new ArrayList<>(getPassengers());
            for (int i = Math.max(1, stats.seats().size()); i < crew.size(); i++) {
                allowDismount = true;
                crew.get(i).stopRiding();
                allowDismount = false;
                crew.get(i).startRiding(part, true);
            }
            syncState();
            return true;
        }
        return false;
    }

    /** A block offset turned by a right-angle rotation matrix. */
    private static BlockPos turn(int[][] m, BlockPos p) {
        return new BlockPos(m[0][0] * p.getX() + m[0][1] * p.getY() + m[0][2] * p.getZ(),
                m[1][0] * p.getX() + m[1][1] * p.getY() + m[1][2] * p.getZ(),
                m[2][0] * p.getX() + m[2][1] * p.getY() + m[2][2] * p.getZ());
    }

    private static Direction turn(int[][] m, Direction d) {
        BlockPos v = turn(m, new BlockPos(d.getStepX(), d.getStepY(), d.getStepZ()));
        return Direction.getNearest(v.getX(), v.getY(), v.getZ());
    }

    /** Turns a block's facing with the rest of the vessel (engines, seats, ports...). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState turnState(int[][] m, BlockState state) {
        for (var property : state.getProperties()) {
            if (property instanceof net.minecraft.world.level.block.state.properties.DirectionProperty facing
                    && property.getName().equals("facing")) {
                Direction turned = turn(m, state.getValue(facing));
                if (facing.getPossibleValues().contains(turned)) {
                    return state.setValue(facing, turned);
                }
            }
        }
        return state;
    }

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        if (!level().isClientSide) {
            SERVER_VESSELS.add(this);
        }
    }

    /**
     * Server tick: vessels whose chunk isn't ticking are ticked here instead. Vessels whose chunk
     * hasn't even loaded yet wait for it, keeping it requested.
     */
    public static void tickOutsideTickingChunks(net.minecraft.server.MinecraftServer server) {
        for (VesselEntity vessel : java.util.List.copyOf(SERVER_VESSELS)) {
            if (vessel.isRemoved()) {
                SERVER_VESSELS.remove(vessel);
                continue;
            }
            if (!(vessel.level() instanceof ServerLevel level) || level.getServer() != server || vessel.isPassenger()) {
                continue;
            }
            var pos = vessel.chunkPosition();
            if (!isReady(level, pos.x, pos.z)) {
                // Its chunk isn't loaded: keep asking for it and wait.
                level.getChunkSource().addRegionTicket(FLIGHT_TICKET, pos, 0, pos);
            } else if (vessel.lastTickedOn != server.getTickCount()) {
                level.tickNonPassenger(vessel);
            }
        }
    }

    /** Whether the chunk the vessel is about to move into is loaded. */
    private boolean nextChunkReady(ServerLevel level, CelestialBody body, Vector3d acceleration) {
        double latitude = body == null ? 0 : new PlanetGeometry(body.radius()).latitude(getZ());
        double eastScale = 1 / Math.max(0.05, Math.cos(latitude));
        double x = getX() + (velocity.x + acceleration.x * TICK) * eastScale * TICK;
        double z = getZ() + (velocity.z + acceleration.z * TICK) * TICK;
        return isReady(level, net.minecraft.util.Mth.floor(x) >> 4, net.minecraft.util.Mth.floor(z) >> 4);
    }

    /**
     * Whether a chunk is fully there for a vessel: generated, and its entities loaded (otherwise an
     * entity entering it is hidden from players, and its riders come loose on their screens).
     */
    static boolean isReady(ServerLevel level, int chunkX, int chunkZ) {
        return level.hasChunk(chunkX, chunkZ) && level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(chunkX, chunkZ));
    }

    /** How many times a vessel was hidden from players by its chunk unloading (should stay 0). */
    public static int timesHidden;

    /** Forge bus: an entity leaving the level without being removed means it was hidden. */
    public static void onLeaveLevel(net.minecraftforge.event.entity.EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof VesselEntity vessel && !vessel.level().isClientSide && !vessel.isRemoved()) {
            timesHidden++;
            com.thepearlking.stevespace.StevesSpace.LOGGER.warn("Vessel {} hidden at {} (chunk not loaded)", vessel.getId(), vessel.blockPosition());
        }
    }

    /** Every vessel on the server, for Mission Control: name, where it is, and its orbit. */
    public static List<com.thepearlking.stevespace.network.MissionControlS2C.Entry> trackingList(net.minecraft.server.MinecraftServer server) {
        List<com.thepearlking.stevespace.network.MissionControlS2C.Entry> list = new ArrayList<>();
        var system = ServerUniverse.system();
        double t = ServerUniverse.time(server);
        for (VesselEntity v : List.copyOf(SERVER_VESSELS)) {
            if (v.isRemoved() || v.stats == null) {
                continue;
            }
            CelestialBody body = v.flightBody();
            if (body == null) {
                continue;
            }
            String name = v.getName().getString();
            double[] state = new double[6];
            int mode;
            double altitude;
            double speed;
            if (v.orbital != null) {
                var s = v.orbital.state();
                state = new double[]{s.position().x(), s.position().y(), s.position().z(), s.velocity().x(), s.velocity().y(), s.velocity().z()};
                mode = 2;
                altitude = v.orbital.altitude();
                speed = s.velocity().length();
            } else {
                DVec3 ground = v.surfaceVelocity();
                var s = com.thepearlking.stevespace.universe.SurfaceFrame.toOrbit(system, body, v.mapX(), v.mapY(), v.mapZ(),
                        ground.x(), ground.y(), ground.z(), t);
                state = new double[]{s.position().x(), s.position().y(), s.position().z(), s.velocity().x(), s.velocity().y(), s.velocity().z()};
                mode = v.isGrounded() ? 0 : 1;
                altitude = v.mapY() - 63;
                speed = ground.length();
            }
            list.add(new com.thepearlking.stevespace.network.MissionControlS2C.Entry(v.getUUID(), name, body.id(), mode, altitude, speed, state));
        }
        return list;
    }

    /** Puts flight tickets on the chunks along a path from (x, z), so arriving vessels keep ticking. */
    static void loadPathAhead(ServerLevel level, CelestialBody body, double x, double z, double east, double south) {
        double latitude = body == null ? 0 : new PlanetGeometry(body.radius()).latitude(z);
        double eastScale = 1 / Math.max(0.05, Math.cos(latitude));
        var chunks = level.getChunkSource();
        // Loaded (not ticking: the vessel ticks itself) on every chunk along the path.
        double dx = east * eastScale * LOOK_AHEAD_SECONDS;
        double dz = south * LOOK_AHEAD_SECONDS;
        int steps = Math.max(1, (int) Math.ceil(Math.sqrt(dx * dx + dz * dz) / 8));
        net.minecraft.world.level.ChunkPos last = null;
        for (int i = 0; i <= steps; i++) {
            var pos = new net.minecraft.world.level.ChunkPos(
                    net.minecraft.util.Mth.floor(x + dx * i / steps) >> 4,
                    net.minecraft.util.Mth.floor(z + dz * i / steps) >> 4);
            if (!pos.equals(last)) {
                chunks.addRegionTicket(FLIGHT_TICKET, pos, 0, pos);
                last = pos;
            }
        }
    }

    /** Moves along {@code velocity} for one tick in small steps, stopping at the first contact. */
    private void move(Vector3d velocity, CelestialBody body) {
        double latitude = body == null ? 0 : new PlanetGeometry(body.radius()).latitude(getZ());
        // On the map, east-west distances shrink toward the poles; true speed is what we track.
        double eastScale = 1 / Math.max(0.05, Math.cos(latitude));
        Vector3d step = new Vector3d(velocity.x * eastScale, velocity.y, velocity.z).mul(TICK);
        int substeps = Math.max(1, (int) Math.ceil(step.length() / 0.4));
        Vector3d delta = new Vector3d(step).div(substeps);
        boolean nearGround = getY() - stats.radius() < level().getMaxBuildHeight() + 1;
        for (int i = 0; i < substeps; i++) {
            Vec3 next = position().add(delta.x, delta.y, delta.z);
            if (nearGround && collides(next)) {
                touchDown();
                return;
            }
            setPos(next);
        }
    }

    private void touchDown() {
        double impact = velocity.length();
        if (impact > SpaceConfig.CRASH_SPEED.get()) {
            crash(impact);
            return;
        }
        velocity.zero();
        angularVelocity.zero();
        setGrounded(true);
        restingTicks = 0;
    }

    /** True if any outer face of the vessel would be inside a solid block at {@code position}. */
    private boolean collides(Vec3 position) {
        Vector3d com = stats.centerOfMass();
        Vector3d point = new Vector3d();
        BlockPos.MutableBlockPos block = new BlockPos.MutableBlockPos();
        for (Vector3d hull : stats.hullPoints()) {
            for (int axis = 0; axis < 7; axis++) {
                point.set(hull).sub(com);
                if (axis > 0) {
                    // centre, then the six face centres (slightly inside the face)
                    double s = axis % 2 == 0 ? 0.48 : -0.48;
                    switch ((axis - 1) / 2) {
                        case 0 -> point.x += s;
                        case 1 -> point.y += s;
                        default -> point.z += s;
                    }
                }
                orientation.transform(point);
                block.set(Math.floor(position.x + point.x), Math.floor(position.y + point.y), Math.floor(position.z + point.z));
                BlockState state = level().getBlockState(block);
                // Water and lava hold a vessel up: it splashes down and floats rather than sinking.
                if (!state.isAir() && (!state.getCollisionShape(level(), block).isEmpty() || !state.getFluidState().isEmpty())) {
                    return true;
                }
            }
        }
        return false;
    }

    // ---------------------------------------------------------------------------------------
    // Landing and crashing
    // ---------------------------------------------------------------------------------------

    /** Turns the vessel back into blocks where it stands, upright and lined up with the grid. */
    public void land() {
        ServerLevel level = (ServerLevel) level();
        Rotation rotation = nearestUprightRotation();
        // Block p lands at origin + rotate(p). Its centre is at p + 0.5 in vessel coordinates and
        // at position + rotate(p + 0.5 - com) in the world, so the origin that matches is
        // position - rotate(com) + rotate(half) - half (rotating a block's corner is not the same
        // as rotating its centre).
        Vector3d half = new Vector3d(0.5, 0.5, 0.5);
        Vector3d originExact = new Vector3d(getX(), getY(), getZ())
                .sub(rotatedOffset(stats.centerOfMass(), rotation))
                .add(rotatedOffset(half, rotation))
                .sub(half);
        BlockPos origin = new BlockPos((int) Math.round(originExact.x), (int) Math.round(originExact.y), (int) Math.round(originExact.z));
        for (int lift = 0; lift < 4 && !canPlace(level, origin, rotation); lift++) {
            origin = origin.above();
        }
        List<Entity> riders = new ArrayList<>(getPassengers());
        allowDismount = true;
        ejectPassengers();
        allowDismount = false;
        VesselAssembler.placeInWorld(level, origin, structure, rotation);
        com.thepearlking.stevespace.world.ShipNames.remember(level, origin, structure, rotation, getCustomName());
        distributePropellant(level, origin, rotation);
        seatRiders(level, origin, rotation, riders);
        discard();
    }

    private Vector3d rotatedOffset(Vector3d local, Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90 -> new Vector3d(-local.z, local.y, local.x);
            case CLOCKWISE_180 -> new Vector3d(-local.x, local.y, -local.z);
            case COUNTERCLOCKWISE_90 -> new Vector3d(local.z, local.y, -local.x);
            default -> new Vector3d(local);
        };
    }

    /** The quarter turn about the vertical axis closest to how the vessel is facing now. */
    private Rotation nearestUprightRotation() {
        Vector3d north = orientation.transform(new Vector3d(0, 0, -1));
        double yaw = Math.atan2(north.x, -north.z); // 0 = still facing north, + = turned east
        int quarter = Math.floorMod((int) Math.round(yaw / (Math.PI / 2)), 4);
        return switch (quarter) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    private boolean canPlace(ServerLevel level, BlockPos origin, Rotation rotation) {
        for (BlockPos rel : structure.blocks().keySet()) {
            BlockState there = level.getBlockState(origin.offset(rel.rotate(rotation)));
            if (!there.isAir() && !there.canBeReplaced()) {
                return false;
            }
        }
        return true;
    }

    private void distributePropellant(ServerLevel level, BlockPos origin, Rotation rotation) {
        double remaining = propellant;
        for (Map.Entry<BlockPos, BlockState> entry : structure.blocks().entrySet()) {
            BlockEntity be = level.getBlockEntity(origin.offset(entry.getKey().rotate(rotation)));
            if (be instanceof PropellantTankBlockEntity tank) {
                tank.setPropellant(0);
                remaining -= tank.fill(remaining);
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // The ship as blocks in space
    // ---------------------------------------------------------------------------------------

    public boolean materialized() {
        return entityData.get(MATERIALIZED);
    }

    public BlockPos blockOrigin() {
        return entityData.get(BLOCK_ORIGIN);
    }

    /** True if a point is inside the ship's blocks (world coordinates), give or take {@code margin}. */
    public boolean holds(double x, double y, double z, double margin) {
        BlockPos o = blockOrigin();
        return x >= o.getX() + boundsMin.getX() - margin && x <= o.getX() + boundsMax.getX() + 1 + margin
                && y >= o.getY() + boundsMin.getY() - margin && y <= o.getY() + boundsMax.getY() + 1 + margin
                && z >= o.getZ() + boundsMin.getZ() - margin && z <= o.getZ() + boundsMax.getZ() + 1 + margin;
    }

    /** Players in or around the ship's blocks. */
    public List<Player> playersAboard(double margin) {
        List<Player> found = new ArrayList<>();
        for (Player player : level().players()) {
            if (holds(player.getX(), player.getY(), player.getZ(), margin)) {
                found.add(player);
            }
        }
        return found;
    }

    /**
     * The ship a player is in: the one they ride, or in space the one whose blocks they stand
     * among or sit in (or float beside, within {@code margin}). Works on either side.
     */
    public static VesselEntity shipOf(Player player, double margin) {
        if (player.getVehicle() instanceof VesselEntity v) {
            return v;
        }
        if (!com.thepearlking.stevespace.world.SpaceDimension.isSpace(player.level())) {
            return null;
        }
        Iterable<? extends Entity> candidates = player.level().isClientSide
                ? clientVessels.get()
                : List.copyOf(SERVER_VESSELS);
        for (Entity e : candidates) {
            if (e instanceof VesselEntity v && !v.isRemoved() && v.level() == player.level() && v.materialized() && v.stats != null
                    && v.holds(player.getX(), player.getY(), player.getZ(), margin)) {
                return v;
            }
        }
        return null;
    }

    /** Client side: the cell's axes right now (rows: east, up, south) for the ship the player is flying, else null. */
    public static java.util.function.Function<VesselEntity, double[]> clientAxes = v -> null;

    /** Client side: the entities to look through (set up by the client; nothing elsewhere). */
    public static java.util.function.Supplier<Iterable<? extends Entity>> clientVessels = List::of;

    public static VesselEntity shipOf(Player player) {
        return shipOf(player, 48);
    }

    /** The materialized ship whose blocks include {@code pos} (server), or null. */
    public static VesselEntity materializedAt(ServerLevel level, BlockPos pos) {
        for (VesselEntity v : List.copyOf(SERVER_VESSELS)) {
            if (!v.isRemoved() && v.level() == level && v.materialized() && v.holds(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 0)) {
                return v;
            }
        }
        return null;
    }

    /** In its own cell in space (map mode or orbit), with the crew caught up after crossing over. */
    private boolean readyToMaterialize() {
        if (!com.thepearlking.stevespace.world.SpaceDimension.isSpace(level()) || isRemoved() || stats == null) {
            return false;
        }
        boolean ownCell = orbital != null ? orbital.anchor() == null : mapPosition != null;
        if (!ownCell || tickCount < 10) {
            return false;
        }
        for (java.util.UUID id : crew.everyone()) {
            boolean here = getPassengers().stream().anyMatch(p -> p.getUUID().equals(id));
            if (!here && tickCount < 60) {
                return false; // someone still crossing over with it
            }
        }
        return true;
    }

    /**
     * Puts the ship's blocks into its cell, upright, and the crew into its seats (or on their feet
     * where they stood): from now on it is a place like any other, flown from its pilot seat.
     */
    public void materialize() {
        ServerLevel level = (ServerLevel) level();
        Vector3d mass = stats.centerOfMass();
        BlockPos origin = new BlockPos((int) Math.round(getX() - mass.x), (int) Math.round(getY() - mass.y), (int) Math.round(getZ() - mass.z));
        List<Entity> riders = new ArrayList<>(getPassengers());
        allowDismount = true;
        ejectPassengers();
        allowDismount = false;
        VesselAssembler.placeInWorld(level, origin, structure, Rotation.NONE);
        com.thepearlking.stevespace.world.ShipNames.remember(level, origin, structure, Rotation.NONE, getCustomName());
        distributePropellant(level, origin, Rotation.NONE);
        entityData.set(MATERIALIZED, true);
        entityData.set(BLOCK_ORIGIN, origin);
        // Anyone else floating where the blocks just appeared is moved clear.
        for (Player nearby : playersAboard(1)) {
            if (riders.contains(nearby)) {
                continue;
            }
            for (int up = 0; up < 64 && !level.noCollision(nearby); up++) {
                nearby.teleportTo(nearby.getX(), nearby.getY() + 1, nearby.getZ());
            }
        }
        seatRiders(level, origin, Rotation.NONE, riders);
        com.thepearlking.stevespace.StevesSpace.LOGGER.info("{} is blocks in its cell at {} ({} aboard)", getName().getString(), origin, riders.size());
    }

    /**
     * Back to a flying object (to come down to the ground, dock, or share another ship's cell):
     * the blocks come out of the world with whatever was built on, the crew aboard as before.
     */
    public void dematerialize() {
        dematerialize(false);
    }

    /**
     * {@code landing}: on the way down to a planet, so nobody is left behind or loose: everyone
     * in or near the ship (on its hull, floating by the hatch) comes along. Those sitting stay
     * in their seats; those on their feet ride along standing where they were (a ship can carry
     * more people than it has seats).
     */
    public void dematerialize(boolean landing) {
        if (!materialized()) {
            return;
        }
        ServerLevel level = (ServerLevel) level();
        refreshFromBlocks(level);
        BlockPos origin = blockOrigin();
        List<Player> aboard = playersAboard(landing ? 8 : 1.5);
        List<VesselStats.Seat> seats = stats.seats();
        for (Player player : aboard) {
            int seat = -1;
            if (player.getVehicle() instanceof SeatEntity s) {
                for (int i = 0; i < seats.size(); i++) {
                    if (origin.offset(seats.get(i).pos()).equals(s.blockPosition())) {
                        seat = i;
                    }
                }
            }
            if (seat >= 0) {
                crew.sit(player.getUUID(), seat);
            } else {
                standHere(player, origin);
            }
            player.fallDistance = 0;
        }
        VesselAssembler.removeFromWorld(level, origin, structure);
        entityData.set(MATERIALIZED, false);
        Vector3d mass = stats.centerOfMass();
        setPos(origin.getX() + mass.x, origin.getY() + mass.y, origin.getZ() + mass.z);
        for (Player player : aboard) {
            leaveGroundSeat(player);
            player.startRiding(this, true);
        }
        crewDirty = true;
    }

    /** Crew on their feet: where they stand, relative to the ship's blocks (kept inside its bounds). */
    private void standHere(Player player, BlockPos origin) {
        crew.remove(player.getUUID());
        double x = Math.max(boundsMin.getX(), Math.min(boundsMax.getX() + 1, player.getX() - origin.getX()));
        double y = Math.max(boundsMin.getY(), Math.min(boundsMax.getY() + 1, player.getY() - origin.getY()));
        double z = Math.max(boundsMin.getZ(), Math.min(boundsMax.getZ() + 1, player.getZ() - origin.getZ()));
        crew.walk(player.getUUID(), new Vector3d(x, y, z));
    }

    /** True if the player stands among the blocks of this ship as it was at {@code origin} (lift-off). */
    public boolean standsIn(Player player, BlockPos origin) {
        double x = player.getX() - origin.getX();
        double y = player.getY() - origin.getY();
        double z = player.getZ() - origin.getZ();
        return x >= boundsMin.getX() - 0.3 && x <= boundsMax.getX() + 1.3
                && y >= boundsMin.getY() - 0.1 && y <= boundsMax.getY() + 1.5
                && z >= boundsMin.getZ() - 0.3 && z <= boundsMax.getZ() + 1.3;
    }

    /**
     * A remote pilot taking the controls at a receiver ({@code receiver}, in the craft's own block
     * positions): they ride in its seat, as a pilot would. False if there's no such seat.
     */
    public boolean boardRemote(Player player, BlockPos receiver) {
        int seat = -1;
        for (int i = 0; i < stats.seats().size(); i++) {
            if (stats.seats().get(i).pos().equals(receiver)) {
                seat = i;
            }
        }
        if (seat < 0) {
            return false;
        }
        crew.remove(player.getUUID());
        crew.sit(player.getUUID(), seat);
        crewDirty = true;
        return player.startRiding(this, true);
    }

    /** Everyone out, now (the craft is about to stop existing). */
    public void ejectAllForDestruction() {
        allowDismount = true;
        ejectPassengers();
        allowDismount = false;
    }

    /** A remote pilot letting go: out of the craft's crew, and out of it. */
    public void releaseRemote(Player player) {
        crew.remove(player.getUUID());
        crewDirty = true;
        allowDismount = true;
        player.stopRiding();
        allowDismount = false;
    }

    /** Someone on their feet in the ship as it lifts off: they come along standing where they are. */
    public void takeAboardStanding(Player player, BlockPos origin) {
        standHere(player, origin);
        player.fallDistance = 0;
        leaveGroundSeat(player);
        player.startRiding(this, true);
        crewDirty = true;
    }

    /** Every second while materialized: pick up what was built on or taken off, and show the fuel in the tanks. */
    private void shipBlocksTick(ServerLevel level) {
        if (tickCount % 20 == 0) {
            refreshFromBlocks(level);
        }
        if (tickCount % 40 == 0) {
            distributePropellant(level, blockOrigin(), Rotation.NONE);
        }
    }

    /** Re-reads the ship from its blocks, from the pilot seat (if it still has one). */
    private void refreshFromBlocks(ServerLevel level) {
        if (stats.seats().isEmpty()) {
            return;
        }
        BlockPos oldOrigin = blockOrigin();
        BlockPos seat = oldOrigin.offset(stats.seats().get(0).pos());
        if (!(level.getBlockState(seat).getBlock() instanceof com.thepearlking.stevespace.block.PilotSeatBlock)) {
            return; // no pilot seat any more: it stays as it was until one is put back
        }
        VesselAssembler.Result scan = VesselAssembler.scan(level, seat, SpaceConfig.MAX_VESSEL_BLOCKS.get(), false);
        if (!scan.ok()) {
            return;
        }
        Map<BlockPos, BlockState> fresh = scan.structure().blocks();
        BlockPos shift = seat.subtract(oldOrigin);
        boolean same = fresh.size() == structure.blocks().size();
        if (same) {
            for (Map.Entry<BlockPos, BlockState> e : fresh.entrySet()) {
                if (!e.getValue().equals(structure.blocks().get(e.getKey().offset(shift)))) {
                    same = false;
                    break;
                }
            }
        }
        if (same) {
            return;
        }
        setStructure(scan.structure());
        entityData.set(BLOCK_ORIGIN, seat);
        propellant = Math.min(propellant, stats.propellantCapacity());
    }

    /** Parked: the blocks stay, the flight stops; the cell keeps following the orbit. */
    private void parkInPlace() {
        autopilot.stop();
        ServerLevel level = (ServerLevel) level();
        BlockPos o = blockOrigin();
        distributePropellant(level, o, Rotation.NONE);
        com.thepearlking.stevespace.world.ParkedShips.get(level.getServer()).put(new com.thepearlking.stevespace.world.ParkedShips.Parked(
                orbital.cell(), orbital.body().id(), orbital.state(), orbital.time(),
                net.minecraft.network.chat.Component.Serializer.toJson(getName(), level.registryAccess()),
                o.offset(boundsMin), o.offset(boundsMax), new com.thepearlking.stevespace.universe.DVec3(getX(), getY(), getZ()),
                getUUID(), owner));
        parking = true;
        discard();
        com.thepearlking.stevespace.StevesSpace.LOGGER.info("{} parked in orbit of {} (cell {})", getName().getString(), orbital.body().id(), orbital.cell());
    }

    private void seatRiders(ServerLevel level, BlockPos origin, Rotation rotation, List<Entity> riders) {
        List<VesselStats.Seat> seats = stats.seats();
        Vector3d half = new Vector3d(0.5, 0.5, 0.5);
        for (Entity rider : riders) {
            Vector3d walking = crew.walkPosition(rider.getUUID());
            int seat = crew.seatOf(rider.getUUID());
            if (walking != null) {
                // Standing in the cabin: still standing there, now among real blocks.
                Vector3d at = rotatedOffset(new Vector3d(walking).sub(half), rotation).add(half);
                rider.teleportTo(origin.getX() + at.x, origin.getY() + at.y + 0.02, origin.getZ() + at.z);
                rider.setDeltaMovement(Vec3.ZERO);
            } else if (seat >= 0 && seat < seats.size()) {
                BlockPos seatPos = origin.offset(seats.get(seat).pos().rotate(rotation));
                SeatEntity seatEntity = SeatEntity.create(level, seatPos);
                level.addFreshEntity(seatEntity);
                rider.startRiding(seatEntity, true);
            } else {
                rider.teleportTo(getX(), getY() + stats.radius(), getZ());
            }
        }
    }

    private void crash(double impact) {
        com.thepearlking.stevespace.remote.RemoteControl.craftLost(this); // remote pilots go home first, unhurt
        if (materialized()) {
            dematerialize(); // the blocks go down with it
        }
        float power = (float) Math.max(2, Math.min(8, impact / 8));
        List<Entity> riders = new ArrayList<>(getPassengers());
        allowDismount = true;
        ejectPassengers();
        allowDismount = false;
        Vec3 at = position();
        discard();
        level().explode(null, at.x, at.y - stats.radius() * 0.5, at.z, power, Level.ExplosionInteraction.TNT);
        for (Entity rider : riders) {
            rider.hurt(level().damageSources().explosion(null, null), power * 4);
        }
    }

    // ---------------------------------------------------------------------------------------
    // Passengers
    // ---------------------------------------------------------------------------------------

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return passenger instanceof Player && (crew().knows(passenger.getUUID()) || crew().seated() < Math.max(1, stats.seats().size()));
    }

    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        // New aboard: the lowest free seat (the pilot's, if it's free). Known crew keep their place.
        if (!level().isClientSide && passenger instanceof Player && stats != null && !crew.knows(passenger.getUUID())) {
            crew.sit(passenger.getUUID(), Math.max(0, crew.freeSeat(Math.max(1, stats.seats().size()))));
            crewDirty = true;
        }
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction move) {
        Vector3d walking = walkPositionOf(passenger);
        if (walking != null) {
            // Up and about: wherever they've walked to, carried along as the ship moves and turns.
            Quaterniond turn = level().isClientSide ? new Quaterniond(entityData.get(ORIENTATION)) : orientation;
            // Placed so the eyes are where they should be in the turned ship (the view comes from there).
            double eye = passenger.getEyeHeight();
            Vector3d o = turn.transform(new Vector3d(walking).add(0, eye, 0).sub(stats.centerOfMass())).sub(0, eye, 0);
            move.accept(passenger, getX() + o.x, getY() + o.y, getZ() + o.z);
            passenger.xo = xo + o.x;
            passenger.yo = yo + o.y;
            passenger.zo = zo + o.z;
            passenger.xOld = xOld + o.x;
            passenger.yOld = yOld + o.y;
            passenger.zOld = zOld + o.z;
            return;
        }
        int index = crew().seatOf(passenger.getUUID());
        Vector3d seat = index >= 0 && index < stats.seats().size()
                ? new Vector3d(stats.seats().get(index).pos().getX() + 0.5,
                stats.seats().get(index).pos().getY() + SeatEntity.SEAT_HEIGHT,
                stats.seats().get(index).pos().getZ() + 0.5)
                : stats.centerOfMass();
        Quaterniond rotation = level().isClientSide ? new Quaterniond(entityData.get(ORIENTATION)) : orientation;
        Vector3d offset = rotation.transform(seat.sub(stats.centerOfMass()));
        Vec3 attach = passenger.getVehicleAttachmentPoint(this);
        double dx = offset.x - attach.x;
        double dy = offset.y - attach.y;
        double dz = offset.z - attach.z;
        move.accept(passenger, getX() + dx, getY() + dy, getZ() + dz);
        // Interpolate riders exactly like the vessel between ticks; otherwise at speed they are drawn
        // a tick behind it (a block or more below the seat when climbing fast).
        passenger.xo = xo + dx;
        passenger.yo = yo + dy;
        passenger.zo = zo + dz;
        passenger.xOld = xOld + dx;
        passenger.yOld = yOld + dy;
        passenger.zOld = zOld + dz;
    }

    @Override
    public LivingEntity getControllingPassenger() {
        return null; // the server flies the vessel; the pilot only sends controls
    }

    /** Where a walking passenger stands (ship block coordinates), or null if seated. */
    private Vector3d walkPositionOf(Entity passenger) {
        if (level().isClientSide) {
            Vector3d own = clientWalk.apply(this, passenger);
            if (own != null) {
                return own;
            }
        }
        return crew().walkPosition(passenger.getUUID());
    }

    /** The local player's own idea of where they're walking (set up by the client; null elsewhere). */
    public static java.util.function.BiFunction<VesselEntity, Entity, Vector3d> clientWalk = (v, e) -> null;

    /** Set while drawing someone walking about the cabin, so they're drawn standing. */
    public static Entity drawingWalker;

    @Override
    public boolean shouldRiderSit() {
        return drawingWalker == null;
    }

    // ---------------------------------------------------------------------------------------
    // Bounds and misc
    // ---------------------------------------------------------------------------------------

    @Override
    protected AABB makeBoundingBox() {
        double r = stats == null ? 0.5 : stats.radius();
        return new AABB(getX() - r, getY() - r, getZ() - r, getX() + r, getY() + r, getZ() + r);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double r = stats == null ? 1 : stats.radius();
        double max = 64 * 64 + r * r * 400;
        return distance < max * 16;
    }
}
