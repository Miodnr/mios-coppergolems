package dev.mioscoppergolems.habour.captain;

import dev.mioscoppergolems.golem.Inventories;
import dev.mioscoppergolems.habour.harbour.Harbours;
import dev.mioscoppergolems.habour.kontor.KontorBlockEntity;
import dev.mioscoppergolems.repair.Errands;
import dev.mioscoppergolems.upkeep.Upkeep;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.animal.golem.CopperGolemState;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * A dockhand's work: when a boat is waiting to be loaded at its kontor, or has moored there with cargo, it walks over and
 * carries the goods 16 items at a time, like a captain does, until the job is done. Several dockhands can work on the
 * same boat. It never gets into a boat.
 */
public class DockhandBehavior extends DockWork {
	private static final int SCAN_INTERVAL = 20;

	private @Nullable UUID captainId;
	private @Nullable UUID boatId;
	private @Nullable BlockPos harbour;
	private boolean done;

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, CopperGolem golem) {
		if (!Dockhands.isDockhand(golem) || golem.isLeashed() || golem.getVehicle() != null || Errands.isBusy(golem)) {
			return false;
		}
		BlockPos home = Captains.harbour(golem);
		if (home == null || (level.getGameTime() + golem.getId()) % SCAN_INTERVAL != 0 || !level.isLoaded(home)) {
			return false;
		}
		List<CopperGolem> work = Dockhands.work(level, home);
		CopperGolem nearest = null;
		boolean nearestFree = false;
		for (CopperGolem candidate : work) {
			Voyage wanted = Captains.voyage(candidate);
			if (wanted == null || !Dockhands.canWork(level, golem, wanted)) {
				continue;
			}
			boolean candidateFree = !Dockhands.isCrewed(level, candidate.position(), wanted.boat);
			if (nearest == null || candidateFree && !nearestFree || candidateFree == nearestFree && candidate.distanceToSqr(golem) < nearest.distanceToSqr(golem)) {
				nearest = candidate;
				nearestFree = candidateFree;
			}
		}
		Voyage voyage = nearest == null ? null : Captains.voyage(nearest);
		if (voyage == null) {
			return false;
		}
		this.captainId = nearest.getUUID();
		this.boatId = voyage.boat;
		this.harbour = home;
		return true;
	}

	@Override
	protected boolean canStillUse(ServerLevel level, CopperGolem golem, long timestamp) {
		return !this.done
			&& golem.getAttached(Dockhands.CLAIM) != null
			&& golem.getBrain().getMemory(MemoryModuleType.TRANSPORT_ITEMS_COOLDOWN_TICKS).isEmpty()
			&& !golem.isPanicking()
			&& !golem.isLeashed()
			&& golem.getVehicle() == null
			&& !Errands.isBusy(golem);
	}

	@Override
	protected boolean timedOut(long timestamp) {
		return false;
	}

	@Override
	protected void start(ServerLevel level, CopperGolem golem, long timestamp) {
		this.done = false;
		this.resetPhase();
		this.fullTicks = 0;
		this.boatFull = false;
		if (this.boatId != null) {
			golem.setAttached(Dockhands.CLAIM, new Dockhands.Claim(this.boatId, level.getGameTime()));
		}
	}

	@Override
	protected void stop(ServerLevel level, CopperGolem golem, long timestamp) {
		golem.removeAttached(Dockhands.CLAIM);
		this.interactionTicks = 0;
		golem.clearOpenedChestPos();
		golem.getNavigation().stop();
		golem.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		golem.setState(CopperGolemState.IDLE);
	}

	@Override
	protected void tick(ServerLevel level, CopperGolem golem, long timestamp) {
		long now = level.getGameTime();
		if (now % 10 == 0 && this.boatId != null) {
			golem.setAttached(Dockhands.CLAIM, new Dockhands.Claim(this.boatId, now));
			Harbours.keepLoaded(level, golem, 3);
		}
		if (now % 20 == 0) {
			Upkeep.spend(golem, 1);
		}
		Voyage voyage = this.captainId != null && level.getEntity(this.captainId) instanceof CopperGolem captain ? Captains.voyage(captain) : null;
		KontorBlockEntity kontor = this.harbour == null ? null : Harbours.kontor(level, this.harbour);
		if (voyage == null || !voyage.isDockwork() || !voyage.boat.equals(this.boatId) || this.harbour == null || !voyage.harbour().equals(this.harbour)
			|| Vessel.of(level, voyage) == null) {
			this.abandon(level, golem, kontor);
			return;
		}
		if (this.pausing(golem)) {
			return;
		}
		if (kontor == null) {
			this.waitAWhile(golem, 40, CopperGolemState.IDLE);
			return;
		}
		Vessel vessel = Vessel.of(level, voyage);
		if (vessel == null) {
			this.abandon(level, golem, kontor);
			return;
		}
		boolean more = voyage.phase == Voyage.Phase.LOAD
			? this.loadStep(level, golem, voyage, vessel, kontor)
			: this.unloadStep(level, golem, voyage, vessel, kontor, voyage.phase == Voyage.Phase.UNLOAD_HOME);
		if (!more) {
			this.done = true;
		}
	}

	/** The voyage vanished under the dockhand's hands: what it carries goes into its kontor, or onto the ground. */
	private void abandon(ServerLevel level, CopperGolem golem, @Nullable KontorBlockEntity kontor) {
		this.done = true;
		ItemStack held = golem.getMainHandItem();
		if (held.isEmpty()) {
			return;
		}
		ItemStack rest = kontor == null ? held : Inventories.insert(kontor, held);
		if (!rest.isEmpty()) {
			golem.spawnAtLocation(level, rest);
		}
		golem.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
	}
}
