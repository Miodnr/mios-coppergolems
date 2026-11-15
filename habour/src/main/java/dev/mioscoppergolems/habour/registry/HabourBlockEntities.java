package dev.mioscoppergolems.habour.registry;

import dev.mioscoppergolems.habour.Habour;
import dev.mioscoppergolems.habour.kontor.KontorBlockEntity;
import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class HabourBlockEntities {
	public static final BlockEntityType<KontorBlockEntity> KONTOR = register("kontor", KontorBlockEntity::new, HabourBlocks.KONTOR, HabourBlocks.STATION);

	private HabourBlockEntities() {
	}

	private static <T extends BlockEntity> BlockEntityType<T> register(String name, BlockEntityType.BlockEntitySupplier<T> factory, Block... blocks) {
		return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Habour.id(name), new BlockEntityType<>(factory, Set.of(blocks)));
	}

	public static void init() {
	}
}
