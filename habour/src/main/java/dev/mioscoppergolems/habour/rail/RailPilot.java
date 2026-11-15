package dev.mioscoppergolems.habour.rail;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Drives the lead cart of a train along the track to a stop: plans a way over the rail graph, pushes the cart towards
 * the next rail block every tick and brakes at the end. The wagons follow on their chains.
 */
public final class RailPilot {
	public enum Result {
		MOVING,
		ARRIVED,
		STUCK
	}

	private static final double CRUISE = 0.28;
	private static final double CREEP = 0.12;
	private static final int REPLAN_TICKS = 100;
	private static final int STUCK_TICKS = 160;
	private static final int BRAKE_BLOCKS = 5;

	private @Nullable Plan plan;
	private int ticksSincePlan;
	private int stalled;
	private @Nullable Vec3 lastPosition;

	private record Plan(List<BlockPos> nodes, boolean complete) {
	}

	public void reset() {
		this.plan = null;
		this.ticksSincePlan = 0;
		this.stalled = 0;
		this.lastPosition = null;
	}

	/** The first rail block of the current plan after the cart's own: which way the train would go off. */
	public @Nullable BlockPos firstStep(ServerLevel level, Entity lead, BlockPos goal) {
		BlockPos at = Rails.railAt(level, lead);
		if (at == null) {
			return null;
		}
		Rails.Path path = Rails.path(level, at, goal);
		return path == null || path.nodes().size() < 2 ? null : path.nodes().get(1);
	}

	public Result step(ServerLevel level, Entity lead, BlockPos goal) {
		BlockPos at = Rails.railAt(level, lead);
		if (at == null) {
			return ++this.stalled > STUCK_TICKS ? Result.STUCK : Result.MOVING;
		}
		Vec3 center = Vec3.atBottomCenterOf(goal);
		double horizontal = Math.sqrt((lead.getX() - center.x) * (lead.getX() - center.x) + (lead.getZ() - center.z) * (lead.getZ() - center.z));
		if (at.equals(goal) && horizontal < 0.6 || horizontal < 0.4) {
			stop(lead);
			return Result.ARRIVED;
		}
		this.ticksSincePlan++;
		int index = this.plan == null ? -1 : this.plan.nodes().indexOf(at);
		if (this.plan == null || index < 0 || this.ticksSincePlan > REPLAN_TICKS || index >= this.plan.nodes().size() - 1 && !this.plan.complete() && this.ticksSincePlan > 20) {
			Rails.Path path = Rails.path(level, at, goal);
			if (path == null) {
				stop(lead);
				return ++this.stalled > STUCK_TICKS ? Result.STUCK : Result.MOVING;
			}
			this.plan = new Plan(path.nodes(), path.complete());
			this.ticksSincePlan = 0;
			index = this.plan.nodes().indexOf(at);
		}
		List<BlockPos> nodes = this.plan.nodes();
		if (index >= nodes.size() - 1) {
			// At the end of the plan: either the goal (arrival is caught above) or as far along as the track goes.
			stop(lead);
			return ++this.stalled > STUCK_TICKS ? Result.STUCK : Result.MOVING;
		}
		BlockPos next = nodes.get(index + 1);
		Vec3 toNext = Vec3.atBottomCenterOf(next).subtract(Vec3.atBottomCenterOf(at));
		Vec3 flat = new Vec3(toNext.x, 0.0, toNext.z);
		if (flat.lengthSqr() < 1.0E-6) {
			flat = Vec3.atBottomCenterOf(next).subtract(lead.position()).multiply(1.0, 0.0, 1.0);
		}
		Vec3 direction = flat.normalize();
		boolean closeToEnd = this.plan.complete() && nodes.size() - 1 - index <= BRAKE_BLOCKS;
		double speed = closeToEnd ? CREEP : CRUISE;
		Vec3 motion = lead.getDeltaMovement();
		lead.setDeltaMovement(direction.x * speed, motion.y, direction.z * speed);
		// Stuck: the cart is not getting anywhere.
		if (this.lastPosition != null && lead.position().distanceToSqr(this.lastPosition) < 0.0004) {
			this.stalled++;
		} else {
			this.stalled = 0;
		}
		this.lastPosition = lead.position();
		return this.stalled > STUCK_TICKS ? Result.STUCK : Result.MOVING;
	}

	public static void stop(Entity lead) {
		lead.setDeltaMovement(Vec3.ZERO);
	}
}
