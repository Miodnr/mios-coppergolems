package dev.mioscoppergolems.habour.harbour;

import dev.mioscoppergolems.habour.registry.HabourAttachments;
import dev.mioscoppergolems.habour.registry.HabourBlocks;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.entity.vehicle.boat.AbstractChestBoat;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The water side of a harbour. A harbour's berths are the stretches of water next to its mooring posts (within
 * {@link #RANGE} blocks of the kontor). A kontor without any mooring post uses the water around itself, so a first
 * harbour works with one boat and no posts.
 */
public final class Berths {
	/** How far from the kontor a mooring post still belongs to the harbour. */
	public static final int RANGE = 24;
	private static final int HEIGHT = 8;
	/** How far from its post a boat lies. */
	private static final int POST_REACH = 4;
	private static final int KONTOR_REACH = 8;
	/** Boats closer than this to a berth occupy it. */
	private static final double BOAT_SPACE = 2.6;
	/** A boat this close to an anchor counts as lying in the harbour. */
	private static final double IN_HARBOUR = 7.0;
	public static final int TAKEN_TICKS = 200;

	private Berths() {
	}

	/** The mooring posts of the harbour; the kontor itself if there is none. */
	public static List<BlockPos> anchors(ServerLevel level, BlockPos kontor) {
		List<BlockPos> posts = new ArrayList<>();
		for (int dx = -RANGE; dx <= RANGE; dx++) {
			for (int dz = -RANGE; dz <= RANGE; dz++) {
				if (!Waters.isLoaded(level, kontor.getX() + dx, kontor.getZ() + dz)) {
					continue;
				}
				for (int dy = -HEIGHT; dy <= HEIGHT; dy++) {
					BlockPos pos = kontor.offset(dx, dy, dz);
					if (level.getBlockState(pos).is(HabourBlocks.MOORING_POST)) {
						posts.add(pos);
					}
				}
			}
		}
		if (posts.isEmpty()) {
			posts.add(kontor);
		}
		return posts;
	}

	private static int reach(List<BlockPos> anchors, BlockPos kontor) {
		return anchors.size() == 1 && anchors.get(0).equals(kontor) ? KONTOR_REACH : POST_REACH;
	}

	/** Boats lying in the harbour that no captain has taken and nobody sits in. */
	public static List<AbstractChestBoat> freeBoats(ServerLevel level, BlockPos kontor, List<BlockPos> anchors) {
		Set<AbstractChestBoat> boats = new LinkedHashSet<>();
		int reach = reach(anchors, kontor);
		for (BlockPos anchor : anchors) {
			AABB area = new AABB(anchor).inflate(reach + IN_HARBOUR, 4.0, reach + IN_HARBOUR);
			for (AbstractChestBoat boat : level.getEntitiesOfClass(AbstractChestBoat.class, area, boat -> boat.isAlive())) {
				if (!isTaken(level, boat) && !boat.isVehicle() && boat.isInWater()) {
					boats.add(boat);
				}
			}
		}
		return new ArrayList<>(boats);
	}

	public static boolean isTaken(ServerLevel level, Entity boat) {
		Long until = boat.getAttached(HabourAttachments.BOAT_TAKEN_UNTIL);
		return until != null && until > level.getGameTime();
	}

	public static void take(ServerLevel level, Entity boat) {
		boat.setAttached(HabourAttachments.BOAT_TAKEN_UNTIL, level.getGameTime() + TAKEN_TICKS);
	}

	public static void release(Entity boat) {
		boat.removeAttached(HabourAttachments.BOAT_TAKEN_UNTIL);
	}

	/**
	 * A free spot to moor: water next to a post that no other boat lies on, and with dry land to stand on next to it. The
	 * spot closest to its post wins. {@code ignore} is the boat that wants to moor (it does not block itself).
	 */
	public static @Nullable BlockPos findBerth(ServerLevel level, BlockPos kontor, List<BlockPos> anchors, @Nullable Entity ignore) {
		int reach = reach(anchors, kontor);
		List<AbstractBoat> boats = new ArrayList<>();
		for (BlockPos anchor : anchors) {
			boats.addAll(level.getEntitiesOfClass(AbstractBoat.class, new AABB(anchor).inflate(reach + 3.0, 4.0, reach + 3.0), boat -> boat != ignore));
		}
		BlockPos best = null;
		double bestScore = Double.MAX_VALUE;
		for (BlockPos anchor : anchors) {
			for (int dx = -reach; dx <= reach; dx++) {
				for (int dz = -reach; dz <= reach; dz++) {
					double score = dx * dx + dz * dz;
					if (score >= bestScore) {
						continue;
					}
					for (int dy = 1; dy >= -3; dy--) {
						Integer y = Waters.surface(level, anchor.getX() + dx, anchor.getZ() + dz, anchor.getY() + dy);
						if (y == null || !Waters.canFloat(level, anchor.getX() + dx, y, anchor.getZ() + dz)) {
							continue;
						}
						BlockPos cell = new BlockPos(anchor.getX() + dx, y, anchor.getZ() + dz);
						if (isOccupied(cell, boats) || Waters.standPoint(level, cell, 3) == null) {
							break;
						}
						best = cell;
						bestScore = score;
						break;
					}
				}
			}
		}
		return best;
	}

	private static boolean isOccupied(BlockPos cell, List<AbstractBoat> boats) {
		Vec3 center = Vec3.atBottomCenterOf(cell);
		for (AbstractBoat boat : boats) {
			double dx = boat.getX() - center.x;
			double dz = boat.getZ() - center.z;
			if (dx * dx + dz * dz < BOAT_SPACE * BOAT_SPACE && Math.abs(boat.getY() - center.y) < 3.0) {
				return true;
			}
		}
		return false;
	}
}
