package dev.mioscoppergolems.habour.captain;

import dev.mioscoppergolems.golem.Inventories;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * What a voyage carries its cargo in: one chest boat, or the carts of a train, lead cart first. Every part is an entity
 * with a container. Dockhands load and unload one part at a time, walking to the part they work on.
 */
public record Vessel(Entity lead, List<Entity> parts) {
	/** The vessel of a voyage, or null if its lead is gone. Carts that were lost drop out of the train. */
	public static @Nullable Vessel of(ServerLevel level, Voyage voyage) {
		if (!(level.getEntity(voyage.boat) instanceof Entity lead) || !lead.isAlive() || !(lead instanceof Container)) {
			return null;
		}
		List<Entity> parts = new ArrayList<>();
		parts.add(lead);
		for (java.util.UUID id : voyage.wagons) {
			if (level.getEntity(id) instanceof Entity wagon && wagon.isAlive() && wagon instanceof Container) {
				parts.add(wagon);
			}
		}
		return new Vessel(lead, parts);
	}

	public static Container hold(Entity part) {
		return (Container) part;
	}

	public boolean isEmpty() {
		for (Entity part : this.parts) {
			if (!hold(part).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	/** The first part that still has room, or null if the vessel is full. */
	public @Nullable Entity partWithRoom() {
		for (Entity part : this.parts) {
			Container container = hold(part);
			for (int slot = 0; slot < container.getContainerSize(); slot++) {
				ItemStack stack = container.getItem(slot);
				if (stack.isEmpty() || stack.getCount() < stack.getMaxStackSize()) {
					return part;
				}
			}
		}
		return null;
	}

	/** The first part that holds anything, or null. */
	public @Nullable Entity partWithItems() {
		for (Entity part : this.parts) {
			if (!hold(part).isEmpty()) {
				return part;
			}
		}
		return null;
	}

	/** Puts the stack into the vessel, the first part that takes it first; returns what did not fit. */
	public ItemStack insert(ItemStack stack) {
		ItemStack rest = stack;
		for (Entity part : this.parts) {
			if (rest.isEmpty()) {
				break;
			}
			rest = Inventories.insert(hold(part), rest);
		}
		return rest;
	}

	/** Total number of slots of all parts. */
	public int slots() {
		int sum = 0;
		for (Entity part : this.parts) {
			sum += hold(part).getContainerSize();
		}
		return sum;
	}
}
