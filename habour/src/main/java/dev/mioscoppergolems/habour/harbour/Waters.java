package dev.mioscoppergolems.habour.harbour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * What a boat with a copper golem aboard can sail through. Everything here only looks at chunks that are already
 * loaded and never loads one.
 */
public final class Waters {
	private Waters() {
	}

	public static boolean isLoaded(ServerLevel level, int x, int z) {
		return level.hasChunkAt(new BlockPos(x, level.getMinY() + 1, z));
	}

	private static boolean passable(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.getCollisionShape(level, pos).isEmpty();
	}

	private static boolean isWater(ServerLevel level, BlockPos pos) {
		return level.getFluidState(pos).is(FluidTags.WATER);
	}

	/**
	 * The y of the water surface in this column, within one block of {@code hint}: the lowest water block with no water
	 * above it. Null if there is none or the chunk is not loaded.
	 */
	public static @Nullable Integer surface(ServerLevel level, int x, int z, int hint) {
		if (!isLoaded(level, x, z)) {
			return null;
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int y = hint + 1; y >= hint - 1; y--) {
			pos.set(x, y, z);
			if (isWater(level, pos)) {
				pos.set(x, y + 1, z);
				if (!isWater(level, pos)) {
					return y;
				}
			}
		}
		return null;
	}

	/**
	 * Whether a boat can float at this surface block: water here, free of solid blocks next to it and room above for the
	 * golem aboard. Boats are about a block and a half wide, so a one-block channel is not enough.
	 */
	public static boolean canFloat(ServerLevel level, int x, int y, int z) {
		if (!isLoaded(level, x, z)) {
			return false;
		}
		BlockPos pos = new BlockPos(x, y, z);
		if (!isWater(level, pos) || isWater(level, pos.above()) || !passable(level, pos.above()) || !passable(level, pos.above(2))) {
			return false;
		}
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockPos side = pos.relative(direction);
			if (!isLoaded(level, side.getX(), side.getZ()) || !passable(level, side) || !passable(level, side.above())) {
				return false;
			}
		}
		return true;
	}

	/** The surface block of the nearest spot within {@code radius} where a boat can float, or null. */
	public static @Nullable BlockPos nearestFloat(ServerLevel level, BlockPos around, int radius) {
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				double distance = dx * dx + dz * dz;
				if (distance >= bestDistance) {
					continue;
				}
				for (int dy = 0; dy >= -3; dy--) {
					Integer y = surface(level, around.getX() + dx, around.getZ() + dz, around.getY() + dy);
					if (y != null && canFloat(level, around.getX() + dx, y, around.getZ() + dz)) {
						best = new BlockPos(around.getX() + dx, y, around.getZ() + dz);
						bestDistance = distance;
						break;
					}
				}
			}
		}
		return best;
	}

	/**
	 * A spot on dry land next to {@code near} where a golem can stand to reach a boat or the water's edge: solid ground,
	 * two free blocks above it and no water. The closest one within {@code radius} wins.
	 */
	public static @Nullable BlockPos standPoint(ServerLevel level, BlockPos near, int radius) {
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		BlockPos.MutableBlockPos feet = new BlockPos.MutableBlockPos();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				if (!isLoaded(level, near.getX() + dx, near.getZ() + dz)) {
					continue;
				}
				for (int dy = -2; dy <= 2; dy++) {
					feet.set(near.getX() + dx, near.getY() + dy, near.getZ() + dz);
					double distance = dx * dx + dz * dz + dy * dy * 0.5;
					if (distance >= bestDistance) {
						continue;
					}
					BlockPos below = feet.below();
					if (isStandable(level, feet, below)) {
						best = feet.immutable();
						bestDistance = distance;
					}
				}
			}
		}
		return best;
	}

	/**
	 * A spot to stand next to a cart on a track: solid ground, free above it, no water, and not a rail itself (a golem
	 * standing on the track would be in the train's way). The closest one within {@code radius} wins.
	 */
	public static @Nullable BlockPos standBeside(ServerLevel level, BlockPos near, int radius) {
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		BlockPos.MutableBlockPos feet = new BlockPos.MutableBlockPos();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				if (!isLoaded(level, near.getX() + dx, near.getZ() + dz)) {
					continue;
				}
				for (int dy = -2; dy <= 2; dy++) {
					feet.set(near.getX() + dx, near.getY() + dy, near.getZ() + dz);
					double distance = dx * dx + dz * dz + dy * dy * 0.5;
					if (distance >= bestDistance || distance < 1.0) {
						continue;
					}
					BlockPos below = feet.below();
					if (isStandable(level, feet, below) && !(level.getBlockState(feet).getBlock() instanceof net.minecraft.world.level.block.BaseRailBlock)
						&& !(level.getBlockState(below).getBlock() instanceof net.minecraft.world.level.block.BaseRailBlock)) {
						best = feet.immutable();
						bestDistance = distance;
					}
				}
			}
		}
		return best;
	}

	private static boolean isStandable(ServerLevel level, BlockPos feet, BlockPos below) {
		BlockState ground = level.getBlockState(below);
		return ground.isFaceSturdy(level, below, Direction.UP)
			&& !isWater(level, below)
			&& !isWater(level, feet)
			&& passable(level, feet)
			&& passable(level, feet.above());
	}
}
