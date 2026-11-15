package dev.mioscoppergolems.habour.route;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** What sets a route off: its timer, or a redstone pulse on the kontor. */
public enum Trigger implements StringRepresentable {
	TIMER("timer"),
	REDSTONE("redstone");

	public static final Codec<Trigger> CODEC = StringRepresentable.fromEnum(Trigger::values);

	private final String key;

	Trigger(String key) {
		this.key = key;
	}

	@Override
	public String getSerializedName() {
		return this.key;
	}

	public Trigger next() {
		return values()[(this.ordinal() + 1) % values().length];
	}
}
