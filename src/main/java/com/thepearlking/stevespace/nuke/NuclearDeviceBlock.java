package com.thepearlking.stevespace.nuke;

import com.thepearlking.stevespace.block.FacingBlock;
import com.thepearlking.stevespace.remote.RemoteFunctions;
import com.thepearlking.stevespace.vessel.VesselEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A nuclear device. The dial sets its yield (right-click): Hiroshima's 15 kilotons, Starfish
 * Prime's 1.45 megatons, or Tsar Bomba's 50 megatons. It does nothing until armed (sneak and
 * right-click); armed, a redstone signal sets it off: a lever, or a remote function relay from
 * the other side of the solar system. Both settings are part of the block, so they stay put when
 * the craft it's built into flies.
 */
public class NuclearDeviceBlock extends FacingBlock implements RemoteFunctions.Triggered {

    public static final IntegerProperty YIELD = IntegerProperty.create("yield", 0, 2);
    public static final BooleanProperty ARMED = BooleanProperty.create("armed");

    public NuclearDeviceBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(YIELD, 1).setValue(ARMED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(YIELD, ARMED);
    }

    public static double kilotons(BlockState state) {
        return NuclearEffects.YIELDS[state.getValue(YIELD)];
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown()) {
            boolean armed = !state.getValue(ARMED);
            level.setBlock(pos, state.setValue(ARMED, armed), Block.UPDATE_ALL);
            level.playSound(null, pos, armed ? SoundEvents.LEVER_CLICK : SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.BLOCKS, 0.6f, armed ? 0.7f : 0.9f);
            player.displayClientMessage(Component.translatable(armed ? "stevespace.nuke.armed" : "stevespace.nuke.safe",
                    Component.translatable("stevespace.nuke.yield." + state.getValue(YIELD))), true);
            // Armed with a signal already on it: it goes.
            if (armed && level instanceof ServerLevel server && level.hasNeighborSignal(pos)) {
                Detonation.fromWorld(server, pos, state.setValue(ARMED, true));
            }
            return InteractionResult.CONSUME;
        }
        if (state.getValue(ARMED)) {
            player.displayClientMessage(Component.translatable("stevespace.nuke.disarm_first"), true);
            return InteractionResult.CONSUME;
        }
        int next = (state.getValue(YIELD) + 1) % NuclearEffects.YIELDS.length;
        level.setBlock(pos, state.setValue(YIELD, next), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.5f, 0.8f);
        player.displayClientMessage(Component.translatable("stevespace.nuke.yield_set", Component.translatable("stevespace.nuke.yield." + next)), true);
        return InteractionResult.CONSUME;
    }

    /** Placed (or landed) armed, next to a signal that's already on. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (level instanceof ServerLevel server && !old.is(state.getBlock()) && state.getValue(ARMED) && level.hasNeighborSignal(pos)) {
            server.getServer().execute(() -> {
                if (server.getBlockState(pos).is(this)) {
                    Detonation.fromWorld(server, pos, server.getBlockState(pos));
                }
            });
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbour, BlockPos from, boolean moving) {
        super.neighborChanged(state, level, pos, neighbour, from, moving);
        if (level instanceof ServerLevel server && level.hasNeighborSignal(pos)) {
            if (state.getValue(ARMED)) {
                Detonation.fromWorld(server, pos, state);
            } else {
                safe(server, pos);
            }
        }
    }

    /** Powered while safe: a dead click, and anyone close by is told it isn't armed (at most every couple of seconds). */
    private static void safe(ServerLevel level, BlockPos pos) {
        long now = level.getGameTime();
        Long last = SAFE_NOTICE.get(pos);
        if (last != null && now - last < 40) {
            return;
        }
        SAFE_NOTICE.put(pos.immutable(), now);
        if (SAFE_NOTICE.size() > 64) {
            SAFE_NOTICE.entrySet().removeIf(e -> now - e.getValue() > 40);
        }
        level.playSound(null, pos, SoundEvents.DISPENSER_FAIL, SoundSource.BLOCKS, 0.5f, 0.6f);
        for (var player : level.players()) {
            if (player.distanceToSqr(pos.getCenter()) < 12 * 12) {
                player.displayClientMessage(Component.translatable("stevespace.nuke.not_armed"), true);
            }
        }
    }

    private static final java.util.Map<BlockPos, Long> SAFE_NOTICE = new java.util.HashMap<>();

    /** A relay touching it pulsed while the craft flies as one piece. */
    @Override
    public void triggerInFlight(VesselEntity vessel, BlockPos relative) {
        BlockState state = vessel.structure().blocks().get(relative);
        if (state != null && state.getValue(ARMED)) {
            Detonation.inFlight(vessel, relative, state);
        } else if (state != null) {
            for (var e : vessel.getPassengers()) {
                if (e instanceof Player p) {
                    p.displayClientMessage(Component.translatable("stevespace.nuke.not_armed"), true);
                }
            }
        }
    }
}
