package dev.mioscoppergolems.habour.rail;

import dev.mioscoppergolems.entity.CopperChestMinecart;
import dev.mioscoppergolems.habour.harbour.Berths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

/**
 * The trains of a station: copper chest minecarts standing on the track near it. A train is a lead cart with the carts
 * chained behind it (each chained to the one in front); a cart that is chained to nothing is a train of one, and can
 * be coupled onto another train by a conductor.
 */
public final class Trains {
	/** How far from the station carts count as belonging to it. */
	public static final int RANGE = 24;
	/** Single carts this close to a lead cart can be coupled to it. */
	public static final double COUPLE_RANGE = 8.0;
	public static final int MAX_CARTS = 5;

	/** A lead cart and the carts behind it, front to back. */
	public record Train(CopperChestMinecart lead, List<CopperChestMinecart> wagons) {
		public int slots() {
			int sum = this.lead.getContainerSize();
			for (CopperChestMinecart wagon : this.wagons) {
				sum += wagon.getContainerSize();
			}
			return sum;
		}

		public List<CopperChestMinecart> all() {
			List<CopperChestMinecart> all = new ArrayList<>();
			all.add(this.lead);
			all.addAll(this.wagons);
			return all;
		}
	}

	private Trains() {
	}

	/** Trains standing near the station that no conductor has taken and nobody sits in. */
	public static List<Train> free(ServerLevel level, BlockPos station) {
		AABB area = new AABB(station).inflate(RANGE, 6.0, RANGE);
		List<CopperChestMinecart> carts = level.getEntitiesOfClass(CopperChestMinecart.class, area, cart -> cart.isAlive() && cart.isOnRails());
		Map<CopperChestMinecart, CopperChestMinecart> follower = new HashMap<>();
		List<CopperChestMinecart> leads = new ArrayList<>();
		for (CopperChestMinecart cart : carts) {
			Entity holder = cart.getHolder();
			if (!cart.isChained()) {
				leads.add(cart);
			} else if (holder instanceof CopperChestMinecart ahead && carts.contains(ahead)) {
				follower.put(ahead, cart);
			}
			// A cart chained to a golem or a player (a courier cart) is not part of any train.
		}
		List<Train> trains = new ArrayList<>();
		for (CopperChestMinecart lead : leads) {
			List<CopperChestMinecart> wagons = new ArrayList<>();
			CopperChestMinecart at = lead;
			while (follower.containsKey(at) && wagons.size() < MAX_CARTS - 1) {
				at = follower.get(at);
				wagons.add(at);
			}
			Train train = new Train(lead, wagons);
			boolean free = true;
			for (CopperChestMinecart cart : train.all()) {
				free &= !Berths.isTaken(level, cart) && !cart.isVehicle();
			}
			if (free) {
				trains.add(train);
			}
		}
		return trains;
	}

	/**
	 * Turns a train around: the carts (front to back) are chained the other way round, so the last one leads. Every
	 * link keeps its own chain item, so no chain is made or lost.
	 */
	public static void reverse(List<CopperChestMinecart> carts) {
		int n = carts.size();
		if (n < 2) {
			return;
		}
		List<net.minecraft.world.item.ItemStack> links = new ArrayList<>();
		links.add(net.minecraft.world.item.ItemStack.EMPTY);
		for (int i = 1; i < n; i++) {
			links.add(carts.get(i).chain().copy());
		}
		for (int i = 1; i < n; i++) {
			carts.get(i).unchain(false);
		}
		for (int i = 0; i < n - 1; i++) {
			// The link between cart i and cart i+1 now belongs to cart i, which follows cart i+1.
			carts.get(i).setChain(links.get(i + 1));
			carts.get(i).chainTo(carts.get(i + 1));
		}
	}

	/** Single free carts near a lead cart that a conductor could couple to its train, nearest first. */
	public static List<CopperChestMinecart> spares(List<Train> trains, Train train) {
		List<CopperChestMinecart> spares = new ArrayList<>();
		for (Train other : trains) {
			if (other != train && other.wagons().isEmpty() && other.lead().distanceTo(train.lead()) <= COUPLE_RANGE) {
				spares.add(other.lead());
			}
		}
		spares.sort((a, b) -> Double.compare(a.distanceToSqr(train.lead()), b.distanceToSqr(train.lead())));
		return spares;
	}
}
