package dev.mioscoppergolems.habour.route;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.BlockPos;

/**
 * A route from the kontor that owns it to another harbour's kontor.
 *
 * @param id        unique within the kontor; voyages report back with it
 * @param dest      the destination kontor
 * @param cargo     what to ship; empty means everything in the kontor
 * @param repeats   runs left; {@link #FOREVER} never runs out
 * @param interval  index into {@link #INTERVALS}: the time between the start of two runs
 * @param pending   while a run is on: what is still to be sent (an empty list with {@code running} means everything)
 * @param running   a run is under way: boats are still being sent off
 * @param lastRun   game time the last run started
 * @param trips     boats that sailed on this route, for the statistics
 * @param delivered items delivered, for the statistics
 */
public record Route(
	int id,
	BlockPos dest,
	boolean enabled,
	List<Cargo> cargo,
	int repeats,
	int interval,
	Trigger trigger,
	boolean waitForFull,
	long lastRun,
	List<Cargo> pending,
	boolean running,
	int trips,
	long delivered
) {
	public static final int FOREVER = -1;
	public static final int MAX_REPEATS = 99;
	public static final int MAX_CARGO = 9;
	/** Ticks between the start of two runs: none, 1 minute, 3, 5, 10, 20 (a day) and 60 (three days). */
	public static final int[] INTERVALS = {0, 1200, 3600, 6000, 12000, 24000, 72000};

	public static final Codec<Route> CODEC = RecordCodecBuilder.create(
		instance -> instance.group(
			Codec.INT.fieldOf("id").forGetter(Route::id),
			BlockPos.CODEC.fieldOf("dest").forGetter(Route::dest),
			Codec.BOOL.fieldOf("enabled").forGetter(Route::enabled),
			Cargo.CODEC.listOf().fieldOf("cargo").forGetter(Route::cargo),
			Codec.INT.fieldOf("repeats").forGetter(Route::repeats),
			Codec.INT.fieldOf("interval").forGetter(Route::interval),
			Trigger.CODEC.fieldOf("trigger").forGetter(Route::trigger),
			Codec.BOOL.fieldOf("wait_for_full").forGetter(Route::waitForFull),
			Codec.LONG.fieldOf("last_run").forGetter(Route::lastRun),
			Cargo.CODEC.listOf().fieldOf("pending").forGetter(Route::pending),
			Codec.BOOL.fieldOf("running").forGetter(Route::running),
			Codec.INT.fieldOf("trips").forGetter(Route::trips),
			Codec.LONG.fieldOf("delivered").forGetter(Route::delivered)
		).apply(instance, Route::new)
	);

	public static Route create(int id, BlockPos dest) {
		return new Route(id, dest, true, List.of(), 1, 0, Trigger.TIMER, true, Long.MIN_VALUE / 2, List.of(), false, 0, 0L);
	}

	public int intervalTicks() {
		return INTERVALS[Math.max(0, Math.min(INTERVALS.length - 1, this.interval))];
	}

	public boolean hasRunsLeft() {
		return this.repeats == FOREVER || this.repeats > 0;
	}

	public Route withDest(BlockPos dest) {
		return new Route(this.id, dest, this.enabled, this.cargo, this.repeats, this.interval, this.trigger, this.waitForFull, this.lastRun, this.pending, this.running, this.trips, this.delivered);
	}

	public Route withEnabled(boolean enabled) {
		return new Route(this.id, this.dest, enabled, this.cargo, this.repeats, this.interval, this.trigger, this.waitForFull, this.lastRun, this.pending, this.running, this.trips, this.delivered);
	}

	public Route withCargo(List<Cargo> cargo) {
		return new Route(this.id, this.dest, this.enabled, List.copyOf(cargo), this.repeats, this.interval, this.trigger, this.waitForFull, this.lastRun, this.pending, this.running, this.trips, this.delivered);
	}

	public Route withRepeats(int repeats) {
		return new Route(this.id, this.dest, this.enabled, this.cargo, repeats, this.interval, this.trigger, this.waitForFull, this.lastRun, this.pending, this.running, this.trips, this.delivered);
	}

	public Route withInterval(int interval) {
		return new Route(this.id, this.dest, this.enabled, this.cargo, this.repeats, interval, this.trigger, this.waitForFull, this.lastRun, this.pending, this.running, this.trips, this.delivered);
	}

	public Route withTrigger(Trigger trigger) {
		return new Route(this.id, this.dest, this.enabled, this.cargo, this.repeats, this.interval, trigger, this.waitForFull, this.lastRun, this.pending, this.running, this.trips, this.delivered);
	}

	public Route withWaitForFull(boolean waitForFull) {
		return new Route(this.id, this.dest, this.enabled, this.cargo, this.repeats, this.interval, this.trigger, waitForFull, this.lastRun, this.pending, this.running, this.trips, this.delivered);
	}

	/** A run starts: the whole cargo list is still to be sent. */
	public Route startRun(long now) {
		return new Route(this.id, this.dest, this.enabled, this.cargo, this.repeats, this.interval, this.trigger, this.waitForFull, now, List.copyOf(this.cargo), true, this.trips, this.delivered);
	}

	public Route withPending(List<Cargo> pending) {
		return new Route(this.id, this.dest, this.enabled, this.cargo, this.repeats, this.interval, this.trigger, this.waitForFull, this.lastRun, List.copyOf(pending), this.running, this.trips, this.delivered);
	}

	/** The last boat of the run has been sent off. */
	public Route endRun() {
		int left = this.repeats == FOREVER ? FOREVER : Math.max(0, this.repeats - 1);
		return new Route(this.id, this.dest, this.enabled, this.cargo, left, this.interval, this.trigger, this.waitForFull, this.lastRun, List.of(), false, this.trips, this.delivered);
	}

	/** A run is called off: no run is used up. */
	public Route cancelRun() {
		return new Route(this.id, this.dest, this.enabled, this.cargo, this.repeats, this.interval, this.trigger, this.waitForFull, this.lastRun, List.of(), false, this.trips, this.delivered);
	}

	public Route withTrip() {
		return new Route(this.id, this.dest, this.enabled, this.cargo, this.repeats, this.interval, this.trigger, this.waitForFull, this.lastRun, this.pending, this.running, this.trips + 1, this.delivered);
	}

	public Route withDelivered(long items) {
		return new Route(this.id, this.dest, this.enabled, this.cargo, this.repeats, this.interval, this.trigger, this.waitForFull, this.lastRun, this.pending, this.running, this.trips, this.delivered + items);
	}
}
