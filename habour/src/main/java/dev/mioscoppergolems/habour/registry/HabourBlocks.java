package dev.mioscoppergolems.habour.registry;

import dev.mioscoppergolems.habour.Habour;
import dev.mioscoppergolems.habour.dock.MooringPostBlock;
import dev.mioscoppergolems.habour.kontor.KontorBlock;
import dev.mioscoppergolems.habour.kontor.StationBlock;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public final class HabourBlocks {
	/** The harbour's warehouse and office: holds the goods and the routes. */
	public static final Block KONTOR = register(
		"kontor", KontorBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD).ignitedByLava()
	);
	/** The railway's warehouse and office: like the kontor, but trains stop at it. */
	public static final Block STATION = register(
		"station", StationBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.5F).sound(SoundType.COPPER)
	);
	/** Marks where boats moor. */
	public static final Block MOORING_POST = register(
		"mooring_post", MooringPostBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F).sound(SoundType.WOOD).noOcclusion().ignitedByLava()
	);

	private HabourBlocks() {
	}

	private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Habour.id(name));
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Habour.id(name));
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		return block;
	}

	public static void init() {
	}
}
