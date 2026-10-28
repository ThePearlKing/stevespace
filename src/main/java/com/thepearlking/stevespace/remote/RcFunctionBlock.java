package com.thepearlking.stevespace.remote;

import com.thepearlking.stevespace.block.FacingBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A remote control function relay, tuned to a channel and set to function 1 to 5 (right-click).
 * A console on the same channel gets a button for each function used on it; pressing one makes
 * every relay on that channel and function give a redstone pulse, wherever it is: on the ground,
 * in a landed or parked ship, or in one flying (once the signal gets there).
 */
public class RcFunctionBlock extends FacingBlock implements net.minecraft.world.level.block.EntityBlock {

    public static final IntegerProperty FUNCTION = IntegerProperty.create("function", 1, 5);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    /** How long a pulse lasts, in ticks (about the length of a button press). */
    public static final int PULSE = 10;

    public RcFunctionBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FUNCTION, 1).setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FUNCTION, POWERED);
    }

    @Override
    public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RcFunctionEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof net.minecraft.server.level.ServerPlayer sp && level.getBlockEntity(pos) instanceof RcFunctionEntity relay) {
            RemoteControl.openTuner(sp, pos, relay.channel(), state.getValue(FUNCTION));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** The channel a relay is on (in the world, or saved with a ship's blocks). */
    public static int channelAt(net.minecraft.world.level.Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof RcFunctionEntity relay ? relay.channel() : ChannelEntity.MIN;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (level instanceof ServerLevel server && !old.is(state.getBlock())) {
            RemoteSites.relays(server.getServer()).add(server.dimension().location().toString(), pos);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState now, boolean moving) {
        if (level instanceof ServerLevel server && !now.is(state.getBlock())) {
            RemoteSites.relays(server.getServer()).remove(server.dimension().location().toString(), pos);
        }
        super.onRemove(state, level, pos, now, moving);
    }

    /** A pulse from this relay (a function button was pressed). */
    public static void pulse(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof RcFunctionBlock)) {
            return;
        }
        level.setBlock(pos, state.setValue(POWERED, true), Block.UPDATE_ALL);
        level.updateNeighborsAt(pos, state.getBlock());
        level.scheduleTick(pos, state.getBlock(), PULSE);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, false), Block.UPDATE_ALL);
            level.updateNeighborsAt(pos, state.getBlock());
        }
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWERED) ? 15 : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWERED) ? 15 : 0;
    }
}
