package dev.mioscoppergolems.habour.captain;

import com.google.common.collect.ImmutableMap;
import dev.mioscoppergolems.api.MiosCoppergolemsApi;
import dev.mioscoppergolems.golem.Inventories;
import dev.mioscoppergolems.golem.TravelWatch;
import dev.mioscoppergolems.habour.harbour.Waters;
import dev.mioscoppergolems.habour.kontor.KontorBlockEntity;
import dev.mioscoppergolems.habour.route.Cargo;
import dev.mioscoppergolems.upkeep.Upkeep;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.animal.golem.CopperGolemState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What captains and dockhands have in common: walking to a kontor or a boat, reaching out for a moment, and carrying a
 * voyage's cargo between the two, 16 items at a time. Both work on the same {@link Voyage}, so several carriers can
 * load or unload one boat together.
 */
public abstract class DockWork extends Behavior<CopperGolem> {
	protected static final int HAND_LIMIT = 16;
	protected static final int INTERACTION_TICKS = 24;
	protected static final int STALL_TICKS = 200;
	protected static final int DETOUR_TICKS = 100;
	protected static final double BOAT_REACH = 3.6;
	/** How long a carrier waits for room in a full kontor before the cargo goes back home. */
	protected static final int FULL_PATIENCE = 1200;

	protected final TravelWatch travel = new TravelWatch();
	protected int interactionTicks;
	protected int waitTicks;
	protected int fullTicks;
	protected boolean boatFull;
	protected @Nullable BlockPos stand;
	protected long standAt;
	protected @Nullable Vec3 detour;
	protected int detourTicks;

	protected DockWork() {
		super(ImmutableMap.of(MemoryModuleType.TRANSPORT_ITEMS_COOLDOWN_TICKS, MemoryStatus.VALUE_ABSENT, MemoryModuleType.IS_PANICKING, MemoryStatus.VALUE_ABSENT));
	}

	protected void resetPhase() {
		this.travel.reset();
		this.interactionTicks = 0;
		this.waitTicks = 0;
		this.detour = null;
		this.stand = null;
	}

	/** True while the golem is waiting or walking a detour: nothing else to do this tick. */
	protected boolean pausing(CopperGolem golem) {
		if (this.waitTicks > 0) {
			this.waitTicks--;
			return true;
		}
		if (this.detour != null) {
			this.tickDetour(golem);
			return true;
		}
		return false;
	}

	/** Stands still and waits for others (dockhands carry, a berth is taken ...). */
	protected void holdStill(CopperGolem golem) {
		// Standing still is not being stuck: the next walk must start with a fresh stall count.
		this.travel.reset();
		this.endInteraction(golem);
		golem.getNavigation().stop();
		golem.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
	}

	// --- loading ---

	/**
	 * One step of loading the boat at the origin kontor. Returns false once there is nothing left to do (the hand is
	 * empty and the manifest is done).
	 */
	protected boolean loadStep(ServerLevel level, CopperGolem golem, Voyage voyage, Vessel vessel, KontorBlockEntity kontor) {
		boolean carrying = !golem.getMainHandItem().isEmpty();
		if (carrying && !this.boatFull) {
			Entity part = vessel.partWithRoom();
			if (part == null) {
				// The vessel is full: load no more, and put what is left back.
				this.boatFull = true;
				voyage.manifest.clear();
				return true;
			}
			if (!this.goToBoat(level, golem, part)) {
				return true;
			}
			if (!this.interact(golem, part.blockPosition(), CopperGolemState.DROPPING_ITEM)) {
				return true;
			}
			ItemStack rest = vessel.insert(golem.getMainHandItem());
			golem.setItemSlot(EquipmentSlot.MAINHAND, rest);
			if (!rest.isEmpty()) {
				// The boat is full: load no more, and put what is left back.
				this.boatFull = true;
				voyage.manifest.clear();
			}
			return true;
		}
		if (carrying) {
			if (!this.goToBlock(golem, voyage.origin)) {
				return true;
			}
			if (!this.interact(golem, voyage.origin, CopperGolemState.DROPPING_ITEM)) {
				return true;
			}
			golem.setItemSlot(EquipmentSlot.MAINHAND, Inventories.insert(kontor, golem.getMainHandItem()));
			if (!golem.getMainHandItem().isEmpty()) {
				golem.spawnAtLocation(level, golem.getMainHandItem());
				golem.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			}
			return true;
		}
		if (voyage.manifest.isEmpty()) {
			return false;
		}
		if (!this.goToBlock(golem, voyage.origin)) {
			return true;
		}
		if (!this.interact(golem, voyage.origin, CopperGolemState.GETTING_ITEM)) {
			return true;
		}
		if (voyage.manifest.isEmpty()) {
			// Another carrier took the last of it while this one was reaching out.
			return true;
		}
		Cargo next = voyage.manifest.get(0);
		int limit = Math.min(MiosCoppergolemsApi.carryLimit(golem, HAND_LIMIT), next.amount());
		ItemStack taken = Inventories.takeFirst(kontor, limit, stack -> stack.is(next.item()));
		if (taken.isEmpty()) {
			// The goods are gone from the kontor: sail with what there is.
			voyage.manifest.remove(0);
		} else {
			golem.setItemSlot(EquipmentSlot.MAINHAND, taken);
			golem.setGuaranteedDrop(EquipmentSlot.MAINHAND);
			int left = next.amount() - taken.getCount();
			if (left > 0) {
				voyage.manifest.set(0, new Cargo(next.item(), left));
			} else {
				voyage.manifest.remove(0);
			}
		}
		return true;
	}

	// --- unloading ---

	/**
	 * One step of carrying the boat's cargo into the kontor of this harbour ({@code home}: the origin kontor, else the
	 * destination). Returns false once there is nothing left to do (the boat is empty, or the cargo goes back home).
	 */
	protected boolean unloadStep(ServerLevel level, CopperGolem golem, Voyage voyage, Vessel vessel, KontorBlockEntity kontor, boolean home) {
		BlockPos kontorPos = home ? voyage.origin : voyage.dest;
		boolean carrying = !golem.getMainHandItem().isEmpty();
		if (carrying && voyage.bringHome) {
			// The far kontor stayed full: put the load back in the vessel.
			Entity back = vessel.partWithRoom() != null ? vessel.partWithRoom() : vessel.lead();
			if (!this.goToBoat(level, golem, back)) {
				return true;
			}
			if (!this.interact(golem, back.blockPosition(), CopperGolemState.DROPPING_ITEM)) {
				return true;
			}
			golem.setItemSlot(EquipmentSlot.MAINHAND, vessel.insert(golem.getMainHandItem()));
			return true;
		}
		if (carrying) {
			if (!this.goToBlock(golem, kontorPos)) {
				return true;
			}
			if (!this.interact(golem, kontorPos, CopperGolemState.DROPPING_ITEM)) {
				return true;
			}
			ItemStack before = golem.getMainHandItem();
			ItemStack rest = Inventories.insert(kontor, before);
			if (!home) {
				voyage.delivered += before.getCount() - rest.getCount();
			}
			golem.setItemSlot(EquipmentSlot.MAINHAND, rest);
			if (!rest.isEmpty()) {
				// No room: wait for a while, then give up on it.
				this.fullTicks += INTERACTION_TICKS + 60;
				this.waitAWhile(golem, 60, CopperGolemState.DROPPING_NO_ITEM);
				if (this.fullTicks > FULL_PATIENCE) {
					if (home) {
						golem.spawnAtLocation(level, rest);
						golem.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
					} else {
						voyage.bringHome = true;
					}
				}
			} else {
				this.fullTicks = 0;
			}
			return true;
		}
		Entity from = voyage.bringHome ? null : vessel.partWithItems();
		if (from == null) {
			return false;
		}
		if (!this.goToBoat(level, golem, from)) {
			return true;
		}
		if (!this.interact(golem, from.blockPosition(), CopperGolemState.GETTING_ITEM)) {
			return true;
		}
		net.minecraft.world.Container container = Vessel.hold(from);
		int slot = firstItemSlot(container);
		if (slot < 0) {
			// Another carrier emptied this part while this one was reaching out.
			return true;
		}
		ItemStack stack = container.getItem(slot);
		int limit = Math.min(MiosCoppergolemsApi.carryLimit(golem, HAND_LIMIT), stack.getCount());
		ItemStack taken = container.removeItem(slot, limit);
		golem.setItemSlot(EquipmentSlot.MAINHAND, taken);
		golem.setGuaranteedDrop(EquipmentSlot.MAINHAND);
		return true;
	}

	protected static int firstItemSlot(net.minecraft.world.Container container) {
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			if (!container.getItem(slot).isEmpty()) {
				return slot;
			}
		}
		return -1;
	}

	// --- walking ---

	/** Walks to the block; true once the golem is within reach of it. */
	protected boolean goToBlock(CopperGolem golem, BlockPos target) {
		if (new AABB(target).inflate(1.0, 1.5, 1.0).intersects(golem.getBoundingBox())) {
			this.travel.reset();
			return true;
		}
		return this.walk(golem, target, Vec3.atCenterOf(target));
	}

	protected boolean goToBoat(ServerLevel level, CopperGolem golem, Entity boat) {
		return this.goToBoat(level, golem, boat, BOAT_REACH);
	}

	/** Walks to dry land next to the boat; true once the golem is within {@code reach} of it. */
	protected boolean goToBoat(ServerLevel level, CopperGolem golem, Entity boat, double reach) {
		double dx = golem.getX() - boat.getX();
		double dz = golem.getZ() - boat.getZ();
		if (Math.sqrt(dx * dx + dz * dz) <= reach && Math.abs(golem.getY() - boat.getY()) < 3.0) {
			this.travel.reset();
			return true;
		}
		if (this.stand == null || level.getGameTime() - this.standAt > 60) {
			this.stand = boat instanceof AbstractBoat ? Waters.standPoint(level, boat.blockPosition(), 4) : Waters.standBeside(level, boat.blockPosition(), 4);
			this.standAt = level.getGameTime();
		}
		BlockPos target = this.stand != null ? this.stand : boat.blockPosition();
		if (this.stand != null) {
			double sx = golem.getX() - (this.stand.getX() + 0.5);
			double sz = golem.getZ() - (this.stand.getZ() + 0.5);
			if (sx * sx + sz * sz < 2.25) {
				// Standing at the spot picked for it: that is as close as it gets.
				this.travel.reset();
				return true;
			}
		}
		return this.walk(golem, target, Vec3.atCenterOf(target));
	}

	protected boolean walk(CopperGolem golem, BlockPos target, Vec3 center) {
		this.endInteraction(golem);
		if (this.travel.tick(golem, center) > STALL_TICKS) {
			this.startDetour(golem);
			return false;
		}
		BehaviorUtils.setWalkAndLookTargetMemories(golem, target, Upkeep.speed(golem), 1);
		return false;
	}

	/** Reaches out for a moment; true once, when the work itself is to be done. */
	protected boolean interact(CopperGolem golem, BlockPos target, CopperGolemState state) {
		golem.getNavigation().stop();
		golem.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		golem.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(target));
		this.interactionTicks++;
		if (this.interactionTicks == 1) {
			golem.setOpenedChestPos(target);
			golem.setState(state);
		}
		if (this.interactionTicks == 9) {
			golem.playSound(state == CopperGolemState.DROPPING_ITEM ? SoundEvents.COPPER_GOLEM_ITEM_DROP : SoundEvents.COPPER_GOLEM_ITEM_GET);
		}
		if (this.interactionTicks < INTERACTION_TICKS) {
			return false;
		}
		this.endInteraction(golem);
		return true;
	}

	protected void endInteraction(CopperGolem golem) {
		if (this.interactionTicks == 0) {
			return;
		}
		this.interactionTicks = 0;
		golem.clearOpenedChestPos();
		golem.setState(CopperGolemState.IDLE);
	}

	protected void waitAWhile(CopperGolem golem, int ticks, CopperGolemState state) {
		this.endInteraction(golem);
		golem.setState(state);
		golem.getNavigation().stop();
		golem.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		this.waitTicks = ticks;
	}

	protected void startDetour(CopperGolem golem) {
		this.travel.reset();
		golem.setState(CopperGolemState.GETTING_NO_ITEM);
		golem.playSound(SoundEvents.COPPER_GOLEM_ITEM_NO_GET);
		this.detour = LandRandomPos.getPos(golem, 10, 7);
		this.detourTicks = 0;
	}

	protected void tickDetour(CopperGolem golem) {
		if (++this.detourTicks > DETOUR_TICKS || this.detour == null || golem.position().distanceTo(this.detour) < 1.5) {
			this.detour = null;
			golem.setState(CopperGolemState.IDLE);
			return;
		}
		BehaviorUtils.setWalkAndLookTargetMemories(golem, BlockPos.containing(this.detour), 1.0F, 0);
	}
}
