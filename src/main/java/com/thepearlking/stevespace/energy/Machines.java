package com.thepearlking.stevespace.energy;

import com.thepearlking.stevespace.registry.ModBlockEntities;
import com.thepearlking.stevespace.registry.ModItems;
import com.thepearlking.stevespace.registry.WorldBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The basic machines: coal generator, electric furnace and crusher. Each has a small inventory
 * with a screen; hoppers put into the input and take from the output.
 */
public final class Machines {

    private Machines() {
    }

    public enum Kind {
        COAL_GENERATOR, ELECTRIC_FURNACE, CRUSHER;

        public int slots() {
            return this == COAL_GENERATOR ? 1 : 2;
        }
    }

    /** Power made while burning, per tick; a piece of coal (1600 ticks) gives 64,000. */
    public static final int COAL_POWER = 40;
    public static final int FURNACE_USE = 20;
    public static final int FURNACE_TIME = 100;
    public static final int CRUSHER_USE = 30;
    public static final int CRUSHER_TIME = 160;

    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    // -------------------------------------------------------------------------------------------

    public static class MachineBlock extends PowerBlocks.PowerBlock {
        private final Kind kind;

        public MachineBlock(Properties properties, Kind kind) {
            super(properties);
            this.kind = kind;
            registerDefaultState(defaultBlockState().setValue(LIT, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            super.createBlockStateDefinition(builder);
            builder.add(LIT);
        }

        @Override
        protected BlockEntityType<? extends PowerBlockEntity> type() {
            return switch (kind) {
                case COAL_GENERATOR -> ModBlockEntities.COAL_GENERATOR.get();
                case ELECTRIC_FURNACE -> ModBlockEntities.ELECTRIC_FURNACE.get();
                case CRUSHER -> ModBlockEntities.CRUSHER.get();
            };
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (!level.isClientSide && player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof MachineEntity machine) {
                sp.openMenu(machine, buf -> {
                    buf.writeBlockPos(pos);
                    buf.writeEnum(kind);
                });
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        @Override
        protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
            if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof MachineEntity machine) {
                for (int i = 0; i < machine.items.getSlots(); i++) {
                    Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), machine.items.getStackInSlot(i));
                }
            }
            super.onRemove(state, level, pos, newState, moved);
        }
    }

    // -------------------------------------------------------------------------------------------

    /** A machine with an inventory and a screen. Progress means burn time left for the generator. */
    public abstract static class MachineEntity extends PowerBlockEntity implements PowerBlocks.Ticking, MenuProvider {
        public final Kind kind;
        final ItemStackHandler items;
        private final LazyOptional<IItemHandler> automation;
        int progress;
        int total;

        /** Synced to the screen as shorts: energy and capacity are split in two halves. */
        public final ContainerData data = new ContainerData() {
            @Override
            public int get(int i) {
                return switch (i) {
                    case 0 -> buffer.getEnergyStored() & 0xFFFF;
                    case 1 -> buffer.getEnergyStored() >>> 16;
                    case 2 -> buffer.getMaxEnergyStored() & 0xFFFF;
                    case 3 -> buffer.getMaxEnergyStored() >>> 16;
                    case 4 -> progress;
                    default -> total;
                };
            }

            @Override
            public void set(int i, int value) {
                // Server side only reads.
            }

            @Override
            public int getCount() {
                return 6;
            }
        };

        protected MachineEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, Kind kind, int capacity, int maxIn, int maxOut) {
            super(type, pos, state, capacity, maxIn, maxOut);
            this.kind = kind;
            this.items = new ItemStackHandler(kind.slots()) {
                @Override
                protected void onContentsChanged(int slot) {
                    setChanged();
                }

                @Override
                public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                    return slot == 0 && accepts(stack);
                }
            };
            this.automation = LazyOptional.of(() -> new IItemHandler() {
                @Override
                public int getSlots() {
                    return items.getSlots();
                }

                @Override
                public @NotNull ItemStack getStackInSlot(int slot) {
                    return items.getStackInSlot(slot);
                }

                @Override
                public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
                    return slot == 0 ? items.insertItem(0, stack, simulate) : stack;
                }

                @Override
                public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
                    // Outputs; for the generator, an empty bucket left by lava.
                    boolean out = slot == 1 || kind == Kind.COAL_GENERATOR && !accepts(items.getStackInSlot(0));
                    return out ? items.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
                }

                @Override
                public int getSlotLimit(int slot) {
                    return items.getSlotLimit(slot);
                }

                @Override
                public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                    return items.isItemValid(slot, stack);
                }
            });
        }

        /** Whether the input slot takes this. */
        protected abstract boolean accepts(ItemStack stack);

        public IItemHandler items() {
            return items;
        }

        protected void setLit(Level level, BlockPos pos, boolean lit) {
            BlockState state = getBlockState();
            if (state.hasProperty(LIT) && state.getValue(LIT) != lit) {
                level.setBlock(pos, state.setValue(LIT, lit), Block.UPDATE_ALL);
            }
        }

        @Override
        public Component getDisplayName() {
            return getBlockState().getBlock().getName();
        }

        @Override
        public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
            return new MachineMenu(id, inventory, this);
        }

        @Override
        public Component status() {
            return PowerBlocks.stored(this);
        }

        @Override
        public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            if (cap == ForgeCapabilities.ITEM_HANDLER) {
                return automation.cast();
            }
            return super.getCapability(cap, side);
        }

        @Override
        public void invalidateCaps() {
            super.invalidateCaps();
            automation.invalidate();
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            tag.put("items", items.serializeNBT(registries));
            tag.putInt("progress", progress);
            tag.putInt("total", total);
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            if (tag.contains("items")) {
                items.deserializeNBT(registries, tag.getCompound("items"));
            }
            progress = tag.getInt("progress");
            total = tag.getInt("total");
        }
    }

    // Coal generator --------------------------------------------------------------------------------

    /** Burns anything a furnace burns. Only takes a new piece once there's room for what it makes. */
    public static class CoalGeneratorEntity extends MachineEntity {
        public CoalGeneratorEntity(BlockPos pos, BlockState state) {
            super(ModBlockEntities.COAL_GENERATOR.get(), pos, state, Kind.COAL_GENERATOR, 40_000, 0, 2_000);
        }

        @Override
        public Role role() {
            return Role.SOURCE;
        }

        @Override
        protected boolean accepts(ItemStack stack) {
            return ForgeHooks.getBurnTime(stack, null) > 0;
        }

        @Override
        public void serverTick(ServerLevel level, BlockPos pos) {
            if (progress > 0) {
                progress--;
                buffer.add(COAL_POWER);
            }
            if (progress <= 0 && buffer.getEnergyStored() <= buffer.getMaxEnergyStored() - COAL_POWER * 20) {
                ItemStack fuel = items.getStackInSlot(0);
                int burn = ForgeHooks.getBurnTime(fuel, null);
                if (burn > 0) {
                    progress = total = burn;
                    ItemStack left = fuel.getCraftingRemainingItem();
                    if (fuel.getCount() == 1 && !left.isEmpty()) {
                        items.setStackInSlot(0, left);
                    } else {
                        items.extractItem(0, 1, false);
                    }
                }
            }
            setLit(level, pos, progress > 0);
            PowerGrid.distribute(level, pos, this);
            if (level.getGameTime() % 40 == 0) {
                setChanged();
            }
        }
    }

    // Processing machines ---------------------------------------------------------------------------

    /** Takes one input, uses power for a while, puts the result in the output slot. */
    public abstract static class ProcessorEntity extends MachineEntity {
        private final int use;
        private final int time;

        protected ProcessorEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, Kind kind, int use, int time) {
            super(type, pos, state, kind, 4_000, 500, 0);
            this.use = use;
            this.time = time;
        }

        @Override
        public Role role() {
            return Role.SINK;
        }

        /** What one of {@code input} turns into, or empty. */
        protected abstract ItemStack result(ServerLevel level, ItemStack input);

        @Override
        protected boolean accepts(ItemStack stack) {
            return level instanceof ServerLevel server ? !result(server, stack).isEmpty() : true;
        }

        @Override
        public void serverTick(ServerLevel level, BlockPos pos) {
            ItemStack in = items.getStackInSlot(0);
            ItemStack result = in.isEmpty() ? ItemStack.EMPTY : result(level, in);
            ItemStack out = items.getStackInSlot(1);
            boolean fits = !result.isEmpty() && (out.isEmpty()
                    || ItemStack.isSameItemSameComponents(out, result) && out.getCount() + result.getCount() <= out.getMaxStackSize());
            boolean running = false;
            if (!fits) {
                progress = 0;
            } else {
                total = time;
                if (buffer.use(use)) {
                    running = true;
                    if (++progress >= total) {
                        progress = 0;
                        items.extractItem(0, 1, false);
                        if (out.isEmpty()) {
                            items.setStackInSlot(1, result.copy());
                        } else {
                            out.grow(result.getCount());
                            items.setStackInSlot(1, out);
                        }
                    }
                }
            }
            setLit(level, pos, running);
            if (level.getGameTime() % 40 == 0) {
                setChanged();
            }
        }
    }

    /** Smelts like a furnace, twice as fast, on power. */
    public static class ElectricFurnaceEntity extends ProcessorEntity {
        public ElectricFurnaceEntity(BlockPos pos, BlockState state) {
            super(ModBlockEntities.ELECTRIC_FURNACE.get(), pos, state, Kind.ELECTRIC_FURNACE, FURNACE_USE, FURNACE_TIME);
        }

        @Override
        protected ItemStack result(ServerLevel level, ItemStack input) {
            SingleRecipeInput recipeInput = new SingleRecipeInput(input);
            return level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, recipeInput, level)
                    .map(r -> r.value().assemble(recipeInput, level.registryAccess())).orElse(ItemStack.EMPTY);
        }
    }

    /** Grinds ores into two dusts (raw ore: one, sometimes two), and stone down to gravel and sand. */
    public static class CrusherEntity extends ProcessorEntity {
        public CrusherEntity(BlockPos pos, BlockState state) {
            super(ModBlockEntities.CRUSHER.get(), pos, state, Kind.CRUSHER, CRUSHER_USE, CRUSHER_TIME);
        }

        @Override
        protected ItemStack result(ServerLevel level, ItemStack input) {
            return crushed(input, level.random.nextInt(3) == 0);
        }
    }

    /** The crusher's table; {@code lucky} gives raw ore its occasional second dust. */
    public static ItemStack crushed(ItemStack in, boolean lucky) {
        Item iron = ModItems.IRON_DUST.get();
        Item gold = ModItems.GOLD_DUST.get();
        Item copper = ModItems.COPPER_DUST.get();
        if (in.is(ItemTags.IRON_ORES)) {
            return new ItemStack(iron, 2);
        }
        if (in.is(ItemTags.GOLD_ORES)) {
            return new ItemStack(gold, 2);
        }
        if (in.is(ItemTags.COPPER_ORES)) {
            return new ItemStack(copper, 6);
        }
        int raw = lucky ? 2 : 1;
        if (in.is(Items.RAW_IRON) || in.is(WorldBlocks.item("hematite")) || in.is(WorldBlocks.item("magnetite"))) {
            return new ItemStack(iron, raw);
        }
        if (in.is(Items.RAW_GOLD)) {
            return new ItemStack(gold, raw);
        }
        if (in.is(Items.RAW_COPPER)) {
            return new ItemStack(copper, raw);
        }
        if (in.is(Items.IRON_INGOT)) {
            return new ItemStack(iron);
        }
        if (in.is(Items.GOLD_INGOT)) {
            return new ItemStack(gold);
        }
        if (in.is(Items.COPPER_INGOT)) {
            return new ItemStack(copper);
        }
        if (in.is(Items.COBBLESTONE) || in.is(Items.STONE)) {
            return new ItemStack(Items.GRAVEL);
        }
        if (in.is(Items.GRAVEL)) {
            return new ItemStack(Items.SAND);
        }
        if (in.is(Items.BLAZE_ROD)) {
            return new ItemStack(Items.BLAZE_POWDER, 4);
        }
        if (in.is(Items.BONE)) {
            return new ItemStack(Items.BONE_MEAL, 5);
        }
        return ItemStack.EMPTY;
    }
}
