package com.thepearlking.stevespace.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Ground equipment that turns fuel items into propellant and pumps it into the tanks it touches
 * (and every tank connected to those). Right-click it holding the fuel.
 */
public class FuelLoaderBlock extends FacingBlock {

    public static final MapCodec<FuelLoaderBlock> CODEC = simpleCodec(FuelLoaderBlock::new);
    /** Water ice from other worlds: split into hydrogen and oxygen. */
    private static final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> PROPELLANT_ICE = net.minecraft.tags.ItemTags.create(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.thepearlking.stevespace.StevesSpace.MODID, "propellant_ice"));
    /** Largest group of connected tanks one loader reaches. */
    private static final int MAX_TANKS = 512;

    public FuelLoaderBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    /** Tonnes of propellant one item of this kind makes, or 0 if it isn't fuel. */
    public static double propellantFrom(ItemStack stack) {
        if (stack.is(Items.COAL_BLOCK)) {
            return 0.9;
        }
        if (stack.is(Items.COAL) || stack.is(Items.CHARCOAL)) {
            return 0.1;
        }
        if (stack.is(Items.BLAZE_POWDER)) {
            return 0.15;
        }
        if (stack.is(Items.LAVA_BUCKET)) {
            return 0.9;
        }
        // From the other worlds: Titan's methane, Io's sulphur, and ice from anywhere off Earth.
        if (stack.is(com.thepearlking.stevespace.registry.ModFluids.METHANE_BUCKET.get())) {
            return 1.5;
        }
        if (stack.getItem() == com.thepearlking.stevespace.registry.WorldBlocks.item("sulphur_crystals")) {
            return 0.35;
        }
        if (stack.is(PROPELLANT_ICE)) {
            return 0.4;
        }
        return 0;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        double perItem = propellantFrom(stack);
        if (perItem <= 0) {
            if (!level.isClientSide) {
                double[] totals = totals(connectedTanks(level, pos));
                player.displayClientMessage(Component.translatable("message.stevespace.fuel_loader.status",
                        String.format("%.1f", totals[0]), String.format("%.1f", totals[1])), true);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        List<PropellantTankBlockEntity> tanks = connectedTanks(level, pos);
        if (tanks.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.stevespace.fuel_loader.no_tanks"), true);
            return ItemInteractionResult.CONSUME;
        }
        // Use one item at a time; stop once the tanks can't take a whole item's worth.
        double room = tanks.stream().mapToDouble(t -> PropellantTankBlock.CAPACITY - t.propellant()).sum();
        int used = 0;
        int available = player.getAbilities().instabuild ? Integer.MAX_VALUE : stack.getCount();
        int wanted = (int) Math.floor(room / perItem + 1e-9);
        int toUse = Math.min(Math.min(available, wanted), 64);
        double toAdd = toUse * perItem;
        for (PropellantTankBlockEntity tank : tanks) {
            toAdd -= tank.fill(toAdd);
            if (toAdd <= 1e-9) {
                break;
            }
        }
        used = toUse;
        if (!player.getAbilities().instabuild) {
            if ((stack.is(Items.LAVA_BUCKET) || stack.is(com.thepearlking.stevespace.registry.ModFluids.METHANE_BUCKET.get())) && used > 0) {
                player.setItemInHand(hand, new ItemStack(Items.BUCKET));
            } else {
                stack.shrink(used);
            }
        }
        double[] totals = totals(tanks);
        player.displayClientMessage(Component.translatable("message.stevespace.fuel_loader.status",
                String.format("%.1f", totals[0]), String.format("%.1f", totals[1])), true);
        return ItemInteractionResult.CONSUME;
    }

    private static double[] totals(List<PropellantTankBlockEntity> tanks) {
        double stored = tanks.stream().mapToDouble(PropellantTankBlockEntity::propellant).sum();
        return new double[]{stored, tanks.size() * PropellantTankBlock.CAPACITY};
    }

    /**
     * The tanks of whatever the loader touches: a tank, any other part of a rocket, or the launch
     * pad a rocket stands on. Everything connected to those counts.
     */
    public static List<PropellantTankBlockEntity> connectedTanks(Level level, BlockPos loader) {
        List<PropellantTankBlockEntity> found = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        seen.add(loader);
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (Direction d : Direction.values()) {
            queue.add(loader.relative(d));
        }
        int limit = com.thepearlking.stevespace.config.SpaceConfig.MAX_VESSEL_BLOCKS.get();
        while (!queue.isEmpty() && seen.size() < limit && found.size() < MAX_TANKS) {
            BlockPos p = queue.poll();
            if (!seen.add(p)) {
                continue;
            }
            BlockState state = level.getBlockState(p);
            boolean pad = state.getBlock() == com.thepearlking.stevespace.registry.ModBlocks.LAUNCH_PAD.get();
            if (!pad && !com.thepearlking.stevespace.vessel.VesselAssembler.isVesselBlock(state)) {
                continue;
            }
            if (level.getBlockEntity(p) instanceof PropellantTankBlockEntity tank) {
                found.add(tank);
            }
            for (Direction d : Direction.values()) {
                BlockPos next = p.relative(d);
                // Pads only lead up into the rocket or across the pad, never down into the ground.
                if (!pad || d != Direction.DOWN) {
                    queue.add(next);
                }
            }
        }
        return found;
    }
}
