package dev.mioscoppergolems.habour.captain;

import dev.mioscoppergolems.api.MiosCoppergolemsApi;
import dev.mioscoppergolems.habour.Habour;
import dev.mioscoppergolems.habour.captain.BoatPilot.Result;
import dev.mioscoppergolems.habour.harbour.Berths;
import dev.mioscoppergolems.habour.harbour.Fleet;
import dev.mioscoppergolems.habour.harbour.Harbours;
import dev.mioscoppergolems.habour.harbour.Waters;
import dev.mioscoppergolems.habour.kontor.KontorBlockEntity;
import dev.mioscoppergolems.habour.registry.HabourAttachments;
import dev.mioscoppergolems.repair.Errands;
import dev.mioscoppergolems.upkeep.Upkeep;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.animal.golem.CopperGolemState;
import net.minecraft.world.entity.vehicle.boat.AbstractChestBoat;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Runs a captain's {@link Voyage}, all of it in plain sight:
 * <ol>
 * <li>waits at the kontor while the dockhands load the chest boat (the captain never carries cargo itself);</li>
 * <li>climbs aboard and sails to the other harbour, where it moors at a free berth;</li>
 * <li>steps ashore and waits while the dockhands of that harbour unload the boat;</li>
 * <li>sails the empty boat home, moors and reports to its own kontor.</li>
 * </ol>
 * Whatever goes wrong, the captain does not give up: a full kontor is waited for, a blocked coast is sailed around, and
 * if the way is shut or the destination stays full, the cargo is brought home again.
 */
public class CaptainBehavior extends DockWork {
	private static final double BOARD_REACH = 4.0;
	private static final double BERTH_SEARCH_DISTANCE = 48.0;
	private static final int BERTH_FALLBACK_TICKS = 600;
	private static final int LOST_BOAT_TICKS = 200;
	/** How long a captain waits for a dockhand to turn up before it sails on without. */
	private static final int CREW_PATIENCE = 1200;

	private final BoatPilot pilot = new BoatPilot();
	private int lostBoatTicks;
	private int boardFailures;
	private @Nullable BlockPos berth;
	private int berthSearchTicks;
	private @Nullable List<BlockPos> berthAnchors;
	private @Nullable Voyage current;
	private long crewCheckedAt = Long.MIN_VALUE / 2;
	private boolean crewed;
	private int noCrewTicks;

	public CaptainBehavior() {
		super();
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, CopperGolem golem) {
		return Captains.isCaptain(golem) && Captains.voyage(golem) != null && !golem.isLeashed() && !Errands.isBusy(golem);
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
		this.lostBoatTicks = 0;
		this.boardFailures = 0;
		this.berth = null;
		this.berthSearchTicks = 0;
		this.berthAnchors = null;
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
		if (now % 10 == 0) {
			Harbours.keepLoaded(level, golem, 3);
		}
		if (!(level.getEntity(voyage.boat) instanceof AbstractChestBoat boat) || !boat.isAlive()) {
			if (++this.lostBoatTicks > LOST_BOAT_TICKS) {
				this.finish(level, golem, voyage, false);
			}
			return;
		}
		this.lostBoatTicks = 0;
		if (now % 20 == 0) {
			Berths.take(level, boat);
			Upkeep.spend(golem, 1);
			Fleet.report(level, golem, voyage);
		}
		if (KontorBlockEntity.DEBUG && now % 100 == 0) {
			Habour.LOGGER.info("[HABOUR-DBG] captain {} phase={} manifest={} boatEmpty={} crewed={} noCrew={} boat={} wait={} detour={}", golem.blockPosition().toShortString(), voyage.phase,
				voyage.manifest.size(), boat.isEmpty(), this.crewed, this.noCrewTicks, boat.blockPosition().toShortString(), this.waitTicks, this.detour != null);
		}
		if (voyage.isSailing()) {
			this.tickSail(level, golem, voyage, boat);
			return;
		}
		if (this.pausing(golem)) {
			return;
		}
		if (golem.isInWater() && golem.getVehicle() == null) {
			this.putAshore(level, golem, golem.blockPosition());
		}
		switch (voyage.phase) {
			case LOAD -> this.tickLoad(level, golem, voyage, boat);
			case UNLOAD -> this.tickUnload(level, golem, voyage, boat, false);
			case UNLOAD_HOME -> this.tickUnload(level, golem, voyage, boat, true);
			default -> {
			}
		}
	}

	// --- loading ---

	/** The captain never carries cargo: it waits for the dockhands to load the boat, then casts off. */
	private void tickLoad(ServerLevel level, CopperGolem golem, Voyage voyage, AbstractChestBoat boat) {
		if (Harbours.kontor(level, voyage.origin) == null) {
			this.waitAWhile(golem, 40, CopperGolemState.IDLE);
			return;
		}
		if (this.crewed(level, golem, voyage)) {
			this.holdStill(golem);
			this.noCrewTicks = 0;
			return;
		}
		if (!voyage.manifest.isEmpty()) {
			this.holdStill(golem);
			if (Dockhands.anyLinked(level, voyage.origin)) {
				// The dockhands are busy with other boats (or asleep): they will come.
				return;
			}
			if (++this.noCrewTicks > CREW_PATIENCE) {
				// Nobody came to load the rest: sail with what is aboard.
				voyage.manifest.clear();
			}
			return;
		}
		if (boat.isEmpty()) {
			// Nothing was loaded after all.
			this.finish(level, golem, voyage, true);
		} else {
			this.board(level, golem, voyage, boat, Voyage.Phase.SAIL_OUT);
		}
	}

	/** Whether dockhands are working on this voyage's boat right now (looked up a few times a second). */
	private boolean crewed(ServerLevel level, CopperGolem golem, Voyage voyage) {
		long now = level.getGameTime();
		if (now - this.crewCheckedAt >= 10) {
			this.crewCheckedAt = now;
			this.crewed = Dockhands.isCrewed(level, golem.position(), voyage.boat);
		}
		return this.crewed;
	}

	/** Walks to the boat and climbs in; switches to {@code next} once aboard. */
	private void board(ServerLevel level, CopperGolem golem, Voyage voyage, AbstractChestBoat boat, Voyage.Phase next) {
		if (golem.getVehicle() == boat) {
			voyage.phase = next;
			this.resetPhase();
			this.pilot.reset();
			this.berth = null;
			this.berthSearchTicks = 0;
			this.berthAnchors = null;
			return;
		}
		if (!this.goToBoat(level, golem, boat, BOARD_REACH)) {
			return;
		}
		this.travel.reset();
		if (!golem.startRiding(boat)) {
			if (++this.boardFailures > 5) {
				// Somebody else is sitting in it: give the cargo up for lost rather than wait for ever.
				this.finish(level, golem, voyage, false);
			} else {
				this.waitAWhile(golem, 40, CopperGolemState.IDLE);
			}
		}
	}

	// --- sailing ---

	private void tickSail(ServerLevel level, CopperGolem golem, Voyage voyage, AbstractChestBoat boat) {
		if (golem.getVehicle() != boat) {
			// Knocked out of the boat: get back in.
			this.board(level, golem, voyage, boat, voyage.phase);
			return;
		}
		golem.getNavigation().stop();
		golem.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		BlockPos kontor = voyage.target();
		Vec3 kontorCenter = Vec3.atCenterOf(kontor);
		double distance = Math.sqrt((boat.getX() - kontorCenter.x) * (boat.getX() - kontorCenter.x) + (boat.getZ() - kontorCenter.z) * (boat.getZ() - kontorCenter.z));
		long now = level.getGameTime();
		if (this.berth == null && distance < BERTH_SEARCH_DISTANCE && level.isLoaded(kontor) && now % 20 == 0) {
			this.searchBerth(level, boat, kontor);
		}
		if (this.berth == null && distance < 10.0) {
			// At the harbour, waiting for a berth to come free.
			BoatPilot.stop(boat);
			return;
		}
		Vec3 goal = this.berth != null ? new Vec3(this.berth.getX() + 0.5, this.berth.getY(), this.berth.getZ() + 0.5) : kontorCenter;
		Result result = this.pilot.step(level, boat, goal, this.berth != null);
		if (result == Result.ARRIVED) {
			this.moor(level, golem, voyage, boat);
		} else if (result == Result.STUCK) {
			this.stuck(level, golem, voyage, boat);
		}
	}

	private void searchBerth(ServerLevel level, AbstractChestBoat boat, BlockPos kontor) {
		if (this.berthAnchors == null) {
			this.berthAnchors = Berths.anchors(level, kontor);
		}
		BlockPos found = Berths.findBerth(level, kontor, this.berthAnchors, boat);
		if (found == null) {
			this.berthSearchTicks += 20;
			if (this.berthSearchTicks % 200 == 0) {
				this.berthAnchors = null;
			}
			if (this.berthSearchTicks > BERTH_FALLBACK_TICKS) {
				found = Waters.nearestFloat(level, kontor, 12);
			}
		}
		if (found != null) {
			this.berth = found;
			this.pilot.replan();
		}
	}

	private void moor(ServerLevel level, CopperGolem golem, Voyage voyage, AbstractChestBoat boat) {
		BoatPilot.stop(boat);
		golem.stopRiding();
		boat.setDeltaMovement(Vec3.ZERO);
		this.putAshore(level, golem, boat.blockPosition());
		boolean out = voyage.phase == Voyage.Phase.SAIL_OUT;
		voyage.phase = out ? Voyage.Phase.UNLOAD : Voyage.Phase.UNLOAD_HOME;
		voyage.bringHome = false;
		this.noCrewTicks = 0;
		this.fullTicks = 0;
		this.resetPhase();
		this.pilot.reset();
		this.berth = null;
		this.berthSearchTicks = 0;
		this.berthAnchors = null;
		BlockPos kontor = voyage.target();
		level.playSound(null, kontor, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 0.7F, 0.9F);
		MiosCoppergolemsApi.awardGuideNearby(level, golem.position(), Habour.id("guide/voyage"));
	}

	/** Steps the captain onto dry land next to {@code near}: a golem dropped into the water cannot walk out of it. */
	private void putAshore(ServerLevel level, CopperGolem golem, BlockPos near) {
		BlockPos land = Waters.standPoint(level, near, 5);
		if (land != null) {
			golem.teleportTo(land.getX() + 0.5, land.getY(), land.getZ() + 0.5);
			golem.getNavigation().stop();
		}
	}

	private void stuck(ServerLevel level, CopperGolem golem, Voyage voyage, AbstractChestBoat boat) {
		Habour.LOGGER.info("Captain {} cannot find a way to {}, turning back", golem.getUUID(), voyage.target());
		BoatPilot.stop(boat);
		this.pilot.reset();
		this.berth = null;
		this.berthSearchTicks = 0;
		this.berthAnchors = null;
		if (voyage.phase == Voyage.Phase.SAIL_OUT) {
			// The way is shut: bring the cargo home.
			voyage.phase = Voyage.Phase.SAIL_BACK;
		} else {
			golem.stopRiding();
			this.finish(level, golem, voyage, false);
		}
	}

	// --- unloading ---

	/** The captain never carries cargo: dockhands of the harbour unload the boat, then it sails on. */
	private void tickUnload(ServerLevel level, CopperGolem golem, Voyage voyage, AbstractChestBoat boat, boolean home) {
		BlockPos kontorPos = home ? voyage.origin : voyage.dest;
		if (Harbours.kontor(level, kontorPos) == null) {
			this.waitAWhile(golem, 40, CopperGolemState.IDLE);
			return;
		}
		if (this.crewed(level, golem, voyage)) {
			this.holdStill(golem);
			this.noCrewTicks = 0;
			return;
		}
		if (!voyage.bringHome && !boat.isEmpty()) {
			this.holdStill(golem);
			if (home || Dockhands.anyLinked(level, kontorPos)) {
				// Cargo is waiting for a dockhand (they may be asleep or charging).
				return;
			}
			if (++this.noCrewTicks <= CREW_PATIENCE) {
				return;
			}
			// No dockhand at all at the far harbour: the cargo goes home again.
			voyage.bringHome = true;
		}
		if (home) {
			this.finish(level, golem, voyage, true);
		} else {
			this.board(level, golem, voyage, boat, Voyage.Phase.SAIL_BACK);
		}
	}

	/** The trip is over, or called off. */
	private void finish(ServerLevel level, CopperGolem golem, Voyage voyage, boolean complete) {
		KontorBlockEntity origin = Harbours.kontor(level, voyage.origin);
		if (origin != null) {
			origin.voyageDone(voyage.routeId, voyage.delivered);
		}
		if (level.getEntity(voyage.boat) instanceof Entity boat) {
			Berths.release(boat);
		}
		if (!complete) {
			Habour.LOGGER.info("Captain {} gave up a voyage from {} to {}", golem.getUUID(), voyage.origin, voyage.dest);
		}
		Fleet.forget(golem);
		golem.removeAttached(HabourAttachments.VOYAGE);
		golem.clearOpenedChestPos();
		golem.setState(CopperGolemState.IDLE);
		this.current = null;
		this.resetVoyageState();
	}
}
