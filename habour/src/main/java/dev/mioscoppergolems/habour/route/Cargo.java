package dev.mioscoppergolems.habour.route;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * One line of a route's cargo list: an item and how many of it to ship. An amount of {@link #ALL} means everything the
 * kontor has of it.
 */
public record Cargo(Item item, int amount) {
	public static final int ALL = 0;
	public static final int MAX_AMOUNT = 3456;

	public static final Codec<Cargo> CODEC = RecordCodecBuilder.create(
		instance -> instance.group(
			BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(Cargo::item),
			Codec.INT.fieldOf("amount").forGetter(Cargo::amount)
		).apply(instance, Cargo::new)
	);

	public boolean isAll() {
		return this.amount <= ALL;
	}

	public Cargo withAmount(int amount) {
		return new Cargo(this.item, Math.max(ALL, Math.min(MAX_AMOUNT, amount)));
	}

	public boolean matches(ItemStack stack) {
		return stack.is(this.item);
	}
}
