package dev.mioscoppergolems.habour.captain;

import dev.mioscoppergolems.habour.harbour.Waters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Sails a boat over open water towards a goal. Water is only searched where chunks are loaded (a sailing boat keeps the
 * chunks around it loaded), so the pilot plans in a window around the boat: an A* search over the water, ending at the
 * goal if it is inside the window and otherwise at the spot closest to it. Bays that lead nowhere are remembered as
 * dead zones and avoided from then on.
 * <p>
 * The boat is steered like {@code RideBehavior} does it: it is turned and pushed along its heading every tick.
 */
public final class BoatPilot {
	public enum Result {
		SAILING,
		ARRIVED,
		/** No way through: the pilot has tried everything it knows. */
		STUCK
	}

	private static final double TURN = 5.0;
	private static final double PUSH = 0.045;
	private static final double CRUISE = 0.30;
	private static final double ARRIVE = 1.3;
	private static final int REPLAN_TICKS = 100;
	private static final int MAX_EXPANSIONS = 9000;
	private static final int STALL_CHECK = 20;
	private static final int MAX_STALLS = 12;
	private static final int PLAN_RADIUS = 56;
	private static final double DEAD_RADIUS = 7.0;
	private static final int MAX_DEAD_ZONES = 14;
	private static final int NO_GAIN_TICKS = 700;

	private record Dead(double x, double z) {
	}

	private record Node(int x, int y, int z, double g, double f, @Nullable Node parent) {
	}

	private List<Vec3> path = new ArrayList<>();
	private int index;
	private long lastPlan = Long.MIN_VALUE / 2;
	private final List<Dead> dead = new ArrayList<>();
	private Vec3 stallPos = Vec3.ZERO;
	private int stallTicks;
	private int stalls;
	private int emptyPlans;
	private double bestGoalDistance = Double.MAX_VALUE;
	private int noGainTicks;
	private @Nullable Vec3 lastGoal;

	public void reset() {
		this.path = new ArrayList<>();
		this.index = 0;
		this.lastPlan = Long.MIN_VALUE / 2;
		this.dead.clear();
		this.stallTicks = 0;
		this.stalls = 0;
		this.emptyPlans = 0;
		this.bestGoalDistance = Double.MAX_VALUE;
		this.noGainTicks = 0;
		this.lastGoal = null;
	}

	/** Forgets the current path but keeps what it knows about dead ends (the goal changed a little). */
	public void replan() {
		this.lastPlan = Long.MIN_VALUE / 2;
	}

	/**
	 * One tick of sailing.
	 *
	 * @param exact whether {@code goal} is a verified spot to moor; the boat only arrives at exact goals
	 */
	public Result step(ServerLevel level, AbstractBoat boat, Vec3 goal, boolean exact) {
		long now = level.getGameTime();
		if (this.lastGoal == null || this.lastGoal.distanceToSqr(goal) > 4.0) {
			// A new goal: plan again, keep the dead ends.
			this.lastGoal = goal;
			this.lastPlan = Long.MIN_VALUE / 2;
			this.bestGoalDistance = Double.MAX_VALUE;
			this.noGainTicks = 0;
		}
		double distance = horizontal(boat.position(), goal);
		if (exact && distance < ARRIVE) {
			return Result.ARRIVED;
		}
		if (distance < this.bestGoalDistance - 2.0) {
			this.bestGoalDistance = distance;
			this.noGainTicks = 0;
		} else if (++this.noGainTicks > NO_GAIN_TICKS) {
			// Sailing about without getting any closer: this part of the coast is a dead end.
			this.noGainTicks = 0;
			this.markDead(boat.position());
			this.lastPlan = Long.MIN_VALUE / 2;
			if (this.dead.size() > MAX_DEAD_ZONES) {
				return Result.STUCK;
			}
		}

		if (this.path.isEmpty() || now - this.lastPlan >= REPLAN_TICKS || this.index >= this.path.size()) {
			this.plan(level, boat, goal);
			this.lastPlan = now;
			if (this.path.isEmpty()) {
				if (++this.emptyPlans > 6) {
					return Result.STUCK;
				}
				this.brake(boat);
				return Result.SAILING;
			}
			this.emptyPlans = 0;
		}

		this.checkStall(boat);
		if (this.stalls > MAX_STALLS) {
			return Result.STUCK;
		}

		while (this.index < this.path.size() - 1 && horizontal(boat.position(), this.path.get(this.index)) < 1.8) {
			this.index++;
		}
		Vec3 aim = this.path.get(Math.min(this.index + 2, this.path.size() - 1));
		double speedLimit = CRUISE;
		if (exact) {
			speedLimit = Math.min(CRUISE, 0.05 + distance * 0.04);
		}
		this.steer(boat, aim, speedLimit);
		return Result.SAILING;
	}

	private void checkStall(AbstractBoat boat) {
		if (++this.stallTicks < STALL_CHECK) {
			return;
		}
		this.stallTicks = 0;
		if (horizontal(boat.position(), this.stallPos) < 0.7) {
			this.stalls++;
			// Pushed against a shore or a bridge: remember the spot just ahead and find another way.
			Vec3 heading = Vec3.directionFromRotation(0.0F, boat.getYRot());
			this.markDead(boat.position().add(heading.scale(3.0)));
			this.lastPlan = Long.MIN_VALUE / 2;
		} else {
			this.stalls = Math.max(0, this.stalls - 1);
		}
		this.stallPos = boat.position();
	}

	private void markDead(Vec3 at) {
		for (Dead zone : this.dead) {
			if (Math.abs(zone.x - at.x) < 3.0 && Math.abs(zone.z - at.z) < 3.0) {
				return;
			}
		}
		this.dead.add(new Dead(at.x, at.z));
	}

	private boolean isDead(int x, int z, Vec3 goal) {
		if (Math.abs(goal.x - x) < 14.0 && Math.abs(goal.z - z) < 14.0) {
			return false;
		}
		for (Dead zone : this.dead) {
			double dx = zone.x - x;
			double dz = zone.z - z;
			if (dx * dx + dz * dz < DEAD_RADIUS * DEAD_RADIUS) {
				return true;
			}
		}
		return false;
	}

	// --- planning ---

	private static long key(int x, int z) {
		return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
	}

	private void plan(ServerLevel level, AbstractBoat boat, Vec3 goal) {
		BlockPos here = boat.blockPosition();
		Integer startY = Waters.surface(level, here.getX(), here.getZ(), here.getY());
		BlockPos start;
		if (startY != null) {
			start = new BlockPos(here.getX(), startY, here.getZ());
		} else {
			start = Waters.nearestFloat(level, here, 4);
			if (start == null) {
				this.path = new ArrayList<>();
				return;
			}
		}
		Map<Long, Integer> floats = new HashMap<>();
		Map<Long, Node> best = new HashMap<>();
		PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.f, b.f));
		Node first = new Node(start.getX(), start.getY(), start.getZ(), 0.0, heuristic(start.getX(), start.getZ(), goal), null);
		open.add(first);
		best.put(key(start.getX(), start.getZ()), first);
		Node closest = first;
		double closestH = first.f;
		Node reached = null;
		int expansions = 0;
		int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
		while (!open.isEmpty() && expansions < MAX_EXPANSIONS) {
			Node node = open.poll();
			Node known = best.get(key(node.x, node.z));
			if (known != node) {
				continue;
			}
			expansions++;
			double h = heuristic(node.x, node.z, goal);
			if (h < closestH) {
				closest = node;
				closestH = h;
			}
			if (h < 1.2) {
				reached = node;
				break;
			}
			for (int[] step : steps) {
				int nx = node.x + step[0];
				int nz = node.z + step[1];
				if (Math.abs(nx - start.getX()) > PLAN_RADIUS || Math.abs(nz - start.getZ()) > PLAN_RADIUS || this.isDead(nx, nz, goal)) {
					continue;
				}
				Integer ny = this.floatAt(level, floats, nx, nz, node.y);
				if (ny == null) {
					continue;
				}
				if (step[0] != 0 && step[1] != 0
					&& (this.floatAt(level, floats, node.x + step[0], node.z, node.y) == null || this.floatAt(level, floats, node.x, node.z + step[1], node.y) == null)) {
					continue;
				}
				double g = node.g + (step[0] != 0 && step[1] != 0 ? 1.414 : 1.0);
				Node old = best.get(key(nx, nz));
				if (old != null && old.g <= g) {
					continue;
				}
				Node next = new Node(nx, ny, nz, g, g + heuristic(nx, nz, goal), node);
				best.put(key(nx, nz), next);
				open.add(next);
			}
		}
		Node end = reached != null ? reached : closest;
		double startH = heuristic(start.getX(), start.getZ(), goal);
		if (reached == null && closestH > startH - 3.0) {
			// Nothing in reach is any closer than where the boat is.
			this.path = new ArrayList<>();
			return;
		}
		List<Vec3> points = new ArrayList<>();
		for (Node node = end; node != null; node = node.parent) {
			points.add(new Vec3(node.x + 0.5, node.y, node.z + 0.5));
		}
		Collections.reverse(points);
		this.path = points;
		this.index = 0;
	}

	private @Nullable Integer floatAt(ServerLevel level, Map<Long, Integer> memo, int x, int z, int hintY) {
		long key = key(x, z);
		if (memo.containsKey(key)) {
			return memo.get(key);
		}
		Integer y = Waters.surface(level, x, z, hintY);
		if (y != null && !Waters.canFloat(level, x, y, z)) {
			y = null;
		}
		memo.put(key, y);
		return y;
	}

	private static double heuristic(int x, int z, Vec3 goal) {
		double dx = goal.x - (x + 0.5);
		double dz = goal.z - (z + 0.5);
		return Math.sqrt(dx * dx + dz * dz);
	}

	// --- steering ---

	private void steer(AbstractBoat boat, Vec3 aim, double speedLimit) {
		Vec3 toAim = aim.subtract(boat.position());
		float wanted = (float) (Mth.atan2(toAim.z, toAim.x) * Mth.RAD_TO_DEG) - 90.0F;
		float difference = Mth.wrapDegrees(wanted - boat.getYRot());
		boat.setYRot(boat.getYRot() + Mth.clamp(difference, (float) -TURN, (float) TURN));
		Vec3 motion = boat.getDeltaMovement();
		double speed = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
		if (Math.abs(difference) < 45.0F && speed < speedLimit) {
			float yaw = boat.getYRot() * Mth.DEG_TO_RAD;
			boat.setDeltaMovement(motion.add(-Mth.sin(yaw) * PUSH, 0.0, Mth.cos(yaw) * PUSH));
		} else if (speed > speedLimit) {
			double scale = speedLimit / speed;
			boat.setDeltaMovement(motion.x * scale, motion.y, motion.z * scale);
		}
		boat.setPaddleState(true, true);
	}

	private void brake(AbstractBoat boat) {
		Vec3 motion = boat.getDeltaMovement();
		boat.setDeltaMovement(motion.x * 0.8, motion.y, motion.z * 0.8);
		boat.setPaddleState(false, false);
	}

	/** Stops the boat dead, once it has moored. */
	public static void stop(AbstractBoat boat) {
		boat.setDeltaMovement(Vec3.ZERO);
		boat.setPaddleState(false, false);
	}

	private static double horizontal(Vec3 a, Vec3 b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}
}
