package dev.mioscoppergolems.habour.captain;

import dev.mioscoppergolems.api.MiosCoppergolemsApi;
import dev.mioscoppergolems.entity.CopperChestMinecart;
import dev.mioscoppergolems.golem.Inventories;
import dev.mioscoppergolems.habour.Habour;
import dev.mioscoppergolems.habour.harbour.Berths;
import dev.mioscoppergolems.habour.harbour.Fleet;
import dev.mioscoppergolems.habour.harbour.Harbours;
import dev.mioscoppergolems.habour.kontor.KontorBlockEntity;
import dev.mioscoppergolems.habour.rail.RailPilot;
import dev.mioscoppergolems.habour.rail.Rails;
import dev.mioscoppergolems.habour.rail.Trains;
import dev.mioscoppergolems.habour.registry.HabourAttachments;
import dev.mioscoppergolems.repair.Errands;
import dev.mioscoppergolems.upkeep.Upkeep;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.animal.golem.CopperGolemState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Runs a conductor's {@link Voyage} with a train of copper chest minecarts, all of it in plain sight:
 * <ol>
 * <li>couples extra carts to the train with chains from the station's stock, if the cargo needs the room;</li>
 * <li>sits in the lead cart while the dockhands load the train;</li>
 * <li>drives it along the track to the other station (turning the train around first if the carts lie ahead of it);</li>
 * <li>waits while the dockhands there unload it, drives the empty train home and reports to its station.</li>
 * </ol>
 * Like the captain, the conductor never carries cargo (it only carries the chains it couples with).
 */
public class ConductorBehavior extends DockWork {
	private static final double BOARD_REACH = 3.0;
	private static final int LOST_TICKS = 200;
	private static final int CREW_PATIENCE = 1200;

	private final RailPilot pilot = new RailPilot();
	private @Nullable Voyage current;
	private int lostTicks;
	private int boardFailures;
	private int noCrewTicks;
	private long crewCheckedAt = Long.MIN_VALUE / 2;
	private boolean crewed;
	private @Nullable BlockPos goal;
	private long goalAt = Long.MIN_VALUE / 2;
	private boolean reverseChecked;

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, CopperGolem golem) {
		return Conductors.isConductor(golem) && Captains.voyage(golem) != null && !golem.isLeashed() && !Errands.isBusy(golem);
	}

	@Override
	protected boolean canStillUse(ServerLevel level, CopperGolem golem, long timestamp) {
		return golem.getBrain().getMemory(MemoryModuleType.TRANSPORT_ITEMS_COOLDOWN_TICKS).isEmpty()
			&& !golem.isPanicking()
			&& this.checkExtraStartConditions(level, golem);
	}

	@Override
	protected boolean timedOut(long timestamp) {
		return false;
	}

	@Override
	protected void start(ServerLevel level, CopperGolem golem, long timestamp) {
		this.resetPhase();
	}

	@Override
	protected void stop(ServerLevel level, CopperGolem golem, long timestamp) {
		this.interactionTicks = 0;
		golem.clearOpenedChestPos();
		if (golem.getVehicle() == null) {
			golem.getNavigation().stop();
			golem.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		}
		golem.setState(CopperGolemState.IDLE);
	}

	private void resetVoyageState() {
		this.resetPhase();
		this.pilot.reset();
		this.fullTicks = 0;
		this.noCrewTicks = 0;
		this.boatFull = false;
		this.lostTicks = 0;
		this.boardFailures = 0;
		this.goal = null;
		this.reverseChecked = false;
	}

	@Override
	protected void tick(ServerLevel level, CopperGolem golem, long timestamp) {
		Voyage voyage = Captains.voyage(golem);
		if (voyage == null) {
			return;
		}
		if (this.current != voyage) {
			this.current = voyage;
			this.resetVoyageState();
		}
		long now = level.getGameTime();
		Vessel vessel = Vessel.of(level, voyage);
		if (vessel == null) {
			if (++this.lostTicks > LOST_TICKS) {
				this.finish(level, golem, voyage, false);
			}
			return;
		}
		this.lostTicks = 0;
		if (now % 10 == 0) {
			Harbours.keepLoaded(level, vessel.lead(), 3);
		}
		if (now % 20 == 0) {
			for (Entity part : vessel.parts()) {
				Berths.take(level, part);
			}
			Upkeep.spend(golem, 1);
			Fleet.report(level, golem, voyage);
		}
		if (this.pausing(golem)) {
			return;
		}
		if (voyage.isSailing()) {
			this.tickRide(level, golem, voyage, vessel);
			return;
		}
		switch (voyage.phase) {
			case COUPLE -> this.tickCouple(level, golem, voyage, vessel);
			case LOAD -> this.tickLoad(level, golem, voyage, vessel);
			case UNLOAD -> this.tickUnload(level, golem, voyage, vessel, false);
			case UNLOAD_HOME -> this.tickUnload(level, golem, voyage, vessel, true);
			default -> {
			}
		}
	}

	// --- coupling ---

	private void tickCouple(ServerLevel level, CopperGolem golem, Voyage voyage, Vessel vessel) {
		KontorBlockEntity station = Harbours.kontor(level, voyage.origin);
		if (station == null) {
			this.waitAWhile(golem, 40, CopperGolemState.IDLE);
			return;
		}
		ItemStack hand = golem.getMainHandItem();
		if (voyage.couple.isEmpty()) {
			if (!hand.isEmpty()) {
				// Chains left over: back into the station's stock.
				if (this.goToBlock(golem, voyage.origin) && this.interact(golem, voyage.origin, CopperGolemState.DROPPING_ITEM)) {
					ItemStack rest = Inventories.insert(station, hand);
					golem.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
					if (!rest.isEmpty()) {
						golem.spawnAtLocation(level, rest);
					}
				}
				return;
			}
			voyage.phase = Voyage.Phase.LOAD;
			this.resetPhase();
			return;
		}
		if (!(level.getEntity(voyage.couple.get(0)) instanceof CopperChestMinecart cart) || !cart.isAlive() || cart.isChained()) {
			voyage.couple.remove(0);
			return;
		}
		if (hand.isEmpty() || !CopperChestMinecart.isChain(hand)) {
			if (!this.goToBlock(golem, voyage.origin) || !this.interact(golem, voyage.origin, CopperGolemState.GETTING_ITEM)) {
				return;
			}
			ItemStack chains = Inventories.takeFirst(station, Math.min(voyage.couple.size(), HAND_LIMIT), CopperChestMinecart::isChain);
			if (chains.isEmpty()) {
				// The station ran out of chains: go with the train as it is.
				voyage.couple.clear();
				return;
			}
			golem.setItemSlot(EquipmentSlot.MAINHAND, chains);
			golem.setGuaranteedDrop(EquipmentSlot.MAINHAND);
			return;
		}
		if (!this.goToBoat(level, golem, cart) || !this.interact(golem, cart.blockPosition(), CopperGolemState.DROPPING_ITEM)) {
			return;
		}
		Entity tail = vessel.parts().get(vessel.parts().size() - 1);
		cart.setChain(hand);
		hand.shrink(1);
		if (hand.isEmpty()) {
			golem.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		}
		cart.chainTo(tail);
		voyage.wagons.add(cart.getUUID());
		voyage.couple.remove(0);
		MiosCoppergolemsApi.awardGuideNearby(level, golem.position(), Habour.id("guide/train"));
	}

	// --- loading and unloading: the dockhands carry, the conductor waits in the lead cart ---

	private boolean crewed(ServerLevel level, CopperGolem golem, Voyage voyage) {
		long now = level.getGameTime();
		if (now - this.crewCheckedAt >= 10) {
			this.crewCheckedAt = now;
			this.crewed = Dockhands.isCrewed(level, golem.position(), voyage.boat);
		}
		return this.crewed;
	}

	/** Sits in the lead cart (a cart nobody sits in may be taken by another golem). True once seated. */
	private boolean seated(ServerLevel level, CopperGolem golem, Voyage voyage, Vessel vessel) {
		if (golem.getVehicle() == vessel.lead()) {
			return true;
		}
		if (!this.goToBoat(level, golem, vessel.lead(), BOARD_REACH)) {
			return false;
		}
		this.travel.reset();
		if (!golem.startRiding(vessel.lead())) {
			if (++this.boardFailures > 20) {
				this.finish(level, golem, voyage, false);
			} else {
				this.waitAWhile(golem, 20, CopperGolemState.IDLE);
			}
		}
		return false;
	}

	private void tickLoad(ServerLevel level, CopperGolem golem, Voyage voyage, Vessel vessel) {
		if (Harbours.kontor(level, voyage.origin) == null) {
			this.waitAWhile(golem, 40, CopperGolemState.IDLE);
			return;
		}
		if (!this.seated(level, golem, voyage, vessel)) {
			return;
		}
		this.holdStill(golem);
		if (this.crewed(level, golem, voyage)) {
			this.noCrewTicks = 0;
			return;
		}
		if (!voyage.manifest.isEmpty()) {
			if (Dockhands.anyLinked(level, voyage.origin)) {
				return;
			}
			if (++this.noCrewTicks > CREW_PATIENCE) {
				voyage.manifest.clear();
			}
			return;
		}
		if (vessel.isEmpty()) {
			this.finish(level, golem, voyage, true);
		} else {
			this.depart(voyage, Voyage.Phase.SAIL_OUT);
		}
	}

	private void tickUnload(ServerLevel level, CopperGolem golem, Voyage voyage, Vessel vessel, boolean home) {
		BlockPos stationPos = home ? voyage.origin : voyage.dest;
		if (Harbours.kontor(level, stationPos) == null) {
			this.waitAWhile(golem, 40, CopperGolemState.IDLE);
			return;
		}
		if (!this.seated(level, golem, voyage, vessel)) {
			return;
		}
		this.holdStill(golem);
		if (this.crewed(level, golem, voyage)) {
			this.noCrewTicks = 0;
			return;
		}
		if (!voyage.bringHome && !vessel.isEmpty()) {
			if (home || Dockhands.anyLinked(level, stationPos)) {
				return;
			}
			if (++this.noCrewTicks <= CREW_PATIENCE) {
				return;
			}
			voyage.bringHome = true;
		}
		if (home) {
			this.finish(level, golem, voyage, true);
		} else {
			this.depart(voyage, Voyage.Phase.SAIL_BACK);
		}
	}

	private void depart(Voyage voyage, Voyage.Phase phase) {
		voyage.phase = phase;
		this.resetPhase();
		this.pilot.reset();
		this.goal = null;
		this.reverseChecked = false;
		this.noCrewTicks = 0;
	}

	// --- driving ---

	private void tickRide(ServerLevel level, CopperGolem golem, Voyage voyage, Vessel vessel) {
		Entity lead = vessel.lead();
		if (golem.getVehicle() != lead) {
			this.seated(level, golem, voyage, vessel);
			return;
		}
		golem.getNavigation().stop();
		golem.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		long now = level.getGameTime();
		BlockPos station = voyage.target();
		if (this.goal == null || now - this.goalAt >= 40) {
			BlockPos stop = level.isLoaded(station) ? Rails.stopNear(level, station, Trains.RANGE) : null;
			this.goal = stop != null ? stop : station;
			this.goalAt = now;
		}
		if (!this.reverseChecked) {
			this.reverseChecked = true;
			if (this.turnAround(level, golem, voyage, vessel)) {
				return;
			}
		}
		RailPilot.Result result = this.pilot.step(level, lead, this.goal);
		if (result == RailPilot.Result.ARRIVED) {
			this.arrive(level, voyage);
		} else if (result == RailPilot.Result.STUCK) {
			this.stuck(level, golem, voyage);
		}
	}

	/**
	 * If the carts lie in the direction the train has to go, the train points the wrong way: the conductor gets out,
	 * the chains are moved so that the last cart leads, and it climbs into that one. Returns true if it did.
	 */
	private boolean turnAround(ServerLevel level, CopperGolem golem, Voyage voyage, Vessel vessel) {
		if (vessel.parts().size() < 2 || this.goal == null) {
			return false;
		}
		BlockPos here = Rails.railAt(level, vessel.lead());
		BlockPos step = this.pilot.firstStep(level, vessel.lead(), this.goal);
		if (here == null || step == null) {
			return false;
		}
		Vec3 forward = Vec3.atBottomCenterOf(step).subtract(Vec3.atBottomCenterOf(here)).multiply(1.0, 0.0, 1.0);
		Vec3 behind = vessel.parts().get(1).position().subtract(vessel.lead().position()).multiply(1.0, 0.0, 1.0);
		if (forward.dot(behind) <= 0.1) {
			return false;
		}
		List<CopperChestMinecart> carts = new ArrayList<>();
		for (Entity part : vessel.parts()) {
			if (part instanceof CopperChestMinecart cart) {
				carts.add(cart);
			}
		}
		if (carts.size() != vessel.parts().size()) {
			return false;
		}
		golem.stopRiding();
		Trains.reverse(carts);
		UUID newLead = carts.get(carts.size() - 1).getUUID();
		voyage.setTrain(newLead, reversedIds(carts));
		this.pilot.reset();
		this.resetPhase();
		return true;
	}

	private static List<UUID> reversedIds(List<CopperChestMinecart> carts) {
		List<UUID> ids = new ArrayList<>();
		for (int i = carts.size() - 2; i >= 0; i--) {
			ids.add(carts.get(i).getUUID());
		}
		return ids;
	}

	private void arrive(ServerLevel level, Voyage voyage) {
		boolean out = voyage.phase == Voyage.Phase.SAIL_OUT;
		voyage.phase = out ? Voyage.Phase.UNLOAD : Voyage.Phase.UNLOAD_HOME;
		voyage.bringHome = false;
		this.fullTicks = 0;
		this.noCrewTicks = 0;
		this.resetPhase();
		this.pilot.reset();
		this.goal = null;
		this.reverseChecked = false;
		level.playSound(null, voyage.target(), SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 0.7F, 0.9F);
	}

	private void stuck(ServerLevel level, CopperGolem golem, Voyage voyage) {
		Habour.LOGGER.info("Conductor {} cannot find a way to {}, turning back", golem.getUUID(), voyage.target());
		this.pilot.reset();
		this.goal = null;
		this.reverseChecked = false;
		if (voyage.phase == Voyage.Phase.SAIL_OUT) {
			voyage.phase = Voyage.Phase.SAIL_BACK;
		} else {
			this.finish(level, golem, voyage, false);
		}
	}

	/** The trip is over, or called off. */
	private void finish(ServerLevel level, CopperGolem golem, Voyage voyage, boolean complete) {
		KontorBlockEntity origin = Harbours.kontor(level, voyage.origin);
		if (origin != null) {
			origin.voyageDone(voyage.routeId, voyage.delivered);
		}
		for (UUID id : voyage.train()) {
			if (level.getEntity(id) instanceof Entity cart) {
				Berths.release(cart);
			}
		}
		if (!complete) {
			Habour.LOGGER.info("Conductor {} gave up a trip from {} to {}", golem.getUUID(), voyage.origin, voyage.dest);
		}
		Fleet.forget(golem);
		golem.stopRiding();
		golem.removeAttached(HabourAttachments.VOYAGE);
		golem.clearOpenedChestPos();
		golem.setState(CopperGolemState.IDLE);
		this.current = null;
		this.resetVoyageState();
	}
}
