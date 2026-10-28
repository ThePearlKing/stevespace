package com.thepearlking.stevespace.energy;

import com.thepearlking.stevespace.block.FacingBlock;
import com.thepearlking.stevespace.registry.ModBlockEntities;
import com.thepearlking.stevespace.registry.ModBlocks;
import com.thepearlking.stevespace.registry.WorldBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/** The blocks of the power system: panels, the solar controller, the RTG, batteries, the creative source (machines: {@link Machines}). */
public final class PowerBlocks {

    private PowerBlocks() {
    }

    /** Formats a power reading for the action bar. */
    static Component stored(PowerBlockEntity be) {
        return Component.translatable("stevespace.power.stored", String.format("%,d", be.buffer().getEnergyStored()),
                String.format("%,d", be.buffer().getMaxEnergyStored()));
    }

    // -------------------------------------------------------------------------------------------
    // Solar panel: a thin plate, no machinery of its own; the controller counts the panels joined to it.

    public static class SolarPanel extends Block {
        private static final VoxelShape SHAPE = Block.box(0, 7, 0, 16, 9, 16);

        public SolarPanel(Properties properties) {
            super(properties);
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPE;
        }
    }

    /** Full sun at Earth's distance, per panel. */
    public static final double PANEL_POWER = 8;

    // -------------------------------------------------------------------------------------------
    // Generators with a block entity that ticks.

    /** Common block for the powered machines: makes its block entity and ticks it on the server. */
    public abstract static class PowerBlock extends FacingBlock implements EntityBlock {

        protected PowerBlock(Properties properties) {
            super(properties);
        }

        protected abstract BlockEntityType<? extends PowerBlockEntity> type();

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return type().create(pos, state);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            if (level.isClientSide || type != type()) {
                return null;
            }
            return (lvl, pos, st, be) -> ((Ticking) be).serverTick((ServerLevel) lvl, pos);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof Ticking be) {
                player.displayClientMessage(be.status(), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
    }

    /** A power block entity that ticks on the server and can describe itself. */
    public interface Ticking {
        void serverTick(ServerLevel level, BlockPos pos);

        Component status();
    }

    // Solar controller ---------------------------------------------------------------------------

    public static class SolarController extends PowerBlock {
        public SolarController(Properties p) {
            super(p);
        }

        @Override
        protected BlockEntityType<? extends PowerBlockEntity> type() {
            return ModBlockEntities.SOLAR_CONTROLLER.get();
        }
    }

    public static class SolarControllerEntity extends PowerBlockEntity implements Ticking {
        private int panels;
        private double light;
        private double fraction;

        public SolarControllerEntity(BlockPos pos, BlockState state) {
            super(ModBlockEntities.SOLAR_CONTROLLER.get(), pos, state, 20_000, 0, 4_000);
        }

        @Override
        public Role role() {
            return Role.SOURCE;
        }

        @Override
        public void serverTick(ServerLevel level, BlockPos pos) {
            if (level.getGameTime() % 20 == 0) {
                countPanels(level, pos);
            }
            fraction += panels * PANEL_POWER * light;
            int whole = (int) fraction;
            fraction -= whole;
            buffer.add(whole);
            PowerGrid.distribute(level, pos, this);
            setChanged();
        }

        /** The panels joined to this controller (touching it, or each other), and their average sunlight. */
        private void countPanels(ServerLevel level, BlockPos pos) {
            Set<BlockPos> panelsFound = new HashSet<>();
            Set<BlockPos> looked = new HashSet<>();
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            for (Direction d : Direction.values()) {
                queue.add(pos.relative(d));
            }
            double sum = 0;
            while (!queue.isEmpty() && panelsFound.size() < 512) {
                BlockPos p = queue.poll();
                if (!looked.add(p) || !level.isLoaded(p) || !level.getBlockState(p).is(ModBlocks.SOLAR_PANEL.get())) {
                    continue;
                }
                panelsFound.add(p);
                sum += Sunlight.at(level, p);
                for (Direction d : Direction.values()) {
                    queue.add(p.relative(d));
                }
            }
            panels = panelsFound.size();
            light = panels == 0 ? 0 : sum / panels;
        }

        @Override
        public Component status() {
            return Component.translatable("stevespace.solar.status", panels, String.format("%.1f", panels * PANEL_POWER * light))
                    .append(" · ").append(stored(this));
        }
    }

    // RTG ------------------------------------------------------------------------------------------

    /** Uraninite feeds it: one piece lasts a long time. */
    public static final int RTG_POWER = 20;
    public static final int FUEL_PER_PIECE = 20 * 60 * 120;

    public static class Rtg extends PowerBlock {
        public Rtg(Properties p) {
            super(p);
        }

        @Override
        protected BlockEntityType<? extends PowerBlockEntity> type() {
            return ModBlockEntities.RTG.get();
        }

        @Override
        protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                                  InteractionHand hand, BlockHitResult hit) {
            if (stack.getItem() != WorldBlocks.item("uraninite")) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof RtgEntity rtg) {
                if (rtg.fuel <= FUEL_PER_PIECE * 3) {
                    rtg.fuel += FUEL_PER_PIECE;
                    if (!player.getAbilities().instabuild) {
                        stack.shrink(1);
                    }
                    rtg.setChanged();
                }
                player.displayClientMessage(rtg.status(), true);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
    }

    public static class RtgEntity extends PowerBlockEntity implements Ticking {
        int fuel;

        public RtgEntity(BlockPos pos, BlockState state) {
            super(ModBlockEntities.RTG.get(), pos, state, 20_000, 0, 4_000);
        }

        @Override
        public Role role() {
            return Role.SOURCE;
        }

        @Override
        public void serverTick(ServerLevel level, BlockPos pos) {
            if (fuel > 0) {
                fuel--;
                buffer.add(RTG_POWER);
                if (fuel % 200 == 0) {
                    setChanged();
                }
            }
            PowerGrid.distribute(level, pos, this);
        }

        @Override
        public Component status() {
            int minutes = fuel / 20 / 60;
            return (fuel > 0 ? Component.translatable("stevespace.rtg.running", RTG_POWER, minutes)
                    : Component.translatable("stevespace.rtg.empty")).copy().append(" · ").append(stored(this));
        }

        @Override
        protected void saveAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            tag.putInt("fuel", fuel);
        }

        @Override
        protected void loadAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            fuel = tag.getInt("fuel");
        }
    }

    // Battery bank ----------------------------------------------------------------------------------

    public static class Battery extends PowerBlock {
        final int capacity;
        final int rate;

        public Battery(Properties p, int capacity, int rate) {
            super(p);
            this.capacity = capacity;
            this.rate = rate;
        }

        @Override
        protected BlockEntityType<? extends PowerBlockEntity> type() {
            return ModBlockEntities.BATTERY.get();
        }
    }

    /** One block entity for every size of battery; the block says how big. */
    public static class BatteryEntity extends PowerBlockEntity implements Ticking {
        public BatteryEntity(BlockPos pos, BlockState state) {
            super(ModBlockEntities.BATTERY.get(), pos, state, size(state).capacity, size(state).rate, size(state).rate);
        }

        private static Battery size(BlockState state) {
            return state.getBlock() instanceof Battery b ? b : (Battery) ModBlocks.BATTERY.get();
        }

        @Override
        public Role role() {
            return Role.STORE;
        }

        @Override
        public void serverTick(ServerLevel level, BlockPos pos) {
            PowerGrid.distribute(level, pos, this);
            if (level.getGameTime() % 40 == 0) {
                setChanged();
            }
        }

        @Override
        public Component status() {
            return stored(this);
        }
    }

    // Creative power source ------------------------------------------------------------------------

    public static class CreativeSource extends PowerBlock {
        public CreativeSource(Properties p) {
            super(p);
        }

        @Override
        protected BlockEntityType<? extends PowerBlockEntity> type() {
            return ModBlockEntities.CREATIVE_POWER.get();
        }
    }

    public static class CreativeSourceEntity extends PowerBlockEntity implements Ticking {
        public CreativeSourceEntity(BlockPos pos, BlockState state) {
            super(ModBlockEntities.CREATIVE_POWER.get(), pos, state, Integer.MAX_VALUE / 2, 0, Integer.MAX_VALUE / 2);
        }

        @Override
        public Role role() {
            return Role.SOURCE;
        }

        @Override
        public void serverTick(ServerLevel level, BlockPos pos) {
            buffer.set(1_000_000);
            PowerGrid.distribute(level, pos, this);
        }

        @Override
        public Component status() {
            return Component.translatable("stevespace.power.unlimited");
        }
    }
}
