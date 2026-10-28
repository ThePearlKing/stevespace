package com.thepearlking.stevespace.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A docking port. {@code FACING} is the way its collar points: two vessels whose ports meet
 * face to face, gently, join into one; undocking parts them again. Docked, power only crosses
 * between two ports that both have {@code SHARING} on (red collar; sneak and use it with an
 * empty hand to switch).
 */
public class DockingPortBlock extends DirectionalBlock {

    public static final MapCodec<DockingPortBlock> CODEC = simpleCodec(DockingPortBlock::new);
    public static final BooleanProperty SHARING = BooleanProperty.create("sharing");

    public DockingPortBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP).setValue(SHARING, false));
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SHARING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            boolean sharing = !state.getValue(SHARING);
            level.setBlock(pos, state.setValue(SHARING, sharing), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.5f, sharing ? 0.7f : 0.55f);
            player.displayClientMessage(Component.translatable(sharing ? "stevespace.docking_port.sharing_on"
                    : "stevespace.docking_port.sharing_off"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Whether two blocks are a pair of docked ports that let power across (both sharing, face to face). */
    public static boolean sharesWith(BlockState a, BlockPos posA, BlockState b, BlockPos posB) {
        return a.getBlock() instanceof DockingPortBlock && b.getBlock() instanceof DockingPortBlock
                && a.getValue(SHARING) && b.getValue(SHARING)
                && posA.relative(a.getValue(FACING)).equals(posB) && posB.relative(b.getValue(FACING)).equals(posA);
    }

    /** Whether going from {@code from} to the block at {@code to} goes out through a port's collar. */
    public static boolean throughCollar(BlockState state, BlockPos pos, BlockPos to) {
        if (!(state.getBlock() instanceof DockingPortBlock)) {
            return false;
        }
        Direction facing = state.getValue(FACING);
        BlockPos d = to.subtract(pos);
        // Straight out, or out at an angle past the collar's face.
        return d.getX() * facing.getStepX() + d.getY() * facing.getStepY() + d.getZ() * facing.getStepZ() > 0;
    }
}
