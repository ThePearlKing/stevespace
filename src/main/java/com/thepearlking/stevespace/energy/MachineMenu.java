package com.thepearlking.stevespace.energy;

import com.thepearlking.stevespace.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

/** The screen side of a machine: its slots, the player's inventory, and power/progress readings. */
public class MachineMenu extends AbstractContainerMenu {

    public final Machines.Kind kind;
    private final BlockPos pos;
    private final ContainerData data;

    /** Server side, from the machine itself. */
    MachineMenu(int id, Inventory inventory, Machines.MachineEntity machine) {
        this(id, inventory, machine.kind, machine.getBlockPos(), machine.items(), machine.data);
    }

    /** Client side, from what the server sent. */
    public MachineMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, buf.readBlockPos(), buf.readEnum(Machines.Kind.class));
    }

    private MachineMenu(int id, Inventory inventory, BlockPos pos, Machines.Kind kind) {
        this(id, inventory, kind, pos, new ItemStackHandler(kind.slots()) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return slot == 0;
            }
        }, new SimpleContainerData(6));
    }

    private MachineMenu(int id, Inventory inventory, Machines.Kind kind, BlockPos pos, IItemHandler items, ContainerData data) {
        super(ModMenus.MACHINE.get(), id);
        this.kind = kind;
        this.pos = pos;
        this.data = data;
        if (kind == Machines.Kind.COAL_GENERATOR) {
            addSlot(new SlotItemHandler(items, 0, 80, 44));
        } else {
            addSlot(new SlotItemHandler(items, 0, 56, 35));
            addSlot(new SlotItemHandler(items, 1, 116, 35));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 142));
        }
        addDataSlots(data);
    }

    public int energy() {
        return (data.get(0) & 0xFFFF) | (data.get(1) & 0xFFFF) << 16;
    }

    public int capacity() {
        return (data.get(2) & 0xFFFF) | (data.get(3) & 0xFFFF) << 16;
    }

    public int progress() {
        return data.get(4) & 0xFFFF;
    }

    public int total() {
        return data.get(5) & 0xFFFF;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        int machineSlots = kind.slots();
        if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, 1, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return before;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64
                && player.level().getBlockEntity(pos) instanceof Machines.MachineEntity;
    }
}
