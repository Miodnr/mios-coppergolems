package dev.mioscoppergolems.habour.rail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import org.jspecify.annotations.Nullable;

/**
 * The track as a graph, read from the rail blocks as they are placed. Two rail blocks are connected when each has an
 * exit that leads to the other (straight, curved, up or down a slope). Junctions are not switched: a rail block with
 * three neighbours only connects to the two its shape points at. Only loaded chunks are looked at.
 */
public final class Rails {
	/** One way out of a rail block: a horizontal step, and whether it is the high end of a slope. */
	record Exit(int dx, int dz, boolean up) {
	}

	private static final int MAX_EXPANSIONS = 6000;

	public record Path(List<BlockPos> nodes, boolean complete) {
	}

	private Rails() {
	}

	static List<Exit> exits(RailShape shape) {
		return switch (shape) {
			case NORTH_SOUTH -> List.of(new Exit(0, -1, false), new Exit(0, 1, false));
			case EAST_WEST -> List.of(new Exit(1, 0, false), new Exit(-1, 0, false));
			case ASCENDING_EAST -> List.of(new Exit(1, 0, true), new Exit(-1, 0, false));
			case ASCENDING_WEST -> List.of(new Exit(-1, 0, true), new Exit(1, 0, false));
			case ASCENDING_NORTH -> List.of(new Exit(0, -1, true), new Exit(0, 1, false));
			case ASCENDING_SOUTH -> List.of(new Exit(0, 1, true), new Exit(0, -1, false));
			case SOUTH_EAST -> List.of(new Exit(0, 1, false), new Exit(1, 0, false));
			case SOUTH_WEST -> List.of(new Exit(0, 1, false), new Exit(-1, 0, false));
			case NORTH_WEST -> List.of(new Exit(0, -1, false), new Exit(-1, 0, false));
			case NORTH_EAST -> List.of(new Exit(0, -1, false), new Exit(1, 0, false));
		};
	}

	public static boolean isRail(ServerLevel level, BlockPos pos) {
		return level.isLoaded(pos) && level.getBlockState(pos).getBlock() instanceof BaseRailBlock;
	}

	private static @Nullable List<Exit> exitsAt(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos)) {
			return null;
		}
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof BaseRailBlock rail)) {
			return null;
		}
		return exits(state.getValue(rail.getShapeProperty()));
	}

	/** The rail blocks this rail block leads to. */
	public static List<BlockPos> neighbors(ServerLevel level, BlockPos pos) {
		List<Exit> mine = exitsAt(level, pos);
		if (mine == null) {
			return List.of();
		}
		List<BlockPos> result = new ArrayList<>(2);
		for (Exit exit : mine) {
			if (exit.up()) {
				BlockPos other = pos.offset(exit.dx(), 1, exit.dz());
				if (leadsBack(level, other, exit, false)) {
					result.add(other);
				}
			} else {
				BlockPos same = pos.offset(exit.dx(), 0, exit.dz());
				BlockPos lower = pos.offset(exit.dx(), -1, exit.dz());
				if (leadsBack(level, same, exit, false)) {
					result.add(same);
				} else if (leadsBack(level, lower, exit, true)) {
					result.add(lower);
				}
			}
		}
		return result;
	}

	/** Whether the rail at {@code other} has an exit pointing back where {@code exit} came from, high end or not. */
	private static boolean leadsBack(ServerLevel level, BlockPos other, Exit exit, boolean needsUp) {
		List<Exit> theirs = exitsAt(level, other);
		if (theirs == null) {
			return false;
		}
		for (Exit back : theirs) {
			if (back.dx() == -exit.dx() && back.dz() == -exit.dz() && back.up() == needsUp) {
				return true;
			}
		}
		return false;
	}

	/** The rail block a cart is on (or just above), or null if it is not on a track. */
	public static @Nullable BlockPos railAt(ServerLevel level, Entity cart) {
		BlockPos here = cart.blockPosition();
		if (isRail(level, here)) {
			return here;
		}
		if (isRail(level, here.below())) {
			return here.below();
		}
		return null;
	}

	/**
	 * A path along the track from {@code from} to {@code goal}. If the goal cannot be reached through loaded chunks, the
	 * path leads to the explored rail block closest to it (and is not complete).
	 */
	public static @Nullable Path path(ServerLevel level, BlockPos from, BlockPos goal) {
		Map<BlockPos, BlockPos> cameFrom = new HashMap<>();
		Map<BlockPos, Double> cost = new HashMap<>();
		Set<BlockPos> closed = new HashSet<>();
		PriorityQueue<double[]> open = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
		List<BlockPos> index = new ArrayList<>();
		cost.put(from, 0.0);
		index.add(from);
		open.add(new double[] {heuristic(from, goal), 0});
		BlockPos best = from;
		double bestH = heuristic(from, goal);
		int expansions = 0;
		while (!open.isEmpty() && expansions++ < MAX_EXPANSIONS) {
			double[] top = open.poll();
			BlockPos current = index.get((int) top[1]);
			if (!closed.add(current)) {
				continue;
			}
			double h = heuristic(current, goal);
			if (h < bestH) {
				bestH = h;
				best = current;
			}
			if (current.equals(goal)) {
				return new Path(rebuild(cameFrom, current), true);
			}
			for (BlockPos next : neighbors(level, current)) {
				double newCost = cost.get(current) + 1.0;
				if (closed.contains(next) || newCost >= cost.getOrDefault(next, Double.MAX_VALUE)) {
					continue;
				}
				cost.put(next, newCost);
				cameFrom.put(next, current);
				index.add(next);
				open.add(new double[] {newCost + heuristic(next, goal), index.size() - 1});
			}
		}
		return best.equals(from) ? null : new Path(rebuild(cameFrom, best), false);
	}

	private static double heuristic(BlockPos a, BlockPos b) {
		return Math.sqrt(a.distSqr(b));
	}

	private static List<BlockPos> rebuild(Map<BlockPos, BlockPos> cameFrom, BlockPos end) {
		List<BlockPos> nodes = new ArrayList<>();
		BlockPos at = end;
		nodes.add(at);
		while (cameFrom.containsKey(at)) {
			at = cameFrom.get(at);
			nodes.add(at);
		}
		Collections.reverse(nodes);
		return nodes;
	}

	/** The rail block nearest to a station that trains stop at: the closest rail within {@code range} blocks. */
	public static @Nullable BlockPos stopNear(ServerLevel level, BlockPos station, int range) {
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = -range; dx <= range; dx++) {
			for (int dz = -range; dz <= range; dz++) {
				for (int dy = -6; dy <= 6; dy++) {
					double distance = dx * dx + dz * dz + dy * dy;
					if (distance >= bestDistance) {
						continue;
					}
					pos.set(station.getX() + dx, station.getY() + dy, station.getZ() + dz);
					if (isRail(level, pos)) {
						best = pos.immutable();
						bestDistance = distance;
					}
				}
			}
		}
		return best;
	}
}
