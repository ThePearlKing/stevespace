package com.thepearlking.stevespace.registry;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The ground of the other worlds: rocks, dusts, ices and ores, one set per world, listed in
 * {@link WorldBlockTable} (generated from tools/worlds.py). World generators look blocks up here
 * by name.
 */
public final class WorldBlocks {

    private static final Map<String, RegistryObject<Block>> BLOCKS = new LinkedHashMap<>();
    private static final Map<String, RegistryObject<Item>> ITEMS = new LinkedHashMap<>();

    private WorldBlocks() {
    }

    /** Called once while the mod's blocks are being declared. */
    static void init() {
        WorldBlockTable.registerAll();
    }

    static void add(String id, Supplier<Block> block) {
        BLOCKS.put(id, ModBlocks.registerWorld(id, block));
    }

    static void addItem(String id) {
        ITEMS.put(id, ModItems.ITEMS.register(id, () -> new Item(new Item.Properties())));
    }

    public static Block get(String id) {
        RegistryObject<Block> block = BLOCKS.get(id);
        if (block == null) {
            throw new IllegalArgumentException("No world block " + id);
        }
        return block.get();
    }

    public static BlockState state(String id) {
        return get(id).defaultBlockState();
    }

    public static Item item(String id) {
        return ITEMS.get(id).get();
    }

    public static Collection<RegistryObject<Block>> blocks() {
        return BLOCKS.values();
    }

    public static Collection<RegistryObject<Item>> items() {
        return ITEMS.values();
    }

    // ---------------------------------------------------------------------------------------
    // Kinds of ground
    // ---------------------------------------------------------------------------------------

    private static BlockBehaviour.Properties base(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color);
    }

    /** Solid rock: needs a pickaxe. */
    static Supplier<Block> stone(MapColor color, float hardness) {
        return () -> new Block(base(color).strength(hardness, 6f).requiresCorrectToolForDrops().sound(SoundType.STONE));
    }

    /** Loose ground: dust, sand, ash. Dug by hand or shovel. */
    static Supplier<Block> soil(MapColor color, float hardness) {
        return () -> new Block(base(color).strength(hardness).sound(SoundType.SAND));
    }

    /** Ices: slippery, ring like glass. */
    static Supplier<Block> ice(MapColor color, float hardness) {
        return () -> new Block(base(color).strength(hardness).friction(0.98f).sound(SoundType.GLASS).requiresCorrectToolForDrops());
    }

    /** Natural glass: hard and brittle-sounding. */
    static Supplier<Block> glass(MapColor color, float hardness) {
        return () -> new Block(base(color).strength(hardness, 8f).sound(SoundType.GLASS).requiresCorrectToolForDrops());
    }

    /** Rock that gives off its own light. */
    static Supplier<Block> glow(MapColor color, float hardness) {
        return () -> new Block(base(color).strength(hardness, 6f).lightLevel(s -> 9).requiresCorrectToolForDrops()
                .sound(SoundType.STONE));
    }

    /** Hot volcanic rock: glows and burns whoever stands on it. */
    static Supplier<Block> vent(MapColor color, float hardness) {
        return () -> new HotRock(base(color).strength(hardness, 6f).lightLevel(s -> 7).requiresCorrectToolForDrops()
                .sound(SoundType.STONE).emissiveRendering((s, l, p) -> true));
    }

    /** Io's lava vent: a geyser of molten rock (see GeyserBlock), glowing in its throat. */
    static Supplier<Block> lavaVent(MapColor color, float hardness) {
        return () -> new com.thepearlking.stevespace.block.GeyserBlock(base(color).strength(hardness, 6f).lightLevel(s -> 10)
                .requiresCorrectToolForDrops().sound(SoundType.BASALT), com.thepearlking.stevespace.block.GeyserBlock.LAVA);
    }

    /** Ore: rock with a mineral in it; needs at least a stone pickaxe. */
    static Supplier<Block> ore(MapColor color, float hardness) {
        return () -> new Block(base(color).strength(hardness, 6f).requiresCorrectToolForDrops().sound(SoundType.STONE));
    }

    /** A geyser vent (see GeyserBlock). Triton's (grey on the map) carry dark dust. */
    static Supplier<Block> geyser(MapColor color, float hardness) {
        return () -> new com.thepearlking.stevespace.block.GeyserBlock(base(color).strength(hardness, 6f)
                .requiresCorrectToolForDrops().sound(SoundType.GLASS),
                color == MapColor.TERRACOTTA_GRAY ? com.thepearlking.stevespace.block.GeyserBlock.DARK : com.thepearlking.stevespace.block.GeyserBlock.ICE);
    }

    /** Like a magma block: standing on it burns unless you sneak or resist fire. */
    private static final class HotRock extends Block {

        HotRock(Properties properties) {
            super(properties);
        }

        @Override
        public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
            if (!entity.isSteppingCarefully() && entity instanceof net.minecraft.world.entity.LivingEntity) {
                entity.hurt(level.damageSources().hotFloor(), 1.0F);
            }
            super.stepOn(level, pos, state, entity);
        }
    }
}
